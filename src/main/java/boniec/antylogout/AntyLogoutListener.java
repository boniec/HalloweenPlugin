package boniec.antylogout;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public final class AntyLogoutListener implements Listener {

    private final AntyLogoutManager manager;
    private final AntyLogoutConfig config;

    public AntyLogoutListener(AntyLogoutManager manager, AntyLogoutConfig config) {
        this.manager = manager;
        this.config = config;
    }

    /**
     * Tagujemy OBIE strony realnego trafienia PvP (gracz bije gracza).
     * MONITOR + ignoreCancelled = true: patrzymy tylko na hity, ktore
     * faktycznie przejda (nic wczesniej ich nie anulowalo - np. WorldGuard
     * pvp=deny, /kit ochrona itd.) i nie ingerujemy w sam damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpHit(EntityDamageByEntityEvent event) {
        if (!config.isEnabled()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            return;
        }
        if (event.getFinalDamage() <= 0.0) return;
        if (!(event.getEntity() instanceof Player defender)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.getUniqueId().equals(defender.getUniqueId())) return;

        manager.tag(defender, attacker);
        manager.tag(attacker, defender);
    }

    /** Zdejmujemy tag przy normalnej smierci (bossbar nie ma juz sensu wisiec dalej). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        manager.untag(event.getEntity().getUniqueId());
    }

    /**
     * Priorytet HIGH (celowo NIE MONITOR): handleQuit() moze tu sztucznie
     * zabic gracza (player.damage(...) -> PlayerDeathEvent), zeby smierc na
     * quit w trakcie walki liczyla sie identycznie jak normalne zabicie -
     * w tym do statystyk/rankingu ELO (PvpListener#onSmierc w module klanow).
     * Musi to zajsc PRZED sprzataniem fallbackowej mapy "ostatni atakujacy"
     * w PvpListener#onWyjscie (tam priorytet MONITOR - patrz komentarz przy
     * tamtej metodzie), inaczej ranking nie ma juz skad odczytac zabojcy i
     * smierc na antylogoucie po cichu przestaje sie liczyc jak zwykle
     * zabicie, mimo ze wizualnie tak wyglada.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }

    /**
     * Blokuje wszystkie komendy oprocz listy z configu (domyslnie /msg /r
     * /helpop) dla graczy aktualnie oznaczonych jako "w walce".
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!config.isEnabled()) return;

        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;
        if (player.hasPermission("antylogout.bypass")) return;

        String message = event.getMessage();
        int spaceIdx = message.indexOf(' ');
        String label = (spaceIdx == -1 ? message : message.substring(0, spaceIdx)).substring(1);
        int slashIdx = label.indexOf(':');
        if (slashIdx != -1) {
            // wsparcie dla "/plugin:komenda"
            label = label.substring(slashIdx + 1);
        }
        label = label.toLowerCase(Locale.ROOT);

        if (config.isCommandAllowed(label)) return;

        event.setCancelled(true);
        player.sendMessage(config.getMsgKomendaZablokowana());
    }
}
