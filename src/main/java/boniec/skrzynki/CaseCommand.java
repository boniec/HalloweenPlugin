package boniec.skrzynki;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * /acase stworz|usun|ustawlokacje|usunlokacje|edytujdrop|typ  (+ klucz, daj, lista, reload, wylacz, wlacz)
 */
public final class CaseCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "&8[&6Skrzynki&8] ";
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private static final List<String> SUBS = List.of(
            "stworz", "usun", "ustawlokacje", "usunlokacje", "edytujdrop", "typ", "klucz", "daj", "lista", "reload",
            "wylacz", "wlacz");

    private final CaseManager mgr;
    private final CaseListener listener;

    public CaseCommand(CaseManager mgr, CaseListener listener) {
        this.mgr = mgr;
        this.listener = listener;
    }

    private static void say(CommandSender s, String text) {
        s.sendMessage(Txt.color(PREFIX + text));
    }

    private void usage(CommandSender s) {
        say(s, "&e/acase stworz <nazwa> [SKRZYNIA|LOOTBOX] &7- nowa skrzynka");
        say(s, "&e/acase usun <nazwa> &7- usuwa skrzynke (z potwierdzeniem)");
        say(s, "&e/acase ustawlokacje <nazwa> &7- blok, na ktory patrzysz: SKRZYNIA (otwieranie) albo LOOTBOX (sam podglad)");
        say(s, "&e/acase usunlokacje <nazwa> [all] &7- usuwa lokalizacje (blok na ktory patrzysz albo wszystkie)");
        say(s, "&e/acase edytujdrop <nazwa> &7- edytor dropu (GUI)");
        say(s, "&e/acase typ <nazwa> <SKRZYNIA|LOOTBOX> &7- zmienia typ skrzynki");
        say(s, "&e/acase klucz <nazwa> &7- ustawia klucz na przedmiot z reki");
        say(s, "&e/acase daj <nazwa> <gracz|all|*> [ilosc] &7- daje klucz (takze wszystkim online)");
        say(s, "&e/acase lista &7| &e/acase reload");
        say(s, "&e/acase wylacz &7<&eskrzynka&7|&eall&7> &7- wylacza otwieranie");
        say(s, "&e/acase wlacz &7<&eskrzynka&7|&eall&7> &7- wlacza otwieranie");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "stworz" -> create(sender, args);
            case "usun" -> delete(sender, args);
            case "ustawlokacje" -> setLocation(sender, args);
            case "usunlokacje" -> removeLocation(sender, args);
            case "edytujdrop" -> editDrop(sender, args);
            case "typ" -> setType(sender, args);
            case "klucz" -> setKey(sender, args);
            case "daj" -> give(sender, args);
            case "lista" -> list(sender);
            case "reload" -> {
                mgr.reload();
                say(sender, "&aPrzeladowano skrzynki (skrzynki-config.yml i skrzynki.yml).");
            }
            case "wylacz" -> setEnabled(sender, args, false);
            case "wlacz" -> setEnabled(sender, args, true);
            default -> usage(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------ podkomendy

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) {
            say(s, "&cUzycie: /acase stworz <nazwa> [SKRZYNIA|LOOTBOX]");
            return;
        }
        if (!ID.matcher(a[1]).matches()) {
            say(s, "&cNazwa: 1-32 znakow (litery, cyfry, _ i -).");
            return;
        }
        if (mgr.get(a[1]) != null) {
            say(s, "&cSkrzynka &e" + a[1] + " &cjuz istnieje.");
            return;
        }
        CaseType type = CaseType.SKRZYNIA;
        if (a.length >= 3) {
            type = CaseType.parse(a[2], null);
            if (type == null) {
                say(s, "&cNieznany typ. Dostepne: SKRZYNIA, LOOTBOX.");
                return;
            }
        }
        CaseDef c = mgr.create(a[1], type);
        say(s, "&aUtworzono skrzynke &e" + c.id + " &7(typ: &f" + type + "&7).");
        if (type == CaseType.SKRZYNIA) {
            say(s, "&7Dalej: &e/acase ustawlokacje " + c.id + " &7(patrzac na blok), &e/acase edytujdrop " + c.id
                    + " &7i &e/acase klucz " + c.id + " &7(klucz z reki).");
        } else {
            say(s, "&7Dalej: wez przedmiot do reki i &e/acase klucz " + c.id
                    + "&7, &e/acase edytujdrop " + c.id + "&7, opcjonalnie &e/acase ustawlokacje " + c.id
                    + " &7(blok = podglad bez otwierania).");
        }
    }

    private void delete(CommandSender s, String[] a) {
        CaseDef c = need(s, a, 1);
        if (c == null) return;
        if (a.length < 3 || !a[2].equalsIgnoreCase("potwierdz")) {
            say(s, "&cTo skasuje skrzynke &e" + c.id + " &cwraz z dropem (" + c.drops.size()
                    + " pozycji). Potwierdz: &e/acase usun " + c.id + " potwierdz");
            return;
        }
        mgr.delete(c);
        say(s, "&aUsunieto skrzynke &e" + c.id + "&a.");
    }

    private void setLocation(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        CaseDef c = need(s, a, 1);
        if (c == null) return;
        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType().isAir()) {
            say(s, "&cPatrz na blok (max 6 kratek).");
            return;
        }
        if (!mgr.addLocation(c, b)) {
            say(s, "&cTen blok jest juz lokalizacja jakiejs skrzynki.");
            return;
        }
        say(s, "&aUstawiono lokalizacje skrzynki &e" + c.id + " &7(" + b.getX() + ", " + b.getY() + ", " + b.getZ()
                + "). &7Razem lokalizacji: &f" + c.locations.size());
        if (c.type == CaseType.LOOTBOX) {
            say(s, "&7Ten blok pokazuje tylko podglad dropu (bez przyciskow otwierania). Otwieranie: PPM lootboxem w rece.");
        }
    }

    private void removeLocation(CommandSender s, String[] a) {
        CaseDef c = need(s, a, 1);
        if (c == null) return;
        if (a.length >= 3 && a[2].equalsIgnoreCase("all")) {
            int n = mgr.clearLocations(c);
            say(s, "&aUsunieto wszystkie lokalizacje (&f" + n + "&a) skrzynki &e" + c.id + "&a.");
            return;
        }
        Player p = player(s);
        if (p == null) return;
        Block b = p.getTargetBlockExact(6);
        if (b == null || !mgr.removeLocation(c, b)) {
            say(s, "&cPatrz na blok bedacy lokalizacja skrzynki &e" + c.id + "&c (albo uzyj &e/acase usunlokacje "
                    + c.id + " all&c).");
            return;
        }
        say(s, "&aUsunieto lokalizacje skrzynki &e" + c.id + "&a.");
    }

    private void editDrop(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        CaseDef c = need(s, a, 1);
        if (c == null) return;
        listener.guis().openEditor(p, c);
    }

    private void setType(CommandSender s, String[] a) {
        CaseDef c = need(s, a, 1);
        if (c == null) return;
        if (a.length < 3) {
            say(s, "&cUzycie: /acase typ <nazwa> <SKRZYNIA|LOOTBOX>  &7(obecnie: &f" + c.type + "&7)");
            return;
        }
        CaseType type = CaseType.parse(a[2], null);
        if (type == null) {
            say(s, "&cNieznany typ. Dostepne: SKRZYNIA, LOOTBOX.");
            return;
        }
        c.type = type;
        mgr.save();
        say(s, "&aTyp skrzynki &e" + c.id + " &austawiony na &f" + type + "&a.");
        if (type == CaseType.LOOTBOX) {
            say(s, "&7LOOTBOX: PPM w rece otwiera, Shift+PPM od razu, LPM / postawiony blok = podglad bez otwierania. "
                    + "Ustaw przedmiot: &e/acase klucz " + c.id);
        } else {
            say(s, "&7SKRZYNIA: klik w zapisany blok = podglad, Shift+PPM = otwarcie od razu (potrzebny klucz).");
        }
    }

    private void setKey(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        CaseDef c = need(s, a, 1);
        if (c == null) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            say(s, "&cWez do reki przedmiot, ktory ma byc kluczem / lootboxem.");
            return;
        }
        c.key = ItemSpec.fromItem(hand);
        mgr.save();
        say(s, "&aKlucz skrzynki &e" + c.id + " &austawiony: &f" + c.keyLabel()
                + " &7(rozpoznawany po materiale" + (c.key.name != null ? ", nazwie" : "")
                + (c.key.customModelData != null ? " i custom-model-data" : "") + ").");
    }

    private void give(CommandSender s, String[] a) {
        CaseDef c = need(s, a, 1);
        if (c == null) return;
        if (a.length < 3) {
            say(s, "&cUzycie: /acase daj <nazwa> <gracz|all|*> [ilosc]");
            return;
        }
        int amount = 1;
        if (a.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
            } catch (NumberFormatException ex) {
                say(s, "&cIlosc musi byc liczba (1-64).");
                return;
            }
        }
        String kto = a[2];
        boolean wszyscy = kto.equalsIgnoreCase("all") || kto.equals("*") || kto.equalsIgnoreCase("wszyscy");
        if (wszyscy) {
            int n = 0;
            for (Player t : Bukkit.getOnlinePlayers()) {
                dajKlucz(t, c, amount);
                n++;
            }
            say(s, "&aDano &f" + amount + "x &r" + c.keyLabel() + " &a" + n + " graczom na serwerze.");
            return;
        }
        Player t = Bukkit.getPlayerExact(kto);
        if (t == null) {
            say(s, "&cGracz &e" + kto + " &cjest offline.");
            return;
        }
        dajKlucz(t, c, amount);
        say(s, "&aDano &f" + amount + "x &r" + c.keyLabel() + " &agraczowi &e" + t.getName() + "&a.");
    }

    private void dajKlucz(Player t, CaseDef c, int amount) {
        ItemStack stack = c.key.build(mgr.basePlaceholders(c), false);
        stack.setAmount(amount);
        Map<Integer, ItemStack> left = t.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) t.getWorld().dropItemNaturally(t.getLocation(), rest);
    }

    private void list(CommandSender s) {
        if (mgr.all().isEmpty()) {
            say(s, "&7Brak skrzynek. Utworz: &e/acase stworz <nazwa>");
            return;
        }
        say(s, "&7Skrzynki (" + mgr.all().size() + "):");
        for (CaseDef c : mgr.all()) {
            say(s, " &8- &e" + c.id + " &7typ: &f" + c.type + " &7drop: &f" + c.drops.size()
                    + " &7lokalizacje: &f" + c.locations.size()
                    + (c.enabled ? " &a[WL]" : " &c[WYL]"));
        }
    }

    private void setEnabled(CommandSender s, String[] a, boolean on) {
        String cmd = on ? "wlacz" : "wylacz";
        if (a.length < 2) {
            say(s, "&cUzycie: /acase " + cmd + " <skrzynka|all>");
            return;
        }
        if (a[1].equalsIgnoreCase("all") || a[1].equals("*")) {
            int n = 0;
            for (CaseDef c : mgr.all()) {
                c.enabled = on;
                n++;
            }
            mgr.save();
            if (on) say(s, "&aWlaczono wszystkie skrzynki &7(&f" + n + "&7).");
            else say(s, "&cWylaczono wszystkie skrzynki &7(&f" + n + "&7).");
            return;
        }
        CaseDef c = need(s, a, 1);
        if (c == null) return;
        c.enabled = on;
        mgr.save();
        if (on) say(s, "&aWlaczono skrzynke &e" + c.id + "&a.");
        else say(s, "&cWylaczono skrzynke &e" + c.id + "&c.");
    }

    // ------------------------------------------------------------------ pomocnicze

    private CaseDef need(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) {
            say(s, "&cPodaj nazwe skrzynki. &7(/acase lista)");
            return null;
        }
        CaseDef c = mgr.get(a[idx]);
        if (c == null) say(s, "&cNie ma skrzynki &e" + a[idx] + "&c. &7(/acase lista)");
        return c;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        say(s, "&cTa komenda jest tylko dla graczy.");
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBS) if (sub.startsWith(pre)) out.add(sub);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && !sub.equals("stworz") && !sub.equals("lista") && !sub.equals("reload")) {
            String pre = args[1].toLowerCase(Locale.ROOT);
            for (CaseDef c : mgr.all()) if (c.id.startsWith(pre)) out.add(c.id);
            if (sub.equals("wylacz") || sub.equals("wlacz")) {
                if ("all".startsWith(pre)) out.add("all");
            }
            return out;
        }
        if (args.length == 3) {
            String pre = args[2].toLowerCase(Locale.ROOT);
            List<String> options = switch (sub) {
                case "typ", "stworz" -> Arrays.asList("SKRZYNIA", "LOOTBOX");
                case "usun" -> List.of("potwierdz");
                case "usunlokacje" -> List.of("all");
                case "daj" -> {
                    List<String> names = new ArrayList<>();
                    names.add("all");
                    names.add("*");
                    names.add("wszyscy");
                    for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                    yield names;
                }
                default -> List.of();
            };
            for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(pre)) out.add(o);
        }
        return out;
    }
}
