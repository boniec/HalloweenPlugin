package boniec.serce;

import boniec.boss.BossbarUtil;
import boniec.code.core.WorldGuardHook;
import boniec.dev.inventory.Main;
import boniec.klany.util.Msg;
import boniec.scoreboard.SidebarManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Event "Serce Kosmosu" na regionie WorldGuard (domyslnie ID "serce", patrz serce.yml).
 * <p>
 * W pustej przestrzeni regionu losowo pojawiaja sie bloki (white_wool / glowstone / glass) z wlasnym HP.
 * Kazde uderzenie (proba zniszczenia) takiego bloku = 1 punkt dla gracza i -1 HP bloku oraz -1 HP serca.
 * Serce ma pulę HP (domyslnie 3000 = 20 blokow po 150 HP). Konczy sie, gdy HP serca spadnie do 0
 * albo po uplywie czasu (domyslnie 20 minut).
 * <p>
 * Stany: BEZCZYNNY -> TRWA -> PO_ZAKONCZENIU (scoreboard zostaje jeszcze X sekund) -> BEZCZYNNY.
 * Na czacie NIC sie nie wysyla - tylko bossbar, scoreboard, actionbar oraz title/subtitle na starcie i na koncu.
 */
public class SerceManager implements Listener, SidebarManager.Nadpisanie {

    public enum Stan { BEZCZYNNY, TRWA, PO_ZAKONCZENIU }

    private record Poz(int x, int y, int z) {}

    private static final class Blok {
        final Material typ;
        final int maxHp;
        int hp;

        Blok(Material typ, int hp) {
            this.typ = typ;
            this.hp = hp;
            this.maxHp = hp;
        }
    }

    private final Main plugin;
    private final SidebarManager sidebar;
    private final File cfgFile;
    private final File nagrodyFile;
    private final File pendingFile;
    private final File stanFile;
    private FileConfiguration cfg;

    public FileConfiguration getCfg() {
        return cfg;
    }

    private Stan stan = Stan.BEZCZYNNY;
    private long stanDo; // epoch ms: koniec eventu / koniec wyswietlania po zakonczeniu
    private boolean wynikZniszczone;

    private World swiat;
    private String region = "serce";
    private int[] granice; // minX, minY, minZ, maxX, maxY, maxZ
    private int hpMax;
    private int hp;
    private long nastepnyRespawn;
    private boolean ostrzezonoBrakMiejsca;

    private final Map<Poz, Blok> bloki = new HashMap<>();
    private List<Poz> kandydaci; // wszystkie wolne (powietrzne) pozycje w regionie; null = skan sie nie udal / za duzy region
    private final Map<UUID, Integer> punkty = new LinkedHashMap<>(); // kolejnosc wstawiania = kto pierwszy zdobyl punkt
    private final Map<UUID, String> nicki = new HashMap<>();
    private List<Map.Entry<UUID, Integer>> topCache = Collections.emptyList();
    private final List<List<ItemStack>> nagrody = new ArrayList<>(); // 3 listy: top1, top2, top3
    private final java.util.Set<Long> uzyteCele = new java.util.HashSet<>();

    private BossBar bar;
    private BukkitTask task;
    private SerceDropGui dropGui;

    // cache configu scoreboardu
    private String sbTytul = "&fSerce Kosmosu";
    private List<String> sbLinie = new ArrayList<>();
    private String czasTrwa = "";
    private String czasPoZniszczone = "";
    private String czasPoCzas = "";
    private String brakNick = "&8-";

    public SerceManager(Main plugin, SidebarManager sidebar) {
        this.plugin = plugin;
        this.sidebar = sidebar;
        this.cfgFile = new File(plugin.getDataFolder(), "serce.yml");
        this.nagrodyFile = new File(plugin.getDataFolder(), "serce-nagrody.yml");
        this.pendingFile = new File(plugin.getDataFolder(), "serce-oczekujace.yml");
        this.stanFile = new File(plugin.getDataFolder(), "serce-bloki.yml");
    }

    // ── Cykl zycia ────────────────────────────────────────────────────────

