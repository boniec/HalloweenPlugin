package boniec.bany;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GUI /alts. Wyglad z bany.yml. */
public final class BanGui implements Listener {

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final String uuid;
        final String name;
        int page = 0;
        List<BanManager.AltEntry> alts = new ArrayList<>();
        final Map<Integer, BanManager.AltEntry> slotMap = new HashMap<>();

        Holder(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final BanManager m;

    public BanGui(BanManager m) {
        this.m = m;
    }

    public void openAlts(Player viewer, String targetArg) {
        final Player online = Bukkit.getPlayerExact(targetArg);
        final UUID ou = online != null ? online.getUniqueId() : null;
        final String on = online != null ? online.getName() : null;
        m.async(() -> {
            BanManager.Target t = m.resolve(targetArg, ou, on, null);
            if (t == null || t.literalIp || t.uuid.isEmpty()) {
                m.sync(() -> m.send(viewer, "wiadomosci.nie-znaleziono", "%gracz%", targetArg));
                return;
            }
            loadAndShowAlts(viewer, t.uuid, t.name);
        });
    }

    private void loadAndShowAlts(Player viewer, String uuid, String name) {
        List<BanManager.AltEntry> alts = m.loadAlts(uuid);
        m.sync(() -> {
            if (!viewer.isOnline()) return;
            Holder h = new Holder(uuid, name);
            h.alts = alts;
            h.inv = Bukkit.createInventory(h, 54, m.color(BanManager.rep(
                    m.cfg().getString("alts.tytul", "Alts"), "%gracz%", name)));
            render(viewer, h);
            viewer.openInventory(h.inv);
        });
    }

    private void render(Player viewer, Holder h) {
        Inventory inv = h.inv;
        inv.clear();
        h.slotMap.clear();
        if (m.cfg().getBoolean("gui.wypelniacz.wlaczony", true)) {
            ItemStack filler = m.item("gui.wypelniacz", null);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        }

        List<Integer> slots = m.contentSlots();
        int total = h.alts.size();
        int per = Math.max(1, slots.size());
        int pages = Math.max(1, (total + per - 1) / per);
        if (h.page >= pages) h.page = pages - 1;
        if (h.page < 0) h.page = 0;
        int from = h.page * per;
        String[] pagePh = {"%strona%", String.valueOf(h.page + 1), "%stron%", String.valueOf(pages)};

        boolean showIp = viewer.hasPermission(m.cfg().getString("alts.permisja-ip", "bany.alts.ip"));

        inv.setItem(m.slot("alts.info", 4), m.head("alts.info", h.uuid, null,
                "%gracz%", h.name, "%ile%", String.valueOf(h.alts.size()), "%status%", online(h.uuid)));

        if (h.alts.isEmpty()) {
            inv.setItem(m.slot("alts.brak", 22), m.item("alts.brak", null, "%gracz%", h.name));
        }

        for (int i = 0; i < slots.size(); i++) {
            int idx = from + i;
            if (idx >= h.alts.size()) break;
            BanManager.AltEntry a = h.alts.get(idx);
            List<String> extra = new ArrayList<>();
            List<String> ph = new ArrayList<>(List.of(
                    "%nick%", a.name,
                    "%status%", online(a.uuid),
                    "%pierwsze%", m.date(a.firstSeen),
                    "%ostatnie%", m.date(a.lastSeen),
                    "%wspolne-ip%", String.valueOf(a.shared),
                    "%ip%", a.ips.replace(",", "&7, &f"),
                    "%gracz%", h.name));
            if (showIp) extra.addAll(m.cfg().getStringList("alts.wpis.lore-ip"));
            ItemStack it = m.head("alts.wpis", a.uuid, extra, ph.toArray(new String[0]));
            inv.setItem(slots.get(i), it);
            h.slotMap.put(slots.get(i), a);
        }

        if (h.page > 0) inv.setItem(m.slot("gui.przyciski.poprzednia", 45), m.item("gui.przyciski.poprzednia", null, pagePh));
        if (h.page < pages - 1) inv.setItem(m.slot("gui.przyciski.nastepna", 53), m.item("gui.przyciski.nastepna", null, pagePh));
        inv.setItem(m.slot("gui.przyciski.zamknij", 49), m.item("gui.przyciski.zamknij", null, pagePh));
    }

    private String online(String uuid) {
        try {
            return Bukkit.getPlayer(UUID.fromString(uuid)) != null
                    ? m.cfg().getString("gui.statusy.online", "&aOnline")
                    : m.cfg().getString("gui.statusy.offline", "&7Offline");
        } catch (IllegalArgumentException e) {
            return m.cfg().getString("gui.statusy.offline", "&7Offline");
        }
    }

    private void click(Player p) {
        String snd = m.cfg().getString("gui.dzwieki.klik", "");
        if (snd != null && !snd.isEmpty()) p.playSound(p.getLocation(), snd, 0.6f, 1f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getRawSlot();
        int pages = Math.max(1, (h.alts.size() + m.contentSlots().size() - 1) / Math.max(1, m.contentSlots().size()));

        if (slot == m.slot("gui.przyciski.zamknij", 49)) {
            click(p);
            p.closeInventory();
            return;
        }
        if (slot == m.slot("gui.przyciski.poprzednia", 45) && h.page > 0) {
            click(p);
            h.page--;
            render(p, h);
            return;
        }
        if (slot == m.slot("gui.przyciski.nastepna", 53) && h.page < pages - 1) {
            click(p);
            h.page++;
            render(p, h);
            return;
        }

        BanManager.AltEntry a = h.slotMap.get(slot);
        if (a == null) return;
        click(p);
        m.async(() -> loadAndShowAlts(p, a.uuid, a.name));
    }
}
