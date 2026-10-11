package boniec.code.core;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Gdy gracz uderzy KOGOKOLWIEK (gracza, moba, cokolwiek) mieczem z odpowiednim
 * poziomem Sharpness, miecz na stale dostaje odpowiadajace mu CustomModelData:
 * <pre>
 *   Sharpness 25 -> CMD 3001
 *   Sharpness 27 -> CMD 3002
 *   Sharpness 29 -> CMD 3003
 *   Sharpness 31 -> CMD 3004
 *   Sharpness 33 -> CMD 3005
 *   Sharpness 35 -> CMD 3006
 *   Sharpness 37 -> CMD 3007
 * </pre>
 * CMD zapisuje sie w NBT przedmiotu (ItemMeta), wiec jest trwale - nie znika po
 * restarcie, zmianie slotu ani wyrzuceniu itemu. Brak jakichkolwiek wiadomosci
 * na czacie.
 * <p>
 * Listener NIE zmienia obrazen ani Sharpness - dziala na MONITOR (po tym, jak
 * HitSystemListener i CombatListener skoncza swoje) i tylko dopisuje CMD.
 * Sharpness pozostaje bez zmian, wiec HitSystem liczy set miecza jak dotychczas.
 */
public class SwordHitListener implements Listener {

    /** Zwraca CMD dla danego poziomu Sharpness albo -1, gdy poziom nie ma przypisanego CMD. */
    public static int cmdForSharpness(int sharpness) {
        switch (sharpness) {
            case 25: return 3001;
            case 27: return 3002;
            case 29: return 3003;
            case 31: return 3004;
            case 33: return 3005;
            case 35: return 3006;
            case 37: return 3007;
            default: return -1;
        }
    }

    // MONITOR + ignoreCancelled: reagujemy tylko na hity, ktore FAKTYCZNIE sie odbyly
    // (nie anulowane przez anti-spam, i-frames, WorldGuard itp.).
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();

        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (held == null || held.getType() != Material.NETHERITE_SWORD) return;
        if (!held.hasItemMeta()) return;

        ItemMeta meta = held.getItemMeta();
        if (meta == null || !meta.hasEnchant(Enchantment.SHARPNESS)) return;

        int cmd = cmdForSharpness(meta.getEnchantLevel(Enchantment.SHARPNESS));
        if (cmd < 0) return;

        // Juz ustawione - nic nie robimy (oszczedza setItemMeta przy kazdym hicie).
        if (meta.hasCustomModelData() && meta.getCustomModelData() == cmd) return;

        meta.setCustomModelData(cmd);
        held.setItemMeta(meta);
        // getItemInMainHand() moze zwrocic kopie zaleznie od wersji API - ustawiamy jawnie.
        attacker.getInventory().setItemInMainHand(held);
    }
}
