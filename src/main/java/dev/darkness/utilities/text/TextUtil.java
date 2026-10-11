/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.event.ClickEvent
 *  net.kyori.adventure.text.format.TextDecoration
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  net.kyori.adventure.title.Title
 *  net.kyori.adventure.title.Title$Times
 *  org.bukkit.Bukkit
 *  org.bukkit.boss.BarColor
 *  org.bukkit.boss.BarFlag
 *  org.bukkit.boss.BarStyle
 *  org.bukkit.boss.BossBar
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package dev.darkness.utilities.text;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TextUtil {
    private static final Map<UUID, BossBar> BOSS_BARS = new ConcurrentHashMap<UUID, BossBar>();
    private static final Map<UUID, Map<String, ActionBarSlot>> ACTION_BAR_SLOTS = new ConcurrentHashMap<UUID, Map<String, ActionBarSlot>>();
    private static final String ACTION_BAR_SEPARATOR = " &8| ";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private TextUtil() {
    }

    public static void send(Player player, String text) {
        TextUtil.send(player, text, MessageType.CHAT);
    }

    public static void send(Player player, String text, Map<String, String> placeholders) {
        TextUtil.send(player, TextUtil.applyPlaceholders(text, placeholders), MessageType.CHAT);
    }

    public static void send(Player player, String text, MessageType type) {
        if (text == null || text.isEmpty()) {
            return;
        }
        TextUtil.send(player, TextUtil.toComponent(text), type);
    }

    public static void send(Player player, Component component, MessageType type) {
        if (player == null || component == null) {
            return;
        }
        switch (type.ordinal()) {
            case 0: {
                player.sendMessage(component);
                break;
            }
            case 1: {
                player.sendActionBar(component);
                break;
            }
            case 2: {
                TextUtil.sendTitle(player, component, (Component)Component.empty());
                break;
            }
            case 3: {
                TextUtil.sendTitle(player, (Component)Component.empty(), component);
                break;
            }
            case 4: {
                TextUtil.sendTitle(player, component, (Component)Component.empty());
                break;
            }
            case 5: {
                TextUtil.showBossBar(null, player, component);
            }
        }
    }

    public static void sendTitleSubtitle(Player player, String title, String subtitle) {
        if (player == null) {
            return;
        }
        Component t = title != null ? TextUtil.toComponent(title) : Component.empty();
        Component s = subtitle != null ? TextUtil.toComponent(subtitle) : Component.empty();
        TextUtil.sendTitle(player, t, s);
    }

    public static void send(CommandSender sender, String text) {
        if (sender instanceof Player) {
            Player p = (Player)sender;
            TextUtil.send(p, text);
        } else {
            sender.sendMessage(TextUtil.toComponent(text));
        }
    }

    public static void sendClickableMsg(Player player, String text, String command) {
        if (player == null || text == null || command == null || command.isBlank()) {
            return;
        }
        player.sendMessage(TextUtil.toComponent(text).clickEvent(ClickEvent.runCommand((String)command)));
    }

    private static void sendTitle(Player player, Component title, Component subtitle) {
        player.showTitle(Title.title((Component)title, (Component)subtitle, (Title.Times)Title.Times.times((Duration)Duration.ofMillis(500L), (Duration)Duration.ofMillis(3500L), (Duration)Duration.ofMillis(1000L))));
    }

    public static void removeBossBar(Player player) {
        BossBar bar = BOSS_BARS.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    public static void showBossBar(Plugin plugin, Player player, Component msg) {
        TextUtil.showBossBar(plugin, player, msg, BarColor.GREEN, BarStyle.SOLID, 1.0, 100L);
    }

    public static void showBossBar(Plugin plugin, Player player, Component msg, BarColor color, BarStyle style, double progress, long ticks) {
        UUID uuid = player.getUniqueId();
        BossBar old = BOSS_BARS.remove(uuid);
        if (old != null) {
            old.removeAll();
        }
        BossBar bar = Bukkit.createBossBar((String)LEGACY_SECTION.serialize(msg), (BarColor)color, (BarStyle)style, (BarFlag[])new BarFlag[0]);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bar.addPlayer(player);
        BOSS_BARS.put(uuid, bar);
        if (plugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (BOSS_BARS.get(uuid) == bar) {
                    BOSS_BARS.remove(uuid);
                    bar.removeAll();
                }
            }, ticks);
        }
    }

    public static void setActionBarSlot(Player player, String slotName, String text, long duration) {
        if (player == null || !player.isOnline() || slotName == null) {
            return;
        }
        long exp = duration < 0L ? -1L : System.currentTimeMillis() + duration;
        ACTION_BAR_SLOTS.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap()).put(slotName, new ActionBarSlot(TextUtil.toComponent(text), exp));
        TextUtil.flushActionBar(player);
    }

    public static void clearActionBarSlot(Player player, String name) {
        if (player == null) {
            return;
        }
        Map<String, ActionBarSlot> slots = ACTION_BAR_SLOTS.get(player.getUniqueId());
        if (slots != null) {
            slots.remove(name);
            if (slots.isEmpty()) {
                ACTION_BAR_SLOTS.remove(player.getUniqueId());
            } else {
                TextUtil.flushActionBar(player);
            }
        }
    }

    private static void flushActionBar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, ActionBarSlot> slots = ACTION_BAR_SLOTS.get(player.getUniqueId());
        if (slots == null || slots.isEmpty()) {
            return;
        }
        slots.entrySet().removeIf(e -> ((ActionBarSlot)e.getValue()).isExpired());
        if (slots.isEmpty()) {
            ACTION_BAR_SLOTS.remove(player.getUniqueId());
            return;
        }
        ArrayList<Component> parts = new ArrayList<Component>();
        for (ActionBarSlot s : slots.values()) {
            parts.add(s.component());
        }
        player.sendActionBar(TextUtil.joinComponents(parts, TextUtil.toComponent(ACTION_BAR_SEPARATOR)));
    }

    public static void tickActionBars() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.flushActionBar(p);
        }
    }

    public static Component toComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    private static Component joinComponents(List<Component> comps, Component sep) {
        if (comps.isEmpty()) {
            return Component.empty();
        }
        Component res = comps.getFirst();
        for (int i = 1; i < comps.size(); ++i) {
            res = res.append(sep).append(comps.get(i));
        }
        return res;
    }

    public static String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null) {
            return text;
        }
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            text = text.replace("{" + e.getKey() + "}", e.getValue()).replace("%" + e.getKey() + "%", e.getValue());
        }
        return text;
    }

    public static List<String> applyPlaceholders(List<String> lines, Map<String, String> placeholders) {
        if (lines == null) {
            return Collections.emptyList();
        }
        return lines.stream().map(l -> TextUtil.applyPlaceholders(l, placeholders)).toList();
    }

    public static enum MessageType {
        CHAT,
        ACTIONBAR,
        TITLE,
        SUBTITLE,
        TITLE_SUBTITLE,
        BOSSBAR;

    }

    public record ActionBarSlot(Component component, long expiryMillis) {
        public boolean isExpired() {
            return this.expiryMillis >= 0L && System.currentTimeMillis() > this.expiryMillis;
        }
    }
}
