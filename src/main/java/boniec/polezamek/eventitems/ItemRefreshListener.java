package boniec.dev.polezamek.eventitems;

import boniec.dev.polezamek.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Dba o to, zeby itemy eventowe ZAWSZE mialy aktualna nazwe i lore z configu,
 * nawet jesli zostaly wydane przed zmiana nazwy.
 */
public class ItemRefreshListener implements Listener {

    private final Main plugin;

    public ItemRefreshListener(Main plugin) {
        this.plugin = plugin;
    }

    /** Odswieza jeden item (wszystkie typy eventowe). Zwraca true jesli zmieniono. */
    public boolean refreshItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        boolean changed = false;
        changed |= plugin.getEventItemsManager().refreshItem(item);
        changed |= plugin.getSauronGearManager().refreshItem(item);
        changed |= plugin.getPoleSmierciManager().refreshItem(item);
        changed |= plugin.getZamekManager().refreshItem(item);
        changed |= plugin.getLodManager().refreshItem(item);
        changed |= plugin.getWitchWandManager().refreshItem(item);
        changed |= plugin.getHookManager().refreshItem(item);
        return changed;
    }

    /** Odswieza caly ekwipunek gracza (w tym pancerz i offhand). */
    public void refreshPlayer(Player player) {
        PlayerInventory inv = player.getInventory();
        boolean changed = false;
        for (ItemStack item : inv.getContents()) {
            changed |= refreshItem(item);
        }
        if (changed) player.updateInventory();
    }

    /** Odswieza wszystkich graczy online (wolane po /polezamekreload). */
    public void refreshAllOnline() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            refreshPlayer(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p) {
            refreshPlayer(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        ItemStack newItem = p.getInventory().getItem(event.getNewSlot());
        if (refreshItem(newItem)) p.updateInventory();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        // ItemStack z entity jest kopia - zmodyfikuj i zapisz z powrotem
        ItemStack stack = event.getItem().getItemStack();
        if (refreshItem(stack)) event.getItem().setItemStack(stack);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshPlayer(event.getPlayer());
    }
}
