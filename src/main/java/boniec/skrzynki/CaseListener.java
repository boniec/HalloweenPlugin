package boniec.skrzynki;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Obsluga klikniec:
 *  SKRZYNIA: klik w zarejestrowany blok = podglad; Shift + PPM = otwarcie bez animacji (potrzebny klucz).
 *  LOOTBOX : PPM przedmiotem w rece = otwarcie z animacja; Shift + PPM = od razu;
 *            LPM albo klik w postawiony blok (/acase ustawlokacje) = podglad bez przyciskow otwierania.
 */
public final class CaseListener implements Listener {

    private final JavaPlugin plugin;
    private final CaseManager mgr;
    private final CaseGuis guis;

    public CaseListener(JavaPlugin plugin, CaseManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.guis = new CaseGuis(mgr);
    }

    CaseGuis guis() {
        return guis;
    }

    // HIGHEST + ignoreCancelled=false: skrzynki dzialaja tez w regionach chronionych (np. spawn z WorldGuard).
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a == Action.PHYSICAL) return;
        Player p = e.getPlayer();

        // 1) Blok skrzynki (typ SKRZYNIA)
        Block b = e.getClickedBlock();
        if (b != null) {
            CaseDef c = mgr.caseAt(b);
            if (c != null) {
                e.setCancelled(true); // zadnego vanillowego otwierania / lamania
                if (e.getHand() != EquipmentSlot.HAND) return;
                if (a != Action.RIGHT_CLICK_BLOCK && a != Action.LEFT_CLICK_BLOCK) return;
                if (!mgr.cooldownOk(p)) return;
                if (c.type == CaseType.LOOTBOX) {
                    guis.openPreview(p, c, 0, false);
                } else if (a == Action.RIGHT_CLICK_BLOCK && p.isSneaking() && mgr.bool(c, "quickOpenCase", true)) {
                    mgr.tryOpen(p, c, true, true);
                } else {
                    guis.openPreview(p, c, 0, c.enabled);
                }
                return;
            }
        }

        // 2) Lootbox (przedmiot w rece)
        ItemStack inHand = e.getItem();
        if (inHand == null || inHand.getType().isAir()) return;
        CaseDef lb = mgr.findLootbox(inHand);
        if (lb == null) return;

        if (e.getHand() != EquipmentSlot.HAND) {
            // lootbox w off-hand: nie stawiamy go jako blok
            if (a == Action.RIGHT_CLICK_BLOCK || a == Action.RIGHT_CLICK_AIR) e.setCancelled(true);
            return;
        }

        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
            if (!mgr.cooldownOk(p)) return;
            mgr.tryOpen(p, lb, p.isSneaking() && mgr.bool(lb, "quickOpenCase", true), true);
            return;
        }
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            e.setUseItemInHand(Event.Result.DENY);
            if (!mgr.cooldownOk(p)) return;
            guis.openPreview(p, lb, 0, false);
        }
    }

    // Zarejestrowanych blokow nie da sie zniszczyc - najpierw /acase usunlokacje.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent e) {
        CaseDef c = mgr.caseAt(e.getBlock());
        if (c == null) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Txt.color("&8[&6Skrzynki&8] &cTo blok skrzynki &r" + c.displayName
                + "&c. Najpierw uzyj &e/acase usunlokacje " + c.id + "&c."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> mgr.caseAt(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> mgr.caseAt(b) != null);
    }

    // ------------------------------------------------------------------ GUI

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof CaseGui gui)) return;
        if (!(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        if (gui.kind == CaseGui.Kind.EDIT) {
            // edytor sam decyduje co anulowac (klikanie w wlasnym ekwipunku dziala normalnie)
            if (!p.hasPermission("acase.admin")) {
                e.setCancelled(true);
                return;
            }
            guis.handleEditClick(e, p, gui);
            return;
        }
        e.setCancelled(true); // podglad / animacja: zadnego wyciagania / wkladania / shift-klikania
        if (gui.kind == CaseGui.Kind.PREVIEW) guis.handlePreviewClick(e, p, gui);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof CaseGui gui)) return;
        if (gui.kind == CaseGui.Kind.EDIT) {
            // przeciaganie po wlasnym ekwipunku jest ok, po edytorze nie
            int top = e.getInventory().getSize();
            for (int raw : e.getRawSlots()) {
                if (raw < top) {
                    e.setCancelled(true);
                    return;
                }
            }
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof CaseGui g && g.kind == CaseGui.Kind.EDIT
                && e.getPlayer() instanceof Player ep) {
            guis.onEditorClosed(ep, g);
        }
        if (e.getInventory().getHolder() instanceof CaseGui gui && gui.kind == CaseGui.Kind.ANIMATION
                && gui.animation != null) {
            gui.animation.onClosed();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        mgr.finishAnimation(p.getUniqueId()); // trwajace losowanie => nagroda od razu, nic nie przepada
        mgr.forget(p.getUniqueId());
    }

    // Wpisywanie wagi dropu na czacie (z edytora dropu)
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!mgr.pending.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> guis.completeWeightPrompt(p, msg));
    }
}
