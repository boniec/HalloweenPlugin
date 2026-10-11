package boniec.poziomy;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Te same placeholdery co w Dream-FlameHubLevels, zeby scoreboard/tab dzialaly bez zmian:
 *   %levels_level%, %levels_current_exp%, %levels_next_level_exp%, %levels_progress_bar%,
 *   %levels_top_name_<1-20>%, %levels_top_level_<1-20>%
 * Dodatkowo: %levels_top_exp_<1-20>%, %levels_percent%, %levels_boost%, %levels_boost_time%.
 */
public class PoziomyExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final PoziomyManager manager;

    public PoziomyExpansion(JavaPlugin plugin, PoziomyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "levels";
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
        String p = params.toLowerCase(Locale.ROOT);

        if (p.startsWith("top_")) {
            String[] czesci = p.split("_");
            if (czesci.length < 3) return "";
            int miejsce;
            try {
                miejsce = Integer.parseInt(czesci[2]);
            } catch (NumberFormatException e) {
                return "";
            }
            if (miejsce < 1 || miejsce > 20) return "Max top is 1-20";
            Profil profil = manager.getTop(miejsce);
            if (profil == null) return "Brak";
            return switch (czesci[1]) {
                case "name" -> profil.getNazwa();
                case "level" -> String.valueOf(profil.getPoziom());
                case "exp" -> PoziomyManager.formatujExp(profil.getExp());
                default -> "";
            };
        }

        if (p.equals("boost")) {
            return manager.isBoostAktywny() ? PoziomyManager.formatuj(manager.getBoostMnoznik()) : "1";
        }
        if (p.equals("boost_time")) {
            return manager.isBoostAktywny() ? PoziomyManager.formatujCzas(manager.getBoostPozostalo()) : "";
        }

        if (player == null) return "";
        Profil profil = manager.getProfil(player);
        int poziom = profil.getPoziom();
        int nastepny = manager.getExpNaNastepny(poziom);

        return switch (p) {
            case "level" -> String.valueOf(poziom);
            case "current_exp" -> PoziomyManager.formatujExp(profil.getExp());
            case "next_level_exp" -> nastepny < 0 ? "MAX" : PoziomyManager.formatujExp(nastepny);
            case "percent" -> String.valueOf(manager.getProcent(profil));
            case "progress_bar" -> manager.getPasekPostepu(manager.getProcent(profil));
            case "pozycja" -> {
                int poz = manager.getPozycja(player.getUniqueId());
                yield poz > 0 ? "#" + poz : "-";
            }
            default -> null;
        };
    }
}
