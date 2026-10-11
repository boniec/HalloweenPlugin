package boniec.dev.inventory.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Komendy /kosz, /repair, /repairall oraz prywatne wiadomości. */
public final class UtilityCommands implements TabExecutor {
    private static final String SPY_PERMISSION = "pw.spy";
    private static final String TRASH_TITLE = color("&7Kosz");

    private final JavaPlugin plugin;

    public UtilityCommands(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        registerCommand("kosz");
        registerCommand("repair");
        registerCommand("repairall");
        registerCommand("msg");
        registerCommand("r");
        registerCommand("ignore");
        registerCommand("spy");
    }

    private void registerCommand(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Brak komendy /" + name + " w plugin.yml");
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cTej komendy może użyć tylko gracz."));
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "kosz" -> openTrash(player);
            case "repair" -> repairHeldItem(player);
            case "repairall" -> repairInventory(player);
            case "msg" -> sendPrivateMessage(player, args);
            case "r" -> reply(player, args);
            case "ignore" -> toggleIgnore(player, args);
            case "spy" -> toggleSpy(player);
            default -> { return false; }
        }
        return true;
    }

    private void openTrash(Player player) {
        player.openInventory(Bukkit.createInventory(null, 54, TRASH_TITLE));
    }

    private void repairHeldItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(color("&f亢 &cMusisz trzymać przedmiot w ręce."));
            return;
        }
        repair(item);
        player.sendMessage(color("&f亩 &aNaprawiono przedmiot!"));
    }

    private void repairInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            repair(item);
        }
        for (ItemStack item : inventory.getArmorContents()) {
            repair(item);
        }
        repair(inventory.getItemInOffHand());
        player.sendMessage(color("&f亩 &aNaprawiono wszystkie przedmioty w ekwipunku!"));
    }

    private void repair(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (item.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
            damageable.setDamage(0);
            item.setItemMeta(damageable);
        }
    }

    private void sendPrivateMessage(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color("&cUżyj: &f/msg <gracz> <wiadomość>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(color("&cTen gracz jest obecnie offline!"));
            return;
        }
        if (target.equals(sender)) {
            sender.sendMessage(color("&cNie możesz pisać sam do siebie!"));
            return;
        }
        if (isIgnored(target.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage(color("&cTen gracz Cię ignoruje!"));
            return;
        }
        if (!boniec.ustawienia.UstawieniaManager.on(target, boniec.ustawienia.UstawieniaKlucz.MSG)) {
            sender.sendMessage(color("&cTen gracz ma wylaczone prywatne wiadomosci (/ustawienia)."));
            return;
        }
        sendPrivateMessage(sender, target, joinMessage(args, 1));
    }

    private void reply(Player sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&cUżyj: &f/r <wiadomość>"));
            return;
        }
        String targetId = plugin.getConfig().getString("private-messages.reply." + sender.getUniqueId());
        if (targetId == null) {
            sender.sendMessage(color("&cNie masz komu odpisać!"));
            return;
        }
        Player target;
        try {
            target = Bukkit.getPlayer(UUID.fromString(targetId));
        } catch (IllegalArgumentException exception) {
            plugin.getConfig().set("private-messages.reply." + sender.getUniqueId(), null);
            sender.sendMessage(color("&cTen gracz jest obecnie offline!"));
            return;
        }
        if (target == null) {
            sender.sendMessage(color("&cTen gracz jest obecnie offline!"));
            return;
        }
        if (isIgnored(target.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage(color("&cTen gracz Cię ignoruje!"));
            return;
        }
        if (!boniec.ustawienia.UstawieniaManager.on(target, boniec.ustawienia.UstawieniaKlucz.MSG)) {
            sender.sendMessage(color("&cTen gracz ma wylaczone prywatne wiadomosci (/ustawienia)."));
            return;
        }
        sendPrivateMessage(sender, target, joinMessage(args, 0));
    }

    private void toggleIgnore(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(color("&cUżyj: &f/ignore <gracz>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(color("&cTen gracz jest obecnie offline!"));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(color("&cNie możesz ignorować samego siebie!"));
            return;
        }

        String path = ignorePath(target.getUniqueId(), player.getUniqueId());
        if (plugin.getConfig().getBoolean(path)) {
            plugin.getConfig().set(path, null);
            player.sendMessage(color("&aPrzestałeś ignorować gracza &f" + target.getName() + "&a!"));
        } else {
            plugin.getConfig().set(path, true);
            player.sendMessage(color("&cIgnorujesz teraz gracza &f" + target.getName() + "&c!"));
        }
        plugin.saveConfig();
    }

    private void toggleSpy(Player player) {
        if (!player.hasPermission(SPY_PERMISSION)) {
            player.sendMessage(color("&cNie masz uprawnień!"));
            return;
        }
        String path = "private-messages.spy." + player.getUniqueId();
        boolean enabled = !plugin.getConfig().getBoolean(path);
        plugin.getConfig().set(path, enabled);
        plugin.saveConfig();
        player.sendMessage(color(enabled
                ? "&aWłączono tryb SocialSpy (podgląd PW)!"
                : "&cWyłączono tryb SocialSpy (podgląd PW)!"));
    }

    private void sendPrivateMessage(Player sender, Player target, String message) {
        sender.sendMessage(color("&8[&eJa &8➔ &6" + target.getName() + "&8] &f" + message));
        target.sendMessage(color("&8[&6" + sender.getName() + " &8➔ &eJa&8] &f" + message));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);

        plugin.getConfig().set("private-messages.reply." + sender.getUniqueId(), target.getUniqueId().toString());
        plugin.getConfig().set("private-messages.reply." + target.getUniqueId(), sender.getUniqueId().toString());
        plugin.saveConfig();

        String spyMessage = color("&7" + sender.getName() + " &8➔ &7" + target.getName() + "&8: &f" + message);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.hasPermission(SPY_PERMISSION)
                    && plugin.getConfig().getBoolean("private-messages.spy." + viewer.getUniqueId())
                    && !viewer.equals(sender) && !viewer.equals(target)) {
                viewer.sendMessage(spyMessage);
            }
        }
    }

    private boolean isIgnored(UUID recipient, UUID sender) {
        return plugin.getConfig().getBoolean(ignorePath(sender, recipient));
    }

    private String ignorePath(UUID ignoredPlayer, UUID ignoringPlayer) {
        // Keep the existing saved format: ignored player first, player who enabled /ignore second.
        return "private-messages.ignore." + ignoredPlayer + "." + ignoringPlayer;
    }

    private String joinMessage(String[] args, int start) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length));
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("msg") || command.getName().equalsIgnoreCase("ignore")) {
            if (args.length == 1) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        names.add(player.getName());
                    }
                }
                return names;
            }
        }
        return List.of();
    }
}
