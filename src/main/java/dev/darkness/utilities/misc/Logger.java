/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 */
package dev.darkness.utilities.misc;

import dev.darkness.utilities.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class Logger {
    private final Plugin plugin;
    private final String pluginTag;
    private boolean debugEnabled = false;

    public Logger(Plugin plugin) {
        this.plugin = plugin;
        this.pluginTag = "&8[&e&l" + plugin.getName() + "&8]";
    }

    public Logger(Plugin plugin, String coloredName) {
        this.plugin = plugin;
        this.pluginTag = "&8[" + coloredName + "&8]";
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    public boolean isDebugEnabled() {
        return this.debugEnabled;
    }

    public void info(String message) {
        this.console(this.pluginTag + " &f" + message + "&r");
    }

    public void success(String message) {
        this.console(this.pluginTag + " &a" + message + "&r");
    }

    public void warn(String message) {
        this.console(this.pluginTag + "&8[&e&lB\u0141\u0104D&8] &f" + message + "&r");
    }

    public void error(String message) {
        this.console(this.pluginTag + "&8[&4&lB\u0141\u0104D&8] &f" + message + "&r");
    }

    public void error(String message, Throwable throwable) {
        this.console(this.pluginTag + "&8[&4&lB\u0141\u0104D&8] &f" + message + " &8(" + throwable.getClass().getSimpleName() + ": " + throwable.getMessage() + ")&r");
    }

    public void debug(String message) {
        if (!this.debugEnabled) {
            return;
        }
        this.console(this.pluginTag + " &7[DEBUG] " + message + "&r");
    }

    public void logStartup(long loadTimeMillis) {
        this.console(this.pluginTag + " &6Uruchomiono plugin! &7(Wczytano w " + loadTimeMillis + "ms)&r");
    }

    public void logStartup(String version, long loadTimeMillis) {
        this.console("&8[&4&l" + this.plugin.getName() + "&8] &6Uruchomiono plugin &e&lv" + version + "&6! &7(Wczytano w " + loadTimeMillis + "ms)&r");
    }

    public void logShutdown() {
        this.console(this.pluginTag + " &cPlugin zosta\u0142 wy\u0142\u0105czony :C&r");
    }

    private void console(String message) {
        Bukkit.getConsoleSender().sendMessage(TextUtil.toComponent(message));
    }
}
