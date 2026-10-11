package boniec.portfel;

import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PortfelManager {

    private static final DecimalFormat SALDO_FORMAT = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private final JavaPlugin plugin;
    private final File plikConfig;
    private final File plikWiadomosci;
    private final File plikProduktow;
    private final File plikDanych;
    private final File folderGui;
    private final File folderHistorii;
    private final File folderImportu;

    private YamlConfiguration config;
    private YamlConfiguration wiadomosci;
    private YamlConfiguration produktyCfg;
    private YamlConfiguration guiWallet;
    private YamlConfiguration guiConfirm;
    private YamlConfiguration guiHistory;

    private final Map<String, Produkt> produkty = new LinkedHashMap<>();
    private final Map<UUID, Konto> konta = new ConcurrentHashMap<>();

    private String serverName;
    private double dailyMin;
    private double dailyMax;
    private long dailyCooldownMs;
    private volatile boolean doZapisu;
    private BukkitTask zadanieZapisu;

    public PortfelManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plikConfig = new File(plugin.getDataFolder(), "portfel.yml");
        this.plikWiadomosci = new File(plugin.getDataFolder(), "portfel-messages.yml");
        this.plikProduktow = new File(plugin.getDataFolder(), "portfel-produkty.yml");
        this.plikDanych = new File(plugin.getDataFolder(), "portfel-data.yml");
        this.folderGui = new File(plugin.getDataFolder(), "portfel/gui");
        this.folderHistorii = new File(plugin.getDataFolder(), "portfel/history");
        this.folderImportu = new File(plugin.getDataFolder(), "777-Portfel");

        zapiszDomyslne("portfel.yml", plikConfig);
        zapiszDomyslne("portfel-messages.yml", plikWiadomosci);
        zapiszDomyslne("portfel-produkty.yml", plikProduktow);
        zapiszDomyslne("portfel-data.yml", plikDanych);
        zapiszDomyslne("portfel/gui/wallet.yml", new File(folderGui, "wallet.yml"));
        zapiszDomyslne("portfel/gui/confirm.yml", new File(folderGui, "confirm.yml"));
        zapiszDomyslne("portfel/gui/history.yml", new File(folderGui, "history.yml"));

        folderHistorii.mkdirs();
        sprobujImportuLegacy();
        wczytajWszystko();

        zadanieZapisu = Bukkit.getScheduler().runTaskTimer(plugin, () -> zapiszDane(true), 600L, 600L);
    }

    public void zapiszPrzyWylaczaniu() {
        if (zadanieZapisu != null) {
            zadanieZapisu.cancel();
            zadanieZapisu = null;
        }
        zapiszDane(false);
    }

    public void zamknij() {
        zapiszPrzyWylaczaniu();
    }

    private void zapiszDomyslne(String zasob, File cel) {
        if (!cel.exists()) {
            cel.getParentFile().mkdirs();
            plugin.saveResource(zasob, false);
        }
    }

    public void wczytajWszystko() {
        config = wczytajZDomyslnymi(plikConfig, "portfel.yml");
        wiadomosci = wczytajZDomyslnymi(plikWiadomosci, "portfel-messages.yml");
        produktyCfg = wczytajZDomyslnymi(plikProduktow, "portfel-produkty.yml");
        guiWallet = YamlConfiguration.loadConfiguration(new File(folderGui, "wallet.yml"));
        guiConfirm = YamlConfiguration.loadConfiguration(new File(folderGui, "confirm.yml"));
        guiHistory = YamlConfiguration.loadConfiguration(new File(folderGui, "history.yml"));

        serverName = config.getString("server-name", "server");
        dailyMin = config.getDouble("daily-reward.min-amount", 0.01);
        dailyMax = config.getDouble("daily-reward.max-amount", 0.15);
        dailyCooldownMs = (long) (config.getDouble("daily-reward.cooldown-hours", 24) * 3600_000L);

        produkty.clear();
        ConfigurationSection sekcja = produktyCfg.getConfigurationSection("products");
        if (sekcja != null) {
            for (String id : sekcja.getKeys(false)) {
                ConfigurationSection s = sekcja.getConfigurationSection(id);
                if (s == null) continue;
                Produkt p = new Produkt();
                p.id = id;
                p.nazwa = s.getString("name", id);
                p.cena = s.getDouble("price", 0);
                p.material = s.getString("material", "PAPER");
                p.customModelData = s.getInt("custom-model-data", 0);
                p.slot = s.getInt("slot", -1);
                p.kategoria = s.getString("category", "main");
                p.lore = s.getStringList("lore");
                p.komendy = s.getStringList("commands");
                produkty.put(id, p);
            }
        }

        wczytajDane();
    }

    private YamlConfiguration wczytajZDomyslnymi(File plik, String zasob) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(plik);
        InputStream def = plugin.getResource(zasob);
        if (def != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
        return yaml;
    }

    private void wczytajDane() {
        zmigrujYamlPortfel();
        try {
            boniec.code.core.CoreDatabase.runLocked(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT uuid, name, balance, total_spent, daily_last_claim FROM portfel_konta");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            Konto k = new Konto();
                            k.name = rs.getString("name");
                            k.balance = rs.getDouble("balance");
                            k.totalSpent = rs.getDouble("total_spent");
                            k.dailyLastClaim = rs.getLong("daily_last_claim");
                            konta.put(uuid, k);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("[Portfel] Odczyt SQLite: " + e.getMessage());
        }
        if (konta.isEmpty()) {
            int wBazie = boniec.code.core.CoreDatabase.get().liczbaWierszy("portfel_konta");
            if (wBazie > 0) {
                plugin.getLogger().severe("[Portfel] WCZYTYWANIE NIEUDANE: 0 kont w pamieci, w data.db jest " + wBazie
                        + ". Nie uzywaj /plugman reload — zrestartuj caly serwer.");
            }
        } else {
            plugin.getLogger().info("[Portfel] Wczytano " + konta.size() + " kont z data.db");
        }
    }

    private void zmigrujYamlPortfel() {
        boniec.code.core.CoreDatabase db = boniec.code.core.CoreDatabase.get();
        if (db.juzZmigrowane("yaml:portfel-data.yml")) return;
        if (plikDanych.exists()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(plikDanych);
            ConfigurationSection gracze = data.getConfigurationSection("players");
            if (gracze != null) {
                for (String uuidStr : gracze.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        ConfigurationSection s = gracze.getConfigurationSection(uuidStr);
                        if (s == null) continue;
                        Konto k = new Konto();
                        k.balance = s.getDouble("balance", 0);
                        k.totalSpent = s.getDouble("total-spent", 0);
                        k.dailyLastClaim = s.getLong("daily-last-claim", 0);
                        k.name = s.getString("name", "");
                        konta.put(uuid, k);
                    } catch (IllegalArgumentException ignored) {}
                }
                doZapisu = true;
                zapiszDane(false);
            }
            File migrated = new File(plugin.getDataFolder(), "portfel-data.yml.migrated");
            plikDanych.renameTo(migrated);
        }
        db.oznaczMigracje("yaml:portfel-data.yml");
    }

    public void zapiszDane(boolean tylkoGdyBrudne) {
        if (tylkoGdyBrudne && !doZapisu) return;
        if (konta.isEmpty()) {
            int wBazie = boniec.code.core.CoreDatabase.get().liczbaWierszy("portfel_konta");
            if (wBazie > 0) {
                plugin.getLogger().warning("[Portfel] Pomijam zapis — cache pusty, w data.db jest " + wBazie
                        + " kont (chroni przed skasowaniem portfela przy reloadzie).");
                return;
            }
        }
        doZapisu = false;
        try {
            boniec.code.core.CoreDatabase.runInTransaction(c -> {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM portfel_konta")) {
                    del.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO portfel_konta (uuid, name, balance, total_spent, daily_last_claim) VALUES (?, ?, ?, ?, ?)")) {
                    for (Map.Entry<UUID, Konto> e : konta.entrySet()) {
                        Konto k = e.getValue();
                        ps.setString(1, e.getKey().toString());
                        ps.setString(2, k.name);
                        ps.setDouble(3, k.balance);
                        ps.setDouble(4, k.totalSpent);
                        ps.setLong(5, k.dailyLastClaim);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            });
        } catch (SQLException ex) {
            plugin.getLogger().warning("[Portfel] Nie udalo sie zapisac do data.db: " + ex.getMessage());
        }
        if (!tylkoGdyBrudne) {
            boniec.code.core.CoreDatabase.checkpointWal();
        }
    }

    private void sprobujImportuLegacy() {
        File legacyData = new File(folderImportu, "data.yml");
        if (plikDanych.exists() && plikDanych.length() > 32) return;
        if (!legacyData.exists()) return;
        plugin.getLogger().info("[Portfel] Importuje dane z 777-Portfel/data.yml...");
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyData);
        ConfigurationSection gracze = legacy.getConfigurationSection("players");
        if (gracze != null) {
            YamlConfiguration out = new YamlConfiguration();
            for (String uuidStr : gracze.getKeys(false)) {
                ConfigurationSection s = gracze.getConfigurationSection(uuidStr);
                if (s == null) continue;
                out.set("players." + uuidStr + ".balance", s.getDouble("balance", 0));
                out.set("players." + uuidStr + ".total-spent", s.getDouble("total-spent", 0));
                out.set("players." + uuidStr + ".daily-last-claim", s.getLong("daily-last-claim", 0));
                out.set("players." + uuidStr + ".name", s.getString("name", ""));
            }
            try {
                out.save(plikDanych);
            } catch (IOException e) {
                plugin.getLogger().warning("[Portfel] Import data.yml nieudany: " + e.getMessage());
            }
        }
        File legacyHist = new File(folderImportu, "history");
        if (legacyHist.isDirectory()) {
            folderHistorii.mkdirs();
            try {
                for (File f : legacyHist.listFiles((dir, name) -> name.endsWith(".yml"))) {
                    Files.copy(f.toPath(), new File(folderHistorii, f.getName()).toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[Portfel] Import historii czesciowy: " + e.getMessage());
            }
        }
        plugin.getLogger().info("[Portfel] Import legacy zakonczony.");
    }

    public Konto getKonto(UUID uuid) {
        return konta.computeIfAbsent(uuid, u -> new Konto());
    }

    public double getBalance(UUID uuid) {
        return getKonto(uuid).balance;
    }

    public String formatBalance(double amount) {
        return SALDO_FORMAT.format(amount);
    }

    public void ustawNazwe(Player p) {
        getKonto(p.getUniqueId()).name = p.getName();
        doZapisu = true;
    }

    public boolean dodaj(UUID uuid, double kwota, String zrodlo, String opis) {
        if (kwota <= 0) return false;
        Konto k = getKonto(uuid);
        k.balance += kwota;
        doZapisu = true;
        dodajTransakcje(uuid, kwota, "VPLN", opis, zrodlo, "ADD");
        return true;
    }

    public boolean zabierz(UUID uuid, double kwota, String zrodlo, String opis) {
        if (kwota <= 0) return false;
        Konto k = getKonto(uuid);
        if (k.balance + 1e-9 < kwota) return false;
        k.balance -= kwota;
        doZapisu = true;
        dodajTransakcje(uuid, kwota, "VPLN", opis, zrodlo, "REMOVE");
        return true;
    }

    public void ustaw(UUID uuid, double kwota) {
        getKonto(uuid).balance = Math.max(0, kwota);
        doZapisu = true;
    }

    public boolean kup(Player gracz, Produkt produkt) {
        UUID uuid = gracz.getUniqueId();
        Konto k = getKonto(uuid);
        if (k.balance + 1e-9 < produkt.cena) return false;
        k.balance -= produkt.cena;
        k.totalSpent += produkt.cena;
        doZapisu = true;

        for (String cmd : produkt.komendy) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{PLAYER}", gracz.getName()).replace("{player}", gracz.getName()));
        }

        dodajTransakcje(uuid, produkt.cena, produkt.nazwa, "Zakup produktu", gracz.getName(), "PURCHASE");
        powiadomZakup(gracz, produkt);
        return true;
    }

    private void powiadomZakup(Player gracz, Produkt produkt) {
        String wiad = wiadomosci.getString("purchase-notification", "");
        if (wiad.isEmpty()) return;
        wiad = wiad.replace("{PLAYER}", gracz.getName()).replace("{PRODUCT}", Msg.color(produkt.nazwa));
        String typ = wiadomosci.getString("purchase-notification-type", "chat");
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.hasPermission("portfel.notify")) continue;
            if (!boniec.ustawienia.UstawieniaManager.on(online, boniec.ustawienia.UstawieniaKlucz.PORTFEL)) continue;
            if ("subtitle".equalsIgnoreCase(typ)) {
                online.sendTitle("", Msg.color(wiad), 10, 60, 10);
            } else {
                Msg.send(online, wiad);
            }
        }
    }

    public boolean odbierzDaily(Player gracz) {
        Konto k = getKonto(gracz.getUniqueId());
        long teraz = System.currentTimeMillis();
        if (k.dailyLastClaim > 0 && teraz - k.dailyLastClaim < dailyCooldownMs) return false;
        double kwota = dailyMin;
        if (dailyMax > dailyMin) {
            kwota = ThreadLocalRandom.current().nextDouble(dailyMin, dailyMax);
        }
        k.dailyLastClaim = teraz;
        k.balance += kwota;
        doZapisu = true;
        dodajTransakcje(gracz.getUniqueId(), kwota, "VPLN", "Codzienna nagroda", "SYSTEM", "DAILY");
        Msg.send(gracz, msg("daily-reward-claimed").replace("{AMOUNT}", formatBalance(kwota)));
        return true;
    }

    public long getDailyCooldownRemaining(UUID uuid) {
        Konto k = getKonto(uuid);
        if (k.dailyLastClaim <= 0) return 0;
        long left = dailyCooldownMs - (System.currentTimeMillis() - k.dailyLastClaim);
        return Math.max(0, left);
    }

    public void dodajTransakcje(UUID uuid, double kwota, String item, String opis, String zrodlo, String typ) {
        File plik = new File(folderHistorii, uuid + ".yml");
        YamlConfiguration hist = plik.exists() ? YamlConfiguration.loadConfiguration(plik) : new YamlConfiguration();
        List<Map<?, ?>> lista = hist.getMapList("transactions");
        List<Map<String, Object>> nowa = new ArrayList<>();
        for (Map<?, ?> m : lista) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, v) -> copy.put(String.valueOf(k), v));
            nowa.add(copy);
        }
        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("date", System.currentTimeMillis());
        tx.put("amount", kwota);
        tx.put("item", item);
        tx.put("description", opis);
        tx.put("source", zrodlo);
        tx.put("type", typ);
        tx.put("server", serverName);
        nowa.add(0, tx);
        hist.set("transactions", nowa);
        try {
            hist.save(plik);
        } catch (IOException e) {
            plugin.getLogger().warning("[Portfel] Nie zapisano historii dla " + uuid);
        }
    }

    public List<Map<String, Object>> getHistoria(UUID uuid) {
        File plik = new File(folderHistorii, uuid + ".yml");
        if (!plik.exists()) return Collections.emptyList();
        YamlConfiguration hist = YamlConfiguration.loadConfiguration(plik);
        List<Map<String, Object>> wynik = new ArrayList<>();
        for (Map<?, ?> m : hist.getMapList("transactions")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, v) -> copy.put(String.valueOf(k), v));
            wynik.add(copy);
        }
        return wynik;
    }

    public String msg(String klucz) {
        return Msg.color(wiadomosci.getString(klucz, "&cBrak wiadomosci: " + klucz));
    }

    public Map<String, Produkt> getProdukty() {
        return produkty;
    }

    public Produkt getProdukt(String id) {
        return produkty.get(id);
    }

    public YamlConfiguration getGuiWallet() { return guiWallet; }
    public YamlConfiguration getGuiConfirm() { return guiConfirm; }
    public YamlConfiguration getGuiHistory() { return guiHistory; }

    public static final class Produkt {
        public String id;
        public String nazwa;
        public double cena;
        public String material;
        public int customModelData;
        public int slot;
        public String kategoria;
        public List<String> lore;
        public List<String> komendy;
    }

    public static final class Konto {
        public double balance;
        public double totalSpent;
        public long dailyLastClaim;
        public String name = "";
    }
}
