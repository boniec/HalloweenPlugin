package boniec.klany.cmd;

import boniec.klany.core.KlanManager;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/**
 * /aresetranking all - komenda dostepna WYLACZNIE z konsoli serwera.
 * <p>
 * Resetuje ranking oraz statystyki PvP (zabojstwa/smierci) WSZYSTKICH graczy do
 * stanu poczatkowego (ranking 1000, 0 zabojstw, 0 smierci) - dotyczy to zarowno
 * globalnego rankingu graczy (tabela "gracze"), jak i wlasnego rankingu kazdego klanu
 * (Klan#rankingKlanu, patrz KlanManager#getSumaRankinguKlanu) - oba sa zerowane razem.
 * <p>
 * NIE resetuje: przynaleznosci do klanu (czlonkowie zostaja w swoich klanach z tymi
 * samymi rangami), skarbca klanu (smocze odlamki), ani ulepszen/perkow klanowych
 * (drop/ranking z /klan ulepszenia) - patrz
 * KlanManager#resetujStatystykiWszystkich.
 */
public class ResetRankingCommand implements CommandExecutor, TabCompleter {

    private final KlanManager manager;

    public ResetRankingCommand(KlanManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            Msg.sendGui(sender, "&cTa komenda jest dostepna wylacznie z konsoli serwera.");
            return true;
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("all")) {
            Msg.sendGui(sender, "&cUzycie: /aresetranking all");
            return true;
        }

        manager.resetujStatystykiWszystkich();

        Msg.sendGui(sender, "&aZresetowano ranking i statystyki PvP (zabojstwa/smierci) wszystkich graczy.");
        Msg.sendGui(sender, "&7Skarbiec klanow i ulepszenia (perki) klanow NIE zostaly zmienione.");

        Bukkit.getLogger().info("[Klany] Konsola zresetowala ranking i statystyki PvP wszystkich graczy (/aresetranking all).");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return List.of();
        if (args.length == 1) {
            return "all".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("all") : List.of();
        }
        return List.of();
    }
}
