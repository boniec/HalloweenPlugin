package boniec.zonebox;

import boniec.dev.inventory.Main;
import boniec.generatory.Generator;
import boniec.generatory.GeneratorManager;
import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.DelegateEvent;
import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;
import com.sk89q.worldguard.bukkit.event.block.PlaceBlockEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Mowi WorldGuardowi, zeby nie deny'owal (i nie spamowal "can't break")
 * kopania generatorow / dozwolonych blokow azonebox.
 */
public final class ZoneBoxWgListener implements Listener {

    private final ZoneBoxManager mgr;

    public ZoneBoxWgListener(ZoneBoxManager mgr) {
        this.mgr = mgr;
    }

    private Generator generatorAt(Location loc) {
        Main main = Main.getInstance();
        if (main == null) return null;
        GeneratorManager gens = main.getGeneratorManager();
        return gens == null ? null : gens.getGeneratorAt(loc);
    }

    private boolean wolnoKopac(Player p, Location loc, Material typ) {
        if (p != null && p.hasPermission("azonebox.bypass")) return true;
        if (boniec.welna.WelnaDropManager.toWelna(typ) && !boniec.welna.WelnaDropManager.maNozyce(p)) return false;
        Generator gen = generatorAt(loc);
        if (gen != null) {
            return gen.canMine(p);
        }
        if (mgr.wWylaczonym(loc)) return false;
        if (mgr.wStrefieGeneratorow(loc)) return false;
        if (mgr.wStrefie(loc)) return mgr.dozwolony(typ);
        return false;
    }

    private boolean wolnoStawiac(Player p, Location loc, Material typ) {
        if (p != null && p.hasPermission("azonebox.bypass")) return true;
        if (mgr.wWylaczonym(loc)) return false;
        if (mgr.wStrefieGeneratorow(loc)) return false;
        if (mgr.wStrefie(loc)) return mgr.dozwolony(typ);
        return false;
    }

    private static Player gracz(DelegateEvent e) {
        Cause cause = e.getCause();
        return cause == null ? null : cause.getFirstPlayer();
    }

    private static void pozwol(DelegateEvent e) {
        e.setAllowed(true);
        e.setSilent(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onWgBreak(BreakBlockEvent e) {
        Player p = gracz(e);
        for (Block b : e.getBlocks()) {
            if (b == null) continue;
            if (wolnoKopac(p, b.getLocation(), b.getType())) {
                pozwol(e);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onWgPlace(PlaceBlockEvent e) {
        Player p = gracz(e);
        for (Block b : e.getBlocks()) {
            if (b == null) continue;
            if (wolnoStawiac(p, b.getLocation(), b.getType())) {
                pozwol(e);
                return;
            }
        }
    }
}
