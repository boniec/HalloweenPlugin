package boniec.ustawienia;

import boniec.szaman.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class UstawieniaCommand implements CommandExecutor {

    private final UstawieniaGui gui;

    public UstawieniaCommand(UstawieniaGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.color("&cTa komenda jest tylko dla graczy."));
            return true;
        }
        gui.otworz(p);
        return true;
    }
}
