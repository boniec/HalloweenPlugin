package boniec.skrzynki;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GUI podgladu dropu (w pelni z configu) oraz edytor dropu dla admina (/acase edytujdrop). */
final class CaseGuis {

    private final CaseManager mgr;

    CaseGuis(CaseManager mgr) {
        this.mgr = mgr;
    }

    // =================================================================== PODGLAD

    void openPreview(Player p, CaseDef c, int page) {
        openPreview(p, c, page, c.type == CaseType.SKRZYNIA);
    }

    void openPreview(Player p, CaseDef c, int page, boolean allowOpen) {
        int size = previewSize(c);
        CaseGui gui = new CaseGui(CaseGui.Kind.PREVIEW, c);
        gui.guiSize = size;
        gui.allowOpen = allowOpen;
        gui.page = Math.max(0, page);

        Map<String, String> ph = previewPh(p, c, gui.page);
        String title = Txt.color(Txt.replace(mgr.str(c, "previewWinItem.title", "&8Podglad: {skrzynka}"), ph));
        Inventory inv = Bukkit.createInventory(gui, size, title);
        gui.inventory = inv;
        fillPreview(p, gui, true);
        mgr.playConfigSound(p, c, "previewCaseSound");
        p.openInventory(inv);
    }

    private int previewSize(CaseDef c) {
        return Math.max(9, Math.min(54, (mgr.integer(c, "previewWinItem.size", 45) / 9) * 9));
    }

    private Map<String, String> previewPh(Player p, CaseDef c, int page) {
        Map<String, String> ph = mgr.basePlaceholders(c);
        ph.put("strona", String.valueOf(page + 1));
        ph.put("stron", "1");
        ph.put("status-klucza", Txt.color(mgr.hasKey(p, c)
                ? mgr.str(c, "previewWinItem.keyStatusHas", "&aMasz klucz")
                : mgr.str(c, "previewWinItem.keyStatusMissing", "&cNie masz klucza")));
        return ph;
    }

    /** Wypelnia istniejace GUI podgladu (bez zamykania okna). */
    private void fillPreview(Player p, CaseGui gui, boolean startRotate) {
        CaseDef c = gui.def;
        Inventory inv = gui.inventory;
        int size = inv.getSize();
        gui.actions.clear();
        inv.clear();

        Map<String, String> ph = previewPh(p, c, gui.page);
        mgr.applyDecorations(inv, c, "previewWinItem.itemDecorations", ph);
        placeDrops(inv, c, size);

        if (gui.allowOpen && c.type == CaseType.SKRZYNIA) {
            placeItem(gui, inv, mgr.section(c, "animationItem"),
                    mgr.intList(c, "previewWinItem.animationSlots"), "OPEN_ANIM", ph);
            placeItem(gui, inv, mgr.section(c, "noAnimationItem"),
                    mgr.intList(c, "previewWinItem.noAnimationSlots"), "OPEN_QUICK", ph);
        }

        final int slotWin = mgr.integer(c, "previewWinItem.slotWinItem", -1);
        boolean slotWinZajety = slotWin >= 0 && (dropNaSlocie(c, slotWin) || dropSlotZConfigu(c, size, slotWin));
        if (startRotate && slotWin >= 0 && slotWin < size && !c.drops.isEmpty() && !slotWinZajety) {
            inv.setItem(slotWin, c.drops.get(0).item.clone());
            final long period = Math.max(5, mgr.integer(c, "previewWinItem.rotateTicks", 20));
            new BukkitRunnable() {
                int idx = 0;

                @Override
                public void run() {
                    if (inv.getViewers().isEmpty() || c.drops.isEmpty()) {
                        cancel();
                        return;
                    }
                    idx = (idx + 1) % c.drops.size();
                    inv.setItem(slotWin, c.drops.get(idx).item.clone());
                }
            }.runTaskTimer(mgr.plugin(), period, period);
        }
    }

