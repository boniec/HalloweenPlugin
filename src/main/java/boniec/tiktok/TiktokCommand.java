package boniec.tiktok;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TiktokCommand implements CommandExecutor, TabCompleter {

    private final TiktokManager manager;
    private final TiktokGui gui;

    public TiktokCommand(TiktokManager manager, TiktokGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("tiktok.admin")) {
                sender.sendMessage(manager.kolor(manager.txt("brak-uprawnien", "&f与 &cNie masz uprawnień.")));
                return true;
            }
            manager.wczytaj();
            sender.sendMessage(manager.kolor(manager.txt("przeladowano", "&f両 &aPrzeładowano tiktok.yml")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.kolor(manager.txt("tylko-gracz", "&f亢 &cTa komenda jest tylko dla graczy.")));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            gui.otworzGracza(player, 0);
            return true;
        }

        if (args[0].equalsIgnoreCase("polacz") || args[0].equalsIgnoreCase("connect")) {
            if (args.length < 2) {
                player.sendMessage(manager.kolor(manager.txt("polacz-uzycie", "&f东 &7Użycie: &e/tiktok polacz <nick>")));
                return true;
            }
            player.sendMessage(manager.kolor(manager.polacz(player, args[1])));
            return true;
        }

        if (args[0].equalsIgnoreCase("odlacz") || args[0].equalsIgnoreCase("disconnect")) {
            player.sendMessage(manager.kolor(manager.odlacz(player)));
            return true;
        }

        if (args[0].equalsIgnoreCase("odswiez") || args[0].equalsIgnoreCase("refresh")) {
            TiktokManager.Konto k = manager.getKonto(player.getUniqueId());
            if (k == null || k.tiktok == null || k.tiktok.isEmpty()) {
                player.sendMessage(manager.kolor(manager.txt("nie-polaczony", "&f与 &cNajpierw: &e/tiktok polacz <nick>")));
                return true;
            }
            player.sendMessage(manager.kolor(manager.txt("pobieranie", "&f东 &7Pobieram filmy z TikToka...")));
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(manager.plugin(), () -> {
                int wynik = manager.odswiezFilmy(player.getUniqueId(), true);
                org.bukkit.Bukkit.getScheduler().runTask(manager.plugin(), () -> {
                    if (!player.isOnline()) return;
                    if (wynik == -2) {
                        player.sendMessage(manager.kolor(manager.txt("blad-api",
                                "&f亢 &eNie udało się pobrać filmów z TikToka. Pokazuję zapisane.")));
                    } else {
                        player.sendMessage(manager.kolor(manager.txt("odswiezone", "&f両 &aOdświeżono listę filmów.")));
                    }
                    gui.otworzGracza(player, 0, false);
                });
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("dodaj") || args[0].equalsIgnoreCase("add")) {
            if (args.length < 2) {
                player.sendMessage(manager.kolor(manager.txt("dodaj-uzycie", "&f东 &7Użycie: &e/tiktok dodaj <link tiktok>")));
                return true;
            }
            player.sendMessage(manager.kolor(manager.dodajFilm(player, args[1])));
            return true;
        }

        player.sendMessage(manager.kolor(manager.txt("uzycie",
                "&f东 &7/tiktok polacz <nick> &8| &7/tiktok dodaj <link> &8| &7/tiktok gui")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("gui", "polacz", "odlacz", "dodaj", "odswiez", "reload")) {
                if (s.startsWith(p) && ( !"reload".equals(s) || sender.hasPermission("tiktok.admin"))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
