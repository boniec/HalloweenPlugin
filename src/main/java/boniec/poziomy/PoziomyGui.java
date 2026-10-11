package boniec.poziomy;

import boniec.szaman.util.Msg;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** /poziom - nagrody za poziomy (z paginacja) oraz edytor przedmiotow nagrody dla admina. */
public class PoziomyGui implements Listener {

    private static final int SIZE = 54;
    private static final int[] SLOTY_NAGROD = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_INFO = 4;
    private static final int SLOT_POPRZEDNIA = 48;
    private static final int SLOT_NASTEPNA = 50;
    private static final Set<Integer> ZOLTE = Set.of(0, 8, 45, 53);
    private static final Set<Integer> POMARANCZOWE = Set.of(1, 7, 9, 17, 36, 44, 46, 52);

    private static final class NagrodyHolder implements InventoryHolder {
        private final int strona;
        private final Map<Integer, Integer> slotNaPoziom = new java.util.HashMap<>();
        private Inventory inventory;
        private NagrodyHolder(int strona) { this.strona = strona; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class EdytorHolder implements InventoryHolder {
        private final int poziom;
        private Inventory inventory;
        private EdytorHolder(int poziom) { this.poziom = poziom; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final PoziomyManager manager;

    public PoziomyGui(PoziomyManager manager) {
        this.manager = manager;
    }

    // ===================== Nagrody gracza =====================

    public void otworz(Player player, int strona) {
        FileConfiguration cfg = manager.getConfig();
        List<Integer> poziomy = new ArrayList<>(manager.getPoziomy());
        int stron = Math.max(1, (int) Math.ceil(poziomy.size() / (double) SLOTY_NAGROD.length));
        strona = Math.max(0, Math.min(strona, stron - 1));

        String tytul = cfg.getString("gui.tytul", "&f乂 &#C02FD4&lɴᴀɢʀᴏᴅʏ ᴢᴀ ᴘᴏᴢɪᴏᴍ &8(%strona%/%stron%)")
                .replace("%strona%", String.valueOf(strona + 1))
                .replace("%stron%", String.valueOf(stron));

        NagrodyHolder holder = new NagrodyHolder(strona);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Msg.color(tytul));
        holder.inventory = inv;

        if (manager.awansujJesliMozna(manager.getProfil(player)) > 0) {
            manager.oznaczDoZapisu();
        }

        ItemStack zolta = item(ramkaMaterial(cfg, "gui.ramka-narozniki", Material.YELLOW_STAINED_GLASS_PANE), " ", List.of());
        ItemStack pomaranczowa = item(ramkaMaterial(cfg, "gui.ramka-krawedzie", Material.ORANGE_STAINED_GLASS_PANE), " ", List.of());
        ItemStack biala = item(ramkaMaterial(cfg, "gui.ramka-reszta", Material.WHITE_STAINED_GLASS_PANE), " ", List.of());
        for (int i = 0; i < SIZE; i++) {
            boolean ramka = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            if (!ramka) continue;
            inv.setItem(i, ZOLTE.contains(i) ? zolta : POMARANCZOWE.contains(i) ? pomaranczowa : biala);
        }
        inv.setItem(SLOT_INFO, ikonaInfo(cfg, player));

        Profil profil = manager.getProfil(player);
        int poziomGracza = profil.getPoziom();
        int start = strona * SLOTY_NAGROD.length;
        for (int i = 0; i < SLOTY_NAGROD.length && start + i < poziomy.size(); i++) {
            int poziom = poziomy.get(start + i);
            inv.setItem(SLOTY_NAGROD[i], ikonaNagrody(cfg, poziom, poziomGracza, profil.getOdebraneNagrody().contains(poziom)));
            holder.slotNaPoziom.put(SLOTY_NAGROD[i], poziom);
        }

        if (strona > 0) {
            inv.setItem(SLOT_POPRZEDNIA, item(Material.SPECTRAL_ARROW, cfg.getString("gui.poprzednia", "&f乁 &#C02FD4ᴘᴏᴘʀᴢᴇᴅɴɪᴀ ꜱᴛʀᴏɴᴀ"), List.of()));
        }
        if (strona < stron - 1) {
            inv.setItem(SLOT_NASTEPNA, item(Material.SPECTRAL_ARROW, cfg.getString("gui.nastepna", "&f乁 &#C02FD4ɴᴀꜱᴛᴇᴘɴᴀ ꜱᴛʀᴏɴᴀ"), List.of()));
        }
        player.openInventory(inv);
    }

    private ItemStack ikonaInfo(FileConfiguration cfg, Player player) {
        Profil profil = manager.getProfil(player);
        int poziom = profil.getPoziom();
        int nastepny = manager.getExpNaNastepny(poziom);
        List<String> lore = new ArrayList<>();
        for (String linia : cfg.getStringList("gui.info-lore")) {
            lore.add(linia
                    .replace("%poziom%", String.valueOf(poziom))
                    .replace("%exp%", PoziomyManager.formatujExp(profil.getExp()))
                    .replace("%cel%", nastepny < 0 ? "ᴍᴀx" : PoziomyManager.formatujExp(nastepny))
                    .replace("%pasek%", manager.getPasekPostepu(manager.getProcent(profil))));
        }
        return item(materialZConfigu(cfg.getString("gui.info-ikona", "GLOW_ITEM_FRAME"), Material.GLOW_ITEM_FRAME),
                cfg.getString("gui.info-nazwa", "&f乂 &#C02FD4&lᴀᴡᴀɴꜱᴇ"), lore);
    }

    private ItemStack ikonaNagrody(FileConfiguration cfg, int poziom, int poziomGracza, boolean odebrana) {
        Material material = odebrana
                ? materialZConfigu(cfg.getString("gui.nagroda-odebrana", "MINECART"), Material.MINECART)
                : materialZConfigu(cfg.getString("gui.nagroda-nieodebrana", "CHEST_MINECART"), Material.CHEST_MINECART);

        String dopisek = odebrana ? cfg.getString("gui.dopisek-odebrana", " &8- &aᴏᴅᴇʙʀᴀɴᴏ")
                : poziomGracza >= poziom ? cfg.getString("gui.dopisek-do-odebrania", " &8- &eᴅᴏ ᴏᴅᴇʙʀᴀɴɪᴀ")
                : cfg.getString("gui.dopisek-za-niski", " &8- &cᴢᴀ ɴɪꜱᴋɪ ᴘᴏᴢɪᴏᴍ");
        String nazwa = cfg.getString("gui.nagroda-nazwa", "&f乂 &#C02FD4&lɴᴀɢʀᴏᴅᴀ &f#%poziom%")
                .replace("%poziom%", String.valueOf(poziom)) + dopisek;

        List<String> przedmioty = new ArrayList<>();
        String prezentacja = cfg.getString("gui.prezentacja-przedmiotu", "&8x &f%nazwa% &#C02FD4x%ilosc%");
        for (ItemStack item : manager.getNagrodyPrzedmioty(poziom)) {
            ItemMeta meta = item.getItemMeta();
            String nazwaItemu = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName()
                    : item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            przedmioty.add(prezentacja.replace("%nazwa%", nazwaItemu).replace("%ilosc%", String.valueOf(item.getAmount())));
        }
        if (przedmioty.isEmpty() && manager.getNagrodyKomendy(poziom).isEmpty()) {
            przedmioty.add(cfg.getString("gui.brak-nagrod", "&8x &7ʙʀᴀᴋ ɴᴀɢʀᴏᴅ"));
        }

        Integer wymagany = manager.getExpNaPoziom(poziom);
        List<String> lore = new ArrayList<>();
        for (String linia : cfg.getStringList("gui.nagroda-lore")) {
            if (linia.contains("%przedmioty%")) {
                lore.addAll(przedmioty);
                continue;
            }
            lore.add(linia
                    .replace("%poziom%", String.valueOf(poziom))
                    .replace("%wymagany-exp%", PoziomyManager.formatujExp(wymagany == null ? 0 : wymagany)));
        }
        ItemStack ikona = item(material, nazwa, lore);
        if (!odebrana && poziomGracza >= poziom) {
            ItemMeta meta = ikona.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                ikona.setItemMeta(meta);
            }
        }
        return ikona;
    }

    // ===================== Edytor nagrody (admin) =====================

    public void otworzEdytor(Player player, int poziom) {
        EdytorHolder holder = new EdytorHolder(poziom);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                Msg.color(manager.getConfig().getString("gui.edytor-tytul", "&8ɴᴀɢʀᴏᴅʏ ᴢᴀ ᴘᴏᴢɪᴏᴍ %poziom%")
                        .replace("%poziom%", String.valueOf(poziom))));
        holder.inventory = inv;
        List<ItemStack> nagrody = manager.getNagrodyPrzedmioty(poziom);
        for (int i = 0; i < nagrody.size() && i < SIZE; i++) {
            inv.setItem(i, nagrody.get(i));
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EdytorHolder holder)) return;
        List<ItemStack> przedmioty = new ArrayList<>();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) przedmioty.add(item.clone());
        }
        manager.setNagrodyPrzedmioty(holder.poziom, przedmioty);
        Msg.send(event.getPlayer(), manager.msg("edytor-zapisano", "&f乂 &#C02FD4ᴘᴏᴢɪᴏᴍʏ &8| &aZapisano &f%ilosc% &aprzedmiotow nagrody za poziom &f%poziom%&a.")
                .replace("%ilosc%", String.valueOf(przedmioty.size()))
                .replace("%poziom%", String.valueOf(holder.poziom)));
    }

    // ===================== Klikniecia =====================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof NagrodyHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == SLOT_POPRZEDNIA && holder.strona > 0) {
            otworz(player, holder.strona - 1);
            return;
        }
        if (slot == SLOT_NASTEPNA && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.SPECTRAL_ARROW) {
            otworz(player, holder.strona + 1);
            return;
        }

        Integer poziom = holder.slotNaPoziom.get(slot);
        if (poziom == null) return;

        switch (manager.odbierzNagrode(player, poziom)) {
            case OK -> {
                String ogloszenie = manager.msg("odebrano-ogloszenie", "&f乂 &8| &fGracz &#C02FD4%gracz% &fodebrał nagrodę za poziom &#C02FD4&l%poziom%&f!")
                        .replace("%gracz%", player.getName()).replace("%poziom%", String.valueOf(poziom));
                Bukkit.broadcastMessage(Msg.color(ogloszenie));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
                otworz(player, holder.strona);
            }
            case JUZ_ODEBRANA -> Msg.send(player, manager.msg("juz-odebrana", "&f乂 &#C02FD4ᴘᴏᴢɪᴏᴍʏ &8| &cOdebrałeś już tą nagrodę."));
            case ZA_NISKI_POZIOM -> Msg.send(player, manager.msg("za-niski-poziom", "&f乂 &#C02FD4ᴘᴏᴢɪᴏᴍʏ &8| &cAby odebrać tą nagrodę musisz mieć &f%poziom% &cpoziom.")
                    .replace("%poziom%", String.valueOf(poziom)));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NagrodyHolder) {
            event.setCancelled(true);
        }
    }

    // ===================== Pomocnicze =====================

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

    private static Material ramkaMaterial(FileConfiguration cfg, String klucz, Material domyslny) {
        String spec = cfg.getString(klucz);
        if (spec == null || spec.isBlank()) {
            spec = cfg.getString("gui.ramka");
        }
        return materialZConfigu(spec, domyslny);
    }

    private static Material materialZConfigu(String nazwa, Material domyslny) {
        Material m = nazwa == null ? null : Material.matchMaterial(nazwa);
        return (m != null && m.isItem()) ? m : domyslny;
    }
}
