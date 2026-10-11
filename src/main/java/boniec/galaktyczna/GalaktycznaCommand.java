package boniec.galaktyczna;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GalaktycznaCommand implements CommandExecutor {

    private final GalaktycznaManager manager;
    private final GalaktycznaGui gui;

    public GalaktycznaCommand(GalaktycznaManager manager, GalaktycznaGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.prefix() + manager.msg("tylko-gracz", "&cTę komendę może użyć tylko gracz."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("galaktyczna.admin")) {
            manager.reload();
            sender.sendMessage(manager.prefix() + "&aPrzeładowano galaktyczna.yml");
            return true;
        }
        gui.otworz(player);
        return true;
    }
}