    private static boolean dropNaSlocie(CaseDef c, int slot) {
        for (CaseDrop d : c.drops) {
            if (d.slot == slot) return true;
        }
        return false;
    }

    private boolean dropSlotZConfigu(CaseDef c, int size, int slot) {
        return parseSlots(mgr.stringList(c, "previewWinItem.dropSlots"), size).contains(slot);
    }

    private void placeDrops(Inventory inv, CaseDef c, int size) {
        List<String> loreCfg = mgr.stringList(c, "previewWinItem.dropLore");
        double total = mgr.totalWeight(c);
        boolean anySlot = false;
        for (CaseDrop d : c.drops) {
            if (d.slot >= 0) {
                anySlot = true;
                break;
            }
        }
        if (anySlot) {
            for (CaseDrop d : c.drops) {
                if (d.slot >= 0 && d.slot < size) inv.setItem(d.slot, decorate(d, total, loreCfg));
            }
            return;
        }
        List<Integer> dropSlots = parseSlots(mgr.stringList(c, "previewWinItem.dropSlots"), size);
        for (int i = 0; i < dropSlots.size() && i < c.drops.size(); i++) {
            inv.setItem(dropSlots.get(i), decorate(c.drops.get(i), total, loreCfg));
        }
    }

    /** Przycisk z pojedynczym slotem: sekcja ma pole slot. */
    private void button(CaseGui gui, Inventory inv, CaseDef c, String path, String action, Map<String, String> ph) {
        ConfigurationSection s = mgr.section(c, path);
        if (s == null) return;
        int slot = s.getInt("slot", -1);
        if (slot < 0 || slot >= inv.getSize()) return;
        ItemStack it = itemFrom(s, ph);
        if (it == null) return;
        inv.setItem(slot, it);
        gui.actions.put(slot, action);
    }

    /** Ten sam przedmiot we wszystkich podanych slotach (np. animationSlots: [19, 20]). */
    private void placeItem(CaseGui gui, Inventory inv, ConfigurationSection s, List<Integer> slots, String action,
                           Map<String, String> ph) {
        ItemStack it = itemFrom(s, ph);
        if (it == null) return;
        for (int slot : slots) {
            if (slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, it.clone());
            if (action != null) gui.actions.put(slot, action);
        }
    }

    private ItemStack itemFrom(ConfigurationSection s, Map<String, String> ph) {
        if (s == null) return null;
        String mat = s.getString("material", "");
        if (mat.isEmpty() || mat.equalsIgnoreCase("AIR")) return null;
        return ItemSpec.from(s, Material.STONE).build(ph, true);
    }

    private ItemStack decorate(CaseDrop d, double total, List<String> loreCfg) {
        ItemStack it = d.item.clone();
        if (loreCfg.isEmpty()) return it;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        Map<String, String> ph = dropPlaceholders(d, total);
        for (String line : loreCfg) lore.add(Txt.color(Txt.replace(line, ph)));
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private Map<String, String> dropPlaceholders(CaseDrop d, double total) {
        double pct = total > 0 && d.weight > 0 ? d.weight / total * 100.0 : 0.0;
        return Txt.map(
                "szansa", Txt.num(pct),
                "waga", Txt.num(d.weight),
                "ilosc", String.valueOf(d.item.getAmount()),
                "ogloszenie", d.announce ? "&aTAK" : "&cNIE");
    }

    void handlePreviewClick(InventoryClickEvent e, Player p, CaseGui gui) {
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;
        String action = gui.actions.get(e.getRawSlot());
        if (action == null) return;
        CaseDef c = gui.def;
        int page = gui.page;
        switch (action) {
            case "PREV" -> Bukkit.getScheduler().runTask(mgr.plugin(), () -> openPreview(p, c, page - 1, gui.allowOpen));
            case "NEXT" -> Bukkit.getScheduler().runTask(mgr.plugin(), () -> openPreview(p, c, page + 1, gui.allowOpen));
            case "OPEN_ANIM" -> Bukkit.getScheduler().runTask(mgr.plugin(), () -> {
                p.closeInventory();
                mgr.tryOpen(p, c, false, false);
            });
            case "OPEN_QUICK" -> Bukkit.getScheduler().runTask(mgr.plugin(), () -> {
                mgr.tryOpen(p, c, true, false);
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof CaseGui open
                        && open.kind == CaseGui.Kind.PREVIEW && open.def == c) {
                    fillPreview(p, open, false);
                }
            });
            default -> {
            }
        }
    }

