package boniec.dev.polezamek.eventitems;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.polesmierci.PoleSmierciManager;
import boniec.dev.polezamek.zamek.LodManager;
import boniec.dev.polezamek.zamek.ZamekManager;
import boniec.dev.polezamek.witchwand.HookManager;
import boniec.dev.polezamek.witchwand.WitchWandManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class EventItemsGUI implements CommandExecutor, Listener {

    /** Holder rozpoznajacy nasze GUI niezaleznie od tytulu (tytul mozna zmienic w configu). */
    private static final class GuiHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final int SIZE = 54;

    private final Main plugin;
    private final EventItemsManager eventItemsManager;
    private final SauronGearManager sauronGearManager;
    private final ZamekManager zamekManager;
    private final LodManager lodManager;
    private final PoleSmierciManager poleSmierciManager;
    private final WitchWandManager witchWandManager;
    private final HookManager hookManager;

    public EventItemsGUI(Main plugin, EventItemsManager eventItemsManager,
                         SauronGearManager sauronGearManager,
                         ZamekManager zamekManager, LodManager lodManager,
                         PoleSmierciManager poleSmierciManager,
                         WitchWandManager witchWandManager, HookManager hookManager) {
        this.plugin = plugin;
        this.eventItemsManager = eventItemsManager;
        this.sauronGearManager = sauronGearManager;
        this.zamekManager = zamekManager;
        this.lodManager = lodManager;
        this.poleSmierciManager = poleSmierciManager;
        this.witchWandManager = witchWandManager;
        this.hookManager = hookManager;
    }

    private Inventory createInventory() {
        String title = eventItemsManager.getMsgGuiTitle();
        GuiHolder holder = new GuiHolder();
        Inventory inv = plugin.getServer().createInventory(holder, SIZE, title);
        holder.inventory = inv;

        ItemStack[] items = buildItems();
        for (int i = 0; i < items.length && i < SIZE; i++) {
            inv.setItem(i, items[i]);
        }

        return inv;
    }

    /** Kolejnosc itemow w GUI - sloty 0..N po kolei, bez dekoracji. */
    private ItemStack[] buildItems() {
        return new ItemStack[] {
                eventItemsManager.createAntycobweb(),
                eventItemsManager.createOlej(),
                eventItemsManager.createMocVadera(),
                eventItemsManager.createMocYody(),
                eventItemsManager.createCukierki(),
                eventItemsManager.createSmoczaFajerwerka(),
                eventItemsManager.createJablkoJedi(),
                eventItemsManager.createKamienOdrodzenia(),
                eventItemsManager.createKoza(),
                eventItemsManager.createEventowkaRandom(),
                eventItemsManager.createSakiewka(),
                eventItemsManager.createKosaGrabaza(),
                eventItemsManager.createGwozdzTrumny(),
                witchWandManager.createWandItem(),
                hookManager.createHookItem(),
                sauronGearManager.createSauronSword(),
                sauronGearManager.createDragonHead(SauronGearManager.DragonHead.KROLEWSKI),
                sauronGearManager.createDragonHead(SauronGearManager.DragonHead.OGNISTY),
                sauronGearManager.createDragonHead(SauronGearManager.DragonHead.MROZNY),
                sauronGearManager.createDragonBoots(SauronGearManager.DragonBoots.KROLEWSKIE),
                sauronGearManager.createDragonBoots(SauronGearManager.DragonBoots.OGNISTE),
                sauronGearManager.createDragonBoots(SauronGearManager.DragonBoots.MROZNE)
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko gracz mo\u017ce u\u017cy\u0107 tej komendy.");
            return true;
        }

        if (!player.hasPermission("polezamek.admin")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnie\u0144 do tej komendy!");
            return true;
        }

        player.openInventory(createInventory());
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) return;

        // Swiezy egzemplarz tego samego itemu (a nie kopia z GUI)
        ItemStack[] items = buildItems();
        ItemStack toGive = slot < items.length ? items[slot] : null;

        if (toGive != null) {
            player.getInventory().addItem(toGive);
            String itemName = toGive.getItemMeta().getDisplayName();
            player.sendMessage(color(eventItemsManager.getMsgGuiReceived(itemName)));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
