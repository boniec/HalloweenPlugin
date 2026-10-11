package boniec.klany.gui;

import boniec.klany.Main;
import boniec.klany.core.Klan;
import boniec.klany.util.ItemBuilder;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI z lista czlonkow klanu (/klan panel -> Czlonkowie).
 * Lider/zastepca moze klikac na glowki, zeby awansowac/degradowac/wyrzucic gracza.
 * Zwykli czlonkowie widza liste, ale klikniecia nic nie robia poza ich wlasna glowka.
 */
public class CzlonkowieGui implements KlanGui, InventoryHolder {

    private final Klan klan;
    private final Inventory inventory;
    private final Map<Integer, UUID> slotDoGracza = new java.util.HashMap<>();

    public CzlonkowieGui(Klan klan) {
        this.klan = klan;
        this.inventory = Bukkit.createInventory(this, 54, Msg.guiTitle("tytul-czlonkowie"));
        zbuduj();
    }

    private void zbuduj() {
        ItemStack szklo = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").hideFlags().build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, szklo);

        int slot = 0;
        for (Map.Entry<UUID, Klan.Ranga> entry : klan.getCzlonkowie().entrySet()) {
            if (slot >= 45) break;
            UUID uuid = entry.getKey();
            Klan.Ranga ranga = entry.getValue();
            OfflinePlayer gracz = Bukkit.getOfflinePlayer(uuid);
            boolean online = gracz.isOnline();

            int zabojstwa = Main.get().getKlanManager().getZabojstwaGracza(uuid);
            int smierci = Main.get().getKlanManager().getSmierciGracza(uuid);

            List<String> lore = new ArrayList<>();
            lore.add("&7Ranga: " + ranga.getEtykieta());
            lore.add("&7Status: " + (online ? "&aOnline" : "&cOffline"));
            lore.add("&7Zabojstwa: &c" + zabojstwa + " &8| &7Smierci: &c" + smierci);
            lore.add("&7K/D: &f" + String.format(java.util.Locale.ROOT, "%.2f", Klan.kd(zabojstwa, smierci)));
            lore.add("");
            lore.add("&8Lewy klik: &7info");
            lore.add("&8Prawy klik (lider/zastepca): &7awans / degradacja");
            lore.add("&8Shift + prawy klik: &cwyrzuc z klanu");

            inventory.setItem(slot, ItemBuilder.glowaGracza(gracz)
                    .name((online ? "&a" : "&7") + gracz.getName())
                    .lore(lore)
                    .build());
            slotDoGracza.put(slot, uuid);
            slot++;
        }

        inventory.setItem(49, new ItemBuilder(Material.ARROW).name("&c&lWroc do panelu").build());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player wykonujacy) {
        int slot = event.getSlot();
        if (slot == 49) {
            wykonujacy.closeInventory();
            return;
        }

        UUID cel = slotDoGracza.get(slot);
        if (cel == null) return;

        // Lewy klik = tylko podglad, nic wiecej.
        if (event.isLeftClick()) {
            return;
        }

        // Prawy klik = akcje zarzadzania - wymaga uprawnien.
        if (!klan.maUprawnieniaZarzadzania(wykonujacy.getUniqueId())) {
            Msg.send(wykonujacy, "brak-uprawnien");
            return;
        }
        if (cel.equals(wykonujacy.getUniqueId())) {
            Msg.send(wykonujacy, "nie-mozna-siebie");
            return;
        }

        Klan.Ranga rangaCelu = klan.getRanga(cel);
        OfflinePlayer celGracz = Bukkit.getOfflinePlayer(cel);
        boolean jestemLiderem = klan.getLider().equals(wykonujacy.getUniqueId());

        wykonujacy.closeInventory();

        if (rangaCelu == Klan.Ranga.LIDER) {
            Msg.sendGui(wykonujacy, "&cNie mozesz zarzadzac liderem klanu.");
            return;
        }

        if (event.isShiftClick()) {
            Main.get().getKlanManager().wyrzucCzlonka(wykonujacy, klan, celGracz);
            return;
        }

        // Tylko lider moze przekazywac przywodztwo / mianowac zastepce.
        if (rangaCelu == Klan.Ranga.CZLONEK) {
            if (!jestemLiderem) {
                Msg.send(wykonujacy, "brak-uprawnien");
                return;
            }
            Main.get().getKlanManager().ustawRange(klan, cel, Klan.Ranga.ZASTEPCA);
            Msg.send(wykonujacy, "nowy-zastepca", "%gracz%", celGracz.getName());
            Main.get().getKlanManager().dodajLog(klan, wykonujacy.getName() + " awansowal " + celGracz.getName() + " na zastepce.");
        } else if (rangaCelu == Klan.Ranga.ZASTEPCA) {
            if (!jestemLiderem) {
                Msg.send(wykonujacy, "brak-uprawnien");
                return;
            }
            Main.get().getKlanManager().ustawRange(klan, cel, Klan.Ranga.CZLONEK);
            Msg.send(wykonujacy, "odebrano-zastepce", "%gracz%", celGracz.getName());
            Main.get().getKlanManager().dodajLog(klan, wykonujacy.getName() + " odebral range zastepcy " + celGracz.getName() + ".");
        }
    }
}
