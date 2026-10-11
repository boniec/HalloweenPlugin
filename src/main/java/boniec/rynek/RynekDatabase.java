package boniec.rynek;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Warstwa dostepu do bazy danych SQLite dla Rynku (dawniej rynekdata.yml).
 * Trzyma: wystawione przedmioty ("items"), przedmioty czekajace na odbior przez
 * offline sprzedawcow po wygasnieciu/resecie oferty ("zwroty") oraz oczekujace
 * smocze odlamki ze sprzedazy, do odebrania przez /rynek odbierz ("odlamki").
 * <p>
 * Cienka warstwa JDBC - caly stan trzymany jest w pamieci przez klase Rynek,
 * ta klasa tylko go wczytuje/zapisuje. Zapis na dysk wykonywany jest przez
 * wywolujacego asynchronicznie (patrz Rynek#saveMarketItems), zeby nie blokowac
 * glownego watku serwera.
 */
public class RynekDatabase {

    private final JavaPlugin plugin;
    private Connection connection;

    public RynekDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(), "rynek.db");
            connection = boniec.code.core.CoreDatabase.connection();
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS items (
                        id TEXT PRIMARY KEY,
                        item BLOB NOT NULL,
                        price REAL NOT NULL,
                        seller TEXT NOT NULL,
                        seller_name TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        waluta TEXT NOT NULL DEFAULT 'pieniadze'
                    );
                """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS zwroty (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner TEXT NOT NULL,
                        item BLOB NOT NULL
                    );
                """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_zwroty_owner ON zwroty(owner);");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS odlamki (
                        owner TEXT PRIMARY KEY,
                        ilosc INTEGER NOT NULL DEFAULT 0
                    );
                """);
            }
            boniec.code.core.CoreDatabase.get().importLegacyDb("rynek.db", java.util.Map.of());
            migrujZYaml();
        } catch (SQLException e) {
            plugin.getLogger().severe("Nie udalo sie polaczyc z baza danych rynek.db: " + e.getMessage());
        }
    }

    public void disconnect() {
        connection = null;
    }

    /**
     * Jednorazowa migracja ze starego rynekdata.yml do SQLite - wykonywana tylko
     * gdy tabela "items" jest pusta ORAZ plik rynekdata.yml istnieje na dysku
     * (czyli instalacja aktualizowana ze starszej wersji pluginu). Po udanej
     * migracji plik zostaje przemianowany na rynekdata.yml.migrated, zeby
     * migracja nie powtarzala sie przy kazdym starcie.
     */
    private void migrujZYaml() {
        File stary = new File(plugin.getDataFolder(), "rynekdata.yml");
        if (!stary.exists()) return;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM items")) {
            if (rs.next() && rs.getInt(1) > 0) {
                // Baza juz ma dane - migracja juz sie kiedys odbyla, tylko sprzatamy plik.
                stary.renameTo(new File(plugin.getDataFolder(), "rynekdata.yml.migrated"));
                return;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy sprawdzaniu, czy migracja rynku jest potrzebna: " + e.getMessage());
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(stary);

        int zmigrowane = 0;
        org.bukkit.configuration.ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ItemStack item = itemsSection.getItemStack(key + ".item");
                    double price = itemsSection.getDouble(key + ".price");
                    UUID seller = UUID.fromString(itemsSection.getString(key + ".seller"));
                    String sellerName = itemsSection.getString(key + ".sellerName");
                    long timestamp = itemsSection.getLong(key + ".timestamp");
                    String waluta = itemsSection.getString(key + ".waluta", "pieniadze");
                    zapiszItem(id, item, price, seller, sellerName, timestamp, waluta);
                    zmigrowane++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Migracja rynku: nie udalo sie przeniesc oferty " + key + ": " + e.getMessage());
                }
            }
        }

        org.bukkit.configuration.ConfigurationSection zwrotySection = yaml.getConfigurationSection("zwroty");
        if (zwrotySection != null) {
            for (String uuidStr : zwrotySection.getKeys(false)) {
                try {
                    UUID owner = UUID.fromString(uuidStr);
                    List<?> raw = zwrotySection.getList(uuidStr);
                    if (raw == null) continue;
                    for (Object o : raw) {
                        if (o instanceof ItemStack) dodajZwrot(owner, (ItemStack) o);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Migracja rynku: nieprawidlowy UUID w zwrotach: " + uuidStr);
                }
            }
        }

        org.bukkit.configuration.ConfigurationSection odlamkiSection = yaml.getConfigurationSection("odlamki");
        if (odlamkiSection != null) {
            for (String uuidStr : odlamkiSection.getKeys(false)) {
                try {
                    UUID owner = UUID.fromString(uuidStr);
                    int ile = odlamkiSection.getInt(uuidStr, 0);
                    if (ile > 0) ustawOdlamki(owner, ile);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Migracja rynku: nieprawidlowy UUID w odlamkach: " + uuidStr);
                }
            }
        }

        stary.renameTo(new File(plugin.getDataFolder(), "rynekdata.yml.migrated"));
        plugin.getLogger().info("Migracja rynku do SQLite zakonczona: " + zmigrowane + " ofert przeniesionych. "
                + "Stary plik zapisano jako rynekdata.yml.migrated.");
    }

    // --- Serializacja ItemStack (Base64 przez BukkitObjectOutputStream, jak w BackupSerializer) ---

    private static byte[] serialize(ItemStack item) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(out)) {
            oos.writeObject(item);
            oos.flush();
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private static ItemStack deserialize(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(in)) {
            return (ItemStack) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // --- ITEMS (oferty na rynku) ---

    public synchronized void zapiszItem(UUID id, ItemStack item, double price, UUID seller,
                                         String sellerName, long timestamp, String waluta) {
        String sql = "INSERT INTO items (id, item, price, seller, seller_name, timestamp, waluta) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET item = excluded.item, price = excluded.price, " +
                "seller = excluded.seller, seller_name = excluded.seller_name, " +
                "timestamp = excluded.timestamp, waluta = excluded.waluta";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setBytes(2, serialize(item));
            ps.setDouble(3, price);
            ps.setString(4, seller.toString());
            ps.setString(5, sellerName);
            ps.setLong(6, timestamp);
            ps.setString(7, waluta);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy zapisywaniu oferty rynku " + id + ": " + e.getMessage());
        }
    }

    public synchronized void usunItem(UUID id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy usuwaniu oferty rynku " + id + ": " + e.getMessage());
        }
    }

    /** Nadpisuje CALA zawartosc tabeli "items" podana lista (uzywane przy zapisie zbiorczym z Rynek). */
    public synchronized void zapiszWszystkieItemy(List<Rynek.MarketItem> items) {
        try {
            boniec.code.core.CoreDatabase.runInTransaction(c -> {
                try (Statement del = c.createStatement()) {
                    del.executeUpdate("DELETE FROM items");
                }
                String sql = "INSERT INTO items (id, item, price, seller, seller_name, timestamp, waluta) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (Rynek.MarketItem mi : items) {
                        ps.setString(1, mi.getId().toString());
                        ps.setBytes(2, serialize(mi.getItem()));
                        ps.setDouble(3, mi.getPrice());
                        ps.setString(4, mi.getSeller().toString());
                        ps.setString(5, mi.getSellerName());
                        ps.setLong(6, mi.getTimestamp());
                        ps.setString(7, mi.getWaluta());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad przy zapisywaniu ofert rynku: " + e.getMessage());
        }
    }

    public synchronized List<Rynek.MarketItem> wczytajWszystkieItemy() {
        List<Rynek.MarketItem> wynik = new ArrayList<>();
        String sql = "SELECT id, item, price, seller, seller_name, timestamp, waluta FROM items";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID id = UUID.fromString(rs.getString("id"));
                    ItemStack item = deserialize(rs.getBytes("item"));
                    if (item == null) continue;
                    double price = rs.getDouble("price");
                    UUID seller = UUID.fromString(rs.getString("seller"));
                    String sellerName = rs.getString("seller_name");
                    long timestamp = rs.getLong("timestamp");
                    String waluta = rs.getString("waluta");
                    wynik.add(new Rynek.MarketItem(id, item, price, seller, sellerName, timestamp, waluta));
                } catch (Exception e) {
                    plugin.getLogger().warning("Blad przy wczytywaniu oferty rynku: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad przy wczytywaniu ofert rynku: " + e.getMessage());
        }
        return wynik;
    }

    // --- ZWROTY (przedmioty offline sprzedawcow czekajace na odbior) ---

    public synchronized void dodajZwrot(UUID owner, ItemStack item) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO zwroty (owner, item) VALUES (?, ?)")) {
            ps.setString(1, owner.toString());
            ps.setBytes(2, serialize(item));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy zapisywaniu zwrotu rynku dla " + owner + ": " + e.getMessage());
        }
    }

    /** Nadpisuje CALA tabele "zwroty" zawartoscia podanej mapy (uzywane przy zapisie zbiorczym). */
    public synchronized void zapiszWszystkieZwroty(Map<UUID, List<ItemStack>> zwroty) {
        try {
            boniec.code.core.CoreDatabase.runInTransaction(c -> {
                try (Statement del = c.createStatement()) {
                    del.executeUpdate("DELETE FROM zwroty");
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO zwroty (owner, item) VALUES (?, ?)")) {
                    for (Map.Entry<UUID, List<ItemStack>> e : zwroty.entrySet()) {
                        for (ItemStack item : e.getValue()) {
                            ps.setString(1, e.getKey().toString());
                            ps.setBytes(2, serialize(item));
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad przy zapisywaniu zwrotow rynku: " + e.getMessage());
        }
    }

    public synchronized Map<UUID, List<ItemStack>> wczytajWszystkieZwroty() {
        Map<UUID, List<ItemStack>> wynik = new HashMap<>();
        String sql = "SELECT owner, item FROM zwroty";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID owner = UUID.fromString(rs.getString("owner"));
                    ItemStack item = deserialize(rs.getBytes("item"));
                    if (item == null) continue;
                    wynik.computeIfAbsent(owner, k -> new ArrayList<>()).add(item);
                } catch (Exception e) {
                    plugin.getLogger().warning("Blad przy wczytywaniu zwrotu rynku: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad przy wczytywaniu zwrotow rynku: " + e.getMessage());
        }
        return wynik;
    }

    // --- ODLAMKI (oczekujace do odbioru przez /rynek odbierz) ---

    /** Nadpisuje CALA tabele "odlamki" zawartoscia podanej mapy (uzywane przy zapisie zbiorczym). */
    public synchronized void zapiszWszystkieOdlamki(Map<UUID, Integer> odlamki) {
        try {
            boniec.code.core.CoreDatabase.runInTransaction(c -> {
                try (Statement del = c.createStatement()) {
                    del.executeUpdate("DELETE FROM odlamki");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO odlamki (owner, ilosc) VALUES (?, ?)")) {
                    for (Map.Entry<UUID, Integer> e : odlamki.entrySet()) {
                        if (e.getValue() <= 0) continue;
                        ps.setString(1, e.getKey().toString());
                        ps.setInt(2, e.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad przy zapisywaniu oczekujacych odlamkow rynku: " + e.getMessage());
        }
    }

    private synchronized void ustawOdlamki(UUID owner, int ile) {
        String sql = "INSERT INTO odlamki (owner, ilosc) VALUES (?, ?) " +
                "ON CONFLICT(owner) DO UPDATE SET ilosc = excluded.ilosc";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, ile);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy zapisywaniu odlamkow dla " + owner + ": " + e.getMessage());
        }
    }

    public synchronized Map<UUID, Integer> wczytajWszystkieOdlamki() {
        Map<UUID, Integer> wynik = new HashMap<>();
        String sql = "SELECT owner, ilosc FROM odlamki";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID owner = UUID.fromString(rs.getString("owner"));
                    int ilosc = rs.getInt("ilosc");
                    if (ilosc > 0) wynik.put(owner, ilosc);
                } catch (Exception e) {
                    plugin.getLogger().warning("Blad przy wczytywaniu odlamkow rynku: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad przy wczytywaniu odlamkow rynku: " + e.getMessage());
        }
        return wynik;
    }
}
