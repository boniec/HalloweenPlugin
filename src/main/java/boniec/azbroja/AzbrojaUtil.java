package boniec.azbroja;

import org.bukkit.Material;

import java.awt.Color;
import java.util.Locale;

public final class AzbrojaUtil {

    private static final String SMALL_FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL_TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private AzbrojaUtil() {}

    public static String smallCaps(String tekst) {
        if (tekst == null || tekst.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(tekst.length());
        for (char c : tekst.toCharArray()) {
            if (c >= 'A' && c <= 'Z') c = Character.toLowerCase(c);
            int idx = SMALL_FROM.indexOf(c);
            sb.append(idx >= 0 ? SMALL_TO.charAt(idx) : c);
        }
        return sb.toString();
    }

    /** np. "miecz chewbacci 1" -> tekst=ᴍɪᴇᴄᴢ ᴄʜᴇᴡʙᴀᴄᴄɪ, numer=1 */
    public static ParsedNazwa parsujNazwe(String[] slowa) {
        if (slowa == null || slowa.length == 0) return null;
        Integer numer = null;
        int koniec = slowa.length;
        try {
            numer = Integer.parseInt(slowa[slowa.length - 1]);
            koniec = slowa.length - 1;
        } catch (NumberFormatException ignored) {}
        if (koniec <= 0) return null;
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < koniec; i++) {
            if (i > 0) raw.append(' ');
            raw.append(slowa[i]);
        }
        return new ParsedNazwa(smallCaps(raw.toString()), numer);
    }

    /** Gradient &x&R&R&G&G&B&B&l na kazda litere (jak eventowki). */
    public static String gradientLegacy(String tekst, String hexOd, String hexDo, boolean bold) {
        if (tekst == null || tekst.isEmpty()) return "";
        Color od = dekoduj(hexOd);
        Color doc = dekoduj(hexDo);
        if (od == null || doc == null) return tekst;

        int len = tekst.length();
        StringBuilder wynik = new StringBuilder();
        String boldCode = bold ? "&l" : "";
        for (int i = 0; i < len; i++) {
            double t = len == 1 ? 0 : (double) i / (len - 1);
            int r = (int) Math.round(od.getRed() + (doc.getRed() - od.getRed()) * t);
            int g = (int) Math.round(od.getGreen() + (doc.getGreen() - od.getGreen()) * t);
            int b = (int) Math.round(od.getBlue() + (doc.getBlue() - od.getBlue()) * t);
            wynik.append(hexToLegacyX(String.format("#%02x%02x%02x", r, g, b))).append(boldCode).append(tekst.charAt(i));
        }
        return wynik.toString();
    }

    public static String zbudujNazwe(String tekstGradient, String hexOd, String hexDo,
                                     Integer numer, String formatNumeru) {
        StringBuilder sb = new StringBuilder();
        sb.append(gradientLegacy(tekstGradient, hexOd, hexDo, true));
        if (numer != null) {
            String fmt = formatNumeru == null ? "&6★&6&l%numer%" : formatNumeru;
            sb.append(' ').append(fmt.replace("%numer%", String.valueOf(numer)));
        }
        return sb.toString();
    }

    public static org.bukkit.Color bukkitColor(String hex) {
        Color c = dekoduj(hex);
        return c == null ? null : org.bukkit.Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue());
    }

    public static boolean isSkorzanaZbroja(Material mat) {
        if (mat == null) return false;
        String n = mat.name();
        return n.startsWith("LEATHER_") && (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS"));
    }

    private static String hexToLegacyX(String hex) {
        String h = hex.replace("#", "").toUpperCase(Locale.ROOT);
        if (h.length() != 6) return "";
        StringBuilder sb = new StringBuilder("&x");
        for (int i = 0; i < 6; i++) {
            sb.append('&').append(h.charAt(i));
        }
        return sb.toString();
    }

    private static Color dekoduj(String hex) {
        if (hex == null) return null;
        try {
            return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record ParsedNazwa(String tekst, Integer numer) {}
}
