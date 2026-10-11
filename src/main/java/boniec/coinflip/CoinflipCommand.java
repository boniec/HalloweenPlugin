package boniec.coinflip;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CoinflipCommand implements CommandExecutor, TabCompleter {

    private final CoinflipManager manager;
    private final CoinflipGui gui;

    public CoinflipCommand(CoinflipManager manager, CoinflipGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(manager.msg("tylko-gracz"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gry")) {
            gui.otworzListe(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("odbierz")) {
            manager.odbierz(p);
            return true;
        }
        if (sub.equals("stworz") || sub.equals("create")) {
            if (args.length < 2) {
                p.sendMessage(manager.msg("uzycie"));
                return true;
            }
            int stawka = parsuj(args[1]);
            if (stawka < 0) {
                p.sendMessage(manager.msg("zla-kwota")
                        .replace("%min%", String.valueOf(manager.minStawka()))
                        .replace("%max%", String.valueOf(manager.maxStawka())));
                return true;
            }
            if (manager.stworz(p, stawka)) {
                gui.otworzListe(p);
            }
            return true;
        }
        int stawka = parsuj(args[0]);
        if (stawka > 0) {
            if (manager.stworz(p, stawka)) gui.otworzListe(p);
            return true;
        }
        p.sendMessage(manager.msg("uzycie"));
        return true;
    }

    private static int parsuj(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("stworz", "gry", "odbierz")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stworz")) {
            out.add("100");
            out.add("250");
            out.add("500");
            out.add("1000");
        }
        return out;
    }
}
