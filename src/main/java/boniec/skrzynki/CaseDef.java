package boniec.skrzynki;

import java.util.ArrayList;
import java.util.List;

/** Definicja jednej skrzynki (wczytywana z skrzynki.yml). */
final class CaseDef {

    final String id;
    String displayName;
    CaseType type = CaseType.SKRZYNIA;
    ItemSpec key;
    /** true = klucz tylko w glownej rece (lootbox). Skrzynia zawsze szuka klucza w calym EQ. */
    boolean keyInHand = false;
    final List<String> locations = new ArrayList<>();
    final List<CaseDrop> drops = new ArrayList<>();
    boolean enabled = true;

    CaseDef(String id) {
        this.id = id;
        this.displayName = "&6" + id;
    }

    String keyLabel() {
        if (key == null) return "?";
        return key.name != null ? key.name : Txt.humanize(key.material);
    }
}
