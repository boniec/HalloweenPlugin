package boniec.klany.util;

import boniec.klany.Main;
import boniec.klany.core.Klan;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Centralny punkt do wysylania:
 *  - broadcastow (globalnych wiadomosci do calego serwera, wieloliniowych),
 *  - title/subtitle (np. do wszystkich obecnych czlonkow klanu),
 *  - dzwiekow towarzyszacych zdarzeniom klanowym (utworzenie, usuniecie, dolaczenie,
 *    opuszczenie, wyrzucenie, zaproszenie...).
 * <p>
 * Wszystko w 100% konfigurowalne z klany-config.yml - sekcje "broadcasty", "tytuly" i "dzwieki".
 * Kazdy komunikat (broadcast) moze miec dowolna liczbe linii - w configu jest to zwykla
 * lista YAML (List&lt;String&gt;), np.:
 * <pre>
 * broadcasty:
 *   dolaczenie:
 *     wlaczony: true
 *     linie:
 *       - "&8[&bKLAN&8] &e%gracz%"
 *       - "&adolaczyl do klanu &e%klan%&a!"
 *     dzwiek: "ENTITY_PLAYER_LEVELUP"
 * </pre>
 */
public final class KlanNotify {

    private KlanNotify() {}

    // ── Broadcast (do calego serwera) ───────────────────────────────────────

    /**
     * Wysyla do WSZYSTKICH graczy na serwerze wieloliniowy komunikat zdefiniowany
     * w "broadcasty.&lt;klucz&gt;.linie", jesli "broadcasty.&lt;klucz&gt;.wlaczony" jest true (domyslnie true).
     * Placeholdery dzialaja tak samo jak w Msg.send (pary "%klucz%", wartosc).
     * Odgrywa tez dzwiek z "broadcasty.&lt;klucz&gt;.dzwiek", jesli podany, kazdemu odbiorcy.
     */
    public static void broadcast(String klucz, Object... placeholdery) {
        FileConfiguration cfg = Main.get().getConfig();
        String sciezka = "broadcasty." + klucz;

        if (!cfg.getBoolean(sciezka + ".wlaczony", true)) {
            return;
        }

        List<String> linie = cfg.getStringList(sciezka + ".linie");
        if (linie.isEmpty()) {
            return;
        }

        java.util.List<Player> odbiorcy = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (boniec.ustawienia.UstawieniaManager.on(p, boniec.ustawienia.UstawieniaKlucz.KLANY)) {
                odbiorcy.add(p);
            }
        }
        for (String linia : linie) {
            String tekst = Msg.color(zastapPlaceholdery(linia, placeholdery));
            Bukkit.getConsoleSender().sendMessage(tekst);
            for (Player p : odbiorcy) p.sendMessage(tekst);
        }

