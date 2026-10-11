package boniec.klany.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skiny NPC z topek. Na serwerze cracked nick NIE istnieje w Mojangu,
 * wiec bierzemy teksture z profilu gracza (gdy jest online) zamiast /npc skin &lt;nick&gt;.
 */
public class TopNpcUpdater implements Listener {

    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern NPC_ID = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");
    private static final Pattern SKIN_URL = Pattern.compile("\"url\"\\s*:\\s*\"(http[^\"]+)\"");

    private final JavaPlugin plugin;
    private final Map<String, String> ostatniKlucz = new HashMap<>();
    private final Map<String, String[]> cacheTekstur = new HashMap<>();
    private BukkitTask task;
    private boolean dziala;

    public TopNpcUpdater(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        dziala = true;
        zaplanuj(20L * 10);
    }

    public void stop() {
        dziala = false;
        if (task != null) task.cancel();
    }

    public void wyczyscCache() {
        ostatniKlucz.clear();
    }

    public int odswiezTeraz() {
        wyczyscCache();
        return odswiez();
    }

    private ConfigurationSection sekcja() {
        boniec.klany.Main klany = boniec.klany.Main.get();
        if (klany == null || klany.getConfig() == null) return null;
        return klany.getConfig().getConfigurationSection("top-npc");
    }