    // =================================================================== EDYTOR DROPU

    /**
     * Edytor w ukladzie GUI skrzynki: dekoracje jak w podgladzie, dropy na konkretnych slotach
     * (miecz na 21 zostaje na 21, nie zsuwa sie do kupy).
     */
    void openEditor(Player p, CaseDef c) {
        int size = previewSize(c);
        CaseGui gui = new CaseGui(CaseGui.Kind.EDIT, c);
        gui.guiSize = size;
        gui.locked.addAll(lockedPreviewSlots(c, size));

        for (CaseDrop d : c.drops) {
            if (d.slot >= 0 && d.slot < size && gui.slots[d.slot] == null) {
                gui.slots[d.slot] = d;
            } else {
                int free = firstFree(gui);
                if (free >= 0) {
                    d.slot = free;
                    gui.slots[free] = d;
                } else {
                    gui.overflow.add(d);
                }
            }
        }

        Map<String, String> ph = mgr.basePlaceholders(c);
        String title = Txt.color(Txt.replace(mgr.str(null, "edycja.tytul", "&8Edycja dropu: {skrzynka}"), ph));
        Inventory inv = Bukkit.createInventory(gui, size, title);
        gui.inventory = inv;
        renderEditor(gui);
        p.openInventory(inv);

        ph.put("drop-ilosc", String.valueOf(c.drops.size()));
        ph.put("max", String.valueOf(size - gui.locked.size()));
        for (String line : mgr.stringList(null, "edycja.pomoc")) {
            p.sendMessage(Txt.color(Txt.replace(line, ph)));
        }
        if (!gui.overflow.isEmpty()) {
            p.sendMessage(Txt.color("&8[&6Skrzynki&8] &eUwaga: &7" + gui.overflow.size()
                    + " &7dropow poza GUI zostaje bez zmian."));
        }
    }

    private Set<Integer> lockedPreviewSlots(CaseDef c, int size) {
        Set<Integer> locked = new HashSet<>();
        for (ConfigurationSection s : mgr.sectionList(c, "previewWinItem.itemDecorations")) {
            for (int slot : s.getIntegerList("slots")) {
                if (slot >= 0 && slot < size) locked.add(slot);
            }
        }
        ConfigurationSection prev = mgr.section(c, "previewWinItem.previousPageItem");
        if (prev != null) locked.add(prev.getInt("slot", -1));
        ConfigurationSection next = mgr.section(c, "previewWinItem.nextPageItem");
        if (next != null) locked.add(next.getInt("slot", -1));
        locked.addAll(mgr.intList(c, "previewWinItem.animationSlots"));
        locked.addAll(mgr.intList(c, "previewWinItem.noAnimationSlots"));
        locked.removeIf(i -> i < 0 || i >= size);
        return locked;
    }

    private void renderEditor(CaseGui gui) {
        CaseDef c = gui.def;
        Inventory inv = gui.inventory;
        int size = inv.getSize();
        inv.clear();
        Map<String, String> ph = mgr.basePlaceholders(c);
        ph.put("status-klucza", Txt.color("&eEDYCJA"));
        mgr.applyDecorations(inv, c, "previewWinItem.itemDecorations", ph);
        placeItem(gui, inv, mgr.section(c, "animationItem"),
                mgr.intList(c, "previewWinItem.animationSlots"), null, ph);
        placeItem(gui, inv, mgr.section(c, "noAnimationItem"),
                mgr.intList(c, "previewWinItem.noAnimationSlots"), null, ph);
        double total = 0;
        for (CaseDrop d : gui.slots) if (d != null) total += Math.max(0, d.weight);
        List<String> loreCfg = mgr.stringList(null, "edycja.lore-dropu");
        for (int i = 0; i < size; i++) {
            CaseDrop d = gui.slots[i];
            if (d != null) inv.setItem(i, decorate(d, total, loreCfg));
        }
    }

