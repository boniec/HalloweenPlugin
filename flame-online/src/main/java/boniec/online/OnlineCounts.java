package boniec.online;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class OnlineCounts {

    private final JavaPlugin plugin;
    private final Map<String, Integer> remote = new ConcurrentHashMap<>();
    private int mnoznik = 3;

    OnlineCounts(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void reload() {
        this.mnoznik = Math.max(1, plugin.getConfig().getInt("mnoznik", 3));
    }

    int mnoznik() {
        return mnoznik;
    }

    void start() {
        reload();
        long ticks = Math.max(20L, plugin.getConfig().getInt("ping-sekundy", 3) * 20L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pingRemote, 20L, ticks);
    }

    private String thisServer() {
        String ten = plugin.getConfig().getString("this-server", "skypvp");
        return ten == null || ten.isBlank() ? "skypvp" : ten.trim().toLowerCase(Locale.ROOT);
    }

    private ConfigurationSection serwery() {
        return plugin.getConfig().getConfigurationSection("serwery");
    }

    private ConfigurationSection sekcja(String id) {
        ConfigurationSection all = serwery();
        if (all == null || id == null) return null;
        ConfigurationSection sec = all.getConfigurationSection(id);
        if (sec != null) return sec;
        for (String k : all.getKeys(false)) {
            if (k.equalsIgnoreCase(id)) return all.getConfigurationSection(k);
        }
        return null;
    }

    private boolean toTenSerwer(String id) {
        if (id == null) return false;
        String key = id.toLowerCase(Locale.ROOT);
        if (key.equals("this") || key.equals("tryb") || key.equals("local") || key.equals("all")) {
            return true;
        }
        return thisServer().equals(key);
    }

    int real(String id) {
        if (id == null) return 0;
        String key = id.toLowerCase(Locale.ROOT);
        if (key.equals("all")) return realAll();
        if (toTenSerwer(key)) {
            return Bukkit.getOnlinePlayers().size();
        }
        ConfigurationSection sec = sekcja(key);
        if (sec != null && sec.getBoolean("local", false)) {
            return Bukkit.getOnlinePlayers().size();
        }
        return Math.max(0, remote.getOrDefault(key, 0));
    }

    boolean mnoznikDla(String id) {
        if (id != null && (id.equalsIgnoreCase("this") || id.equalsIgnoreCase("tryb")
                || id.equalsIgnoreCase("local") || id.equalsIgnoreCase("all"))) {
            return mnoznikDla(thisServer());
        }
        ConfigurationSection sec = sekcja(id);
        if (sec == null) {
            return id != null && id.equalsIgnoreCase("skypvp");
        }
        return sec.getBoolean("mnoznik", id != null && id.equalsIgnoreCase("skypvp"));
    }

    int shown(String id) {
        int n = real(id);
        return mnoznikDla(id) ? n * mnoznik : n;
    }

    int realAll() {
        int sum = Bukkit.getOnlinePlayers().size();
        ConfigurationSection all = serwery();
        if (all == null) return sum;
        String ten = thisServer();
        for (String id : all.getKeys(false)) {
            if (id.equalsIgnoreCase(ten)) continue;
            ConfigurationSection sec = all.getConfigurationSection(id);
            if (sec != null && sec.getBoolean("local", false)) continue;
            sum += Math.max(0, remote.getOrDefault(id.toLowerCase(Locale.ROOT), 0));
        }
        return sum;
    }

    int shownAll() {
        int sum = shown("this");
        ConfigurationSection all = serwery();
        if (all == null) {
            return Math.max(sum, Bukkit.getOnlinePlayers().size() * (mnoznikDla("this") ? mnoznik : 1));
        }
        String ten = thisServer();
        for (String id : all.getKeys(false)) {
            if (id.equalsIgnoreCase(ten)) continue;
            ConfigurationSection sec = all.getConfigurationSection(id);
            if (sec != null && sec.getBoolean("local", false)) continue;
            sum += shown(id);
        }
        if (sum <= 0 && !Bukkit.getOnlinePlayers().isEmpty()) {
            return shown("this");
        }
        return sum;
    }

    private void pingRemote() {
        ConfigurationSection all = serwery();
        if (all == null) return;
        String ten = thisServer();
        for (String id : all.getKeys(false)) {
            if (id.equalsIgnoreCase(ten)) continue;
            ConfigurationSection sec = all.getConfigurationSection(id);
            if (sec == null || sec.getBoolean("local", false)) continue;
            String host = sec.getString("host", "");
            int port = sec.getInt("port", 25565);
            if (host == null || host.isEmpty()) continue;
            int n = ServerPinger.pingOnline(host, port, 1500);
            if (n >= 0) {
                remote.put(id.toLowerCase(Locale.ROOT), n);
            }
        }
    }
}
