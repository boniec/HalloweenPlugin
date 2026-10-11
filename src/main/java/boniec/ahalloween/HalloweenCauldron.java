package boniec.ahalloween;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/** Shared cauldron; commands, deposits and ticking run on the server thread. */
public final class HalloweenCauldron {

    private static final Set<String> DROP_SOURCES = Set.of("zbieranie", "skrzynia", "mob", "boss", "trick-or-treat");
    private final AHalloweenManager manager;
    private int progress;
    private long boostEnd;
    private double boostMultiplier = 3;
    private BossBar bar;

    public HalloweenCauldron(AHalloweenManager manager) {
        this.manager = manager;
        String saved = manager.baza().kv("kociol.stan", "0:0:3.0");
        String[] parts = saved.split(":");
        if (parts.length != 3) throw new IllegalStateException("Nieprawidłowy zapis kotła Halloween");
        progress = Integer.parseInt(parts[0]);
        boostEnd = Long.parseLong(parts[1]);
        boostMultiplier = Double.parseDouble(parts[2]);
        if (progress < 0 || boostEnd < 0 || !Double.isFinite(boostMultiplier) || boostMultiplier < 1) {
            throw new IllegalStateException("Nieprawidłowy zapis kotła Halloween");
        }
    }

    public boolean enabled() { return manager.cfg().getBoolean("kociol.enabled", true); }
    public int progress() { return progress; }
    public int goal() { return Math.max(1, manager.cfg().getInt("kociol.cel", 1000)); }
    public int duration() { return Math.max(1, manager.cfg().getInt("kociol.czas-sekund", 600)); }
    public double configuredMultiplier() {
        double value = manager.cfg().getDouble("kociol.mnoznik", 3);
        return Double.isFinite(value) ? Math.max(1, value) : 3;
    }
    public long remainingSeconds() {
        return active() ? Math.max(1, (boostEnd - System.currentTimeMillis() + 999) / 1000) : 0;
    }
    public boolean active() {
        return enabled() && manager.aktywny() && boostEnd > System.currentTimeMillis();
    }
    public double multiplier(String source) {
        return active() && DROP_SOURCES.contains(source == null ? "" : source) ? boostMultiplier : 1;
    }
    public String status() {
        if (!enabled()) return "&cKocioł jest wyłączony.";
        if (!manager.aktywny()) return "&cEvent Halloween jest wyłączony.";
        if (active()) return "&aBonus cukierków ×" + boostMultiplier + " &7trwa jeszcze &f"
                + HalloweenUtil.formatCzas(remainingSeconds()) + "&7.";
        return "&7Wspólny kocioł: &6" + progress + "&7/&6" + goal() + " &7cukierków. Nagroda: &a×"
                + configuredMultiplier() + " &7przez &f" + HalloweenUtil.formatCzas(duration()) + "&7.";
    }

    public boolean deposit(Player player, int requested) {
        if (!enabled() || !manager.aktywny()) {
            message(player, "&cKocioł jest dostępny tylko podczas aktywnego eventu Halloween.");
            return false;
        }
        if (active()) {
            message(player, "&cBonus kotła już trwa. Kolejne wpłaty będą możliwe po jego zakończeniu.");
            return false;
        }
        if (requested <= 0) {
            message(player, "&cPodaj dodatnią liczbę cukierków.");
            return false;
        }
        HalloweenPlayer data = manager.dane(player);
        int deposited;
        boolean activated;
        synchronized (data) {
            // Count individual candies, regardless of their shop value. Never take excess over the goal.
            deposited = Math.min(requested, Math.max(0, goal() - progress));
            if (deposited <= 0) {
                message(player, "&cZebrany postęp osiągnął nowy cel. Przywróć poprzedni cel w konfiguracji.");
                return false;
            }
            if (data.sumaCukierkow() < deposited) {
                message(player, "&cNie masz tylu cukierków w worku.");
                return false;
            }
            Map<String, Integer> remaining = new HashMap<>(data.cukierki);
            int left = deposited;
            // Spend common, low-value candies first.
            var ids = remaining.keySet().stream().sorted(java.util.Comparator
                    .comparingInt((String id) -> manager.candy(id) == null ? 1 : manager.candy(id).value())
                    .thenComparing(id -> id)).toList();
            for (String id : ids) {
                int take = Math.min(Math.max(0, remaining.get(id)), left);
                remaining.put(id, remaining.get(id) - take);
                left -= take;
                if (left == 0) break;
            }
            remaining.values().removeIf(amount -> amount <= 0);
            int nextProgress = progress + deposited;
            activated = nextProgress >= goal();
            long nextEnd = activated ? System.currentTimeMillis() + duration() * 1000L : 0;
            double nextMultiplier = configuredMultiplier();
            if (activated) nextProgress = 0;
            try {
                manager.baza().zapiszWplateKotla(data, remaining, serialize(nextProgress, nextEnd, nextMultiplier));
            } catch (SQLException e) {
                manager.plugin().getLogger().log(Level.WARNING, "[AHalloween] Nie zapisano wpłaty do kotła", e);
                message(player, "&cNie udało się zapisać wpłaty. Twoje cukierki pozostały w worku.");
                return false;
            }
            data.cukierki.clear();
            data.cukierki.putAll(remaining);
            data.oznacz();
            progress = nextProgress;
            boostEnd = nextEnd;
            boostMultiplier = nextMultiplier;
        }
        message(player, "&aWrzucono &f" + deposited + " &acukierków do wspólnego kotła.");
        if (activated) {
            Bukkit.broadcastMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&6Kocioł został napełniony! &aCukierki ×"
                    + boostMultiplier + " dla wszystkich przez " + HalloweenUtil.formatCzas(duration()) + "!"));
        }
        return true;
    }

    public void tick() {
        if (active()) {
            if (bar == null) bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
            bar.setTitle(HalloweenUtil.color("&6Kocioł Halloween &8| &aCukierki ×" + boostMultiplier
                    + " &8| &f" + HalloweenUtil.formatCzas(remainingSeconds())));
            bar.setProgress(Math.min(1, (double) remainingSeconds() / duration()));
            bar.setVisible(true);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
            }
        } else {
            close();
        }
        if (boostEnd == 0 || System.currentTimeMillis() < boostEnd) return;
        if (!clearBoost()) return;
        if (manager.aktywny()) Bukkit.broadcastMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX
                + "&7Bonus kotła zakończył się. Możecie ponownie wrzucać cukierki!"));
    }

    public boolean clearBoost() {
        close();
        if (boostEnd == 0) return true;
        try {
            manager.baza().zapiszKociol(serialize(progress, 0, boostMultiplier));
            boostEnd = 0;
            return true;
        } catch (SQLException e) {
            manager.plugin().getLogger().log(Level.WARNING, "[AHalloween] Nie zapisano końca bonusu kotła", e);
            return false;
        }
    }

    public void close() {
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    private static String serialize(int progress, long end, double multiplier) {
        return progress + ":" + end + ":" + multiplier;
    }

    private static void message(Player player, String text) {
        player.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + text));
    }
}
