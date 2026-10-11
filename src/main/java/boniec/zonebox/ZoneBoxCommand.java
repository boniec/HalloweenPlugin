package boniec.zonebox;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZoneBoxCommand implements CommandExecutor, TabCompleter {

    private final ZoneBoxManager mgr;

    public ZoneBoxCommand(ZoneBoxManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("azonebox.admin")) {
            sender.sendMessage(c("&cBrak permisji."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(c("&e/azonebox add <blok> &7| &e/azonebox remove <blok>"));
            sender.sendMessage(c("&e/azonebox region <region> &7| &e/azonebox unregion <region>"));
            sender.sendMessage(c("&e/azonebox genregion <region> &7- tylko kopanie generatorow, bez stawiania"));
            sender.sendMessage(c("&e/azonebox wylacz <region> &7| &e/azonebox wlacz <region> &7- region bez zoneboxa, zakaz stawiania"));
            sender.sendMessage(c("&e/azonebox list"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> {
                if (args.length >= 2) {
                    int ile = 0;
                    for (int i = 1; i < args.length; i++) {
                        Material m = Material.matchMaterial(args[i]);
                        if (m == null || !m.isBlock()) {
                            sender.sendMessage(c("&cNieznany blok: &f" + args[i]));
                            continue;
                        }
                        if (mgr.addBlock(m)) {
                            sender.sendMessage(c("&aDodano blok &f" + m.name()));
                            ile++;
                        } else {
                            sender.sendMessage(c("&cJuz na liscie: &f" + m.name()));
                        }
                    }
                    if (ile == 0 && args.length == 2) return true;
                    return true;
                }
                Material m = blok(sender, args);
                if (m == null) return true;
                if (mgr.addBlock(m)) sender.sendMessage(c("&aDodano blok &f" + m.name()));
                else sender.sendMessage(c("&cTen blok juz jest na liscie albo to nie blok."));
            }
            case "remove", "usun" -> {
                Material m = blok(sender, args);
                if (m == null) return true;
                if (mgr.removeBlock(m)) sender.sendMessage(c("&aUsunieto &f" + m.name()));
                else sender.sendMessage(c("&cNie ma tego bloku na liscie."));
            }
            case "region" -> {
                if (args.length < 2) {
                    sender.sendMessage(c("&cUzycie: /azonebox region <nazwa>"));
                    return true;
                }
                if (mgr.addRegion(args[1])) sender.sendMessage(c("&aRegion &f" + args[1] + " &adodany."));
                else sender.sendMessage(c("&cTen region juz jest."));
            }
            case "unregion", "usunregion" -> {
                if (args.length < 2) {
                    sender.sendMessage(c("&cUzycie: /azonebox unregion <nazwa>"));
                    return true;
                }
                if (mgr.removeRegion(args[1])) sender.sendMessage(c("&aRegion &f" + args[1] + " &ausuniety."));
                else sender.sendMessage(c("&cNie ma tego regionu."));
            }
            case "genregion" -> {
                if (args.length < 2) {
                    sender.sendMessage(c("&cUzycie: /azonebox genregion <nazwa>"));
                    return true;
                }
                if (mgr.addGenRegion(args[1])) sender.sendMessage(c("&aRegion &f" + args[1] + " &a: tylko generatory, bez stawiania."));
                else sender.sendMessage(c("&cTen region juz jest na liscie generatorow."));
            }
            case "ungenregion" -> {
                if (args.length < 2) {
                    sender.sendMessage(c("&cUzycie: /azonebox ungenregion <nazwa>"));
                    return true;
                }
                if (mgr.removeGenRegion(args[1])) sender.sendMessage(c("&aUsunieto tryb generatorow z &f" + args[1]));
                else sender.sendMessage(c("&cNie ma tego regionu na liscie generatorow."));
            }
            case "wylacz" -> {
                if (args.length < 2) {
                    sender.sendMessage(c("&cUzycie: /azonebox wylacz <region>"));
                    return true;
                }
                if (mgr.addWylaczony(args[1])) sender.sendMessage(c("&aRegion &f" + args[1] + " &a: bez zoneboxa, zakaz stawiania."));
                else sender.sendMessage(c("&cTen region juz jest wylaczony."));
            }
            case "wlacz" -> {
                if (args.length < 2) {
                    sender.sendMessage(c("&cUzycie: /azonebox wlacz <region>"));
                    return true;
                }
                if (mgr.removeWylaczony(args[1])) sender.sendMessage(c("&aZonebox znow dziala w &f" + args[1]));
                else sender.sendMessage(c("&cTen region nie jest wylaczony."));
            }
            case "list" -> {
                sender.sendMessage(c("&7Regiony (bloki): &f" + String.join(", ", mgr.regiony())));
                sender.sendMessage(c("&7Tylko generatory: &f" + String.join(", ", mgr.genRegiony())));
                sender.sendMessage(c("&7Bez zoneboxa: &f" + String.join(", ", mgr.wylaczone())));
                List<String> b = new ArrayList<>();
                for (Material m : mgr.bloki()) b.add(m.name());
                sender.sendMessage(c("&7Bloki: &f" + String.join(", ", b)));
            }
            default -> sender.sendMessage(c("&cNieznana podkomenda."));
        }
        return true;
    }

    private Material blok(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Material m = Material.matchMaterial(args[1]);
            if (m == null || !m.isBlock()) {
                sender.sendMessage(c("&cNieznany blok: &f" + args[1]));
                return null;
            }
            return m;
        }
        if (sender instanceof Player p) {
            Block b = p.getTargetBlockExact(6);
            if (b == null || b.getType().isAir()) {
                sender.sendMessage(c("&cPodaj blok albo patrz na blok."));
                return null;
            }
            return b.getType();
        }
        sender.sendMessage(c("&cPodaj material bloku."));
        return null;
    }

    private static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("azonebox.admin")) return out;
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("add", "remove", "region", "unregion", "genregion", "ungenregion", "wylacz", "wlacz", "list")) {
                if (s.startsWith(pre)) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            String pre = args[1].toUpperCase(Locale.ROOT);
            for (Material m : Material.values()) {
                if (m.isBlock() && m.name().startsWith(pre) && out.size() < 20) out.add(m.name());
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("usun"))) {
            String pre = args[1].toUpperCase(Locale.ROOT);
            for (Material m : mgr.bloki()) {
                if (m.name().startsWith(pre)) out.add(m.name());
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("unregion") || args[0].equalsIgnoreCase("usunregion"))) {
            String pre = args[1].toLowerCase(Locale.ROOT);
            for (String r : mgr.regiony()) {
                if (r.startsWith(pre)) out.add(r);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("wlacz")) {
            String pre = args[1].toLowerCase(Locale.ROOT);
            for (String r : mgr.wylaczone()) {
                if (r.startsWith(pre)) out.add(r);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ungenregion")) {
            String pre = args[1].toLowerCase(Locale.ROOT);
            for (String r : mgr.genRegiony()) {
                if (r.startsWith(pre)) out.add(r);
            }
        }
        return out;
    }
}
