package boniec.tpa;

import boniec.dev.inventory.Main;
import boniec.dev.polezamek.DuelManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class TpaCommands implements TabExecutor, Listener {

    private static final long REQUEST_DURATION = TimeUnit.SECONDS.toNanos(60);
    private final Main plugin;
    // One outgoing request per player; insertion order selects the latest incoming request.
    private final Map<UUID, Request> requests = new LinkedHashMap<>();

    private record Request(UUID target, long createdAt) {}

    public TpaCommands(Main plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (String name : List.of("tpa", "tpaccept", "tpdeny")) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                throw new IllegalStateException("Brak komendy /" + name + " w plugin.yml");
            }
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cTa komenda jest tylko dla graczy.");
            return true;
        }
        removeExpired();
        if (command.getName().equalsIgnoreCase("tpa")) {
            sendRequest(player, args);
        } else {
            respond(player, args, command.getName().equalsIgnoreCase("tpaccept"));
        }
        return true;
    }

    private void sendRequest(Player player, String[] args) {
        if (args.length != 1) {
            msg(player, "&cUżyj: &f/tpa <gracz>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) {
            msg(player, "&cTen gracz jest offline.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            msg(player, "&cNie możesz wysłać prośby do siebie.");
            return;
        }
        if (blocked(player) || blocked(target)) {
            msg(player, "&cNie można używać TPA podczas walki lub pojedynku.");
            return;
        }
        Request previous = requests.get(player.getUniqueId());
        if (previous != null && previous.target().equals(target.getUniqueId())) {
            msg(player, "&cMasz już aktywną prośbę do tego gracza. Poczekaj na odpowiedź lub wygaśnięcie.");
            return;
        }
        if (previous != null) {
            Player previousTarget = Bukkit.getPlayer(previous.target());
            if (previousTarget != null) {
                msg(previousTarget, "&7Prośba od &f" + player.getName() + " &7została anulowana.");
            }
        }
        requests.remove(player.getUniqueId());
        requests.put(player.getUniqueId(), new Request(target.getUniqueId(), System.nanoTime()));
        msg(player, "&aWysłano prośbę o teleport do &f" + target.getName() + "&a. Jest ważna przez 60 sekund.");
        msg(target, "&f" + player.getName() + " &7prosi o teleport do Ciebie. Prośba jest ważna przez 60 sekund.");
        msg(target, "&7Akceptuj: &a/tpaccept " + player.getName() + " &7lub odrzuć: &c/tpdeny " + player.getName());
    }

    private void respond(Player target, String[] args, boolean accept) {
        if (args.length > 1) {
            msg(target, "&cUżyj: &f/" + (accept ? "tpaccept" : "tpdeny") + " [gracz]");
            return;
        }
        UUID requesterId = null;
        for (Map.Entry<UUID, Request> entry : requests.entrySet()) {
            if (!entry.getValue().target().equals(target.getUniqueId())) continue;
            Player requester = Bukkit.getPlayer(entry.getKey());
            if (requester != null && (args.length == 0 || requester.getName().equalsIgnoreCase(args[0]))) {
                requesterId = entry.getKey();
            }
        }
        if (requesterId == null) {
            msg(target, "&cNie masz takiej aktywnej prośby o teleport. Mogła wygasnąć.");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            requests.remove(requesterId);
            msg(target, "&cTen gracz jest offline.");
            return;
        }
        if (!accept) {
            requests.remove(requesterId);
            msg(target, "&7Odrzucono prośbę od &f" + requester.getName() + "&7.");
            msg(requester, "&cGracz &f" + target.getName() + " &codrzucił Twoją prośbę o teleport.");
            return;
        }
        if (blocked(requester) || blocked(target)) {
            msg(target, "&cNie można używać TPA podczas walki lub pojedynku.");
            return;
        }
        if (requester.isDead() || target.isDead()) {
            msg(target, "&cTeleport jest możliwy dopiero po odrodzeniu obu graczy.");
            return;
        }
        requests.remove(requesterId);
        if (!requester.teleport(target.getLocation(), TeleportCause.COMMAND)) {
            msg(target, "&cTeleport został zablokowany.");
            msg(requester, "&cTeleport został zablokowany. Wyślij nową prośbę, aby spróbować ponownie.");
            return;
        }
        msg(target, "&aZaakceptowano prośbę od &f" + requester.getName() + "&a.");
        msg(requester, "&aTeleportowano do &f" + target.getName() + "&a.");
    }

    private boolean blocked(Player player) {
        return DuelManager.blokujeSpawn(player.getUniqueId())
                || (plugin.getAntyLogoutManager() != null
                && plugin.getAntyLogoutManager().isInCombat(player.getUniqueId())
                && !player.hasPermission("antylogout.bypass"));
    }

    private void removeExpired() {
        long now = System.nanoTime();
        requests.values().removeIf(request -> now - request.createdAt() >= REQUEST_DURATION);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        requests.remove(id);
        requests.values().removeIf(request -> request.target().equals(id));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        removeExpired();
        List<String> names = new ArrayList<>();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (candidate.getUniqueId().equals(player.getUniqueId()) || !player.canSee(candidate)) continue;
            if (!command.getName().equalsIgnoreCase("tpa")) {
                Request request = requests.get(candidate.getUniqueId());
                if (request == null || !request.target().equals(player.getUniqueId())) continue;
            }
            if (candidate.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) names.add(candidate.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static void msg(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f亢 " + message));
    }
}
