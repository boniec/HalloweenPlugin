package boniec.ustawienia;

import org.bukkit.Material;

public enum UstawieniaKlucz {
    MSG("msg", "Prywatne wiadomosci", Material.PAPER, 10,
            "&7Nie dostajesz &f/msg &7i &f/r&7 od innych."),
    SKRZYNKI("skrzynki", "Skrzynki (czat)", Material.CHEST, 11,
            "&7Ukrywa broadcast otwarcia skrzynek innych graczy."),
    PORTFEL("portfel", "Zakupy w portfelu", Material.GOLD_INGOT, 12,
            "&7Ukrywa powiadomienia o zakupach vPLN."),
    CHAT("chat", "Czat globalny", Material.OAK_SIGN, 13,
            "&7Nie widzisz wiadomosci innych graczy na czacie."),
    OGLOSZENIA("ogloszenia", "Ogloszenia", Material.BELL, 14,
            "&7Ukrywa title i czat z &f/bc&7."),
    KODY("kody", "Kody (broadcast)", Material.NAME_TAG, 15,
            "&7Ukrywa ogloszenia po uzyciu &f/kod&7."),
    KLANY("klany", "Wiadomosci klanow", Material.SHIELD, 16,
            "&7Ukrywa broadcasty klanowe na calym serwerze."),
    ACTIONBAR("actionbar", "Actionbar kopania", Material.IRON_PICKAXE, 19,
            "&7Ukrywa +$ i exp przy kopaniu."),
    SCOREBOARD("scoreboard", "Scoreboard", Material.PAINTING, 20,
            "&7Wylacza tablice z boku ekranu.");

    public final String id;
    public final String nazwa;
    public final Material ikona;
    public final int slot;
    public final String opis;

    UstawieniaKlucz(String id, String nazwa, Material ikona, int slot, String opis) {
        this.id = id;
        this.nazwa = nazwa;
        this.ikona = ikona;
        this.slot = slot;
        this.opis = opis;
    }

    public static UstawieniaKlucz zId(String id) {
        if (id == null) return null;
        for (UstawieniaKlucz k : values()) {
            if (k.id.equalsIgnoreCase(id)) return k;
        }
        return null;
    }
}
