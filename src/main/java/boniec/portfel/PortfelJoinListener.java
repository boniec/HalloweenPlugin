package boniec.portfel;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PortfelJoinListener implements Listener {

    private final PortfelManager manager;

    public PortfelJoinListener(PortfelManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.ustawNazwe(e.getPlayer());
    }
}
