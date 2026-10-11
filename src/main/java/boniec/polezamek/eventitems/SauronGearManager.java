package boniec.dev.polezamek.eventitems;

import boniec.code.core.UpgradeManager;
import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.util.ItemTagUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Miecz Saurona + Glowy Smokow (Krolewski / Ognisty / Mrozny).
 * <p>
 * Itemy sa rozpoznawane po tagu PDC (ten sam klucz "event_item" co reszta eventowek),
 * a ich enchanty NIE sa wpisane na sztywno - sa kopiowane z kategorii /ulepsz:
 * <ul>
 *   <li>Miecz Saurona = enchanty NAJLEPSZEGO miecza z /ulepsz (ostatni poziom kategorii),</li>
 *   <li>Krolewski Smok = najlepszy helm, Ognisty = 2. najlepszy, Mrozny = 3. najlepszy.</li>
 * </ul>
 * Gdy w /ulepsz pojawi sie nowszy poziom (np. miecz 15), itemy dociagna nowe enchanty
 * automatycznie (miecz - przy uderzeniu, glowy - przy odswiezeniu ekwipunku).
 */
public class SauronGearManager {

    public static final String TAG_SAURON_SWORD = "sauron_sword";

    /** Rodzaje glow smoka. offset = ktory od gory helm z /ulepsz (0 = najlepszy). */
    public enum DragonHead {
        KROLEWSKI("smok-krolewski", "dragon_krolewski", 0,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTYxMjJjM2FjMTczNzgwM2NmNWVhZjI2NmNiMDU4MjNmZGJkZGFmNGUyNTkwOThlOTU1OTU5NTEzYzhkOTZmNiJ9fX0="),
        OGNISTY("smok-ognisty", "dragon_ognisty", 1,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTdiZWMyOWZiOTBhYTRkOWIxMGQxNzZiYzY4MjI2MjhiMWY4ZWNhZTFlNTRhOWFhY2E5MTNkYjRhNjlhMmFhIn19fQ=="),
        MROZNY("smok-mrozny", "dragon_mrozny", 2,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjFmZmJkYTkyNDQyODczZDU0MDUxZTRlMTdlYTQ3Mjc4Y2IzYjc0N2Q3OTgwNzBiYTgzYmM0YTlkZTU4ZGQifX19");

        public final String configKey;
        public final String tag;
        public final int rankFromTop;
        /** Domyslna tekstura (Base64 "value" player heada) - mozna nadpisac w eventowki.yml. */
        public final String defaultTexture;

        DragonHead(String configKey, String tag, int rankFromTop, String defaultTexture) {
            this.configKey = configKey;
            this.tag = tag;
            this.rankFromTop = rankFromTop;
            this.defaultTexture = defaultTexture;
        }
    }

    /** Buty smoka. rankFromTop = ktore od gory buty z /ulepsz (0 = najlepsze). */
    public enum DragonBoots {
        KROLEWSKIE("buty-krolewskie", "dragon_boots_krolewskie", 0, Material.NETHERITE_BOOTS),
        OGNISTE("buty-ogniste", "dragon_boots_ogniste", 1, Material.DIAMOND_BOOTS),
        MROZNE("buty-mrozne", "dragon_boots_mrozne", 2, Material.GOLDEN_BOOTS);

        public final String configKey;
        public final String tag;
        public final int rankFromTop;
        public final Material material;

        DragonBoots(String configKey, String tag, int rankFromTop, Material material) {
            this.configKey = configKey;
            this.tag = tag;
            this.rankFromTop = rankFromTop;
            this.material = material;
        }
    }

    /** Ustawienia butow smoka (z eventowki.yml). */
    public static final class BootsCfg {
        public String name;
        public List<String> lore;
        public int customModelData;
        /** -1 = brak; 0 = Speed I, 1 = Speed II. */
        public int speedAmp;
        public double dropMultiplier;
        public double expMultiplier;
    }

    /** Ustawienia jednej glowy smoka (z eventowki.yml). */
    public static final class HeadCfg {
        public String name;
        public List<String> lore;
        public int customModelData;
        public String texture;
        /** -1 = brak efektu; 0 = I, 1 = II ... */
        public int strengthAmp;
        public int resistanceAmp;
        public boolean fireResistance;
    }

    private static final String[] SWORD_KEYWORDS = {"miecz", "sword"};
    private static final String[] HELM_KEYWORDS = {"helm", "hełm", "helmet"};
    private static final String[] BOOTS_KEYWORDS = {"buty", "boots"};

    private final Main plugin;
    private final NamespacedKey itemKey;

    private String swordName;
    private List<String> swordLore;
    private int swordCmd;
    private String swordCategory;
    private double radius;
    private double damageMultiplier;
    private int soundIntervalSeconds;
    private int strengthReduction;
    private String headCategory;
    private final Map<DragonHead, HeadCfg> heads = new EnumMap<>(DragonHead.class);
    private final Map<DragonBoots, BootsCfg> boots = new EnumMap<>(DragonBoots.class);
    private String bootsCategory;

    /** Ostatnio utworzony manager - do prostego dostepu z innych modulow (np. MiningListener). */
    private static volatile SauronGearManager instance;

    public static SauronGearManager get() {
        return instance;
    }

    public SauronGearManager(Main plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig();
        instance = this;
    }

    public void reloadConfig() {
        loadConfig();
    }

    /**
     * Odczyt configu z fallbackiem do eventowki.yml z jara.
     * <p>
     * UWAGA: {@code YamlConfiguration#setDefaults} NIE dziala z getterami typu
     * {@code getInt(path, def)} - one zawsze zwracaja podany {@code def}, a nie wartosc z defaults.
     * Przez to na serwerze ze starym eventowki.yml (bez sekcji smok-* / buty-*) strength-amplifier
     * i speed-amplifier wychodzily jako -1 (= brak efektu). Tutaj brakujacy klucz bierzemy
     * z pliku z jara, dopiero potem z domyslnej wartosci w kodzie.
     */
    private static final class Yml {
        private final FileConfiguration file;
        private final FileConfiguration defs;

        Yml(FileConfiguration file, FileConfiguration defs) {
            this.file = file;
            this.defs = defs;
        }

        private boolean inFile(String path) { return file.contains(path, true); }
        private boolean inDefs(String path) { return defs != null && defs.contains(path, true); }

        String getString(String path, String def) {
            if (inFile(path)) return file.getString(path, def);
            if (inDefs(path)) return defs.getString(path, def);
            return def;
        }

        int getInt(String path, int def) {
            if (inFile(path)) return file.getInt(path, def);
            if (inDefs(path)) return defs.getInt(path, def);
            return def;
        }

        double getDouble(String path, double def) {
            if (inFile(path)) return file.getDouble(path, def);
            if (inDefs(path)) return defs.getDouble(path, def);
            return def;
        }

        boolean getBoolean(String path, boolean def) {
            if (inFile(path)) return file.getBoolean(path, def);
            if (inDefs(path)) return defs.getBoolean(path, def);
            return def;
        }

        List<String> getStringList(String path) {
            if (inFile(path)) return file.getStringList(path);
            if (inDefs(path)) return defs.getStringList(path);
            return new ArrayList<>();
        }

        boolean fileHas(String path) { return inFile(path); }
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "eventowki.yml");
        if (!file.exists()) {
            plugin.saveResource("eventowki.yml", false);
        }
        FileConfiguration fileCfg = YamlConfiguration.loadConfiguration(file);

        // Starszy eventowki.yml na serwerze nie ma nowych sekcji - dociagamy domyslne z jara.
        FileConfiguration defCfg = null;
        try (InputStream in = plugin.getHost().getResource("eventowki.yml")) {
            if (in != null) {
                defCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        Yml cfg = new Yml(fileCfg, defCfg);

        if (!cfg.fileHas("smok-krolewski.strength-amplifier") || !cfg.fileHas("buty-krolewskie.speed-amplifier")) {
            plugin.getLogger().warning("eventowki.yml na serwerze jest stary (brak sekcji smok-* / buty-*) - "
                    + "uzywam wartosci domyslnych z jara. Usun plugins/Inventory/eventowki.yml i zrob restart, "
                    + "zeby wygenerowac aktualny plik.");
        }

        swordName = col(cfg.getString("miecz-saurona.name", "&c&lᴍɪᴇᴄᴢ ꜱᴀᴜʀᴏɴᴀ"));
        swordLore = colList(cfg.getStringList("miecz-saurona.lore"));
        swordCmd = cfg.getInt("miecz-saurona.custom-model-data", 0);
        swordCategory = cfg.getString("miecz-saurona.kategoria-ulepszen", "miecz");
        radius = Math.max(1.0, cfg.getDouble("miecz-saurona.promien-oslabiania", 10.0));
        damageMultiplier = Math.max(0.0, cfg.getDouble("miecz-saurona.mnoznik-obrazen", 1.10));
        soundIntervalSeconds = Math.max(1, cfg.getInt("miecz-saurona.dzwiek-co-sekund", 15));
        strengthReduction = Math.max(0, cfg.getInt("miecz-saurona.obnizenie-sily", 1));

        headCategory = cfg.getString("smocze-glowy.kategoria-ulepszen", "helm");
        for (DragonHead h : DragonHead.values()) {
            String p = h.configKey + ".";
            HeadCfg c = new HeadCfg();
            c.name = col(cfg.getString(p + "name", "&6&lɢʟᴏᴡᴀ ꜱᴍᴏᴋᴀ"));
            c.lore = colList(cfg.getStringList(p + "lore"));
            c.customModelData = cfg.getInt(p + "custom-model-data", 0);
            String tex = cfg.getString(p + "texture", "");
            c.texture = (tex == null || tex.isBlank()) ? h.defaultTexture : tex.trim();
            c.strengthAmp = cfg.getInt(p + "strength-amplifier", -1);
            c.resistanceAmp = cfg.getInt(p + "resistance-amplifier", -1);
            c.fireResistance = cfg.getBoolean(p + "fire-resistance", true);
            heads.put(h, c);
        }

        bootsCategory = cfg.getString("smocze-buty.kategoria-ulepszen", "buty");
        for (DragonBoots b : DragonBoots.values()) {
            String p = b.configKey + ".";
            BootsCfg c = new BootsCfg();
            c.name = col(cfg.getString(p + "name", "&6&lʙᴜᴛʏ ꜱᴍᴏᴋᴀ"));
            c.lore = colList(cfg.getStringList(p + "lore"));
            c.customModelData = cfg.getInt(p + "custom-model-data", 0);
            c.speedAmp = cfg.getInt(p + "speed-amplifier", -1);
            c.dropMultiplier = Math.max(1.0, cfg.getDouble(p + "drop-multiplier", 1.0));
            c.expMultiplier = Math.max(1.0, cfg.getDouble(p + "exp-multiplier", 1.0));
            boots.put(b, c);
        }
    }

    // ===================== Tworzenie itemow =====================

    public ItemStack createSauronSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(swordName);
        if (!swordLore.isEmpty()) meta.setLore(swordLore);
        if (swordCmd > 0) meta.setCustomModelData(swordCmd);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, TAG_SAURON_SWORD);
        ukryjEnchantTooltip(meta);
        item.setItemMeta(meta);
        syncEnchants(item);
        return item;
    }

    public ItemStack createDragonHead(DragonHead head) {
        HeadCfg c = heads.get(head);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c.name);
        if (!c.lore.isEmpty()) meta.setLore(c.lore);
        if (c.customModelData > 0) meta.setCustomModelData(c.customModelData);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, head.tag);
        if (meta instanceof SkullMeta skull) {
            PlayerProfile profile = buildProfile(head.tag, c.texture);
            if (profile != null) skull.setOwnerProfile(profile);
        }
        ukryjEnchantTooltip(meta);
        item.setItemMeta(meta);
        syncEnchants(item);
        return item;
    }

