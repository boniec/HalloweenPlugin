package boniec.adminutil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Komendy administracyjne: /vanish /gm /fly /god /heal /feed.
 *
 * Permisje: core.&lt;komenda&gt; (na sobie) oraz core.&lt;komenda&gt;.others (na innym graczu),
 * dodatkowo core.vanish.see (widzenie graczy w vanishu). Wszystkie domyślnie dla OP.
 */
public final class AdminUtilCommands implements TabExecutor {

    // Ikony Nexo (ikony_lista.md). Przed ikoną dajemy "&f", żeby zachowała własne kolory tekstury.
    private static final String ICON_ERROR = "亢";   // czerwony trójkąt ostrzegawczy
    private static final String ICON_BLOCKED = "与"; // czerwone przekreślone koło
    private static final String ICON_VANISH = "临";  // oko
    private static final String ICON_GOD = "世";     // tarcza
    private static final String ICON_FLY = "互";     // skrzydlaty but
    private static final String ICON_HEAL = "乜";    // mikstura życia
    private static final String ICON_FEED = "业";    // chleb
    private static final String ICON_GM = "事";      // zębatka (ustawienia)

    private static final String NO_PERMISSION = "&f" + ICON_ERROR + " &cNie masz uprawnień!";
    private static final List<String> MODES_TAB = List.of(
            "survival", "creative", "adventure", "spectator", "0", "1", "2", "3");

    /** Efekty usuwane przez /heal. */
    private static final Set<PotionEffectType> NEGATIVE_EFFECTS = Set.of(
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.HUNGER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS,
            PotionEffectType.LEVITATION,
            PotionEffectType.DARKNESS,
            PotionEffectType.UNLUCK);

    private final JavaPlugin plugin;
    private final AdminUtilManager manager;

