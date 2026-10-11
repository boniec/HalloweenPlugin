package boniec.klany.db;

import boniec.code.core.CoreDatabase;
import boniec.klany.core.Klan;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Warstwa dostepu do bazy danych SQLite - trzyma klany oraz przynaleznosc graczy.
 * Wszystkie operacje ida przez wspolne {@link CoreDatabase} (data.db), bez wlasnego Connection.
 */
public class KlanDatabase {

    private final JavaPlugin plugin;

    public KlanDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            CoreDatabase.runLocked(c -> {
                try (Statement st = c.createStatement()) {
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS klany (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            nazwa TEXT NOT NULL UNIQUE,
                            tag TEXT NOT NULL UNIQUE,
                            lider TEXT NOT NULL,
                            pvp INTEGER NOT NULL DEFAULT 0,
                            skarbiec INTEGER NOT NULL DEFAULT 0,
                            data_zalozenia INTEGER NOT NULL,
                            ranking_klanu INTEGER NOT NULL DEFAULT 0
                        );
                    """);
                    dodajKolumneJesliBrak(c, st, "klany", "ranking_klanu", "INTEGER NOT NULL DEFAULT 0");
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS czlonkowie (
                            uuid TEXT PRIMARY KEY,
                            klan_id INTEGER NOT NULL,
                            ranga TEXT NOT NULL,
                            zabojstwa INTEGER NOT NULL DEFAULT 0,
                            smierci INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY (klan_id) REFERENCES klany(id)
                        );
                    """);
                    dodajKolumneJesliBrak(c, st, "czlonkowie", "zabojstwa", "INTEGER NOT NULL DEFAULT 0");
                    dodajKolumneJesliBrak(c, st, "czlonkowie", "smierci", "INTEGER NOT NULL DEFAULT 0");
                    dodajKolumneJesliBrak(c, st, "czlonkowie", "ranking", "INTEGER NOT NULL DEFAULT 1000");

                    st.execute("""
                        CREATE TABLE IF NOT EXISTS gracze (
                            uuid TEXT PRIMARY KEY,
                            zabojstwa INTEGER NOT NULL DEFAULT 0,
                            smierci INTEGER NOT NULL DEFAULT 0,
                            ranking INTEGER NOT NULL DEFAULT 1000
                        );
                    """);
                    dodajKolumneJesliBrak(c, st, "gracze", "tier", "INTEGER NOT NULL DEFAULT 0");
                    st.execute("""
                        INSERT OR IGNORE INTO gracze (uuid, zabojstwa, smierci, ranking)
                        SELECT uuid, zabojstwa, smierci, ranking FROM czlonkowie;
                    """);
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS logi (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            klan_id INTEGER NOT NULL,
                            czas INTEGER NOT NULL,
                            tresc TEXT NOT NULL
                        );
                    """);
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS perki_klanu (
                            klan_id INTEGER NOT NULL,
                            perk_id TEXT NOT NULL,
                            wartosc REAL NOT NULL DEFAULT 0,
                            PRIMARY KEY (klan_id, perk_id),
                            FOREIGN KEY (klan_id) REFERENCES klany(id)
                        );
                    """);
                }
            });
            CoreDatabase.get().importLegacyDb("klany.db", java.util.Map.of());
        } catch (SQLException e) {
            plugin.getLogger().severe("Nie udalo sie polaczyc z baza danych klany.db: " + e.getMessage());
        }
    }

    private void dodajKolumneJesliBrak(Connection c, Statement st, String tabela, String kolumna, String definicja) {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, tabela, kolumna)) {
            if (rs.next()) return;
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy sprawdzaniu kolumny " + kolumna + ": " + e.getMessage());
            return;
        }
        try {
            st.execute("ALTER TABLE " + tabela + " ADD COLUMN " + kolumna + " " + definicja);
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy dodawaniu kolumny " + kolumna + ": " + e.getMessage());
        }
    }

    public void disconnect() {
        // Polaczenie nalezy do CoreDatabase - nic nie zamykamy.
    }

    public synchronized int stworzKlan(String nazwa, String tag, UUID lider) {
        String sqlKlan = "INSERT INTO klany (nazwa, tag, lider, pvp, skarbiec, data_zalozenia) VALUES (?, ?, ?, 0, 0, ?)";
        try {
            return CoreDatabase.runLockedQuery(c -> {
                try (PreparedStatement ps = c.prepareStatement(sqlKlan, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nazwa);
                    ps.setString(2, tag);
                    ps.setString(3, lider.toString());
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            int id = rs.getInt(1);
                            dodajCzlonkaOn(c, id, lider, Klan.Ranga.LIDER);
                            return id;
                        }
                    }
                }
                return -1;
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy tworzeniu klanu: " + e.getMessage());
        }
        return -1;
    }

    public synchronized void usunKlan(int klanId) {
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps1 = c.prepareStatement("DELETE FROM czlonkowie WHERE klan_id = ?");
                     PreparedStatement ps2 = c.prepareStatement("DELETE FROM klany WHERE id = ?");
                     PreparedStatement ps3 = c.prepareStatement("DELETE FROM logi WHERE klan_id = ?")) {
                    ps1.setInt(1, klanId);
                    ps1.executeUpdate();
                    ps2.setInt(1, klanId);
                    ps2.executeUpdate();
                    ps3.setInt(1, klanId);
                    ps3.executeUpdate();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy usuwaniu klanu: " + e.getMessage());
        }
    }

    public synchronized void dodajCzlonka(int klanId, UUID uuid, Klan.Ranga ranga) {
        try {
            CoreDatabase.runLocked(c -> dodajCzlonkaOn(c, klanId, uuid, ranga));
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy dodawaniu czlonka: " + e.getMessage());
        }
    }

    private static void dodajCzlonkaOn(Connection c, int klanId, UUID uuid, Klan.Ranga ranga) throws SQLException {
        String sql = "INSERT OR REPLACE INTO czlonkowie (uuid, klan_id, ranga, ranking) VALUES (?, ?, ?, 1000)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, klanId);
            ps.setString(3, ranga.name());
            ps.executeUpdate();
        }
    }

    /**
     * Usuwa gracza z klanu (opuszczenie/wyrzucenie). Kasuje TYLKO wiersz w "czlonkowie"
     * (przynaleznosc/ranga) - statystyki PvP i ranking ELO gracza zyja w osobnej tabeli
     * "gracze" i NIE sa tu ruszane, wiec gracz po wyjsciu z klanu zachowuje swoje kille,
     * smierci i ranking.
     */
    public synchronized void usunCzlonka(UUID uuid) {
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM czlonkowie WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy usuwaniu czlonka: " + e.getMessage());
        }
    }

    public synchronized void ustawRange(UUID uuid, Klan.Ranga ranga) {
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement("UPDATE czlonkowie SET ranga = ? WHERE uuid = ?")) {
                    ps.setString(1, ranga.name());
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy ustawianiu rangi: " + e.getMessage());
        }
    }

    public synchronized void ustawLidera(int klanId, UUID uuid) {
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement("UPDATE klany SET lider = ? WHERE id = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, klanId);
                    ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy ustawianiu lidera: " + e.getMessage());
        }
    }

    public synchronized void ustawPvp(int klanId, boolean wartosc) {
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement("UPDATE klany SET pvp = ? WHERE id = ?")) {
                    ps.setInt(1, wartosc ? 1 : 0);
                    ps.setInt(2, klanId);
                    ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy ustawianiu pvp: " + e.getMessage());
        }
    }

    public synchronized void ustawRankingKlanu(int klanId, int wartosc) {
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement("UPDATE klany SET ranking_klanu = ? WHERE id = ?")) {
                    ps.setInt(1, wartosc);
                    ps.setInt(2, klanId);
                    ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy ustawianiu rankingu klanu: " + e.getMessage());
        }
    }

    public synchronized void ustawSkarbiec(int klanId, int wartosc) {
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement("UPDATE klany SET skarbiec = ? WHERE id = ?")) {
                    ps.setInt(1, wartosc);
                    ps.setInt(2, klanId);
                    ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy ustawianiu skarbca: " + e.getMessage());
        }
    }

    private static void ustawStatystykiOn(Connection c, UUID uuid, int zabojstwa, int smierci) throws SQLException {
        String sql = """
            INSERT INTO gracze (uuid, zabojstwa, smierci, ranking) VALUES (?, ?, ?, 1000)
            ON CONFLICT(uuid) DO UPDATE SET zabojstwa = excluded.zabojstwa, smierci = excluded.smierci
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, zabojstwa);
            ps.setInt(3, smierci);
            ps.executeUpdate();
        }
    }

    public synchronized void ustawStatystyki(UUID uuid, int zabojstwa, int smierci) {
        try {
            CoreDatabase.runInTransaction(
                    c -> ustawStatystykiOn(c, uuid, zabojstwa, smierci));
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy zapisywaniu statystyk PvP: " + e.getMessage());
        }
    }

    private static void ustawRankingOn(Connection c, UUID uuid, int ranking, int tier) throws SQLException {
        String sql = """
            INSERT INTO gracze (uuid, zabojstwa, smierci, ranking, tier) VALUES (?, 0, 0, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET ranking = excluded.ranking, tier = excluded.tier
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, ranking);
            ps.setInt(3, tier);
            ps.executeUpdate();
        }
    }

    public synchronized void ustawRanking(UUID uuid, int ranking, int tier) {
        try {
            CoreDatabase.runInTransaction(
                    c -> ustawRankingOn(c, uuid, ranking, tier));
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy zapisywaniu rankingu: " + e.getMessage());
        }
    }

    private static Map<UUID, int[]> wczytajStatystykiGraczyOn(Connection c) throws SQLException {
        Map<UUID, int[]> wynik = new HashMap<>();
        String sql = "SELECT uuid, zabojstwa, smierci, ranking, tier FROM gracze";
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                wynik.put(uuid, new int[]{rs.getInt("zabojstwa"), rs.getInt("smierci"), rs.getInt("ranking"), rs.getInt("tier")});
            }
        }
        return wynik;
    }

    public synchronized Map<UUID, int[]> wczytajStatystykiGraczy() {
        try {
            return CoreDatabase.runLockedQuery(KlanDatabase::wczytajStatystykiGraczyOn);
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad przy wczytywaniu statystyk graczy: " + e.getMessage());
            return new HashMap<>();
        }
    }

    public synchronized void resetujStatystykiWszystkich() {
        try {
            CoreDatabase.runLocked(c -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("UPDATE gracze SET zabojstwa = 0, smierci = 0, ranking = 1000");
                    st.executeUpdate("UPDATE czlonkowie SET zabojstwa = 0, smierci = 0, ranking = 1000");
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy resetowaniu statystyk graczy: " + e.getMessage());
        }
    }

    public synchronized void resetujRankingKlanowWszystkich() {
        try {
            CoreDatabase.runLocked(c -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("UPDATE klany SET ranking_klanu = 0");
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy resetowaniu rankingu klanow: " + e.getMessage());
        }
    }

    public synchronized void ustawPerk(int klanId, String perkId, double wartosc) {
        String sql = "INSERT INTO perki_klanu (klan_id, perk_id, wartosc) VALUES (?, ?, ?) " +
                "ON CONFLICT(klan_id, perk_id) DO UPDATE SET wartosc = excluded.wartosc";
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, klanId);
                    ps.setString(2, perkId);
                    ps.setDouble(3, wartosc);
                    ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy zapisywaniu perku klanu: " + e.getMessage());
        }
    }

    private static java.util.Map<String, Double> wczytajPerkiOn(Connection c, int klanId) throws SQLException {
        java.util.Map<String, Double> wynik = new java.util.LinkedHashMap<>();
        String sql = "SELECT perk_id, wartosc FROM perki_klanu WHERE klan_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, klanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    wynik.put(rs.getString("perk_id"), rs.getDouble("wartosc"));
                }
            }
        }
        return wynik;
    }

    public synchronized void dodajLog(int klanId, String tresc) {
        try {
            CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO logi (klan_id, czas, tresc) VALUES (?, ?, ?)")) {
                    ps.setInt(1, klanId);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, tresc);
                    ps.executeUpdate();
                    try (PreparedStatement cleanup = c.prepareStatement(
                            "DELETE FROM logi WHERE klan_id = ? AND id NOT IN (SELECT id FROM logi WHERE klan_id = ? ORDER BY id DESC LIMIT 200)")) {
                        cleanup.setInt(1, klanId);
                        cleanup.setInt(2, klanId);
                        cleanup.executeUpdate();
                    }
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy dodawaniu logu: " + e.getMessage());
        }
    }

    public synchronized java.util.List<String> pobierzLogi(int klanId, int limit) {
        try {
            return CoreDatabase.runLockedQuery(c -> {
                java.util.List<String> wynik = new java.util.ArrayList<>();
                String sql = "SELECT czas, tresc FROM logi WHERE klan_id = ? ORDER BY id DESC LIMIT ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, klanId);
                    ps.setInt(2, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            wynik.add(rs.getLong("czas") + "|" + rs.getString("tresc"));
                        }
                    }
                }
                return wynik;
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad przy pobieraniu logow: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    public synchronized java.util.List<Klan> wczytajWszystkie() {
        try {
            return CoreDatabase.runLockedQuery(c -> {
                java.util.List<Klan> klany = new java.util.ArrayList<>();
                String sqlKlany = "SELECT id, nazwa, tag, lider, pvp, skarbiec, data_zalozenia, ranking_klanu FROM klany";
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery(sqlKlany)) {
                    while (rs.next()) {
                        Klan klan = new Klan(
                                rs.getInt("id"),
                                rs.getString("nazwa"),
                                rs.getString("tag"),
                                UUID.fromString(rs.getString("lider")),
                                rs.getInt("pvp") == 1,
                                rs.getInt("skarbiec"),
                                rs.getLong("data_zalozenia")
                        );
                        klan.setRankingKlanu(rs.getInt("ranking_klanu"));
                        klany.add(klan);
                    }
                }

                for (Klan klan : klany) {
                    String sqlCzlonkowie = "SELECT uuid, ranga FROM czlonkowie WHERE klan_id = ? ORDER BY rowid ASC";
                    try (PreparedStatement ps = c.prepareStatement(sqlCzlonkowie)) {
                        ps.setInt(1, klan.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                UUID uuid = UUID.fromString(rs.getString("uuid"));
                                klan.getCzlonkowie().put(uuid, Klan.Ranga.valueOf(rs.getString("ranga")));
                            }
                        }
                    }
                    for (java.util.Map.Entry<String, Double> perk : wczytajPerkiOn(c, klan.getId()).entrySet()) {
                        klan.setPerk(perk.getKey(), perk.getValue());
                    }
                }
                return klany;
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad przy wczytywaniu klanow: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }
}
