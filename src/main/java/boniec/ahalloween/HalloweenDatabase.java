package boniec.ahalloween;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class HalloweenDatabase {

    private final JavaPlugin plugin;

    public HalloweenDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        try (Statement st = conn().createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ahalloween_players (
                      uuid TEXT PRIMARY KEY,
                      nick TEXT NOT NULL,
                      exp INTEGER NOT NULL DEFAULT 0,
                      poziom INTEGER NOT NULL DEFAULT 1,
                      pojemnosc INTEGER NOT NULL DEFAULT 200
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ahalloween_candy (
                      uuid TEXT NOT NULL,
                      candy_id TEXT NOT NULL,
                      ilosc INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY (uuid, candy_id)
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ahalloween_upgrades (
                      uuid TEXT NOT NULL,
                      upgrade_id TEXT NOT NULL,
                      poziom INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY (uuid, upgrade_id)
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ahalloween_missions (
                      uuid TEXT NOT NULL,
                      mission_id TEXT NOT NULL,
                      progress INTEGER NOT NULL DEFAULT 0,
                      claimed INTEGER NOT NULL DEFAULT 0,
                      period TEXT NOT NULL DEFAULT '',
                      PRIMARY KEY (uuid, mission_id, period)
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ahalloween_shop (
                      uuid TEXT NOT NULL,
                      item_id TEXT NOT NULL,
                      bought INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY (uuid, item_id)
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ahalloween_stats (
                      uuid TEXT PRIMARY KEY,
                      candies INTEGER NOT NULL DEFAULT 0,
                      mobs INTEGER NOT NULL DEFAULT 0,
                      missions INTEGER NOT NULL DEFAULT 0,
                      bosses INTEGER NOT NULL DEFAULT 0
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ahalloween_history (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      uuid TEXT NOT NULL,
                      candy_id TEXT NOT NULL,
                      ilosc INTEGER NOT NULL,
                      zrodlo TEXT NOT NULL,
                      czas INTEGER NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ahalloween_kv (
                      klucz TEXT PRIMARY KEY,
                      wartosc TEXT NOT NULL
                    )""");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[AHalloween] Nie udalo sie utworzyc tabel.", e);
        }
    }

    private Connection conn() {
        return boniec.code.core.UstawieniaDatabase.connection();
    }

    public HalloweenPlayer wczytaj(UUID uuid, String nick) {
        HalloweenPlayer p = new HalloweenPlayer(uuid, nick);
        try (PreparedStatement ps = conn().prepareStatement("SELECT exp, poziom, pojemnosc FROM ahalloween_players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    p.exp = rs.getInt("exp");
                    p.poziom = rs.getInt("poziom");
                    p.pojemnosc = rs.getInt("pojemnosc");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[AHalloween] Blad odczytu gracza", e);
        }
        zaladujMape(p.cukierki, "SELECT candy_id, ilosc FROM ahalloween_candy WHERE uuid=?", uuid, "candy_id", "ilosc");
        zaladujMape(p.ulepszenia, "SELECT upgrade_id, poziom FROM ahalloween_upgrades WHERE uuid=?", uuid, "upgrade_id", "poziom");
        zaladujMape(p.sklep, "SELECT item_id, bought FROM ahalloween_shop WHERE uuid=?", uuid, "item_id", "bought");
        try (PreparedStatement ps = conn().prepareStatement("SELECT candies, mobs, missions, bosses FROM ahalloween_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    p.statCukierki = rs.getInt("candies");
                    p.statMoby = rs.getInt("mobs");
                    p.statMisje = rs.getInt("missions");
                    p.statBossy = rs.getInt("bosses");
                }
            }
        } catch (SQLException ignored) {
        }
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT mission_id, progress, claimed, period FROM ahalloween_missions WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("mission_id");
                    p.misjeProgress.put(id, rs.getInt("progress"));
                    p.misjeOdebrane.put(id, rs.getInt("claimed") == 1);
                    p.misjeOkres.put(id, rs.getString("period"));
                }
            }
        } catch (SQLException ignored) {
        }
        return p;
    }

    private void zaladujMape(Map<String, Integer> mapa, String sql, UUID uuid, String k, String v) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) mapa.put(rs.getString(k), rs.getInt(v));
            }
        } catch (SQLException ignored) {
        }
    }

    public void zapisz(HalloweenPlayer p) {
        try {
            try (PreparedStatement ps = conn().prepareStatement(
                    "INSERT INTO ahalloween_players(uuid,nick,exp,poziom,pojemnosc) VALUES(?,?,?,?,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET nick=excluded.nick, exp=excluded.exp, "
                            + "poziom=excluded.poziom, pojemnosc=excluded.pojemnosc")) {
                ps.setString(1, p.uuid.toString());
                ps.setString(2, p.nick);
                ps.setInt(3, p.exp);
                ps.setInt(4, p.poziom);
                ps.setInt(5, p.pojemnosc);
                ps.executeUpdate();
            }
            zapiszMape("DELETE FROM ahalloween_candy WHERE uuid=?",
                    "INSERT INTO ahalloween_candy(uuid,candy_id,ilosc) VALUES(?,?,?)", p.uuid, p.cukierki);
            zapiszMape("DELETE FROM ahalloween_upgrades WHERE uuid=?",
                    "INSERT INTO ahalloween_upgrades(uuid,upgrade_id,poziom) VALUES(?,?,?)", p.uuid, p.ulepszenia);
            zapiszMape("DELETE FROM ahalloween_shop WHERE uuid=?",
                    "INSERT INTO ahalloween_shop(uuid,item_id,bought) VALUES(?,?,?)", p.uuid, p.sklep);
            try (PreparedStatement ps = conn().prepareStatement(
                    "INSERT INTO ahalloween_stats(uuid,candies,mobs,missions,bosses) VALUES(?,?,?,?,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET candies=excluded.candies, mobs=excluded.mobs, "
                            + "missions=excluded.missions, bosses=excluded.bosses")) {
                ps.setString(1, p.uuid.toString());
                ps.setInt(2, p.statCukierki);
                ps.setInt(3, p.statMoby);
                ps.setInt(4, p.statMisje);
                ps.setInt(5, p.statBossy);
                ps.executeUpdate();
            }
            try (PreparedStatement del = conn().prepareStatement("DELETE FROM ahalloween_missions WHERE uuid=?")) {
                del.setString(1, p.uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn().prepareStatement(
                    "INSERT INTO ahalloween_missions(uuid,mission_id,progress,claimed,period) VALUES(?,?,?,?,?)")) {
                for (Map.Entry<String, Integer> e : p.misjeProgress.entrySet()) {
                    ins.setString(1, p.uuid.toString());
                    ins.setString(2, e.getKey());
                    ins.setInt(3, e.getValue());
                    ins.setInt(4, Boolean.TRUE.equals(p.misjeOdebrane.get(e.getKey())) ? 1 : 0);
                    ins.setString(5, p.misjeOkres.getOrDefault(e.getKey(), ""));
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[AHalloween] Blad zapisu " + p.nick, e);
        }
    }

    private void zapiszMape(String delete, String insert, UUID uuid, Map<String, Integer> mapa) throws SQLException {
        try (PreparedStatement del = conn().prepareStatement(delete)) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn().prepareStatement(insert)) {
            for (Map.Entry<String, Integer> e : mapa.entrySet()) {
                ins.setString(1, uuid.toString());
                ins.setString(2, e.getKey());
                ins.setInt(3, e.getValue());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    public void historia(UUID uuid, String candy, int ile, String zrodlo) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO ahalloween_history(uuid,candy_id,ilosc,zrodlo,czas) VALUES(?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, candy);
            ps.setInt(3, ile);
            ps.setString(4, zrodlo);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public List<String> ostatniaHistoria(UUID uuid, int limit) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT candy_id, ilosc, zrodlo, czas FROM ahalloween_history WHERE uuid=? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("zrodlo") + " +" + rs.getInt("ilosc") + " " + rs.getString("candy_id"));
                }
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public List<Map.Entry<String, Integer>> top(String kolumna, int limit) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        String col = switch (kolumna) {
            case "mobs" -> "mobs";
            case "missions" -> "missions";
            case "bosses" -> "bosses";
            default -> "candies";
        };
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT p.nick, s." + col + " AS v FROM ahalloween_stats s "
                        + "JOIN ahalloween_players p ON p.uuid=s.uuid ORDER BY v DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Map.entry(rs.getString("nick"), rs.getInt("v")));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public List<Map.Entry<String, Integer>> topPoziom(int limit) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT nick, poziom FROM ahalloween_players ORDER BY poziom DESC, exp DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Map.entry(rs.getString("nick"), rs.getInt("poziom")));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public void ustawKv(String klucz, String wartosc) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO ahalloween_kv(klucz,wartosc) VALUES(?,?) ON CONFLICT(klucz) DO UPDATE SET wartosc=excluded.wartosc")) {
            ps.setString(1, klucz);
            ps.setString(2, wartosc);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[AHalloween] KV", e);
        }
    }

    public String kv(String klucz, String domyslna) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT wartosc FROM ahalloween_kv WHERE klucz=?")) {
            ps.setString(1, klucz);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException ignored) {
        }
        return domyslna;
    }

    public Map<String, String> wszystkieKv(String prefix) {
        Map<String, String> mapa = new LinkedHashMap<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT klucz, wartosc FROM ahalloween_kv WHERE klucz LIKE ?")) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) mapa.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException ignored) {
        }
        return mapa;
    }

    public void usunKvPrefix(String prefix) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM ahalloween_kv WHERE klucz LIKE ?")) {
            ps.setString(1, prefix + "%");
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void usunKv(String klucz) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM ahalloween_kv WHERE klucz=?")) {
            ps.setString(1, klucz);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}
