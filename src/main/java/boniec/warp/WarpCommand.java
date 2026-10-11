package boniec.warp;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /warp <nazwa> - teleportuje gracza pod dany warp. Celowo BEZ zadnej
 * wiadomosci przy udanej teleportacji (np. gdy uzywane z poziomu GUI w
 * DeluxeMenus, gdzie samo GUI/dzwiek informuje gracza) - komunikaty
 * pojawiaja sie tylko przy bledach (brak warpu, brak uprawnien, zla komenda).
 * /warp (bez argumentu) pokazuje liste dostepnych warpow.
 */
public class WarpCommand implements CommandExecutor, TabCompleter {

    private final WarpManager manager;

    public WarpCommand(WarpManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda jest tylko dla graczy.");
            return true;
        }

        if (args.length == 0) {
            List<String> names = manager.getWarpNames();
            if (names.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Nie ma jeszcze zadnych warpow.");
            } else {
                player.sendMessage(ChatColor.GOLD + "Dostepne warpy: " + ChatColor.YELLOW + String.join(ChatColor.GRAY + ", " + ChatColor.YELLOW.toString(), names));
            }
            return true;
        }

        String name = args[0];
        Location loc = manager.getWarp(name);
        if (loc == null) {
            player.sendMessage(ChatColor.RED + "Nie ma warpu o nazwie '" + name + "'.");
            return true;
        }

        player.teleport(loc);
        // Celowo brak wiadomosci po udanej teleportacji.
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        String partial = args[0].toLowerCase();
        for (String name : manager.getWarpNames()) {
            if (name.toLowerCase().startsWith(partial)) {
                result.add(name);
            }
        }
        return result;
    }
}
