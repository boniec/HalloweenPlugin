package boniec.sklep;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SklepCommand implements CommandExecutor {

    private final SklepGui gui;

    public SklepCommand(SklepGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player gracz)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        gui.otworzGlowny(gracz);
        return true;
    }
}
