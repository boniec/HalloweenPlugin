package boniec.aregion;

import boniec.code.core.UstawieniaDatabase;
import boniec.code.core.WorldGuardHook;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * /aregion set|usun|lista - regiony WorldGuard, do których nikt (poza aregion.bypass) nie może wejść.
 * Wejście ruchem jest cofane z odepchnięciem, teleport do środka jest anulowany.
 * Musi być tworzone tylko gdy WorldGuard jest zainstalowany.
 */
public final class ARegionManager implements Listener, CommandExecutor, TabCompleter {

    private static final String PREFIX = "&8[&cRegion&8] ";
    private static final String KV_MODUL = "aregion";
    private static final String KV_KLUCZ = "zablokowane";
    private static final String PERM_ADMIN = "aregion.admin";
    private static final String PERM_BYPASS = "aregion.bypass";
    private static final double ODEPCHNIECIE = 0.8;
    private static final long KOMUNIKAT_CO_MS = 1500L;

    private final Set<String> zablokowane = new CopyOnWriteArraySet<>();
    private volatile String[] zablokowaneTab = new String[0];
    private final Map<UUID, Long> ostatniKomunikat = new ConcurrentHashMap<>();

    public ARegionManager(JavaPlugin plugin) {
        wczytaj();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("aregion");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    private void wczytaj() {
        zablokowane.clear();
        String raw = UstawieniaDatabase.getKv(KV_MODUL, KV_KLUCZ, "");
        for (String s : raw.split(",")) {
            String id = s.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) zablokowane.add(id);
        }
        zablokowaneTab = zablokowane.toArray(new String[0]);
    }

    private void zapisz() {
        zablokowaneTab = zablokowane.toArray(new String[0]);
        UstawieniaDatabase.setKv(KV_MODUL, KV_KLUCZ, String.join(",", zablokowane));
    }

    private boolean wZablokowanym(Location loc) {
        String[] tab = zablokowaneTab;
        return tab.length > 0 && WorldGuardHook.isInAnyRegion(loc, tab);
    }

    // ───────────────────────── blokada ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (zablokowaneTab.length == 0) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!wZablokowanym(to)) return;
        // Kto już jest w środku (np. zalogował się tam), może swobodnie wyjść.
        if (wZablokowanym(from)) return;
        Player player = event.getPlayer();
        if (player.hasPermission(PERM_BYPASS)) return;

        event.setCancelled(true);
        odepchnij(player, from, to);
        komunikat(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (zablokowaneTab.length == 0) return;
        Location to = event.getTo();
        if (to == null || !wZablokowanym(to)) return;
        if (wZablokowanym(event.getFrom())) return;
        Player player = event.getPlayer();
        if (player.hasPermission(PERM_BYPASS)) return;
        event.setCancelled(true);
        komunikat(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ostatniKomunikat.remove(event.getPlayer().getUniqueId());
    }

    private void odepchnij(Player player, Location from, Location to) {
        Vector kierunek = from.toVector().subtract(to.toVector());
        kierunek.setY(0);
        if (kierunek.lengthSquared() < 1.0E-6) {
            kierunek = player.getLocation().getDirection().multiply(-1);
            kierunek.setY(0);
        }
        if (kierunek.lengthSquared() > 1.0E-6) kierunek.normalize();
        kierunek.multiply(ODEPCHNIECIE);
        kierunek.setY(0.3);

        Location cofnij = from.clone();
        cofnij.setYaw(player.getLocation().getYaw());
        cofnij.setPitch(player.getLocation().getPitch());
        player.teleport(cofnij);
        player.setVelocity(kierunek);
    }

    private void komunikat(Player player) {
        long teraz = System.currentTimeMillis();
        Long ostatni = ostatniKomunikat.get(player.getUniqueId());
        if (ostatni != null && teraz - ostatni < KOMUNIKAT_CO_MS) return;
        ostatniKomunikat.put(player.getUniqueId(), teraz);
        player.sendMessage(color(PREFIX + "&cNie mozesz wejsc na ten teren!"));
    }

    // ───────────────────────── komenda ─────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(color(PREFIX + "&cBrak permisji."));
            return true;
        }
        if (args.length == 0) {
            pomoc(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set", "dodaj", "zablokuj" -> {
                if (args.length < 2) {
                    sender.sendMessage(color(PREFIX + "&cUzycie: /" + label + " set <region>"));
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!WorldGuardHook.regionExistsAnywhere(id)) {
                    sender.sendMessage(color(PREFIX + "&cRegion &f" + id + " &cnie istnieje w WorldGuard."));
                    return true;
                }
                if (!zablokowane.add(id)) {
                    sender.sendMessage(color(PREFIX + "&eRegion &f" + id + " &ejest juz zablokowany."));
                    return true;
                }
                zapisz();
                sender.sendMessage(color(PREFIX + "&aZablokowano wejscie na region &f" + id + "&a."));
            }
            case "usun", "remove", "odblokuj", "unset" -> {
                if (args.length < 2) {
                    sender.sendMessage(color(PREFIX + "&cUzycie: /" + label + " usun <region>"));
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!zablokowane.remove(id)) {
                    sender.sendMessage(color(PREFIX + "&cRegion &f" + id + " &cnie jest zablokowany."));
                    return true;
                }
                zapisz();
                sender.sendMessage(color(PREFIX + "&aOdblokowano region &f" + id + "&a."));
            }
            case "lista", "list" -> {
                if (zablokowane.isEmpty()) {
                    sender.sendMessage(color(PREFIX + "&7Brak zablokowanych regionow."));
                } else {
                    sender.sendMessage(color(PREFIX + "&7Zablokowane: &f" + String.join("&7, &f", zablokowane)));
                }
            }
            default -> pomoc(sender, label);
        }
        return true;
    }

    private void pomoc(CommandSender sender, String label) {
        sender.sendMessage(color(PREFIX + "&e/" + label + " set <region> &7- nikt nie moze wejsc"));
        sender.sendMessage(color(PREFIX + "&e/" + label + " usun <region> &7- zdejmij blokade"));
        sender.sendMessage(color(PREFIX + "&e/" + label + " lista"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("set", "usun", "lista")) {
                if (s.startsWith(pre)) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("usun")) {
            String pre = args[1].toLowerCase(Locale.ROOT);
            for (String s : zablokowane) {
                if (s.startsWith(pre)) out.add(s);
            }
        }
        return out;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
