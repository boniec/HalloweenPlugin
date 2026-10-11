package boniec.dev.polezamek;

import boniec.dev.polezamek.eventitems.EventItemsGUI;
import boniec.dev.polezamek.eventitems.EventItemsListener;
import boniec.dev.polezamek.eventitems.EventItemsManager;
import boniec.dev.polezamek.eventitems.KozaArenaManager;
import boniec.dev.polezamek.eventitems.ItemRefreshListener;
import boniec.dev.polezamek.eventitems.SauronGearListener;
import boniec.dev.polezamek.eventitems.SauronGearManager;
import boniec.dev.polezamek.eventitems.SauronGearTask;
import boniec.dev.polezamek.polesmierci.PoleSmierciCommands;
import boniec.dev.polezamek.polesmierci.PoleSmierciListener;
import boniec.dev.polezamek.polesmierci.PoleSmierciManager;
import boniec.dev.polezamek.zamek.LodManager;
import boniec.dev.polezamek.zamek.ZamekCommands;
import boniec.dev.polezamek.zamek.ZamekListener;
import boniec.dev.polezamek.zamek.ZamekManager;
import boniec.dev.polezamek.util.ChunkLockManager;
import boniec.dev.polezamek.util.TargetImmunity;
import boniec.dev.polezamek.util.WorldGuardUtil;
import boniec.dev.polezamek.witchwand.HookManager;
import boniec.dev.polezamek.witchwand.WitchWandCommands;
import boniec.dev.polezamek.witchwand.WitchWandListener;
import boniec.dev.polezamek.witchwand.WitchWandManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * UWAGA: ta klasa NIE jest wlasnym JavaPlugin - jest zwyklym "menedzerem" modulu
 * PoleZamek (Pole Smierci, Zamek, Rozdzka Wiedzmy, Hak, Event Items), uruchamianym
 * z wnetrza boniec.dev.inventory.Main#onEnable() (jedyna klasa glowna jara, wskazana
 * w plugin.yml). Bukkit tworzy tylko jedna instancje JavaPlugin na jar, wiec drugi
 * "extends JavaPlugin" nigdy nie byl uruchamiany - przez to nie dzialal /eventitems
 * ani zadna inna komenda tego modulu.
 * <p>
 * Miejsca wymagajace prawdziwego org.bukkit.plugin.Plugin (scheduler, NamespacedKey,
 * rejestracja eventow) uzywaja {@link #getHost()}.
 */
public final class Main {

    private final JavaPlugin host;

    private PoleSmierciManager poleSmierciManager;
    private ZamekManager zamekManager;
    private LodManager lodManager;
    private ChunkLockManager chunkLockManager;
    private TargetImmunity targetImmunity;
    private EventItemsManager eventItemsManager;
    private ItemRefreshListener itemRefreshListener;
    private SauronGearManager sauronGearManager;
    private SauronGearTask sauronGearTask;
    private WitchWandManager witchWandManager;
    private HookManager hookManager;
    private KozaArenaManager kozaArenaManager;
    private DuelManager duelManager;
    private boolean worldGuardEnabled;

    public Main(JavaPlugin host) {
        this.host = host;
    }

    /** Prawdziwy JavaPlugin (Inventory) - wszedzie tam, gdzie Bukkit wymaga Plugin. */
    public JavaPlugin getHost() {
        return host;
    }

    public FileConfiguration getConfig() {
        return host.getConfig();
    }

    public void reloadConfig() {
        host.reloadConfig();
    }

    public Logger getLogger() {
        return host.getLogger();
    }

    public Server getServer() {
        return host.getServer();
    }

    public File getDataFolder() {
        return host.getDataFolder();
    }

    public void saveResource(String path, boolean replace) {
        host.saveResource(path, replace);
    }

    /** Wywolywane z Inventory Main#onEnable() (zamiast wlasnego onEnable()). */
    public void wlacz() {
        worldGuardEnabled = isWorldGuardInstalled();
        if (worldGuardEnabled) {
            getLogger().info("WorldGuard wykryte - dodatkowe sprawdzanie regionow (spawn/safe/strefa) aktywne.");
        } else {
            getLogger().info("WorldGuard nie jest zainstalowane - sprawdzanie regionow WorldGuard pominiete (dzialaja nadal zapisane lokalizacje spawnu).");
        }

        // Wspolna blokada chunku (15s) - pole smierci i zamek blokuja sie wzajemnie.
        int chunkLockSeconds = getConfig().getInt("wspolna-blokada-chunku-sekundy", 15);
        chunkLockManager = new ChunkLockManager(chunkLockSeconds);

        // Ochrona osoby przed podwojnym zamkiem / polem smierci (ochrona-celu-sekundy, domyslnie 60s)
        targetImmunity = new TargetImmunity(this);

        // Zamek (tworzony przed Polem Smierci, bo pole musi wiedziec czy stoi zamek)
        zamekManager = new ZamekManager(this);
        zamekManager.load();

        // Pole Smierci
        poleSmierciManager = new PoleSmierciManager(this);
        poleSmierciManager.load();
        Bukkit.getPluginManager().registerEvents(new PoleSmierciListener(this, poleSmierciManager, chunkLockManager, targetImmunity, zamekManager), host);
        PoleSmierciCommands poleSmierciCommands = new PoleSmierciCommands(this, poleSmierciManager);
        registerCommand("dajpolesmierci", poleSmierciCommands);
        registerCommand("zresetujpolesmierci", poleSmierciCommands);
        registerCommand("polesmierci", poleSmierciCommands);

        // Zamek + Lod na patyku
        lodManager = new LodManager(this);
        Bukkit.getPluginManager().registerEvents(new ZamekListener(this, zamekManager, lodManager, poleSmierciManager, chunkLockManager), host);
        ZamekCommands zamekCommands = new ZamekCommands(this, zamekManager, lodManager);
        registerCommand("dajzamek", zamekCommands);
        registerCommand("dajlodnapatyku", zamekCommands);
        registerCommand("dajkokos", zamekCommands);
        registerCommand("zresetujcooldown", zamekCommands);
        registerCommand("zamekselector", zamekCommands);
        registerCommand("ustawzamek", zamekCommands);
        registerCommand("adminzamek", zamekCommands);

        // Rozdzka Wiedzmy + Hak
        witchWandManager = new WitchWandManager(this);
        hookManager = new HookManager(this);
        Bukkit.getPluginManager().registerEvents(new WitchWandListener(this, witchWandManager, hookManager), host);
        Bukkit.getPluginManager().registerEvents(hookManager, host);
        WitchWandCommands witchWandCommands = new WitchWandCommands(this, witchWandManager, hookManager);
        registerCommand("dajrozdzke", witchWandCommands);
        registerCommand("dajhak", witchWandCommands);
        registerCommand("zresetujrozdzke", witchWandCommands);

        // Event Items (Kokos, Lampion, Antycobweb, Rozdzka Wiedzmy, Hak)
        eventItemsManager = new EventItemsManager(this);
        EventItemsListener eventItemsListener = new EventItemsListener(this, eventItemsManager);
        Bukkit.getPluginManager().registerEvents(eventItemsListener, host);
        kozaArenaManager = new KozaArenaManager(this, eventItemsManager);
        Bukkit.getPluginManager().registerEvents(kozaArenaManager, host);
        duelManager = new DuelManager(this, kozaArenaManager);
        Bukkit.getPluginManager().registerEvents(duelManager, host);
        registerCommand("duel", new DuelCommand(duelManager));
        registerCommand("aduel", new ADuelCommand(duelManager));
        // Miecz Saurona + Glowy Smokow (enchanty z /ulepsz). Rejestrowane PRZED HitSystemListener
        // (ten rejestruje sie w Inventory Main dopiero po wlacz()), zeby miecz dostal enchanty przed liczeniem dmg.
        sauronGearManager = new SauronGearManager(this);
        sauronGearTask = new SauronGearTask(sauronGearManager);
        sauronGearTask.runTaskTimer(host, 20L, SauronGearTask.PERIOD_TICKS);
        Bukkit.getPluginManager().registerEvents(new SauronGearListener(sauronGearManager, sauronGearTask), host);

        EventItemsGUI eventItemsGUI = new EventItemsGUI(this, eventItemsManager, sauronGearManager, zamekManager, lodManager, poleSmierciManager, witchWandManager, hookManager);
        Bukkit.getPluginManager().registerEvents(eventItemsGUI, host);
        registerCommand("eventitems", eventItemsGUI);

        // Odswiezanie nazw/lore itemow po zmianie configu
        itemRefreshListener = new ItemRefreshListener(this);
        Bukkit.getPluginManager().registerEvents(itemRefreshListener, host);

        // Reload command
        registerCommand("polezamekreload", new ReloadCommand(this));
        registerCommand("resetujcooldown", new ResetujCooldownCommand(this));

        getLogger().info("PoleZamek wlaczony.");
    }

    /** Wywolywane z Inventory Main#onDisable(). */
    public void wylacz() {
        if (sauronGearTask != null) {
            sauronGearTask.cancel();
        }
        if (poleSmierciManager != null) {
            poleSmierciManager.shutdown();
        }
        if (zamekManager != null) {
            zamekManager.shutdown();
        }
        if (duelManager != null) {
            duelManager.zakonczWylaczanie();
        }
    }

    private void registerCommand(String name, Object executor) {
        if (host.getCommand(name) != null) {
            host.getCommand(name).setExecutor((org.bukkit.command.CommandExecutor) executor);
            if (executor instanceof org.bukkit.command.TabCompleter tc) {
                host.getCommand(name).setTabCompleter(tc);
            }
        } else {
            getLogger().warning("Nie znaleziono komendy '" + name + "' w plugin.yml!");
        }
    }

    public boolean isWorldGuardInstalled() {
        var wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        return wg != null && wg.isEnabled();
    }

    /**
     * Czy w podanej lokalizacji eventowe przedmioty z /eventitems maja byc
     * zablokowane (ciche anulowanie uzycia)?
     *
     * Regiony WorldGuard sa porownywane ze sloami z
     * eventowe-przedmioty.blocked-region-keywords (nazwa regionu musi zawierac
     * dane slowo). Bez WorldGuard lub bez slow w configu zwraca false.
     */
    public boolean isEventItemUseBlocked(Location location) {
        if (location == null || !isWorldGuardInstalled()) return false;

        List<String> keywords;
        if (getConfig().contains("eventowe-przedmioty.blocked-region-keywords")) {
            keywords = getConfig().getStringList("eventowe-przedmioty.blocked-region-keywords");
        } else {
            // Stary config.yml na serwerze (bez tej sekcji) - domyslne strefy.
            keywords = List.of("bezpieczna", "spawn");
        }

        for (String keyword : keywords) {
            if (keyword == null || keyword.isEmpty()) continue;
            if (WorldGuardUtil.isInRegionContaining(location, keyword)) return true;
        }
        return false;
    }

    public PoleSmierciManager getPoleSmierciManager() {
        return poleSmierciManager;
    }

    public ZamekManager getZamekManager() {
        return zamekManager;
    }

    public LodManager getLodManager() {
        return lodManager;
    }

    public TargetImmunity getTargetImmunity() {
        return targetImmunity;
    }

    public ChunkLockManager getChunkLockManager() {
        return chunkLockManager;
    }

    public EventItemsManager getEventItemsManager() {
        return eventItemsManager;
    }

    public SauronGearManager getSauronGearManager() {
        return sauronGearManager;
    }

    public WitchWandManager getWitchWandManager() {
        return witchWandManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    /** Resetuje cooldowny zamku, pola, hakow, rozdzki i eventowek (takze vanilla item CD). */
    public void resetEventCooldowns(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (zamekManager != null) zamekManager.resetCooldown(player);
        if (lodManager != null) lodManager.resetPlayer(player);
        if (poleSmierciManager != null) poleSmierciManager.resetPlayer(player);
        if (witchWandManager != null) witchWandManager.resetPlayer(player);
        if (hookManager != null) hookManager.resetPlayer(player);
        if (eventItemsManager != null) eventItemsManager.resetAllCooldowns(uuid);
        if (targetImmunity != null) targetImmunity.clear(uuid);

        for (Material mat : List.of(
                ZamekManager.ZAMEK_MATERIAL,
                LodManager.LOD_MATERIAL,
                PoleSmierciManager.ITEM_MATERIAL,
                WitchWandManager.WAND_MATERIAL,
                HookManager.HOOK_MATERIAL,
                EventItemsManager.KOKOS_MATERIAL,
                EventItemsManager.LAMPION_MATERIAL,
                EventItemsManager.ANTYCOBWEB_MATERIAL,
                EventItemsManager.OLEJ_MATERIAL,
                EventItemsManager.MOC_VADERA_MATERIAL,
                EventItemsManager.MOC_YODY_MATERIAL,
                EventItemsManager.CUKIERKI_MATERIAL,
                EventItemsManager.KAMIEN_ODRODZENIA_MATERIAL,
                EventItemsManager.KOZA_MATERIAL,
                EventItemsManager.EVENTOWKA_RANDOM_MATERIAL,
                EventItemsManager.KOSA_GRABAZA_MATERIAL,
                EventItemsManager.GWOZDZ_TRUMNY_MATERIAL,
                Material.ELYTRA,
                Material.STICK,
                Material.FERMENTED_SPIDER_EYE,
                Material.WOODEN_AXE,
                Material.FIREWORK_ROCKET
        )) {
            player.setCooldown(mat, 0);
        }
    }

    public void reloadManagers() {
        targetImmunity.reloadConfig(this);
        int chunkLockSeconds = getConfig().getInt("wspolna-blokada-chunku-sekundy", 15);
        chunkLockManager = new ChunkLockManager(chunkLockSeconds);

        poleSmierciManager.reloadConfig(this);
        poleSmierciManager.load();

        zamekManager.reloadConfig(this);
        zamekManager.load();

        lodManager.reloadConfig(this);

        eventItemsManager.reloadConfig(this);
        sauronGearManager.reloadConfig();

        witchWandManager.reloadConfig(this);
        hookManager.reloadConfig(this);

        // Zaktualizuj nazwy/lore itemow, ktore gracze juz maja w ekwipunkach
        if (itemRefreshListener != null) {
            itemRefreshListener.refreshAllOnline();
        }

        getLogger().info("Manadżerowie PoleZamek przeladowani.");
    }
}
