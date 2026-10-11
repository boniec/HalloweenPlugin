package boniec.misje;

public enum TypMisji {
    ZJEDZ_ZLOTE_JABLKA,
    ZABIJ_GRACZY,
    ZDOBADZ_RANKING,
    WYKOP_BLOKI,
    ZABIJ_BOSSA,
    SPEDZ_MINUTY,
    AWANS_POZIOM;

    public static TypMisji zNazwy(String nazwa) {
        if (nazwa == null) return null;
        try {
            return valueOf(nazwa.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
