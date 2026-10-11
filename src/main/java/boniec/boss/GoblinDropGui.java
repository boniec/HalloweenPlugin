package boniec.boss;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GoblinDropGui implements Listener {

    private static final int ROZMIAR = 54;
    private static final int SLOTY_DROPU = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_ZAPISZ = 53;
    private static final int SLOT_WYCZYSC = 45;

    private static final double[] SZANSE_KLAWISZE = {1, 5, 10, 25, 50, 75, 100, 0.5, 0.1};

    private final JavaPlugin plugin;
    private final GoblinDropManager dropManager;
    private final NamespacedKey kluczSzansa;

    public GoblinDropGui(JavaPlugin plugin, GoblinDropManager dropManager) {
        this.plugin = plugin;
        this.dropManager = dropManager;
        this.kluczSzansa = new NamespacedKey(plugin, "goblin_drop_szansa");
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final double[] szanse = new double[SLOTY_DROPU];

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void otworz(Player gracz) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, ROZMIAR,
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Drop Goblina");
        holder.inventory = inv;

        List<GoblinDropManager.Wpis> wpisy = dropManager.getWpisy();
        for (int i = 0; i < wpisy.size() && i < SLOTY_DROPU; i++) {
            GoblinDropManager.Wpis w = wpisy.get(i);
            holder.szanse[i] = w.getSzansa();
            inv.setItem(i, zPodgladem(w.getItem(), w.getSzansa()));
        }

        wypelnijPasek(inv);
        gracz.openInventory(inv);
    }

    private void wypelnijPasek(Inventory inv) {
        ItemStack szyba = przycisk(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = SLOTY_DROPU; slot < ROZMIAR; slot++) {
            inv.setItem(slot, szyba);
        }
        inv.setItem(SLOT_WYCZYSC, przycisk(Material.LAVA_BUCKET, ChatColor.RED + "" + ChatColor.BOLD + "Wyczysc caly drop",
                ChatColor.GRAY + "Kliknij Shift + LPM, aby usunac",
                ChatColor.GRAY + "wszystkie wpisy (zwroci przedmioty)."));
        inv.setItem(SLOT_INFO, przycisk(Material.BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "Jak to dziala",
                ChatColor.GRAY + "Wloz przedmiot w pusty slot na gorze.",
                "",
                ChatColor.YELLOW + "Szansa (klik w przedmiot):",
                ChatColor.GRAY + "LPM " + ChatColor.GREEN + "+1%" + ChatColor.GRAY + "  |  PPM " + ChatColor.RED + "-1%",
                ChatColor.GRAY + "Shift+LPM " + ChatColor.GREEN + "+10%" + ChatColor.GRAY + "  |  Shift+PPM " + ChatColor.RED + "-10%",
                ChatColor.GRAY + "Q " + ChatColor.GREEN + "+0.1%" + ChatColor.GRAY + "  |  Ctrl+Q " + ChatColor.RED + "-0.1%",
                ChatColor.GRAY + "Klawisze 1-9: " + ChatColor.WHITE + "1/5/10/25/50/75/100/0.5/0.1%",
                "",
                ChatColor.YELLOW + "Usuniecie wpisu: " + ChatColor.WHITE + "srodkowy przycisk myszy",
                ChatColor.GRAY + "Zapis nastepuje przy zamknieciu okna."));
        inv.setItem(SLOT_ZAPISZ, przycisk(Material.LIME_DYE, ChatColor.GREEN + "" + ChatColor.BOLD + "Zapisz i zamknij"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory gora = event.getView().getTopInventory();
        if (!(gora.getHolder() instanceof Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player gracz)) return;

        boolean klikWGorze = event.getClickedInventory() != null && event.getClickedInventory().equals(gora);
        int slot = event.getSlot();
        ClickType klik = event.getClick();
        InventoryAction akcja = event.getAction();

        if (!klikWGorze) {
            if (klik.isShiftClick() && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                event.setCancelled(true);
                int wolny = pierwszyWolnySlot(gora);
                if (wolny == -1) {
                    gracz.sendMessage(ChatColor.RED + "Brak wolnych slotow na drop.");
                    return;
                }
                ItemStack przenoszony = event.getCurrentItem().clone();
                event.setCurrentItem(null);
                holder.szanse[wolny] = GoblinDropManager.SZANSA_DOMYSLNA;
                gora.setItem(wolny, zPodgladem(przenoszony, GoblinDropManager.SZANSA_DOMYSLNA));
                return;
            }
            if (akcja == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

        if (slot >= SLOTY_DROPU) {
            obslugaPrzyciskow(gracz, holder, gora, slot, klik);
            return;
        }

        ItemStack wSlocie = gora.getItem(slot);
        boolean pusty = wSlocie == null || wSlocie.getType().isAir();
        ItemStack kursor = event.getCursor();
        boolean kursorPusty = kursor == null || kursor.getType().isAir();

        if (pusty) {
            if (!kursorPusty) {
                ItemStack nowy = kursor.clone();
                event.getView().setCursor(null);
                holder.szanse[slot] = GoblinDropManager.SZANSA_DOMYSLNA;
                gora.setItem(slot, zPodgladem(nowy, GoblinDropManager.SZANSA_DOMYSLNA));
            }
            return;
        }

        if (klik == ClickType.MIDDLE) {
            ItemStack czysty = bezPodgladu(wSlocie);
            gora.setItem(slot, null);
            holder.szanse[slot] = 0;
            oddaj(gracz, czysty);
            return;
        }

        double szansa = holder.szanse[slot];
        switch (klik) {
            case LEFT -> szansa += 1;
            case RIGHT -> szansa -= 1;
            case SHIFT_LEFT -> szansa += 10;
            case SHIFT_RIGHT -> szansa -= 10;
            case DROP -> szansa += 0.1;
            case CONTROL_DROP -> szansa -= 0.1;
            case NUMBER_KEY -> {
                int idx = event.getHotbarButton();
                if (idx >= 0 && idx < SZANSE_KLAWISZE.length) {
                    szansa = SZANSE_KLAWISZE[idx];
                }
            }
            default -> {
                return;
            }
        }
        szansa = zaokraglij(Math.max(GoblinDropManager.SZANSA_MIN, Math.min(GoblinDropManager.SZANSA_MAX, szansa)));
        holder.szanse[slot] = szansa;
        gora.setItem(slot, zPodgladem(bezPodgladu(wSlocie), szansa));
    }

    private void obslugaPrzyciskow(Player gracz, Holder holder, Inventory gora, int slot, ClickType klik) {
        if (slot == SLOT_ZAPISZ) {
            gracz.closeInventory();
            return;
        }
        if (slot == SLOT_WYCZYSC && klik == ClickType.SHIFT_LEFT) {
            for (int i = 0; i < SLOTY_DROPU; i++) {
                ItemStack it = gora.getItem(i);
                if (it != null && !it.getType().isAir()) {
                    oddaj(gracz, bezPodgladu(it));
                    gora.setItem(i, null);
                    holder.szanse[i] = 0;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory gora = event.getView().getTopInventory();
        if (!(gora.getHolder() instanceof Holder)) return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < ROZMIAR) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof Holder holder)) return;

        List<GoblinDropManager.Wpis> nowe = new ArrayList<>();
        for (int i = 0; i < SLOTY_DROPU; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            nowe.add(new GoblinDropManager.Wpis(bezPodgladu(it), holder.szanse[i]));
        }
        dropManager.ustawWpisy(nowe);

        if (event.getPlayer() instanceof Player gracz) {
            gracz.sendMessage(ChatColor.GREEN + "Zapisano drop Goblina (" + nowe.size() + " wpisow).");
        }
    }

    private ItemStack zPodgladem(ItemStack oryginal, double szansa) {
        ItemStack kopia = oryginal.clone();
        ItemMeta meta = kopia.getItemMeta();
        if (meta == null) return kopia;

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        int dodane = 0;
        lore.add(""); dodane++;
        lore.add(ChatColor.GOLD + "Szansa: " + ChatColor.YELLOW + formatuj(szansa) + "%"); dodane++;
        lore.add(ChatColor.DARK_GRAY + "LPM/PPM: +-1%  Shift: +-10%  Q: +-0.1%"); dodane++;
        lore.add(ChatColor.DARK_GRAY + "Srodkowy przycisk: usun wpis"); dodane++;
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(kluczSzansa, PersistentDataType.INTEGER, dodane);
        kopia.setItemMeta(meta);
        return kopia;
    }

    private ItemStack bezPodgladu(ItemStack zPodgladem) {
        ItemStack kopia = zPodgladem.clone();
        ItemMeta meta = kopia.getItemMeta();
        if (meta == null) return kopia;

        Integer dodane = meta.getPersistentDataContainer().get(kluczSzansa, PersistentDataType.INTEGER);
        if (dodane == null) return kopia;

        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        int docelowo = Math.max(0, lore.size() - dodane);
        List<String> oryginalne = new ArrayList<>(lore.subList(0, docelowo));
        meta.setLore(oryginalne.isEmpty() ? null : oryginalne);
        meta.getPersistentDataContainer().remove(kluczSzansa);
        kopia.setItemMeta(meta);
        return kopia;
    }

    private int pierwszyWolnySlot(Inventory inv) {
        for (int i = 0; i < SLOTY_DROPU; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) return i;
        }
        return -1;
    }

    private void oddaj(Player gracz, ItemStack item) {
        var reszta = gracz.getInventory().addItem(item);
        for (ItemStack extra : reszta.values()) {
            gracz.getWorld().dropItemNaturally(gracz.getLocation(), extra);
        }
    }

    private ItemStack przycisk(Material material, String nazwa, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(nazwa);
            if (lore.length > 0) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(s);
                meta.setLore(l);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static double zaokraglij(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String formatuj(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.1f", v);
    }
}