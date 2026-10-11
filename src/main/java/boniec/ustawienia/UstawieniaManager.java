package boniec.ustawienia;

import boniec.code.core.CoreDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Per-gracz wylaczniki wiadomosci. Brak wpisu = wlaczone. */
public final class UstawieniaManager {

    private static UstawieniaManager inst;

    private final JavaPlugin plugin;
    /** uuid -> zbior WYLACZONYCH kluczy (SCOREBOARD trzymany w SidebarManager). */
    private final Map<UUID, EnumSet<UstawieniaKlucz>> wylaczone = new ConcurrentHashMap<>();

    public UstawieniaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        inst = this;
        wczytajWszystko();
    }

    public static UstawieniaManager get() {
        return inst;
    }

    public static boolean on(Player p, UstawieniaKlucz k) {
        return p != null && on(p.getUniqueId(), k);
    }

    public static boolean on(UUID uuid, UstawieniaKlucz k) {
        if (uuid == null || k == null) return true;
        if (k == UstawieniaKlucz.SCOREBOARD) {
            boniec.dev.inventory.Main core = boniec.dev.inventory.Main.getInstance();
            if (core != null && core.getSidebarManager() != null) {
                return core.getSidebarManager().jestWlaczony(uuid);
            }
            return true;
        }
        UstawieniaManager m = inst;
        if (m == null) return true;
        EnumSet<UstawieniaKlucz> set = m.wylaczone.get(uuid);
        return set == null || !set.contains(k);
    }

    public static void doGraczy(UstawieniaKlucz k, Consumer<Player> fn) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (on(p, k)) fn.accept(p);
        }
    }

    public static void broadcast(UstawieniaKlucz k, String wiadomosc) {
        if (wiadomosc == null || wiadomosc.isEmpty()) return;
        Bukkit.getConsoleSender().sendMessage(wiadomosc);
        doGraczy(k, p -> p.sendMessage(wiadomosc));
    }

    public boolean wlaczony(UUID uuid, UstawieniaKlucz k) {
        return on(uuid, k);
    }

    public boolean przelacz(Player p, UstawieniaKlucz k) {
        if (k == UstawieniaKlucz.SCOREBOARD) {
            boniec.dev.inventory.Main core = boniec.dev.inventory.Main.getInstance();
            if (core != null && core.getSidebarManager() != null) {
                return core.getSidebarManager().przelacz(p);
            }
            return true;
        }
        UUID uuid = p.getUniqueId();
        EnumSet<UstawieniaKlucz> set = wylaczone.computeIfAbsent(uuid, u -> EnumSet.noneOf(UstawieniaKlucz.class));
        boolean terazWlaczony;
        if (set.contains(k)) {
            set.remove(k);
            terazWlaczony = true;
        } else {
            set.add(k);
            terazWlaczony = false;
        }
        zapisz(uuid, k, !terazWlaczony);
        return terazWlaczony;
    }

    private void wczytajWszystko() {
        wylaczone.clear();
        try (PreparedStatement ps = CoreDatabase.connection().prepareStatement(
                "SELECT uuid, klucz FROM ustawienia_graczy WHERE wylaczone = 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    UstawieniaKlucz k = UstawieniaKlucz.zId(rs.getString("klucz"));
                    if (k == null || k == UstawieniaKlucz.SCOREBOARD) continue;
                    wylaczone.computeIfAbsent(uuid, u -> EnumSet.noneOf(UstawieniaKlucz.class)).add(k);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Ustawienia] Odczyt: " + e.getMessage());
        }
    }

    /** Pelny sync zapis wylaczonych opcji — przy wylaczaniu pluginu. */
    public void zapiszWszystkieSync() {
        try {
            CoreDatabase.runInTransaction(c -> {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM ustawienia_graczy")) {
                    del.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ustawienia_graczy (uuid, klucz, wylaczone) VALUES (?, ?, 1)")) {
                    for (Map.Entry<UUID, EnumSet<UstawieniaKlucz>> e : wylaczone.entrySet()) {
                        for (UstawieniaKlucz k : e.getValue()) {
                            if (k == UstawieniaKlucz.SCOREBOARD) continue;
                            ps.setString(1, e.getKey().toString());
                            ps.setString(2, k.id);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("[Ustawienia] Zapis przy wylaczaniu: " + e.getMessage());
        }
    }

    public static void wyczyscInstancje() {
        inst = null;
    }

    private void zapisz(UUID uuid, UstawieniaKlucz k, boolean wylacz) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CoreDatabase.runInTransaction(c -> {
                    if (wylacz) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO ustawienia_graczy (uuid, klucz, wylaczone) VALUES (?, ?, 1) ON CONFLICT(uuid, klucz) DO UPDATE SET wylaczone = 1")) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, k.id);
                            ps.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement ps = c.prepareStatement(
                                "DELETE FROM ustawienia_graczy WHERE uuid = ? AND klucz = ?")) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, k.id);
                            ps.executeUpdate();
                        }
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("[Ustawienia] Zapis: " + e.getMessage());
            }
        });
    }
}
