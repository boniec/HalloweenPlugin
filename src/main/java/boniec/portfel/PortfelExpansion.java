package boniec.portfel;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class PortfelExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final PortfelManager manager;

    public PortfelExpansion(JavaPlugin plugin, PortfelManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "777-wallet";
    }

    @Override
    public @NotNull String getAuthor() {
        return "boniec";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "0";
        if (params.equalsIgnoreCase("balance")) {
            return manager.formatBalance(manager.getBalance(player.getUniqueId()));
        }
        return null;
    }
}
