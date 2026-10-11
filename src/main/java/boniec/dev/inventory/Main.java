package boniec.dev.inventory;

import boniec.code.core.AdminEnchantCommand;
import boniec.code.core.AdminKodCommand;
import boniec.code.core.AdminUpgradeCommand;
import boniec.code.core.CodeManager;
import boniec.code.core.EnchantLoreManager;
import boniec.code.core.InventoryExpansion;
import boniec.code.core.Kity;
import boniec.code.core.KodCommand;
import boniec.code.core.MistrzSystem;
import boniec.code.core.UpgradeManager;
import boniec.code.core.UpgradeMenuListener;
import boniec.code.anticheat.CobwebGuard;
import boniec.code.anticheat.Elytratarget;
import boniec.code.anticheat.Fullbox;
import boniec.boss.AdminBossCommand;
import boniec.boss.AdminZeusCommand;
import boniec.boss.MinotaurDropGui;
import boniec.boss.MinotaurListener;
import boniec.boss.MinotaurManager;
import boniec.boss.ZeusDropGui;
import boniec.boss.ZeusListener;
import boniec.boss.ZeusManager;
import boniec.boss.GoblinDropGui;
import boniec.boss.GoblinListener;
import boniec.boss.GoblinManager;
import boniec.boss.AdminGoblinCommand;
import boniec.boss.GoblinExpansion;
import boniec.eventodlamkow.EventOdlamkowCommand;
import boniec.eventodlamkow.EventOdlamkowManager;
import boniec.generatory.AdminGeneratorCommand;
import boniec.generatory.GeneratorManager;
import boniec.gornik.GornikCommand;
import boniec.gornik.GornikManager;
import boniec.gornik.MiningListener;
import boniec.hitsystem.cmd.HitSystemCommand;
import boniec.hitsystem.core.HitSystemManager;
import boniec.hitsystem.listener.HitSystemListener;
import boniec.klany.cmd.AdminKlanCommand;
import boniec.klany.cmd.KlanCommand;
import boniec.klany.core.KlanManager;
import boniec.klany.gui.GuiListener;
import boniec.klany.listener.PvpListener;
import boniec.klany.placeholder.KlanyExpansion;
import boniec.rynek.Rynek;
import boniec.code.core.metin.MetinManager;
import boniec.code.core.metin.MetinCommand;
import boniec.antylogout.AntyLogoutCommand;
import boniec.antylogout.AntyLogoutConfig;
import boniec.antylogout.AntyLogoutListener;
import boniec.antylogout.AntyLogoutManager;
import boniec.warp.WarpManager;
import boniec.warp.WarpCommand;
import boniec.warp.AdminWarpCommand;

