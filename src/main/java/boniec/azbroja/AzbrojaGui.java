package boniec.azbroja;

import boniec.szaman.util.Msg;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AzbrojaGui implements Listener {

    private static final int[] DOMYSLNE_SLOTY_KOLOROW = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final AzbrojaManager manager;

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Integer> slotIndeksPalety = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    public AzbrojaGui(AzbrojaManager manager) {
        this.manager = manager;
    }

    public void otworz(Player gracz) {
        AzbrojaManager.Sesja s = manager.getSesja(gracz.getUniqueId());
        if (s == null) return;

        var cfg = manager.getCfg();
        int maxStron = manager.getStronPalety();
        if (s.stronaPalety >= maxStron) s.stronaPalety = maxStron - 1;
        if (s.stronaPalety < 0) s.stronaPalety = 0;

        String tytul = cfg.getString("gui.tytul", "&8ᴋʀᴇᴀᴛᴏʀ &7%tekst%")
                .replace("%tekst%", s.tekst + (s.numer != null ? " " + s.numer : ""))
                .replace("%strona%", String.valueOf(s.stronaPalety + 1))
                .replace("%max%", String.valueOf(maxStron));

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, Msg.color(tytul));
        holder.inv = inv;

        int slotOd = cfg.getInt("gui.slot-wybierz-od", 0);
        int slotDo = cfg.getInt("gui.slot-wybierz-do", 8);
        int slotBarwnik = cfg.getInt("gui.slot-wybierz-barwnik", 7);
        int slotPodglad = cfg.getInt("gui.slot-podglad", 4);
        int slotZastosuj = cfg.getInt("gui.slot-zastosuj", 49);
        int slotKopiuj = cfg.getInt("gui.slot-kopiuj", 53);
        int slotInfoOd = cfg.getInt("gui.slot-kolor-od", 1);
        int slotInfoDo = cfg.getInt("gui.slot-kolor-do", 6);
        int slotInfoBarwnik = cfg.getInt("gui.slot-info-barwnik", 2);
        int slotPoprzednia = cfg.getInt("gui.slot-poprzednia", 45);
        int slotNastepna = cfg.getInt("gui.slot-nastepna", 51);

        inv.setItem(slotOd, przyciskTrybu(AzbrojaManager.TrybKoloru.OD, s));
        inv.setItem(slotDo, przyciskTrybu(AzbrojaManager.TrybKoloru.DO, s));
        inv.setItem(slotBarwnik, przyciskTrybu(AzbrojaManager.TrybKoloru.BARWNIK, s));
        inv.setItem(slotInfoOd, infoKolor("Gradient OD", s.hexOd, Material.LIME_STAINED_GLASS_PANE));
        inv.setItem(slotInfoDo, infoKolor("Gradient DO", s.hexDo, Material.RED_STAINED_GLASS_PANE));
        inv.setItem(slotInfoBarwnik, infoKolor("Barwnik skory", s.hexBarwnik, Material.LEATHER_CHESTPLATE));

        String raw = manager.zbudujRaw(s);
        inv.setItem(slotPodglad, podglad(raw, s));

        inv.setItem(slotZastosuj, akcja(Material.ANVIL, "&a&lᴢᴀꜱᴛᴏꜱᴜᴊ", List.of(
                "", "&8» &7Nazwe + &6★&7numer na caly zestaw",
                "&8» &7Rece, helm, klata, spodnie, buty",
                "&8» &7Barwnik skory na leather", "", "&aKliknij")));

        inv.setItem(slotKopiuj, akcja(Material.WRITABLE_BOOK, "&e&lᴋᴏᴘɪᴜᴊ ᴅᴏ ᴄᴢᴀᴛᴜ", List.of(
                "", "&8» &7Surowa linia do configu", "", "&eKliknij")));

        if (s.stronaPalety > 0) {
            inv.setItem(slotPoprzednia, akcja(Material.ARROW, "&eᴘᴏᴘʀᴢᴇᴅɴɪᴀ sᴛʀᴏɴᴀ", List.of()));
        }
        if (s.stronaPalety < maxStron - 1) {
            inv.setItem(slotNastepna, akcja(Material.ARROW, "&eɴᴀsᴛᴇᴘɴᴀ sᴛʀᴏɴᴀ", List.of()));
        }

        int[] sloty = slotyKolorow(cfg);
        int start = s.stronaPalety * manager.getKoloryNaStrone();
        for (int i = 0; i < sloty.length && i < manager.getKoloryNaStrone(); i++) {
            int idx = start + i;
            if (idx >= manager.getPaleta().size()) break;
            holder.slotIndeksPalety.put(sloty[i], idx);
            inv.setItem(sloty[i], kolorItem(manager.getPaleta().get(idx), s));
        }

        ItemStack szklo = akcja(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 54; slot++) {
            if (inv.getItem(slot) == null) inv.setItem(slot, szklo);
        }

        gracz.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player gracz)) return;
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof Holder)) return;

        AzbrojaManager.Sesja s = manager.getSesja(gracz.getUniqueId());
        if (s == null) {
            gracz.closeInventory();
            return;
        }

        var cfg = manager.getCfg();
        int slot = e.getRawSlot();

        if (slot == cfg.getInt("gui.slot-wybierz-od", 0)) {
            s.tryb = AzbrojaManager.TrybKoloru.OD;
            Msg.send(gracz, manager.msg("wybor-od"));
            otworz(gracz);
            return;
        }
        if (slot == cfg.getInt("gui.slot-wybierz-do", 8)) {
            s.tryb = AzbrojaManager.TrybKoloru.DO;
            Msg.send(gracz, manager.msg("wybor-do"));
            otworz(gracz);
            return;
        }
        if (slot == cfg.getInt("gui.slot-wybierz-barwnik", 7)) {
            s.tryb = AzbrojaManager.TrybKoloru.BARWNIK;
            Msg.send(gracz, manager.msg("wybor-barwnik"));
            otworz(gracz);
            return;
        }
        if (slot == cfg.getInt("gui.slot-poprzednia", 45) && s.stronaPalety > 0) {
            s.stronaPalety--;
            otworz(gracz);
            return;
        }
        if (slot == cfg.getInt("gui.slot-nastepna", 51) && s.stronaPalety < manager.getStronPalety() - 1) {
            s.stronaPalety++;
            otworz(gracz);
            return;
        }
        if (slot == cfg.getInt("gui.slot-zastosuj", 49)) {
            int ile = zastosujNaZestawie(gracz, s);
            if (ile == 0) {
                Msg.send(gracz, manager.msg("brak-itemu"));
                return;
            }
            Msg.send(gracz, manager.msg("zastosowano").replace("{ilosc}", String.valueOf(ile)));
            otworz(gracz);
            return;
        }
        if (slot == cfg.getInt("gui.slot-kopiuj", 53)) {
            wyslijKopiuj(gracz, manager.zbudujRaw(s));
            return;
        }

        Integer idx = holder.slotIndeksPalety.get(slot);
        if (idx != null && idx < manager.getPaleta().size()) {
            String hex = manager.getPaleta().get(idx).hex;
            switch (s.tryb) {
                case OD -> s.hexOd = hex;
                case DO -> s.hexDo = hex;
                case BARWNIK -> s.hexBarwnik = hex;
            }
            String tryb = switch (s.tryb) {
                case OD -> "OD";
                case DO -> "DO";
                case BARWNIK -> "BARWNIK";
            };
            Msg.send(gracz, manager.msg("ustawiono-kolor").replace("{hex}", hex).replace("{tryb}", tryb));
            otworz(gracz);
        }
    }

    /** Nazwa (+ barwnik leather) na rece, zbroi i drugiej rece. */
    private int zastosujNaZestawie(Player gracz, AzbrojaManager.Sesja s) {
        int ile = 0;
        ItemStack main = gracz.getInventory().getItemInMainHand();
        if (zastosujNaItem(main, s)) {
            gracz.getInventory().setItemInMainHand(main);
            ile++;
        }
        ItemStack off = gracz.getInventory().getItemInOffHand();
        if (zastosujNaItem(off, s)) {
            gracz.getInventory().setItemInOffHand(off);
            ile++;
        }
        ItemStack[] armor = gracz.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (!zastosujNaItem(piece, s)) continue;
            armor[i] = piece;
            ile++;
        }
        if (ile > 0) {
            gracz.getInventory().setArmorContents(armor);
            gracz.updateInventory();
        }
        return ile;
    }

    private boolean zastosujNaItem(ItemStack rec, AzbrojaManager.Sesja s) {
        if (rec == null || rec.getType() == Material.AIR) return false;
        String nazwa = Msg.color(manager.zbudujRaw(s));
        rec.editMeta(meta -> {
            meta.setDisplayName(nazwa);
            if (meta instanceof LeatherArmorMeta leather && AzbrojaUtil.isSkorzanaZbroja(rec.getType())) {
                org.bukkit.Color kolor = AzbrojaUtil.bukkitColor(s.hexBarwnik);
                if (kolor != null) leather.setColor(kolor);
            }
        });
        return true;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void wyslijKopiuj(Player gracz, String raw) {
        TextComponent root = new TextComponent(Msg.color("&8» &aKliknij, aby skopiowac nazwe setu"));
        TextComponent copy = new TextComponent(Msg.color("\n&7" + raw));
        copy.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, raw));
        root.addExtra(copy);
        gracz.spigot().sendMessage(root);
        Msg.send(gracz, manager.msg("skopiowano"));
    }

    private ItemStack podglad(String raw, AzbrojaManager.Sesja s) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&8» &7Tekst: &f" + s.tekst);
        if (s.numer != null) lore.add("&8» &7Numer: &6★&6&l" + s.numer);
        lore.add("&8» &7Gradient: &f" + s.hexOd + " &8→ &f" + s.hexDo);
        lore.add("&8» &7Barwnik skory: &f" + s.hexBarwnik);
        lore.add("");
        lore.add("&8» &7Podglad nazwy na itemku");
        ItemStack item = akcja(Material.LEATHER_CHESTPLATE, raw, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(raw));
            if (meta instanceof LeatherArmorMeta leather) {
                org.bukkit.Color kolor = AzbrojaUtil.bukkitColor(s.hexBarwnik);
                if (kolor != null) leather.setColor(kolor);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack kolorItem(AzbrojaManager.KolorWelny k, AzbrojaManager.Sesja s) {
        boolean aktywny = switch (s.tryb) {
            case OD -> k.hex.equalsIgnoreCase(s.hexOd);
            case DO -> k.hex.equalsIgnoreCase(s.hexDo);
            case BARWNIK -> k.hex.equalsIgnoreCase(s.hexBarwnik);
        };
        String tryb = switch (s.tryb) {
            case OD -> "OD";
            case DO -> "DO";
            case BARWNIK -> "barwnik";
        };
        String nazwa = (aktywny ? "&a&l» " : "&f") + k.hex;
        List<String> lore = List.of("", "&8» &7Tryb: &f" + tryb, "&8» &7Kliknij aby ustawic");
        ItemStack item = akcja(k.material, nazwa, lore);
        if (k.material.name().contains("LEATHER") && item.getItemMeta() instanceof LeatherArmorMeta leather) {
            org.bukkit.Color c = AzbrojaUtil.bukkitColor(k.hex);
            if (c != null) leather.setColor(c);
            item.setItemMeta(leather);
        }
        return item;
    }

    private ItemStack infoKolor(String label, String hex, Material mat) {
        ItemStack item = akcja(mat, "&f" + label, List.of("", "&8» &f" + hex));
        if (mat.name().contains("LEATHER") && item.getItemMeta() instanceof LeatherArmorMeta leather) {
            org.bukkit.Color c = AzbrojaUtil.bukkitColor(hex);
            if (c != null) leather.setColor(c);
            item.setItemMeta(leather);
        }
        return item;
    }

    private ItemStack przyciskTrybu(AzbrojaManager.TrybKoloru tryb, AzbrojaManager.Sesja s) {
        boolean aktywny = s.tryb == tryb;
        Material mat = switch (tryb) {
            case OD -> Material.LIME_DYE;
            case DO -> Material.RED_DYE;
            case BARWNIK -> Material.LEATHER;
        };
        String nazwa = switch (tryb) {
            case OD -> (aktywny ? "&a&l» " : "&7") + "ɢʀᴀᴅɪᴇɴᴛ OD";
            case DO -> (aktywny ? "&a&l» " : "&7") + "ɢʀᴀᴅɪᴇɴᴛ DO";
            case BARWNIK -> (aktywny ? "&a&l» " : "&7") + "ʙᴀʀᴡɴɪᴋ sᴋóʀʏ";
        };
        return akcja(mat, nazwa, List.of("", "&8» &7Wybierz, potem kolor", "&8» &7z palety ponizej"));
    }

    private ItemStack akcja(Material mat, String nazwa, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Msg.color(nazwa));
        List<String> l = new ArrayList<>();
        for (String line : lore) l.add(Msg.color(line));
        meta.setLore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
        item.setItemMeta(meta);
        return item;
    }

    private int[] slotyKolorow(org.bukkit.configuration.file.FileConfiguration cfg) {
        List<Integer> lista = cfg.getIntegerList("gui.sloty-palety");
        if (lista.isEmpty()) return DOMYSLNE_SLOTY_KOLOROW;
        return lista.stream().mapToInt(Integer::intValue).toArray();
    }
}
