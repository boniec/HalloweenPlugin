package boniec.hitsystem.core;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Własne mnożniki efektów, bo HitSystem zeruje vanillowe MAGIC/ARMOR
 * (Siła / Odporność / Luck / Zła Wróżba nie działają same z siebie).
 */
public final class PotionDamageEffects {

    private static final double SILA_NA_POZIOM = 0.15;
    /** Moc Vadera (Zła wróżba): +10% zadawanych obrażeń na poziom. */
    private static final double ZLA_WROZBA_NA_POZIOM = 0.10;
    /** Moc Yody (Luck): −15% otrzymywanych obrażeń na poziom. */
    private static final double SZCZESCIE_NA_POZIOM = 0.15;
    private static final double ODPORNOSC_NA_POZIOM = 0.075;

    private PotionDamageEffects() {}

    public static double multiplier(LivingEntity attacker, LivingEntity victim) {
        double m = 1.0;
        if (attacker != null) {
            m *= 1.0 + SILA_NA_POZIOM * poziom(attacker, PotionEffectType.STRENGTH);
            m *= 1.0 + ZLA_WROZBA_NA_POZIOM * poziom(attacker, PotionEffectType.BAD_OMEN);
        }
        if (victim != null) {
            m *= 1.0 - SZCZESCIE_NA_POZIOM * poziom(victim, PotionEffectType.LUCK);
            m *= 1.0 - ODPORNOSC_NA_POZIOM * poziom(victim, PotionEffectType.RESISTANCE);
        }
        return Math.max(0.05, m);
    }

    private static int poziom(LivingEntity entity, PotionEffectType type) {
        PotionEffect effect = entity.getPotionEffect(type);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }
}
