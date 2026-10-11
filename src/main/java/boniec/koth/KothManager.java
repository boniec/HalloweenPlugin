package boniec.koth;

import boniec.code.core.WorldGuardHook;
import boniec.dev.inventory.Main;
import boniec.klany.util.Msg;
import boniec.scoreboard.SidebarManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KOTH na regionie WorldGuard (domyslnie o ID "koth", patrz koth.yml).
 * <p>
 * Stany: BEZCZYNNY -> ODLICZANIE (scoreboard "start za") -> TRWA (scoreboard "koniec za")
 * -> PO_ZAKONCZENIU (scoreboard zostaje jeszcze X sekund) -> BEZCZYNNY.
 * Na czacie NIC sie nie wysyla - tylko title/subtitle na starcie i na koncu.
 * Scoreboard jest podmieniany przez SidebarManager.Nadpisanie.
 */
public class KothManager implements Listener, SidebarManager.Nadpisanie {

    public enum Stan { BEZCZYNNY, ODLICZANIE, TRWA, PO_ZAKONCZENIU }

    private final Main plugin;
    private final SidebarManager sidebar;
    private final File cfgFile;
    private final File nagrodyFile;
    private final File pendingFile;
    private FileConfiguration cfg;

    public FileConfiguration getCfg() {
        return cfg;
    }

    private Stan stan = Stan.BEZCZYNNY;
    private long stanDo; // epoch ms: koniec odliczania / koniec KOTH-a / koniec wyswietlania po zakonczeniu

    private final Map<UUID, Integer> punkty = new LinkedHashMap<>(); // kolejnosc wstawiania = kto pierwszy zdobyl punkt
    private final Map<UUID, String> nicki = new HashMap<>();
    private List<Map.Entry<UUID, Integer>> topCache = Collections.emptyList();

    private final List<List<ItemStack>> nagrody = new ArrayList<>(); // 3 listy: top1, top2, top3
    private final Set<Long> uzyteCele = new HashSet<>();

    private BukkitTask task;
    private KothDropGui dropGui;

    // cache configu scoreboardu
    private String sbTytul = "&fKOTH";
    private List<String> sbLinie = new ArrayList<>();
    private String czasPrzed = "";
    private String czasTrwa = "";
    private String czasPo = "";
    private String brakNick = "&8-";

    public KothManager(Main plugin, SidebarManager sidebar) {
        this.plugin = plugin;
        this.sidebar = sidebar;
        this.cfgFile = new File(plugin.getDataFolder(), "koth.yml");
        this.nagrodyFile = new File(plugin.getDataFolder(), "koth-nagrody.yml");
        this.pendingFile = new File(plugin.getDataFolder(), "koth-oczekujace.yml");
    }

    // ── Cykl zycia ────────────────────────────────────────────────────────

    public void start() {
        wczytaj();
        dropGui = new KothDropGui(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(dropGui, plugin);
        sidebar.setNadpisanie(this);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        if (!plugin.isWorldGuardEnabled()) {
            plugin.getLogger().warning("[KOTH] WorldGuard nie jest zainstalowany - KOTH nie bedzie liczyl punktow.");
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        sidebar.setNadpisanie(null);
    }

    public void przeladuj() {
        wczytaj();
    }

    private void wczytaj() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!cfgFile.exists()) plugin.saveResource("koth.yml", false);
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        sbTytul = cfg.getString("scoreboard.tytul", "&fKOTH");
        sbLinie = new ArrayList<>(cfg.getStringList("scoreboard.linie"));
        czasPrzed = cfg.getString("scoreboard.czas-przed", "&fStart za: &a%czas%");
        czasTrwa = cfg.getString("scoreboard.czas-trwa", "&fKoniec za: &a%czas%");
        czasPo = cfg.getString("scoreboard.czas-po", "&fKoniec za: &a%czas%");
        brakNick = cfg.getString("scoreboard.brak-gracza", "&8-");

        wczytajNagrody();
    }

    // ── Glowna petla (co sekunde) ─────────────────────────────────────────

