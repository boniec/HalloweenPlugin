package boniec.klany.core;

import boniec.code.core.CoreDatabase;
import boniec.code.core.UstawieniaDatabase;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Zarzadza szablonem przedmiotu "Smocze Odlamki" (waluta klanowa).
 * Ustawiany raz przez admina (/adminklan ustawodlamek) z pelnym NBT,
 * zapisywany w data.db (kv klan/odlamek).
 */
public class KlanWalutaManager {

    private final JavaPlugin plugin;
    private final File plik;

    private ItemStack odlamekTemplate;
    private int ilosc = 0;

    public KlanWalutaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "klany-odlamek.yml");
    }

    public void wczytaj() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (plik.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(plik);
            odlamekTemplate = cfg.getItemStack("odlamek");
            ilosc = cfg.getInt("ilosc", 0);
            zapisz();
            plik.renameTo(new File(plugin.getDataFolder(), "klany-odlamek.yml.migrated"));
            return;
        }
        odlamekTemplate = CoreDatabase.yamlToItem(UstawieniaDatabase.getKv("klan", "odlamek", null));
        try {
            ilosc = Integer.parseInt(CoreDatabase.get().getKv("klan", "ilosc", "0"));
        } catch (NumberFormatException e) {
            ilosc = 0;
        }
    }

    public synchronized void zapisz() {
        UstawieniaDatabase.setKv("klan", "odlamek", CoreDatabase.itemToYaml(odlamekTemplate));
        CoreDatabase.get().setKv("klan", "ilosc", String.valueOf(ilosc));
    }

    /** Ustawia szablon odlamka z pelnym NBT (trzymaj item w rece). */
    public void ustawOdlamek(ItemStack wzor) {
        ItemStack kopia = wzor.clone();
        kopia.setAmount(1);
        this.odlamekTemplate = kopia;
        zapisz();
    }

    /** Zwraca klone szablonu odlamka (lub null jesli nie ustawiony). */
    public ItemStack getOdlamekTemplate() {
        return odlamekTemplate == null ? null : odlamekTemplate.clone();
    }

    /** Czy szablon odlamka zostal ustawiony? */
    public boolean isUstawiony() {
        return odlamekTemplate != null;
    }

    /** Ustawia ilosc odlamkow (gdy admin ustawia recznie). */
    public void setIlosc(int ilosc) {
        this.ilosc = ilosc;
        zapisz();
    }

    public int getIlosc() {
        return ilosc;
    }

    /** Dodaje odlamki do puli. */
    public void dodaj(int ile) {
        this.ilosc += ile;
        zapisz();
    }

    /** Odejmuje odlamki. Zwraca false jesli brak srodkow. */
    public boolean odejmij(int ile) {
        if (ilosc < ile) return false;
        ilosc -= ile;
        zapisz();
        return true;
    }
}
