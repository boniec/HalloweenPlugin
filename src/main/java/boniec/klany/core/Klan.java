package boniec.klany.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reprezentuje pojedynczy klan trzymany w pamieci (cache).
 * Zmiany propagowane sa do bazy danych przez KlanManager/KlanDatabase.
 */
public class Klan {

    private int id;
    private String nazwa;
    private String tag;
    private UUID lider;
    private boolean pvpWlaczone;
    private int skarbiec; // ilosc "smoczych odlamkow" w skarbcu
    private long dataZalozenia;

    // Ranking ELO klanu - WLASNA, niezalezna wartosc (NIE suma rankingow czlonkow!).
    // Zmienia sie tylko o czesc (ulamek) zmiany ELO gracza za zabojstwo/smierc PvP -
    // patrz PvpListener.UDZIAL_KLANU_W_RANKINGU. Dzieki temu klan nie rosnie automatycznie
    // wraz z liczba/sila czlonkow, tylko realnie z aktywnosci PvP klanu.
    private int rankingKlanu;

    // UUID -> ranga (LIDER / ZASTEPCA / CZLONEK)
    private final Map<UUID, Ranga> czlonkowie = new LinkedHashMap<>();

    // UWAGA: statystyki PvP (zabojstwa/smierci) i ranking ELO NIE sa juz trzymane tutaj.
    // Wczesniej zyly w mapach lokalnych dla tego klanu, co powodowalo dwa bledy:
    //  1) gracz BEZ klanu nie mial gdzie ich zapisac - zero killi/smierci/rankingu mimo
    //     realnych zabojstw,
    //  2) gracz opuszczajacy/wyrzucany z klanu tracil je bezpowrotnie razem z wpisem
    //     czlonkostwa.
    // Statystyki gracza sa teraz NIEZALEZNE od przynaleznosci do klanu - trzyma je
    // KlanManager (cache) + tabela "gracze" w bazie (patrz KlanManager.getZabojstwaGracza(),
    // getSmierciGracza(), getRankingGracza() itd.). Ta klasa odpytuje o nie przez
    // KlanManager tam, gdzie potrzebuje sumy dla calego klanu (getSumaZabojstwKlanu() itp.).

    // Perki klanowe (system ulepszen /klan ulepszenia) - klucz to id perku
    // ({@link #PERK_DROP} / {@link #PERK_RANKING}), wartosc to procent, np. 12.5 = 12,5%.
    // Stare id (odpornosc/sila/zrecznosc/inteligencja) z czasow losowania perkow moga
    // jeszcze lezec w tabeli perki_klanu - nikt ich juz nie czyta, wiec sa nieszkodliwe.
    private final Map<String, Double> perki = new LinkedHashMap<>();

    /** Perk klanu: dodatkowy procent dropu z generatorow dla KAZDEGO czlonka klanu. */
    public static final String PERK_DROP = "drop";
    /** Perk klanu: dodatkowy procent do rankingu (ELO) zdobywanego za zabojstwo PvP. */
    public static final String PERK_RANKING = "ranking";
    /** Perk klanu: dodatkowe miejsca w klanie (1 = +1 osoba, max 5 = 10 osob). */
    public static final String PERK_LIMIT_GRACZY = "limit-graczy";

    public Klan(int id, String nazwa, String tag, UUID lider, boolean pvpWlaczone, int skarbiec, long dataZalozenia) {
        this.id = id;
        this.nazwa = nazwa;
        this.tag = tag;
        this.lider = lider;
        this.pvpWlaczone = pvpWlaczone;
        this.skarbiec = skarbiec;
        this.dataZalozenia = dataZalozenia;
    }

    public int getId() { return id; }

