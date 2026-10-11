package boniec.klany.cmd;

import boniec.klany.gui.TopkiGui;
import boniec.klany.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /topki - szybki dostęp do rankingów klanów i graczy
 */
public class TopkiCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player gracz)) {
            Msg.send(sender, "tylko-gracz");
            return true;
        }

        new TopkiGui().otworz(gracz);
        return true;
    }
}
