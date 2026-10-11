package boniec.poziomy;

import boniec.boss.BossbarUtil;
import boniec.szaman.util.Msg;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Poziomy postaci (port Dream-FlameHubLevels): exp za kopanie/stawianie/zabojstwa/dystans/jedzenie,
 * nagrody za poziom (/poziom), boost expa i top 20 (PlaceholderAPI %levels_...%).
 * Config: poziomy.yml, dane graczy: poziomy-dane.yml.
 */
public class PoziomyManager {

    private static final int TOP_ROZMIAR = 20;
    private static final Comparator<Profil> POROWNANIE_TOP = Comparator
            .comparingInt(Profil::getPoziom).reversed()
            .thenComparing(Comparator.comparingDouble(Profil::getExp).reversed());

    private final JavaPlugin plugin;
    private final File plikConfigu;
    private final File plikDanych;
    private YamlConfiguration config;

    private final TreeMap<Integer, Integer> poziomy = new TreeMap<>();
    private final Map<UUID, Profil> profile = new ConcurrentHashMap<>();
    private volatile List<Profil> top = Collections.emptyList();
    private volatile boolean doZapisu = false;

    // Zrodla expa
    private int expKopanie;
    private final Set<Material> blokiKopanie = EnumSet.noneOf(Material.class);
    private int expStawianie;
    private final Set<Material> blokiStawianie = EnumSet.noneOf(Material.class);
    private int expZabojstwo;
    private int expDystans;
    private double dystansCoIle;
    private final Set<String> regionyBezDystansu = new HashSet<>();
    private int expJedzenie;
    private final Set<Material> przedmiotyJedzenie = EnumSet.noneOf(Material.class);
    private String permisjaBypass;
    private boolean actionbarPostep;
    private final Map<UUID, Double> expKopanieDoActionbara = new ConcurrentHashMap<>();

    // Boost
    private double boostMnoznik = 1.0;
    private int boostPozostalo = 0;
    private BossBar boostBar;

    private BukkitTask zadanieSekundowe;
    private BukkitTask zadanieTop;
    private BukkitTask zadanieZapisu;
    private final Object zapisLock = new Object();
    /** true gdy plik danych byl niepusty, ale nie udalo sie wczytac zadnego profilu - nie nadpisuj go. */
    private boolean nieNadpisujPustka = false;

    public PoziomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plikConfigu = new File(plugin.getDataFolder(), "poziomy.yml");
        this.plikDanych = new File(plugin.getDataFolder(), "poziomy-dane.yml");
        wczytajConfig();
        wczytajDane();
        if (config.getBoolean("progi-skumulowane", false)) {
            skonwertujProgiJesliSkumulowane();
        }
        przeliczTop();

