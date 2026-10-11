package boniec.nagroda;

import boniec.szaman.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /nagroda            - gracz dostaje kod weryfikacyjny do odebrania nagrody na Discordzie (nagroda.use)
 * /nagroda embed      - wysyla/odswieza embed z przyciskiem na kanale Discord (nagroda.admin)
 * /nagroda reload     - przeladowuje nagroda.yml i restartuje bota (nagroda.admin)
 * /nagroda odlacz <nick> - zdejmuje polaczenie Discord <-> nick, zeby mozna bylo polaczyc od nowa (nagroda.admin)
 */
public class NagrodaCommand implements CommandExecutor, TabCompleter {

    private final NagrodaManager manager;

    public NagrodaCommand(NagrodaManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            if (!sender.hasPermission("nagroda.admin")) {
                Msg.send(sender, "&cNie masz uprawnień do tej komendy.");
                return true;
            }
            if (args[0].equalsIgnoreCase("embed")) {
                Msg.send(sender, manager.wyslijEmbed());
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                manager.przeladuj();
                Msg.send(sender, "&aPrzeładowano &fnagroda.yml&a i zrestartowano bota Discord.");
                return true;
            }
            if (args[0].equalsIgnoreCase("odlacz")) {
                if (args.length < 2) {
                    Msg.send(sender, "&cUżycie: &f/nagroda odlacz <nick>");
                    return true;
                }
                int n = manager.odlacz(args[1]);
                Msg.send(sender, n > 0
                        ? "&aOdłączono konto Discord od nicku &f" + args[1] + "&a. Może połączyć konto ponownie."
                        : "&cNick &f" + args[1] + " &cnie ma połączonego konta Discord.");
                return true;
            }
            Msg.send(sender, "&cUżycie: &f/nagroda &7| &f/nagroda embed &7| &f/nagroda reload &7| &f/nagroda odlacz <nick>");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy. Admin: /nagroda embed | /nagroda reload");
            return true;
        }
        if (!player.hasPermission("nagroda.use")) {
            Msg.send(player, "&cNie masz uprawnień do tej komendy.");
            return true;
        }
        if (!manager.czyPolaczony()) {
            Msg.send(player, manager.t("wiadomosci.discord-wylaczony", "&cOdbieranie nagrody przez Discorda jest teraz niedostępne."));
            return true;
        }
        if (manager.czyOdebrano(player.getUniqueId())) {
            Msg.send(player, manager.t("wiadomosci.juz-odebrano", "&cJuż odebrałeś nagrodę za Discorda."));
            return true;
        }
        manager.wyslijKodGraczowi(player, manager.pobierzLubStworzKod(player));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("nagroda.admin")) {
            for (String s : manager.podpowiedzi()) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }
}
