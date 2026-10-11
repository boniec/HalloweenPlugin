package boniec.online;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** %osoby% = online z mnoznikiem (10 osob = 30), to samo co %online_all%. */
public final class OsobyExpansion extends PlaceholderExpansion {

    private final FlameOnlinePlugin plugin;

    public OsobyExpansion(FlameOnlinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "osoby";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Boniec";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String getRequiredPlugin() {
        return plugin.getName();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return String.valueOf(plugin.counts().shownAll());
    }
}
