package boniec.boss;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GoblinDropManager {

    public static final double SZANSA_DOMYSLNA = 10.0;
    public static final double SZANSA_MIN = 0.0;
    public static final double SZANSA_MAX = 100.0;

    public static final class Wpis {
        private final ItemStack item;
        private double szansa;

        public Wpis(ItemStack item, double szansa) {
            this.item = item;
            this.szansa = clamp(szansa);
        }

        public ItemStack getItem() { return item; }
        public double getSzansa() { return szansa; }
        public void setSzansa(double szansa) { this.szansa = clamp(szansa); }
    }

    private final JavaPlugin plugin;
    private final File plik;
    private final List<Wpis> wpisy = new ArrayList<>();

    public GoblinDropManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "goblindrop.yml");
        wczytaj();
    }

    public List<Wpis> getWpisy() {
        return wpisy;
    }

    public void ustawWpisy(List<Wpis> nowe) {
        wpisy.clear();
        wpisy.addAll(nowe);
        zapisz();
    }

    public void wczytaj() {
        wpisy.clear();
        if (!plik.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(plik);
        ConfigurationSection sekcja = cfg.getConfigurationSection("drop");
        if (sekcja == null) return;

        for (String klucz : sekcja.getKeys(false)) {
            ConfigurationSection wpis = sekcja.getConfigurationSection(klucz);
            if (wpis == null) continue;
            ItemStack item = wpis.getItemStack("item");
            if (item == null || item.getType().isAir()) continue;
            wpisy.add(new Wpis(item, wpis.getDouble("szansa", SZANSA_DOMYSLNA)));
        }
    }

    public void zapisz() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (int i = 0; i < wpisy.size(); i++) {
            Wpis w = wpisy.get(i);
            cfg.set("drop." + i + ".item", w.getItem());
            cfg.set("drop." + i + ".szansa", w.getSzansa());
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(plik);
        } catch (IOException e) {
            plugin.getLogger().warning("[Boss] Nie udalo sie zapisac goblindrop.yml: " + e.getMessage());
        }
    }

    public List<ItemStack> losuj() {
        List<ItemStack> wynik = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Wpis w : wpisy) {
            if (w.getSzansa() <= 0) continue;
            if (rnd.nextDouble(100.0) < w.getSzansa()) {
                wynik.add(w.getItem().clone());
            }
        }
        return wynik;
    }

    public int daj(Player gracz) {
        if (gracz == null || !gracz.isOnline()) return 0;
        List<ItemStack> dropy = losuj();
        for (ItemStack item : dropy) {
            dajGraczowi(gracz, item);
        }
        return dropy.size();
    }

    public static void dajGraczowi(Player gracz, ItemStack item) {
        if (gracz == null || item == null || item.getType().isAir()) return;
        for (ItemStack reszta : gracz.getInventory().addItem(item).values()) {
            gracz.getWorld().dropItemNaturally(gracz.getLocation(), reszta);
        }
    }

    private static double clamp(double v) {
        return Math.max(SZANSA_MIN, Math.min(SZANSA_MAX, v));
    }
}