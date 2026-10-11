package boniec.ahalloween;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class AHalloweenListener implements Listener {

    private final AHalloweenManager manager;

    public AHalloweenListener(AHalloweenManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.dane(event.getPlayer());
        if (!manager.aktywny()) return;
        Player p = event.getPlayer();
        boolean ma = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (manager.jestWorkiem(it)) { ma = true; break; }
        }
        if (!ma) p.getInventory().addItem(manager.stworzWorek());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        HalloweenPlayer p = manager.dane(event.getPlayer());
        p.oznacz();
        manager.baza().zapisz(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmor(PlayerArmorStandManipulateEvent event) {
        if (manager.jestObiektem(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity e = event.getRightClicked();
        if (!manager.jestObiektem(e)) return;
        event.setCancelled(true);
        manager.zbierz(event.getPlayer(), e, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity e = event.getRightClicked();
        if (!manager.jestObiektem(e)) return;
        event.setCancelled(true);
        manager.zbierz(event.getPlayer(), e, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTrickOrTreat(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (!manager.trickOrTreat().obsluzPukanie(player, block)) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!manager.aktywny()) return;
        double zasieg = manager.cfg().getDouble("event.cukierki-zasieg-klikniecia", 4.5);
        Entity cel = manager.cukierekWPatrzeniu(player, zasieg);
        if (cel != null) {
            event.setCancelled(true);
            manager.zbierz(player, cel, true);
            return;
        }
        ItemStack item = event.getItem();
        if (!manager.jestWorkiem(item)) return;
        event.setCancelled(true);
        HalloweenGuis.otworz(manager, event.getPlayer(), HalloweenGuis.Typ.BAG);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.jestWorkiem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(manager::jestWorkiem);
        Player p = event.getEntity();
        boolean ma = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (manager.jestWorkiem(it)) { ma = true; break; }
        }
        if (!ma && manager.aktywny()) {
            event.getItemsToKeep().add(manager.stworzWorek());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDmg(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!manager.jestMobem(victim)) return;
        Player p = null;
        if (event.getDamager() instanceof Player pl) p = pl;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player pl) p = pl;
        if (p == null) return;
        manager.zapiszObrazenia(victim.getUniqueId(), p.getUniqueId(), manager.obrazeniaDoRankingu(victim, event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!manager.jestMobem(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = event.getEntity().getKiller();
        manager.mobZginal(event.getEntity(), killer);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGui(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof HalloweenGuis.Holder) {
            HalloweenGuis.klik(manager, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HalloweenGuis.Holder) {
            event.setCancelled(true);
        }
    }
}
