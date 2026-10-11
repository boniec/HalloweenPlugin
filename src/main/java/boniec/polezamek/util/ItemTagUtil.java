package boniec.dev.polezamek.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

/**
 * Wspolne narzedzia do rozpoznawania i odswiezania itemow eventowych.
 *
 * Itemy sa rozpoznawane po tagu PDC, a NIE po nazwie - dzieki temu zmiana nazwy
 * w config.yml nie psuje juz wydanych itemow. Dodatkowo zapewniamy migracje
 * starych itemow (bez tagu), ktore da sie rozpoznac po dawnej nazwie.
 */
public final class ItemTagUtil {

    private ItemTagUtil() {}

    /** Zwraca tag itemu (np. "pole", "zamek", "lod", "selector") albo null. */
    public static String getTag(NamespacedKey key, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(key, PersistentDataType.STRING);
    }

    /** Ustawia tag na meta (nie zapisuje meta do itemu - robi to wolajacy). */
    public static void setTag(NamespacedKey key, ItemMeta meta, String tag) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
    }

    /**
     * Sprawdza czy item jest danego typu: po tagu, a jesli tagu brak - po znanych
     * (aktualnej lub dawnych) nazwach. Przy trafieniu po nazwie item zostaje
     * od razu otagowany (migracja) - wymaga zapisu meta, wiec zmieniamy item "w miejscu".
     */
    public static boolean matches(NamespacedKey key, ItemStack item, String tag, String... legacyNames) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String stored = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (stored != null) return stored.equals(tag);

        // Stary item bez tagu - dopasuj po nazwie i zmigruj.
        if (!meta.hasDisplayName()) return false;
        String display = meta.getDisplayName();
        for (String legacy : legacyNames) {
            if (legacy != null && legacy.equals(display)) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
                item.setItemMeta(meta);
                return true;
            }
        }
        return false;
    }

    /**
     * Odswieza nazwe i lore itemu do aktualnych wartosci z configu.
     * Zwraca true jesli cos sie zmienilo.
     */
    public static boolean refresh(ItemStack item, String newName, List<String> newLore) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        boolean changed = false;
        if (newName != null && !Objects.equals(meta.getDisplayName(), newName)) {
            meta.setDisplayName(newName);
            changed = true;
        }
        if (newLore != null && !Objects.equals(meta.getLore(), newLore)) {
            meta.setLore(newLore);
            changed = true;
        }
        if (changed) item.setItemMeta(meta);
        return changed;
    }

    /** Ustawia CustomModelData na meta (cmd <= 0 = brak/usuniecie). */
    public static void applyModel(ItemMeta meta, int cmd) {
        meta.setCustomModelData(cmd > 0 ? cmd : null);
    }

    /**
     * Dopasowuje CustomModelData itemu do wartosci z configu (cmd <= 0 = brak).
     * Zwraca true jesli cos sie zmienilo.
     */
    public static boolean syncModel(ItemStack item, int cmd) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Integer have = meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        Integer want = cmd > 0 ? cmd : null;
        if (Objects.equals(have, want)) return false;
        meta.setCustomModelData(want);
        item.setItemMeta(meta);
        return true;
    }

    /** Pomocniczo: przechodzi po calym ekwipunku gracza (w tym pancerz i offhand). */
    public static void forEachItem(Player player, java.util.function.Consumer<ItemStack> action) {
        Inventory inv = player.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (it != null) action.accept(it);
        }
    }
}