import boniec.code.core.backup.BackupCommand;
import boniec.code.core.backup.BackupDatabase;
import boniec.code.core.backup.BackupGUI;
import boniec.code.core.backup.BackupJoinListener;
import boniec.code.core.backup.BackupManager;
import boniec.code.core.backup.CofnijBackupCommand;
import boniec.code.core.backup.OdbierzBackupCommand;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Main extends JavaPlugin implements Listener {

    private static final Set<Material> STEALABLE_ITEMS = EnumSet.of(
            Material.ELYTRA,
            Material.NETHERITE_AXE,
            Material.NETHERITE_SWORD,
            Material.PLAYER_HEAD,
            Material.LEATHER_HELMET,
            Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.LEATHER_BOOTS
    );

    /** Ułamek itemów z STEALABLE_ITEMS, który zawsze wraca ofierze (min. 1 sztuka, jeśli cokolwiek miała). */
    private static final double RETURN_FRACTION = 0.35;

    private static final Set<Material> PRIORITY_ITEMS = EnumSet.of(
            Material.FERMENTED_SPIDER_EYE,
            Material.HONEYCOMB,
            Material.STICK,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.LANTERN
    );

    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    /** true = ekwipunek po smierci naprawde zostal; false = keepInventory tylko technicznie (PvP), eq zabrane. */
    private final Map<UUID, Boolean> eqZachowanyPoSmierci = new HashMap<>();

    // Przedmioty, ktore realnie przepadly graczowi przy ostatniej smierci (nie wracaja
    // przy respawnie). Backup (DeathListener, priorytet MONITOR) czyta stad, zeby zapisac
    // tylko to, co faktycznie stracone - a nie caly ekwipunek sprzed okradania.
    private final Map<UUID, List<ItemStack>> lastDeathLosses = new HashMap<>();

    /**
     * Zwraca (i usuwa) liste przedmiotow faktycznie utraconych przez gracza przy ostatniej
     * smierci obsluzonej przez system kradziezy. Wywolywane przez DeathListener z backupu,
     * ktory musi zadzialac PO tym listenerze (priority MONITOR), zeby zobaczyc finalny wynik.
     * Zwraca null, jesli ten listener w ogole nie obslugiwal danej smierci (np. brak zabojcy
     * i nie byl to void-death) - wtedy caly ekwipunek gracza zostal przy nim, backup nie jest
     * potrzebny.
     */
    public List<ItemStack> takeLastDeathLosses(UUID uuid) {
        return lastDeathLosses.remove(uuid);
    }

    private Kity kity;
    private MistrzSystem mistrzSystem;
    private File patronFile;
    private FileConfiguration patronConfig;
    private File ulepszConfigFile;
    private FileConfiguration ulepszConfig;
    private boolean worldGuardEnabled;

    private File klanyConfigFile;
    private FileConfiguration klanyConfig;
    private KlanManager klanManager;
    private boniec.klany.core.KlanWalutaManager klanWalutaManager;
    private boniec.klany.listener.NametagKolorListener nametagKolorListener;
    private boniec.klany.util.TopNpcUpdater topNpcUpdater;
    private boniec.scoreboard.SidebarManager sidebarManager;
    private boniec.koth.KothManager kothManager;
    private boniec.serce.SerceManager serceManager;
    private boniec.welna.WelnaDropManager welnaDropManager;
    private boniec.nekromanta.NekromantaManager nekromantaManager;
    private boniec.coinflip.CoinflipManager coinflipManager;
    private boniec.shulker.ShulkerOpenListener shulkerOpenListener;
    private boniec.mapareset.MapaResetManager mapaResetManager;
    private boniec.bezelek.BezelekElytraListener bezelekElytra;
    private boniec.szaman.SzamanPlugin szaman;
    private GornikManager gornikManager;
    private boniec.poziomy.PoziomyManager poziomyManager;
    private boniec.misje.MisjeManager misjeManager;
    private boniec.code.core.TworcyTryb tworcyTryb;
    private Object poziomyExpansion;
    private boniec.portfel.PortfelManager portfelManager;
    private boniec.sklep.SklepManager sklepManager;
    private Object portfelExpansion;
    private boniec.gornik.PrivateTurboManager privateTurboManager;
    private GeneratorManager generatorManager;
    private EventOdlamkowManager eventOdlamkowManager;
    private Rynek rynek;
    private boniec.bany.BanManager banManager;
    private boniec.ustawienia.UstawieniaManager ustawieniaManager;
    private boniec.tiktok.TiktokManager tiktokManager;
    private boniec.dev.polezamek.Main poleZamek;
    private HitSystemManager hitSystemManager;
    private UpgradeManager upgradeManager;
    private boniec.code.core.MeteoriteManager meteoriteManager;
    private boniec.code.core.Osiagniecia osiagniecia;

    // OBA BOSSOWIE JEDNOCZEŚNIE:
    private MinotaurManager minotaurManager;
    private ZeusManager zeusManager;
    private GoblinManager goblinManager;
    private WarpManager warpManager;

    private Object minotaurExpansion;
    private Object zeusExpansion;
    private Object goblinExpansion;
    private Object szalExpansion;

    private boniec.ekonomia.czeki.CzekiManager czekiManager;
    private boniec.ekonomia.chatcooldown.ChatCooldownManager chatCooldownManager;

    // ── System kodów promocyjnych ────────────────────────────────────────
    private CodeManager codeManager;
    private boniec.nagroda.NagrodaManager nagrodaManager;
    private boniec.ahalloween.AHalloweenManager aHalloweenManager;

    // ── System Metinów ───────────────────────────────────────────────────
    private MetinManager metinManager;

    // ── System Antylogout (tag walki) ────────────────────────────────────
    private AntyLogoutManager antyLogoutManager;

    // ── System AdminAbuse (/adminabuse start|stop) ───────────────────────
    private boniec.abuse.AdminAbuseManager adminAbuse;

    private static Main instance;

    // ── System Backupów ──────────────────────────────────────────────────
    private BackupDatabase backupDatabase;
    private BackupManager backupManager;
    private BackupGUI backupGui;

    // ── System skrzynek (/acase) ──
    private boniec.skrzynki.CaseManager caseManager;
    private boniec.afk.AfkStrefa afkStrefa;

    // ── Komendy admina: /vanish /gm /fly /god /heal /feed /steruj ──
    private boniec.adminutil.AdminUtilManager adminUtilManager;
    private boniec.adminutil.SterujManager sterujManager;

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        boniec.code.core.CoreDatabase.open(this);
        try {
            boniec.code.core.UstawieniaDatabase.open(this);
        } catch (java.sql.SQLException e) {
            getLogger().severe("[SQLite] Nie mozna zainicjowac ustawien w data.db: " + e.getMessage());
        }
        int stare = boniec.boss.BossbarUtil.usunWszystkie(this);
        if (stare > 0) {
            getLogger().info("Usunieto " + stare + " starych bossbarow z poprzedniego uruchomienia.");
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        tworcyTryb = new boniec.code.core.TworcyTryb(this);
        Bukkit.getPluginManager().registerEvents(tworcyTryb, this);
        if (getCommand("trybtworcy") != null) {
            getCommand("trybtworcy").setExecutor(tworcyTryb);
            getCommand("trybtworcy").setTabCompleter(tworcyTryb);
        }
        boniec.dev.inventory.utility.UtilityCommands utilityCommands =
                new boniec.dev.inventory.utility.UtilityCommands(this);
        utilityCommands.register();
        adminUtilManager = new boniec.adminutil.AdminUtilManager(this);
        Bukkit.getPluginManager().registerEvents(adminUtilManager, this);
        new boniec.adminutil.AdminUtilCommands(this, adminUtilManager).register();
        new boniec.adminutil.TeleportCommands(this).register();
        new boniec.tpa.TpaCommands(this).register();
        new boniec.siema.SiemaCommand().register(this);
        sterujManager = new boniec.adminutil.SterujManager(this);
        new boniec.adminutil.SterujCommand(this, sterujManager).register();
        Bukkit.getPluginManager().registerEvents(new boniec.pvp.NoChestEquipListener(), this);
        new boniec.adminutil.InvSeeCommand(this).register();
        Bukkit.getPluginManager().registerEvents(new boniec.dev.inventory.utility.UtilityListener(), this);
        boniec.boss.BossSpawn.init(this);
        Bukkit.getPluginManager().registerEvents(new boniec.swiat.SwiatKontrola(this), this);
        shulkerOpenListener = new boniec.shulker.ShulkerOpenListener(this);
        Bukkit.getPluginManager().registerEvents(shulkerOpenListener, this);
        mapaResetManager = new boniec.mapareset.MapaResetManager(this, getConfig().getInt("mapa-reset.interwal-minut", 30));
        Bukkit.getPluginManager().registerEvents(mapaResetManager, this);
        if (getCommand("mapareset") != null) {
            getCommand("mapareset").setExecutor(mapaResetManager);
            getCommand("mapareset").setTabCompleter(mapaResetManager);
        }
        Bukkit.getPluginManager().registerEvents(new Elytratarget(), this);
        Bukkit.getPluginManager().registerEvents(new Fullbox(), this);
        Bukkit.getPluginManager().registerEvents(new CobwebGuard(), this);
        Bukkit.getPluginManager().registerEvents(new NiezniszczalnoscListener(), this);
        kity = new Kity(this);
        new boniec.joinkit.JoinKit(this);
        loadPatronConfig();
        loadUlepszConfig();

        worldGuardEnabled = isWorldGuardEnabled();
        if (worldGuardEnabled) {
            getLogger().info("WorldGuard wykryte - patroni/bogowie nie będą działać w regionach z flagą pvp na deny.");
        } else {
            getLogger().info("WorldGuard nie jest zainstalowane - blokada patronów na regionach pvp-deny nieaktywna.");
        }
        mistrzSystem = new MistrzSystem(this);
        if (getCommand("adminpatron") != null) {
            getCommand("adminpatron").setExecutor(new boniec.code.core.AdminPatronCommand(this));
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            boolean zarejestrowano = new InventoryExpansion(this).register();
            if (zarejestrowano) {
                getLogger().info("Zarejestrowano placeholdery %inventory_...% (PlaceholderAPI wykryte).");
            }
            boniec.code.core.OsobyExpansion.zarejestruj(this);
        }
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
                if ("PlaceholderAPI".equals(event.getPlugin().getName())) {
                    boniec.code.core.OsobyExpansion.zarejestruj(Main.this);
                }
            }
        }, this);

        // ── Inicjalizacja scalonego systemu Klanów ───────────────────────────
        loadKlanyConfig();
        klanManager = new KlanManager(this);
        klanManager.wczytaj();
        boniec.klany.Main.init(klanyConfig, klanManager);
        klanManager.znormalizujIPrzytnijLimity();

        klanWalutaManager = new boniec.klany.core.KlanWalutaManager(this);
        klanWalutaManager.wczytaj();
        boniec.klany.Main.setKlanWalutaManager(klanWalutaManager);

        Bukkit.getPluginManager().registerEvents(new GuiListener(), this);
        Bukkit.getPluginManager().registerEvents(new PvpListener(boniec.klany.Main.get()), this);
        Bukkit.getPluginManager().registerEvents(new boniec.klany.gui.SkarbiecChatListener(this), this);

        nametagKolorListener = new boniec.klany.listener.NametagKolorListener(this, klanManager);
        Bukkit.getPluginManager().registerEvents(nametagKolorListener, this);
        nametagKolorListener.uruchom();

        if (getCommand("klan") != null) {
            KlanCommand klanExecutor = new KlanCommand(boniec.klany.Main.get());
            getCommand("klan").setExecutor(klanExecutor);
            getCommand("klan").setTabCompleter(klanExecutor);
        }

        if (getCommand("topki") != null) {
            boniec.klany.cmd.TopkiCommand topkiExecutor = new boniec.klany.cmd.TopkiCommand();
            getCommand("topki").setExecutor(topkiExecutor);
        }

        if (getCommand("adminklan") != null) {
            AdminKlanCommand adminKlanExecutor = new AdminKlanCommand(klanManager, klanWalutaManager, this::reloadKlanyConfig);
            getCommand("adminklan").setExecutor(adminKlanExecutor);
            getCommand("adminklan").setTabCompleter(adminKlanExecutor);
        }

        if (getCommand("aresetranking") != null) {
            boniec.klany.cmd.ResetRankingCommand resetRankingExecutor = new boniec.klany.cmd.ResetRankingCommand(klanManager);
            getCommand("aresetranking").setExecutor(resetRankingExecutor);
            getCommand("aresetranking").setTabCompleter(resetRankingExecutor);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            KlanyExpansion klanyExp = new KlanyExpansion(boniec.klany.Main.get());
            klanyExp.register();
        }

        // ── Skiny NPC z topek (Citizens / FancyNPCs) - sekcja top-npc w klany-config.yml ──
        topNpcUpdater = new boniec.klany.util.TopNpcUpdater(this);
        Bukkit.getPluginManager().registerEvents(topNpcUpdater, this);
        topNpcUpdater.start();

        // ── Wlasny scoreboard (scoreboard.yml, /scoreboard) ─────────────────
        sidebarManager = new boniec.scoreboard.SidebarManager(this);
        sidebarManager.start();
        if (getCommand("scoreboard") != null) {
            boniec.scoreboard.SidebarCommand sidebarCmd = new boniec.scoreboard.SidebarCommand(sidebarManager);
            getCommand("scoreboard").setExecutor(sidebarCmd);
            getCommand("scoreboard").setTabCompleter(sidebarCmd);
        }
        ustawieniaManager = new boniec.ustawienia.UstawieniaManager(this);
        boniec.ustawienia.UstawieniaGui ustawieniaGui = new boniec.ustawienia.UstawieniaGui(this, ustawieniaManager);
        Bukkit.getPluginManager().registerEvents(ustawieniaGui, this);
        if (getCommand("ustawienia") != null) {
            getCommand("ustawienia").setExecutor(new boniec.ustawienia.UstawieniaCommand(ustawieniaGui));
        }

        boniec.eventy.EventHarmonogram.wczytaj(this);

        // ── KOTH (koth.yml, /adkoth) - korzysta ze scoreboardu powyzej ──────
        kothManager = new boniec.koth.KothManager(this, sidebarManager);
        kothManager.start();
        if (getCommand("adkoth") != null) {
            boniec.koth.KothCommand kothCmd = new boniec.koth.KothCommand(kothManager);
            getCommand("adkoth").setExecutor(kothCmd);
            getCommand("adkoth").setTabCompleter(kothCmd);
        }

        // ── Serce Kosmosu (serce.yml, /aserce) - tez korzysta ze scoreboardu ──
        serceManager = new boniec.serce.SerceManager(this, sidebarManager);
        serceManager.start();
        if (getCommand("aserce") != null) {
            boniec.serce.SerceCommand serceCmd = new boniec.serce.SerceCommand(serceManager);
            getCommand("aserce").setExecutor(serceCmd);
            getCommand("aserce").setTabCompleter(serceCmd);
        }

        // ── Drop z welny (red/green/purple/white, /awelna) ──
        welnaDropManager = new boniec.welna.WelnaDropManager(this, worldGuardEnabled);
        Bukkit.getPluginManager().registerEvents(welnaDropManager, this);
        if (getCommand("awelna") != null) {
            boniec.welna.WelnaCommand welnaCmd = new boniec.welna.WelnaCommand(welnaDropManager);
            getCommand("awelna").setExecutor(welnaCmd);
            getCommand("awelna").setTabCompleter(welnaCmd);
        }

        // ── Nekromanta: wymiana glutow na losowe miecze (/nekromanta, /anekromanta) ──
        nekromantaManager = new boniec.nekromanta.NekromantaManager(this);
        welnaDropManager.setNekromanta(nekromantaManager); // bonus z ulepszen nekromanty do dropu gluta z welny
        boniec.nekromanta.NekromantaGui nekromantaGui = new boniec.nekromanta.NekromantaGui(nekromantaManager);
        Bukkit.getPluginManager().registerEvents(nekromantaGui, this);
        if (getCommand("nekromanta") != null) {
            getCommand("nekromanta").setExecutor(new boniec.nekromanta.NekromantaCommand(nekromantaGui));
        }
        if (getCommand("anekromanta") != null) {
            boniec.nekromanta.ANekromantaCommand aNekroCmd = new boniec.nekromanta.ANekromantaCommand(nekromantaManager);
            getCommand("anekromanta").setExecutor(aNekroCmd);
            getCommand("anekromanta").setTabCompleter(aNekroCmd);
        }

        // ── Coinflip o odlamki ──
        coinflipManager = new boniec.coinflip.CoinflipManager(this);
        boniec.coinflip.CoinflipGui coinflipGui = new boniec.coinflip.CoinflipGui(coinflipManager, this);
        Bukkit.getPluginManager().registerEvents(coinflipGui, this);
        Bukkit.getPluginManager().registerEvents(new boniec.coinflip.CoinflipListener(coinflipManager), this);
        if (getCommand("coinflip") != null) {
            boniec.coinflip.CoinflipCommand cfCmd = new boniec.coinflip.CoinflipCommand(coinflipManager, coinflipGui);
            getCommand("coinflip").setExecutor(cfCmd);
            getCommand("coinflip").setTabCompleter(cfCmd);
        }

        // ── Zakaz elytr na regionie "bezelek" (bezelek.yml) - wymaga WorldGuard ──
        if (worldGuardEnabled) {
            bezelekElytra = new boniec.bezelek.BezelekElytraListener(this);
            Bukkit.getPluginManager().registerEvents(bezelekElytra, this);
            bezelekElytra.start();
        } else {
            getLogger().warning("[Bezelek] WorldGuard nie jest zainstalowany - blokada elytr nieaktywna.");
        }

        // ── Szaman ───────────────────────────────────────────────────────────
        szaman = new boniec.szaman.SzamanPlugin(this);
        szaman.wlacz(klanManager);

        // ── System Górnika ───────────────────────────────────────────────────
        gornikManager = new GornikManager(this);
        MiningListener miningListener = new MiningListener(gornikManager);
        Bukkit.getPluginManager().registerEvents(miningListener, this);
        boniec.gornik.GornikGui gornikGui = new boniec.gornik.GornikGui(gornikManager);
        Bukkit.getPluginManager().registerEvents(gornikGui, this);
        GornikCommand gornikCmd = new GornikCommand(gornikManager, miningListener, gornikGui);
        gornikCmd.setKwotaExecutor(new boniec.ekonomia.gornik.GornikCommand(this, "&8[&6GORNIK&8]&r"));
        if (getCommand("gornik") != null) {
            getCommand("gornik").setExecutor(gornikCmd);
            getCommand("gornik").setTabCompleter(gornikCmd);
        }

        // ── Poziomy postaci (port Dream-FlameHubLevels, poziomy.yml) ─────────
        poziomyManager = new boniec.poziomy.PoziomyManager(this);
        miningListener.setPoziomyManager(poziomyManager);
        Bukkit.getPluginManager().registerEvents(new boniec.poziomy.PoziomyListener(poziomyManager, worldGuardEnabled), this);
        boniec.poziomy.PoziomyGui poziomyGui = new boniec.poziomy.PoziomyGui(poziomyManager);
        Bukkit.getPluginManager().registerEvents(poziomyGui, this);
        if (getCommand("poziom") != null) {
            boniec.poziomy.PoziomCommand poziomCmd = new boniec.poziomy.PoziomCommand(poziomyManager, poziomyGui);
            getCommand("poziom").setExecutor(poziomCmd);
            getCommand("poziom").setTabCompleter(poziomCmd);
        }
        if (getCommand("apoziom") != null) {
            boniec.poziomy.APoziomCommand aPoziomCmd = new boniec.poziomy.APoziomCommand(poziomyManager, poziomyGui);
            getCommand("apoziom").setExecutor(aPoziomCmd);
            getCommand("apoziom").setTabCompleter(aPoziomCmd);
        }

        // ── Misje dnia (/misje, /amisje) ────────────────────────────────────
        misjeManager = new boniec.misje.MisjeManager(this);
        miningListener.setMisjeManager(misjeManager);
        Bukkit.getPluginManager().registerEvents(new boniec.misje.MisjeListener(misjeManager), this);
        boniec.misje.MisjeGui misjeGui = new boniec.misje.MisjeGui(misjeManager);
        Bukkit.getPluginManager().registerEvents(misjeGui, this);
        if (getCommand("misje") != null) {
            getCommand("misje").setExecutor(new boniec.misje.MisjeCommand(misjeManager, misjeGui));
        }
        if (getCommand("amisje") != null) {
            boniec.misje.AMisjeCommand aMisje = new boniec.misje.AMisjeCommand(misjeManager, misjeGui);
            getCommand("amisje").setExecutor(aMisje);
            getCommand("amisje").setTabCompleter(aMisje);
        }

        // ── Portfel vPLN (777-Portfel) ───────────────────────────────────────
        portfelManager = new boniec.portfel.PortfelManager(this);
        boniec.portfel.PortfelGui portfelGui = new boniec.portfel.PortfelGui(portfelManager);
        Bukkit.getPluginManager().registerEvents(portfelGui, this);
        Bukkit.getPluginManager().registerEvents(new boniec.portfel.PortfelJoinListener(portfelManager), this);
        if (getCommand("portfel") != null) {
            getCommand("portfel").setExecutor(new boniec.portfel.PortfelCommand(portfelGui));
        }
        if (getCommand("adminportfel") != null) {
            boniec.portfel.AdminPortfelCommand adminPortfel = new boniec.portfel.AdminPortfelCommand(portfelManager, portfelGui);
            getCommand("adminportfel").setExecutor(adminPortfel);
            getCommand("adminportfel").setTabCompleter(adminPortfel);
        }
        if (getCommand("doladowanie") != null) {
            boniec.portfel.DoladowanieCommand dolad = new boniec.portfel.DoladowanieCommand(portfelManager);
            getCommand("doladowanie").setExecutor(dolad);
            getCommand("doladowanie").setTabCompleter(dolad);
        }

        // ── Sklep serwerowy (777-Sklep) ───────────────────────────────────────
        sklepManager = new boniec.sklep.SklepManager(this);
        sklepManager.start();
        boniec.sklep.SklepGui sklepGui = new boniec.sklep.SklepGui(sklepManager);
        Bukkit.getPluginManager().registerEvents(sklepGui, this);
        if (getCommand("sklep") != null) {
            getCommand("sklep").setExecutor(new boniec.sklep.SklepCommand(sklepGui));
        }
        if (getCommand("asklep") != null) {
            boniec.sklep.AdminSklepCommand adminSklep = new boniec.sklep.AdminSklepCommand(sklepManager);
            getCommand("asklep").setExecutor(adminSklep);
            getCommand("asklep").setTabCompleter(adminSklep);
        }

        // ── FreeTurbo (prywatny TurboDrop na gracza) ──────────────────────────
        privateTurboManager = new boniec.gornik.PrivateTurboManager(this, gornikManager);
        miningListener.setPrivateTurboManager(privateTurboManager);
        if (getCommand("freeturbo") != null) {
            getCommand("freeturbo").setExecutor(new boniec.gornik.FreeTurboCommand(privateTurboManager));
        }

        // ── System Generatorów ───────────────────────────────────────────
        generatorManager = new GeneratorManager(this);
        if (getCommand("admingenerator") != null) {
            getCommand("admingenerator").setExecutor(new AdminGeneratorCommand(generatorManager));
        }

        // ── Event "Szał Odłamków" ────────────────────────────────────────────
        eventOdlamkowManager = new EventOdlamkowManager(this, klanWalutaManager);
        miningListener.setEventOdlamkow(generatorManager, eventOdlamkowManager);
        if (getCommand("eventodlamki") != null) {
            EventOdlamkowCommand eventOdlamkiCmd = new EventOdlamkowCommand(eventOdlamkowManager);
            getCommand("eventodlamki").setExecutor(eventOdlamkiCmd);
            getCommand("eventodlamki").setTabCompleter(eventOdlamkiCmd);
        }

        // ── Boss 1: Minotaur ─────────────────────────────────────────────────
        minotaurManager = new MinotaurManager(this);
        Bukkit.getPluginManager().registerEvents(new MinotaurListener(minotaurManager), this);
        MinotaurDropGui minotaurDropGui = new MinotaurDropGui(this, minotaurManager.getDropManager());
        Bukkit.getPluginManager().registerEvents(minotaurDropGui, this);
        if (getCommand("adminboss") != null) {
            AdminBossCommand adminBossCmd = new AdminBossCommand(minotaurManager, minotaurDropGui);
            getCommand("adminboss").setExecutor(adminBossCmd);
            getCommand("adminboss").setTabCompleter(adminBossCmd);
        }

        // ── Boss 2: Zeus ─────────────────────────────────────────────────────
        zeusManager = new ZeusManager(this);
        Bukkit.getPluginManager().registerEvents(new ZeusListener(zeusManager), this);
        ZeusDropGui zeusDropGui = new ZeusDropGui(this, zeusManager.getDropManager());
        Bukkit.getPluginManager().registerEvents(zeusDropGui, this);
        if (getCommand("adminzeus") != null) {
            AdminZeusCommand adminZeusCmd = new AdminZeusCommand(zeusManager, zeusDropGui);
            getCommand("adminzeus").setExecutor(adminZeusCmd);
            getCommand("adminzeus").setTabCompleter(adminZeusCmd);
        }

        if (getCommand("usunbossbar") != null) {
            getCommand("usunbossbar").setExecutor(new boniec.boss.UsunBossbarCommand(this));
        }
        getLogger().info("Systemy bossow (Minotaur i Zeus) zostaly pomyslnie wlaczone.");

        // ── Boss 3: Goblin ───────────────────────────────────────────────────
        goblinManager = new GoblinManager(this);
        Bukkit.getPluginManager().registerEvents(new GoblinListener(goblinManager), this);
        GoblinDropGui goblinDropGui = new GoblinDropGui(this, goblinManager.getDropManager());
        Bukkit.getPluginManager().registerEvents(goblinDropGui, this);
        if (getCommand("admingoblin") != null) {
            AdminGoblinCommand adminGoblinCmd = new AdminGoblinCommand(goblinManager, goblinDropGui);
            getCommand("admingoblin").setExecutor(adminGoblinCmd);
            getCommand("admingoblin").setTabCompleter(adminGoblinCmd);
        }
        getLogger().info("System bossa (Goblin) zostal pomyslnie wlaczony.");

        // ── Warpy ────────────────────────────────────────────────────────────
        warpManager = new WarpManager(this);
        if (getCommand("warp") != null) {
            WarpCommand warpCmd = new WarpCommand(warpManager);
            getCommand("warp").setExecutor(warpCmd);
            getCommand("warp").setTabCompleter(warpCmd);
        }
        if (getCommand("adminwarp") != null) {
            AdminWarpCommand adminWarpCmd = new AdminWarpCommand(warpManager);
            getCommand("adminwarp").setExecutor(adminWarpCmd);
            getCommand("adminwarp").setTabCompleter(adminWarpCmd);
        }

        boniec.portal.PortalManager portalManager = new boniec.portal.PortalManager(this);
        boniec.portal.PortalListener portalListener = new boniec.portal.PortalListener(this, portalManager);
        Bukkit.getPluginManager().registerEvents(portalListener, this);
        if (getCommand("aportal") != null) {
            boniec.portal.APortalCommand aPortal = new boniec.portal.APortalCommand(portalManager, portalListener);
            getCommand("aportal").setExecutor(aPortal);
            getCommand("aportal").setTabCompleter(aPortal);
        }

        boniec.spawn.SpawnManager spawnManager = new boniec.spawn.SpawnManager(this);
        boniec.spawn.SpawnCommand spawnCmd = new boniec.spawn.SpawnCommand(this, spawnManager);
        Bukkit.getPluginManager().registerEvents(spawnCmd, this);
        if (getCommand("spawn") != null) {
            getCommand("spawn").setExecutor(spawnCmd);
        }
        if (getCommand("ustawspawn") != null) {
            getCommand("ustawspawn").setExecutor(spawnCmd);
        }
        if (getCommand("setspawn") != null) {
            getCommand("setspawn").setExecutor(spawnCmd);
        }

        boniec.zonebox.ZoneBoxManager zoneBox = new boniec.zonebox.ZoneBoxManager(this);
        Bukkit.getPluginManager().registerEvents(new boniec.zonebox.ZoneBoxListener(zoneBox), this);
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            Bukkit.getPluginManager().registerEvents(new boniec.zonebox.ZoneBoxWgListener(zoneBox), this);
        }
        if (getCommand("azonebox") != null) {
            boniec.zonebox.ZoneBoxCommand zoneCmd = new boniec.zonebox.ZoneBoxCommand(zoneBox);
            getCommand("azonebox").setExecutor(zoneCmd);
            getCommand("azonebox").setTabCompleter(zoneCmd);
        }

        // ── PlaceholderAPI Expansions ────────────────────────────────────────
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            boniec.boss.PapiCleanup.wyrejestruj(this, "minotaur", "zeus", "goblin", "szal", "levels", "777-wallet", "osoby", "ahalloween");

            if (poziomyManager != null) {
                boniec.poziomy.PoziomyExpansion poziomyExp = new boniec.poziomy.PoziomyExpansion(this, poziomyManager);
                poziomyExp.register();
                poziomyExpansion = poziomyExp;
            }
            if (portfelManager != null) {
                boniec.portfel.PortfelExpansion portfelExp = new boniec.portfel.PortfelExpansion(this, portfelManager);
                portfelExp.register();
                portfelExpansion = portfelExp;
            }

            boniec.boss.MinotaurExpansion minoExp = new boniec.boss.MinotaurExpansion(this);
            boniec.boss.ZeusExpansion zeusExp = new boniec.boss.ZeusExpansion(this);
            GoblinExpansion goblinExp = new GoblinExpansion(this);
            boniec.eventodlamkow.SzalExpansion szalExp = new boniec.eventodlamkow.SzalExpansion(this);

            minoExp.register();
            zeusExp.register();
            goblinExp.register();
            szalExp.register();
            boniec.code.core.OsobyExpansion.zarejestruj(this);

            minotaurExpansion = minoExp;
            zeusExpansion = zeusExp;
            goblinExpansion = goblinExp;
            szalExpansion = szalExp;
        }

        // ── System Rynku ─────────────────────────────────────────────────────
        rynek = new Rynek(this);
        rynek.startTasks();
        if (getCommand("arynek") != null) {
            boniec.rynek.ARynekCommand arynekCmd = new boniec.rynek.ARynekCommand(rynek);
            getCommand("arynek").setExecutor(arynekCmd);
            getCommand("arynek").setTabCompleter(arynekCmd);
        } else {
            getLogger().warning("Komenda 'arynek' nie jest zarejestrowana w plugin.yml - /arynek nie bedzie dostepny.");
        }

        // ── System AdminAbuse ─────────────────────────────────────────────────
        adminAbuse = new boniec.abuse.AdminAbuseManager(this);
        if (getCommand("adminabuse") != null) {
            boniec.abuse.AdminAbuseCommand adminAbuseCmd = new boniec.abuse.AdminAbuseCommand(adminAbuse);
            getCommand("adminabuse").setExecutor(adminAbuseCmd);
            getCommand("adminabuse").setTabCompleter(adminAbuseCmd);
        } else {
            getLogger().warning("Komenda 'adminabuse' nie jest zarejestrowana w plugin.yml - /adminabuse nie bedzie dostepny.");
        }

        // ── /alts (zapis IP, menu kont wspoldzielacych adres) ────────────────
        try {
            banManager = new boniec.bany.BanManager(this);
            banManager.start();
            boniec.bany.BanGui banGui = new boniec.bany.BanGui(banManager);
            boniec.bany.BanCommands banCmds = new boniec.bany.BanCommands(banManager, banGui);
            Bukkit.getPluginManager().registerEvents(banGui, this);
            Bukkit.getPluginManager().registerEvents(new boniec.bany.BanListener(banManager), this);
            if (getCommand("alts") != null) {
                getCommand("alts").setExecutor(banCmds);
                getCommand("alts").setTabCompleter(banCmds);
            } else {
                getLogger().warning("[Alts] Komenda 'alts' nie jest zarejestrowana w plugin.yml.");
            }

            boniec.bany.SprawdzanieManager sprawdzanie = new boniec.bany.SprawdzanieManager(this, banManager);
            Bukkit.getPluginManager().registerEvents(sprawdzanie, this);
            if (getCommand("sprawdz") != null) {
                getCommand("sprawdz").setExecutor(sprawdzanie);
                getCommand("sprawdz").setTabCompleter(sprawdzanie);
            }
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Nie udalo sie wlaczyc systemu /alts", t);
            banManager = null;
        }

        // ── PoleZamek (Pole Śmierci, Zamek, Różdżka Wiedźmy, Hak, /eventitems) ──
        // Wyjątek w tym module nie może wywalić całego pluginu Inventory.
        try {
            poleZamek = new boniec.dev.polezamek.Main(this);
            poleZamek.wlacz();
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Nie udalo sie wlaczyc modulu PoleZamek (/eventitems itd.)", t);
            poleZamek = null;
        }

        // ── System Hitów/Obrażeń ─────────────────────────────────────────────
        hitSystemManager = new HitSystemManager(this);
        hitSystemManager.load();
        Bukkit.getPluginManager().registerEvents(new HitSystemListener(hitSystemManager), this);
        if (getCommand("hitsystem") != null) {
            HitSystemCommand hitSystemCmd = new HitSystemCommand(hitSystemManager);
            getCommand("hitsystem").setExecutor(hitSystemCmd);
            getCommand("hitsystem").setTabCompleter(hitSystemCmd);
        }

        // ── System nadawania enchantów ──────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new EnchantLoreManager(this), this);
        if (getCommand("adminenchant") != null) {
            AdminEnchantCommand adminEnchantCmd = new AdminEnchantCommand(this);
            getCommand("adminenchant").setExecutor(adminEnchantCmd);
            getCommand("adminenchant").setTabCompleter(adminEnchantCmd);
        }

        // ── System Ulepszeń ──────────────────────────────────────────────────
        upgradeManager = new UpgradeManager(this);
        boniec.azbroja.AzbrojaManager azbrojaManager = new boniec.azbroja.AzbrojaManager(this);
        boniec.azbroja.AzbrojaGui azbrojaGui = new boniec.azbroja.AzbrojaGui(azbrojaManager);
        Bukkit.getPluginManager().registerEvents(azbrojaGui, this);
        if (getCommand("azbroja") != null) {
            boniec.azbroja.AzbrojaCommand azbrojaCmd = new boniec.azbroja.AzbrojaCommand(azbrojaManager, azbrojaGui);
            getCommand("azbroja").setExecutor(azbrojaCmd);
            getCommand("azbroja").setTabCompleter(azbrojaCmd);
        }
        UpgradeMenuListener upgradeMenuListener = new UpgradeMenuListener(this, upgradeManager);
        Bukkit.getPluginManager().registerEvents(upgradeMenuListener, this);
        if (getCommand("aulepsz") != null) {
            getCommand("aulepsz").setExecutor(new AdminUpgradeCommand(this, upgradeManager, upgradeMenuListener));
        }
        if (getCommand("ulepsz") != null) {
            getCommand("ulepsz").setExecutor(upgradeMenuListener);
        }
        Bukkit.getPluginManager().registerEvents(new boniec.code.core.SwordHitListener(), this);

        // ── System Skupu ─────────────────────────────────────────────────────
        boniec.skup.SkupCommand skupCommand = new boniec.skup.SkupCommand(this, upgradeManager);
        Bukkit.getPluginManager().registerEvents(skupCommand, this);
        if (getCommand("skup") != null) {
            getCommand("skup").setExecutor(skupCommand);
        }

        // ── System Meteorytów ────────────────────────────────────────────────
        meteoriteManager = new boniec.code.core.MeteoriteManager(this);
        if (getCommand("adminmeteoryt") != null) {
            getCommand("adminmeteoryt").setExecutor(new boniec.code.core.AdminMeteoriteCommand(meteoriteManager));
        }
        if (getCommand("eventy") != null) {
            boniec.eventy.EventyCommand eventyCmd = new boniec.eventy.EventyCommand(this);
            Bukkit.getPluginManager().registerEvents(eventyCmd.getGui(), this);
            getCommand("eventy").setExecutor(eventyCmd);
            getCommand("eventy").setTabCompleter(eventyCmd);
        }

        // ── TikTok: polaczenie konta, filmy, weryfikacja nagrod ──────────────
        tiktokManager = new boniec.tiktok.TiktokManager(this);
        boniec.tiktok.TiktokGui tiktokGui = new boniec.tiktok.TiktokGui(tiktokManager);
        Bukkit.getPluginManager().registerEvents(tiktokGui, this);
        if (getCommand("tiktok") != null) {
            boniec.tiktok.TiktokCommand tiktokCmd = new boniec.tiktok.TiktokCommand(tiktokManager, tiktokGui);
            getCommand("tiktok").setExecutor(tiktokCmd);
            getCommand("tiktok").setTabCompleter(tiktokCmd);
        }
        if (getCommand("atiktok") != null) {
            boniec.tiktok.AtiktokCommand atiktokCmd = new boniec.tiktok.AtiktokCommand(tiktokManager, tiktokGui);
            getCommand("atiktok").setExecutor(atiktokCmd);
            getCommand("atiktok").setTabCompleter(atiktokCmd);
        }

        // ── System Osiągnięć ─────────────────────────────────────────────────
        osiagniecia = new boniec.code.core.Osiagniecia(this);
        boniec.klany.Main klanyPlugin = boniec.klany.Main.get();
        if (klanyPlugin != null && klanyPlugin.getKlanManager() != null) {
            boniec.klany.core.KlanManager klanManagerRef = klanyPlugin.getKlanManager();
            osiagniecia.setTierProvider(gracz -> klanManagerRef.getLaczonyRankingGracza(gracz.getUniqueId()));
        }
        if (poziomyManager != null) {
            osiagniecia.setZrodlo("POZIOM", gracz -> poziomyManager.getPoziom(gracz));
        }

        // ── System Czeków admina ─────────────────────────────────────────────
        czekiManager = new boniec.ekonomia.czeki.CzekiManager(this, "&8[&aCZEK&8]&r");
        Bukkit.getPluginManager().registerEvents(czekiManager, this);
        if (getCommand("adminczek") != null) {
            getCommand("adminczek").setExecutor(czekiManager);
        }

        // ── Przelew między graczami ──────────────────────────────────────────
        boniec.ekonomia.pay.PayCommand payCommand = new boniec.ekonomia.pay.PayCommand(this, "&8[&aPAY&8]&r");
        if (getCommand("pay") != null) {
            getCommand("pay").setExecutor(payCommand);
        }

        // ── System Ogłoszeń ──────────────────────────────────────────────────
        boniec.ekonomia.ogloszenia.OgloszeniaService ogloszeniaService =
                new boniec.ekonomia.ogloszenia.OgloszeniaService(this);
        if (getCommand("admintitle") != null) {
            boniec.ekonomia.ogloszenia.OgloszeniaManager admTitle =
                    new boniec.ekonomia.ogloszenia.OgloszeniaManager(ogloszeniaService);
            getCommand("admintitle").setExecutor(admTitle);
            getCommand("admintitle").setTabCompleter(admTitle);
        }
        if (getCommand("bc") != null) {
            boniec.ekonomia.ogloszenia.BcCommand bcCmd =
                    new boniec.ekonomia.ogloszenia.BcCommand(ogloszeniaService);
            getCommand("bc").setExecutor(bcCmd);
            getCommand("bc").setTabCompleter(bcCmd);
        }

        // ── HelpOp ───────────────────────────────────────────────────────────
        if (getCommand("helpop") != null) {
            boniec.helpop.HelpOpCommand helpOp = new boniec.helpop.HelpOpCommand(this);
            getCommand("helpop").setExecutor(helpOp);
            getCommand("helpop").setTabCompleter(helpOp);
        }

        // ── Cooldown na czacie ───────────────────────────────────────────────
        chatCooldownManager = new boniec.ekonomia.chatcooldown.ChatCooldownManager();
        Bukkit.getPluginManager().registerEvents(chatCooldownManager, this);
        if (getCommand("chatcd") != null) {
            getCommand("chatcd").setExecutor(chatCooldownManager);
        }

        // ── /chat clear | off | on ───────────────────────────────────────────
        boniec.ekonomia.chatlock.ChatLock chatLock = new boniec.ekonomia.chatlock.ChatLock(this);
        Bukkit.getPluginManager().registerEvents(chatLock, this);
        if (getCommand("chat") != null) {
            getCommand("chat").setExecutor(chatLock);
            getCommand("chat").setTabCompleter(chatLock);
        }

        // ── Format czatu (config.yml -> chat-format) ─────────────────────────
        Bukkit.getPluginManager().registerEvents(new boniec.ekonomia.chatformat.ChatFormatListener(this), this);

        // ── System Kodów Promocyjnych ────────────────────────────────────────
        codeManager = new CodeManager(this);

        if (getCommand("kod") != null) {
            getCommand("kod").setExecutor(new KodCommand(codeManager));
        }
        if (getCommand("adminkod") != null) {
            AdminKodCommand adminKodCmd = new AdminKodCommand(codeManager);
            getCommand("adminkod").setExecutor(adminKodCmd);
            getCommand("adminkod").setTabCompleter(adminKodCmd);
        }

        getLogger().info("System Kodow Promocyjnych zostal pomyslnie wlaczony.");

        // ── Nagroda za Discorda (/nagroda) ───────────────────────────────────
        try {
            nagrodaManager = new boniec.nagroda.NagrodaManager(this);
            nagrodaManager.start();
            if (getCommand("nagroda") != null) {
                boniec.nagroda.NagrodaCommand nagrodaCmd = new boniec.nagroda.NagrodaCommand(nagrodaManager);
                getCommand("nagroda").setExecutor(nagrodaCmd);
                getCommand("nagroda").setTabCompleter(nagrodaCmd);
            }
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Nie udalo sie uruchomic systemu nagrod Discord (brak biblioteki JDA?).", t);
        }

        try {
            aHalloweenManager = new boniec.ahalloween.AHalloweenManager(this, sidebarManager);
            Bukkit.getPluginManager().registerEvents(new boniec.ahalloween.AHalloweenListener(aHalloweenManager), this);
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                Bukkit.getPluginManager().registerEvents(
                        new boniec.ahalloween.HalloweenMythicListener(aHalloweenManager, this), this);
            }
            if (getCommand("ahalloween") != null) {
                boniec.ahalloween.AHalloweenCommand ahCmd = new boniec.ahalloween.AHalloweenCommand(aHalloweenManager);
                getCommand("ahalloween").setExecutor(ahCmd);
                getCommand("ahalloween").setTabCompleter(ahCmd);
                if (getCommand("kociol") != null) {
                    getCommand("kociol").setExecutor(ahCmd);
                    getCommand("kociol").setTabCompleter(ahCmd);
                }
            }
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new boniec.ahalloween.AHalloweenExpansion(aHalloweenManager).register();
            }
            getLogger().info("AHalloween zostal wlaczony.");
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Nie udalo sie uruchomic AHalloween.", t);
        }

        // ── System Metinów (End Crystal) ─────────────────────────────────────
        metinManager = new MetinManager(this);
        if (getCommand("adminmetin") != null) {
            MetinCommand metinCommand = new MetinCommand(metinManager);
            getCommand("adminmetin").setExecutor(metinCommand);
            getCommand("adminmetin").setTabCompleter(metinCommand);
        }
        getLogger().info("System Metinow zostal pomyslnie zainicjalizowany!");

        // ── System Antylogout ────────────────────────────────────────────
        AntyLogoutConfig antyLogoutConfig = new AntyLogoutConfig(this);
        antyLogoutConfig.load();
        antyLogoutConfig.wylaczPoStarcie();
        antyLogoutManager = new AntyLogoutManager(this, antyLogoutConfig);
        antyLogoutManager.start();
        Bukkit.getPluginManager().registerEvents(new AntyLogoutListener(antyLogoutManager, antyLogoutConfig), this);
        if (getCommand("antylogout") != null) {
            AntyLogoutCommand antyLogoutCmd = new AntyLogoutCommand(antyLogoutManager, antyLogoutConfig);
            getCommand("antylogout").setExecutor(antyLogoutCmd);
            getCommand("antylogout").setTabCompleter(antyLogoutCmd);
        }

        // Region "spawnpvp": blokada wchodzenia (odepchniecie) TYLKO dla graczy
        // oznaczonych antylogoutem (w walce) + zabezpieczenie przed kradzieza
        // itemow, jesli ktos i tak w nim zginie (patrz onPlayerDeath).
        // Rejestrowane WYLACZNIE gdy WorldGuard jest wlaczony - inaczej odwolanie
        // sie do WorldGuardHook skonczyloby sie NoClassDefFoundError. Musi byc
        // zarejestrowane PO utworzeniu antyLogoutManager (linia wyzej), bo listener
        // potrzebuje go do sprawdzenia isInCombat().
        if (worldGuardEnabled) {
            Bukkit.getPluginManager().registerEvents(
                    new boniec.spawnpvp.SpawnPvpGuardListener(antyLogoutManager), this);
            new boniec.aregion.ARegionManager(this);
        }

        // ── System skrzynek (/acase): SKRZYNIA (blok) i LOOTBOX (przedmiot w rece) ──
        try {
            caseManager = new boniec.skrzynki.CaseManager(this);
            caseManager.load();
            boniec.skrzynki.CaseListener caseListener = new boniec.skrzynki.CaseListener(this, caseManager);
            Bukkit.getPluginManager().registerEvents(caseListener, this);
            if (getCommand("acase") != null) {
                boniec.skrzynki.CaseCommand caseCmd = new boniec.skrzynki.CaseCommand(caseManager, caseListener);
                getCommand("acase").setExecutor(caseCmd);
                getCommand("acase").setTabCompleter(caseCmd);
            } else {
                getLogger().warning("Brak komendy 'acase' w plugin.yml!");
            }
        } catch (Exception e) {
            getLogger().severe("Blad podczas wlaczania systemu skrzynek: " + e.getMessage());
            e.printStackTrace();
        }

        if (caseManager != null && getCommand("galaktyczna") != null) {
            boniec.galaktyczna.GalaktycznaManager galaktycznaManager = new boniec.galaktyczna.GalaktycznaManager(this);
            boniec.galaktyczna.GalaktycznaGui galaktycznaGui = new boniec.galaktyczna.GalaktycznaGui(galaktycznaManager, caseManager);
            Bukkit.getPluginManager().registerEvents(galaktycznaGui, this);
            getCommand("galaktyczna").setExecutor(new boniec.galaktyczna.GalaktycznaCommand(galaktycznaManager, galaktycznaGui));
        }

        if (worldGuardEnabled) {
            try {
                afkStrefa = new boniec.afk.AfkStrefa(this, caseManager);
            } catch (Exception e) {
                getLogger().severe("Blad podczas wlaczania strefy AFK: " + e.getMessage());
            }
        }

        // ── System Backupów ──────────────────────────────────────────────────
        // Przeplyw statusow: PENDING -> APPROVED -> GIVEN  (+ REJECTED / REVOKED)
        //  /backup <nick>        -> lista smierci; LPM = wyslij do weryfikacji, PPM = podglad itemow
        //  /backup weryfikacja   -> LPM zatwierdz (NIE wydaje itemow), PPM odrzuc, Shift+LPM podglad
        //  /odbierzbackup        -> GRACZ odbiera swoj zatwierdzony backup
        //  /cofnijbackup <nick>  -> STAFF zabiera nadany backup (offline = strip przy wejsciu)
        //
        // WAZNE: backup.DeathListener dziala na EventPriority.LOWEST, wiec robi snapshot
        // pelnego EQ ZANIM Main#onPlayerDeath (MONITOR) wyczysci ekwipunek ofiary.
        try {
            backupDatabase = new BackupDatabase(this);
            backupManager = new BackupManager(this, backupDatabase);
            backupGui = new BackupGUI(this, backupManager);

            Bukkit.getPluginManager().registerEvents(
                    new boniec.code.core.backup.DeathListener(this, backupDatabase), this);
            Bukkit.getPluginManager().registerEvents(backupGui, this);
            Bukkit.getPluginManager().registerEvents(new BackupJoinListener(backupManager), this);

            if (getCommand("backup") != null) {
                BackupCommand backupCmd = new BackupCommand(this, backupManager, backupGui);
                getCommand("backup").setExecutor(backupCmd);
                getCommand("backup").setTabCompleter(backupCmd);
            } else {
                getLogger().warning("Brak komendy 'backup' w plugin.yml!");
            }

            if (getCommand("odbierzbackup") != null) {
                OdbierzBackupCommand odbierzCmd = new OdbierzBackupCommand(backupGui);
                getCommand("odbierzbackup").setExecutor(odbierzCmd);
                getCommand("odbierzbackup").setTabCompleter(odbierzCmd);
            } else {
                getLogger().warning("Brak komendy 'odbierzbackup' w plugin.yml!");
            }

            if (getCommand("cofnijbackup") != null) {
                CofnijBackupCommand cofnijCmd = new CofnijBackupCommand(backupGui);
                getCommand("cofnijbackup").setExecutor(cofnijCmd);
                getCommand("cofnijbackup").setTabCompleter(cofnijCmd);
            } else {
                getLogger().warning("Brak komendy 'cofnijbackup' w plugin.yml! "
                        + "Staff nie bedzie mogl cofac nadanych backupow.");
            }

            getLogger().info("System backupow zostal pomyslnie wlaczony "
                    + "(/backup, /odbierzbackup, /cofnijbackup).");
        } catch (Exception e) {
            getLogger().severe("Blad podczas wlaczania systemu backupow: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (shulkerOpenListener != null) {
            shulkerOpenListener.zamknijWszystkie();
        }
        if (mapaResetManager != null) {
            mapaResetManager.zamknij();
        }
        if (adminUtilManager != null) {
            adminUtilManager.shutdown();
        }
        if (sterujManager != null) {
            sterujManager.shutdown();
        }
        if (banManager != null) {
            banManager.stop();
        }
        if (nekromantaManager != null) {
            nekromantaManager.zamknij();
        }
        if (coinflipManager != null) {
            coinflipManager.zamknij();
        }
        if (caseManager != null) {
            caseManager.shutdown();
        }
        if (afkStrefa != null) {
            afkStrefa.zamknij();
        }
        if (topNpcUpdater != null) {
            topNpcUpdater.stop();
        }
        if (kothManager != null) {
            kothManager.stop();
        }
        if (serceManager != null) {
            serceManager.stop();
        }
        if (bezelekElytra != null) {
            bezelekElytra.stop();
        }
        if (sidebarManager != null) {
            sidebarManager.stop();
        }
        if (privateTurboManager != null) {
            privateTurboManager.zamknij();
        }
        if (poziomyManager != null) {
            poziomyManager.zamknij();
        }
        if (misjeManager != null) {
            misjeManager.zamknij();
        }
        if (tworcyTryb != null) {
            tworcyTryb.zamknij();
        }
        if (sklepManager != null) {
            sklepManager.stop();
        }
        if (portfelManager != null) {
            portfelManager.zamknij();
        }
        if (nagrodaManager != null) {
            nagrodaManager.wylacz();
        }
        if (aHalloweenManager != null) {
            aHalloweenManager.zamknij();
        }
        if (adminAbuse != null) {
            // Zdejmuje ciemnosc ze wszystkich graczy i kasuje zadanie efektu.
            adminAbuse.zamknij();
        }
        if (antyLogoutManager != null) {
            antyLogoutManager.stop();
        }
        if (rynek != null) {
            // Rynek teraz zapisuje do rynek.db (SQLite) asynchronicznie (patrz Rynek#saveMarketItems) -
            // przy wylaczaniu pluginu scheduler NIE gwarantuje juz dokonczenia zaplanowanych
            // zadan async, wiec ewentualny "ostatni" zapis (np. tuz przed /stop) domykamy
            // tutaj synchronicznie, zeby nic nie zgubic.
            rynek.flushPendingSaveSync();
            rynek.zamknij();
        }
        if (metinManager != null) {
            metinManager.shutdown();
        }
        if (meteoriteManager != null) {
            meteoriteManager.cleanup();
        }
        if (mistrzSystem != null) {
            mistrzSystem.shutdown();
        }
        if (kity != null) {
            kity.close();
        }
        if (klanManager != null) {
            klanManager.zamknij();
        }
        if (klanWalutaManager != null) {
            klanWalutaManager.zapisz();
        }
        if (szaman != null) {
            szaman.wylacz();
        }
        if (generatorManager != null) {
            generatorManager.cleanup();
        }
        if (poleZamek != null) {
            poleZamek.wylacz();
        }
        if (gornikManager != null) {
            gornikManager.zatrzymajPayoutTask();
            gornikManager.wylaczTurbodrop();
        }
        if (eventOdlamkowManager != null) {
            eventOdlamkowManager.shutdown();
        }

        // ZATRZYMYWANIE OBU MENEDŻERÓW:
        if (minotaurManager != null) {
            minotaurManager.shutdown();
        }
        if (zeusManager != null) {
            zeusManager.shutdown();
        }
        if (goblinManager != null) {
            goblinManager.shutdown();
        }

        // Zapis systemu kodów promocyjnych
        if (codeManager != null) {
            codeManager.saveConfigs();
        }

        boniec.boss.BossbarUtil.usunWszystkie(this);
        boniec.boss.PapiCleanup.wyrejestruj(this, "minotaur", "zeus", "goblin", "szal", "levels", "777-wallet", "osoby", "klany", "inventory", "ahalloween");

        zapiszWszystkieDaneDoDb();

        // ── Zamkniecie bazy backupow (na koncu - inne systemy moga jeszcze z niej korzystac)
        if (backupDatabase != null) {
            backupDatabase.close();
        }
        boniec.ustawienia.UstawieniaManager.wyczyscInstancje();
        boniec.code.core.UstawieniaDatabase.close();
        boniec.code.core.CoreDatabase.close();
        boniec.klany.Main.wyczysc();
    }

    /**
     * Ostatni synchroniczny flush do data.db — chroni portfel, EXP, ranking i muty
     * gdy plugin sie wylacza (np. plugman reload). Async zadania w tym momencie juz nie dzialaja.
     */
    private void zapiszWszystkieDaneDoDb() {
        if (!boniec.code.core.CoreDatabase.isOpen()) {
            return;
        }
        getLogger().info("[Inventory] Zapis danych graczy do data.db przed zamknieciem...");
        try {
            if (sidebarManager != null) {
                sidebarManager.zapiszPrzyWylaczaniu();
            }
            if (ustawieniaManager != null) {
                ustawieniaManager.zapiszWszystkieSync();
            }
            if (tiktokManager != null) {
                tiktokManager.zapiszPrzyWylaczaniu();
            }
            if (portfelManager != null) {
                portfelManager.zapiszPrzyWylaczaniu();
            }
            if (poziomyManager != null) {
                poziomyManager.zapiszPrzyWylaczaniu();
            }
            if (misjeManager != null) {
                misjeManager.zapiszPrzyWylaczaniu();
            }
            if (klanManager != null) {
                klanManager.zapiszPrzedZamknieciemDb();
            }
            if (klanWalutaManager != null) {
                klanWalutaManager.zapisz();
            }
            boniec.code.core.CoreDatabase.checkpointWal();
            int portfel = boniec.code.core.CoreDatabase.get().liczbaWierszy("portfel_konta");
            int poziomy = boniec.code.core.CoreDatabase.get().liczbaWierszy("poziomy_gracze");
            int ranking = boniec.code.core.CoreDatabase.get().liczbaWierszy("gracze");
            getLogger().info("[Inventory] W data.db po zapisie: portfel=" + portfel + " kont, poziomy=" + poziomy
                    + " profili, ranking/gracze=" + ranking);
        } catch (Exception e) {
            getLogger().severe("[Inventory] Blad przy zapisie danych: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadKlanyConfig() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        klanyConfigFile = new File(getDataFolder(), "klany-config.yml");
        if (!klanyConfigFile.exists()) {
            saveResource("klany-config.yml", false);
        }
        klanyConfig = YamlConfiguration.loadConfiguration(klanyConfigFile);
        uzupelnijBrakujaceKluczeKlanow();
    }

    public void reloadKlanyConfig() {
        klanyConfig = YamlConfiguration.loadConfiguration(klanyConfigFile);
        uzupelnijBrakujaceKluczeKlanow();
        boniec.klany.Main.reloadConfig(klanyConfig);
        if (topNpcUpdater != null) {
            int n = topNpcUpdater.odswiezTeraz();
            getLogger().info("[TopNpc] Reload: ustawiono " + n + " skinow.");
        }
    }

    private void uzupelnijBrakujaceKluczeKlanow() {
        if (klanyConfig == null) return;
        try (InputStream in = getResource("klany-config.yml")) {
            if (in == null) return;
            FileConfiguration jar = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            boolean zmieniono = false;
            String[] klucze = {
                    "ustawienia.max-czlonkow-absolutny",
                    "wiadomosci.ulepszenie-sukces-limit",
                    "ulepszenia-gui.perki.limit-graczy"
            };
            for (String klucz : klucze) {
                if (!klanyConfig.contains(klucz) && jar.contains(klucz)) {
                    klanyConfig.set(klucz, jar.get(klucz));
                    zmieniono = true;
                }
            }
            if (zmieniono && klanyConfigFile != null) {
                klanyConfig.save(klanyConfigFile);
                getLogger().info("[Klany] Uzupelniono brakujace klucze limitu graczy w klany-config.yml.");
            }
        } catch (IOException e) {
            getLogger().warning("[Klany] Nie udalo sie uzupelnic klany-config.yml: " + e.getMessage());
        }
    }

    public KlanManager getKlanManager() {
        return klanManager;
    }

    public boniec.klany.core.KlanWalutaManager getKlanWalutaManager() {
        return klanWalutaManager;
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }

    public MinotaurManager getMinotaurManager() {
        return minotaurManager;
    }

    public boniec.misje.MisjeManager getMisjeManager() {
        return misjeManager;
    }

    public GoblinManager getGoblinManager() { return goblinManager; }
    public ZeusManager getZeusManager() {
        return zeusManager;
    }

    public EventOdlamkowManager getEventOdlamkowManager() {
        return eventOdlamkowManager;
    }

    public boniec.koth.KothManager getKothManager() {
        return kothManager;
    }

    public boniec.serce.SerceManager getSerceManager() {
        return serceManager;
    }

    public boniec.portfel.PortfelManager getPortfelManager() {
        return portfelManager;
    }

    public boniec.scoreboard.SidebarManager getSidebarManager() {
        return sidebarManager;
    }

    public boniec.code.core.MeteoriteManager getMeteoriteManager() {
        return meteoriteManager;
    }

    public UpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    public boniec.dev.polezamek.Main getPoleZamek() {
        return poleZamek;
    }

    public FileConfiguration getPatronConfig() {
        return patronConfig;
    }

    public MistrzSystem getMistrzSystem() {
        return mistrzSystem;
    }

    public GornikManager getGornikManager() {
        return gornikManager;
    }

    public HitSystemManager getHitSystemManager() {
        return hitSystemManager;
    }

    public boniec.szaman.SzamanPlugin getSzaman() {
        return szaman;
    }

    public CodeManager getCodeManager() {
        return codeManager;
    }

    public boniec.nagroda.NagrodaManager getNagrodaManager() {
        return nagrodaManager;
    }

    public boniec.ahalloween.AHalloweenManager getAHalloweenManager() {
        return aHalloweenManager;
    }

    public MetinManager getMetinManager() {
        return metinManager;
    }

    public AntyLogoutManager getAntyLogoutManager() {
        return antyLogoutManager;
    }

    public static Main getInstance() {
        return instance;
    }

    /** Jednorazowo: czy przy ostatniej smierci ekwipunek zostal (null = ta smierc nie byla obslugiwana tutaj). */
    public Boolean zabierzEqZachowanyPoSmierci(UUID uuid) {
        return eqZachowanyPoSmierci.remove(uuid);
    }

    // ── Gettery systemu backupów ─────────────────────────────────────────
    public BackupDatabase getBackupDatabase() {
        return backupDatabase;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public BackupGUI getBackupGui() {
        return backupGui;
    }

    public boniec.code.core.TworcyTryb getTworcyTryb() {
        return tworcyTryb;
    }

    public boolean isWorldGuardEnabled() {
        var wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        return wg != null && wg.isEnabled();
    }

    public void loadPatronConfig() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        patronFile = new File(getDataFolder(), "patron.yml");
        if (!patronFile.exists()) {
            saveResource("patron.yml", false);
        }
        patronConfig = YamlConfiguration.loadConfiguration(patronFile);
    }

    public void savePatronConfig() {
        if (patronConfig == null || patronFile == null) return;
        try {
            patronConfig.save(patronFile);
        } catch (IOException e) {
            getLogger().warning("Nie udało się zapisać patron.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getUlepszConfig() {
        return ulepszConfig;
    }

    public void loadUlepszConfig() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        ulepszConfigFile = new File(getDataFolder(), "ulepsz-config.yml");
        if (!ulepszConfigFile.exists()) {
            saveResource("ulepsz-config.yml", false);
        }
        ulepszConfig = YamlConfiguration.loadConfiguration(ulepszConfigFile);
        try (InputStream in = getResource("ulepsz-config.yml")) {
            if (in != null) {
                FileConfiguration jar = YamlConfiguration.loadConfiguration(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                int wersjaJar = jar.getInt("gui.wersja", 1);
                boolean nadpisz = ulepszConfig.getInt("gui.wersja", 0) < wersjaJar;
                boolean zmieniono = false;
                for (String klucz : jar.getKeys(true)) {
                    if (jar.isConfigurationSection(klucz)) continue;
                    boolean brak = !ulepszConfig.contains(klucz);
                    boolean lore = nadpisz && (klucz.startsWith("podglad.") || klucz.startsWith("gui.")
                            || klucz.startsWith("lore.") || klucz.startsWith("dekoracja.")
                            || klucz.startsWith("placeholder."));
                    if (brak || lore) {
                        ulepszConfig.set(klucz, jar.get(klucz));
                        zmieniono = true;
                    }
                }
                if (nadpisz) {
                    ulepszConfig.set("dekoracja.info", null);
                    ulepszConfig.set("gui.wersja", wersjaJar);
                    zmieniono = true;
                }
                if (zmieniono) {
                    try {
                        ulepszConfig.save(ulepszConfigFile);
                    } catch (java.io.IOException e) {
                        getLogger().warning("Nie zapisano ulepsz-config.yml: " + e.getMessage());
                    }
                }
            }
        } catch (java.io.IOException ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.setDeathMessage(null);

        Player victim = event.getEntity();
        eqZachowanyPoSmierci.remove(victim.getUniqueId());

        if (tworcyTryb != null) {
            Player killerCheck = victim.getKiller();
            if (killerCheck == null) {
                UUID last = lastDamager.get(victim.getUniqueId());
                if (last != null) killerCheck = Bukkit.getPlayer(last);
            }
            if (tworcyTryb.dotyczy(victim, killerCheck)) {
                eqZachowanyPoSmierci.put(victim.getUniqueId(), true);
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.setDroppedExp(0);
                event.getDrops().clear();
                lastDamager.remove(victim.getUniqueId());
                pendingReturns.remove(victim.getUniqueId());
                lastDeathLosses.put(victim.getUniqueId(), Collections.emptyList());
                return;
            }
        }

        if (adminUtilManager != null && adminUtilManager.isKeepInventory()) {
            eqZachowanyPoSmierci.put(victim.getUniqueId(), true);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            event.getDrops().clear();
            lastDamager.remove(victim.getUniqueId());
            pendingReturns.remove(victim.getUniqueId());
            lastDeathLosses.put(victim.getUniqueId(), Collections.emptyList());
            return;
        }

        // Region spawnpvp: gracz "w walce" (antylogout) normalnie nie moze tam
        // wejsc (patrz SpawnPvpGuardListener), ale dla bezpieczenstwa (np. admin
        // go tam wpuscil, kolejnosc pluginow, itp.) - jesli ofiara i tak zginie w
        // tym regionie, ITEMY NIE MOGA byc kradzione/oddawane zabojcy.
        // Dokladnie jak zwykla smierc bez killera: caly ekwipunek zostaje.
        if (worldGuardEnabled && boniec.code.core.WorldGuardHook.isInRegion(
                victim.getLocation(), boniec.spawnpvp.SpawnPvpGuardListener.REGION_ID)) {
            eqZachowanyPoSmierci.put(victim.getUniqueId(), true);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            lastDamager.remove(victim.getUniqueId());
            pendingReturns.remove(victim.getUniqueId());
            lastDeathLosses.put(victim.getUniqueId(), Collections.emptyList()); // nic nie przepadlo
            return;
        }

        Player killer = victim.getKiller();

        if (killer == null) {
            UUID killerUUID = lastDamager.remove(victim.getUniqueId());
            if (killerUUID != null) {
                killer = Bukkit.getPlayer(killerUUID);
            }
        }

        boolean voidDeath = victim.getLastDamageCause() != null
                && victim.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID;

        if (killer == null && !voidDeath) {
            // Ten listener wcale nie obsluguje tej smierci - standardowy drop na ziemie,
            // zaden item nie "wraca" automatycznie. Nie ustawiamy lastDeathLosses:
            // DeathListener (backup) wykryje brak wpisu i sam zapisze caly ekwipunek gracza.
            return;
        }

        // Przy keepInventory (gamerule albo inny plugin) getDrops() jest puste, a eq zostaje
        // u ofiary - wtedy lupem jest faktyczny ekwipunek, inaczej ofiara traci wszystko, a zabojca nic.
        List<ItemStack> deathDrops = new ArrayList<>();
        if (event.getKeepInventory()) {
            for (ItemStack it : victim.getInventory().getContents()) {
                if (it != null && it.getType() != Material.AIR) deathDrops.add(it.clone());
            }
        } else {
            deathDrops.addAll(event.getDrops());
        }
        eqZachowanyPoSmierci.put(victim.getUniqueId(), false);
        event.setKeepInventory(true);
        event.getDrops().clear();

        victim.getInventory().clear();
        victim.getInventory().setArmorContents(null);
        victim.getInventory().setItemInOffHand(null);

        List<ItemStack> toSteal = new ArrayList<>();
        List<ItemStack> toReturn = new ArrayList<>();
        List<ItemStack> stealableSztuki = new ArrayList<>();
        List<ItemStack> priorityDoWydania = new ArrayList<>();
        List<ItemStack> inneDoWydania = new ArrayList<>();

        for (ItemStack item : deathDrops) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (PRIORITY_ITEMS.contains(item.getType()) || jestPrzedmiotEventowy(item)) {
                if (killer != null) {
                    priorityDoWydania.add(item);
                } else {
                    toReturn.add(item);
                }
                continue;
            }

            if (!STEALABLE_ITEMS.contains(item.getType())) {
                if (killer != null) inneDoWydania.add(item);
                continue;
            }

            for (int i = 0; i < item.getAmount(); i++) {
                ItemStack sztuka = item.clone();
                sztuka.setAmount(1);
                stealableSztuki.add(sztuka);
            }
        }

        if (!stealableSztuki.isEmpty()) {
            Collections.shuffle(stealableSztuki);
            int doZwrotu = Math.max(1, (int) Math.round(stealableSztuki.size() * RETURN_FRACTION));
            toReturn.addAll(stealableSztuki.subList(0, doZwrotu));
            toSteal.addAll(stealableSztuki.subList(doZwrotu, stealableSztuki.size()));
        }

        if (killer != null) {
            for (ItemStack item : priorityDoWydania) {
                giveTo(killer, item);
            }
            for (ItemStack item : toSteal) {
                giveTo(killer, item);
            }
            for (ItemStack item : inneDoWydania) {
                giveTo(killer, item);
            }
        }

        if (!toReturn.isEmpty()) {
            pendingReturns.put(victim.getUniqueId(), toReturn);
        }

        // To, co faktycznie przepadlo graczowi (nie wraca przy respawnie):
        // skradzione priorytetowe + skradzione stealable + wszystko inne oddane zabojcy.
        // Jesli killer == null (np. voidDeath), inneDoWydania i priorityDoWydania sa puste
        // (patrz warunki "if (killer != null)" powyzej przy ich budowaniu) - liczy sie
        // wtedy tylko toSteal.
        List<ItemStack> lost = new ArrayList<>();
        lost.addAll(priorityDoWydania);
        lost.addAll(toSteal);
        lost.addAll(inneDoWydania);
        lastDeathLosses.put(victim.getUniqueId(), lost);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker != null) {
            lastDamager.put(victim.getUniqueId(), attacker.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> items = pendingReturns.remove(player.getUniqueId());

        if (items == null || items.isEmpty()) {
            return;
        }

        for (ItemStack item : items) {
            giveTo(player, item);
        }
    }

    private boolean jestPrzedmiotEventowy(ItemStack item) {
        if (item == null || poleZamek == null || poleZamek.getEventItemsManager() == null) return false;
        return poleZamek.getEventItemsManager().jestEventowy(item);
    }

    private void giveTo(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}