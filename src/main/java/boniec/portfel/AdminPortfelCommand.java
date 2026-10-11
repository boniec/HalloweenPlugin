package boniec.portfel;

import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminPortfelCommand implements CommandExecutor, TabCompleter {

    private final PortfelManager manager;
    private final PortfelGui gui;

    public AdminPortfelCommand(PortfelManager manager, PortfelGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("portfel.admin")) {
            Msg.send(sender, manager.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            Msg.send(sender, manager.msg("admin-usage"));
            return true;
        }

        String akcja = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(akcja)) {
            manager.wczytajWszystko();
            Msg.send(sender, manager.msg("admin-config-reloaded"));
            return true;
        }

        if ("history".equals(akcja)) {
            if (!(sender instanceof Player viewer)) {
                sender.sendMessage(Msg.color("&cHistoria wymaga gracza online."));
                return true;
            }
            if (args.length < 2) {
                Msg.send(sender, manager.msg("admin-usage"));
                return true;
            }
            OfflinePlayer cel = Bukkit.getOfflinePlayer(args[1]);
            UUID uuid = cel.getUniqueId();
            if (cel.getName() == null && !manager.getHistoria(uuid).isEmpty()) {
                // ok - ma historie
            } else if (cel.getName() == null && manager.getBalance(uuid) == 0) {
                Msg.send(sender, manager.msg("admin-player-not-found"));
                return true;
            }
            gui.otworzHistorie(viewer, uuid, 0);
            return true;
        }

        if ("check".equals(akcja)) {
            if (args.length < 2) {
                Msg.send(sender, manager.msg("admin-usage"));
                return true;
            }
            OfflinePlayer cel = Bukkit.getOfflinePlayer(args[1]);
            String nick = cel.getName() != null ? cel.getName() : args[1];
            Msg.send(sender, manager.msg("admin-check-balance")
                    .replace("{PLAYER}", nick)
                    .replace("{BALANCE}", manager.formatBalance(manager.getBalance(cel.getUniqueId()))));
            return true;
        }

        if (args.length < 3) {
            Msg.send(sender, manager.msg("admin-usage"));
            return true;
        }

        OfflinePlayer cel = Bukkit.getOfflinePlayer(args[1]);
        String nick = cel.getName() != null ? cel.getName() : args[1];
        double kwota;
        try {
            kwota = Double.parseDouble(args[2].replace(",", "."));
        } catch (NumberFormatException e) {
            Msg.send(sender, manager.msg("admin-invalid-amount"));
            return true;
        }
        if (kwota < 0) {
            Msg.send(sender, manager.msg("admin-negative-amount"));
            return true;
        }

        UUID uuid = cel.getUniqueId();
        switch (akcja) {
            case "add" -> {
                manager.dodaj(uuid, kwota, sender.getName(), "Dodanie srodkow");
                Msg.send(sender, manager.msg("admin-balance-added")
                        .replace("{PLAYER}", nick)
                        .replace("{AMOUNT}", manager.formatBalance(kwota)));
                if (cel.isOnline() && cel.getPlayer() != null) {
                    Msg.send(cel.getPlayer(), manager.msg("balance-added")
                            .replace("{AMOUNT}", manager.formatBalance(kwota)));
                }
            }
            case "take" -> {
                if (!manager.zabierz(uuid, kwota, sender.getName(), "Zabranie srodkow")) {
                    Msg.send(sender, manager.msg("insufficient-funds"));
                    return true;
                }
                Msg.send(sender, manager.msg("admin-balance-removed")
                        .replace("{PLAYER}", nick)
                        .replace("{AMOUNT}", manager.formatBalance(kwota)));
                if (cel.isOnline() && cel.getPlayer() != null) {
                    Msg.send(cel.getPlayer(), manager.msg("balance-removed")
                            .replace("{AMOUNT}", manager.formatBalance(kwota)));
                }
            }
            case "set" -> {
                manager.ustaw(uuid, kwota);
                manager.zapiszDane(false);
                Msg.send(sender, manager.msg("admin-balance-set")
                        .replace("{PLAYER}", nick)
                        .replace("{AMOUNT}", manager.formatBalance(kwota)));
                if (cel.isOnline() && cel.getPlayer() != null) {
                    Msg.send(cel.getPlayer(), manager.msg("balance-set")
                            .replace("{AMOUNT}", manager.formatBalance(kwota)));
                }
            }
            default -> Msg.send(sender, manager.msg("admin-invalid-command"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("portfel.admin")) return List.of();
        if (args.length == 1) {
            return filtruj(List.of("add", "take", "set", "reload", "history", "check"), args[0]);
        }
        if (args.length == 2 && !"reload".equalsIgnoreCase(args[0])) {
            List<String> nicki = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) nicki.add(p.getName());
            return filtruj(nicki, args[1]);
        }
        return List.of();
    }

    private List<String> filtruj(List<String> opcje, String start) {
        String s = start.toLowerCase(Locale.ROOT);
        return opcje.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(s)).collect(Collectors.toList());
    }
}
