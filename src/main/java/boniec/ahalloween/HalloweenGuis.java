package boniec.ahalloween;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HalloweenGuis {

    public enum Typ { MENU, BAG, MISSIONS, UPGRADES, SHOP, RANK, STATS }

    public static final class Holder implements InventoryHolder {
        public final Typ typ;
        public Holder(Typ typ) { this.typ = typ; }
        @Override public Inventory getInventory() { return null; }
    }

    private HalloweenGuis() {}

    public static void otworz(AHalloweenManager m, Player p, Typ typ) {
        Inventory inv = switch (typ) {
            case MENU -> menu(m, p);
            case BAG -> worek(m, p);
            case MISSIONS -> misje(m, p);
            case UPGRADES -> ulepszenia(m, p);
            case SHOP -> sklep(m, p);
            case RANK -> ranking(m, p);
            case STATS -> staty(m, p);
        };
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.3f);
    }

    private static Inventory baza(AHalloweenManager m, Typ typ, String tytul, int size) {
        Inventory inv = Bukkit.createInventory(new Holder(typ), size, HalloweenUtil.colorGuiTitle(tytul));
        ramka(inv);
        return inv;
    }

    private static void ramka(Inventory inv) {
        ItemStack fiolet = HalloweenUtil.pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack pomarancz = HalloweenUtil.pane(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack magenta = HalloweenUtil.pane(Material.MAGENTA_STAINED_GLASS_PANE);
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            boolean brzeg = i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8;
            if (!brzeg) continue;
            inv.setItem(i, (i / 9) % 2 == 0 ? (i % 2 == 0 ? pomarancz : fiolet) : magenta);
        }
    }

    private static String liniaBonusUlepszenia(AHalloweenManager m, AHalloweenManager.UpgradeDef u,
                                               HalloweenPlayer d, int lvl) {
        if ("magnes".equals(u.id())) {
            int naLvl = (int) Math.floor(u.bonus());
            int teraz = m.klikniecDoZebrania(d);
            return "&#9B30FF" + teraz + " &7ppm &8(&7-" + naLvl + " &7/ lvl&8)";
        }
        if ("worek".equals(u.id())) {
            return "&#9B30FF+" + (int) u.bonus() + " &7pojemnosc / lvl";
        }
        if ("szczescie".equals(u.id()) || "dynia".equals(u.id())) {
            return "&#9B30FF+" + (int) (u.bonus() * 100) + "% &7/ lvl";
        }
        return "&#9B30FF+" + u.bonus() + " &7/ lvl";
    }

    private static ItemStack ikona(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        return HalloweenUtil.named(it, name, lore);
    }

    private static ItemStack ikona(AHalloweenManager m, String nexoPath, Material mat, String name, List<String> lore) {
        String nexo = m.cfg().getString(nexoPath, "");
        ItemStack it = HalloweenUtil.nexoOrFallback(m.plugin(), nexo, mat);
        return HalloweenUtil.named(it, name, lore);
    }

    private static Inventory menu(AHalloweenManager m, Player p) {
        Inventory inv = baza(m, Typ.MENU, m.cfg().getString("gui.menu-tytul", "Halloween"), 54);
        HalloweenPlayer d = m.dane(p);
        inv.setItem(20, ikona(m, "nexo.glyphy.worek", Material.BUNDLE, "&f價 &7worek", List.of(
                "&8⋮ &7" + d.sumaCukierkow() + "&8/&7" + m.pojemnosc(d) + " &8· &#9B30FF" + m.wartoscCukierkow(d))));
        inv.setItem(22, ikona(m, "nexo.glyphy.misje", Material.WRITABLE_BOOK, "&f亄 &7misje", List.of(
                "&8⋮ &7dzienne / tygodniowe",
                "&8⋮ &#9B30FFukonczone &7" + d.statMisje)));
        inv.setItem(24, ikona(m, "nexo.glyphy.ulepszenia", Material.NETHER_STAR, "&f亞 &7ulepszenia", List.of(
                "&8⋮ &7wydawaj cukierki na bonusy")));
        inv.setItem(29, ikona(m, "nexo.glyphy.sklep", Material.CHEST, "&f點 &7sklep", List.of(
                "&8⋮ &7nagrody za cukierki")));
        inv.setItem(31, ikona(m, "nexo.glyphy.ranking", Material.GOLDEN_HELMET, "&f澀 &7ranking", List.of(
                "&8⋮ &7top gracze eventu")));
        inv.setItem(33, ikona(Material.PLAYER_HEAD, "&f圍 &7staty", List.of(
                "&8⋮ &#FF7518lvl &7" + d.poziom + " &8· &#9B30FFexp &7" + d.exp,
                HalloweenUtil.pasek(d.exp, d.poziom * Math.max(1, m.cfg().getInt("poziomy.exp-na-poziom", 250)), 10),
                "&8⋮ &#FF7518cukierki &7" + d.statCukierki + " &8· &#9B30FFmoby &7" + d.statMoby,
                "&8⋮ &#FF36D1bossy &7" + d.statBossy,
                "&8⋮ &#35105Eczas &7" + (m.aktywny() ? HalloweenUtil.formatCzas(m.pozostaloSekund()) : "off"))));
        inv.setItem(49, ikona(Material.BARRIER, "&f个 &7zamknij", List.of()));
        return inv;
    }

    private static Inventory worek(AHalloweenManager m, Player p) {
        Inventory inv = baza(m, Typ.BAG, m.cfg().getString("gui.worek-tytul", "Worek"), 54);
        HalloweenPlayer d = m.dane(p);
        int slot = 10;
        for (AHalloweenManager.CandyDef c : m.candyDefs()) {
            while (slot % 9 == 0 || slot % 9 == 8 || slot >= 44) slot++;
            if (slot >= 44) break;
            int ile = d.cukierek(c.id());
            ItemStack head = HalloweenUtil.skullFromTexture(c.texture());
            List<String> lore = new ArrayList<>(c.lore());
            lore.add("&8⋮ &7x" + ile + " &8· &#9B30FF" + c.value() + " &8· &#35105E" + c.rarity());
            HalloweenUtil.named(head, c.name(), lore);
            head.setAmount(Math.max(1, Math.min(64, ile)));
            inv.setItem(slot++, head);
        }
        inv.setItem(45, ikona(Material.ARROW, "&f丧 &7wstecz", List.of()));
        inv.setItem(49, ikona(Material.ENDER_CHEST, "&f價 &7podsumowanie", List.of(
                "&8⋮ &7" + d.sumaCukierkow() + "&8/&7" + m.pojemnosc(d),
                "&8⋮ &#9B30FFwartosc &7" + m.wartoscCukierkow(d),
                "&8&m                    ",
                "&#35105Eostatnie:")));
        List<String> hist = m.baza().ostatniaHistoria(d.uuid, 5);
        ItemStack sum = inv.getItem(49);
        if (sum != null && sum.hasItemMeta()) {
            ItemMeta meta = sum.getItemMeta();
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            for (String h : hist) lore.add(HalloweenUtil.color("&8⋮ &7" + h));
            meta.setLore(lore);
            sum.setItemMeta(meta);
        }
        return inv;
    }

    private static Inventory misje(AHalloweenManager m, Player p) {
        Inventory inv = baza(m, Typ.MISSIONS, m.cfg().getString("gui.misje-tytul", "Misje"), 54);
        HalloweenPlayer d = m.dane(p);
        int slot = 10;
        for (AHalloweenManager.MissionDef mis : m.misje()) {
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot >= 44) break;
            int prog = d.misjeProgress.getOrDefault(mis.id(), 0);
            boolean done = prog >= mis.cel();
            boolean claimed = Boolean.TRUE.equals(d.misjeOdebrane.get(mis.id()));
            Material mat = claimed ? Material.LIME_DYE : done ? Material.ORANGE_DYE : Material.GRAY_DYE;
            String status = claimed ? "&#35105Eodebrana" : done ? "&#FF7518odbierz" : "&#9B30FFw toku";
            ItemStack it = ikona(mat, mis.nazwa(), List.of(
                    "&8⋮ &7" + mis.opis(),
                    "&8⋮ &#FF36D1" + mis.typ(),
                    HalloweenUtil.pasek(Math.min(prog, mis.cel()), mis.cel(), 10),
                    "&8⋮ &#FF7518+" + mis.ile() + " " + mis.nagroda() + " &8· &#9B30FF+" + mis.exp() + " exp",
                    status));
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(m.keyObject(), org.bukkit.persistence.PersistentDataType.STRING, mis.id());
                it.setItemMeta(meta);
            }
            inv.setItem(slot++, it);
        }
        inv.setItem(45, ikona(Material.ARROW, "&f丧 &7wstecz", List.of()));
        return inv;
    }

    private static Inventory ulepszenia(AHalloweenManager m, Player p) {
        Inventory inv = baza(m, Typ.UPGRADES, m.cfg().getString("gui.ulepszenia-tytul", "Ulepszenia"), 54);
        HalloweenPlayer d = m.dane(p);
        int[] slots = {11, 13, 15, 29, 31, 33};
        int i = 0;
        for (AHalloweenManager.UpgradeDef u : m.ulepszenia()) {
            if (i >= slots.length) break;
            int lvl = d.ulepszenie(u.id());
            boolean max = lvl >= u.max();
            int cena = max ? 0 : (lvl < u.koszty().size() ? u.koszty().get(lvl) : u.koszty().get(u.koszty().size() - 1));
            String nazwa = u.nazwa();
            String opis = u.opis();
            if ("magnes".equals(u.id())) {
                if (nazwa.toLowerCase().contains("magnes")) {
                    nazwa = "&#FF7518szybkosc zbierania";
                }
                String o = opis.toLowerCase();
                if (o.contains("zasieg") || o.contains("wiekszy zasieg") || o.contains("magnes")) {
                    opis = "Mniej klikniec ppm na jeden cukierek.";
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add("&8⋮ &7" + opis);
            lore.add("&8⋮ &#FF7518lvl &7" + lvl + "&8/&7" + u.max() + " &8· " + liniaBonusUlepszenia(m, u, d, lvl));
            if (!max) {
                lore.add("&8⋮ &#FF36D1koszt &7" + cena);
                lore.add(m.wartoscCukierkow(d) >= cena ? "&#FF7518kliknij" : "&#35105Eza malo");
            } else lore.add("&#35105Emax");
            ItemStack it = ikona(max ? Material.GLOWSTONE : Material.AMETHYST_SHARD, nazwa, lore);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(m.keyObject(), org.bukkit.persistence.PersistentDataType.STRING, u.id());
                it.setItemMeta(meta);
            }
            inv.setItem(slots[i++], it);
        }
        inv.setItem(45, ikona(Material.ARROW, "&f丧 &7wstecz", List.of()));
        inv.setItem(49, ikona(Material.SUNFLOWER, "&f丮 &7portfel", List.of(
                "&8⋮ &#FF7518" + m.wartoscCukierkow(d))));
        return inv;
    }

    private static Inventory sklep(AHalloweenManager m, Player p) {
        Inventory inv = baza(m, Typ.SHOP, m.cfg().getString("gui.sklep-tytul", "&8「&f點&8」 &#35105Esklep"), 54);
        HalloweenPlayer d = m.dane(p);
        int autoSlot = 10;
        for (AHalloweenManager.ShopDef s : m.sklep()) {
            int slot = s.slot();
            if (slot < 0 || slot >= 44 || slot % 9 == 0 || slot % 9 == 8) {
                while (autoSlot % 9 == 0 || autoSlot % 9 == 8) autoSlot++;
                if (autoSlot >= 44) break;
                slot = autoSlot++;
            }
            int bought = d.sklep.getOrDefault(s.id(), 0);
            List<String> lore = new ArrayList<>(s.display().lore());
            lore.add("&8&m                    ");
            lore.add("&8⋮ &#FF7518cena &7" + s.cena() + " &8· &7wartosc worka");
            if (!s.commands().isEmpty() && !s.dajPrzedmiot() && s.daj().isEmpty()) {
                lore.add("&8⋮ &#35105Eodbior: &7komenda (np. klucz)");
            } else if (!s.daj().isEmpty() || s.dajPrzedmiot()) {
                lore.add("&8⋮ &#35105Eodbior: &7item do eq");
            }
            if (s.crate() || !s.grant().isEmpty()) {
                lore.add("&8⋮ &#35105Eodbior: &7cukierki do worka");
            }
            lore.add("&8⋮ &#9B30FFlimit &7" + bought + (s.limit() > 0 ? "&8/&7" + s.limit() : "&8/&7∞"));
            lore.add(m.wartoscCukierkow(d) >= s.cena() && (s.limit() <= 0 || bought < s.limit())
                    ? "&#FF7518kliknij" : "&#35105Ebrak funduszy");
            ItemStack it = HalloweenShopItems.build(m.plugin(), s.display(), true);
            HalloweenUtil.named(it, s.display().nazwa(), lore);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(m.keyObject(), org.bukkit.persistence.PersistentDataType.STRING, s.id());
                it.setItemMeta(meta);
            }
            inv.setItem(slot, it);
        }
        inv.setItem(45, ikona(Material.ARROW, "&f丧 &7wstecz", List.of()));
        return inv;
    }

    private static Inventory ranking(AHalloweenManager m, Player p) {
        Inventory inv = baza(m, Typ.RANK, m.cfg().getString("gui.ranking-tytul", "Ranking"), 54);
        wstawTop(inv, 10, Material.COOKIE, "&f丮 &7cukierki", m.baza().top("candies", 5));
        wstawTop(inv, 12, Material.BONE, "&#9B30FF亐 &7moby", m.baza().top("mobs", 5));
        wstawTop(inv, 14, Material.BOOK, "&f亄 &7misje", m.baza().top("missions", 5));
        wstawTop(inv, 16, Material.NETHER_STAR, "&f圍 &7poziom", m.baza().topPoziom(5));
        inv.setItem(45, ikona(Material.ARROW, "&f丧 &7wstecz", List.of()));
        return inv;
    }

    private static void wstawTop(Inventory inv, int slot, Material mat, String tytul, List<Map.Entry<String, Integer>> top) {
        List<String> lore = new ArrayList<>();
        int i = 1;
        for (Map.Entry<String, Integer> e : top) {
            lore.add("&8⋮ &#FF7518#" + i++ + " &7" + e.getKey() + " &8· &#9B30FF" + e.getValue());
        }
        if (top.isEmpty()) lore.add("&8⋮ &#35105Ebrak danych");
        inv.setItem(slot, ikona(mat, tytul, lore));
        inv.setItem(slot + 9, ikona(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of()));
    }

    private static Inventory staty(AHalloweenManager m, Player p) {
        Inventory inv = baza(m, Typ.STATS, m.cfg().getString("gui.stats-tytul", "&8「&#9B30FF圍&8」 &#35105Estaty"), 27);
        HalloweenPlayer d = m.dane(p);
        inv.setItem(13, ikona(Material.JACK_O_LANTERN, "&fꔰ &7" + p.getName(), List.of(
                "&8⋮ &#FF7518lvl &7" + d.poziom + " &8· &#9B30FFexp &7" + d.exp,
                "&8⋮ &#FF7518cukierki &7" + d.statCukierki,
                "&8⋮ &#9B30FFmoby &7" + d.statMoby + " &8· &#FF36D1misje &7" + d.statMisje,
                "&8⋮ &#FFD700bossy &7" + d.statBossy,
                "&8⋮ &#35105Eworek &7" + d.sumaCukierkow() + "&8/&7" + m.pojemnosc(d))));
        inv.setItem(22, ikona(Material.ARROW, "&f丧 &7wstecz", List.of()));
        return inv;
    }

    public static void klik(AHalloweenManager m, InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR) return;
        int slot = e.getRawSlot();
        if (slot >= e.getInventory().getSize()) return;
        if (it.getType() == Material.BARRIER) {
            p.closeInventory();
            return;
        }
        if (it.getType() == Material.ARROW) {
            otworz(m, p, Typ.MENU);
            return;
        }
        switch (h.typ) {
            case MENU -> {
                if (slot == 20) otworz(m, p, Typ.BAG);
                else if (slot == 22) otworz(m, p, Typ.MISSIONS);
                else if (slot == 24) otworz(m, p, Typ.UPGRADES);
                else if (slot == 29) otworz(m, p, Typ.SHOP);
                else if (slot == 31) otworz(m, p, Typ.RANK);
                else if (slot == 33) otworz(m, p, Typ.STATS);
            }
            case MISSIONS -> {
                String id = HalloweenUtil.tag(it.getItemMeta().getPersistentDataContainer(), m.keyObject());
                if (id != null) {
                    m.odbierzMisje(p, id);
                    otworz(m, p, Typ.MISSIONS);
                }
            }
            case UPGRADES -> {
                String id = HalloweenUtil.tag(it.getItemMeta().getPersistentDataContainer(), m.keyObject());
                if (id != null) {
                    m.kupUlepszenie(p, id);
                    otworz(m, p, Typ.UPGRADES);
                }
            }
            case SHOP -> {
                String id = HalloweenUtil.tag(it.getItemMeta().getPersistentDataContainer(), m.keyObject());
                if (id != null) {
                    m.kup(p, id);
                    otworz(m, p, Typ.SHOP);
                }
            }
            default -> {
            }
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}
