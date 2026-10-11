package boniec.szaman.core;

import boniec.klany.core.Klan;
import boniec.klany.core.KlanManager;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Cienka warstwa integracji miedzy Szamanem a systemem klanow (pakiet boniec.klany).
 * Szaman NIGDY nie modyfikuje klanow - tylko ODCZYTUJE bonusy klanowe.
 * <p>
 * Obecnie jedyny bonus klanowy wykorzystywany przez Szamana to klanowy PERK DROPU
 * (ulepszenie "Drop" w /klan ulepszenia) - dolicza sie do procentu z Boostu Dropu
 * kazdego czlonka klanu, patrz {@link DropPerkManager#getProcentLaczny}.
 * (Dawny "Bonus Klanowy" per kategoria perkow zniknal razem z kamieniami i drzewkiem perkow.)
 */
public class KlanBonusProvider {

    private final KlanManager klanManager;
    private final Logger logger;

    public KlanBonusProvider(KlanManager klanManager, Logger logger) {
        this.klanManager = klanManager;
        this.logger = logger;
    }

    /** Bonus dropu klanu gracza w % - 0, gdy gracz nie ma klanu albo klan nie ma tego perku. */
    public double getBonusDropu(UUID uuid) {
        if (klanManager == null) return 0.0;
        Klan klan = klanManager.getKlanGracza(uuid);
        if (klan == null) return 0.0;
        return Math.max(0.0, klan.getPerk(Klan.PERK_DROP));
    }

    public boolean maKlan(UUID uuid) {
        return klanManager != null && klanManager.getKlanGracza(uuid) != null;
    }

    public String getNazwaKlanu(UUID uuid) {
        if (klanManager == null) return null;
        Klan klan = klanManager.getKlanGracza(uuid);
        return klan == null ? null : klan.getNazwa();
    }
}
