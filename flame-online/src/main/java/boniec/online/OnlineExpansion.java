package boniec.online;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class OnlineExpansion extends PlaceholderExpansion {

    private final FlameOnlinePlugin plugin;

    public OnlineExpansion(FlameOnlinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "online";
    }

    @Override
    public String getAuthor() {
        return "Boniec";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        OnlineCounts c = plugin.counts();
        String p = params == null ? "this" : params.toLowerCase();
        if (p.isEmpty() || p.equals("this") || p.equals("tryb") || p.equals("local")) {
            return String.valueOf(c.shown("this"));
        }
        if (p.equals("all")) return String.valueOf(c.shownAll());
        if (p.equals("all_real")) return String.valueOf(c.realAll());
        if (p.endsWith("_real")) {
            return String.valueOf(c.real(p.substring(0, p.length() - 5)));
        }
        return String.valueOf(c.shown(p));
    }
}
