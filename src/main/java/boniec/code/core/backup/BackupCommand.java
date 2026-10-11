package boniec.code.core.backup;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BackupCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final BackupManager manager;
    private final BackupGUI gui;

    public BackupCommand(JavaPlugin plugin, BackupManager manager, BackupGUI gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cKomenda tylko dla graczy!");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage("§8§m------------------------------------");
            p.sendMessage("§e§lSYSTEM BACKUPÓW");
            p.sendMessage("§f/backup <nick> §7- lista śmierci gracza");
            p.sendMessage("§f/backup weryfikacja §7- oczekujące wnioski");
            p.sendMessage("§f/odbierzbackup §7- odbiór swojego backupu");
            if (BackupPerms.canRevoke(p)) p.sendMessage("§f/cofnijbackup <nick> §7- cofnięcie backupu");
            p.sendMessage("§8§m------------------------------------");
            return true;
        }

        if (args[0].equalsIgnoreCase("weryfikacja") || args[0].equalsIgnoreCase("verify")) {
            if (!BackupPerms.canViewVerification(p)) {
                p.sendMessage("§cBrak uprawnień do weryfikacji.");
                return true;
            }
            gui.openVerification(p);
            return true;
        }

        if (!BackupPerms.canRequest(p)) {
            p.sendMessage("§cBrak uprawnień do przeglądania backupów.");
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

        gui.openDeathList(p, targetUuid, targetName);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("weryfikacja".startsWith(prefix)) list.add("weryfikacja");
            for (Player player : Bukkit.getOnlinePlayers())
                if (player.getName().toLowerCase().startsWith(prefix)) list.add(player.getName());
        }
        return list;
    }
}