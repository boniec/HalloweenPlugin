package boniec.ahalloween;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AHalloweenExpansion extends PlaceholderExpansion {

    private final AHalloweenManager manager;

    public AHalloweenExpansion(AHalloweenManager manager) {
        this.manager = manager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ahalloween";
    }

    @Override
    public @NotNull String getAuthor() {
        return "boniec";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("active")) return manager.aktywny() ? "tak" : "nie";
        if (params.equalsIgnoreCase("time")) return HalloweenUtil.formatCzas(manager.pozostaloSekund());
        if (player == null) return "";
        HalloweenPlayer d = manager.dane(player);
        return switch (params.toLowerCase()) {
            case "level" -> String.valueOf(d.poziom);
            case "exp" -> String.valueOf(d.exp);
            case "candies" -> String.valueOf(d.sumaCukierkow());
            case "value" -> String.valueOf(manager.wartoscCukierkow(d));
            case "capacity" -> String.valueOf(manager.pojemnosc(d));
            case "mobs" -> String.valueOf(d.statMoby);
            case "missions" -> String.valueOf(d.statMisje);
            case "bosses" -> String.valueOf(d.statBossy);
            default -> params.startsWith("candy_")
                    ? String.valueOf(d.cukierek(params.substring(6)))
                    : "";
        };
    }
}
