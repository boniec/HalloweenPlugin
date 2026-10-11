package boniec.code.core.backup;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Wspólny layout 45-slotowego GUI backupów.
 *
 * Ramka:
 *  ŻÓŁTE        -> 0, 8, 36, 44
 *  POMARAŃCZOWE -> 1, 7, 9, 17, 27, 35, 37, 43
 *  BIAŁE        -> 2, 3, 5, 6, 18, 26, 38, 39, 41, 42
 *
 * Wolne sloty (23): 4, 10-16, 19-25, 28-34, 40
 * Domyślnie 4 = info, 40 = zamknij/powrót -> 21 slotów na zawartość.
 * Ustaw RESERVE_INFO_AND_ACTION = false, aby wszystkie 23 sloty były na przedmioty.
 */
public final class BackupGuiLayout {

    public static final int SIZE = 45;

    public static final int[] YELLOW_SLOTS = {0, 8, 36, 44};
    public static final int[] ORANGE_SLOTS = {1, 7, 9, 17, 27, 35, 37, 43};
    public static final int[] WHITE_SLOTS  = {2, 3, 5, 6, 18, 26, 38, 39, 41, 42};

    public static final boolean RESERVE_INFO_AND_ACTION = true;

    public static final int INFO_SLOT   = 4;   // info / główka gracza
    public static final int ACTION_SLOT = 40;  // zamknij lub powrót
    public static final int PREV_SLOT   = 38;  // strzałka wstecz (podgląd wielostronicowy)
    public static final int NEXT_SLOT   = 42;  // strzałka dalej

    public static final int[] CONTENT_SLOTS = buildContentSlots();

    private BackupGuiLayout() {}

    private static int[] buildContentSlots() {
        boolean[] used = new boolean[SIZE];
        for (int s : YELLOW_SLOTS) used[s] = true;
        for (int s : ORANGE_SLOTS) used[s] = true;
        for (int s : WHITE_SLOTS)  used[s] = true;
        if (RESERVE_INFO_AND_ACTION) {
            used[INFO_SLOT] = true;
            used[ACTION_SLOT] = true;
        }
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) if (!used[i]) free.add(i);
        int[] arr = new int[free.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = free.get(i);
        return arr;
    }

    /** Wypełnia ramkę szkłem. */
    public static void frame(Inventory inv) {
        for (int s : YELLOW_SLOTS) inv.setItem(s, pane(Material.YELLOW_STAINED_GLASS_PANE));
        for (int s : ORANGE_SLOTS) inv.setItem(s, pane(Material.ORANGE_STAINED_GLASS_PANE));
        for (int s : WHITE_SLOTS)  inv.setItem(s, pane(Material.WHITE_STAINED_GLASS_PANE));
    }

    public static void closeButton(Inventory inv) {
        if (!RESERVE_INFO_AND_ACTION) return;
        inv.setItem(ACTION_SLOT, build(Material.BARRIER, "§c§lZamknij",
                Collections.singletonList("§7Kliknij, aby zamknąć menu.")));
    }

    public static void backButton(Inventory inv) {
        if (!RESERVE_INFO_AND_ACTION) return;
        inv.setItem(ACTION_SLOT, build(Material.ARROW, "§e§lPowrót",
                Collections.singletonList("§7Kliknij, aby wrócić.")));
    }

    public static ItemStack pane(Material material) {
        ItemStack is = new ItemStack(material);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r ");
            meta.setLore(new ArrayList<>());
            is.setItemMeta(meta);
        }
        return is;
    }

    public static ItemStack build(Material material, String name, List<String> lore) {
        ItemStack is = new ItemStack(material);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null) meta.setLore(new ArrayList<>(lore));
            is.setItemMeta(meta);
        }
        return is;
    }

    public static ItemStack build(Material material, String name, String... lore) {
        return build(material, name, Arrays.asList(lore));
    }

    /** Bezpieczne pobranie materiału (GLOW_ITEM_FRAME istnieje dopiero od 1.17). */
    public static Material material(String name, Material fallback) {
        Material m = Material.getMaterial(name);
        return m != null ? m : fallback;
    }
}