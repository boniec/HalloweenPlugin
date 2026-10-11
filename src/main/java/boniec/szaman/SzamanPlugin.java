package boniec.szaman;

import boniec.klany.core.KlanManager;
import boniec.szaman.cmd.AszamanCommand;
import boniec.szaman.cmd.SzamanCommand;
import boniec.szaman.core.BonusCalculator;
import boniec.szaman.core.DropPerkManager;
import boniec.szaman.core.KlanBonusProvider;
import boniec.szaman.core.PerkManager;
import boniec.szaman.core.WalutaManager;
import boniec.szaman.listener.CombatListener;
import boniec.szaman.listener.MovementListener;
import boniec.szaman.listener.SzamanGuiListener;
import boniec.szaman.listener.ZyciaListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UWAGA: ta klasa NIE jest wlasnym JavaPlugin - jest zwyklym "menedzerem" systemu
 * Szamana, uruchamianym z wnetrza Main#onEnable() (klasa glowna calego pluginu
 * "Inventory", zadeklarowana w plugin.yml jako "main:"). Bukkit tworzy tylko jedna
 * instancje glownej klasy na jar - proba zrobienia z SzamanPlugin osobnego
 * JavaPlugin (tak jak bylo pierwotnie, gdy Szaman byl osobnym pluginem) konczy sie
 * wyjatkiem "Plugin already initialized!" przy proba stworzenia drugiej instancji
 * JavaPlugin w tym samym jarze/classloaderze.
 * <p>
 * Wszystkie miejsca w kodzie Szamana ktore potrzebuja prawdziwego org.bukkit.plugin.Plugin
 * (np. Bukkit.getScheduler().runTaskLater(plugin, ...)) uzywaja {@link #getHost()}
 * zamiast bezposrednio "this"/SzamanPlugin.
 */
public final class SzamanPlugin {

    private static SzamanPlugin instancja;

    private final JavaPlugin host;

    private PerkManager perkManager;
    private DropPerkManager dropPerkManager;
    private WalutaManager walutaManager;
    private KlanBonusProvider klanBonusProvider;
    private BonusCalculator bonusCalculator;
    private FileConfiguration szamanConfig;

    public SzamanPlugin(JavaPlugin host) {
        this.host = host;
    }

    /**
     * Wywolywane z Main#onEnable() (zamiast wlasnego onEnable() - ta klasa nie jest
     * juz JavaPlugin), po tym jak KlanManager systemu klanow jest juz zaladowany.
     */
    public void wlacz(KlanManager klanManager) {
        instancja = this;

        this.szamanConfig = wczytajSzamanConfig();

        this.perkManager = new PerkManager(this);
        this.perkManager.wczytaj();

        this.dropPerkManager = new DropPerkManager(this);
        this.dropPerkManager.wczytaj();

        this.walutaManager = new WalutaManager(this);
        this.walutaManager.wczytaj();

        // Integracja z systemem klanow - teraz w tym samym jarze co Szaman (patrz
        // boniec.klany.core.KlanManager), wiec przekazujemy go bezposrednio z
        // Main#onEnable(), bez refleksji miedzy-pluginowej uzywanej pierwotnie.
        this.klanBonusProvider = new KlanBonusProvider(klanManager, getLogger());
        this.bonusCalculator = new BonusCalculator(perkManager, klanBonusProvider, this);

        org.bukkit.command.PluginCommand szamanCmd = host.getCommand("szaman");
        if (szamanCmd != null) {
            szamanCmd.setExecutor(new SzamanCommand(this));
        }
        org.bukkit.command.PluginCommand aszamanCmd = host.getCommand("aszaman");
        if (aszamanCmd != null) {
            AszamanCommand aszaman = new AszamanCommand(this);
            aszamanCmd.setExecutor(aszaman);
            aszamanCmd.setTabCompleter(aszaman);
        }

        Bukkit.getPluginManager().registerEvents(new SzamanGuiListener(this), host);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), host);
        Bukkit.getPluginManager().registerEvents(new MovementListener(this), host);
        Bukkit.getPluginManager().registerEvents(new ZyciaListener(this), host);

        getLogger().info("Szaman wlaczony.");
    }

    /** Wywolywane z Main#onDisable(). */
    public void wylacz() {
        if (perkManager != null) perkManager.zapiszSync();
        if (dropPerkManager != null) dropPerkManager.zapiszSync();
        if (walutaManager != null) walutaManager.zapisz();
    }

    /** Prawdziwy JavaPlugin (Main) - do uzycia wszedzie tam, gdzie Bukkit wymaga org.bukkit.plugin.Plugin. */
    public JavaPlugin getHost() {
        return host;
    }

    public Logger getLogger() {
        return host.getLogger();
    }

    public File getDataFolder() {
        return host.getDataFolder();
    }

    public static SzamanPlugin get() {
        return instancja;
    }

    public PerkManager getPerkManager() {
        return perkManager;
    }

    public DropPerkManager getDropPerkManager() {
        return dropPerkManager;
    }

    public WalutaManager getWalutaManager() {
        return walutaManager;
    }

    public KlanBonusProvider getKlanBonusProvider() {
        return klanBonusProvider;
    }

    public BonusCalculator getBonusCalculator() {
        return bonusCalculator;
    }

    public FileConfiguration getSzamanConfig() {
        return szamanConfig;
    }

    public void zapiszSzamanConfig() {
        if (szamanConfig == null) return;
        File plik = new File(getDataFolder(), "szaman.yml");
        try {
            szamanConfig.save(plik);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Nie udalo sie zapisac szaman.yml", e);
        }
    }

    private FileConfiguration wczytajSzamanConfig() {
        File plik = new File(getDataFolder(), "szaman.yml");

        // Migracja: szaman.yml ze starego systemu (kamienie, kategorie, drzewko perkow) nie ma
        // sekcji nowych perkow ("strength", "data"...), a Bukkit nie dopisuje brakujacych sekcji
        // do istniejacego pliku - wiec taki plik odkladamy na bok i zapisujemy nowy z jara.
        if (plik.exists()) {
            YamlConfiguration istniejacy = YamlConfiguration.loadConfiguration(plik);
            boolean nowyFormat = istniejacy.isConfigurationSection("data") && istniejacy.isConfigurationSection("strength");
            if (!nowyFormat) {
                File kopia = new File(getDataFolder(), "szaman.yml.stary");
                try {
                    Files.move(plik.toPath(), kopia.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLogger().warning("Wykryto stary szaman.yml (kamienie/drzewko perkow) - zapisano kopie jako "
                            + kopia.getName() + " i utworzono nowy plik z konfiguracja artefaktow.");
                } catch (IOException e) {
                    getLogger().log(Level.WARNING, "Nie udalo sie odlozyc starego szaman.yml - nadpisuje go nowym.", e);
                    plik.delete();
                }
            }
        }

        if (!plik.exists()) {
            host.saveResource("szaman.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(plik);

        // Defaults z JAR-a
        InputStream defStream = host.getResource("szaman.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            // Dopisz do istniejacego pliku sekcje perkow dodanych w nowszych wersjach (Bukkit sam
            // ich nie dopisuje, a bez sekcji "data.<perk>" perk nie mialby zadnych poziomow).
            boolean zmieniono = false;
            for (String sciezka : new String[]{"weakening", "block", "health", "data.weakening", "data.block", "data.health",
                    "perki-nazwy.weakening", "perki-nazwy.block", "perki-nazwy.health"}) {
                if (!config.isConfigurationSection(sciezka) && defConfig.isConfigurationSection(sciezka)) {
                    for (String klucz : defConfig.getConfigurationSection(sciezka).getKeys(true)) {
                        String pelny = sciezka + "." + klucz;
                        Object wartosc = defConfig.get(pelny);
                        if (!(wartosc instanceof org.bukkit.configuration.ConfigurationSection)) {
                            config.set(pelny, wartosc);
                        }
                    }
                    zmieniono = true;
                    getLogger().info("Dopisano brakujaca sekcje '" + sciezka + "' do szaman.yml.");
                }
            }
            // Stary plik mial perk zycia wylaczony (kolizja slotu 21 z uwiezieniem).
            if (!config.getBoolean("health.enable", false)) {
                config.set("health.enable", true);
                if (config.getInt("health.slot", 21) == config.getInt("confinement.slot", 21)) {
                    config.set("health.slot", 20);
                }
                zmieniono = true;
                getLogger().info("Wlaczono perk zycia w szaman.yml (slot 20).");
            }
            if (zmieniono) {
                try {
                    config.save(plik);
                } catch (IOException e) {
                    getLogger().log(Level.WARNING, "Nie udalo sie zapisac uzupelnionego szaman.yml", e);
                }
            }
            config.setDefaults(defConfig);
        }
        return config;
    }
}
