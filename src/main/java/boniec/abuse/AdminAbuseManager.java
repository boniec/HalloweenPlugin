package boniec.abuse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * "AdminAbuse" - efekt serwerowy wlaczany komenda /adminabuse start (koniec: /adminabuse stop).
 *
 * <p>Sekwencja (powtarza sie w kolo az do stop):
 * <ol>
 *   <li><b>START</b>: dla wszystkich graczy leci dzwiek <code>minecraft:tungtungsahur</code>
 *       (z resource packa serwera) oraz <b>13 s NIC nie widza</b> - Blindness + Darkness,
 *       ciagle odswiezane (tez dla osob dolaczajacych w trakcie).</li>
 *   <li><b>RAINBOW 13 s</b>: efekty zostaja sciagniete, wokol KAZDEGO gracza wiruja
 *       kolorowe czasteczki (dust) zmieniajace barwe, nad glowa kula disco
 *       (sfera czasteczek), a co sekunde kazdy gracz na serwerze dostaje
 *       wymuszony skok.</li>
 *   <li><b>3 s ciemnosci</b> i znowu <b>RAINBOW 13 s</b> ... tak az do stop.</li>
 * </ol>
 */
public class AdminAbuseManager {

    /** Klucz dzwieku z resource packa (assets/minecraft/sounds/tungtungsahur.ogg). */
    private static final String DZWIEK = "minecraft:tungtungsahur";

    private static final int TICK_S = 20;
    private static final int CIEMNOSC_START_S = 13;
    private static final int RAINBOW_S = 13;
    private static final int CIEMNOSC_S = 3;

    private final JavaPlugin plugin;
    private BukkitRunnable zadanie;
    private boolean aktywny;
    /** Czy biezaca faza to ciemnosc (false = rainbow). */
    private boolean fazaCiemna;
    /** Ticki od startu biezacej fazy. */
    private int licznik;
    /** Dlugosc biezacej fazy w tickach. */
    private int celFazy;
    /** Licznik tickow fazy rainbow - steruje kolorem i obrotem czasteczek. */
    private int t;

    public AdminAbuseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAktywny() {
        return aktywny;
    }

    /** Start efektu (niepowtarzalny - gdy juz dziala, nic sie nie dzieje). */
    public void start() {
        if (aktywny) return;
        aktywny = true;
        fazaCiemna = true;
        licznik = 0;
        t = 0;
        celFazy = CIEMNOSC_START_S * TICK_S;
        zagrajDzwiekWszystkim();
        dajCiemnoscWszystkim();

        zadanie = new BukkitRunnable() {
            @Override
            public void run() {
                krok();
            }
        };
        zadanie.runTaskTimer(plugin, 1L, 1L);
    }

    /** Stop efektu - zdejmuje ciemnosc ze wszystkich graczy i kasuje zadanie. */
    public void stop() {
        if (!aktywny) return;
        aktywny = false;
        if (zadanie != null) {
            zadanie.cancel();
            zadanie = null;
        }
        sciagnijCiemnoscWszystkim();
    }

    /** Wywolywane z Main#onDisable() - bezpieczne zamkniecie. */
    public void zamknij() {
        stop();
    }

    // ── Petla faz ─────────────────────────────────────────────────────────

    private void krok() {
        licznik++;
        if (fazaCiemna) {
            // Co 0.5 s odswiezamy efekty - takze dla graczy, ktorzy doszli w trakcie.
            if (licznik % 10 == 0) dajCiemnoscWszystkim();
            if (licznik >= celFazy) {
                fazaCiemna = false;
                licznik = 0;
                t = 0;
                celFazy = RAINBOW_S * TICK_S;
                zagrajDzwiekWszystkim();
                sciagnijCiemnoscWszystkim();
            }
            return;
        }

        t++;
        rainbowKrok();

        if (licznik >= celFazy) {
            fazaCiemna = true;
            licznik = 0;
            celFazy = CIEMNOSC_S * TICK_S;
            dajCiemnoscWszystkim();
        }
    }

    // ── Ciemnosc ──────────────────────────────────────────────────────────

