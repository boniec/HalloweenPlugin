package boniec.code.core;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

/**
 * Cienka, "miękka" integracja z WorldGuard (patrz plugin.yml -> softdepend).
 *
 * WAŻNE: ta klasa odwołuje się BEZPOŚREDNIO do klas WorldGuard/WorldEdit, więc
 * wolno ją wywoływać (nawet pośrednio) TYLKO gdy WorldGuard jest faktycznie
 * zainstalowany na serwerze - patrz Main.isWorldGuardEnabled() oraz sprawdzenie
 * w Main.onEnable(). Wywołanie tej klasy bez zainstalowanego WorldGuard
 * skończy się NoClassDefFoundError.
 */
public final class WorldGuardHook {

    private static RegionQuery cachedQuery;
    private static RegionContainer cachedContainer;

    private WorldGuardHook() {
    }

    private static RegionContainer getContainer() {
        if (cachedContainer == null) {
            cachedContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        }
        return cachedContainer;
    }

    private static RegionQuery getQuery() {
        if (cachedQuery == null) {
            cachedQuery = getContainer().createQuery();
        }
        return cachedQuery;
    }

    /**
     * @return true, jeśli flaga PVP w regionie WorldGuard obejmującym podaną
     * lokalizację jest jawnie ustawiona na DENY (PvP zablokowane w tym miejscu).
     * Jeśli flaga nie jest ustawiona (domyślnie PVP dozwolone) albo wystąpi
     * jakikolwiek błąd integracji, zwraca false - żeby awaria/inna wersja
     * WorldGuard nigdy nie blokowała patronów na całym serwerze.
     */
    public static boolean isPvpDenied(Location location) {
        if (location == null || location.getWorld() == null) return false;
        try {
            boolean allowed = getQuery().testState(BukkitAdapter.adapt(location), (RegionAssociable) null, Flags.PVP);
            return !allowed;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @return true, jeśli podana lokalizacja znajduje się w regionie WorldGuard
     * o wskazanym ID (porównanie bez rozróżniania wielkości liter). Jeśli region
     * nie istnieje albo wystąpi błąd integracji, zwraca false.
     * Wykonuje bezpośrednie sprawdzenie O(1) w bryle wskazanego regionu,
     * bez przeszukiwania całego drzewa przestrzennego świata.
     */
    public static boolean isInRegion(Location location, String regionId) {
        if (location == null || location.getWorld() == null || regionId == null) return false;
        try {
            RegionManager manager = getContainer().get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) return false;
            ProtectedRegion region = findRegion(manager, regionId);
            if (region == null) return false;
            return region.contains(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Sprawdza listę regionów po ich ID bez przeszukiwania całego drzewa przestrzennego
     * wszystkich regionów świata.
     *
     * @return true, jesli lokalizacja znajduje sie w KTORYMKOLWIEK z podanych regionow.
     */
    public static boolean isInAnyRegion(Location location, String... regionIds) {
        if (location == null || location.getWorld() == null || regionIds == null || regionIds.length == 0) return false;
        try {
            RegionManager manager = getContainer().get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) return false;

            BlockVector3 pt = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            for (String regionId : regionIds) {
                if (regionId == null) continue;
                ProtectedRegion region = findRegion(manager, regionId);
                if (region != null && region.contains(pt)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean errorLogged = false;

    private static void logOnce(Throwable t) {
        if (errorLogged) return;
        errorLogged = true;
        org.bukkit.Bukkit.getLogger().warning("[WorldGuardHook] Blad integracji z WorldGuard: " + t);
    }

    /** Szuka regionu po ID: najpierw dokladnie, potem malymi literami, na koncu bez rozrozniania wielkosci liter. */
    private static ProtectedRegion findRegion(RegionManager manager, String regionId) {
        ProtectedRegion r = manager.getRegion(regionId);
        if (r == null) r = manager.getRegion(regionId.toLowerCase(java.util.Locale.ROOT));
        if (r == null) {
            for (ProtectedRegion candidate : manager.getRegions().values()) {
                if (candidate.getId().equalsIgnoreCase(regionId)) return candidate;
            }
        }
        return r;
    }

    /**
     * @return true, jesli region o podanym ID istnieje w KTORYMKOLWIEK zaladowanym swiecie.
     * Przy bledzie integracji zwraca true (zeby nie blokowac funkcji na podstawie niepewnego wyniku).
     */
    public static boolean regionExistsAnywhere(String regionId) {
        if (regionId == null) return false;
        try {
            for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                RegionManager manager = getContainer().get(BukkitAdapter.adapt(w));
                if (manager != null && findRegion(manager, regionId) != null) return true;
            }
            return false;
        } catch (Throwable t) {
            logOnce(t);
            return true;
        }
    }

    /**
     * Wersja "tolerancyjna" dla graczy STOJACYCH na regionie: gracz jest w regionie, jesli region obejmuje
     * blok, w ktorym ma stopy, ALBO blok bezposrednio pod nimi (gracz stojacy na gornej scianie regionu
     * ma stopy o 1 blok wyzej niz ostatni blok regionu). ID regionu jest porownywane bez rozrozniania
     * wielkosci liter.
     */
    public static boolean isStandingInRegion(Location location, String regionId) {
        if (location == null || location.getWorld() == null || regionId == null) return false;
        try {
            RegionManager manager = getContainer().get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) return false;
            ProtectedRegion region = findRegion(manager, regionId);
            if (region == null) return false;
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            return region.contains(BlockVector3.at(x, y, z)) || region.contains(BlockVector3.at(x, y - 1, z));
        } catch (Throwable t) {
            logOnce(t);
            return false;
        }
    }

    /** @return swiat, w ktorym istnieje region o podanym ID (pierwszy znaleziony) albo null. */
    public static org.bukkit.World findRegionWorld(String regionId) {
        if (regionId == null) return null;
        try {
            for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                RegionManager manager = getContainer().get(BukkitAdapter.adapt(w));
                if (manager != null && findRegion(manager, regionId) != null) return w;
            }
        } catch (Throwable t) {
            logOnce(t);
        }
        return null;
    }

    /** @return {minX, minY, minZ, maxX, maxY, maxZ} regionu albo null, jesli regionu nie ma / blad integracji. */
    public static int[] getRegionBounds(org.bukkit.World world, String regionId) {
        if (world == null || regionId == null) return null;
        try {
            RegionManager manager = getContainer().get(BukkitAdapter.adapt(world));
            if (manager == null) return null;
            ProtectedRegion region = findRegion(manager, regionId);
            if (region == null) return null;
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            return new int[]{min.getBlockX(), min.getBlockY(), min.getBlockZ(), max.getBlockX(), max.getBlockY(), max.getBlockZ()};
        } catch (Throwable t) {
            logOnce(t);
            return null;
        }
    }

    /** @return true, jesli blok (x,y,z) lezy w regionie o podanym ID (bez rozrozniania wielkosci liter). */
    public static boolean containsBlock(org.bukkit.World world, String regionId, int x, int y, int z) {
        if (world == null || regionId == null) return false;
        try {
            RegionManager manager = getContainer().get(BukkitAdapter.adapt(world));
            if (manager == null) return false;
            ProtectedRegion region = findRegion(manager, regionId);
            return region != null && region.contains(BlockVector3.at(x, y, z));
        } catch (Throwable t) {
            logOnce(t);
            return false;
        }
    }

    /**
     * @return lista {x,y,z} WSZYSTKICH blokow powietrza lezacych w regionie (tylko w zaladowanych chunkach)
     * albo null, jesli bryla regionu jest wieksza niz maxObjetosc / region nie istnieje / blad integracji.
     * Region jest pobierany raz, wiec skan jest tani w porownaniu z wolaniem containsBlock() dla kazdego bloku.
     */
    public static java.util.List<int[]> listAirBlocks(org.bukkit.World world, String regionId, long maxObjetosc) {
        if (world == null || regionId == null) return null;
        try {
            RegionManager manager = getContainer().get(BukkitAdapter.adapt(world));
            if (manager == null) return null;
            ProtectedRegion region = findRegion(manager, regionId);
            if (region == null) return null;
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            int minY = Math.max(min.getBlockY(), world.getMinHeight());
            int maxY = Math.min(max.getBlockY(), world.getMaxHeight() - 1);
            long objetosc = (long) (max.getBlockX() - min.getBlockX() + 1)
                    * (max.getBlockZ() - min.getBlockZ() + 1) * Math.max(0, maxY - minY + 1);
            if (objetosc > maxObjetosc) return null;

            java.util.List<int[]> wynik = new java.util.ArrayList<>();
            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    for (int y = minY; y <= maxY; y++) {
                        if (!region.contains(BlockVector3.at(x, y, z))) continue;
                        if (world.getBlockAt(x, y, z).getType().isAir()) wynik.add(new int[]{x, y, z});
                    }
                }
            }
            return wynik;
        } catch (Throwable t) {
            logOnce(t);
            return null;
        }
    }
}