    public String getNazwa() { return nazwa; }
    public void setNazwa(String nazwa) { this.nazwa = nazwa; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public UUID getLider() { return lider; }
    public void setLider(UUID lider) { this.lider = lider; }

    public boolean isPvpWlaczone() { return pvpWlaczone; }
    public void setPvpWlaczone(boolean pvpWlaczone) { this.pvpWlaczone = pvpWlaczone; }

    public int getSkarbiec() { return skarbiec; }
    public void setSkarbiec(int skarbiec) { this.skarbiec = skarbiec; }
    public void dodajDoSkarbca(int ile) { this.skarbiec += ile; }
    public boolean odejmijZeSkarbca(int ile) {
        if (skarbiec < ile) return false;
        skarbiec -= ile;
        return true;
    }

    public long getDataZalozenia() { return dataZalozenia; }

    public int getRankingKlanu() { return rankingKlanu; }
    public void setRankingKlanu(int rankingKlanu) { this.rankingKlanu = rankingKlanu; }
    public void dodajDoRankinguKlanu(int ile) { this.rankingKlanu = Math.max(0, this.rankingKlanu + ile); }

    public Map<UUID, Ranga> getCzlonkowie() { return czlonkowie; }

    public int getLiczbaCzlonkow() { return czlonkowie.size(); }

    public Ranga getRanga(UUID uuid) { return czlonkowie.get(uuid); }

    public boolean isCzlonek(UUID uuid) { return czlonkowie.containsKey(uuid); }

    public boolean maUprawnieniaZarzadzania(UUID uuid) {
        Ranga r = czlonkowie.get(uuid);
        return r == Ranga.LIDER || r == Ranga.ZASTEPCA;
    }

    public static double kd(int zabojstwa, int smierci) {
        return smierci == 0 ? zabojstwa : (double) zabojstwa / smierci;
    }

    // ── Perki klanowe (ulepszenia) ──────────────────────────────────────────

    public double getPerk(String perkId) {
        return perki.getOrDefault(perkId, 0.0);
    }

    /** Ustawia wartosc procentowa perku (uzywane m.in. przy wczytywaniu z bazy). */
    public void setPerk(String perkId, double wartosc) {
        perki.put(perkId, wartosc);
    }

    /** Zwieksza perk o zadana wartosc procentowa i zwraca nowa wartosc. */
    public double dodajDoPerku(String perkId, double ile) {
        double nowa = getPerk(perkId) + ile;
        perki.put(perkId, nowa);
        return nowa;
    }

    public Map<String, Double> getPerki() {
        return perki;
    }

    /**
     * Dodatkowe miejsca z perku limit-graczy. Kazdy poziom = +1.
     * Stare wartosci-mnozniki (1.15, 1.3, ...) sa przeliczane na poziomy.
     */
    public static int dodatkoweMiejscaZWartosci(double v) {
        if (v <= 0) return 0;
        if (v > 1.0 && v < 2.0 && Math.abs(v - Math.rint(v)) > 1e-6) {
            return Math.max(0, (int) Math.round((v - 1.0) / 0.15));
        }
        return Math.min(5, Math.max(0, (int) Math.round(v)));
    }

    public int getDodatkoweMiejsca() {
        return dodatkoweMiejscaZWartosci(getPerk(PERK_LIMIT_GRACZY));
    }

    /** Limit czlonkow: baza + poziomy perku, nie wiecej niz absolutny max. */
    public int getMaxCzlonkow(int bazowyLimit) {
        return getMaxCzlonkow(bazowyLimit, 10);
    }

    public int getMaxCzlonkow(int bazowyLimit, int absolutnyMax) {
        int baza = Math.max(1, bazowyLimit);
        int cap = Math.max(baza, absolutnyMax);
        return Math.min(cap, baza + getDodatkoweMiejsca());
    }

    public enum Ranga {
        LIDER("&4Lider"),
        ZASTEPCA("&6Zastepca"),
        CZLONEK("&7Czlonek");

        private final String etykieta;

        Ranga(String etykieta) {
            this.etykieta = etykieta;
        }

        public String getEtykieta() {
            return etykieta;
        }
    }
}
