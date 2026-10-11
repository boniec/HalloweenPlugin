package boniec.dev.polezamek.zamek;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.polesmierci.PoleSmierciManager;
import boniec.dev.polezamek.util.ChunkLockManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class ZamekListener implements Listener {

    private final Main plugin;
    private final ZamekManager zamekManager;
    private final LodManager lodManager;
    private final PoleSmierciManager poleSmierciManager;
    private final ChunkLockManager chunkLockManager;

    public ZamekListener(Main plugin, ZamekManager zamekManager, LodManager lodManager, PoleSmierciManager poleSmierciManager, ChunkLockManager chunkLockManager) {
        this.plugin = plugin;
        this.zamekManager = zamekManager;
        this.lodManager = lodManager;
        this.poleSmierciManager = poleSmierciManager;
        this.chunkLockManager = chunkLockManager;
    }

    // ================= Selektor + uzycie zamku =================

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack tool = event.getItem();

        if (tool == null) return;
        Material typ = tool.getType();
        if (typ != ZamekManager.ZAMEK_MATERIAL && typ != ZamekManager.SELECTOR_MATERIAL) return;

        if (typ == ZamekManager.SELECTOR_MATERIAL && zamekManager.isSelectorItem(tool)) {
            if (event.getClickedBlock() == null) return;
            Location loc = event.getClickedBlock().getLocation();

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                zamekManager.setPos1(player, loc);
                player.sendMessage(color(zamekManager.getMsgSelector1(locToString(loc))));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                zamekManager.setPos2(player, loc);
                player.sendMessage(color(zamekManager.getMsgSelector2(locToString(loc))));
            }
            return;
        }

        if (typ == ZamekManager.ZAMEK_MATERIAL && zamekManager.isZamekItem(tool)) {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            event.setCancelled(true);
            tryUseZamek(player, tool);
        }
    }

    private void tryUseZamek(Player player, ItemStack tool) {
        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        if (!zamekManager.hasTemplate()) {
            player.sendMessage(color(zamekManager.getMsgNoTemplate()));
            return;
        }

        Location base = player.getLocation();

        if (zamekManager.isInBlockedRegion(base)) {
            player.sendMessage(color(zamekManager.getMsgBlockedRegion()));
            return;
        }

        if (zamekManager.isOnCooldown(player)) {
            player.sendMessage(color(zamekManager.getMsgOnCooldown(zamekManager.getCooldownRemainingSeconds(player))));
            return;
        }

        if (poleSmierciManager.isAnyFieldActiveWithin(base, 25)) {
            player.sendMessage(color(zamekManager.getMsgPoleActive()));
            return;
        }

        String chunkKey = zamekManager.chunkKey(base);
        if (zamekManager.isChunkOccupied(chunkKey)) {
            player.sendMessage(color(zamekManager.getMsgChunkOccupied()));
            return;
        }

        if (zamekManager.isChunkOnPlaceLock(chunkKey)) {
            player.sendMessage(color(zamekManager.getMsgChunkLocked(zamekManager.getChunkPlaceLockRemainingSeconds(chunkKey))));
            return;
        }

        if (chunkLockManager.isLocked(base)) {
            player.sendMessage(color(zamekManager.getMsgChunkSharedLocked(chunkLockManager.getRemainingSeconds(base))));
            return;
        }

        if (chunkLockManager.isTooCloseToRecentPlacement(base)) {
            player.sendMessage(color(zamekManager.getMsgTooClose(chunkLockManager.getProximityLockRemainingSeconds(base))));
            return;
        }

        // Zamek mozna dac wielokrotnie na ta sama osobe - jedyne ograniczenie to 25 kratek
        // od poprzedniego zamku/pola (chunkLockManager.isTooCloseToRecentPlacement wyzej).

        zamekManager.setCooldownNow(player);
        player.setCooldown(tool.getType(), (int) (zamekManager.getCooldownRemainingSeconds(player) * 20));

        chunkLockManager.markUsed(ChunkLockManager.Source.ZAMEK, base);

        ZamekManager.ActiveCastle castle = zamekManager.build(player, base);
        chunkLockManager.markPlacement(ChunkLockManager.Source.ZAMEK, base);

        if (zamekManager.isBossbarEnabled()) {
            castle.bossBar = zamekManager.createBossBar(zamekManager.getDurationSeconds());
            zamekManager.updateBossBarViewers(castle);
        }

        int durationTicks = zamekManager.getDurationSeconds() * 20;

        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                ticksElapsed += 5;

                if (ticksElapsed >= durationTicks) {
                    cancel();
                    startDisappearAnimation(player, castle);
                    return;
                }

                if (castle.bossBar != null) {
                    int secondsRemaining = (int) Math.ceil((durationTicks - ticksElapsed) / 20.0);
                    castle.bossBar.setTitle(zamekManager.formatBossbarTitle(secondsRemaining));
                    castle.bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) (durationTicks - ticksElapsed) / durationTicks)));
                    zamekManager.updateBossBarViewers(castle);
                }
            }
        }.runTaskTimer(plugin.getHost(), 5L, 5L);
    }

    private void startDisappearAnimation(Player player, ZamekManager.ActiveCastle castle) {
        List<List<String>> layers = new java.util.ArrayList<>(castle.blockKeysByLayer.values());

        if (layers.isEmpty()) {
            finishDisappear(player, castle);
            return;
        }

        double totalSeconds = zamekManager.getDisappearAnimationSeconds();
        if (totalSeconds <= 0) {
            finishDisappear(player, castle);
            return;
        }

        long totalTicks = Math.max(1L, Math.round(totalSeconds * 20));
        long tickInterval = Math.max(1L, totalTicks / layers.size());

        new BukkitRunnable() {
            int layerIndex = 0;

            @Override
            public void run() {
                if (layerIndex >= layers.size()) {
                    cancel();
                    finishDisappear(player, castle);
                    return;
                }
                zamekManager.restoreBlocks(layers.get(layerIndex));
                layerIndex++;
            }
        }.runTaskTimer(plugin.getHost(), 0L, tickInterval);
    }

    private void finishDisappear(Player player, ZamekManager.ActiveCastle castle) {
        zamekManager.finishCastle(castle);
        if (castle.bossBar != null) {
            castle.bossBar.removeAll();
            castle.bossBar.setVisible(false);
        }
        chunkLockManager.markUsed(ChunkLockManager.Source.ZAMEK, castle.base);
        player.sendMessage(color(zamekManager.getMsgDisappeared()));
    }

    // ================= Ochrona blokow zamku =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        String key = zamekManager.blockKey(event.getBlock().getWorld(), event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
        if (zamekManager.isBlockOccupiedByCastle(key)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color(zamekManager.getMsgCantBreak()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        String key = zamekManager.blockKey(b.getWorld(), b.getX(), b.getY(), b.getZ());
        if (zamekManager.isBlockOccupiedByCastle(key)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color(zamekManager.getMsgCantPlace()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> zamekManager.isBlockOccupiedByCastle(
                zamekManager.blockKey(b.getWorld(), b.getX(), b.getY(), b.getZ())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> zamekManager.isBlockOccupiedByCastle(
                zamekManager.blockKey(b.getWorld(), b.getX(), b.getY(), b.getZ())));
    }

    // ================= Lod na patyku =================

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        boolean maLod = lodManager.isLodItem(attacker.getInventory().getItemInMainHand())
                || lodManager.isLodItem(attacker.getInventory().getItemInOffHand());
        if (!maLod) return;

        if (plugin.isEventItemUseBlocked(attacker.getLocation())) {
            if (lodManager.isLodItem(attacker.getInventory().getItemInMainHand())) {
                event.setCancelled(true);
            }
            return;
        }

        if (lodManager.isOnCooldown(attacker)) {
            attacker.sendMessage(color(lodManager.getMsgOnCooldown(lodManager.getCooldownRemainingSeconds(attacker))));
            return;
        }

        if (lodManager.isVictimProtected(victim)) {
            attacker.sendMessage(color(lodManager.getMsgVictimProtected(lodManager.getVictimProtectionRemainingSeconds(victim))));
            return;
        }

        lodManager.setCooldownNow(attacker);
        ItemStack lodItem = attacker.getInventory().getItemInMainHand();
        if (!lodManager.isLodItem(lodItem)) lodItem = attacker.getInventory().getItemInOffHand();
        attacker.setCooldown(lodItem.getType(), (int) (lodManager.getCooldownRemainingSeconds(attacker) * 20));

        lodManager.setVictimProtectedNow(victim);

        int levSeconds = lodManager.getLevitationSeconds();
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, levSeconds * 20, 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, levSeconds * 20, 0));

        attacker.sendMessage(color(lodManager.getMsgHitOther(victim.getName())));
        victim.sendMessage(color(lodManager.getMsgHitSelf(attacker.getName())));
    }

    private String locToString(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
