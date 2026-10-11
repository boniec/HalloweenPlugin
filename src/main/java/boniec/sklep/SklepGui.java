package boniec.sklep;

import boniec.dev.polezamek.util.ItemTagUtil;
import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SklepGui implements Listener {

    private static final int[] DEFAULT_PRODUCT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int MAX_ILOSC = 64 * 9;

    private final SklepManager manager;

    public SklepGui(SklepManager manager) {
        this.manager = manager;
    }

    public void otworzGlowny(Player gracz) {
        otworzZYaml(gracz, "main-shop.yml", "mainShop", Typ.MAIN, null);
        gracz.sendMessage(manager.msg("shopOpened"));
    }

    private void otworzZYaml(Player gracz, String plik, String rootKey, Typ typ, Holder poprzedni) {
        YamlConfiguration yaml = manager.gui(plik);
        ConfigurationSection root = manager.sekcjaGui(yaml, rootKey);
        if (root == null) {
            gracz.sendMessage(Msg.color("&c[Sklep] Brak sekcji " + rootKey + " w " + plik));
            return;
        }
        int rows = root.getInt("rows", 4);
        String title = manager.podstaw(gracz, root.getString("title", "&8Sklep"));
        Holder holder = new Holder(typ, plik, rootKey);
        holder.poprzedni = poprzedni;
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Msg.color(title));
        holder.inv = inv;
        uzupelnijPrzyciski(gracz, inv, holder, root);
        if (typ == Typ.BUY) wstawProduktyKupna(gracz, inv, holder, root);
        if (typ == Typ.TIME) wstawProduktyCzas(gracz, inv, holder, root);
        gracz.openInventory(inv);
    }

    private void uzupelnijPrzyciski(Player gracz, Inventory inv, Holder holder, ConfigurationSection root) {
        ConfigurationSection items = root.getConfigurationSection("items");
        if (items == null) return;
        boolean stackable = holder.szablon == null || holder.szablon.getMaxStackSize() > 1;
        for (String key : items.getKeys(false)) {
            ConfigurationSection btn = items.getConfigurationSection(key);
            if (btn == null || !btn.getBoolean("enabled", true)) continue;
            String action = btn.getString("action", "NONE");
            if (holder.szablon != null) {
                String act = action.toUpperCase(Locale.ROOT);
                String k = key.toLowerCase(Locale.ROOT);
                if (stackable && (k.startsWith("ns_") || act.startsWith("NS_"))) continue;
                if (!stackable && (act.equals("ADD_10") || act.equals("REMOVE_10") || act.equals("SET_64"))) continue;
            }
            ItemStack stack = zbudujPrzycisk(gracz, btn, holder);
            int slot = btn.getInt("slot", -1);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, stack);
                if (!"NONE".equalsIgnoreCase(action)) holder.akcje.put(slot, action);
            }
            for (int s : btn.getIntegerList("slots")) {
                if (s >= 0 && s < inv.getSize()) {
                    inv.setItem(s, stack.clone());
                    if (!"NONE".equalsIgnoreCase(action)) holder.akcje.put(s, action);
                }
            }
        }
    }

    private ItemStack zbudujPrzycisk(Player gracz, ConfigurationSection btn, Holder holder) {
        Material mat = Material.matchMaterial(btn.getString("material", "PAPER"));
        if (mat == null) mat = Material.PAPER;
        ItemStack item = new ItemStack(mat, Math.max(1, btn.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Msg.color(manager.podstaw(gracz, btn.getString("name", " "))));
        List<String> lore = new ArrayList<>();
        for (String line : btn.getStringList("lore")) {
            lore.add(Msg.color(manager.podstaw(gracz, podstawSesja(line, holder))));
        }
        meta.setLore(lore);
        ItemTagUtil.applyModel(meta, btn.getInt("customModelData", 0));
        item.setItemMeta(meta);
        return item;
    }

    private String podstawSesja(String line, Holder h) {
        if (line == null) return "";
        double cena = h.cenaJednostkowa * h.ilosc;
        return line
                .replace("{ilosc}", String.valueOf(h.ilosc))
                .replace("{cena}", manager.formatMoney(cena))
                .replace("{czas}", manager.formatTime(h.kosztCzasSekund));
    }

    private void wstawProduktyKupna(Player gracz, Inventory inv, Holder holder, ConfigurationSection root) {
        List<String> loreAppend = root.getStringList("shopItemLoreAppend");
        Map<Integer, SklepDatabase.ShopEntry> items = manager.database().loadBuyItems();
        for (SklepDatabase.ShopEntry e : items.values()) {
            if (e.slot() < 0 || e.slot() >= inv.getSize()) continue;
            ItemStack display = e.item().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                for (String line : loreAppend) {
                    lore.add(Msg.color(line.replace("{cena}", manager.formatMoney(e.price()))));
                }
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(e.slot(), display);
            holder.produkty.put(e.slot(), e.slot());
        }
    }

    private void wstawProduktyCzas(Player gracz, Inventory inv, Holder holder, ConfigurationSection root) {
        List<String> loreAppend = root.getStringList("shopItemLoreAppend");
        Map<Integer, SklepDatabase.TimeEntry> items = manager.database().loadTimeItems();
        for (SklepDatabase.TimeEntry e : items.values()) {
            if (e.slot() < 0 || e.slot() >= inv.getSize()) continue;
            ItemStack display = e.item().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                for (String line : loreAppend) {
                    lore.add(Msg.color(line.replace("{czas}", manager.formatTime(e.timeSeconds()))));
                }
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(e.slot(), display);
            holder.produkty.put(e.slot(), e.slot());
        }
    }

    private void otworzPotwierdzenie(Player gracz, Holder zrodlo, int dbSlot, boolean czas) {
        otworzPotwierdzenie(gracz, zrodlo, dbSlot, czas, 1);
    }

    private void otworzPotwierdzenie(Player gracz, Holder zrodlo, int dbSlot, boolean czas, int ilosc) {
        String plik = czas ? "time-shop-buy.yml" : "buy-confirm.yml";
        String rootKey = czas ? "timeShopBuy" : "buyConfirm";
        YamlConfiguration yaml = manager.gui(plik);
        ConfigurationSection root = manager.sekcjaGui(yaml, rootKey);
        if (root == null) return;
        Holder holder = new Holder(czas ? Typ.CONFIRM_TIME : Typ.CONFIRM_MONEY, plik, rootKey);
        holder.poprzedni = zrodlo;
        holder.dbSlot = dbSlot;
        holder.ilosc = Math.max(1, ilosc);
        holder.czasowy = czas;
        if (czas) {
            SklepDatabase.TimeEntry e = manager.database().loadTimeItems().get(dbSlot);
            if (e == null) return;
            holder.szablon = e.item().clone();
            holder.kosztCzasSekund = e.timeSeconds();
        } else {
            SklepDatabase.ShopEntry e = manager.database().loadBuyItems().get(dbSlot);
            if (e == null) return;
            holder.szablon = e.item().clone();
            holder.cenaJednostkowa = e.price();
        }
        int rows = root.getInt("rows", 5);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Msg.color(root.getString("title", "&8Potwierdz")));
        holder.inv = inv;
        uzupelnijPrzyciski(gracz, inv, holder, root);
        int previewSlot = root.getInt("previewSlot", 22);
        odswiezPodglad(inv, holder, previewSlot, root.getStringList("previewLoreAppend"));
        gracz.openInventory(inv);
    }

    private void odswiezPodglad(Inventory inv, Holder holder, int slot, List<String> append) {
        if (holder.szablon == null) return;
        ItemStack preview = holder.szablon.clone();
        preview.setAmount(Math.min(64, holder.ilosc));
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            double cena = holder.cenaJednostkowa * holder.ilosc;
            for (String line : append) {
                lore.add(Msg.color(podstawSesja(line, holder)));
            }
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, preview);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player gracz)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory().getHolder() != holder) return;

        int slot = e.getRawSlot();
        ItemStack clicked = e.getCurrentItem();

        String akcja = holder.akcje.get(slot);
        if (akcja != null) {
            obsluzAkcje(gracz, holder, akcja);
            return;
        }

        if (holder.typ == Typ.BUY && holder.produkty.containsKey(slot)) {
            otworzPotwierdzenie(gracz, holder, holder.produkty.get(slot), false);
            return;
        }
        if (holder.typ == Typ.TIME && holder.produkty.containsKey(slot)) {
            otworzPotwierdzenie(gracz, holder, holder.produkty.get(slot), true);
        }
    }

    private void obsluzAkcje(Player gracz, Holder holder, String akcja) {
        switch (akcja.toUpperCase(Locale.ROOT)) {
            case "BUY" -> {
                if (holder.typ == Typ.MAIN) {
                    otworzZYaml(gracz, "virtual-shop-main.yml", "virtualShopMain", Typ.VIRTUAL_MAIN, holder);
                } else {
                    otworzZYaml(gracz, "virtual-shop-buy.yml", "virtualShopBuy", Typ.BUY, holder);
                }
            }
            case "SELL" -> gracz.sendMessage(Msg.color("&cSprzedaz jest wylaczona w configu."));
            case "TIME_SHOP" -> otworzZYaml(gracz, "time-shop.yml", "timeShop", Typ.TIME, holder);
            case "BACK" -> {
                if (holder.typ == Typ.MORE) {
                    otworzPotwierdzenie(gracz, holder.poprzedni != null ? holder.poprzedni.poprzedni : null,
                            holder.dbSlot, holder.czasowy, holder.ilosc);
                    return;
                }
                if (holder.typ == Typ.CONFIRM_MONEY || holder.typ == Typ.CONFIRM_TIME) {
                    if (holder.poprzedni != null && holder.poprzedni.typ == Typ.TIME) {
                        otworzZYaml(gracz, "time-shop.yml", "timeShop", Typ.TIME, holder.poprzedni.poprzedni);
                    } else {
                        otworzZYaml(gracz, "virtual-shop-buy.yml", "virtualShopBuy", Typ.BUY, holder.poprzedni != null ? holder.poprzedni.poprzedni : null);
                    }
                } else if (holder.typ == Typ.BUY || holder.typ == Typ.TIME) {
                    otworzZYaml(gracz, "virtual-shop-main.yml", "virtualShopMain", Typ.VIRTUAL_MAIN, null);
                } else if (holder.typ == Typ.VIRTUAL_MAIN) {
                    otworzGlowny(gracz);
                } else {
                    otworzGlowny(gracz);
                }
            }
            case "CLOSE" -> gracz.closeInventory();
            case "CONFIRM" -> zatwierdzZakup(gracz, holder);
            case "ADD_1", "NS_ADD_1" -> zmienIlosc(holder, 1);
            case "REMOVE_1", "NS_REMOVE_1" -> zmienIlosc(holder, -1);
            case "ADD_10" -> zmienIlosc(holder, 10);
            case "REMOVE_10" -> zmienIlosc(holder, -10);
            case "SET_1" -> ustawIlosc(holder, 1);
            case "SET_64" -> ustawIlosc(holder, 64);
            case "ADD_STACK" -> zmienIlosc(holder, rozmiarStosu(holder));
            case "REMOVE_STACK" -> zmienIlosc(holder, -rozmiarStosu(holder));
            case "SET_1_STACK" -> ustawIlosc(holder, rozmiarStosu(holder));
            case "SET_9_STACKS" -> ustawIlosc(holder, rozmiarStosu(holder) * 9);
            case "BUY_MORE" -> {
                otworzWiecej(gracz, holder);
                return;
            }
            case "PREVIEW", "WALLET", "NONE" -> {
                return;
            }
            case "TAB_BUY" -> otworzZYaml(gracz, "virtual-shop-buy.yml", "virtualShopBuy", Typ.BUY, holder);
            case "TAB_SELL" -> gracz.sendMessage(Msg.color("&cSprzedaz jest wylaczona."));
            default -> {
                if (akcja.startsWith("COMMAND:")) {
                    gracz.closeInventory();
                    gracz.performCommand(akcja.substring("COMMAND:".length()).trim());
                }
            }
        }
        if (holder.typ == Typ.CONFIRM_MONEY || holder.typ == Typ.CONFIRM_TIME || holder.typ == Typ.MORE) {
            odswiezConfirm(gracz, holder);
        }
    }

    private void odswiezConfirm(Player gracz, Holder holder) {
        YamlConfiguration yaml = manager.gui(holder.plik);
        ConfigurationSection root = manager.sekcjaGui(yaml, holder.rootKey);
        if (root == null || holder.inv == null) return;
        holder.akcje.clear();
        uzupelnijPrzyciski(gracz, holder.inv, holder, root);
        odswiezPodglad(holder.inv, holder, root.getInt("previewSlot", 22), root.getStringList("previewLoreAppend"));
        gracz.updateInventory();
    }

    private int rozmiarStosu(Holder holder) {
        return holder.szablon != null ? Math.max(1, holder.szablon.getMaxStackSize()) : 64;
    }

    private void zmienIlosc(Holder holder, int delta) {
        ustawIlosc(holder, holder.ilosc + delta);
    }

    private void ustawIlosc(Holder holder, int ilosc) {
        holder.ilosc = Math.max(1, Math.min(MAX_ILOSC, ilosc));
    }

    private void otworzWiecej(Player gracz, Holder zrodlo) {
        YamlConfiguration yaml = manager.gui("buy-more.yml");
        ConfigurationSection root = manager.sekcjaGui(yaml, "buyMore");
        if (root == null) return;
        Holder holder = new Holder(Typ.MORE, "buy-more.yml", "buyMore");
        holder.poprzedni = zrodlo;
        holder.dbSlot = zrodlo.dbSlot;
        holder.ilosc = zrodlo.ilosc;
        holder.szablon = zrodlo.szablon;
        holder.cenaJednostkowa = zrodlo.cenaJednostkowa;
        holder.kosztCzasSekund = zrodlo.kosztCzasSekund;
        holder.czasowy = zrodlo.typ == Typ.CONFIRM_TIME || zrodlo.czasowy;
        int rows = root.getInt("rows", 5);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Msg.color(root.getString("title", "&8Wiecej")));
        holder.inv = inv;
        uzupelnijPrzyciski(gracz, inv, holder, root);
        odswiezPodglad(inv, holder, root.getInt("previewSlot", 22), root.getStringList("previewLoreAppend"));
        gracz.openInventory(inv);
    }

    private void zatwierdzZakup(Player gracz, Holder holder) {
        if (holder.szablon == null) return;
        boolean czas = holder.typ == Typ.CONFIRM_TIME || holder.czasowy;

        if (czas) {
            long koszt = holder.kosztCzasSekund * (long) holder.ilosc;
            if (!manager.database().takePlaytimeSeconds(gracz.getUniqueId(), koszt)) {
                gracz.sendMessage(manager.msg("insufficientTime").replace("{czas}", manager.formatTime(koszt)));
                gracz.playSound(gracz.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            dajItem(gracz, holder.szablon, holder.ilosc);
            gracz.sendMessage(manager.msg("timePurchaseSuccess")
                    .replace("{ilosc}", String.valueOf(holder.ilosc))
                    .replace("{item}", nazwa(holder.szablon))
                    .replace("{czas}", manager.formatTime(koszt)));
        } else {
            double koszt = holder.cenaJednostkowa * holder.ilosc;
            if (!manager.charge(gracz, koszt)) {
                gracz.sendMessage(manager.msg("insufficientFunds").replace("{cena}", manager.formatMoney(koszt)));
                gracz.playSound(gracz.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            dajItem(gracz, holder.szablon, holder.ilosc);
            gracz.sendMessage(manager.msg("purchaseSuccess")
                    .replace("{ilosc}", String.valueOf(holder.ilosc))
                    .replace("{item}", nazwa(holder.szablon))
                    .replace("{cena}", manager.formatMoney(koszt)));
        }
        gracz.playSound(gracz.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        odswiezConfirm(gracz, holder);
    }

    private String nazwa(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    private void dajItem(Player gracz, ItemStack szablon, int ilosc) {
        int max = Math.max(1, szablon.getMaxStackSize());
        int left = Math.max(1, ilosc);
        while (left > 0) {
            ItemStack stack = szablon.clone();
            int n = Math.min(max, left);
            stack.setAmount(n);
            Map<Integer, ItemStack> leftover = gracz.getInventory().addItem(stack);
            for (ItemStack extra : leftover.values()) {
                gracz.getWorld().dropItemNaturally(gracz.getLocation(), extra);
            }
            left -= n;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    enum Typ { MAIN, VIRTUAL_MAIN, BUY, TIME, CONFIRM_MONEY, CONFIRM_TIME, MORE }

    static final class Holder implements InventoryHolder {
        final Typ typ;
        final String plik;
        final String rootKey;
        Inventory inv;
        Holder poprzedni;
        final Map<Integer, String> akcje = new HashMap<>();
        final Map<Integer, Integer> produkty = new HashMap<>();
        int dbSlot;
        int ilosc = 1;
        double cenaJednostkowa;
        long kosztCzasSekund;
        boolean czasowy;
        ItemStack szablon;

        Holder(Typ typ, String plik, String rootKey) {
            this.typ = typ;
            this.plik = plik;
            this.rootKey = rootKey;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
