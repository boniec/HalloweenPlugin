package boniec.azbroja;

import boniec.szaman.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AzbrojaCommand implements CommandExecutor, TabCompleter {

    private final AzbrojaManager manager;
    private final AzbrojaGui gui;

    public AzbrojaCommand(AzbrojaManager manager, AzbrojaGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player gracz)) {
            sender.sendMessage(Msg.color("&cTa komenda jest tylko dla graczy."));
            return true;
        }
        if (!gracz.hasPermission("azbroja.admin")) {
            Msg.send(gracz, manager.msg("brak-uprawnien"));
            return true;
        }
        if (args.length == 0) {
            Msg.send(gracz, manager.msg("uzycie"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            manager.wczytaj();
            Msg.send(gracz, "&aPrzeladowano azbroja.yml");
            return true;
        }

        if (args[0].equalsIgnoreCase("nazwa")) {
            if (args.length < 2) {
                Msg.send(gracz, manager.msg("nazwa-za-krotka"));
                return true;
            }
            String[] slowa = Arrays.copyOfRange(args, 1, args.length);
            AzbrojaUtil.ParsedNazwa parsed = AzbrojaUtil.parsujNazwe(slowa);
            if (parsed == null) {
                Msg.send(gracz, manager.msg("nazwa-za-krotka"));
                return true;
            }
            manager.rozpocznij(gracz, parsed);
            gui.otworz(gracz);
            return true;
        }

        // Skrocona forma: /azbroja miecz chewbacci 1
        AzbrojaUtil.ParsedNazwa parsed = AzbrojaUtil.parsujNazwe(args);
        if (parsed != null) {
            manager.rozpocznij(gracz, parsed);
            gui.otworz(gracz);
            return true;
        }

        Msg.send(gracz, manager.msg("uzycie"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("azbroja.admin")) return List.of();
        if (args.length == 1) {
            return filtruj(List.of("nazwa", "reload"), args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("nazwa")) {
            return List.of("<miecz chewbacci 1>");
        }
        return List.of();
    }

    private List<String> filtruj(List<String> opcje, String start) {
        String s = start.toLowerCase(Locale.ROOT);
        return opcje.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(s)).collect(Collectors.toList());
    }
}
