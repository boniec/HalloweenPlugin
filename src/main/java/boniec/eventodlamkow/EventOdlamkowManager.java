package boniec.eventodlamkow;

import boniec.klany.core.KlanWalutaManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Event "Szal Odlamkow": przez ustawiony czas KAZDY blok wykopany w obrebie
 * generatora (system boniec.generatory) ma szanse% na zadropienie odlamkow
 * (fizyczny item wg szablonu z KlanWalutaManager/klany-odlamek.yml) prosto
 * do ekwipunku gracza. Przez caly czas trwania eventu online graczom
 * wyswietla sie odliczajacy bossbar.
 * <p>
 * Config: eventodlamki.yml (bossbar, szansa procentowa na blok) +
 * odlamki-klan.yml (ile sztuk leci za "trafiony" blok).
 * <p>
 * Wzorowane na TurboDropie z GornikManager (ten sam schemat bossbara/tasku),
 * ale to NIEZALEZNY event - dziala rownolegle i nie wplywa na TurboDropa.
 */
public class EventOdlamkowManager {

    private final JavaPlugin plugin;

    private final File eventFile;
    private FileConfiguration eventConfig;

    public FileConfiguration getEventConfig() {
        return eventConfig;
    }

    private final File dropFile;
    private FileConfiguration dropConfig;

    private final KlanWalutaManager klanWalutaManager;
    private final Random random = new Random();

    private boolean active = false;
    private int pozostaloSekund = 0;
    private int calkowityCzas = 0;
    private BossBar bossBar = null;
    private BukkitRunnable task = null;

    // Automatyczny harmonogram (harmonogram.* w eventodlamki.yml): co N sekund odpala event.
    private BukkitTask harmonogramTask = null;
    // Kiedy (epoch ms) zaplanowany jest kolejny automatyczny start; -1 gdy harmonogram wylaczony.
    private long nastepnyStartEpochMs = -1;

    public EventOdlamkowManager(JavaPlugin plugin, KlanWalutaManager klanWalutaManager) {
        this.plugin = plugin;
        this.klanWalutaManager = klanWalutaManager;
        this.eventFile = new File(plugin.getDataFolder(), "eventodlamki.yml");
        this.dropFile = new File(plugin.getDataFolder(), "odlamki-klan.yml");
        wczytajConfigi();
        uruchomHarmonogram();
    }

    private void wczytajConfigi() {
        eventConfig = wczytajZResource(eventFile, "eventodlamki.yml");
        dropConfig = wczytajZResource(dropFile, "odlamki-klan.yml");
    }

