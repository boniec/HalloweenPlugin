package boniec.spawnpvp;

import boniec.antylogout.AntyLogoutManager;
import boniec.code.core.WorldGuardHook;
import boniec.dev.inventory.Main;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

/**
 * Blokuje wchodzenie do regionu WorldGuard "spawnpvp" WYŁĄCZNIE graczom,
 * którzy są aktualnie oznaczeni systemem antylogout (czyli są "w walce").
 * Gracz bez antylogoutu wchodzi normalnie, bez żadnej ingerencji. Kto ma
 * antylogout i mimo to spróbuje wejść (zwykłym ruchem albo
 * teleportem/enderpearlem itd.), zostaje zablokowany i odepchnięty do tyłu
 * (w stronę, z której przyszedł), zamiast po prostu "zamrożony" na granicy
 * regionu.
 * <p>
 * WAŻNE: ta klasa NIE odwołuje się bezpośrednio do klas WorldGuard - cała
 * integracja jest w {@link WorldGuardHook}, którego wolno wywoływać tylko gdy
 * WorldGuard jest faktycznie zainstalowany (patrz {@link Main#isWorldGuardEnabled()}).
 * Rejestracja tego listenera w Main musi więc być warunkowa (if worldGuardEnabled)
 * i musi nastąpić PO utworzeniu {@link AntyLogoutManager}.
 */
public final class SpawnPvpGuardListener implements Listener {

    /** Id regionu WorldGuard blokowanego dla wchodzących graczy "w walce". */
    public static final String REGION_ID = "spawnpvp";

    /** Siła odepchnięcia gracza z powrotem, poza region. */
    private static final double KNOCKBACK_STRENGTH = 0.6;

    private final AntyLogoutManager antyLogoutManager;

    public SpawnPvpGuardListener(AntyLogoutManager antyLogoutManager) {
        this.antyLogoutManager = antyLogoutManager;
    }

    /**
     * Blokujemy tylko graczy aktualnie oznaczonych antylogoutem (w walce).
     * KOLEJNOSC MA ZNACZENIE: najpierw tanie sprawdzenie isInCombat()
     * (odczyt z HashMap) - w kazdym ticku ruchu odsiewa to praktycznie
     * wszystkich graczy na serwerze (bo w walce jest zawsze tylko waska
     * garstka), zanim dojdzie do sprawdzenia bypass - rowniez odczyt z HashMap
     * (uprawnienie zakeszowane w CombatData przy tagowaniu), bez kosztownego
     * wywolania LuckPerms hasPermission() na kazdy ruch.
     */
    private boolean shouldBlock(Player player) {
        if (!antyLogoutManager.isInCombat(player.getUniqueId())) return false;
        return !antyLogoutManager.hasBypass(player.getUniqueId());
    }

    /**
     * Ruch PLAYER_MOVE odpala się bardzo często (każdy krok/obrót głowy), więc
     * sprawdzamy WYŁĄCZNIE gdy gracz faktycznie zmienił blok (x/y/z) - obrót
     * kamery bez ruchu nie generuje niepotrzebnie zapytań do WorldGuard.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!shouldBlock(player)) return;

        boolean isInside = WorldGuardHook.isInRegion(to, REGION_ID);
        if (!isInside) return;

        // Gracz wchodzi do regionu. Jesli byl juz w srodku (np. wpuszczony przez admina/spawn) - nie ingerujemy.
        boolean wasInside = WorldGuardHook.isInRegion(from, REGION_ID);
        if (wasInside) return;

        // Gracz "w walce" wchodzi do regionu (z zewnatrz albo bedac juz "na krawedzi") - blokujemy i odpychamy.
        event.setCancelled(true);
        pushBack(player, from, to);
    }

    /**
     * Teleporty (enderpearl, /tp, pluginowe warpy...) nie generuja
     * PlayerMoveEvent - trzeba je zablokowac osobno, inaczej region byłby
     * chroniony wyłącznie przed zwykłym chodzeniem.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        if (!shouldBlock(player)) return;

        if (WorldGuardHook.isInRegion(to, REGION_ID)) {
            event.setCancelled(true);
        }
    }

    /** Odpycha gracza od granicy regionu z powrotem w strone, z ktorej przyszedl. */
    private void pushBack(Player player, Location from, Location to) {
        Vector direction = from.toVector().subtract(to.toVector());
        if (direction.lengthSquared() < 1.0E-6) {
            // Brak wyraznego kierunku (np. teleport w miejscu) - odpychamy
            // wprost przeciwnie do kierunku patrzenia gracza.
            direction = player.getLocation().getDirection().multiply(-1);
        }
        direction.setY(0);
        if (direction.lengthSquared() > 1.0E-6) {
            direction.normalize();
        }
        direction.multiply(KNOCKBACK_STRENGTH);
        direction.setY(0.25);

        Location bezpieczna = from.clone();
        bezpieczna.setDirection(to.toVector().subtract(from.toVector()));
        player.teleport(bezpieczna);
        player.setVelocity(direction);
    }
}
