package boniec.code.core;

import boniec.dev.inventory.Main;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * /trybtworcy [gracz] [on|off] — ofiara i zabojca nie gubia/nie kradna itemow.
 * Ranking, kille i misje licza sie normalnie.
 * Actionbar jest odswiezany caly czas, dopoki tryb jest wlaczony.
 */
public class TworcyTryb implements CommandExecutor, TabCompleter, Listener {

    private static final String FILE_NAME = "tworcy.yml";
    private static final String KEY = "gracze";
    private static final String ACTIONBAR =
            "&#890BBE&lᴛ&#9111C7&lʀ&#9917D0&lʏ&#A21DDA&lʙ &#B229EC&lᴛ&#AA23E3&lᴡ&#A21DDA&lᴏ&#9917D0&lʀ&#9111C7&lᴄ&#890BBE&lʏ";

    private final Main plugin;
    private final Set<UUID> aktywni = new HashSet<>();
    private File file;
    private YamlConfiguration data;
    private BukkitTask actionbarTask;

    public TworcyTryb(Main plugin) {
        this.plugin = plugin;
        load();
        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::odswiezActionbary, 10L, 10L);
    }

    public void zamknij() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }
        save();
    }

    private void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        aktywni.clear();
        for (String raw : data.getStringList(KEY)) {
            try {
                aktywni.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        List<String> asStrings = aktywni.stream().map(UUID::toString).toList();
        data.set(KEY, asStrings);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udalo sie zapisac " + FILE_NAME + ": " + e.getMessage());
        }
    }

    public boolean isEnabled(UUID uuid) {
        return uuid != null && aktywni.contains(uuid);
    }

    public boolean isEnabled(Player player) {
        return player != null && isEnabled(player.getUniqueId());
    }

    /** true gdy ofiara albo zabojca ma tryb tworcy. */
    public boolean dotyczy(Player ofiara, Player zabojca) {
        return isEnabled(ofiara) || isEnabled(zabojca);
    }

    public boolean toggle(Player player) {
        return ustaw(player.getUniqueId(), !isEnabled(player));
    }

    public boolean ustaw(UUID uuid, boolean wlacz) {
        if (wlacz) aktywni.add(uuid);
        else aktywni.remove(uuid);
        save();
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            if (wlacz) wyslijActionbar(p);
            else p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(" "));
        }
        return wlacz;
    }

    private void odswiezActionbary() {
        if (aktywni.isEmpty()) return;
        String colored = MistrzSystem.color(ACTIONBAR);
        for (UUID uuid : aktywni) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
            }
        }
    }

    private void wyslijActionbar(Player player) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(MistrzSystem.color(ACTIONBAR)));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player ofiara = event.getEntity();
        Player zabojca = ofiara.getKiller();
        if (!dotyczy(ofiara, zabojca)) return;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("inventory.trybtworcy") && !sender.hasPermission("trybtworcy.use")) {
            sender.sendMessage(MistrzSystem.color("&cBrak uprawnien."));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MistrzSystem.color("&cUzycie: /trybtworcy <gracz> [on|off]"));
                return true;
            }
            powiadomStan(sender, player.getName(), toggle(player), true);
            return true;
        }

        OfflinePlayer cel = znajdzGracza(args[0]);
        if (cel == null) {
            sender.sendMessage(MistrzSystem.color("&cNie znaleziono gracza &f" + args[0] + "&c."));
            return true;
        }
        boolean wlacz;
        if (args.length >= 2) {
            String tryb = args[1].toLowerCase(Locale.ROOT);
            if (tryb.equals("on") || tryb.equals("wlacz") || tryb.equals("enable")) {
                wlacz = true;
            } else if (tryb.equals("off") || tryb.equals("wylacz") || tryb.equals("disable")) {
                wlacz = false;
            } else {
                sender.sendMessage(MistrzSystem.color("&cUzycie: /trybtworcy [gracz] [on|off]"));
                return true;
            }
        } else {
            wlacz = !isEnabled(cel.getUniqueId());
        }
        ustaw(cel.getUniqueId(), wlacz);
        String nick = cel.getName() != null ? cel.getName() : args[0];
        boolean sobie = sender instanceof Player p && p.getUniqueId().equals(cel.getUniqueId());
        powiadomStan(sender, nick, wlacz, sobie);
        if (!sobie && cel.getPlayer() != null) {
            cel.getPlayer().sendMessage(MistrzSystem.color(wlacz
                    ? "&aOtrzymales tryb tworcy &7(" + sender.getName() + "&7)."
                    : "&cZostal Ci wylaczony tryb tworcy &7(" + sender.getName() + "&7)."));
        }
        return true;
    }

    private void powiadomStan(CommandSender sender, String nick, boolean wlacz, boolean sobie) {
        if (sobie) {
            sender.sendMessage(MistrzSystem.color(wlacz
                    ? "&aTryb tworcy wlaczony &7- nikt nie traci itemow, ranking i kille licza sie."
                    : "&cTryb tworcy wylaczony."));
        } else {
            sender.sendMessage(MistrzSystem.color(wlacz
                    ? "&aWlaczono tryb tworcy dla &f" + nick + "&a."
                    : "&cWylaczono tryb tworcy dla &f" + nick + "&c."));
        }
    }

    private OfflinePlayer znajdzGracza(String nick) {
        Player online = Bukkit.getPlayerExact(nick);
        if (online != null) return online;
        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(nick);
            if (cached != null) return cached;
        } catch (NoSuchMethodError ignored) {
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(nick);
        if (op.hasPlayedBefore() || op.isOnline()) return op;
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("inventory.trybtworcy") && !sender.hasPermission("trybtworcy.use")) {
            return out;
        }
        String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(cur)) out.add(p.getName());
            }
        } else if (args.length == 2) {
            for (String s : List.of("on", "off")) {
                if (s.startsWith(cur)) out.add(s);
            }
        }
        return out;
    }
}
