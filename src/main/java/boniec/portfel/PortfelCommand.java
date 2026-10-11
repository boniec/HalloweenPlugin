package boniec.portfel;

import boniec.szaman.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PortfelCommand implements CommandExecutor {

    private final PortfelGui gui;

    public PortfelCommand(PortfelGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player gracz)) {
            sender.sendMessage(Msg.color("&cTa komenda jest tylko dla graczy."));
            return true;
        }
        gui.otworzPortfel(gracz);
        return true;
    }
}