    /** Przepisuje sloty edytora do listy dropow skrzynki i zapisuje. */
    private void syncAndSave(CaseGui gui) {
        CaseDef c = gui.def;
        c.drops.clear();
        int size = gui.inventory != null ? gui.inventory.getSize() : gui.guiSize;
        for (int i = 0; i < size; i++) {
            CaseDrop d = gui.slots[i];
            if (d == null) continue;
            d.slot = i;
            c.drops.add(d);
        }
        c.drops.addAll(gui.overflow);
        mgr.save();
    }

    private void refresh(CaseGui gui) {
        syncAndSave(gui);
        renderEditor(gui);
    }

    private int firstFree(CaseGui gui) {
        int size = gui.inventory != null ? gui.inventory.getSize() : gui.guiSize;
        for (int i = 0; i < size; i++) {
            if (gui.locked.contains(i)) continue;
            if (gui.slots[i] == null) return i;
        }
        return -1;
    }

    private boolean copyMode() {
        return mgr.bool(null, "edycja.kopiuj-przedmioty", false);
    }

    /** Czysty przedmiot (bez lore edytora) - to, co laduje w dropie / wraca graczowi. */
    private static ItemStack clean(ItemStack it) {
        return it.clone();
    }

    void handleEditClick(InventoryClickEvent e, Player p, CaseGui gui) {
        e.setCancelled(true); // domyslnie wszystko w edytorze obslugujemy sami
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;
        boolean inTop = clicked.equals(e.getInventory());

        // ------------------------------------------------ wlasny ekwipunek gracza
        if (!inTop) {
            if (e.isShiftClick()) {
                // Shift+klik w ekwipunku => przenies do pierwszego wolnego slotu edytora
                ItemStack cur = e.getCurrentItem();
                if (!isValid(cur)) return;
                int free = firstFree(gui);
                if (free < 0) {
                    p.sendMessage(Txt.color("&8[&6Skrzynki&8] &cEdytor jest pelny. Usun jakis drop albo poloz na wolnym slocie."));
                    return;
                }
                gui.slots[free] = new CaseDrop(clean(cur), 1.0, false, free);
                if (!copyMode()) e.setCurrentItem(null);
                refresh(gui);
                return;
            }
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) return; // double-click zebralby z edytora
            // zwykle klikniecie we wlasnym ekwipunku - dziala normalnie
            gui.held = null;
            e.setCancelled(false);
            return;
        }

        // ------------------------------------------------ gorne okno (edytor)
        int slot = e.getRawSlot();
        int size = gui.inventory.getSize();
        if (slot < 0 || slot >= size) return;
        ClickType ct = e.getClick();
        CaseDrop existing = gui.slots[slot];
        ItemStack cursor = e.getCursor();
        if (gui.locked.contains(slot) && existing == null) return;

        if (isValid(cursor)) {
            if (ct != ClickType.LEFT && ct != ClickType.RIGHT) return;
            boolean copy = copyMode();
            CaseDrop nd;
            if (gui.held != null && gui.held.item.isSimilar(cursor)) {
                nd = gui.held;
                nd.item = clean(cursor);
                nd.slot = slot;
            } else {
                nd = new CaseDrop(clean(cursor), 1.0, false, slot);
            }
            if (existing == null) {
                gui.slots[slot] = nd;
                if (!copy) p.setItemOnCursor(null);
                gui.held = null;
            } else if (copy) {
                gui.slots[slot] = nd;
            } else {
                // zamiana: stary drop ladowuje na kursorze, nowy w slocie
                p.setItemOnCursor(clean(existing.item));
                gui.held = existing;
                gui.slots[slot] = nd;
            }
            refresh(gui);
            return;
        }