    private void dajCiemnoscWszystkim() {
        // 40 tickow trwania przy odswiezaniu co 10 tickow - przerwa nigdy nie wystapi.
        PotionEffect ciemnosc = new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false);
        PotionEffect mrok = new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(ciemnosc);
            p.addPotionEffect(mrok);
        }
    }

    private void sciagnijCiemnoscWszystkim() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    // ── Rainbow: czasteczki + kula disco + wymuszone skoki ────────────────

    private void rainbowKrok() {
        // Kolor zmienia sie cyklicznie (pelny takt = 5 s), obrót czasteczek leci ciaggle.
        float hue = (t % 100) / 100f;
        // Czasteczki spawnujemy co 2 ticki (10x na sekunde) - dla czytelnosci i wydajnosci.
        boolean spawn = (t % 2) == 0;
        // Skok wymuszany raz na sekunde fazy rainbow.
        boolean skok = (licznik % TICK_S) == 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();

            if (spawn) {
                // 1) Ring czasteczek wirujacy wokol gracza na wysokosci ciala.
                for (int i = 0; i < 8; i++) {
                    double kat = (i * Math.PI / 4.0) + (t * 0.15);
                    Location pkt = loc.clone().add(Math.cos(kat), 1.0 + Math.sin(t * 0.2 + i) * 0.2, Math.sin(kat));
                    p.getWorld().spawnParticle(Particle.DUST, pkt, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(kolor((hue + i * 0.06f) % 1f), 1.1f));
                }

                // 2) Kula disco nad glowa - sfera czasteczek (5 poziomow x 10 punktow).
                Location ball = loc.clone().add(0, 2.3, 0);
                for (int ring = 0; ring < 5; ring++) {
                    double elevation = Math.toRadians(-60 + ring * 30);
                    double promien = Math.cos(elevation) * 0.9;
                    double y = Math.sin(elevation) * 0.9;
                    for (int i = 0; i < 10; i++) {
                        double azymut = (i * Math.PI / 5.0) + (t * 0.1) + ring * 0.3;
                        Location pkt = ball.clone().add(Math.cos(azymut) * promien, y, Math.sin(azymut) * promien);
                        p.getWorld().spawnParticle(Particle.DUST, pkt, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(kolor((hue + ring * 0.1f + i * 0.03f) % 1f), 1.0f));
                    }
                }
            }

            // 3) Wymuszony skok - kazda osoba na serwerze co sekunde.
            if (skok && p.isOnGround()) {
                Vector v = p.getVelocity();
                p.setVelocity(new Vector(v.getX(), 0.5, v.getZ()));
            }
        }
    }

    /** Kolor z barwy HSB (0-1) na org.bukkit.Color - konwersja jak w java.awt.Color.HSBtoRGB. */
    private Color kolor(float h, float s, float b) {
        int r;
        int g;
        int blue;
        if (s == 0f) {
            r = g = blue = Math.round(b * 255f);
        } else {
            float h6 = h * 6f;
            int i = (int) h6;
            float f = h6 - i;
            float p = b * (1f - s);
            float q = b * (1f - s * f);
            float t = b * (1f - s * (1f - f));
            switch (i % 6) {
                case 0:  r = Math.round(b * 255f); g = Math.round(t * 255f); blue = Math.round(p * 255f); break;
                case 1:  r = Math.round(q * 255f); g = Math.round(b * 255f); blue = Math.round(p * 255f); break;
                case 2:  r = Math.round(p * 255f); g = Math.round(b * 255f); blue = Math.round(t * 255f); break;
                case 3:  r = Math.round(p * 255f); g = Math.round(q * 255f); blue = Math.round(b * 255f); break;
                case 4:  r = Math.round(t * 255f); g = Math.round(p * 255f); blue = Math.round(b * 255f); break;
                default: r = Math.round(b * 255f); g = Math.round(p * 255f); blue = Math.round(q * 255f); break;
            }
        }
        return Color.fromRGB(r, g, blue);
    }

    private Color kolor(float hue) {
        return kolor(hue, 1f, 1f);
    }

    // ── Dzwiek ────────────────────────────────────────────────────────────

    /** Puszcza dzwiek u kazdego gracza (we wlasnej lokalizacji - wszyscy slysza). */
    private void zagrajDzwiekWszystkim() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), DZWIEK, 1.0f, 1.0f);
        }
    }

    /** Tlumaczy kody kolorow '&' (bez hexa - wiadomosci administracyjne sa proste). */
    public static String kolor(String tekst) {
        return ChatColor.translateAlternateColorCodes('&', tekst);
    }
}
