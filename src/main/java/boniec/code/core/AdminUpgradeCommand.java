package boniec.code.core;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /aulepsz create|remove|add|setslot - odpowiednik komendy "/aulepsz" ze Skripta.
 */
public class AdminUpgradeCommand implements CommandExecutor, TabCompleter {

    public static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.YELLOW + "" + ChatColor.BOLD + "ULEPSZ" + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " ";
    public static final String PERMISSION = "ulepsz.admin";

    private final JavaPlugin plugin;
    private final UpgradeManager manager;
    private final UpgradeMenuListener menuListener;

    public AdminUpgradeCommand(JavaPlugin plugin, UpgradeManager manager, UpgradeMenuListener menuListener) {
        this.plugin = plugin;
        this.manager = manager;
        this.menuListener = menuListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy!");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnień!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------------------------------");
            player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "System Ulepszeń " + ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + "Admin");
            player.sendMessage("");
            player.sendMessage(ChatColor.WHITE + "/aulepsz create <nazwa> [slot] " + ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + "Tworzy kategorię (slot 0-53 w menu)");
            player.sendMessage(ChatColor.WHITE + "/aulepsz setslot <nazwa> <slot> " + ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + "Zmienia slot kategorii");
            player.sendMessage(ChatColor.WHITE + "/aulepsz remove <nazwa> " + ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + "Usuwa kategorię");
            player.sendMessage(ChatColor.WHITE + "/aulepsz add <nazwa> " + ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + "Otwiera GUI do wkładania itemów");
            player.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------------------------------");
            player.sendMessage(ChatColor.GRAY + "* SHIFT + PPM na item w GUI = ustawienie ceny");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("create")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + ChatColor.RED + "Użyj: /aulepsz create <nazwa>");
                return true;
            }
            String name = args[1];
            if (manager.categoryExists(name)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Taka kategoria już istnieje!");
                return true;
            }

            Integer slot = null;
            if (args.length >= 3) {
                slot = parseSlot(player, args[2]);
                if (slot == null) return true;
                String taken = manager.getCategoryAtSlot(slot, null);
                if (taken != null) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Slot " + slot + " jest już zajęty przez kategorię " + ChatColor.WHITE + taken + ChatColor.RED + "!");
                    return true;
                }
            }

            manager.createCategory(name, slot);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Stworzono kategorię " + ChatColor.WHITE + name + ChatColor.GREEN
                    + (slot != null ? " na slocie " + ChatColor.WHITE + slot + ChatColor.GREEN : "") + "!");
            return true;
        }

        if (sub.equals("setslot")) {
            if (args.length < 3) {
                player.sendMessage(PREFIX + ChatColor.RED + "Użyj: /aulepsz setslot <nazwa> <slot>");
                return true;
            }
            String name = args[1];
            if (!manager.categoryExists(name)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Taka kategoria nie istnieje!");
                return true;
            }
            Integer slot = parseSlot(player, args[2]);
            if (slot == null) return true;
            String taken = manager.getCategoryAtSlot(slot, name);
            if (taken != null) {
                player.sendMessage(PREFIX + ChatColor.RED + "Slot " + slot + " jest już zajęty przez kategorię " + ChatColor.WHITE + taken + ChatColor.RED + "!");
                return true;
            }
            manager.setSlot(name, slot);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Kategoria " + ChatColor.WHITE + name + ChatColor.GREEN + " jest teraz na slocie " + ChatColor.WHITE + slot + ChatColor.GREEN + "!");
            return true;
        }

        if (sub.equals("remove")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + ChatColor.RED + "Użyj: /aulepsz remove <nazwa>");
                return true;
            }
            String name = args[1];
            if (!manager.categoryExists(name)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Taka kategoria nie istnieje!");
                return true;
            }
            manager.removeCategory(name);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Usunięto kategorię " + ChatColor.WHITE + name + ChatColor.GREEN + "!");
            return true;
        }

        if (sub.equals("add")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + ChatColor.RED + "Użyj: /aulepsz add <nazwa>");
                return true;
            }
            String cat = args[1];
            if (!manager.categoryExists(cat)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Taka kategoria nie istnieje!");
                return true;
            }

            Inventory inv = plugin.getServer().createInventory(player, 54, ChatColor.RED + "Edycja: " + ChatColor.WHITE + cat);
            List<ItemStack> items = manager.getItems(cat);
            for (int i = 0; i < items.size() && i < 54; i++) {
                inv.setItem(i, items.get(i));
            }
            player.openInventory(inv);
            menuListener.setEditing(player.getUniqueId(), cat);

            player.sendMessage(PREFIX + ChatColor.GREEN + "Otwarto edycję " + ChatColor.WHITE + cat + ChatColor.GREEN + ". Ułóż itemy od najsłabszego.");
            player.sendMessage(PREFIX + ChatColor.GRAY + "SHIFT + PPM na item = ustaw cenę.");
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "remove", "add", "setslot"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("setslot"))) {
            return filter(manager.getCategoryNames(), args[1]);
        }
        return Collections.emptyList();
    }

    /** Parsuje slot 0-53; przy bledzie wysyla komunikat i zwraca null. */
    private Integer parseSlot(Player player, String raw) {
        int slot;
        try {
            slot = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Slot musi być liczbą od 0 do 53!");
            return null;
        }
        if (slot < 0 || slot > 53) {
            player.sendMessage(PREFIX + ChatColor.RED + "Slot musi być w zakresie 0-53!");
            return null;
        }
        return slot;
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String low = prefix.toLowerCase();
        for (String o : options) {
            if (o.toLowerCase().startsWith(low)) out.add(o);
        }
        return out;
    }
}
