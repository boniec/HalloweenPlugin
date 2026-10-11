package boniec.code.core.backup;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /cofnijbackup <nick> - administracja zabiera nadany backup. */
public class CofnijBackupCommand implements CommandExecutor, TabCompleter {

    private final BackupGUI gui;

    public CofnijBackupCommand(BackupGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cKomenda dostępna tylko dla graczy!");
            return true;
        }
        Player p = (Player) sender;

        if (!BackupPerms.canRevoke(p)) {
            p.sendMessage("§cBrak uprawnień do cofania backupów.");
            return true;
        }

        if (args.length < 1) {
            p.sendMessage("§7§l» §cUżycie: §e/cofnijbackup <nick>");
            return true;
        }

        String targetName = args[0];
        Player online = Bukkit.getPlayerExact(targetName);
        UUID targetUuid;
        if (online != null) {
            targetUuid = online.getUniqueId();
            targetName = online.getName();
        } else {
            targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        }

        gui.openRevoke(p, targetUuid, targetName);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1 && sender instanceof Player && BackupPerms.canRevoke((Player) sender)) {
            String prefix = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers())
                if (player.getName().toLowerCase().startsWith(prefix)) list.add(player.getName());
        }
        return list;
    }
}