package boniec.code.core.metin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class MetinMob {

    private final JavaPlugin plugin;
    private final MetinCrystal parentMetin;

    private LivingEntity entity;
    private boolean alive;

    public MetinMob(JavaPlugin plugin, MetinCrystal parentMetin) {
        this.plugin = plugin;
        this.parentMetin = parentMetin;
        this.alive = false;
    }

    public void spawn(Location center, int hp) {
        if (center == null || center.getWorld() == null) {
            plugin.getLogger().warning("[Metin] Nie można zespawnować strażnika - lokalizacja jest pusta.");
            return;
        }

        Location spawnLoc = findSafeSpawnLocation(center);

        entity = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);

        if (entity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);
        }
        entity.setHealth(Math.min(hp, entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));

        if (entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35D);
        }

        if (entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(6.0D);
        }

        entity.setCustomName(createName(hp));
        entity.setCustomNameVisible(true);

        entity.setMetadata("metin_mob", new FixedMetadataValue(plugin, parentMetin.getName()));
        entity.setMetadata("metin_mob_id", new FixedMetadataValue(plugin, entity.getUniqueId().toString()));

        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.clear();

            equipment.setHelmetDropChance(0.0F);
            equipment.setChestplateDropChance(0.0F);
            equipment.setLeggingsDropChance(0.0F);
            equipment.setBootsDropChance(0.0F);
            equipment.setItemInMainHandDropChance(0.0F);
            equipment.setItemInOffHandDropChance(0.0F);

            equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        }

        alive = true;

        spawnLoc.getWorld().spawnParticle(
                Particle.FLAME,
                spawnLoc.clone().add(0, 1, 0),
                30,
                0.5D,
                1.0D,
                0.5D,
                0.05D
        );

        spawnLoc.getWorld().playSound(
                spawnLoc,
                Sound.ENTITY_ZOMBIE_AMBIENT,
                2.0F,
                0.5F
        );
    }

    private Location findSafeSpawnLocation(Location center) {
        World world = center.getWorld();

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = 3.0D + Math.random() * 4.0D;

            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);

            int startY = center.getBlockY();

            for (int y = startY + 6; y >= startY - 10; y--) {
                Location groundLoc = new Location(world, x, y, z);

                Material ground = groundLoc.getBlock().getType();
                Material body = groundLoc.clone().add(0, 1, 0).getBlock().getType();
                Material head = groundLoc.clone().add(0, 2, 0).getBlock().getType();

                if (!ground.isSolid()) {
                    continue;
                }

                if (!body.isAir() || !head.isAir()) {
                    continue;
                }

                return groundLoc.clone().add(0.5D, 1.0D, 0.5D);
            }
        }

        return new Location(
                world,
                center.getX() + 3.5D,
                center.getY(),
                center.getZ() + 0.5D
        );
    }

    private String createName(int hp) {
        return ChatColor.DARK_RED + "⚔ "
                + ChatColor.RED + "Strażnik Metina"
                + ChatColor.DARK_RED + " ⚔ "
                + ChatColor.YELLOW + "[" + hp + "❤]";
    }

    public void updateNameTag() {
        if (entity == null || entity.isDead()) {
            alive = false;
            return;
        }

        int currentHp = (int) Math.ceil(entity.getHealth());
        entity.setCustomName(createName(currentHp));
    }

    public void remove() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
        alive = false;
    }

    public boolean isAlive() {
        if (entity == null || entity.isDead()) {
            alive = false;
        }
        return alive;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public MetinCrystal getParentMetin() {
        return parentMetin;
    }
}