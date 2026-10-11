package boniec.szaman.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Wspolny interfejs dla wszystkich GUI Szamana. Analogiczny do KlanGui z modulu
 * klanow - dzieki temu SzamanGuiListener rozpoznaje kliknięcia po typie holdera
 * ekwipunku, zamiast po tytule (name of event-inventory) tak jak w oryginalnym
 * Skripcie. To rowniez naprawia mozliwe kolizje, gdyby dwa gracze mieli tytuly
 * GUI z tym samym tekstem, ale rozna zawartoscia.
 */
public interface SzamanGuiHolder extends InventoryHolder {

    void onClick(InventoryClickEvent event, Player player);
}
