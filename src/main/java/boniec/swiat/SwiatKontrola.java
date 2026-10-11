package boniec.swiat;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Staly dzien, bez deszczu, night vision po wejsciu/smierci
 * oraz zakaz spawnu szkieleta / creepera / zombie / endermana / pajaka.
 * Vanilla XP (kule, piece, moby) jest wylaczony - exp postaci idzie z /poziom.
 */
public final class SwiatKontrola implements Listener {

    private static final Set<EntityType> ZABRONIONE = EnumSet.of(
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.BOGGED,
            EntityType.WITHER_SKELETON,
            EntityType.CREEPER,
            EntityType.ZOMBIE,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.ENDERMAN,
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.WITCH,
            // "Pigmen" = Zombified Piglin (tak samo nazywa go komentarz w MinotaurManager).
            // NIE dodawac tu PIGLIN_BRUTE - to model bossa Minotaura (world.spawn(..., PiglinBrute.class))
            // i jego spawn poszedlby przez to samo CreatureSpawnEvent, ktore by tu kanclowalo boss-spawn.
            EntityType.ZOMBIFIED_PIGLIN
    );

    private static final PotionEffect NIGHT_VISION = new PotionEffect(
            PotionEffectType.NIGHT_VISION,
            PotionEffect.INFINITE_DURATION,
            0,
            true,
            false,
            false);

    private final JavaPlugin plugin;

    public SwiatKontrola(JavaPlugin plugin) {
        this.plugin = plugin;
        for (World world : Bukkit.getWorlds()) {
            ustawSwiat(world);
            usunZabronione(world);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            dajNightVision(p);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::utrzymujPogodeICzas, 20L, 200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::odswiezNightVision, 40L, 200L);
    }

    private void utrzymujPogodeICzas() {
        for (World world : Bukkit.getWorlds()) {
            ustawSwiat(world);
        }
    }

    private void odswiezNightVision() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            dajNightVision(p);
        }
    }

    private void ustawSwiat(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setTime(1000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(0);
        world.setThunderDuration(0);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    private void dajNightVision(Player player) {
        PotionEffect obecny = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (obecny != null && obecny.isInfinite()) return;
        player.addPotionEffect(NIGHT_VISION);
    }

    private boolean zabroniony(EntityType type) {
        return type != null && ZABRONIONE.contains(type);
    }

    /** Bossy / MythicMobs (Dulahan, mountry, Zeus) nie sa czyszczone jak waniliowe zombie. */
    private boolean chroniony(Entity entity) {
        if (entity == null || entity instanceof Player) return true;
        if (boniec.boss.BossSpawn.jestBossem(entity) || boniec.boss.BossSpawn.trwa()) return true;
        if (entity.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey("inventory", "ah_mob"),
                org.bukkit.persistence.PersistentDataType.STRING)) return true;
        return jestMythicMob(entity);
    }

    private boolean jestMythicMob(Entity entity) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return false;
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().isMythicMob(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean spawnKomendowy(SpawnReason reason) {
        return reason == SpawnReason.CUSTOM || reason == SpawnReason.COMMAND;
    }

    private void usunZabronione(World world) {
        for (LivingEntity mob : world.getLivingEntities()) {
            if (zabroniony(mob.getType()) && !chroniony(mob)) {
                mob.remove();
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        ustawSwiat(event.getWorld());
        usunZabronione(event.getWorld());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!zabroniony(event.getEntityType())) return;
        if (chroniony(event.getEntity()) || spawnKomendowy(event.getSpawnReason())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity e : event.getChunk().getEntities()) {
            if (zabroniony(e.getType()) && !chroniony(e)) {
                e.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        if (event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNetherPortal(PlayerPortalEvent event) {
        if (doNetheru(event.getTo()) || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNetherEntityPortal(EntityPortalEvent event) {
        if (doNetheru(event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNetherTeleport(PlayerTeleportEvent event) {
        if (doNetheru(event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNetherCreate(PortalCreateEvent event) {
        String reason = event.getReason().name();
        if (reason.contains("NETHER") || reason.equals("FIRE")) {
            event.setCancelled(true);
        }
    }

    private boolean doNetheru(org.bukkit.Location loc) {
        return loc != null && loc.getWorld() != null && loc.getWorld().getEnvironment() == World.Environment.NETHER;
    }

    private void wyrzucZNetheru(Player player) {
        if (player == null || player.getWorld() == null) return;
        if (player.getWorld().getEnvironment() != World.Environment.NETHER) return;
        World over = null;
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                over = w;
                break;
            }
        }
        if (over == null) return;
        player.teleport(over.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            wyrzucZNetheru(p);
            dajNightVision(p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathSkipScreen(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.isDead()) {
                p.spigot().respawn();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) dajNightVision(p);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        wyrzucZNetheru(event.getPlayer());
        dajNightVision(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != org.bukkit.Material.MILK_BUCKET) return;
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) dajNightVision(p);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaExp(PlayerExpChangeEvent event) {
        event.setAmount(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExp(BlockBreakEvent event) {
        event.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobExp(EntityDeathEvent event) {
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnaceExp(FurnaceExtractEvent event) {
        event.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFishExp(PlayerFishEvent event) {
        event.setExpToDrop(0);
    }
}
