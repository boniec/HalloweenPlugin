package boniec.code.core.metin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MetinManager implements Listener {

    private final JavaPlugin plugin;
    private final MetinConfig metinConfig;
    private final Map<String, MetinCrystal> metins;

    public MetinManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.metinConfig = new MetinConfig(plugin);
        this.metins = new HashMap<>();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        loadAndSpawnMetins();
    }

    private void loadAndSpawnMetins() {
        Map<String, Location> spawns = metinConfig.getSpawnLocations();

        for (Map.Entry<String, Location> entry : spawns.entrySet()) {
            MetinCrystal metin = new MetinCrystal(plugin, entry.getKey(), entry.getValue(), metinConfig);
            metins.put(entry.getKey(), metin);

            Bukkit.getScheduler().runTaskLater(plugin, metin::spawn, 60L);
        }

        plugin.getLogger().info("[Metin] Załadowano " + spawns.size() + " metinów.");
    }

    public void createMetin(String name, Location location) {
        if (metins.containsKey(name)) {
            metins.get(name).forceRemove();
        }

        metinConfig.saveSpawnLocation(name, location);
        MetinCrystal metin = new MetinCrystal(plugin, name, location, metinConfig);
        metins.put(name, metin);
        metin.spawn();
    }

    public void removeMetin(String name) {
        MetinCrystal metin = metins.get(name);
        if (metin != null) {
            metin.forceRemove();
            metins.remove(name);
            metinConfig.removeSpawnLocation(name);
        }
    }

    public MetinCrystal getMetin(String name) {
        return metins.get(name);
    }

    public Map<String, MetinCrystal> getAllMetins() {
        return Collections.unmodifiableMap(metins);
    }

    public MetinConfig getMetinConfig() {
        return metinConfig;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // --- OBRAŻENIA W END CRYSTAL (METIN) ---
        if (event.getEntity() instanceof EnderCrystal) {
            EnderCrystal crystal = (EnderCrystal) event.getEntity();

            if (crystal.hasMetadata("metin_crystal")) {
                event.setCancelled(true);

                String metinName = crystal.getMetadata("metin_crystal").get(0).asString();
                MetinCrystal metin = metins.get(metinName);

                if (metin == null || !metin.isAlive()) {
                    return;
                }

                Player player = null;
                if (event.getDamager() instanceof Player) {
                    player = (Player) event.getDamager();
                } else if (event.getDamager() instanceof Projectile) {
                    Projectile proj = (Projectile) event.getDamager();
                    if (proj.getShooter() instanceof Player) {
                        player = (Player) proj.getShooter();
                    }
                }

                if (player != null) {
                    int damage = calculatePlayerDamage(player);
                    metin.damage(player, damage);
                }
                return;
            }
        }

        // --- STAŁE OBRAŻENIA (1-2 HP) W MOBA STRAŻNIKA METINA ---
        if (event.getEntity() instanceof LivingEntity && event.getEntity().hasMetadata("metin_mob")) {
            LivingEntity mob = (LivingEntity) event.getEntity();

            // Dokładnie od 1 do 2 HP obrażeń (losowo 1 lub 2)
            double hitPointsDamage = 1.0D + (Math.random() > 0.5D ? 1.0D : 0.0D);
            event.setDamage(hitPointsDamage);

            // Aktualizacja tagu nazwy moba z opóźnieniem 1 ticka
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isDead()) {
                    String metinName = mob.getMetadata("metin_mob").get(0).asString();
                    MetinCrystal metin = metins.get(metinName);

                    if (metin != null) {
                        for (MetinMob metinMob : metin.getActiveMobs()) {
                            if (metinMob.getEntity() != null
                                    && metinMob.getEntity().getUniqueId().equals(mob.getUniqueId())) {
                                metinMob.updateNameTag();
                                break;
                            }
                        }
                    }
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof EnderCrystal
                && event.getEntity().hasMetadata("metin_crystal")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity.hasMetadata("metin_mob")) {
            event.getDrops().clear();
            event.setDroppedExp(15);

            String metinName = entity.getMetadata("metin_mob").get(0).asString();
            MetinCrystal metin = metins.get(metinName);

            if (metin != null) {
                for (MetinMob mob : new ArrayList<>(metin.getActiveMobs())) {
                    if (mob.getEntity() != null
                            && mob.getEntity().getUniqueId().equals(entity.getUniqueId())) {
                        metin.onMobKilled(mob);

                        try {
                            Particle particle = Particle.valueOf("SOUL_FIRE_FLAME");
                            entity.getWorld().spawnParticle(
                                    particle,
                                    entity.getLocation().add(0, 1, 0),
                                    20,
                                    0.3D,
                                    0.5D,
                                    0.3D,
                                    0.05D
                            );
                        } catch (IllegalArgumentException ignored) {
                            entity.getWorld().spawnParticle(
                                    Particle.FLAME,
                                    entity.getLocation().add(0, 1, 0),
                                    20,
                                    0.3D,
                                    0.5D,
                                    0.3D,
                                    0.05D
                            );
                        }
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().hasMetadata("metin_nametag")
                || event.getRightClicked().hasMetadata("metin_hptag")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorStandDamage(EntityDamageEvent event) {
        if (event.getEntity().hasMetadata("metin_nametag")
                || event.getEntity().hasMetadata("metin_hptag")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMetinMobBurn(EntityCombustEvent event) {
        if (event.getEntity().hasMetadata("metin_mob")) {
            event.setCancelled(true);
        }
    }

    private int calculatePlayerDamage(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        int baseDamage = 1;

        if (mainHand != null) {
            Material type = mainHand.getType();
            String name = type.name();

            if (name.contains("NETHERITE_SWORD")) baseDamage = 8;
            else if (name.contains("DIAMOND_SWORD")) baseDamage = 7;
            else if (name.contains("IRON_SWORD")) baseDamage = 6;
            else if (name.contains("STONE_SWORD")) baseDamage = 5;
            else if (name.contains("GOLDEN_SWORD")) baseDamage = 4;
            else if (name.contains("WOODEN_SWORD")) baseDamage = 4;
            else if (name.contains("NETHERITE_AXE")) baseDamage = 10;
            else if (name.contains("DIAMOND_AXE")) baseDamage = 9;
            else if (name.contains("IRON_AXE")) baseDamage = 9;
            else if (name.contains("STONE_AXE")) baseDamage = 9;
            else if (name.contains("GOLDEN_AXE")) baseDamage = 7;
            else if (name.contains("WOODEN_AXE")) baseDamage = 7;
            else if (name.contains("TRIDENT")) baseDamage = 9;
            else baseDamage = 1;
        }

        baseDamage += (int) (Math.random() * 3);
        return Math.max(1, baseDamage);
    }

    public void shutdown() {
        for (MetinCrystal metin : metins.values()) {
            metin.forceRemove();
        }
        metins.clear();
    }
}