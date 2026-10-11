package boniec.dev.polezamek.zamek;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.polesmierci.PoleSmierciManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ZamekCommands implements CommandExecutor {

    private final Main plugin;
    private final ZamekManager zamekManager;
    private final LodManager lodManager;

    public ZamekCommands(Main plugin, ZamekManager zamekManager, LodManager lodManager) {
        this.plugin = plugin;
        this.zamekManager = zamekManager;
        this.lodManager = lodManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "dajzamek" -> {
                if (!(sender instanceof Player player)) return consoleOnly(sender);
                player.getInventory().addItem(zamekManager.createZamekItem());
                player.sendMessage(ZamekListener.color(zamekManager.getMsgZamekGiven()));
                return true;
            }
            case "dajlodnapatyku" -> {
                if (!(sender instanceof Player player)) return consoleOnly(sender);
                player.getInventory().addItem(lodManager.createLodItem());
                player.sendMessage(ZamekListener.color(lodManager.getMsgGiven()));
                return true;
            }
            case "zresetujcooldown" -> {
                Player target;
                if (args.length > 0) {
                    target = plugin.getServer().getPlayerExact(args[0]);
                    if (target == null) {
                        sender.sendMessage(ZamekListener.color("&cNie znaleziono gracza &f" + args[0] + "&c!"));
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("Musisz podać gracza z konsoli.");
                    return true;
                }
                zamekManager.resetCooldown(target);
                lodManager.resetPlayer(target);
                plugin.getTargetImmunity().clear(target.getUniqueId());
                for (Material mat : List.of(ZamekManager.ZAMEK_MATERIAL, LodManager.LOD_MATERIAL,
                        PoleSmierciManager.ITEM_MATERIAL, Material.HONEYCOMB, Material.STICK, Material.FERMENTED_SPIDER_EYE,
                        Material.ENCHANTED_GOLDEN_APPLE, Material.WOODEN_AXE, Material.LANTERN, Material.BLAZE_ROD)) {
                    target.setCooldown(mat, 0);
                }
                if (args.length > 0) {
                    sender.sendMessage(ZamekListener.color("&aZresetowano cooldown graczowi &e" + target.getName() + "&a!"));
                } else {
                    sender.sendMessage(ZamekListener.color("&aTwój cooldown został zresetowany!"));
                }
                return true;
            }
            case "zamekselector" -> {
                return handleSelector(sender);
            }
            case "ustawzamek" -> {
                return handleUstawZamek(sender);
            }
            case "adminzamek" -> {
                if (args.length < 1) {
                    sender.sendMessage(ZamekListener.color("&cUżycie: &e/adminzamek <selector|ustawzamek>"));
                    return true;
                }
                return switch (args[0].toLowerCase()) {
                    case "selector" -> handleSelector(sender);
                    case "ustawzamek" -> handleUstawZamek(sender);
                    default -> {
                        sender.sendMessage(ZamekListener.color("&cUżycie: &e/adminzamek <selector|ustawzamek>"));
                        yield true;
                    }
                };
            }
        }
        return false;
    }

    private boolean handleSelector(CommandSender sender) {
        if (!(sender instanceof Player player)) return consoleOnly(sender);
        player.getInventory().addItem(zamekManager.createSelectorItem());
        player.sendMessage(ZamekListener.color("&aOtrzymałeś siekierkę do zaznaczania zamku!"));
        return true;
    }

    private boolean handleUstawZamek(CommandSender sender) {
        if (!(sender instanceof Player player)) return consoleOnly(sender);
        if (!zamekManager.hasPos1(player) || !zamekManager.hasBothPositions(player)) {
            player.sendMessage(ZamekListener.color(zamekManager.getMsgNoSelector()));
            return true;
        }
        int count = zamekManager.buildTemplateFromSelection(player);
        if (count < 0) {
            player.sendMessage(ZamekListener.color(zamekManager.getMsgNoSelector()));
            return true;
        }
        player.sendMessage(ZamekListener.color(zamekManager.getMsgTemplateSaved(count)));
        player.sendMessage(ZamekListener.color(zamekManager.getMsgTemplateHint()));
        return true;
    }

    private boolean consoleOnly(CommandSender sender) {
        sender.sendMessage("Tylko gracz może użyć tej komendy.");
        return true;
    }
}
