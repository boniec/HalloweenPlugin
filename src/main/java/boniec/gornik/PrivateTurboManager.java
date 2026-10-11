package boniec.gornik;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Prywatny (nie-serwerowy) TurboDrop: /freeturbo daje TYLKO graczowi, ktory go
 * uzyl, mnoznik do ceny bloku w /gornik (GornikManager#obliczCene) na czas z
 * freeturbo.yml (domyslnie 15 minut).
 * <p>
 * Czas leci NORMALNIE, w czasie rzeczywistym (co sekunde), niezaleznie od tego
 * ile blokow gracz kopie - kopanie 1 albo 100 blokow nic nie zmienia w odliczaniu.
 * Koniec bafu jest zapisywany jako znacznik czasu w freeturbo.yml, wiec baf
 * przetrwa tez restart serwera. Kazdy gracz ma wlasny bossbar (widoczny TYLKO dla
 * niego), ktorego wyglad (kolor, styl, tytul, wlaczenie) ustawia sie w freeturbo.yml.
 * <p>
 * Cooldown (domyslnie 12h) jest zapisywany trwale do freeturbo.yml - liczony od
 * momentu UZYCIA komendy (nie od wygasniecia bafu).
 */
public class PrivateTurboManager {

    private static final class Stan {
        /** Moment (epoch millis), w ktorym baf wygasa. */
        long koniecMs;
        BossBar bossBar;
    }

    private final JavaPlugin plugin;
    private final GornikManager gornikManager;
    private final File plik;
    private FileConfiguration cfg;
    private BukkitTask zadanie;

    /** Aktywne baffy per gracz (takze offline - czas leci normalnie). */
    private final Map<UUID, Stan> aktywne = new HashMap<>();

    /** Czas (epoch millis) ostatniego UZYCIA /freeturbo per gracz - do cooldownu. Trwale w freeturbo.yml. */
    private final Map<UUID, Long> ostatnieUzycie = new HashMap<>();

    public PrivateTurboManager(JavaPlugin plugin, GornikManager gornikManager) {
        this.plugin = plugin;
        this.gornikManager = gornikManager;
        this.plik = new File(plugin.getDataFolder(), "freeturbo.yml");
        this.cfg = wczytajKonfig();
        wczytajCooldowny();
        wczytajAktywne();
        // Odliczanie w czasie rzeczywistym: raz na sekunde odswiezamy bossbary i gasimy wygasle baffy.
        this.zadanie = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private FileConfiguration wczytajKonfig() {
        if (!plik.exists()) {
            try (java.io.InputStream in = plugin.getResource("freeturbo.yml")) {
                plik.getParentFile().mkdirs();
                if (in != null) {
                    java.nio.file.Files.copy(in, plik.toPath());
                } else {
                    plik.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[FreeTurbo] Nie udalo sie utworzyc freeturbo.yml.", e);
            }
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(plik);
        java.io.InputStream defStream = plugin.getResource("freeturbo.yml");
        if (defStream != null) {
            try {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
                cfg.setDefaults(def);
            } catch (Exception ignored) {
                // Brak defaultow nie jest krytyczne - cfg.getX(..., fallback) i tak ma wlasne wartosci domyslne w kodzie.
            }
        }
        return cfg;
    }

    /** Przeladowuje freeturbo.yml (wyglad bossbara, wiadomosci, mnoznik, czas...) bez restartu. */
    public void przeladuj() {
        this.cfg = wczytajKonfig();
        wczytajCooldowny();
        for (Map.Entry<UUID, Stan> e : aktywne.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            Stan stan = e.getValue();
            if (p == null) continue;
            if (stan.bossBar != null) {
                usunBossBar(stan);
            }
            if (bossbarWlaczony()) {
                stan.bossBar = utworzBossBar(p);
                odswiezBossBar(stan);
            }
        }
    }

    private void wczytajCooldowny() {
        ostatnieUzycie.clear();
        var section = cfg.getConfigurationSection("cooldowny");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long czas = section.getLong(key);
                ostatnieUzycie.put(uuid, czas);
            } catch (IllegalArgumentException ignored) {
                // Nieprawidlowy UUID w pliku - pomijamy pojedynczy wpis, reszta wczytuje sie normalnie.
            }
        }
    }

    /** Wczytuje aktywne baffy zapisane przed restartem (sekcja "aktywne" w freeturbo.yml). */
    private void wczytajAktywne() {
        aktywne.clear();
        ConfigurationSection section = cfg.getConfigurationSection("aktywne");
        if (section == null) return;
        long teraz = System.currentTimeMillis();
        boolean zmieniono = false;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long koniec = section.getLong(key);
                if (koniec > teraz) {
                    Stan stan = new Stan();
                    stan.koniecMs = koniec;
                    aktywne.put(uuid, stan);
                } else {
                    cfg.set("aktywne." + key, null);
                    zmieniono = true;
                }
            } catch (IllegalArgumentException ignored) {
                // Nieprawidlowy UUID - pomijamy.
            }
        }
        if (zmieniono) zapiszPlik();
    }

    private void zapiszPlik() {
        try {
            cfg.save(plik);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[FreeTurbo] Nie udalo sie zapisac freeturbo.yml.", e);
        }
    }

    private void zapiszCooldown(UUID uuid, long czas) {
        cfg.set("cooldowny." + uuid, czas);
        zapiszPlik();
    }

    // ── Config (freeturbo.yml) ─────────────────────────────────

    public double getMnoznik() {
        return cfg.getDouble("settings.mnoznik", 2.0);
    }

    public int getCzasTrwaniaSekund() {
        return cfg.getInt("settings.czas-trwania-sekund", 15 * 60);
    }

    public long getCooldownMs() {
        long godziny = cfg.getLong("settings.cooldown-godzin", 12);
        return godziny * 60L * 60L * 1000L;
    }

    public boolean bossbarWlaczony() {
        return cfg.getBoolean("bossbar.wlaczony", true);
    }

    /** Wiadomosc z sekcji "wiadomosci" freeturbo.yml (kolory '&' zamieniane przez wywolujacego). */
    public String getWiadomosc(String klucz, String domyslna) {
        return cfg.getString("wiadomosci." + klucz, domyslna);
    }

    // ── Cooldown ───────────────────────────────────────────────

    /** Zwraca ile ms zostalo do konca cooldownu (0 = mozna uzyc juz teraz). */
    public long getPozostalyCooldownMs(UUID uuid) {
        Long ostatnie = ostatnieUzycie.get(uuid);
        if (ostatnie == null) return 0L;
        long minelo = System.currentTimeMillis() - ostatnie;
        long pozostalo = getCooldownMs() - minelo;
        return Math.max(0L, pozostalo);
    }

    public boolean isOnCooldown(UUID uuid) {
        return getPozostalyCooldownMs(uuid) > 0;
    }

    // ── Aktywacja / status bafu ────────────────────────────────

    public boolean isActive(UUID uuid) {
        Stan stan = aktywne.get(uuid);
        return stan != null && stan.koniecMs > System.currentTimeMillis();
    }

    /** Ile sekund (czasu rzeczywistego) zostalo do konca bafu. */
    public int getPozostaleSekundy(UUID uuid) {
        Stan stan = aktywne.get(uuid);
        if (stan == null) return 0;
        long ms = stan.koniecMs - System.currentTimeMillis();
        return ms <= 0 ? 0 : (int) Math.ceil(ms / 1000.0);
    }

    /**
     * Aktywuje (lub PRZEDLUZA, jesli juz aktywny) prywatny turbo dla gracza na
     * pelny czas-trwania-sekund od nowa. Komenda zawsze odswieza cooldown.
     */
    public void aktywuj(Player player) {
        UUID uuid = player.getUniqueId();
        long teraz = System.currentTimeMillis();
        ostatnieUzycie.put(uuid, teraz);
        cfg.set("cooldowny." + uuid, teraz);

        Stan stan = aktywne.get(uuid);
        if (stan == null) {
            stan = new Stan();
            aktywne.put(uuid, stan);
        }
        stan.koniecMs = teraz + getCzasTrwaniaSekund() * 1000L;
        cfg.set("aktywne." + uuid, stan.koniecMs);
        zapiszPlik();

        if (stan.bossBar != null) {
            usunBossBar(stan);
        }
        if (bossbarWlaczony()) {
            stan.bossBar = utworzBossBar(player);
            odswiezBossBar(stan);
        }
    }

    private BossBar utworzBossBar(Player player) {
        BarColor barColor = BarColor.YELLOW;
        BarStyle barStyle = BarStyle.SOLID;
        try {
            barColor = BarColor.valueOf(cfg.getString("bossbar.kolor", "YELLOW").toUpperCase());
        } catch (Exception ignored) {}
        try {
            barStyle = BarStyle.valueOf(cfg.getString("bossbar.styl", "SOLID").toUpperCase());
        } catch (Exception ignored) {}
        BossBar bar = boniec.boss.BossbarUtil.utworz(plugin, "", barColor, barStyle);
        bar.addPlayer(player);
        return bar;
    }

    private void usunBossBar(Stan stan) {
        boniec.boss.BossbarUtil.usun(stan.bossBar);
        stan.bossBar = null;
    }

    /** Co sekunde: odswieza bossbary online graczy i gasi wygasle baffy. */
    private void tick() {
        if (aktywne.isEmpty()) return;
        long teraz = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(aktywne.keySet())) {
            Stan stan = aktywne.get(uuid);
            if (stan == null) continue;
            if (stan.koniecMs <= teraz) {
                wygas(uuid);
                continue;
            }
            odswiezBossBar(stan);
        }
    }

    private void odswiezBossBar(Stan stan) {
        if (stan.bossBar == null) return;
        int pozostalo = (int) Math.max(0L, (long) Math.ceil((stan.koniecMs - System.currentTimeMillis()) / 1000.0));
        String tytul = cfg.getString("bossbar.tytul", "&6Twoj FreeTurbo &e&lx%x% &7aktywny! &8| &7Pozostalo: &e%czas%")
                .replace("%x%", formatujMnoznik(getMnoznik()))
                .replace("%czas%", formatujCzas(pozostalo))
                .replace("%mmss%", String.format("%02d:%02d", pozostalo / 60, pozostalo % 60))
                .replace("%sekundy%", String.valueOf(pozostalo));
        stan.bossBar.setTitle(boniec.szaman.util.Msg.color(tytul));
        int calkowity = Math.max(1, getCzasTrwaniaSekund());
        stan.bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) pozostalo / calkowity)));
    }

    private void wygas(UUID uuid) {
        Stan stan = aktywne.remove(uuid);
        if (stan != null && stan.bossBar != null) {
            boniec.boss.BossbarUtil.usun(stan.bossBar);
        }
        cfg.set("aktywne." + uuid, null);
        zapiszPlik();

        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            String msg = getWiadomosc("wygasl", "&cTwój prywatny FreeTurbo wygasł.");
            if (msg != null && !msg.isEmpty()) {
                p.sendMessage(boniec.szaman.util.Msg.color(msg));
            }
        }
    }

    /** Wywolywane przy wyjsciu gracza z serwera - baf leci dalej normalnie, ale bossbar trzeba sprzatnac. */
    public void onQuit(UUID uuid) {
        Stan stan = aktywne.get(uuid);
        if (stan != null && stan.bossBar != null) {
            boniec.boss.BossbarUtil.usun(stan.bossBar);
            stan.bossBar = null;
        }
    }

    /** Wywolywane przy wejsciu gracza - jesli ma jeszcze aktywny baf, odtwarzamy jego prywatny bossbar. */
    public void onJoin(Player player) {
        Stan stan = aktywne.get(player.getUniqueId());
        if (stan == null) return;
        if (stan.koniecMs <= System.currentTimeMillis()) {
            wygas(player.getUniqueId());
            return;
        }
        if (!bossbarWlaczony()) return;
        if (stan.bossBar != null) boniec.boss.BossbarUtil.usun(stan.bossBar);
        stan.bossBar = utworzBossBar(player);
        odswiezBossBar(stan);
    }

    /** Wylaczenie pluginu - zatrzymuje odliczanie i sprząta bossbary (baffy zostaja zapisane w pliku). */
    public void zamknij() {
        if (zadanie != null) {
            zadanie.cancel();
            zadanie = null;
        }
        for (Stan stan : aktywne.values()) {
            if (stan.bossBar != null) {
                boniec.boss.BossbarUtil.usun(stan.bossBar);
                stan.bossBar = null;
            }
        }
    }

    /** Mnoznik do dolozenia do ceny bloku - 1.0, jesli gracz nie ma aktywnego prywatnego turbo. */
    public double getMnoznikDlaGracza(UUID uuid) {
        return isActive(uuid) ? getMnoznik() : 1.0;
    }

    private static String formatujMnoznik(double m) {
        return m == Math.floor(m) ? String.valueOf((int) m) : String.valueOf(m);
    }

    private String formatujCzas(int sekundy) {
        if (sekundy >= 60) {
            int min = sekundy / 60;
            int sec = sekundy % 60;
            return min + "m " + sec + "s";
        }
        return sekundy + "s";
    }

    public static String formatujCzasStatyczny(long ms) {
        long sekundy = ms / 1000L;
        long godziny = sekundy / 3600L;
        long minuty = (sekundy % 3600L) / 60L;
        long sek = sekundy % 60L;
        if (godziny > 0) {
            return godziny + "h " + minuty + "m";
        }
        if (minuty > 0) {
            return minuty + "m " + sek + "s";
        }
        return sek + "s";
    }
}
