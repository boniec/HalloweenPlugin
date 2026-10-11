package boniec.klany.listener;

import boniec.klany.Main;
import boniec.klany.core.Klan;
import boniec.klany.util.KlanNotify;
import boniec.klany.util.Msg;
import boniec.klany.util.TitleAnimator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blokuje obrazenia miedzy czlonkami tego samego klanu, dopoki klan nie ma
 * wlaczonego PvP (friendly fire) w /klan pvp.
 */
public class PvpListener implements Listener {

    // ── Skala zmiany rankingu ELO za zabojstwo ──────────────────────────────
    // Zysk zabojcy jest liniowo interpolowany na podstawie oczekiwanego wyniku (klasyczny
    // ELO expected score) miedzy MIN_ZYSK (zabojca duzo SILNIEJSZY od ofiary - latwe zabojstwo)
    // a MAX_ZYSK (zabojca duzo SLABSZY od ofiary - trudne, "underdogowe" zabojstwo).
    // Przy rownym rankingu obu graczy zysk wychodzi w okolicy polowy skali (~(MIN+MAX)/2).
    // Strata ofiary to zawsze polowa zysku zabojcy (zaokraglona), NIE symetryczna zmiana.
    /** Rowny ranking obu graczy -> (3 + 117) / 2 = 60 pkt bazowo. */
    private static final int MAX_ZYSK_RANKINGU = 117;
    private static final int MIN_ZYSK_RANKINGU = 3;

    // ── Udzial klanu w zmianie rankingu ELO gracza ───────────────────────────
    // Ranking klanu to WLASNA wartosc (nie suma ELO czlonkow) - zmienia sie tylko
    // o ten ulamek zmiany ELO gracza za zabojstwo/smierc, np. +300 ELO zabojcy -> klan
    // zabojcy dostaje +75 (300 * 0.25), a strata ofiary dziala tak samo (np. -150 ELO -> -37 klanu).
    private static final double UDZIAL_KLANU_W_RANKINGU = 0.25;

    // ── Ostatni atakujacy (fallback, gdy getKiller() zwroci null) ───────────
    // Vanilla zeruje getKiller() po ok. 5 s bez obrazen od gracza, a przy strzalach,
    // perlach i obrazeniach opoznionych (ogien, wither, upadek po ciosie) bywa null od razu.
    // Bez tego fallbacku taki kill po cichu NIE nabijal ani zabojstw, ani smierci, ani rankingu.
    // Trzymamy wlasny tracker (nie ten z dev.inventory.Main), bo tamten kasuje wpis przy
    // smierci i zalezalby od kolejnosci listenerow. Wpis wygasa po OSTATNI_ATAKUJACY_MS.
    private static final long OSTATNI_ATAKUJACY_MS = 15_000L;

    private static final class OstatniAtak {
        final UUID atakujacy;
        final long czas;
        OstatniAtak(UUID atakujacy, long czas) { this.atakujacy = atakujacy; this.czas = czas; }
    }

    private final Map<UUID, OstatniAtak> ostatniAtakujacy = new ConcurrentHashMap<>();

    private final Main plugin;

    public PvpListener(Main plugin) {
        this.plugin = plugin;
    }

