package boniec.klany.util;

/**
 * Base64 tekstur glow graczy (z head-database / minecraft-heads.com) uzywanych jako
 * dekoracyjne ikony w GUI klanow. Wszystkie ponizsze tekstury to publicznie dostepne,
 * powszechnie uzywane "custom heads" (strzalki, symbole, itp.) - dziala z reflection
 * w ItemBuilder#glowaTekstury na dowolnym Spigot/Paper 1.21.
 */
public final class Tekstury {

    private Tekstury() {}

    // Zielona strzalka w prawo (nawigacja "dalej")
    public static final String STRZALKA_PRAWO =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzE2YTM4M2VjYmY1ZjJjYzEyYzFlZmMxYzQ2NjAyZTQ4MTZlOTU4MDYyYzZlMTU2MjQwZTZmYjIwMWY2In19fQ==";

    // Czerwona strzalka w lewo (nawigacja "wstecz")
    public static final String STRZALKA_LEWO =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTA2ZTQ2MTJlNGY4YTA5MDdiZTRhNDY2ZTNjNTc0YTNkZWFmY2M3MjBhMzE1M2NmNzZiZTNkMTYwOTM3NDgifX19";

    // Skrzynia / skarbiec
    public static final String SKRZYNIA =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzY3NzJlN2MzMzdjNzBmOTdiZmM4ZDBhMjU3ZjlhNDM4MjZkOGYzMmY4NDgxMDVhNzYyNDNhMDBiYjgifX19";

    // Skrzyzowane miecze (PvP)
    public static final String MIECZE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTc3MjJjMzYzMjE1ZTBjNTk0MmQwNTdmYTU1MjBjMjQ1ZjBjMTBhNTBjZWRjZmFjMmY4ZTgwZjA0YzUifX19";

    // Gwiazda (ulepszenia)
    public static final String GWIAZDA =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDRjY2NiZDMwZjNjOTFmMmM3ZjNjYTQ1ZWViMzBkNTAxMzE1OTMwYzU2YWJmNzE2ZjUwOTU4ZmFkNGM3ZWE1In19fQ==";

    // Korona (lider)
    public static final String KORONA =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTM3Y2E3NDdlOWRlNDUyMDdjNWFkNzk3Njg1YmVkNTljMDM3NGM3N2NkNGY5YmU0ODE1YmM4NjdjMTNkOGYifX19";

    // Zielony check / potwierdzenie
    public static final String CHECK =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTA0ZTNlNjc1ZDBiOTdlYzhiNjJhZjI3YmE3ZmE5MjM0YmRhZmM2MDU2MzcwYzk4YzhjMzE0NzQwOTBjNGIifX19";

    // Czerwony krzyzyk / anuluj
    public static final String KRZYZYK =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDk0YWZlYzk3OTJhOTQ3NGY4MDBmOTk3ZTVkNDA3ZDMzOTdlOGY1YmZkNzYwYmZjZjE1OWU5ZWQ0MTM5MTQifX19";

    // Ranking / puchar
    public static final String PUCHAR =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzM4ZDBlNjc0YzZkZjEzMWZjZTc5ODVhNzY5NmI3ZTEwYWY1NzY5YWQ4YzY4NWE3ODM2NzYyMTQ4ZTRmMDIifX19";

    // Ksiega / info
    public static final String KSIEGA =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzEwNTkzMTRhZTU2NmVkYjhhMjJjZmY1ZGE3ZjZmM2NhOTUxOTllZjNmM2MyODAwYWJkOTQ2NmNkYTZlZmYifX19";

    // Tarcza / ustawienia i uprawnienia
    public static final String TARCZA =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzE1ZWMzNTQ0MDI2NmVmNTM3ZDZlYWM4MzMyZTNjNzg4YzE5ZTNiOTVlYzdiZjE2Y2ZmZjJhZTFhNzk3MyJ9fX0=";
}
