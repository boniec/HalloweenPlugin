package boniec.antylogout;

import boniec.boss.BossbarUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sedno systemu antylogout: kto jest "w walce", od kiedy do kiedy, oraz kto
 * ostatnio go trafil (do zabicia na quit).
 * <p>
 * WYDAJNOSC (kluczowe przy 300 graczach on-line naraz): zamiast osobnego
 * harmonogramu/bossbara na kazdego z 300 graczy, mamy:
 * <ul>
 *   <li>JEDEN globalny task (co 20 tickow = 1 sekunda), ktory przechodzi
 *   WYLACZNIE po mapie realnie oznaczonych "w walce" graczy - jesli akurat
 *   nikt sie nie bije, ta mapa jest pusta i task praktycznie nic nie robi;
 *   nawet przy duzym ruchu PvP realistycznie tagniete jest zawsze male
 *   podzbiory 300-osobowego serwera, nie wszyscy naraz.</li>
 *   <li>Kazdy tagniety gracz dostaje SWOJ bossbar utworzony RAZ (przy
 *   pierwszym trafieniu) i trzymany az do konca walki - kolejne trafienia
 *   tylko przedluzaja timer na istniejacym obiekcie (patrz tag()), bez
 *   tworzenia nowych BossBar/NamespacedKey co sekunde.</li>
 *   <li>Tytul bossbara jest podmieniany TYLKO gdy liczba wyswietlanych
 *   sekund faktycznie sie zmienila (patrz CombatData#lastShownSeconds) -
 *   Bukkit i tak nie wysle pakietu, gdy setTitle/setProgress dostanie te
 *   sama wartosc co poprzednio, ale unikamy nawet budowania/formatowania
 *   Stringa, gdy nic by sie nie zmienilo.</li>
 * </ul>
 */
public final class AntyLogoutManager {

    private final JavaPlugin plugin;
    private final AntyLogoutConfig config;

    /** Gracze aktualnie "w walce". Dostep wylacznie z watku glownego serwera (jak wszystkie eventy Bukkit). */
    private final Map<UUID, CombatData> walka = new HashMap<>();

    private BukkitTask tickTask;

    public AntyLogoutManager(JavaPlugin plugin, AntyLogoutConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        stop();
        // Jeden globalny task dla calego serwera - patrz komentarz klasy.
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        wyczyscWszystkich();
    }

    /** Zdejmuje tag walki ze wszystkich (bossbary tez) - np. /antylogout wylacz. */
    public void wyczyscWszystkich() {
        for (CombatData data : walka.values()) {
            BossbarUtil.usun(data.bossBar);
        }
        walka.clear();
    }

    /**
     * Oznacza gracza jako "w walce" na pelny czas z configu, ustawiajac
     * atakujacego jako ostatniego przeciwnika (do ewentualnego zabicia na
     * quit). Wywolywane dla OBU uczestnikow trafienia PvP - patrz
     * AntyLogoutListener - zeby zaden z nich nie mogl "uciec" wylogowaniem
     * z przegrywanej walki.
     */
    public void tag(Player defender, Player attacker) {
        if (!config.isEnabled()) return;
        if (defender.hasPermission("antylogout.bypass")) return;

        long now = System.currentTimeMillis();
        long totalMillis = TimeUnit.SECONDS.toMillis(config.getCombatSeconds());
        long endsAt = now + totalMillis;
        UUID uuid = defender.getUniqueId();

        CombatData data = walka.get(uuid);
        if (data == null) {
            BossBar bar = BossbarUtil.utworz(plugin, config.buildBossbarTitle(config.getCombatSeconds()),
                    config.getBossbarColor(), config.getBossbarStyle());
            bar.setProgress(1.0);
            bar.addPlayer(defender);
            data = new CombatData(bar, endsAt, attacker.getUniqueId());
            data.totalMillis = totalMillis;
            data.lastShownSeconds = config.getCombatSeconds();
            data.hasBypass = defender.hasPermission("spawnpvp.bypass");
            walka.put(uuid, data);
        } else {
            data.endsAtMillis = endsAt;
            data.totalMillis = totalMillis;
            data.lastAttacker = attacker.getUniqueId();
            // Przy retagu odswiezamy tez cache uprawnienia - mogl sie zmienic
            // (np. admin nadal uprawnienie przez LuckPerms w trakcie walki).
            data.hasBypass = defender.hasPermission("spawnpvp.bypass");
        }
    }

    /** Zdejmuje tag walki (bez zabijania) - np. po smierci w normalnej walce albo reczne zwolnienie. */
    public void untag(UUID uuid) {
        CombatData data = walka.remove(uuid);
        if (data != null) {
            BossbarUtil.usun(data.bossBar);
        }
    }

    public boolean isInCombat(UUID uuid) {
        return walka.containsKey(uuid);
    }

    /**
     * Zwraca zakeszowane uprawnienie "spawnpvp.bypass" gracza bedacego w walce.
     * Wartość jest ustalana RAZ przy tagowaniu (i odswiezana przy retagu), wiec
     * wywolanie tej metody jest zwyklym odczytem z HashMap - zero kosztu LuckPerms.
     * Zawsze zwraca false gdy gracz nie jest w walce.
     */
    public boolean hasBypass(UUID uuid) {
        CombatData data = walka.get(uuid);
        return data != null && data.hasBypass;
    }

    public UUID getLastAttacker(UUID uuid) {
        CombatData data = walka.get(uuid);
        return data == null ? null : data.lastAttacker;
    }

    public int getTaggedCount() {
        return walka.size();
    }

    /**
     * Wywolywane z PlayerQuitEvent. Jesli gracz byl w walce - zabija go,
     * przypisujac kill ostatniemu przeciwnikowi (jesli wciaz jest on-line),
     * dokladnie tak jak normalna smierc w PvP (odpala sie zwykly
     * EntityDamageByEntityEvent -> PlayerDeathEvent, wiec cala reszta
     * pluginu - kradziez ekwipunku, statystyki zabojstw itd. - dziala
     * identycznie jak przy zwyklej smierci w walce).
     */
    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        CombatData data = walka.remove(uuid);
        if (data == null) {
            return;
        }
        BossbarUtil.usun(data.bossBar);

        if (data.endsAtMillis <= System.currentTimeMillis()) {
            // Walka i tak juz sie skonczyla (task jeszcze nie zdazyl posprzatac) - nic nie robimy.
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // UWAGA: celowo BEZ sprawdzania player.isOnline() tutaj. Ta metoda jest
        // wywolywana WYLACZNIE z PlayerQuitEvent (patrz AntyLogoutListener#onQuit),
        // czyli gracz z definicji juz jest w trakcie wylogowywania - w zaleznosci
        // od wersji serwera Bukkit/Spigot/Paper potrafi w tym momencie zwracac
        // isOnline() == false, mimo ze obiekt Player nadal jest w pelni sprawny
        // (mozna na nim wywolac damage()/setHealth() i normalnie odpali to
        // PlayerDeathEvent). Taki fallszywy false po cichu przerywal cala metode
        // TUTAJ - gracz znikal z serwera bez zadnej smierci, bez wiadomosci na
        // czacie, bez utraty ekwipunku/rankingu - dokladnie objaw "wychodzi z
        // walki i nic sie nie dzieje". Sam isDead() wystarczy jako zabezpieczenie
        // przed podwojnym zabiciem juz martwego gracza.
        if (player.isDead()) {
            return;
        }

        double zdrowie = player.getHealth();
        if (zdrowie <= 0.0) {
            return;
        }

        // UWAGA - dlaczego zabicie NIE MOZE ograniczac sie do samego
        // player.damage()/setHealth(0.0) i "zaufania", ze sie udalo:
        // W momencie PlayerQuitEvent serwer (Spigot/Paper) juz zaczyna
        // proces usuwania encji gracza ze swiata/watku swiata. Wywolanie
        // damage()/setHealth(0.0) w tym oknie POTRAFI odtworzyc dzwiek
        // obrazen (ten pakiet leci natychmiast, zanim cokolwiek zostanie
        // przerwane), ale samo dokonczenie PlayerDeathEvent (w tym zapis
        // stanu "martwy" do NBT gracza) bywa po cichu ucinane przez to, ze
        // zapis danych gracza przy quit odbywa sie NIEZALEZNIE i moze
        // nadpisac/wyscigowo ubiec efekt smierci - stad zglaszany objaw:
        // "jest dzwiek, a po ponownym wejsciu gracz i tak zyje dalej".
        // Rozwiazanie: 1) zabijamy dokladnie jak dotychczas (damage z
        // atakujacym -> normalny PlayerDeathEvent, zeby killfeed/ranking
        // ELO w PvpListener#onSmierc zadzialal identycznie jak przy zwyklym
        // zabiciu), 2) NASTEPNIE jawnie wymuszamy zapis danych gracza, zeby
        // stan "nie zyje" na pewno trafil na dysk PRZED tym, jak serwer
        // dokonczy standardowy zapis przy rozlaczaniu.
        try {
            Player napastnik = data.lastAttacker != null ? Bukkit.getPlayer(data.lastAttacker) : null;
            if (napastnik != null && napastnik.isOnline() && !napastnik.getUniqueId().equals(uuid)) {
                // damage() z encja jako sprawca - normalny EntityDamageByEntityEvent, wiec
                // Bukkit sam ustawi killera na PlayerDeathEvent tak samo jak przy zwyklym hicie.
                player.damage(zdrowie + 1.0, napastnik);
            } else {
                // Ostatni przeciwnik zdazyl wyjsc/nie jest juz on-line - zwykla smierc bez killera.
                player.setHealth(0.0);
            }

            // Zabezpieczenie: na czesci wersji/implementacji serwera powyzsze
            // wywolanie na wychodzacej encji potrafi zostac zignorowane bez
            // rzucenia wyjatku (zdrowie zostaje > 0, gracz "przezywa" mimo
            // dzwieku obrazen). Jesli tak sie stalo - dobijamy bezposrednio.
            if (!player.isDead() && player.getHealth() > 0.0) {
                player.setHealth(0.0);
            }

            // KLUCZOWE: wymuszamy natychmiastowy zapis danych gracza (playerdata),
            // zeby stan "martwy" na pewno trafil na dysk teraz, a nie zostal
            // po cichu nadpisany/wyscigowo ubiegniety przez standardowy zapis
            // przy rozlaczaniu gracza. Bez tego smierc potrafila "nie zdazyc"
            // zapisac sie, mimo ze caly powyzszy kod technicznie sie wykonal.
            player.saveData();
        } catch (Exception e) {
            // Zabezpieczenie diagnostyczne: gdyby cokolwiek w tym miejscu rzucilo wyjatek
            // (np. specyficzne zachowanie danej wersji serwera dla encji w trakcie
            // rozlaczania), chcemy to widziec w logu zamiast gracza po cichu znikajacego
            // z walki bez smierci/wiadomosci - dokladnie objaw, ktory ta metoda ma naprawiac.
            plugin.getLogger().warning("[Antylogout] Nie udalo sie zabic gracza " + player.getName()
                    + " na quit w trakcie walki: " + e);
        }
    }

    /** Co sekunde: przelicza pasek wylacznie oznaczonym graczom (patrz komentarz klasy - to caly sekret wydajnosci). */
    private void tick() {
        if (walka.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, CombatData>> it = walka.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CombatData> entry = it.next();
            CombatData data = entry.getValue();
            long remainingMillis = data.endsAtMillis - now;

            if (remainingMillis <= 0) {
                BossbarUtil.usun(data.bossBar);
                it.remove();
                continue;
            }

            int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);
            if (remainingSeconds != data.lastShownSeconds) {
                data.lastShownSeconds = remainingSeconds;
                data.bossBar.setTitle(config.buildBossbarTitle(remainingSeconds));
            }
            double progress = data.totalMillis <= 0 ? 1.0
                    : Math.max(0.0, Math.min(1.0, remainingMillis / (double) data.totalMillis));
            data.bossBar.setProgress(progress);
        }
    }
}
