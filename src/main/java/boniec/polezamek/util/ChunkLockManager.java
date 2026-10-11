package boniec.dev.polezamek.util;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Wspolna blokada chunku dla Pola Smierci i Zamku.
 *
 * Zasady:
 *  - Kiedy gracz UZYJE pola smierci lub zamku na danym chunku, ten chunk
 *    zostaje "zablokowany" na okreslony czas (domyslnie 15 sekund) od
 *    momentu uzycia.
 *  - Dopoki chunk jest zablokowany, nie da sie w nim postawic ANI pola
 *    smierci, ANI zamku (niezaleznie od tego, co go zablokowalo).
 *  - Zamek dodatkowo caly czas swojego trwania (nie tylko 15 sekund od
 *    uzycia) blokuje postawienie drugiego zamku w tym samym chunku –
 *    to zachowanie jest juz obslugiwane osobno w ZamekManager
 *    (activeChunks). Ta klasa odpowiada tylko za dodatkowy, wspolny
 *    15-sekundowy "recharge" po kazdym uzyciu.
 *  - Kazda blokada pamieta zrodlo (ZAMEK / POLE). Pole Smierci ignoruje
 *    blokady zalozone przez zamek, wiec gdy zamek zniknie (razem z animacja),
 *    pole mozna od razu postawic. Sam zamek respektuje wszystkie blokady
 *    (w tym 25 kratek od poprzedniego zamku).
 */
public class ChunkLockManager {

    /** Kto zalozyl blokade - pozwala Polu Smierci ignorowac blokady po zamku (i odwrotnie). */
    public enum Source { ZAMEK, POLE }

    private final long lockMillis;

    // zrodlo -> (klucz chunku ("world_chunkX_chunkZ") -> czas (ms) do ktorego chunk jest zablokowany)
    private final Map<Source, ConcurrentHashMap<String, Long>> lockedUntil = new EnumMap<>(Source.class);

    // Blokada 25 blokow: ostatnie lokalizacje uzycia + czas
    private static final int PROXIMITY_RADIUS = 25;
    private static final long PROXIMITY_LOCK_MILLIS = 30_000L;
    private final List<LocationEntry> recentPlacements = new ArrayList<>();

    private static class LocationEntry {
        final Location location;
        final long timestamp;
        final Source source;

        LocationEntry(Location location, long timestamp, Source source) {
            this.location = location;
            this.timestamp = timestamp;
            this.source = source;
        }
    }

    public ChunkLockManager(int lockSeconds) {
        this.lockMillis = TimeUnit.SECONDS.toMillis(lockSeconds);
        for (Source s : Source.values()) lockedUntil.put(s, new ConcurrentHashMap<>());
    }

    public static String chunkKey(Location loc) {
        return loc.getWorld().getName() + "_" + (loc.getBlockX() >> 4) + "_" + (loc.getBlockZ() >> 4);
    }

    /** Oznacza chunk jako uzyty TERAZ - blokuje go na lockSeconds od tego momentu. */
    public void markUsed(Source source, Location loc) {
        lockedUntil.get(source).put(chunkKey(loc), System.currentTimeMillis() + lockMillis);
    }

    /** Czy chunk jest zablokowany (przez kogokolwiek). */
    public boolean isLocked(Location loc) {
        return isLocked(loc, null);
    }

    /** Czy chunk jest zablokowany, pomijajac blokady zalozone przez {@code ignore} (moze byc null). */
    public boolean isLocked(Location loc, Source ignore) {
        return getRemainingSeconds(loc, ignore) > 0;
    }

    public long getRemainingSeconds(Location loc) {
        return getRemainingSeconds(loc, null);
    }

    public long getRemainingSeconds(Location loc, Source ignore) {
        String key = chunkKey(loc);
        long now = System.currentTimeMillis();
        long max = 0;
        for (Map.Entry<Source, ConcurrentHashMap<String, Long>> e : lockedUntil.entrySet()) {
            if (e.getKey() == ignore) continue;
            Long until = e.getValue().get(key);
            if (until != null && until - now > max) max = until - now;
        }
        return max > 0 ? (max + 999) / 1000L : 0;
    }

    // ================= Blokada 25 blokow =================

    /** Sprawdza czy lokalizacja jest w promieniu 25 blokow od ostatniego uzycia (w ciagu 30s). */
    public boolean isTooCloseToRecentPlacement(Location loc) {
        return isTooCloseToRecentPlacement(loc, null);
    }

    /** Jak wyzej, ale pomija uzycia zrodla {@code ignore} (moze byc null). */
    public boolean isTooCloseToRecentPlacement(Location loc, Source ignore) {
        return getProximityLockRemainingSeconds(loc, ignore) > 0;
    }

    /** Zwraca ile sekund zostalo z blokady 25 blokow (0 jesli nie blokuje). */
    public long getProximityLockRemainingSeconds(Location loc) {
        return getProximityLockRemainingSeconds(loc, null);
    }

    public long getProximityLockRemainingSeconds(Location loc, Source ignore) {
        long now = System.currentTimeMillis();
        recentPlacements.removeIf(e -> (now - e.timestamp) >= PROXIMITY_LOCK_MILLIS);
        long maxRemaining = 0;
        for (LocationEntry entry : recentPlacements) {
            if (entry.source == ignore) continue;
            if (entry.location.getWorld() != null && entry.location.getWorld().equals(loc.getWorld())
                    && entry.location.distance(loc) < PROXIMITY_RADIUS) {
                long remaining = PROXIMITY_LOCK_MILLIS - (now - entry.timestamp);
                if (remaining > maxRemaining) {
                    maxRemaining = remaining;
                }
            }
        }
        return maxRemaining > 0 ? (maxRemaining + 999) / 1000L : 0;
    }

    /** Zapisuje lokalizacje jako ostatnie miejsce uzycia zamku/pola. */
    public void markPlacement(Source source, Location loc) {
        recentPlacements.add(new LocationEntry(loc.clone(), System.currentTimeMillis(), source));
    }
}
