package boniec.ustawienia;

import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class UstawieniaGui implements Listener, InventoryHolder {

    private static final String TITLE = Msg.color("&8Ustawienia");
    private final JavaPlugin plugin;
    private final UstawieniaManager manager;
    private final NamespacedKey kluczKey;
    private Inventory inv;

    public UstawieniaGui(JavaPlugin plugin, UstawieniaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.kluczKey = new NamespacedKey(plugin, "ustawienie");
    }

    public void otworz(Player p) {
        Inventory gui = Bukkit.createInventory(this, 27, TITLE);
        this.inv = gui;
        wypelnij(p, gui);
        p.openInventory(gui);
    }

    private void wypelnij(Player p, Inventory gui) {
        gui.clear();
        for (UstawieniaKlucz k : UstawieniaKlucz.values()) {
            boolean on = manager.wlaczony(p.getUniqueId(), k);
            ItemStack item = new ItemStack(k.ikona);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Msg.color((on ? "&a" : "&c") + k.nazwa));
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(Msg.color(k.opis));
                lore.add("");
                lore.add(Msg.color(on ? "&aWlaczony" : "&cWylaczony"));
                lore.add(Msg.color("&7Kliknij, aby " + (on ? "&cwylaczyc" : "&awlaczyc")));
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                meta.getPersistentDataContainer().set(kluczKey, PersistentDataType.STRING, k.id);
                item.setItemMeta(meta);
            }
            gui.setItem(k.slot, item);
        }
        gui.setItem(22, dekor(Material.ARROW, "&7Zamknij"));
    }

    private static ItemStack dekor(Material mat, String nazwa) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(nazwa));
            it.setItemMeta(meta);
        }
        return it;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof UstawieniaGui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        if (e.getSlot() == 22) {
            p.closeInventory();
            return;
        }
        String id = clicked.getItemMeta().getPersistentDataContainer().get(kluczKey, PersistentDataType.STRING);
        UstawieniaKlucz k = UstawieniaKlucz.zId(id);
        if (k == null) return;
        boolean on = manager.przelacz(p, k);
        p.sendMessage(Msg.color("&8[&eUstawienia&8] " + (on ? "&aWlaczono &f" : "&cWylaczono &f") + k.nazwa));
        wypelnij(p, e.getInventory());
    }
}
