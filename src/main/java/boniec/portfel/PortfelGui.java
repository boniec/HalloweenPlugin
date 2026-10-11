package boniec.portfel;

import boniec.dev.polezamek.util.ItemTagUtil;
import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PortfelGui implements Listener {

    private final PortfelManager manager;

    public PortfelGui(PortfelManager manager) {
        this.manager = manager;
    }

    private static final class Holder implements InventoryHolder {
        enum Typ { WALLET, CONFIRM, HISTORY }
        final Typ typ;
        String kategoria = "main";
        String produktId;
        UUID historiaGracza;
        int strona;
        final Map<Integer, String> akcje = new HashMap<>();
        final Map<Integer, String> produktySlot = new HashMap<>();
        Inventory inv;

        Holder(Typ typ) { this.typ = typ; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    public void otworzPortfel(Player gracz) {
        otworzPortfel(gracz, "main");
    }

    public void otworzPortfel(Player gracz, String kategoria) {
        var cfg = manager.getGuiWallet();
        int size = cfg.getInt("size", 45);
        String tytul = cfg.getString("title", "&8Portfel");
        Holder holder = new Holder(Holder.Typ.WALLET);
        holder.kategoria = kategoria == null ? "main" : kategoria;
        Inventory inv = Bukkit.createInventory(holder, size, Msg.color(podstaw(gracz, tytul)));
        holder.inv = inv;

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= size) continue;
                String action = s.getString("action", "NULL");
                ItemStack item = zbudujItem(gracz, s, null);
                if (item.getType() == Material.AIR && "NULL".equalsIgnoreCase(action)) continue;
                inv.setItem(slot, item);
                if (!"NULL".equalsIgnoreCase(action)) holder.akcje.put(slot, action);
            }
        }

        for (PortfelManager.Produkt p : manager.getProdukty().values()) {
            if (!holder.kategoria.equalsIgnoreCase(p.kategoria)) continue;
            if (p.slot < 0 || p.slot >= size) continue;
            if (inv.getItem(p.slot) != null && inv.getItem(p.slot).getType() != Material.AIR) continue;
            inv.setItem(p.slot, zbudujProdukt(gracz, p));
            holder.produktySlot.put(p.slot, p.id);
        }

        gracz.openInventory(inv);
    }

    public void otworzPotwierdzenie(Player gracz, PortfelManager.Produkt produkt) {
        var cfg = manager.getGuiConfirm();
        int size = cfg.getInt("size", 9);
        Holder holder = new Holder(Holder.Typ.CONFIRM);
        holder.produktId = produkt.id;
        Inventory inv = Bukkit.createInventory(holder, size, Msg.color(podstawProdukt(cfg.getString("title", "&cPotwierdz"), produkt)));
        holder.inv = inv;

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= size) continue;
                inv.setItem(slot, zbudujItem(gracz, s, produkt));
                String action = s.getString("action", "NULL");
                if (!"NULL".equalsIgnoreCase(action)) holder.akcje.put(slot, action);
            }
        }
        gracz.openInventory(inv);
    }

    public void otworzHistorie(Player viewer, UUID target, int strona) {
        var cfg = manager.getGuiHistory();
        List<Map<String, Object>> tx = manager.getHistoria(target);
        List<Integer> sloty = cfg.getIntegerList("history-slots");
        if (sloty.isEmpty()) {
            sloty = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
        }
        int naStrone = sloty.size();
        int maxStron = Math.max(1, (tx.size() + naStrone - 1) / naStrone);
        strona = Math.max(0, Math.min(strona, maxStron - 1));

        String nick = Bukkit.getOfflinePlayer(target).getName();
        if (nick == null) nick = target.toString();
        String tytul = cfg.getString("title", "&8Historia")
                .replace("{PLAYER}", nick)
                .replace("{PAGE}", String.valueOf(strona + 1))
                .replace("{MAX_PAGE}", String.valueOf(maxStron));

        Holder holder = new Holder(Holder.Typ.HISTORY);
        holder.historiaGracza = target;
        holder.strona = strona;
        Inventory inv = Bukkit.createInventory(holder, cfg.getInt("size", 54), Msg.color(tytul));
        holder.inv = inv;

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0) continue;
                inv.setItem(slot, zbudujItem(viewer, s, null));
                String action = s.getString("action", "");
                if (!action.isEmpty()) holder.akcje.put(slot, action);
            }
        }

        int start = strona * naStrone;
        ConfigurationSection txCfg = cfg.getConfigurationSection("transaction_item");
        for (int i = 0; i < naStrone && start + i < tx.size(); i++) {
            Map<String, Object> row = tx.get(start + i);
            int slot = sloty.get(i);
            inv.setItem(slot, zbudujTransakcje(txCfg, row));
        }

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player gracz)) return;
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof Holder)) return;

        int slot = e.getRawSlot();
        if (holder.typ == Holder.Typ.WALLET) {
            String akcja = holder.akcje.get(slot);
            if (akcja != null) {
                if (akcja.startsWith("CATEGORY:")) {
                    otworzPortfel(gracz, akcja.substring("CATEGORY:".length()));
                    return;
                }
                if ("DAILY".equalsIgnoreCase(akcja)) {
                    long left = manager.getDailyCooldownRemaining(gracz.getUniqueId());
                    if (left > 0) {
                        Msg.send(gracz, manager.msg("daily-reward-cooldown").replace("{TIME}", formatCzas(left)));
                    } else if (!manager.odbierzDaily(gracz)) {
                        Msg.send(gracz, manager.msg("daily-reward-cooldown").replace("{TIME}", "???"));
                    }
                    otworzPortfel(gracz, holder.kategoria);
                    return;
                }
            }
            String prodId = holder.produktySlot.get(slot);
            if (prodId != null) {
                PortfelManager.Produkt p = manager.getProdukt(prodId);
                if (p != null) otworzPotwierdzenie(gracz, p);
            }
        } else if (holder.typ == Holder.Typ.CONFIRM) {
            String akcja = holder.akcje.get(slot);
            if ("CANCEL".equalsIgnoreCase(akcja)) {
                otworzPortfel(gracz);
            } else if ("CONFIRM".equalsIgnoreCase(akcja)) {
                PortfelManager.Produkt p = manager.getProdukt(holder.produktId);
                if (p == null) {
                    gracz.closeInventory();
                    return;
                }
                if (manager.kup(gracz, p)) {
                    Msg.send(gracz, manager.msg("purchase-success")
                            .replace("{PRODUCT}", Msg.color(p.nazwa))
                            .replace("{PRICE}", manager.formatBalance(p.cena)));
                    gracz.closeInventory();
                } else {
                    Msg.send(gracz, manager.msg("insufficient-funds"));
                }
            }
        } else if (holder.typ == Holder.Typ.HISTORY) {
            String akcja = holder.akcje.get(slot);
            if ("NEXT_PAGE".equalsIgnoreCase(akcja)) {
                otworzHistorie(gracz, holder.historiaGracza, holder.strona + 1);
            } else if ("PREVIOUS_PAGE".equalsIgnoreCase(akcja)) {
                if (holder.strona <= 0) {
                    Msg.send(gracz, manager.msg("history-first-page"));
                } else {
                    otworzHistorie(gracz, holder.historiaGracza, holder.strona - 1);
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private ItemStack zbudujProdukt(Player gracz, PortfelManager.Produkt p) {
        ConfigurationSection fake = manager.getGuiWallet().createSection("tmp");
        fake.set("material", p.material);
        fake.set("name", p.nazwa);
        fake.set("lore", p.lore);
        fake.set("custom-model-data", p.customModelData);
        return zbudujItem(gracz, fake, p);
    }

    private ItemStack zbudujItem(Player gracz, ConfigurationSection s, PortfelManager.Produkt produkt) {
        Material mat = Material.matchMaterial(s.getString("material", "PAPER"));
        if (mat == null) mat = Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = s.getString("name", " ");
        name = podstawProdukt(podstaw(gracz, name), produkt);
        meta.setDisplayName(Msg.color(name));

        List<String> lore = new ArrayList<>();
        for (String line : s.getStringList("lore")) {
            lore.add(Msg.color(podstawProdukt(podstaw(gracz, line), produkt)));
        }
        meta.setLore(lore);
        ItemTagUtil.applyModel(meta, s.getInt("custom-model-data", 0));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack zbudujTransakcje(ConfigurationSection s, Map<String, Object> tx) {
        if (s == null) {
            s = manager.getGuiHistory().createSection("tx");
            s.set("material", "BOOK");
            s.set("name", "&b{type} &7- &f{item}");
        }
        Material mat = Material.matchMaterial(s.getString("material", "BOOK"));
        if (mat == null) mat = Material.BOOK;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = s.getString("name", "&bTransakcja");
        name = name.replace("{TRANSACTION_TYPE}", String.valueOf(tx.getOrDefault("type", "?")))
                .replace("{TRANSACTION_ITEM}", String.valueOf(tx.getOrDefault("item", "?")));
        meta.setDisplayName(Msg.color(name));

        List<String> lore = new ArrayList<>();
        long date = tx.get("date") instanceof Number n ? n.longValue() : 0L;
        String dataStr = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("pl-PL")).format(new Date(date));
        for (String line : s.getStringList("lore")) {
            lore.add(Msg.color(line
                    .replace("{TRANSACTION_DATE}", dataStr)
                    .replace("{TRANSACTION_SOURCE}", String.valueOf(tx.getOrDefault("source", "?")))
                    .replace("{TRANSACTION_AMOUNT}", manager.formatBalance(((Number) tx.getOrDefault("amount", 0)).doubleValue()))
                    .replace("{TRANSACTION_SERVER}", String.valueOf(tx.getOrDefault("server", "?")))));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String podstaw(Player gracz, String tekst) {
        if (tekst == null) return "";
        return tekst.replace("{BALANCE}", manager.formatBalance(manager.getBalance(gracz.getUniqueId())))
                .replace("{PLAYER}", gracz.getName());
    }

    private String podstawProdukt(String tekst, PortfelManager.Produkt produkt) {
        if (tekst == null) return "";
        if (produkt == null) {
            return tekst.replace("{PRODUCT_NAME}", "").replace("{PRODUCT_PRICE}", "")
                    .replace("{PRICE}", "");
        }
        return tekst.replace("{PRODUCT_NAME}", Msg.color(produkt.nazwa))
                .replace("{PRODUCT_PRICE}", manager.formatBalance(produkt.cena))
                .replace("{PRICE}", manager.formatBalance(produkt.cena));
    }

    private String formatCzas(long ms) {
        long sec = ms / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
