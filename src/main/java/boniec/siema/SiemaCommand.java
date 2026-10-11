package boniec.siema;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class SiemaCommand implements TabExecutor {

    public void register(JavaPlugin plugin) {
        PluginCommand command = plugin.getCommand("siema");
        if (command == null) {
            throw new IllegalStateException("Brak komendy /siema w plugin.yml");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Użyj: /siema <nick>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || (sender instanceof Player player && !player.canSee(target))) {
            sender.sendMessage(ChatColor.RED + "Ten gracz jest offline.");
            return true;
        }
        target.sendTitle(target.getName(), "", 10, 60, 20);
        sender.sendMessage(ChatColor.GREEN + "Wyświetlono nick na ekranie gracza " + target.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
