package boniec.bany;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/** Zapis IP graczy do /alts i opcjonalny alert staffu o kontach alt. */
public final class BanListener implements Listener {

    private final BanManager m;

    public BanListener(BanManager m) {
        this.m = m;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString();
        String name = p.getName();
        String ip = BanManager.ipOf(p);
        if (ip.isEmpty()) return;
        m.async(() -> {
            if (m.isShuttingDown() || !boniec.code.core.CoreDatabase.isOpen()) return;
            m.db().upsertPlayer(uuid, name, ip);
            if (!m.cfg().getBoolean("alts.alert.wlaczony", true)) return;
            List<BanManager.AltEntry> alts = m.loadAlts(uuid);
            if (alts.isEmpty()) return;
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < alts.size(); i++) {
                if (i > 0) names.append("&7, ");
                names.append("&f").append(alts.get(i).name);
            }
            final String list = names.toString();
            final int count = alts.size();
            m.sync(() -> m.notifyStaff("alts.alert.wiadomosc", "%gracz%", name, "%ile%", String.valueOf(count),
                    "%lista%", list));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString();
        String ip = BanManager.ipOf(p);
        if (ip.isEmpty() || !m.plugin().isEnabled() || m.isShuttingDown()) return;
        m.async(() -> {
            if (m.isShuttingDown() || !boniec.code.core.CoreDatabase.isOpen()) return;
            m.db().touchPlayer(uuid, ip);
        });
    }
}
