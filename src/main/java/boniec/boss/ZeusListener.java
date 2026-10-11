package boniec.boss;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.ThreadLocalRandom;

public class ZeusListener implements Listener {

    private final ZeusManager manager;

    public ZeusListener(ZeusManager manager) {
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

    // Ponizej jakiego naladowania ataku (Player#getAttackCooldown(), 0.0-1.0)
    // uderzenie jest uznawane za "spam" i CALKOWICIE ignorowane (0 obrazen,
    // brak nagrody za uderzenie, brak dropu, brak wpisu do rankingu obrazen).
    // 1.0 = wymagany pelny, w pelni naladowany atak (tak jak krytyk w vanilli).
    private static final double MIN_NALADOWANIE_ATAKU = 0.9;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHitsBossFixedDamage(EntityDamageByEntityEvent event) {
        if (!manager.isAlive()) return;
        if (!event.getEntity().equals(manager.getAktywny().getEntity())) return;
        Player atakujacy = resolveAttacker(event);
        if (atakujacy == null) return;

        // Wymagamy pelnego (naladowanego) ataku - szybkie spamowanie lewym
        // przyciskiem ma naladowanie < 1.0 i jest w calosci ignorowane, zeby
        // gracz nie mogl "spamic" hitami zamiast wyprowadzac pelne uderzenia.
        if (atakujacy.getAttackCooldown() < MIN_NALADOWANIE_ATAKU) {
            event.setDamage(0.0);
            event.setCancelled(true);
            return;
        }

        double visualDmg = ThreadLocalRandom.current().nextDouble(1.0, 3.0);
        double mnoznik = manager.getAktywny().getMnoznikObrazen();
        double realDmg = mnoznik > 0 ? visualDmg * mnoznik : visualDmg;

        event.setDamage(realDmg);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElytraGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (manager.czyMaZablokowanaElytre(player)) {
                event.setCancelled(true);
                player.setGliding(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireworkUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getItem() != null && event.getItem().getType() == Material.FIREWORK_ROCKET) {
            if (manager.czyMaZablokowanaElytre(player)) {
                event.setCancelled(true);
                player.sendMessage(org.bukkit.ChatColor.RED + "Twój sprzęt do latania został sparaliżowany przez porażenie Zeusa!");
            }
        }
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