package boniec.portal;

import boniec.code.core.CoreDatabase;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalManager {

    public static final class Zaznaczenie {
        public Location pos1;
        public Location pos2;
    }

    private final Plugin plugin;
    private final Map<String, Portal> portale = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<UUID, Zaznaczenie> zaznaczenia = new ConcurrentHashMap<>();

    public PortalManager(Plugin plugin) {
        this.plugin = plugin;
        wczytaj();
    }

    public void wczytaj() {
        portale.clear();
        try (PreparedStatement ps = boniec.code.core.UstawieniaDatabase.connection().prepareStatement(
                "SELECT nazwa, world, x1, y1, z1, x2, y2, z2, dest_world, dest_x, dest_y, dest_z, dest_yaw, dest_pitch FROM portale");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Portal p = new Portal(
                        rs.getString("nazwa"),
                        rs.getString("world"),
                        rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"),
                        rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2"),
                        rs.getString("dest_world"),
                        rs.getDouble("dest_x"), rs.getDouble("dest_y"), rs.getDouble("dest_z"),
                        (float) rs.getDouble("dest_yaw"), (float) rs.getDouble("dest_pitch"));
                portale.put(p.nazwa.toLowerCase(Locale.ROOT), p);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Portal] Odczyt: " + e.getMessage());
        }
    }

    public Zaznaczenie zaznaczenie(UUID uuid) {
        return zaznaczenia.computeIfAbsent(uuid, k -> new Zaznaczenie());
    }

    public List<Portal> wszystkie() {
        return new ArrayList<>(portale.values());
    }

    public Portal get(String nazwa) {
        return nazwa == null ? null : portale.get(nazwa.toLowerCase(Locale.ROOT));
    }

    public Portal portalNa(Location loc) {
        for (Portal p : portale.values()) {
            if (p.zawiera(loc)) return p;
        }
        return null;
    }

    public boolean usun(String nazwa) {
        String key = nazwa.toLowerCase(Locale.ROOT);
        if (!portale.containsKey(key)) return false;
        portale.remove(key);
        try (PreparedStatement ps = boniec.code.core.UstawieniaDatabase.connection().prepareStatement("DELETE FROM portale WHERE nazwa = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Portal] Usun: " + e.getMessage());
        }
        return true;
    }

    public void zapisz(Portal portal) {
        String key = portal.nazwa.toLowerCase(Locale.ROOT);
        portale.put(key, portal);
        try (PreparedStatement ps = boniec.code.core.UstawieniaDatabase.connection().prepareStatement(
                "INSERT INTO portale (nazwa, world, x1, y1, z1, x2, y2, z2, dest_world, dest_x, dest_y, dest_z, dest_yaw, dest_pitch) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(nazwa) DO UPDATE SET world=excluded.world, x1=excluded.x1, y1=excluded.y1, z1=excluded.z1, "
                        + "x2=excluded.x2, y2=excluded.y2, z2=excluded.z2, dest_world=excluded.dest_world, dest_x=excluded.dest_x, "
                        + "dest_y=excluded.dest_y, dest_z=excluded.dest_z, dest_yaw=excluded.dest_yaw, dest_pitch=excluded.dest_pitch")) {
            ps.setString(1, key);
            ps.setString(2, portal.world);
            ps.setInt(3, portal.minX);
            ps.setInt(4, portal.minY);
            ps.setInt(5, portal.minZ);
            ps.setInt(6, portal.maxX);
            ps.setInt(7, portal.maxY);
            ps.setInt(8, portal.maxZ);
            ps.setString(9, portal.destWorld);
            ps.setDouble(10, portal.destX);
            ps.setDouble(11, portal.destY);
            ps.setDouble(12, portal.destZ);
            ps.setDouble(13, portal.destYaw);
            ps.setDouble(14, portal.destPitch);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Portal] Zapis: " + e.getMessage());
        }
    }
}
