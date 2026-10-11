package boniec.dev.inventory.utility;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateMessageIgnoreTest {
    private JavaPlugin plugin;
    private YamlConfiguration config;
    private UtilityCommands commands;
    private Player anna;
    private Player bartek;
    private Player celina;
    private Player spy;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setup() {
        plugin = mock(JavaPlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        commands = new UtilityCommands(plugin);
        anna = player("Anna");
        bartek = player("Bartek");
        celina = player("Celina");
        spy = player("Spy");
        when(spy.hasPermission("pw.spy")).thenReturn(true);
        config.set("private-messages.spy." + spy.getUniqueId(), true);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayerExact("Anna")).thenReturn(anna);
        bukkit.when(() -> Bukkit.getPlayerExact("Bartek")).thenReturn(bartek);
        bukkit.when(() -> Bukkit.getPlayerExact("Celina")).thenReturn(celina);
        bukkit.when(() -> Bukkit.getPlayer(anna.getUniqueId())).thenReturn(anna);
        bukkit.when(() -> Bukkit.getPlayer(bartek.getUniqueId())).thenReturn(bartek);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(anna, bartek, celina, spy));
    }

    @AfterEach
    void close() { bukkit.close(); }

    @Test
    void ignoreBlocksIncomingMessagesAndDoesNotReverseTheBlock() {
        run(anna, "ignore", "Bartek");
        clearInvocations(anna, bartek, spy, plugin);
        run(bartek, "msg", "Anna", "blocked");
        verify(anna, never()).sendMessage(anyString());
        verify(spy, never()).sendMessage(anyString());
        verify(bartek).sendMessage(contains("ignoruje"));
        verify(plugin, never()).saveConfig();
        assertNull(config.getString("private-messages.reply." + anna.getUniqueId()));
        run(anna, "msg", "Bartek", "allowed");
        verify(bartek).sendMessage(contains("allowed"));
    }

    @Test
    void replyCannotBypassIgnoreAndUnignoreRestoresDelivery() {
        run(bartek, "msg", "Anna", "before");
        run(anna, "ignore", "Bartek");
        clearInvocations(anna, bartek, spy);
        run(bartek, "r", "blocked-reply");
        verify(anna, never()).sendMessage(anyString());
        verify(spy, never()).sendMessage(anyString());
        verify(bartek).sendMessage(contains("ignoruje"));
        run(anna, "ignore", "Bartek");
        clearInvocations(anna, bartek);
        run(bartek, "r", "after");
        verify(anna).sendMessage(contains("after"));
        verify(bartek).sendMessage(contains("after"));
    }

    @Test
    void previouslySavedIgnoreEntriesWorkWithoutMigration() {
        config.set("private-messages.ignore." + bartek.getUniqueId() + "." + anna.getUniqueId(), true);
        commands = new UtilityCommands(plugin);
        run(bartek, "msg", "Anna", "blocked-old-entry");
        verify(anna, never()).sendMessage(anyString());
        verify(bartek).sendMessage(contains("ignoruje"));
        run(anna, "ignore", "Bartek");
        clearInvocations(anna);
        run(bartek, "msg", "Anna", "unblocked-old-entry");
        verify(anna).sendMessage(contains("unblocked-old-entry"));
    }

    @Test
    void ignoringOnePlayerDoesNotBlockOtherPlayers() {
        run(anna, "ignore", "Bartek");
        clearInvocations(anna);
        run(celina, "msg", "Anna", "from-celina");
        verify(anna).sendMessage(contains("from-celina"));
        run(bartek, "msg", "Celina", "to-celina");
        verify(celina).sendMessage(contains("to-celina"));
    }

    private void run(Player player, String name, String... args) {
        Command command = mock(Command.class);
        when(command.getName()).thenReturn(name);
        assertTrue(commands.onCommand(player, command, name, args));
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        return player;
    }
}
