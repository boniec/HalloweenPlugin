package boniec.klany.listener;

import boniec.klany.core.Klan;
import boniec.klany.core.KlanManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Koloruje nick/nametag nad glowa gracza NA ZIELONO, jesli patrzacy i wyswietlany
 * gracz sa razem w tym samym klanie, i NA CZERWONO, jesli wyswietlany gracz ma
 * klan, ale inny (albo patrzacy nie ma klanu wcale). Gracze bez klanu zostaja
 * bez koloru (domyslny bialy).
 * <p>
 * DZIALA CALKOWICIE NIEZALEZNIE od PlaceholderAPI i TAB - uzywa wprost natywnych
 * "Team" ze Scoreboard API Bukkita. Kazdy gracz dostaje WLASNY Scoreboard (a nie
 * ten sam co wszyscy), zeby ten sam gracz mogl byc jednoczesnie zielony dla
 * jednego widza i czerwony dla drugiego - to jest jedyny sposob, zeby to
 * naprawde bylo "per-widz", a nie globalne dla calego serwera.
 * <p>
 * UWAGA: jesli na serwerze dziala TEZ plugin TAB (albo inny plugin od nametagow),
 * ktory rowniez przypisuje graczom wlasny Scoreboard, moze to wejsc w konflikt
 * (wygra ten, ktory przypisze scoreboard jako ostatni). Jesli po wgraniu tego
 * nadal nic sie nie zmienia, a serwer ma TAB - trzeba wylaczyc w TAB kolorowanie
 * nazwy gracza (zostawiajac np. tylko prefix/tabliste), zeby nie nadpisywal
 * naszego Scoreboardu.
 */
public class NametagKolorListener implements Listener {

    private static final String TEAM_SWOI = "klan_swoi";
    private static final String TEAM_OBCY = "klan_obcy";
    // Scheduler co 200 tickow (~10s) jako fallback na wypadek desynca.
    // Kolory sa przede wszystkim aktualizowane zdarzeniowo (join, quit, zmiany w klanie),
    // wiec czestszy scheduler to zbedny koszt iterowania po wszystkich 300 graczy.
    private static final long INTERWAL_TICKOW = 200L;

    private final Plugin plugin;
    private final KlanManager klanManager;

    public NametagKolorListener(Plugin plugin, KlanManager klanManager) {
        this.plugin = plugin;
        this.klanManager = klanManager;
    }

