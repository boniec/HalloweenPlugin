package boniec.szaman.cmd;

import boniec.szaman.SzamanPlugin;
import boniec.szaman.gui.SzamanGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SzamanCommand implements CommandExecutor {

    private final SzamanPlugin plugin;

    public SzamanCommand(SzamanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko gracze moga uzywac tej komendy.");
            return true;
        }
        new SzamanGui(plugin, player).otworz(player);
        return true;
    }
}
