package boniec.code.anticheat;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Fullbox implements Listener {

    private final Map<UUID, Deque<Long>> placeHistory = new HashMap<>();

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        placeHistory.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Deque<Long> history = placeHistory.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        history.addLast(now);

        while (!history.isEmpty() && now - history.peekFirst() > 1000) {
            history.removeFirst();
        }

        if (history.size() > 8) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4&l❌ &cStawiasz bloki zbyt szybko!"));
        }
    }
}
