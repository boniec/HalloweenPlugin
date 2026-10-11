package boniec.gornik;

import boniec.szaman.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GornikCommand implements CommandExecutor, TabCompleter {

    private final GornikManager manager;
    private final MiningListener miningListener;
    private final GornikGui gui;
    /** /gornik <kwota> - stare dodawanie pieniedzy (boniec.ekonomia.gornik). */
    private CommandExecutor kwotaExecutor;

    public GornikCommand(GornikManager manager, MiningListener miningListener, GornikGui gui) {
        this.manager = manager;
        this.miningListener = miningListener;
        this.gui = gui;
    }

    public void setKwotaExecutor(CommandExecutor kwotaExecutor) {
        this.kwotaExecutor = kwotaExecutor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && kwotaExecutor != null && czyLiczba(args[0])) {
            return kwotaExecutor.onCommand(sender, command, label, args);
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("gornik.admin")) {
                Msg.send(sender, "&cBrak uprawnien!");
                return true;
            }
            manager.reload();
            if (miningListener != null) {
                miningListener.invalidateActionbarCache();
            }
            Msg.send(sender, manager.getConfig().getString("messages.reload", "&aPrzeladowano kopanie.yml!"));
            return true;
        }

        if (args.length >= 1 && (args[0].equalsIgnoreCase("dodaj") || args[0].equalsIgnoreCase("usun"))) {
            if (!sender.hasPermission("gornik.admin")) {
                Msg.send(sender, "&cBrak uprawnien!");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Wez blok do reki na serwerze.");
                return true;
            }
            ItemStack reka = player.getInventory().getItemInMainHand();
            if (reka == null || reka.getType().isAir()) {
                Msg.send(player, "&cWez blok do reki.");
                return true;
            }
            Material mat = reka.getType();
            if (!mat.isBlock()) {
                Msg.send(player, "&cTo nie jest blok (trzymaj np. kamien, rude, weglowy).");
                return true;
            }
            if (args[0].equalsIgnoreCase("usun")) {
                if (manager.ustawCeneBloku(mat, 0)) {
                    Msg.send(player, "&aUsunieto &e" + mat.name() + " &az kopania.");
                } else {
                    Msg.send(player, "&cNie udalo sie zapisac kopanie.yml.");
                }
                return true;
            }
            double cena = 1.0;
            if (args.length >= 2) {
                try {
                    cena = Double.parseDouble(args[1].replace(",", "."));
                } catch (NumberFormatException e) {
                    Msg.send(player, "&cCena musi byc liczba, np. /gornik dodaj 2.5");
                    return true;
                }
            }
            if (cena <= 0) {
                Msg.send(player, "&cCena musi byc wieksza od 0.");
                return true;
            }
            if (manager.ustawCeneBloku(mat, cena)) {
                Msg.send(player, "&aDodano &e" + mat.name() + " &aza &e" + cena + "$&a. Trzymaj blok w rece i: &f/gornik dodaj <cena>");
            } else {
                Msg.send(player, "&cNie udalo sie zapisac kopanie.yml.");
            }
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("turbodrop")) {
            if (!sender.hasPermission("gornik.admin")) {
                Msg.send(sender, manager.getConfig().getString("turbodrop.brak-uprawnien", "&cBrak uprawnien!"));
                return true;
            }

            if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
                manager.wylaczTurbodrop();
                Msg.send(sender, manager.getConfig().getString("turbodrop.wylaczone", "&cTurboDrop wylaczony!"));
                return true;
            }

            if (args.length < 3) {
                Msg.send(sender, manager.getConfig().getString("turbodrop.uzycie", "&cUzycie: /gornik turbodrop <mnoznik> <sekundy> | off"));
                return true;
            }

            double mnoznik;
            int sekundy;
            try {
                mnoznik = Double.parseDouble(args[1]);
                sekundy = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                Msg.send(sender, "&cMnoznik musi byc liczba (np. 2.0), a czas w sekundach (np. 300)!");
                return true;
            }

            if (mnoznik < 1.0 || mnoznik > 100.0) {
                Msg.send(sender, "&cMnoznik musi byc miedzy 1.0 a 100.0!");
                return true;
            }
            if (sekundy < 10 || sekundy > 86400) {
                Msg.send(sender, "&cCzas musi byc miedzy 10 a 86400 sekund (24h)!");
                return true;
            }

            manager.wlaczTurbodrop(mnoznik, sekundy);

            String msg = manager.getConfig().getString("turbodrop.wlaczone", "&aTurboDrop wlaczony! Mnoznik: &e%x% &7na &e%czas%s")
                    .replace("%x%", String.valueOf((int) mnoznik))
                    .replace("%czas%", String.valueOf(sekundy));
            Msg.send(sender, msg);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        if (!manager.isEnabled()) {
            Msg.send(player, manager.getConfig().getString("messages.regiona-wylaczona", "&cEkonomia kopania jest wylaczona!"));
            return true;
        }

        if (manager.getCenyBlokw().isEmpty()) {
            Msg.send(player, "&cBrak ustawionych blokow w kopanie.yml!");
            return true;
        }

        gui.otworz(player, 0);
        return true;
    }

    private static boolean czyLiczba(String tekst) {
        try {
            Double.parseDouble(tekst.replace(",", "."));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("reload");
            completions.add("dodaj");
            completions.add("usun");
            completions.add("turbodrop");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("turbodrop")) {
            completions.add("off");
            completions.add("2");
            completions.add("3");
            completions.add("5");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("turbodrop")) {
            completions.add("60");
            completions.add("300");
            completions.add("600");
            completions.add("1800");
            completions.add("3600");
        }
        return completions;
    }

}
