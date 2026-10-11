package boniec.klany.teams;

import boniec.klany.core.Klan;
import boniec.klany.core.KlanManager;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Zarzadza teamami na scoreboardzie na podstawie przynaleznosci klanowej.
 * Kazdy klan = osobny team z kolorowym prefixem (zielony tag).
 * Gracze bez klanu - bez prefixu.
 */
public class KlanTeams implements Listener {

    private static final String PREFIX = "klany_";
    private static final String NO_CLAN_TEAM = PREFIX + "brak";

    private final boniec.dev.inventory.Main plugin;
    private final Scoreboard scoreboard;

    public KlanTeams(boniec.dev.inventory.Main plugin) {
        this.plugin = plugin;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public void ustawDlaGracza(Player player) {
        KlanManager km = plugin.getKlanManager();
        if (km == null) return;

        Klan klan = km.getKlanGracza(player.getUniqueId());
        String teamName = klan != null ? PREFIX + klan.getId() : NO_CLAN_TEAM;

        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(PREFIX) && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            if (klan != null) {
                team.setPrefix(Msg.color(" &8[&#8FE5A3\uD83C\uDFF9" + klan.getTag() + "&8] "));
                team.setColor(ChatColor.GREEN);
            } else {
                team.setPrefix("");
            }
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }

        team.addEntry(player.getName());
    }

    public void odswiezWszystkich() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ustawDlaGracza(player);
        }
    }

    public void odswiezKlan(Klan klan) {
        if (klan == null) return;
        for (UUID uuid : klan.getCzlonkowie().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                ustawDlaGracza(player);
            }
        }
    }

    public void usunGracza(Player player) {
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(PREFIX) && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
                break;
            }
        }
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ustawDlaGracza(event.getPlayer()), 5L);
    }
}
