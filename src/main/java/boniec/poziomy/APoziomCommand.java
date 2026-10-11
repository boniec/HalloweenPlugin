package boniec.poziomy;

import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /apoziom set <gracz> <poziom>
 * /apoziom add|remove <gracz> <exp>
 * /apoziom reset <gracz>
 * /apoziom boost <mnoznik> <czas>     (czas: 300, 30s, 10m, 2h, 1d; 0 = wylacz)
 * /apoziom nagroda <poziom>           - edytor przedmiotow nagrody (zapis po zamknieciu)
 * /apoziom nagroda <poziom> exp <ilosc>
 * /apoziom nagroda <poziom> komenda <dodaj <komenda...>|wyczysc|lista>
 */
public class APoziomCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "&f乂 &#C02FD4ᴘᴏᴢɪᴏᴍʏ &8| ";

    private final PoziomyManager manager;
    private final PoziomyGui gui;

    public APoziomCommand(PoziomyManager manager, PoziomyGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            pomoc(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("levels.admin") && !sender.hasPermission("levels." + sub)) {
            Msg.send(sender, manager.msg("brak-uprawnien", "&cNie posiadasz uprawnien."));
            return true;
        }

        switch (sub) {
            case "set" -> {
                if (args.length < 3) { pomoc(sender, label); return true; }
                OfflinePlayer cel = gracz(sender, args[1]);
                Integer poziom = liczba(sender, args[2]);
                if (cel == null || poziom == null) return true;
                Integer exp = manager.getExpNaPoziom(poziom);
                if (exp == null) {
                    Msg.send(sender, PREFIX + "&cPoziom &f" + poziom + " &cnie istnieje w poziomy.yml.");
                    return true;
                }
                manager.getProfil(cel).setPoziom(poziom);
                manager.getProfil(cel).setExp(0);
                manager.oznaczDoZapisu();
                Msg.send(sender, PREFIX + "&aUstawiono graczowi &f" + cel.getName() + " &apoziom &f" + poziom);
            }
            case "add", "remove" -> {
                if (args.length < 3) { pomoc(sender, label); return true; }
                OfflinePlayer cel = gracz(sender, args[1]);
                Integer ilosc = liczba(sender, args[2]);
                if (cel == null || ilosc == null) return true;
                Profil profil = manager.getProfil(cel);
                if (cel instanceof Player online) {
                    manager.dodajExp(online, sub.equals("add") ? ilosc : -ilosc, false);
                    profil = manager.getProfil(cel);
                } else {
                    profil.setExp(profil.getExp() + (sub.equals("add") ? ilosc : -ilosc));
                    manager.awansujJesliMozna(profil);
                }
                manager.oznaczDoZapisu();
                Msg.send(sender, PREFIX + (sub.equals("add") ? "&aDodano &f" : "&cZabrano &f") + ilosc
                        + " exp &7graczowi &f" + cel.getName() + " &7(teraz " + PoziomyManager.formatujExp(profil.getExp())
                        + " / poziom " + profil.getPoziom() + ")");
            }
            case "reset" -> {
                if (args.length < 2) { pomoc(sender, label); return true; }
                OfflinePlayer cel = gracz(sender, args[1]);
                if (cel == null) return true;
                Profil profil = manager.getProfil(cel);
                profil.setExp(0);
                profil.getOdebraneNagrody().clear();
                manager.oznaczDoZapisu();
                Msg.send(sender, PREFIX + "&cZresetowano exp i odebrane nagrody gracza &f" + cel.getName());
            }
            case "boost" -> {
                if (args.length < 3) { pomoc(sender, label); return true; }
                double mnoznik;
                try {
                    mnoznik = Double.parseDouble(args[1].replace(",", "."));
                } catch (NumberFormatException e) {
                    Msg.send(sender, PREFIX + "&cMnoznik musi byc liczba (np. 2 albo 1.5).");
                    return true;
                }
                int sekundy = PoziomyManager.parsujCzas(args[2]);
                if (sekundy < 0 || mnoznik <= 0) {
                    Msg.send(sender, PREFIX + "&cPodaj poprawny czas, np. &f300&c, &f30s&c, &f10m&c, &f2h&c.");
                    return true;
                }
                manager.ustawBoost(mnoznik, sekundy);
                Msg.send(sender, sekundy == 0
                        ? PREFIX + "&cWylaczono boost expa."
                        : PREFIX + "&aUstawiono boost expa &fx" + PoziomyManager.formatuj(mnoznik)
                                + " &ana &f" + PoziomyManager.formatujCzas(sekundy));
            }
            case "nagroda" -> nagroda(sender, label, args);
            default -> pomoc(sender, label);
        }
        return true;
    }

    private void nagroda(CommandSender sender, String label, String[] args) {
        if (args.length < 2) { pomoc(sender, label); return; }
        Integer poziom = liczba(sender, args[1]);
        if (poziom == null) return;

        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Edytor przedmiotow jest dostepny tylko dla graczy.");
                return;
            }
            if (manager.getExpNaPoziom(poziom) == null) {
                Msg.send(sender, PREFIX + "&ePoziom &f" + poziom + " &enie ma jeszcze ustawionego expa - ustaw go: &f/"
                        + label + " nagroda " + poziom + " exp <ilosc>");
            }
            gui.otworzEdytor(player, poziom);
            return;
        }

        String co = args[2].toLowerCase(Locale.ROOT);
        if (co.equals("exp") && args.length >= 4) {
            Integer exp = liczba(sender, args[3]);
            if (exp == null) return;
            manager.setExpNaPoziom(poziom, exp);
            Msg.send(sender, PREFIX + "&aPoziom &f" + poziom + " &awymaga teraz &f" + exp + " &aexp.");
            return;
        }
        if (co.equals("komenda") && args.length >= 4) {
            List<String> komendy = manager.getNagrodyKomendy(poziom);
            switch (args[3].toLowerCase(Locale.ROOT)) {
                case "dodaj" -> {
                    if (args.length < 5) {
                        Msg.send(sender, PREFIX + "&cPodaj komende, np. &f/" + label + " nagroda " + poziom + " komenda dodaj eco give {player} 1000");
                        return;
                    }
                    String komenda = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                    komendy.add(komenda);
                    manager.setNagrodyKomendy(poziom, komendy);
                    Msg.send(sender, PREFIX + "&aDodano komende nagrody: &f" + komenda);
                }
                case "wyczysc" -> {
                    manager.setNagrodyKomendy(poziom, new ArrayList<>());
                    Msg.send(sender, PREFIX + "&cUsunieto wszystkie komendy nagrody za poziom &f" + poziom);
                }
                default -> {
                    Msg.send(sender, PREFIX + "&fKomendy nagrody za poziom &#C02FD4" + poziom + "&f:");
                    if (komendy.isEmpty()) Msg.send(sender, "&8x &7brak");
                    for (String k : komendy) Msg.send(sender, "&8x &f" + k);
                }
            }
            return;
        }
        pomoc(sender, label);
    }

    private void pomoc(CommandSender sender, String label) {
        Msg.send(sender, PREFIX + "&fKomendy:");
        Msg.send(sender, "&8x &f/" + label + " set <gracz> <poziom>");
        Msg.send(sender, "&8x &f/" + label + " add <gracz> <exp>");
        Msg.send(sender, "&8x &f/" + label + " remove <gracz> <exp>");
        Msg.send(sender, "&8x &f/" + label + " reset <gracz>");
        Msg.send(sender, "&8x &f/" + label + " boost <mnoznik> <czas> &7(0 = wylacz)");
        Msg.send(sender, "&8x &f/" + label + " nagroda <poziom> &7- edytor przedmiotow");
        Msg.send(sender, "&8x &f/" + label + " nagroda <poziom> exp <ilosc>");
        Msg.send(sender, "&8x &f/" + label + " nagroda <poziom> komenda <dodaj|wyczysc|lista>");
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer gracz(CommandSender sender, String nazwa) {
        Player online = Bukkit.getPlayerExact(nazwa);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(nazwa);
        if (!offline.hasPlayedBefore()) {
            Msg.send(sender, PREFIX + "&cNie znaleziono gracza &f" + nazwa);
            return null;
        }
        return offline;
    }

    private Integer liczba(CommandSender sender, String tekst) {
        try {
            return Integer.parseInt(tekst);
        } catch (NumberFormatException e) {
            Msg.send(sender, PREFIX + "&cTo nie jest liczba calkowita: &f" + tekst);
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> wynik = new ArrayList<>();
        if (args.length == 1) {
            wynik.addAll(List.of("set", "add", "remove", "reset", "boost", "nagroda"));
        } else if (args.length == 2 && List.of("set", "add", "remove", "reset").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) wynik.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("nagroda")) {
            for (Integer p : manager.getPoziomy()) wynik.add(String.valueOf(p));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("nagroda")) {
            wynik.addAll(List.of("exp", "komenda"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("nagroda") && args[2].equalsIgnoreCase("komenda")) {
            wynik.addAll(List.of("dodaj", "wyczysc", "lista"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("boost")) {
            wynik.addAll(List.of("10m", "30m", "1h", "0"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        wynik.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return wynik;
    }
}
