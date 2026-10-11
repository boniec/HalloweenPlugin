package boniec.eventy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Godziny startu eventow. Jedyne zrodlo: eventy.yml (sekcja godziny, a jak jej brak —
 * godziny z lore /eventy). Czas zawsze Europe/Warsaw. Harmonogramy w koth.yml,
 * eventodlamki.yml, meteorites.yml, zeus/minotaur/goblin.yml sa ignorowane.
 */
public final class EventHarmonogram {

    public static final ZoneId STREFA = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern GODZINA = Pattern.compile("(\\d{1,2}):(\\d{2})");

    public static final List<LocalTime> KOTH = new CopyOnWriteArrayList<>(Arrays.asList(
            LocalTime.of(15, 0), LocalTime.of(18, 0), LocalTime.of(21, 0)));
    public static final List<LocalTime> SERCE = new CopyOnWriteArrayList<>(Arrays.asList(
            LocalTime.of(16, 0), LocalTime.of(19, 0)));
    public static final List<LocalTime> METEORYTY = new CopyOnWriteArrayList<>(Arrays.asList(
            LocalTime.of(14, 0), LocalTime.of(20, 0)));
    public static final List<LocalTime> MINOTAUR = new CopyOnWriteArrayList<>(Arrays.asList(
            LocalTime.of(12, 0), LocalTime.of(20, 0)));
    public static final List<LocalTime> ZEUS = new CopyOnWriteArrayList<>(Arrays.asList(
            LocalTime.of(13, 0), LocalTime.of(21, 0)));
    public static final List<LocalTime> GOBLIN = new CopyOnWriteArrayList<>(Arrays.asList(
            LocalTime.of(17, 0), LocalTime.of(22, 0)));
    public static final List<LocalTime> SZAL = new CopyOnWriteArrayList<>(Arrays.asList(
            LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(22, 0)));

    private EventHarmonogram() {
    }

    public static ZoneId strefa() {
        return STREFA;
    }

    public static LocalDateTime teraz() {
        return LocalDateTime.now(STREFA);
    }

    public static void wczytaj(JavaPlugin plugin) {
        File plik = new File(plugin.getDataFolder(), "eventy.yml");
        FileConfiguration cfg;
        if (plik.exists()) {
            cfg = YamlConfiguration.loadConfiguration(plik);
        } else {
            InputStream in = plugin.getResource("eventy.yml");
            if (in == null) return;
            cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        wczytaj(cfg);
    }

    public static void wczytaj(FileConfiguration cfg) {
        if (cfg == null) return;
        ustaw(cfg, "koth", KOTH, LocalTime.of(15, 0), LocalTime.of(18, 0), LocalTime.of(21, 0));
        ustaw(cfg, "serce", SERCE, LocalTime.of(16, 0), LocalTime.of(19, 0));
        ustaw(cfg, "meteoryty", METEORYTY, LocalTime.of(14, 0), LocalTime.of(20, 0));
        ustaw(cfg, "minotaur", MINOTAUR, LocalTime.of(12, 0), LocalTime.of(20, 0));
        ustaw(cfg, "zeus", ZEUS, LocalTime.of(13, 0), LocalTime.of(21, 0));
        ustaw(cfg, "goblin", GOBLIN, LocalTime.of(17, 0), LocalTime.of(22, 0));
        ustaw(cfg, "szal", SZAL, LocalTime.of(16, 0), LocalTime.of(19, 0), LocalTime.of(22, 0));
    }

    @SafeVarargs
    private static void ustaw(FileConfiguration cfg, String klucz, List<LocalTime> cel, LocalTime... fallback) {
        List<LocalTime> w = new ArrayList<>();
        for (String s : cfg.getStringList("eventy." + klucz + ".godziny")) {
            LocalTime t = parsuj(s);
            if (t != null) w.add(t);
        }
        if (w.isEmpty()) {
            for (String linia : cfg.getStringList("eventy." + klucz + ".lore")) {
                Matcher m = GODZINA.matcher(linia == null ? "" : linia);
                while (m.find()) {
                    LocalTime t = parsuj(m.group(1) + ":" + m.group(2));
                    if (t != null) w.add(t);
                }
            }
        }
        if (w.isEmpty()) {
            w.addAll(Arrays.asList(fallback));
        }
        w.sort(LocalTime::compareTo);
        cel.clear();
        cel.addAll(w);
    }

    private static LocalTime parsuj(String surowy) {
        if (surowy == null) return null;
        String s = surowy.trim();
        Matcher m = GODZINA.matcher(s);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        if (h < 0 || h > 23 || min < 0 || min > 59) return null;
        return LocalTime.of(h, min);
    }

    /** Najblizszy start, wliczajac biezaca minute (restart o 13:00 odpala event 13:00). */
    public static LocalDateTime nastepny(List<LocalTime> godziny) {
        LocalDateTime teraz = teraz();
        return nastepnyOd(godziny, teraz.withSecond(0).withNano(0));
    }

    public static LocalDateTime nastepnyPo(List<LocalTime> godziny) {
        return nastepnyPo(godziny, teraz());
    }

    public static LocalDateTime nastepnyPo(List<LocalTime> godziny, LocalDateTime po) {
        if (po == null) po = teraz();
        return nastepnyOd(godziny, po.plusNanos(1));
    }

    private static LocalDateTime nastepnyOd(List<LocalTime> godziny, LocalDateTime od) {
        if (godziny == null || godziny.isEmpty() || od == null) return null;
        LocalDateTime najlepszy = null;
        for (int d = 0; d <= 1; d++) {
            for (int i = 0; i < godziny.size(); i++) {
                LocalDateTime kandydat = LocalDateTime.of(od.toLocalDate().plusDays(d), godziny.get(i));
                if (!kandydat.isBefore(od) && (najlepszy == null || kandydat.isBefore(najlepszy))) {
                    najlepszy = kandydat;
                }
            }
        }
        return najlepszy;
    }

    public static long epochNastepnego(List<LocalTime> godziny) {
        return doEpoch(nastepny(godziny));
    }

    public static long epochNastepnegoPo(List<LocalTime> godziny) {
        return doEpoch(nastepnyPo(godziny));
    }

    private static long doEpoch(LocalDateTime n) {
        if (n == null) return -1L;
        return n.atZone(STREFA).toInstant().toEpochMilli();
    }

    public static long millisDoNastepnego(List<LocalTime> godziny) {
        LocalDateTime n = nastepny(godziny);
        if (n == null) return -1L;
        return Math.max(0L, Duration.between(teraz(), n).toMillis());
    }

    public static int sekundyDoNastepnego(List<LocalTime> godziny) {
        return sekundyDo(nastepnyPo(godziny));
    }

    public static int sekundyDoNastepnegoWlacznie(List<LocalTime> godziny) {
        return sekundyDo(nastepny(godziny));
    }

    private static int sekundyDo(LocalDateTime n) {
        if (n == null) return 1;
        long ms = Duration.between(teraz(), n).toMillis();
        return (int) Math.max(1L, ms / 1000L);
    }

    public static String opis(List<LocalTime> godziny) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < godziny.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(godziny.get(i).format(HH_MM));
        }
        return sb.toString();
    }
}
