package boniec.dev.polezamek.eventitems;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class SauronGearListener implements Listener {

    private final SauronGearManager mgr;
    private final SauronGearTask task;

    public SauronGearListener(SauronGearManager mgr, SauronGearTask task) {
        this.mgr = mgr;
        this.task = task;
    }

    /**
     * Po uderzeniu miecz Saurona dostaje enchanty najlepszego miecza z /ulepsz.
     * LOWEST + rejestracja PRZED HitSystemListener, zeby system setow policzyl obrazenia
     * juz z nowymi enchantami (set miecza wynika z poziomu Sharpness).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHitSyncEnchants(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!mgr.isSauronSword(hand)) return;

        if (mgr.syncEnchants(hand)) {
            attacker.getInventory().setItemInMainHand(hand);
        }
    }

    /** +10% obrazen (mnoznik z configu). HIGH = po HitSystem (LOWEST) i CombatListener (NORMAL). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitDamage(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!mgr.isSauronSword(attacker.getInventory().getItemInMainHand())) return;

        double mult = mgr.getDamageMultiplier();
        if (mult == 1.0) return;
        event.setDamage(event.getDamage() * mult);
    }

    // ===================== Glowy smoka: zakladanie prawym =====================

    @EventHandler
    public void onHeadRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;
        if (mgr.getHead(item) == null) return;

        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack currentHelmet = inv.getHelmet();

        event.setCancelled(true);
        inv.setHelmet(item.clone());
        inv.setItemInMainHand(currentHelmet == null ? new ItemStack(Material.AIR) : currentHelmet);
    }

    /** Glowy smoka nie mozna postawic jako blok. */
    @EventHandler(ignoreCancelled = true)
    public void onHeadPlace(BlockPlaceEvent event) {
        if (event.getItemInHand().getType() != Material.PLAYER_HEAD) return;
        if (mgr.getHead(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    // ===================== Buty smoka: exp + drop =====================

    /** Wiekszy exp (x1.5 krolewskie, x1.25 ogniste). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent event) {
        double mult = mgr.getExpMultiplier(event.getPlayer());
        if (mult <= 1.0) return;
        int amount = event.getAmount();
        if (amount <= 0) return;
        double scaled = amount * mult;
        int whole = (int) scaled;
        // Losowe zaokraglanie, zeby male porcje expa (np. 1 x 1.25) tez srednio dawaly wiecej.
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < scaled - whole) whole++;
        event.setAmount(whole);
    }

    /**
     * Wiekszy drop z generatorow: dodatkowe itemy tylko gdy blok NIE jest placony przez ekonomie
     * kopania (np. welna bez ceny w kopanie.yml). Przy piasku/kamieniu itd. caly bonus idzie
     * do kasy w {@link boniec.gornik.MiningListener} (Fortune, buty, szaman, turbo...).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGeneratorBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        double mult = mgr.getDropMultiplier(player);
        if (mult <= 1.0) return;
        if (!(mgr.getPlugin().getHost() instanceof boniec.dev.inventory.Main inv)) return;
        if (inv.getGeneratorManager() == null) return;
        if (inv.getGeneratorManager().getGeneratorAt(event.getBlock().getLocation()) == null) return;
        boniec.gornik.GornikManager gornik = inv.getGornikManager();
        if (gornik != null && gornik.placiEkonomiaKopania(event.getBlock().getLocation(), event.getBlock().getType())) {
            return;
        }
        double nocny = inv.getGeneratorManager().mnoznikNocnyPvp(event.getBlock().getLocation());

        for (ItemStack drop : event.getBlock().getDrops(player.getInventory().getItemInMainHand(), player)) {
            if (drop == null) continue;
            int add = (int) Math.round(drop.getAmount() * (mult - 1.0) * nocny);
            if (add <= 0) continue;
            ItemStack bonus = drop.clone();
            bonus.setAmount(add);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), bonus);
        }
    }

    // ===================== Porzadki =====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        task.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        task.forget(event.getEntity().getUniqueId());
    }
}
