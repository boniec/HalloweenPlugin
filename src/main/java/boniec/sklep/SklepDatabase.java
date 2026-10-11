package boniec.sklep;

import boniec.code.core.CoreDatabase;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SklepDatabase {

    public record ShopEntry(int slot, ItemStack item, double price) {}

    public record TimeEntry(int slot, ItemStack item, long timeSeconds) {}

    private final JavaPlugin plugin;
    private final Map<UUID, Long> playtimeDelta = new ConcurrentHashMap<>();

    public SklepDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            CoreDatabase.open(plugin);
            CoreDatabase.runLocked(c -> {
                try (Statement st = c.createStatement()) {
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS buy_items (
                            slot INTEGER PRIMARY KEY,
                            item_data BLOB NOT NULL,
                            price REAL NOT NULL
                        );
                    """);
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS sell_items (
                            slot INTEGER PRIMARY KEY,
                            item_data BLOB NOT NULL,
                            price REAL NOT NULL
                        );
                    """);
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS time_items (
                            slot INTEGER PRIMARY KEY,
                            item_data BLOB NOT NULL,
                            time_seconds INTEGER NOT NULL
                        );
                    """);
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS player_time (
                            uuid TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            time_seconds INTEGER NOT NULL DEFAULT 0
                        );
                    """);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[Sklep] Blad bazy: " + e.getMessage());
        }
    }

    public void disconnect() {
        flushPlaytime();
    }

    public Map<Integer, ShopEntry> loadBuyItems() {
        Map<Integer, ShopEntry> map = new LinkedHashMap<>();
        if (!CoreDatabase.isOpen()) return map;
        try {
            return CoreDatabase.runLockedQuery(c -> {
                Map<Integer, ShopEntry> out = new LinkedHashMap<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT slot, item_data, price FROM buy_items ORDER BY slot");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ItemStack item = deserialize(rs.getBytes("item_data"));
                        if (item == null) continue;
                        out.put(rs.getInt("slot"), new ShopEntry(rs.getInt("slot"), item, rs.getDouble("price")));
                    }
                }
                return out;
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("[Sklep] loadBuyItems: " + e.getMessage());
            return map;
        }
    }

    public Map<Integer, TimeEntry> loadTimeItems() {
        Map<Integer, TimeEntry> map = new LinkedHashMap<>();
        if (!CoreDatabase.isOpen()) return map;
        try {
            return CoreDatabase.runLockedQuery(c -> {
                Map<Integer, TimeEntry> out = new LinkedHashMap<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT slot, item_data, time_seconds FROM time_items ORDER BY slot");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ItemStack item = deserialize(rs.getBytes("item_data"));
                        if (item == null) continue;
                        out.put(rs.getInt("slot"), new TimeEntry(rs.getInt("slot"), item, rs.getLong("time_seconds")));
                    }
                }
                return out;
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("[Sklep] loadTimeItems: " + e.getMessage());
            return map;
        }
    }

    public void setBuyItem(int slot, ItemStack item, double price) throws SQLException {
        CoreDatabase.runLocked(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR REPLACE INTO buy_items (slot, item_data, price) VALUES (?, ?, ?)")) {
                ps.setInt(1, slot);
                ps.setBytes(2, serialize(item));
                ps.setDouble(3, price);
                ps.executeUpdate();
            }
        });
    }

    public void removeBuyItem(int slot) throws SQLException {
        CoreDatabase.runLocked(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM buy_items WHERE slot = ?")) {
                ps.setInt(1, slot);
                ps.executeUpdate();
            }
        });
    }

    public void setTimeItem(int slot, ItemStack item, long seconds) throws SQLException {
        CoreDatabase.runLocked(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR REPLACE INTO time_items (slot, item_data, time_seconds) VALUES (?, ?, ?)")) {
                ps.setInt(1, slot);
                ps.setBytes(2, serialize(item));
                ps.setLong(3, seconds);
                ps.executeUpdate();
            }
        });
    }

    public void removeTimeItem(int slot) throws SQLException {
        CoreDatabase.runLocked(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM time_items WHERE slot = ?")) {
                ps.setInt(1, slot);
                ps.executeUpdate();
            }
        });
    }

    public long getPlaytimeSeconds(UUID uuid) {
        long delta = playtimeDelta.getOrDefault(uuid, 0L);
        if (!CoreDatabase.isOpen()) return delta;
        try {
            Long fromDb = CoreDatabase.runLockedQuery(c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT time_seconds FROM player_time WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getLong("time_seconds") : 0L;
                    }
                }
            });
            return fromDb + delta;
        } catch (SQLException e) {
            plugin.getLogger().warning("[Sklep] getPlaytime: " + e.getMessage());
            return delta;
        }
    }

    public void tickPlaytime(UUID uuid, String name, long delta) {
        if (delta <= 0) return;
        playtimeDelta.merge(uuid, delta, Long::sum);
    }

    public void flushPlaytime() {
        if (!CoreDatabase.isOpen() || playtimeDelta.isEmpty()) return;
        Map<UUID, Long> batch = new LinkedHashMap<>(playtimeDelta);
        try {
            CoreDatabase.runInTransaction(c -> flushPlaytimeBatch(c, batch));
            for (Map.Entry<UUID, Long> e : batch.entrySet()) {
                playtimeDelta.compute(e.getKey(), (k, pending) -> {
                    if (pending == null) return null;
                    long left = pending - e.getValue();
                    return left <= 0 ? null : left;
                });
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Sklep] flushPlaytime: " + e.getMessage());
        }
    }

    private void flushPlaytimeBatch(Connection c, Map<UUID, Long> batch) throws SQLException {
        for (Map.Entry<UUID, Long> e : batch.entrySet()) {
            if (e.getValue() <= 0) continue;
            var off = plugin.getServer().getOfflinePlayer(e.getKey());
            String name = off.getName() != null ? off.getName() : e.getKey().toString();
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_time (uuid, name, time_seconds) VALUES (?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name = excluded.name,
                        time_seconds = player_time.time_seconds + excluded.time_seconds
                    """)) {
                ps.setString(1, e.getKey().toString());
                ps.setString(2, name);
                ps.setLong(3, e.getValue());
                ps.executeUpdate();
            }
        }
    }

    public boolean takePlaytimeSeconds(UUID uuid, long amount) {
        if (amount <= 0) return false;
        flushPlaytime();
        long have = getPlaytimeSeconds(uuid);
        if (have < amount) return false;
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE player_time SET time_seconds = time_seconds - ? WHERE uuid = ?")) {
                    ps.setLong(1, amount);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
            });
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("[Sklep] takePlaytime: " + e.getMessage());
            return false;
        }
    }

    private static byte[] serialize(ItemStack item) throws SQLException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(out)) {
            oos.writeObject(item);
            return out.toByteArray();
        } catch (IOException e) {
            throw new SQLException(e);
        }
    }

    private static ItemStack deserialize(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(in)) {
            return (ItemStack) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }
}
