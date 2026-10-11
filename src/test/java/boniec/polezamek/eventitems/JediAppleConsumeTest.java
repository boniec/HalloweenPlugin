package boniec.dev.polezamek.eventitems;

import boniec.dev.polezamek.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JediAppleConsumeTest {
    private Main module;
    private JavaPlugin host;
    private EventItemsManager manager;
    private EventItemsListener listener;
    private Player player;
    private PlayerItemConsumeEvent event;
    private ItemStack original;

    @BeforeEach
    void setup() {
        module = mock(Main.class);
        host = mock(JavaPlugin.class);
        when(host.getName()).thenReturn("JediTest");
        when(module.getHost()).thenReturn(host);
        manager = mock(EventItemsManager.class);
        player = mock(Player.class);
        event = mock(PlayerItemConsumeEvent.class);
        original = mock(ItemStack.class);
        when(event.getItem()).thenReturn(original);
        when(event.getPlayer()).thenReturn(player);
        when(manager.isJablkoJedi(original)).thenReturn(true);
        listener = new EventItemsListener(module, manager);
    }

    @Test
    void normalUseAllowsVanillaConsumptionAndRestoresTheWholeOriginalStack() {
        when(original.getAmount()).thenReturn(32);
        listener.onConsume(event);
        verify(event, never()).setCancelled(true);
        verify(event).setReplacement(original);
        // Normal food, regeneration and absorption are applied by Minecraft after the event.
        verify(player).getLocation();
        verifyNoMoreInteractions(player);
        verify(original, never()).setAmount(anyInt());
    }

    @Test
    void offHandUseAlsoUsesPapersReplacementWithoutInventoryMutation() {
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        listener.onConsume(event);
        verify(event).setReplacement(original);
        verify(event, never()).setCancelled(true);
        verify(player, never()).getInventory();
    }

    @Test
    void cancelledConsumptionDoesNotApplyEffectsOrReplaceAnything() {
        when(event.isCancelled()).thenReturn(true);
        listener.onConsume(event);
        verify(event, never()).setReplacement(any());
        verifyNoInteractions(player);
    }

    @Test
    void blockedRegionCancelsVanillaConsumptionWithoutSideEffects() {
        when(module.isEventItemUseBlocked(null)).thenReturn(true);
        listener.onConsume(event);
        verify(event).setCancelled(true);
        verify(event, never()).setReplacement(any());
        verify(player).getLocation();
        verifyNoMoreInteractions(player);
    }

    @Test
    void existingAbsorptionBonusWaitsUntilAfterVanillaConsumptionAndHonorsLaterCancellation() {
        when(manager.getJablkoJediBonusChance()).thenReturn(1.0);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            listener.onConsume(event);
            verify(event, never()).setCancelled(true);
            verify(event).setReplacement(original);
            ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(host), callback.capture());
            when(event.isCancelled()).thenReturn(true);
            callback.getValue().run();
            verify(player).getLocation();
            verifyNoMoreInteractions(player);
        }
    }
}
