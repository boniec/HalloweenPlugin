package boniec.dev.polezamek.eventitems;

import boniec.dev.polezamek.eventitems.SauronGearManager.BootsCfg;
import boniec.dev.polezamek.eventitems.SauronGearManager.DragonBoots;
import boniec.dev.polezamek.eventitems.SauronGearManager.DragonHead;
import boniec.dev.polezamek.eventitems.SauronGearManager.HeadCfg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cykliczne zadanie (co 10 tickow):
 * <ol>
 *   <li>Miecz Saurona w rece: particle lawy wokol wlasciciela, dzwiek Withera co X sekund,
 *       osoby w promieniu (domyslnie 10 kratek) trafiaja do "aury".</li>
 *   <li>Glowy smoka na glowie: Fire Resistance (kazda) + Resistance / Strength wg rodzaju.</li>
 *   <li>Aura: poziom Sily osob w promieniu miecza spada o 1 (Sila 2 -> 1, Sila 1 -> brak).
 *       Po wyjsciu z zasiegu poziom wraca, z zachowaniem pozostalego czasu.</li>
 * </ol>
 */
public class SauronGearTask extends BukkitRunnable {

    public static final int PERIOD_TICKS = 10;

    private static final class StrengthState {
        final int originalAmp;
        /** Poziom po obnizeniu; -1 = efekt zostal calkiem zdjety. */
        final int reducedAmp;
        /** Moment wygasniecia efektu (ms) albo Long.MAX_VALUE dla efektu nieskonczonego. */
        final long expiryMs;
        final boolean ambient;
        final boolean particles;
        final boolean icon;

        StrengthState(int originalAmp, int reducedAmp, long expiryMs, boolean ambient, boolean particles, boolean icon) {
            this.originalAmp = originalAmp;
            this.reducedAmp = reducedAmp;
            this.expiryMs = expiryMs;
            this.ambient = ambient;
            this.particles = particles;
            this.icon = icon;
        }
    }

    private final SauronGearManager mgr;
    private final Map<UUID, StrengthState> states = new HashMap<>();
    /** Poziom Sily, ktory glowa smoka sama nadala graczowi w tym przebiegu (juz uwzglednia aure). */
    private final Map<UUID, Integer> accounted = new HashMap<>();
    private final Set<UUID> aura = new HashSet<>();
    private int soundTimerTicks = 0;

    public SauronGearTask(SauronGearManager mgr) {
        this.mgr = mgr;
    }

    /** Wolane przy wyjsciu/smierci gracza - czyscimy zapamietany stan, zeby nic nie "wrocilo" po respawnie. */
    public void forget(UUID id) {
        states.remove(id);
        accounted.remove(id);
        aura.remove(id);
    }

    @Override
    public void run() {
        soundTimerTicks += PERIOD_TICKS;
        boolean playSound = false;
        if (soundTimerTicks >= mgr.getSoundIntervalSeconds() * 20) {
            soundTimerTicks = 0;
            playSound = true;
        }

        var online = Bukkit.getOnlinePlayers();
        aura.clear();

        // 1) Wlasciciele miecza Saurona
        double r = mgr.getRadius();
        double rSq = r * r;
        for (Player holder : online) {
            if (holder.isDead()) continue;
            if (!mgr.isSauronSword(holder.getInventory().getItemInMainHand())) continue;

            Location loc = holder.getLocation();
            if (loc.getWorld() == null) continue;

            loc.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0, 1.0, 0), 3, 0.4, 0.6, 0.4, 0);
            loc.getWorld().spawnParticle(Particle.DRIPPING_LAVA, loc.clone().add(0, 1.2, 0), 4, 0.4, 0.7, 0.4, 0);

