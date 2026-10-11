package boniec.szaman.listener;

import boniec.szaman.SzamanPlugin;
import boniec.szaman.gui.SzamanGuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Odpowiednik bloku "on inventory click:" z oryginalu, tylko ze zamiast
 * sprawdzania "name of event-inventory is ..." (co moglo kolidowac z innymi
 * pluginami majacymi GUI o tym samym tytule), rozpoznajemy nasze GUI po typie
 * InventoryHolder (SzamanGuiHolder) - bezpieczniejsze i szybsze.
 */
public class SzamanGuiListener implements Listener {

    public SzamanGuiListener(SzamanPlugin plugin) {
        // plugin nieuzywany bezposrednio tutaj, ale trzymany dla spojnosci z reszta klas
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SzamanGuiHolder gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Klikniecia w ekwipunek gracza (dolna czesc) w naszych GUI ignorujemy
        // domyslnie - w oryginale caly event byl "cancel event" niezaleznie od
        // tego, gdzie kliknieto, wiec zachowujemy to zachowanie przez samo
        // wywolanie gui.onClick(), ktore samo ustawia setCancelled(true).
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            event.setCancelled(true);
            return;
        }

        gui.onClick(event, player);
    }
}
