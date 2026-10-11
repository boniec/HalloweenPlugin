package boniec.boss;

import boniec.dev.inventory.Main;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class ZeusExpansion extends PlaceholderExpansion {

    public static final String NAZWA =
            "&#FDD835&lᴢ&lᴇ&lᴜ&lѕ";

    private static final String PREFIX = "&8☰ ";

    private final Main plugin;

    public ZeusExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "zeus";
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
        ZeusManager m = plugin.getZeusManager();
        if (m == null) return "";

        switch (params.toLowerCase(Locale.ROOT)) {
            case "nazwa":
                return NAZWA;
            case "zyje":
                return String.valueOf(m.isAlive());
            case "hp":
                return m.isAlive()
                        ? String.valueOf((long) Math.ceil(m.getAktywny().getVisualHp()))
                        : "0";
            case "hpmax":
                return m.isAlive() ? String.valueOf((long) m.getAktywny().getVisualHpMax()) : "0";
            case "procent": {
                if (!m.isAlive()) return "0";
                double max = m.getAktywny().getVisualHpMax();
                if (max <= 0) return "0";
                double hp = m.getAktywny().getVisualHp();
                return String.valueOf((int) Math.round(hp / max * 100));
            }
            case "czas":
                return (!m.isAlive() && m.getSekundyDoRespawnu() >= 0) ? m.formatujCzasDoRespawnu() : "";
            case "status":
                return status(m);
            default:
                return null;
        }
    }

    private String status(ZeusManager m) {
        StringBuilder sb = new StringBuilder(PREFIX).append(NAZWA).append(" &8(");
        if (m.isAlive()) {
            sb.append("&aZYJE");
        } else if (m.getSekundyDoRespawnu() >= 0) {
            sb.append("&fza &#97F653").append(m.formatujCzasDoRespawnu());
        } else if (!m.czyMaZapisanySpawn()) {
            sb.append("&7brak spawnu");
        } else {
            sb.append("&7martwy");
        }
        return sb.append("&8)").toString();
    }
}