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
 * /bc &lt;title|chat&gt; &lt;treść&gt; — to samo co admintitle, permisja bc.use
 */
public class BcCommand implements CommandExecutor, TabCompleter {

    private final OgloszeniaService service;

    public BcCommand(OgloszeniaService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bc.use") && !sender.isOp()) {
            Msg.send(sender, "&cNie masz uprawnień do tej komendy!");
            return true;
        }

        if (args.length < 1) {
            Msg.send(sender, "&cUżycie: /bc <title|chat> <treść>");
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
            default -> Msg.send(sender, "&cTryb: &etitle &8| &echat");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("title", "chat"));
            String p = args[0].toLowerCase();
            opts.removeIf(s -> !s.startsWith(p));
            return opts;
        }
        return List.of();
    }
}
