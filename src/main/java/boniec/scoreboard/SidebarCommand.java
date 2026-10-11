package boniec.scoreboard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /scoreboard [toggle|reload] (aliasy: /sb, /tablica) */
public class SidebarCommand implements CommandExecutor, TabCompleter {

    private final SidebarManager manager;

    public SidebarCommand(SidebarManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("scoreboard.admin")) {
                sender.sendMessage(manager.wiadomosc("brak-uprawnien"));
                return true;
            }
            manager.przeladuj();
            sender.sendMessage(manager.wiadomosc("przeladowano"));
            return true;
        }

        if (sub.equals("toggle")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(manager.wiadomosc("tylko-gracz"));
                return true;
            }
            boolean wlaczony = manager.przelacz(p);
            p.sendMessage(manager.wiadomosc(wlaczony ? "wlaczony" : "wylaczony"));
            return true;
        }

        sender.sendMessage("\u00A7e/" + label + " [toggle|reload]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> wynik = new ArrayList<>();
        if (args.length == 1) {
            for (String s : manager.podpowiedzi()) {
                if (s.equals("reload") && !sender.hasPermission("scoreboard.admin")) continue;
                if (s.startsWith(args[0].toLowerCase())) wynik.add(s);
            }
        }
        return wynik;
    }
}
