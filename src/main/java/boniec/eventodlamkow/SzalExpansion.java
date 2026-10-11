package boniec.eventodlamkow;

import boniec.dev.inventory.Main;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Placeholdery PlaceholderAPI dla eventu Szal Odlamkow - ten sam styl co
 * %inventory_nastepna_godzina_boga% (surowy tekst z '&' i '&#RRGGBB').
 *
 *   %szal_status%   -> gotowy tekst do tablisty/scoreboardu:
 *                        aktywny:     "&8☰ SZAL ODLAMKOW &8(&aAKTYWNY &8| &fzostalo &#97F653Xm Ys&8)"
 *                        nieaktywny:  "&8☰ SZAL ODLAMKOW &8(&fza &#97F653Xm Ys&8)"  (odliczanie do nastepnego startu)
 *   %szal_nazwa%    -> sama nazwa z gradientem
 *   %szal_aktywny%  -> "true" / "false"
 *   %szal_czas%     -> pozostaly czas ("5m 12s"), pusty gdy nieaktywny
 *   %szal_sekundy%  -> pozostale sekundy (liczba), "0" gdy nieaktywny
 *   %szal_nastepny% -> czas do nastepnego automatycznego startu ("1h 59m 3s"), pusty gdy event trwa
 *
 * Rejestrowana TYLKO gdy PlaceholderAPI jest zainstalowane (patrz Main.onEnable()).
 */
public class SzalExpansion extends PlaceholderExpansion {

    /** Nazwa eventu "SZAL ODLAMKOW" jako small text z gradientem (S zostaje zwykle - brak malego kapitalika, jak w smallcaps.py). */
    public static final String NAZWA =
            "&#C02FD4&ls&#C63CD8&lᴢ&#CB49DC&lᴀ&#D156E0&lʟ &#D763E4&lᴏ&#DD70E8&lᴅ&#E27DEB&lʟ&#E88AEF&lᴀ&#EE97F3&lᴍ&#F4A4F7&lᴋ&#F9B1FB&lᴏ&#FFBEFF&lᴡ";

    private static final String PREFIX = "&8☰ ";

    private final Main plugin;

    public SzalExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        // %szal_<...>%
        return "szal";
    }

    @Override
    public @NotNull String getAuthor() {
        return "boniec";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    // false: ekspansja NIE zostaje w PAPI po przeladowaniu pluginu. Przy true stara instancja
    // (z martwym obiektem Main) blokowala nowa rejestracje po /reload, a placeholder dalej
    // odpytywal stary, nieaktualny plugin - dlatego %szal_status% nie dzialal.
    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        EventOdlamkowManager e = plugin.getEventOdlamkowManager();
        if (e == null) return "";

        switch (params.toLowerCase(Locale.ROOT)) {
            case "nazwa":
                return NAZWA;
            case "aktywny":
                return String.valueOf(e.isActive());
            case "sekundy":
                return e.isActive() ? String.valueOf(e.getPozostaloSekund()) : "0";
            case "czas":
                return e.isActive() ? formatujCzas(e.getPozostaloSekund()) : "";
            case "nastepny": {
                long doStartu = e.getSekundyDoNastepnegoStartu();
                return (!e.isActive() && doStartu >= 0) ? formatujCzas((int) doStartu) : "";
            }
            case "status":
                return status(e);
            default:
                return null; // nieznany placeholder spod %szal_...%
        }
    }

    private String status(EventOdlamkowManager e) {
        StringBuilder sb = new StringBuilder(PREFIX).append(NAZWA).append(" &8(");
        if (e.isActive()) {
            sb.append("&aAKTYWNY &8| &fzostalo &#97F653").append(formatujCzas(e.getPozostaloSekund()));
        } else {
            long doStartu = e.getSekundyDoNastepnegoStartu();
            if (doStartu >= 0) {
                sb.append("&fza &#97F653").append(formatujCzas((int) doStartu));
            } else {
                // Harmonogram wylaczony w eventodlamki.yml - nie ma czego odliczac.
                sb.append("&7brak harmonogramu");
            }
        }
        return sb.append("&8)").toString();
    }

    private static String formatujCzas(int sekundy) {
        if (sekundy >= 3600) {
            return (sekundy / 3600) + "h " + ((sekundy % 3600) / 60) + "m " + (sekundy % 60) + "s";
        }
        if (sekundy >= 60) {
            return (sekundy / 60) + "m " + (sekundy % 60) + "s";
        }
        return sekundy + "s";
    }
}
