package boniec.gornik;

import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /gornik - lista blokow z kopanie.yml (sekcja "bloki") razem z cenami. */
public class GornikGui implements Listener {

    private static final int SIZE = 54;
    private static final int[] SLOTY_BLOKOW = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_POPRZEDNIA = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NASTEPNA = 50;

    private static final String SMALL_FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL_TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private static final class Holder implements InventoryHolder {
        private final int strona;
        private Inventory inventory;

        private Holder(int strona) {
            this.strona = strona;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final GornikManager manager;

    public GornikGui(GornikManager manager) {
        this.manager = manager;
    }

    public void otworz(Player player, int strona) {
        List<Map.Entry<String, Double>> bloki = new ArrayList<>(manager.getCenyBlokw().entrySet());
        int stron = Math.max(1, (int) Math.ceil(bloki.size() / (double) SLOTY_BLOKOW.length));
        strona = Math.max(0, Math.min(strona, stron - 1));

        FileConfiguration cfg = manager.getConfig();
        String tytul = cfg.getString("gui.tytul", "&f丮 &#FFC34D&lɢᴏʀɴɪᴋ &8(%strona%/%stron%)")
                .replace("%strona%", String.valueOf(strona + 1))
                .replace("%stron%", String.valueOf(stron));

        Holder holder = new Holder(strona);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Msg.color(tytul));
        holder.inventory = inv;

        ItemStack ramka = item(materialZConfigu(cfg.getString("gui.ramka", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE), " ", List.of());
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, ramka);
        }

        int start = strona * SLOTY_BLOKOW.length;
        for (int i = 0; i < SLOTY_BLOKOW.length; i++) {
            int idx = start + i;
            if (idx >= bloki.size()) {
                inv.setItem(SLOTY_BLOKOW[i], null);
                continue;
            }
            Map.Entry<String, Double> wpis = bloki.get(idx);
            inv.setItem(SLOTY_BLOKOW[i], ikonaBloku(cfg, wpis.getKey(), wpis.getValue()));
        }

        inv.setItem(SLOT_INFO, ikonaInfo(cfg, player));
        if (strona > 0) {
            inv.setItem(SLOT_POPRZEDNIA, item(Material.SPECTRAL_ARROW,
                    cfg.getString("gui.poprzednia", "&f乁 &#FFC34Dᴘᴏᴘʀᴢᴇᴅɴɪᴀ ꜱᴛʀᴏɴᴀ"), List.of()));
        }
        if (strona < stron - 1) {
            inv.setItem(SLOT_NASTEPNA, item(Material.SPECTRAL_ARROW,
                    cfg.getString("gui.nastepna", "&f乁 &#FFC34Dɴᴀꜱᴛᴇᴘɴᴀ ꜱᴛʀᴏɴᴀ"), List.of()));
        }

        player.openInventory(inv);
    }

    private ItemStack ikonaBloku(FileConfiguration cfg, String nazwaMaterialu, double cena) {
        Material material = Material.matchMaterial(nazwaMaterialu);
        Material ikona = (material != null && material.isItem() && !material.isAir()) ? material : Material.PAPER;

        String wyswietlana = cfg.getString("gui.nazwy." + nazwaMaterialu, smallCaps(nazwaMaterialu.replace('_', ' ')));
        String nazwa = cfg.getString("gui.blok-nazwa", "&f丮 &#FFC34D&l%blok%").replace("%blok%", wyswietlana);

        List<String> szablon = cfg.getStringList("gui.blok-lore");
        if (szablon.isEmpty()) {
            szablon = List.of(
                    "&f",
                    "&f 丶 ᴄᴇɴᴀ ᴢᴀ ʙʟᴏᴋ: &#FFC34D%cena%$",
                    "&f",
                    "&f 亝 ᴢ ꜰᴏʀᴛᴜɴᴇ:",
                    "&8x &fꜰᴏʀᴛᴜɴᴇ I &8- &#FFC34D%f1%$",
                    "&8x &fꜰᴏʀᴛᴜɴᴇ II &8- &#FFC34D%f2%$",
                    "&8x &fꜰᴏʀᴛᴜɴᴇ III &8- &#FFC34D%f3%$",
                    "%turbo%",
                    "&f"
            );
        }

        String turbo = "";
        if (manager.isTurbodropActive()) {
            turbo = cfg.getString("gui.blok-turbo", "&f 乂 ᴛᴜʀʙᴏᴅʀᴏᴘ &8(&#FFC34Dx%x%&8) &8- &#FFC34D%turbo-cena%$")
                    .replace("%x%", formatuj(manager.getTurbodropMnoznik()))
                    .replace("%turbo-cena%", formatuj(cena * manager.getTurbodropMnoznik()));
        }

        List<String> lore = new ArrayList<>(szablon.size());
        for (String linia : szablon) {
            if (linia.contains("%turbo%")) {
                if (turbo.isEmpty()) continue;
                linia = linia.replace("%turbo%", turbo);
            }
            lore.add(linia
                    .replace("%cena%", formatuj(cena))
                    .replace("%f1%", formatuj(manager.cenaZFortune(cena, 1)))
                    .replace("%f2%", formatuj(manager.cenaZFortune(cena, 2)))
                    .replace("%f3%", formatuj(manager.cenaZFortune(cena, 3)))
                    .replace("%blok%", wyswietlana));
        }
        return item(ikona, nazwa, lore);
    }

