package boniec.poziomy;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PoziomyListener implements Listener {

    private final PoziomyManager manager;

    public PoziomyListener(PoziomyManager manager, boolean worldGuard) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player gracz = event.getPlayer();
        Profil profil = manager.getProfil(gracz);
        int przed = profil.getPoziom();
        if (manager.awansujJesliMozna(profil) > 0) {
            manager.oznaczDoZapisu();
            if (profil.getPoziom() > przed) {
                manager.powiadomAwans(gracz, profil.getPoziom());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.oznaczDoZapisu();
        manager.zapiszDane(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent event) {
        Player zabojca = event.getEntity().getKiller();
        if (zabojca != null && !zabojca.equals(event.getEntity())) {
            manager.dodajExp(zabojca, manager.getExpZabojstwo(), true);
        }
    }
}
