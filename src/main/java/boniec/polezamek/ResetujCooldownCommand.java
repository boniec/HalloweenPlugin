package boniec.dev.polezamek;

import boniec.code.core.WorldGuardHook;
import boniec.dev.polezamek.util.WorldGuardUtil;
import boniec.klany.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /resetujcooldown — tylko na regionie WorldGuard "spawn". */
public final class ResetujCooldownCommand implements CommandExecutor {

    private static final String PREFIX = "&8[&x&3&1&E&A&F&Cᴄᴅ&8] &r";
    private static final String SPAWN_REGION = "spawn";

    private final Main plugin;

    public ResetujCooldownCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!plugin.isWorldGuardInstalled()) {
            player.sendMessage(Msg.color(PREFIX + "&cNie mozna sprawdzic regionu (brak WorldGuard)."));
            return true;
        }
        if (!jestNaSpawnie(player)) {
            player.sendMessage(Msg.color(PREFIX + "&cTej komendy uzyjesz tylko na regionie &fspawn&c."));
            return true;
        }

        plugin.resetEventCooldowns(player);
        player.sendMessage(Msg.color(PREFIX + "&aZresetowano cooldowny eventowek."));
        return true;
    }

    private static boolean jestNaSpawnie(Player player) {
        var loc = player.getLocation();
        if (WorldGuardHook.isInRegion(loc, SPAWN_REGION)) return true;
        return WorldGuardUtil.isInRegionContaining(loc, SPAWN_REGION);
    }
}
