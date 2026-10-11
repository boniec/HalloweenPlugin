package boniec.tiktok;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AtiktokCommand implements CommandExecutor, TabCompleter {

    private final TiktokManager manager;
    private final TiktokGui gui;

    public AtiktokCommand(TiktokManager manager, TiktokGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tiktok.admin")) {
            sender.sendMessage(manager.kolor(manager.txt("brak-uprawnien", "&f与 &cNie masz uprawnień.")));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            manager.wczytaj();
            sender.sendMessage(manager.kolor(manager.txt("przeladowano", "&f両 &aPrzeładowano tiktok.yml")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.kolor(manager.txt("tylko-gracz", "&f亢 &cTa komenda jest tylko dla graczy.")));
            return true;
        }
        gui.otworzAdmin(player, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            out.add("reload");
        }
        return out;
    }
}
