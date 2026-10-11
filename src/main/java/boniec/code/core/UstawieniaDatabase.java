package boniec.code.core;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Wszystko w {@code data.db} — ten typ to tylko kompatybilnosc API (kity, sklep, spawn, warpy).
 * Osobny plik ustawienia.db nie jest juz uzywany; jednorazowo importuje stary plik jesli istnieje.
 */
public final class UstawieniaDatabase {

    /** @deprecated uzywaj {@link CoreDatabase#plik}; zostawione dla starych logow. */
    public static final String PLIK = "data.db";

    private static volatile boolean legacyImportAttempted;

    private UstawieniaDatabase() {}

    public static synchronized Connection open(JavaPlugin plugin) throws SQLException {
        CoreDatabase.open(plugin);
        return CoreDatabase.connection();
    }

    public static synchronized Connection connection() {
        return CoreDatabase.connection();
    }

    public static synchronized void setKv(String modul, String klucz, String wartosc) {
        CoreDatabase.get().setKv(modul, klucz, wartosc);
    }

    public static synchronized String getKv(String modul, String klucz, String domyslna) {
        return CoreDatabase.get().getKv(modul, klucz, domyslna);
    }

    /** Nic nie zamyka — zamyka {@link CoreDatabase#close()}. */
    public static synchronized void close() {
    }

    /** Wywolywane raz z {@link CoreDatabase} po utworzeniu schematu. */
    static void importLegacyUstawieniaDbIfNeeded(JavaPlugin plugin) {
        if (legacyImportAttempted) return;
        legacyImportAttempted = true;
        importLegacyUstawieniaDb(plugin);
    }

    private static void importLegacyUstawieniaDb(JavaPlugin plugin) {
        if (CoreDatabase.get().juzZmigrowane("file:ustawienia.db")) return;
        File stary = new File(plugin.getDataFolder(), "ustawienia.db");
        File kitySklep = new File(plugin.getDataFolder(), "kity_sklep.db");
        if (!stary.exists() && kitySklep.exists()) {
            kitySklep.renameTo(stary);
        }
        if (!stary.exists() || stary.length() < 16) {
            CoreDatabase.get().oznaczMigracje("file:ustawienia.db");
            return;
        }
        String attachUri = sqliteReadOnlyUri(stary);
        String[][] tabele = {
                {"kits", "name, cooldown, permission, slot, icon, items, enabled"},
                {"player_cooldowns", "uuid, kit_name, last_use"},
                {"buy_items", "slot, item_data, price"},
                {"sell_items", "slot, item_data, price"},
                {"time_items", "slot, item_data, time_seconds"},
                {"player_time", "uuid, name, time_seconds"},
                {"warps", "nazwa, world, x, y, z, yaw, pitch"},
                {"portale", "nazwa, world, x1, y1, z1, x2, y2, z2, dest_world, dest_x, dest_y, dest_z, dest_yaw, dest_pitch"},
                {"polaczenia", "discord_id, mc_uuid, mc_nick, discord_nazwa, polaczono"},
                {"ustawienia", "klucz, wartosc"},
        };
        try {
            CoreDatabase.runInTransaction(c -> {
                try (Statement st = c.createStatement()) {
                    detachQuiet(st, "legacy_ust");
                    st.execute("ATTACH DATABASE '" + attachUri + "' AS legacy_ust");
                    try {
                        for (String[] t : tabele) {
                            try {
                                st.executeUpdate("INSERT OR IGNORE INTO " + t[0] + " (" + t[1] + ") SELECT "
                                        + t[1] + " FROM legacy_ust." + t[0]);
                            } catch (SQLException e) {
                                if ("kits".equals(t[0])) {
                                    String bez = t[1].replace(", enabled", "");
                                    st.executeUpdate("INSERT OR IGNORE INTO kits (" + bez + ") SELECT "
                                            + bez + " FROM legacy_ust.kits");
                                } else {
                                    plugin.getLogger().warning("[SQLite] Import ustawienia.db." + t[0] + ": " + e.getMessage());
                                }
                            }
                        }
                        try {
                            st.executeUpdate("INSERT OR IGNORE INTO kv (modul, klucz, wartosc) "
                                    + "SELECT modul, klucz, wartosc FROM legacy_ust.kv");
                        } catch (SQLException ignored) {
                        }
                        File migrated = new File(plugin.getDataFolder(), "ustawienia.db.migrated");
                        if (!stary.renameTo(migrated)) {
                            plugin.getLogger().warning("[SQLite] Nie zmieniono nazwy ustawienia.db po imporcie do data.db.");
                        }
                        CoreDatabase.get().oznaczMigracje("file:ustawienia.db");
                        plugin.getLogger().info("[SQLite] Zmigrowano ustawienia.db -> data.db");
                    } finally {
                        detachQuiet(st, "legacy_ust");
                    }
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("[SQLite] Import ustawienia.db nie powiodl sie: " + e.getMessage()
                    + " (zamknij DB Browser / drugi serwer trzymajacy ustawienia.db i zrestartuj)");
        }
    }

    private static void detachQuiet(Statement st, String alias) {
        try {
            st.execute("DETACH DATABASE " + alias);
        } catch (SQLException ignored) {
        }
    }

    /** URI tylko do odczytu — mniej konfliktow z blokada pliku przy ATTACH. */
    private static String sqliteReadOnlyUri(File file) {
        String path = file.getAbsolutePath().replace('\\', '/').replace("'", "''");
        return "file:" + path + "?mode=ro";
    }
}
