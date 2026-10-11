package boniec.nagroda;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Baza SQLite systemu nagrod za Discorda (nagroda.db).
 * <p>
 * Tabela "polaczenia" pilnuje zasady MAX 1 POLACZONE KONTO:
 *  - jedno konto Discord (discord_id - klucz glowny) = najwyzej jeden nick z Minecrafta,
 *  - jeden nick z Minecrafta (mc_uuid - UNIQUE) = najwyzej jedno konto Discord.
 * Zasade wymusza sama baza, wiec nawet wyscig dwoch klikniec nie stworzy drugiego polaczenia.
 * <p>
 * Dostep jest synchronizowany (jedno polaczenie) - baza jest uzywana z watku serwera i z watku JDA.
 */
public class NagrodaBaza {

    private final JavaPlugin plugin;
    private Connection polaczenie;

    public NagrodaBaza(JavaPlugin plugin) throws SQLException {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        polaczenie = boniec.code.core.UstawieniaDatabase.open(plugin);
        try (Statement st = polaczenie.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS polaczenia (" +
                    "discord_id INTEGER PRIMARY KEY," +
                    "mc_uuid TEXT NOT NULL UNIQUE," +
                    "mc_nick TEXT NOT NULL," +
                    "discord_nazwa TEXT," +
                    "polaczono INTEGER NOT NULL" +
                    ")");
            st.execute("CREATE TABLE IF NOT EXISTS ustawienia (" +
                    "klucz TEXT PRIMARY KEY," +
                    "wartosc TEXT NOT NULL" +
                    ")");
        }
        zmigrujStareDane(new File(plugin.getDataFolder(), "nagroda-dane.yml"));
    }

    /** Jednorazowy import ze starego pliku nagroda-dane.yml (jesli istnieje) - potem plik dostaje rozszerzenie .migrated. */
    private void zmigrujStareDane(File stary) {
        if (!stary.exists()) return;
        try {
            FileConfiguration y = YamlConfiguration.loadConfiguration(stary);
            var mc = y.getConfigurationSection("odebrane-mc");
            var dc = y.getConfigurationSection("odebrane-dc");
            int n = 0;
            if (mc != null) {
                for (String uuid : mc.getKeys(false)) {
                    try {
                        long discordId = Long.parseLong(mc.getString(uuid, ""));
                        String nick = dc != null ? dc.getString(String.valueOf(discordId), "?") : "?";
                        if (polacz(discordId, null, UUID.fromString(uuid), nick)) n++;
                    } catch (IllegalArgumentException ignored) {
                        // uszkodzony wpis - pomijamy
                    }
                }
            }
            String msgId = y.getString("wiadomosc-id", "");
            if (!msgId.isEmpty()) {
                ustaw("wiadomosc-id", msgId);
                ustaw("wiadomosc-kanal-id", y.getString("wiadomosc-kanal-id", ""));
            }
            File nowy = new File(stary.getParentFile(), "nagroda-dane.yml.migrated");
            if (!stary.renameTo(nowy)) plugin.getLogger().warning("[Nagroda] Nie udalo sie zmienic nazwy nagroda-dane.yml po migracji.");
            plugin.getLogger().info("[Nagroda] Zmigrowano " + n + " polaczen z nagroda-dane.yml do SQLite (nagroda.db).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Nagroda] Migracja nagroda-dane.yml nie powiodla sie.", e);
        }
    }

    // ── Polaczenia Discord <-> Minecraft ───────────────────────

    /** Nick MC polaczony z tym kontem Discord, albo null. */
    public synchronized String nickDlaDiscord(long discordId) {
        try (PreparedStatement ps = polaczenie.prepareStatement("SELECT mc_nick FROM polaczenia WHERE discord_id = ?")) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Nagroda] Blad odczytu bazy.", e);
            return null;
        }
    }

    /** Czy to konto Minecraft ma juz polaczone konto Discord. */
    public synchronized boolean czyMcPolaczone(UUID uuid) {
        try (PreparedStatement ps = polaczenie.prepareStatement("SELECT 1 FROM polaczenia WHERE mc_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Nagroda] Blad odczytu bazy.", e);
            return false;
        }
    }

    /**
     * Zapisuje polaczenie. Zwraca false, gdy ktores z kont jest juz gdzies polaczone
     * (naruszenie PRIMARY KEY / UNIQUE) albo wystapil blad bazy.
     */
    public synchronized boolean polacz(long discordId, String discordNazwa, UUID uuid, String nick) {
        try (PreparedStatement ps = polaczenie.prepareStatement(
                "INSERT INTO polaczenia (discord_id, mc_uuid, mc_nick, discord_nazwa, polaczono) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, discordId);
            ps.setString(2, uuid.toString());
            ps.setString(3, nick);
            ps.setString(4, discordNazwa);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Usuwa polaczenie po nicku MC (bez rozroznienia wielkosci liter). Zwraca liczbe usunietych wpisow. */
    public synchronized int odlaczPoNicku(String nick) {
        try (PreparedStatement ps = polaczenie.prepareStatement("DELETE FROM polaczenia WHERE LOWER(mc_nick) = LOWER(?)")) {
            ps.setString(1, nick);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Nagroda] Blad usuwania polaczenia.", e);
            return 0;
        }
    }

    // ── Ustawienia (np. ID wiadomosci z embedem) ───────────────

    public synchronized String pobierz(String klucz, String domyslna) {
        try (PreparedStatement ps = polaczenie.prepareStatement("SELECT wartosc FROM ustawienia WHERE klucz = ?")) {
            ps.setString(1, klucz);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : domyslna;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Nagroda] Blad odczytu bazy.", e);
            return domyslna;
        }
    }

    public synchronized void ustaw(String klucz, String wartosc) {
        try (PreparedStatement ps = polaczenie.prepareStatement(
                "INSERT INTO ustawienia (klucz, wartosc) VALUES (?, ?) ON CONFLICT(klucz) DO UPDATE SET wartosc = excluded.wartosc")) {
            ps.setString(1, klucz);
            ps.setString(2, wartosc);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Nagroda] Blad zapisu do bazy.", e);
        }
    }

    public synchronized void zamknij() {
        polaczenie = null;
    }
}
