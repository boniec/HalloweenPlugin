package boniec.joinkit;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Kit startowy: /joinkit edytuj — przedmioty na pierwsze wejście i po śmierci (respawn).
 */
public final class JoinKit implements CommandExecutor, TabCompleter, Listener {

    private static final String PREFIX = "&8[&eJoinKit&8] ";
    private static final String TITLE = Msg.color("&8Join kit");
    private static final int SIZE = 54;

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration cfg;
    private final List<ItemStack> items = new ArrayList<>();
    private final Set<UUID> skipRespawnKit = new HashSet<>();

    public JoinKit(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "joinkit.yml");
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("joinkit") != null) {
            plugin.getCommand("joinkit").setExecutor(this);
            plugin.getCommand("joinkit").setTabCompleter(this);
        }
    }

    private void load() {
        if (!file.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie utworzono joinkit.yml: " + e.getMessage());
            }
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        items.clear();
        ConfigurationSection slots = cfg.getConfigurationSection("slots");
        if (slots != null) {
            List<Integer> keys = new ArrayList<>();
            for (String k : slots.getKeys(false)) {
                try {
                    keys.add(Integer.parseInt(k));
                } catch (NumberFormatException ignored) {
                }
            }
            keys.sort(Integer::compareTo);
            for (int k : keys) {
                ItemStack it = slots.getItemStack(String.valueOf(k));
                if (it != null && it.getType() != Material.AIR) items.add(it.clone());
            }
        }
    }

    private void saveFromInventory(Inventory inv) {
        cfg.set("slots", null);
        items.clear();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
            cfg.set("slots." + i, it);
            items.add(it.clone());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie zapisano joinkit.yml: " + e.getMessage());
        }
    }

    void give(Player player) {
        if (items.isEmpty()) return;
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            var leftover = player.getInventory().addItem(it.clone());
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
    }

    private void openEditor(Player player) {
        JoinKitHolder holder = new JoinKitHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.inv = inv;
        ConfigurationSection slots = cfg.getConfigurationSection("slots");
        if (slots != null) {
            for (String k : slots.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(k);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (slot < 0 || slot >= SIZE) continue;
                ItemStack it = slots.getItemStack(k);
                if (it != null) inv.setItem(slot, it);
            }
        }
        player.openInventory(inv);
        player.sendMessage(Msg.color(PREFIX + "&7Wloz przedmioty i zamknij okno — zapisze sie automatycznie."));
        player.sendMessage(Msg.color(PREFIX + "&7Gracz dostaje je &fprzy pierwszym wejsciu &7i &fpo smierci&7."));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("joinkit.admin")) {
            sender.sendMessage(Msg.color(PREFIX + "&cBrak permisji."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Msg.color(PREFIX + "&e/joinkit edytuj &7- GUI z itemami startowymi"));
            sender.sendMessage(Msg.color(PREFIX + "&e/joinkit reload"));
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("edytuj") || sub.equals("edit")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Msg.color(PREFIX + "&cTylko dla graczy."));
                return true;
            }
            openEditor(p);
            return true;
        }
        if (sub.equals("reload")) {
            load();
            sender.sendMessage(Msg.color(PREFIX + "&aPrzeladowano joinkit.yml &8(&f" + items.size() + " &aprzedmiotow&8)."));
            return true;
        }
        sender.sendMessage(Msg.color(PREFIX + "&cUzycie: /joinkit edytuj | reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("joinkit.admin")) return List.of();
        if (args.length == 1) {
            String pre = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : List.of("edytuj", "reload")) {
                if (s.startsWith(pre)) out.add(s);
            }
            return out;
        }
        return List.of();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof JoinKitHolder)) return;
        saveFromInventory(event.getInventory());
        if (event.getPlayer() instanceof Player p) {
            p.sendMessage(Msg.color(PREFIX + "&aZapisano kit startowy &8(&f" + items.size() + " &aprzedmiotow&8)."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.hasPlayedBefore()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) give(p);
        }, 10L);
    }

    /** PvP w Main ustawia keepInventory tylko technicznie (i czyści eq) - o tym rozstrzyga Main przy respawnie. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            skipRespawnKit.add(event.getEntity().getUniqueId());
        } else {
            skipRespawnKit.remove(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        boolean keepFlag = skipRespawnKit.remove(p.getUniqueId());
        boniec.dev.inventory.Main main = boniec.dev.inventory.Main.getInstance();
        Boolean eqZachowany = main != null ? main.zabierzEqZachowanyPoSmierci(p.getUniqueId()) : null;
        if (eqZachowany != null ? eqZachowany : keepFlag) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) give(p);
        }, 2L);
    }

    static final class JoinKitHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
