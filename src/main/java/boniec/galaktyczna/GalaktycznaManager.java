package boniec.galaktyczna;

import boniec.klany.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class GalaktycznaManager {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public GalaktycznaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "galaktyczna.yml");
        if (!file.exists()) {
            plugin.saveResource("galaktyczna.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        InputStream def = plugin.getResource("galaktyczna.yml");
        if (def != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
    }

    public int kosztOdlamkow() {
        return Math.max(1, cfg.getInt("koszt-odlamkow", 70));
    }

    public String skrzynkaId() {
        return cfg.getString("skrzynka-id", "galaktyczna").toLowerCase();
    }

    public int iloscSkrzyn() {
        return Math.max(1, cfg.getInt("ilosc-skrzyn", 1));
    }

    public String msg(String path, String def) {
        return Msg.color(cfg.getString("messages." + path, def));
    }

    public String prefix() {
        return msg("prefix", "&8[&dGalaktyczna&8] ");
    }

    public FileConfiguration cfg() {
        return cfg;
    }

    public Material material(String path, Material def) {
        String raw = cfg.getString(path);
        if (raw == null || raw.isBlank()) return def;
        try {
            return Material.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
