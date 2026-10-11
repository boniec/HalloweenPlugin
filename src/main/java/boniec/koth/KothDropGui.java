package boniec.koth;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI /adkoth editdrop - 3 wiersze: TOP 1, TOP 2, TOP 3.
 * Pierwszy slot kazdego wiersza to etykieta (nie do ruszenia), pozostale 8 to miejsce
 * na nagrody. Zapis nastepuje przy zamknieciu okna.
 */
public class KothDropGui implements Listener {

    private static final int ROZMIAR = 27;

    /** Holder pozwalajacy rozpoznac nasze okno. */
    public static final class Holder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final KothManager manager;

    public KothDropGui(KothManager manager) {
        this.manager = manager;
    }

    public void otworz(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, ROZMIAR, Msg.color("&#940707&lKOTH &8» &fNagrody"));
        holder.inv = inv;

        Material[] materialy = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};
        String[] nazwy = {"&6&lTOP 1", "&7&lTOP 2", "&c&lTOP 3"};
        for (int i = 0; i < 3; i++) {
            inv.setItem(i * 9, etykieta(materialy[i], nazwy[i]));
            List<ItemStack> lista = manager.getNagrody(i + 1);
            for (int j = 0; j < lista.size() && j < 8; j++) {
                inv.setItem(i * 9 + 1 + j, lista.get(j));
            }
        }
        p.openInventory(inv);
    }

    private ItemStack etykieta(Material m, String nazwa) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(nazwa));
            meta.setLore(List.of(
                    Msg.color("&7Wloz nagrody w wolne pola obok."),
                    Msg.color("&7Zapis nastapi po zamknieciu okna.")));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static boolean jestEtykieta(int rawSlot) {
        return rawSlot >= 0 && rawSlot < ROZMIAR && rawSlot % 9 == 0;
    }

    private static boolean nasze(Inventory top) {
        return top != null && top.getHolder() instanceof Holder;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!nasze(e.getView().getTopInventory())) return;
        if (jestEtykieta(e.getRawSlot())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!nasze(e.getView().getTopInventory())) return;
        for (int slot : e.getRawSlots()) {
            if (jestEtykieta(slot)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!nasze(inv)) return;

        List<List<ItemStack>> nowe = new ArrayList<>();
        for (int wiersz = 0; wiersz < 3; wiersz++) {
            List<ItemStack> lista = new ArrayList<>();
            for (int j = 1; j <= 8; j++) {
                ItemStack it = inv.getItem(wiersz * 9 + j);
                if (it != null && it.getType() != Material.AIR) lista.add(it.clone());
            }
            nowe.add(lista);
        }
        manager.zapiszNagrody(nowe);
        if (e.getPlayer() instanceof Player p) {
            p.sendMessage(manager.msg("nagrody-zapisane"));
        }
    }
}
