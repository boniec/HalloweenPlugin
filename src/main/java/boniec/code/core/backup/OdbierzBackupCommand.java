package boniec.code.core.backup;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /odbierzbackup - gracz odbiera swój zatwierdzony backup. */
public class OdbierzBackupCommand implements CommandExecutor, TabCompleter {

    private final BackupGUI gui;

    public OdbierzBackupCommand(BackupGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cKomenda dostępna tylko dla graczy!");
            return true;
        }
        Player p = (Player) sender;

        if (args.length > 0) {
            p.sendMessage("§7§l» §cTa komenda służy do odbioru §fwłasnego §cbackupu.");
            if (BackupPerms.canRevoke(p)) p.sendMessage("§7Aby cofnąć backup graczowi użyj: §e/cofnijbackup " + args[0]);
            return true;
        }

        gui.openClaim(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return new ArrayList<>();
    }
}