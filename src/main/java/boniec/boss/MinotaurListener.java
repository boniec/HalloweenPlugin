package boniec.boss;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MinotaurListener implements Listener {

    private final MinotaurManager manager;

    public MinotaurListener(MinotaurManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossAttacksPlayer(EntityDamageByEntityEvent event) {
        if (!manager.isAlive()) return;
        LivingEntity bossEntity = manager.getAktywny().getEntity();

        if (event.getDamager().equals(bossEntity) && event.getEntity() instanceof Player) {
            event.setDamage(manager.getObrazeniaStaleHp());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHitsBossFixedDamage(EntityDamageByEntityEvent event) {
        if (!manager.isAlive()) return;
        if (!event.getEntity().equals(manager.getAktywny().getEntity())) return;
        if (resolveAttacker(event) == null) return;

        double stale = manager.getObrazeniaOdGraczaHp();
        if (stale <= 0) return;

        event.setDamage(stale);
        for (EntityDamageEvent.DamageModifier mod : EntityDamageEvent.DamageModifier.values()) {
            if (mod == EntityDamageEvent.DamageModifier.BASE) continue;
            if (event.isApplicable(mod)) {
                event.setDamage(mod, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttacksBoss(EntityDamageByEntityEvent event) {
        if (!manager.isAlive()) return;
        LivingEntity bossEntity = manager.getAktywny().getEntity();
        if (!event.getEntity().equals(bossEntity)) return;

        Player atakujacy = resolveAttacker(event);
        if (atakujacy == null) return;

        manager.zarejestrujObrazenia(atakujacy, event.getFinalDamage());
        manager.sprobujNagrodeZaUderzenie(atakujacy);
        manager.sprobujDropZaTrafienie(atakujacy);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeathClearVanillaDrops(EntityDeathEvent event) {
        if (!manager.czyToBoss(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!manager.czyToBoss(event.getEntity())) return;

        Player ostatniCios = event.getEntity().getKiller();
        if (ostatniCios == null && manager.getAktywny().getOstatniAtakujacy() != null) {
            ostatniCios = org.bukkit.Bukkit.getPlayer(manager.getAktywny().getOstatniAtakujacy());
        }
        manager.obsluzSmierc(ostatniCios);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (!manager.isAlive()) return;
        if (event.getEntity().equals(manager.getAktywny().getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.dodajGraczaDoBossbarow(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (manager.isAlive()) {
            manager.getAktywny().getBossBar().removePlayer(event.getPlayer());
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                return (Player) proj.getShooter();
            }
        }
        return null;
    }
}