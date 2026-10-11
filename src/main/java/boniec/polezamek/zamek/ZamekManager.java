package boniec.dev.polezamek.zamek;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.util.ItemTagUtil;
import org.bukkit.NamespacedKey;
import boniec.dev.polezamek.util.WorldGuardUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Odpowiednik systemu "Zamek z piasku" z drugiego skryptu, plus dwie NOWE
 * zasady dodane na zyczenie:
 *
 *  1) Nie da sie postawic zamku, jesli w danej chwili aktywne jest
 *     jakiekolwiek Pole Smierci na serwerze (patrz ZamekListener, uzywa
 *     PoleSmierciManager#isAnyFieldActive()).
 *  2) Nie da sie postawic DRUGIEGO zamku w tym samym chunku, dopoki
 *     pierwszy jeszcze stoi (patrz activeChunks nizej).
 */
public class ZamekManager {

    public static final Material ZAMEK_MATERIAL = Material.HONEYCOMB;

    public static final Material SELECTOR_MATERIAL = Material.WOODEN_AXE;
    public static final String SELECTOR_NAME = ChatColor.GREEN + "" + ChatColor.BOLD + "Zaznaczacz Zamku";

    private Main plugin;

    // Nazwa i lore (konfigurowalne)
    private String zamekName;
    private NamespacedKey itemKey;
    private final java.util.Set<String> legacyNames = new java.util.HashSet<>();
    private List<String> zamekLore;

    private int cooldownSeconds;
    private int durationSeconds;
    private int safeZoneRadius;
    private List<String> blockedRegionKeywords;

    private double disappearAnimationSeconds;

    private boolean bossbarEnabled;
    private String bossbarTitleTemplate;
    private BarColor bossbarColor;
    private BarStyle bossbarStyle;
    private int bossbarRadius;

    // Wiadomosci
    private String msgNoTemplate;
    private String msgBlockedRegion;
    private String msgOnCooldown;
    private String msgPoleActive;
    private String msgChunkOccupied;
    private String msgChunkLocked;
    private String msgChunkSharedLocked;
    private String msgTooClose;
    private String msgDisappeared;
    private String msgCantBreak;
    private String msgCantPlace;
    private String msgSelector1;
    private String msgSelector2;
    private String msgTemplateSaved;
    private String msgTemplateHint;
    private String msgZamekGiven;
    private String msgNoSelector;
    private String msgVictimProtected;

    // Szablon: klucz "ox_oy_oz" (offset wzgledem srodka zaznaczenia) -> pelne
    // dane bloku (material + orientacja/ksztalt, np. schody, plot, drzwi itp.).
    private final Map<String, BlockData> template = new HashMap<>();

    // Zaznaczanie (selektor) - per gracz.
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();

    // Cooldown budowy zamku - per gracz.
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // Blok aktywnego zamku ("world_x_y_z") -> oryginalne dane bloku (globalnie, jak w skrypcie).
    private final Map<String, BlockData> activeBlocks = new ConcurrentHashMap<>();

    // NOWE: chunk ("world_chunkX_chunkZ"), w ktorym aktualnie stoi zamek.
    private final Set<String> activeChunks = ConcurrentHashMap.newKeySet();

    // Zamki, ktore aktualnie stoja lub sa w trakcie animacji znikania
    // (zamek "konczy sie" dopiero w finishCastle - po animacji).
    private final List<ActiveCastle> activeCastles = new java.util.concurrent.CopyOnWriteArrayList<>();

    // NOWE: chunk ("world_chunkX_chunkZ") -> czas (ms) do ktorego nie mozna
    // postawic w nim KOLEJNEGO zamku (odliczane od znikniecia poprzedniego).
    private final Map<String, Long> chunkPlaceLockedUntil = new ConcurrentHashMap<>();
    private static final long ZAMEK_CHUNK_LOCK_MILLIS = 15_000L;

    private File templateFile;
    private FileConfiguration templateConfig;

