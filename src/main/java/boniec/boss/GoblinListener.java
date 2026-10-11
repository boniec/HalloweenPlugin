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

import java.util.concurrent.ThreadLocalRandom;

public class GoblinListener implements Listener {

    private final GoblinManager manager;

    public GoblinListener(GoblinManager manager) {
        this.manager = manager;
    }



    // ignoreCancelled = false (celowo): jesli inny plugin/handler o wczesniejszym
    // priorytecie juz anulowal ten event (np. anti-knockback, jakis inny system PvE),
    // wciaz chcemy WYMUSIC nasze stale obrazenia od bossa - inaczej Goblin potrafil
    // nie zadawac zadnego dmg, mimo obrazenia-stale-hp > 0 w configu. Priorytet
    // HIGHEST, zeby zadzialac jak najpozniej i miec ostatnie slowo w kwestii damage'u.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBossAttacksPlayer(EntityDamageByEntityEvent event) {
        if (!manager.isAlive()) return;
        LivingEntity bossEntity = manager.getAktywny().getEntity();

        if (event.getDamager().getUniqueId().equals(bossEntity.getUniqueId()) && event.getEntity() instanceof Player) {
            double stale = manager.getObrazeniaStaleHp();
            if (stale > 0) {
                event.setCancelled(false);
                event.setDamage(stale);
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHitsBossFixedDamage(EntityDamageByEntityEvent event) {
        if (!manager.isAlive()) return;
        if (!event.getEntity().equals(manager.getAktywny().getEntity())) return;
        if (resolveAttacker(event) == null) return;

        double stale = manager.getObrazeniaOdGraczaHp();
        if (stale > 0) {
            // Anulujemy CALY vanilla damage i odejmujemy HP reczne przez
            // manager.zadajStaleObrazeniaBossowi(...) na priorytecie MONITOR
            // (patrz onPlayerHitsBossFixedDamageApply). Dzieki temu zaden inny
            // plugin (np. MythicMobs modyfikujacy ten sam event) nie moze juz
            // nadpisac naszej wartosci obrazen - HP jest zdejmowane bezposrednio.
            event.setDamage(0.0);
            event.setCancelled(true);
            return;
        }

        // Brak stalej wartosci: skalujemy normalne obrazenia z broni gracza (REALNEMU
        // HP encji, max 1024 z powodu limitu Bukkita), tak zeby odpowiadaly obrazeniom
        // wobec docelowego hp-max z configu (np. 5000).
        double mnoznik = manager.getAktywny().getMnoznikObrazen();
        if (mnoznik > 0 && mnoznik < 1.0) {
            event.setDamage(event.getDamage() * mnoznik);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerHitsBossFixedDamageApply(EntityDamageByEntityEvent event) {
        // Wykonuje sie NIEZALEZNIE od tego, czy event zostal anulowany (nie
        // uzywamy ignoreCancelled=true), bo to wlasnie my go anulowalismy wyzej
        // zeby zablokowac vanilla damage, i teraz musimy sami zdjac HP oraz
        // zarejestrowac statystyki/nagrody, ktore normalnie zalatwilby
        // onPlayerAttacksBoss (ten nie odpali sie bo event jest cancelled).
        if (!manager.isAlive()) return;
        if (!event.getEntity().equals(manager.getAktywny().getEntity())) return;
        Player atakujacy = resolveAttacker(event);
        if (atakujacy == null) return;

        double stale = manager.getObrazeniaOdGraczaHp();
        if (stale <= 0) return;

        manager.zadajStaleObrazeniaBossowi(stale);
        manager.zarejestrujObrazenia(atakujacy, stale);
        manager.sprobujNagrodeZaUderzenie(atakujacy);
        manager.sprobujDropZaTrafienie(atakujacy);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttacksBoss(EntityDamageByEntityEvent event) {
        // ignoreCancelled=true: gdy obrazenia-od-gracza-hp > 0, ten event zostal
        // juz anulowany w onPlayerHitsBossFixedDamage, wiec ten handler sie NIE
        // wykona - statystyki/nagrody/HP zalatwia wtedy onPlayerHitsBossFixedDamageApply
        // powyzej. Ten handler obsluguje tylko przypadek normalnych (nie-stalych)
        // obrazen z broni gracza.
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
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}