package boniec.coinflip;

import boniec.klany.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CoinflipGui implements Listener {

    private static final int SIZE = 45;
    private static final int[] SLOT_GIER = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_INFO = 40;
    private static final int SLOT_HOST = 20;
    private static final int SLOT_GOSC = 24;
    private static final int SLOT_MONETA = 22;

    public static final class ListaHolder implements InventoryHolder {
        Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    public static final class LosowanieHolder implements InventoryHolder {
        Inventory inv;
        final UUID graId;
        LosowanieHolder(UUID graId) { this.graId = graId; }
        @Override
        public Inventory getInventory() { return inv; }
    }

    private final CoinflipManager manager;
    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> animacje = new ConcurrentHashMap<>();

    public CoinflipGui(CoinflipManager manager, JavaPlugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    public void otworzListe(Player p) {
        ListaHolder holder = new ListaHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, manager.tytul("gui.tytul-gry", "&#C77DFFꔣ ᴄᴏɪɴꜰʟɪᴘ"));
        holder.inv = inv;
        wypelnijListe(inv, p);
        p.openInventory(inv);
    }

    private void wypelnijListe(Inventory inv, Player p) {
        inv.clear();
        ramka(inv);
        List<CoinflipManager.Gra> gry = manager.listaGier();
        for (int i = 0; i < SLOT_GIER.length; i++) {
            if (i >= gry.size()) break;
            inv.setItem(SLOT_GIER[i], glowaGry(p, gry.get(i)));
        }
        int czek = manager.oczekujace(p.getUniqueId());
        int masz = boniec.galaktyczna.OdlamkiHelper.policz(p, boniec.dev.inventory.Main.getInstance());
        List<String> loreInfo = new ArrayList<>();
        loreInfo.add(CoinflipText.kolor(""));
        loreInfo.add(CoinflipText.kolor("&#C77DFFꔣ &#FFFFFFᴏᴄᴢᴇᴋᴜᴊᴀᴄᴇ: &#FF4FD8" + czek));
        loreInfo.add(CoinflipText.kolor("&#C77DFF丮 &#FFFFFFᴛᴡᴏᴊᴇ ᴏᴅłᴀᴍᴋɪ: &#FF4FD8" + masz));
        loreInfo.add(CoinflipText.kolor(""));
        loreInfo.add(CoinflipText.kolor("&#B8F5C0丣 &#FFFFFFᴋʟɪᴋ, ᴀʙʏ ᴏᴅᴇʙʀᴀᴄ"));
        loreInfo.add(CoinflipText.kolor("&8/coinflip stworz <ilosc>"));
        inv.setItem(SLOT_INFO, new ItemBuilder(Material.AMETHYST_SHARD)
                .nameRaw(CoinflipText.kolor("&#E8A0FF亡 &#FFFFFFᴏᴅʙɪᴏʀ"))
                .loreRaw(loreInfo)
                .hideFlags()
                .build());
    }

    private ItemStack glowaGry(Player p, CoinflipManager.Gra gra) {
        List<String> lore = new ArrayList<>();
        lore.add(CoinflipText.kolor(""));
        lore.add(CoinflipText.kolor("&#C77DFF丮 &#FFFFFFꜱᴛᴀᴡᴋᴀ: &#FF4FD8" + gra.stawka + " &#E8A0FFᴏᴅłᴀᴍᴋᴏᴡ"));
        lore.add(CoinflipText.kolor("&#C77DFF乴 &#FFFFFFᴘᴜʟᴀ: &#FF4FD8" + (gra.stawka * 2)));
        lore.add(CoinflipText.kolor("&#C77DFF乵 &#FFFFFFᴘᴏᴅᴀᴛᴇᴋ: &#FF4FD8" + manager.podatekProcent() + "%"));
        lore.add(CoinflipText.kolor(""));
        if (gra.host.equals(p.getUniqueId())) {
            lore.add(CoinflipText.kolor("&#FF6B9A个 &#FFFFFFᴋʟɪᴋ, ᴀʙʏ &#FF4FD8ᴀɴᴜʟᴏᴡᴀᴄ"));
        } else {
            lore.add(CoinflipText.kolor("&#B8F5C0丣 &#FFFFFFᴋʟɪᴋ, ᴀʙʏ &#B8F5C0ᴅᴏłᴀᴄᴢʏᴄ"));
        }
        return ItemBuilder.glowaGracza(Bukkit.getOfflinePlayer(gra.host))
                .nameRaw(CoinflipText.kolor("&#E8A0FFꔣ &#FFFFFF" + gra.hostName))
                .loreRaw(lore)
                .hideFlags()
                .amount(Math.min(64, Math.max(1, gra.stawka)))
                .build();
    }

    public void otworzLosowanie(Player p, CoinflipManager.Gra gra, UUID goscId) {
        LosowanieHolder holder = new LosowanieHolder(gra.id);
        Inventory inv = Bukkit.createInventory(holder, SIZE, manager.tytul("gui.tytul-losowanie", "&#FF4FD8ꔣ ʟᴏꜱᴏᴡᴀɴɪᴇ"));
        holder.inv = inv;
        ramka(inv);

        inv.setItem(SLOT_HOST, glowaStrona(gra.host, gra.hostName, true, gra.stawka));
        inv.setItem(SLOT_GOSC, glowaStrona(goscId, nick(goscId), false, gra.stawka));
        inv.setItem(SLOT_MONETA, glowaStrona(gra.host, gra.hostName, true, gra.stawka));
        p.openInventory(inv);
        startAnimacja(p, inv, gra.host, goscId);
    }

    private void startAnimacja(Player p, Inventory inv, UUID a, UUID b) {
        UUID pid = p.getUniqueId();
        BukkitTask stary = animacje.remove(pid);
        if (stary != null) stary.cancel();
        final int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) {
                BukkitTask t = animacje.remove(pid);
                if (t != null) t.cancel();
                return;
            }
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof LosowanieHolder)) {
                BukkitTask t = animacje.remove(pid);
                if (t != null) t.cancel();
                return;
            }
            tick[0]++;
            boolean host = (tick[0] % 2) == 0;
            UUID kto = host ? a : b;
            inv.setItem(SLOT_MONETA, glowaStrona(kto, nick(kto), host, 1));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, host ? 1.6f : 1.1f);
            if (tick[0] >= 24) {
                BukkitTask t = animacje.remove(pid);
                if (t != null) t.cancel();
            }
        }, 2L, 2L);
        animacje.put(pid, task);
    }

    private ItemStack glowaStrona(UUID uuid, String nick, boolean host, int stawka) {
        String kolor = host ? "&#C77DFF" : "&#FF4FD8";
        List<String> lore = new ArrayList<>();
        lore.add(CoinflipText.kolor(""));
        lore.add(CoinflipText.kolor(kolor + "丮 &#FFFFFFꜱᴛᴀᴡᴋᴀ: &#FFFFFF" + stawka));
        lore.add(CoinflipText.kolor("&#E8A0FF亖 &#FFFFFFʟᴏꜱᴏᴡᴀɴɪᴇ..."));
        return ItemBuilder.glowaGracza(Bukkit.getOfflinePlayer(uuid))
                .nameRaw(CoinflipText.kolor(kolor + "ꔣ &#FFFFFF" + nick))
                .loreRaw(lore)
                .hideFlags()
                .glow()
                .build();
    }

    private static String nick(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n == null ? "?" : n;
    }

    private void ramka(Inventory inv) {
        polozSzklo(inv, "gui.szklo.fioletowe", Material.PURPLE_STAINED_GLASS_PANE);
        polozSzklo(inv, "gui.szklo.magenta", Material.MAGENTA_STAINED_GLASS_PANE);
        polozSzklo(inv, "gui.szklo.biale", Material.WHITE_STAINED_GLASS_PANE);
        if (!manager.cfg().getBoolean("gui.szklo.puste-gry.wlaczone", true)) return;
        Material puste = material(manager.cfg().getString("gui.szklo.puste-gry.material"), Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot : SLOT_GIER) {
            if (inv.getItem(slot) != null) continue;
            inv.setItem(slot, szyba(puste, "&8"));
        }
    }

    private void polozSzklo(Inventory inv, String sciezka, Material domyslny) {
        Material mat = material(manager.cfg().getString(sciezka + ".material"), domyslny);
        List<Integer> sloty = manager.cfg().getIntegerList(sciezka + ".sloty");
        if (sloty.isEmpty()) {
            if (sciezka.endsWith("fioletowe")) sloty = List.of(0, 3, 6, 9, 18, 27, 36, 39, 42);
            else if (sciezka.endsWith("magenta")) sloty = List.of(1, 4, 7, 37, 40, 43);
            else if (sciezka.endsWith("biale")) sloty = List.of(2, 5, 8, 17, 26, 35, 38, 41, 44);
        }
        for (int slot : sloty) {
            if (slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, szyba(mat, "&#C77DFF"));
        }
    }

    private static ItemStack szyba(Material mat, String nazwa) {
        return new ItemBuilder(mat)
                .nameRaw(CoinflipText.kolor(nazwa))
                .hideFlags()
                .build();
    }

    private static Material material(String nazwa, Material domyslny) {
        if (nazwa == null || nazwa.isBlank()) return domyslny;
        Material m = Material.matchMaterial(nazwa);
        return m == null || m.isAir() ? domyslny : m;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof ListaHolder) && !(holder instanceof LosowanieHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (holder instanceof LosowanieHolder) return;

        int slot = e.getRawSlot();
        if (slot == SLOT_INFO) {
            manager.odbierz(p);
            otworzListe(p);
            return;
        }
        int idx = indeksGry(slot);
        if (idx < 0) return;
        List<CoinflipManager.Gra> gry = manager.listaGier();
        if (idx >= gry.size()) return;
        CoinflipManager.Gra gra = gry.get(idx);
        if (gra.host.equals(p.getUniqueId())) {
            manager.anuluj(p, gra);
            otworzListe(p);
            return;
        }
        manager.dolacz(p, gra, this);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof ListaHolder || holder instanceof LosowanieHolder) {
            e.setCancelled(true);
        }
    }

    private static int indeksGry(int slot) {
        for (int i = 0; i < SLOT_GIER.length; i++) {
            if (SLOT_GIER[i] == slot) return i;
        }
        return -1;
    }
}