    private void tick() {
        long teraz = System.currentTimeMillis();
        switch (stan) {
            case ODLICZANIE -> {
                if (teraz >= stanDo) rozpocznij();
            }
            case TRWA -> {
                naliczPunkty();
                if (teraz >= stanDo) zakoncz();
            }
            case PO_ZAKONCZENIU -> {
                if (teraz >= stanDo) {
                    stan = Stan.BEZCZYNNY;
                    punkty.clear();
                    nicki.clear();
                    odswiezTop();
                }
            }
            case BEZCZYNNY -> sprawdzHarmonogram();
        }
    }

    /** @return null jesli sie udalo, w przeciwnym razie klucz bledu z sekcji "wiadomosci" w koth.yml. */
    public String uruchom(int odliczanieSekundy) {
        if (stan != Stan.BEZCZYNNY) return "juz-trwa";
        if (!plugin.isWorldGuardEnabled()) return "brak-wg";
        if (!WorldGuardHook.regionExistsAnywhere(cfg.getString("region", "koth"))) return "brak-regionu";
        zapowiedz(odliczanieSekundy);
        return null;
    }

    private void zapowiedz(int sekundy) {
        punkty.clear();
        nicki.clear();
        odswiezTop();
        if (sekundy <= 0) {
            rozpocznij();
            return;
        }
        stan = Stan.ODLICZANIE;
        stanDo = System.currentTimeMillis() + sekundy * 1000L;
    }

    private void rozpocznij() {
        stan = Stan.TRWA;
        stanDo = System.currentTimeMillis() + Math.max(1, cfg.getInt("czas-trwania-sekundy", 300)) * 1000L;
        punkty.clear();
        nicki.clear();
        odswiezTop();
        wyslijTitle("title.start");
    }

