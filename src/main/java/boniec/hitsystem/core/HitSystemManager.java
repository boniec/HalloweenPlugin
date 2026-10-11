package boniec.hitsystem.core;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Fasada systemu hitów: spina config, identyfikację setów i kalkulator,
 * udostępniając jedną metodę do wywołania z listenera.
 */
public final class HitSystemManager {

    private final Plugin plugin;
    private final HitSystemConfig config;
    private final SetIdentifier setIdentifier;
    private final DamageCalculator calculator;

    // Cache flagi "czy WorldGuard jest zainstalowany i wlaczony", wyliczanej RAZ przy
    // load()/reload zamiast robic Bukkit.getPluginManager().getPlugin("WorldGuard")
    // (lookup po nazwie w HashMapie pluginow) przy KAZDYM pojedynczym hicie PvP -
    // ten lookup pojawial sie w profilerze (SimplePluginManager.getPlugin()) jako
    // zbedny koszt ponoszony na kazde trafienie mieczem netherytowym. Stan pluginow
    // na serwerze praktycznie nigdy nie zmienia sie w trakcie dzialania (poza
    // /reload calego serwera, ktorego tak czy inaczej nikt tu nie wspiera), wiec
    // odswiezanie przy kazdym /hitsystem reload jest w zupelnosci wystarczajace -
    // dokladnie ten sam wzorzec, co Main#worldGuardEnabled liczone raz w onEnable().
    private boolean worldGuardEnabled;

    public HitSystemManager(Plugin plugin) {
        this.plugin = plugin;
        this.config = new HitSystemConfig(plugin);
        this.setIdentifier = new SetIdentifier();
        this.calculator = new DamageCalculator(config);
    }

    public void load() {
        config.load();
        org.bukkit.plugin.Plugin wg = org.bukkit.Bukkit.getPluginManager().getPlugin("WorldGuard");
        worldGuardEnabled = wg != null && wg.isEnabled();
    }

    /** true jesli WorldGuard jest zainstalowany i wlaczony - patrz load() (cache, nie liczone na hit). */
    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    /** Plugin właściciel (potrzebny np. do budowania NamespacedKey w komendach). */
    public Plugin getOwningPlugin() {
        return plugin;
    }

    public HitSystemConfig getConfig() {
        return config;
    }

    /**
     * Przełącza debug WYŁĄCZNIE w pamięci (runtime), bez trwałego zapisu do
     * hitsystem.yml - wygodne do szybkiego włączenia logów bez edycji pliku.
     * Po /hitsystem reload wraca wartość z pliku.
     */
    public void setDebugRuntime(boolean on) {
        config.setDebugRuntime(on);
    }

    public SetIdentifier getSetIdentifier() {
        return setIdentifier;
    }

    public DamageCalculator getCalculator() {
        return calculator;
    }

    /**
     * Liczy finalne obrażenia (w punktach HP Minecrafta) dla ataku
     * attacker -> defender. Nie modyfikuje żadnego stanu - czyste obliczenie.
     */
    public DamageCalculator.Result computeDamage(Player attacker, Player defender, boolean critical) {
        int attackerArmorSet = setIdentifier.getFullArmorSetId(attacker);
        int attackerSwordSet = setIdentifier.getHeldSwordSetId(attacker);
        // Obronca: gdy armor-mix wlaczony, mieszana zbroja (rozne poziomy Protection na czesciach)
        // dostaje ulamkowy efektywny set (najslabsza czesc + ulamek nadwyzki) zamiast -1 -> fallback 0.
        // Gdy wylaczony albo zbroja jest jednolita, wynik jest taki sam jak dawniej.
        double defenderArmorSet = config.isArmorMixEnabled()
                ? setIdentifier.getEffectiveArmorSet(defender, config.getArmorMixShare())
                : setIdentifier.getFullArmorSetId(defender);

        // Protection (i Sharpness, ktory nigdy nie byl tu odczytywany) NIE maja
        // wplywac na obrazenia PvP - na sztywno w kodzie, niezaleznie od
        // ustawienia extra-vanilla-protection-scaling w configu, zeby nie bylo
        // furtki przez zmiane pliku .yml na serwerze.
        int defenderProtectionLevels = 0;

        return calculator.calculate(attackerArmorSet, attackerSwordSet, defenderArmorSet, critical, defenderProtectionLevels);
    }

    private int sumProtectionLevels(Player player) {
        EntityEquipment eq = player.getEquipment();
        if (eq == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack piece : new ItemStack[]{eq.getHelmet(), eq.getChestplate(), eq.getLeggings(), eq.getBoots()}) {
            if (piece != null && piece.getEnchantments().containsKey(Enchantment.PROTECTION)) {
                total += piece.getEnchantmentLevel(Enchantment.PROTECTION);
            }
        }
        return total;
    }

    public void debug(String message) {
        if (config.isDebugEnabled()) {
            plugin.getLogger().info("[HitSystem] " + message);
        }
    }
}