    public void start() {
        wczytaj();
        sprzatnijPozostalosci();
        dropGui = new SerceDropGui(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(dropGui, plugin);
        sidebar.dodajNadpisanie(this);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        if (!plugin.isWorldGuardEnabled()) {
            plugin.getLogger().warning("[Serce] WorldGuard nie jest zainstalowany - Serce Kosmosu nie zadziala.");
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        if (stan != Stan.BEZCZYNNY) {
            usunWszystkieBloki();
            usunBar();
            stan = Stan.BEZCZYNNY;
        }
        sidebar.usunNadpisanie(this);
    }

    public void przeladuj() {
        wczytaj();
    }

    private void wczytaj() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!cfgFile.exists()) plugin.saveResource("serce.yml", false);
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        sbTytul = cfg.getString("scoreboard.tytul", "&fSerce Kosmosu");
        sbLinie = new ArrayList<>(cfg.getStringList("scoreboard.linie"));
        czasTrwa = cfg.getString("scoreboard.czas-trwa", "&fKoniec za: &a%czas%");
        czasPoZniszczone = cfg.getString("scoreboard.czas-po-zniszczone", "&aSerce zniszczone!");
        czasPoCzas = cfg.getString("scoreboard.czas-po-czas", "&cCzas minal!");
        brakNick = cfg.getString("scoreboard.brak-gracza", "&8-");

        wczytajNagrody();
    }

    // ── Start / stop eventu ───────────────────────────────────────────────

    /** @return null jesli sie udalo, w przeciwnym razie klucz bledu z sekcji "wiadomosci" w serce.yml. */
    public String uruchom() {
        if (stan != Stan.BEZCZYNNY) return "juz-trwa";
        if (!plugin.isWorldGuardEnabled()) return "brak-wg";

        region = cfg.getString("region", "serce");
        World w = WorldGuardHook.findRegionWorld(region);
        int[] gr = w == null ? null : WorldGuardHook.getRegionBounds(w, region);
        if (w == null || gr == null) return "brak-regionu";

        swiat = w;
        granice = gr;
        hpMax = Math.max(1, cfg.getInt("serce-hp", 3000));
        hp = hpMax;
        bloki.clear();
        kandydaci = null;
        skanLogowany = false;
        nieudaneSpawny = 0;
        punkty.clear();
        nicki.clear();
        odswiezTop();
        ostrzezonoBrakMiejsca = false;
        wynikZniszczone = false;

        long teraz = System.currentTimeMillis();
        stan = Stan.TRWA;
        stanDo = teraz + Math.max(1, cfg.getInt("czas-trwania-sekundy", 1200)) * 1000L;
        nastepnyRespawn = teraz + Math.max(1, cfg.getInt("odstep-respawnu-sekundy", 5)) * 1000L;

        utworzBar();
        if (!(cfg.getBoolean("wypelnij-caly-region", true) && wypelnijRegion())) {
            int poczatkowo = Math.max(0, cfg.getInt("poczatkowa-ilosc-blokow", 5));
            for (int i = 0; i < poczatkowo; i++) {
                if (!sprobujSpawnu()) break;
            }
        }
        zapiszStanTeraz();

        wyslijTitle("title.start");
        return null;
    }

    /** Przerywa event bez nagrod i bez tytulow (/aserce despawn). @return false jesli nic nie trwalo. */
    public boolean przerwij() {
        if (stan == Stan.BEZCZYNNY) return false;
        usunWszystkieBloki();
        usunBar();
        stan = Stan.BEZCZYNNY;
        punkty.clear();
        nicki.clear();
        odswiezTop();
        return true;
    }

