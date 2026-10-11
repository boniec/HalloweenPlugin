package boniec.spawn;

import boniec.code.core.CoreDatabase;
import boniec.code.core.UstawieniaDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;

public final class SpawnManager {

    private final Plugin plugin;
    private Location spawn;

    public SpawnManager(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        spawn = null;
        zmigrujYaml();
        spawn = wczytaj();
        if (spawn == null) {
            String worldName = UstawieniaDatabase.getKv("spawn", "world", null);
            if (worldName != null && !worldName.isEmpty()) {
                plugin.getLogger().warning("[Spawn] Swiat '" + worldName + "' nie istnieje.");
            }
        }
    }

    /** Spawn z ustawienia.db (null gdy nie ustawiony albo swiat nie istnieje). */
    public static Location wczytaj() {
        String worldName = UstawieniaDatabase.getKv("spawn", "world", null);
        if (worldName == null || worldName.isEmpty()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                Double.parseDouble(UstawieniaDatabase.getKv("spawn", "x", "0")),
                Double.parseDouble(UstawieniaDatabase.getKv("spawn", "y", "64")),
                Double.parseDouble(UstawieniaDatabase.getKv("spawn", "z", "0")),
                Float.parseFloat(UstawieniaDatabase.getKv("spawn", "yaw", "0")),
                Float.parseFloat(UstawieniaDatabase.getKv("spawn", "pitch", "0")));
    }

    private void zmigrujYaml() {
        if (CoreDatabase.get().juzZmigrowane("yaml:spawn.yml")) return;
        File file = new File(plugin.getDataFolder(), "spawn.yml");
        if (file.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String worldName = cfg.getString("world");
            if (worldName != null && !worldName.isEmpty()) {
                UstawieniaDatabase.setKv("spawn", "world", worldName);
                UstawieniaDatabase.setKv("spawn", "x", String.valueOf(cfg.getDouble("x")));
                UstawieniaDatabase.setKv("spawn", "y", String.valueOf(cfg.getDouble("y")));
                UstawieniaDatabase.setKv("spawn", "z", String.valueOf(cfg.getDouble("z")));
                UstawieniaDatabase.setKv("spawn", "yaw", String.valueOf(cfg.getDouble("yaw")));
                UstawieniaDatabase.setKv("spawn", "pitch", String.valueOf(cfg.getDouble("pitch")));
            }
            file.renameTo(new File(plugin.getDataFolder(), "spawn.yml.migrated"));
        }
        CoreDatabase.get().oznaczMigracje("yaml:spawn.yml");
    }

    public void setSpawn(Location loc) {
        this.spawn = loc.clone();
        UstawieniaDatabase.setKv("spawn", "world", loc.getWorld().getName());
        UstawieniaDatabase.setKv("spawn", "x", String.valueOf(loc.getX()));
        UstawieniaDatabase.setKv("spawn", "y", String.valueOf(loc.getY()));
        UstawieniaDatabase.setKv("spawn", "z", String.valueOf(loc.getZ()));
        UstawieniaDatabase.setKv("spawn", "yaw", String.valueOf(loc.getYaw()));
        UstawieniaDatabase.setKv("spawn", "pitch", String.valueOf(loc.getPitch()));
    }

    public Location getSpawn() {
        return spawn == null ? null : spawn.clone();
    }

    public boolean teleport(Player player) {
        Location loc = getSpawn();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return player.teleport(loc);
    }
}
