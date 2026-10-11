package boniec.hitsystem.core;

/**
 * Czysta logika liczenia finalnego obrażenia - bez zależności od Bukkit.
 * Wejście: numer seta atakującego (zbroja), numer seta miecza atakującego,
 * numer seta obrońcy (zbroja), czy hit jest critical. Wyjście: obrażenia
 * w SERCACH (mnożone przez 2.0 dają Minecraftowe punkty obrażeń HP).
 */
public final class DamageCalculator {

    private final HitSystemConfig config;

    public DamageCalculator(HitSystemConfig config) {
        this.config = config;
    }

    public static final class Result {
        public final double finalHearts;
        public final double finalDamagePoints;
        public final int effectiveAttackerSet;
        /** Efektywny set obroncy zaokraglony do calkowitej (dla logow / kompatybilnosci). */
        public final int effectiveDefenderSet;
        /** Dokladny (ulamkowy) efektywny set obroncy uzyty w formule. */
        public final double effectiveDefenderSetExact;
        public final double baseHearts;
        public final double differenceBonusHearts;
        public final double extraProtectionReductionHearts;
        public final double heartsBeforeGlobalMultiplier;

        Result(double finalHearts, int effectiveAttackerSet, double effectiveDefenderSetExact,
               double baseHearts, double differenceBonusHearts, double extraProtectionReductionHearts,
               double heartsBeforeGlobalMultiplier) {
            this.finalHearts = finalHearts;
            this.finalDamagePoints = finalHearts * 2.0;
            this.effectiveAttackerSet = effectiveAttackerSet;
            this.effectiveDefenderSetExact = effectiveDefenderSetExact;
            this.effectiveDefenderSet = (int) Math.round(effectiveDefenderSetExact);
            this.baseHearts = baseHearts;
            this.differenceBonusHearts = differenceBonusHearts;
            this.extraProtectionReductionHearts = extraProtectionReductionHearts;
            this.heartsBeforeGlobalMultiplier = heartsBeforeGlobalMultiplier;
        }
    }

    /**
     * @param attackerArmorSet set-id pełnej zbroi atakującego, -1 jeśli brak pełnego seta
     * @param attackerSwordSet set-id miecza atakującego, -1 jeśli brak tagu
     * @param defenderArmorSet set-id pełnej zbroi obrońcy, -1 jeśli brak pełnego seta
     * @param critical         czy hit spełnia warunki critical hita
     * @param defenderTotalProtectionLevels suma poziomów Protection na założonej zbroi obrońcy
     *                                       (używane tylko gdy extra-vanilla-protection-scaling.enabled = true)
     */
    public Result calculate(int attackerArmorSet, int attackerSwordSet, int defenderArmorSet,
                             boolean critical, int defenderTotalProtectionLevels) {
        return calculate(attackerArmorSet, attackerSwordSet, (double) defenderArmorSet,
                critical, defenderTotalProtectionLevels);
    }

    /**
     * To samo co {@link #calculate(int, int, int, boolean, int)}, ale set zbroi obroncy moze byc
     * ulamkowy (efektywny set z mieszanej zbroi, patrz armor-mix w hitsystem.yml, np. 12.75).
     * Wartosc ujemna oznacza brak seta (fallback z configu), tak jak przy int.
     */
    public Result calculate(int attackerArmorSet, int attackerSwordSet, double defenderArmorSet,
                             boolean critical, int defenderTotalProtectionLevels) {

        int effectiveAttackerSet = resolveEffectiveAttackerSet(attackerArmorSet, attackerSwordSet);
        if (effectiveAttackerSet < 0) {
            effectiveAttackerSet = config.getFallbackAttackerSet();
        }

        double effectiveDefenderSet = defenderArmorSet;
        if (effectiveDefenderSet < 0) {
            effectiveDefenderSet = config.getFallbackDefenderSet();
        }

        HitSystemConfig.SetDamage setDamage = config.getSetDamage(effectiveAttackerSet);
        double baseHearts = critical ? setDamage.criticalHearts : setDamage.normalHearts;

        double differenceBonus = 0.0;
        if (config.isDifferenceEnabled()) {
            double diff = effectiveAttackerSet - effectiveDefenderSet;
            differenceBonus = diff * config.getDifferencePerLevel();
            differenceBonus = clamp(differenceBonus, config.getDifferenceMinBonus(), config.getDifferenceMaxBonus());
        }

        double hearts = baseHearts + differenceBonus;

        double extraReduction = 0.0;
        if (config.isExtraProtectionScalingEnabled() && defenderTotalProtectionLevels > 0) {
            double reductionFraction = Math.min(
                    defenderTotalProtectionLevels * config.getExtraProtectionReductionPerLevel(),
                    config.getExtraProtectionMaxReduction()
            );
            extraReduction = hearts * reductionFraction;
            hearts -= extraReduction;
        }

        hearts = clamp(hearts, config.getMinDamageHearts(), config.getMaxDamageHearts());

        // --- Global Hits Multiplier: mnożnik na SAMYM KOŃCU formuły, PO
        // limitach min/max-damage-hearts. Zastosowany po clampie (nie przed),
        // żeby zawsze realnie skalował finalny wynik - jeśli byłby PRZED
        // clampem, min-damage-hearts mógłby "podbić" go z powrotem do góry
        // przy niskich wartościach multipliera, psując cel tej opcji (łatwe,
        // pewne zmniejszenie/zwiększenie WSZYSTKICH hitów jedną liczbą w
        // configu, bez przeliczania całej tabeli set-damage).
        double heartsBeforeGlobalMultiplier = hearts;
        hearts = hearts * config.getGlobalHitsMultiplier();

        return new Result(hearts, effectiveAttackerSet, effectiveDefenderSet, baseHearts, differenceBonus,
                extraReduction, heartsBeforeGlobalMultiplier);
    }

    /**
     * Ustala, którego numeru seta użyć jako "seta atakującego" w formule,
     * zależnie od sword.mismatch-mode:
     *  - "lower"       -> niższy z pary (armor, sword); brak jednego z nich traktowany jako brak ograniczenia
     *  - "sword-only"  -> zawsze set miecza (armor ignorowany)
     *  - "armor-only"  -> zawsze set zbroi (miecz ignorowany, choć use-sword-set-in-formula=false robi to samo)
     */
    private int resolveEffectiveAttackerSet(int armorSet, int swordSet) {
        if (!config.isUseSwordSetInFormula()) {
            return armorSet;
        }

        String mode = config.getSwordMismatchMode();
        if ("sword-only".equalsIgnoreCase(mode)) {
            return swordSet;
        }
        if ("armor-only".equalsIgnoreCase(mode)) {
            return armorSet;
        }

        // "lower" (domyślne): bierzemy mniejszy z dwóch dostępnych numerów,
        // żeby słaby miecz + mocna zbroja (lub odwrotnie) nie dawały obrażeń mocniejszego z nich.
        if (armorSet < 0 && swordSet < 0) {
            return -1;
        }
        if (armorSet < 0) {
            return swordSet;
        }
        if (swordSet < 0) {
            return armorSet;
        }
        return Math.min(armorSet, swordSet);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
