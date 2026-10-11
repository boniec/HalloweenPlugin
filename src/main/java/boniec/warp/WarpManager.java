package boniec.warp;

import boniec.code.core.CoreDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

public class WarpManager {

    private final Plugin plugin;
    private final Map<String, Location> warps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> displayNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public WarpManager(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        warps.clear();
        displayNames.clear();
        zmigrujYaml();
        try (PreparedStatement ps = boniec.code.core.UstawieniaDatabase.connection().prepareStatement(
                "SELECT nazwa, world, x, y, z, yaw, pitch FROM warps");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("nazwa");
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) {
                    plugin.getLogger().warning("[Warp] Pomijam warp '" + key + "' - swiat nie istnieje.");
                    continue;
                }
                Location loc = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        (float) rs.getDouble("yaw"), (float) rs.getDouble("pitch"));
                warps.put(key, loc);
                displayNames.put(key, key);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Warp] Odczyt SQLite", e);
        }
    }

    private void zmigrujYaml() {
        if (CoreDatabase.get().juzZmigrowane("yaml:warps.yml")) return;
        File file = new File(plugin.getDataFolder(), "warps.yml");
        if (file.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = cfg.getConfigurationSection("warps");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection w = section.getConfigurationSection(key);
                    if (w == null) continue;
                    World world = Bukkit.getWorld(w.getString("world", ""));
                    if (world == null) continue;
                    Location loc = new Location(world, w.getDouble("x"), w.getDouble("y"), w.getDouble("z"),
                            (float) w.getDouble("yaw"), (float) w.getDouble("pitch"));
                    warps.put(key, loc);
                    displayNames.put(key, w.getString("display-name", key));
                    zapiszWiersz(key, loc);
                }
            }
            file.renameTo(new File(plugin.getDataFolder(), "warps.yml.migrated"));
        }
        CoreDatabase.get().oznaczMigracje("yaml:warps.yml");
    }

    private void zapiszWiersz(String name, Location loc) {
        try (PreparedStatement ps = boniec.code.core.UstawieniaDatabase.connection().prepareStatement(
                "INSERT INTO warps (nazwa, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(nazwa) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch")) {
            ps.setString(1, name);
            ps.setString(2, loc.getWorld().getName());
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setDouble(6, loc.getYaw());
            ps.setDouble(7, loc.getPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Warp] Zapis SQLite", e);
        }
    }

    public void createWarp(String name, Location loc) {
        warps.put(name, loc.clone());
        displayNames.put(name, name);
        zapiszWiersz(name, loc);
    }

    public boolean deleteWarp(String name) {
        if (!warps.containsKey(name)) return false;
        warps.remove(name);
        displayNames.remove(name);
        try (PreparedStatement ps = boniec.code.core.UstawieniaDatabase.connection().prepareStatement("DELETE FROM warps WHERE nazwa = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Warp] Usun SQLite", e);
        }
        return true;
    }

    public boolean exists(String name) {
        return warps.containsKey(name);
    }

    public Location getWarp(String name) {
        Location loc = warps.get(name);
        return loc == null ? null : loc.clone();
    }

    public List<String> getWarpNames() {
        return new ArrayList<>(displayNames.values());
    }

    public int getWarpCount() {
        return warps.size();
    }
}
