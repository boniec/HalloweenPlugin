package boniec.online;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /chat on | /chat off — wylacza czat na tym serwerze. */
public final class ChatLock implements Listener, CommandExecutor, TabCompleter {

    private final FlameOnlinePlugin plugin;
    private boolean wylaczony;

    public ChatLock(FlameOnlinePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!wylaczony) return;
        Player p = event.getPlayer();
        if (p.hasPermission("flameonline.chat.bypass") || p.hasPermission("flameonline.admin")) return;
        event.setCancelled(true);
        p.sendMessage(msg("chat.wylaczony-info", "&cCzat jest obecnie wylaczony."));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("flameonline.chat") && !sender.hasPermission("flameonline.admin")) {
            sender.sendMessage(msg("chat.brak-permisji", "&cBrak permisji."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(msg("chat.uzycie", "&7Uzycie: &f/chat on &7| &f/chat off"));
            return true;
        }
        String kto = sender instanceof Player p ? p.getName() : "Konsola";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "off", "wylacz", "disable" -> {
                if (wylaczony) {
                    sender.sendMessage(msg("chat.juz-wylaczony", "&eCzat jest juz wylaczony."));
                    return true;
                }
                wylaczony = true;
                Bukkit.broadcastMessage(msg("chat.wylaczono", "&cCzat zostal wylaczony przez &f%gracz%&c.",
                        "%gracz%", kto));
            }
            case "on", "wlacz", "enable" -> {
                if (!wylaczony) {
                    sender.sendMessage(msg("chat.juz-wlaczony", "&eCzat jest juz wlaczony."));
                    return true;
                }
                wylaczony = false;
                Bukkit.broadcastMessage(msg("chat.wlaczono", "&aCzat zostal wlaczony przez &f%gracz%&a.",
                        "%gracz%", kto));
            }
            default -> sender.sendMessage(msg("chat.uzycie", "&7Uzycie: &f/chat on &7| &f/chat off"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String cur = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("on", "off")) {
                if (s.startsWith(cur)) out.add(s);
            }
        }
        return out;
    }

    private String msg(String path, String def, String... ph) {
        String raw = plugin.getConfig().getString(path, def);
        if (raw == null) raw = def;
        for (int i = 0; i + 1 < ph.length; i += 2) {
            raw = raw.replace(ph[i], ph[i + 1] == null ? "" : ph[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
