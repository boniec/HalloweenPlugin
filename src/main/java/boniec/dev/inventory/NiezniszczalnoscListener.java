package boniec.dev.inventory;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Wszystkie itemy sa niezniszczalne, oprocz nozyc (SHEARS) i netherytowej lopaty.
 * Bez Unbreakable w NBT/lore - pasek dury zostaje, po prostu nie spada.
 */
public class NiezniszczalnoscListener implements Listener {

    private static final Set<Material> ZUZYWAJA_SIE = EnumSet.of(
            Material.SHEARS,
            Material.NETHERITE_SHOVEL
    );

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (event.getItem() == null) return;
        if (ZUZYWAJA_SIE.contains(event.getItem().getType())) return;

        event.setCancelled(true);
        event.setDamage(0);
    }
}
