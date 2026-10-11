package boniec.warp;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /adminwarp create <nazwa> - tworzy warp w aktualnej lokalizacji gracza.
 * /adminwarp delete <nazwa> - usuwa warp.
 * Wymaga uprawnienia warp.admin.
 */
public class AdminWarpCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBKOMENDY = Arrays.asList("create", "delete");

    private final WarpManager manager;

    public AdminWarpCommand(WarpManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("warp.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Użycie: /adminwarp <create|delete> <nazwa>");
            return true;
        }

        String sub = args[0].toLowerCase();
        String name = args[1];

        switch (sub) {
            case "create": {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Ta subkomenda jest tylko dla graczy.");
                    return true;
                }
                boolean nadpisano = manager.exists(name);
                manager.createWarp(name, player.getLocation());
                if (nadpisano) {
                    sender.sendMessage(ChatColor.GREEN + "Nadpisano warp '" + name + "' Twoją aktualną lokalizacją.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Utworzono warp '" + name + "'.");
                }
                return true;
            }
            case "delete": {
                boolean usunieto = manager.deleteWarp(name);
                if (usunieto) {
                    sender.sendMessage(ChatColor.GREEN + "Usunięto warp '" + name + "'.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Nie ma warpu o nazwie '" + name + "'.");
                }
                return true;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Użycie: /adminwarp <create|delete> <nazwa>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : SUBKOMENDY) {
                if (sub.startsWith(partial)) {
                    result.add(sub);
                }
            }
        } else if (args.length == 2 && "delete".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase();
            for (String name : manager.getWarpNames()) {
                if (name.toLowerCase().startsWith(partial)) {
                    result.add(name);
                }
            }
        }
        return result;
    }
}
