package boniec.dev.polezamek;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReloadCommand implements CommandExecutor {

    private final Main plugin;

    public ReloadCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko gracz moze uzyc tej komendy.");
            return true;
        }

        if (!player.hasPermission("polezamek.admin")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnien!");
            return true;
        }

        plugin.reloadConfig();
        plugin.reloadManagers();

        player.sendMessage(ChatColor.GREEN + "Przeladowano konfiguracje PoleZamek!");
        return true;
    }
}
