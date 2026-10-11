package boniec.bany;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /alts - menu kont wspoldzielacych IP. */
public final class BanCommands implements CommandExecutor, TabCompleter {

    private final BanManager m;
    private final BanGui gui;

    public BanCommands(BanManager m, BanGui gui) {
        this.m = m;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("bany.alts") && !sender.hasPermission("bany.admin")) {
            m.send(sender, "wiadomosci.brak-uprawnien");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")
                && (sender.hasPermission("bany.admin") || sender.isOp())) {
            m.reload();
            m.send(sender, "wiadomosci.przeladowano");
            return true;
        }
        if (args.length < 1) {
            m.send(sender, "wiadomosci.uzycie-alts");
            return true;
        }
        if (sender instanceof Player p) {
            gui.openAlts(p, args[0]);
            return true;
        }
        final String arg = args[0];
        m.async(() -> {
            BanManager.Target t = m.resolve(arg, null, null, null);
            if (t == null || t.uuid.isEmpty()) {
                m.sync(() -> m.send(sender, "wiadomosci.nie-znaleziono", "%gracz%", arg));
                return;
            }
            List<BanManager.AltEntry> alts = m.loadAlts(t.uuid);
            m.sync(() -> {
                m.send(sender, "alts.konsola.naglowek", "%gracz%", t.name, "%ile%", String.valueOf(alts.size()));
                for (BanManager.AltEntry e : alts) {
                    sender.sendMessage(m.str("alts.konsola.wpis",
                            "%nick%", e.name, "%ip%", e.ips, "%ostatnie%", m.date(e.lastSeen)));
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("bany.alts") && !sender.hasPermission("bany.admin")) return out;
        String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (sender.hasPermission("bany.admin") || sender.isOp()) out.add("reload");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
