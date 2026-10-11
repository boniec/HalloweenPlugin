package boniec.nekromanta;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /anekromanta dodaj <100|200|300> <szansa%> | usun <pakiet> <numer> | szansa <pakiet> <numer> <szansa%>
 *              | lista [pakiet] | glut [gracz] [ilosc]
 *              | saldo <gracz> [dodaj|ustaw <ilosc>] | poziom <gracz> [ustaw <n>] | reload
 */
public class ANekromantaCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "&8[&5Nekromanta&8] &r";
    private static final List<String> PODKOMENDY = List.of("dodaj", "usun", "szansa", "lista", "glut", "czerwonyglut", "ustawglut", "saldo", "poziom", "reload");

    private final NekromantaManager manager;

    public ANekromantaCommand(NekromantaManager manager) {
        this.manager = manager;
    }

    private void msg(CommandSender s, String tekst) {
        s.sendMessage(Msg.color(PREFIX + tekst));
    }

    private void uzycie(CommandSender s) {
        msg(s, "&cUzycie:");
        s.sendMessage(Msg.color("&7/anekromanta dodaj <100|200|300> <szansa%> &8- &fitem z reki jako nagroda"));
        s.sendMessage(Msg.color("&7/anekromanta usun <pakiet> <numer>"));
        s.sendMessage(Msg.color("&7/anekromanta szansa <pakiet> <numer> <szansa%>"));
        s.sendMessage(Msg.color("&7/anekromanta lista [pakiet]"));
        s.sendMessage(Msg.color("&7/anekromanta glut [gracz] [ilosc] &8- &fwydaje gluty"));
        s.sendMessage(Msg.color("&7/anekromanta czerwonyglut [gracz] [ilosc] &8- &fwydaje czerwone gluty (1 = 10)"));
        s.sendMessage(Msg.color("&7/anekromanta ustawglut [reset] &8- &fitem z reki jako glut (reset = glut z configu)"));
        s.sendMessage(Msg.color("&7/anekromanta saldo <gracz> [dodaj|ustaw <ilosc>] &8- &fsaldo wplaconych glutow"));
        s.sendMessage(Msg.color("&7/anekromanta poziom <gracz> [ustaw <n>] &8- &fpoziom ulepszen"));
        s.sendMessage(Msg.color("&7/anekromanta reload"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("nekromanta.admin")) {
            msg(sender, "&cNie masz uprawnien do tej komendy.");
            return true;
        }
        if (args.length == 0) {
            uzycie(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "dodaj", "add" -> dodaj(sender, args);
            case "usun", "remove" -> usun(sender, args);
            case "szansa", "chance" -> szansa(sender, args);
            case "lista", "list" -> lista(sender, args);
            case "glut", "gluty" -> glut(sender, args);
            case "czerwonyglut", "redglut" -> czerwonyGlut(sender, args);
            case "ustawglut", "setglut" -> ustawGlut(sender, args);
            case "saldo" -> saldo(sender, args);
            case "poziom" -> poziom(sender, args);
            case "reload" -> {
                manager.przeladuj(); // zapis + odczyt salda + config
                sender.sendMessage(Msg.color(PREFIX + manager.cfg().getString("wiadomosci.reload", "&aPrzeladowano.")));
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
            msg(sender, "&cUzycie: /anekromanta dodaj <100|200|300> <szansa%>");
            return;
        }
        NekromantaManager.Tier t = manager.znajdzTier(args[1]);
        Double szansa = parsujSzanse(args[2]);
        if (t == null || szansa == null) {
            msg(sender, "&cPodaj poprawny pakiet (koszt: " + koszty() + ") i szanse 0-100.");
            return;
        }
        ItemStack wRece = p.getInventory().getItemInMainHand();
        if (wRece.getType().isAir()) {
            msg(sender, "&cMusisz trzymac przedmiot (miecz) w rece.");
            return;
        }
        manager.dodaj(t, wRece, szansa);
        msg(sender, "&aDodano &f" + NekromantaManager.nazwaPrzedmiotu(wRece) + " &ado pakietu &f" + t.koszt
                + " &8(&e" + NekromantaManager.formatSzansy(szansa) + "%&8)");
        ostrzezSume(sender, t);
    }

    private void usun(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "&cUzycie: /anekromanta usun <pakiet> <numer>");
            return;
        }
        NekromantaManager.Tier t = manager.znajdzTier(args[1]);
        Integer numer = parsujLiczbe(args[2]);
        if (t == null || numer == null) {
            msg(sender, "&cPodaj poprawny pakiet (" + koszty() + ") i numer z /anekromanta lista.");
            return;
        }
        if (manager.usun(t, numer)) {
            msg(sender, "&aUsunieto wpis #" + numer + " z pakietu " + t.koszt + ".");
            ostrzezSume(sender, t);
        } else {
            msg(sender, "&cNie ma wpisu o takim numerze.");
        }
    }

    private void szansa(CommandSender sender, String[] args) {
        if (args.length < 4) {
            msg(sender, "&cUzycie: /anekromanta szansa <pakiet> <numer> <szansa%>");
            return;
        }
        NekromantaManager.Tier t = manager.znajdzTier(args[1]);
        Integer numer = parsujLiczbe(args[2]);
        Double szansa = parsujSzanse(args[3]);
        if (t == null || numer == null || szansa == null) {
            msg(sender, "&cPodaj poprawny pakiet, numer i szanse (0-100).");
            return;
        }
        if (manager.ustawSzanse(t, numer, szansa)) {
            msg(sender, "&aUstawiono szanse wpisu #" + numer + " na &e" + NekromantaManager.formatSzansy(szansa) + "%");
            ostrzezSume(sender, t);
        } else {
            msg(sender, "&cNie ma wpisu o takim numerze.");
        }
    }

    private void lista(CommandSender sender, String[] args) {
        List<NekromantaManager.Tier> tiery = new ArrayList<>();
        if (args.length >= 2) {
            NekromantaManager.Tier t = manager.znajdzTier(args[1]);
            if (t == null) {
                msg(sender, "&cNieznany pakiet. Dostepne: &f" + koszty());
                return;
            }
            tiery.add(t);
        } else {
            tiery.addAll(manager.getTiery());
        }
        for (NekromantaManager.Tier t : tiery) {
            List<NekromantaManager.Wpis> wpisy = manager.getWpisy(t);
            sender.sendMessage(Msg.color("&5Pakiet " + t.koszt + " glutow &8(" + wpisy.size() + " nagrod, suma szans: &e"
                    + NekromantaManager.formatSzansy(manager.sumaSzans(t)) + "%&8)"));
            if (wpisy.isEmpty()) sender.sendMessage(Msg.color("  &7brak nagrod"));
            for (int i = 0; i < wpisy.size(); i++) {
                NekromantaManager.Wpis w = wpisy.get(i);
                sender.sendMessage(Msg.color("  &e#" + (i + 1) + " &f" + NekromantaManager.nazwaPrzedmiotu(w.getItem())
                        + " &8- &e" + NekromantaManager.formatSzansy(w.getSzansa()) + "% &8(realnie: &a"
                        + NekromantaManager.formatSzansy(manager.szansaEfektywna(t, w)) + "%&8)"));
            }
        }
    }

    private void glut(CommandSender sender, String[] args) {
        Player cel = null;
        int ilosc = 1;
        int idx = 1;
        if (args.length > idx) {
            Player znaleziony = Bukkit.getPlayerExact(args[idx]);
            if (znaleziony != null) {
                cel = znaleziony;
                idx++;
            }
        }
        if (cel == null && sender instanceof Player p) cel = p;
        if (cel == null) {
            msg(sender, "&cPodaj gracza: /anekromanta glut <gracz> [ilosc]");
            return;
        }
        if (args.length > idx) {
            Integer n = parsujLiczbe(args[idx]);
            if (n == null || n < 1 || n > 10000) {
                msg(sender, "&cIlosc musi byc liczba od 1 do 10000.");
                return;
            }
            ilosc = n;
        }
        manager.dajGluty(cel, ilosc);
        msg(sender, "&aWydano &f" + ilosc + " &aglutow graczowi &f" + cel.getName() + "&a.");
    }

    private void czerwonyGlut(CommandSender sender, String[] args) {
        Player cel = null;
        int ilosc = 1;
        int idx = 1;
        if (args.length > idx) {
            Player znaleziony = Bukkit.getPlayerExact(args[idx]);
            if (znaleziony != null) {
                cel = znaleziony;
                idx++;
            }
        }
        if (cel == null && sender instanceof Player p) cel = p;
        if (cel == null) {
            msg(sender, "&cPodaj gracza: /anekromanta czerwonyglut <gracz> [ilosc]");
            return;
        }
        if (args.length > idx) {
            Integer n = parsujLiczbe(args[idx]);
            if (n == null || n < 1 || n > 10000) {
                msg(sender, "&cIlosc musi byc liczba od 1 do 10000.");
                return;
            }
            ilosc = n;
        }
        manager.dajItem(cel, manager.stworzCzerwonyGlut(ilosc));
        msg(sender, "&aWydano &f" + ilosc + " &aczerwonych glutow graczowi &f" + cel.getName() + "&a (wartosc: " + (ilosc * 10) + " glutow).");
    }

    private void ustawGlut(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            msg(sender, manager.resetujGlut()
                    ? "&aPrzywrocono domyslnego gluta z nekromanta.yml."
                    : "&eNie ustawiono wlasnego gluta - uzywany jest glut z nekromanta.yml.");
            return;
        }
        if (!(sender instanceof Player p)) {
            msg(sender, "&cTa komenda jest tylko dla graczy (bierze item z reki).");
            return;
        }
        ItemStack wRece = p.getInventory().getItemInMainHand();
        if (wRece.getType().isAir()) {
            msg(sender, "&cMusisz trzymac w rece przedmiot, ktory ma byc glutem.");
            return;
        }
        // Item w rece zostaje podmieniony na wersje z tagiem gluta (od razu nadaje sie do /awelna dodaj i wymiany).
        p.getInventory().setItemInMainHand(manager.ustawGlut(wRece));
        msg(sender, "&aUstawiono gluta na &f" + NekromantaManager.nazwaPrzedmiotu(wRece)
                + "&a. Stare gluty z configu nadal dzialaja w wymianie.");
    }

    private void saldo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cUzycie: /anekromanta saldo <gracz> [dodaj|ustaw <ilosc>]");
            return;
        }
        Player cel = Bukkit.getPlayerExact(args[1]);
        if (cel == null) {
            msg(sender, "&cGracz &f" + args[1] + " &cmusi byc online.");
            return;
        }
        if (args.length == 2) {
            msg(sender, "&7Saldo &f" + cel.getName() + "&7: &a" + manager.saldo(cel.getUniqueId()) + " &7glutow.");
            return;
        }
        Integer n = args.length >= 4 ? parsujLiczbe(args[3]) : null;
        if (n == null) {
            msg(sender, "&cUzycie: /anekromanta saldo <gracz> <dodaj|ustaw> <ilosc>");
            return;
        }
        switch (args[2].toLowerCase()) {
            case "dodaj", "add" -> manager.dodajDoSalda(cel.getUniqueId(), n);
            case "ustaw", "set" -> {
                if (n < 0) {
                    msg(sender, "&cSaldo nie moze byc ujemne.");
                    return;
                }
                manager.ustawSaldo(cel.getUniqueId(), n);
            }
            default -> {
                msg(sender, "&cUzycie: /anekromanta saldo <gracz> <dodaj|ustaw> <ilosc>");
                return;
            }
        }
        msg(sender, "&aSaldo &f" + cel.getName() + "&a: &f" + manager.saldo(cel.getUniqueId()) + " &aglutow.");
    }

    private void poziom(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cUzycie: /anekromanta poziom <gracz> [ustaw <n>]");
            return;
        }
        Player cel = Bukkit.getPlayerExact(args[1]);
        if (cel == null) {
            msg(sender, "&cGracz &f" + args[1] + " &cmusi byc online.");
            return;
        }
        if (args.length >= 4 && (args[2].equalsIgnoreCase("ustaw") || args[2].equalsIgnoreCase("set"))) {
            Integer n = parsujLiczbe(args[3]);
            if (n == null || n < 0 || n > manager.maxPoziom()) {
                msg(sender, "&cPoziom musi byc liczba od 0 do " + manager.maxPoziom() + ".");
                return;
            }
            manager.ustawPoziom(cel.getUniqueId(), n);
        }
        int poz = manager.poziom(cel.getUniqueId());
        msg(sender, "&7Poziom ulepszen &f" + cel.getName() + "&7: &a" + poz + "&8/&a" + manager.maxPoziom()
                + " &8(&e+" + NekromantaManager.formatSzansy(manager.bonusProcent(cel.getUniqueId())) + "% &7do dropu gluta z welny&8)");
    }

    private void ostrzezSume(CommandSender sender, NekromantaManager.Tier t) {
        double suma = manager.sumaSzans(t);
        if (Math.abs(suma - 100.0) > 0.001) {
            msg(sender, "&eUwaga: suma szans w pakiecie " + t.koszt + " to &f" + NekromantaManager.formatSzansy(suma)
                    + "%&e (nie 100%). Szanse beda przeliczone proporcjonalnie - zobacz /anekromanta lista.");
        }
    }

    private String koszty() {
        StringBuilder sb = new StringBuilder();
        for (NekromantaManager.Tier t : manager.getTiery()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.koszt);
        }
        return sb.toString();
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
        if (!sender.hasPermission("nekromanta.admin")) return wynik;
        if (args.length == 1) {
            for (String s : PODKOMENDY) if (s.startsWith(args[0].toLowerCase())) wynik.add(s);
        } else if (args.length == 2) {
            String pod = args[0].toLowerCase();
            if (List.of("dodaj", "usun", "szansa", "lista").contains(pod)) {
                for (NekromantaManager.Tier t : manager.getTiery()) {
                    String k = String.valueOf(t.koszt);
                    if (k.startsWith(args[1])) wynik.add(k);
                }
            } else if (pod.equals("ustawglut")) {
                if ("reset".startsWith(args[1].toLowerCase())) wynik.add("reset");
            } else if (pod.equals("glut") || pod.equals("czerwonyglut") || pod.equals("saldo") || pod.equals("poziom")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) wynik.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            String pod = args[0].toLowerCase();
            List<String> opcje = pod.equals("saldo") ? List.of("dodaj", "ustaw") : pod.equals("poziom") ? List.of("ustaw") : List.of();
            for (String o : opcje) if (o.startsWith(args[2].toLowerCase())) wynik.add(o);
        }
        return wynik;
    }
}
