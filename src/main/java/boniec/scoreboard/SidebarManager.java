package boniec.scoreboard;

import boniec.klany.util.Msg;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wlasny scoreboard (sidebar) - w pelni konfigurowalny w scoreboard.yml.
 * <p>
 * WSPOLPRACA Z NametagKolorListener: ten listener daje kazdemu graczowi WLASNY Scoreboard
 * (z team'ami klan_swoi/klan_obcy do kolorow nametagow). My dokladamy do TEGO SAMEGO
 * scoreboardu gracza tylko swoj objective ("gzsidebar") i team'y "gzsb_<N>" - niczego
 * innego nie ruszamy. Jesli gracz ma jeszcze glowny scoreboard, dajemy mu nowy
 * (NametagKolorListener je potem normalnie uzupelni, bo sprawdza tylko "czy to main").
 */
public class SidebarManager implements Listener {

    private static final String OBJ = "gzsidebar";
    private static final String TEAM = "gzsb_";
    private static final int MAX_LINII = 15;
    private static final int LIMIT = 64; // limit prefixu/sufiksu team'u na starszych wersjach

    private final JavaPlugin plugin;
    private final File configFile;
    private final File dataFile;
    private FileConfiguration cfg;
    /**
     * Pozwala innemu systemowi (np. KOTH) chwilowo podmienic tresc scoreboardu WSZYSTKIM graczom.
     * Dopoki aktywne() zwraca true, zamiast zwyklego scoreboardu rysowane sa linie z linie(p).
     */
    public interface Nadpisanie {
        boolean aktywne();
        String tytul();
        List<String> linie(Player p);
    }

    private volatile Nadpisanie nadpisanie;

    public void setNadpisanie(Nadpisanie n) {
        this.nadpisanie = n;
    }

    // Dodatkowe nadpisania (np. Serce Kosmosu). Pierwszenstwo: glowne (KOTH), potem w kolejnosci dodania.
    private final java.util.List<Nadpisanie> dodatkoweNadpisania = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void dodajNadpisanie(Nadpisanie n) {
        if (n != null && !dodatkoweNadpisania.contains(n)) dodatkoweNadpisania.add(n);
    }

    public void usunNadpisanie(Nadpisanie n) {
        dodatkoweNadpisania.remove(n);
    }

    private Nadpisanie aktywneNadpisanie() {
        Nadpisanie glowne = nadpisanie;
        if (glowne != null && glowne.aktywne()) return glowne;
        for (Nadpisanie n : dodatkoweNadpisania) {
            if (n.aktywne()) return n;
        }
        return null;
    }

    private final Map<UUID, Stan> stany = new HashMap<>();
    private final Set<UUID> wylaczeni = new HashSet<>();
    private BukkitTask task;

    // zcache'owane ustawienia z configu
    private List<String> tytuly = new ArrayList<>();
    private long tytulKlatkaTicks = 10L;
    private List<String> linie = new ArrayList<>();
    private long interwal = 20L;
    private boolean ukryjNumery = true;
    private boolean wlaczonyCfg = true;
    private boolean papi = false;
    private Set<String> wylaczoneSwiaty = new HashSet<>();
    private final Map<String, String> aliasySwiatow = new HashMap<>();

    private static final class Stan {
        Scoreboard board;
        Objective obj;
        int liczbaLinii;
        String[] prefixy;
        String[] sufiksy;
        String tytul;
    }

    public SidebarManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "scoreboard.yml");
        this.dataFile = new File(plugin.getDataFolder(), "scoreboard-wylaczeni.yml");
    }

    // ── Cykl zycia ────────────────────────────────────────────────────────

    public void start() {
        wczytaj();
        wczytajWylaczonych();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        uruchomZadanie();
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) usun(p);
        stany.clear();
    }

    public void zapiszPrzyWylaczaniu() {
        zapiszWylaczonych();
    }

    /** /scoreboard reload */
    public void przeladuj() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) usun(p);
        stany.clear();
        wczytaj();
        uruchomZadanie();
    }

    private void uruchomZadanie() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, interwal);
    }

    private void wczytaj() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!configFile.exists()) plugin.saveResource("scoreboard.yml", false);
        cfg = YamlConfiguration.loadConfiguration(configFile);
        uzupelnijZJara();

        tytuly = new ArrayList<>();
        if (cfg.isList("tytul")) tytuly.addAll(cfg.getStringList("tytul"));
        else tytuly.add(cfg.getString("tytul", "&fScoreboard"));
        if (tytuly.isEmpty()) tytuly.add("&fScoreboard");

        tytulKlatkaTicks = Math.max(1L, cfg.getLong("tytul-klatka-tickow", 10L));
        interwal = Math.max(1L, cfg.getLong("interwal-tickow", 20L));
        ukryjNumery = cfg.getBoolean("ukryj-numery", true);
        wlaczonyCfg = cfg.getBoolean("wlaczony", true);
        papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        linie = new ArrayList<>(cfg.getStringList("linie"));
        if (linie.size() > MAX_LINII) {
            plugin.getLogger().warning("[Scoreboard] Maksymalnie " + MAX_LINII + " linii - nadmiarowe zostaly pominiete.");
            linie = new ArrayList<>(linie.subList(0, MAX_LINII));
        }

        wylaczoneSwiaty = new HashSet<>(cfg.getStringList("wylaczone-swiaty"));
        aliasySwiatow.clear();
        ConfigurationSection swiaty = cfg.getConfigurationSection("swiaty");
        if (swiaty != null) {
            for (String klucz : swiaty.getKeys(false)) {
                String alias = swiaty.getString(klucz);
                if (alias != null && !alias.isBlank()) aliasySwiatow.put(klucz.toLowerCase(), alias);
            }
        }
    }

    private void uzupelnijZJara() {
        java.io.InputStream in = plugin.getResource("scoreboard.yml");
        if (in == null) return;
        FileConfiguration jar = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        int wersjaJar = jar.getInt("wersja", 1);
        boolean nadpisz = cfg.getInt("wersja", 0) < wersjaJar;
        boolean zmieniono = false;
        for (String klucz : jar.getKeys(true)) {
            if (jar.isConfigurationSection(klucz)) continue;
            boolean brak = !cfg.contains(klucz);
            boolean layout = nadpisz && (klucz.equals("tytul") || klucz.equals("linie") || klucz.equals("swiaty")
                    || klucz.startsWith("swiaty.") || klucz.equals("wersja"));
            if (brak || layout) {
                cfg.set(klucz, jar.get(klucz));
                zmieniono = true;
            }
        }
        if (nadpisz) {
            cfg.set("wersja", wersjaJar);
            zmieniono = true;
        }
        if (zmieniono) {
            try {
                cfg.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().warning("[Scoreboard] Nie zapisano scoreboard.yml: " + e.getMessage());
            }
        }
    }

    // ── Wylaczeni gracze (/scoreboard toggle) ─────────────────────────────

    private void wczytajWylaczonych() {
        wylaczeni.clear();
        File dataFile = this.dataFile;
        if (dataFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
            for (String s : y.getStringList("wylaczeni")) {
                try { wylaczeni.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
            zapiszWylaczonych();
            dataFile.renameTo(new File(plugin.getDataFolder(), "scoreboard-wylaczeni.yml.migrated"));
            return;
        }
        try (PreparedStatement ps = boniec.code.core.CoreDatabase.connection().prepareStatement(
                "SELECT uuid FROM scoreboard_wylaczeni");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try { wylaczeni.add(UUID.fromString(rs.getString(1))); } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Scoreboard] Odczyt SQLite: " + e.getMessage());
        }
    }

    private void zapiszWylaczonych() {
        Connection c = boniec.code.core.CoreDatabase.connection();
        try {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM scoreboard_wylaczeni")) {
                del.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO scoreboard_wylaczeni (uuid) VALUES (?)")) {
                for (UUID u : wylaczeni) {
                    ps.setString(1, u.toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Scoreboard] Zapis SQLite: " + e.getMessage());
        }
    }

    public boolean jestWlaczony(java.util.UUID uuid) {
        return uuid != null && !wylaczeni.contains(uuid);
    }

    public void ustaw(Player p, boolean wlaczony) {
        if (p == null) return;
        if (wlaczony) wylaczeni.remove(p.getUniqueId());
        else wylaczeni.add(p.getUniqueId());
        zapiszWylaczonych();
        if (wlaczony) odswiez(p); else usun(p);
    }

    /** @return true jesli scoreboard jest teraz WLACZONY dla gracza */
    public boolean przelacz(Player p) {
        boolean wlaczony;
        if (wylaczeni.remove(p.getUniqueId())) {
            wlaczony = true;
        } else {
            wylaczeni.add(p.getUniqueId());
            wlaczony = false;
        }
        zapiszWylaczonych();
        if (wlaczony) odswiez(p); else usun(p);
        return wlaczony;
    }

    public String wiadomosc(String klucz) {
        return Msg.color(cfg.getString("wiadomosci." + klucz, "&c[brak wiadomosci: " + klucz + "]"));
    }

    // ── Listenery ─────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // chwila opoznienia, zeby NametagKolorListener zdazyl nadac graczowi wlasny scoreboard
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) odswiez(p);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stany.remove(e.getPlayer().getUniqueId());
    }

    // ── Rysowanie ─────────────────────────────────────────────────────────

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                odswiez(p);
            } catch (Exception ex) {
                plugin.getLogger().warning("[Scoreboard] Blad u gracza " + p.getName() + ": " + ex.getMessage());
            }
        }
    }

    private void odswiez(Player p) {
        UUID uuid = p.getUniqueId();

        // Nadpisanie (KOTH) ma pierwszenstwo: pokazuje sie wszystkim, niezaleznie od swiata i
        // od "wlaczony" w scoreboard.yml - ale nadal szanuje /scoreboard toggle gracza.
        Nadpisanie ov = aktywneNadpisanie();
        boolean nadpisane = ov != null;

        List<String> aktLinie = nadpisane ? ov.linie(p) : linie;
        List<String> aktTytuly = nadpisane ? List.of(ov.tytul()) : tytuly;

        boolean pokaz = nadpisane
                ? !wylaczeni.contains(uuid)
                : wlaczonyCfg
                        && !wylaczeni.contains(uuid)
                        && !wylaczoneSwiaty.contains(p.getWorld().getName());
        if (!pokaz || aktLinie == null || aktLinie.isEmpty()) {
            usun(p);
            return;
        }
        if (aktLinie.size() > MAX_LINII) aktLinie = aktLinie.subList(0, MAX_LINII);

        org.bukkit.scoreboard.ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        Stan s = stany.get(uuid);
        Scoreboard board = p.getScoreboard();
        if (board == null || board.equals(sm.getMainScoreboard())) {
            board = sm.getNewScoreboard();
            p.setScoreboard(board);
            s = null;
        }

        if (s == null || s.board != board || board.getObjective(OBJ) == null || s.liczbaLinii != aktLinie.size()) {
            s = zbuduj(board, aktLinie.size());
            stany.put(uuid, s);
        }

        // Tytul (opcjonalnie animowany - lista klatek)
        int klatka = (int) ((System.currentTimeMillis() / 50L / tytulKlatkaTicks) % aktTytuly.size());
        String tytul = formatuj(p, aktTytuly.get(klatka));
        if (!tytul.equals(s.tytul)) {
            s.obj.setDisplayName(tytul);
            s.tytul = tytul;
        }

        // Linie - aktualizujemy tylko te, ktore realnie sie zmienily (mniej pakietow)
        for (int i = 0; i < s.liczbaLinii; i++) {
            String[] czesci = podziel(formatuj(p, aktLinie.get(i)));
            if (czesci[0].equals(s.prefixy[i]) && czesci[1].equals(s.sufiksy[i])) continue;
            Team t = board.getTeam(TEAM + i);
            if (t == null) {
                // ktos nam usunal team - przebudujemy wszystko przy nastepnym ticku
                stany.remove(uuid);
                return;
            }
            t.setPrefix(czesci[0]);
            t.setSuffix(czesci[1]);
            s.prefixy[i] = czesci[0];
            s.sufiksy[i] = czesci[1];
        }
    }

    private Stan zbuduj(Scoreboard board, int n) {
        czysc(board);

        Objective obj = board.registerNewObjective(OBJ, Criteria.DUMMY, "-");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        if (ukryjNumery) ukryjNumeryReflection(obj);

        for (int i = 0; i < n; i++) {
            String wpis = wpis(i);
            Team t = board.registerNewTeam(TEAM + i);
            t.addEntry(wpis);
            obj.getScore(wpis).setScore(n - i);
        }

        Stan s = new Stan();
        s.board = board;
        s.obj = obj;
        s.liczbaLinii = n;
        s.prefixy = new String[n];
        s.sufiksy = new String[n];
        return s;
    }

    /** Usuwa NASZ objective i team'y z danego scoreboardu (reszta - np. klan_swoi/klan_obcy - zostaje). */
    private void czysc(Scoreboard board) {
        try {
            Objective o = board.getObjective(OBJ);
            if (o != null) o.unregister();
            for (int i = 0; i < MAX_LINII; i++) {
                Team t = board.getTeam(TEAM + i);
                if (t != null) t.unregister();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private void usun(Player p) {
        Stan s = stany.remove(p.getUniqueId());
        if (s != null) czysc(s.board);
    }

    /** Unikalny, niewidoczny wpis dla linii i (kod koloru + reset). */
    private static String wpis(int i) {
        return "\u00A7" + Integer.toHexString(i) + "\u00A7r";
    }

    /**
     * Ukrywa czerwone numerki po prawej stronie. To funkcja Paper API (1.20.3+), wiec wolamy ja
     * refleksja - na zwyklym Spigocie nic sie nie stanie (numerki po prostu zostana widoczne).
     */
    private void ukryjNumeryReflection(Objective obj) {
        try {
            Class<?> nf = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = nf.getMethod("blank").invoke(null);
            Objective.class.getMethod("numberFormat", nf).invoke(obj, blank);
        } catch (Throwable ignored) {
        }
    }

    private String formatuj(Player p, String tekst) {
        if (tekst.indexOf('%') < 0) return Msg.color(tekst);
        String t = tekst.replace("%player%", p.getName());
        if (t.indexOf('%') >= 0) t = t.replace("%swiat%", nazwaSwiata(p));
        if (t.indexOf('%') >= 0) t = t.replace("%czas%", czasGry(p));
        if (t.indexOf('%') >= 0) t = boniec.code.core.OsobyExpansion.podmien(t);
        if (papi && t.indexOf('%') >= 0) {
            t = PlaceholderAPI.setPlaceholders(p, t);
        }
        return Msg.color(t);
    }

    private String nazwaSwiata(Player p) {
        String klucz = p.getWorld().getName();
        String alias = aliasySwiatow.get(klucz.toLowerCase());
        return alias != null ? alias : klucz;
    }

    private String czasGry(Player p) {
        int ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int sekundy = Math.max(0, ticks / 20);
        int dni = sekundy / 86400;
        int godz = (sekundy % 86400) / 3600;
        int min = (sekundy % 3600) / 60;
        if (dni > 0) return dni + "d " + godz + "h";
        if (godz > 0) return godz + "h " + min + "m";
        return min + "m";
    }

    // ── Dzielenie tekstu na prefix/sufiks ─────────────────────────────────

    private static String[] podziel(String s) {
        if (s.length() <= LIMIT) return new String[]{s, ""};

        int cut = LIMIT;
        if (s.charAt(cut - 1) == '\u00A7') cut--;
        // nie tnij w srodku sekwencji hex: §x§R§R§G§G§B§B (14 znakow)
        int x = s.lastIndexOf("\u00A7x", cut - 1);
        if (x >= 0 && x + 14 > cut) cut = x;

        String prefix = s.substring(0, cut);
        String suffix = ostatnieFormatowanie(prefix) + s.substring(cut);
        if (suffix.length() > LIMIT) {
            suffix = suffix.substring(0, LIMIT);
            if (suffix.endsWith("\u00A7")) suffix = suffix.substring(0, suffix.length() - 1);
        }
        return new String[]{prefix, suffix};
    }

    /** Zwraca aktywny kolor (takze hex) i style na koncu tekstu, zeby sufiks "dziedziczyl" wyglad. */
    private static String ostatnieFormatowanie(String s) {
        String kolor = "";
        StringBuilder style = new StringBuilder();
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) != '\u00A7') continue;
            char c = Character.toLowerCase(s.charAt(i + 1));
            if (c == 'x' && i + 13 < s.length()) {
                kolor = s.substring(i, i + 14);
                style.setLength(0);
                i += 13;
            } else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                kolor = "\u00A7" + c;
                style.setLength(0);
                i++;
            } else if (c == 'r') {
                kolor = "";
                style.setLength(0);
                i++;
            } else if (c >= 'k' && c <= 'o') {
                style.append('\u00A7').append(c);
                i++;
            }
        }
        return kolor + style;
    }

    public List<String> podpowiedzi() {
        return Collections.unmodifiableList(List.of("toggle", "reload"));
    }
}
