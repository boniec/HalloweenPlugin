package boniec.adminutil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SterujCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final SterujManager manager;

    public SterujCommand(JavaPlugin plugin, SterujManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void register() {
        PluginCommand command = plugin.getCommand("steruj");
        if (command == null) {
            plugin.getLogger().warning("Brak komendy /steruj w plugin.yml");
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(manager, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(SterujManager.color("&f亢 &cTylko gracz."));
            return true;
        }
        if (!player.hasPermission(SterujManager.PERM)) {
            player.sendMessage(SterujManager.color("&f亢 &cNie masz uprawnień!"));
            return true;
        }

        if (args.length == 0 || jestWylacz(args[0])) {
            if (manager.zakonczKontrolera(player, true)) {
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(SterujManager.color("&f" + SterujManager.ICON + " &cUżyj: &f/steruj <nick>"));
                return true;
            }
            player.sendMessage(SterujManager.color("&f亢 &cNikim teraz nie sterujesz."));
            return true;
        }

        Player cel = Bukkit.getPlayerExact(args[0]);
        if (cel == null) {
            player.sendMessage(SterujManager.color("&f与 &cTen gracz jest obecnie offline!"));
            return true;
        }

        String blad = manager.rozpocznij(player, cel);
        if (blad != null) {
            player.sendMessage(SterujManager.color(blad));
            return true;
        }
        player.sendMessage(SterujManager.color("&f" + SterujManager.ICON
                + " &7Wcieliłeś się w &f" + cel.getName() + "&7. Chodzisz i grasz jego postacią."));
        player.sendMessage(SterujManager.color("&f" + SterujManager.ICON + " &7Wpisz &f/steruj &7żeby przestać."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(SterujManager.PERM) || args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        names.add("off");
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player self && self.equals(online)) {
                continue;
            }
            if (online.hasPermission(SterujManager.PERM_BYPASS)) {
                continue;
            }
            names.add(online.getName());
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> wynik = new ArrayList<>();
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                wynik.add(name);
            }
        }
        return wynik;
    }

    private static boolean jestWylacz(String arg) {
        String v = arg.toLowerCase(Locale.ROOT);
        return v.equals("off") || v.equals("wylacz") || v.equals("wyłącz") || v.equals("stop");
    }
}
