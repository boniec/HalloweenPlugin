package boniec.skrzynki;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Male narzedzia tekstowe systemu skrzynek: kolory (w tym hex &#RRGGBB), placeholdery, liczby. */
final class Txt {

    private static final Pattern HEX = Pattern.compile("&?#([A-Fa-f0-9]{6})");

    private Txt() {
    }

    static String color(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String hex = net.md_5.bungee.api.ChatColor.of("#" + m.group(1)).toString();
            m.appendReplacement(sb, Matcher.quoteReplacement(hex));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    static String strip(String s) {
        return s == null ? "" : ChatColor.stripColor(s);
    }

    static String replace(String s, Map<String, String> placeholders) {
        if (s == null) return "";
        if (placeholders == null) return s;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return s;
    }

    /** map("a","1","b","2") -> {a=1, b=2} (modyfikowalna). */
    static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static String humanize(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    static String num(double value) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }
}
