package boniec.sklep;

import boniec.szaman.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AdminSklepCommand implements CommandExecutor, TabCompleter {

    private final SklepManager manager;

    public AdminSklepCommand(SklepManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sklep.admin")) {
            sender.sendMessage(Msg.color("&cBrak uprawnien."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Msg.color("&e/asklep reload &7| &eaddbuy &8<slot> <cena> &7| &erembuy &8<slot>"));
            sender.sendMessage(Msg.color("&e/asklep addtime &8<slot> <sekundy> &7| &eremtime &8<slot>"));
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            manager.wczytaj();
            sender.sendMessage(manager.msg("reloaded"));
            return true;
        }
        if (!(sender instanceof Player gracz)) {
            sender.sendMessage(Msg.color("&cTe podkomendy wymagaja gracza z itemem w reku."));
            return true;
        }
        ItemStack wReku = gracz.getInventory().getItemInMainHand();
        if (wReku.getType().isAir()) {
            gracz.sendMessage(Msg.color("&cTrzymaj przedmiot w rece."));
            return true;
        }
        try {
            if ("addbuy".equals(sub) && args.length >= 3) {
                int slot = Integer.parseInt(args[1]);
                double cena = Double.parseDouble(args[2].replace(",", "."));
                manager.database().setBuyItem(slot, wReku.clone(), cena);
                gracz.sendMessage(manager.msg("itemAdded").replace("{slot}", String.valueOf(slot)));
                return true;
            }
            if ("rembuy".equals(sub) && args.length >= 2) {
                int slot = Integer.parseInt(args[1]);
                manager.database().removeBuyItem(slot);
                gracz.sendMessage(manager.msg("itemRemoved").replace("{slot}", String.valueOf(slot)));
                return true;
            }
            if ("addtime".equals(sub) && args.length >= 3) {
                int slot = Integer.parseInt(args[1]);
                long sec = Long.parseLong(args[2]);
                manager.database().setTimeItem(slot, wReku.clone(), sec);
                gracz.sendMessage(manager.msg("itemAdded").replace("{slot}", String.valueOf(slot)));
                return true;
            }
            if ("remtime".equals(sub) && args.length >= 2) {
                int slot = Integer.parseInt(args[1]);
                manager.database().removeTimeItem(slot);
                gracz.sendMessage(manager.msg("itemRemoved").replace("{slot}", String.valueOf(slot)));
                return true;
            }
        } catch (NumberFormatException ex) {
            gracz.sendMessage(Msg.color("&cZly format liczby."));
            return true;
        } catch (SQLException ex) {
            gracz.sendMessage(Msg.color("&cBlad bazy: " + ex.getMessage()));
            return true;
        }
        sender.sendMessage(Msg.color("&cNieznana podkomenda."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sklep.admin")) return List.of();
        if (args.length == 1) {
            return pref(List.of("reload", "addbuy", "rembuy", "addtime", "remtime"), args[0]);
        }
        return List.of();
    }

    private List<String> pref(List<String> opts, String start) {
        String s = start.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : opts) if (o.startsWith(s)) out.add(o);
        return out;
    }
}
