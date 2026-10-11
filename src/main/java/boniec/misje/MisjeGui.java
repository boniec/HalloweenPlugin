package boniec.misje;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MisjeGui implements Listener {

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> slotNaId = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class EdytorHolder implements InventoryHolder {
        private final String id;
        private Inventory inventory;
        private EdytorHolder(String id) { this.id = id; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final MisjeManager manager;

    public MisjeGui(MisjeManager manager) {
        this.manager = manager;
    }

    public void otworz(Player player) {
        FileConfiguration cfg = manager.getCfg();
        int size = cfg.getInt("gui.wielkosc", 27);
        if (size != 9 && size != 18 && size != 27 && size != 36 && size != 45 && size != 54) size = 27;

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, size, Msg.color(manager.txt("gui.tytul", "&f亄 &#FFC34D&lᴍɪꜱᴊᴇ ᴅɴɪᴀ")));
        holder.inventory = inv;

        ItemStack ramka = item(material(cfg.getString("gui.ramka"), Material.BLACK_STAINED_GLASS_PANE), " ", List.of());
        for (int i = 0; i < size; i++) inv.setItem(i, ramka);

        int infoSlot = cfg.getInt("gui.info-slot", 4);
        if (infoSlot >= 0 && infoSlot < size) {
            inv.setItem(infoSlot, ikonaInfo(player));
        }

        for (MisjeManager.Definicja def : manager.getMisje().values()) {
            if (def.slot < 0 || def.slot >= size) continue;
            inv.setItem(def.slot, ikonaMisji(player, def));
            holder.slotNaId.put(def.slot, def.id);
        }

        int slotZ = cfg.getInt("gui.zamknij.slot", 22);
        if (slotZ >= 0 && slotZ < size) {
            inv.setItem(slotZ, item(
                    material(cfg.getString("gui.zamknij.material"), Material.BARRIER),
                    manager.txt("gui.zamknij.nazwa", "&f个 &cᴢᴀᴍᴋɴɪᴊ"),
                    cfg.getStringList("gui.zamknij.lore")));
        }
        player.openInventory(inv);
    }

    public void otworzEdytor(Player player, String id) {
        EdytorHolder holder = new EdytorHolder(id);
        Inventory inv = Bukkit.createInventory(holder, 54, Msg.color(
                manager.txt("gui.edytor-tytul", "&8ɴᴀɢʀᴏᴅᴀ ᴍɪꜱᴊɪ: %id%").replace("%id%", id)));
        holder.inventory = inv;
        List<ItemStack> nagrody = manager.getNagrody(id);
        for (int i = 0; i < nagrody.size() && i < 54; i++) {
            inv.setItem(i, nagrody.get(i));
        }
        player.openInventory(inv);
    }

    private ItemStack ikonaInfo(Player player) {
        int wszystkie = manager.getMisje().size();
        int ukonczone = 0;
        for (MisjeManager.Definicja def : manager.getMisje().values()) {
            if (manager.wartosc(player.getUniqueId(), def.id) >= def.cel) ukonczone++;
        }
        List<String> lore = new ArrayList<>();
        for (String linia : manager.getCfg().getStringList("gui.info-lore")) {
            lore.add(linia
                    .replace("%reset%", manager.czasDoResetu())
                    .replace("%ukonczone%", String.valueOf(ukonczone))
                    .replace("%wszystkie%", String.valueOf(wszystkie)));
        }
        return item(material(manager.getCfg().getString("gui.info-ikona"), Material.CLOCK),
                manager.txt("gui.info-nazwa", "&f丩 &#FFC34D&lᴍɪꜱᴊᴇ ᴅɴɪᴀ"), lore);
    }

    private ItemStack ikonaMisji(Player player, MisjeManager.Definicja def) {
        MisjeManager.Postep p = manager.postep(player.getUniqueId(), def.id);
        boolean gotowe = p.wartosc >= def.cel;
        String dopisek = p.odebrano
                ? manager.txt("gui.dopisek-odebrana", " &8- &aᴏᴅᴇʙʀᴀɴᴏ")
                : gotowe
                ? manager.txt("gui.dopisek-do-odebrania", " &8- &eᴅᴏ ᴏᴅᴇʙʀᴀɴɪᴀ")
                : manager.txt("gui.dopisek-w-trakcie", " &8- &7ᴡ ᴛʀᴀᴋᴄɪᴇ");
        String status = p.odebrano
                ? manager.txt("status.odebrane", "&f丣 &aɴᴀɢʀᴏᴅᴀ ᴏᴅᴇʙʀᴀɴᴀ")
                : gotowe
                ? manager.txt("status.gotowe", "&f両 &aᴜᴋᴏɴᴄᴢᴏɴᴀ")
                : manager.txt("status.w-trakcie", "&f五 &#FFC34D%postep%&8/&f%cel%")
                .replace("%postep%", String.valueOf(p.wartosc))
                .replace("%cel%", String.valueOf(def.cel));

        String prezentacja = manager.txt("gui.prezentacja-przedmiotu", "&8x &f%nazwa% &#FFC34Dx%ilosc%");
        List<String> nagrodyLinie = new ArrayList<>();
        for (ItemStack item : manager.getNagrody(def.id)) {
            String nazwa = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name().toLowerCase().replace('_', ' ');
            nagrodyLinie.add(prezentacja.replace("%nazwa%", nazwa).replace("%ilosc%", String.valueOf(item.getAmount())));
        }
        if (nagrodyLinie.isEmpty()) {
            nagrodyLinie.add(manager.txt("gui.brak-nagrod", "&8x &7ʙʀᴀᴋ ɴᴀɢʀᴏᴅ · /amisje ustawnagroda"));
        }

        List<String> lore = new ArrayList<>();
        for (String linia : def.lore) {
            if (linia.contains("%nagrody%")) {
                lore.addAll(nagrodyLinie);
            } else {
                lore.add(linia.replace("%status%", status)
                        .replace("%postep%", String.valueOf(p.wartosc))
                        .replace("%cel%", String.valueOf(def.cel)));
            }
        }
        String tytul = "&f" + def.ikona + " " + def.nazwa + dopisek;
        ItemStack ikona = item(def.material, tytul, lore);
        if (gotowe && !p.odebrano) {
            ItemMeta meta = ikona.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                ikona.setItemMeta(meta);
            }
        }
        return ikona;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EdytorHolder) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == manager.getCfg().getInt("gui.zamknij.slot", 22)) {
            player.closeInventory();
            return;
        }
        String id = holder.slotNaId.get(slot);
        if (id == null) return;
        MisjeManager.Definicja def = manager.getMisja(id);
        switch (manager.odbierz(player, id)) {
            case OK -> {
                player.sendMessage(Msg.color(manager.txt("wiadomosci.odebrano",
                        "&8[&f亄&8] &f両 &aᴏᴅᴇʙʀᴀʟᴇꜱ ɴᴀɢʀᴏᴅᴇ ᴢᴀ &#FFC34D%nazwa%")
                        .replace("%nazwa%", def == null ? id : def.nazwa)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
                otworz(player);
            }
            case JUZ_ODEBRANA -> player.sendMessage(Msg.color(manager.txt("wiadomosci.juz-odebrana",
                    "&8[&f亄&8] &f亢 &cᴛᴀ ɴᴀɢʀᴏᴅᴀ ᴊᴜᴢ ᴢᴏꜱᴛᴀʟᴀ ᴏᴅᴇʙʀᴀɴᴀ.")));
            case NIE_UKONCZONA -> {
                MisjeManager.Postep p = manager.postep(player.getUniqueId(), id);
                player.sendMessage(Msg.color(manager.txt("wiadomosci.nie-ukonczona",
                        "&8[&f亄&8] &f亢 &cɴᴀᴊᴘɪᴇʀᴡ ᴜᴋᴏɴᴄᴢ ᴍɪꜱᴊᴇ. &7(&f%postep%&8/&f%cel%&7)")
                        .replace("%postep%", String.valueOf(p.wartosc))
                        .replace("%cel%", String.valueOf(def == null ? 0 : def.cel))));
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EdytorHolder holder)) return;
        List<ItemStack> przedmioty = new ArrayList<>();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) przedmioty.add(item.clone());
        }
        manager.setNagrody(holder.id, przedmioty);
        event.getPlayer().sendMessage(Msg.color(manager.txt("wiadomosci.edytor-zapisano",
                "&8[&f亄&8] &f両 &aᴢᴀᴘɪꜱᴀɴᴏ &f%ilosc% &aᴘʀᴢᴇᴅᴍɪᴏᴛᴏᴡ ɴᴀɢʀᴏᴅʏ: &#FFC34D%id%")
                .replace("%ilosc%", String.valueOf(przedmioty.size()))
                .replace("%id%", holder.id)));
    }

    private static ItemStack item(Material material, String nazwa, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Msg.color(nazwa));
        List<String> kolorowe = new ArrayList<>(lore.size());
        for (String linia : lore) kolorowe.add(Msg.color(linia));
        meta.setLore(kolorowe);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static Material material(String nazwa, Material domyslny) {
        Material m = nazwa == null ? null : Material.matchMaterial(nazwa);
        return (m != null && m.isItem()) ? m : domyslny;
    }
}
