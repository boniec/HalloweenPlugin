package boniec.antylogout;

import org.bukkit.boss.BossBar;

import java.util.UUID;

/**
 * Stan walki JEDNEGO gracza. Bossbar jest tworzony RAZ na poczatku walki
 * i trzymany az do jej konca - kolejne trafienia tylko przedluzaja
 * endsAtMillis oraz podmieniaja lastAttacker, bez tworzenia nowego obiektu
 * BossBar (patrz AntyLogoutManager - to glowny element optymalizacji przy
 * wielu graczach na raz).
 */
final class CombatData {

    final BossBar bossBar;
    long endsAtMillis;
    UUID lastAttacker;
    /** Sekundy wyswietlone na bossbarze przy ostatniej aktualizacji - zeby nie podmieniac tytulu, gdy wartosc sie nie zmienila. */
    int lastShownSeconds = -1;
    /** Pelny czas trwania biezacego okna walki (ms) - do policzenia progress na bossbarze; ustawiany na kazdym (re)tagu. */
    long totalMillis;
    /**
     * Cache uprawnienia "spawnpvp.bypass" - ustalane RAZ przy tagowaniu gracza
     * (i przy kazdym retagu), zeby SpawnPvpGuardListener nie musial pytac LuckPerms
     * przez caly stos cache'u przy kazdej zmianie bloku gracza bedacego w walce.
     */
    boolean hasBypass;

    CombatData(BossBar bossBar, long endsAtMillis, UUID lastAttacker) {
        this.bossBar = bossBar;
        this.endsAtMillis = endsAtMillis;
        this.lastAttacker = lastAttacker;
    }
}
