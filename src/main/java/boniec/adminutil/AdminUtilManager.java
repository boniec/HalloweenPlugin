package boniec.adminutil;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stan i nasłuchiwanie dla /god oraz /vanish.
 * Stan trzymany jest tylko w pamięci - przy wyjściu gracza z serwera oba tryby się wyłączają.
 */
public final class AdminUtilManager implements Listener {

    /** Kto ma tę permisję, widzi graczy będących w vanishu. */
    public static final String PERM_VANISH_SEE = "core.vanish.see";

    private static final Set<UUID> GOD = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> VANISH = ConcurrentHashMap.newKeySet();
    /** Gracze, którym fly włączył dopiero vanish - przy wyłączeniu vanisha fly też gaśnie. */
    private static final Set<UUID> FLY_FROM_VANISH = ConcurrentHashMap.newKeySet();

    private final JavaPlugin plugin;
    private final File keepInvFile;
    private boolean keepInventory;

    public AdminUtilManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keepInvFile = new File(plugin.getDataFolder(), "keepinv.yml");
        loadKeepInventory();
    }

    // ───────────────────────── API ─────────────────────────

    /** Czy gracz ma włączony /god (używane też przez kod, który zabiera HP przez setHealth). */
    public static boolean isGod(Player player) {
        return player != null && GOD.contains(player.getUniqueId());
    }

    public static boolean isVanished(Player player) {
        return player != null && VANISH.contains(player.getUniqueId());
    }

    public boolean isKeepInventory() {
        return keepInventory;
    }

    public boolean setKeepInventory(boolean enabled) {
        if (this.keepInventory == enabled) {
            return enabled;
        }
        this.keepInventory = enabled;
        saveKeepInventory();
        return enabled;
    }

    private void loadKeepInventory() {
        File file = keepInvFile;
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            keepInventory = cfg.getBoolean("enabled", false);
            saveKeepInventory();
            file.renameTo(new File(plugin.getDataFolder(), "keepinv.yml.migrated"));
            return;
        }
        keepInventory = "1".equals(boniec.code.core.CoreDatabase.get().getKv("keepinv", "enabled", "0"));
    }

    private void saveKeepInventory() {
        boniec.code.core.CoreDatabase.get().setKv("keepinv", "enabled", keepInventory ? "1" : "0");
    }

    /** @return true gdy po przełączeniu god jest włączony. */
    public boolean toggleGod(Player player) {
        UUID id = player.getUniqueId();
        if (GOD.remove(id)) {
            return false;
        }
        GOD.add(id);
        player.setFireTicks(0);
        return true;
    }

    /** @return true gdy po przełączeniu vanish jest włączony. */
    public boolean toggleVanish(Player player) {
        UUID id = player.getUniqueId();
        if (VANISH.remove(id)) {
            reveal(player);
            if (FLY_FROM_VANISH.remove(id) && !isCreativeLike(player)) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            return false;
        }
        VANISH.add(id);
        conceal(player);
        if (!player.getAllowFlight()) {
            FLY_FROM_VANISH.add(id);
            player.setAllowFlight(true);
        }
        player.setFlying(true);
        return true;
    }

    private static boolean isCreativeLike(Player player) {
        org.bukkit.GameMode gm = player.getGameMode();
        return gm == org.bukkit.GameMode.CREATIVE || gm == org.bukkit.GameMode.SPECTATOR;
    }

    /** Wywoływane z onDisable. */
    public void shutdown() {
        for (UUID id : new ArrayList<>(VANISH)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                try {
                    reveal(player);
                } catch (RuntimeException ignored) {
                    // plugin jest już wyłączany - serwer sam posprząta stan widoczności
                }
            }
        }
        VANISH.clear();
        FLY_FROM_VANISH.clear();
        GOD.clear();
    }

    private void conceal(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player) && !viewer.hasPermission(PERM_VANISH_SEE)) {
                viewer.hidePlayer(plugin, player);
            }
        }
        player.setCollidable(false);
        player.setSleepingIgnored(true);
    }

    private void reveal(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                viewer.showPlayer(plugin, player);
            }
        }
        player.setCollidable(true);
        player.setSleepingIgnored(false);
    }

    // ───────────────────────── /god ─────────────────────────

    /** Blokuje wszystkie obrażenia poza voidem i /kill (żeby admin nie spadał w nieskończoność). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !GOD.contains(player.getUniqueId())) {
            return;
        }
        switch (event.getCause()) {
            case VOID, KILL, SUICIDE -> { }
            default -> event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && GOD.contains(player.getUniqueId())
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────── /vanish ─────────────────────────

    /** Gracz, który wchodzi na serwer, nie może zobaczyć osób będących w vanishu (chyba że ma permisję). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (joined.hasPermission(PERM_VANISH_SEE)) {
            return;
        }
        for (UUID id : VANISH) {
            Player vanished = Bukkit.getPlayer(id);
            if (vanished != null && !vanished.equals(joined)) {
                joined.hidePlayer(plugin, vanished);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (VANISH.remove(id)) {
            event.quitMessage(null);
        }
        if (FLY_FROM_VANISH.remove(id) && !isCreativeLike(event.getPlayer())) {
            event.getPlayer().setFlying(false);
            event.getPlayer().setAllowFlight(false);
        }
        GOD.remove(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && VANISH.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && VANISH.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Płyty naciskowe, tripwire itp. nie mogą zdradzać obecności gracza w vanishu. */
    @EventHandler
    public void onPhysicalInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && VANISH.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