    private ItemStack ikonaInfo(FileConfiguration cfg, Player player) {
        String turbo = manager.isTurbodropActive()
                ? cfg.getString("gui.info-turbo-on", "&aᴀᴋᴛʏᴡɴʏ &8(&#FFC34Dx%x% &8| &f%czas%s&8)")
                        .replace("%x%", formatuj(manager.getTurbodropMnoznik()))
                        .replace("%czas%", String.valueOf(manager.getTurbodropCzas()))
                : cfg.getString("gui.info-turbo-off", "&cᴡʏʟᴀᴄᴢᴏɴʏ");

        List<String> szablon = cfg.getStringList("gui.info-lore");
        if (szablon.isEmpty()) {
            szablon = List.of(
                    "&f",
                    "&f 丶 ᴋᴏᴘɪᴇꜱᴢ ʙʟᴏᴋɪ ᴢ ʟɪꜱᴛʏ, ᴀ ᴋᴀꜱᴀ",
                    "&f ᴡᴘᴀᴅᴀ &#FFC34Dᴀᴜᴛᴏᴍᴀᴛʏᴄᴢɴɪᴇ &fɴᴀ ᴛᴡᴏᴊᴇ ᴋᴏɴᴛᴏ",
                    "&f",
                    "&f 亝 ꜰᴏʀᴛᴜɴᴇ: &#FFC34D+%fortune-pct%% &fᴢᴀ ᴋᴀᴢᴅʏ ᴘᴏᴢɪᴏᴍ",
                    "&f 乂 ᴛᴜʀʙᴏᴅʀᴏᴘ: %turbo%",
                    "&f 丮 ᴛᴡᴏᴊ ᴢᴀʀᴏʙᴇᴋ/ᴍɪɴ: &#FFC34D%na-minute%$",
                    "&f"
            );
        }
        String naMinute = formatuj(manager.getZarobkiNaMinute(player.getUniqueId()));
        String fortunePct = formatuj(manager.getFortuneBonusNaPoziom() * 100.0);
        List<String> lore = new ArrayList<>(szablon.size());
        for (String linia : szablon) {
            lore.add(linia.replace("%turbo%", turbo)
                    .replace("%na-minute%", naMinute)
                    .replace("%fortune-pct%", fortunePct));
        }
        return item(materialZConfigu(cfg.getString("gui.info-ikona", "GOLDEN_PICKAXE"), Material.GOLDEN_PICKAXE),
                cfg.getString("gui.info-nazwa", "&f丮 &#FFC34D&lɪɴꜰᴏʀᴍᴀᴄᴊᴇ"), lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot == SLOT_POPRZEDNIA && holder.strona > 0) {
            otworz(player, holder.strona - 1);
        } else if (slot == SLOT_NASTEPNA) {
            ItemStack kliknieto = event.getCurrentItem();
            if (kliknieto != null && kliknieto.getType() == Material.SPECTRAL_ARROW) {
                otworz(player, holder.strona + 1);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static ItemStack item(Material material, String nazwa, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Msg.color(nazwa));
        List<String> kolorowe = new ArrayList<>(lore.size());
        for (String linia : lore) {
            kolorowe.add(Msg.color(linia));
        }
        meta.setLore(kolorowe);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static Material materialZConfigu(String nazwa, Material domyslny) {
        Material m = nazwa == null ? null : Material.matchMaterial(nazwa);
        return (m != null && m.isItem()) ? m : domyslny;
    }

    private static String smallCaps(String tekst) {
        StringBuilder sb = new StringBuilder(tekst.length());
        for (char c : tekst.toLowerCase(Locale.ROOT).toCharArray()) {
            int idx = SMALL_FROM.indexOf(c);
            sb.append(idx >= 0 ? SMALL_TO.charAt(idx) : c);
        }
        return sb.toString();
    }

    private static String formatuj(double wartosc) {
        if (wartosc == Math.floor(wartosc) && !Double.isInfinite(wartosc)) {
            return String.valueOf((long) wartosc);
        }
        return String.format(Locale.US, "%.2f", wartosc);
    }
}
