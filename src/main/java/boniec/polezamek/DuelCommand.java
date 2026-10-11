package boniec.dev.polezamek;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelManager duels;

    public DuelCommand(DuelManager duels) {
        this.duels = duels;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (args.length == 0) {
            duels.wyswietlUzycie(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("akceptuj") || sub.equals("accept") || sub.equals("tak")) {
            duels.akceptuj(player);
            return true;
        }
        if (sub.equals("odrzuc") || sub.equals("deny") || sub.equals("nie")) {
            duels.odrzuc(player);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            DuelManager.msg(player, duels.cfgStr("wiadomosci.offline", "&cGracz &e{gracz} &cjest offline.")
                    .replace("{gracz}", args[0]));
            return true;
        }
        if (target.equals(player)) {
            DuelManager.msg(player, duels.cfgStr("wiadomosci.siebie", "&cNie mozesz wyzywac samego siebie."));
            return true;
        }
        if (duels.zajety(player.getUniqueId()) || duels.zajety(target.getUniqueId())) {
            DuelManager.msg(player, duels.cfgStr("wiadomosci.zajety", "&cTy albo ten gracz jestes juz na arenie."));
            return true;
        }
        duels.wyzwij(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        String pre = args[0].toLowerCase(Locale.ROOT);
        if ("akceptuj".startsWith(pre)) out.add("akceptuj");
        if ("odrzuc".startsWith(pre)) out.add("odrzuc");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player me && p.equals(me)) continue;
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
        }
        return out;
    }
}
