package boniec.klany.util;

import boniec.klany.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility do pobierania i wysylania sformatowanych wiadomosci z config.yml.
 * Wszystkie teksty widoczne dla gracza sa w pelni konfigurowalne (placeholdery).
 * <p>
 * Wiadomosci pod "wiadomosci.*" moga byc zdefiniowane w configu na dwa sposoby:
 *  - jako pojedynczy string (klasyczny sposob),
 *  - jako LISTA stringow (YAML "- linia1 / - linia2 / ..."), wtedy kazda linia
 *    zostanie wyslana jako osobna wiadomosc na czacie, w kolejnosci z configu.
 * Dzieki temu np. "otrzymano-zaproszenie" moze miec dowolna liczbe linii.
 */
public final class Msg {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");

    private Msg() {}

    public static String color(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static String raw(String sciezka) {
        FileConfiguration cfg = Main.get().getConfig();
        String tekst = cfg.getString("wiadomosci." + sciezka, "&c[brak wiadomosci: " + sciezka + "]");
        return color(tekst);
    }

    public static String prefix() {
        return color(Main.get().getConfig().getString("wiadomosci.prefix", ""));
    }

    /**
     * Wysyla wiadomosc z "wiadomosci.&lt;sciezka&gt;". Jesli w configu jest to lista
     * linii, wysyla kazda linie z osobna (pierwsza z prefixem, kolejne bez, zeby nie
     * powtarzac "[KLAN]" przy kazdej linii) - jesli to pojedynczy string, dziala jak dawniej.
     */
    public static void send(CommandSender odbiorca, String sciezka, Object... placeholdery) {
        FileConfiguration cfg = Main.get().getConfig();
        String pelnaSciezka = "wiadomosci." + sciezka;

        if (cfg.isList(pelnaSciezka)) {
            List<String> linie = cfg.getStringList(pelnaSciezka);
            if (linie.isEmpty()) {
                odbiorca.sendMessage(prefix() + "&c[brak wiadomosci: " + sciezka + "]");
                return;
            }
            for (int i = 0; i < linie.size(); i++) {
                String tekst = color(zastapPlaceholdery(linie.get(i), placeholdery));
                odbiorca.sendMessage((i == 0 ? prefix() : "") + tekst);
            }
            return;
        }

        String tekst = raw(sciezka);
        tekst = zastapPlaceholdery(tekst, placeholdery);
        odbiorca.sendMessage(prefix() + tekst);
    }

    public static void sendGui(CommandSender odbiorca, String tekst) {
        odbiorca.sendMessage(prefix() + color(tekst));
    }

    private static String zastapPlaceholdery(String tekst, Object... placeholdery) {
        // placeholdery przekazywane jako pary: "%klucz%", wartosc, "%klucz2%", wartosc2 ...
        for (int i = 0; i + 1 < placeholdery.length; i += 2) {
            String klucz = String.valueOf(placeholdery[i]);
            String wartosc = String.valueOf(placeholdery[i + 1]);
            tekst = tekst.replace(klucz, wartosc);
        }
        return tekst;
    }

    public static String guiTitle(String sciezka) {
        FileConfiguration cfg = Main.get().getConfig();
        String tekst = cfg.getString("gui." + sciezka, "&8Klany");
        return color(tekst);
    }
}
