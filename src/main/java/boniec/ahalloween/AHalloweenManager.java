package boniec.ahalloween;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import boniec.scoreboard.SidebarManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class AHalloweenManager implements SidebarManager.Nadpisanie {

    private enum KrolSbStan { WYLACZONY, WALKA, PO_ZABICIU }

    public static final String PDC_OBJECT = "obj";
    public static final String PDC_MOB = "mob";
    public static final String PDC_BAG = "bag";

    public record CandyDef(String id, boolean enabled, String name, List<String> lore, String texture, String nexo,
                           String interaction, int respawn, int min, int max, String rarity, int value, int weight,
                           int maxActive, double radius, boolean crate) {}

    public record MobDef(String id, boolean enabled, String name, EntityType type, double hp, double dmg,
                         int respawn, int maxAlive, String candyId, int cMin, int cMax, boolean rare, boolean boss,
                         List<String> miniony, String mythicMob, double mythicLevel, boolean overrideHp,
                         boolean overrideName) {}

    public record MissionDef(String id, String typ, String nazwa, String opis, int cel, String stat,
                             String nagroda, int ile, int exp) {}

    public record UpgradeDef(String id, String nazwa, String opis, int max, List<Integer> koszty, double bonus) {}

    public record ShopDef(String id, int cena, int limit, boolean crate, Map<String, Integer> grant,
                          List<String> commands, HalloweenShopItems.StackDef display,
                          List<HalloweenShopItems.StackDef> daj, int slot, boolean dajPrzedmiot) {}

    public record WorldObj(UUID entity, String candyId, String spawnKey, long immuneDo) {}

    public record LiveMob(UUID entity, String mobId, String spawnKey, boolean boss) {}

    private final JavaPlugin plugin;
    private final SidebarManager sidebar;
    private final File plik;
    private final File plikSpawnow;
    private FileConfiguration cfg;
    private FileConfiguration spawnyCfg;
    private HalloweenDatabase baza;
    private NamespacedKey keyObject;
    private NamespacedKey keyMob;
    private NamespacedKey keyBag;
    private NamespacedKey keyCandy;

    private final Map<UUID, HalloweenPlayer> cache = new ConcurrentHashMap<>();
    private final Map<UUID, WorldObj> obiekty = new ConcurrentHashMap<>();
    private final Map<UUID, LiveMob> moby = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> obrazenia = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCollect = new ConcurrentHashMap<>();
    /** postęp PPM: klucz entityUuid|playerUuid → liczba kliknięć */
    private final Map<String, Integer> klikPostep = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> collectMinute = new ConcurrentHashMap<>();
    private final Map<UUID, Long> collectMinuteTs = new ConcurrentHashMap<>();
    private final Set<UUID> przetwarzane = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> pendingCandy = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingMob = new ConcurrentHashMap<>();
    private BossBar krolBar;
    private int tickLicznik;
    private int krolFaza;

    private KrolSbStan krolSbStan = KrolSbStan.WYLACZONY;
    private long krolSbDo;
    private UUID krolSbBoss;
    private UUID krolOstatniAtak;
    private final Map<UUID, String> krolSbNicki = new HashMap<>();
    private List<Map.Entry<UUID, Double>> krolSbTop = Collections.emptyList();
    private String krolSbWierszCzasu = "";
    private boolean krolSbWlaczony;
    private int krolSbPoSekundy;
    private String krolSbTytul = "";
    private List<String> krolSbLinie = List.of();
    private String krolSbCzasTrwa = "";
    private String krolSbCzasPo = "";
    private String krolSbBrakNick = "&8-";

    private final Map<String, CandyDef> candies = new HashMap<>();
    private final Map<String, MobDef> mobDefs = new HashMap<>();
    private final Map<String, MissionDef> missions = new HashMap<>();
    private final Map<String, UpgradeDef> upgrades = new HashMap<>();
    private final Map<String, ShopDef> shop = new HashMap<>();

    private volatile boolean aktywny;
    private long koniecEpoch;
    private long caloscMs = 1;
    private BossBar bar;
    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private ItemStack worekSzablon;
    private HalloweenTrickOrTreat trickOrTreat;
    private HalloweenCauldron kociol;
    private final Map<String, String> domNazwy = new ConcurrentHashMap<>();
    private volatile boolean blokujSkrzynieKrola;
    private boolean krolUsunSkrzyniePoSmierci = true;
    private int krolPromienCzyszczenia = 80;
    private final Set<String> krolMythicDoUsuniecia = ConcurrentHashMap.newKeySet();
    private String krolObrazeniaRanking = "per_hit";
    private double krolObrazeniaNaUderzenie = 1.0;

    private boolean faleWlaczone;
    private int faleCoMinuty = 18;
    private boolean faleTylkoNocMc = true;
    private int faleCzasSec = 180;
    private int faleMobyNaSpawn = 2;
    private List<String> faleTypyMobow = List.of("dynia", "duch", "szkielet");
    private double faleMnoznikDropMob = 1.5;
    private boolean faleBossbar = true;
    private String faleTytulBossbar = "";
    private long faleKoniecAt;
    private long nastepnaFalaPo;
    private BossBar faleBar;

    private boolean miniWlaczony;
    private int miniCoSec = 3600;
    private int miniCzasSec = 600;
    private int miniPierwszyDelaySec = 120;
    private double miniMnoznikZebranych = 2.0;
    private double miniMnoznikRespawn = 0.5;
    private boolean miniBossbar = true;
    private String miniTytulBossbar = "";
    private long miniKoniecAt;
    private long nastepnyMiniPo;
    private BossBar miniBar;

    public AHalloweenManager(JavaPlugin plugin, SidebarManager sidebar) {
        this.plugin = plugin;
        this.sidebar = sidebar;
        this.plik = new File(plugin.getDataFolder(), "ahalloween.yml");
        this.plikSpawnow = new File(plugin.getDataFolder(), "ahalloween-spawns.yml");
        this.keyObject = new NamespacedKey(plugin, "ah_obj");
        this.keyMob = new NamespacedKey(plugin, "ah_mob");
        this.keyBag = new NamespacedKey(plugin, "ah_bag");
        this.keyCandy = new NamespacedKey(plugin, "ah_candy");
        this.baza = new HalloweenDatabase(plugin);
        wczytajConfig();
        wczytajSpawny();
        wczytajSzablonWorka();
        kociol = new HalloweenCauldron(this);
        trickOrTreat = new HalloweenTrickOrTreat(this, plugin);
        trickOrTreat.reload();
        if (cfg.getBoolean("event.wlaczony", false)) {
            start(cfg.getInt("event.czas-sekund", 86400));
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::zapiszBrudnych, 20L * 30, 20L * 30);
        if (sidebar != null) sidebar.dodajNadpisanie(this);
    }

    public JavaPlugin plugin() { return plugin; }
    public FileConfiguration cfg() { return cfg; }
    public HalloweenDatabase baza() { return baza; }
    public boolean aktywny() { return aktywny; }
    public NamespacedKey keyObject() { return keyObject; }
    public NamespacedKey keyMob() { return keyMob; }
    public NamespacedKey keyBag() { return keyBag; }
    public Collection<CandyDef> candyDefs() { return candies.values(); }
    public Collection<MobDef> mobDefinicje() { return mobDefs.values(); }
    public Collection<MissionDef> misje() { return missions.values(); }
    public Collection<UpgradeDef> ulepszenia() { return upgrades.values(); }
    public Collection<ShopDef> sklep() { return shop.values(); }
    public CandyDef candy(String id) { return candies.get(id); }
    public MobDef mob(String id) { return mobDefs.get(id); }
    public UpgradeDef upgrade(String id) { return upgrades.get(id); }
    public ShopDef produkt(String id) { return shop.get(id); }
    public MissionDef misja(String id) { return missions.get(id); }
    public HalloweenTrickOrTreat trickOrTreat() { return trickOrTreat; }
    public HalloweenCauldron kociol() { return kociol; }
    public long pozostaloSekund() { return aktywny ? Math.max(0, (koniecEpoch - System.currentTimeMillis()) / 1000L) : -1; }

    public void wczytajConfig() {
        if (!plik.exists()) {
            try (InputStream in = plugin.getResource("ahalloween.yml")) {
                if (in != null) {
                    plik.getParentFile().mkdirs();
                    Files.copy(in, plik.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[AHalloween] Nie utworzono ahalloween.yml: " + e.getMessage());
            }
        }
        cfg = YamlConfiguration.loadConfiguration(plik);
        try (InputStream def = plugin.getResource("ahalloween.yml")) {
            if (def != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
        }
        candies.clear();
        ConfigurationSection cs = cfg.getConfigurationSection("candies");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                candies.put(id, new CandyDef(id, s.getBoolean("enabled", true), s.getString("display-name", id),
                        s.getStringList("lore"), s.getString("texture", ""), s.getString("nexo", ""),
                        s.getString("interaction", "RIGHT_CLICK").toUpperCase(Locale.ROOT),
                        s.getInt("respawn-seconds", 30), s.getInt("amount-min", 1), s.getInt("amount-max", 1),
                        s.getString("rarity", "COMMON"), s.getInt("value", 1), s.getInt("weight", 10),
                        s.getInt("max-active", 20), s.getDouble("collect-radius", 1.6),
                        s.getBoolean("crate", false)));
            }
        }
        mobDefs.clear();
        ConfigurationSection ms = cfg.getConfigurationSection("mobs");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                if (s == null) continue;
                EntityType typ = EntityType.ZOMBIE;
                try {
                    typ = EntityType.valueOf(s.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {
                }
                String mythic = s.getString("mythic-mob", "");
                boolean hasMythic = mythic != null && !mythic.isBlank();
                mobDefs.put(id, new MobDef(id, s.getBoolean("enabled", true), s.getString("display-name", id),
                        typ, s.getDouble("hp", 50), s.getDouble("damage", 4), s.getInt("respawn-seconds", 90),
                        s.getInt("max-alive", 6), s.getString("candy-id", "zwykly"),
                        s.getInt("candy-min", 1), s.getInt("candy-max", 3),
                        s.getBoolean("rare", false), s.getBoolean("boss", false),
                        s.getStringList("miniony"),
                        hasMythic ? mythic : "",
                        s.getDouble("mythic-level", 1),
                        s.getBoolean("override-hp", true),
                        s.getBoolean("override-name", !hasMythic)));
            }
        }
        missions.clear();
        ConfigurationSection mis = cfg.getConfigurationSection("missions");
        if (mis != null) {
            for (String id : mis.getKeys(false)) {
                ConfigurationSection s = mis.getConfigurationSection(id);
                if (s == null) continue;
                missions.put(id, new MissionDef(id, s.getString("typ", "DAILY"), s.getString("nazwa", id),
                        s.getString("opis", ""), s.getInt("cel", 1), s.getString("stat", "candies"),
                        s.getString("nagroda-candy", "zwykly"), s.getInt("nagroda-ilosc", 1), s.getInt("exp", 10)));
            }
        }
        upgrades.clear();
        ConfigurationSection us = cfg.getConfigurationSection("upgrades");
        if (us != null) {
            for (String id : us.getKeys(false)) {
                ConfigurationSection s = us.getConfigurationSection(id);
                if (s == null) continue;
                List<Integer> koszty = new ArrayList<>();
                for (Object o : s.getList("koszty", List.of(10))) {
                    if (o instanceof Number n) koszty.add(n.intValue());
                }
                upgrades.put(id, new UpgradeDef(id, s.getString("nazwa", id), s.getString("opis", ""),
                        s.getInt("max", 5), koszty, s.getDouble("bonus-na-poziom", 0.1)));
            }
        }
        naprawStaryMagnes();
        shop.clear();
        ConfigurationSection ss = cfg.getConfigurationSection("shop");
        if (ss != null) {
            for (String id : ss.getKeys(false)) {
                ConfigurationSection s = ss.getConfigurationSection(id);
                if (s == null) continue;
                Map<String, Integer> grant = new HashMap<>();
                ConfigurationSection g = s.getConfigurationSection("grant");
                if (g != null) {
                    for (String k : g.getKeys(false)) grant.put(k, g.getInt(k));
                }
                List<String> commands = new ArrayList<>();
                String cmd = s.getString("command", "");
                if (cmd != null && !cmd.isBlank()) commands.add(cmd);
                commands.addAll(s.getStringList("komendy"));
                HalloweenShopItems.StackDef display = HalloweenShopItems.parseDisplay(s);
                List<HalloweenShopItems.StackDef> daj = HalloweenShopItems.parseDaj(s);
                boolean dajPrzedmiot = s.contains("daj-przedmiot")
                        ? s.getBoolean("daj-przedmiot")
                        : commands.isEmpty();
                shop.put(id, new ShopDef(id, s.getInt("cena", 10), s.getInt("limit", 0),
                        s.getBoolean("crate", false), grant, commands, display, daj, s.getInt("slot", -1), dajPrzedmiot));
            }
        }
        wczytajKrolScoreboardConfig();
        wczytajKrolSkrzynieConfig();
        wczytajDynamicEventsConfig();
    }

    private void wczytajDynamicEventsConfig() {
        ConfigurationSection root = cfg.getConfigurationSection("dynamic-events");
        ConfigurationSection fale = root == null ? null : root.getConfigurationSection("nocne-fale");
        faleWlaczone = fale != null && fale.getBoolean("enabled", true);
        faleCoMinuty = Math.max(5, fale == null ? 18 : fale.getInt("co-minuty", 18));
        faleTylkoNocMc = fale == null || fale.getBoolean("tylko-noc-minecraft", true);
        faleCzasSec = Math.max(30, fale == null ? 180 : fale.getInt("czas-trwania-sekundy", 180));
        faleMobyNaSpawn = Math.max(1, fale == null ? 2 : fale.getInt("moby-na-spawn", 2));
        faleMnoznikDropMob = Math.max(1.0, fale == null ? 1.5 : fale.getDouble("mnoznik-drop-mob", 1.5));
        faleBossbar = fale == null || fale.getBoolean("bossbar", true);
        faleTytulBossbar = fale == null ? "" : fale.getString("tytul-bossbar", "");
        List<String> typy = fale == null ? null : fale.getStringList("typy-mobow");
        faleTypyMobow = filtrujTypyFali(typy);

        ConfigurationSection mini = root == null ? null : root.getConfigurationSection("mini-godzinny");
        miniWlaczony = mini != null && mini.getBoolean("enabled", true);
        miniCoSec = Math.max(300, mini == null ? 3600 : mini.getInt("co-sekundy", 3600));
        miniCzasSec = Math.max(60, mini == null ? 600 : mini.getInt("czas-trwania-sekundy", 600));
        miniPierwszyDelaySec = Math.max(0, mini == null ? 120 : mini.getInt("pierwszy-po-start-sekundy", 120));
        miniMnoznikZebranych = Math.max(1.0, mini == null ? 2.0 : mini.getDouble("mnoznik-zebranych-cukierkow", 2.0));
        miniMnoznikRespawn = mini == null ? 0.5 : mini.getDouble("mnoznik-respawn-cukierkow", 0.5);
        if (miniMnoznikRespawn <= 0) miniMnoznikRespawn = 0.5;
        miniBossbar = mini == null || mini.getBoolean("bossbar", true);
        miniTytulBossbar = mini == null ? "" : mini.getString("tytul-bossbar", "");
    }

    private List<String> filtrujTypyFali(List<String> typy) {
        List<String> out = new ArrayList<>();
        if (typy != null) {
            for (String id : typy) {
                if (id == null || id.isBlank()) continue;
                MobDef d = mobDefs.get(id.trim());
                if (d != null && d.enabled() && !d.boss()) out.add(d.id());
            }
        }
        if (out.isEmpty()) {
            for (MobDef d : mobDefs.values()) {
                if (d.enabled() && !d.boss()) out.add(d.id());
            }
        }
        return out;
    }

    private void wczytajKrolSkrzynieConfig() {
        krolUsunSkrzyniePoSmierci = cfg.getBoolean("mobs.krol-usun-skrzynie-po-smierci", true);
        krolPromienCzyszczenia = Math.max(16, cfg.getInt("mobs.krol-promien-czyszczenia", 80));
        krolObrazeniaRanking = cfg.getString("mobs.krol-obrazenia-ranking", "per_hit").toLowerCase(Locale.ROOT);
        krolObrazeniaNaUderzenie = Math.max(0.01, cfg.getDouble("mobs.krol-obrazenia-na-uderzenie", 1.0));
        krolMythicDoUsuniecia.clear();
        List<String> lista = cfg.getStringList("mobs.krol-mythic-do-usuniecia");
        if (lista == null || lista.isEmpty()) {
            krolMythicDoUsuniecia.add("DulahanChest");
            krolMythicDoUsuniecia.add("DulahanTheHeadlessHorsemanDeathSummon");
        } else {
            for (String s : lista) {
                if (s != null && !s.isBlank()) {
                    krolMythicDoUsuniecia.add(s.trim());
                }
            }
        }
    }

    public boolean blokujSkrzynieKrola() {
        return blokujSkrzynieKrola;
    }

    public boolean mythicUsuwanyPoSmierciKrola(String internalName) {
        if (internalName == null || internalName.isBlank()) return false;
        for (String w : krolMythicDoUsuniecia) {
            if (w.equalsIgnoreCase(internalName)) return true;
        }
        return false;
    }

    public void reload() {
        wczytajConfig();
        wczytajSpawny();
        wczytajSzablonWorka();
        if (trickOrTreat != null) {
            trickOrTreat.reload();
        }
        if (aktywny) {
            odswiezSpawnyCukierkow();
            odswiezSpawnyMobow();
        }
    }

    public String msg(String klucz, String... pary) {
        String prefix = cfg.getString("wiadomosci.prefix", HalloweenUtil.PREFIX);
        return HalloweenUtil.colorMsg(prefix + msgPlain(klucz, pary));
    }

    public String msgPlain(String klucz, String... pary) {
        String s = cfg.getString("wiadomosci." + klucz, klucz);
        for (int i = 0; i + 1 < pary.length; i += 2) {
            s = s.replace("%" + pary[i] + "%", pary[i + 1]);
        }
        return s;
    }

    public boolean swiatEventu(Location loc) {
        return swiatOk(loc);
    }

    public HalloweenPlayer dane(Player player) {
        return dane(player.getUniqueId(), player.getName());
    }

    public HalloweenPlayer dane(UUID uuid, String nick) {
        return cache.computeIfAbsent(uuid, id -> baza.wczytaj(id, nick == null ? "?" : nick));
    }

    public void zapiszBrudnych() {
        for (HalloweenPlayer p : cache.values()) {
            if (!p.brudny) continue;
            p.brudny = false;
            baza.zapisz(p);
        }
    }

    public void zamknij() {
        kociol.close();
        if (tickTask != null) tickTask.cancel();
        if (saveTask != null) saveTask.cancel();
        resetDynamicEvents();
        usunObiekty();
        usunMoby();
        ukryjKrola();
        resetKrolScoreboard();
        if (sidebar != null) sidebar.usunNadpisanie(this);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        for (HalloweenPlayer p : cache.values()) baza.zapisz(p);
        cache.clear();
    }

    public void start(int sekundy) {
        aktywny = true;
        caloscMs = Math.max(1, sekundy) * 1000L;
        koniecEpoch = System.currentTimeMillis() + caloscMs;
        cfg.set("event.wlaczony", true);
        zapiszCfg();
        if (cfg.getBoolean("event.bossbar", true)) {
            if (bar == null) bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_10);
            bar.setVisible(true);
            for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        }
        odswiezSpawnyCukierkow();
        odswiezSpawnyMobow();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean ma = false;
            for (ItemStack it : p.getInventory().getContents()) {
                if (jestWorkiem(it)) { ma = true; break; }
            }
            if (!ma) p.getInventory().addItem(stworzWorek());
        }
        initDynamicEventsPoStarcie();
        Bukkit.broadcastMessage(msg("event-start"));
    }

    public void stop() {
        aktywny = false;
        kociol.clearBoost();
        cfg.set("event.wlaczony", false);
        zapiszCfg();
        usunObiekty();
        usunMoby();
        pendingCandy.clear();
        pendingMob.clear();
        ukryjKrola();
        resetKrolScoreboard();
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        resetDynamicEvents();
        Bukkit.broadcastMessage(msg("event-stop"));
    }

    private void zapiszCfg() {
        try {
            cfg.save(plik);
        } catch (IOException e) {
            plugin.getLogger().warning("[AHalloween] Zapis configu: " + e.getMessage());
        }
    }

    private void tick() {
        kociol.tick();
        if (aktywny && System.currentTimeMillis() >= koniecEpoch) {
            stop();
            return;
        }
        if (bar != null && bar.isVisible()) {
            long left = pozostaloSekund();
            String tytul = cfg.getString("event.tytul-bossbar", "Halloween %czas%")
                    .replace("%czas%", HalloweenUtil.formatCzas(left));
            bar.setTitle(HalloweenUtil.color(tytul));
            bar.setProgress(Math.min(1, Math.max(0, (double) (koniecEpoch - System.currentTimeMillis()) / caloscMs)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            }
        }
        tickKrolScoreboard();
        if (!aktywny) return;
        tickLicznik++;
        sprawdzRespawny();
        sprawdzMoby();
        tickBossa();
        tickDynamicEvents();
    }

    public boolean jestObiektem(Entity e) {
        return e != null && HalloweenUtil.has(e.getPersistentDataContainer(), keyObject);
    }

    /** Celowanie PPM — dziala tez gdy armor stand ma marker / trudny hitbox. */
    public Entity cukierekWPatrzeniu(Player player, double maxRange) {
        if (player == null || maxRange <= 0) return null;
        org.bukkit.util.Vector dir = player.getEyeLocation().getDirection();
        org.bukkit.Location eye = player.getEyeLocation();
        org.bukkit.World world = player.getWorld();
        try {
            org.bukkit.util.RayTraceResult hit = world.rayTraceEntities(eye, dir, maxRange, 0.55,
                    ent -> jestObiektem(ent) && ent.getWorld().equals(world));
            if (hit != null && hit.getHitEntity() != null) return hit.getHitEntity();
        } catch (Throwable ignored) {
        }
        Entity naj = null;
        double najDist = maxRange * maxRange;
        for (WorldObj o : obiekty.values()) {
            Entity ent = Bukkit.getEntity(o.entity());
            if (ent == null || ent.isDead() || !ent.getWorld().equals(world)) continue;
            org.bukkit.Location c = ent.getLocation().add(0, 0.45, 0);
            org.bukkit.util.Vector to = c.toVector().subtract(eye.toVector());
            double distSq = to.lengthSquared();
            if (distSq > najDist || distSq < 0.01) continue;
            to.normalize();
            if (dir.dot(to) < 0.72) continue;
            najDist = distSq;
            naj = ent;
        }
        return naj;
    }

    public boolean jestMobem(Entity e) {
        return e != null && HalloweenUtil.has(e.getPersistentDataContainer(), keyMob);
    }

    public boolean jestWorkiem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyBag, PersistentDataType.BYTE);
    }

    public ItemStack stworzWorek() {
        ItemStack base = worekSzablon != null ? worekSzablon.clone()
                : HalloweenUtil.nexoOrFallback(plugin, cfg.getString("nexo.worek", ""), Material.PLAYER_HEAD);
        if (base.getType() == Material.PLAYER_HEAD && (worekSzablon == null || worekSzablon.getType() == Material.PLAYER_HEAD)) {
            base = HalloweenUtil.skullFromTexture(cfg.getString("worek.texture", ""));
        }
        List<String> lore = cfg.getStringList("worek.lore");
        HalloweenUtil.named(base, cfg.getString("worek.nazwa", "Worek"), lore);
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyBag, PersistentDataType.BYTE, (byte) 1);
            base.setItemMeta(meta);
        }
        return base;
    }

    public boolean ustawWorekZReki(Player player, boolean confirm) {
        ItemStack reka = player.getInventory().getItemInMainHand();
        if (reka == null || reka.getType() == Material.AIR) return false;
        if (worekSzablon != null && !confirm) return false;
        worekSzablon = reka.clone();
        worekSzablon.setAmount(1);
        File f = new File(plugin.getDataFolder(), "ahalloween-worek.yml");
        YamlConfiguration y = new YamlConfiguration();
        y.set("item", worekSzablon);
        try {
            y.save(f);
        } catch (IOException ignored) {
        }
        return true;
    }

    private void wczytajSzablonWorka() {
        File f = new File(plugin.getDataFolder(), "ahalloween-worek.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ItemStack it = y.getItemStack("item");
        if (it != null) worekSzablon = it;
    }

    public boolean maSzablonWorka() {
        return worekSzablon != null;
    }

    /** Stare ahalloween.yml mialo magnes = zasieg; po reloadzie nadpisujemy nazwe/opis z domyslów JAR. */
    private void naprawStaryMagnes() {
        UpgradeDef u = upgrades.get("magnes");
        if (u == null) return;
        String n = u.nazwa() == null ? "" : u.nazwa().toLowerCase(Locale.ROOT);
        String o = u.opis() == null ? "" : u.opis().toLowerCase(Locale.ROOT);
        boolean stary = n.contains("magnes") || o.contains("zasieg") || o.contains("zasięg");
        if (!stary) return;
        ConfigurationSection def = cfg.getDefaults() == null ? null : cfg.getDefaults().getConfigurationSection("upgrades.magnes");
        if (def == null) return;
        upgrades.put("magnes", new UpgradeDef("magnes",
                def.getString("nazwa", u.nazwa()),
                def.getString("opis", u.opis()),
                u.max(), u.koszty(), u.bonus()));
    }

    public int pojemnosc(HalloweenPlayer p) {
        int bazaCap = cfg.getInt("worek.pojemnosc-bazowa", 200);
        UpgradeDef u = upgrades.get("worek");
        double bonus = u == null ? 0 : u.bonus() * p.ulepszenie("worek");
        return (int) Math.round(bazaCap + bonus);
    }

    /** Ile kliknięć PPM magnes odejmuje od bazy (event.klikniec-na-zbieranie). */
    public int redukcjaKlikniecMagnes(HalloweenPlayer p) {
        UpgradeDef u = upgrades.get("magnes");
        if (u == null) return 0;
        return (int) Math.floor(u.bonus() * p.ulepszenie("magnes"));
    }

    public int klikniecDoZebrania(HalloweenPlayer p) {
        int baza = Math.max(1, cfg.getInt("event.klikniec-na-zbieranie", 20));
        return Math.max(1, baza - redukcjaKlikniecMagnes(p));
    }

    public double bonusSzczescie(HalloweenPlayer p) {
        UpgradeDef u = upgrades.get("szczescie");
        return u == null ? 0 : u.bonus() * p.ulepszenie("szczescie");
    }

    public double bonusLowca(HalloweenPlayer p) {
        UpgradeDef u = upgrades.get("lowca");
        return u == null ? 0 : u.bonus() * p.ulepszenie("lowca");
    }

    public double bonusPospiech(HalloweenPlayer p) {
        UpgradeDef u = upgrades.get("pospiech");
        return u == null ? 0 : u.bonus() * p.ulepszenie("pospiech");
    }

    public double bonusDynia(HalloweenPlayer p) {
        UpgradeDef u = upgrades.get("dynia");
        return u == null ? 0 : u.bonus() * p.ulepszenie("dynia");
    }

    public boolean dodajCukierki(Player player, String candyId, int ile, String zrodlo) {
        if (!aktywny || ile <= 0) return false;
        ile = Math.max(1, (int) Math.round(ile * mnoznikIloscCukierkow(zrodlo)));
        HalloweenPlayer p = dane(player);
        CandyDef def = candies.get(candyId);
        if (def == null || !def.enabled()) return false;
        int limit = cfg.getInt("event.max-cukierkow-na-gracza-na-minute", 40);
        long now = System.currentTimeMillis();
        long okno = collectMinuteTs.getOrDefault(player.getUniqueId(), 0L);
        if (now - okno > 60_000L) {
            collectMinute.put(player.getUniqueId(), 0);
            collectMinuteTs.put(player.getUniqueId(), now);
        }
        int juz = collectMinute.getOrDefault(player.getUniqueId(), 0);
        if (juz >= limit) {
            player.sendMessage(msg("limit"));
            return false;
        }
        int mnoznik = 1 + (ThreadLocalRandom.current().nextDouble() < bonusPospiech(p) ? 1 : 0);
        int daj = Math.max(1, ile * Math.max(1, mnoznik));
        if (ThreadLocalRandom.current().nextDouble() < bonusSzczescie(p)) {
            CandyDef rare = losujRzadki();
            if (rare != null) candyId = rare.id();
        }
        if (ThreadLocalRandom.current().nextDouble() < bonusDynia(p)) daj++;
        int cap = pojemnosc(p);
        int suma = p.sumaCukierkow();
        if (suma >= cap) {
            player.sendMessage(msg("worek-pelny"));
            return false;
        }
        daj = Math.min(daj, cap - suma);
        daj = Math.min(daj, limit - juz);
        if (daj <= 0) return false;
        p.cukierki.merge(candyId, daj, Integer::sum);
        p.statCukierki += daj;
        p.exp += cfg.getInt("poziomy.exp-cukierek", 2) * daj;
        collectMinute.put(player.getUniqueId(), juz + daj);
        sprawdzPoziom(p);
        p.oznacz();
        dodajPostep(p, "candies", daj);
        dodajPostep(p, "candy:" + candyId, daj);
        String histCandy = candyId;
        int histIle = daj;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> baza.historia(p.uuid, histCandy, histIle, zrodlo));
        CandyDef po = candies.getOrDefault(candyId, def);
        player.sendMessage(msg("zebrano", "ilosc", String.valueOf(daj), "nazwa",
                HalloweenUtil.colorMsg(po.name()), "suma", String.valueOf(p.sumaCukierkow()),
                "max", String.valueOf(cap)));
        return true;
    }

    private CandyDef losujRzadki() {
        List<CandyDef> rare = new ArrayList<>();
        for (CandyDef c : candies.values()) {
            if (c.enabled() && (c.rarity().equals("EPIC") || c.rarity().equals("LEGENDARY") || c.rarity().equals("RARE"))) {
                rare.add(c);
            }
        }
        if (rare.isEmpty()) return null;
        return rare.get(ThreadLocalRandom.current().nextInt(rare.size()));
    }

    public void sprawdzPoziom(HalloweenPlayer p) {
        int per = Math.max(1, cfg.getInt("poziomy.exp-na-poziom", 250));
        int max = cfg.getInt("poziomy.max-poziom", 50);
        while (p.poziom < max && p.exp >= p.poziom * per) {
            p.exp -= p.poziom * per;
            p.poziom++;
        }
    }

    public int wartoscCukierkow(HalloweenPlayer p) {
        int v = 0;
        for (Map.Entry<String, Integer> e : p.cukierki.entrySet()) {
            CandyDef d = candies.get(e.getKey());
            v += e.getValue() * (d == null ? 1 : d.value());
        }
        return v;
    }

    public boolean zabierzWartosc(HalloweenPlayer p, int cena) {
        if (wartoscCukierkow(p) < cena) return false;
        int left = cena;
        List<String> ids = new ArrayList<>(p.cukierki.keySet());
        ids.sort((a, b) -> {
            int va = candies.get(a) == null ? 1 : candies.get(a).value();
            int vb = candies.get(b) == null ? 1 : candies.get(b).value();
            return Integer.compare(va, vb);
        });
        for (String id : ids) {
            if (left <= 0) break;
            int val = candies.get(id) == null ? 1 : Math.max(1, candies.get(id).value());
            int have = p.cukierek(id);
            int need = (left + val - 1) / val;
            int take = Math.min(have, need);
            p.cukierki.put(id, have - take);
            if (p.cukierki.get(id) <= 0) p.cukierki.remove(id);
            left -= take * val;
        }
        p.oznacz();
        return left <= 0;
    }

    public void dodajPostep(HalloweenPlayer p, String stat, int ile) {
        String okresD = java.time.LocalDate.now().toString();
        String okresW = java.time.Year.now().getValue() + "-W" + java.time.LocalDate.now().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        for (MissionDef m : missions.values()) {
            if (!pasujeStat(m.stat(), stat)) continue;
            String okres = switch (m.typ()) {
                case "DAILY" -> okresD;
                case "WEEKLY" -> okresW;
                default -> "ONCE";
            };
            String stary = p.misjeOkres.getOrDefault(m.id(), "");
            if (!stary.equals(okres) && !"ONCE".equals(okres)) {
                p.misjeProgress.put(m.id(), 0);
                p.misjeOdebrane.put(m.id(), false);
                p.misjeOkres.put(m.id(), okres);
            } else if (stary.isEmpty()) {
                p.misjeOkres.put(m.id(), okres);
            }
            if (Boolean.TRUE.equals(p.misjeOdebrane.get(m.id()))) continue;
            int now = p.misjeProgress.getOrDefault(m.id(), 0) + ile;
            p.misjeProgress.put(m.id(), now);
        }
        p.oznacz();
    }

    private boolean pasujeStat(String potrzebny, String dany) {
        if (potrzebny.equals(dany)) return true;
        if (potrzebny.equals("candies") && dany.startsWith("candy:")) return true;
        return false;
    }

    public boolean odbierzMisje(Player player, String id) {
        HalloweenPlayer p = dane(player);
        MissionDef m = missions.get(id);
        if (m == null) return false;
        if (Boolean.TRUE.equals(p.misjeOdebrane.get(id))) return false;
        if (p.misjeProgress.getOrDefault(id, 0) < m.cel()) return false;
        p.misjeOdebrane.put(id, true);
        p.statMisje++;
        p.exp += m.exp() + cfg.getInt("poziomy.exp-misja", 25);
        sprawdzPoziom(p);
        dodajCukierki(player, m.nagroda(), m.ile(), "misja");
        p.oznacz();
        player.sendMessage(msg("misja-odebrana", "nazwa", HalloweenUtil.colorMsg(m.nazwa())));
        return true;
    }

    public boolean kupUlepszenie(Player player, String id) {
        HalloweenPlayer p = dane(player);
        UpgradeDef u = upgrades.get(id);
        if (u == null) return false;
        int lvl = p.ulepszenie(id);
        if (lvl >= u.max()) return false;
        int cena = lvl < u.koszty().size() ? u.koszty().get(lvl) : u.koszty().get(u.koszty().size() - 1);
        if (!zabierzWartosc(p, cena)) {
            player.sendMessage(msg("za-malo", "wymagane", String.valueOf(cena)));
            player.sendMessage(msg("posiadasz", "cukierki", String.valueOf(wartoscCukierkow(p))));
            return false;
        }
        p.ulepszenia.put(id, lvl + 1);
        if ("worek".equals(id)) p.pojemnosc = pojemnosc(p);
        p.oznacz();
        player.sendMessage(msg("ulepszono", "nazwa", HalloweenUtil.colorMsg(u.nazwa()), "poziom", String.valueOf(lvl + 1)));
        return true;
    }

    public boolean kup(Player player, String id) {
        HalloweenPlayer p = dane(player);
        ShopDef s = shop.get(id);
        if (s == null) return false;
        int bought = p.sklep.getOrDefault(id, 0);
        if (s.limit() > 0 && bought >= s.limit()) return false;
        if (!zabierzWartosc(p, s.cena())) {
            player.sendMessage(msg("za-malo", "wymagane", String.valueOf(s.cena())));
            player.sendMessage(msg("posiadasz", "cukierki", String.valueOf(wartoscCukierkow(p))));
            return false;
        }
        p.sklep.put(id, bought + 1);
        if (s.crate()) {
            String cid = losujWage();
            int n = ThreadLocalRandom.current().nextInt(3, 9);
            dodajCukierki(player, cid, n, "sklep");
        }
        for (Map.Entry<String, Integer> e : s.grant().entrySet()) {
            dodajCukierki(player, e.getKey(), e.getValue(), "sklep");
        }
        for (String komenda : s.commands()) {
            if (komenda == null || komenda.isBlank()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), komenda
                    .replace("%player%", player.getName())
                    .replace("%gracz%", player.getName()));
        }
        if (!s.daj().isEmpty()) {
            for (HalloweenShopItems.StackDef stack : s.daj()) {
                player.getInventory().addItem(HalloweenShopItems.build(plugin, stack, false));
            }
        } else if (s.dajPrzedmiot() && !s.crate() && s.grant().isEmpty()) {
            player.getInventory().addItem(HalloweenShopItems.build(plugin, s.display(), false));
        }
        p.oznacz();
        player.sendMessage(msg("kupiono", "nazwa", HalloweenUtil.colorMsg(s.display().nazwa())));
        return true;
    }

    public String losujCukierek() {
        return losujWage();
    }

    private String losujWage() {
        int suma = 0;
        for (CandyDef c : candies.values()) if (c.enabled()) suma += Math.max(1, c.weight());
        int r = ThreadLocalRandom.current().nextInt(Math.max(1, suma));
        int acc = 0;
        for (CandyDef c : candies.values()) {
            if (!c.enabled()) continue;
            acc += Math.max(1, c.weight());
            if (r < acc) return c.id();
        }
        return "zwykly";
    }

    public String dodajSpawnCukierka(Location loc, String typ) {
        if (typ == null || typ.isBlank()) typ = "any";
        String key = "spawn.candy." + typ + "." + System.currentTimeMillis();
        zapiszSpawn("candy", key, typ, loc);
        spawnCukierekAt(key, typ, loc);
        return key;
    }

    public String dodajSpawnMoba(Location loc, String typ) {
        String key = "spawn.mob." + typ + "." + System.currentTimeMillis();
        zapiszSpawn("mob", key, typ, loc);
        if (aktywny) spawnMobAt(key, typ, loc);
        return key;
    }

    public int wyczyscSpawny(String prefix) {
        int n = baza.wszystkieKv("spawn." + prefix).size();
        baza.usunKvPrefix("spawn." + prefix);
        usunSpawnyZPliku(prefix);
        return n;
    }

    public int liczbaSpawnowCukierkow() {
        return baza.wszystkieKv("spawn.candy.").size();
    }

    public List<String> opisSpawnowCukierkow() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : baza.wszystkieKv("spawn.candy.").entrySet()) {
            Location loc = deserializuj(e.getValue());
            if (loc == null || loc.getWorld() == null) {
                out.add(e.getKey() + " &8(&cbrak swiata&8)");
                continue;
            }
            out.add(e.getKey() + " &8→ &7" + loc.getWorld().getName()
                    + " &8(&2" + (int) loc.getX() + "&8, &2" + (int) loc.getY()
                    + "&8, &2" + (int) loc.getZ() + "&8)");
        }
        return out;
    }

    private void wczytajSpawny() {
        if (!plikSpawnow.exists()) {
            try (InputStream in = plugin.getResource("ahalloween-spawns.yml")) {
                if (in != null) {
                    plikSpawnow.getParentFile().mkdirs();
                    Files.copy(in, plikSpawnow.toPath());
                }
            } catch (IOException ignored) {
            }
        }
        spawnyCfg = YamlConfiguration.loadConfiguration(plikSpawnow);
        importSpawnySekcji(spawnyCfg.getConfigurationSection("candy"), "candy");
        importSpawnySekcji(spawnyCfg.getConfigurationSection("mob"), "mob");
        importSpawnyDomow(spawnyCfg.getConfigurationSection("dom"));
        importSpawnyZConfigu();
    }

    private void importSpawnyDomow(ConfigurationSection sekcja) {
        if (sekcja == null) return;
        for (String pathKey : sekcja.getKeys(false)) {
            ConfigurationSection s = sekcja.getConfigurationSection(pathKey);
            if (s == null) continue;
            String kvKey = s.getString("key", "spawn.dom." + pathKey);
            Location loc = lokacjaZeSekcji(s);
            if (loc == null) continue;
            baza.ustawKv(kvKey, serializuj(loc));
            domNazwy.put(kvKey, s.getString("nazwa", pathKey.replace('_', ' ')));
        }
    }

    public String nazwaDomu(String spawnDomKey) {
        return domNazwy.get(spawnDomKey);
    }

    public void zapiszDom(String key, String nazwa, Location blockLoc) {
        Location loc = blockLoc.clone().add(0.5, 0, 0.5);
        baza.ustawKv(key, serializuj(loc));
        domNazwy.put(key, nazwa);
        if (spawnyCfg == null) spawnyCfg = new YamlConfiguration();
        String path = "dom." + key.replace("spawn.dom.", "").replace('.', '_');
        spawnyCfg.set(path + ".key", key);
        spawnyCfg.set(path + ".nazwa", nazwa);
        spawnyCfg.set(path + ".world", blockLoc.getWorld().getName());
        spawnyCfg.set(path + ".x", blockLoc.getX());
        spawnyCfg.set(path + ".y", blockLoc.getY());
        spawnyCfg.set(path + ".z", blockLoc.getZ());
        try {
            spawnyCfg.save(plikSpawnow);
        } catch (IOException e) {
            plugin.getLogger().warning("[AHalloween] Zapis domu: " + e.getMessage());
        }
    }

    public void usunDom(String key) {
        baza.usunKv(key);
        domNazwy.remove(key);
        if (spawnyCfg == null) return;
        ConfigurationSection sec = spawnyCfg.getConfigurationSection("dom");
        if (sec == null) return;
        List<String> remove = new ArrayList<>();
        for (String path : sec.getKeys(false)) {
            String full = "dom." + path;
            if (key.equals(spawnyCfg.getString(full + ".key", ""))) {
                remove.add(full);
            }
        }
        for (String p : remove) spawnyCfg.set(p, null);
        try {
            spawnyCfg.save(plikSpawnow);
        } catch (IOException ignored) {
        }
    }

    private void importSpawnyZConfigu() {
        ConfigurationSection cs = cfg.getConfigurationSection("spawns.candy");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                String typ = s.getString("type", s.getString("typ", "any"));
                Location loc = lokacjaZeSekcji(s);
                if (loc == null) continue;
                String key = "spawn.candy." + typ + ".cfg." + id;
                if (baza.kv(key, "").isBlank()) {
                    baza.ustawKv(key, serializuj(loc));
                }
            }
        }
        ConfigurationSection ms = cfg.getConfigurationSection("spawns.mob");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                if (s == null) continue;
                String typ = s.getString("type", s.getString("typ", id));
                Location loc = lokacjaZeSekcji(s);
                if (loc == null) continue;
                String key = "spawn.mob." + typ + ".cfg." + id;
                if (baza.kv(key, "").isBlank()) {
                    baza.ustawKv(key, serializuj(loc));
                }
            }
        }
    }

    private void importSpawnySekcji(ConfigurationSection sekcja, String rodzaj) {
        if (sekcja == null) return;
        for (String key : sekcja.getKeys(false)) {
            ConfigurationSection s = sekcja.getConfigurationSection(key);
            if (s == null) continue;
            String typ = s.getString("type", rodzaj.equals("candy") ? "any" : key);
            Location loc = lokacjaZeSekcji(s);
            if (loc == null) continue;
            String kvKey = s.getString("key", "");
            if (kvKey.isBlank()) {
                kvKey = key.startsWith("spawn.") ? key : ("spawn." + rodzaj + "." + typ + "." + key);
            }
            baza.ustawKv(kvKey, serializuj(loc));
        }
    }

    private Location lokacjaZeSekcji(ConfigurationSection s) {
        String world = s.getString("world", s.getString("swiat", ""));
        if (world.isBlank()) return null;
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw", 0), (float) s.getDouble("pitch", 0));
    }

    private void zapiszSpawn(String rodzaj, String key, String typ, Location loc) {
        baza.ustawKv(key, serializuj(loc));
        if (spawnyCfg == null) spawnyCfg = new YamlConfiguration();
        String path = rodzaj + "." + key.replace('.', '_');
        spawnyCfg.set(path + ".type", typ);
        spawnyCfg.set(path + ".world", loc.getWorld().getName());
        spawnyCfg.set(path + ".x", loc.getX());
        spawnyCfg.set(path + ".y", loc.getY());
        spawnyCfg.set(path + ".z", loc.getZ());
        spawnyCfg.set(path + ".yaw", loc.getYaw());
        spawnyCfg.set(path + ".pitch", loc.getPitch());
        spawnyCfg.set(path + ".key", key);
        try {
            spawnyCfg.save(plikSpawnow);
        } catch (IOException e) {
            plugin.getLogger().warning("[AHalloween] Zapis ahalloween-spawns.yml: " + e.getMessage());
        }
    }

    private void usunSpawnyZPliku(String kvPrefix) {
        if (spawnyCfg == null) return;
        String rodzaj = kvPrefix.startsWith("mob.") ? "mob" : kvPrefix.startsWith("dom.") ? "dom" : "candy";
        if ("dom".equals(rodzaj)) {
            domNazwy.keySet().removeIf(k -> k.startsWith("spawn.dom."));
        }
        ConfigurationSection sec = spawnyCfg.getConfigurationSection(rodzaj);
        if (sec == null) return;
        List<String> remove = new ArrayList<>();
        for (String path : sec.getKeys(false)) {
            String full = rodzaj + "." + path;
            String storedKey = spawnyCfg.getString(full + ".key", "");
            if (storedKey.startsWith("spawn." + kvPrefix)) remove.add(full);
        }
        for (String p : remove) spawnyCfg.set(p, null);
        try {
            spawnyCfg.save(plikSpawnow);
        } catch (IOException ignored) {
        }
    }

    public List<String> listaSpawn(String prefix) {
        return new ArrayList<>(baza.wszystkieKv("spawn." + prefix).keySet());
    }

    public void wyczyscCukierkiSwiata() {
        usunObiekty();
    }

    public int recznySpawnMoba(String typ, Location loc, int ile) {
        int n = 0;
        for (int i = 0; i < ile; i++) {
            if (spawnMobAt("manual." + UUID.randomUUID(), typ, loc.clone().add(
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0,
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5)))) n++;
        }
        return n;
    }

    private void odswiezSpawnyCukierkow() {
        for (Map.Entry<String, String> e : baza.wszystkieKv("spawn.candy.").entrySet()) {
            Location loc = deserializuj(e.getValue());
            if (loc == null) continue;
            String typ = e.getKey().substring("spawn.candy.".length());
            int dot = typ.indexOf('.');
            String candyId = dot > 0 ? typ.substring(0, dot) : "any";
            spawnCukierekAt(e.getKey(), candyId, loc);
        }
    }

    private void odswiezSpawnyMobow() {
        for (Map.Entry<String, String> e : baza.wszystkieKv("spawn.mob.").entrySet()) {
            Location loc = deserializuj(e.getValue());
            if (loc == null) continue;
            String rest = e.getKey().substring("spawn.mob.".length());
            int dot = rest.indexOf('.');
            String mobId = dot > 0 ? rest.substring(0, dot) : rest;
            spawnMobAt(e.getKey(), mobId, loc);
        }
    }

    private void sprawdzRespawny() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> e : baza.wszystkieKv("spawn.candy.").entrySet()) {
            boolean zajete = false;
            for (WorldObj o : obiekty.values()) {
                if (o.spawnKey().equals(e.getKey())) { zajete = true; break; }
            }
            if (zajete) continue;
            Long ready = pendingCandy.get(e.getKey());
            if (ready != null && now < ready) continue;
            pendingCandy.remove(e.getKey());
            Location loc = deserializuj(e.getValue());
            if (loc == null) continue;
            String rest = e.getKey().substring("spawn.candy.".length());
            int dot = rest.indexOf('.');
            String candyId = dot > 0 ? rest.substring(0, dot) : "any";
            spawnCukierekAt(e.getKey(), candyId, loc);
        }
    }

    private void sprawdzMoby() {
        moby.entrySet().removeIf(en -> {
            Entity e = entity(en.getKey());
            return e == null || e.isDead();
        });
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> e : baza.wszystkieKv("spawn.mob.").entrySet()) {
            boolean zajete = false;
            for (LiveMob m : moby.values()) {
                if (m.spawnKey().equals(e.getKey())) { zajete = true; break; }
            }
            if (zajete) continue;
            Long ready = pendingMob.get(e.getKey());
            if (ready != null && now < ready) continue;
            pendingMob.remove(e.getKey());
            Location loc = deserializuj(e.getValue());
            if (loc == null) continue;
            String rest = e.getKey().substring("spawn.mob.".length());
            int dot = rest.indexOf('.');
            String mobId = dot > 0 ? rest.substring(0, dot) : rest;
            int alive = 0;
            for (LiveMob m : moby.values()) if (m.mobId().equals(mobId)) alive++;
            MobDef def = mobDefs.get(mobId);
            if (def != null && alive >= def.maxAlive()) continue;
            spawnMobAt(e.getKey(), mobId, loc);
        }
    }

    private boolean swiatOk(Location loc) {
        List<String> swiaty = cfg.getStringList("event.swiaty");
        if (swiaty == null || swiaty.isEmpty()) return true;
        return loc.getWorld() != null && swiaty.contains(loc.getWorld().getName());
    }

    private void spawnCukierekAt(String spawnKey, String candyId, Location loc) {
        if (loc.getWorld() == null || !swiatOk(loc)) return;
        CandyDef def = "any".equals(candyId) ? candies.get(losujWage()) : candies.get(candyId);
        if (def == null || !def.enabled()) return;
        int aktywne = 0;
        for (WorldObj o : obiekty.values()) if (o.candyId().equals(def.id())) aktywne++;
        if (aktywne >= def.maxActive()) return;
        loc.getChunk().load();
        double yOff = cfg.getDouble("event.cukierki-offset-y", 0.0);
        boolean marker = cfg.getBoolean("event.cukierki-marker", false);
        boolean small = cfg.getBoolean("event.cukierki-male", true);
        ArmorStand stand = loc.getWorld().spawn(loc.clone().add(0, yOff, 0), ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(marker);
            as.setGravity(cfg.getBoolean("event.cukierki-gravity", false));
            as.setSmall(small);
            as.setBasePlate(false);
            as.setArms(false);
            as.setSilent(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(true);
            as.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(HalloweenUtil.color(def.name())));
            as.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
            as.getPersistentDataContainer().set(keyObject, PersistentDataType.STRING, def.id());
            as.getPersistentDataContainer().set(keyCandy, PersistentDataType.STRING, spawnKey);
        });
        ItemStack head = HalloweenUtil.nexoOrFallback(plugin, def.nexo(), Material.PLAYER_HEAD);
        if (head.getType() == Material.PLAYER_HEAD) head = HalloweenUtil.skullFromTexture(def.texture());
        HalloweenUtil.named(head, def.name(), def.lore());
        stand.getEquipment().setHelmet(head);
        obiekty.put(stand.getUniqueId(), new WorldObj(stand.getUniqueId(), def.id(), spawnKey, System.currentTimeMillis() + 400));
    }

    private boolean spawnMobAt(String spawnKey, String mobId, Location loc) {
        MobDef def = mobDefs.get(mobId);
        if (def == null || !def.enabled() || loc.getWorld() == null || !swiatOk(loc)) return false;
        loc.getChunk().load();
        LivingEntity mob = null;
        boolean mythic = def.mythicMob() != null && !def.mythicMob().isBlank();
        if (mythic) {
            mob = HalloweenMythicSpawner.spawn(plugin, loc, def.mythicMob(), def.mythicLevel());
        }
        if (mob == null && cfg.getBoolean("mobs.vanilla-fallback", true) && !mythic) {
            mob = boniec.boss.BossSpawn.pozwol(() -> {
                Entity e = loc.getWorld().spawnEntity(loc, def.type());
                return e instanceof LivingEntity le ? le : null;
            });
        } else if (mob == null && mythic && cfg.getBoolean("mobs.vanilla-fallback", false)) {
            plugin.getLogger().warning("[AHalloween] Fallback vanilla dla " + mobId + " (Mythic nie zadzialal).");
            mob = boniec.boss.BossSpawn.pozwol(() -> {
                Entity e = loc.getWorld().spawnEntity(loc, def.type());
                return e instanceof LivingEntity le ? le : null;
            });
        }
        if (mob == null) return false;
        if (def.overrideHp()) {
            HalloweenUtil.ustawHp(mob, def.hp());
        }
        if (!mythic || def.overrideName()) {
            HalloweenUtil.ustawObrazenia(mob, def.dmg());
            mob.setCustomNameVisible(true);
            mob.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(HalloweenUtil.colorMsg(def.name())));
        }
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);
        mob.getPersistentDataContainer().set(keyMob, PersistentDataType.STRING, def.id());
        if (!mythic && mob.getEquipment() != null) {
            ItemStack head = HalloweenUtil.skullFromTexture(cfg.getString("worek.texture", ""));
            mob.getEquipment().setHelmet(head);
            mob.getEquipment().setHelmetDropChance(0f);
        }
        if (def.boss()) {
            blokujSkrzynieKrola = false;
            Bukkit.broadcastMessage(msg("boss-spawn"));
            if (!mythic) mob.setGlowing(true);
            startKrolScoreboard(mob.getUniqueId());
        }
        moby.put(mob.getUniqueId(), new LiveMob(mob.getUniqueId(), def.id(), spawnKey, def.boss()));
        return true;
    }

    public void zbierz(Player player, Entity entity, boolean prawy) {
        if (!aktywny || entity == null) return;
        WorldObj obj = obiekty.get(entity.getUniqueId());
        if (obj == null) return;
        if (System.currentTimeMillis() < obj.immuneDo()) return;
        CandyDef def = candies.get(obj.candyId());
        if (def == null) return;
        if (!prawy) return;
        HalloweenPlayer hpGracz = dane(player);
        double zasieg = def.radius();
        if (player.getLocation().distanceSquared(entity.getLocation()) > zasieg * zasieg) return;
        long odstep = Math.max(50L, cfg.getLong("event.odstep-klikniecia-ms", 120L));
        if (lastCollect.getOrDefault(player.getUniqueId(), 0L) + odstep > System.currentTimeMillis()) return;
        lastCollect.put(player.getUniqueId(), System.currentTimeMillis());

        int wymagane = klikniecDoZebrania(hpGracz);
        String progKey = kluczPostepu(entity.getUniqueId(), player.getUniqueId());
        int postep = klikPostep.getOrDefault(progKey, 0) + 1;
        klikPostep.put(progKey, postep);
        dodajPostep(hpGracz, "clicks", 1);
        HalloweenUtil.efektKlikniecia(entity.getLocation(), postep, wymagane);

        if (postep < wymagane) {
            wyslijPasekZbierania(player, postep, wymagane);
            return;
        }

        if (!przetwarzane.add(entity.getUniqueId())) {
            klikPostep.put(progKey, wymagane - 1);
            return;
        }
        try {
            WorldObj akt = obiekty.get(entity.getUniqueId());
            if (akt == null) return;
            int ile = ThreadLocalRandom.current().nextInt(def.min(), def.max() + 1);
            String candy = def.crate() ? losujWage() : def.id();
            if (!dodajCukierki(player, candy, ile, def.crate() ? "skrzynia" : "zbieranie")) {
                klikPostep.put(progKey, wymagane - 1);
                return;
            }
            HalloweenUtil.efektZbierania(entity.getLocation());
            wyczyscPostepCukierka(entity.getUniqueId());
            obiekty.remove(entity.getUniqueId());
            entity.remove();
            pendingCandy.put(akt.spawnKey(), System.currentTimeMillis() + sekundyRespawnCukierka(def) * 1000L);
        } finally {
            przetwarzane.remove(entity.getUniqueId());
        }
    }

    private static String kluczPostepu(UUID entity, UUID gracz) {
        return entity + "|" + gracz;
    }

    private void wyczyscPostepCukierka(UUID entityId) {
        String prefix = entityId + "|";
        klikPostep.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private void wyslijPasekZbierania(Player player, int postep, int wymagane) {
        String szablon = cfg.getString("wiadomosci.zbieranie-postep",
                "&7ppm &8| %pasek%");
        String tekst = szablon.replace("%pasek%", HalloweenUtil.pasek(postep, wymagane, 12))
                .replace("%postep%", String.valueOf(postep))
                .replace("%wymagane%", String.valueOf(wymagane));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(HalloweenUtil.colorMsg(tekst)));
    }

    /**
     * Ile dmg wliczyc do rankingu / scoreboarda Krola Dyni.
     * Mythic + resistance czesto obcina {@link org.bukkit.event.entity.EntityDamageEvent#getFinalDamage()} do ~1.
     */
    public double obrazeniaDoRankingu(LivingEntity victim, org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        LiveMob live = moby.get(victim.getUniqueId());
        if (live == null || !live.boss()) {
            return event.getFinalDamage();
        }
        return switch (krolObrazeniaRanking) {
            case "final" -> Math.max(0, event.getFinalDamage());
            case "raw", "bazowe" -> Math.max(0, event.getDamage());
            default -> {
                if (event.getFinalDamage() <= 0 && event.getDamage() <= 0) {
                    yield 0;
                }
                yield krolObrazeniaNaUderzenie;
            }
        };
    }

    public void zapiszObrazenia(UUID mob, UUID gracz, double dmg) {
        if (dmg <= 0) return;
        obrazenia.computeIfAbsent(mob, k -> new ConcurrentHashMap<>()).merge(gracz, dmg, Double::sum);
        LiveMob live = moby.get(mob);
        if (live != null && live.boss()) {
            krolOstatniAtak = gracz;
            Player pl = Bukkit.getPlayer(gracz);
            krolSbNicki.put(gracz, pl != null ? pl.getName() : Bukkit.getOfflinePlayer(gracz).getName());
        }
    }

    public void mobZginal(LivingEntity entity, Player killer) {
        LiveMob live = moby.remove(entity.getUniqueId());
        if (live == null) return;
        MobDef def = mobDefs.get(live.mobId());
        if (def == null) return;
        Map<UUID, Double> dmg = obrazenia.remove(entity.getUniqueId());
        if (dmg == null || dmg.isEmpty()) {
            if (killer != null) dmg = Map.of(killer.getUniqueId(), 1.0);
            else return;
        }
        double suma = 0;
        for (double v : dmg.values()) suma += v;
        if (suma <= 0) return;
        if (def.boss()) {
            Bukkit.broadcastMessage(msg("boss-kill"));
            for (Map.Entry<UUID, Double> e : dmg.entrySet()) {
                Player pl = Bukkit.getPlayer(e.getKey());
                if (pl != null) krolSbNicki.put(e.getKey(), pl.getName());
            }
            odswiezKrolTop(dmg);
            wyplacNagrodyKrola(dmg, killer);
            zakonczKrolScoreboardPoZabiciu();
            if (krolUsunSkrzyniePoSmierci) {
                poSmierciKrolaUsunSkrzynie(entity);
            }
        }
        for (Map.Entry<UUID, Double> e : dmg.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            double share = e.getValue() / suma;
            int ile = Math.max(1, (int) Math.round((def.cMin() + ThreadLocalRandom.current().nextInt(
                    Math.max(1, def.cMax() - def.cMin() + 1))) * share * (1.0 + bonusLowca(dane(p)))));
            dodajCukierki(p, def.candyId(), ile, def.boss() ? "boss" : "mob");
            HalloweenPlayer hp = dane(p);
            hp.statMoby++;
            if (def.boss()) {
                hp.statBossy++;
                hp.exp += cfg.getInt("poziomy.exp-boss", 80);
                dodajPostep(hp, "bosses", 1);
            }
            hp.exp += cfg.getInt("poziomy.exp-mob", 8);
            dodajPostep(hp, "mob:" + def.id(), 1);
            sprawdzPoziom(hp);
            hp.oznacz();
        }
        if (live.boss()) ukryjKrola();
        if (!live.spawnKey().startsWith("manual.")) {
            pendingMob.put(live.spawnKey(), System.currentTimeMillis() + Math.max(20, def.respawn()) * 1000L);
        }
    }

    private void poSmierciKrolaUsunSkrzynie(LivingEntity entity) {
        blokujSkrzynieKrola = true;
        Location center = entity.getLocation().clone();
        for (long delay : List.of(0L, 2L, 5L, 10L, 20L, 40L, 60L)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> usunMythicKrolaWZasięgu(center), delay);
        }
    }

    private void usunMythicKrolaWZasięgu(Location center) {
        if (center.getWorld() == null || krolMythicDoUsuniecia.isEmpty()) return;
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return;
        double r2 = (double) krolPromienCzyszczenia * krolPromienCzyszczenia;
        for (Entity e : center.getWorld().getEntities()) {
            if (!(e instanceof LivingEntity) || e.getLocation().distanceSquared(center) > r2) continue;
            try {
                var opt = io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getActiveMob(e.getUniqueId());
                if (opt.isEmpty()) continue;
                io.lumine.mythic.api.mobs.MythicMob mythicMob = opt.get().getType();
                if (mythicMob == null) continue;
                String typ = mythicMob.getInternalName();
                if (mythicUsuwanyPoSmierciKrola(typ)) {
                    e.remove();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void tickBossa() {
        LiveMob boss = null;
        for (LiveMob m : moby.values()) {
            if (m.boss()) { boss = m; break; }
        }
        if (boss == null) {
            ukryjKrola();
            return;
        }
        MobDef bossDef = mobDefs.get(boss.mobId());
        if (bossDef != null && bossDef.mythicMob() != null && !bossDef.mythicMob().isBlank()
                && cfg.getBoolean("mobs.wylacz-pasek-przy-mythic-boss", true)) {
            ukryjKrola();
            return;
        }
        Entity raw = entity(boss.entity());
        if (!(raw instanceof LivingEntity le) || le.isDead()) {
            ukryjKrola();
            return;
        }
        double max = le.getMaxHealth();
        double hp = le.getHealth();
        double pct = max <= 0 ? 0 : hp / max;
        int faza = pct > 0.66 ? 1 : pct > 0.33 ? 2 : 3;
        if (krolBar == null) {
            krolBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_12);
        }
        krolBar.setVisible(true);
        krolBar.setProgress(Math.min(1, Math.max(0, pct)));
        krolBar.setTitle(HalloweenUtil.color(cfg.getString("event.tytul-krol",
                "&#FF7518&lKról Dyni &#FFFFFFfaza %faza%").replace("%faza%", String.valueOf(faza))));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(le.getWorld()) && p.getLocation().distanceSquared(le.getLocation()) <= 80 * 80) {
                if (!krolBar.getPlayers().contains(p)) krolBar.addPlayer(p);
            } else {
                krolBar.removePlayer(p);
            }
        }
        if (faza != krolFaza && krolFaza != 0) {
            Bukkit.broadcastMessage(msg("boss-faza", "faza", String.valueOf(faza)));
        }
        krolFaza = faza;
        MobDef def = mobDefs.get(boss.mobId());
        int interwal = faza == 3 ? 4 : faza == 2 ? 6 : 8;
        if (tickLicznik % interwal == 0) {
            Location c = le.getLocation();
            if (c.getWorld() != null) {
                c.getWorld().spawnParticle(org.bukkit.Particle.FLAME, c.clone().add(0, 1, 0), 40, 2.2, 0.6, 2.2, 0.02);
                c.getWorld().spawnParticle(org.bukkit.Particle.WITCH, c.clone().add(0, 1.2, 0), 24, 1.8, 0.5, 1.8, 0.01);
                c.getWorld().playSound(c, org.bukkit.Sound.ENTITY_WITHER_SHOOT, 0.7f, 0.6f);
                double dmg = (def == null ? 4 : def.dmg()) * (0.6 + 0.3 * faza);
                for (Player p : c.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(c) <= 36) {
                        p.damage(dmg, le);
                    }
                }
            }
        }
        if (faza >= 2 && tickLicznik % (faza == 3 ? 16 : 24) == 0 && def != null) {
            List<String> miniony = def.miniony();
            if (!miniony.isEmpty()) {
                String typ = miniony.get(ThreadLocalRandom.current().nextInt(miniony.size()));
                recznySpawnMoba(typ, le.getLocation(), faza == 3 ? 2 : 1);
            }
        }
    }

    private void ukryjKrola() {
        krolFaza = 0;
        if (krolBar != null) {
            krolBar.removeAll();
            krolBar.setVisible(false);
        }
    }

    public void usunObiekty() {
        for (UUID id : List.copyOf(obiekty.keySet())) {
            wyczyscPostepCukierka(id);
            Entity e = entity(id);
            if (e != null) e.remove();
        }
        obiekty.clear();
        klikPostep.clear();
    }

    public void usunMoby() {
        for (UUID id : List.copyOf(moby.keySet())) {
            Entity e = entity(id);
            if (e != null) e.remove();
        }
        moby.clear();
        obrazenia.clear();
        resetKrolScoreboard();
    }

    private void initDynamicEventsPoStarcie() {
        long now = System.currentTimeMillis();
        faleKoniecAt = 0;
        miniKoniecAt = 0;
        nastepnaFalaPo = now + faleCoMinuty * 60_000L;
        nastepnyMiniPo = now + miniPierwszyDelaySec * 1000L;
    }

    private void resetDynamicEvents() {
        faleKoniecAt = 0;
        miniKoniecAt = 0;
        nastepnaFalaPo = 0;
        nastepnyMiniPo = 0;
        ukryjDynamicBossbar(faleBar);
        ukryjDynamicBossbar(miniBar);
    }

    private void ukryjDynamicBossbar(BossBar b) {
        if (b == null) return;
        b.removeAll();
        b.setVisible(false);
    }

    private boolean trwaFala() {
        return faleKoniecAt > System.currentTimeMillis();
    }

    private boolean trwaMiniEvent() {
        return miniKoniecAt > System.currentTimeMillis();
    }

    private double mnoznikIloscCukierkow(String zrodlo) {
        double m = 1.0;
        if (trwaMiniEvent()) m *= miniMnoznikZebranych;
        if (trwaFala() && zrodlo != null) {
            String z = zrodlo.toLowerCase(Locale.ROOT);
            if ("mob".equals(z) || "boss".equals(z)) m *= faleMnoznikDropMob;
        }
        // The cauldron guarantees its multiplier without multiplying the timed events again.
        return Math.max(m, kociol.multiplier(zrodlo));
    }

    private long sekundyRespawnCukierka(CandyDef def) {
        int sec = Math.max(10, def.respawn());
        if (trwaMiniEvent() && miniMnoznikRespawn > 0) {
            sec = (int) Math.max(5, Math.round(sec * miniMnoznikRespawn));
        }
        return sec;
    }

    private void tickDynamicEvents() {
        if (!aktywny) return;
        long now = System.currentTimeMillis();

        if (miniWlaczony) {
            if (miniKoniecAt > 0 && now >= miniKoniecAt) {
                miniKoniecAt = 0;
                ukryjDynamicBossbar(miniBar);
                Bukkit.broadcastMessage(msg("mini-koniec"));
            } else if (miniKoniecAt <= 0 && nastepnyMiniPo > 0 && now >= nastepnyMiniPo) {
                miniKoniecAt = now + miniCzasSec * 1000L;
                nastepnyMiniPo += miniCoSec * 1000L;
                Bukkit.broadcastMessage(msg("mini-start",
                        "czas", HalloweenUtil.formatCzas(miniCzasSec),
                        "mnoznik", formatMnoznik(miniMnoznikZebranych)));
            }
        }

        if (faleWlaczone) {
            if (faleKoniecAt > 0 && now >= faleKoniecAt) {
                faleKoniecAt = 0;
                ukryjDynamicBossbar(faleBar);
                nastepnaFalaPo = now + faleCoMinuty * 60_000L;
                Bukkit.broadcastMessage(msg("fala-koniec"));
            } else if (faleKoniecAt <= 0 && nastepnaFalaPo > 0 && now >= nastepnaFalaPo) {
                if (faleTylkoNocMc && !czyNocNaMapieEventu()) {
                    nastepnaFalaPo = now + 5 * 60_000L;
                } else {
                    uruchomFale();
                    faleKoniecAt = now + faleCzasSec * 1000L;
                    nastepnaFalaPo = now + faleCoMinuty * 60_000L;
                }
            }
        }

        odswiezDynamicBossbary(now);
    }

    private static String formatMnoznik(double m) {
        if (Math.abs(m - Math.rint(m)) < 0.001) return String.valueOf((int) Math.rint(m));
        return String.format(Locale.ROOT, "%.1f", m);
    }

    private boolean czyNocNaMapieEventu() {
        List<String> swiaty = cfg.getStringList("event.swiaty");
        if (swiaty != null && !swiaty.isEmpty()) {
            for (String name : swiaty) {
                World w = Bukkit.getWorld(name);
                if (w != null && minecraftNoc(w)) return true;
            }
            return false;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (swiatOk(p.getLocation()) && minecraftNoc(p.getWorld())) return true;
        }
        World def = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return def != null && minecraftNoc(def);
    }

    private static boolean minecraftNoc(World w) {
        long t = w.getTime() % 24000L;
        return t >= 13000 && t <= 23000;
    }

    private void uruchomFale() {
        if (faleTypyMobow.isEmpty()) return;
        int moby = 0;
        for (Map.Entry<String, String> e : baza.wszystkieKv("spawn.mob.").entrySet()) {
            Location loc = deserializuj(e.getValue());
            if (loc == null) continue;
            for (int i = 0; i < faleMobyNaSpawn; i++) {
                String typ = faleTypyMobow.get(ThreadLocalRandom.current().nextInt(faleTypyMobow.size()));
                Location at = loc.clone().add(
                        ThreadLocalRandom.current().nextDouble(-1.8, 1.8), 0,
                        ThreadLocalRandom.current().nextDouble(-1.8, 1.8));
                if (spawnMobAt("wave." + UUID.randomUUID(), typ, at)) moby++;
            }
        }
        Bukkit.broadcastMessage(msg("fala-start", "moby", String.valueOf(moby)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (swiatOk(p.getLocation())) {
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PHANTOM_AMBIENT, 0.9f, 0.7f);
            }
        }
    }

    private void odswiezDynamicBossbary(long now) {
        if (faleBossbar && trwaFala()) {
            if (faleBar == null) {
                faleBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SEGMENTED_6);
            }
            long left = Math.max(0, (faleKoniecAt - now) / 1000L);
            String tytul = (faleTytulBossbar == null || faleTytulBossbar.isBlank()
                    ? "&#35105Enoc duchow &8| &#9B30FF%czas%" : faleTytulBossbar)
                    .replace("%czas%", HalloweenUtil.formatCzas(left));
            faleBar.setTitle(HalloweenUtil.color(tytul));
            faleBar.setProgress(Math.min(1, Math.max(0, (double) left / Math.max(1, faleCzasSec))));
            faleBar.setVisible(true);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (swiatOk(p.getLocation())) {
                    if (!faleBar.getPlayers().contains(p)) faleBar.addPlayer(p);
                } else {
                    faleBar.removePlayer(p);
                }
            }
        } else if (faleBar != null && faleBar.isVisible()) {
            ukryjDynamicBossbar(faleBar);
        }

        if (miniBossbar && trwaMiniEvent()) {
            if (miniBar == null) {
                miniBar = Bukkit.createBossBar("", BarColor.PINK, BarStyle.SEGMENTED_10);
            }
            long left = Math.max(0, (miniKoniecAt - now) / 1000L);
            String tytul = (miniTytulBossbar == null || miniTytulBossbar.isBlank()
                    ? "&#FF7518slodki szal &8| x%mnoznik% &7%czas%" : miniTytulBossbar)
                    .replace("%czas%", HalloweenUtil.formatCzas(left))
                    .replace("%mnoznik%", formatMnoznik(miniMnoznikZebranych));
            miniBar.setTitle(HalloweenUtil.color(tytul));
            miniBar.setProgress(Math.min(1, Math.max(0, (double) left / Math.max(1, miniCzasSec))));
            miniBar.setVisible(true);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (swiatOk(p.getLocation())) {
                    if (!miniBar.getPlayers().contains(p)) miniBar.addPlayer(p);
                } else {
                    miniBar.removePlayer(p);
                }
            }
        } else if (miniBar != null && miniBar.isVisible()) {
            ukryjDynamicBossbar(miniBar);
        }
    }

    private Entity entity(UUID id) {
        return Bukkit.getEntity(id);
    }

    public String serializuj(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";"
                + loc.getYaw() + ";" + loc.getPitch();
    }

    public Location deserializuj(String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    p.length > 4 ? Float.parseFloat(p[4]) : 0f, p.length > 5 ? Float.parseFloat(p[5]) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Set<String> mobIds() {
        return Collections.unmodifiableSet(mobDefs.keySet());
    }

    public Set<String> candyIds() {
        return Collections.unmodifiableSet(candies.keySet());
    }

    // ── Scoreboard Króla Dyni (SidebarManager.Nadpisanie) ─────────────────

    private void wczytajKrolScoreboardConfig() {
        krolSbWlaczony = cfg.getBoolean("krol-scoreboard.wlaczony", true);
        krolSbPoSekundy = Math.max(0, cfg.getInt("krol-scoreboard.sekundy-po-zabiciu", 12));
        krolSbTytul = cfg.getString("krol-scoreboard.tytul",
                "&#FF7518&l子 &#9B30FF&lkrol &#35105E&ldyni");
        krolSbLinie = new ArrayList<>(cfg.getStringList("krol-scoreboard.linie"));
        if (krolSbLinie.isEmpty()) {
            krolSbLinie = List.of(
                    "",
                    "ꕋ &f%nick_1% &8- &#FF7518%punkty_1% &7dmg",
                    "ꕌ &f%nick_2% &8- &#FF7518%punkty_2% &7dmg",
                    "ꕍ &f%nick_3% &8- &#FF7518%punkty_3% &7dmg",
                    "",
                    "%czas_linia%",
                    "",
                    "&#FF7518&l子 &#35105E&krol dyni &7na mapie",
                    " &7top dmg = &#FF7518cukierki",
                    ""
            );
        }
        krolSbCzasTrwa = cfg.getString("krol-scoreboard.czas-trwa",
                "&#35105E&lhp &8| &#FF7518%hp% &8(&7%hp_pct%&8)");
        krolSbCzasPo = cfg.getString("krol-scoreboard.czas-po", "&#FF7518&lpokonany! &7nagrody w worku");
        krolSbBrakNick = cfg.getString("krol-scoreboard.brak-gracza", "&8-");
    }

    private void startKrolScoreboard(UUID bossEntity) {
        if (!krolSbWlaczony) return;
        krolSbStan = KrolSbStan.WALKA;
        krolSbBoss = bossEntity;
        krolOstatniAtak = null;
        krolSbNicki.clear();
        krolSbTop = Collections.emptyList();
        krolSbWierszCzasu = krolSbCzasTrwa;
    }

    private void zakonczKrolScoreboardPoZabiciu() {
        if (!krolSbWlaczony || krolSbStan != KrolSbStan.WALKA) return;
        krolSbStan = KrolSbStan.PO_ZABICIU;
        krolSbBoss = null;
        krolSbDo = System.currentTimeMillis() + krolSbPoSekundy * 1000L;
        krolSbWierszCzasu = krolSbCzasPo;
    }

    private void resetKrolScoreboard() {
        krolSbStan = KrolSbStan.WYLACZONY;
        krolSbBoss = null;
        krolOstatniAtak = null;
        krolSbTop = Collections.emptyList();
        krolSbNicki.clear();
        krolSbWierszCzasu = "";
    }

    private void tickKrolScoreboard() {
        if (krolSbStan == KrolSbStan.WYLACZONY) return;
        if (krolSbStan == KrolSbStan.PO_ZABICIU && System.currentTimeMillis() >= krolSbDo) {
            resetKrolScoreboard();
            return;
        }
        if (krolSbStan == KrolSbStan.WALKA && krolSbBoss != null) {
            Map<UUID, Double> dmg = obrazenia.get(krolSbBoss);
            if (dmg != null) odswiezKrolTop(dmg);
            Entity raw = entity(krolSbBoss);
            if (raw instanceof LivingEntity le && !le.isDead()) {
                double max = le.getMaxHealth();
                double hp = le.getHealth();
                int pct = max <= 0 ? 0 : (int) Math.round(100.0 * hp / max);
                String hpTxt = (int) Math.ceil(hp) + "/" + (int) Math.ceil(max);
                krolSbWierszCzasu = krolSbCzasTrwa.replace("%hp%", hpTxt).replace("%hp_pct%", pct + "%");
            }
        }
    }

    private void odswiezKrolTop(Map<UUID, Double> dmg) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(dmg.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        krolSbTop = list;
    }

    private void wyplacNagrodyKrola(Map<UUID, Double> dmg, Player killer) {
        if (!cfg.getBoolean("krol-scoreboard.nagrody.wlaczone", true)) return;
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(dmg.entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        String[] miejsca = {"top-1", "top-2", "top-3"};
        for (int i = 0; i < miejsca.length && i < ranking.size(); i++) {
            UUID uuid = ranking.get(i).getKey();
            String sciezka = "krol-scoreboard.nagrody." + miejsca[i];
            int ile = cfg.getInt(sciezka + ".ilosc", 0);
            String candy = cfg.getString(sciezka + ".candy", "legendarny");
            if (ile <= 0) continue;
            nagrodaKrolDoWorka(uuid, candy, ile, "krol-top", String.valueOf(i + 1));
        }
        UUID ostatni = killer != null ? killer.getUniqueId() : krolOstatniAtak;
        if (ostatni != null) {
            int ile = cfg.getInt("krol-scoreboard.nagrody.ostatni-cios.ilosc", 0);
            String candy = cfg.getString("krol-scoreboard.nagrody.ostatni-cios.candy", "przeklety");
            if (ile > 0) nagrodaKrolDoWorka(ostatni, candy, ile, "krol-ostatni", "0");
        }
    }

    private void nagrodaKrolDoWorka(UUID uuid, String candyId, int ile, String zrodlo, String miejsce) {
        CandyDef def = candies.get(candyId);
        if (def == null || !def.enabled() || ile <= 0) return;
        Player online = Bukkit.getPlayer(uuid);
        HalloweenPlayer hp;
        if (online != null) {
            hp = dane(online);
        } else {
            String nick = krolSbNicki.getOrDefault(uuid, Bukkit.getOfflinePlayer(uuid).getName());
            if (nick == null) nick = "?";
            hp = dane(uuid, nick);
        }
        int cap = pojemnosc(hp);
        int suma = hp.sumaCukierkow();
        int daj = Math.min(ile, Math.max(0, cap - suma));
        if (daj <= 0) {
            if (online != null) online.sendMessage(msg("worek-pelny"));
            return;
        }
        hp.cukierki.merge(candyId, daj, Integer::sum);
        hp.statCukierki += daj;
        hp.exp += cfg.getInt("poziomy.exp-cukierek", 2) * daj;
        sprawdzPoziom(hp);
        hp.oznacz();
        if (online != null) {
            if ("krol-ostatni".equals(zrodlo)) {
                online.sendMessage(msg("boss-nagroda-ostatni", "ilosc", String.valueOf(daj),
                        "nazwa", HalloweenUtil.colorMsg(def.name())));
            } else {
                online.sendMessage(msg("boss-nagroda-top", "miejsce", miejsce, "ilosc", String.valueOf(daj),
                        "nazwa", HalloweenUtil.colorMsg(def.name())));
            }
        }
        int hist = daj;
        String histCandy = candyId;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> baza.historia(hp.uuid, histCandy, hist, zrodlo));
    }

    @Override
    public boolean aktywne() {
        return krolSbWlaczony && krolSbStan != KrolSbStan.WYLACZONY;
    }

    @Override
    public String tytul() {
        return krolSbTytul;
    }

    @Override
    public List<String> linie(Player p) {
        List<String> wynik = new ArrayList<>(krolSbLinie.size());
        List<Map.Entry<UUID, Double>> top = krolSbTop;
        int twoje = 0;
        if (p != null && krolSbBoss != null) {
            Map<UUID, Double> dmg = obrazenia.get(krolSbBoss);
            if (dmg != null) twoje = (int) Math.round(dmg.getOrDefault(p.getUniqueId(), 0.0));
        } else if (p != null && krolSbStan == KrolSbStan.PO_ZABICIU) {
            for (Map.Entry<UUID, Double> e : top) {
                if (e.getKey().equals(p.getUniqueId())) {
                    twoje = (int) Math.round(e.getValue());
                    break;
                }
            }
        }
        String wierszCzasu = krolSbWierszCzasu == null ? "" : krolSbWierszCzasu;
        if (krolSbStan == KrolSbStan.PO_ZABICIU) {
            long sek = Math.max(0L, (krolSbDo - System.currentTimeMillis() + 999L) / 1000L);
            wierszCzasu = krolSbCzasPo.replace("%czas%", HalloweenUtil.formatCzas(sek));
        }
        for (String linia : krolSbLinie) {
            String s = linia.replace("%czas_linia%", wierszCzasu).replace("%twoje_punkty%", String.valueOf(twoje));
            for (int i = 0; i < 3; i++) {
                String nick = krolSbBrakNick;
                int pkt = 0;
                if (i < top.size()) {
                    UUID id = top.get(i).getKey();
                    nick = krolSbNicki.getOrDefault(id, "?");
                    pkt = (int) Math.round(top.get(i).getValue());
                }
                s = s.replace("%nick_" + (i + 1) + "%", nick).replace("%punkty_" + (i + 1) + "%", String.valueOf(pkt));
            }
            wynik.add(s);
        }
        return wynik;
    }
}
