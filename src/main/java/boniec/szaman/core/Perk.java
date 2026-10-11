package boniec.szaman.core;

import boniec.szaman.SzamanPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Perki (artefakty) Szamana - wszystkie widoczne w JEDNYM glownym GUI /szaman.
 * <p>
 * Kazdy perk (poza BOOSTDROP) ma kilka POZIOMOW kupowanych za Fragmenty Galaktyki -
 * koszt i wartosc kazdego poziomu sa w szaman.yml -> data.&lt;klucz&gt;.&lt;poziom&gt;.
 * BOOSTDROP dziala inaczej: kazde ulepszenie DOLOSOWUJE procent do dropu z generatorow
 * (patrz {@link DropPerkManager}).
 */
public enum Perk {

    CONFINEMENT("confinement", true),
    HEALTH("health", true),
    STRENGTH("strength", true),
    VAMPIRISM("vampirism", true),
    WEAKENING("weakening", true),
    BLOCK("block", true),
    BACKSTAB("backstab", true),
    BOOSTDROP("boostdrop", false);

    private final String klucz;
    private final boolean poziomowy;

    Perk(String klucz, boolean poziomowy) {
        this.klucz = klucz;
        this.poziomowy = poziomowy;
    }

    public String getKlucz() {
        return klucz;
    }

    /** true = perk ma poziomy z tabeli "data" (wszystkie poza BOOSTDROP). */
    public boolean isPoziomowy() {
        return poziomowy;
    }

    /**
     * true = bonus z kostiumow/aur moze podbijac ten perk. BOOSTDROP jest wylaczony,
     * bo jego procent liczy sie wylacznie z losowan (+ klan) i nie ma "szansy/wartosci"
     * ktora kostium czy aura mogliby sensownie zwiekszyc.
     */
    public boolean isBonusowy() {
        return poziomowy;
    }

    private FileConfiguration cfg() {
        SzamanPlugin plugin = SzamanPlugin.get();
        return plugin == null ? null : plugin.getSzamanConfig();
    }

    // ── Wyglad / ustawienia z configu ────────────────────────────────────────

    public boolean isWlaczony() {
        FileConfiguration c = cfg();
        return c == null || c.getBoolean(klucz + ".enable", true);
    }

    public int getSlot() {
        FileConfiguration c = cfg();
        return c == null ? -1 : c.getInt(klucz + ".slot", -1);
    }

    public String getMaterial() {
        FileConfiguration c = cfg();
        return c == null ? "PAPER" : c.getString(klucz + ".material", "PAPER");
    }

    /** Pelna, kolorowa nazwa itema z GUI (displayname z configu). */
    public String getDisplayName() {
        FileConfiguration c = cfg();
        return c == null ? klucz : c.getString(klucz + ".displayname", klucz);
    }

    public List<String> getLore() {
        FileConfiguration c = cfg();
        return c == null ? Collections.emptyList() : c.getStringList(klucz + ".lore");
    }

    public String getFormatAktywny() {
        FileConfiguration c = cfg();
        return c == null ? "" : c.getString(klucz + ".format.active", "");
    }

    public String getFormatNieaktywny() {
        FileConfiguration c = cfg();
        return c == null ? "" : c.getString(klucz + ".format.no-active", "");
    }

    /** Krotka nazwa (np. "Sila") - uzywana w lore aur i komendach admina. */
    public String getNazwa() {
        FileConfiguration c = cfg();
        return c == null ? klucz : c.getString("perki-nazwy." + klucz + ".name", klucz);
    }

    /** Kolor hex perka ("#RRGGBB") do lore aur. */
    public String getHex() {
        FileConfiguration c = cfg();
        return c == null ? "#FFFFFF" : c.getString("perki-nazwy." + klucz + ".hex", "#FFFFFF");
    }

    // ── Poziomy (data.<klucz>.<poziom>.cost / value) ─────────────────────────

    private ConfigurationSection sekcjaDanych() {
        FileConfiguration c = cfg();
        return c == null ? null : c.getConfigurationSection("data." + klucz);
    }

