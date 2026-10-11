package boniec.generatory;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminGeneratorCommand implements CommandExecutor {

    private final GeneratorManager generatorManager;

    public AdminGeneratorCommand(GeneratorManager generatorManager) {
        this.generatorManager = generatorManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("core.admingenerator")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnień.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Użycie: /admingenerator <stworz|edytuj|clean>");
            return true;
        }

        if (args[0].equalsIgnoreCase("clean")) {
            int usuniete = generatorManager.cleanOrphanedHolograms();
            player.sendMessage(ChatColor.GREEN + "Usunięto " + usuniete + " martwych armor standow (hologramow bez generatora).");
            return true;
        }

        if (args[0].equalsIgnoreCase("stworz")) {
            if (args.length < 4) {
                player.sendMessage(ChatColor.RED + "Użycie: /admingenerator stworz <promien> <wysokosc> <blok1> [blok2...]");
                return true;
            }
            try {
                int promien = Integer.parseInt(args[1]);
                int wysokosc = Integer.parseInt(args[2]);
                List<Material> materials = new ArrayList<>();
                for (int i = 3; i < args.length; i++) {
                    materials.add(Material.valueOf(args[i].toUpperCase()));
                }

                generatorManager.createGenerator(player.getLocation().getBlock().getLocation(), promien, wysokosc, materials);
                player.sendMessage(ChatColor.GREEN + "Stworzono generator!");

            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Długość i wysokość muszą być liczbami.");
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Nie znaleziono materiału. Użyj poprawnej nazwy z Minecraft.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("edytuj")) {
            Block targetBlock = player.getTargetBlockExact(5);
            if (targetBlock == null) {
                player.sendMessage(ChatColor.RED + "Musisz patrzeć na generator, aby go edytować.");
                return true;
            }

            Generator gen = generatorManager.getGeneratorAt(targetBlock.getLocation());
            if (gen == null) {
                player.sendMessage(ChatColor.RED + "W tym miejscu nie ma generatora.");
                return true;
            }

            generatorManager.openEditGUI(player, gen);
            return true;
        }

        return false;
    }
}