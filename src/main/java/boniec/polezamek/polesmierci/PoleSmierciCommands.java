package boniec.dev.polezamek.polesmierci;

import boniec.dev.polezamek.Main;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PoleSmierciCommands implements CommandExecutor {

    private final Main plugin;
    private final PoleSmierciManager manager;

    public PoleSmierciCommands(Main plugin, PoleSmierciManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "dajpolesmierci" -> {
                Player target = resolveTarget(sender, args, 0);
                if (target == null) return true;
                target.getInventory().addItem(manager.createPoleItem());
                target.sendMessage(PoleSmierciListener.color(manager.getMsgGiven()));
                return true;
            }
            case "zresetujpolesmierci" -> {
                Player target = resolveTarget(sender, args, 0);
                if (target == null) return true;
                manager.resetPlayer(target);
                plugin.getTargetImmunity().clear(target.getUniqueId());
                for (org.bukkit.Material mat : List.of(PoleSmierciManager.ITEM_MATERIAL, org.bukkit.Material.FERMENTED_SPIDER_EYE,
                        org.bukkit.Material.HONEYCOMB, org.bukkit.Material.STICK)) {
                    target.setCooldown(mat, 0);
                }
                sender.sendMessage(PoleSmierciListener.color(manager.getMsgCooldownReset(target.getName())));
                if (!target.equals(sender)) {
                    target.sendMessage(PoleSmierciListener.color(manager.getMsgCooldownResetSelf()));
                }
                return true;
            }
            case "polesmierci" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Tylko gracz mo\u017ce u\u017cy\u0107 tej komendy.");
                    return true;
                }
                if (args.length == 0) {
                    sendUsage(player);
                    return true;
                }
                switch (args[0].toLowerCase()) {
                    case "ustawspawn" -> {
                        manager.setSpawnLoc(player.getLocation());
                        player.sendMessage(PoleSmierciListener.color("&aUstawiono blokad\u0119 SPAWN na Twojej lokalizacji (&f" + manager.getRegionBlockDistance() + " kratek&a)!"));
                    }
                    case "ustawspawnpvp" -> {
                        manager.setSpawnPvpLoc(player.getLocation());
                        player.sendMessage(PoleSmierciListener.color("&aUstawiono blokad\u0119 SPAWNPVP na Twojej lokalizacji (&f" + manager.getRegionBlockDistance() + " kratek&a)!"));
                    }
                    case "usunspawn" -> {
                        manager.clearSpawnLocs();
                        player.sendMessage(PoleSmierciListener.color("&aUsuni\u0119to zapisane lokalizacje spawnu!"));
                    }
                    case "info" -> {
                        player.sendMessage(PoleSmierciListener.color("&7Spawn: &f" + locToString(manager.getSpawnLoc())));
                        player.sendMessage(PoleSmierciListener.color("&7SpawnPvP: &f" + locToString(manager.getSpawnPvpLoc())));
                    }
                    default -> sendUsage(player);
                }
                return true;
            }
        }
        return false;
    }

    private void sendUsage(Player player) {
        player.sendMessage(PoleSmierciListener.color("&f/polesmierci ustawspawn"));
        player.sendMessage(PoleSmierciListener.color("&f/polesmierci ustawspawnpvp"));
        player.sendMessage(PoleSmierciListener.color("&f/polesmierci usunspawn"));
        player.sendMessage(PoleSmierciListener.color("&f/polesmierci info"));
    }

    private String locToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "brak";
        return loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player p = plugin.getServer().getPlayerExact(args[index]);
            if (p == null) {
                sender.sendMessage(PoleSmierciListener.color("&cNie znaleziono gracza &f" + args[index] + "&c!"));
                return null;
            }
            return p;
        }
        if (sender instanceof Player p) {
            return p;
        }
        sender.sendMessage("Musisz poda\u0107 gracza z konsoli.");
        return null;
    }
}
