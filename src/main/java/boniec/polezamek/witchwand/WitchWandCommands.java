package boniec.dev.polezamek.witchwand;

import boniec.dev.polezamek.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WitchWandCommands implements CommandExecutor {

    private final Main plugin;
    private final WitchWandManager witchWandManager;
    private final HookManager hookManager;

    public WitchWandCommands(Main plugin, WitchWandManager witchWandManager, HookManager hookManager) {
        this.plugin = plugin;
        this.witchWandManager = witchWandManager;
        this.hookManager = hookManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "dajrozdzke" -> {
                if (!(sender instanceof Player player)) return consoleOnly(sender);
                player.getInventory().addItem(witchWandManager.createWandItem());
                player.sendMessage(color(witchWandManager.getMsgGiven()));
                return true;
            }
            case "dajhak" -> {
                if (!(sender instanceof Player player)) return consoleOnly(sender);
                player.getInventory().addItem(hookManager.createHookItem());
                player.sendMessage(color(hookManager.getMsgGiven()));
                return true;
            }
            case "zresetujrozdzke" -> {
                Player target;
                if (args.length > 0) {
                    target = plugin.getServer().getPlayerExact(args[0]);
                    if (target == null) {
                        sender.sendMessage(color("&cNie znaleziono gracza &f" + args[0] + "&c!"));
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("Musisz podać gracza z konsoli.");
                    return true;
                }
                witchWandManager.resetPlayer(target);
                hookManager.resetPlayer(target);
                target.setCooldown(WitchWandManager.WAND_MATERIAL, 0);
                target.setCooldown(HookManager.HOOK_MATERIAL, 0);
                if (args.length > 0) {
                    sender.sendMessage(color("&aZresetowano cooldown graczowi &e" + target.getName() + "&a!"));
                } else {
                    sender.sendMessage(color("&aTwój cooldown został zresetowany!"));
                }
                return true;
            }
        }
        return false;
    }

    private boolean consoleOnly(CommandSender sender) {
        sender.sendMessage("Tylko gracz może użyć tej komendy.");
        return true;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
