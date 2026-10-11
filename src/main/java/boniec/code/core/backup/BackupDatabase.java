package boniec.code.core.backup;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class BackupDatabase {

    private final JavaPlugin plugin;
    private Connection connection;

    public BackupDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "backups.db");
            if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();

            this.connection = boniec.code.core.CoreDatabase.connection();

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA cache_size=-2000;");
                stmt.execute("PRAGMA temp_store=MEMORY;");

                stmt.execute("CREATE TABLE IF NOT EXISTS deaths (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "uuid VARCHAR(36) NOT NULL," +
                        "player_name VARCHAR(16) NOT NULL," +
                        "death_time INTEGER," +
                        "items_json TEXT," +
                        "armor_json TEXT," +
                        "exp_level INTEGER DEFAULT 0)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_deaths_uuid ON deaths(uuid);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_deaths_name ON deaths(player_name);");

                // status: PENDING -> APPROVED -> GIVEN  (oraz REJECTED / REVOKED)
                stmt.execute("CREATE TABLE IF NOT EXISTS backups (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "death_id INTEGER," +
                        "target_uuid VARCHAR(36) NOT NULL," +
                        "target_name VARCHAR(16) NOT NULL," +
                        "status VARCHAR(20) DEFAULT 'PENDING'," +
                        "requested_by_uuid VARCHAR(36)," +
                        "requested_by_name VARCHAR(16)," +
                        "verified_by_uuid VARCHAR(36)," +
                        "verified_by_name VARCHAR(16)," +
                        "items_json TEXT," +
                        "armor_json TEXT," +
                        "delivered INTEGER DEFAULT 0," +
                        "needs_strip INTEGER DEFAULT 0," +
                        "created_at INTEGER," +
                        "updated_at INTEGER)");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_backups_target ON backups(target_uuid);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_backups_target_name ON backups(target_name);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_backups_status ON backups(status);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_backups_death ON backups(death_id);");

                // Migracje starszych baz
                try { stmt.execute("ALTER TABLE backups ADD COLUMN armor_json TEXT;"); } catch (Exception ignored) {}
                try { stmt.execute("ALTER TABLE backups ADD COLUMN delivered INTEGER DEFAULT 0;"); } catch (Exception ignored) {}
                try { stmt.execute("ALTER TABLE backups ADD COLUMN needs_strip INTEGER DEFAULT 0;"); } catch (Exception ignored) {}
                try { stmt.execute("ALTER TABLE deaths ADD COLUMN killer_name VARCHAR(16);"); } catch (Exception ignored) {}
                try { stmt.execute("ALTER TABLE deaths ADD COLUMN ranking INTEGER DEFAULT 1000;"); } catch (Exception ignored) {}
                try { stmt.execute("ALTER TABLE backups ADD COLUMN ranking INTEGER DEFAULT -1;"); } catch (Exception ignored) {}

                // Stare "GIVEN, ale niewydane" -> teraz czekają na /odbierzbackup
                try {
                    stmt.execute("UPDATE backups SET status='APPROVED' " +
                            "WHERE status='GIVEN' AND (delivered IS NULL OR delivered = 0);");
                } catch (Exception ignored) {}
            }
            boniec.code.core.CoreDatabase.get().importLegacyDb("backups.db", java.util.Map.of());
        } catch (Exception e) {
            plugin.getLogger().severe("Blad inicjalizacji bazy danych backupow: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** UWAGA: połączenie jest współdzielone - NIE zamykaj go w try-with-resources! */
    public synchronized Connection getConnection() throws SQLException {
        this.connection = boniec.code.core.CoreDatabase.connection();
        return connection;
    }

    public synchronized void close() {
        connection = null;
    }
}