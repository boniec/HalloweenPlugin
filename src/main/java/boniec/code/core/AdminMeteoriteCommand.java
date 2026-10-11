package boniec.code.core;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminMeteoriteCommand implements CommandExecutor {

    private final MeteoriteManager manager;

    public AdminMeteoriteCommand(MeteoriteManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("ᴋᴏᴍᴇɴᴅᴀ ᴛʏʟᴋᴏ ᴅʟᴀ ɢʀᴀᴄᴢʏ");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("core.adminmeteoryt")) {
            player.sendMessage(ChatColor.RED + "ʙʀᴀᴋ ᴜᴘʀᴀᴡɴɪᴇɴ");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "ᴜᴢʏᴄɪᴇ: /adminmeteoryt <editdrop|setspawn|gui>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "editdrop":
                manager.openEditDropGUI(player);
                return true;
            case "setspawn":
                manager.addSpawnLocation(player.getLocation().getBlock().getLocation());
                player.sendMessage(ChatColor.GREEN + "ᴅᴏᴅᴀɴᴏ ʟᴏᴋᴀʟɪᴢᴀᴄᴊᴇ sᴘᴀᴡɴᴜ ᴍᴇᴛᴇᴏʀʏᴛᴜ");
                return true;
            case "gui":
                manager.openControlGUI(player);
                return true;
            default:
                player.sendMessage(ChatColor.RED + "ᴜᴢʏᴄɪᴇ: /adminmeteoryt <editdrop|setspawn|gui>");
                return true;
        }
    }
}
