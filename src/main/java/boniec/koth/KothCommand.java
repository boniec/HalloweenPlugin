package boniec.koth;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /adkoth <start [sekundy] | stop | editdrop | status | reload> */
public class KothCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PODKOMENDY = List.of("start", "stop", "editdrop", "status", "reload");

    private final KothManager manager;

    public KothCommand(KothManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("koth.admin")) {
            sender.sendMessage(manager.msg("brak-uprawnien"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(manager.msg("uzycie"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                int sek = manager.domyslneOdliczanie();
                if (args.length > 1) {
                    try {
                        sek = Math.max(0, Integer.parseInt(args[1]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(manager.msg("uzycie"));
                        return true;
                    }
                }
                String blad = manager.uruchom(sek);
                sender.sendMessage(manager.msg(blad == null ? "start-ok" : blad));
            }
            case "stop" -> sender.sendMessage(manager.msg(manager.przerwij() ? "stop-ok" : "nic-nie-trwa"));
            case "editdrop" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(manager.msg("tylko-gracz"));
                    return true;
                }
                manager.getDropGui().otworz(p);
            }
            case "status" -> manager.status().forEach(sender::sendMessage);
            case "reload" -> {
                manager.przeladuj();
                sender.sendMessage(manager.msg("reload-ok"));
            }
            default -> sender.sendMessage(manager.msg("uzycie"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> wynik = new ArrayList<>();
        if (!sender.hasPermission("koth.admin")) return wynik;
        if (args.length == 1) {
            for (String s : PODKOMENDY) {
                if (s.startsWith(args[0].toLowerCase())) wynik.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            wynik.add("0");
            wynik.add("60");
        }
        return wynik;
    }
}
