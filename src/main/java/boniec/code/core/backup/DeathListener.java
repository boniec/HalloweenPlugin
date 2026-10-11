package boniec.code.core.backup;

import boniec.dev.inventory.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.logging.Level;

public class DeathListener implements Listener {
    private final JavaPlugin plugin;
    private final BackupDatabase db;

    public DeathListener(JavaPlugin plugin, BackupDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    /**
     * LOWEST — snapshot pelnego eq ZANIM Main/sakiewka cos zabiora.
     * Serializacja i INSERT na watku glownym (ItemStack nie jest thread-safe;
     * async zapis gubil backupy przy restarcie / wyścigu z czyszczeniem eq).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (p == null) return;

        UUID uuid = p.getUniqueId();
        String name = p.getName();
        int level = p.getLevel();
        long deathTime = System.currentTimeMillis();
        String killerName = p.getKiller() != null ? p.getKiller().getName() : null;

        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = cloneArr(inv.getStorageContents());
        ItemStack[] armor = cloneArr(inv.getArmorContents());
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            ItemStack[] withOff = new ItemStack[storage.length + 1];
            System.arraycopy(storage, 0, withOff, 0, storage.length);
            withOff[storage.length] = offhand.clone();
            storage = withOff;
        }

        boolean any = hasAny(storage) || hasAny(armor);
        if (!any) {
            plugin.getLogger().info("[Backup] Smierc " + name + " bez itemow — zapisuje ranking.");
        }

        String itemsJson = BackupSerializer.serializeItemArray(storage);
        String armorJson = BackupSerializer.serializeItemArray(armor);
        if (any && (itemsJson == null || itemsJson.isEmpty()) && (armorJson == null || armorJson.isEmpty())) {
            plugin.getLogger().warning("[Backup] Serializacja eq gracza " + name + " zwrocila pusto — ponawiam.");
            itemsJson = BackupSerializer.serializeItemArray(storage);
            armorJson = BackupSerializer.serializeItemArray(armor);
        }

        int ranking = 1000;
        if (plugin instanceof Main main && main.getKlanManager() != null) {
            ranking = main.getKlanManager().getRankingGracza(uuid);
        }

        final String items = itemsJson == null ? "" : itemsJson;
        final String armorJ = armorJson == null ? "" : armorJson;
        final String killer = killerName;
        final int rankingFinal = ranking;

        try {
            db.getConnection();
            boniec.code.core.CoreDatabase.runLocked(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO deaths (uuid, player_name, death_time, items_json, armor_json, exp_level, killer_name, ranking) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setLong(3, deathTime);
                    ps.setString(4, items);
                    ps.setString(5, armorJ);
                    ps.setInt(6, level);
                    ps.setString(7, killer);
                    ps.setInt(8, rankingFinal);
                    ps.executeUpdate();
                }
            });
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[Backup] Nie udalo sie zapisac smierci gracza " + name, ex);
        }
    }

    private static ItemStack[] cloneArr(ItemStack[] src) {
        if (src == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            if (src[i] != null && src[i].getType() != Material.AIR) {
                out[i] = src[i].clone();
            }
        }
        return out;
    }

    private static boolean hasAny(ItemStack[] arr) {
        if (arr == null) return false;
        for (ItemStack it : arr) {
            if (it != null && it.getType() != Material.AIR) return true;
        }
        return false;
    }
}
