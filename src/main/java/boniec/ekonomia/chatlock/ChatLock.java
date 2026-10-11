package boniec.ekonomia.chatlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /chat clear | off | on */
public final class ChatLock implements Listener, CommandExecutor, TabCompleter {

    private static final String PERM = "chat.admin";
    private static final String BYPASS = "chat.bypass";
    private static final List<String> SUB = List.of("clear", "off", "on");

    private final JavaPlugin plugin;
    private volatile boolean wylaczony;

    public ChatLock(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!wylaczony) return;
        Player p = event.getPlayer();
        if (p.hasPermission(BYPASS) || p.hasPermission(PERM)) return;
        event.setCancelled(true);
        p.sendMessage(msg("chat.lock.wylaczony-info", "&cCzat jest obecnie wylaczony."));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(msg("chat.lock.brak-permisji", "&cNie masz uprawnien!"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(msg("chat.lock.uzycie", "&7Uzycie: &f/chat clear &7| &f/chat off &7| &f/chat on"));
            return true;
        }
        String kto = sender instanceof Player p ? p.getName() : "Konsola";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "clear", "wyczysc", "cc" -> wyczysc(kto);
            case "off", "wylacz", "disable" -> {
                if (wylaczony) {
                    sender.sendMessage(msg("chat.lock.juz-wylaczony", "&eCzat jest juz wylaczony."));
                    return true;
                }
                wylaczony = true;
                Bukkit.broadcastMessage(msg("chat.lock.wylaczono",
                        "&cCzat zostal wylaczony przez &f%gracz%&c.", "%gracz%", kto));
            }
            case "on", "wlacz", "enable" -> {
                if (!wylaczony) {
                    sender.sendMessage(msg("chat.lock.juz-wlaczony", "&eCzat jest juz wlaczony."));
                    return true;
                }
                wylaczony = false;
                Bukkit.broadcastMessage(msg("chat.lock.wlaczono",
                        "&aCzat zostal wlaczony przez &f%gracz%&a.", "%gracz%", kto));
            }
            default -> sender.sendMessage(msg("chat.lock.uzycie",
                    "&7Uzycie: &f/chat clear &7| &f/chat off &7| &f/chat on"));
        }
        return true;
    }

    private void wyczysc(String kto) {
        int linie = Math.max(20, plugin.getConfig().getInt("chat.lock.clear-linie", 120));
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < linie; i++) p.sendMessage(" ");
        }
        Bukkit.broadcastMessage(msg("chat.lock.wyczyszczono",
                "&eCzat zostal wyczyszczony przez &f%gracz%&e.", "%gracz%", kto));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission(PERM)) return out;
        if (args.length == 1) {
            String cur = args[0].toLowerCase(Locale.ROOT);
            for (String s : SUB) {
                if (s.startsWith(cur)) out.add(s);
            }
        }
        return out;
    }

    private String msg(String path, String def, String... ph) {
        FileConfiguration cfg = plugin.getConfig();
        String raw = cfg.getString(path, def);
        if (raw == null) raw = def;
        for (int i = 0; i + 1 < ph.length; i += 2) {
            raw = raw.replace(ph[i], ph[i + 1] == null ? "" : ph[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