    /** Zabojca ofiary: getKiller(), a gdy null - ostatni gracz, ktory ja trafil w ostatnich 15 s. */
    private Player ustalZabojce(Player ofiara) {
        Player zabojca = ofiara.getKiller();
        OstatniAtak atak = ostatniAtakujacy.remove(ofiara.getUniqueId());
        if (zabojca != null) return zabojca;
        if (atak == null) return null;
        if (System.currentTimeMillis() - atak.czas > OSTATNI_ATAKUJACY_MS) return null;
        return org.bukkit.Bukkit.getPlayer(atak.atakujacy);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAtakZapamietaj(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player ofiara)) return;
        Player atakujacy = null;
        if (event.getDamager() instanceof Player p) {
            atakujacy = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            atakujacy = p;
        }
        if (atakujacy == null || atakujacy.equals(ofiara)) return;
        ostatniAtakujacy.put(ofiara.getUniqueId(), new OstatniAtak(atakujacy.getUniqueId(), System.currentTimeMillis()));
    }

    /**
     * Sprzatanie, zeby mapa nie rosla w nieskonczonosc.
     * <p>
     * WAZNE - kolejnosc wzgledem antylogout: gdy gracz wychodzi "w walce",
     * AntyLogoutManager.handleQuit() (rejestrowane na PlayerQuitEvent w
     * AntyLogoutListener) sztucznie go zabija (player.damage(...) ->
     * PlayerDeathEvent), zeby smierc na quit liczyla sie DOKLADNIE tak jak
     * normalne zabicie w walce - w tym do statystyk/rankingu ELO w onSmierc()
     * ponizej, ktore przy braku getKiller() korzysta z fallbacku
     * ostatniAtakujacy. Gdyby ten wpis zostal skasowany TUTAJ zanim
     * PlayerDeathEvent zdazy sie odpalic, ustalZabojce() nie miec z czego
     * odczytac zabojcy i cala smierc na antylogoucie przepadalaby bez
     * zabojstwa/smierci/zmiany rankingu - mimo ze wizualnie i tak wygladala
     * jak zwykle zabicie. Dlatego priorytet MONITOR: sprzatamy jako jedni z
     * ostatnich, gdy PlayerDeathEvent (a wiec i onSmierc) juz na pewno zdazyl
     * sie wykonac.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWyjscie(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ostatniAtakujacy.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player ofiara)) return;

        Player atakujacy = null;
        if (event.getDamager() instanceof Player p) {
            atakujacy = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            atakujacy = p;
        }
        if (atakujacy == null || atakujacy.equals(ofiara)) return;

        Klan klanAtakujacego = plugin.getKlanManager().getKlanGracza(atakujacy.getUniqueId());
        if (klanAtakujacego == null) return;

        Klan klanOfiary = plugin.getKlanManager().getKlanGracza(ofiara.getUniqueId());
        if (klanOfiary == null || klanOfiary.getId() != klanAtakujacego.getId()) return;

        // Ten sam klan - blokujemy, chyba ze PvP w klanie jest jawnie wlaczone.
        if (!klanAtakujacego.isPvpWlaczone()) {
            event.setCancelled(true);
            Msg.sendGui(atakujacy, "&cNie mozesz atakowac czlonkow wlasnego klanu (PvP wylaczone - /klan pvp).");
        }
    }

    /**
     * Statystyki zabojstw/smierci PvP + system rankingu ELO.
     * Zabojstwo czlonka WLASNEGO klanu (mozliwe gdy klan ma jawnie wlaczone
     * wewnetrzne PvP w /klan pvp) NIGDY nie liczy sie do statystyk -
     * ani jako zabojstwo, ani jako smierc, ani do rankingu.
     *
     * Statystyki i ranking naliczaja sie zawsze, takze gdy jedna lub obie strony nie
     * naleza do zadnego klanu - patrz KlanManager (cache statystykiGraczy niezalezny od klanu).
     * Po zarejestrowaniu zabojstwa (gdy klany sa rozne albo jedna ze stron nie ma klanu):
     *  - oblicza zmiane rankingu ELO (skala MIN_ZYSK_RANKINGU..MAX_ZYSK_RANKINGU dla zabojcy,
     *    strata ofiary to polowa zysku zabojcy - patrz obliczZyskZabojcy)
     *  - aktualizuje rankingi obu graczy
     *  - wysyla title/subtitle WYLACZNIE zabojcy (z placeholderami rankingu, w tym
     *    %otrzymanyranking%, %zmianawtopcepozycja% i %pozycja% - zobacz komentarz
     *    przy sekcji "tytuly.zabojstwo" w klany-config.yml)
     *  - wysyla killfeed na czat calego serwera
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmierc(PlayerDeathEvent event) {
        Player ofiara = event.getEntity();
        Player zabojca = ustalZabojce(ofiara);
        if (zabojca == null || zabojca.equals(ofiara)) return;

        Klan klanZabojcy = plugin.getKlanManager().getKlanGracza(zabojca.getUniqueId());
        Klan klanOfiary = plugin.getKlanManager().getKlanGracza(ofiara.getUniqueId());

        // Zabojstwo w obrebie tego samego klanu nigdy sie nie liczy do statystyk
        // ani nie wywoluje killfeeda/title.
        if (klanZabojcy != null && klanOfiary != null && klanZabojcy.getId() == klanOfiary.getId()) {
            return;
        }

        // Statystyki (zabojstwa/smierci/ranking) naliczaja sie ZAWSZE, niezaleznie od tego,
        // czy zabojca/ofiara nalezy do jakiegos klanu (klanZabojcy/klanOfiary moga byc null -
        // KlanManager.zarejestrujZabojstwo/zarejestrujSmierc dziala z nimi poprawnie).
        plugin.getKlanManager().zarejestrujZabojstwo(klanZabojcy, zabojca.getUniqueId());
        plugin.getKlanManager().zarejestrujSmierc(klanOfiary, ofiara.getUniqueId());

        // ── Ranking ELO ──────────────────────────────────────────────────────
        // Ranking za zabicie TEJ SAMEJ osoby mozna dostac tylko raz na 30 minut -
        // zapobiega to farmieniu ELO przez powtarzane zabijanie tego samego gracza.
        // Statystyki zabojstw/smierci i killfeed dzialaja normalnie niezaleznie od tego.
        boolean liczySieDoRankingu = plugin.getKlanManager().mozeDostacRanking(zabojca.getUniqueId(), ofiara.getUniqueId());

        // Ranking ELO gracza jest NIEZALEZNY od przynaleznosci do klanu - dziala identycznie
        // z klanem i bez klanu (patrz KlanManager.getRankingGracza()).
        int rankingZabojcy = plugin.getKlanManager().getRankingGracza(zabojca.getUniqueId());
        int rankingOfiary = plugin.getKlanManager().getRankingGracza(ofiara.getUniqueId());
        // Ranking laczny (ELO + tiery) zabojcy PRZED zabojstwem - do liczenia zmiany pozycji w topie.
        int laczonyRankingZabojcyPrzed = plugin.getKlanManager().getLaczonyRankingGracza(zabojca.getUniqueId());

        int zmianaZabojcy;
        int zmianaOfiary;
        int nowyRankingZabojcy;
        int nowyRankingOfiary;
        int zmianaPozycji;
        int pozycjaZabojcy;

        if (liczySieDoRankingu) {
            int bazowyZysk = obliczZyskZabojcy(rankingZabojcy, rankingOfiary);
            // Strata ofiary to zawsze polowa BAZOWEGO zysku zabojcy (zaokraglona w dol) - bonus
            // klanu zabojcy podbija wylacznie jego zysk, nie zwieksza straty ofiary.
            zmianaOfiary = -(bazowyZysk / 2);
            // Ulepszenie klanu "Zwiekszony ranking za zabojstwo" (+% do zdobywanego rankingu).
            double bonusRankingProcent = klanZabojcy != null ? klanZabojcy.getPerk(Klan.PERK_RANKING) : 0.0;
            zmianaZabojcy = bonusRankingProcent > 0
                    ? (int) Math.round(bazowyZysk * (1.0 + bonusRankingProcent / 100.0))
                    : bazowyZysk;

            // Darth Vader - godzina patrona: wiekszy ranking za zabojstwo dla wszystkich.
            boniec.dev.inventory.Main glowny = boniec.dev.inventory.Main.getInstance();
            boniec.code.core.MistrzSystem mistrz = glowny != null ? glowny.getMistrzSystem() : null;
            if (mistrz != null) {
                double mnoznikVadera = mistrz.getRankingMultiplier();
                if (mnoznikVadera > 1.0) {
                    zmianaZabojcy = (int) Math.round(zmianaZabojcy * mnoznikVadera);
                }
            }

            nowyRankingZabojcy = Math.max(0, rankingZabojcy + zmianaZabojcy);
            nowyRankingOfiary = Math.max(0, rankingOfiary + zmianaOfiary);

            plugin.getKlanManager().ustawRanking(zabojca.getUniqueId(), nowyRankingZabojcy);
            plugin.getKlanManager().ustawRanking(ofiara.getUniqueId(), nowyRankingOfiary);
            // Po awansie na tiera ranking jest resetowany do 1000 - do title/killfeedu bierzemy realny stan.
            nowyRankingZabojcy = plugin.getKlanManager().getRankingGracza(zabojca.getUniqueId());
            nowyRankingOfiary = plugin.getKlanManager().getRankingGracza(ofiara.getUniqueId());
            plugin.getKlanManager().zarejestrujRankingCooldown(zabojca.getUniqueId(), ofiara.getUniqueId());

            if (zmianaZabojcy > 0 && glowny != null && glowny.getMisjeManager() != null) {
                glowny.getMisjeManager().dodajPostep(zabojca, boniec.misje.TypMisji.ZDOBADZ_RANKING, zmianaZabojcy);
            }

            // Ranking KLANU - wlasna wartosc, zmienia sie tylko o czesc (UDZIAL_KLANU_W_RANKINGU)
            // zmiany ELO gracza, NIE o cala jej wartosc i NIE jest suma ELO czlonkow.
            if (klanZabojcy != null) {
                int zmianaKlanuZabojcy = (int) Math.round(zmianaZabojcy * UDZIAL_KLANU_W_RANKINGU);
                plugin.getKlanManager().dodajDoRankinguKlanu(klanZabojcy, zmianaKlanuZabojcy);
            }
            if (klanOfiary != null) {
                int zmianaKlanuOfiary = (int) Math.round(zmianaOfiary * UDZIAL_KLANU_W_RANKINGU);
                plugin.getKlanManager().dodajDoRankinguKlanu(klanOfiary, zmianaKlanuOfiary);
            }

            // Zmiana pozycji zabojcy w globalnym topie ELO (liczona wzgledem rankingu SPRZED
            // tego zabojstwa, wiec musi byc wyliczona PO ustawRanking powyzej, ktora aktualizuje
            // stan wszystkich graczy do porownania).
            zmianaPozycji = plugin.getKlanManager().getZmianaPozycjiWTopie(zabojca.getUniqueId(), laczonyRankingZabojcyPrzed);
            pozycjaZabojcy = plugin.getKlanManager().getPozycjaWTopie(zabojca.getUniqueId());
        } else {
            // Cooldown aktywny - zero zmian w rankingu, ale zabojstwo nadal jest widoczne w killfeedzie.
            zmianaZabojcy = 0;
            zmianaOfiary = 0;
            nowyRankingZabojcy = rankingZabojcy;
            nowyRankingOfiary = rankingOfiary;
            zmianaPozycji = 0;
            pozycjaZabojcy = plugin.getKlanManager().getPozycjaWTopie(zabojca.getUniqueId());
        }

        // Powiadomienie (title/killfeed) jest tylko "wizytowka" - nie moze zablokowac ani
        // wywrocic nabijania statystyk. Wyjatek tutaj (np. w TitleAnimator) byl wczesniej
        // wypuszczany z handlera i zostawial reszte logiki w niedokonczonym stanie.
        try {
            powiadomOZabojstwie(zabojca, ofiara, klanZabojcy, klanOfiary,
                    zmianaZabojcy, zmianaOfiary, nowyRankingZabojcy, nowyRankingOfiary, zmianaPozycji, pozycjaZabojcy);
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger()
                    .warning("[Klany] Blad przy powiadomieniu o zabojstwie (statystyki zapisane): " + e);
        }
    }

    /**
     * Zysk rankingu zabojcy za zabojstwo - skalowany miedzy MIN_ZYSK_RANKINGU i MAX_ZYSK_RANKINGU
     * na podstawie klasycznego ELO "expected score":
     *   - zabojca duzo SILNIEJSZY od ofiary (latwe zabojstwo)  -> zysk bliski MIN_ZYSK_RANKINGU
     *   - zabojca duzo SLABSZY od ofiary (trudne, underdogowe) -> zysk bliski MAX_ZYSK_RANKINGU
     *   - rowny ranking obu graczy                             -> zysk w okolicy polowy skali
     */
    private int obliczZyskZabojcy(int rankingZabojcy, int rankingOfiary) {
        double expectedZabojca = 1.0 / (1.0 + Math.pow(10, (rankingOfiary - rankingZabojcy) / 400.0));
        double zysk = MIN_ZYSK_RANKINGU + (MAX_ZYSK_RANKINGU - MIN_ZYSK_RANKINGU) * (1.0 - expectedZabojca);
        return (int) Math.round(zysk);
    }

    private void powiadomOZabojstwie(Player zabojca, Player ofiara, Klan klanZabojcy, Klan klanOfiary,
                                     int zmianaZabojcy, int zmianaOfiary, int nowyRankingZabojcy, int nowyRankingOfiary,
                                     int zmianaPozycji, int pozycjaZabojcy) {
        String zabojcaKlanNazwa = klanZabojcy == null ? "" : klanZabojcy.getNazwa();
        String zabojcaTag = klanZabojcy == null ? "" : klanZabojcy.getTag();
        String ofiaraKlanNazwa = klanOfiary == null ? "" : klanOfiary.getNazwa();
        String ofiaraTag = klanOfiary == null ? "" : klanOfiary.getTag();

        // %klan_zabojca% / %klan_ofiara% - jesli gracz ma klan, zwraca "&8[&cTAG&8]",
        // jesli nie ma klanu, zwraca pusty string (wtedy w wiadomosci zostaje sam nick, bez tagu).
        String klanZabojcaPlaceholder = klanZabojcy == null ? "" : "&8[&c" + zabojcaTag + "&8]";
        String klanOfiaraPlaceholder = klanOfiary == null ? "" : "&8[&c" + ofiaraTag + "&8]";

        // Statystyki gracza sa NIEZALEZNE od przynaleznosci do klanu (patrz KlanManager).
        int zabojstwa = plugin.getKlanManager().getZabojstwaGracza(zabojca.getUniqueId());
        int smierciZabojcy = plugin.getKlanManager().getSmierciGracza(zabojca.getUniqueId());
        String kd = String.format(Locale.ROOT, "%.2f", Klan.kd(zabojstwa, smierciZabojcy));

        // Bez "+" - tylko liczba (np. "25" lub "-18")
        String zmianaStr = String.valueOf(zmianaZabojcy);
        // %otrzymanyranking% - to samo co zmiana ELO zabojcy, ale zawsze bez znaku (do wyswietlenia po "+")
        String otrzymanyRankingStr = String.valueOf(Math.abs(zmianaZabojcy));
        // %pktplus% - ile ELO ZYSKAL zabojca (bez znaku); %pktminus% - ile ELO STRACILA ofiara (bez znaku).
        // To NIE sa te same wartosci - strata ofiary to polowa zysku zabojcy (patrz obliczZyskZabojcy/onSmierc).
        String pktPlusStr = String.valueOf(Math.abs(zmianaZabojcy));
        String pktMinusStr = String.valueOf(Math.abs(zmianaOfiary));
        // %zmianawtopcepozycja% - zmiana miejsca w topie ELO (dodatnia = awans)
        String zmianaPozycjiStr = String.valueOf(Math.abs(zmianaPozycji));
        // %pozycja% - aktualne miejsce zabojcy w topie ELO (1 = najlepszy)
        String pozycjaStr = pozycjaZabojcy < 0 ? "?" : String.valueOf(pozycjaZabojcy);

        // ── Title z animacja (NMS) ──────────────────────────────────────────
        FileConfiguration cfg = Main.get().getConfig();
        String tytulRaw = cfg.getString("tytuly.zabojstwo.title", "");
        String subtitleRaw = cfg.getString("tytuly.zabojstwo.subtitle", "");

        // Podstawienie placeholderow
        tytulRaw = zastapPlaceholdery(tytulRaw,
                "%ofiara%", ofiara.getName(),
                "%ofiara-klan%", ofiaraKlanNazwa,
                "%ofiara-tag%", ofiaraTag,
                "%zabojca%", zabojca.getName(),
                "%zabojca-klan%", zabojcaKlanNazwa,
                "%zabojca-tag%", zabojcaTag,
                "%zabojstwa%", String.valueOf(zabojstwa),
                "%smierci%", String.valueOf(smierciZabojcy),
                "%kd%", kd,
                "%zmiana%", zmianaStr,
                "%otrzymanyranking%", otrzymanyRankingStr,
                "%zmianawtopcepozycja%", zmianaPozycjiStr,
                "%pozycja%", pozycjaStr,
                "%ranking-zabojcy%", String.valueOf(nowyRankingZabojcy),
                "%ranking-ofiary%", String.valueOf(nowyRankingOfiary));

        subtitleRaw = zastapPlaceholdery(subtitleRaw,
                "%ofiara%", ofiara.getName(),
                "%ofiara-klan%", ofiaraKlanNazwa,
                "%ofiara-tag%", ofiaraTag,
                "%zabojca%", zabojca.getName(),
                "%zabojca-klan%", zabojcaKlanNazwa,
                "%zabojca-tag%", zabojcaTag,
                "%zabojstwa%", String.valueOf(zabojstwa),
                "%smierci%", String.valueOf(smierciZabojcy),
                "%kd%", kd,
                "%zmiana%", zmianaStr,
                "%otrzymanyranking%", otrzymanyRankingStr,
                "%zmianawtopcepozycja%", zmianaPozycjiStr,
                "%pozycja%", pozycjaStr,
                "%ranking-zabojcy%", String.valueOf(nowyRankingZabojcy),
                "%ranking-ofiary%", String.valueOf(nowyRankingOfiary));

        try {
            TitleAnimator.animate(zabojca, tytulRaw, subtitleRaw);
        } catch (Exception e) {
            // Fallback: zwykly title bez animacji
            zabojca.sendTitle(Msg.color(tytulRaw), Msg.color(subtitleRaw), 5, 40, 10);
            zabojca.playSound(zabojca.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.6f);
        }

        // ── Broadcast na czat ────────────────────────────────────────────────
        // Kolejnosc placeholderow ma znaczenie: "%klan_zabojca%" musi byc PRZED "%zabojca%"
        // (i analogicznie dla ofiary), inaczej "%klan_zabojca%" zostaloby czesciowo
        // podmienione przez podmiane "%zabojca%" (bo jedna nazwa zawiera w sobie drugą).
        List<Object> placeholdery = List.of(
                "%klan_zabojca%", klanZabojcaPlaceholder,
                "%zabojca%", zabojca.getName(),
                "%zabojca-klan%", zabojcaKlanNazwa,
                "%zabojca-tag%", zabojcaTag,
                "%klan_ofiara%", klanOfiaraPlaceholder,
                "%ofiara%", ofiara.getName(),
                "%ofiara-klan%", ofiaraKlanNazwa,
                "%ofiara-tag%", ofiaraTag,
                "%zabojstwa%", String.valueOf(zabojstwa),
                "%smierci%", String.valueOf(smierciZabojcy),
                "%kd%", kd,
                "%zmiana%", zmianaStr,
                "%pktplus%", pktPlusStr,
                "%pktminus%", pktMinusStr,
                "%ranking-zabojcy%", String.valueOf(nowyRankingZabojcy),
                "%ranking-ofiary%", String.valueOf(nowyRankingOfiary)
        );
        Object[] tab = placeholdery.toArray();
        KlanNotify.broadcast("zabojstwo", tab);
    }

    private static String zastapPlaceholdery(String tekst, Object... placeholdery) {
        for (int i = 0; i + 1 < placeholdery.length; i += 2) {
            String klucz = String.valueOf(placeholdery[i]);
            String wartosc = String.valueOf(placeholdery[i + 1]);
            tekst = tekst.replace(klucz, wartosc);
        }
        return tekst;
    }
}
