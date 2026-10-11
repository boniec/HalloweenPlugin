package boniec.klany.core;

import boniec.klany.db.KlanDatabase;
import boniec.klany.util.KlanNotify;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trzyma wszystkie klany w pamieci (cache) i synchronizuje zmiany z baza danych
 * asynchronicznie (zeby nigdy nie blokowac glownego watku serwera operacjami I/O).
 * <p>
 * UWAGA: "plugin" to teraz prawdziwy JavaPlugin hosta (boniec.dev.inventory.Main),
 * a nie stary boniec.klany.Main - potrzebny do schedulera/loggera/getDataFolder().
 */
public class KlanManager {

    private final JavaPlugin plugin;
    private final KlanDatabase database;

    private final Map<Integer, Klan> klanyById = new ConcurrentHashMap<>();
    private final Map<String, Integer> tagIndex = new ConcurrentHashMap<>();       // tag (lowercase) -> klanId
    private final Map<UUID, Integer> czlonkowieIndex = new ConcurrentHashMap<>();  // gracz -> klanId

    // ── Statystyki gracza (NIEZALEZNE od przynaleznosci do klanu) ───────────────
    // Wczesniej zabojstwa/smierci/ranking zyly WYLACZNIE wewnatrz obiektu Klan, co
    // powodowalo dwa bledy: (1) gracz bez klanu nie mial ich gdzie zapisac, wiec kille/
    // smierci/ranking sie nie naliczaly, (2) gracz opuszczajacy klan tracil je razem z
    // usuwanym wpisem czlonkostwa. Ten cache (+ tabela "gracze" w bazie) zyje
    // niezaleznie od tego, czy gracz aktualnie nalezy do jakiegos klanu.
    private final Map<UUID, StatystykiGracza> statystykiGraczy = new ConcurrentHashMap<>();

    private static final class StatystykiGracza {
        volatile int zabojstwa;
        volatile int smierci;
        volatile int ranking = 1000;
        volatile int tier = 0;
    }

    // ── System tierow ───────────────────────────────────────────────────────────
    // Gdy ranking ELO gracza osiagnie PROG_TIERA (2000), dostaje +1 tiera, a jego ranking
    // wraca do RANKING_PO_TIERZE (1000). Kazdy tier "kosztuje" wiec PUNKTY_NA_TIER punktow ELO.
    public static final int PROG_TIERA = 2000;
    public static final int RANKING_PO_TIERZE = 1000;
    public static final int PUNKTY_NA_TIER = PROG_TIERA - RANKING_PO_TIERZE;

    private StatystykiGracza staty(UUID uuid) {
        return statystykiGraczy.computeIfAbsent(uuid, k -> new StatystykiGracza());
    }

    // gracz zapraszany -> lista aktywnych zaproszen (moze miec kilka od roznych klanow)
    private final Map<UUID, List<Zaproszenie>> zaproszenia = new ConcurrentHashMap<>();

    // Cooldown na zdobywanie/utrate rankingu ELO za zabojstwo TEJ SAMEJ osoby -
    // klucz to "zabojcaUuid:ofiaraUuid", wartosc to czas systemowy (millis), do ktorego
    // kolejne zabojstwo tej samej ofiary przez tego samego zabojce NIE zmieni rankingu ELO.
    // Statystyki zabojstw/smierci oraz killfeed dzialaja normalnie, niezaleznie od tego cooldownu.
    private final Map<String, Long> rankingCooldown = new ConcurrentHashMap<>();
    private static final long RANKING_COOLDOWN_MS = 30L * 60L * 1000L; // 30 minut

