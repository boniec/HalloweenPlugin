package boniec.code.core.metin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetinConfig {

    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    public MetinConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "metin.yml");
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Wyszukuje wartość w hierarchii konfiguracji bez nadpisywania pliku na dysku.
     * Kolejność sprawdzania:
     * 1. settings.<name>.<key> (konkretny Metin, np. Podziemia)
     * 2. settings.Metin_Walki.<key> (Twoja domyślna sekcja wzorcowa)
     * 3. Pierwsza znaleziona sekcja w settings (fallback)
     * 4. Wartość domyślna przekazana w parametrze
     */
    private int getHierarchicalInt(String name, String key, int defaultValue) {
        // 1. Sprawdź konkretnego Metina
        if (config.contains("settings." + name + "." + key)) {
            return config.getInt("settings." + name + "." + key);
        }
        // 2. Sprawdź wzorcowy Metin_Walki
        if (config.contains("settings.Metin_Walki." + key)) {
            return config.getInt("settings.Metin_Walki." + key);
        }
        // 3. Fallback do jakiejkolwiek innej skonfigurowanej sekcji
        ConfigurationSection section = config.getConfigurationSection("settings");
        if (section != null) {
            for (String subKey : section.getKeys(false)) {
                if (config.contains("settings." + subKey + "." + key)) {
                    return config.getInt("settings." + subKey + "." + key);
                }
            }
        }
        return defaultValue;
    }

    public void saveSpawnLocation(String name, Location loc) {
        String path = "spawns." + name;
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        save();
    }

    public void removeSpawnLocation(String name) {
        config.set("spawns." + name, null);
        save();
    }

    public Map<String, Location> getSpawnLocations() {
        Map<String, Location> locations = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("spawns");
        if (section == null) return locations;

        for (String key : section.getKeys(false)) {
            String worldName = config.getString("spawns." + key + ".world");
            double x = config.getDouble("spawns." + key + ".x");
            double y = config.getDouble("spawns." + key + ".y");
            double z = config.getDouble("spawns." + key + ".z");

            if (worldName != null && plugin.getServer().getWorld(worldName) != null) {
                Location loc = new Location(plugin.getServer().getWorld(worldName), x, y, z);
                locations.put(key, loc);
            }
        }
        return locations;
    }

    // Settery zapisują dane bezpośrednio do wybranej sekcji
    public void setMetinMaxHp(String name, int hp) {
        config.set("settings." + name + ".maxHp", hp);
        save();
    }

    public int getMetinMaxHp(String name) {
        return getHierarchicalInt(name, "maxHp", 3000);
    }

    public void setMobSpawnInterval(String name, int hpInterval) {
        config.set("settings." + name + ".mobSpawnInterval", hpInterval);
        save();
    }

    public int getMobSpawnInterval(String name) {
        return getHierarchicalInt(name, "mobSpawnInterval", 200);
    }

    public void setMobHp(String name, int hp) {
        config.set("settings." + name + ".mobHp", hp);
        save();
    }

    public int getMobHp(String name) {
        return getHierarchicalInt(name, "mobHp", 20);
    }

    public void setRespawnTime(String name, int seconds) {
        config.set("settings." + name + ".respawnTime", seconds);
        save();
    }

    public int getRespawnTime(String name) {
        return getHierarchicalInt(name, "respawnTime", 300);
    }

    @SuppressWarnings("unchecked")
    public void addDrop(String name, ItemStack item, double chance) {
        String path = "drops." + name;
        List<Map<String, Object>> drops = new ArrayList<>();

        if (config.contains(path)) {
            List<?> existing = config.getList(path);
            if (existing != null) {
                for (Object obj : existing) {
                    if (obj instanceof Map) {
                        drops.add((Map<String, Object>) obj);
                    }
                }
            }
        }

        Map<String, Object> dropEntry = new HashMap<>();
        dropEntry.put("item", item.clone());
        dropEntry.put("chance", chance);
        drops.add(dropEntry);

        config.set(path, drops);
        save();
    }

    public void clearDrops(String name) {
        config.set("drops." + name, null);
        save();
    }

    @SuppressWarnings("unchecked")
    public List<MetinDrop> getDrops(String name) {
        List<MetinDrop> drops = new ArrayList<>();
        String path = "drops." + name;

        // Jeśli dany Metin nie ma zdefiniowanego dropu, pobierz drop z sekcji "Metin_Walki"
        if (!config.contains(path)) {
            path = "drops.Metin_Walki";
        }
        // Jeżeli Metin_Walki też nie ma, pobierz pierwszy lepszy skonfigurowany drop
        if (!config.contains(path)) {
            ConfigurationSection section = config.getConfigurationSection("drops");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    path = "drops." + key;
                    break;
                }
            }
        }

        if (!config.contains(path)) return drops;

        List<?> list = config.getList(path);
        if (list == null) return drops;

        for (Object obj : list) {
            if (obj instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) obj;
                ItemStack item = null;

                if (map.containsKey("item")) {
                    item = (ItemStack) map.get("item");
                }
                else if (map.containsKey("material")) {
                    String materialStr = (String) map.get("material");
                    int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
                    String displayName = map.containsKey("name") ? (String) map.get("name") : null;
                    try {
                        Material mat = Material.valueOf(materialStr);
                        item = new ItemStack(mat, amount);
                        if (displayName != null) {
                            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
                                item.setItemMeta(meta);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;

                if (item != null) {
                    drops.add(new MetinDrop(item, chance));
                }
            }
        }
        return drops;
    }

    public FileConfiguration getConfig() {
        return config;
    }
}