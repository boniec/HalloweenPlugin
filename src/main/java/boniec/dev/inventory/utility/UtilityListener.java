package boniec.dev.inventory.utility;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;

public final class UtilityListener implements Listener {
    @EventHandler
    public void onShroomlightBreak(BlockDropItemEvent event) {
        if (event.getBlockState().getType() == Material.SHROOMLIGHT) {
            event.getItems().clear();
        }
    }
}
