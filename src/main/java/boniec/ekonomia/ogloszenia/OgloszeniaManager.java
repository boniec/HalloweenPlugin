package boniec.ekonomia.ogloszenia;

import boniec.klany.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /admintitle &lt;title|chat|reload&gt; [treść]
 */
public class OgloszeniaManager implements CommandExecutor, TabCompleter {

    private final OgloszeniaService service;

    public OgloszeniaManager(OgloszeniaService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("admin.bc")) {
            Msg.send(sender, "&cNie masz uprawnień.");
            return true;
        }

        if (args.length == 0) {
            Msg.send(sender, "&cUżycie: /admintitle <title|chat|reload> [treść]");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            service.reload();
            Msg.send(sender, "&#55FF88&l✔ &fPrzeładowano &7admintitle.yml&f.");
            return true;
        }

        if (args.length < 2) {
            Msg.send(sender, "&cMusisz podać treść ogłoszenia!");
            return true;
        }

        String tekst = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        switch (args[0].toLowerCase()) {
            case "title" -> {
                service.wyslijTitle(tekst);
                sender.sendMessage(service.potwierdzenieTitle());
            }
            case "chat" -> {
                service.wyslijChat(tekst);
                sender.sendMessage(service.potwierdzenieChat());
            }
            default -> Msg.send(sender, "&cTryb: &etitle &8| &echat &8| &ereload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("admin.bc")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("title", "chat", "reload"));
            String p = args[0].toLowerCase();
            opts.removeIf(s -> !s.startsWith(p));
            return opts;
        }
        return List.of();
    }
}