        odtworzDzwiek(sciezka + ".dzwiek", odbiorcy);
    }

    // ── Title / Subtitle ─────────────────────────────────────────────────────

    /**
     * Wysyla title+subtitle (z sekcji "tytuly.&lt;klucz&gt;") do podanych graczy,
     * jesli "tytuly.&lt;klucz&gt;.wlaczony" jest true (domyslnie true). Odgrywa tez
     * skonfigurowany dzwiek kazdemu odbiorcy z osobna.
     */
    public static void title(String klucz, Iterable<? extends Player> odbiorcy, Object... placeholdery) {
        FileConfiguration cfg = Main.get().getConfig();
        String sciezka = "tytuly." + klucz;

        if (!cfg.getBoolean(sciezka + ".wlaczony", true)) {
            return;
        }

        String title = zastapPlaceholdery(cfg.getString(sciezka + ".title", ""), placeholdery);
        String subtitle = zastapPlaceholdery(cfg.getString(sciezka + ".subtitle", ""), placeholdery);

        int fadeIn = cfg.getInt(sciezka + ".fade-in", 10);
        int stay = cfg.getInt(sciezka + ".stay", 40);
        int fadeOut = cfg.getInt(sciezka + ".fade-out", 10);

        String dzwiekSciezka = sciezka + ".dzwiek";

        for (Player gracz : odbiorcy) {
            gracz.sendTitle(Msg.color(title), Msg.color(subtitle), fadeIn, stay, fadeOut);
            odtworzDzwiek(dzwiekSciezka, gracz);
        }
    }

    /** Wysyla title/subtitle do wszystkich obecnie zalogowanych czlonkow danego klanu. */
    public static void titleDoKlanu(String klucz, Klan klan, Object... placeholdery) {
        List<? extends Player> czlonkowieOnline = Bukkit.getOnlinePlayers().stream()
                .filter(p -> klan.isCzlonek(p.getUniqueId()))
                .toList();
        title(klucz, czlonkowieOnline, placeholdery);
    }

    /** Jak {@link #titleDoKlanu(String, Klan, Object...)}, ale pomija jednego gracza (np. nowo dolaczonego). */
    public static void titleDoKlanuOprocz(String klucz, Klan klan, java.util.UUID pominiety, Object... placeholdery) {
        List<? extends Player> czlonkowieOnline = Bukkit.getOnlinePlayers().stream()
                .filter(p -> klan.isCzlonek(p.getUniqueId()) && !p.getUniqueId().equals(pominiety))
                .toList();
        title(klucz, czlonkowieOnline, placeholdery);
    }

    // ── Dzwieki ──────────────────────────────────────────────────────────────

    /**
     * Odtwarza dzwiek skonfigurowany pod "dzwieki.&lt;klucz&gt;.dzwiek" (+ .glosnosc, .pitch)
     * dla jednego gracza. Uzywane np. przy akcjach, ktore nie maja wlasnego broadcastu/title
     * (np. otrzymanie zaproszenia, wplata do skarbca).
     */
    public static void dzwiek(String klucz, Player odbiorca) {
        odtworzDzwiek("dzwieki." + klucz + ".dzwiek", odbiorca);
    }

    public static void dzwiek(String klucz, Iterable<? extends Player> odbiorcy) {
        odtworzDzwiek("dzwieki." + klucz + ".dzwiek", odbiorcy);
    }

    // ── Wewnetrzne ───────────────────────────────────────────────────────────

    private static void odtworzDzwiek(String sciezkaDoDzwieku, Player odbiorca) {
        odtworzDzwiek(sciezkaDoDzwieku, List.of(odbiorca));
    }

    private static void odtworzDzwiek(String sciezkaDoDzwieku, Iterable<? extends Player> odbiorcy) {
        FileConfiguration cfg = Main.get().getConfig();
        String nazwa = cfg.getString(sciezkaDoDzwieku);
        if (nazwa == null || nazwa.isBlank() || nazwa.equalsIgnoreCase("NONE")) {
            return;
        }

        Sound dzwiek;
        try {
            NamespacedKey nsKey = NamespacedKey.minecraft(nazwa.trim().toLowerCase(Locale.ROOT).replace('_', '.'));
            dzwiek = org.bukkit.Registry.SOUNDS.get(nsKey);
            if (dzwiek == null) {
                Bukkit.getLogger().warning("[Klany] Nieznana nazwa dzwieku w configu: '" + nazwa + "' (sciezka: " + sciezkaDoDzwieku + ")");
                return;
            }
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[Klany] Niepoprawna nazwa dzwieku w configu: '" + nazwa + "' (sciezka: " + sciezkaDoDzwieku + ")");
            return;
        }

        // glosnosc/pitch sa opcjonalne, brane z tego samego "rodzica" co "dzwiek",
        // np. "broadcasty.dolaczenie.glosnosc" i "broadcasty.dolaczenie.pitch"
        String rodzic = sciezkaDoDzwieku.substring(0, sciezkaDoDzwieku.lastIndexOf('.'));
        float glosnosc = (float) cfg.getDouble(rodzic + ".glosnosc", 1.0);
        float pitch = (float) cfg.getDouble(rodzic + ".pitch", 1.0);

        for (Player gracz : odbiorcy) {
            gracz.playSound(gracz.getLocation(), dzwiek, glosnosc, pitch);
        }
    }

    private static String zastapPlaceholdery(String tekst, Object... placeholdery) {
        if (tekst == null) return "";
        for (int i = 0; i + 1 < placeholdery.length; i += 2) {
            String klucz = String.valueOf(placeholdery[i]);
            String wartosc = String.valueOf(placeholdery[i + 1]);
            tekst = tekst.replace(klucz, wartosc);
        }
        return tekst;
    }
}
