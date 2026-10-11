package boniec.code.core;

import boniec.dev.inventory.Main;
import org.bukkit.command.Command;
import boniec.code.core.MistrzSystem;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AdminPatronCommand implements CommandExecutor {

    private final Main plugin;

    public AdminPatronCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Konsola (i command block) ma pelne uprawnienia - sprawdzamy je tylko u gracza.
        // p == null oznacza wykonanie z konsoli.
        Player p = (sender instanceof Player) ? (Player) sender : null;
        if (p != null && !p.hasPermission("mistrz.admin") && !p.hasPermission("klany.admin")) {
            sender.sendMessage("Brak uprawnień");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("setodlamek")) {
            if (plugin.getMistrzSystem() == null) {
                sender.sendMessage("Brak systemu patronów (MistrzSystem) - nie można ustawić odłamka.");
                return true;
            }

            // Z konsoli nie ma przedmiotu "w ręce" - da się zmienić tylko ILOŚĆ odłamków
            // (przedmiot-wzorzec ustawia się w grze).
            if (p == null) {
                if (args.length < 2) {
                    sender.sendMessage("Użycie z konsoli: /adminpatron setodlamek <ilość> (zmienia tylko ilość; przedmiot ustaw w grze, trzymając go w ręce)");
                    return true;
                }
                int consoleAmount;
                try {
                    consoleAmount = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("Podaj poprawną liczbę.");
                    return true;
                }
                if (consoleAmount <= 0) {
                    sender.sendMessage("Podaj poprawną, dodatnią ilość.");
                    return true;
                }
                plugin.getMistrzSystem().setOdlamekAmount(consoleAmount);
                sender.sendMessage("Ustawiono ilość odłamków: " + consoleAmount + " (przedmiot bez zmian).");
                return true;
            }

            int amount = plugin.getMistrzSystem().getOdlamekAmount();
            if (args.length >= 2) {
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (Exception ignored) {
                }
            }
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                p.sendMessage("Trzymaj w ręce przedmiot, który ma być używany jako odłamki.");
                return true;
            }

            // NAPRAWA: item.clone() zapisywany jest teraz z PEŁNYM NBT (bajt w bajt,
            // patrz MistrzSystem#setOdlamekTemplate), a ilość jest ustawiana przez
            // JEDYNE źródło prawdy (setOdlamekAmount) - bez starego, zdublowanego
            // klucza "odlamekAmount", który potrafił zostać "za sobą" ze starą
            // wartością i sprawiać wrażenie, że komenda nic nie zmienia.
            plugin.getMistrzSystem().setOdlamekTemplate(item.clone());
            plugin.getMistrzSystem().setOdlamekAmount(amount);
            p.sendMessage("Ustawiono odlamki: " + amount + " x " + item.getType().name() + " (pełne NBT zapisane).");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("resetdraw") && args.length >= 2) {
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("Nie znaleziono gracza.");
                return true;
            }
            if (plugin.getMistrzSystem() == null) {
                sender.sendMessage("Brak systemu patronów (MistrzSystem).");
                return true;
            }
            plugin.getMistrzSystem().resetDrawCooldown(target);
            String msg = plugin.getPatronConfig().getString("resetDraw.wiadomosc", "&aZresetowałeś czas resetu patrona!");
            sender.sendMessage(MistrzSystem.color(msg));
            return true;
        }

        // Wcześniej nie było ŻADNEGO sposobu, żeby faktycznie wydać graczowi ustawiony
        // odłamek - /adminpatron setodlamek tylko zapisywało przedmiot jako "wzorzec" w
        // patron.yml, ale nikt nie mógł go potem dostać do ręki. Ta komenda go wydaje,
        // dokładnie w takiej postaci (cały NBT: nazwa, lore, zaklęcia itd.), w jakiej
        // został ustawiony przez setodlamek.
        if (args.length >= 1 && args[0].equalsIgnoreCase("giveodlamek")) {
            if (args.length < 2) {
                sender.sendMessage("Użycie: /adminpatron giveodlamek <gracz> [ilość]");
                return true;
            }
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("Nie znaleziono gracza (musi być online).");
                return true;
            }
            if (plugin.getMistrzSystem() == null) {
                sender.sendMessage("Brak systemu patronów (MistrzSystem).");
                return true;
            }
            int amount = plugin.getMistrzSystem().getOdlamekAmount();
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (Exception ignored) {
                }
            }
            if (amount <= 0) {
                sender.sendMessage("Podaj poprawną, dodatnią ilość.");
                return true;
            }
            boolean ok = plugin.getMistrzSystem().giveOdlamki(target, amount);
            if (!ok) {
                sender.sendMessage("Brak ustawionego odłamka - najpierw użyj /adminpatron setodlamek.");
                return true;
            }
            sender.sendMessage("Wydano " + amount + "x odłamek graczowi " + target.getName() + ".");
            if (p == null || !target.equals(p)) {
                target.sendMessage(MistrzSystem.color("&aOtrzymałeś &f" + amount + "x &aodłamek patrona!"));
            }
            return true;
        }

        sender.sendMessage("Usage: /adminpatron setodlamek [amount] (hold item; z konsoli tylko <ilość>) | giveodlamek <gracz> [ilość] | resetdraw <gracz>");
        return true;
    }
}
