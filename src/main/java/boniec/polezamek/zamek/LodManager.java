package boniec.dev.polezamek.zamek;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.util.ItemTagUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Odpowiednik sekcji "LOD NA PATYKU" z drugiego skryptu. */
public class LodManager {

    public static final Material LOD_MATERIAL = Material.STICK;

    public static final int LOD_CUSTOM_MODEL_DATA = 1001;
    private int customModelData = LOD_CUSTOM_MODEL_DATA;

    // Nazwa i lore (konfigurowalne)
    private String lodName;
    private NamespacedKey itemKey;
    private final java.util.Set<String> legacyNames = new java.util.HashSet<>();
    private List<String> lodLore;

    private int cooldownSeconds;
    private int victimProtectionSeconds;
    private int levitationSeconds;

    // Wiadomosci
    private String msgOnCooldown;
    private String msgVictimProtected;
    private String msgHitOther;
    private String msgHitSelf;
    private String msgGiven;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> victimProtection = new ConcurrentHashMap<>();

    public LodManager(Main plugin) {
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    public void reloadConfig(Main plugin) {
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    private void loadConfig(Main plugin) {
        FileConfiguration cfg = plugin.getConfig();

        if (this.lodName != null) legacyNames.add(this.lodName);
        this.lodName = col(cfg.getString("lod.name", "&5&lʟÓᴅ ɴᴀ ᴘᴀᴛʏᴋᴜ"));
        this.customModelData = cfg.getInt("lod.custom-model-data", LOD_CUSTOM_MODEL_DATA);
        legacyNames.add(this.lodName);
        this.lodLore = cfg.getStringList("lod.lore");

        this.cooldownSeconds = cfg.getInt("lod.cooldown-seconds", 60);
        this.victimProtectionSeconds = cfg.getInt("ochrona-celu-sekundy", 60);
        this.levitationSeconds = cfg.getInt("lod.levitation-seconds", 8);

        this.msgOnCooldown = cfg.getString("messages.lod-on-cooldown", "&cLód jest jeszcze na cooldownie &8(&4{czas}s&8)");
        this.msgVictimProtected = cfg.getString("messages.lod-victim-protected", "&cTen gracz jest już pod wpływem &eLodu na patyku &8(&4{czas}s&8)!");
        this.msgHitOther = cfg.getString("messages.lod-hit-other", "&dUderzyłeś &5{player} &dLodem na patyku!");
        this.msgHitSelf = cfg.getString("messages.lod-hit-self", "&dZostałeś uderzony &5Lodem na patyku &dprzez &c{player}&d!");
        this.msgGiven = cfg.getString("messages.lod-given", "&aOtrzymałeś Lód na patyku!");
    }

    public ItemStack createLodItem() {
        return createLodItem(LOD_MATERIAL);
    }

    public ItemStack createLodItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(lodName);
        ItemTagUtil.applyModel(meta, customModelData);

        if (lodLore != null && !lodLore.isEmpty()) {
            meta.setLore(lodLore.stream().map(LodManager::col).toList());
        } else {
            meta.setLore(List.of(
                    col("&0"),
                    col("&8x &fPo uderzeniu gracza nadajemy mu oraz"),
                    col("&8x &fsobie &eefekt lewitacji &fna określony czas"),
                    col("&0")
            ));
        }

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        ItemTagUtil.setTag(itemKey, meta, "lod");
        item.setItemMeta(meta);
        return item;
    }

    /** Rozpoznaje po tagu PDC (nie po nazwie); stare itemy bez tagu sa migrowane. */
    public boolean isLodItem(ItemStack item) {
        return ItemTagUtil.matches(itemKey, item, "lod", legacyNames.toArray(new String[0]));
    }

    /** Odswieza nazwe i lore itemu do aktualnych wartosci z configu (gdy to Lod). */
    public boolean refreshItem(ItemStack item) {
        if (!isLodItem(item)) return false;
        boolean changed = ItemTagUtil.refresh(item, lodName, buildLore());
        changed |= ItemTagUtil.syncModel(item, customModelData);
        return changed;
    }

    private List<String> buildLore() {
        if (lodLore != null && !lodLore.isEmpty()) {
            return lodLore.stream().map(LodManager::col).toList();
        }
        return List.of(
                col("&0"),
                col("&8x &fPo uderzeniu gracza nadajemy mu oraz"),
                col("&8x &fsobie &eefekt lewitacji &fna określony czas"),
                col("&0"));
    }

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

    public boolean isVictimProtected(Player victim) {
        Long until = victimProtection.get(victim.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long getVictimProtectionRemainingSeconds(Player victim) {
        Long until = victimProtection.get(victim.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }

    public void setVictimProtectedNow(Player victim) {
        victimProtection.put(victim.getUniqueId(), System.currentTimeMillis() + victimProtectionSeconds * 1000L);
    }

    public void resetPlayer(Player player) {
        cooldowns.remove(player.getUniqueId());
        victimProtection.remove(player.getUniqueId());
    }

    public int getLevitationSeconds() {
        return levitationSeconds;
    }

    // Wiadomosci
    public String getMsgOnCooldown(long seconds) { return msgOnCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgVictimProtected(long seconds) { return msgVictimProtected.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgHitOther(String name) { return msgHitOther.replace("{player}", name); }
    public String getMsgHitSelf(String name) { return msgHitSelf.replace("{player}", name); }
    public String getMsgGiven() { return msgGiven; }

    private static String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
