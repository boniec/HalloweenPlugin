package boniec.szaman.core;

import boniec.code.core.CoreDatabase;
import boniec.szaman.SzamanPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Poziomy perkow Szamana. Zapis w data.db (szaman_perki).
 * Stary zapis do poziomy.yml nadpisywal config poziomow postaci.
 */
public class PerkManager {

    private final SzamanPlugin plugin;
    private final Map<UUID, Map<String, Integer>> poziomy = new ConcurrentHashMap<>();
    private final Map<UUID, Long> uwiezienie = new ConcurrentHashMap<>();

    public PerkManager(SzamanPlugin plugin) {
        this.plugin = plugin;
    }

    public void wczytaj() {
        zmigrujYamlJesliTrzeba();
        poziomy.clear();
        try (PreparedStatement ps = CoreDatabase.connection().prepareStatement(
                "SELECT uuid, perk, poziom FROM szaman_perki");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String perk = rs.getString("perk");
                    int poziom = rs.getInt("poziom");
                    if (poziom > 0 && Perk.zKlucza(perk) != null) {
                        poziomy.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(perk.toLowerCase(), poziom);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Blad odczytu szaman_perki", e);
        }
        plugin.getLogger().info("Wczytano poziomy perkow dla " + poziomy.size() + " graczy (data.db).");
    }

    private void zmigrujYamlJesliTrzeba() {
        if (CoreDatabase.get().juzZmigrowane("yaml:szaman-perki")) return;
        File plik = new File(plugin.getDataFolder(), "poziomy.yml");
        if (plik.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(plik);
            if (config.isConfigurationSection("poziomy")) {
                boolean byloUuid = false;
                for (String uuidStr : config.getConfigurationSection("poziomy").getKeys(false)) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidStr);
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    byloUuid = true;
                    if (config.getConfigurationSection("poziomy." + uuidStr) == null) continue;
                    for (String klucz : config.getConfigurationSection("poziomy." + uuidStr).getKeys(false)) {
                        int poziom = config.getInt("poziomy." + uuidStr + "." + klucz, 0);
                        if (poziom > 0 && Perk.zKlucza(klucz) != null) {
                            this.poziomy.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(klucz.toLowerCase(), poziom);
                        }
                    }
                }
                if (byloUuid) {
                    zapiszTeraz();
                    plugin.getLogger().info("Zmigrowano perki Szamana z poziomy.yml do data.db");
                }
            }
        }
        CoreDatabase.get().oznaczMigracje("yaml:szaman-perki");
    }

    public void zapiszAsync() {
        if (!plugin.getHost().isEnabled()) {
            zapiszTeraz();
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin.getHost(), this::zapiszTeraz);
    }

    /** Synchroniczny zapis — przy wylaczaniu pluginu (async i tak ginie przy /plugman reload). */
    public void zapiszSync() {
        zapiszTeraz();
    }

    private synchronized void zapiszTeraz() {
        try {
            CoreDatabase.runInTransaction(c -> {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM szaman_perki")) {
                    del.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO szaman_perki (uuid, perk, poziom) VALUES (?, ?, ?)")) {
                    for (Map.Entry<UUID, Map<String, Integer>> wpis : poziomy.entrySet()) {
                        for (Map.Entry<String, Integer> perk : wpis.getValue().entrySet()) {
                            ps.setString(1, wpis.getKey().toString());
                            ps.setString(2, perk.getKey());
                            ps.setInt(3, perk.getValue());
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Blad przy zapisywaniu szaman_perki", e);
        }
    }

    public int getPoziom(UUID uuid, Perk perk) {
        Map<String, Integer> mapa = poziomy.get(uuid);
        int raw = mapa == null ? 0 : mapa.getOrDefault(perk.getKlucz(), 0);
        int cap = perk.getMaxPoziom();
        if (cap > 0 && raw > cap) return cap;
        return raw;
    }

    public void ustawPoziom(UUID uuid, Perk perk, int poziom) {
        int max = perk.getMaxPoziom();
        int docelowy = Math.max(0, max > 0 ? Math.min(poziom, max) : poziom);
        if (docelowy <= 0) {
            Map<String, Integer> mapa = poziomy.get(uuid);
            if (mapa != null) mapa.remove(perk.getKlucz());
        } else {
            poziomy.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(perk.getKlucz(), docelowy);
        }
        zapiszAsync();
    }

    public void resetujPerk(UUID uuid, Perk perk) {
        Map<String, Integer> mapa = poziomy.get(uuid);
        if (mapa != null) mapa.remove(perk.getKlucz());
        zapiszAsync();
    }

    public void resetujWszystko(UUID uuid) {
        poziomy.remove(uuid);
        zapiszAsync();
    }

    public boolean istniejeAktywneUwiezienie() {
        return !uwiezienie.isEmpty();
    }

    public Long getKoniecUwiezienia(UUID uuid) {
        return uwiezienie.get(uuid);
    }

    public boolean czyUwieziony(UUID uuid) {
        Long koniec = uwiezienie.get(uuid);
        return koniec != null && System.currentTimeMillis() < koniec;
    }

    public void ustawUwiezienie(UUID uuid, long koniecEpochMillis) {
        uwiezienie.put(uuid, koniecEpochMillis);
    }

    public void usunUwiezienie(UUID uuid) {
        uwiezienie.remove(uuid);
    }

    public void wyczyscPoWyjsciu(UUID uuid) {
        uwiezienie.remove(uuid);
    }
}
