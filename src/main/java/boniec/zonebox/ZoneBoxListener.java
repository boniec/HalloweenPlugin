package boniec.zonebox;

import boniec.dev.inventory.Main;
import boniec.generatory.Generator;
import boniec.generatory.GeneratorManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public final class ZoneBoxListener implements Listener {

    private final ZoneBoxManager mgr;

    public ZoneBoxListener(ZoneBoxManager mgr) {
        this.mgr = mgr;
    }

    private Generator generatorAt(org.bukkit.Location loc) {
        Main main = Main.getInstance();
        if (main == null) return null;
        GeneratorManager gens = main.getGeneratorManager();
        return gens == null ? null : gens.getGeneratorAt(loc);
    }

    private void zastosuj(Player p, Material m, org.bukkit.Location loc, Cancellable e, boolean stawianie) {
        if (p.hasPermission("azonebox.bypass")) return;
        if (!stawianie && boniec.welna.WelnaDropManager.toWelna(m)
                && !boniec.welna.WelnaDropManager.maNozyce(p)) {
            e.setCancelled(true);
            return;
        }
        if (!stawianie) {
            Generator gen = generatorAt(loc);
            if (gen != null) {
                e.setCancelled(!gen.canMine(p));
                return;
            }
        }
        if (mgr.wWylaczonym(loc)) {
            if (stawianie) e.setCancelled(true);
            return;
        }
        if (mgr.wStrefieGeneratorow(loc)) {
            e.setCancelled(true);
            return;
        }
        if (!mgr.wStrefie(loc)) return;
        if (mgr.dozwolony(m)) {
            e.setCancelled(false);
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent e) {
        zastosuj(e.getPlayer(), e.getBlockPlaced().getType(), e.getBlock().getLocation(), e, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucket(PlayerBucketEmptyEvent e) {
        zastosuj(e.getPlayer(), e.getBucket(), e.getBlock().getLocation(), e, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        zastosuj(e.getPlayer(), b.getType(), b.getLocation(), e, false);
        if (e.isCancelled()) e.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(BlockDamageEvent e) {
        Block b = e.getBlock();
        zastosuj(e.getPlayer(), b.getType(), b.getLocation(), e, false);
    }
}