    private void zaplanuj(long ticks) {
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!dziala) return;
            try {
                odswiez();
            } catch (Exception e) {
                plugin.getLogger().warning("[TopNpc] Blad: " + e.getMessage());
            }
            ConfigurationSection s = sekcja();
            int interwal = s == null ? 300 : Math.max(30, s.getInt("interwal-sekundy", 300));
            zaplanuj(20L * interwal);
        }, ticks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        String nick = event.getPlayer().getName();
        zapiszTeksture(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!dziala) return;
            ConfigurationSection s = sekcja();
            if (s == null || !s.getBoolean("wlaczone", true)) return;
            for (Map<?, ?> wpis : s.getMapList("npc")) {
                Object ph = wpis.get("placeholder");
                if (ph == null) continue;
                String topNick = PlaceholderAPI.setPlaceholders(
                        Bukkit.getOfflinePlayer(new UUID(0L, 0L)), String.valueOf(ph)).trim();
                if (nick.equalsIgnoreCase(oczyscNick(topNick))) {
                    odswiezTeraz();
                    return;
                }
            }
        }, 40L);
    }

    private int odswiez() {
        ConfigurationSection s = sekcja();
        if (s == null) {
            plugin.getLogger().warning("[TopNpc] Brak sekcji top-npc w klany-config.yml.");
            return 0;
        }
        if (!s.getBoolean("wlaczone", true)) return 0;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().warning("[TopNpc] Brak PlaceholderAPI.");
            return 0;
        }

        OfflinePlayer dummy = Bukkit.getOfflinePlayer(new UUID(0L, 0L));
        List<Map<?, ?>> wpisy = s.getMapList("npc");
        if (wpisy.isEmpty()) {
            plugin.getLogger().warning("[TopNpc] top-npc.npc jest puste.");
            return 0;
        }

        int ustawiono = 0;
        for (Map<?, ?> wpis : wpisy) {
            if (wpis.get("id") == null || wpis.get("placeholder") == null) continue;
            String id = String.valueOf(wpis.get("id")).trim();
            String placeholder = String.valueOf(wpis.get("placeholder")).trim();
            if (!NPC_ID.matcher(id).matches()) {
                plugin.getLogger().warning("[TopNpc] Zla nazwa NPC: " + id);
                continue;
            }

            String nick = oczyscNick(PlaceholderAPI.setPlaceholders(dummy, placeholder).trim());
            if (nick.isEmpty() || nick.contains("%") || !NICK.matcher(nick).matches()) {
                plugin.getLogger().warning("[TopNpc] " + placeholder + " = '" + nick + "'");
                continue;
            }

            Player online = znajdzOnline(nick);
            if (online != null) zapiszTeksture(online);

            String[] tex = cacheTekstur.get(nick.toLowerCase(Locale.ROOT));
            String klucz = nick.toLowerCase(Locale.ROOT) + "|" + (tex != null ? tex[0].hashCode() : "brak");
            if (klucz.equals(ostatniKlucz.get(id))) {
                ustawiono++;
                continue;
            }

            boolean ok;
            if (tex != null) {
                ok = ustawSkinTekstura(id, nick, tex[0], tex[1]);
            } else {
                plugin.getLogger().warning("[TopNpc] " + nick
                        + " nie ma skina w cache (cracked / offline). Wejdzie na serwer = podciagnie sie.");
                ok = false;
            }
            if (ok) {
                ostatniKlucz.put(id, klucz);
                ustawiono++;
                plugin.getLogger().info("[TopNpc] " + id + " -> skin " + nick);
            }
        }
        return ustawiono;
    }

    private static String oczyscNick(String nick) {
        return nick.replaceAll("§[0-9a-fk-orx]", "").replaceAll("&[0-9a-fk-orx]", "").trim();
    }

    private static Player znajdzOnline(String nick) {
        Player p = Bukkit.getPlayerExact(nick);
        if (p != null) return p;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getName().equalsIgnoreCase(nick)) return o;
        }
        return null;
    }

    private void zapiszTeksture(Player player) {
        String[] tex = teksturaZProfilu(player);
        if (tex != null) {
            cacheTekstur.put(player.getName().toLowerCase(Locale.ROOT), tex);
        }
    }

    private String[] teksturaZProfilu(Player player) {
        try {
            PlayerProfile profile = player.getPlayerProfile();
            for (ProfileProperty prop : profile.getProperties()) {
                if ("textures".equals(prop.getName()) && prop.getValue() != null && !prop.getValue().isEmpty()) {
                    String sig = prop.getSignature() == null ? "" : prop.getSignature();
                    return new String[]{prop.getValue(), sig};
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TopNpc] Profil " + player.getName() + ": " + e.getMessage());
        }
        return null;
    }

    private boolean ustawSkinTekstura(String npc, String nick, String value, String signature) {
        if (ustawPrzezApi(npc, nick, value, signature)) return true;
        String url = urlZeValue(value);
        if (url != null) {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc skin " + npc + " " + url);
            if (ok) return true;
        }
        plugin.getLogger().warning("[TopNpc] Nie udalo sie ustawic skina NPC " + npc + " dla " + nick);
        return false;
    }

    private String urlZeValue(String value) {
        try {
            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            Matcher m = SKIN_URL.matcher(json);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean ustawPrzezApi(String npcName, String nick, String value, String signature) {
        try {
            Class<?> pluginCl = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin");
            Object api = pluginCl.getMethod("get").invoke(null);
            Object npcManager = api.getClass().getMethod("getNpcManager").invoke(api);
            Object npc = npcManager.getClass().getMethod("getNpc", String.class).invoke(npcManager, npcName);
            if (npc == null) {
                plugin.getLogger().warning("[TopNpc] FancyNpcs nie zna NPC '" + npcName + "' (sprawdz /npc list).");
                return false;
            }
            Class<?> variantCl = Class.forName("de.oliver.fancynpcs.api.skins.SkinData$SkinVariant");
            Object auto = Enum.valueOf(variantCl.asSubclass(Enum.class), "AUTO");
            Class<?> skinCl = Class.forName("de.oliver.fancynpcs.api.skins.SkinData");
            Object skin;
            try {
                skin = skinCl.getConstructor(String.class, variantCl, String.class, String.class)
                        .newInstance(nick, auto, value, signature);
            } catch (NoSuchMethodException e) {
                skin = skinCl.getConstructor(String.class, String.class, String.class)
                        .newInstance(nick, value, signature);
            }
            Object data = npc.getClass().getMethod("getData").invoke(npc);
            boolean ustawione = false;
            for (String metoda : List.of("setSkinData", "setSkin")) {
                try {
                    data.getClass().getMethod(metoda, skinCl).invoke(data, skin);
                    ustawione = true;
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (!ustawione) return false;
            for (String metoda : List.of("updateForAll", "spawnForAll", "refreshForAll")) {
                try {
                    npc.getClass().getMethod(metoda).invoke(npc);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("[TopNpc] FancyNpcs API: " + e.getMessage());
            return false;
        }
    }
}
