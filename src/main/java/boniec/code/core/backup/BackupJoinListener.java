package boniec.code.core.backup;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class BackupJoinListener implements Listener {
    private final BackupManager manager;

    public BackupJoinListener(BackupManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.handlePlayerJoin(event.getPlayer());
    }
}