    private FileConfiguration wczytajZResource(File plik, String resourceName) {
        if (!plik.exists()) {
            try (InputStream in = plugin.getResource(resourceName)) {
                if (in != null) {
                    plik.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, plik.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[EventOdlamkow] Nie udalo sie utworzyc " + resourceName + ": " + e.getMessage());
            }
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(plik);
        InputStream defStream = plugin.getResource(resourceName);
        if (defStream != null) {
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            cfg.setDefaults(def);
        }
        return cfg;
    }

    public void reload() {
        wczytajConfigi();
        uruchomHarmonogram();
    }

    public boolean isActive() {
        return active;
    }

    public int getPozostaloSekund() {
        return pozostaloSekund;
    }

    // ── Automatyczny harmonogram ────────────────────────────────

    public void odswiezHarmonogram() {
        uruchomHarmonogram();
    }

    /**
     * Startuje event wg godzin z eventy.yml (Szal). Czas trwania z eventodlamki.yml.
     * Pole harmonogram.co-sekund jest ignorowane.
     */
    private void uruchomHarmonogram() {
        zatrzymajHarmonogram();

        if (!eventConfig.getBoolean("harmonogram.wlaczony", true)) {
            return;
        }
        nastepnyStartEpochMs = boniec.eventy.EventHarmonogram.epochNastepnego(boniec.eventy.EventHarmonogram.SZAL);

        harmonogramTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (nastepnyStartEpochMs < 0 || System.currentTimeMillis() < nastepnyStartEpochMs) {
                    return;
                }
                nastepnyStartEpochMs = boniec.eventy.EventHarmonogram.epochNastepnegoPo(boniec.eventy.EventHarmonogram.SZAL);
                if (active) {
                    return;
                }
                int czasTrwania = eventConfig.getInt("harmonogram.czas-trwania-sekund", 300);
                if (czasTrwania < 1) {
                    czasTrwania = 300;
                }
                start(czasTrwania);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void zatrzymajHarmonogram() {
        if (harmonogramTask != null) {
            harmonogramTask.cancel();
            harmonogramTask = null;
        }
        nastepnyStartEpochMs = -1;
    }

    /** Sprzatanie przy wylaczaniu pluginu - patrz Main.onDisable(). */
    public void shutdown() {
        zatrzymajHarmonogram();
        if (active) {
            stop(true);
        }
    }

    /** Sekundy do kolejnego automatycznego startu; -1 gdy harmonogram wylaczony. */
    public long getSekundyDoNastepnegoStartu() {
        if (nastepnyStartEpochMs < 0) {
            return -1;
        }
        return Math.max(0, (nastepnyStartEpochMs - System.currentTimeMillis()) / 1000L);
    }

    // ── Start / Stop eventu ─────────────────────────────────────

    /** @return false jesli event byl juz aktywny (nie odpalono ponownie). */
    public boolean start(int sekundy) {
        if (active) {
            return false;
        }
        if (sekundy < 1) {
            sekundy = 1;
        }
        this.active = true;
        this.calkowityCzas = sekundy;
        this.pozostaloSekund = sekundy;

        BarColor barColor = BarColor.PINK;
        BarStyle barStyle = BarStyle.SOLID;
        try {
            barColor = BarColor.valueOf(eventConfig.getString("bossbar.kolor", "PINK"));
        } catch (Exception ignored) {}
        try {
            barStyle = BarStyle.valueOf(eventConfig.getString("bossbar.styl", "SOLID"));
        } catch (Exception ignored) {}

        // UWAGA: celowo BEZ BarFlag.CREATE_FOG - ta flaga wlacza wizualna mgle
        // na ekranie graczy (jak np. TurboDrop w GornikManager - patrz komentarz tam).
        bossBar = boniec.boss.BossbarUtil.utworz(plugin,
                boniec.szaman.util.Msg.color(bossbarTytul(sekundy)),
                barColor, barStyle
        );
        for (org.bukkit.boss.BarFlag f : org.bukkit.boss.BarFlag.values()) bossBar.removeFlag(f); // bez mgly/ciemnego nieba/muzyki bossa
        bossBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        if (task != null) {
            task.cancel();
        }
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (pozostaloSekund <= 0) {
                    stop(false);
                    return;
                }
                pozostaloSekund--;
                if (bossBar != null) {
                    bossBar.setTitle(boniec.szaman.util.Msg.color(bossbarTytul(pozostaloSekund)));
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) pozostaloSekund / calkowityCzas)));
                }
                if (pozostaloSekund <= 0) {
                    stop(false);
                }
            }
        };
        task.runTaskTimer(plugin, 20L, 20L);

        String startMsg = eventConfig.getString("wiadomosci.start", "")
                .replace("%czas%", formatujCzas(sekundy));
        if (!startMsg.isEmpty()) {
            Bukkit.broadcastMessage(boniec.szaman.util.Msg.color(startMsg));
        }
        return true;
    }

    /**
     * Konczy event.
     * @param recznie true jesli zatrzymane komenda admina (/eventodlamki stop),
     *                false jesli event wygasl sam z siebie po uplywie czasu.
     * @return false jesli event i tak nie byl aktywny.
     */
    public boolean stop(boolean recznie) {
        if (!active) {
            return false;
        }
        active = false;
        pozostaloSekund = 0;
        calkowityCzas = 0;

        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            boniec.boss.BossbarUtil.usun(bossBar);
            bossBar = null;
        }

        String key = recznie ? "wiadomosci.stop-recznie" : "wiadomosci.stop";
        String msg = eventConfig.getString(key, "");
        if (!msg.isEmpty()) {
            Bukkit.broadcastMessage(boniec.szaman.util.Msg.color(msg));
        }
        return true;
    }

    private String bossbarTytul(int sekundyPozostale) {
        double procent = eventConfig.getDouble("szansa-na-odlamek.procent", 15.0);
        return eventConfig.getString("bossbar.tytul", "&d&lSZAL ODLAMKOW &7| &7Pozostalo: &e%czas%")
                .replace("%procent%", formatujProcent(procent))
                .replace("%czas%", formatujCzas(sekundyPozostale));
    }

    /** Dodaje/usuwa gracza z bossbara przy join/quit - patrz odpowiedniki w GornikManager dla TurboDropa. */
    public void dodajGraczaDoBossBara(Player player) {
        if (bossBar != null && active) {
            bossBar.addPlayer(player);
        }
    }

    public void usunGraczaZBossBara(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    // ── Drop odlamkow przy kopaniu generatora ───────────────────

    /**
     * Wywolywane przez MiningListener dla KAZDEGO wykopanego bloku, ktory
     * znajduje sie w obrebie generatora. Jesli event jest aktywny, losuje
     * szanse-na-odlamek% i ewentualnie daje graczowi odlamki (bezposrednio
     * do EQ, a jesli brak miejsca - upada pod nogi).
     */
    public void probujZadropicOdlamki(Player player) {
        if (!active) {
            return;
        }
        double procent = eventConfig.getDouble("szansa-na-odlamek.procent", 15.0);
        if (procent <= 0.0) {
            return;
        }
        if (random.nextDouble() * 100.0 >= procent) {
            return;
        }

        if (!klanWalutaManager.isUstawiony()) {
            // Szablon fizycznego odlamka nie zostal jeszcze ustawiony przez admina
            // (/adminklan ustawodlamek) - cicho pomijamy, tylko log w konsoli,
            // zeby nie spamowac graczy bledami z powodu braku konfiguracji.
            String log = eventConfig.getString("wiadomosci.brak-szablonu-konsola", "")
                    .replace("%gracz%", player.getName());
            if (!log.isEmpty()) {
                plugin.getLogger().warning(log);
            }
            return;
        }

        int ilosc = dropConfig.getInt("drop.ilosc-za-blok", 1);
        if (ilosc < 1) {
            ilosc = 1;
        }

        ItemStack odlamek = klanWalutaManager.getOdlamekTemplate();
        odlamek.setAmount(ilosc);

        var leftover = player.getInventory().addItem(odlamek);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }

        String wiad = eventConfig.getString("wiadomosci.gracz-otrzymal", "")
                .replace("%ilosc%", String.valueOf(ilosc));
        if (!wiad.isEmpty()) {
            player.sendMessage(boniec.szaman.util.Msg.color(wiad));
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

    private String formatujProcent(double procent) {
        if (procent == Math.floor(procent)) {
            return String.valueOf((long) procent);
        }
        return String.format(java.util.Locale.US, "%.1f", procent);
    }
}
