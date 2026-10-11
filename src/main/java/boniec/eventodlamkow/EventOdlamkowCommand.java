package boniec.eventodlamkow;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /eventodlamki start <sekundy> | stop | reload
 * Odpala/wylacza event "Szal Odlamkow" - patrz EventOdlamkowManager.
 */
public class EventOdlamkowCommand implements CommandExecutor, TabCompleter {

    private final EventOdlamkowManager manager;

    public EventOdlamkowCommand(EventOdlamkowManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eventodlamki.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Użycie: /eventodlamki <start <sekundy>|stop|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start": {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Użycie: /eventodlamki start <sekundy>");
                    return true;
                }
                int sekundy;
                try {
                    sekundy = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Czas musi być liczbą sekund.");
                    return true;
                }
                if (sekundy < 1) {
                    sender.sendMessage(ChatColor.RED + "Czas musi być większy od 0.");
                    return true;
                }
                boolean started = manager.start(sekundy);
                if (!started) {
                    sender.sendMessage(ChatColor.RED + "Event Szał Odłamków jest już aktywny! Użyj /eventodlamki stop, aby go zakończyć wcześniej.");
                    return true;
                }
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "Odpalono event Szał Odłamków na " + sekundy + "s.");
                return true;
            }
            case "stop": {
                boolean stopped = manager.stop(true);
                if (!stopped) {
                    sender.sendMessage(ChatColor.RED + "Event Szał Odłamków nie jest aktualnie aktywny.");
                    return true;
                }
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "Zatrzymano event Szał Odłamków.");
                return true;
            }
            case "reload": {
                manager.reload();
                sender.sendMessage(ChatColor.GREEN + "Przeładowano eventodlamki.yml i odlamki-klan.yml.");
                return true;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Użycie: /eventodlamki <start <sekundy>|stop|reload>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return new ArrayList<>(Arrays.asList("60", "120", "300"));
        }
        return new ArrayList<>();
    }
}
