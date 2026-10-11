package boniec.szaman.core;

import boniec.code.core.CoreDatabase;
import boniec.code.core.UstawieniaDatabase;
import boniec.szaman.SzamanPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Waluta Szamana - "Fragment Galaktyki" (dawniej "Odlamek Szamana"). To fizyczny
 * przedmiot ustawiany raz przez admina (/aszaman ustawodlamek), a gracz placi nim
 * w /szaman za kolejne poziomy perkow.
 * <p>
 * Zastepuje dawny KamienManager (kamienie Sily/Odpornosci/Zrecznosci/Inteligencji
 * zniknely razem z losowaniem perkow). Dane trzymane w waluta.yml; przy pierwszym
 * starcie po aktualizacji szablon waluty jest automatycznie przenoszony ze starego
 * pliku kamienie.yml (klucz "odlamek"), wiec admin nie musi ustawiac go ponownie.
 */
public class WalutaManager {

    private final SzamanPlugin plugin;
    private final File plik;
    private final File staryPlik;

    private ItemStack odlamek;

    public WalutaManager(SzamanPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "waluta.yml");
        this.staryPlik = new File(plugin.getDataFolder(), "kamienie.yml");
    }

    public void wczytaj() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (plik.exists()) {
            odlamek = YamlConfiguration.loadConfiguration(plik).getItemStack("odlamek");
            if (odlamek != null) {
                UstawieniaDatabase.setKv("szaman", "odlamek", CoreDatabase.itemToYaml(odlamek));
            }
            plik.renameTo(new File(plugin.getDataFolder(), "waluta.yml.migrated"));
            CoreDatabase.get().oznaczMigracje("yaml:waluta.yml");
            return;
        }
        String raw = UstawieniaDatabase.getKv("szaman", "odlamek", null);
        if (raw != null) {
            odlamek = CoreDatabase.yamlToItem(raw);
            return;
        }

        // Migracja: szablon waluty ze starego kamienie.yml.
        if (staryPlik.exists()) {
            ItemStack stary = YamlConfiguration.loadConfiguration(staryPlik).getItemStack("odlamek");
            if (stary != null) {
                odlamek = stary;
                zapisz();
                plugin.getLogger().info("Przeniesiono szablon waluty Szamana z kamienie.yml do waluta.yml.");
            }
        }
    }

    public synchronized void zapisz() {
        UstawieniaDatabase.setKv("szaman", "odlamek", CoreDatabase.itemToYaml(odlamek));
    }

    public ItemStack getOdlamek() {
        return odlamek == null ? null : odlamek.clone();
    }

    public void ustawOdlamek(ItemStack wzor) {
        ItemStack kopia = wzor.clone();
        kopia.setAmount(1);
        this.odlamek = kopia;
        zapisz();
    }
}
