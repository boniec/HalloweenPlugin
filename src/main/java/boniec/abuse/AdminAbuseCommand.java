package boniec.abuse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Komenda administracyjna /adminabuse start|stop - odpowiada za caly efekt
 * "AdminAbuse" opisany w {@link AdminAbuseManager} (tungtungsahur, ciemnosc,
 * rainbow, kula disco, wymuszone skoki).
 */
public class AdminAbuseCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISJA = "abuse.admin";

    private final AdminAbuseManager manager;

    public AdminAbuseCommand(AdminAbuseManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISJA)) {
            sender.sendMessage(AdminAbuseManager.kolor("&cNie masz uprawnień do tej komendy!"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("start")) {
            if (manager.isAktywny()) {
                sender.sendMessage(AdminAbuseManager.kolor("&cAdminAbuse już działa!"));
                return true;
            }
            manager.start();
            sender.sendMessage(AdminAbuseManager.kolor(
                    "&aAdminAbuse START &8- &f13 s ciemności, potem rainbow + kula disco "
                    + "+ wymuszone skoki (petla do /adminabuse stop)."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            if (!manager.isAktywny()) {
                sender.sendMessage(AdminAbuseManager.kolor("&cAdminAbuse nie działa."));
                return true;
            }
            manager.stop();
            sender.sendMessage(AdminAbuseManager.kolor("&cAdminAbuse STOP &8- &7efekty zdjęte z graczy."));
            return true;
        }
        sender.sendMessage(AdminAbuseManager.kolor("&cUżycie: &e/adminabuse start|stop"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISJA)) return List.of();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> wynik = new ArrayList<>();
            for (String opcja : List.of("start", "stop")) {
                if (opcja.startsWith(p)) wynik.add(opcja);
            }
            return wynik;
        }
        return List.of();
    }
}