    private void zakoncz(boolean zniszczone) {
        if (stan != Stan.TRWA) return;
        stan = Stan.PO_ZAKONCZENIU;
        wynikZniszczone = zniszczone;
        stanDo = System.currentTimeMillis() + Math.max(0, cfg.getInt("scoreboard-po-zakonczeniu-sekundy", 10)) * 1000L;
        usunWszystkieBloki();
        usunBar();
        wyslijTitle(zniszczone ? "title.koniec-zniszczone" : "title.koniec-czas");

        if (zniszczone || cfg.getBoolean("nagrody-przy-timeout", true)) {
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
    }

    // ── Glowna petla (co sekunde) ─────────────────────────────────────────

    private void tick() {
        long teraz = System.currentTimeMillis();
        switch (stan) {
            case TRWA -> {
                if (teraz >= stanDo) {
                    zakoncz(false);
                    return;
                }
                int max = Math.max(1, cfg.getInt("max-blokow-naraz", 10));
                if (teraz >= nastepnyRespawn && bloki.size() < max && nieprzydzieloneHp() > 0) {
                    boolean ok = sprobujSpawnu();
                    nastepnyRespawn = teraz + Math.max(1, cfg.getInt("odstep-respawnu-sekundy", 5)) * 1000L;
                    // Zostalo HP serca, ale w regionie nie ma juz miejsca na kolejne bloki i nic nie stoi:
                    // nie ma czego bic, wiec serce uznajemy za zniszczone (zeby event sie nie zawiesil do konca czasu).
                    if (!ok && bloki.isEmpty()) {
                        if (++nieudaneSpawny >= 3) {
                            zakoncz(true);
                            return;
                        }
                    } else if (ok) {
                        nieudaneSpawny = 0;
                    }
                }
                if (zapisBrudny) zapiszStanTeraz();
                if (bar != null) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
                    }
                }
                odswiezBar();
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
            default -> {
            }
        }
    }

    private void sprawdzHarmonogram() {
        if (!plugin.isWorldGuardEnabled()) return;
        java.time.LocalDateTime teraz = boniec.eventy.EventHarmonogram.teraz();
        for (java.time.LocalTime t : boniec.eventy.EventHarmonogram.SERCE) {
            for (int dni = 0; dni <= 1; dni++) {
                java.time.LocalDateTime cel = java.time.LocalDateTime.of(teraz.toLocalDate().plusDays(dni), t);
                long sek = java.time.Duration.between(teraz, cel).getSeconds();
                boolean taMinuta = dni == 0 && t.equals(teraz.toLocalTime().withSecond(0).withNano(0));
                if ((sek >= 0 && sek <= 15) || taMinuta) {
                    long klucz = cel.atZone(boniec.eventy.EventHarmonogram.STREFA).toEpochSecond();
                    if (uzyteCele.add(klucz)) {
                        uruchom();
                        return;
                    }
                }
            }
        }
    }

    // ── Bloki ─────────────────────────────────────────────────────────────

    private int sumaHpBlokow() {
        int s = 0;
        for (Blok b : bloki.values()) s += b.hp;
        return s;
    }

    /** HP serca, ktore nie jest jeszcze "zapisane" w zadnym zywym bloku (czeka na kolejne spawny). */
    private int nieprzydzieloneHp() {
        return hp - sumaHpBlokow();
    }

    /**
     * Tryb "wypelnij-caly-region": kazde wolne miejsce (powietrze) w regionie dostaje blok, a cale HP serca jest
     * rozdzielone rowno miedzy te bloki (reszta z dzielenia trafia po 1 HP do pierwszych blokow).
     * @return false, jesli nie udalo sie wypelnic (np. region za duzy do skanu) - wtedy dziala zwykly spawn.
     */
    private boolean wypelnijRegion() {
        przeskanuj();
        if (kandydaci == null) {
            plugin.getLogger().warning("[Serce] Region '" + region + "' jest za duzy do skanu (max-objetosc-skanu) - "
                    + "pomijam wypelnianie calego regionu i uzywam zwyklego spawnu.");
            return false;
        }
        List<Poz> miejsca = new ArrayList<>(kandydaci.size());
        for (Poz p : kandydaci) {
            if (!swiat.getBlockAt(p.x(), p.y(), p.z()).getType().isAir()) continue;
            if (graczBlisko(p.x(), p.y(), p.z())) continue; // nie wmurowujemy graczy w bloki
            miejsca.add(p);
        }
        kandydaci = null; // pozniejsze skany (jesli beda potrzebne) zaczna od zera
        if (miejsca.isEmpty()) {
            plugin.getLogger().warning("[Serce] Region '" + region + "' nie ma zadnego wolnego miejsca na bloki.");
            return true; // nic do wypelnienia - event zakonczy sie przez reguly "brak miejsca"
        }

        Collections.shuffle(miejsca);
        int n = Math.min(miejsca.size(), hp); // kazdy blok musi miec min. 1 HP
        int baza = hp / n;
        int reszta = hp % n;
        for (int i = 0; i < n; i++) {
            Poz poz = miejsca.get(i);
            Material typ = losujTyp();
            if (typ == null) break;
            int blokHp = baza + (i < reszta ? 1 : 0);
            swiat.getBlockAt(poz.x(), poz.y(), poz.z()).setType(typ, false);
            bloki.put(poz, new Blok(typ, blokHp));
        }
        plugin.getLogger().info("[Serce] Wypelniono region blokami: " + bloki.size() + " (po ok. " + baza + " HP, razem " + hp + " HP).");
        return true;
    }

