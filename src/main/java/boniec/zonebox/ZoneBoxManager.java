package boniec.zonebox;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZoneBoxManager {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration cfg;
    private final List<String> regiony = new ArrayList<>();
    private final List<String> genRegiony = new ArrayList<>();
    private final List<String> wylaczone = new ArrayList<>();
    private final List<Material> bloki = new ArrayList<>();

    public ZoneBoxManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "azonebox.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        regiony.clear();
        genRegiony.clear();
        wylaczone.clear();
        bloki.clear();
        if (!cfg.contains("bez-zonebox")) {
            cfg.set("bez-zonebox", new ArrayList<>(List.of("spawnpvp")));
            try {
                cfg.save(file);
            } catch (IOException ignored) {
            }
        }
        for (String r : cfg.getStringList("bez-zonebox")) {
            if (r != null && !r.isBlank()) wylaczone.add(r.toLowerCase(Locale.ROOT));
        }
        for (String r : cfg.getStringList("regiony")) {
            if (r != null && !r.isBlank()) regiony.add(r.toLowerCase(Locale.ROOT));
        }
        if (!cfg.contains("tylko-generatory")) {
            genRegiony.add("spawn");
            cfg.set("tylko-generatory", new ArrayList<>(genRegiony));
            try {
                cfg.save(file);
            } catch (IOException ignored) {
            }
        } else {
            for (String r : cfg.getStringList("tylko-generatory")) {
                if (r != null && !r.isBlank()) genRegiony.add(r.toLowerCase(Locale.ROOT));
            }
        }
        for (String b : cfg.getStringList("bloki")) {
            Material m = Material.matchMaterial(b == null ? "" : b);
            if (m != null && m.isBlock()) bloki.add(m);
        }
    }

    private void save() {
        List<String> mats = new ArrayList<>();
        for (Material m : bloki) mats.add(m.name());
        cfg.set("regiony", new ArrayList<>(regiony));
        cfg.set("tylko-generatory", new ArrayList<>(genRegiony));
        cfg.set("bez-zonebox", new ArrayList<>(wylaczone));
        cfg.set("bloki", mats);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie zapisano azonebox.yml: " + e.getMessage());
        }
    }

    public boolean addBlock(Material m) {
        if (m == null || !m.isBlock()) return false;
        if (bloki.contains(m)) return false;
        bloki.add(m);
        save();
        return true;
    }

    public boolean removeBlock(Material m) {
        boolean ok = bloki.remove(m);
        if (ok) save();
        return ok;
    }

    public boolean addRegion(String id) {
        String n = id.toLowerCase(Locale.ROOT);
        if (regiony.contains(n)) return false;
        regiony.add(n);
        save();
        return true;
    }

    public boolean removeRegion(String id) {
        boolean ok = regiony.remove(id.toLowerCase(Locale.ROOT));
        if (ok) save();
        return ok;
    }

    public List<String> regiony() {
        return regiony;
    }

    public List<Material> bloki() {
        return bloki;
    }

    public boolean addGenRegion(String id) {
        String n = id.toLowerCase(Locale.ROOT);
        if (genRegiony.contains(n)) return false;
        genRegiony.add(n);
        save();
        return true;
    }

    public boolean removeGenRegion(String id) {
        boolean ok = genRegiony.remove(id.toLowerCase(Locale.ROOT));
        if (ok) save();
        return ok;
    }

    public List<String> genRegiony() {
        return genRegiony;
    }

    public boolean addWylaczony(String id) {
        String n = id.toLowerCase(Locale.ROOT);
        if (wylaczone.contains(n)) return false;
        wylaczone.add(n);
        save();
        return true;
    }

    public boolean removeWylaczony(String id) {
        boolean ok = wylaczone.remove(id.toLowerCase(Locale.ROOT));
        if (ok) save();
        return ok;
    }

    public List<String> wylaczone() {
        return wylaczone;
    }

    /** Regiony bez ZoneBoxa (np. spawnpvp): zadnych wyjatkow na stawianie/kopanie. */
    public boolean wWylaczonym(Location loc) {
        if (loc == null || wylaczone.isEmpty()) return false;
        return boniec.code.core.WorldGuardHook.isInAnyRegion(loc, wylaczone.toArray(String[]::new));
    }

    public boolean wStrefie(Location loc) {
        if (loc == null || regiony.isEmpty() || wWylaczonym(loc)) return false;
        return boniec.code.core.WorldGuardHook.isInAnyRegion(loc, regiony.toArray(String[]::new));
    }

    /** Spawn itd.: wolno tylko kopac generatory, zero stawiania. */
    public boolean wStrefieGeneratorow(Location loc) {
        if (loc == null || genRegiony.isEmpty()) return false;
        return boniec.code.core.WorldGuardHook.isInAnyRegion(loc, genRegiony.toArray(String[]::new));
    }

    public boolean dozwolony(Material m) {
        return m != null && bloki.contains(m);
    }
}
