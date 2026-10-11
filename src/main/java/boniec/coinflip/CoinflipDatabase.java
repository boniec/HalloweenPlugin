package boniec.coinflip;

import boniec.code.core.SqliteConnector;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** SQLite coinflip.db — odlamki do odbioru po wygranej / restarcie. */
final class CoinflipDatabase {

    private final JavaPlugin plugin;
    private final File plik;
    private final ReentrantLock lock = new ReentrantLock();
    private Connection connection;

    CoinflipDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "coinflip.db");
    }

    void open() {
        lock.lock();
        try {
            plugin.getDataFolder().mkdirs();
            connection = SqliteConnector.connect(plik.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=8000");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS oczekujace (
                        uuid TEXT PRIMARY KEY,
                        ilosc INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Coinflip] Nie otwarto coinflip.db: " + e.getMessage());
            throw new IllegalStateException(e);
        } finally {
            lock.unlock();
        }
    }

    void close() {
        lock.lock();
        try {
            if (connection == null) return;
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException ignored) {
            }
            try {
                if (!connection.isClosed()) connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        } finally {
            lock.unlock();
        }
    }

    Map<UUID, Integer> wczytajOczekujace() {
        Map<UUID, Integer> out = new HashMap<>();
        lock.lock();
        try {
            ensureOpen();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT uuid, ilosc FROM oczekujace")) {
                while (rs.next()) {
                    try {
                        int n = rs.getInt("ilosc");
                        if (n > 0) out.put(UUID.fromString(rs.getString("uuid")), n);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Coinflip] Odczyt oczekujacych: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return out;
    }

    void zapiszOczekujace(UUID uuid, int ilosc) {
        lock.lock();
        try {
            ensureOpen();
            if (ilosc <= 0) {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM oczekujace WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO oczekujace (uuid, ilosc) VALUES (?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET ilosc = excluded.ilosc")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, ilosc);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Coinflip] Zapis oczekujacych: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void ensureOpen() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = SqliteConnector.connect(plik.getAbsolutePath());
        }
    }
}
