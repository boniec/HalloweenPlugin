package boniec.szaman.core;

import boniec.szaman.SzamanPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Efektywne wartosci perkow Szamana = poziomy kupione w /szaman.
 */
public class BonusCalculator {

    public static final double PRZELICZNIK_OBRAZEN = 0.25;
    public static final double PRZELICZNIK_ZYCIA_HP = 0.1;
    public static final double PRZELICZNIK_SZANSY = 0.0875;

    private final PerkManager perkManager;
    private final SzamanPlugin szamanPlugin;

    public BonusCalculator(PerkManager perkManager, KlanBonusProvider klanBonusProvider, SzamanPlugin szamanPlugin) {
        this.perkManager = perkManager;
        this.szamanPlugin = szamanPlugin;
    }

    public void wczytajZConfigu() {
    }

    public int getPoziom(UUID uuid, Perk perk) {
        return perkManager.getPoziom(uuid, perk);
    }

    public double getWartoscKupiona(UUID uuid, Perk perk) {
        return perk.getWartosc(getPoziom(uuid, perk));
    }

    public double getBonusKostiumowy(UUID uuid, Perk perk) {
        return 0.0;
    }

    public double getConfigBonusKostiumowy(Perk perk) {
        return 0.0;
    }

    public double getBonusAury(UUID uuid, Perk perk) {
        return 0.0;
    }

    public double getBonusZewnetrzny(UUID uuid, Perk perk) {
        return 0.0;
    }

    public Map<String, Double> getAllBonusyKostiumoweDlaPerku(Perk perk) {
        return Collections.emptyMap();
    }

    public double getObrazeniaProcent(UUID uuid) {
        return getWartoscKupiona(uuid, Perk.STRENGTH);
    }

    public double getDodatkoweZyciaHp(UUID uuid) {
        return getWartoscKupiona(uuid, Perk.HEALTH) * 2.0;
    }

    public double getSzansaWampiryzmu(UUID uuid) {
        return getWartoscKupiona(uuid, Perk.VAMPIRISM);
    }

    public double getSzansaOslabienia(UUID uuid) {
        return getWartoscKupiona(uuid, Perk.WEAKENING);
    }

    public double getSzansaBlokady(UUID uuid) {
        return getWartoscKupiona(uuid, Perk.BLOCK);
    }

    public double getSzansaUwiezienia(UUID uuid) {
        if (getPoziom(uuid, Perk.CONFINEMENT) <= 0) return 0.0;
        FileConfiguration cfg = szamanPlugin.getSzamanConfig();
        return cfg == null ? 5.0 : cfg.getDouble("confinement.chance", 5.0);
    }

    public double getCzasUwiezieniaSekundy(UUID uuid) {
        int poziom = Math.max(1, getPoziom(uuid, Perk.CONFINEMENT));
        return Perk.CONFINEMENT.getWartosc(poziom);
    }

    public boolean maZablokowanyPerkPrzezKostium(UUID noszacyUuid) {
        return false;
    }

    public double getSkrytobojcaProcent(UUID uuid) {
        return getWartoscKupiona(uuid, Perk.BACKSTAB);
    }
}
