package boniec.skrzynki;

import org.bukkit.inventory.ItemStack;

/** Jeden mozliwy drop: przedmiot (z iloscia), waga losowania i flaga ogloszenia na czacie. */
final class CaseDrop {

    ItemStack item;
    double weight;
    boolean announce;
    /** Slot w GUI skrzynki (-1 = stary zapis, uklad po kolei). */
    int slot = -1;

    CaseDrop(ItemStack item, double weight, boolean announce) {
        this(item, weight, announce, -1);
    }

    CaseDrop(ItemStack item, double weight, boolean announce, int slot) {
        this.item = item;
        this.weight = weight;
        this.announce = announce;
        this.slot = slot;
    }
}
