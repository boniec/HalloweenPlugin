package boniec.online;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

final class MotdListener implements Listener {

    private final FlameOnlinePlugin plugin;

    MotdListener(FlameOnlinePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("motd.enabled", false)) return;
        OnlineCounts c = plugin.counts();
        if (plugin.getConfig().getBoolean("motd.fake-count", true)) {
            event.setNumPlayers(c.shownAll());
        }
        if (plugin.getConfig().getBoolean("motd.fake-max", true)) {
            event.setMaxPlayers(Math.max(1, event.getMaxPlayers() * c.mnoznik()));
        }
        String l1 = plugin.getConfig().getString("motd.linia1", "");
        String l2 = plugin.getConfig().getString("motd.linia2", "");
        if ((l1 == null || l1.isEmpty()) && (l2 == null || l2.isEmpty())) return;
        event.setMotd(color(apply(l1, c)) + "\n" + color(apply(l2 == null ? "" : l2, c)));
    }

    private static String apply(String text, OnlineCounts c) {
        if (text == null) return "";
        return text
                .replace("{mnoznik}", String.valueOf(c.mnoznik()))
                .replace("{online_all}", String.valueOf(c.shownAll()))
                .replace("{online_all_real}", String.valueOf(c.realAll()))
                .replace("{online_skypvp}", String.valueOf(c.shown("skypvp")))
                .replace("{online_skypvp_real}", String.valueOf(c.real("skypvp")));
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
