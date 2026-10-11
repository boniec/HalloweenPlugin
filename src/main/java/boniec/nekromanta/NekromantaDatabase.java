package boniec.nekromanta;

import boniec.code.core.SqliteConnector;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite {@code nekromanta.db}: salda/poziomy graczy, pule nagrod i szablon gluta.
 * {@code nekromanta.yml} zostaje plikiem konfiguracji (teksty, tiery, dzwieki).
 */
final class NekromantaDatabase {

    static final class WpisDanych {
        final ItemStack item;
        final double szansa;

        WpisDanych(ItemStack item, double szansa) {
            this.item = item;
            this.szansa = szansa;
        }
    }

    static final class DaneGracza {
        long saldo;
        int poziom;
    }

    private final JavaPlugin plugin;
    private final File plik;
    private final ReentrantLock lock = new ReentrantLock();
    private Connection connection;

    NekromantaDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "nekromanta.db");
    }

    void open() {
        lock.lock();
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            connection = SqliteConnector.connect(plik.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=8000");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS gracze (
                        uuid TEXT PRIMARY KEY,
                        saldo INTEGER NOT NULL DEFAULT 0,
                        poziom INTEGER NOT NULL DEFAULT 0
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS dropy (
                        tier_id INTEGER NOT NULL,
                        slot INTEGER NOT NULL,
                        item BLOB NOT NULL,
                        szansa REAL NOT NULL,
                        PRIMARY KEY (tier_id, slot)
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS glut (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        item BLOB NOT NULL
                    )
                    """);
            }
            plugin.getLogger().info("[Nekromanta] Baza: " + plik.getAbsolutePath());
            zmigrujYamlJesliTrzeba();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Nekromanta] Nie mozna otworzyc nekromanta.db: " + e.getMessage());
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

    Map<UUID, DaneGracza> wczytajGraczy() {
        Map<UUID, DaneGracza> wynik = new HashMap<>();
        lock.lock();
        try {
            ensureOpen();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT uuid, saldo, poziom FROM gracze")) {
                while (rs.next()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(rs.getString("uuid"));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    DaneGracza d = new DaneGracza();
                    d.saldo = Math.max(0L, rs.getLong("saldo"));
                    d.poziom = Math.max(0, rs.getInt("poziom"));
                    if (d.saldo > 0 || d.poziom > 0) {
                        wynik.put(uuid, d);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Odczyt graczy: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return wynik;
    }

    void zapiszGracza(UUID uuid, long saldo, int poziom) {
        lock.lock();
        try {
            ensureOpen();
            if (saldo <= 0 && poziom <= 0) {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM gracze WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO gracze (uuid, saldo, poziom) VALUES (?, ?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET saldo = excluded.saldo, poziom = excluded.poziom")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, Math.max(0L, saldo));
                ps.setInt(3, Math.max(0, poziom));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Zapis gracza: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    void zapiszGraczy(Map<UUID, DaneGracza> wszyscy) {
        lock.lock();
        try {
            ensureOpen();
            boolean autocommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (Map.Entry<UUID, DaneGracza> e : wszyscy.entrySet()) {
                    DaneGracza d = e.getValue();
                    zapiszGraczaBezLocka(e.getKey(), d.saldo, d.poziom);
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                connection.setAutoCommit(autocommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Zapis graczy: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    Map<Integer, List<WpisDanych>> wczytajDropy() {
        Map<Integer, List<WpisDanych>> wynik = new HashMap<>();
        lock.lock();
        try {
            ensureOpen();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT tier_id, slot, item, szansa FROM dropy ORDER BY tier_id, slot")) {
                while (rs.next()) {
                    ItemStack item = deserialize(rs.getBytes("item"));
                    if (item == null || item.getType().isAir()) continue;
                    int tier = rs.getInt("tier_id");
                    wynik.computeIfAbsent(tier, k -> new ArrayList<>())
                            .add(new WpisDanych(item, rs.getDouble("szansa")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Odczyt dropow: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return wynik;
    }

    void zapiszDropy(Map<Integer, List<NekromantaManager.Wpis>> dropy) {
        lock.lock();
        try {
            ensureOpen();
            boolean autocommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM dropy");
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO dropy (tier_id, slot, item, szansa) VALUES (?, ?, ?, ?)")) {
                    for (Map.Entry<Integer, List<NekromantaManager.Wpis>> e : dropy.entrySet()) {
                        List<NekromantaManager.Wpis> lista = e.getValue();
                        for (int i = 0; i < lista.size(); i++) {
                            NekromantaManager.Wpis w = lista.get(i);
                            byte[] blob = serialize(w.getItem());
                            if (blob == null) continue;
                            ps.setInt(1, e.getKey());
                            ps.setInt(2, i);
                            ps.setBytes(3, blob);
                            ps.setDouble(4, w.getSzansa());
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                connection.setAutoCommit(autocommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Zapis dropow: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    ItemStack wczytajGlut() {
        lock.lock();
        try {
            ensureOpen();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT item FROM glut WHERE id = 1")) {
                if (rs.next()) {
                    return deserialize(rs.getBytes("item"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Odczyt gluta: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return null;
    }

    void zapiszGlut(ItemStack item) {
        lock.lock();
        try {
            ensureOpen();
            byte[] blob = serialize(item);
            if (blob == null) return;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO glut (id, item) VALUES (1, ?) ON CONFLICT(id) DO UPDATE SET item = excluded.item")) {
                ps.setBytes(1, blob);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Zapis gluta: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    void usunGlut() {
        lock.lock();
        try {
            ensureOpen();
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM glut");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Usuniecie gluta: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void zapiszGraczaBezLocka(UUID uuid, long saldo, int poziom) throws SQLException {
        if (saldo <= 0 && poziom <= 0) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM gracze WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO gracze (uuid, saldo, poziom) VALUES (?, ?, ?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET saldo = excluded.saldo, poziom = excluded.poziom")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, Math.max(0L, saldo));
            ps.setInt(3, Math.max(0, poziom));
            ps.executeUpdate();
        }
    }

    private void ensureOpen() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = SqliteConnector.connect(plik.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=8000");
            }
        }
    }

    private boolean tabelaPusta(String tabela) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            return !rs.next() || rs.getInt(1) == 0;
        }
    }

    private void zmigrujYamlJesliTrzeba() {
        File graczeYml = new File(plugin.getDataFolder(), "nekromantagracze.yml");
        File dropYml = new File(plugin.getDataFolder(), "nekromantadrop.yml");
        File glutYml = new File(plugin.getDataFolder(), "nekromantaglut.yml");
        try {
            if (tabelaPusta("gracze") && graczeYml.exists()) {
                int n = importGraczyYaml(graczeYml);
                oznaczZmigrowany(graczeYml);
                plugin.getLogger().info("[Nekromanta] Zmigrowano " + n + " graczy z nekromantagracze.yml do nekromanta.db");
            }
            if (tabelaPusta("dropy") && dropYml.exists()) {
                int n = importDropowYaml(dropYml);
                oznaczZmigrowany(dropYml);
                plugin.getLogger().info("[Nekromanta] Zmigrowano " + n + " nagrod z nekromantadrop.yml do nekromanta.db");
            }
            if (tabelaPusta("glut") && glutYml.exists()) {
                if (importGlutYaml(glutYml)) {
                    oznaczZmigrowany(glutYml);
                    plugin.getLogger().info("[Nekromanta] Zmigrowano gluta z nekromantaglut.yml do nekromanta.db");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Nekromanta] Migracja YAML: " + e.getMessage());
        }
    }

    private int importGraczyYaml(File plikYml) throws SQLException {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(plikYml);
        ConfigurationSection root = data.getConfigurationSection("gracze");
        if (root == null) return 0;
        int n = 0;
        for (String klucz : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(klucz);
            } catch (IllegalArgumentException e) {
                continue;
            }
            long saldo = Math.max(0L, root.getLong(klucz + ".saldo", 0L));
            int poziom = Math.max(0, root.getInt(klucz + ".poziom", 0));
            if (saldo <= 0 && poziom <= 0) continue;
            zapiszGraczaBezLocka(uuid, saldo, poziom);
            n++;
        }
        return n;
    }

    private int importDropowYaml(File plikYml) throws SQLException {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(plikYml);
        ConfigurationSection root = data.getConfigurationSection("drop");
        if (root == null) return 0;
        int n = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO dropy (tier_id, slot, item, szansa) VALUES (?, ?, ?, ?)")) {
            for (String tierKlucz : root.getKeys(false)) {
                int tierId;
                try {
                    tierId = Integer.parseInt(tierKlucz);
                } catch (NumberFormatException e) {
                    continue;
                }
                ConfigurationSection sekcja = root.getConfigurationSection(tierKlucz);
                if (sekcja == null) continue;
                int slot = 0;
                for (String klucz : sekcja.getKeys(false)) {
                    ConfigurationSection wpis = sekcja.getConfigurationSection(klucz);
                    if (wpis == null) continue;
                    ItemStack item = wpis.getItemStack("item");
                    if (item == null || item.getType().isAir()) continue;
                    byte[] blob = serialize(item);
                    if (blob == null) continue;
                    ps.setInt(1, tierId);
                    ps.setInt(2, slot++);
                    ps.setBytes(3, blob);
                    ps.setDouble(4, wpis.getDouble("szansa", 10.0));
                    ps.executeUpdate();
                    n++;
                }
            }
        }
        return n;
    }

    private boolean importGlutYaml(File plikYml) throws SQLException {
        ItemStack it = YamlConfiguration.loadConfiguration(plikYml).getItemStack("item");
        if (it == null || it.getType().isAir()) return false;
        byte[] blob = serialize(it);
        if (blob == null) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO glut (id, item) VALUES (1, ?)")) {
            ps.setBytes(1, blob);
            ps.executeUpdate();
        }
        return true;
    }

    private void oznaczZmigrowany(File stary) {
        File nowy = new File(stary.getParentFile(), stary.getName() + ".migrated");
        if (!stary.renameTo(nowy)) {
            plugin.getLogger().warning("[Nekromanta] Nie zmieniono nazwy " + stary.getName() + " po migracji.");
        }
    }

    private static byte[] serialize(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(out)) {
            oos.writeObject(item);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
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
