package boniec.ahalloween;

import boniec.code.core.CoreDatabase;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HalloweenCauldronTest {
    @TempDir Path folder;
    private Connection connection;
    private Object previousDatabase;
    private AHalloweenManager manager;
    private HalloweenDatabase database;
    private HalloweenCauldron cauldron;
    private HalloweenPlayer data;
    private Player player;
    private YamlConfiguration config;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setup() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cauldron-test"));
        Constructor<CoreDatabase> ctor = CoreDatabase.class.getDeclaredConstructor(JavaPlugin.class);
        ctor.setAccessible(true);
        CoreDatabase core = ctor.newInstance(plugin);
        connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("test.db"));
        field(CoreDatabase.class, "connection").set(core, connection);
        Field instance = field(CoreDatabase.class, "instance");
        previousDatabase = instance.get(null);
        instance.set(null, core);
        database = new HalloweenDatabase(plugin);
        manager = mock(AHalloweenManager.class);
        config = new YamlConfiguration();
        config.set("kociol.cel", 1000);
        config.set("kociol.mnoznik", 3.0);
        config.set("kociol.czas-sekund", 600);
        when(manager.cfg()).thenReturn(config);
        when(manager.aktywny()).thenReturn(true);
        when(manager.baza()).thenReturn(database);
        when(manager.plugin()).thenReturn(plugin);
        player = mock(Player.class);
        data = new HalloweenPlayer(UUID.randomUUID(), "Test");
        data.cukierki.put("zwykly", 1500);
        when(manager.dane(player)).thenReturn(data);
        database.zapisz(data);
        bukkit = mockStatic(Bukkit.class);
        cauldron = new HalloweenCauldron(manager);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (bukkit != null) bukkit.close();
        if (connection != null) connection.close();
        field(CoreDatabase.class, "instance").set(null, previousDatabase);
    }

    @Test
    void twoPlayersShareProgressAndStartOneTenMinuteBonus() {
        assertTrue(cauldron.deposit(player, 400));
        assertEquals(400, cauldron.progress());
        assertEquals(1100, data.sumaCukierkow());
        Player other = mock(Player.class);
        HalloweenPlayer otherData = new HalloweenPlayer(UUID.randomUUID(), "Other");
        otherData.cukierki.put("zwykly", 800);
        when(manager.dane(other)).thenReturn(otherData);
        assertTrue(cauldron.deposit(other, 800));
        assertEquals(200, otherData.sumaCukierkow(), "Only the missing 600 should be debited");
        assertEquals(0, cauldron.progress());
        assertTrue(cauldron.active());
        assertTrue(cauldron.remainingSeconds() >= 599 && cauldron.remainingSeconds() <= 600);
        assertEquals(3, cauldron.multiplier("zbieranie"));
        assertFalse(cauldron.deposit(player, 100));
        assertEquals(1100, data.sumaCukierkow(), "Deposits during a bonus must not consume candy");
        bukkit.verify(() -> Bukkit.broadcastMessage(anyString()), times(1));
    }

    @Test
    void boostAppliesOnlyToEarnedDrops() {
        assertTrue(cauldron.deposit(player, 1000));
        for (String source : new String[]{"zbieranie", "skrzynia", "mob", "boss", "trick-or-treat"}) {
            assertEquals(3, cauldron.multiplier(source));
        }
        for (String source : new String[]{"admin", "misja", "sklep", "unknown"}) {
            assertEquals(1, cauldron.multiplier(source));
        }
        assertEquals(1, cauldron.multiplier(null));
    }

    @Test
    void managerUsesTheCauldronWithoutMultiplyingExistingMiniEventsAgain() throws Exception {
        assertTrue(cauldron.deposit(player, 1000));
        field(AHalloweenManager.class, "kociol").set(manager, cauldron);
        field(AHalloweenManager.class, "miniKoniecAt").setLong(manager, System.currentTimeMillis() + 600_000);
        field(AHalloweenManager.class, "miniMnoznikZebranych").setDouble(manager, 2);
        var multiplier = AHalloweenManager.class.getDeclaredMethod("mnoznikIloscCukierkow", String.class);
        multiplier.setAccessible(true);
        assertEquals(3.0, multiplier.invoke(manager, "zbieranie"));
        assertEquals(2.0, multiplier.invoke(manager, "sklep"), "Shop receives only its existing mini-event multiplier");
        field(AHalloweenManager.class, "miniKoniecAt").setLong(manager, 0);
        assertEquals(1.0, multiplier.invoke(manager, "admin"));
    }

    @Test
    void directAndHalloweenCommandsRouteDepositsAndRejectInvalidInput() {
        when(manager.kociol()).thenReturn(cauldron);
        when(player.hasPermission("ahalloween.use")).thenReturn(true);
        AHalloweenCommand executor = new AHalloweenCommand(manager);
        org.bukkit.command.Command direct = mock(org.bukkit.command.Command.class);
        when(direct.getName()).thenReturn("kociol");
        executor.onCommand(player, direct, "kociol", new String[]{"100"});
        assertEquals(100, cauldron.progress());
        org.bukkit.command.Command event = mock(org.bukkit.command.Command.class);
        when(event.getName()).thenReturn("ahalloween");
        executor.onCommand(player, event, "ah", new String[]{"kociol", "50"});
        assertEquals(150, cauldron.progress());
        executor.onCommand(player, direct, "kociol", new String[]{"2147483648"});
        executor.onCommand(player, direct, "kociol", new String[]{"10", "extra"});
        when(player.hasPermission("ahalloween.use")).thenReturn(false);
        executor.onCommand(player, direct, "kociol", new String[]{"10"});
        assertEquals(150, cauldron.progress());
        when(player.hasPermission("ahalloween.use")).thenReturn(true);
        executor.onCommand(player, direct, "kociol", new String[]{"all"});
        assertTrue(cauldron.active());
        assertEquals(500, data.sumaCukierkow());
    }

    @Test
    void progressBalanceAndActiveDeadlineSurviveReconstruction() {
        assertTrue(cauldron.deposit(player, 100));
        cauldron = new HalloweenCauldron(manager);
        assertEquals(100, cauldron.progress());
        assertEquals(1400, database.wczytaj(data.uuid, data.nick).sumaCukierkow());
        assertTrue(cauldron.deposit(player, 900));
        long remaining = cauldron.remainingSeconds();
        cauldron = new HalloweenCauldron(manager);
        assertTrue(cauldron.active());
        assertEquals(remaining, cauldron.remainingSeconds());
        assertEquals(500, database.wczytaj(data.uuid, data.nick).sumaCukierkow());
        database.zapisz(data);
        assertEquals(500, database.wczytaj(data.uuid, data.nick).sumaCukierkow(), "Later autosave preserves the debit");
    }

    @Test
    void databaseFailureRollsBackBalanceAndGlobalProgress() throws Exception {
        database.zapiszKociol("0:0:3.0");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_cauldron BEFORE INSERT ON ahalloween_kv "
                    + "BEGIN SELECT RAISE(ABORT, 'simulated disk failure'); END");
        }
        assertFalse(cauldron.deposit(player, 100));
        assertEquals(1500, data.sumaCukierkow());
        assertEquals(1500, database.wczytaj(data.uuid, data.nick).sumaCukierkow());
        assertEquals(0, cauldron.progress());
        assertEquals("0:0:3.0", database.kv("kociol.stan", "missing"));
        assertTrue(connection.getAutoCommit(), "The failed transaction must restore autocommit");
        bukkit.verifyNoInteractions();
    }

    @Test
    void invalidOrUnavailableDepositsNeverConsumeCandy() {
        assertFalse(cauldron.deposit(player, 0));
        assertFalse(cauldron.deposit(player, -1));
        assertFalse(cauldron.deposit(player, Integer.MIN_VALUE));
        data.cukierki.put("zwykly", 5);
        assertFalse(cauldron.deposit(player, 10));
        when(manager.aktywny()).thenReturn(false);
        assertFalse(cauldron.deposit(player, 5));
        when(manager.aktywny()).thenReturn(true);
        config.set("kociol.enabled", false);
        assertFalse(cauldron.deposit(player, 5));
        assertEquals(5, data.sumaCukierkow());
        assertEquals(0, cauldron.progress());
    }

    @Test
    void expiredBonusHasNoMultiplierAndCanBeFilledAgain() throws Exception {
        database.zapiszKociol("0:1:3.0");
        cauldron = new HalloweenCauldron(manager);
        assertFalse(cauldron.active());
        assertEquals(1, cauldron.multiplier("mob"));
        cauldron.tick();
        assertEquals("0:0:3.0", database.kv("kociol.stan", "missing"));
        assertTrue(cauldron.deposit(player, 10));
        assertEquals(10, cauldron.progress());
    }

    @Test
    void stopCancelsBonusAndConfigReloadDoesNotChangeAnActiveMultiplier() {
        assertTrue(cauldron.deposit(player, 1000));
        config.set("kociol.mnoznik", 8);
        assertEquals(3, cauldron.multiplier("mob"));
        assertTrue(cauldron.clearBoost());
        assertFalse(cauldron.active());
        assertTrue(cauldron.deposit(player, 100));
        assertEquals(100, cauldron.progress());
    }

    @Test
    void countsPiecesAndSpendsCheaperCandyFirst() {
        data.cukierki.clear();
        data.cukierki.put("zwykly", 10);
        data.cukierki.put("rzadki", 10);
        AHalloweenManager.CandyDef rare = mock(AHalloweenManager.CandyDef.class);
        when(rare.value()).thenReturn(100);
        when(manager.candy("rzadki")).thenReturn(rare);
        assertTrue(cauldron.deposit(player, 12));
        assertEquals(Map.of("rzadki", 8), data.cukierki);
        assertEquals(12, cauldron.progress());
    }

    @Test
    void bundledConfigurationIsValidAndIncludesExistingEventAndCauldron() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(Path.of("src/main/resources/ahalloween.yml").toFile());
        assertNotNull(yaml.getConfigurationSection("candies"));
        assertNotNull(yaml.getConfigurationSection("mobs"));
        assertEquals(1000, yaml.getInt("kociol.cel"));
        assertEquals(3, yaml.getDouble("kociol.mnoznik"));
        assertEquals(600, yaml.getInt("kociol.czas-sekund"));
        yaml.load(Path.of("src/main/resources/plugin.yml").toFile());
        assertEquals("ahalloween.use", yaml.getString("commands.kociol.permission"));
    }

    @Test
    void existingPlayerSaveRollsBackAsAWholeOnFailure() throws Exception {
        data.cukierki.put("zwykly", 5);
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_stats BEFORE INSERT ON ahalloween_stats "
                    + "BEGIN SELECT RAISE(ABORT, 'simulated autosave failure'); END");
        }
        database.zapisz(data);
        assertEquals(1500, database.wczytaj(data.uuid, data.nick).sumaCukierkow());
        assertTrue(data.brudny, "Failed saves must remain dirty for retry");
        try (var statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            assertTrue(result.next());
            assertEquals("ok", result.getString(1));
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
