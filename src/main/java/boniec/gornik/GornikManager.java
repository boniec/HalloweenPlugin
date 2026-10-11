package boniec.gornik;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import boniec.code.core.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GornikManager {

    private final FileConfiguration config;
    private final File plik;
    private final JavaPlugin plugin;
    private boolean enabled;

    private final LinkedHashMap<String, Double> cenyBlokw = new LinkedHashMap<>();
    private final LinkedHashMap<String, Double> expBlokow = new LinkedHashMap<>();
    private final ConcurrentHashMap<UUID, double[]> zarobki = new ConcurrentHashMap<>();

    // Batchowanie wyplat ekonomii - zeby nie wywolywac economy.depositPlayer()
    // (a przez to zapisu/serializacji pliku gracza w Essentials) przy KAZDYM
    // wykopanym bloku. Zamiast tego kwoty sa sumowane w pamieci i wyplacane
    // zbiorczo co N sekund jednym wywolaniem na gracza.
    private final ConcurrentHashMap<UUID, Double> pendingPayout = new ConcurrentHashMap<>();
    private BukkitRunnable payoutTask = null;

    /** Domyslnie +50% kasy za kazdy poziom Fortune (F III = 2.5x). */

    // TurboDrop
    private boolean turbodropActive = false;
    private double turbodropMnoznik = 1.0;
    private int turbodropCzas = 0;
    private BossBar turbodropBossBar = null;
    private BukkitRunnable turbodropTask = null;

    public GornikManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "kopanie.yml");
        this.config = wczytajKonfig();
        this.enabled = config.getBoolean("settings.enabled", true);
        wczytajCeny();
        uruchomPayoutTask();
    }

    // ── Batchowanie wyplat ───────────────────────────────────

    /** Interwal (w tickach) wyplacania zaleglych zarobkow. Domyslnie 40 ticks = 2 sekundy. */
    private int getPayoutIntervalTicks() {
        int sekundy = config.getInt("settings.wyplata-co-sekund", 2);
        if (sekundy < 1) sekundy = 1;
        return sekundy * 20;
    }

    private void uruchomPayoutTask() {
        if (payoutTask != null) {
            payoutTask.cancel();
        }
        int interval = getPayoutIntervalTicks();
        payoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                wyplacZalegle();
            }
        };
        payoutTask.runTaskTimer(plugin, interval, interval);
    }

    /**
     * Wyplaca wszystkim graczom z zaleglosciami ich zsumowane zarobki -
     * jedno wywolanie economy.depositPlayer() na gracza zamiast jednego
     * na kazdy wykopany blok. Drastycznie zmniejsza obciazenie Essentials
     * (kazda wyplata to u nich zapis/serializacja pliku gracza).
     */
    private void wyplacZalegle() {
        if (pendingPayout.isEmpty()) return;
        Economy economy = getEconomyIfPresent();
        if (economy == null) return;

        // Zdejmujemy snapshot i czyscimy mape, zeby nowe zarobki w miedzyczasie
        // zaczely sie zbierac od zera (bez utraty danych - dziala na ConcurrentHashMap).
        for (UUID uuid : pendingPayout.keySet()) {
            Double kwota = pendingPayout.remove(uuid);
            if (kwota == null || kwota <= 0) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            economy.depositPlayer(player, kwota);
        }
    }

    /** Wyplaca natychmiast zaleglosc pojedynczego gracza (np. przy wyjsciu z serwera). */
    public void wyplacNatychmiast(UUID uuid) {
        Double kwota = pendingPayout.remove(uuid);
        if (kwota == null || kwota <= 0) return;
        Economy economy = getEconomyIfPresent();
        if (economy == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;
        economy.depositPlayer(player, kwota);
    }

    private Economy getEconomyIfPresent() {
        org.bukkit.plugin.RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    /** Dodaje kwote do zaleglej wyplaty gracza zamiast wyplacac natychmiast. */
    public void dodajDoWyplaty(UUID uuid, double kwota) {
        pendingPayout.merge(uuid, kwota, Double::sum);
    }

    public void zatrzymajPayoutTask() {
        if (payoutTask != null) {
            payoutTask.cancel();
            payoutTask = null;
        }
        // Wyplac wszystko co zostalo w kolejce przy wylaczaniu pluginu.
        wyplacZalegle();
    }

    private FileConfiguration wczytajKonfig() {
        if (!plik.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("kopanie.yml")) {
                if (in != null) {
                    plik.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, plik.toPath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(plik);
        InputStream defStream = getClass().getClassLoader().getResourceAsStream("kopanie.yml");
        if (defStream != null) {
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            cfg.setDefaults(def);
        }
        return cfg;
    }

    private void wczytajCeny() {
        cenyBlokw.clear();
        var section = config.getConfigurationSection("bloki");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                double cena = section.getDouble(key, 0);
                if (cena > 0) {
                    cenyBlokw.put(key.toUpperCase(), cena);
                }
            }
        }
        wczytajExp();
    }

    private void wczytajExp() {
        expBlokow.clear();
        var defRoot = config.getDefaults();
        var defSec = defRoot != null ? defRoot.getConfigurationSection("exp-bloki") : null;
        boolean dopisano = false;
        if (defSec != null) {
            for (String key : defSec.getKeys(false)) {
                if (!config.isSet("exp-bloki." + key)) {
                    config.set("exp-bloki." + key, defSec.get(key));
                    dopisano = true;
                }
            }
        }
        if (dopisano) {
            try {
                config.save(plik);
            } catch (Exception e) {
                plugin.getLogger().warning("[Gornik] Nie zapisano sekcji exp-bloki: " + e.getMessage());
            }
        }
        var section = config.getConfigurationSection("exp-bloki");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            double exp = section.getDouble(key, 0);
            if (exp > 0) {
                expBlokow.put(key.toUpperCase(), exp);
            }
        }
    }

    // ── Gettery ──────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public boolean isActionbarEnabled() { return config.getBoolean("settings.actionbar", true); }
    public int getStatystykiOknoSekund() { return config.getInt("settings.statystyki-okno-sekund", 60); }
    public double getCena(Material material) { return cenyBlokw.getOrDefault(material.name(), 0.0); }

    /** Czy w tym miejscu blok zamienia sie w $ (kopanie.yml), a nie w itemy na ziemi. */
    public boolean placiEkonomiaKopania(Location location, Material material) {
        if (!enabled || material == null) return false;
        if (getCena(material) <= 0) return false;
        try {
            return !WorldGuardHook.isInAnyRegion(location, "gornik-deny", "no-mining-economy");
        } catch (Throwable ignored) {
            return true;
        }
    }

    public double getExp(Material material) {
        if (material == null) return 0;
        Double v = expBlokow.get(material.name());
        if (v != null && v > 0) return v;
        String n = material.name();
        if (n.endsWith("_CONCRETE") && !n.endsWith("_CONCRETE_POWDER")) {
            return expBlokow.getOrDefault(n + "_POWDER", 0.0);
        }
        return 0;
    }
    public LinkedHashMap<String, Double> getCenyBlokw() { return cenyBlokw; }
    public FileConfiguration getConfig() { return config; }

    // ── Fortune + TurboDrop ──────────────────────────────────

    public double getFortuneBonusNaPoziom() {
        return Math.max(0.0, config.getDouble("settings.fortune-bonus-na-poziom", 0.5));
    }

    public double cenaZFortune(double cena, int fortuneLevel) {
        if (cena <= 0) return 0;
        if (fortuneLevel <= 0) return cena;
        return cena * (1.0 + getFortuneBonusNaPoziom() * fortuneLevel);
    }

    public double obliczCene(Material material, int fortuneLevel) {
        double cena = cenaZFortune(getCena(material), fortuneLevel);
        if (cena <= 0) return 0;
        if (turbodropActive) {
            cena *= turbodropMnoznik;
        }
        return cena;
    }

    public boolean isTurbodropActive() { return turbodropActive; }
    public double getTurbodropMnoznik() { return turbodropMnoznik; }
    public int getTurbodropCzas() { return turbodropCzas; }

    // ── TurboDrop ────────────────────────────────────────────

    public void wlaczTurbodrop(double mnoznik, int sekundy) {
        this.turbodropActive = true;
        this.turbodropMnoznik = mnoznik;
        this.turbodropCzas = sekundy;

        BarColor barColor = BarColor.YELLOW;
        BarStyle barStyle = BarStyle.SOLID;
        try {
            barColor = BarColor.valueOf(config.getString("turbodrop.bossbar-kolor", "YELLOW"));
        } catch (Exception ignored) {}
        try {
            barStyle = BarStyle.valueOf(config.getString("turbodrop.bossbar-styl", "SOLID"));
        } catch (Exception ignored) {}

        String tytul = config.getString("turbodrop.tytul", "&6TurboDrop &e&l%x% &7aktywny! &8| &7Pozostalo: &e%czas%")
                .replace("%x%", String.valueOf((int) mnoznik))
                .replace("%czas%", formatujCzas(sekundy));

        if (turbodropBossBar != null) {
            boniec.boss.BossbarUtil.usun(turbodropBossBar);
        }
        // UWAGA: celowo BEZ BarFlag.CREATE_FOG - ta flaga bossbara wlacza
        // wizualny efekt mgly na ekranie graczy (jak podczas walki z Witherem/
        // Enderdragonem), co bylo niezamierzone przy zwyklym baffie ekonomicznym.
        turbodropBossBar = boniec.boss.BossbarUtil.utworz(plugin,
                boniec.szaman.util.Msg.color(tytul),
                barColor, barStyle
        );
        for (org.bukkit.boss.BarFlag f : org.bukkit.boss.BarFlag.values()) turbodropBossBar.removeFlag(f); // bez mgly/ciemnego nieba/muzyki bossa
        turbodropBossBar.setVisible(true);

        // Dodaj wszystkich online graczy
        for (Player p : Bukkit.getOnlinePlayers()) {
            turbodropBossBar.addPlayer(p);
        }

        if (turbodropTask != null) {
            turbodropTask.cancel();
        }

        turbodropTask = new BukkitRunnable() {
            int remaining = sekundy;

            @Override
            public void run() {
                if (remaining <= 0) {
                    wylaczTurbodrop();
                    return;
                }
                remaining--;
                turbodropCzas = remaining;

                String tytulInner = config.getString("turbodrop.tytul", "&6TurboDrop &e&l%x% &7aktywny! &8| &7Pozostalo: &e%czas%")
                        .replace("%x%", String.valueOf((int) turbodropMnoznik))
                        .replace("%czas%", formatujCzas(remaining));
                turbodropBossBar.setTitle(boniec.szaman.util.Msg.color(tytulInner));
                turbodropBossBar.setProgress((double) remaining / sekundy);
            }
        };
        turbodropTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void wylaczTurbodrop() {
        turbodropActive = false;
        turbodropMnoznik = 1.0;
        turbodropCzas = 0;
        if (turbodropTask != null) {
            turbodropTask.cancel();
            turbodropTask = null;
        }
        if (turbodropBossBar != null) {
            boniec.boss.BossbarUtil.usun(turbodropBossBar);
            turbodropBossBar = null;
        }
    }

    public void dodajGraczaDoBossBara(Player player) {
        if (turbodropBossBar != null && turbodropActive) {
            turbodropBossBar.addPlayer(player);
        }
    }

    public void usunGraczaZBossBara(Player player) {
        if (turbodropBossBar != null) {
            turbodropBossBar.removePlayer(player);
        }
    }

    // ── Zarobki ──────────────────────────────────────────────

    public double dodajZarobek(UUID uuid, double kwota) {
        long teraz = System.currentTimeMillis();
        double[] entry = zarobki.get(uuid);
        if (entry == null) {
            entry = new double[]{teraz, 0};
            zarobki.put(uuid, entry);
        }
        long sekundy = getStatystykiOknoSekund() * 1000L;
        if (teraz - entry[0] > sekundy) {
            entry[0] = teraz;
            entry[1] = 0;
        }
        entry[1] += kwota;
        return kwota;
    }

    public double getZarobkiNaMinute(UUID uuid) {
        double[] entry = zarobki.get(uuid);
        if (entry == null) return 0;
        long sekundy = getStatystykiOknoSekund();
        return entry[1] * (60.0 / sekundy);
    }

    public void czysc(UUID uuid) { zarobki.remove(uuid); }
    public void wyczyscWszystkie() { zarobki.clear(); }

    // ── Reload ───────────────────────────────────────────────

    /** Dodaje/aktualizuje blok z reki admina. cena <= 0 usuwa wpis. */
    public boolean ustawCeneBloku(Material material, double cena) {
        if (material == null || material.isAir()) return false;
        Material blok = material;
        if (!blok.isBlock()) {
            return false;
        }
        if (cena <= 0) {
            config.set("bloki." + blok.name(), null);
            cenyBlokw.remove(blok.name());
        } else {
            config.set("bloki." + blok.name(), cena);
            cenyBlokw.put(blok.name(), cena);
        }
        try {
            config.save(plik);
        } catch (Exception e) {
            plugin.getLogger().warning("[Gornik] Nie zapisano kopanie.yml: " + e.getMessage());
            return false;
        }
        return true;
    }

    public void reload() {
        if (config instanceof YamlConfiguration yaml && plik.exists()) {
            try {
                yaml.load(plik);
            } catch (Exception e) {
                plugin.getLogger().warning("[Gornik] Nie udalo sie przeladowac kopanie.yml: " + e.getMessage());
            }
        }
        this.enabled = config.getBoolean("settings.enabled", true);
        wczytajCeny();
        wczytajExp();
        if (plugin instanceof boniec.dev.inventory.Main main && main.getGeneratorManager() != null) {
            main.getGeneratorManager().wczytajNocnyNerf();
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private String formatujCzas(int sekundy) {
        if (sekundy >= 60) {
            int min = sekundy / 60;
            int sec = sekundy % 60;
            return min + "m " + sec + "s";
        }
        return sekundy + "s";
    }
}
