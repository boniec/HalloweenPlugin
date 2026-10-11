package boniec.ahalloween;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HalloweenCommandRegistrationTest {
    @Test
    void registeredBukkitCommandOpensCauldronAndRoutesDeposits() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        AHalloweenManager manager = mock(AHalloweenManager.class);
        HalloweenCauldron cauldron = mock(HalloweenCauldron.class);
        when(manager.kociol()).thenReturn(cauldron);
        Player player = mock(Player.class);
        when(player.hasPermission("ahalloween.use")).thenReturn(true);
        PluginCommand direct = command("kociol", plugin);
        PluginCommand event = command("ahalloween", plugin);
        when(plugin.getCommand("kociol")).thenReturn(direct);
        when(plugin.getCommand("ahalloween")).thenReturn(event);
        AHalloweenCommand handler = new AHalloweenCommand(manager);
        handler.register(plugin);
        assertSame(handler, direct.getExecutor());
        assertSame(handler, direct.getTabCompleter());
        try (var guis = mockStatic(HalloweenGuis.class)) {
            assertTrue(direct.execute(player, "kociol", new String[0]));
            guis.verify(() -> HalloweenGuis.otworz(manager, player, HalloweenGuis.Typ.CAULDRON));
            assertTrue(event.execute(player, "ahalloween", new String[]{"kociol"}));
            guis.verify(() -> HalloweenGuis.otworz(manager, player, HalloweenGuis.Typ.CAULDRON), times(2));
        }
        assertTrue(direct.execute(player, "kociol", new String[]{"100"}));
        verify(cauldron).deposit(player, 100);
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    void missingEventCommandDoesNotLeaveCauldronWithoutExecutor() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("command-registration-test"));
        PluginCommand direct = command("kociol", plugin);
        when(plugin.getCommand("kociol")).thenReturn(direct);
        AHalloweenCommand handler = new AHalloweenCommand(mock(AHalloweenManager.class));
        handler.register(plugin);
        assertSame(handler, direct.getExecutor());
        assertSame(handler, direct.getTabCompleter());
    }

    private PluginCommand command(String name, Plugin plugin) throws Exception {
        var constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, plugin);
    }
}
