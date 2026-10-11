package boniec.code.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Jedna baza SQLite na caly plugin: {@code data.db}.
 * Configi YAML (szaman.yml, poziomy.yml, ...) zostaja plikami.
 * Dane graczy / klanow / spawn / warpy / portfel itd. zyja tutaj.
 */
public final class CoreDatabase {

    private static CoreDatabase instance;

    private final JavaPlugin plugin;
    private final File plik;
    private Connection connection;
    /** Jedno polaczenie SQLite - wszystkie zapisy musza isc kolejno, inaczej setAutoCommit(false) rzuca nested transaction. */
    private final ReentrantLock lock = new ReentrantLock();

    private static final Set<String> TABELE_LICZBA = Set.of(
            "portfel_konta", "poziomy_gracze", "gracze", "szaman_perki", "szaman_drop",
            "ustawienia_graczy", "muty", "misje_postep", "scoreboard_wylaczeni"
    );

    @FunctionalInterface
    public interface SqlWork {
        void run(Connection c) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlQuery<T> {
        T query(Connection c) throws SQLException;
    }

    private CoreDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "data.db");
    }

    public static synchronized CoreDatabase open(JavaPlugin plugin) {
        if (instance != null) {
            try {
                if (instance.connection != null && !instance.connection.isClosed()) {
                    return instance;
                }
            } catch (SQLException ignored) {
            }
            instance.connect();
            return instance;
        }
        instance = new CoreDatabase(plugin);
        instance.connect();
        return instance;
    }

    public static CoreDatabase get() {
        if (instance == null || instance.connection == null) {
            throw new IllegalStateException("CoreDatabase nie jest otwarte.");
        }
        return instance;
    }

    public static boolean isOpen() {
        if (instance == null) return false;
        try {
            return instance.connection != null && !instance.connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public static Connection connection() {
        CoreDatabase db = get();
        db.lock.lock();
        try {
            db.ensureLiveConnection();
            return db.connection;
        } finally {
            db.lock.unlock();
        }
    }

    /**
     * Uruchamia zapis w jednej transakcji na wspolnym polaczeniu.
     * Watki czekaja na siebie; zagniezdzone wywolanie z tego samego watku dolacza do biezacej transakcji.
     */
    public static void runInTransaction(SqlWork work) throws SQLException {
        CoreDatabase db = get();
        db.lock.lock();
        try {
            db.ensureLiveConnection();
            Connection c = db.connection;
            boolean started = false;
            try {
                if (c.getAutoCommit()) {
                    c.setAutoCommit(false);
                    started = true;
                }
                work.run(c);
                if (started) {
                    c.commit();
                }
            } catch (SQLException | RuntimeException e) {
                if (started) {
                    try {
                        c.rollback();
                    } catch (SQLException ignored) {
                    }
                }
                throw e;
            } finally {
                if (started) {
                    try {
                        c.setAutoCommit(true);
                    } catch (SQLException ignored) {
                    }
                }
            }
        } finally {
            db.lock.unlock();
        }
    }

    /** Jedno polaczenie SQLite — kazdy odczyt/zapis poza transakcja tez musi trzymac te blokade. */
    public static void runLocked(SqlWork work) throws SQLException {
        CoreDatabase db = get();
        db.lock.lock();
        try {
            db.ensureLiveConnection();
            work.run(db.connection);
        } finally {
            db.lock.unlock();
        }
    }

    public static <T> T runLockedReturning(Function<Connection, T> work) throws SQLException {
        CoreDatabase db = get();
        db.lock.lock();
        try {
            db.ensureLiveConnection();
            return work.apply(db.connection);
        } finally {
            db.lock.unlock();
        }
    }

    public static <T> T runLockedQuery(SqlQuery<T> work) throws SQLException {
        CoreDatabase db = get();
        db.lock.lock();
        try {
            db.ensureLiveConnection();
            return work.query(db.connection);
        } finally {
            db.lock.unlock();
        }
    }

    /** Scala WAL do data.db (wylaczenie serwera / po duzym zapisie). */
    public static void checkpointWal() {
        if (!isOpen()) return;
        try {
            runLocked(c -> {
                try (Statement st = c.createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(FULL)");
                }
            });
        } catch (SQLException e) {
            if (instance != null) {
                instance.plugin.getLogger().warning("[SQLite] wal_checkpoint: " + e.getMessage());
            }
        }
    }

    public static File plik(JavaPlugin plugin) {
        return new File(plugin.getDataFolder(), "data.db");
    }

    private void connect() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            connection = SqliteConnector.connect(plik.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=8000");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS _migracje (
                        id TEXT PRIMARY KEY,
                        czas INTEGER NOT NULL
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS kv (
                        modul TEXT NOT NULL,
                        klucz TEXT NOT NULL,
                        wartosc TEXT,
                        PRIMARY KEY (modul, klucz)
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS poziomy_gracze (
                        uuid TEXT PRIMARY KEY,
                        nazwa TEXT,
                        poziom INTEGER NOT NULL DEFAULT 1,
                        exp INTEGER NOT NULL DEFAULT 0,
                        odebrane TEXT NOT NULL DEFAULT ''
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS poziomy_boost (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        mnoznik REAL NOT NULL DEFAULT 1,
                        pozostalo INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS szaman_perki (
                        uuid TEXT NOT NULL,
                        perk TEXT NOT NULL,
                        poziom INTEGER NOT NULL,
                        PRIMARY KEY (uuid, perk)
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS szaman_drop (
                        uuid TEXT PRIMARY KEY,
                        procent REAL NOT NULL DEFAULT 0,
                        losowania INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS portfel_konta (
                        uuid TEXT PRIMARY KEY,
                        name TEXT,
                        balance REAL NOT NULL DEFAULT 0,
                        total_spent REAL NOT NULL DEFAULT 0,
                        daily_last_claim INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS patron_gracze (
                        uuid TEXT PRIMARY KEY,
                        patron TEXT,
                        last_draw INTEGER NOT NULL DEFAULT 0,
                        until_ts INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS tiktok_konta (
                        uuid TEXT PRIMARY KEY,
                        minecraft TEXT,
                        tiktok TEXT,
                        opis TEXT
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS tiktok_filmy (
                        id TEXT PRIMARY KEY,
                        uuid TEXT NOT NULL,
                        url TEXT,
                        video_id TEXT,
                        status TEXT,
                        dodano INTEGER,
                        wyslano INTEGER,
                        admin TEXT,
                        tytul TEXT,
                        opis TEXT,
                        wyswietlenia INTEGER
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS scoreboard_wylaczeni (
                        uuid TEXT PRIMARY KEY
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS ustawienia_graczy (
                        uuid TEXT NOT NULL,
                        klucz TEXT NOT NULL,
                        wylaczone INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY (uuid, klucz)
                    )
                    """);
                st.execute("CREATE TABLE IF NOT EXISTS bans (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "uuid TEXT NOT NULL DEFAULT ''," +
                        "name TEXT NOT NULL DEFAULT ''," +
                        "ip TEXT NOT NULL DEFAULT ''," +
                        "ip_ban INTEGER NOT NULL DEFAULT 0," +
                        "reason TEXT," +
                        "admin TEXT," +
                        "admin_uuid TEXT," +
                        "created INTEGER NOT NULL," +
                        "expires INTEGER NOT NULL DEFAULT 0," +
                        "active INTEGER NOT NULL DEFAULT 1," +
                        "unbanned_by TEXT," +
                        "unbanned_at INTEGER DEFAULT 0," +
                        "unban_reason TEXT)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_bans_uuid ON bans(uuid)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_bans_ip ON bans(ip)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_bans_active ON bans(active)");
                st.execute("CREATE TABLE IF NOT EXISTS players (" +
                        "uuid TEXT NOT NULL," +
                        "name TEXT NOT NULL," +
                        "ip TEXT NOT NULL," +
                        "first_seen INTEGER NOT NULL," +
                        "last_seen INTEGER NOT NULL," +
                        "PRIMARY KEY (uuid, ip))");
                st.execute("CREATE INDEX IF NOT EXISTS idx_players_ip ON players(ip)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_players_name ON players(name COLLATE NOCASE)");
                st.execute("CREATE TABLE IF NOT EXISTS muty (" +
                        "uuid TEXT PRIMARY KEY," +
                        "nick TEXT," +
                        "powod TEXT," +
                        "admin TEXT," +
                        "utworzono INTEGER," +
                        "wygasa INTEGER)");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS warps (
                        nazwa TEXT PRIMARY KEY,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL NOT NULL,
                        pitch REAL NOT NULL
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS portale (
                        nazwa TEXT PRIMARY KEY,
                        world TEXT NOT NULL,
                        x1 INTEGER NOT NULL,
                        y1 INTEGER NOT NULL,
                        z1 INTEGER NOT NULL,
                        x2 INTEGER NOT NULL,
                        y2 INTEGER NOT NULL,
                        z2 INTEGER NOT NULL,
                        dest_world TEXT NOT NULL,
                        dest_x REAL NOT NULL,
                        dest_y REAL NOT NULL,
                        dest_z REAL NOT NULL,
                        dest_yaw REAL NOT NULL,
                        dest_pitch REAL NOT NULL
                    )
                    """);
                st.execute("CREATE TABLE IF NOT EXISTS kits (" +
                        "name VARCHAR(32) PRIMARY KEY," +
                        "cooldown INT," +
                        "permission VARCHAR(64)," +
                        "slot INT," +
                        "icon TEXT," +
                        "items TEXT," +
                        "enabled INTEGER NOT NULL DEFAULT 1)");
                st.execute("CREATE TABLE IF NOT EXISTS player_cooldowns (" +
                        "uuid VARCHAR(36)," +
                        "kit_name VARCHAR(32)," +
                        "last_use LONG," +
                        "PRIMARY KEY (uuid, kit_name))");
                st.execute("CREATE TABLE IF NOT EXISTS buy_items (slot INTEGER PRIMARY KEY, item_data BLOB NOT NULL, price REAL NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS sell_items (slot INTEGER PRIMARY KEY, item_data BLOB NOT NULL, price REAL NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS time_items (slot INTEGER PRIMARY KEY, item_data BLOB NOT NULL, time_seconds INTEGER NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS player_time (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, time_seconds INTEGER NOT NULL DEFAULT 0)");
                st.execute("CREATE TABLE IF NOT EXISTS polaczenia (" +
                        "discord_id INTEGER PRIMARY KEY," +
                        "mc_uuid TEXT NOT NULL UNIQUE," +
                        "mc_nick TEXT NOT NULL," +
                        "discord_nazwa TEXT," +
                        "polaczono INTEGER NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS ustawienia (klucz TEXT PRIMARY KEY, wartosc TEXT NOT NULL)");
            }
            plugin.getLogger().info("[SQLite] Baza danych: " + plik.getAbsolutePath()
                    + " (pliki data.db-wal / data.db-shm przy WAL sa normalne; po /stop trafiaja do data.db)");
            UstawieniaDatabase.importLegacyUstawieniaDbIfNeeded(plugin);
        } catch (SQLException e) {
            plugin.getLogger().severe("[SQLite] Nie mozna otworzyc data.db: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private void ensureLiveConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }
        } catch (SQLException ignored) {
        }
        connect();
    }

    public boolean juzZmigrowane(String id) {
        lock.lock();
        try {
            ensureLiveConnection();
            try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM _migracje WHERE id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SQLite] migracje: " + e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void oznaczMigracje(String id) {
        lock.lock();
        try {
            ensureLiveConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO _migracje (id, czas) VALUES (?, ?)")) {
                ps.setString(1, id);
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SQLite] oznacz migracje: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Kopiuje tabele ze starego pliku .db do data.db (raz).
     * {@code rename} mapuje stara nazwe tabeli -> nowa (np. ustawienia -> aura_ustawienia).
     */
    public void importLegacyDb(String plikDb, Map<String, String> rename) {
        lock.lock();
        try {
            if (juzZmigrowane("db:" + plikDb)) return;
            File stary = new File(plugin.getDataFolder(), plikDb);
            if (!stary.exists() || stary.length() < 16) {
                oznaczMigracje("db:" + plikDb);
                return;
            }
            String alias = "legacy_" + plikDb.replace('.', '_').replace('-', '_').replace('/', '_');
            String path = stary.getAbsolutePath().replace("'", "''");
            try (Statement st = connection.createStatement()) {
                st.execute("ATTACH DATABASE '" + path + "' AS " + alias);
                Map<String, String> map = rename != null ? rename : Map.of();
                try (ResultSet rs = st.executeQuery("SELECT name FROM " + alias + ".sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                    while (rs.next()) {
                        String oldName = rs.getString(1);
                        String newName = map.getOrDefault(oldName, oldName);
                        try {
                            st.execute("INSERT OR IGNORE INTO " + newName + " SELECT * FROM " + alias + "." + oldName);
                        } catch (SQLException e) {
                            plugin.getLogger().warning("[SQLite] Import " + plikDb + "." + oldName + " -> " + newName + ": " + e.getMessage());
                        }
                    }
                }
                st.execute("DETACH DATABASE " + alias);
                File migrated = new File(plugin.getDataFolder(), plikDb + ".migrated");
                if (!stary.renameTo(migrated)) {
                    plugin.getLogger().warning("[SQLite] Nie zmieniono nazwy " + plikDb + " po imporcie.");
                }
                oznaczMigracje("db:" + plikDb);
                plugin.getLogger().info("[SQLite] Zmigrowano " + plikDb + " do data.db");
            } catch (SQLException e) {
                plugin.getLogger().warning("[SQLite] Import " + plikDb + " nie powiodl sie: " + e.getMessage());
                try (Statement st = connection.createStatement()) {
                    st.execute("DETACH DATABASE " + alias);
                } catch (SQLException ignored) {
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void setKv(String modul, String klucz, String wartosc) {
        lock.lock();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO kv (modul, klucz, wartosc) VALUES (?, ?, ?) ON CONFLICT(modul, klucz) DO UPDATE SET wartosc = excluded.wartosc")) {
            ps.setString(1, modul);
            ps.setString(2, klucz);
            ps.setString(3, wartosc);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[SQLite] kv set: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public String getKv(String modul, String klucz, String domyslna) {
        lock.lock();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT wartosc FROM kv WHERE modul = ? AND klucz = ?")) {
            ps.setString(1, modul);
            ps.setString(2, klucz);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : domyslna;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SQLite] kv get: " + e.getMessage());
            return domyslna;
        } finally {
            lock.unlock();
        }
    }

    public static String itemToYaml(ItemStack item) {
        if (item == null) return null;
        YamlConfiguration y = new YamlConfiguration();
        y.set("i", item);
        return y.saveToString();
    }

    public static ItemStack yamlToItem(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(raw);
            return y.getItemStack("i");
        } catch (Exception e) {
            return null;
        }
    }

    /** Liczba wierszy w tabeli (chroni przed DELETE+cache pusty przy reload pluginu). */
    public int liczbaWierszy(String tabela) {
        if (!TABELE_LICZBA.contains(tabela)) {
            throw new IllegalArgumentException("Niedozwolona tabela: " + tabela);
        }
        lock.lock();
        try {
            ensureLiveConnection();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SQLite] COUNT " + tabela + ": " + e.getMessage());
            return 0;
        } finally {
            lock.unlock();
        }
    }

    public static Map<String, String> mapa(String... pary) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pary.length; i += 2) {
            m.put(pary[i], pary[i + 1]);
        }
        return m;
    }

    public static synchronized void close() {
        if (instance == null) return;
        checkpointWal();
        instance.lock.lock();
        try {
            if (instance.connection != null && !instance.connection.isClosed()) {
                try (Statement st = instance.connection.createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                } catch (SQLException ignored) {
                }
                instance.connection.close();
            }
        } catch (SQLException ignored) {
        } finally {
            instance.lock.unlock();
            instance.connection = null;
            instance = null;
        }
    }
}
