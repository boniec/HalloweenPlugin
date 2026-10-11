package boniec.klany.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Pojedynczy listener dla wszystkich GUI klanow. Kazde GUI implementuje InventoryHolder
 * (przez KlanInventoryHolder), dzieki czemu nie trzeba trzymac osobnych map "otwarte GUI graczy" -
 * po prostu odczytujemy holder z eventu.
 */
public class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof KlanGui gui)) {
            return;
        }
        // Zawsze anulujemy - GUI klanow sa czysto informacyjne/nawigacyjne, gracz
        // nigdy nie powinien miec mozliwosci wyciagniecia/przelozenia itemu z panelu.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Klikniecia poza gorny inventory (np. w plecak gracza) ignorujemy calkowicie.
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        gui.onClick(event, player);
    }
}
