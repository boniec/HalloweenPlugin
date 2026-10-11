package boniec.coinflip;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class CoinflipListener implements Listener {

    private final CoinflipManager manager;

    public CoinflipListener(CoinflipManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.powiadomOczekujace(event.getPlayer());
    }
}
