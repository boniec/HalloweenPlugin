package boniec.klany.cmd;

import boniec.klany.core.Klan;
import boniec.klany.core.KlanManager;
import boniec.klany.core.KlanWalutaManager;
import boniec.klany.gui.UlepszeniaGui;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /adminklan <subkomenda> - komendy administracyjne systemu klanow.
 * Wymaga permisji klany.admin (domyslnie: op) - sprawdzanej recznie tutaj,
 * NIEZALEZNIE od "permission:" w plugin.yml (dzieki temu dziala tez, gdy ktos
 * poda permission-message albo command blocki wywoluja komende jako konsola).
 */
public class AdminKlanCommand implements CommandExecutor, TabCompleter {

    private final KlanManager manager;
    private final KlanWalutaManager walutaManager;
    private final Runnable reloadKonfiguracji;

    private static final List<String> SUBKOMENDY = List.of(
            "usun", "ustawodlamki", "dodajodlamki", "setpoints", "info", "sprawdz", "reload", "pomoc", "ustawodlamek"
    );
    private static final List<String> SUBKOMENDY_Z_TAGIEM = List.of(
            "usun", "ustawodlamki", "dodajodlamki", "info", "sprawdz"
    );

    public AdminKlanCommand(KlanManager manager, KlanWalutaManager walutaManager, Runnable reloadKonfiguracji) {
        this.manager = manager;
        this.walutaManager = walutaManager;
        this.reloadKonfiguracji = reloadKonfiguracji;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("klany.admin")) {
            Msg.send(sender, "brak-uprawnien");
            return true;
        }

        if (args.length == 0) {
            pomoc(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "usun" -> usun(sender, args);
            case "ustawodlamki" -> ustawOdlamki(sender, args);
            case "dodajodlamki" -> dodajOdlamki(sender, args);
            case "setpoints" -> setPoints(sender, args);
            case "info" -> info(sender, args);
            case "sprawdz" -> sprawdz(sender, args);
            case "reload" -> reload(sender);
            case "ustawodlamek" -> ustawOdlamek(sender);
            default -> pomoc(sender);
        }
        return true;
    }

    private Klan znajdzKlan(CommandSender sender, String tag) {
        Klan klan = manager.getKlanByTag(tag);
        if (klan == null) {
            Msg.sendGui(sender, "&cKlan o tagu &e" + tag + " &cnie istnieje.");
        }
        return klan;
    }

    private Integer parsujLiczbe(CommandSender sender, String tekst) {
        try {
            return Integer.parseInt(tekst);
        } catch (NumberFormatException e) {
            Msg.sendGui(sender, "&cPodana ilosc musi byc liczba calkowita.");
            return null;
        }
    }

