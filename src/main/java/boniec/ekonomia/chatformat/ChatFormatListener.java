package boniec.ekonomia.chatformat;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format czatu z config.yml (chat-format), w tym %rel_...% per odbiorca.
 * Wylacza join/quit i ogłoszenia advancements.
 */
public class ChatFormatListener implements Listener {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final JavaPlugin plugin;

    public ChatFormatListener(JavaPlugin plugin) {
        this.plugin = plugin;
        for (World w : Bukkit.getWorlds()) wylaczAdvancements(w);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("chat-format.enabled", true)) return;

        Player player = event.getPlayer();
        String format = cfg.getString("chat-format.format",
                "%rel_klany_rel_klan_tag% %luckperms_prefix%&f%player% &8» &f%message%");
        String rawMessage = event.getMessage();
        String perm = cfg.getString("chat-format.color-permission", "chat.color");
        if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) {
            rawMessage = kolor(rawMessage);
        }

        Set<Player> recipients = new HashSet<>(event.getRecipients());
        event.getRecipients().clear();
        event.setCancelled(true);

        final String message = rawMessage;
        final String fmt = format;
        Bukkit.getScheduler().runTask(plugin, () -> {
            String konsola = zbudujLinie(null, player, message, fmt);
            Bukkit.getConsoleSender().sendMessage(konsola);
            for (Player viewer : recipients) {
                if (viewer == null || !viewer.isOnline()) continue;
                if (!viewer.equals(player)
                        && !boniec.ustawienia.UstawieniaManager.on(viewer, boniec.ustawienia.UstawieniaKlucz.CHAT)) {
                    continue;
                }
                viewer.sendMessage(zbudujLinie(viewer, player, message, fmt));
            }
        });
    }

    private String zbudujLinie(Player viewer, Player nadawca, String message, String format) {
        String f = format
                .replace("%player%", nadawca.getName())
                .replace("%displayname%", nadawca.getDisplayName())
                .replace("%message%", message);
        f = boniec.code.core.OsobyExpansion.podmien(f);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                f = PlaceholderAPI.setPlaceholders(nadawca, f);
                if (viewer != null) {
                    f = PlaceholderAPI.setRelationalPlaceholders(viewer, nadawca, f);
                } else {
                    f = f.replaceAll("%rel_[^%]+%", "");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[ChatFormat] Blad PlaceholderAPI: " + t);
            }
        }
        return kolor(f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        event.message(null);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        wylaczAdvancements(event.getWorld());
    }

    private static void wylaczAdvancements(World world) {
        if (world != null) world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
    }

    private static String kolor(String tekst) {
        if (tekst == null) return "";
        Matcher m = HEX.matcher(tekst);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder hex = new StringBuilder("\u00A7x");
            for (char c : m.group(1).toCharArray()) {
                hex.append('\u00A7').append(c);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(hex.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
