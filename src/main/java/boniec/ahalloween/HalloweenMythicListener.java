package boniec.ahalloween;

import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Po smierci Krola Dyni (Dulahan) MythicMobs odpala death-summon ze skrzynia — tu to blokujemy.
 */
public final class HalloweenMythicListener implements Listener {

    private final AHalloweenManager manager;
    private final JavaPlugin plugin;

    public HalloweenMythicListener(AHalloweenManager manager, JavaPlugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMythicSpawn(MythicMobSpawnEvent event) {
        if (!manager.blokujSkrzynieKrola()) return;
        if (event.getMobType() == null || event.getEntity() == null) return;
        String typ = event.getMobType().getInternalName();
        if (!manager.mythicUsuwanyPoSmierciKrola(typ)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Entity entity = event.getEntity();
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        });
    }
}
