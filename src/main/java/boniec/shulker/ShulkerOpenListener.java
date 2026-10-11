package boniec.shulker;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PPM shulkerem w rece otwiera jego zawartosc. Shulkerow nie da sie postawic (gracz ani dispenser).
 * Otwarty shulker jest oznaczony tokenem w PDC i zablokowany w ekwipunku (nie da sie go ruszyc,
 * wyrzucic ani zamienic), a zawartosc jest zapisywana do itemu po kazdej zmianie - item i GUI
 * nigdy nie rozjezdzaja sie dluzej niz 1 tick.
 */
public final class ShulkerOpenListener implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey tokenKey;
    private final NamespacedKey eventItemKey;
    private static final String SAKIEWKA = "sakiewka";
    private static final String SAKIEWKA_LOOT = "sakiewka_loot";
    private final Map<UUID, Sesja> sesje = new HashMap<>();
    private final Set<UUID> zaplanowanyZapis = new HashSet<>();

    private static final class Sesja {
        final String token;
        final int slot;
        final Inventory inv;

        Sesja(String token, int slot, Inventory inv) {
            this.token = token;
            this.slot = slot;
            this.inv = inv;
        }
    }

    private static final class ShulkerHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public ShulkerOpenListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tokenKey = new NamespacedKey(plugin, "shulker_sesja");
        this.eventItemKey = new NamespacedKey(plugin, "event_item");
    }

    private static boolean jestShulkerem(ItemStack item) {
        return item != null && Tag.SHULKER_BOXES.isTagged(item.getType());
    }

    private String typEventItemu(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(eventItemKey, PersistentDataType.STRING);
    }

    private boolean jestSakiewkaZLupami(ItemStack item) {
        return SAKIEWKA_LOOT.equals(typEventItemu(item));
    }

    /** Shulkery z danymi innych pluginow w PDC maja wlasna logike - nie ruszamy ich. Sakiewki otwieramy. */
    private boolean jestSpecjalny(ItemStack item) {
        String typ = typEventItemu(item);
        if (!item.hasItemMeta() || SAKIEWKA.equals(typ) || SAKIEWKA_LOOT.equals(typ)) return false;
        return item.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .anyMatch(k -> !k.equals(tokenKey));
    }

    private String token(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
    }

    private boolean jestZablokowany(Player p, ItemStack item) {
        Sesja s = sesje.get(p.getUniqueId());
        return s != null && s.token.equals(token(item));
    }

    // ------------------------------------------------------------------ otwieranie

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!jestShulkerem(item) || jestSpecjalny(item)) return;

        if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking()) {
            Block b = event.getClickedBlock();
            if (b != null && b.getType().isInteractable()) return;
        }

        event.setCancelled(true);
        otworz(p, item);
    }

    private void otworz(Player p, ItemStack item) {
        if (sesje.containsKey(p.getUniqueId())) return;
        // Stack >1 (creative/pluginy) = kazdy egzemplarz dostalby te sama zawartosc.
        if (item.getAmount() != 1) {
            p.sendMessage("§cRozdziel shulkery, zeby otworzyc jeden.");
            return;
        }
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return;
        }

        String token = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, token);
        item.setItemMeta(meta);
        int slot = p.getInventory().getHeldItemSlot();
        p.getInventory().setItem(slot, item);

        ShulkerHolder holder = new ShulkerHolder();
        String tytul = meta.hasDisplayName() ? meta.getDisplayName() : "Shulker Box";
        Inventory inv = Bukkit.createInventory(holder, InventoryType.SHULKER_BOX, tytul);
        holder.inv = inv;
        inv.setContents(box.getInventory().getContents());

        sesje.put(p.getUniqueId(), new Sesja(token, slot, inv));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 0.8f, 1f);
    }

    // ------------------------------------------------------------------ zapis

    /** Szuka zablokowanego shulkera w ekwipunku (zwykle na zapamietanym slocie). */
    private int znajdzSlot(Player p, Sesja s) {
        if (s.token.equals(token(p.getInventory().getItem(s.slot)))) return s.slot;
        ItemStack[] zawartosc = p.getInventory().getContents();
        for (int i = 0; i < zawartosc.length; i++) {
            if (s.token.equals(token(zawartosc[i]))) return i;
        }
        return -1;
    }

    private void zapiszDoItemu(ItemStack item, Inventory inv, boolean zdejmijToken) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return;
        }
        box.getInventory().setContents(inv.getContents());
        meta.setBlockState(box);
        if (zdejmijToken) {
            meta.getPersistentDataContainer().remove(tokenKey);
        }
        item.setItemMeta(meta);
    }

    /** @return false gdy shulker zniknal z ekwipunku - sesja jest wtedy zamykana bez zapisu. */
    private boolean zapisz(Player p, boolean zakoncz) {
        Sesja s = sesje.get(p.getUniqueId());
        if (s == null) return false;
        int slot = znajdzSlot(p, s);
        if (slot < 0) {
            sesje.remove(p.getUniqueId());
            s.inv.clear();
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof ShulkerHolder) {
                p.closeInventory();
            }
            return false;
        }
        ItemStack item = p.getInventory().getItem(slot);
        zapiszDoItemu(item, s.inv, zakoncz);
        if (zakoncz && s.inv.isEmpty() && jestSakiewkaZLupami(item)) {
            item = null;
        }
        p.getInventory().setItem(slot, item);
        if (zakoncz) {
            sesje.remove(p.getUniqueId());
        }
        return true;
    }

    private void zaplanujZapis(Player p) {
        if (!zaplanowanyZapis.add(p.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            zaplanowanyZapis.remove(p.getUniqueId());
            if (p.isOnline() && sesje.containsKey(p.getUniqueId())) {
                zapisz(p, false);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShulkerHolder)) return;
        if (!(event.getPlayer() instanceof Player p)) return;
        if (zapisz(p, true)) {
            p.playSound(p.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 0.8f, 1f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (sesje.containsKey(p.getUniqueId())) {
            zapisz(p, true);
        }
    }

    /** Dropy sa juz skopiowane z ekwipunku - zapisujemy zawartosc bezposrednio do shulkera w dropach. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Sesja s = sesje.remove(p.getUniqueId());
        if (s == null) return;
        for (ItemStack drop : event.getDrops()) {
            if (s.token.equals(token(drop))) {
                zapiszDoItemu(drop, s.inv, true);
                break;
            }
        }
        for (ItemStack item : p.getInventory().getContents()) {
            if (s.token.equals(token(item))) {
                zapiszDoItemu(item, s.inv, true);
            }
        }
        s.inv.clear();
    }

    // ------------------------------------------------------------------ blokady

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        Sesja s = sesje.get(p.getUniqueId());
        if (s == null) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShulkerHolder)) return;

        Inventory top = event.getView().getTopInventory();
        Inventory kliknieta = event.getClickedInventory();
        ClickType click = event.getClick();

        // Otwarty shulker nie moze sie ruszyc ze swojego miejsca.
        if (jestZablokowany(p, event.getCurrentItem()) || jestZablokowany(p, event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (click == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar == s.slot || jestZablokowany(p, p.getInventory().getItem(hotbar))) {
                event.setCancelled(true);
                return;
            }
        }
        if (click == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }

        // Shulker w shulkerze - blokada jak w vanilli.
        if (kliknieta == top) {
            if (jestShulkerem(event.getCursor())) {
                event.setCancelled(true);
                return;
            }
            if (click == ClickType.NUMBER_KEY
                    && jestShulkerem(p.getInventory().getItem(event.getHotbarButton()))) {
                event.setCancelled(true);
                return;
            }
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && jestShulkerem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        zaplanujZapis(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!sesje.containsKey(p.getUniqueId())) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShulkerHolder)) return;

        int rozmiarTop = event.getView().getTopInventory().getSize();
        boolean doGory = event.getRawSlots().stream().anyMatch(slot -> slot < rozmiarTop);
        if (jestZablokowany(p, event.getOldCursor()) || (doGory && jestShulkerem(event.getOldCursor()))) {
            event.setCancelled(true);
            return;
        }
        zaplanujZapis(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (jestShulkerem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDispense(BlockDispenseEvent event) {
        if (jestShulkerem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (jestZablokowany(event.getPlayer(), event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (sesje.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Wylaczenie pluginu z otwartymi shulkerami - zapis i zamkniecie. */
    public void zamknijWszystkie() {
        for (UUID uuid : new HashSet<>(sesje.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                sesje.remove(uuid);
                continue;
            }
            zapisz(p, true);
            p.closeInventory();
        }
    }
}