    /** @return true, jesli blok zostal zespawnowany. */
    private boolean sprobujSpawnu() {
        int wolne = nieprzydzieloneHp();
        if (wolne <= 0) return false;
        Poz poz = znajdzMiejsce();
        if (poz == null) {
            if (!ostrzezonoBrakMiejsca) {
                ostrzezonoBrakMiejsca = true;
                plugin.getLogger().warning("[Serce] Nie znaleziono pustego miejsca w regionie '" + region
                        + "' - sprobuje ponownie przy nastepnym respawnie.");
            }
            return false;
        }
        Material typ = losujTyp();
        if (typ == null) return false;

        // HP bloku losowo od "hp-bloku-min" do wartosci przypisanej typowi w sekcji "bloki"
        int maxHp = Math.max(1, cfg.getInt("bloki." + typ.name(), 150));
        int minHp = Math.min(maxHp, Math.max(1, cfg.getInt("hp-bloku-min", maxHp)));
        int losowe = minHp == maxHp ? maxHp : ThreadLocalRandom.current().nextInt(minHp, maxHp + 1);
        int blokHp = Math.min(losowe, wolne);

        swiat.getBlockAt(poz.x(), poz.y(), poz.z()).setType(typ, false);
        bloki.put(poz, new Blok(typ, blokHp));
        zapiszStan();
        return true;
    }

    private Material losujTyp() {
        var sekcja = cfg.getConfigurationSection("bloki");
        List<Material> typy = new ArrayList<>();
        if (sekcja != null) {
            for (String klucz : sekcja.getKeys(false)) {
                Material m = Material.matchMaterial(klucz);
                if (m != null && m.isBlock()) typy.add(m);
            }
        }
        if (typy.isEmpty()) {
            typy = List.of(Material.WHITE_WOOL, Material.GLOWSTONE, Material.GLASS);
        }
        return typy.get(ThreadLocalRandom.current().nextInt(typy.size()));
    }

    /** Skanuje CALY region i zapamietuje kazda wolna pozycje (powietrze), z ktorej losujemy miejsca spawnu. */
    private void przeskanuj() {
        long limit = Math.max(1000L, cfg.getLong("max-objetosc-skanu", 3_000_000L));
        List<int[]> lista = WorldGuardHook.listAirBlocks(swiat, region, limit);
        if (lista == null) {
            kandydaci = null;
            return;
        }
        kandydaci = new ArrayList<>(lista.size());
        for (int[] t : lista) kandydaci.add(new Poz(t[0], t[1], t[2]));
        if (!skanLogowany) {
            skanLogowany = true;
            plugin.getLogger().info("[Serce] Wolnych blokow w regionie '" + region + "': " + kandydaci.size());
        }
    }

    private boolean skanLogowany;
    private int nieudaneSpawny;
    private boolean zapisBrudny;

    private boolean pustyLubNasz(Block b) {
        return b.getType().isAir() || bloki.containsKey(new Poz(b.getX(), b.getY(), b.getZ()));
    }

