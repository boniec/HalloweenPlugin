package boniec.portal;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Portal {

    public final String nazwa;
    public final String world;
    public final int minX, minY, minZ, maxX, maxY, maxZ;
    public final String destWorld;
    public final double destX, destY, destZ;
    public final float destYaw, destPitch;

    public Portal(String nazwa, String world, int x1, int y1, int z1, int x2, int y2, int z2,
                  String destWorld, double destX, double destY, double destZ, float destYaw, float destPitch) {
        this.nazwa = nazwa;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.destWorld = destWorld;
        this.destX = destX;
        this.destY = destY;
        this.destZ = destZ;
        this.destYaw = destYaw;
        this.destPitch = destPitch;
    }

    public boolean zawiera(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!world.equals(loc.getWorld().getName())) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Location cel() {
        World w = Bukkit.getWorld(destWorld);
        if (w == null) return null;
        return new Location(w, destX, destY, destZ, destYaw, destPitch);
    }
}