    /** Najwyzszy poziom zdefiniowany w configu, obciety limitem /aszaman max (0 dla perku bez poziomow). */
    public int getMaxPoziom() {
        ConfigurationSection s = sekcjaDanych();
        if (s == null) return 0;
        int max = 0;
        for (String k : s.getKeys(false)) {
            try {
                max = Math.max(max, Integer.parseInt(k.trim()));
            } catch (NumberFormatException ignored) {
                // nieznany klucz - pomijamy
            }
        }
        FileConfiguration c = cfg();
        int cap = c == null ? 0 : c.getInt("limity.max-poziom", 0);
        if (cap > 0 && max > 0) {
            max = Math.min(max, cap);
        }
        return max;
    }

    /** Koszt (we Fragmentach Galaktyki) wykupienia danego poziomu; -1 gdy poziom nie istnieje. */
    public int getKoszt(int poziom) {
        ConfigurationSection s = sekcjaDanych();
        if (s == null || !s.contains(poziom + ".cost")) return -1;
        return s.getInt(poziom + ".cost", -1);
    }

    /** Wartosc danego poziomu jako liczba (jednostka - %, sekundy lub serca - zalezy od perku). */
    public double getWartosc(int poziom) {
        if (poziom <= 0) return 0.0;
        ConfigurationSection s = sekcjaDanych();
        if (s == null) return 0.0;
        return parsujLiczbe(s.get(poziom + ".value"));
    }

    /**
     * Wartosc poziomu jako tekst do GUI/wiadomosci - sama liczba, BEZ jednostki
     * (jednostke - %, sekundy, serce - dopisuje juz format w configu, np. "{value} sekund").
     */
    public String getWartoscTekst(int poziom) {
        return formatLiczby(getWartosc(poziom));
    }

    static double parsujLiczbe(Object surowa) {
        if (surowa == null) return 0.0;
        if (surowa instanceof Number n) return n.doubleValue();
        // np. "5%", "2s", "1,5" -> zostawiamy tylko cyfry, kropke i minus
        String czysty = String.valueOf(surowa).replace(',', '.').replaceAll("[^0-9.\\-]", "");
        if (czysty.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(czysty);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static String formatLiczby(double wartosc) {
        if (wartosc == Math.floor(wartosc) && !Double.isInfinite(wartosc)) {
            return String.valueOf((long) wartosc);
        }
        String tekst = String.format(Locale.ROOT, "%.2f", wartosc);
        // ucinamy zbedne zera: 0.50 -> 0.5
        if (tekst.contains(".")) {
            tekst = tekst.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return tekst;
    }

    // ── Wyszukiwanie ─────────────────────────────────────────────────────────

    public static Perk zKlucza(String klucz) {
        if (klucz == null) return null;
        for (Perk p : values()) {
            if (p.klucz.equalsIgnoreCase(klucz)) return p;
        }
        return null;
    }

    /**
     * Jak {@link #zKlucza}, ale rozumie tez STARE nazwy perkow (z czasow kamieni i
     * drzewka perkow) - dzieki temu zapisane aury i wpisy "bonus-kostiumowy" w
     * szaman.yml dalej dzialaja tam, gdzie stary perk ma nowy odpowiednik:
     * obrazenia -> strength, zycia -> health, lifesteal -> vampirism, spetanie -> confinement,
     * glowing -> weakening, blokada -> block.
     * Perki bez odpowiednika (extrahit, ochrona, wytrzymalosc, ...) zwracaja null.
     */
    public static Perk parsuj(String nazwa) {
        if (nazwa == null) return null;
        String n = nazwa.trim().toLowerCase(Locale.ROOT);
        Perk nowy = zKlucza(n);
        if (nowy != null) return nowy;
        // nazwy enuma z poprzedniej wersji (np. SILA_OBRAZENIA) -> sam podklucz po "_"
        int idx = n.indexOf('_');
        String koncowka = idx >= 0 ? n.substring(idx + 1) : n;
        return switch (koncowka) {
            case "obrazenia" -> STRENGTH;
            case "zycia" -> HEALTH;
            case "lifesteal" -> VAMPIRISM;
            case "spetanie" -> CONFINEMENT;
            case "glowing" -> WEAKENING;
            case "blokada" -> BLOCK;
            case "skrytobojca" -> BACKSTAB;
            default -> null;
        };
    }

    /** Perki, ktore moga dostawac bonus z kostiumow/aur (wszystkie poza BOOSTDROP). */
    public static List<Perk> bonusowe() {
        List<Perk> lista = new ArrayList<>();
        for (Perk p : values()) {
            if (p.isBonusowy()) lista.add(p);
        }
        return lista;
    }
}
