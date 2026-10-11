package boniec.code.core.backup;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;

public class BackupManager {

    public static final String PENDING  = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String GIVEN    = "GIVEN";
    public static final String REJECTED = "REJECTED";
    public static final String REVOKED  = "REVOKED";

    private final JavaPlugin plugin;
    private final BackupDatabase db;
    private final Object lock = new Object();

    public BackupManager(JavaPlugin plugin, BackupDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    public JavaPlugin getPlugin() { return plugin; }

    // ============================== ŚMIERCI ==============================

    public List<DeathRecord> getDeaths(UUID uuid, String name) throws SQLException {
        synchronized (lock) {
            List<DeathRecord> list = new ArrayList<>();
            Connection conn = db.getConnection();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, uuid, player_name, death_time, items_json, armor_json, killer_name, ranking FROM deaths " +
                            "WHERE uuid = ? OR LOWER(player_name) = LOWER(?) " +
                            "ORDER BY death_time DESC LIMIT " + BackupGuiLayout.CONTENT_SLOTS.length)) {
                ps.setString(1, uuid != null ? uuid.toString() : "");
                ps.setString(2, name != null ? name : "");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        DeathRecord dr = new DeathRecord(
                                rs.getInt("id"),
                                rs.getString("uuid"),
                                rs.getString("player_name"),
                                rs.getLong("death_time"),
                                rs.getString("items_json"),
                                rs.getString("armor_json"));
                        dr.killerName = rs.getString("killer_name");
                        try {
                            int r = rs.getInt("ranking");
                            dr.ranking = rs.wasNull() ? -1 : r;
                        } catch (SQLException ignored) {
                            dr.ranking = -1;
                        }
                        list.add(dr);
                    }
                }
            }

            attachStatuses(conn, list);
            return list;
        }
    }

    /** Dokleja do każdej śmierci status NAJNOWSZEGO wniosku. */
    private void attachStatuses(Connection conn, List<DeathRecord> deaths) throws SQLException {
        if (deaths.isEmpty()) return;

        StringBuilder in = new StringBuilder();
        for (DeathRecord d : deaths) {
            if (in.length() > 0) in.append(',');
            in.append(d.id);
        }

        Map<Integer, String> statuses = new HashMap<>();
        Map<Integer, Integer> ids = new HashMap<>();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, death_id, status FROM backups WHERE death_id IN (" + in + ") ORDER BY id ASC")) {
            while (rs.next()) {
                int deathId = rs.getInt("death_id");
                statuses.put(deathId, rs.getString("status")); // ostatni (najwyższe id) wygrywa
                ids.put(deathId, rs.getInt("id"));
            }
        }

        for (DeathRecord d : deaths) {
            d.backupStatus = statuses.get(d.id);
            d.backupId = ids.getOrDefault(d.id, -1);
        }
    }

    // ============================== WNIOSKI ==============================

    /**
     * Tworzy wniosek o weryfikację (PENDING). NIC nie jest wydawane graczowi.
     * @return id wniosku, 0 przy błędzie, -1 gdy dla tej śmierci istnieje już aktywny wniosek
     */
    public int createVerificationRequest(Player staff, UUID targetUuid, String targetName, int deathId) throws SQLException {
        synchronized (lock) {
            Connection conn = db.getConnection();

            String itemsJson = "", armorJson = "";
            String realUuid = targetUuid != null ? targetUuid.toString() : "";
            String realName = targetName != null ? targetName : "";
            int ranking = -1;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, player_name, items_json, armor_json, ranking FROM deaths WHERE id = ?")) {
                ps.setInt(1, deathId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 0;
                    itemsJson = rs.getString("items_json");
                    armorJson = rs.getString("armor_json");
                    String u = rs.getString("uuid");
                    String n = rs.getString("player_name");
                    if (u != null && !u.isEmpty()) realUuid = u;
                    if (n != null && !n.isEmpty()) realName = n;
                    try {
                        int r = rs.getInt("ranking");
                        ranking = rs.wasNull() ? -1 : r;
                    } catch (SQLException ignored) {
                        ranking = -1;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM backups WHERE death_id = ? AND status IN ('PENDING','APPROVED','GIVEN') LIMIT 1")) {
                ps.setInt(1, deathId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return -1;
                }
            }

            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO backups (death_id, target_uuid, target_name, status, requested_by_uuid, requested_by_name," +
                            " verified_by_uuid, verified_by_name, items_json, armor_json, delivered, needs_strip, created_at, updated_at, ranking) " +
                            "VALUES (?, ?, ?, 'PENDING', ?, ?, NULL, NULL, ?, ?, 0, 0, ?, ?, ?)")) {
                ps.setInt(1, deathId);
                ps.setString(2, realUuid);
                ps.setString(3, realName);
                ps.setString(4, staff.getUniqueId().toString());
                ps.setString(5, staff.getName());
                ps.setString(6, itemsJson);
                ps.setString(7, armorJson);
                ps.setLong(8, now);
                ps.setLong(9, now);
                ps.setInt(10, ranking);
                ps.executeUpdate();
            }

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<BackupEntry> getPending() throws SQLException {
        synchronized (lock) {
            List<BackupEntry> list = new ArrayList<>();
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM backups WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT "
                            + BackupGuiLayout.CONTENT_SLOTS.length);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(read(rs));
            }
            return list;
        }
    }

    /** Backupy gotowe do odebrania przez gracza (/odbierzbackup). */
    public List<BackupEntry> getClaimable(UUID uuid, String name) throws SQLException {
        return listForTarget("AND status = 'APPROVED'", uuid, name);
    }

    /** Backupy możliwe do cofnięcia przez staff (/cofnijbackup). */
    public List<BackupEntry> getRevocable(UUID uuid, String name) throws SQLException {
        return listForTarget("AND status IN ('APPROVED','GIVEN')", uuid, name);
    }

    private List<BackupEntry> listForTarget(String extra, UUID uuid, String name) throws SQLException {
        synchronized (lock) {
            List<BackupEntry> list = new ArrayList<>();
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM backups WHERE (target_uuid = ? OR LOWER(target_name) = LOWER(?)) " + extra +
                            " ORDER BY updated_at DESC LIMIT " + BackupGuiLayout.CONTENT_SLOTS.length)) {
                ps.setString(1, uuid != null ? uuid.toString() : "");
                ps.setString(2, name != null ? name : "");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(read(rs));
                }
            }
            return list;
        }
    }

    public BackupEntry getEntry(int id) throws SQLException {
        synchronized (lock) {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM backups WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? read(rs) : null;
                }
            }
        }
    }

    public int countActive(UUID uuid, String name) throws SQLException {
        synchronized (lock) {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM backups WHERE (target_uuid = ? OR LOWER(target_name) = LOWER(?)) " +
                            "AND status IN ('APPROVED','GIVEN')")) {
                ps.setString(1, uuid != null ? uuid.toString() : "");
                ps.setString(2, name != null ? name : "");
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }
    }

    private BackupEntry read(ResultSet rs) throws SQLException {
        BackupEntry e = new BackupEntry(
                rs.getInt("id"),
                rs.getInt("death_id"),
                rs.getString("target_uuid"),
                rs.getString("target_name"),
                rs.getString("status"),
                rs.getString("requested_by_name"),
                rs.getString("verified_by_name"),
                rs.getString("items_json"),
                rs.getString("armor_json"),
                rs.getInt("delivered"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
        try {
            int r = rs.getInt("ranking");
            e.ranking = rs.wasNull() ? -1 : r;
        } catch (SQLException ignored) {
            e.ranking = -1;
        }
        return e;
    }

    // ============================== ZMIANY STATUSÓW ==============================

    /** PENDING -> APPROVED (bez wydawania itemów, gracz odbiera /odbierzbackup) */
    public boolean approve(int backupId, Player admin) throws SQLException {
        synchronized (lock) {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE backups SET status='APPROVED', verified_by_uuid=?, verified_by_name=?, updated_at=? " +
                            "WHERE id=? AND status='PENDING'")) {
                ps.setString(1, admin.getUniqueId().toString());
                ps.setString(2, admin.getName());
                ps.setLong(3, System.currentTimeMillis());
                ps.setInt(4, backupId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    /** PENDING -> REJECTED */
    public boolean reject(int backupId, Player admin) throws SQLException {
        synchronized (lock) {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE backups SET status='REJECTED', verified_by_uuid=?, verified_by_name=?, updated_at=? " +
                            "WHERE id=? AND status='PENDING'")) {
                ps.setString(1, admin.getUniqueId().toString());
                ps.setString(2, admin.getName());
                ps.setLong(3, System.currentTimeMillis());
                ps.setInt(4, backupId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    /** APPROVED -> GIVEN (gracz odebrał itemy) */
    public boolean claim(int backupId) throws SQLException {
        synchronized (lock) {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE backups SET status='GIVEN', delivered=1, needs_strip=0, updated_at=? " +
                            "WHERE id=? AND status='APPROVED'")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setInt(2, backupId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    /** APPROVED/GIVEN -> REVOKED */
    public boolean markRevoked(int backupId) throws SQLException {
        synchronized (lock) {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE backups SET status='REVOKED', updated_at=? WHERE id=? AND status IN ('APPROVED','GIVEN')")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setInt(2, backupId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public void setNeedsStrip(int backupId, boolean value) throws SQLException {
        synchronized (lock) {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement("UPDATE backups SET needs_strip=? WHERE id=?")) {
                ps.setInt(1, value ? 1 : 0);
                ps.setInt(2, backupId);
                ps.executeUpdate();
            }
        }
    }

    public List<BackupEntry> getPendingStrips(UUID uuid, String name) throws SQLException {
        return listForTarget("AND status='REVOKED' AND needs_strip=1", uuid, name);
    }

    // ============================== JOIN ==============================

    public void handlePlayerJoin(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        UUID uuid = player.getUniqueId();
                        String name = player.getName();

                        // 1) zabranie itemów po backupie cofniętym gdy gracz był offline
                        for (BackupEntry entry : getPendingStrips(uuid, name)) {
                            final ItemStack[] items = BackupSerializer.deserializeItemArray(entry.itemsJson);
                            final ItemStack[] armor = BackupSerializer.deserializeItemArray(entry.armorJson);
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                int removed = stripItemsFromPlayer(player, items, armor);
                                player.sendMessage("§8[§cBACKUP§8] §cTwój backup został cofnięty podczas Twojej nieobecności.");
                                player.sendMessage("§8[§cBACKUP§8] §cUsunięto §f" + removed + " §cszt. przedmiotów z ekwipunku.");
                            });
                            setNeedsStrip(entry.id, false);
                        }

                        // 2) info o backupach czekających na odbiór
                        int claimable = getClaimable(uuid, name).size();
                        if (claimable > 0) {
                            final int count = claimable;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                player.sendMessage("§8§m------------------------------------");
                                player.sendMessage("§8[§aBACKUP§8] §aMasz §e" + count + " §abackup(ów) do odebrania!");
                                player.sendMessage("§8[§aBACKUP§8] §7Wpisz §e/odbierzbackup §7aby je odebrać.");
                                player.sendMessage("§8§m------------------------------------");
                            });
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Blad sprawdzania backupow dla " + player.getName() + ": " + e.getMessage());
                    }
                }), 20L);
    }

    // ============================== OPERACJE NA ITEMACH ==============================

    public static void giveItemsToPlayer(Player player, ItemStack[] items, ItemStack[] armor) {
        if (armor != null) {
            ItemStack[] current = player.getInventory().getArmorContents();
            for (int i = 0; i < Math.min(current.length, armor.length); i++) {
                ItemStack arm = armor[i];
                if (arm == null || arm.getType() == Material.AIR) continue;

                if (current[i] == null || current[i].getType() == Material.AIR) {
                    // slot pusty -> po prostu zakładamy zbroję z backupu
                    current[i] = arm.clone();
                } else if (current[i].isSimilar(arm)) {
                    // gracz ma już założoną DOKŁADNIE tę samą zbroję (np. bo nie zdjęła się
                    // przy śmierci / keepInventory) -> pomijamy, żeby nie zduplikować
                    continue;
                } else {
                    // w slocie jest inny przedmiot niż backupowana zbroja -> nie nadpisujemy
                    // jej na siłę, tylko próbujemy dołożyć do plecaka / upuścić na ziemię
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(arm.clone());
                    for (ItemStack extra : leftover.values())
                        player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
            player.getInventory().setArmorContents(current);
        }

        if (items != null) {
            // Zliczamy to, co gracz już ma w ekwipunku (poza zbroją), żeby nie dokładać
            // drugi raz przedmiotów, które i tak już posiada (np. bo nie wypadły przy śmierci).
            for (ItemStack item : items) {
                if (item == null || item.getType() == Material.AIR) continue;

                int toGive = item.getAmount();
                for (ItemStack slot : player.getInventory().getContents()) {
                    if (toGive <= 0) break;
                    if (slot != null && slot.isSimilar(item)) {
                        toGive -= Math.min(toGive, slot.getAmount());
                    }
                }
                if (toGive <= 0) continue; // gracz ma już wszystko z tego stacku

                ItemStack toAdd = item.clone();
                toAdd.setAmount(toGive);

                Map<Integer, ItemStack> leftover = player.getInventory().addItem(toAdd);
                for (ItemStack extra : leftover.values())
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
        player.updateInventory();
    }

    public static int stripItemsFromPlayer(Player player, ItemStack[] items, ItemStack[] armor) {
        List<ItemStack> toRemove = new ArrayList<>();
        if (armor != null) for (ItemStack it : armor) if (it != null && it.getType() != Material.AIR) toRemove.add(it.clone());
        if (items != null) for (ItemStack it : items) if (it != null && it.getType() != Material.AIR) toRemove.add(it.clone());

        int totalRemoved = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (ItemStack target : toRemove) {
            int remaining = target.getAmount();

            // 1) dokładne dopasowanie
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (slot != null && slot.isSimilar(target)) {
                    int take = Math.min(remaining, slot.getAmount());
                    remaining -= take; totalRemoved += take;
                    slot.setAmount(slot.getAmount() - take);
                    if (slot.getAmount() <= 0) contents[i] = null;
                }
            }

            // 2) typ + nazwa (zmieniona trwałość itp.)
            if (remaining > 0) {
                for (int i = 0; i < contents.length && remaining > 0; i++) {
                    ItemStack slot = contents[i];
                    if (slot != null && slot.getType() == target.getType()) {
                        boolean nameMatch = true;
                        if (target.hasItemMeta() && target.getItemMeta().hasDisplayName()) {
                            nameMatch = slot.hasItemMeta() && slot.getItemMeta().hasDisplayName()
                                    && slot.getItemMeta().getDisplayName().equals(target.getItemMeta().getDisplayName());
                        }
                        if (nameMatch) {
                            int take = Math.min(remaining, slot.getAmount());
                            remaining -= take; totalRemoved += take;
                            slot.setAmount(slot.getAmount() - take);
                            if (slot.getAmount() <= 0) contents[i] = null;
                        }
                    }
                }
            }

            // 3) sam typ dla sprzętu / narzędzi
            if (remaining > 0 && isGearOrTool(target.getType())) {
                for (int i = 0; i < contents.length && remaining > 0; i++) {
                    ItemStack slot = contents[i];
                    if (slot != null && slot.getType() == target.getType()) {
                        int take = Math.min(remaining, slot.getAmount());
                        remaining -= take; totalRemoved += take;
                        slot.setAmount(slot.getAmount() - take);
                        if (slot.getAmount() <= 0) contents[i] = null;
                    }
                }
            }
        }

        player.getInventory().setContents(contents);
        player.updateInventory();
        return totalRemoved;
    }

    private static boolean isGearOrTool(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.equals("ELYTRA") || n.equals("BOW") || n.equals("CROSSBOW")
                || n.equals("TRIDENT") || n.equals("MACE") || n.equals("SHIELD");
    }
}