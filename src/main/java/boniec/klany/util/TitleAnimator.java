package boniec.klany.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animowany title/subtitle wysylany WYLACZNIE przez standardowe API Bukkit/Paper
 * (Player#sendTitle). Zadnego NMS/reflection - dzieki temu dziala identycznie
 * na kazdej wersji serwera (1.13 -> 1.21+, Spigot i Paper), bez ryzyka ze
 * "cichy" fallback zepsuje animacje po update serwera.
 * <p>
 * Efekt:
 *  1) Title pojawia sie od razu (w calosci).
 *  2) Po 200ms (4 ticki) startuje animacja subtitle: tekst pojawia sie od
 *     srodka na boki w klatkach co 3 widoczne znaki (np. dla "DDDDD DDDDD DDDDD"
 *     kolejno pokazywane jest coraz wiecej znakow z centrum na zewnatrz -
 *     wyglada to jak: "...DDD..." -> "..DDDDD.." -> "DDDDD DDDDD" -> caly tekst),
 *     rozlozonych rownomiernie na ~450ms (9 tickow).
 * <p>
 * Kazde wywolanie Player#sendTitle z fadeIn=0 aktualizuje aktualnie wyswietlany
 * title/subtitle bez migania (klient tylko podmienia tekst), wiec mozemy
 * "doslac" kolejne klatki subtitle jako kolejne wywolania sendTitle z tym
 * samym (juz widocznym) title.
 */
public final class TitleAnimator {

    private TitleAnimator() {}

    // ── Public API ──────────────────────────────────────────────────────────

    public static void animate(Player gracz, String title, String subtitle) {
        animate(gracz, title, subtitle, 5, 40, 10);
    }

