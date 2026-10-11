package boniec.misje;

import boniec.dev.inventory.Main;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MisjeManager {

    private static final DateTimeFormatter DZIEN = DateTimeFormatter.ISO_LOCAL_DATE;

    public static final class Definicja {
        public final String id;
        public final TypMisji typ;
        public final int cel;
        public final int slot;
        public final Material material;
        public final String ikona;
        public final String nazwa;
        public final List<String> lore;

        Definicja(String id, TypMisji typ, int cel, int slot, Material material, String ikona, String nazwa, List<String> lore) {
            this.id = id;
            this.typ = typ;
            this.cel = Math.max(1, cel);
            this.slot = slot;
            this.material = material;
            this.ikona = ikona;
            this.nazwa = nazwa;
            this.lore = lore;
        }
    }

    public static final class Postep {
        int wartosc;
        boolean odebrano;
    }

    private final Main plugin;
    private final File plik;
    private FileConfiguration cfg;
    private final Map<String, Definicja> misje = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Postep>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> doZapisu = ConcurrentHashMap.newKeySet();
    private volatile String dzienCache = "";
    private BukkitTask ticker;
    private BukkitTask zapis;

    public MisjeManager(Main plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "misje.yml");
        utworzTabele();
        wczytaj();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 60, 20L * 60);
        zapis = Bukkit.getScheduler().runTaskTimer(plugin, this::zapiszCache, 20L * 30, 20L * 30);
    }

    public FileConfiguration getCfg() { return cfg; }
    public Main getPlugin() { return plugin; }
    public Map<String, Definicja> getMisje() { return misje; }
    public Definicja getMisja(String id) { return id == null ? null : misje.get(id.toLowerCase()); }

    public String txt(String path, String domyslna) {
        String v = cfg.getString(path);
        return (v == null || v.isEmpty()) ? domyslna : v;
    }

    public void wczytaj() {
        if (!dzienCache.isEmpty()) {
            zapiszCache();
        }
        if (!plik.exists()) {
            plugin.saveResource("misje.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(plik);
        InputStream def = plugin.getResource("misje.yml");
        if (def != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
        misje.clear();
        ConfigurationSection sekcja = cfg.getConfigurationSection("misje");
        if (sekcja != null) {
            for (String id : sekcja.getKeys(false)) {
                ConfigurationSection s = sekcja.getConfigurationSection(id);
                if (s == null) continue;
                TypMisji typ = TypMisji.zNazwy(s.getString("typ"));
                if (typ == null) {
                    plugin.getLogger().warning("[Misje] Nieznany typ misji: " + id);
                    continue;
                }
                Material mat = Material.matchMaterial(s.getString("material", "PAPER"));
                if (mat == null || !mat.isItem()) mat = Material.PAPER;
                misje.put(id.toLowerCase(), new Definicja(
                        id.toLowerCase(),
                        typ,
                        s.getInt("cel", 1),
                        s.getInt("slot", -1),
                        mat,
                        s.getString("ikona", "丶"),
                        s.getString("nazwa", id),
                        s.getStringList("lore")));
            }
        }
        dzienCache = dzisiaj();
        cache.clear();
    }

    public void zapiszConfig() {
        try {
            cfg.save(plik);
        } catch (IOException e) {
            plugin.getLogger().warning("[Misje] Zapis misje.yml: " + e.getMessage());
        }
    }

    public void zapiszPrzyWylaczaniu() {
        if (ticker != null) ticker.cancel();
        if (zapis != null) zapis.cancel();
        zapiszCache();
    }

    public void zamknij() {
        zapiszPrzyWylaczaniu();
    }

    public ZoneId strefa() {
        try {
            return ZoneId.of(txt("reset.strefa", "Europe/Warsaw"));
        } catch (Exception e) {
            return ZoneId.of("Europe/Warsaw");
        }
    }

    public String dzisiaj() {
        return LocalDate.now(strefa()).format(DZIEN);
    }

    public String czasDoResetu() {
        ZonedDateTime teraz = ZonedDateTime.now(strefa());
        int h = cfg.getInt("reset.godzina", 0);
        int m = cfg.getInt("reset.minuta", 0);
        ZonedDateTime reset = teraz.with(LocalTime.of(Math.max(0, Math.min(23, h)), Math.max(0, Math.min(59, m)), 0));
        if (!reset.isAfter(teraz)) reset = reset.plusDays(1);
        long sek = ChronoUnit.SECONDS.between(teraz, reset);
        long godz = sek / 3600;
        long min = (sek % 3600) / 60;
        return godz + "ʜ " + min + "ᴍ";
    }

    private void tick() {
        String dzis = dzisiaj();
        if (!dzis.equals(dzienCache)) {
            dzienCache = dzis;
            cache.clear();
            String msg = txt("wiadomosci.reset-globalny", "&8[&f亄&8] &f东 &eᴍɪꜱᴊᴇ ᴅɴɪᴀ ꜱɪᴇ ᴢʀᴇꜱᴇᴛᴏᴡᴀʟʏ!");
            Bukkit.broadcastMessage(Msg.color(msg));
            czyscStareDni(dzis);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            dodajPostep(p, TypMisji.SPEDZ_MINUTY, 1);
        }
    }

    public void dodajPostep(Player gracz, TypMisji typ, int ile) {
        if (gracz == null || typ == null || ile <= 0) return;
        String dzis = dzisiaj();
        if (!dzis.equals(dzienCache)) {
            dzienCache = dzis;
            cache.clear();
        }
        Map<String, Postep> mapa = postepGracza(gracz.getUniqueId(), dzis);
        for (Definicja def : misje.values()) {
            if (def.typ != typ) continue;
            Postep p = mapa.computeIfAbsent(def.id, k -> new Postep());
            if (p.odebrano) continue;
            int przed = p.wartosc;
            if (przed >= def.cel) continue;
            p.wartosc = Math.min(def.cel, przed + ile);
            doZapisu.add(gracz.getUniqueId());
            if (przed < def.cel && p.wartosc >= def.cel) {
                gracz.sendMessage(Msg.color(txt("wiadomosci.ukonczenie",
                        "&8[&f亄&8] &f両 &aᴍɪꜱᴊᴀ &#FFC34D%nazwa% &aᴜᴋᴏɴᴄᴢᴏɴᴀ — ᴏᴅʙɪᴇʀᴢ ᴡ /ᴍɪꜱᴊᴇ")
                        .replace("%nazwa%", def.nazwa)));
                zapiszGracza(gracz.getUniqueId(), dzis);
            }
        }
    }

    public void zapiszGraczaTeraz(UUID uuid) {
        zapiszGracza(uuid, dzisiaj());
    }

    public Postep postep(UUID uuid, String id) {
        return postepGracza(uuid, dzisiaj()).computeIfAbsent(id, k -> new Postep());
    }

    public int wartosc(UUID uuid, String id) {
        return postep(uuid, id).wartosc;
    }

    public boolean odebrano(UUID uuid, String id) {
        return postep(uuid, id).odebrano;
    }

    public enum WynikOdbioru { OK, NIE_UKONCZONA, JUZ_ODEBRANA, BRAK }

    public WynikOdbioru odbierz(Player gracz, String id) {
        Definicja def = getMisja(id);
        if (def == null) return WynikOdbioru.BRAK;
        Postep p = postep(gracz.getUniqueId(), def.id);
        if (p.odebrano) return WynikOdbioru.JUZ_ODEBRANA;
        if (p.wartosc < def.cel) return WynikOdbioru.NIE_UKONCZONA;
        p.odebrano = true;
        for (ItemStack item : getNagrody(def.id)) {
            for (ItemStack reszta : gracz.getInventory().addItem(item).values()) {
                gracz.getWorld().dropItemNaturally(gracz.getLocation(), reszta);
            }
        }
        zapiszGracza(gracz.getUniqueId(), dzisiaj());
        return WynikOdbioru.OK;
    }

    @SuppressWarnings("unchecked")
    public List<ItemStack> getNagrody(String id) {
        List<?> lista = cfg.getList("nagrody." + id);
        List<ItemStack> wynik = new ArrayList<>();
        if (lista != null) {
            for (Object o : lista) {
                if (o instanceof ItemStack item && !item.getType().isAir()) {
                    wynik.add(item.clone());
                }
            }
        }
        return wynik;
    }

    public void setNagrody(String id, List<ItemStack> przedmioty) {
        cfg.set("nagrody." + id, przedmioty);
        zapiszConfig();
    }

    public void resetGracza(UUID uuid) {
        cache.remove(uuid);
        String dzis = dzisiaj();
        try (PreparedStatement ps = boniec.code.core.CoreDatabase.connection()
                .prepareStatement("DELETE FROM misje_postep WHERE uuid = ? AND dzien = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, dzis);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Misje] reset: " + e.getMessage());
        }
    }

    private Map<String, Postep> postepGracza(UUID uuid, String dzien) {
        return cache.computeIfAbsent(uuid, u -> wczytajGracza(u, dzien));
    }

    private Map<String, Postep> wczytajGracza(UUID uuid, String dzien) {
        Map<String, Postep> mapa = new ConcurrentHashMap<>();
        try {
            boniec.code.core.CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT misja, postep, odebrano FROM misje_postep WHERE uuid = ? AND dzien = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, dzien);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Postep p = new Postep();
                            p.wartosc = rs.getInt("postep");
                            p.odebrano = rs.getInt("odebrano") != 0;
                            mapa.put(rs.getString("misja"), p);
                        }
                    }
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("[Misje] odczyt: " + e.getMessage());
        }
        return mapa;
    }

    private void zapiszCache() {
        if (doZapisu.isEmpty()) return;
        String dzis = dzisiaj();
        for (UUID uuid : List.copyOf(doZapisu)) {
            zapiszGracza(uuid, dzis);
            doZapisu.remove(uuid);
        }
    }

    private void zapiszGracza(UUID uuid, String dzien) {
        Map<String, Postep> mapa = cache.get(uuid);
        if (mapa == null || mapa.isEmpty()) return;
        try {
            boniec.code.core.CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO misje_postep (uuid, dzien, misja, postep, odebrano) VALUES (?, ?, ?, ?, ?) "
                                + "ON CONFLICT(uuid, dzien, misja) DO UPDATE SET postep = excluded.postep, odebrano = excluded.odebrano")) {
                    for (Map.Entry<String, Postep> e : mapa.entrySet()) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, dzien);
                        ps.setString(3, e.getKey());
                        ps.setInt(4, e.getValue().wartosc);
                        ps.setInt(5, e.getValue().odebrano ? 1 : 0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("[Misje] zapis: " + e.getMessage());
        }
    }

    private void czyscStareDni(String dzis) {
        try (PreparedStatement ps = boniec.code.core.CoreDatabase.connection()
                .prepareStatement("DELETE FROM misje_postep WHERE dzien <> ?")) {
            ps.setString(1, dzis);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Misje] czyszczenie: " + e.getMessage());
        }
    }

    private void utworzTabele() {
        try {
            boniec.code.core.CoreDatabase.connection().createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS misje_postep (
                        uuid TEXT NOT NULL,
                        dzien TEXT NOT NULL,
                        misja TEXT NOT NULL,
                        postep INTEGER NOT NULL DEFAULT 0,
                        odebrano INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, dzien, misja)
                    )
                    """);
        } catch (SQLException e) {
            plugin.getLogger().severe("[Misje] tabela: " + e.getMessage());
        }
    }
}
