package boniec.dev.polezamek.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

/**
 * Cienka, "miekka" integracja z WorldGuard (patrz plugin.yml -> softdepend).
 *
 * WAZNE: ta klasa odwoluje sie BEZPOSREDNIO do klas WorldGuard/WorldEdit, wiec
 * wolno ja wywolywac (nawet posrednio) TYLKO gdy WorldGuard jest faktycznie
 * zainstalowany na serwerze (patrz Main.isWorldGuardInstalled()). Wywolanie tej
 * klasy bez zainstalowanego WorldGuard skonczy sie NoClassDefFoundError.
 */
public final class WorldGuardUtil {

    private WorldGuardUtil() {
    }

    /**
     * Odpowiednik skryptowego "loop regions at {_loc}: if name contains X" -
     * zwraca true, jesli ID dowolnego regionu WorldGuard obejmujacego dana
     * lokalizacje zawiera podany fragment (bez rozroznienia wielkosci liter).
     * W razie jakiegokolwiek bledu integracji zwraca false, zeby nigdy nie
     * zablokowac mechaniki na sztywno z powodu awarii WorldGuard.
     */
    public static boolean isInRegionContaining(Location location, String needle) {
        try {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));
            String lowerNeedle = needle.toLowerCase(java.util.Locale.ROOT);
            for (ProtectedRegion region : set) {
                if (region.getId().toLowerCase(java.util.Locale.ROOT).contains(lowerNeedle)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True, gdy lokalizacja jest w regionie o dokladnym ID. */
    public static boolean isInRegionExact(Location location, String id) {
        if (location == null || id == null || id.isBlank()) return false;
        try {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));
            for (ProtectedRegion region : set) {
                if (region.getId().equalsIgnoreCase(id)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
