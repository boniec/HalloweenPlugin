package boniec.dev.polezamek;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ADuelCommand implements CommandExecutor, TabCompleter {

    private final DuelManager duels;

    public ADuelCommand(DuelManager duels) {
        this.duels = duels;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("polezamek.admin")) {
            sender.sendMessage("Brak permisji.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/aduel start <czas>  |  stop  |  ustawswiat  |  ustaw1  |  ustaw2");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start")) {
            if (args.length < 2) {
                duels.msgSender(sender, duels.cfgStr("wiadomosci.event-brak-czasu", "&cPodaj czas. &7/aduel start 30m"));
                return true;
            }
            int sek = parseCzas(args[1]);
            if (sek <= 0) {
                duels.msgSender(sender, "&cNiepoprawny czas. Np. &f30m&c, &f10s&c, &f1h");
                return true;
            }
            duels.startEvent(sek);
            return true;
        }
        if (sub.equals("stop")) {
            duels.stopEvent();
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Te podkomendy sa tylko dla graczy.");
            return true;
        }
        if (sub.equals("ustawswiat")) {
            duels.ustawSwiat(player);
            DuelManager.msg(player, "&aSwiat duelu: &f" + player.getWorld().getName());
            return true;
        }
        if (sub.equals("ustaw1")) {
            duels.ustawLokacje(player, 1);
            DuelManager.msg(player, "&aLokacja 1 ustawiona.");
            return true;
        }
        if (sub.equals("ustaw2")) {
            duels.ustawLokacje(player, 2);
            DuelManager.msg(player, "&aLokacja 2 ustawiona.");
            return true;
        }
        sender.sendMessage("/aduel start <czas>  |  stop  |  ustawswiat  |  ustaw1  |  ustaw2");
        return true;
    }

    static int parseCzas(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("h")) return Integer.parseInt(s.substring(0, s.length() - 1).trim()) * 3600;
            if (s.endsWith("min")) return Integer.parseInt(s.substring(0, s.length() - 3).trim()) * 60;
            if (s.endsWith("m")) return Integer.parseInt(s.substring(0, s.length() - 1).trim()) * 60;
            if (s.endsWith("s")) return Integer.parseInt(s.substring(0, s.length() - 1).trim());
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("polezamek.admin")) return out;
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("start", "stop", "ustawswiat", "ustaw1", "ustaw2")) {
                if (s.startsWith(pre)) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            String pre = args[1].toLowerCase(Locale.ROOT);
            for (String s : List.of("30m", "15m", "10m", "5m", "60s")) {
                if (s.startsWith(pre)) out.add(s);
            }
        }
        return out;
    }
}