    public KlanManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new KlanDatabase(plugin);
    }

    /**
     * Wczytuje wszystkie klany z bazy do cache w pamieci.
     * <p>
     * UWAGA: celowo SYNCHRONICZNIE (na glownym watku), a nie async! Ta metoda jest
     * wolana raz, w onEnable(), ZANIM serwer zacznie akceptowac graczy - dopoki
     * cache (czlonkowieIndex/klanyById) nie jest wypelniony, getKlanGracza() zwraca
     * null dla KAZDEGO gracza, wiec wszystkie placeholdery %klany_...% (ranking,
     * tag, pozycja itd.) wygladaja tak, jakby dany gracz nie mial klanu - to byla
     * przyczyna "0 rankingu po restarcie": gracz laczyl sie/PAPI odpytywalo
     * placeholder, zanim asynchroniczne wczytanie z SQLite zdazylo sie skonczyc.
     * SQLite to lokalny plik, wczytanie nawet kilkuset klanow trwa pojedyncze
     * milisekundy, wiec zrobienie tego synchronicznie przy starcie nie zauwazalnie
     * wydluza uruchamianie serwera, a eliminuje ten wyscig.
     */
    public void wczytaj() {
        database.connect();
        List<Klan> wczytane = database.wczytajWszystkie();
        for (Klan klan : wczytane) {
            klanyById.put(klan.getId(), klan);
            tagIndex.put(klan.getTag().toLowerCase(Locale.ROOT), klan.getId());
            for (UUID uuid : klan.getCzlonkowie().keySet()) {
                czlonkowieIndex.put(uuid, klan.getId());
            }
        }
        // Statystyki PvP/ranking WSZYSTKICH graczy - niezaleznie od tego, czy aktualnie
        // naleza do jakiegos klanu (patrz komentarz przy statystykiGraczy powyzej).
        for (Map.Entry<UUID, int[]> wpis : database.wczytajStatystykiGraczy().entrySet()) {
            StatystykiGracza s = staty(wpis.getKey());
            int[] dane = wpis.getValue();
            s.zabojstwa = dane[0];
            s.smierci = dane[1];
            s.ranking = dane[2];
            s.tier = dane.length > 3 ? dane[3] : 0;
        }
        plugin.getLogger().info("Wczytano " + wczytane.size() + " klanow, statystyki " + statystykiGraczy.size()
                + " graczy z bazy danych.");
        if (statystykiGraczy.isEmpty()) {
            int wBazie = boniec.code.core.CoreDatabase.get().liczbaWierszy("gracze");
            if (wBazie > 0) {
                plugin.getLogger().severe("[Klany] WCZYTYWANIE STATYSTYK NIEUDANE: cache pusty, w data.db jest " + wBazie
                        + " graczy. Nie uzywaj /plugman reload — zrestartuj caly serwer.");
            }
        }
    }

    /** Synchroniczny flush rankingu/statystyk — wywolac przed zamknieciem data.db. */
    public void zapiszPrzedZamknieciemDb() {
        try {
            if (boniec.code.core.CoreDatabase.isOpen()) {
                database.connect();
                int graczy = zapiszCacheDoBazy();
                if (graczy > 0) {
                    plugin.getLogger().info("[Klany] Zapisano statystyki " + graczy + " graczy do data.db.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Klany] Nie zapisano cache: " + e.getMessage());
        }
    }

    public void zamknij() {
        zapiszPrzedZamknieciemDb();
        database.disconnect();
    }

    /** Synchroniczny zapis calego cache PvP/rankingu i stanu klanow. */
    private int zapiszCacheDoBazy() {
        if (statystykiGraczy.isEmpty()) {
            int wBazie = boniec.code.core.CoreDatabase.get().liczbaWierszy("gracze");
            if (wBazie > 0) {
                plugin.getLogger().warning("[Klany] Pomijam zapis statystyk — cache pusty, w data.db jest " + wBazie
                        + " graczy (chroni przed resetem rankingu przy reloadzie).");
                return 0;
            }
        }
        int n = 0;
        for (Map.Entry<UUID, StatystykiGracza> entry : statystykiGraczy.entrySet()) {
            UUID uuid = entry.getKey();
            StatystykiGracza s = entry.getValue();
            database.ustawStatystyki(uuid, s.zabojstwa, s.smierci);
            database.ustawRanking(uuid, s.ranking, s.tier);
            n++;
        }
        for (Klan klan : klanyById.values()) {
            database.ustawSkarbiec(klan.getId(), klan.getSkarbiec());
            database.ustawRankingKlanu(klan.getId(), klan.getRankingKlanu());
            database.ustawPvp(klan.getId(), klan.isPvpWlaczone());
            for (Map.Entry<String, Double> perk : klan.getPerki().entrySet()) {
                database.ustawPerk(klan.getId(), perk.getKey(), perk.getValue());
            }
        }
        return n;
    }

    public KlanDatabase getDatabase() {
        return database;
    }

    // ── Wyszukiwanie ──────────────────────────────────────────────────────────

    public Klan getKlanGracza(UUID uuid) {
        Integer id = czlonkowieIndex.get(uuid);
        return id == null ? null : klanyById.get(id);
    }

    public Klan getKlanByTag(String tag) {
        Integer id = tagIndex.get(tag.toLowerCase(Locale.ROOT));
        return id == null ? null : klanyById.get(id);
    }

    /**
     * Szuka czlonka klanu po nicku takze gdy jest offline.
     * getOfflinePlayer(String) na Paper 1.21 daje losowe UUID i "gracz nie w klanie".
     */
    public OfflinePlayer znajdzCzlonka(Klan klan, String nick) {
        if (klan == null || nick == null || nick.isBlank()) return null;
        Player online = Bukkit.getPlayerExact(nick);
        if (online != null && klan.isCzlonek(online.getUniqueId())) return online;
        for (UUID uuid : klan.getCzlonkowie().keySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName();
            if (name != null && name.equalsIgnoreCase(nick)) return op;
        }
        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(nick);
            if (cached != null && klan.isCzlonek(cached.getUniqueId())) return cached;
        } catch (NoSuchMethodError ignored) {
        }
        return null;
    }

    public List<String> nickiCzlonkow(Klan klan) {
        List<String> out = new ArrayList<>();
        if (klan == null) return out;
        for (UUID uuid : klan.getCzlonkowie().keySet()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null && !name.isBlank()) out.add(name);
        }
        return out;
    }

    public boolean istniejeTag(String tag) {
        return tagIndex.containsKey(tag.toLowerCase(Locale.ROOT));
    }

    public boolean istniejeNazwa(String nazwa) {
        return klanyById.values().stream().anyMatch(k -> k.getNazwa().equalsIgnoreCase(nazwa));
    }

    public Collection<Klan> getWszystkieKlany() {
        return klanyById.values();
    }

    /** Top klanow posortowany malejaco po sumie ELO wszystkich czlonkow.
     *  Klany mniejsze niz MIN_CZLONKOW_DO_RANKINGU nie sa uwzgledniane w rankingu. */
    private static final int MIN_CZLONKOW_DO_RANKINGU = 3;

    public List<Klan> getTopKlanowPoRankingu(int limit) {
        List<Klan> lista = new ArrayList<>(klanyById.values())
                .stream()
                .filter(k -> k.getLiczbaCzlonkow() >= MIN_CZLONKOW_DO_RANKINGU)
                .collect(Collectors.toList());
        lista.sort((a, b) -> Integer.compare(getSumaRankinguKlanu(b), getSumaRankinguKlanu(a)));
        return lista.subList(0, Math.min(limit, lista.size()));
    }

    /**
     * Ranking ELO klanu - WLASNA wartosc klanu (NIE suma rankingow czlonkow!).
     * Rosnie/maleje tylko o czesc zmiany ELO gracza za zabojstwo/smierc PvP -
     * patrz PvpListener. Dzieki temu np. 3 czlonkow po 2000 ELO nie daje klanowi
     * 6000 rankingu.
     */
    public int getSumaRankinguKlanu(Klan klan) {
        return klan.getRankingKlanu();
    }

    /** Zmienia (dodaje/odejmuje) ranking ELO klanu i zapisuje go asynchronicznie w bazie. */
    public void dodajDoRankinguKlanu(Klan klan, int ile) {
        if (klan == null || ile == 0) return;
        klan.dodajDoRankinguKlanu(ile);
        int wartosc = klan.getRankingKlanu();
        int klanId = klan.getId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.ustawRankingKlanu(klanId, wartosc));
    }

    /** Suma zabojstw wszystkich obecnych czlonkow klanu. */
    public int getSumaZabojstwKlanu(Klan klan) {
        int suma = 0;
        for (UUID uuid : klan.getCzlonkowie().keySet()) {
            suma += getZabojstwaGracza(uuid);
        }
        return suma;
    }

    /** Klan na danej pozycji w topie (1-based). Zwraca null jesli pozycja poza zakresem. */
    public Klan getKlanNaPozycji(int pozycja) {
        List<Klan> top = getTopKlanowPoRankingu(pozycja);
        if (pozycja < 1 || pozycja > top.size()) return null;
        return top.get(pozycja - 1);
    }

    /**
     * Top klanow posortowany malejaco po skarbcu (kasie klanu). W odroznieniu od
     * getTopKlanowPoRankingu, TUTAJ nie ma minimalnej liczby czlonkow do wejscia w top -
     * kazdy klan z niezerowym/zerowym skarbcem sie liczy, bo to zwykla kolejka po kasie,
     * a nie ranking rywalizacyjny ELO.
     */
    public List<Klan> getTopKlanowPoSkarbcu(int limit) {
        List<Klan> lista = new ArrayList<>(klanyById.values());
        lista.sort((a, b) -> Integer.compare(b.getSkarbiec(), a.getSkarbiec()));
        return lista.subList(0, Math.min(limit, lista.size()));
    }

    /** Klan na danej pozycji w topie kasy (1-based). Zwraca null jesli pozycja poza zakresem. */
    public Klan getKlanNaPozycjiKasy(int pozycja) {
        List<Klan> top = getTopKlanowPoSkarbcu(pozycja);
        if (pozycja < 1 || pozycja > top.size()) return null;
        return top.get(pozycja - 1);
    }

    /**
     * Top klanow posortowany malejaco po sumie zabojstw wszystkich obecnych czlonkow
     * (patrz getSumaZabojstwKlanu). Bez minimalnej liczby czlonkow - to zwykla kolejka
     * po zabojstwach, a nie ranking rywalizacyjny ELO.
     */
    public List<Klan> getTopKlanowPoZabojstwach(int limit) {
        List<Klan> lista = new ArrayList<>(klanyById.values());
        lista.sort((a, b) -> Integer.compare(getSumaZabojstwKlanu(b), getSumaZabojstwKlanu(a)));
        return lista.subList(0, Math.min(limit, lista.size()));
    }

    /** Klan na danej pozycji w topie zabojstw (1-based). Zwraca null jesli pozycja poza zakresem. */
    public Klan getKlanNaPozycjiZabojstw(int pozycja) {
        List<Klan> top = getTopKlanowPoZabojstwach(pozycja);
        if (pozycja < 1 || pozycja > top.size()) return null;
        return top.get(pozycja - 1);
    }

    /** Gracz na danej pozycji w globalnym topie ELO (1-based). Zwraca null jesli pozycja poza zakresem. */
    public UUID getGraczNaPozycji(int pozycja) {
        List<UUID> posortowani = getGraczyPosortowanychPoRankingu();
        if (pozycja < 1 || pozycja > posortowani.size()) return null;
        return posortowani.get(pozycja - 1);
    }

    /**
     * Gracz na danej pozycji w globalnym topie zabojstw PvP (1-based, malejaco po
     * liczbie zabojstw). Zwraca null jesli pozycja poza zakresem.
     */
    public UUID getGraczNaPozycjiZabojstw(int pozycja) {
        List<UUID> posortowani = new ArrayList<>(statystykiGraczy.keySet());
        posortowani.sort((a, b) -> Integer.compare(getZabojstwaGracza(b), getZabojstwaGracza(a)));
        if (pozycja < 1 || pozycja > posortowani.size()) return null;
        return posortowani.get(pozycja - 1);
    }

    /**
     * Gracz na danej pozycji w globalnym topie smierci PvP (1-based, malejaco po
     * liczbie smierci). Zwraca null jesli pozycja poza zakresem.
     */
    public UUID getGraczNaPozycjiSmierci(int pozycja) {
        List<UUID> posortowani = new ArrayList<>(statystykiGraczy.keySet());
        posortowani.sort((a, b) -> Integer.compare(getSmierciGracza(b), getSmierciGracza(a)));
        if (pozycja < 1 || pozycja > posortowani.size()) return null;
        return posortowani.get(pozycja - 1);
    }

    /**
     * Zwraca posortowana (malejaco po ELO) liste UUID WSZYSTKICH graczy, ktorzy maja
     * jakikolwiek zapisany ranking/statystyki - niezaleznie od tego, czy naleza aktualnie
     * do jakiegos klanu. Uzywana do wyliczenia globalnej pozycji gracza w topie ELO
     * (patrz getPozycjaWTopie).
     */
    private List<UUID> getGraczyPosortowanychPoRankingu() {
        List<UUID> gracze = new ArrayList<>(statystykiGraczy.keySet());
        gracze.sort((a, b) -> Integer.compare(getLaczonyRankingGracza(b), getLaczonyRankingGracza(a)));
        return gracze;
    }

    /** Ranking ELO gracza (domyslnie 1000), niezaleznie od przynaleznosci do klanu. */
    public int getRankingGracza(UUID uuid) {
        StatystykiGracza s = statystykiGraczy.get(uuid);
        return s == null ? 1000 : s.ranking;
    }

    /** Tier gracza (domyslnie 0) - ile razy przekroczyl PROG_TIERA rankingu. */
    public int getTierGracza(UUID uuid) {
        StatystykiGracza s = statystykiGraczy.get(uuid);
        return s == null ? 0 : s.tier;
    }

    /**
     * Ranking "laczny" = ranking + tiery * PUNKTY_NA_TIER (np. tier 1 + ranking 1000 = 2000).
     * Uzywany do sortowania topu i do osiagniec, zeby awans na tiera nie wyrzucal gracza w dol topki.
     */
    public int getLaczonyRankingGracza(UUID uuid) {
        return getRankingGracza(uuid) + getTierGracza(uuid) * PUNKTY_NA_TIER;
    }

    /** Zabojstwa PvP gracza (domyslnie 0), niezaleznie od przynaleznosci do klanu. */
    public int getZabojstwaGracza(UUID uuid) {
        StatystykiGracza s = statystykiGraczy.get(uuid);
        return s == null ? 0 : s.zabojstwa;
    }

    /** Smierci PvP gracza (domyslnie 0), niezaleznie od przynaleznosci do klanu. */
    public int getSmierciGracza(UUID uuid) {
        StatystykiGracza s = statystykiGraczy.get(uuid);
        return s == null ? 0 : s.smierci;
    }

    /**
     * Pozycja gracza w globalnym topie ELO (1 = najlepszy). Dziala dla KAZDEGO gracza,
     * ktory ma jakikolwiek zapisany ranking (np. zabil lub zginal chociaz raz), niezaleznie
     * od tego, czy aktualnie nalezy do klanu. Zwraca -1, jesli gracz nie ma jeszcze zadnych
     * zapisanych statystyk (nigdy nie wzial udzialu w PvP liczonym do rankingu).
     */
    public int getPozycjaWTopie(UUID uuid) {
        if (!statystykiGraczy.containsKey(uuid)) return -1;
        List<UUID> posortowani = getGraczyPosortowanychPoRankingu();
        int pozycja = posortowani.indexOf(uuid);
        return pozycja < 0 ? -1 : pozycja + 1;
    }

    public int getPozycjaWTopieZabojstw(UUID uuid) {
        if (!statystykiGraczy.containsKey(uuid)) return -1;
        List<UUID> posortowani = new ArrayList<>(statystykiGraczy.keySet());
        posortowani.sort((a, b) -> Integer.compare(getZabojstwaGracza(b), getZabojstwaGracza(a)));
        int pozycja = posortowani.indexOf(uuid);
        return pozycja < 0 ? -1 : pozycja + 1;
    }

    public int getPozycjaWTopieSmierci(UUID uuid) {
        if (!statystykiGraczy.containsKey(uuid)) return -1;
        List<UUID> posortowani = new ArrayList<>(statystykiGraczy.keySet());
        posortowani.sort((a, b) -> Integer.compare(getSmierciGracza(b), getSmierciGracza(a)));
        int pozycja = posortowani.indexOf(uuid);
        return pozycja < 0 ? -1 : pozycja + 1;
    }

    /**
     * Zmiana pozycji gracza w topie ELO wzgledem stanu SPRZED zabojstwa (dodatnia =
     * awans, np. gracz z 15. na 12. miejsce -> zwraca +3; ujemna = spadek miejsc).
     * "laczonyRankingSprzed" to ranking LACZNY gracza (patrz getLaczonyRankingGracza) PRZED
     * przetworzeniem tego zabojstwa.
     */
    public int getZmianaPozycjiWTopie(UUID uuid, int laczonyRankingSprzed) {
        if (!statystykiGraczy.containsKey(uuid)) return 0;

        List<UUID> gracze = new ArrayList<>(statystykiGraczy.keySet());

        // Pozycja PRZED zabojstwem: podmieniamy chwilowo ranking gracza na wartosc sprzed zmiany.
        List<UUID> przed = new ArrayList<>(gracze);
        przed.sort((a, b) -> {
            int elA = a.equals(uuid) ? laczonyRankingSprzed : getLaczonyRankingGracza(a);
            int elB = b.equals(uuid) ? laczonyRankingSprzed : getLaczonyRankingGracza(b);
            return Integer.compare(elB, elA);
        });
        int pozycjaPrzed = przed.indexOf(uuid) + 1;

        int pozycjaPo = getPozycjaWTopie(uuid);
        if (pozycjaPo < 0) return 0;

        return pozycjaPrzed - pozycjaPo;
    }

    // ── Operacje ──────────────────────────────────────────────────────────────

    public Klan stworzKlan(String nazwa, String tag, UUID lider) {
        int id = database.stworzKlan(nazwa, tag, lider);
        if (id == -1) return null;
        Klan klan = new Klan(id, nazwa, tag, lider, false, 0, System.currentTimeMillis());
        klan.getCzlonkowie().put(lider, Klan.Ranga.LIDER);
        klanyById.put(id, klan);
        tagIndex.put(tag.toLowerCase(Locale.ROOT), id);
        czlonkowieIndex.put(lider, id);
        // Zapisz przywownika do bazy z rankingiem 1000
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.dodajCzlonka(id, lider, Klan.Ranga.LIDER));
        return klan;
    }

    public void usunKlan(Klan klan) {
        for (UUID uuid : new HashSet<>(klan.getCzlonkowie().keySet())) {
            czlonkowieIndex.remove(uuid);
        }
        klanyById.remove(klan.getId());
        tagIndex.remove(klan.getTag().toLowerCase(Locale.ROOT));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.usunKlan(klan.getId()));
    }

    /** Baza + perk (+1 na poziom), max z configu. Stary default 8 bez nowego klucza = 5. */
    public int getLimitCzlonkow(Klan klan) {
        if (klan == null) return 5;
        org.bukkit.configuration.file.FileConfiguration c =
                boniec.klany.Main.get() != null ? boniec.klany.Main.get().getConfig() : null;
        int baza = c != null ? c.getInt("ustawienia.max-czlonkow", 5) : 5;
        int abs = c != null ? c.getInt("ustawienia.max-czlonkow-absolutny", 10) : 10;
        if (c != null && !c.contains("ustawienia.max-czlonkow-absolutny") && baza == 8) {
            baza = 5;
        }
        if (abs <= baza) abs = 10;
        return klan.getMaxCzlonkow(baza, abs);
    }

    /**
     * Przelicza stary mnoznik perku na +osoby i wyrzuca nadmiar czlonkow
     * (od konca listy, lider zostaje). Klany z perkami maja wyzszy limit.
     */
    public void znormalizujIPrzytnijLimity() {
        int wyrzuconych = 0;
        for (Klan klan : klanyById.values()) {
            double surowa = klan.getPerk(Klan.PERK_LIMIT_GRACZY);
            int extra = klan.getDodatkoweMiejsca();
            if (surowa > 0 && Math.abs(surowa - extra) > 1e-6) {
                klan.setPerk(Klan.PERK_LIMIT_GRACZY, extra);
                database.ustawPerk(klan.getId(), Klan.PERK_LIMIT_GRACZY, extra);
            }
            wyrzuconych += przytnijNadmiarCzlonkow(klan);
        }
        if (wyrzuconych > 0) {
            plugin.getLogger().info("[Klany] Usunieto " + wyrzuconych
                    + " czlonkow ponad limit (baza 5, +1 za perk, max 10).");
        }
    }

    /** Wyrzuca nadmiarowych czlonkow od najpozniej dodanych. Lidera nie rusza. */
    public int przytnijNadmiarCzlonkow(Klan klan) {
        int limit = getLimitCzlonkow(klan);
        if (klan.getLiczbaCzlonkow() <= limit) return 0;
        List<UUID> kolejnosc = new ArrayList<>(klan.getCzlonkowie().keySet());
        Collections.reverse(kolejnosc);
        int usunieto = 0;
        for (UUID uuid : kolejnosc) {
            if (klan.getLiczbaCzlonkow() <= limit) break;
            if (klan.getRanga(uuid) == Klan.Ranga.LIDER) continue;
            if (klan.getLider() != null && klan.getLider().equals(uuid)) continue;
            usunCzlonka(klan, uuid);
            usunieto++;
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                Msg.send(online, "wyrzucony-limit", "%klan%", klan.getNazwa(), "%limit%", String.valueOf(limit));
            }
            database.dodajLog(klan.getId(), "SYSTEM usunal czlonka ponad limit (" + limit + ").");
        }
        return usunieto;
    }

    public void dodajCzlonka(Klan klan, UUID uuid, Klan.Ranga ranga) {
        klan.getCzlonkowie().put(uuid, ranga);
        czlonkowieIndex.put(uuid, klan.getId());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.dodajCzlonka(klan.getId(), uuid, ranga));
    }

    /**
     * Usuwa gracza z klanu (opuszczenie/wyrzucenie). Nie rusza statystykiGraczy ani
     * tabeli "gracze" w bazie - kille/smierci/ranking gracza sa NIEZALEZNE od
     * przynaleznosci do klanu i pozostaja nietkniete po wyjsciu z klanu.
     */
    public void usunCzlonka(Klan klan, UUID uuid) {
        klan.getCzlonkowie().remove(uuid);
        czlonkowieIndex.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.usunCzlonka(uuid));
    }

    public void wyrzucCzlonka(Player wykonujacy, Klan klan, OfflinePlayer cel) {
        if (klan.getRanga(cel.getUniqueId()) == Klan.Ranga.LIDER) {
            Msg.sendGui(wykonujacy, "&cNie mozesz wyrzucic lidera klanu.");
            return;
        }
        if (klan.getRanga(cel.getUniqueId()) == Klan.Ranga.ZASTEPCA && !klan.getLider().equals(wykonujacy.getUniqueId())) {
            Msg.send(wykonujacy, "brak-uprawnien");
            return;
        }
        String nick = cel.getName() != null ? cel.getName() : "gracz";
        usunCzlonka(klan, cel.getUniqueId());
        dodajLog(klan, wykonujacy.getName() + " wyrzucil " + nick + " z klanu.");
        Msg.send(wykonujacy, "wyrzucono-gracza", "%gracz%", nick);
        if (cel.isOnline() && cel.getPlayer() != null) {
            Msg.send(cel.getPlayer(), "wyrzucony", "%klan%", klan.getNazwa());
        }
        KlanNotify.broadcast("wyrzucenie",
                "%gracz%", nick, "%wyrzucajacy%", wykonujacy.getName(),
                "%klan%", klan.getNazwa(), "%tag%", klan.getTag());
    }

    public void ustawRange(Klan klan, UUID uuid, Klan.Ranga ranga) {
        klan.getCzlonkowie().put(uuid, ranga);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.ustawRange(uuid, ranga));
    }

    public void ustawLidera(Klan klan, UUID nowyLider) {
        UUID staryLider = klan.getLider();
        klan.setLider(nowyLider);
        klan.getCzlonkowie().put(nowyLider, Klan.Ranga.LIDER);
        if (staryLider != null && !staryLider.equals(nowyLider)) {
            klan.getCzlonkowie().put(staryLider, Klan.Ranga.ZASTEPCA);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            database.ustawLidera(klan.getId(), nowyLider);
            database.ustawRange(nowyLider, Klan.Ranga.LIDER);
            if (staryLider != null && !staryLider.equals(nowyLider)) {
                database.ustawRange(staryLider, Klan.Ranga.ZASTEPCA);
            }
        });
    }

    public void ustawPvp(Klan klan, boolean wartosc) {
        klan.setPvpWlaczone(wartosc);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.ustawPvp(klan.getId(), wartosc));
    }

    public void zapiszSkarbiec(Klan klan) {
        int wartosc = klan.getSkarbiec();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.ustawSkarbiec(klan.getId(), wartosc));
    }

    public void dodajLog(Klan klan, String tresc) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.dodajLog(klan.getId(), tresc));
    }

    /**
     * Zwieksza dany perk klanowy (Klan.PERK_DROP / Klan.PERK_RANKING) o "ile" procent -
     * aktualizuje cache w pamieci od razu i zapisuje nowa wartosc do bazy asynchronicznie.
     * Zwraca nowa wartosc procentowa perku.
     */
    public double dodajDoPerku(Klan klan, String perkId, double ile) {
        double nowaWartosc = klan.dodajDoPerku(perkId, ile);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.ustawPerk(klan.getId(), perkId, nowaWartosc));
        return nowaWartosc;
    }

    public void ustawPerk(Klan klan, String perkId, double wartosc) {
        klan.setPerk(perkId, wartosc);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.ustawPerk(klan.getId(), perkId, wartosc));
    }

    /**
     * Rejestruje zabojstwo gracza "zabojca" - aktualizuje cache statystyk w pamieci od razu
     * i zapisuje do bazy asynchronicznie. NIEZALEZNE od przynaleznosci do klanu - dziala
     * identycznie dla gracza z klanem i bez klanu (parametr "klanZabojcy" moze byc null,
     * zostal tylko dla czytelnosci wywolan w PvpListener). Wolanie tej metody dla zabojstw
     * wewnatrz tego samego klanu jest odpowiedzialnoscia wywolujacego (PvpListener).
     */
    public void zarejestrujZabojstwo(Klan klanZabojcy, UUID zabojca) {
        StatystykiGracza s = staty(zabojca);
        s.zabojstwa++;
        zapiszStatystykiGracza(zabojca);
    }

    /** Rejestruje smierc gracza "ofiara". NIEZALEZNE od przynaleznosci do klanu (patrz wyzej). */
    public void zarejestrujSmierc(Klan klanOfiary, UUID ofiara) {
        StatystykiGracza s = staty(ofiara);
        s.smierci++;
        zapiszStatystykiGracza(ofiara);
    }

    /**
     * Zapisuje statystyki gracza do bazy. Wczesniej kazde zdarzenie wysylalo do osobnego zadania
     * async wartosc policzona W CHWILI zdarzenia - przy dwoch szybkich zabojstwach zadania mogly
     * sie wykonac w odwrotnej kolejnosci i nowszy wynik byl nadpisywany starszym (gracz "tracil" kill).
     * Teraz zadanie czyta wartosci z cache DOPIERO gdy sie wykonuje, wiec zapisuje zawsze najnowszy
     * stan, a zadania na jednym watku bazy wykonuja sie sekwencyjnie (metody bazy sa synchronized).
     */
    private void zapiszStatystykiGracza(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                database.ustawStatystyki(uuid, getZabojstwaGracza(uuid), getSmierciGracza(uuid)));
    }

    /** Ustawia ranking bez awansu na tier (przywracanie z backupu). */
    public void nadpiszRanking(UUID uuid, int ranking) {
        if (uuid == null) return;
        StatystykiGracza s = staty(uuid);
        s.ranking = Math.max(0, ranking);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> database.ustawRanking(uuid, getRankingGracza(uuid), getTierGracza(uuid)));
    }

    /**
     * Aktualizuje ranking ELO gracza w cache i bazie danych. Dziala NIEZALEZNIE od
     * przynaleznosci do klanu - gracz bez klanu ma wlasny wiersz w tabeli "gracze".
     */
    public void ustawRanking(UUID uuid, int nowyRanking) {
        StatystykiGracza s = staty(uuid);
        boolean awansNaTiera = nowyRanking >= PROG_TIERA;
        if (awansNaTiera) {
            s.tier++;
            nowyRanking = RANKING_PO_TIERZE;
        }
        s.ranking = nowyRanking;
        // Czytamy z cache dopiero przy wykonaniu zadania (patrz zapiszStatystykiGracza) -
        // dwa szybkie zabojstwa nie nadpisza nowszego rankingu starszym.
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> database.ustawRanking(uuid, getRankingGracza(uuid), getTierGracza(uuid)));

        if (awansNaTiera) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(Msg.color("&6&lTIER &8» &fOsiagnales &e" + PROG_TIERA + " &frankingu! Dostajesz &eTier "
                        + s.tier + "&f, a Twoj ranking wraca do &e" + RANKING_PO_TIERZE + "&f."));
            }
        }
    }

    // ── Cooldown na ranking ELO za zabojstwo tej samej osoby ────────────────────

    /**
     * Sprawdza, czy zabojca moze otrzymac/stracic ranking ELO za zabicie tej konkretnej
     * ofiary teraz - zwraca false, jesli od ostatniego takiego zabojstwa (zabojca -> ta sama
     * ofiara) minelo mniej niz 30 minut. Nie modyfikuje stanu - do samego sprawdzenia
     * uzyj razem z {@link #zarejestrujRankingCooldown(UUID, UUID)}.
     */
    public boolean mozeDostacRanking(UUID zabojca, UUID ofiara) {
        Long ostatnio = rankingCooldown.get(kluczCooldownu(zabojca, ofiara));
        return ostatnio == null || System.currentTimeMillis() >= ostatnio;
    }

    /** Zapisuje moment (teraz + 30 minut), do ktorego zabojca nie dostanie rankingu za ta sama ofiare ponownie. */
    public void zarejestrujRankingCooldown(UUID zabojca, UUID ofiara) {
        rankingCooldown.put(kluczCooldownu(zabojca, ofiara), System.currentTimeMillis() + RANKING_COOLDOWN_MS);
    }

    /** Ile milisekund pozostalo do konca cooldownu rankingu zabojca->ofiara (0, jesli brak cooldownu). */
    public long pozostalyCooldownRankingu(UUID zabojca, UUID ofiara) {
        Long ostatnio = rankingCooldown.get(kluczCooldownu(zabojca, ofiara));
        if (ostatnio == null) return 0;
        long pozostalo = ostatnio - System.currentTimeMillis();
        return Math.max(0, pozostalo);
    }

    private static String kluczCooldownu(UUID zabojca, UUID ofiara) {
        return zabojca + ":" + ofiara;
    }

    /**
     * Resetuje statystyki PvP i ranking ELO WSZYSTKICH graczy (zabojstwa, smierci,
     * ranking -> 1000) - uzywane przez konsolowa komende /aresetranking all.
     * <p>
     * Resetowany jest zarowno cache statystyk graczy w pamieci (statystykiGraczy),
     * jak i baza danych (tabele "gracze" i "czlonkowie" - patrz KlanDatabase#resetujStatystykiWszystkich).
     * Cooldown rankingu ELO (zabojca -> ofiara) jest rowniez czyszczony, zeby nie
     * blokowal naliczania rankingu po resecie starym stanem sprzed resetu.
     * <p>
     * CELOWO NIE rusza: skarbca klanu (Klan#skarbiec), perkow klanowych/ulepszen
     * (Klan#perki), ani samej przynaleznosci/rang czlonkow klanu - "aresetranking"
     * dotyczy WYLACZNIE statystyk PvP/rankingu, nie struktury ani zasobow klanu.
     */
    public void resetujStatystykiWszystkich() {
        statystykiGraczy.clear();
        rankingCooldown.clear();
        // Ranking klanu to od teraz WLASNA wartosc klanu (nie suma ELO czlonkow), wiec trzeba
        // ja rowniez wyzerowac tutaj - inaczej reset rankingu graczy nie zerowalby rankingu klanow.
        for (Klan klan : klanyById.values()) {
            klan.setRankingKlanu(0);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, database::resetujStatystykiWszystkich);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, database::resetujRankingKlanowWszystkich);
    }

    // ── Zaproszenia ──────────────────────────────────────────────────────────

    public void dodajZaproszenie(UUID gracz, Zaproszenie zaproszenie) {
        zaproszenia.computeIfAbsent(gracz, k -> new ArrayList<>()).add(zaproszenie);
    }

    public boolean maZaproszenieOd(UUID gracz, String tag) {
        oczyscWygasle(gracz);
        List<Zaproszenie> lista = zaproszenia.get(gracz);
        if (lista == null) return false;
        return lista.stream().anyMatch(z -> z.getKlanTag().equalsIgnoreCase(tag));
    }

    public Zaproszenie pobierzZaproszenie(UUID gracz, String tag) {
        oczyscWygasle(gracz);
        List<Zaproszenie> lista = zaproszenia.get(gracz);
        if (lista == null) return null;
        return lista.stream().filter(z -> z.getKlanTag().equalsIgnoreCase(tag)).findFirst().orElse(null);
    }

    public void usunZaproszenia(UUID gracz, int klanId) {
        List<Zaproszenie> lista = zaproszenia.get(gracz);
        if (lista == null) return;
        lista.removeIf(z -> z.getKlanId() == klanId);
    }

    private void oczyscWygasle(UUID gracz) {
        List<Zaproszenie> lista = zaproszenia.get(gracz);
        if (lista == null) return;
        lista.removeIf(Zaproszenie::isWygasle);
    }
}
