package boniec.adminutil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TeleportCommands implements TabExecutor {

    private static final String NO_PERM = "&f亢 &cNie masz uprawnień!";
    private final JavaPlugin plugin;

    public TeleportCommands(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (String n : List.of("tp", "tphere")) {
            PluginCommand c = plugin.getCommand(n);
            if (c == null) {
                plugin.getLogger().warning("Brak komendy /" + n + " w plugin.yml");
                continue;
            }
            c.setExecutor(this);
            c.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("tphere")) {
            tphere(sender, args);
            return true;
        }
        tp(sender, args);
        return true;
    }

    private void tp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("core.tp")) {
            msg(sender, NO_PERM);
            return;
        }
        if (args.length == 0) {
            msg(sender, "&f亢 &cUżyj: &f/tp <gracz> &7lub &f/tp <kto> <do-kogo>");
            return;
        }
        if (args.length == 1) {
            if (!(sender instanceof Player self)) {
                msg(sender, "&cKonsola: /tp <kto> <do-kogo>");
                return;
            }
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t == null) {
                msg(sender, "&f与 &cTen gracz jest offline.");
                return;
            }
            self.teleport(t.getLocation());
            msg(self, "&aTeleport do &f" + t.getName() + "&a.");
            return;
        }
        if (!sender.hasPermission("core.tp.others")) {
            msg(sender, NO_PERM);
            return;
        }
        Player a = Bukkit.getPlayerExact(args[0]);
        Player b = Bukkit.getPlayerExact(args[1]);
        if (a == null || b == null) {
            msg(sender, "&f与 &cGracz jest offline.");
            return;
        }
        a.teleport(b.getLocation());
        msg(sender, "&aPrzeteleportowano &f" + a.getName() + " &ado &f" + b.getName() + "&a.");
        msg(a, "&aTeleport do &f" + b.getName() + "&a.");
    }

    private void tphere(CommandSender sender, String[] args) {
        if (!sender.hasPermission("core.tphere")) {
            msg(sender, NO_PERM);
            return;
        }
        if (!(sender instanceof Player self)) {
            msg(sender, "&cTylko gracz.");
            return;
        }
        if (args.length == 0) {
            msg(sender, "&f亢 &cUżyj: &f/tphere <gracz> &7(&f/s&7)");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) {
            msg(sender, "&f与 &cTen gracz jest offline.");
            return;
        }
        t.teleport(self.getLocation());
        msg(self, "&aSprowadzono &f" + t.getName() + "&a.");
        msg(t, "&aTeleport do &f" + self.getName() + "&a.");
    }

    private static void msg(CommandSender s, String t) {
        s.sendMessage(ChatColor.translateAlternateColorCodes('&', t));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 || args.length == 2) {
            String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
            }
        }
        return out;
    }
}
