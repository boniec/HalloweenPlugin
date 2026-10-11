package boniec.szaman.core;

import boniec.szaman.SzamanPlugin;
import boniec.code.core.CoreDatabase;
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
 * "Boost Dropu" (BOOSTDROP) - jedyny perk Szamana, ktory NIE ma stalych poziomow, tylko
 * LOSUJE procent: kazde ulepszenie w /szaman dolosowuje losowa wartosc (domyslnie 3%-10%)
 * do zarobku podczas kopania generatorow. Kupowany za Fragmenty Galaktyki.
 * <p>
 * Zasady (konfigurowalne w szaman.yml -> perk-dropu):
 *  - wlasny procent gracza liczy sie z losowan; DODATKOWO dolicza sie bonus dropu calego
 *    KLANU gracza (klanowy perk dropu z /klan ulepszenia) - patrz {@link #getProcentLaczny},
 *  - koszt kolejnego losowania rosnie wraz z liczba juz wykonanych losowan
 *    (progi konfigurowalne w liscie "progi-kosztow"),
 *  - jesli wlasny procent gracza przekracza prog (domyslnie 70%), kazde kolejne
 *    losowanie kosztuje stala, wysoka cene (domyslnie 50),
 *  - twardy limit WLASNEGO procentu (domyslnie 200%).
 * <p>
 * Trzymane w pamieci (cache) i zapisywane asynchronicznie do perk-dropu.yml
 * (format pliku bez zmian wzgledem poprzedniej wersji - dane graczy zostaja).
 */
public class DropPerkManager {

    private final SzamanPlugin plugin;

    private final Map<UUID, Double> procenty = new ConcurrentHashMap<>();
    // uuid -> ile razy gracz juz losowal (do progresywnego kosztu)
    private final Map<UUID, Integer> losowania = new ConcurrentHashMap<>();

    public DropPerkManager(SzamanPlugin plugin) {
        this.plugin = plugin;
    }

    public void wczytaj() {
        zmigrujYamlJesliTrzeba();
        procenty.clear();
        losowania.clear();
        try (PreparedStatement ps = CoreDatabase.connection().prepareStatement(
                "SELECT uuid, procent, losowania FROM szaman_drop");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    double procent = rs.getDouble("procent");
                    int ile = rs.getInt("losowania");
                    if (procent > 0) procenty.put(uuid, procent);
                    if (ile > 0) losowania.put(uuid, ile);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Blad odczytu szaman_drop", e);
        }
        plugin.getLogger().info("Wczytano perk dropu dla " + procenty.size() + " graczy (data.db).");
    }

    private void zmigrujYamlJesliTrzeba() {
        if (CoreDatabase.get().juzZmigrowane("yaml:perk-dropu.yml")) return;
        File plik = new File(plugin.getDataFolder(), "perk-dropu.yml");
        if (plik.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(plik);
            if (config.isConfigurationSection("gracze")) {
                for (String uuidStr : config.getConfigurationSection("gracze").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        double procent = config.getDouble("gracze." + uuidStr + ".procent", 0.0);
                        int ile = config.getInt("gracze." + uuidStr + ".losowania", 0);
                        if (procent > 0) procenty.put(uuid, procent);
                        if (ile > 0) losowania.put(uuid, ile);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                zapiszTeraz();
                File migrated = new File(plugin.getDataFolder(), "perk-dropu.yml.migrated");
                if (!plik.renameTo(migrated)) {
                    plugin.getLogger().warning("Nie zmieniono nazwy perk-dropu.yml po migracji.");
                }
                plugin.getLogger().info("Zmigrowano perk-dropu.yml do data.db");
            }
        }
        CoreDatabase.get().oznaczMigracje("yaml:perk-dropu.yml");
    }

    public void zapiszAsync() {
        if (!plugin.getHost().isEnabled()) {
            zapiszTeraz();
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin.getHost(), this::zapiszTeraz);
    }

    public void zapiszSync() {
        zapiszTeraz();
    }

    private synchronized void zapiszTeraz() {
        try {
            CoreDatabase.runInTransaction(c -> {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM szaman_drop")) {
                    del.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO szaman_drop (uuid, procent, losowania) VALUES (?, ?, ?)")) {
                    java.util.Set<UUID> wszyscy = new java.util.HashSet<>();
                    wszyscy.addAll(procenty.keySet());
                    wszyscy.addAll(losowania.keySet());
                    for (UUID uuid : wszyscy) {
                        ps.setString(1, uuid.toString());
                        ps.setDouble(2, procenty.getOrDefault(uuid, 0.0));
                        ps.setInt(3, losowania.getOrDefault(uuid, 0));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Blad przy zapisywaniu szaman_drop", e);
        }
    }

    /** Maksymalny procent perku dropu (limit). */
    public double getLimit() {
        FileConfiguration cfg = plugin.getSzamanConfig();
        if (cfg == null) return 200.0;
        return cfg.getDouble("perk-dropu.limit", 200.0);
    }

    public double getProcentGracza(UUID uuid) {
        return Math.min(procenty.getOrDefault(uuid, 0.0), getLimit());
    }

    public int getIloscLosowan(UUID uuid) {
        return losowania.getOrDefault(uuid, 0);
    }

    /**
     * Zwraca koszt (w Odlamkach) NASTEPNEGO losowania dla gracza, na podstawie
     * jego aktualnego procentu i liczby dotychczasowych losowan.
     * <p>
     * Priorytet: jesli gracz ma juz > prog-wysokiego-kosztu% (domyslnie 70%),
     * kazde kolejne losowanie kosztuje staly "koszt-powyzej-progu" (domyslnie
     * 50), niezaleznie od liczby wczesniejszych losowan. W przeciwnym razie
     * koszt zalezy od progow zdefiniowanych w konfiguracji (domyslnie:
     * losowania 1-4 -> 10, 5-9 -> 20, 10-13 -> 30, od 14 w gore -> ostatni
     * zdefiniowany prog).
     */
    public int getKosztNastepnegoLosowania(UUID uuid) {
        FileConfiguration cfg = plugin.getSzamanConfig();
        double progWysokiegoKosztu = cfg != null ? cfg.getDouble("perk-dropu.prog-wysokiego-kosztu", 70.0) : 70.0;
        int kosztPowyzejProgu = cfg != null ? cfg.getInt("perk-dropu.koszt-powyzej-progu", 50) : 50;

        if (getProcentGracza(uuid) >= progWysokiegoKosztu) {
            return kosztPowyzejProgu;
        }

        int jużWykonane = getIloscLosowan(uuid);
        int numerLosowania = jużWykonane + 1; // ktore z kolei losowanie gracz zaraz wykona

        java.util.List<Map<?, ?>> progi = cfg != null
                ? cfg.getMapList("perk-dropu.progi-kosztow")
                : java.util.Collections.emptyList();

        if (progi.isEmpty()) {
            // Domyslne progi, gdyby config nie mial sekcji (fallback bezpieczenstwa).
            if (numerLosowania <= 4) return 10;
            if (numerLosowania <= 9) return 20;
            if (numerLosowania <= 13) return 30;
            return kosztPowyzejProgu;
        }

        int ostatniKoszt = kosztPowyzejProgu;
        for (Map<?, ?> prog : progi) {
            Object doObj = prog.get("do");
            Object kosztObj = prog.get("koszt");
            if (kosztObj == null) continue;
            int koszt = kosztObj instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(kosztObj));
            ostatniKoszt = koszt;

            // "do: -1" (lub brak klucza "do") oznacza "bez gornej granicy" - ostatni prog w liscie.
            if (doObj == null) {
                return koszt;
            }
            int do_ = doObj instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(doObj));
            if (do_ < 0) {
                return koszt;
            }
            if (numerLosowania <= do_) {
                return koszt;
            }
        }
        return ostatniKoszt;
    }

    /** Czy gracz ma juz osiagniety limit (nie mozna wiecej losowac). */
    public boolean maMaksymalnyPerk(UUID uuid) {
        return getProcentGracza(uuid) >= getLimit();
    }

    /**
     * Dolicza wylosowana wartosc do procentu gracza, capujac do limitu, i
     * zwieksza licznik losowan o 1. Zwraca FAKTYCZNIE dodana wartosc (moze byc
     * mniejsza niz "proponowanaWartosc", jesli gracz jest blisko limitu).
     */
    public double dodajProcent(UUID uuid, double proponowanaWartosc) {
        double limit = getLimit();
        double aktualny = getProcentGracza(uuid);
        double nowy = aktualny + proponowanaWartosc;
        double faktycznieDodane = proponowanaWartosc;
        if (nowy > limit) {
            nowy = limit;
            faktycznieDodane = limit - aktualny;
        }
        procenty.put(uuid, nowy);
        losowania.merge(uuid, 1, Integer::sum);
        zapiszAsync();
        return faktycznieDodane;
    }

    /**
     * Bonus dropu calego klanu gracza (klanowy perk dropu), w %. 0, gdy gracz nie ma klanu
     * albo klan nie ulepszyl perku dropu.
     */
    public double getBonusKlanowy(UUID uuid) {
        KlanBonusProvider provider = plugin.getKlanBonusProvider();
        return provider == null ? 0.0 : provider.getBonusDropu(uuid);
    }

    /** Wlasny procent gracza + bonus klanowy - to jest dokladnie to, co mnozy zarobek z generatorow. */
    public double getProcentLaczny(UUID uuid) {
        return getProcentGracza(uuid) + getBonusKlanowy(uuid);
    }

    /** Admin: ustawia wlasny procent gracza (przyciety do 0..limit); 0 usuwa procent. */
    public void ustawProcent(UUID uuid, double procent) {
        double wartosc = Math.max(0.0, Math.min(procent, getLimit()));
        if (wartosc <= 0) {
            procenty.remove(uuid);
        } else {
            procenty.put(uuid, wartosc);
        }
        zapiszAsync();
    }

    /** Admin: zeruje procent i licznik losowan gracza (koszt wraca do poziomu poczatkowego). */
    public void resetuj(UUID uuid) {
        procenty.remove(uuid);
        losowania.remove(uuid);
        zapiszAsync();
    }

    public void wyczyscPoWyjsciu(UUID uuid) {
        // Dane boostu dropu sa trwale - nic nie czyscimy przy wyjsciu; metoda zostawiona
        // dla spojnosci z PerkManager#wyczyscPoWyjsciu.
    }
}
