package boniec.ahalloween;

import boniec.boss.BossSpawn;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Level;

/** Spawn mobow Halloween z MythicMobs (ModelEngine / 3D). */
public final class HalloweenMythicSpawner {

    private HalloweenMythicSpawner() {}

    public static LivingEntity spawn(JavaPlugin plugin, Location loc, String internalName, double level) {
        if (loc == null || loc.getWorld() == null || internalName == null || internalName.isBlank()) {
            return null;
        }
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            plugin.getLogger().warning("[AHalloween] Brak MythicMobs — nie mozna spawnowac " + internalName);
            return null;
        }

        Optional<MythicMob> mobOpt = MythicBukkit.inst().getMobManager().getMythicMob(internalName);
        if (mobOpt.isEmpty()) {
            plugin.getLogger().warning("[AHalloween] Nie znaleziono MythicMoba '" + internalName
                    + "' (MythicMobs/mobs/halloween/*.yml).");
            return null;
        }

        loc.getChunk().load();
        final LivingEntity[] zlapany = new LivingEntity[1];
        org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onSpawn(MythicMobSpawnEvent event) {
                if (event.getMob() == null || event.getMobType() == null) return;
                if (!internalName.equalsIgnoreCase(event.getMobType().getInternalName())) return;
                if (event.getEntity() instanceof LivingEntity le) {
                    zlapany[0] = le;
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        try {
            ActiveMob active = BossSpawn.bezBlokad(() -> mobOpt.get().spawn(BukkitAdapter.adapt(loc), level));
            if (zlapany[0] == null && active != null) {
                try {
                    if (active.getEntity() instanceof LivingEntity le) {
                        zlapany[0] = le;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[AHalloween] Spawn Mythic " + internalName, t);
            return null;
        } finally {
            org.bukkit.event.HandlerList.unregisterAll(listener);
        }
        if (zlapany[0] != null) {
            BossSpawn.oznacz(zlapany[0]);
        }
        return zlapany[0];
    }
}
