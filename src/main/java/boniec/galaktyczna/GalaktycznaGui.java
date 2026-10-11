package boniec.galaktyczna;

import boniec.dev.inventory.Main;
import boniec.klany.util.Msg;
import boniec.skrzynki.CaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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

public final class GalaktycznaGui implements Listener {

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final GalaktycznaManager manager;
    private final CaseManager caseManager;

    public GalaktycznaGui(GalaktycznaManager manager, CaseManager caseManager) {
        this.manager = manager;
        this.caseManager = caseManager;
    }

    public void otworz(Player gracz) {
        FileConfiguration cfg = manager.cfg();
        int size = cfg.getInt("gui.wielkosc", 27);
        if (size != 9 && size != 18 && size != 27 && size != 36 && size != 45 && size != 54) {
            size = 27;
        }

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, size, Msg.color(cfg.getString("gui.tytul", "&8Galaktyczna skrzynia")));
        holder.inventory = inv;

        ItemStack ramka = item(manager.material("gui.ramka", Material.GRAY_STAINED_GLASS_PANE), " ", List.of());
        for (int i = 0; i < size; i++) {
            inv.setItem(i, ramka);
        }

        int infoSlot = cfg.getInt("gui.slot-info", 4);
        if (infoSlot >= 0 && infoSlot < size) {
            inv.setItem(infoSlot, ikonaInfo(gracz));
        }

        int wymianaSlot = cfg.getInt("gui.slot-wymiana", 13);
        if (wymianaSlot >= 0 && wymianaSlot < size) {
            inv.setItem(wymianaSlot, ikonaWymiana(gracz));
        }

        int zamknijSlot = cfg.getInt("gui.slot-zamknij", 22);
        if (zamknijSlot >= 0 && zamknijSlot < size) {
            inv.setItem(zamknijSlot, item(
                    manager.material("gui.zamknij.material", Material.BARRIER),
                    cfg.getString("gui.zamknij.nazwa", "&cZamknij"),
                    List.of()));
        }

        gracz.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player gracz)) return;
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != event.getView().getTopInventory().getHolder()) {
            return;
        }

        FileConfiguration cfg = manager.cfg();
        int slot = event.getSlot();
        if (slot == cfg.getInt("gui.slot-zamknij", 22)) {
            gracz.closeInventory();
            return;
        }
        if (slot != cfg.getInt("gui.slot-wymiana", 13)) {
            return;
        }
        wymien(gracz);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void wymien(Player gracz) {
        Main main = Main.getInstance();
        int koszt = manager.kosztOdlamkow();
        int ma = OdlamkiHelper.policz(gracz, main);

        if (ma < koszt) {
            gracz.sendMessage(manager.prefix() + manager.msg("brak-odlamkow", "&cMasz za mało odłamków!")
                    .replace("%masz%", String.valueOf(ma))
                    .replace("%koszt%", String.valueOf(koszt)));
            gracz.playSound(gracz.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            otworz(gracz);
            return;
        }

        String caseId = manager.skrzynkaId();
        if (!caseManager.istnieje(caseId)) {
            gracz.sendMessage(manager.prefix() + manager.msg("brak-skrzynki", "&cSkrzynka nie istnieje.")
                    .replace("%id%", caseId));
            gracz.playSound(gracz.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!OdlamkiHelper.zabierz(gracz, main, koszt)) {
            gracz.sendMessage(manager.prefix() + manager.msg("blad-zabierania", "&cBłąd pobierania odłamków."));
            return;
        }

        int before = countFreeSlots(gracz);
        String keyName = caseManager.dajKlucz(gracz, caseId, manager.iloscSkrzyn());
        if (keyName == null) {
            zwrocOdlamki(gracz, main, koszt);
            gracz.sendMessage(manager.prefix() + manager.msg("brak-skrzynki", "&cSkrzynka nie istnieje.")
                    .replace("%id%", caseId));
            return;
        }

        int after = countFreeSlots(gracz);
        if (before == after && before == 0) {
            gracz.sendMessage(manager.prefix() + manager.msg("brak-miejsca", "&eEkwipunek pełny — item na ziemi."));
        }

        gracz.sendMessage(manager.prefix() + manager.msg("sukces", "&aOtrzymano skrzynię!")
                .replace("%koszt%", String.valueOf(koszt)));
        gracz.playSound(gracz.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        otworz(gracz);
    }

    private static void zwrocOdlamki(Player gracz, Main main, int ile) {
        if (main.getMistrzSystem() != null && main.getMistrzSystem().giveOdlamki(gracz, ile)) {
            return;
        }
        var km = main.getKlanWalutaManager();
        if (km != null && km.getOdlamekTemplate() != null) {
            ItemStack stack = km.getOdlamekTemplate().clone();
            stack.setAmount(Math.min(64, ile));
            int left = ile;
            while (left > 0) {
                ItemStack part = stack.clone();
                part.setAmount(Math.min(64, left));
                for (ItemStack rest : gracz.getInventory().addItem(part).values()) {
                    gracz.getWorld().dropItemNaturally(gracz.getLocation(), rest);
                }
                left -= part.getAmount();
            }
        }
    }

    private static int countFreeSlots(Player gracz) {
        int wolne = 0;
        for (ItemStack stack : gracz.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) wolne++;
        }
        return wolne;
    }

    private ItemStack ikonaInfo(Player gracz) {
        FileConfiguration cfg = manager.cfg();
        int koszt = manager.kosztOdlamkow();
        int ma = OdlamkiHelper.policz(gracz, Main.getInstance());
        List<String> lore = new ArrayList<>();
        for (String linia : cfg.getStringList("gui.info.lore")) {
            lore.add(linia.replace("%koszt%", String.valueOf(koszt)).replace("%masz%", String.valueOf(ma)));
        }
        return item(
                manager.material("gui.info.material", Material.NETHER_STAR),
                cfg.getString("gui.info.nazwa", "&dGalaktyczna skrzynia"),
                lore);
    }

    private ItemStack ikonaWymiana(Player gracz) {
        FileConfiguration cfg = manager.cfg();
        int koszt = manager.kosztOdlamkow();
        int ma = OdlamkiHelper.policz(gracz, Main.getInstance());
        boolean gotowe = ma >= koszt;
        String base = gotowe ? "gui.wymiana.nazwa-gotowe" : "gui.wymiana.nazwa-brak";
        List<String> rawLore = gotowe
                ? cfg.getStringList("gui.wymiana.lore-gotowe")
                : cfg.getStringList("gui.wymiana.lore-brak");
        int brakuje = Math.max(0, koszt - ma);
        List<String> lore = new ArrayList<>();
        for (String linia : rawLore) {
            lore.add(linia
                    .replace("%koszt%", String.valueOf(koszt))
                    .replace("%masz%", String.valueOf(ma))
                    .replace("%brakuje%", String.valueOf(brakuje)));
        }
        return item(
                manager.material("gui.wymiana.material", Material.END_CRYSTAL),
                cfg.getString(base, gotowe ? "&aOdbierz" : "&cZa mało"),
                lore);
    }

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(Msg.color(l));
                meta.setLore(colored);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
