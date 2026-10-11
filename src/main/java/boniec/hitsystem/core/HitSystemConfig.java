package boniec.hitsystem.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Wczytuje i udostępnia wartości z hitsystem.yml. Cały balans (wartości
 * obrażeń per set, różnica setów, limity itd.) mieszka tutaj - dzięki temu
 * balans zmienia się samym configiem + /hitsystem reload, bez przebudowy pluginu.
 */
public final class HitSystemConfig {

    public static final class SetDamage {
        public final double normalHearts;
        public final double criticalHearts;

        public SetDamage(double normalHearts, double criticalHearts) {
            this.normalHearts = normalHearts;
            this.criticalHearts = criticalHearts;
        }
    }

    private final Plugin plugin;
    private final File file;
    private FileConfiguration cfg;

    private boolean enabled;
    private boolean debugEnabled;
    private boolean pvpMeleeOnly;

    private String noSetMode;
    private int fallbackAttackerSet;
    private int fallbackDefenderSet;

    private final Map<Integer, SetDamage> setDamage = new HashMap<>();

    private double defaultBaseNormal;
    private double defaultBaseCritical;
    private double defaultIncrementNormal;
    private double defaultIncrementCritical;

    private boolean differenceEnabled;
    private double differencePerLevel;
    private double differenceMaxBonus;
    private double differenceMinBonus;

    private boolean useSwordSetInFormula;
    private String swordMismatchMode;

    private boolean armorMixEnabled;
    private double armorMixShare;

    private double minDamageHearts;
    private double maxDamageHearts;

    private boolean requireVanillaCriticalConditions;

    private boolean extraProtectionScalingEnabled;
    private double extraProtectionReductionPerLevel;
    private double extraProtectionMaxReduction;

    private boolean elytraAsChestplateEnabled;
    /** Zmiana obrazen gracza w elytrze w PROCENTACH (50 = +50%, -10 = 10% mniej). */
    private double elytraDamagePercent;
    /** Stale dodatkowe serca gdy obronca ma elytre zamiast klaty (0.3 -> 2.5 staje sie 2.8). */
    private double elytraExtraHearts;

    private boolean missingArmorPenaltyEnabled;
    /** Dodatkowe obrazenia w PROCENTACH za kazdy brakujacy element zbroi. */
    private double missingArmorExtraDamagePercent;

    private double globalHitsMultiplier;

    private boolean customIframesEnabled;

    private boolean antiSpamEnabled;
    private long antiSpamMinIntervalMs;
    private String antiSpamBlockMode;
    private double antiSpamPenaltyPercent;

    private boolean attackStrengthRequirementEnabled;
    private double attackStrengthMinPercent;

    private boolean gangUpPenaltyEnabled;
    private double gangUpPenaltyPercent;
    private long gangUpWindowMs;

    private boolean combatLockEnabled;
    private long combatLockTimeoutMs;

    public HitSystemConfig(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hitsystem.yml");
    }

