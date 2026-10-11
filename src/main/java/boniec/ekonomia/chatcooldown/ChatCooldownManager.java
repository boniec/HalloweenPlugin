package boniec.ekonomia.chatcooldown;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wymusza minimalny odstep czasu miedzy kolejnymi wiadomosciami gracza na czacie.
 * Gracze z uprawnieniem {@link #PERMISSION_BYPASS} nie podlegaja limitowi.
 * <p>
 * Odpowiednik oryginalnego skryptu Skript "Cooldown na pisanie na czacie".
 * <p>
 * UWAGA: uzywa {@link AsyncPlayerChatEvent}, ktory jest wywolywany na osobnym
 * watku (nie glownym watku serwera) - dlatego cooldown trzymamy w
 * {@link ConcurrentHashMap}, a nie w zwyklej HashMap, zeby uniknac wyscigu
 * przy jednoczesnym pisaniu wielu graczy.
 */
public class ChatCooldownManager implements Listener, CommandExecutor {

    private static final String PERMISSION_BYPASS = "chat.cooldown.bypass";
    private static final String PERMISSION_ADMIN = "chat.cooldown.admin";
    /** Uprawnienie ogolnoadministracyjne z oryginalu, ktore rowniez omija cooldown. */
    private static final String PERMISSION_SKRYPT_ADMIN = "skript.admin";

    /** Domyslny czas odczekania miedzy wiadomosciami, w milisekundach. */
    private volatile long cooldownMs = 3000L;

    private final Map<UUID, Long> ostatniaWiadomosc = new ConcurrentHashMap<>();

    /** Zabronione ciagi znakow - wiadomosci je zawierajace sa blokowane. */
    private static final String[] ZABLOKOWANE_CIAGI = {
            "544545454549",
            "45454545449",
            "5445454549"
    };

    /** Sprawdza czy wiadomosc zawiera ktorykolwiek z zabronionych ciagow. */
    private boolean zawieraZablokowanyCiag(String wiadomosc) {
        for (String ciag : ZABLOKOWANE_CIAGI) {
            if (wiadomosc.contains(ciag)) {
                return true;
            }
        }
        return false;
    }

    // ── Listener czatu ──────────────────────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (zawieraZablokowanyCiag(event.getMessage())) {
            event.setCancelled(true);
            player.sendMessage(color("&c&l✘ &7Twoja wiadomosc zawiera niedozwolony ciag znakow!"));
            return;
        }

        if (player.hasPermission(PERMISSION_BYPASS) || player.hasPermission(PERMISSION_SKRYPT_ADMIN)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long teraz = System.currentTimeMillis();
        Long ostatnia = ostatniaWiadomosc.get(uuid);

        if (ostatnia != null) {
            long minelo = teraz - ostatnia;
            if (minelo < cooldownMs) {
                event.setCancelled(true);
                long pozostalo = cooldownMs - minelo;
                player.sendMessage(color("&c&l✘ &7Zbyt szybko piszesz! Musisz poczekac jeszcze &e"
                        + formatCzas(pozostalo) + "&7."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                return;
            }
        }

        ostatniaWiadomosc.put(uuid, teraz);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ostatniaWiadomosc.remove(event.getPlayer().getUniqueId());
    }

    /** Formatuje milisekundy jako czytelny czas w sekundach (np. "2.3s"). */
    private String formatCzas(long ms) {
        double sekundy = ms / 1000.0;
        return String.format(java.util.Locale.US, "%.1fs", sekundy);
    }

    // ── /chatcd ustaw <sekundy> ─────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(color("&c&l✘ &cNie masz uprawnien!"));
            return true;
        }

        if (args.length >= 2 && (args[0].equalsIgnoreCase("ustaw") || args[0].equalsIgnoreCase("set"))) {
            double sekundy;
            try {
                sekundy = Double.parseDouble(args[1].replace(",", "."));
            } catch (NumberFormatException e) {
                sender.sendMessage(color("&c&l✘ &7Podaj poprawna liczbe sekund!"));
                return true;
            }
            if (sekundy < 0) {
                sender.sendMessage(color("&c&l✘ &7Czas nie moze byc ujemny!"));
                return true;
            }
            cooldownMs = Math.round(sekundy * 1000);
            sender.sendMessage(color("&a&l✔ &7Ustawiono cooldown czatu na: &e" + formatSekundy(sekundy) + " sek.&7!"));
            return true;
        }

        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&e&lCOOLDOWN CZATU - POMOC"));
        sender.sendMessage(color("&7Aktualny cooldown: &e" + formatSekundy(cooldownMs / 1000.0)));
        sender.sendMessage(color("&e/chatcd ustaw <sekundy> &7- Zmienia czas odczekania"));
        sender.sendMessage(color("&8&m--------------------------------"));
        return true;
    }

    private String formatSekundy(double sekundy) {
        if (sekundy == Math.floor(sekundy)) {
            return String.valueOf((long) sekundy);
        }
        return String.format(java.util.Locale.US, "%.1f", sekundy);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
