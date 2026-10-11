package boniec.klany.core;

import java.util.UUID;

/**
 * Reprezentuje pojedyncze zaproszenie do klanu wyslane graczowi.
 * Zaproszenia trzymane sa tylko w pamieci (nie przezywaja restartu serwera - to celowe,
 * podobnie jak np. zaproszenia do party w wielu pluginach).
 */
public class Zaproszenie {

    private final int klanId;
    private final String klanTag;
    private final UUID zapraszajacy;
    private final long czasUtworzenia;
    private static final long WAZNOSC_MS = 5 * 60 * 1000L; // 5 minut

    public Zaproszenie(int klanId, String klanTag, UUID zapraszajacy) {
        this.klanId = klanId;
        this.klanTag = klanTag;
        this.zapraszajacy = zapraszajacy;
        this.czasUtworzenia = System.currentTimeMillis();
    }

    public int getKlanId() { return klanId; }
    public String getKlanTag() { return klanTag; }
    public UUID getZapraszajacy() { return zapraszajacy; }

    public boolean isWygasle() {
        return System.currentTimeMillis() - czasUtworzenia > WAZNOSC_MS;
    }
}
