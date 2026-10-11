package boniec.serce;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /aserce <spawn | despawn | editdrop | status | reload> */
public class SerceCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PODKOMENDY = List.of("spawn", "despawn", "editdrop", "status", "reload");

    private final SerceManager manager;

    public SerceCommand(SerceManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serce.admin")) {
            sender.sendMessage(manager.msg("brak-uprawnien"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(manager.msg("uzycie"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn", "start" -> {
                String blad = manager.uruchom();
                sender.sendMessage(manager.msg(blad == null ? "spawn-ok" : blad));
            }
            case "despawn", "stop" ->
                    sender.sendMessage(manager.msg(manager.przerwij() ? "despawn-ok" : "nic-nie-trwa"));
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
        if (!sender.hasPermission("serce.admin")) return wynik;
        if (args.length == 1) {
            for (String s : PODKOMENDY) {
                if (s.startsWith(args[0].toLowerCase())) wynik.add(s);
            }
        }
        return wynik;
    }
}
