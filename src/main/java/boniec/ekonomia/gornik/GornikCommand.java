package boniec.ekonomia.gornik;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * /gornik <kwota> - dodaje pieniądze do ekonomii gracza (Vault)
 * Wymaga permisji "gornik.use" lub OP.
 */
public class GornikCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final String prefix;
    private Economy econ;

    public GornikCommand(JavaPlugin plugin, String prefix) {
        this.plugin = plugin;
        this.prefix = prefix;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault nie jest zainstalowany! Komenda /gornik nie bedzie dzialac.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("Nie znaleziono dostawcy Economy (Vault)! Komenda /gornik nie bedzie dzialac.");
            return;
        }
        econ = rsp.getProvider();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest dostepna tylko dla graczy.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("gornik.use") && !player.isOp()) {
            player.sendMessage(color(prefix + " &cNie masz uprawnien do tej komendy!"));
            return true;
        }

        if (econ == null) {
            player.sendMessage(color(prefix + " &cSystem ekonomii jest niedostepny - zglos to administracji."));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(color(prefix + " &cUzyj: &f/" + label + " <kwota>"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[0].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(color(prefix + " &cPodaj poprawna liczbe!"));
            return true;
        }

        if (amount <= 0 || !Double.isFinite(amount)) {
            player.sendMessage(color(prefix + " &cKwota musi byc wieksza od 0!"));
            return true;
        }

        econ.depositPlayer(player, amount);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        player.sendMessage(color(prefix + " &aOtrzymales &f" + formatKwota(amount) + "$ &ado ekonomii!"));
        return true;
    }

    private String formatKwota(double amount) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
