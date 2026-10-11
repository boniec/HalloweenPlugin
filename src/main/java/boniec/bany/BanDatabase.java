package boniec.bany;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * IP graczy do /alts w {@code data.db} (tabela players).
 * Metody sa blokujace - wolaj je z watku async.
 */
public final class BanDatabase {

    public static final class KnownPlayer {
        public final String uuid;
        public final String name;
        public final String lastIp;

        KnownPlayer(String uuid, String name, String lastIp) {
            this.uuid = uuid;
            this.name = name;
            this.lastIp = lastIp;
        }
    }

    private final JavaPlugin plugin;

    public BanDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            boniec.code.core.CoreDatabase.get().importLegacyDb("bany.db", java.util.Map.of());
        } catch (Exception e) {
            plugin.getLogger().severe("[Alts] Blad inicjalizacji bazy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void write(boniec.code.core.CoreDatabase.SqlWork work, String label) {
        if (!boniec.code.core.CoreDatabase.isOpen()) return;
        try {
            boniec.code.core.CoreDatabase.runInTransaction(work);
        } catch (SQLException e) {
            err(label, e);
        }
    }

    @FunctionalInterface
    private interface SqlRead<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> T read(SqlRead<T> work, T fallback, String label) {
        if (!boniec.code.core.CoreDatabase.isOpen()) return fallback;
        try {
            return boniec.code.core.CoreDatabase.runLockedQuery(work::run);
        } catch (SQLException e) {
            err(label, e);
            return fallback;
        }
    }

    public synchronized void close() {
    }

    private void err(String what, Exception e) {
        if (!boniec.code.core.CoreDatabase.isOpen()) return;
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("closed")) return;
        plugin.getLogger().severe("[Alts] Blad bazy (" + what + "): " + msg);
    }

    public synchronized void upsertPlayer(String uuid, String name, String ip) {
        long now = System.currentTimeMillis();
        write(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO players (uuid,name,ip,first_seen,last_seen) VALUES (?,?,?,?,?) " +
                            "ON CONFLICT(uuid,ip) DO UPDATE SET name=excluded.name, last_seen=excluded.last_seen")) {
                ps.setString(1, uuid);
                ps.setString(2, name);
                ps.setString(3, ip);
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE players SET name=? WHERE uuid=?")) {
                ps.setString(1, name);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
        }, "upsertPlayer");
    }

    public synchronized void touchPlayer(String uuid, String ip) {
        write(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE players SET last_seen=? WHERE uuid=? AND ip=?")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, uuid);
                ps.setString(3, ip);
                ps.executeUpdate();
            }
        }, "touchPlayer");
    }

    public synchronized String lastIp(String uuid) {
        return read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ip FROM players WHERE uuid=? ORDER BY last_seen DESC LIMIT 1")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString(1);
                }
            }
            return null;
        }, null, "lastIp");
    }

    public synchronized KnownPlayer findPlayerByName(String name) {
        return read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT uuid, name, ip FROM players WHERE name = ? COLLATE NOCASE ORDER BY last_seen DESC LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new KnownPlayer(rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }
            return null;
        }, null, "findPlayerByName");
    }

    /** Konta wspoldzielace przynajmniej jedno IP z podanym kontem (bez niego samego). */
    public synchronized List<BanManager.AltEntry> sharedAccounts(String uuid) {
        return read(c -> {
            List<BanManager.AltEntry> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT p2.uuid AS uuid, MAX(p2.name) AS name, MIN(p2.first_seen) AS fs, MAX(p2.last_seen) AS ls, " +
                            "COUNT(DISTINCT p2.ip) AS c, GROUP_CONCAT(DISTINCT p2.ip) AS ips " +
                            "FROM players p1 JOIN players p2 ON p1.ip = p2.ip " +
                            "WHERE p1.uuid = ? AND p2.uuid <> p1.uuid " +
                            "GROUP BY p2.uuid ORDER BY ls DESC")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new BanManager.AltEntry(rs.getString("uuid"), rs.getString("name"),
                                rs.getLong("fs"), rs.getLong("ls"), rs.getInt("c"), rs.getString("ips")));
                    }
                }
            }
            return list;
        }, new ArrayList<>(), "sharedAccounts");
    }
}
