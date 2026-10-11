package boniec.ekonomia.pay;

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
 * /pay <gracz> <kwota> (alias /przelej) - przelewa graczowi wskazana kwote
 * z konta ekonomii (Vault) nadawcy. Odpowiednik prostego "pay" znanego z
 * serwerow ekonomicznych.
 * <p>
 * Wymaga zarejestrowanego dostawcy Economy (Vault) - bez tego komenda
 * informuje o braku systemu ekonomii i nic nie robi.
 */
public class PayCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final String prefix;
    private Economy econ;

    public PayCommand(JavaPlugin plugin, String prefix) {
        this.plugin = plugin;
        this.prefix = prefix;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault nie jest zainstalowany! Komenda /pay nie bedzie dzialac.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("Nie znaleziono dostawcy Economy (Vault)! Komenda /pay nie bedzie dzialac.");
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

        if (econ == null) {
            player.sendMessage(color(prefix + " &cSystem ekonomii jest niedostepny - zglos to administracji."));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(color(prefix + " &cUzyj: &f/" + label + " <gracz> <kwota>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(color(prefix + " &cGracz &f" + args[0] + " &cnie jest online."));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(color(prefix + " &cNie mozesz przelac pieniedzy samemu sobie!"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(color(prefix + " &cPodaj poprawna liczbe!"));
            return true;
        }

        if (amount <= 0 || !Double.isFinite(amount)) {
            player.sendMessage(color(prefix + " &cKwota musi byc wieksza od 0!"));
            return true;
        }

        if (!econ.has(player, amount)) {
            player.sendMessage(color(prefix + " &cNie masz wystarczajaco pieniedzy! &7(potrzebujesz &f"
                    + formatKwota(amount) + "$&7)"));
            return true;
        }

        econ.withdrawPlayer(player, amount);
        econ.depositPlayer(target, amount);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        player.sendMessage(color(prefix + " &aPrzeslano &f" + formatKwota(amount) + "$ &agraczowi &f"
                + target.getName() + "&a!"));
        target.sendMessage(color(prefix + " &aOtrzymales &f" + formatKwota(amount) + "$ &aod gracza &f"
                + player.getName() + "&a!"));
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
