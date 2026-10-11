package boniec.nekromanta;

import boniec.klany.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /nekromanta - otwiera menu wymiany glutow na losowe miecze. */
public class NekromantaCommand implements CommandExecutor {

    private final NekromantaGui gui;

    public NekromantaCommand(NekromantaGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.color("&cTa komenda jest tylko dla graczy."));
            return true;
        }
        gui.otworz(p);
        return true;
    }
}
