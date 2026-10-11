package boniec.szaman.listener;

import boniec.szaman.SzamanPlugin;
import boniec.szaman.core.PerkManager;
import boniec.szaman.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Efekt UWIEZIENIA (Artefakt Uwiezienia): uwieziony gracz nie moze uzywac fajerwerk
 * (boost elytry / odpalanie rakiety). Ruch zostaje normalny.
 */
public class MovementListener implements Listener {

    private static final long KOMUNIKAT_CO_MS = 2000L;

    private final SzamanPlugin plugin;
    private final Map<UUID, Long> ostatniKomunikat = new ConcurrentHashMap<>();

    public MovementListener(SzamanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false)
    public void onFireworkUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREWORK_ROCKET) return;
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        if (!zablokujJesliUwieziony(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireworkLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Firework)) return;
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        if (!zablokujJesliUwieziony(player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ostatniKomunikat.remove(event.getPlayer().getUniqueId());
    }

    /** @return true gdy uzycie fajerwerki ma zostac zablokowane. */
    private boolean zablokujJesliUwieziony(Player player) {
        PerkManager pm = plugin.getPerkManager();
        if (!pm.istniejeAktywneUwiezienie()) return false;

        UUID uuid = player.getUniqueId();
        Long koniec = pm.getKoniecUwiezienia(uuid);
        if (koniec == null) return false;

        long teraz = System.currentTimeMillis();
        if (teraz >= koniec) {
            pm.usunUwiezienie(uuid);
            ostatniKomunikat.remove(uuid);
            return false;
        }

        Long ostatni = ostatniKomunikat.get(uuid);
        if (ostatni == null || teraz - ostatni >= KOMUNIKAT_CO_MS) {
            ostatniKomunikat.put(uuid, teraz);
            Msg.send(player, plugin.getSzamanConfig().getString("lang.confinement-cooldown",
                    "&a&lUwięzienie &8» &fNie możesz używać fajerwerk!"));
        }
        return true;
    }
}
