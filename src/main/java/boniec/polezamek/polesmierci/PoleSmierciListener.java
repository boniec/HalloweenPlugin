package boniec.dev.polezamek.polesmierci;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.util.ChunkLockManager;
import boniec.dev.polezamek.util.TargetImmunity;
import boniec.dev.polezamek.zamek.ZamekManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class PoleSmierciListener implements Listener {

    private final Main plugin;
    private final PoleSmierciManager manager;
    private final ChunkLockManager chunkLockManager;
    private final TargetImmunity targetImmunity;
    private final ZamekManager zamekManager;

    public PoleSmierciListener(Main plugin, PoleSmierciManager manager, ChunkLockManager chunkLockManager, TargetImmunity targetImmunity, ZamekManager zamekManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.chunkLockManager = chunkLockManager;
        this.targetImmunity = targetImmunity;
        this.zamekManager = zamekManager;
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack tool = event.getItem();

        // --- Blokada fajerwerkow podczas blokady elytry ---
        if (tool != null && tool.getType() == Material.FIREWORK_ROCKET
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && manager.isElytraCurrentlyBlocked(player)) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1.2f);
            player.sendMessage(color(manager.getMsgBlockedFirework()));
            return;
        }

        // --- Uzycie Pola Smierci ---
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!manager.isPoleSmierciItem(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        if (manager.isOnCooldown(player)) {
            player.sendMessage(color(manager.getMsgOnCooldown(manager.getCooldownRemainingSeconds(player))));
            return;
        }

        Location below = player.getLocation().getBlock().getRelative(0, -1, 0).getLocation().add(0.5, 0, 0.5);
        below.setPitch(0);
        below.setYaw(0);

        if (manager.isNearForbiddenSpawn(below)) {
            player.sendMessage(color(manager.getMsgNearSpawn()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (manager.hasOtherActiveFieldNearby(below)) {
            player.sendMessage(color(manager.getMsgNearbyActive()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Dopoki zamek stoi (lacznie z animacja znikania) - pole zablokowane.
        // Gdy juz zniknie, jego blokady (chunk / 25 kratek) pola nie dotycza.
        if (zamekManager.isCastleActiveWithin(below, 25)) {
            player.sendMessage(color(manager.getMsgCastleActive()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (chunkLockManager.isLocked(below, ChunkLockManager.Source.ZAMEK)) {
            player.sendMessage(color(manager.getMsgChunkLocked(chunkLockManager.getRemainingSeconds(below, ChunkLockManager.Source.ZAMEK))));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (chunkLockManager.isTooCloseToRecentPlacement(below, ChunkLockManager.Source.ZAMEK)) {
            player.sendMessage(color(manager.getMsgTooClose(chunkLockManager.getProximityLockRemainingSeconds(below, ChunkLockManager.Source.ZAMEK))));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        activate(player, below, tool);
    }

    private void activate(Player player, Location center, ItemStack tool) {
        manager.setCooldownNow(player);
        chunkLockManager.markUsed(ChunkLockManager.Source.POLE, center);
        chunkLockManager.markPlacement(ChunkLockManager.Source.POLE, center);
        player.setCooldown(tool.getType(), manager.getCooldownSeconds() * 20);

        int r = manager.getRadius();
        PoleSmierciManager.ActiveField field = manager.registerField(center, player.getUniqueId());

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt((double) x * x + (double) z * z);
                if (dist <= r) {
                    Location loc = center.clone().add(x, 0, z);
                    org.bukkit.block.Block block = loc.getBlock();
                    field.savedLocs.add(loc);
                    field.savedTypes.add(block.getType());
                    if (dist > (r - 1)) {
                        block.setType(Material.RED_SANDSTONE);
                    } else {
                        block.setType(Material.SANDSTONE);
                    }
                }
            }
        }

        player.sendMessage(color(manager.getMsgActivated()));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
        for (Player nearby : center.getWorld().getPlayers()) {
            if (!nearby.equals(player) && nearby.getLocation().distance(center) <= 15) {
                nearby.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.8f);
            }
        }

        int totalTicks = manager.getDurationSeconds() * 40;

        new BukkitRunnable() {
            int counter = 0;

            @Override
            public void run() {
                counter++;

                for (Player p : center.getWorld().getPlayers()) {
                    if (p.getUniqueId().equals(field.activatorUUID)) continue;

                    Location loc = p.getLocation();
                    double dx = loc.getX() - center.getX();
                    double dz = loc.getZ() - center.getZ();
                    double pDist = Math.sqrt(dx * dx + dz * dz);
                    double yDiff = loc.getY() - center.getY();

                    if (pDist <= r && yDiff >= 0 && yDiff <= 4) {
                        // Na jedna osobe nie dzialaja 2 pola smierci: kto byl juz pod wplywem innego pola
                        // w ciagu ostatnich 60s (ochrona-celu-sekundy), tego to pole nie rusza.
                        if (!targetImmunity.tryApply(TargetImmunity.Type.POLE_SMIERCI, p.getUniqueId(), field)) continue;
                        manager.blockElytra(p);

                        if (counter % 10 == 0) {
                            p.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.5f);
                            p.playSound(loc, Sound.ENTITY_ENDERMAN_STARE, 0.15f, 0.5f);
                        }
                        if (counter % 40 == 0) {
                            p.damage(2.0);
                            p.playSound(loc, Sound.ENTITY_PLAYER_HURT, 0.7f, 0.8f);
                            p.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.2f);
                            p.sendMessage(color(manager.getMsgLaserHurt()));
                        }
                    }
                }

                if (counter >= totalTicks) {
                    restore(field);
                    manager.unregisterField(field);
                    chunkLockManager.markUsed(ChunkLockManager.Source.POLE, center);
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
                    player.sendMessage(color(manager.getMsgExpired()));
                    cancel();
                }
            }
        }.runTaskTimer(plugin.getHost(), 0L, 1L);
    }

    private void restore(PoleSmierciManager.ActiveField field) {
        for (int i = 0; i < field.savedLocs.size(); i++) {
            field.savedLocs.get(i).getBlock().setType(field.savedTypes.get(i));
        }
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
