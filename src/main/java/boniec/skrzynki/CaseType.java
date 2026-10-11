package boniec.skrzynki;

import java.util.Locale;

public enum CaseType {
    /** Skrzynka na swiecie (blok pod zapisana lokalizacja) - klik otwiera podglad, klucz otwiera. */
    SKRZYNIA,
    /** Przedmiot (np. gabka o nazwie "gzibbox") - PPM z nim w rece otwiera, bez blokow i bez otwierania z podgladu. */
    LOOTBOX;

    public static CaseType parse(String s, CaseType def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
