package boniec.skrzynki;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Holder rozpoznajacy inventory systemu skrzynek (podglad / animacja / edycja dropu). */
final class CaseGui implements InventoryHolder {

    enum Kind { PREVIEW, ANIMATION, EDIT }

    final Kind kind;
    final CaseDef def;
    int page;
    /** false = podglad lootboxa / bloku bez przyciskow otwierania */
    boolean allowOpen = true;
    /** rawSlot -> akcja (OPEN, CLOSE, PREV, NEXT) - tylko podglad. */
    final Map<Integer, String> actions = new HashMap<>();
    CaseAnimation animation;
    Inventory inventory;

    /** Sloty dekoracji / przyciskow - w edytorze nie da sie na nie polozyc dropu. */
    final Set<Integer> locked = new HashSet<>();
    int guiSize = 54;

    // --- edytor dropu: slot GUI -> drop (null = pusty). Uklad jak w skrzynce. ---
    /** slot edytora -> drop (null = pusty slot). */
    final CaseDrop[] slots = new CaseDrop[54];
    /** Dropy, ktore nie zmiescily sie w 54 slotach - zachowane bez zmian. */
    final java.util.List<CaseDrop> overflow = new java.util.ArrayList<>();
    /** Drop aktualnie trzymany na kursorze (zeby waga/ogloszenie przetrwaly przenoszenie). */
    CaseDrop held;

    CaseGui(Kind kind, CaseDef def) {
        this.kind = kind;
        this.def = def;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
