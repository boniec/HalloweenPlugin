/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.plugin.Plugin
 */
package dev.darkness.utilities.misc;

import dev.darkness.utilities.text.TextUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class UpdateChecker {
    private final Plugin plugin;
    private final String versionUrl;
    private final boolean enabled;
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(Plugin plugin, String versionUrl, boolean enabled) {
        this.plugin = plugin;
        this.versionUrl = versionUrl;
        this.enabled = enabled;
    }

    public void checkOnStartup() {
        if (!this.enabled) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection)new URL(this.versionUrl).openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("GET");
                if (connection.getResponseCode() != 200) {
                    return;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));){
                    String response = reader.readLine();
                    if (response == null) {
                        return;
                    }
                    String currentVersion = this.plugin.getDescription().getVersion();
                    this.latestVersion = response.trim();
                    this.updateAvailable = !this.latestVersion.equalsIgnoreCase(currentVersion);
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        String pluginName = this.plugin.getName();
                        ConsoleCommandSender console = Bukkit.getConsoleSender();
                        if (this.updateAvailable) {
                            console.sendMessage(TextUtil.toComponent("&8[&c" + pluginName + "&8] &cDost\u0119pna jest nowa wersja! &8(&4" + this.latestVersion + "&8) | &cTwoja wersja: &4" + currentVersion));
                        } else {
                            console.sendMessage(TextUtil.toComponent("&8[&6&l" + pluginName + "&8] &fPosiadasz aktualn\u0105 wersj\u0119 &8(&e" + currentVersion + "&8)"));
                        }
                    });
                }
            }
            catch (Exception ex) {
                Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.getConsoleSender().sendMessage(TextUtil.toComponent("&8[&c" + this.plugin.getName() + "&8] &cNie uda\u0142o si\u0119 sprawdzi\u0107 aktualizacji: &4" + ex.getMessage())));
            }
        });
    }

    public void registerJoinNotify(String permission) {
        this.registerJoinNotify(permission, 30L);
    }

    public void registerJoinNotify(final String permission, final long delayTicks) {
        Bukkit.getPluginManager().registerEvents(new Listener(){

            @EventHandler(priority=EventPriority.MONITOR)
            public void onJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                if (!player.hasPermission(permission)) {
                    return;
                }
                if (!UpdateChecker.this.enabled || !UpdateChecker.this.updateAvailable) {
                    return;
                }
                Bukkit.getScheduler().runTaskLater(UpdateChecker.this.plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    String pluginName = UpdateChecker.this.plugin.getName();
                    String current = UpdateChecker.this.getCurrentVersion();
                    String latest = UpdateChecker.this.getLatestVersion();
                    player.sendMessage(TextUtil.toComponent(""));
                    player.sendMessage(TextUtil.toComponent("&8\u00bb &cDost\u0119pna jest nowa wersja &c&l" + pluginName + "&c! &8(&4" + latest + "&8)"));
                    player.sendMessage(TextUtil.toComponent(""));
                    player.sendMessage(TextUtil.toComponent(" &8\u2192 &fTwoja wersja: &c" + current));
                    player.sendMessage(TextUtil.toComponent(" &8\u2192 &fNajnowsza wersja: &a" + latest));
                    player.sendMessage(TextUtil.toComponent(""));
                }, delayTicks);
            }
        }, this.plugin);
    }

    public boolean isUpdateAvailable() {
        return this.updateAvailable;
    }

    public String getLatestVersion() {
        return this.latestVersion;
    }

    public String getCurrentVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean isEnabled() {
        return this.enabled;
    }
}
