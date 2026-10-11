package boniec.portfel;

import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /doladowanie <nick> <kwota> - dodaje vPLN i pokazuje title calemu serwerowi. */
public final class DoladowanieCommand implements CommandExecutor, TabCompleter {

    private static final String TITLE =
            "&#FCE16E&lᴅ&#F8E478&lᴏ&#F3E782&lʟ&#EFEB8D&lᴀ&#EAEE97&lᴅ&#E6F1A1&lᴏ&#EAEE97&lᴡ&#EFEB8D&lᴀ&#F3E782&lɴ&#F8E478&lɪ&#FCE16E&lᴇ";
    private static final String SUBTITLE = "&e%player% &fDoladowal &6%ilosc%vPLN";
    private static final String SOUND = "nusse:rollo";

    private final PortfelManager manager;

    public DoladowanieCommand(PortfelManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("portfel.admin")) {
            Msg.send(sender, "&cBrak uprawnien!");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "&cUzycie: /doladowanie <nick> <kwota>");
            return true;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer cel = Bukkit.getOfflinePlayer(args[0]);
        if (cel == null || (!cel.hasPlayedBefore() && !cel.isOnline() && cel.getName() == null)) {
            Msg.send(sender, "&cNie znaleziono gracza: &e" + args[0]);
            return true;
        }
        double kwota;
        try {
            kwota = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException e) {
            Msg.send(sender, "&cKwota musi byc liczba.");
            return true;
        }
        if (kwota <= 0) {
            Msg.send(sender, "&cKwota musi byc wieksza od 0.");
            return true;
        }

        UUID uuid = cel.getUniqueId();
        String nick = cel.getName() != null ? cel.getName() : args[0];
        if (!manager.dodaj(uuid, kwota, sender.getName(), "Doladowanie")) {
            Msg.send(sender, "&cNie udalo sie dodac vPLN.");
            return true;
        }
        if (cel.getName() == null) {
            manager.getKonto(uuid).name = nick;
        }

        String ilosc = manager.formatBalance(kwota);
        String tytul = Msg.color(TITLE);
        String podtytul = Msg.color(SUBTITLE.replace("%player%", nick).replace("%ilosc%", ilosc));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(tytul, podtytul, 10, 70, 20);
            p.playSound(p.getLocation(), SOUND, 1f, 1f);
        }
        Msg.send(sender, "&aDoladowano &e" + ilosc + " vPLN &agraczowi &f" + nick + "&a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (sender instanceof Player && !sender.hasPermission("portfel.admin")) return out;
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
            }
        }
        return out;
    }
}
