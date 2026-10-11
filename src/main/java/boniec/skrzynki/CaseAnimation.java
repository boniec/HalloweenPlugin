package boniec.skrzynki;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Animacja losowania wedlug sekcji spinData w configu: sloty animationSlots przewijaja sie i zwalniaja,
 * a w srodkowym slocie zatrzymuje sie wylosowany przedmiot. Wynik jest ustalony PRZED startem, wiec:
 *  - zamkniecie ekwipunku w trakcie, wyjscie z serwera i wylaczenie pluginu od razu wydaja nagrode,
 *  - animacja jest tylko pokazem.
 */
final class CaseAnimation {

    private final CaseManager mgr;
    private final Player player;
    private final CaseDef def;
    private final CaseDrop winner;

    private final List<ItemStack> strip = new ArrayList<>();
    private final List<Integer> slots = new ArrayList<>();
    private final int size;
    private final int steps;
    private final int delayStart;
    private final int delayEnd;

    private int offset = 0;
    private boolean rewarded = false;
    private boolean guiOpen = false;
    private boolean finished = false;
    private BukkitTask task;
    private CaseGui gui;

    CaseAnimation(CaseManager mgr, Player player, CaseDef def, CaseDrop winner) {
        this.mgr = mgr;
        this.player = player;
        this.def = def;
        this.winner = winner;

        int sz = mgr.integer(def, "spinData.size", 45);
        this.size = Math.max(9, Math.min(54, (sz / 9) * 9));

        for (int slot : mgr.intList(def, "spinData.animationSlots")) {
            if (slot >= 0 && slot < size && !slots.contains(slot)) slots.add(slot);
        }
        if (slots.isEmpty()) {
            // domyslnie: 5 srodkowych slotow srodkowego rzedu
            int base = (size / 9 / 2) * 9;
            for (int i = 2; i <= 6; i++) slots.add(base + i);
        }

        this.steps = Math.max(5, mgr.integer(def, "spinData.steps", 32));
        this.delayStart = Math.max(1, mgr.integer(def, "spinData.delayStart", 1));
        this.delayEnd = Math.max(delayStart, mgr.integer(def, "spinData.delayEnd", 7));
    }

    void start() {
        int len = slots.size();
        int total = steps + len;
        int winnerIndex = steps + len / 2; // koncowe okno: indeksy steps..steps+len-1, srodek = wygrana
        for (int i = 0; i < total; i++) {
            CaseDrop d = i == winnerIndex ? winner : mgr.roll(def);
            strip.add(d.item.clone());
        }

        gui = new CaseGui(CaseGui.Kind.ANIMATION, def);
        gui.animation = this;
        String title = Txt.color(Txt.replace(mgr.str(def, "spinData.title", "&8Losowanie"), mgr.basePlaceholders(def)));
        Inventory inv = Bukkit.createInventory(gui, size, title);
        gui.inventory = inv;

        mgr.applyDecorations(inv, def, "spinData.itemDecorations", mgr.basePlaceholders(def));

        render();
        guiOpen = true;
        player.openInventory(inv);
        task = Bukkit.getScheduler().runTaskLater(mgr.plugin(), () -> step(), delay(0));
    }

    private void render() {
        Inventory inv = gui.inventory;
        for (int i = 0; i < slots.size(); i++) {
            inv.setItem(slots.get(i), strip.get(offset + i).clone());
        }
    }

    private long delay(int n) {
        double t = steps <= 1 ? 1.0 : (double) n / (steps - 1);
        return Math.max(1L, Math.round(delayStart + (delayEnd - delayStart) * t * t));
    }

    private void step() {
        if (finished) return;
        offset++;
        render();
        mgr.playConfigSound(player, def, "spinSound");
        if (offset >= steps) {
            rewardNow();
            long closeAfter = Math.max(1, mgr.integer(def, "spinData.closeAfter", 30));
            task = Bukkit.getScheduler().runTaskLater(mgr.plugin(), () -> closeGui(), closeAfter);
            return;
        }
        task = Bukkit.getScheduler().runTaskLater(mgr.plugin(), () -> step(), delay(offset));
    }

    private void rewardNow() {
        if (rewarded) return;
        rewarded = true;
        mgr.award(player, def, winner);
    }

    private void closeGui() {
        if (guiOpen && player.isOnline()) {
            player.closeInventory(); // -> InventoryCloseEvent -> onClosed()
        } else {
            cleanup();
        }
    }

    /** Wywolywane z InventoryCloseEvent (gracz zamknal okno albo zamknelismy je my). */
    void onClosed() {
        guiOpen = false;
        if (!rewarded) {
            offset = steps;
            rewardNow();
        }
        cleanup();
    }

    /** Wyjscie gracza / wylaczenie pluginu / reload: wydaj nagrode od razu i posprzataj. */
    void finishNow() {
        if (task != null) task.cancel();
        if (!rewarded) {
            offset = steps;
            if (gui != null && gui.inventory != null) render();
            rewardNow();
        }
        boolean open = guiOpen;
        cleanup();
        if (open && player.isOnline()) player.closeInventory();
    }

    private void cleanup() {
        finished = true;
        if (task != null) task.cancel();
        mgr.unregister(player.getUniqueId());
    }
}