    /** Wczytuje (lub przeładowuje) plik hitsystem.yml z dysku. */
    public void load() {
        if (!file.exists()) {
            plugin.saveResource("hitsystem.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);

        // Uzupełnij ewentualne brakujące klucze wartościami domyślnymi z jara,
        // żeby aktualizacje pluginu nie psuły starych configów graczy.
        try {
            java.io.InputStream defStream = plugin.getResource("hitsystem.yml");
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
                cfg.setDefaults(defaults);
                cfg.options().copyDefaults(true);

                // Plik na serwerze (plugins/<plugin>/hitsystem.yml) NIE jest nadpisywany
                // przez jar (saveResource(..., false)), wiec klucze dodane w nowszej wersji
                // (np. global-hits.multiplier) po cichu wracaly do wartosci domyslnej i
                // edycja hitsystem.yml w projekcie nic nie zmieniala na serwerze. Dopisujemy
                // brakujace klucze do pliku na dysku, zeby admin widzial je i mogl edytowac.
                java.util.List<String> missing = new java.util.ArrayList<>();
                for (String key : defaults.getKeys(true)) {
                    if (defaults.isConfigurationSection(key) || key.startsWith("set-damage.")) {
                        continue;
                    }
                    if (!cfg.contains(key, true)) {
                        missing.add(key);
                    }
                }
                if (!missing.isEmpty()) {
                    save();
                    plugin.getLogger().info("[HitSystem] Dopisano brakujace klucze do hitsystem.yml: " + String.join(", ", missing));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[HitSystem] Nie udało się scalić domyślnych wartości hitsystem.yml.", ex);
        }

        enabled = cfg.getBoolean("damage-system.enabled", true);
        debugEnabled = cfg.getBoolean("debug.enabled", false);
        pvpMeleeOnly = cfg.getBoolean("scope.pvp-melee-only", true);

        noSetMode = cfg.getString("no-set-behavior.mode", "fallback-set");
        fallbackAttackerSet = cfg.getInt("no-set-behavior.fallback-attacker-set", 0);
        fallbackDefenderSet = cfg.getInt("no-set-behavior.fallback-defender-set", 0);

        setDamage.clear();
        ConfigurationSection setSection = cfg.getConfigurationSection("set-damage");
        if (setSection != null) {
            for (String key : setSection.getKeys(false)) {
                try {
                    int setId = Integer.parseInt(key);
                    double normal = setSection.getDouble(key + ".normal", 1.0);
                    double critical = setSection.getDouble(key + ".critical", normal + 0.2);
                    setDamage.put(setId, new SetDamage(normal, critical));
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("[HitSystem] Nieprawidłowy numer seta w set-damage: '" + key + "' (pomijam).");
                }
            }
        }

        defaultBaseNormal = cfg.getDouble("default-scaling.base-normal", 1.0);
        defaultBaseCritical = cfg.getDouble("default-scaling.base-critical", 1.2);
        defaultIncrementNormal = cfg.getDouble("default-scaling.per-set-increment-normal", 0.15);
        defaultIncrementCritical = cfg.getDouble("default-scaling.per-set-increment-critical", 0.15);

        differenceEnabled = cfg.getBoolean("set-difference.enabled", true);
        differencePerLevel = cfg.getDouble("set-difference.per-level", 0.05);
        differenceMaxBonus = cfg.getDouble("set-difference.max-bonus", 0.5);
        differenceMinBonus = cfg.getDouble("set-difference.min-bonus", -0.5);

        useSwordSetInFormula = cfg.getBoolean("sword.use-sword-set-in-formula", true);
        swordMismatchMode = cfg.getString("sword.mismatch-mode", "lower");
        // armor-mix (zbroja z mieszanych setow) dziala DOMYSLNIE, bez zadnego wpisu w hitsystem.yml
        // (enabled = true, share = 0.3). Wpis jest opcjonalny - mozna go dopisac recznie, zeby
        // zmienic wartosci albo wylaczyc funkcje:
        //   armor-mix:
        //     enabled: false
        //     share: 0.3
        // Plugin sam NIC nie dopisuje do pliku (klucza nie ma w hitsystem.yml z jara).
        armorMixEnabled = cfg.getBoolean("armor-mix.enabled", true);
        armorMixShare = cfg.getDouble("armor-mix.share", 0.3);

        minDamageHearts = cfg.getDouble("limits.min-damage-hearts", 0.5);
        maxDamageHearts = cfg.getDouble("limits.max-damage-hearts", 10.0);

        requireVanillaCriticalConditions = cfg.getBoolean("critical.require-vanilla-critical-conditions", true);

        extraProtectionScalingEnabled = cfg.getBoolean("extra-vanilla-protection-scaling.enabled", false);
        extraProtectionReductionPerLevel = cfg.getDouble("extra-vanilla-protection-scaling.reduction-per-protection-level", 0.02);
        extraProtectionMaxReduction = cfg.getDouble("extra-vanilla-protection-scaling.max-reduction", 0.30);

        elytraAsChestplateEnabled = cfg.getBoolean("elytra-as-chestplate.enabled", true);
        elytraDamagePercent = cfg.getDouble("elytra-as-chestplate.damage-percent", 0.0);
        elytraExtraHearts = cfg.getDouble("elytra-as-chestplate.extra-hearts", 0.21);

        missingArmorPenaltyEnabled = cfg.getBoolean("missing-armor-penalty.enabled", true);
        missingArmorExtraDamagePercent = cfg.getDouble("missing-armor-penalty.extra-damage-percent", 25.0);

        globalHitsMultiplier = cfg.getDouble("global-hits.multiplier", 1.0);
        if (globalHitsMultiplier < 0.0) {
            plugin.getLogger().warning("[HitSystem] global-hits.multiplier < 0 (" + globalHitsMultiplier + ") - ustawiam 0.");
            globalHitsMultiplier = 0.0;
        }

        customIframesEnabled = cfg.getBoolean("hit-registration.custom-iframes", true);

        antiSpamEnabled = cfg.getBoolean("anti-spam.enabled", true);
        antiSpamMinIntervalMs = cfg.getLong("anti-spam.min-interval-ms", 500);
        antiSpamBlockMode = cfg.getString("anti-spam.block-mode", "cancel");
        antiSpamPenaltyPercent = cfg.getDouble("anti-spam.penalty-percent", 50);

        attackStrengthRequirementEnabled = cfg.getBoolean("attack-strength-requirement.enabled", true);
        attackStrengthMinPercent = cfg.getDouble("attack-strength-requirement.min-percent", 55.0);

        gangUpPenaltyEnabled = cfg.getBoolean("gang-up-penalty.enabled", true);
        gangUpPenaltyPercent = cfg.getDouble("gang-up-penalty.penalty-percent", 15);
        gangUpWindowMs = cfg.getLong("gang-up-penalty.window-ms", 3000);

        // Combat-lock: ofiara "nalezy" do ostatniego atakujacego, ktory ja trafil w ciagu
        // ostatnich timeout-ms. Inny gracz probujacy trafic te sama ofiare w tym oknie
        // dostaje CALKOWICIE anulowany hit (0 dmg) - zapobiega to stackowaniu obrazen
        // od wielu graczy naraz (np. 5 osob bijacych 1 gracza jednoczesnie).
        combatLockEnabled = cfg.getBoolean("combat-lock.enabled", true);
        combatLockTimeoutMs = cfg.getLong("combat-lock.timeout-ms", 750);

        plugin.getLogger().info("[HitSystem] Wczytano " + file.getPath()
                + " | global-hits.multiplier=" + globalHitsMultiplier
                + " | custom-iframes=" + customIframesEnabled
                + " | anti-spam=" + antiSpamEnabled + " (min-interval=" + antiSpamMinIntervalMs + "ms, mode=" + antiSpamBlockMode + ")"
                + " | attack-strength-requirement=" + attackStrengthRequirementEnabled + " (min=" + attackStrengthMinPercent + "%)"
                + " | gang-up-penalty=" + gangUpPenaltyEnabled + " (" + gangUpPenaltyPercent + "%, window=" + gangUpWindowMs + "ms)"
                + " | combat-lock=" + combatLockEnabled + " (timeout=" + combatLockTimeoutMs + "ms)"
                + " | zdefiniowanych setow=" + setDamage.size());
    }

    /** Zapisuje bieżący FileConfiguration z powrotem na dysk (rzadko potrzebne, głównie load() wystarcza). */
    public void save() {
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "[HitSystem] Nie udało się zapisać hitsystem.yml.", ex);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /** Ustawia debug wyłącznie w pamięci (runtime) - patrz HitSystemManager#setDebugRuntime. */
    public void setDebugRuntime(boolean on) {
        this.debugEnabled = on;
    }

    public boolean isPvpMeleeOnly() {
        return pvpMeleeOnly;
    }

    public String getNoSetMode() {
        return noSetMode;
    }

    public int getFallbackAttackerSet() {
        return fallbackAttackerSet;
    }

    public int getFallbackDefenderSet() {
        return fallbackDefenderSet;
    }

    /**
     * Zwraca skonfigurowane (lub liniowo ekstrapolowane, jeśli brak wpisu)
     * obrażenia bazowe dla danego numeru seta.
     */
    public SetDamage getSetDamage(int setId) {
        SetDamage exact = setDamage.get(setId);
        if (exact != null) {
            return exact;
        }
        if (setDamage.isEmpty()) {
            double normal = defaultBaseNormal + setId * defaultIncrementNormal;
            double critical = defaultBaseCritical + setId * defaultIncrementCritical;
            return new SetDamage(normal, critical);
        }
        int highestDefined = setDamage.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (setId > highestDefined) {
            SetDamage highest = setDamage.get(highestDefined);
            int diff = setId - highestDefined;
            double normal = highest.normalHearts + diff * defaultIncrementNormal;
            double critical = highest.criticalHearts + diff * defaultIncrementCritical;
            return new SetDamage(normal, critical);
        }
        int lowestDefined = setDamage.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
        if (setId < lowestDefined) {
            SetDamage lowest = setDamage.get(lowestDefined);
            int diff = lowestDefined - setId;
            double normal = Math.max(0.0, lowest.normalHearts - diff * defaultIncrementNormal);
            double critical = Math.max(0.0, lowest.criticalHearts - diff * defaultIncrementCritical);
            return new SetDamage(normal, critical);
        }
        // setId mieści się w zakresie zdefiniowanych kluczy, ale sam nie jest zdefiniowany
        // (dziura w configu) - użyj najbliższego niższego jako przybliżenia.
        int nearestBelow = setDamage.keySet().stream()
                .filter(k -> k <= setId)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(lowestDefined);
        return setDamage.get(nearestBelow);
    }

    public boolean isDifferenceEnabled() {
        return differenceEnabled;
    }

    public double getDifferencePerLevel() {
        return differencePerLevel;
    }

    public double getDifferenceMaxBonus() {
        return differenceMaxBonus;
    }

    public double getDifferenceMinBonus() {
        return differenceMinBonus;
    }

    public boolean isUseSwordSetInFormula() {
        return useSwordSetInFormula;
    }

    /** Czy zbroja z roznych setow ma byc liczona wg armor-mix (zamiast traktowac jak brak seta). */
    public boolean isArmorMixEnabled() {
        return armorMixEnabled;
    }

    /** Ulamek nadwyzki ponad najslabsza czescia zbroi, ktory sie liczy (0.0 - 1.0). */
    public double getArmorMixShare() {
        return armorMixShare;
    }

    public String getSwordMismatchMode() {
        return swordMismatchMode;
    }

    public double getMinDamageHearts() {
        return minDamageHearts;
    }

    public double getMaxDamageHearts() {
        return maxDamageHearts;
    }

    public boolean isRequireVanillaCriticalConditions() {
        return requireVanillaCriticalConditions;
    }

    public boolean isExtraProtectionScalingEnabled() {
        return extraProtectionScalingEnabled;
    }

    public double getExtraProtectionReductionPerLevel() {
        return extraProtectionReductionPerLevel;
    }

    public double getExtraProtectionMaxReduction() {
        return extraProtectionMaxReduction;
    }

    public boolean isElytraAsChestplateEnabled() {
        return elytraAsChestplateEnabled;
    }

    /** Zmiana obrazen w procentach (np. 20 = +20%). */
    public double getElytraDamagePercent() {
        return elytraDamagePercent;
    }

    public double getElytraExtraHearts() {
        return elytraExtraHearts;
    }

    /** Ustawia procent elytry w pamieci i zapisuje do hitsystem.yml (bez restartu, bez /hitsystem reload). */
    public void setElytraDamagePercent(double percent) {
        this.elytraDamagePercent = percent;
        cfg.set("elytra-as-chestplate.damage-percent", percent);
        save();
    }

    public boolean isMissingArmorPenaltyEnabled() {
        return missingArmorPenaltyEnabled;
    }

    public double getMissingArmorExtraDamagePercent() {
        return missingArmorExtraDamagePercent;
    }

    /** Ustawia procent kary za brakujaca czesc zbroi w pamieci i zapisuje do hitsystem.yml. */
    public void setMissingArmorExtraDamagePercent(double percent) {
        this.missingArmorExtraDamagePercent = percent;
        cfg.set("missing-armor-penalty.extra-damage-percent", percent);
        save();
    }

    public double getGlobalHitsMultiplier() {
        return globalHitsMultiplier;
    }

    /** Czy rejestracje hitow (i-frames) liczymy wlasnymi obrazeniami z systemu setow (hit-registration.custom-iframes). */
    public boolean isCustomIframesEnabled() {
        return customIframesEnabled;
    }

    public boolean isAntiSpamEnabled() {
        return antiSpamEnabled;
    }

    public long getAntiSpamMinIntervalMs() {
        return antiSpamMinIntervalMs;
    }

    public String getAntiSpamBlockMode() {
        return antiSpamBlockMode;
    }

    public double getAntiSpamPenaltyPercent() {
        return antiSpamPenaltyPercent;
    }

    public boolean isAttackStrengthRequirementEnabled() {
        return attackStrengthRequirementEnabled;
    }

    /** Minimalne naladowanie ataku w PROCENTACH (0-100), np. 55.0. */
    public double getAttackStrengthMinPercent() {
        return attackStrengthMinPercent;
    }

    public boolean isGangUpPenaltyEnabled() {
        return gangUpPenaltyEnabled;
    }

    public double getGangUpPenaltyPercent() {
        return gangUpPenaltyPercent;
    }

    public long getGangUpWindowMs() {
        return gangUpWindowMs;
    }

    public boolean isCombatLockEnabled() {
        return combatLockEnabled;
    }

    /** Ile ms bez trafienia ofiary traci sie "lock" nad nia (kolejny atakujacy moze go przejac). */
    public long getCombatLockTimeoutMs() {
        return combatLockTimeoutMs;
    }
}
