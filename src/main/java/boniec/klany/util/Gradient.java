package boniec.klany.util;

import net.md_5.bungee.api.ChatColor;

/**
 * Generuje kolorowe gradienty hex "litera po literze" (jak w kliencie Minecraft 1.16+)
 * do uzytku w nazwach/lore itemow GUI, np. nazwy perkow w /klan ulepszenia.
 * <p>
 * Jesli gradient-od i gradient-do sa tym samym kolorem, wynik jest po prostu jednolitym
 * kolorem (co pokrywa najprostszy przypadek uzycia bez specjalnej obslugi).
 */
public final class Gradient {

    private Gradient() {}

    /**
     * Koloruje caly tekst gradientem miedzy dwoma kolorami hex (np. "#79ebeb"), zachowujac
     * pogrubienie (bold) jesli podane. Znaki spoza [a-zA-Z0-9...] (spacje) tez dostaja kolor,
     * zgodnie z zachowaniem klienta MC.
     */
    public static String apply(String tekst, String hexOd, String hexDo, boolean bold) {
        if (tekst == null || tekst.isEmpty()) return tekst;
        if (hexOd == null || hexDo == null) return Msg.color(tekst);

        java.awt.Color od = dekoduj(hexOd);
        java.awt.Color docelowy = dekoduj(hexDo);
        if (od == null || docelowy == null) return Msg.color(tekst);

        int dlugosc = tekst.length();
        StringBuilder wynik = new StringBuilder();
        String pogrubienie = bold ? "" + ChatColor.COLOR_CHAR + "l" : "";

        for (int i = 0; i < dlugosc; i++) {
            double udzial = dlugosc == 1 ? 0 : (double) i / (dlugosc - 1);
            int r = (int) Math.round(od.getRed() + (docelowy.getRed() - od.getRed()) * udzial);
            int g = (int) Math.round(od.getGreen() + (docelowy.getGreen() - od.getGreen()) * udzial);
            int b = (int) Math.round(od.getBlue() + (docelowy.getBlue() - od.getBlue()) * udzial);
            String hex = String.format("#%02x%02x%02x", r, g, b);
            wynik.append(ChatColor.of(hex)).append(pogrubienie).append(tekst.charAt(i));
        }
        return wynik.toString();
    }

    public static String apply(String tekst, String hexOd, String hexDo) {
        return apply(tekst, hexOd, hexDo, false);
    }

    private static java.awt.Color dekoduj(String hex) {
        try {
            return java.awt.Color.decode(hex);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
