package boniec.pvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/** PPM trzymając klatę lub elytrę zakłada item na slot zbroi (zamiana z tym, co było). */
public final class NoChestEquipListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        ItemStack wRece = p.getInventory().getItemInMainHand();
        if (!jestNaSlotKlaty(wRece)) return;
        if (klikaBlokInteraktywny(e)) return;

        e.setCancelled(true);
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);

        ItemStack zakladany = wRece.clone();
        zakladany.setAmount(1);
        ItemStack poprzedni = p.getInventory().getChestplate();
        ItemStack poprzedniKopia = (poprzedni == null || poprzedni.getType().isAir())
                ? null
                : poprzedni.clone();
        int zostaloWRece = wRece.getAmount() - 1;
        Material typWRece = wRece.getType();

        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(NoChestEquipListener.class), () -> {
            if (!p.isOnline()) return;
            PlayerInventory inv = p.getInventory();
            ItemStack terazWRece = inv.getItemInMainHand();
            if (terazWRece.getType() != typWRece || terazWRece.getAmount() < 1) return;

            if (p.isGliding()) {
                p.setGliding(false);
            }

            if (zostaloWRece > 0) {
                terazWRece.setAmount(zostaloWRece);
                inv.setItemInMainHand(terazWRece);
                if (poprzedniKopia != null) {
                    for (ItemStack reszta : inv.addItem(poprzedniKopia).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), reszta);
                    }
                }
            } else {
                inv.setItemInMainHand(poprzedniKopia);
            }
            inv.setChestplate(zakladany);
            p.updateInventory();
        });
    }

    private static boolean klikaBlokInteraktywny(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getPlayer().isSneaking()) {
            return false;
        }
        Block b = e.getClickedBlock();
        return b != null && b.getType().isInteractable();
    }

    private static boolean jestNaSlotKlaty(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        return it.getType().getEquipmentSlot() == EquipmentSlot.CHEST;
    }
}