        if (existing == null) return;

        if (ct == ClickType.DROP || ct == ClickType.CONTROL_DROP) {
            gui.slots[slot] = null;
            refresh(gui);
        } else if (ct == ClickType.SHIFT_LEFT) {
            existing.announce = !existing.announce;
            refresh(gui);
        } else if (ct == ClickType.RIGHT) {
            mgr.pending.put(p.getUniqueId(), new CaseManager.Pending(gui.def, existing));
            Bukkit.getScheduler().runTask(mgr.plugin(), () -> {
                p.closeInventory();
                p.sendMessage(Txt.color("&8[&6Skrzynki&8] &7Wpisz na czacie nowa &ewage &7(np. &e2.5&7) dla tego dropu "
                        + "albo &eanuluj&7. Im wieksza waga, tym wieksza szansa."));
            });
        } else if (ct == ClickType.LEFT) {
            // wez drop na kursor (jak zwykly przedmiot) - przestaje byc dropem dopoki go gdzies nie polozysz
            if (!copyMode()) {
                p.setItemOnCursor(clean(existing.item));
                gui.held = existing;
                gui.slots[slot] = null;
                refresh(gui);
            }
        }
    }

    /** Zamkniecie edytora: drop trzymany na kursorze wraca do dropow (nic nie przepada). */
    void onEditorClosed(Player p, CaseGui gui) {
        ItemStack cursor = p.getItemOnCursor();
        if (gui.held != null && isValid(cursor) && gui.held.item.isSimilar(cursor)) {
            int free = firstFree(gui);
            if (free >= 0) gui.slots[free] = gui.held;
            else gui.overflow.add(gui.held);
            p.setItemOnCursor(null);
        }
        gui.held = null;
        syncAndSave(gui);
    }

    /** Dokonczenie edycji wagi po wpisaniu liczby na czacie (wywolywane na glownym watku). */
    void completeWeightPrompt(Player p, String input) {
        CaseManager.Pending pend = mgr.pending.remove(p.getUniqueId());
        if (pend == null) return;
        String in = input.trim();
        if (!in.equalsIgnoreCase("anuluj")) {
            try {
                double w = Double.parseDouble(in.replace(',', '.'));
                if (Double.isNaN(w) || Double.isInfinite(w) || w <= 0) throw new NumberFormatException();
                pend.drop().weight = w;
                mgr.save();
                p.sendMessage(Txt.color("&8[&6Skrzynki&8] &aUstawiono wage: &e" + Txt.num(w)));
            } catch (NumberFormatException ex) {
                p.sendMessage(Txt.color("&8[&6Skrzynki&8] &cTo nie jest poprawna liczba dodatnia - anulowano."));
            }
        } else {
            p.sendMessage(Txt.color("&8[&6Skrzynki&8] &7Anulowano."));
        }
        openEditor(p, pend.def());
    }

    private static boolean isValid(ItemStack it) {
        return it != null && !it.getType().isAir();
    }

    // =================================================================== pomocnicze

    /** "10-16", "19" -> lista slotow (w granicach inventory). */
    private static List<Integer> parseSlots(List<String> raw, int size) {
        List<Integer> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) continue;
            String t = s.trim();
            int dash = t.indexOf('-');
            if (dash > 0) {
                int a = parseIntSafe(t.substring(0, dash).trim(), -1);
                int b = parseIntSafe(t.substring(dash + 1).trim(), -1);
                for (int i = Math.max(0, a); a >= 0 && i <= b && i < size; i++) out.add(i);
            } else {
                int a = parseIntSafe(t, -1);
                if (a >= 0 && a < size) out.add(a);
            }
        }
        return out;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
