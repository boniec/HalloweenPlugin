package boniec.code.core.metin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MetinCrystal {

    private final JavaPlugin plugin;
    private final String name;
    private final Location location;
    private final MetinConfig metinConfig;

    private final List<MetinMob> activeMobs = new ArrayList<>();
    private final Map<UUID, Integer> damageDealt = new HashMap<>();

    private int maxHp;
    private int currentHp;
    private int mobSpawnInterval;
    private int mobHp;
    private int hpSinceLastMobSpawn;

    private boolean alive;
    private boolean hpFrozen;

    private EnderCrystal crystal;
    private ArmorStand nameTag;
    private ArmorStand hpTag;
    private BossBar bossBar;

    private BukkitTask tickTask;
    private BukkitTask passiveDamageTask;
    private BukkitTask particleTask;

    public MetinCrystal(JavaPlugin plugin, String name, Location location, MetinConfig metinConfig) {
        this.plugin = plugin;
        this.name = name;
        this.location = location.clone();
        this.metinConfig = metinConfig;

        this.maxHp = metinConfig.getMetinMaxHp(name);
        this.currentHp = maxHp;
        this.mobSpawnInterval = metinConfig.getMobSpawnInterval(name);
        this.mobHp = metinConfig.getMobHp(name);

        this.alive = false;
        this.hpFrozen = false;
        this.hpSinceLastMobSpawn = 0;
    }

    public void spawn() {
        if (alive) {
            return;
        }

        if (location.getWorld() == null) {
            plugin.getLogger().warning("[Metin] Nie można zespawnować Metina " + name + " - świat nie istnieje.");
            return;
        }

        currentHp = maxHp;
        hpFrozen = false;
        hpSinceLastMobSpawn = 0;
        damageDealt.clear();
        activeMobs.clear();

        crystal = location.getWorld().spawn(location, EnderCrystal.class);
        crystal.setShowingBottom(true);
        crystal.setInvulnerable(true);
        crystal.setGravity(false);
        crystal.setBeamTarget(null);
        crystal.setMetadata("metin_crystal", new FixedMetadataValue(plugin, name));

        nameTag = location.getWorld().spawn(location.clone().add(0, 2.7D, 0), ArmorStand.class);
        nameTag.setVisible(false);
        nameTag.setMarker(true);
        nameTag.setGravity(false);
        nameTag.setInvulnerable(true);
        nameTag.setCustomNameVisible(true);
        nameTag.setCustomName(ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE + "METIN " +
                ChatColor.WHITE + name + ChatColor.DARK_PURPLE + " ✦");
        nameTag.setMetadata("metin_nametag", new FixedMetadataValue(plugin, name));

        hpTag = location.getWorld().spawn(location.clone().add(0, 2.25D, 0), ArmorStand.class);
        hpTag.setVisible(false);
        hpTag.setMarker(true);
        hpTag.setGravity(false);
        hpTag.setInvulnerable(true);
        hpTag.setCustomNameVisible(true);
        hpTag.setMetadata("metin_hptag", new FixedMetadataValue(plugin, name));

        bossBar = Bukkit.createBossBar(
                ChatColor.DARK_PURPLE + "✦ METIN ✦",
                BarColor.PURPLE,
                BarStyle.SEGMENTED_10
        );

        alive = true;

        updateHpTag();
        updateBossBar();

        startTickTask();
        startPassiveDamageTask();
        startParticleTask();

        location.getWorld().strikeLightningEffect(location);
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0F, 1.0F);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE
                + "Metin " + ChatColor.WHITE + name
                + ChatColor.LIGHT_PURPLE + " pojawił się na mapie!"
                + ChatColor.DARK_PURPLE + " ✦");
    }

    public void damage(Player player, int amount) {
        if (!alive || player == null || amount <= 0) {
            return;
        }

        cleanDeadMobs();

        if (!activeMobs.isEmpty()) {
            hpFrozen = true;

            player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.YELLOW
                    + "Najpierw zabij strażników Metina!");

            spawnSafeParticle(
                    location.clone().add(0, 1.5D, 0),
                    5,
                    0.3D,
                    0.3D,
                    0.3D,
                    0.0D,
                    "VILLAGER_ANGRY",
                    "ANGRY_VILLAGER"
            );
            return;
        }

        hpFrozen = false;

        int realDamage = Math.min(amount, currentHp);
        currentHp -= realDamage;
        hpSinceLastMobSpawn += realDamage;

        damageDealt.merge(player.getUniqueId(), realDamage, Integer::sum);

        if (crystal != null && !crystal.isDead()) {
            spawnSafeParticle(
                    crystal.getLocation().clone().add(0, 1, 0),
                    15,
                    0.3D,
                    0.5D,
                    0.3D,
                    0.1D,
                    "CRIT"
            );
        }

        location.getWorld().playSound(location, Sound.BLOCK_ANVIL_HIT, 0.5F, 1.5F);

        updateHpTag();
        updateBossBar();

        if (currentHp <= 0) {
            destroy(player);
            return;
        }

        if (mobSpawnInterval > 0 && hpSinceLastMobSpawn >= mobSpawnInterval) {
            spawnGuardMobs();
            hpSinceLastMobSpawn = 0;
        }

        rollDrops(player);
    }

    private void spawnGuardMobs() {
        if (!alive || location.getWorld() == null) {
            return;
        }

        cleanDeadMobs();

        if (!activeMobs.isEmpty()) {
            hpFrozen = true;
            return;
        }

        int mobCount = 2 + randomInt(0, 2);

        for (int i = 0; i < mobCount; i++) {
            MetinMob mob = new MetinMob(plugin, this);
            mob.spawn(location, mobHp);

            if (mob.getEntity() != null) {
                activeMobs.add(mob);
            }
        }

        if (activeMobs.isEmpty()) {
            plugin.getLogger().warning("[Metin] Nie udało się zespawnować strażników dla Metina: " + name);
            return;
        }

        hpFrozen = true;
        updateHpTag();
        updateBossBar();

        for (Player player : bossBar.getPlayers()) {
            player.sendMessage(ChatColor.DARK_RED + "⚔ " + ChatColor.RED
                    + "Pojawili się strażnicy Metina! Zabij ich!");
        }

        location.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.0F, 1.5F);

        spawnSafeParticle(
                location.clone().add(0, 1.0D, 0),
                50,
                1.0D,
                1.0D,
                1.0D,
                0.05D,
                "SMOKE_LARGE",
                "LARGE_SMOKE"
        );
    }

    public void onMobKilled(MetinMob mob) {
        activeMobs.remove(mob);
        cleanDeadMobs();

        if (activeMobs.isEmpty()) {
            hpFrozen = false;
            updateHpTag();
            updateBossBar();

            for (Player player : bossBar.getPlayers()) {
                player.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.YELLOW
                        + "Wszyscy strażnicy zostali pokonani. Możesz dalej niszczyć Metin!");
            }

            location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.5F);
        }
    }

    private void cleanDeadMobs() {
        activeMobs.removeIf(mob -> mob == null || !mob.isAlive());
    }

    private void rollDrops(Player player) {
        for (MetinDrop drop : metinConfig.getDrops(name)) {
            if (Math.random() <= 0.01D) {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop.createItemStack());

                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }

                player.sendMessage(ChatColor.GOLD + "★ " + ChatColor.YELLOW
                        + "Drop z Metina: " + ChatColor.WHITE + drop.toString());
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
            }
        }
    }

    private void destroy(Player killer) {
        alive = false;

        for (MetinMob mob : new ArrayList<>(activeMobs)) {
            mob.remove();
        }
        activeMobs.clear();

        location.getWorld().strikeLightningEffect(location);

        spawnSafeParticle(
                location.clone().add(0, 1, 0),
                100,
                1.0D,
                2.0D,
                1.0D,
                0.2D,
                "TOTEM",
                "TOTEM_OF_UNDYING"
        );

        spawnSafeParticle(
                location.clone().add(0, 1, 0),
                80,
                1.0D,
                2.0D,
                1.0D,
                0.1D,
                "END_ROD"
        );

        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0F, 1.0F);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE
                + "Metin " + ChatColor.WHITE + name
                + ChatColor.LIGHT_PURPLE + " został zniszczony przez "
                + ChatColor.GOLD + killer.getName()
                + ChatColor.DARK_PURPLE + " ✦");

        broadcastTopDamage();
        dropDeathLoot();

        removeEntities();
        stopTasks();
        scheduleRespawn();
    }

    private void destroyByTime() {
        if (!alive) {
            return;
        }

        alive = false;

        for (MetinMob mob : new ArrayList<>(activeMobs)) {
            mob.remove();
        }
        activeMobs.clear();

        Bukkit.broadcastMessage(ChatColor.GRAY + "Metin " + name + " rozpadł się samoistnie.");

        removeEntities();
        stopTasks();
        scheduleRespawn();
    }

    private void dropDeathLoot() {
        for (MetinDrop drop : metinConfig.getDrops(name)) {
            if (drop.shouldDrop() || drop.shouldDrop()) {
                location.getWorld().dropItemNaturally(location, drop.createItemStack());
            }
        }
    }

    private void broadcastTopDamage() {
        if (damageDealt.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>(damageDealt.entrySet());
        ranking.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        Bukkit.broadcastMessage(ChatColor.GOLD + "Top obrażeń Metina:");

        int place = 1;
        for (Map.Entry<UUID, Integer> entry : ranking) {
            if (place > 5) {
                break;
            }

            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (playerName == null) {
                playerName = entry.getKey().toString();
            }

            Bukkit.broadcastMessage(ChatColor.YELLOW + "" + place + ". "
                    + ChatColor.WHITE + playerName
                    + ChatColor.GRAY + " - "
                    + ChatColor.RED + entry.getValue() + " DMG");

            place++;
        }
    }

    private void scheduleRespawn() {
        int respawnTime = metinConfig.getRespawnTime(name);

        if (respawnTime <= 0) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!alive) {
                respawn();
            }
        }, respawnTime * 20L);
    }

    public void respawn() {
        if (alive) {
            return;
        }

        damageDealt.clear();
        spawn();
    }

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!alive) {
                    cancel();
                    return;
                }

                cleanDeadMobs();

                if (activeMobs.isEmpty() && hpFrozen) {
                    hpFrozen = false;
                    updateHpTag();
                    updateBossBar();
                }

                for (MetinMob mob : activeMobs) {
                    mob.updateNameTag();
                }

                updateBossBarPlayers();
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void startPassiveDamageTask() {
        passiveDamageTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!alive) {
                    cancel();
                    return;
                }

                cleanDeadMobs();

                if (!activeMobs.isEmpty()) {
                    hpFrozen = true;
                    return;
                }

                hpFrozen = false;

                currentHp--;
                hpSinceLastMobSpawn++;

                updateHpTag();
                updateBossBar();

                if (currentHp <= 0) {
                    destroyByTime();
                    return;
                }

                if (mobSpawnInterval > 0 && hpSinceLastMobSpawn >= mobSpawnInterval) {
                    spawnGuardMobs();
                    hpSinceLastMobSpawn = 0;
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startParticleTask() {
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!alive || crystal == null || crystal.isDead()) {
                    cancel();
                    return;
                }

                Location center = location.clone().add(0, 1.5D, 0);
                double time = System.currentTimeMillis() / 1000.0D;

                for (int i = 0; i < 4; i++) {
                    double angle = time * 2.0D + (i * Math.PI / 2.0D);

                    Location particleLocation = center.clone().add(
                            Math.cos(angle) * 1.5D,
                            Math.sin(time * 3.0D + i) * 0.5D,
                            Math.sin(angle) * 1.5D
                    );

                    spawnSafeParticle(
                            particleLocation,
                            1,
                            0,
                            0,
                            0,
                            0,
                            "DRAGON_BREATH"
                    );
                }

                if (hpFrozen) {
                    spawnSafeParticle(
                            center,
                            2,
                            0.5D,
                            0.5D,
                            0.5D,
                            0.0D,
                            "VILLAGER_ANGRY",
                            "ANGRY_VILLAGER"
                    );
                }

                spawnDustParticle(center);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void spawnDustParticle(Location loc) {
        Color color;

        double percent = maxHp <= 0 ? 0 : (double) currentHp / (double) maxHp;

        if (percent > 0.66D) {
            color = Color.PURPLE;
        } else if (percent > 0.33D) {
            color = Color.ORANGE;
        } else {
            color = Color.RED;
        }

        try {
            Particle dust = Particle.valueOf("DUST");
            loc.getWorld().spawnParticle(
                    dust,
                    loc,
                    3,
                    0.5D,
                    0.5D,
                    0.5D,
                    new Particle.DustOptions(color, 1.2F)
            );
        } catch (IllegalArgumentException ignored) {
            try {
                Particle redstone = Particle.valueOf("REDSTONE");
                loc.getWorld().spawnParticle(
                        redstone,
                        loc,
                        3,
                        0.5D,
                        0.5D,
                        0.5D,
                        new Particle.DustOptions(color, 1.2F)
                );
            } catch (IllegalArgumentException ignoredAgain) {
            }
        }
    }

    private void spawnSafeParticle(
            Location loc,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double extra,
            String... possibleParticles
    ) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        for (String particleName : possibleParticles) {
            try {
                Particle particle = Particle.valueOf(particleName);
                loc.getWorld().spawnParticle(
                        particle,
                        loc,
                        count,
                        offsetX,
                        offsetY,
                        offsetZ,
                        extra
                );
                return;
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void updateHpTag() {
        if (hpTag == null || hpTag.isDead()) {
            return;
        }

        double percent = maxHp <= 0 ? 0 : (double) currentHp / (double) maxHp;

        ChatColor hpColor;
        if (percent > 0.66D) {
            hpColor = ChatColor.GREEN;
        } else if (percent > 0.33D) {
            hpColor = ChatColor.YELLOW;
        } else {
            hpColor = ChatColor.RED;
        }

        String protectedText = hpFrozen
                ? ChatColor.RED + " ⛔ CHRONIONY"
                : "";

        hpTag.setCustomName(
                hpColor + "" + currentHp
                        + ChatColor.GRAY + "/"
                        + ChatColor.WHITE + maxHp
                        + ChatColor.RED + " ❤"
                        + protectedText
        );
    }

    private void updateBossBar() {
        if (bossBar == null) {
            return;
        }

        double progress = maxHp <= 0
                ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, (double) currentHp / (double) maxHp));

        bossBar.setProgress(progress);

        if (progress > 0.66D) {
            bossBar.setColor(BarColor.PURPLE);
        } else if (progress > 0.33D) {
            bossBar.setColor(BarColor.YELLOW);
        } else {
            bossBar.setColor(BarColor.RED);
        }

        String protectedText = hpFrozen ? ChatColor.RED + " ⛔ CHRONIONY" : "";

        bossBar.setTitle(
                ChatColor.DARK_PURPLE + "✦ METIN " + name + " ✦ "
                        + ChatColor.WHITE + currentHp + "/" + maxHp + " ❤"
                        + protectedText
        );
    }

    private void updateBossBarPlayers() {
        if (bossBar == null || location.getWorld() == null) {
            return;
        }

        double rangeSquared = 50.0D * 50.0D;

        for (Player player : location.getWorld().getPlayers()) {
            boolean close = player.getLocation().distanceSquared(location) <= rangeSquared;

            if (close && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            } else if (!close && bossBar.getPlayers().contains(player)) {
                bossBar.removePlayer(player);
            }
        }
    }

    private void removeEntities() {
        if (crystal != null && !crystal.isDead()) {
            crystal.remove();
        }

        if (nameTag != null && !nameTag.isDead()) {
            nameTag.remove();
        }

        if (hpTag != null && !hpTag.isDead()) {
            hpTag.remove();
        }

        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    private void stopTasks() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        if (passiveDamageTask != null) {
            passiveDamageTask.cancel();
            passiveDamageTask = null;
        }

        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    public void forceRemove() {
        alive = false;

        for (MetinMob mob : new ArrayList<>(activeMobs)) {
            mob.remove();
        }

        activeMobs.clear();
        removeEntities();
        stopTasks();
    }

    private int randomInt(int min, int max) {
        return min + (int) (Math.random() * ((max - min) + 1));
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location.clone();
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public boolean isAlive() {
        return alive;
    }

    public boolean isHpFrozen() {
        return hpFrozen;
    }

    public List<MetinMob> getActiveMobs() {
        return activeMobs;
    }

    public EnderCrystal getCrystal() {
        return crystal;
    }

    public void setMaxHp(int hp) {
        this.maxHp = Math.max(1, hp);
        this.currentHp = Math.min(currentHp, maxHp);
        metinConfig.setMetinMaxHp(name, this.maxHp);
        updateHpTag();
        updateBossBar();
    }

    public void setMobSpawnInterval(int interval) {
        this.mobSpawnInterval = Math.max(1, interval);
        metinConfig.setMobSpawnInterval(name, this.mobSpawnInterval);
    }

    public void setMobHp(int hp) {
        this.mobHp = Math.max(1, hp);
        metinConfig.setMobHp(name, this.mobHp);
    }
}