package boniec.code.core;

import boniec.dev.inventory.Main;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import boniec.code.core.AdminPatronCommand;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MistrzSystem implements Listener, CommandExecutor {

    public static final String[] GODS = {
            "CHEWBACCA", "BOBAFETT", "HANSOLO", "DARTHVADER", "YODA"
    };

    private static final UUID HEARTS_MODIFIER_ID = UUID.fromString("a7c0b0e1-4d2a-4c3b-9e11-51f0c0d1b0e1");
    private static final String HEARTS_MODIFIER_NAME = "inventory_god_hearts";
    // ID regionu WorldGuard, w którym obowiązują specjalne zasady "Hades" (nie
    // patronowe "godziny bogów" - to osobna, niezależna mechanika oparta o miejsce).
    private static final String HADES_REGION_ID = "hades";
    private static final UUID HADES_LOCK_MODIFIER_ID = UUID.fromString("b8d1c2f2-5e3b-4d4c-9f22-62a1d1e2c1f2");
    private static final String HADES_LOCK_MODIFIER_NAME = "inventory_hades_region_lock";
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_HEX = Pattern.compile("&x((?:&[A-Fa-f0-9]){6})", Pattern.CASE_INSENSITIVE);

    private final Main plugin;
    private final NamespacedKey actionKey;
    private File dataFile;
    private YamlConfiguration data;

    public static String aktualnaGodzinaTyp = "BRAK";
    private BossBar activeBossBar;
    private BukkitRunnable timerTask;
    private int hourSecondsLeft = 0;
    private int hourSecondsMax = 3600;
    private int lastOdpalonaGodzinaBoga = -1;

    private boolean votingActive = false;
    private String voteOption1 = "";
    private String voteOption2 = "";
    private int votes1 = 0;
    private int votes2 = 0;
    private final Set<UUID> votedPlayers = new HashSet<>();
    private final Set<UUID> drawing = new HashSet<>();
    private final Set<UUID> extraHitLock = new HashSet<>();
    // Gracze aktualnie stojący w regionie "hades" - potrzebne, żeby wykryć MOMENT
    // wejścia/wyjścia (a nie odpalać efektu na każdym ticku ruchu).
    private final Set<UUID> playersInHadesRegion = new HashSet<>();
    // Cooldown na złotą płytkę naciskową (per gracz) - 3 sekundy między wybiciami.
    private final Map<UUID, Long> goldPlateCooldown = new HashMap<>();
    // Śledzimy DOKŁADNIE które okno menu /mistrz jest otwarte u danego gracza,
    // zamiast porównywać tytuł inventory (to bywa niepewne przy kolorach hex
    // i było prawdopodobną przyczyną tego, że klik w "losuj" czasem nic nie robił).
    private final Map<UUID, Inventory> openMenus = new HashMap<>();

    // Cache patronów graczy w pamięci RAM - eliminuje parsowanie drzewa YAML
    // przy KAŻDYM trafieniu PvP i KAŻDYM wykopanym bloku na serwerze z 300 osobami.
    private static final class CachedPatron {
        final String patron;
        final long until;
        CachedPatron(String patron, long until) {
            this.patron = patron;
            this.until = until;
        }
    }
    private final Map<UUID, CachedPatron> patronCache = new java.util.concurrent.ConcurrentHashMap<>();

    public MistrzSystem(Main plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "mistrz_action");
        loadData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("mistrz") != null) plugin.getCommand("mistrz").setExecutor(this);
        if (plugin.getCommand("glosuj") != null) plugin.getCommand("glosuj").setExecutor(this);
        if (plugin.getCommand("adminmistrz") != null) plugin.getCommand("adminmistrz").setExecutor(this);
        naprawBobaFettBonus();
        startScheduler();
        startHadesRegionScheduler();
        for (Player p : Bukkit.getOnlinePlayers()) clearHeartsModifier(p);
    }

    /**
     * Stary patron.yml mial bobaFettBonus: 1.0, czyli pasywka Boby nic nie dawala
     * (opis w GUI obiecuje zwiekszony drop z generatorow). 1.25 = +25%.
     */
    private void naprawBobaFettBonus() {
        FileConfiguration cfg = cfg();
        if (cfg == null) return;
        if (!cfg.isSet("bobaFettBonus") || cfg.getDouble("bobaFettBonus") == 1.0) {
            cfg.set("bobaFettBonus", 1.25);
            plugin.savePatronConfig();
        }
    }

    public void shutdown() {
        saveData();
        if (timerTask != null) timerTask.cancel();
        if (activeBossBar != null) boniec.boss.BossbarUtil.usun(activeBossBar);
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearHeartsModifier(p);
            clearHadesRegionHearts(p);
        }
        playersInHadesRegion.clear();
    }

    // Niezależny, stały scheduler (nie ten sam co timerTask od godzin bogów -
    // startVoting()/startSpecialHour() anulują timerTask, a to musi działać
    // ZAWSZE, niezależnie od aktualnej "godziny bogów"). Co sekundę sprawdzamy
    // wszystkich graczy pod kątem regionu "hades" - to też łapie wejście przez
    // teleport/portal/warp, których PlayerMoveEvent w ogóle nie widzi.
    private void startHadesRegionScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHadesRegionState(p, p.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean isInHadesRegion(Location location) {
        return plugin.isWorldGuardEnabled() && WorldGuardHook.isInRegion(location, HADES_REGION_ID);
    }

    // Wywoływane zarówno z ruchu (szybka reakcja - np. wyłączenie elytry od razu),
    // jak i z okresowego schedulera powyżej (łapie teleporty i koryguje ewentualny
    // "dryf" maksymalnego HP, gdyby coś innego zmieniło je w międzyczasie).
    private void updateHadesRegionState(Player player, Location location) {
        boolean inside = isInHadesRegion(location);
        boolean wasInside = playersInHadesRegion.contains(player.getUniqueId());

        if (inside && !wasInside) {
            playersInHadesRegion.add(player.getUniqueId());
            applyHadesRegionHearts(player);
        } else if (!inside && wasInside) {
            playersInHadesRegion.remove(player.getUniqueId());
            clearHadesRegionHearts(player);
        }

        if (inside && player.isGliding()) {
            player.setGliding(false);
        }
    }

    // Wymusza DOKŁADNIE 20 serc (40 HP) maksimum, niezależnie od tego, ile gracz
    // miałby "normalnie" (bazowe 20 HP + ewentualny bonus Afrodyty itp.) - stąd
    // liczymy różnicę względem WSZYSTKIEGO innego, a nie dodajemy bonus na sztywno,
    // żeby suma zawsze wyszła równo 40, a nie 40+coś.
    private void applyHadesRegionHearts(Player player) {
        AttributeInstance attr = maxHealth(player);
        if (attr == null) return;

        double currentLock = currentHadesLockAmount(attr);
        double withoutLock = attr.getValue() - currentLock;
        double targetTotal = cfg().getDouble("hadesRegionHearts", 20.0) * 2.0;
        double newLock = targetTotal - withoutLock;

        boolean firstEntry = Math.abs(currentLock) < 0.0001;
        setHadesLockModifier(attr, newLock);

        double newMax = attr.getValue();
        if (firstEntry) {
            // Dopiero co wszedł do regionu - "automatycznie daje 20 serc", czyli
            // od razu leczymy do pełna, tak jak prosił użytkownik.
            player.setHealth(newMax);
        } else if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }

    private void clearHadesRegionHearts(Player player) {
        AttributeInstance attr = maxHealth(player);
        if (attr == null) return;
        setHadesLockModifier(attr, 0.0);
        double newMax = attr.getValue();
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }

    private double currentHadesLockAmount(AttributeInstance attr) {
        for (AttributeModifier modifier : attr.getModifiers()) {
            if (isHadesLockModifier(modifier)) return modifier.getAmount();
        }
        return 0.0;
    }

    private boolean isHadesLockModifier(AttributeModifier modifier) {
        try {
            NamespacedKey key = modifier.getKey();
            if (key != null && "hades_region_lock".equals(key.getKey())) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (HADES_LOCK_MODIFIER_NAME.equals(modifier.getName()) || HADES_LOCK_MODIFIER_ID.equals(modifier.getUniqueId())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void setHadesLockModifier(AttributeInstance attr, double amount) {
        for (AttributeModifier modifier : new ArrayList<>(attr.getModifiers())) {
            if (isHadesLockModifier(modifier)) attr.removeModifier(modifier);
        }
        if (Math.abs(amount) < 0.0001) return;
        try {
            NamespacedKey key = new NamespacedKey(plugin, "hades_region_lock");
            AttributeModifier modifier = new AttributeModifier(
                    key,
                    amount,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY
            );
            attr.addModifier(modifier);
        } catch (Throwable ignored) {
            @SuppressWarnings("deprecation")
            AttributeModifier legacy = new AttributeModifier(HADES_LOCK_MODIFIER_ID, HADES_LOCK_MODIFIER_NAME, amount, AttributeModifier.Operation.ADD_NUMBER);
            attr.addModifier(legacy);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        // Sprawdzamy region tylko przy zmianie bloku - nie na każde drgnięcie
        // kamery/głowy, żeby nie zapytywać WorldGuard na każdy pojedynczy tick ruchu.
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        updateHadesRegionState(e.getPlayer(), e.getTo());
    }

    // Elytra: EntityToggleGlideEvent łapie MOMENT rozpoczęcia szybowania - jeśli
    // gracz jest już w regionie "hades", od razu blokujemy. Przypadek "wleciał
    // w region już szybując" obsługuje updateHadesRegionState() (onMove/scheduler).
    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (e.isGliding() && isInHadesRegion(player.getLocation())) {
            e.setCancelled(true);
        }
    }

    // Złota płytka naciskowa (LIGHT_WEIGHTED_PRESSURE_PLATE) w regionie "hades" -
    // wybija gracza ~20 kratek w górę, z cooldownem 3 sekund na gracza.
    @EventHandler(ignoreCancelled = true)
    public void onGoldPlate(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) return;
        Player player = e.getPlayer();
        if (!isInHadesRegion(player.getLocation())) return;

        long now = System.currentTimeMillis();
        long until = goldPlateCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (until > now) return;

        long cooldownSeconds = cfg().getLong("hadesGoldPlateCooldownSeconds", 3L);
        goldPlateCooldown.put(player.getUniqueId(), now + cooldownSeconds * 1000L);

        int launchHeight = cfg().getInt("hadesGoldPlateLaunchHeight", 20);
        int amplifier = cfg().getInt("hadesGoldPlateLevitationAmplifier", 4);
        // Lewitacja o sile (amplifier+1) podnosi gracza o 0.05*(amplifier+1) kratki
        // na tick - stąd wyliczamy czas trwania potrzebny na dokładnie tyle kratek.
        double blocksPerTick = 0.05 * (amplifier + 1);
        int durationTicks = (int) Math.ceil(launchHeight / blocksPerTick);

        player.setFallDistance(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, durationTicks, amplifier, true, true, true));
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
    }


    private void loadData() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "patron-data.yml");
        data = new YamlConfiguration();
        String raw = boniec.code.core.CoreDatabase.get().getKv("patron", "yaml", null);
        if (raw == null && dataFile.exists()) {
            data = YamlConfiguration.loadConfiguration(dataFile);
            saveData();
            dataFile.renameTo(new File(plugin.getDataFolder(), "patron-data.yml.migrated"));
        } else if (raw != null) {
            try {
                data.loadFromString(raw);
            } catch (Exception e) {
                plugin.getLogger().warning("Nie udalo sie wczytac patron z data.db: " + e.getMessage());
            }
        }
        patronCache.clear();
    }

    private void saveData() {
        boniec.code.core.CoreDatabase.get().setKv("patron", "yaml", data.saveToString());
    }

    // Ustawienia systemu bogów (patron.yml) - NIE mylić z data (patron-data.yml,
    // czyli dane graczy). Ładowane/zapisywane centralnie przez Main, żeby
    // Kity.java i MistrzSystem.java nie trzymały dwóch niezależnych kopii tego
    // samego pliku.
    private FileConfiguration cfg() {
        return plugin.getPatronConfig();
    }

    private void startScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (votingActive) return;
                LocalTime now = LocalTime.now(boniec.eventy.EventHarmonogram.STREFA);
                int h = now.getHour();
                if (h == lastOdpalonaGodzinaBoga) return;
                for (String god : GODS) {
                    int hour = cfg().getInt(key(god) + "Hour", domyslnaGodzina(god));
                    if (hour != h) continue;
                    lastOdpalonaGodzinaBoga = h;
                    if (god.equalsIgnoreCase(aktualnaGodzinaTyp)) return;
                    int max = Math.max(1, cfg().getInt("hourDurationSeconds", 3600));
                    int leftover = max - (now.getMinute() * 60 + now.getSecond());
                    if (leftover > 0) startSpecialHour(god, leftover);
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private static int domyslnaGodzina(String god) {
        return switch (god == null ? "" : god.toUpperCase(Locale.ROOT)) {
            case "CHEWBACCA" -> 13;
            case "BOBAFETT" -> 14;
            case "HANSOLO" -> 15;
            case "DARTHVADER" -> 16;
            case "YODA" -> 17;
            default -> -1;
        };
    }

    public void startSpecialHour(String typ) {
        startSpecialHour(typ, -1);
    }

    public void startSpecialHour(String typ, int durationSeconds) {
        String god = typ.toUpperCase(Locale.ROOT);
        if (timerTask != null) timerTask.cancel();
        if (activeBossBar != null) boniec.boss.BossbarUtil.usun(activeBossBar);
        votingActive = false;
        aktualnaGodzinaTyp = god;

        String titleTemplate = cfg().getString(key(god) + "Bossbar.title", "&5🔱 &8| &fGodzina: {PATRON} &8(&f{TIME}&8)");
        titleTemplate = titleTemplate.replace("{PATRON}", displayName(god));
        BarColor color = parseBarColor(cfg().getString(key(god) + "Bossbar.barColor", "PURPLE"));
        BarStyle style = parseBarStyle(cfg().getString(key(god) + "Bossbar.barStyle", "SOLID"));
        hourSecondsMax = Math.max(1, cfg().getInt("hourDurationSeconds", 3600));
        hourSecondsLeft = durationSeconds > 0 ? Math.min(durationSeconds, hourSecondsMax) : hourSecondsMax;

        // Przetlumacz kolory (hex + legacy) RAZ przy starcie godziny.
        // Nastepnie {TIME} to zwykly String.replace() w srodku gotowego stringa -
        // zero regex/Matcher na sekunde przez cala godzine boga (3600 wywolan x kostPregs).
        final String coloredTemplate = color(titleTemplate);
        final int timePlaceholderIdx = coloredTemplate.indexOf("{TIME}");
        // Jesli {TIME} nie ma w szablonie - caly tytuł jest staly, bez podmiany.
        final String bossbarPrefix;
        final String bossbarSuffix;
        if (timePlaceholderIdx >= 0) {
            bossbarPrefix = coloredTemplate.substring(0, timePlaceholderIdx);
            bossbarSuffix = coloredTemplate.substring(timePlaceholderIdx + "{TIME}".length());
        } else {
            bossbarPrefix = coloredTemplate;
            bossbarSuffix = "";
        }

        activeBossBar = boniec.boss.BossbarUtil.utworz(plugin, bossbarPrefix + formatClock(hourSecondsLeft) + bossbarSuffix, color, style);
        for (org.bukkit.boss.BarFlag f : org.bukkit.boss.BarFlag.values()) activeBossBar.removeFlag(f); // bez mgly/ciemnego nieba/muzyki bossa
        for (Player p : Bukkit.getOnlinePlayers()) activeBossBar.addPlayer(p);

        // Nowa "godzina boga" - title "Godzina Boga", a pod spodem (subtitle) nazwa
        // aktualnego boga/patrona, np. skolorowana "AFRODYTA".
        String godzinaTitle = color(cfg().getString("godzinaBogaTitle", "&5Godzina Boga"));
        String godzinaSubtitle = displayName(god);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(godzinaTitle, godzinaSubtitle, 10, 60, 20);
        }

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (hourSecondsLeft <= 0) {
                    if (activeBossBar != null) boniec.boss.BossbarUtil.usun(activeBossBar);
                    activeBossBar = null;
                    aktualnaGodzinaTyp = "BRAK";
                    cancel();
                    return;
                }
                if (activeBossBar != null) {
                    // Tylko String concatenation - bez regex/Matcher/color() co sekunde.
                    activeBossBar.setTitle(bossbarPrefix + formatClock(hourSecondsLeft) + bossbarSuffix);
                    activeBossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) hourSecondsLeft / hourSecondsMax)));
                }
                tickHourEffects();
                hourSecondsLeft--;
            }
        };
        timerTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void tickHourEffects() {
        // Chewbacca - godzina patrona: Sila dla wszystkich graczy online.
        if ("CHEWBACCA".equals(aktualnaGodzinaTyp)) {
            PotionEffectType type = resolveEffect(cfg().getString("chewbaccaEffectGod.type", "STRENGTH"));
            int amp = cfg().getInt("chewbaccaEffectGod.amplifier", 0);
            int dur = cfg().getInt("chewbaccaEffectGod.duration", 40);
            if (type != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.addPotionEffect(new PotionEffect(type, dur, amp, true, false, false));
                }
            }
        }
    }

    /** Szansa w procentach (0-100), np. 15 = 15%, 0.25 = 0.25%. */
    private static boolean roll(double percent) {
        return percent > 0 && Math.random() * 100.0 < percent;
    }

    /**
     * Mnoznik rankingu za zabojstwo. Darth Vader - godzina patrona: wiekszy ranking
     * po zabojstwie dla WSZYSTKICH graczy (darthVaderRankingBonusGod, np. 1.25 = +25%).
     * Poza godzina Vadera zawsze 1.0. Wywolywane z PvpListener.
     */
    public double getRankingMultiplier() {
        if (!isGodHour("DARTHVADER")) return 1.0;
        return Math.max(1.0, cfg().getDouble("darthVaderRankingBonusGod", 1.25));
    }

    /**
     * Mnoznik kasy z generatorow (MiningListener). Pasywnie +25% dla posiadacza Boby,
     * w godzinie patrona x1.5 dla wszystkich. Oba sie mnoza.
     */
    public double getBobaDropMultiplier(Player player) {
        double multiplier = 1.0;
        if (player != null && hasPatron(player, "BOBAFETT")) {
            double passive = cfg().getDouble("bobaFettBonus", 1.25);
            if (passive > 1.0) multiplier *= passive;
        }
        if (isGodHour("BOBAFETT")) {
            multiplier *= Math.max(1.0, cfg().getDouble("bobaFettBonusGod", 1.5));
        }
        return multiplier < 1.0 ? 1.0 : multiplier;
    }

    public void startVoting(String op1, String op2, int duration) {
        if (timerTask != null) timerTask.cancel();
        if (activeBossBar != null) boniec.boss.BossbarUtil.usun(activeBossBar);

        votingActive = true;
        voteOption1 = op1;
        voteOption2 = op2;
        votes1 = 0;
        votes2 = 0;
        votedPlayers.clear();
        aktualnaGodzinaTyp = "GLOSOWANIE";

        activeBossBar = boniec.boss.BossbarUtil.utworz(plugin, formatVoteTitle(),
                parseBarColor(cfg().getString("glosowanie.barColor", "GREEN")),
                parseBarStyle(cfg().getString("glosowanie.barStyle", "SOLID")));
        for (org.bukkit.boss.BarFlag f : org.bukkit.boss.BarFlag.values()) activeBossBar.removeFlag(f); // bez mgly/ciemnego nieba/muzyki bossa
        for (Player p : Bukkit.getOnlinePlayers()) activeBossBar.addPlayer(p);

        timerTask = new BukkitRunnable() {
            int timeLeft = duration;
            int maxTime = Math.max(1, duration);

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    endVoting();
                    cancel();
                    return;
                }
                if (activeBossBar != null) {
                    activeBossBar.setTitle(formatVoteTitle());
                    activeBossBar.setProgress((double) timeLeft / maxTime);
                }
                timeLeft--;
            }
        };
        timerTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void endVoting() {
        votingActive = false;
        if (activeBossBar != null) {
            activeBossBar.removeAll();
            activeBossBar = null;
        }
        String winner = (votes1 >= votes2) ? voteOption1 : voteOption2;
        Bukkit.broadcastMessage(color(cfg().getString("glosowanie.wiadomosci.zakonczone", "&aGłosowanie zakończone! Wygrał: &e{ZWYCIEZCA}")
                .replace("{ZWYCIEZCA}", winner)));
        String type = resolveGodName(winner);
        if (type != null) startSpecialHour(type);
        else aktualnaGodzinaTyp = "BRAK";
    }

    private String formatVoteTitle() {
        int total = votes1 + votes2;
        int p1 = (total == 0) ? 0 : (int) ((votes1 / (double) total) * 100);
        int p2 = (total == 0) ? 0 : (int) ((votes2 / (double) total) * 100);
        // Wyglad bossbara glosowania (/adminmistrz vote) - patron.yml -> glosowanie.title
        String title = cfg().getString("glosowanie.title",
                "&bZaglosuj na bohatera komenda &8(&3/glosuj <nazwa>&8) &8| {OPCJA1} &3{PROC1}% {OPCJA2} &3{PROC2}%!");
        return color(title
                .replace("{OPCJA1}", voteOption1)
                .replace("{OPCJA2}", voteOption2)
                .replace("{PROC1}", String.valueOf(p1))
                .replace("{PROC2}", String.valueOf(p2))
                .replace("{GLOSY1}", String.valueOf(votes1))
                .replace("{GLOSY2}", String.valueOf(votes2)));
    }

    public boolean isGodHour(String god) {
        return god != null && god.equalsIgnoreCase(aktualnaGodzinaTyp);
    }

    /** Godziny startu z patron.yml (*Hour), posortowane. */
    public int[] godzinyBogow() {
        List<Integer> godziny = new ArrayList<>();
        for (String god : GODS) {
            int hour = cfg().getInt(key(god) + "Hour", domyslnaGodzina(god));
            if (hour >= 0 && hour <= 23 && !godziny.contains(hour)) {
                godziny.add(hour);
            }
        }
        Collections.sort(godziny);
        int[] out = new int[godziny.size()];
        for (int i = 0; i < godziny.size(); i++) out[i] = godziny.get(i);
        return out;
    }

    /**
     * true, jeśli WorldGuard jest zainstalowany i region na danej lokalizacji ma
     * flagę pvp ustawioną na deny - w takim wypadku żaden patron/bóg nie powinien
     * działać w tym miejscu. Bez WorldGuard zawsze zwraca false (nic nie blokujemy).
     */
    private boolean isPvpDenied(Location location) {
        return plugin.isWorldGuardEnabled() && WorldGuardHook.isPvpDenied(location);
    }

    private static boolean isKnownGod(String god) {
        for (String g : GODS) {
            if (g.equalsIgnoreCase(god)) return true;
        }
        return false;
    }

    public boolean hasPatron(Player player, String god) {
        String current = getPatron(player);
        return god != null && god.equalsIgnoreCase(current);
    }

    public String getPatron(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        CachedPatron cached = patronCache.get(uuid);
        if (cached == null) {
            String path = "players." + uuid;
            long until = data.getLong(path + ".until", 0L);
            String rawPatron = data.getString(path + ".patron");
            String patron = resolveGodName(rawPatron);
            if (patron != null && !isKnownGod(patron)) patron = null;
            cached = new CachedPatron(patron, until);
            patronCache.put(uuid, cached);
        }

        if (cached.until > 0 && cached.until < now) {
            String path = "players." + uuid;
            data.set(path + ".patron", null);
            data.set(path + ".until", 0L);
            patronCache.put(uuid, new CachedPatron(null, 0L));
            saveData();
            return null;
        }
        return cached.patron;
    }

    /**
     * Nazwa aktualnego patrona gracza, JUŻ z kolorem (tak jak wyświetlana w GUI/
     * tytułach) - używana m.in. przez placeholder PlaceholderAPI %inventory_patron%.
     * Zwraca skolorowany "brak patrona" (nullPatron z patron.yml), jeśli gracz
     * nie ma aktualnie żadnego patrona.
     */
    public String getPatronDisplayName(Player player) {
        return displayName(getPatron(player));
    }


    public void resetDrawCooldown(Player player) {
        data.set("players." + player.getUniqueId() + ".lastDraw", 0L);
        saveData();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (activeBossBar != null) activeBossBar.addPlayer(e.getPlayer());
        clearHeartsModifier(e.getPlayer());
        applyJoinKnockbackResistance(e.getPlayer());
    }

    /**
     * Ustawia knockback resistance gracza na sztywno 0.4 przy wejściu na serwer
     * (0.0 = brak odporności na odrzut, 1.0 = całkowita odporność/brak odrzutu).
     * Nadpisuje bazową wartość atrybutu - nie dodaje modifiera, więc działa
     * niezależnie od zbroi/efektów, które ewentualnie też go modyfikują.
     */
    private void applyJoinKnockbackResistance(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (attr != null) {
            attr.setBaseValue(0.3);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clearHeartsModifier(e.getPlayer());
    }

    // Zadaje "prawdziwe" obrażenia - odejmuje HP bezpośrednio (setHealth),
    // więc nie interesuje go czym/jak trafił atakujący (łapka, miecz, topór itd.)
    // ani zbroja/efekty ofiary - zawsze zetnie dokładnie tyle serc, ile podamy.
    private void applyTrueDamage(Player attacker, Player victim, double hearts) {
        if (boniec.adminutil.AdminUtilManager.isGod(victim)) {
            return; // /god - setHealth omija eventy obrażeń, więc blokujemy ręcznie
        }
        double dmg = hearts * 2.0;
        double newHealth = Math.max(0.0, victim.getHealth() - dmg);
        victim.setHealth(newHealth);
    }

    // MONITOR: uruchamiamy się jako JEDEN Z OSTATNICH nasłuchiwaczy na to zdarzenie,
    // żeby WorldGuard (albo inny plugin chroniący spawn/regiony) zdążył WCZEŚNIEJ
    // anulować cios, jeśli PVP jest tam zablokowane. Dzięki temu poniższe
    // e.isCancelled() poprawnie wykrywa TAKĄ blokadę, niezależnie od tego, jaki
    // plugin ją ustawił (WorldGuard, Towny, GriefPrevention, spawn-protection itp.),
    // a nie tylko konkretnie WorldGuard (jak isPvpDenied() poniżej).
    // ignoreCancelled = false (domyślne) celowo - MUSIMY sami sprawdzić isCancelled(),
    // a nie pozwolić Bukkitowi pominąć nas automatycznie, bo dalej w metodzie
    // ustawiamy e.setCancelled(true) dla Hefajstosa (blok), co nadal ma normalnie działać.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (extraHitLock.contains(attacker.getUniqueId())) return;

        // Jeśli JAKIKOLWIEK inny plugin (WorldGuard, Towny, GriefPrevention,
        // ochrona spawnu itp.) już anulował ten cios - traktujemy to jako PVP
        // zablokowane w tym miejscu i pomijamy WSZYSTKIE bonusy patronów/bogów,
        // niezależnie od tego, jakim mechanizmem dana ochrona działa.
        if (e.isCancelled()) return;

        // Kratos i KAŻDY inny patron/bóg mają być wyłączone w regionach WorldGuard,
        // w których flaga pvp jest ustawiona na deny (np. spawn, safe-strefy) - nie
        // ruszamy tu samego zdarzenia (nie cancelujemy obrażeń - to i tak zwykle
        // robi WorldGuard swoim własnym listenerem), tylko pomijamy WSZYSTKIE bonusy
        // patronów, żeby np. Hefajstos nie blokował/Ares nie dobijał na safe-strefie.
        if (isPvpDenied(victim.getLocation())) return;

        // Region "hades" - dodatkowe obrażenia obowiązują tam ZAWSZE, niezależnie
        // od patronów/godziny bogów (to osobna, terenowa mechanika).
        if (isInHadesRegion(victim.getLocation())) {
            e.setDamage(e.getDamage() * (1.0 + cfg().getDouble("hadesRegionBonusDamage", 0.10)));
        }

        // ── Chewbacca (pasywnie): wiecej zadawanych obrazen ──────────────────────
        if (hasPatron(attacker, "CHEWBACCA")) {
            e.setDamage(e.getDamage() * (1.0 + cfg().getDouble("chewbaccaBonusDamage", 0.10)));
        }

        // ── Yoda (pasywnie): mniej otrzymywanych obrazen ─────────────────────────
        if (hasPatron(victim, "YODA")) {
            double resist = Math.max(0.0, Math.min(1.0, cfg().getDouble("yodaResistance", 0.15)));
            e.setDamage(e.getDamage() * (1.0 - resist));
        }

        // ── Han Solo (pasywnie): szansa na pajeczyne pod przeciwnikiem ───────────
        if (hasPatron(attacker, "HANSOLO") && roll(cfg().getDouble("hanSoloCobwebChance", 15))) {
            Location loc = victim.getLocation();
            if (loc.getBlock().getType() == Material.AIR) {
                loc.getBlock().setType(Material.COBWEB);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (loc.getBlock().getType() == Material.COBWEB) loc.getBlock().setType(Material.AIR);
                }, 60L);
            }
        }

        // ── Darth Vader (pasywnie): szansa na oslabienie przeciwnika ─────────────
        if (hasPatron(attacker, "DARTHVADER") && roll(cfg().getDouble("darthVaderWeaknessChance", 0.20))) {
            PotionEffectType type = resolveEffect(cfg().getString("darthVaderWeaknessEffect.type", "WEAKNESS"));
            if (type != null) {
                victim.addPotionEffect(new PotionEffect(type,
                        cfg().getInt("darthVaderWeaknessEffect.duration", 60),
                        cfg().getInt("darthVaderWeaknessEffect.amplifier", 0)));
            }
        }

        // ── Han Solo (godzina patrona): szansa na zdjecie ochrony przed ogniem ───
        if (isGodHour("HANSOLO")
                && victim.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)
                && roll(cfg().getDouble("hanSoloFireResistanceRemovalChanceGod", 0.50))) {
            victim.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            victim.sendTitle(
                    color(cfg().getString("hanSoloFireRemovalTitleVictim", "&#5FE2C5&lFIRE REMOVED")),
                    color(cfg().getString("hanSoloFireRemovalSubtitleVictim", "&fTwoja ochrona odeszła")),
                    5, 20, 5
            );
        }

        // ── Yoda (godzina patrona): szansa na oslepienie po uderzeniu ────────────
        if (isGodHour("YODA") && roll(cfg().getDouble("yodaBlindnessChance", 0.30))) {
            PotionEffectType type = resolveEffect(cfg().getString("yodaBlindnessEffect.type", "BLINDNESS"));
            if (type != null) {
                victim.addPotionEffect(new PotionEffect(type,
                        cfg().getInt("yodaBlindnessEffect.duration", 50),
                        cfg().getInt("yodaBlindnessEffect.amplifier", 0)));
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;

        // Sprawdzamy multiplier PRZED policzeniem drops - getDrops() liczy realny
        // loot bloku (z uwzglednieniem narzedzia/enchantow) i jest wywolywane przy
        // KAZDYM zbiciu bloku przez KAZDEGO gracza na serwerze, wiec bez tej
        // kolejnosci ten koszt byl ponoszony bezwarunkowo w >99% przypadkow
        // (gdy gracz nie ma patrona BOBA FETT i nie trwa jego godzina),
        // mimo ze funkcja i tak natychmiast konczyla dzialanie bez zadnego efektu.
        double multiplier = getBobaDropMultiplier(e.getPlayer());
        if (multiplier <= 1.0) return;

        // Godzina Boby mnozy TYLKO kase z MiningListener. Extra itemy z generatorow
        // wylatuja na ziemie (wełna/proszek) i tak nie powinny.
        if (plugin.getGeneratorManager() != null
                && plugin.getGeneratorManager().getGeneratorAt(e.getBlock().getLocation()) != null) {
            return;
        }
        boniec.gornik.GornikManager gornik = plugin.getGornikManager();
        if (gornik != null && gornik.isEnabled() && gornik.getCena(e.getBlock().getType()) > 0) {
            return;
        }

        Collection<ItemStack> drops = e.getBlock().getDrops(e.getPlayer().getInventory().getItemInMainHand(), e.getPlayer());

        double nocny = 1.0;
        if (plugin.getGeneratorManager() != null) {
            nocny = plugin.getGeneratorManager().mnoznikNocnyPvp(e.getBlock().getLocation());
        }

        List<String> materials = cfg().getStringList("bobaFettGeneratorMaterials");
        boolean extra = false;
        for (ItemStack drop : drops) {
            if (drop == null) continue;
            if (materials.isEmpty() || materials.contains(drop.getType().name())) {
                extra = true;
                int add = Math.max(0, (int) Math.round(drop.getAmount() * (multiplier - 1.0) * nocny));
                if (add > 0) {
                    ItemStack bonus = drop.clone();
                    bonus.setAmount(add);
                    e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), bonus);
                }
            }
        }
        if (!extra && multiplier > 1.0 && !drops.isEmpty()) {
            for (ItemStack drop : drops) {
                if (drop == null) continue;
                int add = Math.max(0, (int) Math.round(drop.getAmount() * (multiplier - 1.0) * nocny));
                if (add > 0) {
                    ItemStack bonus = drop.clone();
                    bonus.setAmount(add);
                    e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), bonus);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Inventory openGui = openMenus.get(player.getUniqueId());
        if (openGui == null || !e.getInventory().equals(openGui)) return;
        e.setCancelled(true);
        try {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if ("DRAW".equalsIgnoreCase(action)) {
                startDraw(player);
            }
        } catch (Throwable t) {
            // Dowolny wyjątek tutaj wcześniej powodował, że klik "znikał" bez śladu
            // (Bukkit po prostu loguje go w konsoli, gracz nic nie widzi). Teraz gracz
            // dostaje wiadomość, a admin widzi dokładny błąd w logach serwera.
            plugin.getLogger().warning("Błąd obsługi kliknięcia w menu /mistrz gracza " + player.getName() + ": " + t);
            player.sendMessage(color("&cWystąpił błąd, spróbuj ponownie."));
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        Inventory openGui = openMenus.get(player.getUniqueId());
        if (openGui != null && e.getInventory().equals(openGui)) {
            openMenus.remove(player.getUniqueId());
        }
    }

    public void openGUI(Player player) {
        int rows = Math.max(1, Math.min(6, cfg().getInt("menuRows", 6)));
        Inventory gui = Bukkit.createInventory(null, rows * 9, menuTitle());

        List<Map<?, ?>> menu = cfg().getMapList("menu");
        for (Map<?, ?> entry : menu) {
            int slot1 = toInt(entry.get("slot"), -1);
            if (slot1 < 1 || slot1 > rows * 9) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) entry.get("item");
            if (itemMap == null) continue;
            gui.setItem(slot1 - 1, buildConfigItem(itemMap));
        }

        int drawSlot = cfg().getInt("draw.slot", 4) - 1;
        if (drawSlot >= 0 && drawSlot < gui.getSize()) {
            gui.setItem(drawSlot, tagged(buildDrawItem(player), "DRAW"));
        }

        int infoSlot = cfg().getInt("infoSlot", 50) - 1;
        if (infoSlot >= 0 && infoSlot < gui.getSize()) {
            gui.setItem(infoSlot, buildInfoItem(player));
        }

        for (String god : GODS) {
            String section = guiKey(god);
            if (cfg().getBoolean(section + ".hide", false)) continue;
            int slot = cfg().getInt(section + ".slot", -1) - 1;
            if (slot < 0 || slot >= gui.getSize()) continue;
            gui.setItem(slot, buildGodHead(god));
        }

        openMenus.put(player.getUniqueId(), gui);
        player.openInventory(gui);
    }

    private ItemStack buildDrawItem(Player player) {
        String tex = cfg().getString("draw.texture", "cc80e5e99ec3487b2a4e10dfdf6224f64515d4bc3c2fd045db96c45d2f9af6f1");
        ItemStack head = skull(tex, cfg().getString("draw.itemName", "&bʟᴏꜱᴏᴡᴀɴɪᴇ"),
                replaceList(cfg().getStringList("draw.itemLore"), player));
        return head;
    }

    private ItemStack buildInfoItem(Player player) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(color(cfg().getString("info.itemName", "&bɪɴꜰᴏʀᴍᴀᴄᴊᴇ")));
        meta.setLore(colorList(replaceList(cfg().getStringList("info.itemLore"), player)));
        paper.setItemMeta(meta);
        return paper;
    }

    private ItemStack buildGodHead(String god) {
        String section = guiKey(god);
        ItemStack head = skull(
                cfg().getString(section + ".texture", ""),
                cfg().getString(section + ".itemName", displayName(god)),
                cfg().getStringList(section + ".itemLore")
        );
        if (cfg().getBoolean(section + ".enchanted", false)) {
            ItemMeta meta = head.getItemMeta();
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack tagged(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private void startDraw(Player player) {
        if (drawing.contains(player.getUniqueId())) return;
        long now = System.currentTimeMillis();
        long last = data.getLong("players." + player.getUniqueId() + ".lastDraw", 0L);
        long freeEvery = parseDuration(cfg().getString("freeReset", "7d"));
        boolean free = last == 0L || now - last >= freeEvery;

        if (!free) {
            int need = getOdlamekAmount();
            if (!takeOdlamki(player, need)) {
                // Wcześniej to była WYŁĄCZNIE cicha wiadomość na czacie - łatwo ją przeoczyć
                // i wygląda to tak, jakby kliknięcie w ogóle nic nie robiło. Dodajemy dźwięk
                // i wyraźny actionbar, żeby gracz na pewno zauważył odmowę.
                String msg = cfg().getString("noAmount.wiadomosc", "&cNie posiadasz wystarczająco przedmiotów! &7(potrzebujesz &f{AMOUNT}x&7)");
                if (!msg.contains("{AMOUNT}")) {
                    // NAPRAWA: jeśli admin ma w patron.yml sztywno wpisaną liczbę (np. "...10x...")
                    // zamiast {AMOUNT}, wiadomość pokazywałaby STARĄ, zapisaną na twardo wartość,
                    // nawet po zmianie wymaganej ilości przez /adminpatron setodlamek. Ostrzegamy
                    // o tym w konsoli i dopisujemy realną (aktualną) wartość na końcu, żeby gracz
                    // zawsze widział prawdziwą, obowiązującą liczbę.
                    plugin.getLogger().warning("messages.noAmount.wiadomosc w patron.yml nie zawiera {AMOUNT} - "
                            + "wiadomość może pokazywać nieaktualną liczbę odłamków. Dodaj {AMOUNT} do tekstu.");
                    msg = msg + " &7(&f" + need + "x&7)";
                } else {
                    msg = msg.replace("{AMOUNT}", String.valueOf(need));
                }
                player.sendMessage(color(msg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }

        List<String> pool = buildDrawPool();
        if (pool.isEmpty()) {
            player.sendMessage(color("&cBrak aktywnych bohaterów w losowaniu (sprawdź *Status i *DrawWeight w patron.yml)."));
            return;
        }
        final String won = rollPatronFromPool(pool);

        drawing.add(player.getUniqueId());
        player.closeInventory();
        // Domyślnie: 20 "tyknięć" co 5 ticków = 100 ticków = dokładnie 5 sekund losowania.
        int ticks = Math.max(1, cfg().getInt("timerDrawing", 20));
        int interval = Math.max(1, cfg().getInt("cooldownDraw", 5));
        Sound click = parseSound(cfg().getString("drawingSound", "UI_BUTTON_CLICK"), Sound.UI_BUTTON_CLICK);
        Sound success = parseSound(cfg().getString("successSound", "ENTITY_PLAYER_LEVELUP"), Sound.ENTITY_PLAYER_LEVELUP);
        String title = cfg().getString("drawingTitle", "&5&lʟᴏꜱᴏᴡᴀɴɪᴇ ᴘᴀᴛʀᴏɴᴀ");
        String subtitle = cfg().getString("drawingSubtitle", "{PATRON}");

        new BukkitRunnable() {
            int left = ticks;
            boolean finished = false;

            @Override
            public void run() {
                // WAŻNE: cały korpus w try/finally. Wcześniej, gdyby cokolwiek tutaj rzuciło
                // wyjątek (np. błędna konfiguracja tekstu), gracz zostawał NA STAŁE w zbiorze
                // "drawing" i każdy kolejny klik "losuj" był po cichu ignorowany (return na
                // samej górze startDraw) - wyglądało to dokładnie jak "klikam i nic się nie
                // dzieje", aż do restartu serwera. Teraz błąd trafia do logów, a gracz zawsze
                // odzyskuje możliwość ponownego losowania (finally usuwa go z "drawing" przy
                // KAŻDYM zakończeniu zadania - sukces, wyjście gracza czy błąd).
                try {
                    if (!player.isOnline()) {
                        finished = true;
                        cancel();
                        return;
                    }
                    String preview = left <= 1 ? won : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                    player.sendTitle(color(title), color(applyPatronPlaceholder(subtitle, displayName(preview))), 0, 30, 10);
                    player.playSound(player.getLocation(), click, 1f, 1f);
                    left--;
                    if (left <= 0) {
                        finished = true;
                        cancel();
                        grantPatron(player, won);
                        String successTitle = cfg().getString("drawingTitleSuccess", "&b&lWYLOSOWAŁEŚ...");
                        String successSubtitle = cfg().getString("drawingSubtitleSuccess", "%patron%");
                        player.sendTitle(
                                color(successTitle),
                                color(applyPatronPlaceholder(successSubtitle, displayName(won))),
                                10, 60, 10
                        );
                        player.playSound(player.getLocation(), success, 1f, 1f);
                        String broadcast = cfg().getString("newPatronBroadcast", "");
                        if (broadcast != null && !broadcast.isEmpty()) {
                            Bukkit.broadcastMessage(color(applyPatronPlaceholder(
                                    broadcast.replace("{PLAYER}", player.getName()), displayName(won))));
                        }
                    }
                } catch (Throwable t) {
                    finished = true;
                    plugin.getLogger().warning("Błąd podczas losowania patrona dla " + player.getName() + ": " + t);
                    try {
                        cancel();
                    } catch (Throwable ignored) {
                    }
                } finally {
                    if (finished) {
                        drawing.remove(player.getUniqueId());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    // Wspiera obie składnie placeholdera nazwy patrona: {PATRON} (reszta configu) i %patron% (jak w prośbie).
    private String applyPatronPlaceholder(String text, String patronDisplayName) {
        if (text == null) return "";
        return text.replace("{PATRON}", patronDisplayName)
                .replace("%patron%", patronDisplayName)
                .replace("%PATRON%", patronDisplayName);
    }

    /** Patroni dostepni w /mistrz (Status + waga DrawWeight > 0). */
    private List<String> buildDrawPool() {
        List<String> pool = new ArrayList<>();
        for (String god : GODS) {
            if (!isGodEnabledForDraw(god)) continue;
            pool.add(god);
        }
        return pool;
    }

    private boolean isGodEnabledForDraw(String god) {
        if (!cfg().getBoolean(key(god) + "Status", true)) return false;
        return getDrawWeight(god) > 0;
    }

    /** Waga w losowaniu (domyślnie 100). Klucz w patron.yml: np. chewbaccaDrawWeight. */
    private int getDrawWeight(String god) {
        return Math.max(0, cfg().getInt(key(god) + "DrawWeight", 100));
    }

    private String rollPatronFromPool(List<String> pool) {
        if (pool.size() == 1) return pool.get(0);
        int total = 0;
        int[] weights = new int[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = getDrawWeight(pool.get(i));
            total += weights[i];
        }
        if (total <= 0) return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights[i];
            if (roll < 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    private void grantPatron(Player player, String god) {
        long now = System.currentTimeMillis();
        long until = now + parseDuration(cfg().getString("patronDuration", "30d"));
        String cleanGod = god != null ? god.toUpperCase(Locale.ROOT) : null;
        data.set("players." + player.getUniqueId() + ".patron", cleanGod);
        data.set("players." + player.getUniqueId() + ".lastDraw", now);
        data.set("players." + player.getUniqueId() + ".until", until);
        patronCache.put(player.getUniqueId(), new CachedPatron(cleanGod, until));
        saveData();
    }

    private boolean takeOdlamki(Player player, int amount) {
        ItemStack template = getOdlamkiTemplate();
        if (template == null) return false;
        int have = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isOdlamki(item, template)) have += item.getAmount();
        }
        if (have < amount) return false;
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (!isOdlamki(item, template)) continue;
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        player.getInventory().setContents(contents);
        return true;
    }

    // WAŻNE: porównujemy CAŁE NBT/meta przedmiotu (nazwa, lore, zaklęcia, custom model data,
    // niestandardowe dane PDC itd.) przez ItemStack#isSimilar, a nie tylko samą nazwę jak
    // poprzednio. Dzięki temu gracz nie może "oszukać" systemu, np. nazywając kowadłem
    // dowolny przedmiot tak samo jak odłamek - musi mieć DOKŁADNIE ten sam przedmiot.
    private boolean isOdlamki(ItemStack item, ItemStack template) {
        if (item == null || template == null) return false;
        return item.isSimilar(template);
    }

    // Ile odłamków potrzeba na jedno (płatne) losowanie - wspólne dla GUI i komendy /adminpatron.
    //
    // NAPRAWA: wcześniej istniały DWIE różne, niepowiązane ścieżki w configu:
    //   - "odlamek.amount"  (domyślna, z szablonu patron.yml - używana TYLKO jako
    //                        rozmiar stacka fallbackowego przedmiotu, zanim admin
    //                        cokolwiek ustawi przez /adminpatron setodlamek)
    //   - "odlamki.amount" / "odlamekAmount" (rzeczywiście sprawdzana wymagana ilość)
    // Admin edytujący patron.yml ręcznie pod kluczem "odlamek.amount" nie miał żadnego
    // wpływu na realnie wymaganą ilość - stąd wrażenie, że ustawienie nowej wartości
    // "nie działa". Od teraz jest JEDNO źródło prawdy: "odlamki.amount" (ustawiane
    // przez setOdlamekAmount/setOdlamekTemplate), z zachowaniem odczytu starych
    // kluczy dla kompatybilności wstecznej.
    public int getOdlamekAmount() {
        if (cfg().contains("odlamki.amount")) return Math.max(1, cfg().getInt("odlamki.amount"));
        if (cfg().contains("odlamekAmount")) return Math.max(1, cfg().getInt("odlamekAmount"));
        return Math.max(1, cfg().getInt("odlamek.amount", 10));
    }

    /** Ustawia wymaganą ilość odłamków - jedyne miejsce, które powinno to robić. */
    public void setOdlamekAmount(int amount) {
        cfg().set("odlamki.amount", Math.max(1, amount));
        // Usuwamy stary zdublowany klucz, żeby nie było już dwóch "źródeł prawdy" w pliku.
        cfg().set("odlamekAmount", null);
        plugin.savePatronConfig();
    }

    // Publiczne, żeby AdminPatronCommand (komenda giveodlamek) mogło pobrać potrzebną liczbę.
    public boolean giveOdlamki(Player player, int amount) {
        if (amount <= 0) return false;
        ItemStack template = getOdlamkiTemplate();
        if (template == null) return false;
        int maxStack = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack toGive = template.clone();
            toGive.setAmount(stackAmount);
            var leftover = player.getInventory().addItem(toGive);
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
            remaining -= stackAmount;
        }
        return true;
    }

    public ItemStack getOdlamkiTemplate() {
        FileConfiguration cfg = cfg();
        // Nowy, w pełni wierny zapis NBT (patrz setOdlamekTemplate/serializeItemFull)
        // - obejmuje KOMPLETNIE cały stan przedmiotu (nazwa, lore, zaklęcia, custom
        // model data, unbreakable, flagi, PersistentDataContainer, atrybuty itd.),
        // bo zapisujemy surowe bajty samego obiektu ItemStack, a nie tylko to, co
        // Bukkit akurat potrafi wypisać jako czytelny YAML.
        if (cfg.isString("odlamki.item-nbt")) {
            ItemStack full = deserializeItemFull(cfg.getString("odlamki.item-nbt"));
            if (full != null) return full;
        }
        // Kompatybilność wsteczna: stary format zapisany zwykłym cfg.set(ItemStack)
        // (np. ustawiony przed tą poprawką).
        if (cfg.isItemStack("odlamki.item")) {
            return cfg.getItemStack("odlamki.item");
        }
        ConfigurationSection section = cfg.getConfigurationSection("odlamek");
        if (section == null) return new ItemStack(Material.PRISMARINE_CRYSTALS);
        Material mat = Material.matchMaterial(section.getString("material", "PRISMARINE_CRYSTALS"));
        if (mat == null) mat = Material.PRISMARINE_CRYSTALS;
        ItemStack item = new ItemStack(mat, Math.max(1, section.getInt("amount", 1)));
        ConfigurationSection metaSec = section.getConfigurationSection("item-meta");
        if (metaSec != null) {
            ItemMeta meta = item.getItemMeta();
            if (metaSec.contains("display-name")) meta.setDisplayName(color(metaSec.getString("display-name")));
            if (metaSec.contains("lore")) meta.setLore(colorList(metaSec.getStringList("lore")));
            ConfigurationSection ench = metaSec.getConfigurationSection("enchantments");
            if (ench != null) {
                for (String key : ench.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByName(key.equalsIgnoreCase("DURABILITY") ? "UNBREAKING" : key);
                    if (enchantment == null) enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                    if (enchantment != null) meta.addEnchant(enchantment, ench.getInt(key), true);
                }
            }
            for (String flag : metaSec.getStringList("item-flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag));
                } catch (IllegalArgumentException ignored) {
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Ustawia przedmiot używany jako "odłamek" - zapisuje go w PEŁNI 1:1
     * (wszystkie NBT/meta: nazwa, lore, zaklęcia, custom model data, unbreakable,
     * item-flagi, PersistentDataContainer, attribute modifiers itd.), bo zamiast
     * czytelnego YAML-a (Bukkit ItemStack -> Map -> YAML, które w pewnych
     * przypadkach potrafi pominąć bardziej egzotyczne dane) zapisujemy surowe
     * bajty samego obiektu ItemStack (Bukkit's ObjectOutputStream) zakodowane
     * jako Base64. Przy odczycie (getOdlamkiTemplate) dostajemy z powrotem
     * DOKŁADNIE ten sam obiekt, bajt w bajt.
     */
    public void setOdlamekTemplate(ItemStack item) {
        String serialized = serializeItemFull(item);
        cfg().set("odlamki.item-nbt", serialized);
        // Stary klucz usuwamy, żeby po restarcie nie było wątpliwości, który
        // format jest aktualnie źródłem prawdy.
        cfg().set("odlamki.item", null);
        plugin.savePatronConfig();
    }

    private String serializeItemFull(ItemStack item) {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(byteOut)) {
            out.writeObject(item);
            out.flush();
            return Base64.getEncoder().encodeToString(byteOut.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się w pełni zserializować przedmiotu odłamka (NBT): " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private ItemStack deserializeItemFull(String data) {
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(byteIn)) {
            Object obj = in.readObject();
            return obj instanceof ItemStack ? (ItemStack) obj : null;
        } catch (Exception e) {
            plugin.getLogger().severe("Nie udało się odczytać zapisanego przedmiotu odłamka (uszkodzone dane NBT?): " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void clearHeartsModifier(Player player) {
        AttributeInstance attr = maxHealth(player);
        if (attr == null) return;
        for (AttributeModifier modifier : new ArrayList<>(attr.getModifiers())) {
            boolean match = false;
            try {
                NamespacedKey key = modifier.getKey();
                if (key != null && "god_hearts".equals(key.getKey())) match = true;
            } catch (Throwable ignored) {
            }
            try {
                if (HEARTS_MODIFIER_NAME.equals(modifier.getName()) || HEARTS_MODIFIER_ID.equals(modifier.getUniqueId())) {
                    match = true;
                }
            } catch (Throwable ignored) {
            }
            if (match) attr.removeModifier(modifier);
        }
    }

    private AttributeInstance maxHealth(Player player) {
        try {
            org.bukkit.attribute.Attribute attr = org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("max_health"));
            if (attr == null) return null;
            return player.getAttribute(attr);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String menuTitle() {
        return color(cfg().getString("menuName", "&8ʟᴏꜱᴏᴡᴀɴɪᴇ ᴘᴀᴛʀᴏɴᴀ"));
    }

    private String displayName(String god) {
        if (god == null) return color(cfg().getString("nullPatron", "&c&lʙʀᴀᴋ"));
        return color(cfg().getString(key(god) + "Name", god));
    }

    /**
     * Prefiks kluczy w patron.yml: chewbaccaName, bobaFettHour, hanSoloBossbar,
     * darthVaderWeaknessChance, yodaResistance...
     */
    private String key(String god) {
        return switch (god.toUpperCase(Locale.ROOT)) {
            case "BOBAFETT" -> "bobaFett";
            case "HANSOLO" -> "hanSolo";
            case "DARTHVADER" -> "darthVader";
            default -> god.toLowerCase(Locale.ROOT);
        };
    }

    /** Nazwa sekcji ikony w menu GUI (chewbacca:, bobafett:, hansolo:, darthvader:, yoda:). */
    private String guiKey(String god) {
        return god.toLowerCase(Locale.ROOT);
    }

    private List<String> replaceList(List<String> input, Player player) {
        String patron = getPatron(player);
        String patronName = patron == null ? color(cfg().getString("nullPatron", "&c&lʙʀᴀᴋ")) : displayName(patron);
        long last = data.getLong("players." + player.getUniqueId() + ".lastDraw", 0L);
        long freeEvery = parseDuration(cfg().getString("freeReset", "7d"));
        long remain = Math.max(0L, last + freeEvery - System.currentTimeMillis());
        long until = data.getLong("players." + player.getUniqueId() + ".until", 0L);
        long expire = Math.max(0L, until - System.currentTimeMillis());
        List<String> out = new ArrayList<>();
        for (String line : input) {
            out.add(line.replace("{PATRON}", patronName)
                    .replace("{TIME}", remain == 0 ? "&aTeraz" : formatDuration(remain))
                    .replace("{EXPIRE}", expire == 0 ? "&cbrak" : formatDuration(expire))
                    .replace("{PLAYER}", player.getName()));
        }
        return out;
    }

    private ItemStack skull(String texture, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(colorList(lore));
        if (texture != null && !texture.isEmpty()) {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                profile.getTextures().setSkin(new URL("http://textures.minecraft.net/texture/" + texture));
                meta.setOwnerProfile(profile);
            } catch (Exception ignored) {
            }
        }
        head.setItemMeta(meta);
        return head;
    }

    @SuppressWarnings("unchecked")
    private ItemStack buildConfigItem(Map<String, Object> itemMap) {
        Material mat = Material.matchMaterial(String.valueOf(itemMap.getOrDefault("material", "STONE")));
        if (mat == null) mat = Material.STONE;
        ItemStack item = new ItemStack(mat);
        Object metaObj = itemMap.get("item-meta");
        if (metaObj instanceof Map<?, ?> metaMap) {
            ItemMeta meta = item.getItemMeta();
            Object dn = metaMap.get("display-name");
            if (dn != null) meta.setDisplayName(color(String.valueOf(dn)));
            Object lore = metaMap.get("lore");
            if (lore instanceof List<?> list) {
                List<String> colored = new ArrayList<>();
                for (Object o : list) colored.add(color(String.valueOf(o)));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String color(String msg) {
        if (msg == null || msg.isEmpty()) return "";
        Matcher legacy = LEGACY_HEX.matcher(msg);
        StringBuffer hexed = new StringBuffer();
        while (legacy.find()) {
            String raw = legacy.group(1).replace("&", "");
            StringBuilder repl = new StringBuilder("§x");
            for (char c : raw.toCharArray()) repl.append("§").append(c);
            legacy.appendReplacement(hexed, repl.toString());
        }
        legacy.appendTail(hexed);
        Matcher matcher = HEX.matcher(hexed.toString());
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append("§").append(c);
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private List<String> colorList(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(color(s));
        return out;
    }

    private String resolveGodName(String input) {
        if (input == null) return null;
        String upper = input.toUpperCase(Locale.ROOT).replace(" ", "").replace("ᴀ", "A");
        for (String god : GODS) {
            if (god.equalsIgnoreCase(input) || god.equalsIgnoreCase(upper)) return god;
        }
        String stripped = ChatColor.stripColor(color(input)).toLowerCase(Locale.ROOT);
        for (String god : GODS) {
            String shown = ChatColor.stripColor(displayName(god)).toLowerCase(Locale.ROOT);
            if (shown.contains(stripped) || stripped.contains(god.toLowerCase(Locale.ROOT))) return god;
        }
        return null;
    }

    private PotionEffectType resolveEffect(String name) {
        if (name == null) return null;
        PotionEffectType type = PotionEffectType.getByName(name);
        if (type != null) return type;
        String mapped = switch (name.toUpperCase(Locale.ROOT)) {
            case "INCREASE_DAMAGE" -> "STRENGTH";
            case "SLOW" -> "SLOWNESS";
            case "CONFUSION" -> "NAUSEA";
            default -> name;
        };
        type = PotionEffectType.getByName(mapped);
        if (type != null) return type;
        try {
            return Registry.EFFECT.get(NamespacedKey.minecraft(mapped.toLowerCase(Locale.ROOT)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null || name.isBlank()) return fallback;
        // WAŻNE: NIE wolno wywoływać Sound.valueOf(...). Od pewnej wersji Paper/Spigot
        // (system rejestrów dla dźwięków z resource packów) "Sound" przestał być zwykłym
        // enumem, więc skompilowana metoda Sound.valueOf(String) wysypuje się w runtime
        // błędem "IncompatibleClassChangeError: ... must be Methodref constant".
        // TO DOKŁADNIE powodowało, że klik w przycisk "losuj" wyglądał, jakby nic nie
        // robił - błąd leciał w środku obsługi kliknięcia, zanim cokolwiek zdążyło się
        // wydarzyć. Registry.SOUNDS działa bezpiecznie niezależnie od tego, czy Sound
        // jest enumem czy nowym typem rejestrowym.
        String key = name.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        try {
            NamespacedKey nsKey = key.contains(":") ? NamespacedKey.fromString(key) : NamespacedKey.minecraft(key);
            if (nsKey != null) {
                Sound sound = Registry.SOUNDS.get(nsKey);
                if (sound != null) return sound;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private BarColor parseBarColor(String name) {
        try {
            return BarColor.valueOf(name);
        } catch (Throwable e) {
            return BarColor.PURPLE;
        }
    }

    private BarStyle parseBarStyle(String name) {
        try {
            return BarStyle.valueOf(name);
        } catch (Throwable e) {
            return BarStyle.SOLID;
        }
    }

    private long parseDuration(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86_400_000L;
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3_600_000L;
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60_000L;
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1_000L;
            return Long.parseLong(s) * 1_000L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String formatDuration(long ms) {
        long s = ms / 1000;
        long d = s / 86400;
        s %= 86400;
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;
        List<String> parts = new ArrayList<>();
        if (d > 0) parts.add(d + "d");
        if (h > 0) parts.add(h + "h");
        if (m > 0) parts.add(m + "min");
        parts.add(s + "s");
        return String.join(", ", parts);
    }

    private String formatClock(int sec) {
        return (sec / 60) + "m " + (sec % 60) + "s";
    }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (label.equalsIgnoreCase("mistrz") || label.equalsIgnoreCase("patron") || label.equalsIgnoreCase("bohater")) {
            if (sender instanceof Player p) openGUI(p);
            else sender.sendMessage("Tylko gracz.");
            return true;
        }

        if (label.equalsIgnoreCase("glosuj")) {
            if (!votingActive) {
                sender.sendMessage(color(cfg().getString("glosowanie.wiadomosci.brak-glosowania", "&cObecnie nie trwa żadne głosowanie!")));
                return true;
            }
            if (!(sender instanceof Player p)) return true;
            if (votedPlayers.contains(p.getUniqueId())) {
                p.sendMessage(color(cfg().getString("glosowanie.wiadomosci.juz-oddany", "&cJuż oddałeś głos!")));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(color(cfg().getString("glosowanie.wiadomosci.uzycie", "&4☹ &cUżycie: &4/glosuj <nazwa>")));
                return true;
            }
            String vote = args[0];
            if (vote.equalsIgnoreCase(voteOption1)) {
                votes1++;
                votedPlayers.add(p.getUniqueId());
                p.sendMessage(color(cfg().getString("glosowanie.wiadomosci.zaglosowano", "&aZagłosowałeś na {OPCJA}").replace("{OPCJA}", voteOption1)));
            } else if (vote.equalsIgnoreCase(voteOption2)) {
                votes2++;
                votedPlayers.add(p.getUniqueId());
                p.sendMessage(color(cfg().getString("glosowanie.wiadomosci.zaglosowano", "&aZagłosowałeś na {OPCJA}").replace("{OPCJA}", voteOption2)));
            } else {
                p.sendMessage(color(cfg().getString("glosowanie.wiadomosci.brak-opcji", "&cNie ma takiej opcji! Wybierz: {OPCJA1} lub {OPCJA2}")
                        .replace("{OPCJA1}", voteOption1).replace("{OPCJA2}", voteOption2)));
            }
            if (activeBossBar != null) activeBossBar.setTitle(formatVoteTitle());
            return true;
        }

        if (label.equalsIgnoreCase("adminmistrz")) {
            if (!sender.hasPermission("mistrz.admin")) {
                sender.sendMessage(color("&cBrak uprawnień"));
                return true;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("forcehour")) {
                String type = resolveGodName(args[1]);
                if (type == null) {
                    sender.sendMessage(color("&cNieznany bóg. Dostępni: " + String.join(", ", GODS)));
                    return true;
                }
                startSpecialHour(type);
                sender.sendMessage(color("&aWymuszono godzinę: " + type));
                return true;
            }
            if (args.length >= 3 && args[0].equalsIgnoreCase("vote")) {
                int duration = 120;
                if (args.length >= 4) {
                    try {
                        duration = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(color("&cPodaj poprawny czas w sekundach!"));
                        return true;
                    }
                }
                startVoting(args[1], args[2], duration);
                sender.sendMessage(color(cfg().getString("glosowanie.wiadomosci.start-admin", "&aRozpoczęto głosowanie: {OPCJA1} vs {OPCJA2} ({CZAS}s)")
                        .replace("{OPCJA1}", args[1]).replace("{OPCJA2}", args[2]).replace("{CZAS}", String.valueOf(duration))));
                return true;
            }
            sender.sendMessage(color("&cUżycie: /adminmistrz forcehour <bóg> | vote <bóg1> <bóg2> [sekundy]"));
            return true;
        }
        return true;
    }
}
