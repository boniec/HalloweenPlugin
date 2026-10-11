package boniec.misje;

import boniec.dev.inventory.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MisjeListener implements Listener {

    private final MisjeManager manager;

    public MisjeListener(MisjeManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJedz(PlayerItemConsumeEvent event) {
        Material m = event.getItem().getType();
        if (m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE) {
            manager.dodajPostep(event.getPlayer(), TypMisji.ZJEDZ_ZLOTE_JABLKA, 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.zapiszGraczaTeraz(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent event) {
        Player zabojca = event.getEntity().getKiller();
        if (zabojca != null && !zabojca.equals(event.getEntity())) {
            manager.dodajPostep(zabojca, TypMisji.ZABIJ_GRACZY, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoss(EntityDeathEvent event) {
        Main plugin = manager.getPlugin();
        boolean boss = (plugin.getMinotaurManager() != null && plugin.getMinotaurManager().czyToBoss(event.getEntity()))
                || (plugin.getZeusManager() != null && plugin.getZeusManager().czyToBoss(event.getEntity()))
                || (plugin.getGoblinManager() != null && plugin.getGoblinManager().czyToBoss(event.getEntity()));
        if (!boss) return;
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            manager.dodajPostep(killer, TypMisji.ZABIJ_BOSSA, 1);
        }
    }
}
