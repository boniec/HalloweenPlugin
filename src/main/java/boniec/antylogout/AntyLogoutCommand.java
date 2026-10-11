package boniec.antylogout;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /antylogout reload | status | wlacz | wylacz
 */
public final class AntyLogoutCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "status", "wlacz", "wylacz");

    private final AntyLogoutManager manager;
    private final AntyLogoutConfig config;

    public AntyLogoutCommand(AntyLogoutManager manager, AntyLogoutConfig config) {
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uzycie: /antylogout <wlacz|wylacz|reload|status>");
            return true;
        }

        if (!sender.hasPermission("antylogout.admin")) {
            sender.sendMessage(config.getMsgBrakUprawnien());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                config.load();
                sender.sendMessage(config.getMsgConfigPrzeladowany());
            }
            case "status" -> {
                String msg = config.getMsgStatus()
                        .replace("%enabled%", String.valueOf(config.isEnabled()))
                        .replace("%czas%", String.valueOf(config.getCombatSeconds()))
                        .replace("%ile%", String.valueOf(manager.getTaggedCount()));
                sender.sendMessage(msg);
            }
            case "wlacz", "on", "enable" -> {
                config.setEnabled(true);
                sender.sendMessage(config.getMsgWlaczony());
            }
            case "wylacz", "off", "disable" -> {
                config.setEnabled(false);
                manager.wyczyscWszystkich();
                sender.sendMessage(config.getMsgWylaczony());
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Uzycie: /antylogout <wlacz|wylacz|reload|status>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return java.util.Collections.emptyList();
    }
}