    private void usun(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.sendGui(sender, "&cUzycie: /adminklan usun <tag>");
            return;
        }
        Klan klan = znajdzKlan(sender, args[1]);
        if (klan == null) return;
        String tag = klan.getTag();
        manager.usunKlan(klan);
        Msg.sendGui(sender, "&aUsunieto klan &e" + tag + " &a(admin).");
    }

    private void ustawOdlamki(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.sendGui(sender, "&cUzycie: /adminklan ustawodlamki <tag> <ilosc>");
            return;
        }
        Klan klan = znajdzKlan(sender, args[1]);
        if (klan == null) return;
        Integer ilosc = parsujLiczbe(sender, args[2]);
        if (ilosc == null) return;

        klan.setSkarbiec(ilosc);
        manager.zapiszSkarbiec(klan);
        manager.dodajLog(klan, sender.getName() + " ustawil skarbiec na " + ilosc + " odlamkow (admin).");
        Msg.sendGui(sender, "&aUstawiono skarbiec klanu &e" + klan.getTag() + " &ana &e" + ilosc + " &aodlamkow.");
    }

    private void dodajOdlamki(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.sendGui(sender, "&cUzycie: /adminklan dodajodlamki <tag> <ilosc>");
            return;
        }
        Klan klan = znajdzKlan(sender, args[1]);
        if (klan == null) return;
        Integer ilosc = parsujLiczbe(sender, args[2]);
        if (ilosc == null) return;

        klan.dodajDoSkarbca(ilosc);
        manager.zapiszSkarbiec(klan);
        manager.dodajLog(klan, sender.getName() + " dodal " + ilosc + " odlamkow do skarbca (admin).");
        Msg.sendGui(sender, "&aDodano &e" + ilosc + " &aodlamkow do skarbca klanu &e" + klan.getTag() + "&a. Nowy stan: &e" + klan.getSkarbiec() + "&a.");
    }

    private void setPoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.sendGui(sender, "&cUzycie: /adminklan setpoints <gracz> <ilosc>");
            return;
        }
        OfflinePlayer cel = Bukkit.getOfflinePlayer(args[1]);
        if (!cel.hasPlayedBefore() && !cel.isOnline()) {
            Msg.sendGui(sender, "&cGracz &e" + args[1] + " &cnigdy nie byl na serwerze.");
            return;
        }
        Integer ilosc = parsujLiczbe(sender, args[2]);
        if (ilosc == null) return;

        Klan klan = manager.getKlanGracza(cel.getUniqueId());
        if (klan == null) {
            Msg.sendGui(sender, "&cGracz &e" + cel.getName() + " &cnie nalezy do zadnego klanu - ranking ELO jest przypisany do gracza w ramach klanu, wiec nie mozna go ustawic.");
            return;
        }

        manager.ustawRanking(cel.getUniqueId(), ilosc);
        manager.dodajLog(klan, sender.getName() + " ustawil ranking gracza " + cel.getName() + " na " + ilosc + " (admin).");
        Msg.sendGui(sender, "&aUstawiono ranking gracza &e" + cel.getName() + " &ana &e" + ilosc + "&a.");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.sendGui(sender, "&cUzycie: /adminklan info <tag>");
            return;
        }
        Klan klan = znajdzKlan(sender, args[1]);
        if (klan == null) return;

        sender.sendMessage(Msg.color("&8&m----&r &b" + klan.getNazwa() + " &7[&f" + klan.getTag() + "&7] &8&m----"));
        sender.sendMessage(Msg.color("&7ID: &f" + klan.getId()));
        sender.sendMessage(Msg.color("&7Czlonkowie: &f" + klan.getLiczbaCzlonkow()));
        sender.sendMessage(Msg.color("&7Skarbiec (odlamki): &f" + klan.getSkarbiec()));
        sender.sendMessage(Msg.color("&7PvP wewnetrzne: &f" + (klan.isPvpWlaczone() ? "Wlaczone" : "Wylaczone")));
        sender.sendMessage(Msg.color("&7Suma zabojstw klanu: &f" + manager.getSumaZabojstwKlanu(klan)));
        sender.sendMessage(Msg.color("&8&m-------------------------------"));
    }

    /** /adminklan sprawdz <tag> - otwiera GUI (tylko do odczytu) z ulepszeniami klanu. */
    private void sprawdz(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.sendGui(sender, "&cTa komenda jest dostepna tylko dla graczy.");
            return;
        }
        if (args.length < 2) {
            Msg.sendGui(sender, "&cUzycie: /adminklan sprawdz <tag>");
            return;
        }
        Klan klan = znajdzKlan(sender, args[1]);
        if (klan == null) return;
        new UlepszeniaGui(klan, true).otworz(player);
    }

    private void reload(CommandSender sender) {
        reloadKonfiguracji.run();
        Msg.sendGui(sender, "&aPrzeladowano klany-config.yml.");
    }

    /** Ustawia szablon odlamka klanowego z pelnym NBT z itemu w rece. */
    private void ustawOdlamek(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.sendGui(sender, "&cTa komenda jest dostepna tylko dla graczy.");
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) {
            Msg.sendGui(sender, "&cMusisz trzymac przedmiot, ktory ma byc Smoczym Odlamkiem!");
            return;
        }
        walutaManager.ustawOdlamek(tool);
        Msg.sendGui(sender, "&aPomyslnie ustawiono trzymany przedmiot jako &5Smoczy Odlamek &a(klanowy)!");
        Msg.sendGui(sender, "&7Zapisano z pelnym NBT do &fklany-odlamek.yml");
    }

    private void pomoc(CommandSender sender) {
        List<String> linie = List.of(
                "&8&m--------------------&r &4&lADMIN KLANY &8&m--------------------",
                "&c/adminklan usun &7<tag> &8- &fUsuwa dowolny klan",
                "&c/adminklan ustawodlamki &7<tag> <ilosc> &8- &fUstawia skarbiec (odlamki) klanu",
                "&c/adminklan dodajodlamki &7<tag> <ilosc> &8- &fDodaje odlamki do skarbca klanu",
                "&c/adminklan setpoints &7<gracz> <ilosc> &8- &fUstawia ranking ELO gracza",
                "&c/adminklan info &7<tag> &8- &fSzczegolowe informacje o klanie",
                "&c/adminklan sprawdz &7<tag> &8- &fPodglad ulepszen klanu (GUI)",
                "&c/adminklan ustawodlamek &8- &fUstawia Smoczy Odlamek (trzymaj w rece)",
                "&c/adminklan reload &8- &fPrzeladowuje klany-config.yml",
                "&c/aresetranking all &8- &fResetuje ranking/statystyki PvP wszystkich graczy (tylko konsola)",
                "&8&m-------------------------------------------------"
        );
        linie.forEach(l -> sender.sendMessage(Msg.color(l)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("klany.admin")) return List.of();

        if (args.length == 1) {
            return SUBKOMENDY.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && SUBKOMENDY_Z_TAGIEM.contains(args[0].toLowerCase(Locale.ROOT))) {
            return manager.getWszystkieKlany().stream()
                    .map(Klan::getTag)
                    .filter(tag -> tag.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setpoints")) {
            String start = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(nick -> nick.toLowerCase(Locale.ROOT).startsWith(start))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
