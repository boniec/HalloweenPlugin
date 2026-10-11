package boniec.dev.polezamek.witchwand;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.util.ItemTagUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Galaktyczna rozdzka: PPM = lewitacja dla graczy w promieniu. LPM nic nie robi. */
public class WitchWandManager {

    public static final Material WAND_MATERIAL = Material.END_ROD;
    public static final int WAND_CUSTOM_MODEL_DATA = 1001;
    private int customModelData = WAND_CUSTOM_MODEL_DATA;

    private String wandName;
    private NamespacedKey itemKey;
    private final java.util.Set<String> legacyNames = new java.util.HashSet<>();
    private List<String> wandLore;

    private int radius;
    private int cooldownSeconds;

    private int leftAmplifier;
    private int leftDurationSeconds;
    private int rightAmplifier;
    private int rightDurationSeconds;

    // Wiadomosci
    private String msgOnCooldown;
    private String msgLeftUsed;
    private String msgRightUsed;
    private String msgGiven;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public WitchWandManager(Main plugin) {
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    public void reloadConfig(Main plugin) {
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    private void loadConfig(Main plugin) {
        FileConfiguration cfg = plugin.getConfig();

        if (this.wandName != null) legacyNames.add(this.wandName);
        this.wandName = col(cfg.getString("witchwand.name",
                "&f七 &x&3&1&E&A&F&C&lɢ&x&3&1&E&C&E&F&lᴀ&x&3&1&E&D&E&2&lʟ&x&3&1&E&F&D&5&lᴀ&x&3&1&F&1&C&8&lᴋ&x&3&1&F&2&B&B&lᴛ&x&3&1&F&4&A&D&lʏ&x&3&1&F&5&A&0&lᴄ&x&3&1&F&7&9&3&lᴢ&x&3&1&F&9&8&6&lɴ&x&3&1&F&A&7&9&lᴀ &f&lʀᴏᴢᴅᴢᴋᴀ"));
        this.customModelData = cfg.getInt("witchwand.custom-model-data", WAND_CUSTOM_MODEL_DATA);
        legacyNames.add(this.wandName);
        this.wandLore = cfg.getStringList("witchwand.lore");

        this.radius = cfg.getInt("witchwand.radius", 7);
        this.cooldownSeconds = cfg.getInt("witchwand.cooldown-seconds", 45);

        this.leftAmplifier = cfg.getInt("witchwand.left-effect.amplifier", 0);
        this.leftDurationSeconds = 5;
        this.rightAmplifier = cfg.getInt("witchwand.right-effect.amplifier", 1);
        this.rightDurationSeconds = 5;

        this.msgOnCooldown = cfg.getString("messages.witchwand-on-cooldown", "&cGalaktyczna różdżka jest jeszcze na cooldownie &8(&4{czas}s&8)");
        this.msgLeftUsed = cfg.getString("messages.witchwand-left-used", "");
        this.msgRightUsed = cfg.getString("messages.witchwand-right-used", "");
        this.msgGiven = cfg.getString("messages.witchwand-given", "&aOtrzymałeś Galaktyczną Różdżkę!");
    }

    public ItemStack createWandItem() {
        ItemStack item = new ItemStack(WAND_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(wandName);
        ItemTagUtil.applyModel(meta, customModelData);

        if (wandLore != null && !wandLore.isEmpty()) {
            meta.setLore(wandLore.stream().map(WitchWandManager::col).toList());
        } else {
            meta.setLore(buildDefaultLore());
        }

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        ItemTagUtil.setTag(itemKey, meta, "witchwand");
        item.setItemMeta(meta);
        return item;
    }

    private List<String> buildDefaultLore() {
        return List.of(
                col("&f"),
                col("&f 七 ᴅᴢɪᴇᴋɪ ᴛᴇᴊ &x&3&1&E&A&F&Cɢᴀʟᴀᴋᴛʏᴄᴢɴᴇᴊ &fʀᴏᴢᴅᴢᴄᴇ"),
                col("&f ʀᴢᴜᴄᴀꜱᴢ &x&C&0&2&F&D&4ʟᴇᴡɪᴛᴀᴄᴊᴇ &fɴᴀ ɢʀᴀᴄᴢʏ ᴡᴏᴋᴏʟ"),
                col("&f"),
                col("&f  &8» &eᴘᴘᴍ &7- ʟᴇᴡɪᴛᴀᴄᴊᴀ ᴡ ᴘʀᴏᴍɪᴇɴɪᴜ"),
                col("&f")
        );
    }

    /** Rozpoznaje po tagu PDC (nie po nazwie); stare itemy bez tagu sa migrowane. */
    public boolean isWandItem(ItemStack item) {
        return ItemTagUtil.matches(itemKey, item, "witchwand", legacyNames.toArray(new String[0]));
    }

    /** Odswieza nazwe i lore itemu do aktualnych wartosci z configu (gdy to rozdzka). */
    public boolean refreshItem(ItemStack item) {
        if (!isWandItem(item)) return false;
        List<String> lore = (wandLore != null && !wandLore.isEmpty())
                ? wandLore.stream().map(WitchWandManager::col).toList()
                : buildDefaultLore();
        boolean changed = ItemTagUtil.refresh(item, wandName, lore);
        changed |= ItemTagUtil.syncModel(item, customModelData);
        return changed;
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

    public void resetPlayer(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public int getRadius() { return radius; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getLeftAmplifier() { return leftAmplifier; }
    public int getLeftDurationSeconds() { return leftDurationSeconds; }
    public int getRightAmplifier() { return rightAmplifier; }
    public int getRightDurationSeconds() { return rightDurationSeconds; }

    // Wiadomosci
    public String getMsgOnCooldown(long seconds) { return msgOnCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgLeftUsed() { return msgLeftUsed; }
    public String getMsgRightUsed(int count) { return msgRightUsed.replace("{count}", String.valueOf(count)); }
    public String getMsgGiven() { return msgGiven; }

    private static String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
