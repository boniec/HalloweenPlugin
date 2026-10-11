package boniec.szaman.cmd;

import boniec.szaman.SzamanPlugin;
import boniec.szaman.core.DropPerkManager;
import boniec.szaman.core.Perk;
import boniec.szaman.core.PerkManager;
import boniec.szaman.listener.ZyciaListener;
import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Komendy administracyjne Szamana (/aszaman). Uprawnienie sprawdzane jest przez
 * plugin.yml i tutaj (admin.szaman).
 * <p>
 * Waluta: "Fragment Galaktyki" (w komendach wciaz "odlamek", zeby nie zmieniac nazw komend).
 * Kamienie nie istnieja - perki kupuje sie bezposrednio w /szaman.
 */
public class AszamanCommand implements CommandExecutor, TabCompleter {

    private final SzamanPlugin plugin;

    public AszamanCommand(SzamanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("admin.szaman")) {
            Msg.send(sender, "&cBrak uprawnien!");
            return true;
        }

        if (args.length == 0) {
            wyslijPomoc(sender);
            return true;
        }

        String arg1 = args[0].toLowerCase(Locale.ROOT);
        switch (arg1) {
            case "ustawodlamek", "ustawfragment" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Tylko gracze.");
                    return true;
                }
                ustawOdlamek(player);
            }
            case "dajodlamek", "dajfragment" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Tylko gracze.");
                    return true;
                }
                dajOdlamek(player, args);
            }
            case "ustawperk" -> ustawPerk(sender, args);
            case "resetperkow" -> resetPerkow(sender, args);
            case "sprawdz" -> sprawdz(sender, args);
            case "max" -> ustawMaxPoziom(sender, args);
            case "maxdrop" -> ustawMaxDrop(sender, args);
            default -> wyslijPomoc(sender);
        }
        return true;
    }

    // --- USTAW ODLAMEK (Fragment Galaktyki) ---
    private void ustawOdlamek(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) {
            Msg.send(player, "&cMusisz trzymac przedmiot, ktory ma byc Fragmentem Galaktyki!");
            return;
        }
        plugin.getWalutaManager().ustawOdlamek(tool);
        Msg.send(player, "&aPomyslnie ustawiono trzymany przedmiot jako &dFragment Galaktyki&a!");
    }

    // --- DAJ ODLAMEK ---
    private void dajOdlamek(Player player, String[] args) {
        ItemStack odlamek = plugin.getWalutaManager().getOdlamek();
        if (odlamek == null) {
            Msg.send(player, "&cFragment Galaktyki nie jest jeszcze ustawiony!");
            return;
        }
        int ilosc = parsujInt(args, 1, 1);
        odlamek.setAmount(ilosc);
        wydajGraczowi(player, odlamek);
        Msg.send(player, "&aOtrzymales &e" + ilosc + "x &dFragment Galaktyki&a!");
    }

    // --- USTAW PERK ---
    // /aszaman ustawperk (gracz) (perk) (poziom)       - dla confinement/health/strength/vampirism
    // /aszaman ustawperk (gracz) boostdrop (procent)   - dla Boostu Dropu
    private void ustawPerk(CommandSender player, String[] args) {
        if (args.length < 4) {
            Msg.send(player, "&cUzycie: /aszaman ustawperk (gracz) (perk) (poziom)");
            Msg.send(player, "&7Perki: &e" + nazwyPerkow() + "&7 (dla boostdrop podaj procent zamiast poziomu)");
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer cel = Bukkit.getOfflinePlayer(args[1]);
        if (cel == null || (!cel.hasPlayedBefore() && !cel.isOnline())) {
            Msg.send(player, "&cNie znaleziono gracza: &e" + args[1]);
            return;
        }

        Perk perk = Perk.zKlucza(args[2]);
        if (perk == null) {
            Msg.send(player, "&cNieznany perk: &e" + args[2]);
            Msg.send(player, "&7Dostepne: &e" + nazwyPerkow());
            return;
        }

        Double wartosc = parsujDouble(args[3]);
        if (wartosc == null) {
            Msg.send(player, "&cWartosc musi byc liczba!");
            return;
        }

        UUID uuid = cel.getUniqueId();
        if (perk.isPoziomowy()) {
            int max = perk.getMaxPoziom();
            int poziom = (int) Math.round(wartosc);
            if (poziom < 0) poziom = 0;
            if (poziom > max) {
                poziom = max;
                Msg.send(player, "&eMaksymalny poziom tego perku to &f" + max + "&e - przycieto.");
            }
            plugin.getPerkManager().ustawPoziom(uuid, perk, poziom);
            Msg.send(player, "&aUstawiono perk &e" + perk.getKlucz() + " &agracza &f" + cel.getName() + " &ana poziom &e" + poziom + "&a!");
            if (cel.isOnline() && cel.getPlayer() != null) {
                Player celOnline = cel.getPlayer();
                if (perk == Perk.HEALTH) ZyciaListener.odswiezZdrowie(celOnline);
                Msg.send(celOnline, "&d&lSZAMAN &8» &7Admin ustawil Ci perk &e" + perk.getKlucz() + " &7na poziom &a" + poziom + "&7!");
                celOnline.playSound(celOnline.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            }
        } else {
            DropPerkManager dpm = plugin.getDropPerkManager();
            dpm.ustawProcent(uuid, wartosc);
            Msg.send(player, "&aUstawiono Boost Dropu gracza &f" + cel.getName() + " &ana &e" + Perk.formatLiczby(dpm.getProcentGracza(uuid)) + "%&a!");
            if (cel.isOnline() && cel.getPlayer() != null) {
                Msg.send(cel.getPlayer(), "&d&lSZAMAN &8» &7Admin ustawil Ci Boost Dropu na &a" + Perk.formatLiczby(dpm.getProcentGracza(uuid)) + "%&7!");
            }
        }
    }

    // --- SPRAWDZ ---
    // /aszaman sprawdz <nick> - wypisuje poziomy perkow gracza na czacie
    private void sprawdz(CommandSender player, String[] args) {
        if (args.length < 2) {
            Msg.send(player, "&cUzycie: /aszaman sprawdz <nick>");
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer cel = Bukkit.getOfflinePlayer(args[1]);
        if (cel == null || (!cel.hasPlayedBefore() && !cel.isOnline())) {
            Msg.send(player, "&cNie znaleziono gracza: &e" + args[1]);
            return;
        }
        UUID uuid = cel.getUniqueId();
        String nick = cel.getName() != null ? cel.getName() : args[1];
        PerkManager pm = plugin.getPerkManager();
        DropPerkManager dpm = plugin.getDropPerkManager();

        Msg.send(player, "&8&m--------&r &d&lPERKI &f" + nick + " &8&m--------");
        for (Perk perk : Perk.values()) {
            if (perk.isPoziomowy()) {
                int poziom = pm.getPoziom(uuid, perk);
                String wartosc = poziom > 0 ? " &8(&7" + perk.getWartoscTekst(poziom) + "&8)" : "";
                Msg.send(player, "&e" + perk.getKlucz() + "&7: &f" + poziom + "&7/&f" + perk.getMaxPoziom() + wartosc
                        + (perk.isWlaczony() ? "" : " &8[wylaczony w configu]"));
            } else {
                Msg.send(player, "&e" + perk.getKlucz() + "&7: &f" + Perk.formatLiczby(dpm.getProcentGracza(uuid))
                        + "% &7(losowan: &f" + dpm.getIloscLosowan(uuid) + "&7, bonus klanu: &f"
                        + Perk.formatLiczby(dpm.getBonusKlanowy(uuid)) + "%&7)");
            }
        }
    }

    // --- RESET PERKOW ---
    private void resetPerkow(CommandSender player, String[] args) {
        if (args.length < 3) {
            Msg.send(player, "&cUzycie: /aszaman resetperkow (gracz) (all/" + nazwyPerkow().replace(", ", "/") + ")");
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer cel = Bukkit.getOfflinePlayer(args[1]);
        if (cel == null || (!cel.hasPlayedBefore() && !cel.isOnline())) {
            Msg.send(player, "&cNie znaleziono gracza: &e" + args[1]);
            return;
        }

        UUID uuid = cel.getUniqueId();
        PerkManager pm = plugin.getPerkManager();
        String zakres = args[2].toLowerCase();

        if (zakres.equals("all")) {
            pm.resetujWszystko(uuid);
            plugin.getDropPerkManager().resetuj(uuid);
            odswiezZdrowieJesliOnline(cel);
            Msg.send(player, "&aZresetowano &eWSZYSTKIE &aperki gracza &f" + cel.getName() + "&a!");
            return;
        }

        Perk perk = Perk.zKlucza(zakres);
        if (perk == null) {
            Msg.send(player, "&cNieznany perk! Uzyj: all/" + nazwyPerkow().replace(", ", "/"));
            return;
        }
        if (perk.isPoziomowy()) {
            pm.resetujPerk(uuid, perk);
        } else {
            plugin.getDropPerkManager().resetuj(uuid);
        }
        odswiezZdrowieJesliOnline(cel);
        Msg.send(player, "&aZresetowano perk &e" + perk.getKlucz() + " &agracza &f" + cel.getName() + "&a!");
    }

    private void odswiezZdrowieJesliOnline(OfflinePlayer cel) {
        if (cel.isOnline() && cel.getPlayer() != null) {
            ZyciaListener.odswiezZdrowie(cel.getPlayer());
        }
    }

    // --- POMOC ---
    private void wyslijPomoc(CommandSender player) {
        Msg.send(player, "&8&m--------&r &d&lPANEL ASZAMAN &8&m--------");
        Msg.send(player, "&e/aszaman ustawodlamek &7- ustawia trzymany item jako Fragment Galaktyki (waluta)");
        Msg.send(player, "&e/aszaman dajodlamek [ilosc]");
        Msg.send(player, "&e/aszaman ustawperk (gracz) (perk) (poziom) &7- dla boostdrop: procent");
        Msg.send(player, "&e/aszaman resetperkow (gracz) (all/" + nazwyPerkow().replace(", ", "/") + ")");
        Msg.send(player, "&e/aszaman sprawdz <nick> &7- poziomy perkow gracza");
        Msg.send(player, "&e/aszaman max <poziom> &7- cap ulepszen (1 = max 1 poziom, 0 = bez capu)");
        Msg.send(player, "&e/aszaman maxdrop <procent> &7- cap Boostu Dropu (np. 50)");
        Msg.send(player, "&8&m---------------------------------");
        Msg.send(player, "&7Perki: &f" + nazwyPerkow());
    }

    private void ustawMaxPoziom(CommandSender sender, String[] args) {
        if (args.length < 2) {
            int teraz = plugin.getSzamanConfig().getInt("limity.max-poziom", 0);
            Msg.send(sender, "&cUzycie: /aszaman max <poziom>");
            Msg.send(sender, "&7Aktualny cap: &f" + (teraz <= 0 ? "brak (poziomy z szaman.yml)" : teraz));
            return;
        }
        Integer n = null;
        try {
            n = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {
        }
        if (n == null || n < 0) {
            Msg.send(sender, "&cPodaj liczbe >= 0 (0 = bez capu).");
            return;
        }
        plugin.getSzamanConfig().set("limity.max-poziom", n);
        plugin.zapiszSzamanConfig();
        if (n == 0) {
            Msg.send(sender, "&aUsunieto cap poziomow perkow. Gracze moga ulepszac do poziomow z szaman.yml.");
        } else {
            Msg.send(sender, "&aUstawiono max poziom perkow na &e" + n + "&a. Nikt nie ulepszy wyzej.");
        }
    }

    private void ustawMaxDrop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "&cUzycie: /aszaman maxdrop <procent>");
            Msg.send(sender, "&7Aktualny limit Boostu Dropu: &f" + Perk.formatLiczby(plugin.getDropPerkManager().getLimit()) + "%");
            return;
        }
        Double n = parsujDouble(args[1]);
        if (n == null || n < 0) {
            Msg.send(sender, "&cPodaj procent >= 0.");
            return;
        }
        plugin.getSzamanConfig().set("perk-dropu.limit", n);
        plugin.zapiszSzamanConfig();
        Msg.send(sender, "&aUstawiono max Boost Dropu na &e" + Perk.formatLiczby(n) + "%&a.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (sender instanceof Player && !sender.hasPermission("admin.szaman")) return out;
        String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : List.of("ustawodlamek", "dajodlamek", "ustawperk", "resetperkow", "sprawdz", "max", "maxdrop")) {
                if (s.startsWith(pre)) out.add(s);
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("ustawperk") || sub.equals("resetperkow") || sub.equals("sprawdz"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
            }
            return out;
        }
        if (args.length == 2 && sub.equals("max")) {
            for (String s : List.of("0", "1", "2", "3", "4")) {
                if (s.startsWith(pre)) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && sub.equals("maxdrop")) {
            for (String s : List.of("50", "70", "100", "200")) {
                if (s.startsWith(pre)) out.add(s);
            }
            return out;
        }
        if (args.length == 3 && (sub.equals("ustawperk") || sub.equals("resetperkow"))) {
            if (sub.equals("resetperkow") && "all".startsWith(pre)) out.add("all");
            for (Perk p : Perk.values()) {
                if (p.getKlucz().startsWith(pre)) out.add(p.getKlucz());
            }
        }
        return out;
    }

    // --- HELPERY ---
    private String nazwyPerkow() {
        StringBuilder sb = new StringBuilder();
        for (Perk p : Perk.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.getKlucz());
        }
        return sb.toString();
    }

    private void wydajGraczowi(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    private int parsujInt(String[] args, int index, int domyslnie) {
        if (args.length <= index) return domyslnie;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return domyslnie;
        }
    }

    private Double parsujDouble(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
