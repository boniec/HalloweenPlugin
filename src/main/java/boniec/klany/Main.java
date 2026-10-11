package boniec.klany;

import boniec.klany.core.KlanManager;
import boniec.klany.core.KlanWalutaManager;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * UWAGA: to JUZ NIE jest glowna klasa pluginu (JavaPlugin) - system Klanow zostal
 * scalony do jednego pluginu razem z Inventory (zob. boniec.dev.inventory.Main,
 * ktore jest teraz jedynym "main:" w plugin.yml).
 * <p>
 * Ta klasa zostala zachowana jako lekki, statyczny "uchwyt" (holder) wylacznie po to,
 * zeby cala reszta kodu klanow (GUI, komendy itd.), ktora byla pisana pod
 * {@code Main.get().getConfig()} / {@code Main.get().getKlanManager()}, mogla
 * dzialac bez zmian. Prawdziwa inicjalizacja (wczytanie klany-config.yml,
 * utworzenie KlanManager) odbywa sie w boniec.dev.inventory.Main.onEnable().
 */
public final class Main {

    private static Main instance;

    private FileConfiguration klanyConfig;
    private final KlanManager klanManager;
    private KlanWalutaManager klanWalutaManager;

    private Main(FileConfiguration klanyConfig, KlanManager klanManager) {
        this.klanyConfig = klanyConfig;
        this.klanManager = klanManager;
    }

    /**
     * Wywolywane raz z boniec.dev.inventory.Main.onEnable() po wczytaniu
     * klany-config.yml i utworzeniu KlanManager.
     */
    public static void init(FileConfiguration klanyConfig, KlanManager klanManager) {
        instance = new Main(klanyConfig, klanManager);
    }

    /**
     * Wywolywane raz z boniec.dev.inventory.Main.onEnable() po utworzeniu
     * KlanWalutaManager (przechowuje szablon "smoczego odlamka" z klany-odlamek.yml).
     */
    public static void setKlanWalutaManager(KlanWalutaManager klanWalutaManager) {
        if (instance != null) {
            instance.klanWalutaManager = klanWalutaManager;
        }
    }

    /**
     * Wywolywane z /adminklan reload (przez boniec.dev.inventory.Main), po tym jak
     * host na nowo wczyta plik klany-config.yml z dysku.
     */
    public static void reloadConfig(FileConfiguration nowyKonfig) {
        if (instance != null) {
            instance.klanyConfig = nowyKonfig;
        }
    }

    public static Main get() {
        return instance;
    }

    /** Po wylaczeniu pluginu — zeby stara ekspansja PAPI nie trzymala martwego KlanManagera. */
    public static void wyczysc() {
        instance = null;
    }

    /** Odpowiednik dawnego JavaPlugin#getConfig() - teraz zwraca klany-config.yml. */
    public FileConfiguration getConfig() {
        return klanyConfig;
    }

    public KlanManager getKlanManager() {
        return klanManager;
    }

    /** Zwraca menedzer waluty klanowej (szablon "smoczego odlamka" z klany-odlamek.yml). */
    public KlanWalutaManager getKlanWalutaManager() {
        return klanWalutaManager;
    }
}