            if (playSound) {
                loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 1.0f);
            }

            for (Entity e : holder.getNearbyEntities(r, r, r)) {
                if (!(e instanceof Player target)) continue;
                if (target.getUniqueId().equals(holder.getUniqueId())) continue;
                if (target.getGameMode() == GameMode.SPECTATOR) continue;
                if (target.getLocation().distanceSquared(loc) > rSq) continue;
                aura.add(target.getUniqueId());
            }
        }

        // 2) Glowy smoka
        for (Player p : online) {
            applyHeadEffects(p);
            applyBootsEffects(p);
        }

        // 3) Obnizanie / przywracanie Sily
        for (Player p : online) {
            handleStrength(p);
        }

        // Porzadki po graczach, ktorzy wyszli
        if (!states.isEmpty()) {
            states.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        }
    }

    // ===================== Glowy smoka =====================

    private void applyHeadEffects(Player p) {
        UUID id = p.getUniqueId();
        ItemStack helm = p.getInventory().getHelmet();
        DragonHead head = mgr.getHead(helm);
        if (head == null) {
            accounted.remove(id);
            return;
        }
        HeadCfg c = mgr.getHeadCfg(head);

        if (c.fireResistance) give(p, PotionEffectType.FIRE_RESISTANCE, 0);
        if (c.resistanceAmp >= 0) give(p, PotionEffectType.RESISTANCE, c.resistanceAmp);

        if (c.strengthAmp >= 0) {
            int desired = c.strengthAmp - (aura.contains(id) ? mgr.getStrengthReduction() : 0);
            accounted.put(id, desired);
            if (desired >= 0) give(p, PotionEffectType.STRENGTH, desired);
        } else {
            accounted.remove(id);
        }
    }

    // ===================== Buty smoka =====================

    private void applyBootsEffects(Player p) {
        DragonBoots b = mgr.getBoots(p.getInventory().getBoots());
        if (b == null) return;
        BootsCfg c = mgr.getBootsCfg(b);
        if (c.speedAmp >= 0) give(p, PotionEffectType.SPEED, c.speedAmp);
    }

    private void give(Player p, PotionEffectType type, int amp) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null) {
            // Nie nadpisujemy mocniejszego efektu ani dluzszego tego samego poziomu (np. z potiona).
            if (cur.getAmplifier() > amp) return;
            if (cur.getAmplifier() == amp && cur.getDuration() > 60) return;
        }
        p.addPotionEffect(new PotionEffect(type, 40, amp, true, false, true));
    }

    // ===================== Aura Saurona: Sila -1 =====================

    private void handleStrength(Player p) {
        UUID id = p.getUniqueId();
        boolean inAura = aura.contains(id);
        PotionEffect eff = p.getPotionEffect(PotionEffectType.STRENGTH);
        StrengthState st = states.get(id);
        long now = System.currentTimeMillis();

        if (inAura) {
            if (eff == null) {
                // Stan z reducedAmp == -1 zostaje (to my zdjelismy efekt); inny stan = efekt wygasl.
                if (st != null && st.reducedAmp >= 0) states.remove(id);
                return;
            }
            if (st != null && eff.getAmplifier() == st.reducedAmp) return; // juz obnizony
            Integer acc = accounted.get(id);
            if (acc != null && eff.getAmplifier() == acc) return;           // glowa smoka juz to uwzglednila

            int dur = eff.getDuration();
            int newAmp = eff.getAmplifier() - mgr.getStrengthReduction();
            long expiry = dur < 0 ? Long.MAX_VALUE : now + dur * 50L;
            states.put(id, new StrengthState(eff.getAmplifier(), newAmp, expiry,
                    eff.isAmbient(), eff.hasParticles(), eff.hasIcon()));

            p.removePotionEffect(PotionEffectType.STRENGTH);
            if (newAmp >= 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, newAmp,
                        eff.isAmbient(), eff.hasParticles(), eff.hasIcon()));
            }
            return;
        }

        // Poza aura - przywracamy poziom, ale tylko jesli efekt nadal jest "nasz".
        if (st == null) return;
        states.remove(id);

        boolean ours = (eff == null && st.reducedAmp < 0)
                || (eff != null && eff.getAmplifier() == st.reducedAmp);
        if (!ours) return;

        int remaining;
        if (st.expiryMs == Long.MAX_VALUE) {
            remaining = -1;
        } else {
            long ticks = (st.expiryMs - now) / 50L;
            if (ticks <= 0) return;
            remaining = (int) ticks;
        }
        if (eff != null) p.removePotionEffect(PotionEffectType.STRENGTH);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, remaining, st.originalAmp,
                st.ambient, st.particles, st.icon));
    }
}
