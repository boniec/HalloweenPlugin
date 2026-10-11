package boniec.dev.polezamek.polesmierci;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.util.ItemTagUtil;
import boniec.dev.polezamek.util.WorldGuardUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Odpowiednik pierwszego skryptu ("SKRYPT NA POLE SMIERCI").
 *
 * Trzyma stan (aktywne pola, cooldowny, blokady elytry, zapisane lokalizacje
 * spawnu) oraz logike sprawdzania, czy dana lokalizacja jest zablokowana.
 */
public class PoleSmierciManager {

    public static final Material ITEM_MATERIAL = Material.FERMENTED_SPIDER_EYE;

    private Main plugin;

    // Nazwa i lore (konfigurowalne)
    private String poleName;
    private NamespacedKey itemKey;
    /** Dawne nazwy (sprzed zmiany w configu) - do migracji starych itemow bez tagu. */
    private final java.util.Set<String> legacyNames = new java.util.HashSet<>();
    private List<String> poleLore;

    private int radius;
    private int durationSeconds;
    private int cooldownSeconds;
    private int regionBlockDistance;

    // Wiadomosci
    private String msgOnCooldown;
    private String msgActivated;
    private String msgExpired;
    private String msgLaserHurt;
    private String msgBlockedFirework;
    private String msgNearSpawn;
    private String msgNearbyActive;
    private String msgCastleActive;
    private String msgChunkLocked;
    private String msgTooClose;
    private String msgGiven;
    private String msgCooldownReset;
    private String msgCooldownResetSelf;

    /** Aktywne pola. Niepuste == na serwerze aktywne jest jakies Pole Smierci. */
    private final java.util.List<ActiveField> activeFields = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> elytraBlock = new ConcurrentHashMap<>();

    private File dataFile;
    private FileConfiguration data;
    private Location spawnLoc;
    private Location spawnPvpLoc;

    public PoleSmierciManager(Main plugin) {
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

        if (this.poleName != null) legacyNames.add(this.poleName);
        this.poleName = col(cfg.getString("polesmierci.name", "&c&lᴘᴏʟᴇ śᴍɪᴇʀcɪ"));
        this.customModelData = cfg.getInt("polesmierci.custom-model-data", POLE_CUSTOM_MODEL_DATA);
        legacyNames.add(this.poleName);
        this.poleLore = cfg.getStringList("polesmierci.lore");

        this.radius = cfg.getInt("polesmierci.radius", 5);
        this.durationSeconds = cfg.getInt("polesmierci.duration-seconds", 5);
        this.cooldownSeconds = cfg.getInt("polesmierci.cooldown-seconds", 60);
        this.regionBlockDistance = cfg.getInt("polesmierci.region-block-distance", 10);

        this.msgOnCooldown = cfg.getString("messages.polesmierci-on-cooldown", "&cMusisz odczekać jeszcze &e{czas}s&c!");
        this.msgActivated = cfg.getString("messages.polesmierci-activated", "&aAktywowano &c&lᴘᴏʟᴇ śᴍɪᴇʀcɪ&a!");
        this.msgExpired = cfg.getString("messages.polesmierci-expired", "&7Pole śmierci wygasło.");
        this.msgLaserHurt = cfg.getString("messages.polesmierci-laser-hurt", "&cLasery Cię ranią! Elytra zablokowana!");
        this.msgBlockedFirework = cfg.getString("messages.polesmierci-blocked-firework", "&cNie możesz używać fajerwerków w polu śmierci!");
        this.msgNearSpawn = cfg.getString("messages.polesmierci-near-spawn", "&cNie możesz stawiać pola śmierci w pobliżu spawnu / spawnpvp!");
        this.msgNearbyActive = cfg.getString("messages.polesmierci-nearby-active", "&cW pobliżu jest już aktywne pole śmierci!");
        this.msgCastleActive = cfg.getString("messages.polesmierci-castle-active", "&cNie możesz postawić pola śmierci, dopóki w pobliżu stoi zamek! Poczekaj, aż zniknie.");
        this.msgChunkLocked = cfg.getString("messages.polesmierci-chunk-locked", "&cMusisz poczekać jeszcze &e{czas}s&c, zanim użyjesz pola/zamku w tym miejscu!");
        this.msgTooClose = cfg.getString("messages.polesmierci-too-close", "&cNie możesz postawić pola w pobliżu ostatniego zamku/pola! Poczekaj &e{czas}s&c!");
        this.msgGiven = cfg.getString("messages.polesmierci-given", "&8[&aKity&8] &r&aOtrzymano &c&lᴘᴏʟᴇ śᴍɪᴇʀcɪ&a!");
        this.msgCooldownReset = cfg.getString("messages.polesmierci-cooldown-reset", "&aZresetowano cooldown graczowi &f{player}&a!");
        this.msgCooldownResetSelf = cfg.getString("messages.polesmierci-cooldown-reset-self", "&aTwój cooldown pola śmierci został zresetowany!");
    }