    public ZamekManager(Main plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    public void reloadConfig(Main plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    private void loadConfig(Main plugin) {
        FileConfiguration cfg = plugin.getConfig();

        if (this.zamekName != null) legacyNames.add(this.zamekName);
        this.zamekName = col(cfg.getString("zamek.name", "&6&lᴢᴀᴍᴇᴋ ᴢ ᴘɪᴀsᴋᴜ"));
        this.customModelData = cfg.getInt("zamek.custom-model-data", ZAMEK_CUSTOM_MODEL_DATA);
        legacyNames.add(this.zamekName);
        this.zamekLore = cfg.getStringList("zamek.lore");

        this.cooldownSeconds = cfg.getInt("zamek.cooldown-seconds", 60);
        this.durationSeconds = cfg.getInt("zamek.duration-seconds", 8);
        this.safeZoneRadius = cfg.getInt("zamek.safe-zone-check-radius", 10);
        this.blockedRegionKeywords = cfg.getStringList("zamek.blocked-region-keywords");

        this.disappearAnimationSeconds = Math.max(0, cfg.getDouble("zamek.disappear-animation-seconds", 1.5));

        this.bossbarEnabled = cfg.getBoolean("zamek.bossbar.enabled", true);
        this.bossbarTitleTemplate = cfg.getString("zamek.bossbar.title", "&6&lZamek z piasku &8| &fZnika za: &e{czas}s");
        BarColor parsedColor;
        try {
            parsedColor = BarColor.valueOf(cfg.getString("zamek.bossbar.color", "YELLOW").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsedColor = BarColor.YELLOW;
        }
        this.bossbarColor = parsedColor;
        BarStyle parsedStyle;
        try {
            parsedStyle = BarStyle.valueOf(cfg.getString("zamek.bossbar.style", "SOLID").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsedStyle = BarStyle.SOLID;
        }
        this.bossbarStyle = parsedStyle;
        this.bossbarRadius = cfg.getInt("zamek.bossbar.radius", 30);

        this.msgNoTemplate = cfg.getString("messages.zamek-no-template", "&cSzablon zamku nie jest ustawiony! Użyj /zamekselector i /ustawzamek");
        this.msgBlockedRegion = cfg.getString("messages.zamek-blocked-region", "&cNie możesz użyć zamku w pobliżu spawna");
        this.msgOnCooldown = cfg.getString("messages.zamek-on-cooldown", "&cMusisz poczekać jeszcze &4{czas}s&c!");
        this.msgPoleActive = cfg.getString("messages.zamek-pole-active", "&cNie możesz postawić zamku, gdy aktywne jest &c&lᴘᴏʟᴇ śᴍɪᴇʀcɪ&c!");
        this.msgChunkOccupied = cfg.getString("messages.zamek-chunk-occupied", "&cW tym chunku jest już aktywny zamek! Poczekaj, aż zniknie.");
        this.msgChunkLocked = cfg.getString("messages.zamek-chunk-locked", "&cMusisz poczekać jeszcze &4{czas}s&c, zanim postawisz kolejny zamek w tym chunku!");
        this.msgChunkSharedLocked = cfg.getString("messages.zamek-chunk-shared-locked", "&cMusisz poczekać jeszcze &4{czas}s&c, zanim użyjesz pola/zamku w tym miejscu!");
        this.msgTooClose = cfg.getString("messages.zamek-too-close", "&cNie możesz postawić zamku w pobliżu ostatniego zamku/pola! Poczekaj &4{czas}s&c!");
        this.msgDisappeared = cfg.getString("messages.zamek-disappeared", "&cZamek z piasku zniknął...");
        this.msgCantBreak = cfg.getString("messages.zamek-cant-break", "&cNie możesz zniszczyć bloków zamku!");
        this.msgCantPlace = cfg.getString("messages.zamek-cant-place", "&cNie możesz stawiać bloków w zamku!");
        this.msgSelector1 = cfg.getString("messages.zamek-selector-1", "&a&l[1] &aZaznaczono 1. róg: &e{loc}");
        this.msgSelector2 = cfg.getString("messages.zamek-selector-2", "&a&l[2] &aZaznaczono 2. róg: &e{loc}");
        this.msgTemplateSaved = cfg.getString("messages.zamek-template-saved", "&a&l✔ &aZapisano szablon zamku! Bloków: &e{count}");
        this.msgTemplateHint = cfg.getString("messages.zamek-template-hint", "&7Zamek będzie budowany tak, że Ty zawsze będziesz na jego środku!");
        this.msgZamekGiven = cfg.getString("messages.zamek-given", "&aOtrzymałeś Zamek z piasku!");
        this.msgVictimProtected = cfg.getString("messages.zamek-victim-protected", "&cGracz &e{player} &cbył niedawno zamknięty w zamku! Zamek zadziała na niego dopiero za &4{czas}s&c.");
        this.msgNoSelector = cfg.getString("messages.zamek-no-selector", "&cNajpierw zaznacz 1. i 2. róg siekierką (/zamekselector)!");
    }

    // ================= Persistencja szablonu =================

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        templateFile = new File(plugin.getDataFolder(), "zamek-template.yml");
        if (!templateFile.exists()) {
            try {
                templateFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        templateConfig = YamlConfiguration.loadConfiguration(templateFile);
        template.clear();
        ConfigurationSection section = templateConfig.getConfigurationSection("template");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String dataString = section.getString(key);
                if (dataString == null) continue;
                try {
                    BlockData data = Bukkit.createBlockData(dataString);
                    template.put(key, data);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Nieprawidlowe dane bloku w szablonie zamku ('" + key + "': " + dataString + "), pomijam.");
                }
            }
        }
        if (!template.isEmpty()) {
            plugin.getLogger().info("Wczytano szablon zamku (" + template.size() + " blokow).");
        }
    }

    private void saveTemplate() {
        templateConfig.set("template", null);
        for (Map.Entry<String, BlockData> entry : template.entrySet()) {
            templateConfig.set("template." + entry.getKey(), entry.getValue().getAsString());
        }
        try {
            templateConfig.save(templateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udalo sie zapisac zamek-template.yml: " + e.getMessage());
        }
    }

    public boolean hasTemplate() {
        return !template.isEmpty();
    }

    // ================= Zaznaczanie / tworzenie szablonu =================

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc);
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc);
    }

    public boolean hasBothPositions(Player player) {
        return pos1.containsKey(player.getUniqueId()) && pos2.containsKey(player.getUniqueId());
    }

    public boolean hasPos1(Player player) {
        return pos1.containsKey(player.getUniqueId());
    }

    /**
     * Buduje szablon na podstawie zaznaczonego obszaru, zapisujac wszystkie
     * niepowietrzne bloki jako offsety wzgledem GEOMETRYCZNEGO SRODKA
     * zaznaczonego obszaru (a nie pozycji gracza w momencie /ustawzamek).
     * Dzieki temu przy kazdym uzyciu zamku gracz zawsze laduje na jego
     * srodku, niezaleznie od tego gdzie stal podczas zapisywania szablonu.
     *
     * @return liczba zapisanych blokow, albo -1 jesli brak zaznaczenia.
     */
    public int buildTemplateFromSelection(Player player) {
        Location p1 = pos1.get(player.getUniqueId());
        Location p2 = pos2.get(player.getUniqueId());
        if (p1 == null || p2 == null) return -1;

        template.clear();

        int minX = Math.min((int) Math.floor(p1.getX()), (int) Math.floor(p2.getX()));
        int maxX = Math.max((int) Math.floor(p1.getX()), (int) Math.floor(p2.getX()));
        int minY = Math.min((int) Math.floor(p1.getY()), (int) Math.floor(p2.getY()));
        int maxY = Math.max((int) Math.floor(p1.getY()), (int) Math.floor(p2.getY()));
        int minZ = Math.min((int) Math.floor(p1.getZ()), (int) Math.floor(p2.getZ()));
        int maxZ = Math.max((int) Math.floor(p1.getZ()), (int) Math.floor(p2.getZ()));

        // Srodek zaznaczonego obszaru - X i Z geometryczny srodek,
        // Y = pozycja gracza (zeby zamek pojawial sie dokladnie tam gdzie stoi).
        int sx = (minX + maxX) / 2;
        int sy = player.getLocation().getBlockY();
        int sz = (minZ + maxZ) / 2;

        World world = player.getWorld();
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.AIR) {
                        int ox = x - sx;
                        int oy = y - sy;
                        int oz = z - sz;
                        String key = ox + "_" + oy + "_" + oz;
                        // Zapisujemy PELNE dane bloku (nie tylko material), zeby
                        // schody, ploty, drzwi itp. zachowaly swoja orientacje/ksztalt.
                        template.put(key, b.getBlockData().clone());
                        count++;
                    }
                }
            }
        }

        saveTemplate();
        return count;
    }

    // ================= Itemy =================

    public static final int ZAMEK_CUSTOM_MODEL_DATA = 1001;
    private int customModelData = ZAMEK_CUSTOM_MODEL_DATA;

    public ItemStack createZamekItem() {
        return createZamekItem(ZAMEK_MATERIAL);
    }

    public ItemStack createZamekItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(zamekName);
        ItemTagUtil.applyModel(meta, customModelData);

        if (zamekLore != null && !zamekLore.isEmpty()) {
            meta.setLore(zamekLore.stream().map(ZamekManager::col).toList());
        } else {
            meta.setLore(List.of(
                    col("&0"),
                    col("&8x &fPo użyciu &6tworzymy &fdookoła"),
                    col("&8x &fnas &ezamek z piasku &fi zamykamy w nim"),
                    col("&8x &fwszystkich graczy w obrębie na określony czas"),
                    col("&0"),
                    col("&8x &aKliknij prawym aby użyć!")
            ));
        }

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        ItemTagUtil.setTag(itemKey, meta, "zamek");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSelectorItem() {
        ItemStack item = new ItemStack(SELECTOR_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SELECTOR_NAME);
        meta.setLore(List.of(
                col("&7LPM: Zaznacz pozycję 1"),
                col("&7PPM: Zaznacz pozycję 2")
        ));
        item.setItemMeta(meta);
        return item;
    }

    /** Rozpoznaje po tagu PDC (nie po nazwie); stare itemy bez tagu sa migrowane. */
    public boolean isZamekItem(ItemStack item) {
        return ItemTagUtil.matches(itemKey, item, "zamek", legacyNames.toArray(new String[0]));
    }

    /** Odswieza nazwe i lore itemu do aktualnych wartosci z configu (gdy to Zamek). */
    public boolean refreshItem(ItemStack item) {
        if (!isZamekItem(item)) return false;
        boolean changed = ItemTagUtil.refresh(item, zamekName, buildLore());
        changed |= ItemTagUtil.syncModel(item, customModelData);
        return changed;
    }

    private List<String> buildLore() {
        if (zamekLore != null && !zamekLore.isEmpty()) {
            return zamekLore.stream().map(ZamekManager::col).toList();
        }
        return List.of(
                col("&0"),
                col("&8x &fPo użyciu &6tworzymy &fdookoła"),
                col("&8x &fnas &ezamek z piasku &fi zamykamy w nim"),
                col("&8x &fwszystkich graczy w obrębie na określony czas"),
                col("&0"),
                col("&8x &aKliknij prawym aby użyć!"));
    }

    public boolean isSelectorItem(ItemStack item) {
        return matchesByName(item, SELECTOR_NAME);
    }

    private boolean matchesByName(ItemStack item, String name) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(name);
    }

    private static String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // ================= Cooldown =================

    public boolean isOnCooldown(Player player) {
        Long until = cooldowns.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long getCooldownRemainingSeconds(Player player) {
        Long until = cooldowns.get(player.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }

    public void setCooldownNow(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    // ================= Sprawdzanie mozliwosci postawienia =================

    public boolean isInBlockedRegion(Location base) {
        if (!plugin.isWorldGuardInstalled() || blockedRegionKeywords.isEmpty()) return false;
        int[] offsets = {-10, -5, 0, 5, 10};
        for (int cx : offsets) {
            for (int cz : offsets) {
                if (Math.abs(cx) > safeZoneRadius || Math.abs(cz) > safeZoneRadius) continue;
                Location check = base.clone().add(cx, 0, cz);
                for (String keyword : blockedRegionKeywords) {
                    if (WorldGuardUtil.isInRegionContaining(check, keyword)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Czy w promieniu {@code distance} od {@code center} stoi jeszcze zamek (razem z jego
     * animacja znikania). Uzywane, zeby nie dalo sie postawic Pola Smierci przy zamku,
     * ktory jeszcze sie nie skonczyl - po jego zniknieciu pole jest juz dozwolone.
     */
    public boolean isCastleActiveWithin(Location center, double distance) {
        if (center.getWorld() == null) return false;
        for (ActiveCastle castle : activeCastles) {
            if (center.getWorld().equals(castle.base.getWorld()) && castle.base.distance(center) < distance) {
                return true;
            }
        }
        return false;
    }

    public boolean isBlockOccupiedByCastle(String blockKey) {
        return activeBlocks.containsKey(blockKey);
    }

    /** NOWE: sprawdza czy w danym chunku stoi juz jakis zamek. */
    public boolean isChunkOccupied(String chunkKey) {
        return activeChunks.contains(chunkKey);
    }

    /**
     * NOWE: sprawdza czy w danym chunku obowiazuje jeszcze 15-sekundowa
     * blokada po znikniecu poprzedniego zamku (niezaleznie od activeChunks,
     * ktore dotyczy tylko czasu, w ktorym zamek faktycznie stoi).
     */
    public boolean isChunkOnPlaceLock(String chunkKey) {
        Long until = chunkPlaceLockedUntil.get(chunkKey);
        return until != null && until > System.currentTimeMillis();
    }

    public long getChunkPlaceLockRemainingSeconds(String chunkKey) {
        Long until = chunkPlaceLockedUntil.get(chunkKey);
        if (until == null) return 0;
        long remaining = until - System.currentTimeMillis();
        return remaining > 0 ? (remaining + 999) / 1000L : 0;
    }

    public String chunkKey(Location loc) {
        return loc.getWorld().getName() + "_" + (loc.getBlockX() >> 4) + "_" + (loc.getBlockZ() >> 4);
    }

    public String blockKey(World world, int x, int y, int z) {
        return world.getName() + "_" + x + "_" + y + "_" + z;
    }

    // ================= Gracze zamykani w zamku =================

    /**
     * Gracze (poza wlascicielem), ktorych zamek postawiony w {@code base} zamknalby w srodku -
     * czyli stojacy w prostopadloscianie obejmujacym caly szablon zamku.
     */
    public List<Player> getVictimsInside(Player caster, Location base) {
        List<Player> result = new ArrayList<>();
        if (template.isEmpty() || base.getWorld() == null) return result;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : template.keySet()) {
            String[] parts = key.split("_");
            int ox = Integer.parseInt(parts[0]);
            int oy = Integer.parseInt(parts[1]);
            int oz = Integer.parseInt(parts[2]);
            minX = Math.min(minX, ox); maxX = Math.max(maxX, ox);
            minY = Math.min(minY, oy); maxY = Math.max(maxY, oy);
            minZ = Math.min(minZ, oz); maxZ = Math.max(maxZ, oz);
        }
        // Zamek budowany jest od (base X, base Y + 1, base Z) - jak w build().
        int fromX = base.getBlockX() + minX, toX = base.getBlockX() + maxX;
        int fromY = base.getBlockY() + 1 + minY, toY = base.getBlockY() + 1 + maxY;
        int fromZ = base.getBlockZ() + minZ, toZ = base.getBlockZ() + maxZ;
        for (Player p : base.getWorld().getPlayers()) {
            if (p.equals(caster)) continue;
            Location l = p.getLocation();
            if (l.getBlockX() >= fromX && l.getBlockX() <= toX
                    && l.getBlockY() >= fromY && l.getBlockY() <= toY
                    && l.getBlockZ() >= fromZ && l.getBlockZ() <= toZ) {
                result.add(p);
            }
        }
        return result;
    }

    // ================= Budowa / rozbiorka =================

    public static class ActiveCastle {
        public final List<String> blockKeys = new java.util.ArrayList<>();
        public final String chunkKey;
        public final Location base;

        // Bloki zamku pogrupowane wg warstwy X szablonu (offset ox), posortowane
        // od najmniejszego do najwiekszego - potrzebne do animacji znikania
        // "od lewej do prawej" po 1 warstwie na raz.
        public final TreeMap<Integer, List<String>> blockKeysByLayer = new TreeMap<>();

        public BossBar bossBar;

        public ActiveCastle(String chunkKey, Location base) {
            this.chunkKey = chunkKey;
            this.base = base;
        }
    }

    public ActiveCastle build(Player player, Location base) {
        World world = base.getWorld();
        int sx = base.getBlockX();
        int sy = base.getBlockY() + 1;
        int sz = base.getBlockZ();
        String chunkKey = chunkKey(base);

        ActiveCastle castle = new ActiveCastle(chunkKey, base.clone());
        activeChunks.add(chunkKey);
        activeCastles.add(castle);

        for (Map.Entry<String, BlockData> entry : template.entrySet()) {
            String[] parts = entry.getKey().split("_");
            int ox = Integer.parseInt(parts[0]);
            int oy = Integer.parseInt(parts[1]);
            int oz = Integer.parseInt(parts[2]);

            int tx = sx + ox;
            int ty = sy + oy;
            int tz = sz + oz;
            String key = blockKey(world, tx, ty, tz);

            Block block = world.getBlockAt(tx, ty, tz);
            activeBlocks.put(key, block.getBlockData().clone());
            castle.blockKeys.add(key);
            castle.blockKeysByLayer.computeIfAbsent(ox, k -> new ArrayList<>()).add(key);
            block.setBlockData(entry.getValue().clone());
        }

        return castle;
    }

    /**
     * NOWE: usuwa (przywraca oryginal) tylko blokow z podanej listy, bez
     * ruszania calego zamku - uzywane przez animacje znikania warstwami.
     */
    public void restoreBlocks(List<String> blockKeys) {
        for (String key : blockKeys) {
            BlockData original = activeBlocks.remove(key);
            if (original == null) continue;
            Location loc = parseBlockKey(key);
            if (loc != null) {
                loc.getBlock().setBlockData(original);
            }
        }
    }

    /** NOWE: konczy zycie zamku - odblokowuje chunk i startuje 15s blokade postawienia. */
    public void finishCastle(ActiveCastle castle) {
        activeChunks.remove(castle.chunkKey);
        activeCastles.remove(castle);
        chunkPlaceLockedUntil.put(castle.chunkKey, System.currentTimeMillis() + ZAMEK_CHUNK_LOCK_MILLIS);
    }

    public void restore(ActiveCastle castle) {
        restoreBlocks(castle.blockKeys);
        finishCastle(castle);
    }

    private Location parseBlockKey(String key) {
        int lastZ = key.lastIndexOf('_');
        int lastY = key.lastIndexOf('_', lastZ - 1);
        int lastX = key.lastIndexOf('_', lastY - 1);
        String worldName = key.substring(0, lastX);
        int x = Integer.parseInt(key.substring(lastX + 1, lastY));
        int y = Integer.parseInt(key.substring(lastY + 1, lastZ));
        int z = Integer.parseInt(key.substring(lastZ + 1));
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public double getDisappearAnimationSeconds() {
        return disappearAnimationSeconds;
    }

    // ================= BossBar =================

    public boolean isBossbarEnabled() {
        return bossbarEnabled;
    }

    public int getBossbarRadius() {
        return bossbarRadius;
    }

    public BossBar createBossBar(int secondsRemaining) {
        BossBar bar = plugin.getServer().createBossBar(formatBossbarTitle(secondsRemaining), bossbarColor, bossbarStyle);
        bar.setVisible(true);
        return bar;
    }

    public String formatBossbarTitle(int secondsRemaining) {
        return col(bossbarTitleTemplate.replace("{czas}", String.valueOf(Math.max(0, secondsRemaining))));
    }

    /**
     * NOWE: utrzymuje liste graczy widzacych bossbar zgodna z tymi, ktorzy
     * aktualnie znajduja sie w promieniu (bossbarRadius) od bazy zamku.
     */
    public void updateBossBarViewers(ActiveCastle castle) {
        if (castle.bossBar == null) return;
        World world = castle.base.getWorld();
        if (world == null) return;

        Set<Player> shouldSee = new java.util.HashSet<>();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distance(castle.base) <= bossbarRadius) {
                shouldSee.add(p);
            }
        }

        for (Player current : new java.util.ArrayList<>(castle.bossBar.getPlayers())) {
            if (!shouldSee.contains(current)) {
                castle.bossBar.removePlayer(current);
            }
        }
        for (Player p : shouldSee) {
            castle.bossBar.addPlayer(p);
        }
    }

    // ================= Wiadomosci =================

    public String getMsgNoTemplate() { return msgNoTemplate; }
    public String getMsgBlockedRegion() { return msgBlockedRegion; }
    public String getMsgOnCooldown(long seconds) { return msgOnCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgPoleActive() { return msgPoleActive; }
    public String getMsgChunkOccupied() { return msgChunkOccupied; }
    public String getMsgChunkLocked(long seconds) { return msgChunkLocked.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgChunkSharedLocked(long seconds) { return msgChunkSharedLocked.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgTooClose(long seconds) { return msgTooClose.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgDisappeared() { return msgDisappeared; }
    public String getMsgCantBreak() { return msgCantBreak; }
    public String getMsgCantPlace() { return msgCantPlace; }
    public String getMsgSelector1(String loc) { return msgSelector1.replace("{loc}", loc); }
    public String getMsgSelector2(String loc) { return msgSelector2.replace("{loc}", loc); }
    public String getMsgTemplateSaved(int count) { return msgTemplateSaved.replace("{count}", String.valueOf(count)); }
    public String getMsgTemplateHint() { return msgTemplateHint; }
    public String getMsgZamekGiven() { return msgZamekGiven; }
    public String getMsgNoSelector() { return msgNoSelector; }
    public String getMsgVictimProtected(String player, long seconds) {
        return msgVictimProtected.replace("{player}", player).replace("{czas}", String.valueOf(seconds));
    }

    public void shutdown() {
        // Aktywne zamki (jesli jakies stoja) zostana na mapie do nastepnego
        // startu - to samo zachowanie co przy krotkotrwalym Polu Smierci;
        // to efemeryczne efekty, nie zamrazamy/nie odtwarzamy ich stanu.
    }
}