    public ItemStack createDragonBoots(DragonBoots type) {
        BootsCfg c = boots.get(type);
        ItemStack item = new ItemStack(type.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c.name);
        if (!c.lore.isEmpty()) meta.setLore(c.lore);
        if (c.customModelData > 0) meta.setCustomModelData(c.customModelData);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.tag);
        ukryjEnchantTooltip(meta);
        item.setItemMeta(meta);
        syncEnchants(item);
        return item;
    }

    private static final Pattern SKIN_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    /** Profil z Base64 "value" (JSON z url tekstury). Stale UUID na rodzaj glowy = glowy sie stackuja. */
    private PlayerProfile buildProfile(String seed, String base64) {
        try {
            String json = new String(Base64.getDecoder().decode(base64.trim()), StandardCharsets.UTF_8);
            Matcher m = SKIN_URL.matcher(json);
            if (!m.find()) return null;
            PlayerProfile profile = Bukkit.createPlayerProfile(
                    UUID.nameUUIDFromBytes(("dragon-head:" + seed).getBytes(StandardCharsets.UTF_8)), "SmokHead");
            profile.getTextures().setSkin(new URL(m.group(1)));
            return profile;
        } catch (Exception ex) {
            plugin.getLogger().warning("Nieprawidlowa tekstura glowy smoka (" + seed + "): " + ex.getMessage());
            return null;
        }
    }

    // ===================== Rozpoznawanie =====================

    private String tagOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public boolean isSauronSword(ItemStack item) {
        return TAG_SAURON_SWORD.equals(tagOf(item));
    }

    /** Rodzaj glowy smoka albo null, gdy item nie jest glowa smoka. */
    public DragonHead getHead(ItemStack item) {
        String tag = tagOf(item);
        if (tag == null) return null;
        for (DragonHead h : DragonHead.values()) {
            if (h.tag.equals(tag)) return h;
        }
        return null;
    }

    public DragonBoots getBoots(ItemStack item) {
        String tag = tagOf(item);
        if (tag == null) return null;
        for (DragonBoots b : DragonBoots.values()) {
            if (b.tag.equals(tag)) return b;
        }
        return null;
    }

    public BootsCfg getBootsCfg(DragonBoots b) { return boots.get(b); }

    /** Mnoznik dropu z butow smoka na nogach gracza (1.0 = brak). */
    public double getDropMultiplier(Player p) {
        DragonBoots b = getBoots(p.getInventory().getBoots());
        return b == null ? 1.0 : boots.get(b).dropMultiplier;
    }

    /** Mnoznik expa z butow smoka na nogach gracza (1.0 = brak). */
    public double getExpMultiplier(Player p) {
        DragonBoots b = getBoots(p.getInventory().getBoots());
        return b == null ? 1.0 : boots.get(b).expMultiplier;
    }

    // ===================== Enchanty z /ulepsz =====================

    private UpgradeManager upgradeManager() {
        if (plugin.getHost() instanceof boniec.dev.inventory.Main inv) {
            return inv.getUpgradeManager();
        }
        return null;
    }

    /** Szuka kategorii: najpierw dokladna nazwa z configu (bez wzgledu na wielkosc liter), potem po slowie kluczowym. */
    private String resolveCategory(UpgradeManager um, String configured, String[] keywords) {
        List<String> names = um.getCategoryNames();
        if (configured != null && !configured.isBlank()) {
            for (String n : names) {
                if (n.equalsIgnoreCase(configured.trim())) return n;
            }
        }
        for (String n : names) {
            String low = n.toLowerCase(Locale.ROOT);
            for (String k : keywords) {
                if (low.contains(k)) return n;
            }
        }
        return null;
    }

    /** Enchanty itemu z kategorii: rankFromTop 0 = najlepszy, 1 = drugi od gory itd. Null = brak danych. */
    private Map<Enchantment, Integer> bestEnchants(String configured, String[] keywords, int rankFromTop) {
        UpgradeManager um = upgradeManager();
        if (um == null) return null;
        String cat = resolveCategory(um, configured, keywords);
        if (cat == null) return null;
        List<ItemStack> items = um.getItems(cat);
        if (items == null || items.isEmpty()) return null;
        int idx = Math.max(0, items.size() - 1 - rankFromTop);
        ItemStack ref = items.get(idx);
        if (ref == null) return null;
        return ref.getEnchantments();
    }

    /**
     * Ustawia na itemie enchanty z /ulepsz (miecz = najlepszy miecz, glowa = helm wg rangi).
     * Zwraca true, jesli cos sie zmienilo.
     */
    public boolean syncEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        Map<Enchantment, Integer> target = null;
        if (isSauronSword(item)) {
            target = bestEnchants(swordCategory, SWORD_KEYWORDS, 0);
        } else {
            DragonHead h = getHead(item);
            if (h != null) {
                target = bestEnchants(headCategory, HELM_KEYWORDS, h.rankFromTop);
            } else {
                DragonBoots b = getBoots(item);
                if (b != null) target = bestEnchants(bootsCategory, BOOTS_KEYWORDS, b.rankFromTop);
            }
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        boolean changed = ukryjEnchantTooltip(meta);
        if (target != null && !item.getEnchantments().equals(target)) {
            for (Enchantment e : new ArrayList<>(meta.getEnchants().keySet())) {
                meta.removeEnchant(e);
            }
            for (Map.Entry<Enchantment, Integer> e : target.entrySet()) {
                meta.addEnchant(e.getKey(), e.getValue(), true);
            }
            changed = true;
        }
        if (changed) item.setItemMeta(meta);
        return changed;
    }

    /** Glowki/buty/miecz Saurona: glowka glowki, bez listy enchantow i atrybutow w tooltipie. */
    private static boolean ukryjEnchantTooltip(ItemMeta meta) {
        if (meta == null) return false;
        boolean changed = false;
        ItemFlag[] flags = {
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE
        };
        for (ItemFlag f : flags) {
            if (!meta.hasItemFlag(f)) {
                meta.addItemFlags(f);
                changed = true;
            }
        }
        try {
            ItemFlag extra = ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP");
            if (!meta.hasItemFlag(extra)) {
                meta.addItemFlags(extra);
                changed = true;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return changed;
    }

    /** Odswieza nazwe/lore z configu oraz enchanty z /ulepsz. True, jesli item sie zmienil. */
    public boolean refreshItem(ItemStack item) {
        String tag = tagOf(item);
        if (tag == null) return false;

        boolean changed = false;
        if (TAG_SAURON_SWORD.equals(tag)) {
            changed |= ItemTagUtil.refresh(item, swordName, swordLore.isEmpty() ? null : swordLore);
            changed |= syncEnchants(item);
            return changed;
        }
        DragonHead h = getHead(item);
        if (h != null) {
            HeadCfg c = heads.get(h);
            changed |= ItemTagUtil.refresh(item, c.name, c.lore.isEmpty() ? null : c.lore);
            changed |= syncEnchants(item);
            return changed;
        }
        DragonBoots b = getBoots(item);
        if (b != null) {
            BootsCfg c = boots.get(b);
            changed |= ItemTagUtil.refresh(item, c.name, c.lore.isEmpty() ? null : c.lore);
            changed |= syncEnchants(item);
        }
        return changed;
    }

    // ===================== Gettery =====================

    public HeadCfg getHeadCfg(DragonHead head) { return heads.get(head); }
    public Main getPlugin() { return plugin; }
    public double getRadius() { return radius; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public int getSoundIntervalSeconds() { return soundIntervalSeconds; }
    public int getStrengthReduction() { return strengthReduction; }

    private static String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static List<String> colList(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) for (String s : in) out.add(col(s));
        return out;
    }
}