    /** Wywolac raz w onEnable() - uruchamia cykliczne odswiezanie kolorow dla wszystkich online. */
    public void uruchom() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            przygotujScoreboard(p);
        }
        odswiezWszystkich();
        Bukkit.getScheduler().runTaskTimer(plugin, this::odswiezWszystkich, INTERWAL_TICKOW, INTERWAL_TICKOW);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        przygotujScoreboard(event.getPlayer());
        // Nowy gracz musi zobaczyc kolory wszystkich, i wszyscy musza zobaczyc jego kolor.
        odswiezWszystkich();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Wychodzacego gracza trzeba usunac z zespolow u KAZDEGO innego gracza,
        // inaczej zostanie "duch" wpisu po jego nicku. Sprawdzamy hasEntry() zeby
        // nie wysylac zbednego pakietu removeEntry tam, gdzie wpisu i tak nie bylo.
        String nick = event.getPlayer().getName();
        for (Player pozostaly : Bukkit.getOnlinePlayers()) {
            if (pozostaly.equals(event.getPlayer())) continue;
            Scoreboard tablica = pozostaly.getScoreboard();
            Team swoi = tablica.getTeam(TEAM_SWOI);
            Team obcy = tablica.getTeam(TEAM_OBCY);
            if (swoi != null && swoi.hasEntry(nick)) swoi.removeEntry(nick);
            if (obcy != null && obcy.hasEntry(nick)) obcy.removeEntry(nick);
        }
    }

    /** Wywolac po kazdej zmianie w klanach (dolaczenie/wyjscie/wyrzucenie/rozwiazanie), zeby kolory zaktualizowaly sie od razu, a nie dopiero przy nastepnym ticku odswiezania. */
    public void odswiezWszystkich() {
        // Cache klanu KAZDEGO gracza online liczony RAZ na caly przebieg (a nie
        // N razy w petli wewnetrznej per patrzacy) - getKlanGracza() to tylko
        // odczyt z mapy w pamieci, ale przy K graczy online i bez cache
        // wolalibysmy go K*(K-1) razy zamiast K razy. Realny zysk rosnie
        // kwadratowo wraz z liczba graczy online.
        Player[] wszyscy = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        java.util.Map<UUID, Klan> klanyGraczy = new java.util.HashMap<>(wszyscy.length);
        for (Player p : wszyscy) {
            klanyGraczy.put(p.getUniqueId(), klanManager.getKlanGracza(p.getUniqueId()));
        }

        // Grupowanie graczy PO KLANIE, liczone RAZ na caly przebieg (nie per
        // patrzacy) - klucz to id klanu (null-klan pomijamy, bo gracze bez klanu
        // nigdy nie sa "swoi" dla nikogo). Dzieki temu, dla danego patrzacego,
        // caly zestaw "swoi" to po prostu goracy podglad jednej gotowej listy
        // (jego wlasna grupa minus on sam), zamiast n-krotnego porownywania
        // klanId para-po-parze.
        java.util.Map<Integer, java.util.List<Player>> graczeWgKlanu = new java.util.HashMap<>();
        for (Player p : wszyscy) {
            Klan klan = klanyGraczy.get(p.getUniqueId());
            if (klan == null) continue;
            graczeWgKlanu.computeIfAbsent(klan.getId(), k -> new java.util.ArrayList<>()).add(p);
        }

        // Zbior WSZYSTKICH nickow graczy majacych jakikolwiek klan (do policzenia
        // "obcy" = ma klan - swoi, bez kolejnej petli po wszyscy).
        java.util.Set<String> wszyscyZKlanem = new java.util.HashSet<>();
        for (java.util.List<Player> grupa : graczeWgKlanu.values()) {
            for (Player p : grupa) wszyscyZKlanem.add(p.getName());
        }

        for (Player patrzacy : wszyscy) {
            przygotujScoreboard(patrzacy);
            Scoreboard tablica = patrzacy.getScoreboard();
            Team swoi = tablica.getTeam(TEAM_SWOI);
            Team obcy = tablica.getTeam(TEAM_OBCY);
            if (swoi == null || obcy == null) continue;

            Klan klanPatrzacego = klanyGraczy.get(patrzacy.getUniqueId());

            // Docelowy zbior "swoi" = cala jego grupa klanowa minus on sam - JEDNO
            // pobranie z mapy grup, zero petli po wszystkich graczach online.
            java.util.Set<String> docelowiSwoi = new java.util.HashSet<>();
            if (klanPatrzacego != null) {
                for (Player wspolklanowiec : graczeWgKlanu.get(klanPatrzacego.getId())) {
                    if (!wspolklanowiec.equals(patrzacy)) docelowiSwoi.add(wspolklanowiec.getName());
                }
            }
            // Docelowy zbior "obcy" = kazdy majacy klan, ktory nie jest w "swoi".
            java.util.Set<String> docelowiObcy = new java.util.HashSet<>(wszyscyZKlanem);
            docelowiObcy.removeAll(docelowiSwoi);
            docelowiObcy.remove(patrzacy.getName());

            // Roznica zbiorow zamiast n-krotnego hasEntry()+ewentualnego add/remove:
            // getEntries() to JEDNO wywolanie (nie n), a nastepnie tylko FAKTYCZNE
            // rozjazdy trafiaja w addEntry/removeEntry (kazde to broadcast pakietu,
            // wiec liczba tych wywolan i tak zostaje minimalna - taka sama jak wczesniej).
            aktualizujTeam(swoi, docelowiSwoi);
            aktualizujTeam(obcy, docelowiObcy);
        }
    }

    /** Doprowadza zawartosc danego Team do dokladnie zbioru docelowychNickow - tylko realne roznice trafiaja w addEntry/removeEntry. */
    private void aktualizujTeam(Team team, java.util.Set<String> docelowychNickow) {
        java.util.Set<String> obecne = team.getEntries();
        for (String nick : obecne) {
            if (!docelowychNickow.contains(nick)) team.removeEntry(nick);
        }
        for (String nick : docelowychNickow) {
            if (!obecne.contains(nick)) team.addEntry(nick);
        }
    }

    private void przygotujScoreboard(Player gracz) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard tablica = gracz.getScoreboard();
        // Jesli gracz ma jeszcze glowna (wspoldzielona) tablice wynikow - dajemy mu WLASNA
        // kopie. To jest kluczowe: bez tego wszyscy widzieliby te same zespoly/kolory.
        if (tablica == null || tablica.equals(manager.getMainScoreboard())) {
            tablica = manager.getNewScoreboard();
            gracz.setScoreboard(tablica);
        }

        if (tablica.getTeam(TEAM_SWOI) == null) {
            Team swoi = tablica.registerNewTeam(TEAM_SWOI);
            swoi.setColor(ChatColor.GREEN);
        }
        if (tablica.getTeam(TEAM_OBCY) == null) {
            Team obcy = tablica.registerNewTeam(TEAM_OBCY);
            obcy.setColor(ChatColor.RED);
        }
    }
}
