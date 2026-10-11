package boniec.poziomy;

import boniec.szaman.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /poziom - GUI nagrod, /poziom info - postep, /poziom reload - przeladowanie poziomy.yml. */
public class PoziomCommand implements CommandExecutor, TabCompleter {

    private final PoziomyManager manager;
    private final PoziomyGui gui;

    public PoziomCommand(PoziomyManager manager, PoziomyGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("levels.reload")) {
                Msg.send(sender, manager.msg("brak-uprawnien", "&cNie posiadasz uprawnien."));
                return true;
            }
            long start = System.currentTimeMillis();
            manager.wczytajConfig();
            Msg.send(sender, manager.msg("przeladowano", "&f乂 &#C02FD4ᴘᴏᴢɪᴏᴍʏ &8| &aPrzeladowano poziomy.yml! &7(%czas%ms)")
                    .replace("%czas%", String.valueOf(System.currentTimeMillis() - start)));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            Profil profil = manager.getProfil(player);
            int poziom = profil.getPoziom();
            int nastepny = manager.getExpNaNastepny(poziom);
            for (String linia : manager.getConfig().getStringList("wiadomosci.info")) {
                Msg.send(player, linia
                        .replace("%pasek%", manager.getPasekPostepu(manager.getProcent(profil)))
                        .replace("%exp%", PoziomyManager.formatujExp(profil.getExp()))
                        .replace("%cel%", nastepny < 0 ? "ᴍᴀx" : PoziomyManager.formatujExp(nastepny))
                        .replace("%poziom%", String.valueOf(poziom)));
            }
            return true;
        }

        gui.otworz(player, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> wynik = new ArrayList<>();
        if (args.length == 1) {
            wynik.add("info");
            if (sender.hasPermission("levels.reload")) wynik.add("reload");
        }
        return wynik;
    }
}