    private boolean nadajeSie(Poz poz, boolean pustySasiedzi, boolean sprawdzRegion) {
        if (bloki.containsKey(poz)) return false;
        if (!swiat.isChunkLoaded(poz.x() >> 4, poz.z() >> 4)) return false;
        if (sprawdzRegion && !WorldGuardHook.containsBlock(swiat, region, poz.x(), poz.y(), poz.z())) return false;

        Block b = swiat.getBlockAt(poz.x(), poz.y(), poz.z());
        if (!b.getType().isAir()) return false;
        if (pustySasiedzi) {
            // sasiad moze byc powietrzem albo innym blokiem serca - nie moze byc scianami/podloga
            for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (!pustyLubNasz(b.getRelative(f))) return false;
            }
        }
        return !graczBlisko(poz.x(), poz.y(), poz.z());
    }

    /** Losuje wolne miejsce w regionie (tylko powietrze). */
    private Poz znajdzMiejsce() {
        if (swiat == null || granice == null) return null;
        boolean pustySasiedzi = cfg.getBoolean("wymagaj-pustych-sasiadow", false);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (kandydaci == null || kandydaci.isEmpty()) przeskanuj();

        if (kandydaci != null) {
            while (!kandydaci.isEmpty()) {
                int i = r.nextInt(kandydaci.size());
                Poz poz = kandydaci.get(i);
                Poz ostatni = kandydaci.remove(kandydaci.size() - 1);
                if (i < kandydaci.size()) kandydaci.set(i, ostatni);
                if (nadajeSie(poz, pustySasiedzi, false)) return poz;
            }
            return null;
        }

        // Region za duzy do skanu - losujemy punkty w prostopadloscianie regionu.
        for (int proba = 0; proba < 1000; proba++) {
            Poz poz = new Poz(r.nextInt(granice[0], granice[3] + 1),
                    r.nextInt(granice[1], granice[4] + 1),
                    r.nextInt(granice[2], granice[5] + 1));
            if (nadajeSie(poz, pustySasiedzi, true)) return poz;
        }
        return null;
    }

    private boolean graczBlisko(int x, int y, int z) {
        for (Player p : swiat.getPlayers()) {
            Location l = p.getLocation();
            if (Math.abs(l.getX() - (x + 0.5)) < 0.9 && Math.abs(l.getZ() - (z + 0.5)) < 0.9
                    && l.getY() < y + 1 && l.getY() + 1.8 > y) {
                return true;
            }
        }
        return false;
    }

    private void usunBlok(Poz poz) {
        Blok blok = bloki.remove(poz);
        if (blok == null || swiat == null) return;
        Block b = swiat.getBlockAt(poz.x(), poz.y(), poz.z());
        if (b.getType() == blok.typ) b.setType(Material.AIR, false);
        zapiszStan();
    }

    private void usunWszystkieBloki() {
        if (swiat != null) {
            for (Map.Entry<Poz, Blok> e : bloki.entrySet()) {
                Poz poz = e.getKey();
                Block b = swiat.getBlockAt(poz.x(), poz.y(), poz.z());
                if (b.getType() == e.getValue().typ) b.setType(Material.AIR, false);
            }
        }
        bloki.clear();
        zapiszStanTeraz();
    }

    // Zapis pozycji aktywnych blokow - jesli serwer padnie w trakcie eventu, przy starcie je sprzatniemy.
    private void zapiszStan() {
        zapisBrudny = true;
    }

    private void zapiszStanTeraz() {
        zapisBrudny = false;
        if (bloki.isEmpty() || swiat == null) {
            if (stanFile.exists()) stanFile.delete();
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("swiat", swiat.getName());
        List<String> lista = new ArrayList<>();
        for (Map.Entry<Poz, Blok> e : bloki.entrySet()) {
            Poz p = e.getKey();
            lista.add(p.x() + "," + p.y() + "," + p.z() + "," + e.getValue().typ.name());
        }
        y.set("bloki", lista);
        try {
            y.save(stanFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Serce] Nie udalo sie zapisac " + stanFile.getName() + ": " + ex.getMessage());
        }
    }

    private void sprzatnijPozostalosci() {
        if (!stanFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(stanFile);
        World w = Bukkit.getWorld(y.getString("swiat", ""));
        if (w != null) {
            for (String s : y.getStringList("bloki")) {
                String[] t = s.split(",");
                if (t.length < 4) continue;
                try {
                    Block b = w.getBlockAt(Integer.parseInt(t[0].trim()), Integer.parseInt(t[1].trim()), Integer.parseInt(t[2].trim()));
                    Material m = Material.matchMaterial(t[3].trim());
                    if (m != null && b.getType() == m) b.setType(Material.AIR, false);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        stanFile.delete();
    }

    private boolean tracked(Block b) {
        return !bloki.isEmpty() && b.getWorld().equals(swiat) && bloki.containsKey(new Poz(b.getX(), b.getY(), b.getZ()));
    }

    // ── Trafienia ─────────────────────────────────────────────────────────

    // HIGHEST + ignoreCancelled=false: obslugujemy blok serca nawet jesli WorldGuard zablokowal budowanie na regionie.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (bloki.isEmpty()) return;
        Block b = e.getBlock();
        if (!b.getWorld().equals(swiat)) return;
        Poz poz = new Poz(b.getX(), b.getY(), b.getZ());
        Blok blok = bloki.get(poz);
        if (blok == null) return;

        e.setCancelled(true);
        e.setDropItems(false);
        e.setExpToDrop(0);

        Player p = e.getPlayer();
        GameMode gm = p.getGameMode();
        boolean liczy = gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE
                || (gm == GameMode.CREATIVE && cfg.getBoolean("liczy-tryb-creative", false));
        if (!liczy || stan != Stan.TRWA) return;
        trafienie(p, poz, blok);
    }

    private void trafienie(Player p, Poz poz, Blok blok) {
        blok.hp -= 1;
        hp = Math.max(0, hp - 1);

        punkty.merge(p.getUniqueId(), Math.max(1, cfg.getInt("punkty-za-trafienie", 1)), Integer::sum);
        nicki.put(p.getUniqueId(), p.getName());
        odswiezTop();

        String dzwiek = cfg.getString("dzwiek-trafienia", "block.amethyst_block.hit");
        if (dzwiek != null && !dzwiek.isEmpty()) {
            p.playSound(p.getLocation(), dzwiek, 0.6f, 1.4f);
        }
        String ab = cfg.getString("actionbar", "");
        if (ab != null && !ab.isEmpty()) {
            String txt = Msg.color(ab
                    .replace("%blok_hp%", String.valueOf(Math.max(0, blok.hp)))
                    .replace("%blok_max%", String.valueOf(blok.maxHp))
                    .replace("%hp%", String.valueOf(hp))
                    .replace("%hp_max%", String.valueOf(hpMax))
                    .replace("%twoje_punkty%", String.valueOf(punkty.getOrDefault(p.getUniqueId(), 0))));
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(txt));
        }

        if (blok.hp <= 0) usunBlok(poz);
        odswiezBar();
        if (hp <= 0) zakoncz(true);
    }

    // ── Ochrona blokow serca przed innymi sposobami zniszczenia ───────────

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!bloki.isEmpty()) e.blockList().removeIf(this::tracked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!bloki.isEmpty()) e.blockList().removeIf(this::tracked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (tracked(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (tracked(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (bloki.isEmpty()) return;
        for (Block b : e.getBlocks()) {
            if (tracked(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (bloki.isEmpty()) return;
        for (Block b : e.getBlocks()) {
            if (tracked(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ── Bossbar ───────────────────────────────────────────────────────────

    private void utworzBar() {
        usunBar();
        BarColor kolor = BarColor.PURPLE;
        BarStyle styl = BarStyle.SOLID;
        try {
            kolor = BarColor.valueOf(cfg.getString("bossbar.kolor", "PURPLE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        try {
            styl = BarStyle.valueOf(cfg.getString("bossbar.styl", "SOLID").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        bar = BossbarUtil.utworz(plugin, "", kolor, styl);
        odswiezBar();
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
    }

    private void usunBar() {
        if (bar != null) {
            BossbarUtil.usun(bar);
            bar = null;
        }
    }

    private void odswiezBar() {
        if (bar == null) return;
        String tytul = cfg.getString("bossbar.tytul", "&dSerce Kosmosu &8- &f%hp%&7/&f%hp_max% HP");
        bar.setTitle(Msg.color(tytul
                .replace("%hp%", String.valueOf(hp))
                .replace("%hp_max%", String.valueOf(hpMax))
                .replace("%proc%", String.valueOf(procentHp()))));
        double postep = hpMax <= 0 ? 0.0 : (double) hp / hpMax;
        bar.setProgress(Math.max(0.0, Math.min(1.0, postep)));
    }

    private int procentHp() {
        return hpMax <= 0 ? 0 : (int) Math.ceil(hp * 100.0 / hpMax);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (bar != null) bar.addPlayer(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) wydajOczekujace(p);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (bar != null) bar.removePlayer(e.getPlayer());
    }

    // ── Ranking ───────────────────────────────────────────────────────────

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
        String czas = formatCzas(stan == Stan.TRWA ? sekundyDo() : 0);
        String wierszCzasu = stan == Stan.TRWA
                ? czasTrwa
                : (wynikZniszczone ? czasPoZniszczone : czasPoCzas);
        wierszCzasu = wierszCzasu.replace("%czas%", czas);

        List<String> wynik = new ArrayList<>(sbLinie.size());
        List<Map.Entry<UUID, Integer>> top = topCache;
        for (String linia : sbLinie) {
            String s = linia.replace("%czas_linia%", wierszCzasu)
                    .replace("%czas%", czas)
                    .replace("%hp%", String.valueOf(hp))
                    .replace("%hp_max%", String.valueOf(hpMax))
                    .replace("%proc%", String.valueOf(procentHp()))
                    .replace("%bloki%", String.valueOf(bloki.size()))
                    .replace("%twoje_punkty%", String.valueOf(punkty.getOrDefault(p.getUniqueId(), 0)));
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

    // ── Status (do /aserce status) ────────────────────────────────────────

    public Stan getStan() {
        return stan;
    }

    public String czasPozostaly() {
        return formatCzas(sekundyDo());
    }

    public List<String> status() {
        List<String> out = new ArrayList<>();
        String nazwa = switch (stan) {
            case BEZCZYNNY -> "&7nieaktywne";
            case TRWA -> "&atrwa (koniec za " + formatCzas(sekundyDo()) + ")";
            case PO_ZAKONCZENIU -> "&6zakonczone (scoreboard znika za " + formatCzas(sekundyDo()) + ")";
        };
        out.add(Msg.color("&8[&dSerce&8] &fStan: " + nazwa));
        if (stan == Stan.TRWA) {
            out.add(Msg.color("&8[&dSerce&8] &fHP serca: &c" + hp + "&7/&c" + hpMax
                    + " &8(&f" + procentHp() + "%&8) &7| aktywne bloki: &f" + bloki.size()
                    + " &7| do zespawnowania: &f" + Math.max(0, nieprzydzieloneHp()) + " HP"));
        }
        List<Map.Entry<UUID, Integer>> top = topCache;
        for (int i = 0; i < top.size(); i++) {
            out.add(Msg.color("&8[&dSerce&8] &f#" + (i + 1) + " " + nicki.getOrDefault(top.get(i).getKey(), "?")
                    + " &8- &a" + top.get(i).getValue() + "pkt"));
        }
        return out;
    }

    // ── Wiadomosci / nagrody ──────────────────────────────────────────────

    public SerceDropGui getDropGui() {
        return dropGui;
    }

    public String msg(String klucz) {
        String domyslna = klucz.equals("brak-regionu")
                ? "&cRegion WorldGuard &e%region% &cnie istnieje - Serce Kosmosu nie moze wystartowac."
                : "&c[brak wiadomosci: " + klucz + "]";
        return Msg.color(cfg.getString("wiadomosci." + klucz, domyslna)
                .replace("%region%", cfg.getString("region", "serce")));
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
            plugin.getLogger().warning("[Serce] Nie udalo sie zapisac " + nagrodyFile.getName() + ": " + e.getMessage());
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
            plugin.getLogger().warning("[Serce] Nie udalo sie zapisac " + pendingFile.getName() + ": " + e.getMessage());
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
            plugin.getLogger().warning("[Serce] Nie udalo sie zapisac " + pendingFile.getName() + ": " + e.getMessage());
            return; // nie wydajemy, zeby nie zdublowac przy nastepnym wejsciu
        }
        daj(p, lista);
    }
}