    // ================= Persistencja lokalizacji spawnu =================

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "polesmierci-data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        spawnLoc = data.getLocation("spawn-loc");
        spawnPvpLoc = data.getLocation("spawnpvp-loc");
    }

    private void save() {
        data.set("spawn-loc", spawnLoc);
        data.set("spawnpvp-loc", spawnPvpLoc);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udalo sie zapisac polesmierci-data.yml: " + e.getMessage());
        }
    }

    public void setSpawnLoc(Location loc) {
        this.spawnLoc = loc;
        save();
    }

    public void setSpawnPvpLoc(Location loc) {
        this.spawnPvpLoc = loc;
        save();
    }

    public void clearSpawnLocs() {
        this.spawnLoc = null;
        this.spawnPvpLoc = null;
        save();
    }

    public Location getSpawnLoc() {
        return spawnLoc;
    }

    public Location getSpawnPvpLoc() {
        return spawnPvpLoc;
    }

    // ================= Item =================

    public static final int POLE_CUSTOM_MODEL_DATA = 1001;
    private int customModelData = POLE_CUSTOM_MODEL_DATA;

    public ItemStack createPoleItem() {
        return createPoleItem(ITEM_MATERIAL);
    }

    public ItemStack createPoleItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(poleName);
        ItemTagUtil.applyModel(meta, customModelData);

        if (poleLore != null && !poleLore.isEmpty()) {
            meta.setLore(poleLore.stream().map(PoleSmierciManager::col).toList());
        } else {
            meta.setLore(List.of(
                    col("&0"),
                    col("&8x &fW obrębie &a5 kratek &fdookoła zabieramy"),
                    col("&8x &fkażdemu graczowi możliwość używania &7elytry &fna kilka sekund"),
                    col("&8x &foraz tworzymy &estrefe z laserami, &fktóre"),
                    col("&8x &fzadają &cobrażenia &fkażdemu kto stanie im na drodze"),
                    col("&0"),
                    col("&aKliknij prawy aby użyć")
            ));
        }

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        ItemTagUtil.setTag(itemKey, meta, "polesmierci");
        item.setItemMeta(meta);
        return item;
    }

    /** Rozpoznaje po tagu PDC (nie po nazwie); stare itemy bez tagu sa migrowane. */
    public boolean isPoleSmierciItem(ItemStack item) {
        return ItemTagUtil.matches(itemKey, item, "polesmierci", legacyNames.toArray(new String[0]));
    }

    /** Odswieza nazwe i lore itemu do aktualnych wartosci z configu (gdy to Pole Smierci). */
    public boolean refreshItem(ItemStack item) {
        if (!isPoleSmierciItem(item)) return false;
        boolean changed = ItemTagUtil.refresh(item, poleName, buildLore());
        changed |= ItemTagUtil.syncModel(item, customModelData);
        return changed;
    }

    private List<String> buildLore() {
        if (poleLore != null && !poleLore.isEmpty()) {
            return poleLore.stream().map(PoleSmierciManager::col).toList();
        }
        return List.of(
                col("&0"),
                col("&8x &fW obrębie &a5 kratek &fdookoła zabieramy"),
                col("&8x &fkażdemu graczowi możliwość używania &7elytry &fna kilka sekund"),
                col("&8x &foraz tworzymy &estrefe z laserami, &fktóre"),
                col("&8x &fzadają &cobrażenia &fkażdemu kto stanie im na drodze"),
                col("&0"),
                col("&aKliknij prawy aby użyć"));
    }

    // ================= Cooldown / stan gracza =================

    public boolean isOnCooldown(Player player) {
        Long since = cooldowns.get(player.getUniqueId());
        if (since == null) return false;
        return (System.currentTimeMillis() - since) < cooldownSeconds * 1000L;
    }

    public long getCooldownRemainingSeconds(Player player) {
        Long since = cooldowns.get(player.getUniqueId());
        if (since == null) return 0;
        long remaining = cooldownSeconds * 1000L - (System.currentTimeMillis() - since);
        return Math.max(0, remaining / 1000L);
    }

    public void setCooldownNow(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void resetPlayer(Player player) {
        cooldowns.remove(player.getUniqueId());
        elytraBlock.remove(player.getUniqueId());
    }

    public void blockElytra(Player player) {
        player.setCooldown(Material.ELYTRA, 60); // ~3 sekundy przy 20 tickach/s
        player.setCooldown(Material.FIREWORK_ROCKET, 60);
        if (player.isGliding()) {
            player.setGliding(false);
        }
        elytraBlock.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isElytraCurrentlyBlocked(Player player) {
        Long since = elytraBlock.get(player.getUniqueId());
        if (since == null) return false;
        return (System.currentTimeMillis() - since) < 3000L;
    }

    public void clearExpiredElytraBlock(Player player) {
        Long since = elytraBlock.get(player.getUniqueId());
        if (since != null && (System.currentTimeMillis() - since) >= 3000L) {
            elytraBlock.remove(player.getUniqueId());
        }
    }

    // ================= Sprawdzanie zablokowanych stref =================

    public boolean isNearForbiddenSpawn(Location loc) {
        // Metoda 1 i 2: regiony WorldGuard zawierajace "spawn" w nazwie.
        if (plugin.isWorldGuardInstalled() && WorldGuardUtil.isInRegionContaining(loc, "spawn")) {
            return true;
        }

        // Metoda 3: zapisane lokalizacje spawnu.
        if (spawnLoc != null && sameWorld(loc, spawnLoc) && loc.distance(spawnLoc) <= regionBlockDistance) {
            return true;
        }
        if (spawnPvpLoc != null && sameWorld(loc, spawnPvpLoc) && loc.distance(spawnPvpLoc) <= regionBlockDistance) {
            return true;
        }

        // Metoda 4: gracze w poblizu stojacy na regionie "spawn".
        if (plugin.isWorldGuardInstalled() && loc.getWorld() != null) {
            for (org.bukkit.entity.Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distance(loc) <= regionBlockDistance) {
                    if (WorldGuardUtil.isInRegionContaining(p.getLocation(), "spawn")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }

    public boolean hasOtherActiveFieldNearby(Location center) {
        int r = radius;
        for (ActiveField field : activeFields) {
            if (sameWorld(center, field.center) && center.distance(field.center) < ((r * 2) + 2)) {
                return true;
            }
        }
        return false;
    }

    // ================= Aktywne pola =================

    /** @return true, jesli w tej chwili istnieje jakiekolwiek aktywne Pole Smierci na serwerze. */
    public boolean isAnyFieldActive() {
        return !activeFields.isEmpty();
    }

    /** @return true, jesli istnieje aktywne Pole Smierci w podanym promieniu od lokalizacji. */
    public boolean isAnyFieldActiveWithin(Location center, int distance) {
        for (ActiveField field : activeFields) {
            if (sameWorld(center, field.center) && center.distance(field.center) < distance) {
                return true;
            }
        }
        return false;
    }

    public ActiveField registerField(Location center, UUID activatorUUID) {
        ActiveField field = new ActiveField(center.clone(), radius, activatorUUID);
        activeFields.add(field);
        return field;
    }

    public void unregisterField(ActiveField field) {
        activeFields.remove(field);
    }

    public java.util.List<ActiveField> getActiveFields() {
        return activeFields;
    }

    public int getRadius() {
        return radius;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getRegionBlockDistance() {
        return regionBlockDistance;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    // Wiadomosci
    public String getMsgOnCooldown(long seconds) { return msgOnCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgActivated() { return msgActivated; }
    public String getMsgExpired() { return msgExpired; }
    public String getMsgLaserHurt() { return msgLaserHurt; }
    public String getMsgBlockedFirework() { return msgBlockedFirework; }
    public String getMsgNearSpawn() { return msgNearSpawn; }
    public String getMsgNearbyActive() { return msgNearbyActive; }
    public String getMsgCastleActive() { return msgCastleActive; }
    public String getMsgChunkLocked(long seconds) { return msgChunkLocked.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgTooClose(long seconds) { return msgTooClose.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgGiven() { return msgGiven; }
    public String getMsgCooldownReset(String name) { return msgCooldownReset.replace("{player}", name); }
    public String getMsgCooldownResetSelf() { return msgCooldownResetSelf; }
    public String getPoleName() { return poleName; }

    public void shutdown() {
        // Nic dodatkowego do posprzatania - zadania BukkitRunnable koncza sie
        // same z serwerem, a bloki zostaja przywrocone przy nastepnym starcie
        // reczne (pole smierci jest efemeryczne, nie odtwarzamy go po restarcie).
    }

    private static String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static class ActiveField {
        public final Location center;
        public final int radius;
        public final UUID activatorUUID;
        public final List<Location> savedLocs = new ArrayList<>();
        public final List<Material> savedTypes = new ArrayList<>();

        public ActiveField(Location center, int radius, UUID activatorUUID) {
            this.center = center;
            this.radius = radius;
            this.activatorUUID = activatorUUID;
        }
    }
}
