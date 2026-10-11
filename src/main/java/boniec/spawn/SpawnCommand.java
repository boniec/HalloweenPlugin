package boniec.spawn;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public final class SpawnCommand implements CommandExecutor, Listener {

    private static final String ICON = "亢";

    private final Plugin plugin;
    private final SpawnManager manager;

    public SpawnCommand(Plugin plugin, SpawnManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("ustawspawn") || name.equals("setspawn")) {
            return ustaw(sender);
        }
        return spawn(sender);
    }

    private boolean ustaw(CommandSender sender) {
        if (!sender.hasPermission("spawn.admin")) {
            sender.sendMessage(color("&f" + ICON + " &cNie masz uprawnień!"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&f" + ICON + " &cTylko gracz może ustawić spawn."));
            return true;
        }
        manager.setSpawn(player.getLocation());
        sender.sendMessage(color("&f也 &aUstawiono spawn serwera."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return true;
    }

    private boolean spawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&f" + ICON + " &cTylko gracz może użyć /spawn."));
            return true;
        }
        if (boniec.dev.polezamek.DuelManager.blokujeSpawn(player.getUniqueId())) {
            player.sendMessage(color("&f" + ICON + " &cW duele nie możesz iść na spawn."));
            return true;
        }
        if (!manager.teleport(player)) {
            player.sendMessage(color("&f" + ICON + " &cSpawn nie jest ustawiony. Użyj &f/ustawspawn"));
            return true;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (boniec.dev.polezamek.DuelManager.blokujeSpawn(player.getUniqueId())) return;
            manager.teleport(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location loc = manager.getSpawn();
        if (loc == null || loc.getWorld() == null) return;
        event.setRespawnLocation(loc);
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
