package boniec.code.core.metin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MetinCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "✦ "
            + ChatColor.LIGHT_PURPLE + "Metin"
            + ChatColor.DARK_PURPLE + " ✦ "
            + ChatColor.GRAY;

    private final MetinManager metinManager;

    public MetinCommand(MetinManager metinManager) {
        this.metinManager = metinManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("metin.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Brak uprawnień!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                handleSpawn(sender, args);
                break;
            case "ustawspawn":
                handleSetSpawn(sender, args);
                break;
            case "usun":
            case "remove":
                handleRemove(sender, args);
                break;
            case "drop":
                handleDrop(sender, args);
                break;
            case "lista":
            case "list":
                handleList(sender);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            case "sethp":
                handleSetHp(sender, args);
                break;
            case "setmobhp":
                handleSetMobHp(sender, args);
                break;
            case "setmobinterval":
                handleSetMobInterval(sender, args);
                break;
            case "setrespawn":
                handleSetRespawn(sender, args);
                break;
            case "respawn":
                handleForceRespawn(sender, args);
                break;
            case "tp":
                handleTeleport(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Tylko gracz!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin spawn <nazwa>");
            return;
        }

        Player player = (Player) sender;
        String name = args[1];

        metinManager.createMetin(name, player.getLocation());
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Metin " + ChatColor.WHITE + name
                + ChatColor.GREEN + " został zespawnowany!");
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Tylko gracz!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin ustawspawn <nazwa>");
            return;
        }

        Player player = (Player) sender;
        String name = args[1];
        Location loc = player.getLocation();

        MetinCrystal existing = metinManager.getMetin(name);
        if (existing != null) {
            existing.forceRemove();
        }

        metinManager.getMetinConfig().saveSpawnLocation(name, loc);
        metinManager.createMetin(name, loc);

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Ustawiono spawn Metina "
                + ChatColor.WHITE + name + ChatColor.GREEN + " na: "
                + ChatColor.WHITE + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin usun <nazwa>");
            return;
        }

        String name = args[1];
        if (metinManager.getMetin(name) == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Metin " + name + " nie istnieje!");
            return;
        }

        metinManager.removeMetin(name);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Metin " + ChatColor.WHITE + name
                + ChatColor.GREEN + " został usunięty!");
    }

    private void handleDrop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj:");
            sender.sendMessage(ChatColor.GRAY + "  /adminmetin drop <nazwa> add <material> <ilość> <szansa%>");
            sender.sendMessage(ChatColor.GRAY + "  /adminmetin drop <nazwa> addhand <szansa%>");
            sender.sendMessage(ChatColor.GRAY + "  /adminmetin drop <nazwa> clear");
            sender.sendMessage(ChatColor.GRAY + "  /adminmetin drop <nazwa> list");
            return;
        }

        String name = args[1];
        String action = args[2].toLowerCase();

        switch (action) {
            case "add":
                handleDropAdd(sender, name, args);
                break;
            case "addhand":
                handleDropAddHand(sender, name, args);
                break;
            case "clear":
                metinManager.getMetinConfig().clearDrops(name);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Wyczyszczono dropy Metina "
                        + ChatColor.WHITE + name);
                break;
            case "list":
            case "lista":
                handleDropList(sender, name);
                break;
            default:
                sender.sendMessage(PREFIX + ChatColor.RED + "Nieznana akcja: " + action);
                break;
        }
    }

    private void handleDropAdd(CommandSender sender, String name, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Użyj: /adminmetin drop " + name + " add <material> <ilość> <szansa%>");
            return;
        }

        Material material;
        try {
            material = Material.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Nieznany materiał: " + args[3]);
            return;
        }

        int amount;
        double chance;
        try {
            amount = Integer.parseInt(args[4]);
            chance = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Nieprawidłowa liczba!");
            return;
        }

        ItemStack item = new ItemStack(material, amount);
        metinManager.getMetinConfig().addDrop(name, item, chance);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Dodano drop: " + ChatColor.WHITE
                + material.name() + " x" + amount + ChatColor.GRAY + " (" + chance + "%)");
    }

    private void handleDropAddHand(CommandSender sender, String name, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Tylko gracz!");
            return;
        }

        Player player = (Player) sender;
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand == null || hand.getType() == Material.AIR) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Trzymaj przedmiot w ręce!");
            return;
        }

        double chance = 10.0D;
        if (args.length >= 4) {
            try {
                chance = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Nieprawidłowa szansa!");
                return;
            }
        }

        metinManager.getMetinConfig().addDrop(name, hand.clone(), chance);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Dodano drop z ręki: "
                + ChatColor.WHITE + hand.getType().name() + " x" + hand.getAmount()
                + ChatColor.GRAY + " (" + chance + "%)");
    }

    private void handleDropList(CommandSender sender, String name) {
        List<MetinDrop> drops = metinManager.getMetinConfig().getDrops(name);

        if (drops.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Metin " + name + " nie ma ustawionych dropów.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.GOLD + "Dropy Metina " + ChatColor.WHITE + name + ChatColor.GOLD + ":");
        for (int i = 0; i < drops.size(); i++) {
            MetinDrop drop = drops.get(i);
            sender.sendMessage(ChatColor.GRAY + "  " + (i + 1) + ". "
                    + ChatColor.WHITE + drop.getMaterial().name() + " x" + drop.getAmount()
                    + ChatColor.GRAY + " - " + ChatColor.YELLOW + drop.getChance() + "%"
                    + (drop.getDisplayName() != null
                    ? ChatColor.GRAY + " (" + drop.getDisplayName() + ")"
                    : ""));
        }
    }

    private void handleList(CommandSender sender) {
        Map<String, MetinCrystal> all = metinManager.getAllMetins();

        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Brak aktywnych metinów.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.GOLD + "Aktywne Metiny (" + all.size() + "):");
        for (Map.Entry<String, MetinCrystal> entry : all.entrySet()) {
            MetinCrystal metin = entry.getValue();
            String status = metin.isAlive()
                    ? (ChatColor.GREEN + "ŻYWY " + ChatColor.WHITE + metin.getCurrentHp() + "/" + metin.getMaxHp())
                    : (ChatColor.RED + "MARTWY");

            String frozen = metin.isHpFrozen() ? ChatColor.RED + " [CHRONIONY]" : "";

            sender.sendMessage(ChatColor.GRAY + "  • "
                    + ChatColor.WHITE + entry.getKey()
                    + ChatColor.GRAY + " - " + status + frozen
                    + ChatColor.GRAY + " @ " + ChatColor.WHITE
                    + metin.getLocation().getBlockX() + ", "
                    + metin.getLocation().getBlockY() + ", "
                    + metin.getLocation().getBlockZ());
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin info <nazwa>");
            return;
        }

        String name = args[1];
        MetinCrystal metin = metinManager.getMetin(name);

        if (metin == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Metin " + name + " nie istnieje!");
            return;
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════════");
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Info: " + ChatColor.WHITE + name);
        sender.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════════");
        sender.sendMessage(ChatColor.GRAY + "  Status: "
                + (metin.isAlive() ? ChatColor.GREEN + "ŻYWY" : ChatColor.RED + "MARTWY"));
        sender.sendMessage(ChatColor.GRAY + "  HP: " + ChatColor.WHITE + metin.getCurrentHp() + "/" + metin.getMaxHp());
        sender.sendMessage(ChatColor.GRAY + "  Chroniony: "
                + (metin.isHpFrozen() ? ChatColor.RED + "TAK" : ChatColor.GREEN + "NIE"));
        sender.sendMessage(ChatColor.GRAY + "  Aktywne moby: " + ChatColor.WHITE + metin.getActiveMobs().size());
        sender.sendMessage(ChatColor.GRAY + "  Mob spawn co: " + ChatColor.WHITE
                + metinManager.getMetinConfig().getMobSpawnInterval(name) + " HP");
        sender.sendMessage(ChatColor.GRAY + "  HP moba: " + ChatColor.WHITE
                + metinManager.getMetinConfig().getMobHp(name));
        sender.sendMessage(ChatColor.GRAY + "  Respawn: " + ChatColor.WHITE
                + metinManager.getMetinConfig().getRespawnTime(name) + "s");
        sender.sendMessage(ChatColor.GRAY + "  Lokalizacja: " + ChatColor.WHITE
                + metin.getLocation().getBlockX() + ", "
                + metin.getLocation().getBlockY() + ", "
                + metin.getLocation().getBlockZ());
        sender.sendMessage(ChatColor.GRAY + "  Ilość dropów: " + ChatColor.WHITE
                + metinManager.getMetinConfig().getDrops(name).size());
        sender.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════════");
    }

    private void handleSetHp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin sethp <nazwa> <hp>");
            return;
        }

        String name = args[1];
        int hp;
        try {
            hp = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Nieprawidłowa wartość HP!");
            return;
        }

        metinManager.getMetinConfig().setMetinMaxHp(name, hp);
        MetinCrystal metin = metinManager.getMetin(name);
        if (metin != null) {
            metin.setMaxHp(hp);
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Ustawiono HP Metina "
                + ChatColor.WHITE + name + ChatColor.GREEN + " na " + ChatColor.WHITE + hp);
    }

    private void handleSetMobHp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin setmobhp <nazwa> <hp>");
            return;
        }

        String name = args[1];
        int hp;
        try {
            hp = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Nieprawidłowa wartość!");
            return;
        }

        metinManager.getMetinConfig().setMobHp(name, hp);
        MetinCrystal metin = metinManager.getMetin(name);
        if (metin != null) {
            metin.setMobHp(hp);
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Ustawiono HP mobów Metina "
                + ChatColor.WHITE + name + ChatColor.GREEN + " na " + ChatColor.WHITE + hp);
    }

    private void handleSetMobInterval(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Użyj: /adminmetin setmobinterval <nazwa> <co ile hp>");
            return;
        }

        String name = args[1];
        int interval;
        try {
            interval = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Nieprawidłowa wartość!");
            return;
        }

        metinManager.getMetinConfig().setMobSpawnInterval(name, interval);
        MetinCrystal metin = metinManager.getMetin(name);
        if (metin != null) {
            metin.setMobSpawnInterval(interval);
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Moby będą się spawnować co "
                + ChatColor.WHITE + interval + ChatColor.GREEN + " HP.");
    }

    private void handleSetRespawn(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin setrespawn <nazwa> <sekundy>");
            return;
        }

        String name = args[1];
        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Nieprawidłowa wartość!");
            return;
        }

        metinManager.getMetinConfig().setRespawnTime(name, seconds);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Czas respawnu Metina "
                + ChatColor.WHITE + name + ChatColor.GREEN + " ustawiony na "
                + ChatColor.WHITE + seconds + "s");
    }

    private void handleForceRespawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin respawn <nazwa>");
            return;
        }

        String name = args[1];
        MetinCrystal metin = metinManager.getMetin(name);

        if (metin == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Metin " + name + " nie istnieje!");
            return;
        }

        metin.forceRemove();
        metin.respawn();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Metin " + ChatColor.WHITE + name
                + ChatColor.GREEN + " został zrespawnowany!");
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Tylko gracz!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Użyj: /adminmetin tp <nazwa>");
            return;
        }

        String name = args[1];
        MetinCrystal metin = metinManager.getMetin(name);

        if (metin == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Metin " + name + " nie istnieje!");
            return;
        }

        ((Player) sender).teleport(metin.getLocation());
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Teleportowano do Metina "
                + ChatColor.WHITE + name);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════════");
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Komendy Metin:");
        sender.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin spawn <nazwa>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin ustawspawn <nazwa>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin usun <nazwa>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin respawn <nazwa>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin tp <nazwa>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin lista");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin info <nazwa>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin sethp <nazwa> <hp>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin setmobhp <nazwa> <hp>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin setmobinterval <nazwa> <hp>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin setrespawn <nazwa> <s>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin drop <nazwa> add <material> <ilość> <szansa%>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin drop <nazwa> addhand <szansa%>");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin drop <nazwa> clear");
        sender.sendMessage(ChatColor.YELLOW + "  /adminmetin drop <nazwa> list");
        sender.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                    "spawn", "ustawspawn", "usun", "drop", "lista",
                    "info", "sethp", "setmobhp", "setmobinterval",
                    "setrespawn", "respawn", "tp"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList(
                    "usun", "remove", "drop", "info", "sethp",
                    "setmobhp", "setmobinterval", "setrespawn",
                    "respawn", "tp").contains(sub)) {
                completions.addAll(metinManager.getAllMetins().keySet());
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("drop")) {
                completions.addAll(Arrays.asList("add", "addhand", "clear", "list"));
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("drop") && args[2].equalsIgnoreCase("add")) {
                for (Material mat : Material.values()) {
                    if (mat.isItem() && mat.name().toLowerCase().startsWith(args[3].toLowerCase())) {
                        completions.add(mat.name());
                    }
                }
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}