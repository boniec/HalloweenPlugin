package boniec.szaman.listener;

import boniec.szaman.SzamanPlugin;
import boniec.szaman.core.BonusCalculator;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.ArrayList;

/**
 * Artefakt Życia — dodatkowe serca z poziomu kupionego w /szaman
 * (data.health w szaman.yml, wartość w SERCACH, 1 serce = 2 HP)
 * plus bonus kostiumu/aury z {@link BonusCalculator#getDodatkoweZyciaHp}.
 */
public class ZyciaListener implements Listener {

    private static final String MOD_ID = "szaman_health";

    private final SzamanPlugin plugin;

    public ZyciaListener(SzamanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin.getHost(), () -> odswiezZdrowie(player, false));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin.getHost(), () -> odswiezZdrowie(player, true));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        odswiezZdrowie(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        usunModyfikator(event.getPlayer());
    }

    public static void odswiezZdrowie(Player player) {
        odswiezZdrowie(player, true);
    }

    public static void odswiezZdrowie(Player player, boolean uzupelnij) {
        if (player == null || !player.isOnline()) return;
        SzamanPlugin szaman = SzamanPlugin.get();
        if (szaman == null || szaman.getBonusCalculator() == null) return;

        AttributeInstance ai = instancjaMaxHealth(player);
        if (ai == null) return;

        usunModyfikator(ai);
        double extraHp = 0.0;
        if (PerkHealthWlaczony(szaman)) {
            extraHp = Math.max(0.0, szaman.getBonusCalculator().getDodatkoweZyciaHp(player.getUniqueId()));
        }
        if (extraHp > 0) {
            ai.addModifier(new AttributeModifier(
                    new NamespacedKey(szaman.getHost(), MOD_ID),
                    extraHp,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY));
        }

        double max = ai.getValue();
        double hp = player.getHealth();
        if (uzupelnij && extraHp > 0 && hp < max) {
            player.setHealth(Math.min(max, hp + extraHp));
        } else if (hp > max) {
            player.setHealth(max);
        }
    }

    private static boolean PerkHealthWlaczony(SzamanPlugin szaman) {
        return szaman.getSzamanConfig() == null || szaman.getSzamanConfig().getBoolean("health.enable", true);
    }

    private static void usunModyfikator(Player player) {
        AttributeInstance ai = instancjaMaxHealth(player);
        if (ai != null) usunModyfikator(ai);
    }

    private static void usunModyfikator(AttributeInstance ai) {
        for (AttributeModifier m : new ArrayList<>(ai.getModifiers())) {
            NamespacedKey key = m.getKey();
            if (key != null && MOD_ID.equals(key.getKey())) {
                ai.removeModifier(m);
            }
        }
    }

    static AttributeInstance instancjaMaxHealth(Player player) {
        try {
            AttributeInstance ai = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (ai != null) return ai;
        } catch (Throwable ignored) {
        }
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (attr == null) {
            attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
        }
        return attr == null ? null : player.getAttribute(attr);
    }
}
