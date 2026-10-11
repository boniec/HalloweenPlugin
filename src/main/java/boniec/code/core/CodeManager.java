package boniec.code.core;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CodeManager {

    private final JavaPlugin plugin;
    private File codesFile;
    private FileConfiguration codesConfig;

    private File dataFile;
    private FileConfiguration dataConfig;

    /** uuid|KOD — zrodlo prawdy, zeby spam /kod nie przechodzil 2x. */
    private final Set<String> claimed = ConcurrentHashMap.newKeySet();

    public CodeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        createConfigs();
    }

    private void createConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        codesFile = new File(plugin.getDataFolder(), "kody.yml");
        if (!codesFile.exists()) {
            try {
                codesFile.createNewFile();
                codesConfig = YamlConfiguration.loadConfiguration(codesFile);

                // Przykładowy kod startowy z wielolinijkowym broadcastem
                codesConfig.set("codes.START.required-seconds", 3600);
                codesConfig.set("codes.START.require-discord", true);

                List<String> defaultBroadcast = new ArrayList<>();
                defaultBroadcast.add("&8&m----------------------------------------");
                defaultBroadcast.add("&6&lKOD &8» &7Gracz &e%player% &7odebral kod &eSTART&7!");
                defaultBroadcast.add("&6&lKOD &8» &7Zgarnal darmowe przedmioty na start!");
                defaultBroadcast.add("&8&m----------------------------------------");
                codesConfig.set("codes.START.broadcast", defaultBroadcast);

                List<String> cmds = new ArrayList<>();
                cmds.add("give %player% diamond 5");
                cmds.add("xp add %player% 100");
                codesConfig.set("codes.START.commands", cmds);

                codesConfig.save(codesFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Nie udalo sie utworzyc kody.yml!");
            }
        }
        codesConfig = YamlConfiguration.loadConfiguration(codesFile);

        dataFile = new File(plugin.getDataFolder(), "kody-data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie udalo sie utworzyc kody-data.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        rebuildClaimedCache();
    }

    public void reloadConfigs() {
        codesConfig = YamlConfiguration.loadConfiguration(codesFile);
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        rebuildClaimedCache();
    }

    public void saveConfigs() {
        saveCodesFile();
        saveDataFile();
    }

    private void saveCodesFile() {
        try {
            codesConfig.save(codesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udalo sie zapisac kody.yml: " + e.getMessage());
        }
    }

    private void saveDataFile() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udalo sie zapisac kody-data.yml: " + e.getMessage());
        }
    }

    private static String claimKey(UUID uuid, String code) {
        return uuid + "|" + code.toUpperCase(Locale.ROOT);
    }

    private void rebuildClaimedCache() {
        claimed.clear();
        var used = dataConfig.getConfigurationSection("used");
        if (used == null) return;
        for (String code : used.getKeys(false)) {
            for (String raw : readUsedList(code)) {
                try {
                    claimed.add(claimKey(UUID.fromString(raw), code));
                } catch (IllegalArgumentException ignored) {
                    claimed.add(raw.toLowerCase(Locale.ROOT) + "|" + code.toUpperCase(Locale.ROOT));
                }
            }
        }
    }

    private List<String> readUsedList(String code) {
        String path = "used." + code.toUpperCase(Locale.ROOT);
        if (dataConfig.isList(path)) return dataConfig.getStringList(path);
        if (dataConfig.isString(path)) {
            String one = dataConfig.getString(path);
            return one == null || one.isBlank() ? List.of() : List.of(one);
        }
        var sec = dataConfig.getConfigurationSection(path);
        if (sec != null) return new ArrayList<>(sec.getKeys(false));
        return List.of();
    }

    public boolean codeExists(String code) {
        return codesConfig.contains("codes." + code.toUpperCase());
    }

    public void createCode(String code, int requiredSeconds) {
        String path = "codes." + code.toUpperCase(Locale.ROOT);
        codesConfig.set(path + ".required-seconds", requiredSeconds);
        if (code.toUpperCase(Locale.ROOT).equals("START")) {
            codesConfig.set(path + ".require-discord", true);
        }

        List<String> defaultBroadcast = new ArrayList<>();
        defaultBroadcast.add("&6&lKOD &8» &7Gracz &e%player% &7odebral kod &e" + code.toUpperCase(Locale.ROOT) + "&7!");
        codesConfig.set(path + ".broadcast", defaultBroadcast);

        codesConfig.set(path + ".commands", new ArrayList<String>());
        saveConfigs();
    }

    public boolean deleteCode(String code) {
        String id = code.toUpperCase(Locale.ROOT);
        if (!codeExists(id)) return false;
        codesConfig.set("codes." + id, null);
        dataConfig.set("used." + id, null);
        claimed.removeIf(k -> k.endsWith("|" + id));
        saveConfigs();
        return true;
    }

    public List<String> listCodes() {
        var sec = codesConfig.getConfigurationSection("codes");
        if (sec == null) return List.of();
        Set<String> keys = sec.getKeys(false);
        List<String> out = new ArrayList<>(keys);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public int getUsedCount(String code) {
        return dataConfig.getStringList("used." + code.toUpperCase(Locale.ROOT)).size();
    }

    public void addCommand(String code, String command) {
        String path = "codes." + code.toUpperCase(Locale.ROOT) + ".commands";
        List<String> cmds = new ArrayList<>(codesConfig.getStringList(path));
        cmds.add(command);
        codesConfig.set(path, cmds);
        saveConfigs();
    }

    public boolean removeCommand(String code, int index1) {
        String path = "codes." + code.toUpperCase(Locale.ROOT) + ".commands";
        List<String> cmds = new ArrayList<>(codesConfig.getStringList(path));
        int idx = index1 - 1;
        if (idx < 0 || idx >= cmds.size()) return false;
        cmds.remove(idx);
        codesConfig.set(path, cmds);
        saveConfigs();
        return true;
    }

    public int getRequiredSeconds(String code) {
        return codesConfig.getInt("codes." + code.toUpperCase() + ".required-seconds", 0);
    }

    /** Kod START wymaga polaczonego Discorda z /nagroda, chyba ze w kody.yml jest require-discord: false. */
    public boolean wymagaDiscord(String code) {
        String id = code.toUpperCase(Locale.ROOT);
        String path = "codes." + id + ".require-discord";
        if (codesConfig.contains(path)) return codesConfig.getBoolean(path);
        return "START".equals(id);
    }

    public List<String> getCommands(String code) {
        return codesConfig.getStringList("codes." + code.toUpperCase() + ".commands");
    }

    public List<String> getBroadcast(String code) {
        String path = "codes." + code.toUpperCase() + ".broadcast";
        if (codesConfig.isList(path)) {
            return codesConfig.getStringList(path);
        } else if (codesConfig.isString(path)) {
            // Wsparcie dla starych, jednolinijkowych wpisów
            String singleLine = codesConfig.getString(path);
            if (singleLine != null && !singleLine.isEmpty()) {
                return Collections.singletonList(singleLine);
            }
        }
        return new ArrayList<>();
    }

    public boolean hasUsed(UUID uuid, String code) {
        if (claimed.contains(claimKey(uuid, code))) return true;
        return readUsedList(code).contains(uuid.toString());
    }

    /**
     * Atomowo oznacza kod jako zuzyty. false = juz odebrany (albo wlasnie w tej chwili przez spam).
     */
    public synchronized boolean tryRedeem(UUID uuid, String code) {
        String id = code.toUpperCase(Locale.ROOT);
        String key = claimKey(uuid, id);
        if (!claimed.add(key)) return false;

        List<String> usedPlayers = new ArrayList<>(readUsedList(id));
        String uid = uuid.toString();
        if (!usedPlayers.contains(uid)) {
            usedPlayers.add(uid);
            dataConfig.set("used." + id, usedPlayers);
            saveDataFile();
        }
        return true;
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}