        zadanieSekundowe = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBoost, 20L, 20L);
        zadanieTop = Bukkit.getScheduler().runTaskTimer(plugin, this::przeliczTop, 1200L, 1200L);
        zadanieZapisu = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (doZapisu) zapiszDane(false);
        }, 200L, 200L);
    }

    // ===================== Config =====================

    public void wczytajConfig() {
        if (!plikConfigu.exists()) {
            plugin.saveResource("poziomy.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(plikConfigu);
        InputStream def = plugin.getResource("poziomy.yml");
        if (def != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }

        poziomy.clear();
        ConfigurationSection sekcja = config.getConfigurationSection("poziomy");
        if (sekcja != null) {
            for (String klucz : sekcja.getKeys(false)) {
                try {
                    poziomy.put(Integer.parseInt(klucz), Math.max(0, sekcja.getInt(klucz)));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("[Poziomy] Niepoprawny poziom w poziomy.yml: " + klucz);
                }
            }
        }
        if (poziomy.isEmpty()) {
            poziomy.put(1, 0);
        }

        expKopanie = config.getInt("exp.kopanie.ilosc", 1);
        wczytajMaterialy(config.getStringList("exp.kopanie.bloki"), blokiKopanie);
        expStawianie = config.getInt("exp.stawianie.ilosc", 2);
        wczytajMaterialy(config.getStringList("exp.stawianie.bloki"), blokiStawianie);
        expZabojstwo = config.getInt("exp.zabojstwo", 500);
        expDystans = config.getInt("exp.dystans.ilosc", 3);
        dystansCoIle = Math.max(1.0, config.getDouble("exp.dystans.co-ile-kratek", 10.0));
        regionyBezDystansu.clear();
        for (String r : config.getStringList("exp.dystans.wylaczone-regiony")) {
            regionyBezDystansu.add(r.toLowerCase(Locale.ROOT));
        }
        expJedzenie = config.getInt("exp.jedzenie.ilosc", 5);
        wczytajMaterialy(config.getStringList("exp.jedzenie.przedmioty"), przedmiotyJedzenie);
        permisjaBypass = config.getString("bypass-permisja", "levels.bypass");
        actionbarPostep = config.getBoolean("wiadomosci.actionbar-postep-wlaczony", true);
        if (config.getBoolean("progi-skumulowane", false) && !profile.isEmpty()) {
            skonwertujProgiJesliSkumulowane();
        }
    }

    private void wczytajMaterialy(List<String> nazwy, Set<Material> cel) {
        cel.clear();
        for (String nazwa : nazwy) {
            Material m = Material.matchMaterial(nazwa);
            if (m != null) {
                cel.add(m);
            } else {
                plugin.getLogger().warning("[Poziomy] Nieznany material w poziomy.yml: " + nazwa);
            }
        }
    }

    public void zapiszConfig() {
        try {
            config.save(plikConfigu);
        } catch (IOException e) {
            plugin.getLogger().warning("[Poziomy] Nie udalo sie zapisac poziomy.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() { return config; }

    public String msg(String sciezka, String domyslna) {
        return config.getString("wiadomosci." + sciezka, domyslna);
    }

    // ===================== Dane graczy =====================

    private void wczytajDane() {
        profile.clear();
        nieNadpisujPustka = false;
        zmigrujYamlJesliTrzeba();
        Connection c = boniec.code.core.CoreDatabase.connection();
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, nazwa, poziom, exp, odebrane FROM poziomy_gracze");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    Profil p = new Profil(uuid, rs.getString("nazwa") != null ? rs.getString("nazwa") : "?");
                    p.setPoziom(rs.getInt("poziom"));
                    p.setExp(rs.getDouble("exp"));
                    p.setZmigrowany(true);
                    String odebrane = rs.getString("odebrane");
                    if (odebrane != null && !odebrane.isEmpty()) {
                        for (String s : odebrane.split(",")) {
                            try {
                                p.getOdebraneNagrody().add(Integer.parseInt(s.trim()));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    profile.put(uuid, p);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Poziomy] Blad odczytu SQLite: " + e.getMessage());
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT mnoznik, pozostalo FROM poziomy_boost WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                boostMnoznik = rs.getDouble("mnoznik");
                boostPozostalo = Math.max(0, rs.getInt("pozostalo"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Poziomy] Blad odczytu boostu: " + e.getMessage());
        }
        for (Profil p : profile.values()) {
            if (awansujJesliMozna(p) > 0) doZapisu = true;
        }
        if (profile.isEmpty()) {
            int wBazie = boniec.code.core.CoreDatabase.get().liczbaWierszy("poziomy_gracze");
            if (wBazie > 0) {
                plugin.getLogger().severe("[Poziomy] WCZYTYWANIE NIEUDANE: 0 profili w pamieci, w data.db jest " + wBazie
                        + ". Nie uzywaj /plugman reload — zrestartuj caly serwer.");
            }
        } else {
            plugin.getLogger().info("[Poziomy] Wczytano " + profile.size() + " profili z data.db");
        }
    }

    private void zmigrujYamlJesliTrzeba() {
        boniec.code.core.CoreDatabase db = boniec.code.core.CoreDatabase.get();
        if (db.juzZmigrowane("yaml:poziomy-dane.yml")) return;
        if (!plikDanych.exists()) {
            File bak = new File(plugin.getDataFolder(), "poziomy-dane.yml.bak");
            if (bak.exists()) {
                wczytajYamlDoPamieci(bak);
                zapiszDane(false);
            }
            db.oznaczMigracje("yaml:poziomy-dane.yml");
            return;
        }
        wczytajYamlDoPamieci(plikDanych);
        zapiszDane(false);
        File migrated = new File(plugin.getDataFolder(), "poziomy-dane.yml.migrated");
        if (!plikDanych.renameTo(migrated)) {
            plugin.getLogger().warning("[Poziomy] Nie zmieniono nazwy poziomy-dane.yml po migracji do SQLite.");
        }
        db.oznaczMigracje("yaml:poziomy-dane.yml");
        plugin.getLogger().info("[Poziomy] Zmigrowano poziomy-dane.yml do data.db");
    }

    private void wczytajYamlDoPamieci(File plik) {
        YamlConfiguration dane = YamlConfiguration.loadConfiguration(plik);
        wczytajProfileZYaml(dane);
        boostMnoznik = dane.getDouble("boost.mnoznik", 1.0);
        boostPozostalo = Math.max(0, dane.getInt("boost.pozostalo-sekund", 0));
        doZapisu = true;
    }

    private void wczytajProfileZYaml(YamlConfiguration dane) {
        ConfigurationSection gracze = dane.getConfigurationSection("gracze");
        if (gracze == null) return;
        for (String klucz : gracze.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(klucz);
                ConfigurationSection s = gracze.getConfigurationSection(klucz);
                String nazwa = s != null ? s.getString("nazwa", "?") : gracze.getString(klucz + ".nazwa", "?");
                Profil p = new Profil(uuid, nazwa != null ? nazwa : "?");
                double exp = s != null ? s.getDouble("exp", 0) : gracze.getDouble(klucz + ".exp", 0);
                int poziom = s != null ? s.getInt("poziom", 1) : gracze.getInt(klucz + ".poziom", 1);
                p.setExp(exp);
                p.setPoziom(poziom);
                p.setZmigrowany(true);
                List<Integer> odebrane = s != null ? s.getIntegerList("odebrane") : gracze.getIntegerList(klucz + ".odebrane");
                p.getOdebraneNagrody().addAll(odebrane);
                profile.put(uuid, p);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void zapiszDane(boolean async) {
        if (!doZapisu && async) return;
        doZapisu = false;
        nieNadpisujPustka = false;

        List<Profil> snapshot = new ArrayList<>(profile.values());
        double mnoznik = boostMnoznik;
        int pozostalo = boostPozostalo;

        Runnable zapis = () -> zapiszSqlite(snapshot, mnoznik, pozostalo);
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, zapis);
        } else {
            zapis.run();
        }
    }

    private void zapiszSqlite(List<Profil> snapshot, double mnoznik, int pozostalo) {
        synchronized (zapisLock) {
            if (snapshot.isEmpty()) {
                int wBazie = boniec.code.core.CoreDatabase.get().liczbaWierszy("poziomy_gracze");
                if (wBazie > 0) {
                    plugin.getLogger().warning("[Poziomy] Pomijam zapis — cache pusty, w data.db jest " + wBazie
                            + " profili (chroni przed skasowaniem EXP przy reloadzie).");
                    return;
                }
            }
            try {
                boniec.code.core.CoreDatabase.runInTransaction(c -> {
                    try (PreparedStatement del = c.prepareStatement("DELETE FROM poziomy_gracze")) {
                        del.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO poziomy_gracze (uuid, nazwa, poziom, exp, odebrane) VALUES (?, ?, ?, ?, ?)")) {
                        for (Profil p : snapshot) {
                            ps.setString(1, p.getUuid().toString());
                            ps.setString(2, p.getNazwa());
                            ps.setInt(3, p.getPoziom());
                            ps.setDouble(4, p.getExp());
                            ps.setString(5, p.getOdebraneNagrody().stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO poziomy_boost (id, mnoznik, pozostalo) VALUES (1, ?, ?) ON CONFLICT(id) DO UPDATE SET mnoznik = excluded.mnoznik, pozostalo = excluded.pozostalo")) {
                        ps.setDouble(1, mnoznik);
                        ps.setInt(2, pozostalo);
                        ps.executeUpdate();
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("[Poziomy] Nie udalo sie zapisac do data.db: " + e.getMessage());
            }
            boniec.code.core.CoreDatabase.checkpointWal();
        }
    }

    public Profil getProfil(OfflinePlayer gracz) {
        Profil p = profile.computeIfAbsent(gracz.getUniqueId(), uuid -> {
            doZapisu = true;
            return new Profil(uuid, gracz.getName() != null ? gracz.getName() : "?");
        });
        if (gracz.getName() != null && !gracz.getName().equals(p.getNazwa())) {
            p.setNazwa(gracz.getName());
            doZapisu = true;
        }
        return p;
    }

    public void oznaczDoZapisu() { doZapisu = true; }

    // ===================== Poziomy i exp =====================

    public int getPoziomZExpa(int exp) {
        int wynik = poziomy.isEmpty() ? 1 : poziomy.firstKey();
        for (Map.Entry<Integer, Integer> e : poziomy.entrySet()) {
            if (e.getValue() <= exp) wynik = e.getKey();
        }
        return wynik;
    }

    public int getPoziom(OfflinePlayer gracz) {
        return getProfil(gracz).getPoziom();
    }

    /**
     * Exp potrzebny na pasku aktualnego poziomu (do nastepnego), albo -1 gdy max.
     * W poziomy.yml liczba przy poziomie N to koszt WEJSCIA na ten poziom
     * (pasek gracza na N-1).
     */
    public int getExpNaNastepny(int poziom) {
        Integer nastepny = poziomy.higherKey(poziom);
        if (nastepny == null) return -1;
        int koszt = poziomy.getOrDefault(nastepny, 0);
        return koszt > 0 ? koszt : -1;
    }

    public Integer getExpNaPoziom(int poziom) { return poziomy.get(poziom); }
    public Set<Integer> getPoziomy() { return poziomy.keySet(); }

    public int getProcent(double exp, int poziom) {
        int nastepny = getExpNaNastepny(poziom);
        if (nastepny <= 0) return 100;
        return Math.max(0, Math.min(100, (int) (exp * 100.0 / nastepny)));
    }

    public int getProcent(Profil p) {
        return getProcent(p.getExp(), p.getPoziom());
    }

    /** 433.0 albo 2.1k / 2.5k / 0.05 */
    public static String formatujExp(int wartosc) {
        return formatujExp((double) wartosc);
    }

    public static String formatujExp(double wartosc) {
        if (wartosc < 0) return "MAX";
        double v = wartosc;
        if (v >= 1_000_000_000d) return trimExp(v / 1_000_000_000d) + "b";
        if (v >= 1_000_000d) return trimExp(v / 1_000_000d) + "m";
        if (v >= 1000d) return trimExp(v / 1000d) + "k";
        if (v > 0 && v < 1) return String.format(Locale.US, "%.2f", v);
        return String.format(Locale.US, "%.1f", v);
    }

    private static String trimExp(double d) {
        return String.format(Locale.US, "%.1f", d);
    }

    private boolean wygladaNaProgiCalkowite() {
        if (poziomy.size() < 2) return false;
        if (poziomy.get(poziomy.firstKey()) != 0) return false;
        Integer prev = null;
        for (int v : poziomy.values()) {
            if (prev != null && v <= prev) return false;
            prev = v;
        }
        return true;
    }

    private void skonwertujProgiJesliSkumulowane() {
        boolean skumulowane = wygladaNaProgiCalkowite();
        if (skumulowane) {
            for (Profil p : profile.values()) {
                if (p.isZmigrowany()) continue;
                double total = p.getExp();
                int lvl = getPoziomZExpa((int) total);
                int prog = poziomy.getOrDefault(lvl, 0);
                p.setPoziom(lvl);
                p.setExp(Math.max(0, total - prog));
                p.setZmigrowany(true);
            }
            TreeMap<Integer, Integer> koszty = new TreeMap<>();
            Integer prevK = null;
            int prevV = 0;
            for (Map.Entry<Integer, Integer> e : poziomy.entrySet()) {
                if (prevK != null) {
                    koszty.put(e.getKey(), Math.max(0, e.getValue() - prevV));
                } else {
                    koszty.put(e.getKey(), 0);
                }
                prevK = e.getKey();
                prevV = e.getValue();
            }
            poziomy.clear();
            poziomy.putAll(koszty);
        } else {
            for (Profil p : profile.values()) {
                if (p.isZmigrowany()) continue;
                p.setPoziom(Math.max(1, poziomy.isEmpty() ? 1 : poziomy.firstKey()));
                p.setZmigrowany(true);
            }
        }
        doZapisu = true;
    }

    public String getPasekPostepu(int procent) {
        int dlugosc = Math.max(1, config.getInt("pasek.dlugosc", 8));
        String znak = config.getString("pasek.znak", "■");
        int pelne = procent * dlugosc / 100;
        StringBuilder sb = new StringBuilder(config.getString("pasek.kolor-pelny", "&a"));
        for (int i = 0; i < pelne; i++) sb.append(znak);
        sb.append(config.getString("pasek.kolor-pusty", "&c"));
        for (int i = pelne; i < dlugosc; i++) sb.append(znak);
        return sb.toString();
    }

    public void dodajExp(Player gracz, int bazowy) {
        dodajExp(gracz, (double) bazowy, true);
    }

    public void dodajExp(Player gracz, int bazowy, boolean actionbar) {
        dodajExp(gracz, (double) bazowy, actionbar);
    }

    /**
     * @param actionbar false = zapisuje exp do wspolnego actionbara z kasa (kopanie),
     *                  true = od razu pokazuje pasek EXP (np. zabojstwo gracza).
     */
    public void dodajExp(Player gracz, double bazowy, boolean actionbar) {
        if (Math.abs(bazowy) < 1e-9) return;
        if (permisjaBypass != null && !permisjaBypass.isEmpty() && gracz.hasPermission(permisjaBypass)) return;

        if (bazowy < 0) {
            Profil p = getProfil(gracz);
            p.setExp(p.getExp() + bazowy);
            doZapisu = true;
            return;
        }

        double exp = boostPozostalo > 0 ? bazowy * boostMnoznik : bazowy;
        Profil p = getProfil(gracz);
        int przed = p.getPoziom();
        p.setExp(p.getExp() + exp);
        awansujJesliMozna(p);
        int po = p.getPoziom();
        doZapisu = true;

        if (po > przed) {
            powiadomAwans(gracz, po);
            boniec.dev.inventory.Main core = boniec.dev.inventory.Main.getInstance();
            if (core != null && core.getMisjeManager() != null) {
                core.getMisjeManager().dodajPostep(gracz, boniec.misje.TypMisji.AWANS_POZIOM, po - przed);
            }
        }

        if (!actionbar) {
            expKopanieDoActionbara.merge(gracz.getUniqueId(), exp, Double::sum);
            return;
        }
        if (actionbarPostep) {
            wyslijActionbarPostep(gracz, exp);
        }
    }

    public String zbudujActionbarPostep(Player gracz, double dodano) {
        Profil p = getProfil(gracz);
        int poziom = p.getPoziom();
        int nastepny = getExpNaNastepny(poziom);
        return msg("actionbar-postep", "&8[%pasek%&8] &8(&#C02FD4%exp%&8/&f%cel%&8) &#C02FD4+%dodano% ᴇxᴘ")
                .replace("%pasek%", getPasekPostepu(getProcent(p)))
                .replace("%exp%", formatujExp(p.getExp()))
                .replace("%cel%", nastepny < 0 ? "ᴍᴀx" : formatujExp(nastepny))
                .replace("%dodano%", formatujExp(Math.max(0, dodano)))
                .replace("%poziom%", String.valueOf(poziom));
    }

    /**
     * Gdy exp >= koszt nastepnego poziomu: podbija poziom i odejmuje koszt (moze kilka razy).
     * Nagrod nie daje — gracz odbiera je sam w /poziom.
     * @return ile poziomow awansowano
     */
    public int awansujJesliMozna(Profil p) {
        if (p == null) return 0;
        int przed = p.getPoziom();
        while (true) {
            int potrzeba = getExpNaNastepny(p.getPoziom());
            if (potrzeba <= 0) break;
            if (p.getExp() < potrzeba) break;
            p.setExp(p.getExp() - potrzeba);
            Integer nastepny = poziomy.higherKey(p.getPoziom());
            if (nastepny == null) break;
            p.setPoziom(nastepny);
        }
        return Math.max(0, p.getPoziom() - przed);
    }

    public void powiadomAwans(Player gracz, int poziom) {
        gracz.sendTitle(
                Msg.color(msg("awans-tytul", "&f乂 &#C02FD4&lᴀᴡᴀɴꜱ!").replace("%poziom%", String.valueOf(poziom))),
                Msg.color(msg("awans-podtytul", "&fᴏꜱɪᴀɢɴᴀʟᴇꜱ ᴘᴏᴢɪᴏᴍ &#C02FD4%poziom%").replace("%poziom%", String.valueOf(poziom))),
                10, 50, 15);
        gracz.playSound(gracz.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    public void wyslijActionbarPostep(Player gracz, double dodano) {
        gracz.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(Msg.color(zbudujActionbarPostep(gracz, dodano))));
    }

    /** Pobiera i zeruje exp z kopania zebrany w tym ticku (do wspolnego actionbara z kasa). */
    public double wezExpKopaniaDoActionbara(UUID uuid) {
        Double v = expKopanieDoActionbara.remove(uuid);
        return v == null ? 0 : v;
    }

    public void oddajExpKopaniaDoActionbara(UUID uuid, double exp) {
        if (exp > 0) expKopanieDoActionbara.merge(uuid, exp, Double::sum);
    }

    // Gettery zrodel expa (uzywane przez PoziomyListener)
    public int getExpKopanie() { return expKopanie; }
    public boolean liczySieKopanie(Material m) { return blokiKopanie.contains(m); }
    public int getExpStawianie() { return expStawianie; }
    public boolean liczySieStawianie(Material m) { return blokiStawianie.contains(m); }
    public int getExpZabojstwo() { return expZabojstwo; }
    public int getExpDystans() { return expDystans; }
    public double getDystansCoIle() { return dystansCoIle; }
    public Set<String> getRegionyBezDystansu() { return regionyBezDystansu; }
    public int getExpJedzenie() { return expJedzenie; }
    public boolean liczySieJedzenie(Material m) { return przedmiotyJedzenie.contains(m); }

    // ===================== Nagrody =====================

    @SuppressWarnings("unchecked")
    public List<ItemStack> getNagrodyPrzedmioty(int poziom) {
        List<?> lista = config.getList("nagrody." + poziom + ".przedmioty");
        List<ItemStack> wynik = new ArrayList<>();
        if (lista != null) {
            for (Object o : lista) {
                if (o instanceof ItemStack item && !item.getType().isAir()) {
                    wynik.add(item.clone());
                }
            }
        }
        return wynik;
    }

    public void setNagrodyPrzedmioty(int poziom, List<ItemStack> przedmioty) {
        config.set("nagrody." + poziom + ".przedmioty", przedmioty);
        zapiszConfig();
    }

    public List<String> getNagrodyKomendy(int poziom) {
        return new ArrayList<>(config.getStringList("nagrody." + poziom + ".komendy"));
    }

    public void setNagrodyKomendy(int poziom, List<String> komendy) {
        config.set("nagrody." + poziom + ".komendy", komendy);
        zapiszConfig();
    }

    public void setExpNaPoziom(int poziom, int exp) {
        config.set("poziomy." + poziom, exp);
        zapiszConfig();
        poziomy.put(poziom, Math.max(0, exp));
    }

    public enum WynikOdbioru { OK, ZA_NISKI_POZIOM, JUZ_ODEBRANA }

    public WynikOdbioru odbierzNagrode(Player gracz, int poziom) {
        Profil p = getProfil(gracz);
        if (p.getOdebraneNagrody().contains(poziom)) return WynikOdbioru.JUZ_ODEBRANA;
        if (p.getPoziom() < poziom) return WynikOdbioru.ZA_NISKI_POZIOM;

        p.getOdebraneNagrody().add(poziom);
        doZapisu = true;

        for (ItemStack item : getNagrodyPrzedmioty(poziom)) {
            for (ItemStack reszta : gracz.getInventory().addItem(item).values()) {
                gracz.getWorld().dropItemNaturally(gracz.getLocation(), reszta);
            }
        }
        for (String komenda : getNagrodyKomendy(poziom)) {
            String gotowa = komenda.startsWith("/") ? komenda.substring(1) : komenda;
            gotowa = gotowa.replace("{player}", gracz.getName()).replace("%player%", gracz.getName())
                    .replace("{gracz}", gracz.getName()).replace("%gracz%", gracz.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), gotowa);
        }
        return WynikOdbioru.OK;
    }

    // ===================== Boost =====================

    public void ustawBoost(double mnoznik, int sekundy) {
        boostMnoznik = Math.max(0.1, mnoznik);
        boostPozostalo = Math.max(0, sekundy);
        doZapisu = true;
        if (boostPozostalo == 0) usunBossbar();
    }

    public boolean isBoostAktywny() { return boostPozostalo > 0; }
    public double getBoostMnoznik() { return boostMnoznik; }
    public int getBoostPozostalo() { return boostPozostalo; }

    private void tickBoost() {
        if (boostPozostalo <= 0) {
            usunBossbar();
            return;
        }
        boostPozostalo--;
        String tytul = Msg.color(msg("boost", "&f乂 &#C02FD4&lʙᴏᴏꜱᴛ ᴇxᴘᴀ &8(&#C02FD4x%mnoznik%&8) &fᴘʀᴢᴇᴢ &#C02FD4%czas%")
                .replace("%mnoznik%", formatuj(boostMnoznik))
                .replace("%czas%", formatujCzas(boostPozostalo)));

        if (config.getBoolean("boost.bossbar", true)) {
            if (boostBar == null) {
                BarColor kolor = BarColor.PURPLE;
                BarStyle styl = BarStyle.SOLID;
                try { kolor = BarColor.valueOf(config.getString("boost.kolor", "PURPLE")); } catch (Exception ignored) { }
                try { styl = BarStyle.valueOf(config.getString("boost.styl", "SOLID")); } catch (Exception ignored) { }
                boostBar = BossbarUtil.utworz(plugin, tytul, kolor, styl);
                boostBar.setVisible(true);
            }
            boostBar.setTitle(tytul);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!boostBar.getPlayers().contains(p)) boostBar.addPlayer(p);
            }
        } else {
            usunBossbar();
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(tytul));
            }
        }
        if (boostPozostalo == 0) {
            usunBossbar();
        }
    }

    private void usunBossbar() {
        if (boostBar != null) {
            BossbarUtil.usun(boostBar);
            boostBar = null;
        }
    }

    // ===================== Top =====================

    public void przeliczTop() {
        List<Profil> lista = new ArrayList<>(profile.values());
        lista.sort(POROWNANIE_TOP);
        top = Collections.unmodifiableList(new ArrayList<>(lista.subList(0, Math.min(TOP_ROZMIAR, lista.size()))));
    }

    /** Miejsce 1..20, null gdy brak gracza na tej pozycji. */
    public Profil getTop(int miejsce) {
        List<Profil> t = top;
        return (miejsce >= 1 && miejsce <= t.size()) ? t.get(miejsce - 1) : null;
    }

    public int getPozycja(UUID uuid) {
        if (uuid == null || !profile.containsKey(uuid)) return -1;
        List<Profil> lista = new ArrayList<>(profile.values());
        lista.sort(POROWNANIE_TOP);
        for (int i = 0; i < lista.size(); i++) {
            if (uuid.equals(lista.get(i).getUuid())) return i + 1;
        }
        return -1;
    }

    // ===================== Wylaczanie =====================

    public void zapiszPrzyWylaczaniu() {
        if (zadanieSekundowe != null) zadanieSekundowe.cancel();
        if (zadanieTop != null) zadanieTop.cancel();
        if (zadanieZapisu != null) zadanieZapisu.cancel();
        usunBossbar();
        zapiszDane(false);
    }

    public void zamknij() {
        zapiszPrzyWylaczaniu();
    }

    // ===================== Pomocnicze =====================

    public static String formatuj(double wartosc) {
        if (wartosc == Math.floor(wartosc) && !Double.isInfinite(wartosc)) {
            return String.valueOf((long) wartosc);
        }
        return String.format(Locale.US, "%.2f", wartosc);
    }

    public static String formatujCzas(int sekundy) {
        if (sekundy >= 3600) return (sekundy / 3600) + "h " + ((sekundy % 3600) / 60) + "m " + (sekundy % 60) + "s";
        if (sekundy >= 60) return (sekundy / 60) + "m " + (sekundy % 60) + "s";
        return sekundy + "s";
    }

    /** "90", "30s", "10m", "2h", "1d" -> sekundy; -1 gdy niepoprawne. */
    public static int parsujCzas(String tekst) {
        if (tekst == null || tekst.isEmpty()) return -1;
        String t = tekst.toLowerCase(Locale.ROOT).trim();
        int mnoznik = 1;
        char ostatni = t.charAt(t.length() - 1);
        if (Character.isLetter(ostatni)) {
            switch (ostatni) {
                case 's' -> mnoznik = 1;
                case 'm' -> mnoznik = 60;
                case 'h' -> mnoznik = 3600;
                case 'd' -> mnoznik = 86400;
                default -> { return -1; }
            }
            t = t.substring(0, t.length() - 1);
        }
        try {
            return Math.max(0, Integer.parseInt(t)) * mnoznik;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
