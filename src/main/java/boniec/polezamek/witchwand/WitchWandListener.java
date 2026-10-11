package boniec.dev.polezamek.witchwand;

import boniec.dev.polezamek.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WitchWandListener implements Listener {

    private final Main plugin;
    private final WitchWandManager witchWandManager;
    private final HookManager hookManager;

    private static final double LEVITATION_KB_RESISTANCE = 0.7;
    private final NamespacedKey levitationKbKey;
    private final Set<UUID> levitationKbTracked = ConcurrentHashMap.newKeySet();

    public WitchWandListener(Main plugin, WitchWandManager witchWandManager, HookManager hookManager) {
        this.plugin = plugin;
        this.witchWandManager = witchWandManager;
        this.hookManager = hookManager;
        this.levitationKbKey = new NamespacedKey(plugin.getHost(), "witchwand_levitation_kb");
    }

    // ===================== Rozdzka Wiedzmy =====================

    @EventHandler
    public void onWandInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!witchWandManager.isWandItem(tool)) return;

        Action action = event.getAction();
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!leftClick && !rightClick) return;

        event.setCancelled(true);
        if (leftClick) return;

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        if (witchWandManager.isOnCooldown(player)) {
            long remaining = witchWandManager.getCooldownRemainingSeconds(player);
            player.sendMessage(color(witchWandManager.getMsgOnCooldown(remaining)));
            return;
        }

        witchWandManager.setCooldownNow(player);
        player.setCooldown(tool.getType(), witchWandManager.getCooldownSeconds() * 20);
        castRight(player);
    }

    private void castLeft(Player player) {
        int durationTicks = witchWandManager.getLeftDurationSeconds() * 20;
        int amplifier = witchWandManager.getLeftAmplifier();
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, durationTicks, amplifier));
        applyLevitationKnockbackResistance(player);
    }

    private void castRight(Player player) {
        int radius = witchWandManager.getRadius();
        int durationTicks = witchWandManager.getRightDurationSeconds() * 20;
        int amplifier = witchWandManager.getRightAmplifier();

        Location center = player.getLocation();

        for (Entity nearby : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Player target)) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, durationTicks, amplifier));
            applyLevitationKnockbackResistance(target);
        }
    }

    // Odpornosc na odrzut trzyma sie tak dlugo, jak gracz ma lewitacje (sprawdzane co 5 tickow).
    private void applyLevitationKnockbackResistance(Player target) {
        AttributeInstance attr = target.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (attr == null) return;
        removeLevitationKnockbackResistance(target);
        attr.addModifier(new AttributeModifier(levitationKbKey, LEVITATION_KB_RESISTANCE,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));

        UUID uuid = target.getUniqueId();
        if (!levitationKbTracked.add(uuid)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || !p.hasPotionEffect(PotionEffectType.LEVITATION)) {
                    if (p != null) removeLevitationKnockbackResistance(p);
                    levitationKbTracked.remove(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(plugin.getHost(), 5L, 5L);
    }

    private void removeLevitationKnockbackResistance(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (attr == null) return;
        for (AttributeModifier modifier : new ArrayList<>(attr.getModifiers())) {
            if (levitationKbKey.equals(modifier.getKey())) {
                attr.removeModifier(modifier);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeLevitationKnockbackResistance(event.getPlayer());
        levitationKbTracked.remove(event.getPlayer().getUniqueId());
    }

    // Modyfikator zapisuje sie w danych gracza - czyscimy go po crashu / restarcie w trakcie lewitacji.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        removeLevitationKnockbackResistance(event.getPlayer());
    }

    // ===================== Hak =====================
    //
    // Cala logika haka (rzut w dowolne miejsce, w tym w powietrze,
    // oraz przyciaganie na PPM) jest obslugiwana przez HookManager.
    // Ten listener kiedys mial wlasna, konkurencyjna implementacje
    // ograniczona do event.getState() == IN_GROUND / CAUGHT_* / FAILED_ATTEMPT,
    // przez co hak wiszacy w powietrzu (bez zaczepienia o blok) w ogole
    // nie generowal kolejnego PlayerFishEvent i przyciaganie nie dzialalo.
    // Usunieto duplikat, zeby nie kolidowal z HookManager.

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
