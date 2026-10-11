package boniec.boss;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Zarządzanie bezpiecznym tworzeniem i usuwaniem zarejestrowanych KeyedBossBarów.
 */
public final class BossbarUtil {

    private static final AtomicLong LICZNIK = new AtomicLong();

    private BossbarUtil() {
    }

    public static BossBar utworz(JavaPlugin plugin, String tytul, BarColor kolor, BarStyle styl) {
        NamespacedKey klucz = new NamespacedKey(plugin, "bar_" + System.currentTimeMillis() + "_" + LICZNIK.incrementAndGet());
        KeyedBossBar bar = Bukkit.createBossBar(klucz, tytul, kolor, styl);
        for (BarFlag flaga : BarFlag.values()) {
            bar.removeFlag(flaga);
        }
        return bar;
    }

    public static void usun(BossBar bar) {
        if (bar == null) return;
        bar.removeAll();
        bar.setVisible(false);
        if (bar instanceof KeyedBossBar keyed) {
            Bukkit.removeBossBar(keyed.getKey());
        }
    }

    public static int usunWszystkie(JavaPlugin plugin) {
        String ns = plugin.getName().toLowerCase(java.util.Locale.ROOT);
        List<KeyedBossBar> doUsuniecia = new ArrayList<>();
        Iterator<KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            KeyedBossBar bar = it.next();
            if (bar.getKey().getNamespace().equals(ns)) {
                doUsuniecia.add(bar);
            }
        }
        for (KeyedBossBar bar : doUsuniecia) {
            bar.removeAll();
            bar.setVisible(false);
            Bukkit.removeBossBar(bar.getKey());
        }
        return doUsuniecia.size();
    }

    public static void zdejmij(BossBar bar, Player gracz) {
        if (bar != null) bar.removePlayer(gracz);
    }
}