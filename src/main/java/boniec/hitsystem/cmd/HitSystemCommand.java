package boniec.hitsystem.cmd;

import boniec.hitsystem.core.DamageCalculator;
import boniec.hitsystem.core.HitSystemManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /hitsystem reload
 * /hitsystem info
 * /hitsystem debug <on|off>
 * /hitsystem test <gracz>
 * /hitsystem elytra [procent]   - podglad / zmiana % obrazen dla graczy w elytrze
 * /hitsystem brakzbroi [procent] - podglad / zmiana % kary za brakujaca czesc zbroi
 * <p>
 * Set-id jest wyliczany automatycznie z waniliowych enchantow (Protection na
 * zbroi, Sharpness na mieczu) - nie ma juz recznego tagowania PDC, wiec nie
 * ma nic do "zapisania" poza samym enchantem, ktory Minecraft trzyma w NBT
 * itemu tak samo jak kazdy inny enchant (przetrwa restart tak jak Sharpness
 * na zwyklym mieczu).
 */
public final class HitSystemCommand implements CommandExecutor, TabCompleter {

    private final HitSystemManager manager;

    public HitSystemCommand(HitSystemManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("hitsystem.admin")) {
                    sender.sendMessage(ChatColor.RED + "Nie masz uprawnień.");
                    return true;
                }
                manager.load();
                sender.sendMessage(ChatColor.GREEN + "[HitSystem] Konfiguracja przeładowana. global-hits.multiplier = x"
                        + manager.getConfig().getGlobalHitsMultiplier());
                return true;
            }
            case "info" -> {
                sender.sendMessage(ChatColor.GOLD + "[HitSystem] enabled=" + manager.getConfig().isEnabled()
                        + " debug=" + manager.getConfig().isDebugEnabled()
                        + " pvp-melee-only=" + manager.getConfig().isPvpMeleeOnly()
                        + " global-hits=x" + manager.getConfig().getGlobalHitsMultiplier()
                        + " custom-iframes=" + manager.getConfig().isCustomIframesEnabled());
                sender.sendMessage(ChatColor.GOLD + "[HitSystem] anti-spam=" + manager.getConfig().isAntiSpamEnabled()
                        + " (min-interval=" + manager.getConfig().getAntiSpamMinIntervalMs() + "ms, mode="
                        + manager.getConfig().getAntiSpamBlockMode() + ", kara=" + manager.getConfig().getAntiSpamPenaltyPercent() + "%)"
                        + " | gang-up-penalty=" + manager.getConfig().isGangUpPenaltyEnabled()
                        + " (" + manager.getConfig().getGangUpPenaltyPercent() + "%, okno="
                        + manager.getConfig().getGangUpWindowMs() + "ms)");
                sender.sendMessage(ChatColor.GOLD + "[HitSystem] elytra=" + (manager.getConfig().isElytraAsChestplateEnabled() ? "on" : "off")
                        + " (" + formatPercent(manager.getConfig().getElytraDamagePercent()) + "% obrażeń)"
                        + " | brak-zbroi=" + (manager.getConfig().isMissingArmorPenaltyEnabled() ? "on" : "off")
                        + " (" + formatPercent(manager.getConfig().getMissingArmorExtraDamagePercent()) + "% za część)");
                if (sender instanceof Player player) {
                    int fullSet = manager.getSetIdentifier().getFullArmorSetId(player);
                    int swordSet = manager.getSetIdentifier().getHeldSwordSetId(player);
                    sender.sendMessage(ChatColor.YELLOW + "Twój Full Set (z Protection na zbroi): " + (fullSet == -1 ? "brak" : fullSet)
                            + " | Set miecza w ręce (z Sharpness): " + (swordSet == -1 ? "brak" : swordSet));
                }
                return true;
            }
            case "debug" -> {
                if (!sender.hasPermission("hitsystem.admin")) {
                    sender.sendMessage(ChatColor.RED + "Nie masz uprawnień.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Użycie: /hitsystem debug <on|off>");
                    return true;
                }
                boolean on = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
                // Debug trzyma się w configu w pamięci na czas działania serwera;
                // trwały zapis do pliku robimy tylko jeśli admin chce (uproszczenie:
                // tu modyfikujemy tylko runtime, pełny reset wraca po /hitsystem reload
                // albo restarcie, zgodnie z wartością w hitsystem.yml).
                manager.setDebugRuntime(on);
                sender.sendMessage(ChatColor.GREEN + "[HitSystem] debug = " + on);
                return true;
            }
            case "elytra" -> {
                return handlePercent(sender, args,
                        "Elytra",
                        manager.getConfig().getElytraDamagePercent(),
                        manager.getConfig()::setElytraDamagePercent);
            }
            case "brakzbroi" -> {
                return handlePercent(sender, args,
                        "Kara za brakujaca czesc zbroi (za kazda)",
                        manager.getConfig().getMissingArmorExtraDamagePercent(),
                        manager.getConfig()::setMissingArmorExtraDamagePercent);
            }
            case "test" -> {
                if (!sender.hasPermission("hitsystem.admin")) {
                    sender.sendMessage(ChatColor.RED + "Nie masz uprawnień.");
                    return true;
                }
                return handleTest(sender, args);
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    /**
     * Wspolna obsluga "/hitsystem <elytra|brakzbroi> [procent]":
     * bez argumentu pokazuje aktualna wartosc, z argumentem ustawia ja (w pamieci + zapis do hitsystem.yml).
     * Wartosc w procentach: 20 = +20% obrazen, 0 = bez zmian, -10 = 10% mniej.
     */
    private boolean handlePercent(CommandSender sender, String[] args, String nazwa, double aktualna,
                                  java.util.function.DoubleConsumer setter) {
        if (!sender.hasPermission("hitsystem.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "[HitSystem] " + nazwa + ": " + formatPercent(aktualna) + "% obrażeń"
                    + ChatColor.GRAY + " (zmień: /hitsystem " + args[0].toLowerCase() + " <procent>)");
            return true;
        }
        double nowa;
        try {
            nowa = Double.parseDouble(args[1].replace(',', '.').replace("%", ""));
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Podaj liczbę, np. /hitsystem " + args[0].toLowerCase() + " 20");
            return true;
        }
        if (nowa < -100.0 || nowa > 1000.0) {
            sender.sendMessage(ChatColor.RED + "Procent musi być w zakresie od -100 do 1000.");
            return true;
        }
        setter.accept(nowa);
        sender.sendMessage(ChatColor.GREEN + "[HitSystem] " + nazwa + ": " + formatPercent(aktualna) + "% -> "
                + formatPercent(nowa) + "% obrażeń (zapisano w hitsystem.yml).");
        return true;
    }

    private static String formatPercent(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private boolean handleTest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Użycie: /hitsystem test <gracz>");
            return true;
        }
        Player attacker = sender instanceof Player p ? p : null;
        Player defender = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (attacker == null) {
            sender.sendMessage(ChatColor.RED + "Tę komendę musi wywołać gracz (żeby był atakującym w symulacji).");
            return true;
        }
        if (defender == null) {
            sender.sendMessage(ChatColor.RED + "Nie znaleziono gracza online: " + args[1]);
            return true;
        }
        DamageCalculator.Result normal = manager.computeDamage(attacker, defender, false);
        DamageCalculator.Result crit = manager.computeDamage(attacker, defender, true);
        sender.sendMessage(ChatColor.GOLD + "=== Symulacja " + attacker.getName() + " -> " + defender.getName() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Set atakującego (efektywny): " + normal.effectiveAttackerSet
                + " | Set obrońcy: " + String.format(java.util.Locale.ROOT, "%.2f", normal.effectiveDefenderSetExact));
        sender.sendMessage(ChatColor.YELLOW + String.format("Normal: %.2f serca (%.2f dmg)", normal.finalHearts, normal.finalDamagePoints));
        sender.sendMessage(ChatColor.YELLOW + String.format("Critical: %.2f serca (%.2f dmg)", crit.finalHearts, crit.finalDamagePoints));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== HitSystem ===");
        sender.sendMessage(ChatColor.YELLOW + "/hitsystem info " + ChatColor.GRAY + "- status i Twój aktualny set (z Protection/Sharpness)");
        sender.sendMessage(ChatColor.YELLOW + "/hitsystem reload " + ChatColor.GRAY + "- przeładuj hitsystem.yml");
        sender.sendMessage(ChatColor.YELLOW + "/hitsystem debug <on|off> " + ChatColor.GRAY + "- przełącz logi debug");
        sender.sendMessage(ChatColor.YELLOW + "/hitsystem test <gracz> " + ChatColor.GRAY + "- symuluj obrażenia bez zadawania ich");
        sender.sendMessage(ChatColor.YELLOW + "/hitsystem elytra [procent] " + ChatColor.GRAY + "- podgląd / zmiana % obrażeń gracza w elytrze (np. 20 = +20%)");
        sender.sendMessage(ChatColor.YELLOW + "/hitsystem brakzbroi [procent] " + ChatColor.GRAY + "- podgląd / zmiana % kary za brakującą część zbroi");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("reload", "info", "debug", "test", "elytra", "brakzbroi"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return filter(Arrays.asList("on", "off"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