    public AdminUtilCommands(JavaPlugin plugin, AdminUtilManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void register() {
        for (String name : List.of("vanish", "gm", "fly", "god", "heal", "feed", "keepinv")) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                plugin.getLogger().warning("Brak komendy /" + name + " w plugin.yml");
                continue;
            }
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "vanish" -> withTarget(sender, args, name, "/vanish [gracz]", this::vanish);
            case "fly" -> withTarget(sender, args, name, "/fly [gracz]", this::fly);
            case "god" -> withTarget(sender, args, name, "/god [gracz]", this::god);
            case "heal" -> withTarget(sender, args, name, "/heal [gracz]", this::heal);
            case "feed" -> withTarget(sender, args, name, "/feed [gracz]", this::feed);
            case "gm" -> gamemode(sender, args);
            case "keepinv" -> keepInv(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ───────────────────────── wspólne ─────────────────────────

    private static String perm(String commandName) {
        return "core." + commandName;
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static void msg(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    /** Sprawdza permisję, wybiera gracza docelowego (siebie albo wskazanego) i wykonuje akcję. */
    private void withTarget(CommandSender sender, String[] args, String commandName, String usage,
                            BiConsumer<CommandSender, Player> action) {
        if (!sender.hasPermission(perm(commandName))) {
            msg(sender, NO_PERMISSION);
            return;
        }
        Player target = resolveTarget(sender, args, 0, commandName, usage);
        if (target != null) {
            action.accept(sender, target);
        }
    }

    /** @return gracz docelowy albo null (wiadomość o błędzie została już wysłana). */
    private Player resolveTarget(CommandSender sender, String[] args, int index, String commandName, String usage) {
        if (args.length > index) {
            String requested = args[index];
            boolean self = sender instanceof Player p && p.getName().equalsIgnoreCase(requested);
            if (!self && !sender.hasPermission(perm(commandName) + ".others")) {
                msg(sender, NO_PERMISSION);
                return null;
            }
            Player target = Bukkit.getPlayerExact(requested);
            if (target == null) {
                msg(sender, "&f" + ICON_BLOCKED + " &cTen gracz jest obecnie offline!");
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        msg(sender, "&f" + ICON_ERROR + " &cUżyj: &f" + usage);
        return null;
    }

    private void feedbackToggle(CommandSender sender, Player target, String icon, String feature, boolean enabled) {
        String state = enabled ? "&awłączony" : "&cwyłączony";
        msg(target, "&f" + icon + " &7" + feature + ": " + state + "&7.");
        if (!target.equals(sender)) {
            msg(sender, "&f" + icon + " &7" + feature + " gracza &f" + target.getName() + "&7: " + state + "&7.");
        }
    }

    // ───────────────────────── akcje ─────────────────────────

    private void vanish(CommandSender sender, Player target) {
        boolean enabled = manager.toggleVanish(target);
        feedbackToggle(sender, target, ICON_VANISH, "Vanish", enabled);
    }

    private void god(CommandSender sender, Player target) {
        boolean enabled = manager.toggleGod(target);
        feedbackToggle(sender, target, ICON_GOD, "Tryb God", enabled);
    }

    @SuppressWarnings("deprecation")
    private void fly(CommandSender sender, Player target) {
        GameMode mode = target.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            msg(sender, "&f" + ICON_ERROR + " &cGracz &f" + target.getName() + " &cma latanie w tym trybie gry zawsze włączone.");
            return;
        }
        boolean enabled = !target.getAllowFlight();
        target.setAllowFlight(enabled);
        if (enabled) {
            if (!target.isOnGround()) {
                target.setFlying(true);
            }
        } else {
            target.setFlying(false);
        }
        feedbackToggle(sender, target, ICON_FLY, "Latanie", enabled);
    }

    private void heal(CommandSender sender, Player target) {
        if (target.isDead()) {
            msg(sender, "&f" + ICON_BLOCKED + " &cGracz &f" + target.getName() + " &cnie żyje.");
            return;
        }
        AttributeInstance maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        target.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setExhaustion(0f);
        target.setFireTicks(0);
        for (PotionEffect effect : new ArrayList<>(target.getActivePotionEffects())) {
            if (NEGATIVE_EFFECTS.contains(effect.getType())) {
                target.removePotionEffect(effect.getType());
            }
        }
        msg(target, "&f" + ICON_HEAL + " &aZostałeś uleczony.");
        if (!target.equals(sender)) {
            msg(sender, "&f" + ICON_HEAL + " &aUleczono gracza &f" + target.getName() + "&a.");
        }
    }

    private void feed(CommandSender sender, Player target) {
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setExhaustion(0f);
        msg(target, "&f" + ICON_FEED + " &aTwój głód został zaspokojony.");
        if (!target.equals(sender)) {
            msg(sender, "&f" + ICON_FEED + " &aNakarmiono gracza &f" + target.getName() + "&a.");
        }
    }

    // ───────────────────────── /gm ─────────────────────────

    private void gamemode(CommandSender sender, String[] args) {
        if (!sender.hasPermission(perm("gm"))) {
            msg(sender, NO_PERMISSION);
            return;
        }
        String usage = "/gm <tryb> [gracz]";
        if (args.length == 0) {
            msg(sender, "&f" + ICON_ERROR + " &cUżyj: &f" + usage + " &8(0/s, 1/c, 2/a, 3/sp)");
            return;
        }
        GameMode mode = parseMode(args[0]);
        if (mode == null) {
            msg(sender, "&f" + ICON_BLOCKED + " &cNieznany tryb gry: &f" + args[0] + "&c. Dostępne: &f0/s&c, &f1/c&c, &f2/a&c, &f3/sp&c.");
            return;
        }
        Player target = resolveTarget(sender, args, 1, "gm", usage);
        if (target == null) {
            return;
        }
        target.setGameMode(mode);
        msg(target, "&f" + ICON_GM + " &aTwój tryb gry został zmieniony na &f" + modeName(mode) + "&a.");
        if (!target.equals(sender)) {
            msg(sender, "&f" + ICON_GM + " &aZmieniono tryb gry gracza &f" + target.getName() + " &ana &f" + modeName(mode) + "&a.");
        }
    }

    private static GameMode parseMode(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival", "przetrwanie" -> GameMode.SURVIVAL;
            case "1", "c", "creative", "kreatywny" -> GameMode.CREATIVE;
            case "2", "a", "adventure", "przygoda", "przygodowy" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator", "obserwator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static String modeName(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "Przetrwanie";
            case CREATIVE -> "Kreatywny";
            case ADVENTURE -> "Przygodowy";
            case SPECTATOR -> "Obserwator";
        };
    }

    // ───────────────────────── /keepinv ─────────────────────────

    private void keepInv(CommandSender sender, String[] args) {
        if (!sender.hasPermission(perm("keepinv"))) {
            msg(sender, NO_PERMISSION);
            return;
        }
        if (args.length == 0) {
            String state = manager.isKeepInventory() ? "&awłączony" : "&cwyłączony";
            msg(sender, "&f" + ICON_GOD + " &7Keep inventory: " + state + "&7. Użyj: &f/keepinv on&7 lub &f/keepinv off");
            return;
        }
        Boolean enabled = parseOnOff(args[0]);
        if (enabled == null) {
            msg(sender, "&f" + ICON_ERROR + " &cUżyj: &f/keepinv on &club &f/keepinv off");
            return;
        }
        manager.setKeepInventory(enabled);
        String state = enabled ? "&awłączony" : "&cwyłączony";
        msg(sender, "&f" + ICON_GOD + " &7Keep inventory: " + state + "&7.");
    }

    private static Boolean parseOnOff(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "on", "wlacz", "włącz", "true", "1" -> true;
            case "off", "wylacz", "wyłącz", "false", "0" -> false;
            default -> null;
        };
    }

    // ───────────────────────── tab-complete ─────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(perm(name))) {
            return List.of();
        }
        boolean gm = name.equals("gm");
        if (gm && args.length == 1) {
            return filter(MODES_TAB, args[0]);
        }
        if (name.equals("keepinv") && args.length == 1) {
            return filter(List.of("on", "off"), args[0]);
        }
        int playerArg = gm ? 1 : 0;
        if (args.length == playerArg + 1 && sender.hasPermission(perm(name) + ".others")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[playerArg]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