    private void zakoncz() {
        stan = Stan.PO_ZAKONCZENIU;
        stanDo = System.currentTimeMillis() + Math.max(0, cfg.getInt("scoreboard-po-zakonczeniu-sekundy", 10)) * 1000L;
        wyslijTitle("title.koniec");

        // nagrody dla top 3 (tylko gracze, ktorzy zdobyli jakies punkty)
        List<Map.Entry<UUID, Integer>> top = topCache;
        for (int i = 0; i < top.size() && i < 3; i++) {
            List<ItemStack> items = nagrody.size() > i ? nagrody.get(i) : Collections.emptyList();
            if (items.isEmpty()) continue;
            UUID uuid = top.get(i).getKey();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) daj(p, items);
            else dodajOczekujace(uuid, items);
        }
    }

    /** Przerywa KOTH bez nagrod i bez tytulow (/adkoth stop). @return false jesli nic nie trwalo. */
    public boolean przerwij() {
        if (stan == Stan.BEZCZYNNY) return false;
        stan = Stan.BEZCZYNNY;
        punkty.clear();
        nicki.clear();
        odswiezTop();
        return true;
    }

    private void naliczPunkty() {
        if (!plugin.isWorldGuardEnabled()) return;
        String region = cfg.getString("region", "koth");
        int ile = Math.max(1, cfg.getInt("punkty-na-sekunde", 1));
        for (Player p : Bukkit.getOnlinePlayers()) {
            GameMode gm = p.getGameMode();
            if (p.isDead() || (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE)) continue;
            if (!WorldGuardHook.isStandingInRegion(p.getLocation(), region)) continue;
            punkty.merge(p.getUniqueId(), ile, Integer::sum);
            nicki.put(p.getUniqueId(), p.getName());
        }
        odswiezTop();
    }

    private void odswiezTop() {
        List<Map.Entry<UUID, Integer>> lista = new ArrayList<>(punkty.entrySet());
        lista.removeIf(e -> e.getValue() <= 0);
        // sort stabilny: remis wygrywa ten, kto pierwszy zdobyl punkt
        lista.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>();
        for (int i = 0; i < lista.size() && i < 3; i++) {
            top.add(Map.entry(lista.get(i).getKey(), lista.get(i).getValue()));
        }
        topCache = top;
    }

    private void wyslijTitle(String sciezka) {
        String tytul = Msg.color(cfg.getString(sciezka + ".tytul", ""));
        String podtytul = Msg.color(cfg.getString(sciezka + ".podtytul", ""));
        int in = cfg.getInt("title.fade-in-ticks", 10);
        int stay = cfg.getInt("title.stay-ticks", 60);
        int out = cfg.getInt("title.fade-out-ticks", 20);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(tytul, podtytul, in, stay, out);
        }
    }

    // ── Harmonogram (opcjonalny) ──────────────────────────────────────────

    private void sprawdzHarmonogram() {
        if (!plugin.isWorldGuardEnabled()) return;
        int zap = Math.max(5, cfg.getInt("zapowiedz-przed-startem-sekundy", 300));
        LocalDateTime teraz = boniec.eventy.EventHarmonogram.teraz();
        for (LocalTime t : boniec.eventy.EventHarmonogram.KOTH) {
            for (int dni = 0; dni <= 1; dni++) {
                LocalDateTime cel = LocalDateTime.of(teraz.toLocalDate().plusDays(dni), t);
                long sek = Duration.between(teraz, cel).getSeconds();
                boolean taMinuta = dni == 0 && t.equals(teraz.toLocalTime().withSecond(0).withNano(0));
                if ((sek >= 0 && sek <= zap) || taMinuta) {
                    long klucz = cel.atZone(boniec.eventy.EventHarmonogram.STREFA).toEpochSecond();
                    if (uzyteCele.add(klucz)) {
                        zapowiedz(taMinuta && sek <= 0 ? 0 : (int) Math.max(0, sek));
                        return;
                    }
                }
            }
        }
    }

    // ── Scoreboard (SidebarManager.Nadpisanie) ────────────────────────────

    @Override
    public boolean aktywne() {
        return stan != Stan.BEZCZYNNY;
    }

    @Override
    public String tytul() {
        return sbTytul;
    }

    @Override
    public List<String> linie(Player p) {
        String czas = formatCzas(stan == Stan.PO_ZAKONCZENIU ? 0 : sekundyDo());
        String wierszCzasu = switch (stan) {
            case ODLICZANIE -> czasPrzed;
            case TRWA -> czasTrwa;
            default -> czasPo;
        };
        wierszCzasu = wierszCzasu.replace("%czas%", czas);

        List<String> wynik = new ArrayList<>(sbLinie.size());
        List<Map.Entry<UUID, Integer>> top = topCache;
        for (String linia : sbLinie) {
            String s = linia.replace("%czas_linia%", wierszCzasu).replace("%czas%", czas);
            for (int i = 0; i < 3; i++) {
                String nick = brakNick;
                int pkt = 0;
                if (i < top.size()) {
                    nick = nicki.getOrDefault(top.get(i).getKey(), "?");
                    pkt = top.get(i).getValue();
                }
                s = s.replace("%nick_" + (i + 1) + "%", nick)
                        .replace("%punkty_" + (i + 1) + "%", String.valueOf(pkt));
            }
            wynik.add(s);
        }
        return wynik;
    }

    private long sekundyDo() {
        return Math.max(0L, (stanDo - System.currentTimeMillis() + 999L) / 1000L);
    }

    private static String formatCzas(long sek) {
        long m = sek / 60;
        long s = sek % 60;
        if (m >= 60) return String.format(Locale.ROOT, "%d:%02d:%02d", m / 60, m % 60, s);
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    // ── Status (do /adkoth status) ────────────────────────────────────────

    public Stan getStan() {
        return stan;
    }

    public String czasPozostaly() {
        return formatCzas(sekundyDo());
    }

    public List<String> status() {
        List<String> out = new ArrayList<>();
        String nazwa = switch (stan) {
            case BEZCZYNNY -> "&7bezczynny";
            case ODLICZANIE -> "&eodliczanie do startu (" + formatCzas(sekundyDo()) + ")";
            case TRWA -> "&atrwa (koniec za " + formatCzas(sekundyDo()) + ")";
            case PO_ZAKONCZENIU -> "&6zakonczony (scoreboard znika za " + formatCzas(sekundyDo()) + ")";
        };
        out.add(Msg.color("&8[&cKOTH&8] &fStan: " + nazwa));
        List<Map.Entry<UUID, Integer>> top = topCache;
        for (int i = 0; i < top.size(); i++) {
            out.add(Msg.color("&8[&cKOTH&8] &f#" + (i + 1) + " " + nicki.getOrDefault(top.get(i).getKey(), "?")
                    + " &8- &a" + top.get(i).getValue() + "pkt"));
        }
        return out;
    }

    // ── Nagrody ───────────────────────────────────────────────────────────

    public KothDropGui getDropGui() {
        return dropGui;
    }

    public String msg(String klucz) {
        String domyslna = klucz.equals("brak-regionu")
                ? "&cRegion WorldGuard &e%region% &cnie istnieje - KOTH nie moze liczyc punktow."
                : "&c[brak wiadomosci: " + klucz + "]";
        return Msg.color(cfg.getString("wiadomosci." + klucz, domyslna)
                .replace("%region%", cfg.getString("region", "koth")));
    }

    public int domyslneOdliczanie() {
        return cfg.getInt("domyslne-odliczanie-sekundy", 60);
    }

    /** Kopia nagrod za dane miejsce (1-3). */
    public List<ItemStack> getNagrody(int miejsce) {
        List<ItemStack> kopia = new ArrayList<>();
        if (miejsce < 1 || miejsce > nagrody.size()) return kopia;
        for (ItemStack it : nagrody.get(miejsce - 1)) kopia.add(it.clone());
        return kopia;
    }

    public void zapiszNagrody(List<List<ItemStack>> nowe) {
        nagrody.clear();
        YamlConfiguration y = new YamlConfiguration();
        for (int i = 0; i < 3; i++) {
            List<ItemStack> l = i < nowe.size() ? nowe.get(i) : new ArrayList<>();
            nagrody.add(new ArrayList<>(l));
            y.set("top" + (i + 1), l);
        }
        try {
            y.save(nagrodyFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[KOTH] Nie udalo sie zapisac " + nagrodyFile.getName() + ": " + e.getMessage());
        }
    }

    private void wczytajNagrody() {
        nagrody.clear();
        YamlConfiguration y = nagrodyFile.exists() ? YamlConfiguration.loadConfiguration(nagrodyFile) : new YamlConfiguration();
        for (int i = 1; i <= 3; i++) {
            List<ItemStack> l = new ArrayList<>();
            for (Object o : y.getList("top" + i, Collections.emptyList())) {
                if (o instanceof ItemStack it) l.add(it);
            }
            nagrody.add(l);
        }
    }

    private void daj(Player p, List<ItemStack> items) {
        for (ItemStack it : items) {
            Map<Integer, ItemStack> nadmiar = p.getInventory().addItem(it.clone());
            for (ItemStack reszta : nadmiar.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), reszta);
            }
        }
    }

    // Nagrody dla zwyciezcow, ktorzy w chwili konca byli offline - dostana je po wejsciu na serwer.
    private void dodajOczekujace(UUID uuid, List<ItemStack> items) {
        YamlConfiguration y = pendingFile.exists() ? YamlConfiguration.loadConfiguration(pendingFile) : new YamlConfiguration();
        String sciezka = "oczekujace." + uuid;
        List<ItemStack> lista = new ArrayList<>();
        for (Object o : y.getList(sciezka, Collections.emptyList())) {
            if (o instanceof ItemStack it) lista.add(it);
        }
        for (ItemStack it : items) lista.add(it.clone());
        y.set(sciezka, lista);
        try {
            y.save(pendingFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[KOTH] Nie udalo sie zapisac " + pendingFile.getName() + ": " + e.getMessage());
        }
    }

    private void wydajOczekujace(Player p) {
        if (!pendingFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(pendingFile);
        String sciezka = "oczekujace." + p.getUniqueId();
        if (!y.contains(sciezka)) return;
        List<ItemStack> lista = new ArrayList<>();
        for (Object o : y.getList(sciezka, Collections.emptyList())) {
            if (o instanceof ItemStack it) lista.add(it);
        }
        y.set(sciezka, null);
        try {
            y.save(pendingFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[KOTH] Nie udalo sie zapisac " + pendingFile.getName() + ": " + e.getMessage());
            return; // nie wydajemy, zeby nie zdublowac przy nastepnym wejsciu
        }
        daj(p, lista);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) wydajOczekujace(p);
        }, 40L);
    }
}
