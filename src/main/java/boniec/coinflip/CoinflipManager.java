package boniec.coinflip;

import boniec.dev.inventory.Main;
import boniec.galaktyczna.OdlamkiHelper;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CoinflipManager {

    public static final class Gra {
        public final UUID id;
        public final UUID host;
        public final String hostName;
        public final int stawka;
        public volatile boolean toczySie;

        Gra(UUID host, String hostName, int stawka) {
            this.id = UUID.randomUUID();
            this.host = host;
            this.hostName = hostName;
            this.stawka = stawka;
        }
    }

    private final JavaPlugin plugin;
    private final Main main;
    private final CoinflipDatabase baza;
    private final File plikCfg;
    private YamlConfiguration cfg;
    private final Map<UUID, Gra> gry = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> oczekujace = new ConcurrentHashMap<>();

    public CoinflipManager(Main main) {
        this.main = main;
        this.plugin = main;
        this.plikCfg = new File(plugin.getDataFolder(), "coinflip.yml");
        this.baza = new CoinflipDatabase(plugin);
        this.baza.open();
        if (!plikCfg.exists()) plugin.saveResource("coinflip.yml", false);
        this.cfg = YamlConfiguration.loadConfiguration(plikCfg);
        this.oczekujace.putAll(baza.wczytajOczekujace());
    }

    public void zamknij() {
        for (Gra gra : new ArrayList<>(gry.values())) {
            if (!gra.toczySie) {
                dodajOczekujace(gra.host, gra.stawka);
            }
        }
        gry.clear();
        for (Map.Entry<UUID, Integer> e : oczekujace.entrySet()) {
            baza.zapiszOczekujace(e.getKey(), e.getValue());
        }
        baza.close();
    }

    public YamlConfiguration cfg() {
        return cfg;
    }

    public List<Gra> listaGier() {
        List<Gra> out = new ArrayList<>();
        for (Gra g : gry.values()) {
            if (!g.toczySie) out.add(g);
        }
        return out;
    }

    public Gra gra(UUID id) {
        return gry.get(id);
    }

    public Gra graHosta(UUID host) {
        for (Gra g : gry.values()) {
            if (g.host.equals(host) && !g.toczySie) return g;
        }
        return null;
    }

    public int ileGierHosta(UUID host) {
        int n = 0;
        for (Gra g : gry.values()) {
            if (g.host.equals(host) && !g.toczySie) n++;
        }
        return n;
    }

    public int maxGierNaGracza() {
        return Math.max(1, cfg.getInt("max-gier-na-gracza", 3));
    }

    private boolean wWalce(UUID uuid) {
        var anty = main.getAntyLogoutManager();
        return anty != null && anty.isInCombat(uuid);
    }

    public int podatekProcent() {
        return Math.max(0, Math.min(50, cfg.getInt("podatek-procent", 5)));
    }

    public int minStawka() {
        return Math.max(1, cfg.getInt("min-stawka", 1));
    }

    public int maxStawka() {
        return Math.max(minStawka(), cfg.getInt("max-stawka", 1_000_000));
    }

    public int oczekujace(UUID uuid) {
        return oczekujace.getOrDefault(uuid, 0);
    }

    public void powiadomOczekujace(Player p) {
        int n = oczekujace(p.getUniqueId());
        if (n <= 0) return;
        p.sendMessage(msg("join-pending", "%ilosc%", String.valueOf(n)));
    }

    public boolean stworz(Player p, int stawka) {
        if (stawka < minStawka() || stawka > maxStawka()) {
            p.sendMessage(msg("zla-kwota",
                    "%min%", String.valueOf(minStawka()),
                    "%max%", String.valueOf(maxStawka())));
            return false;
        }
        int limit = maxGierNaGracza();
        if (ileGierHosta(p.getUniqueId()) >= limit) {
            p.sendMessage(msg("juz-masz-gre", "%limit%", String.valueOf(limit)));
            return false;
        }
        if (!OdlamkiHelper.maSzablon(main)) {
            p.sendMessage(msg("brak-szablonu"));
            return false;
        }
        int ma = OdlamkiHelper.policz(p, main);
        if (ma < stawka) {
            p.sendMessage(msg("brak-odlamkow",
                    "%masz%", String.valueOf(ma),
                    "%brak%", String.valueOf(stawka - ma)));
            return false;
        }
        if (!OdlamkiHelper.zabierz(p, main, stawka)) {
            p.sendMessage(msg("brak-odlamkow",
                    "%masz%", String.valueOf(ma),
                    "%brak%", String.valueOf(stawka)));
            return false;
        }
        Gra gra = new Gra(p.getUniqueId(), p.getName(), stawka);
        gry.put(gra.id, gra);
        p.sendMessage(msg("utworzono", "%stawka%", String.valueOf(stawka)));
        Bukkit.broadcastMessage(msg("broadcast-stworz",
                "%gracz%", p.getName(),
                "%stawka%", String.valueOf(stawka)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        return true;
    }

    public boolean anuluj(Player p, Gra gra) {
        if (gra == null || gra.toczySie || !gra.host.equals(p.getUniqueId())) return false;
        if (gry.remove(gra.id) == null) return false;
        wyplac(p, gra.stawka);
        p.sendMessage(msg("anulowano", "%stawka%", String.valueOf(gra.stawka)));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
        return true;
    }

    public boolean dolacz(Player p, Gra gra, CoinflipGui gui) {
        if (gra == null || gra.toczySie || !gry.containsKey(gra.id)) {
            p.sendMessage(msg("gra-zajeta"));
            return false;
        }
        if (gra.host.equals(p.getUniqueId())) {
            p.sendMessage(msg("wlasna-gra"));
            return false;
        }
        int ma = OdlamkiHelper.policz(p, main);
        if (ma < gra.stawka) {
            p.sendMessage(msg("brak-odlamkow",
                    "%masz%", String.valueOf(ma),
                    "%brak%", String.valueOf(gra.stawka - ma)));
            return false;
        }
        if (!OdlamkiHelper.zabierz(p, main, gra.stawka)) {
            p.sendMessage(msg("brak-odlamkow",
                    "%masz%", String.valueOf(ma),
                    "%brak%", String.valueOf(gra.stawka)));
            return false;
        }
        gra.toczySie = true;
        boolean skipAnim = wWalce(gra.host) || wWalce(p.getUniqueId());
        if (!skipAnim) {
            gui.otworzLosowanie(p, gra, p.getUniqueId());
            Player host = Bukkit.getPlayer(gra.host);
            if (host != null && host.isOnline() && !host.getUniqueId().equals(p.getUniqueId())) {
                gui.otworzLosowanie(host, gra, p.getUniqueId());
            }
        }
        startLosowanie(gra, p, skipAnim ? 1L : 55L);
        if (skipAnim) {
            gui.otworzListe(p);
        }
        return true;
    }

    private void startLosowanie(Gra gra, Player gosc, long delay) {
        boolean hostWygrywa = ThreadLocalRandom.current().nextBoolean();
        UUID zwyciezcaId = hostWygrywa ? gra.host : gosc.getUniqueId();
        String zwyciezcaNick = hostWygrywa ? gra.hostName : gosc.getName();
        String przegranyNick = hostWygrywa ? gosc.getName() : gra.hostName;
        int pot = gra.stawka * 2;
        final int wyplata = Math.max(1, (int) Math.floor(pot * (100 - podatekProcent()) / 100.0));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gry.remove(gra.id);
            Player win = Bukkit.getPlayer(zwyciezcaId);
            Player lose = Bukkit.getPlayer(hostWygrywa ? gosc.getUniqueId() : gra.host);
            wyplacLubOczekuj(zwyciezcaId, win, wyplata);
            String bc = msg("broadcast-wygrana",
                    "%zwyciezca%", zwyciezcaNick,
                    "%przegrany%", przegranyNick,
                    "%wygrana%", String.valueOf(wyplata),
                    "%podatek%", String.valueOf(podatekProcent()));
            Bukkit.broadcastMessage(bc);
            if (win != null && win.isOnline()) {
                win.sendMessage(msg("wygrana",
                        "%wygrana%", String.valueOf(wyplata),
                        "%podatek%", String.valueOf(podatekProcent())));
                win.playSound(win.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            }
            if (lose != null && lose.isOnline()) {
                lose.sendMessage(msg("przegrana", "%zwyciezca%", zwyciezcaNick));
                lose.playSound(lose.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
            }
        }, delay);
    }

    public void odbierz(Player p) {
        int n = oczekujace(p.getUniqueId());
        if (n <= 0) {
            p.sendMessage(msg("odbierz-brak"));
            return;
        }
        int weszlo = OdlamkiHelper.dajDoEq(p, main, n);
        int zostalo = n - weszlo;
        ustawOczekujace(p.getUniqueId(), zostalo);
        if (weszlo <= 0) {
            p.sendMessage(msg("odbierz-czesc", "%ilosc%", "0", "%zostalo%", String.valueOf(n)));
            return;
        }
        if (zostalo > 0) {
            p.sendMessage(msg("odbierz-czesc",
                    "%ilosc%", String.valueOf(weszlo),
                    "%zostalo%", String.valueOf(zostalo)));
        } else {
            p.sendMessage(msg("odbierz-ok", "%ilosc%", String.valueOf(weszlo)));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
    }

    private void wyplac(Player p, int ile) {
        int weszlo = OdlamkiHelper.dajDoEq(p, main, ile);
        if (weszlo < ile) dodajOczekujace(p.getUniqueId(), ile - weszlo);
    }

    private void wyplacLubOczekuj(UUID uuid, Player online, int ile) {
        if (online != null && online.isOnline()) {
            wyplac(online, ile);
        } else {
            dodajOczekujace(uuid, ile);
        }
    }

    private void dodajOczekujace(UUID uuid, int ile) {
        if (ile <= 0) return;
        ustawOczekujace(uuid, oczekujace(uuid) + ile);
    }

    private void ustawOczekujace(UUID uuid, int ile) {
        if (ile <= 0) {
            oczekujace.remove(uuid);
            baza.zapiszOczekujace(uuid, 0);
        } else {
            oczekujace.put(uuid, ile);
            baza.zapiszOczekujace(uuid, ile);
        }
    }

    public String msg(String klucz, String... pary) {
        String prefix = cfg.getString("wiadomosci.prefix", "");
        String tresc = cfg.getString("wiadomosci." + klucz, "");
        String s = prefix + tresc;
        for (int i = 0; i + 1 < pary.length; i += 2) {
            if (pary[i] != null && pary[i + 1] != null) {
                s = s.replace(pary[i], pary[i + 1]);
            }
        }
        return CoinflipText.kolor(s);
    }

    public String tytul(String klucz, String def) {
        return CoinflipText.kolor(cfg.getString(klucz, def));
    }
}
