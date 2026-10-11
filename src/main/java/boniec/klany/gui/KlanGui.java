package boniec.klany.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Bazowy interfejs kazdego GUI systemu klanow. GuiListener sprawdza instanceof
 * i deleguje klikniecia do konkretnej implementacji.
 */
public interface KlanGui {

    Inventory getInventory();

    void onClick(InventoryClickEvent event, Player gracz);

    default void otworz(Player gracz) {
        gracz.openInventory(getInventory());
    }
}
