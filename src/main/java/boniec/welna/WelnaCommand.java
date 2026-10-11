package boniec.welna;

import boniec.klany.util.Msg;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /awelna dodaj <red|green|purple|white> <szansa%>   - dodaje przedmiot z reki jako drop z danej welny
 * /awelna usun <welna> <numer>
 * /awelna szansa <welna> <numer> <szansa%>
 * /awelna lista [welna]
 * /awelna reload
 */
public class WelnaCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "&8[&aWelna&8] &r";
    private static final List<String> PODKOMENDY = List.of("dodaj", "usun", "szansa", "lista", "reload");
    private static final List<String> WELNY = List.of("red", "green", "purple", "white");

    private final WelnaDropManager manager;

    public WelnaCommand(WelnaDropManager manager) {
        this.manager = manager;
    }

    private void msg(CommandSender s, String tekst) {
        s.sendMessage(Msg.color(PREFIX + tekst));
    }

    private void uzycie(CommandSender s) {
        msg(s, "&cUzycie:");
        s.sendMessage(Msg.color("&7/awelna dodaj <red|green|purple|white> <szansa%> &8- &fitem z reki jako drop"));
        s.sendMessage(Msg.color("&7/awelna usun <welna> <numer>"));
        s.sendMessage(Msg.color("&7/awelna szansa <welna> <numer> <szansa%>"));
        s.sendMessage(Msg.color("&7/awelna lista [welna]"));
        s.sendMessage(Msg.color("&7/awelna reload"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("welna.admin")) {
            msg(sender, "&cNie masz uprawnien do tej komendy.");
            return true;
        }
        if (args.length == 0) {
            uzycie(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "dodaj", "add", "set" -> dodaj(sender, args);
            case "usun", "remove" -> usun(sender, args);
            case "szansa", "chance" -> szansa(sender, args);
            case "lista", "list" -> lista(sender, args);
            case "reload" -> {
                manager.przeladuj();
                msg(sender, "&aPrzeladowano welna.yml i welnadrop.yml.");
            }
            default -> uzycie(sender);
        }
        return true;
    }

    private void dodaj(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "&cTa komenda jest tylko dla graczy (bierze item z reki).");
            return;
        }
        if (args.length < 3) {
            msg(sender, "&cUzycie: /awelna dodaj <red|green|purple|white> <szansa%>");
            return;
        }
        Material welna = WelnaDropManager.parsujWelne(args[1]);
        if (welna == null) {
            msg(sender, "&cNieznana welna. Dostepne: &fred, green, purple, white");
            return;
        }
        Double szansa = parsujSzanse(args[2]);
        if (szansa == null) {
            msg(sender, "&cSzansa musi byc liczba od 0 do 100 (np. 12.5).");
            return;
        }
        ItemStack wRece = p.getInventory().getItemInMainHand();
        if (wRece == null || wRece.getType().isAir()) {
            msg(sender, "&cMusisz trzymac przedmiot w rece.");
            return;
        }
        manager.dodaj(welna, wRece, szansa);
        msg(sender, "&aDodano &f" + WelnaDropManager.nazwaPrzedmiotu(wRece) + " &7x" + wRece.getAmount()
                + " &ado dropu z &f" + welna.name() + " &8(&e" + WelnaDropManager.formatSzansy(szansa) + "%&8)");
    }

    private void usun(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "&cUzycie: /awelna usun <welna> <numer>");
            return;
        }
        Material welna = WelnaDropManager.parsujWelne(args[1]);
        Integer numer = parsujLiczbe(args[2]);
        if (welna == null || numer == null) {
            msg(sender, "&cPodaj poprawna welne (red|green|purple|white) i numer z /awelna lista.");
            return;
        }
        msg(sender, manager.usun(welna, numer) ? "&aUsunieto wpis #" + numer + " z " + welna.name() + "."
                : "&cNie ma wpisu o takim numerze.");
    }

    private void szansa(CommandSender sender, String[] args) {
        if (args.length < 4) {
            msg(sender, "&cUzycie: /awelna szansa <welna> <numer> <szansa%>");
            return;
        }
        Material welna = WelnaDropManager.parsujWelne(args[1]);
        Integer numer = parsujLiczbe(args[2]);
        Double szansa = parsujSzanse(args[3]);
        if (welna == null || numer == null || szansa == null) {
            msg(sender, "&cPodaj poprawna welne, numer i szanse (0-100).");
            return;
        }
        msg(sender, manager.ustawSzanse(welna, numer, szansa)
                ? "&aUstawiono szanse wpisu #" + numer + " na &e" + WelnaDropManager.formatSzansy(szansa) + "%"
                : "&cNie ma wpisu o takim numerze.");
    }

    private void lista(CommandSender sender, String[] args) {
        List<Material> welny = new ArrayList<>();
        if (args.length >= 2) {
            Material m = WelnaDropManager.parsujWelne(args[1]);
            if (m == null) {
                msg(sender, "&cNieznana welna. Dostepne: &fred, green, purple, white");
                return;
            }
            welny.add(m);
        } else {
            welny.addAll(WelnaDropManager.OBSLUGIWANE);
        }
        for (Material m : welny) {
            List<WelnaDropManager.Wpis> wpisy = manager.getWpisy(m);
            sender.sendMessage(Msg.color("&a" + m.name() + " &8(" + wpisy.size() + ")"));
            if (wpisy.isEmpty()) sender.sendMessage(Msg.color("  &7brak dropow"));
            for (int i = 0; i < wpisy.size(); i++) {
                WelnaDropManager.Wpis w = wpisy.get(i);
                sender.sendMessage(Msg.color("  &e#" + (i + 1) + " &f" + WelnaDropManager.nazwaPrzedmiotu(w.getItem())
                        + " &7x" + w.getItem().getAmount() + " &8- &e" + WelnaDropManager.formatSzansy(w.getSzansa()) + "%"));
            }
        }
    }

    private static Double parsujSzanse(String s) {
        try {
            double d = Double.parseDouble(s.replace("%", "").replace(',', '.'));
            if (Double.isNaN(d) || d < 0 || d > 100) return null;
            return d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parsujLiczbe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> wynik = new ArrayList<>();
        if (!sender.hasPermission("welna.admin")) return wynik;
        if (args.length == 1) {
            for (String s : PODKOMENDY) if (s.startsWith(args[0].toLowerCase())) wynik.add(s);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            for (String s : WELNY) if (s.startsWith(args[1].toLowerCase())) wynik.add(s);
        }
        return wynik;
    }
}