    public static void animate(Player gracz, String title, String subtitle,
                               int fadeIn, int stay, int fadeOut) {
        String coloredTitle = Msg.color(title);
        String coloredSubtitle = Msg.color(subtitle);

        playAdvancementSound(gracz);

        org.bukkit.plugin.Plugin plugin = findPlugin();
        if (plugin == null || !plugin.isEnabled()) {
            gracz.sendTitle(coloredTitle, coloredSubtitle, fadeIn, stay, fadeOut);
            return;
        }

        // Krok 1: title od razu, subtitle na razie puste. stay wydluzony o czas
        // trwania animacji (~9 tickow), zeby caly title/subtitle nie zniknal
        // w trakcie "rozjezdzania sie" tekstu.
        gracz.sendTitle(coloredTitle, "", fadeIn, stay + 9, fadeOut);

        // Krok 2: po 200ms (4 ticki) startuje animacja subtitle
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> animateSubtitle(gracz, coloredTitle, coloredSubtitle, stay, fadeOut),
                4L);
    }

    // ── Animacja subtitle: "rozjezdzanie sie" od srodka na boki ─────────────

    private static void animateSubtitle(Player gracz, String title, String subtitle,
                                        int stay, int fadeOut) {
        final List<Seg> segs = parseColored(subtitle);
        int tv = 0;
        for (Seg s : segs) tv += s.text.length();
        final int totalVisible = tv;

        if (totalVisible == 0) {
            gracz.sendTitle(title, subtitle, 0, stay, fadeOut);
            return;
        }

        // Klatki co KROK_ZNAKOW (3) widocznych znakow, od srodka na zewnatrz:
        // np. dla tekstu o dlugosci 15 -> pokazujemy kolejno 3, 6, 9, 12, 15 znakow,
        // zawsze wycentrowane. Klatki rozlozone rownomiernie na CZAS_TICKOW (9 tickow, ~450ms).
        final int KROK_ZNAKOW = 3;
        List<Integer> showCounts = new ArrayList<>();
        for (int show = Math.min(KROK_ZNAKOW, totalVisible); show < totalVisible; show += KROK_ZNAKOW) {
            showCounts.add(show);
        }
        showCounts.add(totalVisible); // ostatnia klatka = caly tekst

        final int liczbaKlatek = showCounts.size();
        final long CZAS_TICKOW = 9L;

        org.bukkit.plugin.Plugin plugin = findPlugin();
        if (plugin == null) {
            gracz.sendTitle(title, subtitle, 0, stay, fadeOut);
            return;
        }

        for (int i = 0; i < liczbaKlatek; i++) {
            final int show = showCounts.get(i);
            final boolean ostatnia = (i == liczbaKlatek - 1);
            // Rownomierne rozlozenie klatek na CZAS_TICKOW tickow (klatka 0 = natychmiast).
            final long delay = liczbaKlatek <= 1 ? 0L
                    : Math.round(CZAS_TICKOW * (double) i / (liczbaKlatek - 1));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!gracz.isOnline()) return;
                int startVis = (totalVisible - show) / 2;
                int endVis = startVis + show;
                String portion = substringVisible(segs, startVis, endVis);
                if (portion.isEmpty()) portion = " ";

                int rem = ostatnia ? stay : Math.max(1, stay + 9 - (int) delay);
                // fadeIn=0 -> Paper/Bukkit tylko podmienia tekst bez ponownego "wjazdu" tytulu
                gracz.sendTitle(title, portion, 0, rem, fadeOut);
            }, delay);
        }
    }

    // ── Color parsing (dziala na juz sformatowanym tekscie, z kodami sekcji \u00A7) ──

    private static class Seg {
        final String color;
        final String text;
        Seg(String color, String text) { this.color = color; this.text = text; }
    }

    private static List<Seg> parseColored(String raw) {
        List<Seg> segs = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            String currentColor = "";
            while (i + 1 < raw.length() && raw.charAt(i) == '\u00A7') {
                currentColor += raw.substring(i, i + 2);
                i += 2;
            }
            int start = i;
            while (i < raw.length()) {
                if (raw.charAt(i) == '\u00A7' && i + 1 < raw.length()) break;
                i++;
            }
            String text = raw.substring(start, i);
            if (!text.isEmpty()) segs.add(new Seg(currentColor, text));
        }
        return segs;
    }

    private static String substringVisible(List<Seg> segs, int from, int to) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (Seg seg : segs) {
            int segStart = pos;
            int segEnd = pos + seg.text.length();
            if (segEnd <= from || segStart >= to) { pos = segEnd; continue; }
            int localFrom = Math.max(0, from - segStart);
            int localTo = Math.min(seg.text.length(), to - segStart);
            if (!seg.color.isEmpty()) sb.append(seg.color);
            sb.append(seg.text, localFrom, localTo);
            pos = segEnd;
        }
        return sb.toString();
    }

    // ── Sound ───────────────────────────────────────────────────────────────

    private static org.bukkit.plugin.Plugin findPlugin() {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Inventory");
        if (p != null && p.isEnabled()) return p;
        for (org.bukkit.plugin.Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (pl.isEnabled()) return pl;
        }
        return null;
    }

    private static void playAdvancementSound(Player gracz) {
        try {
            String dzwiekNazwa = "UI_TOAST_CHALLENGE_COMPLETE";
            float glosnosc = 1.0f;
            float pitch = 1.0f;
            try {
                boniec.klany.Main m = boniec.klany.Main.get();
                if (m != null) {
                    var cfg = m.getConfig();
                    dzwiekNazwa = cfg.getString("tytuly.zabojstwo.dzwiek", dzwiekNazwa);
                    glosnosc = (float) cfg.getDouble("tytuly.zabojstwo.glosnosc", glosnosc);
                    pitch = (float) cfg.getDouble("tytuly.zabojstwo.pitch", pitch);
                }
            } catch (Exception ignored) {}
            if (dzwiekNazwa == null || dzwiekNazwa.isBlank() || dzwiekNazwa.equalsIgnoreCase("NONE")) return;

            Sound dzwiek = resolveSound(dzwiekNazwa.trim());
            if (dzwiek == null) return;
            gracz.playSound(gracz.getLocation(), dzwiek, glosnosc, pitch);
        } catch (Exception ignored) {}
    }

    /**
     * Zamienia nazwe dzwieku z configu (np. "UI_TOAST_CHALLENGE_COMPLETE" - format
     * enuma Bukkit Sound, LUB "ui.toast.challenge_complete" - format klucza
     * namespaced) na obiekt Sound.
     * <p>
     * UWAGA: NIE uzywamy tu Sound.valueOf(String). W nowszych wersjach Paper/Bukkit
     * (1.21+) Sound przestal byc enumem (stal sie interfejsem opartym o Registry),
     * wiec metoda Sound.valueOf zniknela z realnego API. Jesli plugin zostanie
     * skompilowany przeciwko innej wersji API niz ta, na ktorej dziala serwer,
     * bezposrednie wywolanie Sound.valueOf(...) moze rzucic
     * IncompatibleClassChangeError zamiast zwyklego bledu kompilacji/braku metody -
     * dlatego rozwiazujemy dzwiek WYLACZNIE przez Registry, co dziala niezaleznie
     * od tego, czy Sound jest enumem czy interfejsem.
     */
    private static Sound resolveSound(String nazwa) {
        // 1) Potraktuj wejscie jako namespaced key w formacie "ui.toast.challenge_complete"
        //    (z ewentualnym prefixem "minecraft:").
        Sound sound = lookupInRegistry(nazwa);
        if (sound != null) return sound;

        // 2) Fallback - wejscie moze byc w formacie enuma Bukkit
        //    "UI_TOAST_CHALLENGE_COMPLETE" - zamien na klucz namespaced
        //    (podkreslenia -> kropki, lowercase) i sprobuj ponownie w rejestrze.
        String asKey = nazwa.toLowerCase(Locale.ROOT).replace('_', '.').replace(' ', '.');
        return lookupInRegistry(asKey);
    }

    private static Sound lookupInRegistry(String nazwa) {
        try {
            String key = nazwa.toLowerCase(Locale.ROOT);
            if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
            NamespacedKey nsKey = NamespacedKey.minecraft(key);
            return org.bukkit.Registry.SOUNDS.get(nsKey);
        } catch (Exception ignored) {
            return null;
        }
    }
}
