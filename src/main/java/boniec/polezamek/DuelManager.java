package boniec.dev.polezamek;

import boniec.adminutil.AdminUtilManager;
import boniec.dev.polezamek.eventitems.KozaArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DuelManager implements Listener {

    static final String PREFIX = "&8[&x&F&F&5&5&5&5ᴅᴜᴇʟ&8] &r";

    private static DuelManager instancja;

    private final JavaPlugin host;
    private final KozaArenaManager koza;
    private FileConfiguration cfg;
    private File plik;

    private final Map<UUID, UUID> oczekuje = new ConcurrentHashMap<>();
    /** Gracz -> aktywna walka (wiele dueli naraz na tej samej arenie, izolacja hide/damage). */
    private final Map<UUID, Walka> walki = new ConcurrentHashMap<>();

    private long eventDo;
    private int eventTotal;
    private BossBar eventBar;
    private BukkitTask eventTick;

    public DuelManager(Main plugin, KozaArenaManager koza) {
        this.host = plugin.getHost();
        this.koza = koza;
        instancja = this;
        wczytaj();
    }

    public static DuelManager get() {
        return instancja;
    }

    public static boolean blokujeSpawn(UUID uuid) {
        return instancja != null && instancja.wDuele(uuid);
    }

    /** true = nie mozna uzyc eventowki miedzy tymi graczami (inny duel / tylko jeden w duelu). */
    public static boolean izolujEventowki(UUID uzytkownik, UUID cel) {
        DuelManager dm = instancja;
        if (dm == null || cel == null) return false;
        return dm.blokujeMiedzyDuelami(uzytkownik, cel);
    }

    public boolean wDuele(UUID uuid) {
        return walkaGracza(uuid) != null;
    }

    private Walka walkaGracza(UUID uuid) {
        Walka w = walki.get(uuid);
        if (w == null || !w.zyje) return null;
        if (!w.uczestnik(uuid)) return null;
        if (w.wyszedl != null && w.wyszedl.equals(uuid)) return null;
        return w;
    }

    private boolean taSamaWalka(UUID a, UUID b) {
        Walka wa = walkaGracza(a);
        Walka wb = walkaGracza(b);
        return wa != null && wa == wb;
    }

    private boolean blokujeMiedzyDuelami(UUID a, UUID b) {
        Walka wa = walkaGracza(a);
        Walka wb = walkaGracza(b);
        if (wa == null && wb == null) return false;
        return wa == null || wb == null || wa != wb;
    }

    public boolean zajety(UUID uuid) {
        if (koza != null && koza.wArenie(uuid)) return true;
        return wDuele(uuid);
    }

    boolean eventOn() {
        return System.currentTimeMillis() < eventDo;
    }

    void wczytaj() {
        if (!host.getDataFolder().exists()) host.getDataFolder().mkdirs();
        plik = new File(host.getDataFolder(), "duel.yml");
        if (!plik.exists()) host.saveResource("duel.yml", false);
        cfg = YamlConfiguration.loadConfiguration(plik);
    }

    String cfgStr(String path, String def) {
        return cfg.getString(path, def);
    }

    void msgSender(CommandSender s, String text) {
        s.sendMessage(color(text));
    }

    private void zapisz() {
        try {
            cfg.save(plik);
        } catch (IOException e) {
            host.getLogger().warning("Nie zapisano duel.yml: " + e.getMessage());
        }
    }

    boolean ustawSwiat(Player p) {
        cfg.set("swiat", p.getWorld().getName());
        zapisz();
        return true;
    }

    boolean ustawLokacje(Player p, int nr) {
        Location loc = p.getLocation();
        cfg.set("swiat", loc.getWorld().getName());
        String k = nr == 1 ? "lokacja-1" : "lokacja-2";
        cfg.set(k + ".x", loc.getX());
        cfg.set(k + ".y", loc.getY());
        cfg.set(k + ".z", loc.getZ());
        cfg.set(k + ".yaw", loc.getYaw());
        cfg.set(k + ".pitch", loc.getPitch());
        zapisz();
        return true;
    }

    Location lokacja(int nr) {
        World world = swiat();
        if (world == null) return null;
        String k = nr == 1 ? "lokacja-1" : "lokacja-2";
        if (!cfg.contains(k + ".x")) return null;
        return new Location(world,
                cfg.getDouble(k + ".x"),
                cfg.getDouble(k + ".y"),
                cfg.getDouble(k + ".z"),
                (float) cfg.getDouble(k + ".yaw"),
                (float) cfg.getDouble(k + ".pitch"));
    }

    World swiat() {
        String n = cfg.getString("swiat", "");
        if (n == null || n.isBlank()) return null;
        return Bukkit.getWorld(n);
    }

    boolean arenaGotowa() {
        return lokacja(1) != null && lokacja(2) != null;
    }

    void startEvent(int sekundy) {
        eventTotal = Math.max(1, sekundy);
        eventDo = System.currentTimeMillis() + eventTotal * 1000L;
        if (eventBar == null) {
            eventBar = Bukkit.createBossBar(color(eventTitle()), barKolor(), BarStyle.SOLID);
        }
        eventBar.setColor(barKolor());
        eventBar.setStyle(BarStyle.SOLID);
        eventBar.setProgress(1.0);
        eventBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!wDuele(p.getUniqueId())) eventBar.addPlayer(p);
        }
        if (eventTick != null) eventTick.cancel();
        eventTick = Bukkit.getScheduler().runTaskTimer(host, this::tickEvent, 2L, 2L);
        broadcast(cfg.getString("wiadomosci.event-start", "&aEvent duel wlaczony na &f{czas}&a.")
                .replace("{czas}", formatCzas(sekundy)));
    }

    void stopEvent() {
        eventDo = 0;
        if (eventTick != null) {
            eventTick.cancel();
            eventTick = null;
        }
        if (eventBar != null) {
            eventBar.removeAll();
            eventBar.setVisible(false);
        }
        broadcast(cfg.getString("wiadomosci.event-stop", "&cEvent duel wylaczony."));
    }

    private void tickEvent() {
        if (!eventOn()) {
            stopEvent();
            return;
        }
        if (eventBar == null) return;
        long leftMs = Math.max(0, eventDo - System.currentTimeMillis());
        int left = (int) (leftMs / 1000L);
        eventBar.setColor(barKolor());
        eventBar.setTitle(color(eventTitle().replace("{czas}", formatCzas(left))));
        eventBar.setProgress(clamp(leftMs / (eventTotal * 1000.0)));
    }

    private String eventTitle() {
        return cfg.getString("bossbar.event",
                "&f亗 &8| &fNa serwerze odpalone są &3pojedynki 1vs1 &8(&b/duel&8) &fjeszcze przez: &3{czas}");
    }

    void wyswietlUzycie(Player p) {
        List<String> lines = cfg.getStringList("wiadomosci.uzycie");
        if (lines.isEmpty()) {
            p.sendMessage(color("&$☹ &cPoprawne uzycie:"));
            p.sendMessage(color("&8- &4/duel <gracz>"));
            return;
        }
        for (String line : lines) p.sendMessage(color(line));
    }

    void wyzwij(Player from, Player target) {
        if (!eventOn()) {
            msg(from, cfg.getString("wiadomosci.event-off", "&cDuel nie jest teraz odpalony."));
            return;
        }
        oczekuje.put(target.getUniqueId(), from.getUniqueId());
        Bukkit.getScheduler().runTaskLater(host, () -> {
            UUID id = oczekuje.get(target.getUniqueId());
            if (id != null && id.equals(from.getUniqueId())) {
                oczekuje.remove(target.getUniqueId());
                if (from.isOnline()) msg(from, cfg.getString("wiadomosci.wyzwanie-wygaslo", "&7Wyzwanie wygaslo."));
                if (target.isOnline()) msg(target, cfg.getString("wiadomosci.wyzwanie-wygaslo", "&7Wyzwanie wygaslo."));
            }
        }, 30 * 20L);
        msg(from, cfg.getString("wiadomosci.wyzwanie-wyslane", "&aWyzwales &e{gracz}&a.")
                .replace("{gracz}", target.getName()));
        msg(target, cfg.getString("wiadomosci.wyzwanie-dostal", "&e{gracz} &awyzwal Cie. &f/duel akceptuj")
                .replace("{gracz}", from.getName()));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    void akceptuj(Player player) {
        if (!eventOn()) {
            msg(player, cfg.getString("wiadomosci.event-off", "&cDuel nie jest teraz odpalony."));
            return;
        }
        UUID fromId = oczekuje.remove(player.getUniqueId());
        if (fromId == null) {
            msg(player, cfg.getString("wiadomosci.brak-wyzwania", "&cNikt Cie nie wyzywa."));
            return;
        }
        Player from = Bukkit.getPlayer(fromId);
        if (from == null || !from.isOnline()) {
            msg(player, cfg.getString("wiadomosci.offline", "&cGracz &e{gracz} &cjest offline.").replace("{gracz}", "gracz"));
            return;
        }
        start(from, player);
    }

    void odrzuc(Player player) {
        UUID fromId = oczekuje.remove(player.getUniqueId());
        if (fromId == null) {
            msg(player, cfg.getString("wiadomosci.brak-wyzwania", "&cNikt Cie nie wyzywa."));
            return;
        }
        Player from = Bukkit.getPlayer(fromId);
        msg(player, cfg.getString("wiadomosci.odrzucil", "&7Odrzuciles duel."));
        if (from != null && from.isOnline()) {
            msg(from, cfg.getString("wiadomosci.odrzucono", "&e{gracz} &codrzucil duel.").replace("{gracz}", player.getName()));
        }
    }

    private void start(Player a, Player b) {
        if (!arenaGotowa()) {
            msg(a, cfg.getString("wiadomosci.arena-brak", "&cArena nie jest ustawiona."));
            msg(b, cfg.getString("wiadomosci.arena-brak", "&cArena nie jest ustawiona."));
            return;
        }
        if (zajety(a.getUniqueId()) || zajety(b.getUniqueId())) {
            msg(a, cfg.getString("wiadomosci.zajety", "&cTy albo ten gracz jestes juz na arenie."));
            return;
        }
        Location l1 = lokacja(1);
        Location l2 = lokacja(2);
        int borderSize = cfg.getInt("border", 0);

        Walka walka = new Walka(a.getUniqueId(), b.getUniqueId());
        walki.put(a.getUniqueId(), walka);
        walki.put(b.getUniqueId(), walka);

        a.teleport(l1);
        b.teleport(l2);

        WorldBorder border = null;
        if (borderSize > 0) {
            Location center = l1.clone().add(l2).multiply(0.5);
            border = Bukkit.createWorldBorder();
            border.setCenter(center);
            border.setSize(Math.max(10, borderSize));
            border.setWarningDistance(5);
            border.setDamageAmount(0.2);
            border.setDamageBuffer(0);
        }

        for (Player p : new Player[]{a, b}) {
            p.setWorldBorder(border);
            p.setCooldown(Material.ELYTRA, Integer.MAX_VALUE / 4);
            if (p.isGliding()) {
                p.setGliding(false);
                zdejmijElytre(p);
            }
            if (eventBar != null) eventBar.removePlayer(p);
        }
        ukryjPare(a, b);

        walka.bar = Bukkit.createBossBar(color(walkaTitle(0)), barKolor(), BarStyle.SOLID);
        walka.bar.setProgress(1);
        walka.bar.addPlayer(a);
        walka.bar.addPlayer(b);
        walka.ticker = Bukkit.getScheduler().runTaskTimer(host, () -> tickWalka(walka), 2L, 2L);

        String startMsg = cfg.getString("wiadomosci.start", "&aDuel z &e{gracz}&a. Czas nielimitowany.");
        msg(a, startMsg.replace("{gracz}", b.getName()));
        msg(b, startMsg.replace("{gracz}", a.getName()));
        a.playSound(a.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1f);
        b.playSound(b.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1f);
    }

    private void tickWalka(Walka walka) {
        if (walka == null || !walka.zyje) return;
        if (walka.faza == Walka.Faza.KONIEC) {
            walka.koniecTicks -= 2;
            Player zostal = zostawiony(walka);
            odswiezKoniec(walka, zostal);
            if (walka.koniecTicks <= 0) zakonczTp(walka);
            return;
        }
        int elapsed = (int) ((System.currentTimeMillis() - walka.startMs) / 1000L);
        if (walka.bar != null) {
            walka.bar.setColor(barKolor());
            walka.bar.setTitle(color(walkaTitle(elapsed)));
            int cykl = Math.max(10, cfg.getInt("bossbar.walka-cykl-sekundy", 90));
            double frac = (elapsed % cykl) / (double) cykl;
            walka.bar.setProgress(clamp(1.0 - frac));
        }
    }

    private String walkaTitle(int elapsed) {
        return cfg.getString("bossbar.walka",
                        "&f亗 &8| &cWasza walka na arenie trwa od: &f{czas}")
                .replace("{czas}", formatCzas(elapsed));
    }

    private void rozpocznijKoniec(Walka walka, UUID wyszedlLubPrzegrany) {
        if (walka == null || !walka.zyje || walka.faza == Walka.Faza.KONIEC) return;
        walka.faza = Walka.Faza.KONIEC;
        walka.wyszedl = wyszedlLubPrzegrany;
        int sek = Math.max(1, cfg.getInt("countdown-koniec-sekundy", 19));
        walka.koniecTicks = sek * 20;
        walka.koniecMaxTicks = sek * 20;
        Player zostal = zostawiony(walka);
        odswiezKoniec(walka, zostal);
    }

    private void odswiezKoniec(Walka walka, Player zostal) {
        int s = Math.max(0, (int) Math.ceil(walka.koniecTicks / 20.0));
        String bar = cfg.getString("bossbar.koniec",
                "&f丙 &8| &cZamknięcie areny za: &f{sek}s").replace("{sek}", String.valueOf(s));
        String sub = cfg.getString("subtitle.koniec",
                "&f丙 &8| &cZamknięcie areny za: &f{sek}s").replace("{sek}", String.valueOf(s));
        if (walka.bar != null) {
            walka.bar.setColor(barKolor());
            walka.bar.setTitle(color(bar));
            walka.bar.setProgress(clamp(walka.koniecTicks / (double) Math.max(1, walka.koniecMaxTicks)));
            walka.bar.removeAll();
            if (zostal != null) walka.bar.addPlayer(zostal);
        }
        if (zostal != null && zostal.isOnline()) {
            zostal.sendTitle(" ", color(sub), 0, 25, 5);
        }
    }

    private Player zostawiony(Walka walka) {
        UUID id = walka.wyszedl != null && walka.a.equals(walka.wyszedl) ? walka.b : walka.a;
        if (walka.wyszedl != null && id.equals(walka.wyszedl)) id = walka.b;
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline() && (walka.wyszedl == null || !p.getUniqueId().equals(walka.wyszedl))) return p;
        for (UUID u : new UUID[]{walka.a, walka.b}) {
            if (walka.wyszedl != null && u.equals(walka.wyszedl)) continue;
            Player o = Bukkit.getPlayer(u);
            if (o != null && o.isOnline()) return o;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDuelDamage(EntityDamageByEntityEvent event) {
        Player ofiara = event.getEntity() instanceof Player p ? p : null;
        Player atak = atakujacy(event);
        if (ofiara == null || atak == null || ofiara.equals(atak)) return;
        if (!taSamaWalka(atak.getUniqueId(), ofiara.getUniqueId())) {
            if (wDuele(atak.getUniqueId()) || wDuele(ofiara.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDuelPotion(PotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player strzelec)) return;
        Walka walka = walkaGracza(strzelec.getUniqueId());
        if (walka == null) {
            event.getAffectedEntities().removeIf(e -> e instanceof Player p && wDuele(p.getUniqueId()));
            return;
        }
        event.getAffectedEntities().removeIf(e -> {
            if (!(e instanceof Player p)) return false;
            return !walka.uczestnik(p.getUniqueId());
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDuelInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player cel)) return;
        if (blokujeMiedzyDuelami(event.getPlayer().getUniqueId(), cel.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDuelGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!event.isGliding() || !wDuele(p.getUniqueId())) return;
        event.setCancelled(true);
        zdejmijElytre(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Walka w = walkaGracza(event.getEntity().getUniqueId());
        if (w == null || !w.zyje || w.faza != Walka.Faza.WALKA) return;
        if (!w.uczestnik(event.getEntity().getUniqueId())) return;
        UUID dead = event.getEntity().getUniqueId();
        UUID winId = w.przeciwnik(dead);
        Player win = Bukkit.getPlayer(winId);
        String txt = cfg.getString("wiadomosci.wygrana", "&e{gracz} &awygrał duel.")
                .replace("{gracz}", win != null ? win.getName() : "gracz");
        if (win != null) msg(win, txt);
        msg(event.getEntity(), txt);
        przywrocPozniej(event.getEntity());
        rozpocznijKoniec(w, dead);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Walka w = walki.get(event.getPlayer().getUniqueId());
        if (w == null || !w.zyje || !w.uczestnik(event.getPlayer().getUniqueId())) return;
        if (w.faza == Walka.Faza.KONIEC) {
            if (w.wyszedl == null || w.wyszedl.equals(event.getPlayer().getUniqueId())) {
                zakonczTp(w);
            }
            return;
        }
        Player drugi = Bukkit.getPlayer(w.przeciwnik(event.getPlayer().getUniqueId()));
        if (drugi != null) {
            msg(drugi, cfg.getString("wiadomosci.wylog", "&e{gracz} &cwyszedl. Duel zakonczony.")
                    .replace("{gracz}", event.getPlayer().getName()));
        }
        rozpocznijKoniec(w, event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        Bukkit.getScheduler().runTask(host, () -> {
            if (!joined.isOnline()) return;
            if (!walki.isEmpty()) {
                for (UUID id : walki.keySet()) {
                    Player wArenie = Bukkit.getPlayer(id);
                    if (wArenie == null || wArenie.equals(joined) || wArenie.hasMetadata("NPC")) continue;
                    joined.hidePlayer(host, wArenie);
                    wArenie.hidePlayer(host, joined);
                }
            }
            if (!wDuele(joined.getUniqueId())) przywrocPozniej(joined);
            if (eventOn() && eventBar != null && !wDuele(joined.getUniqueId())) {
                eventBar.addPlayer(joined);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnCmd(PlayerCommandPreprocessEvent event) {
        if (!wDuele(event.getPlayer().getUniqueId())) return;
        String raw = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (raw.contains(":")) raw = raw.substring(raw.indexOf(':') + 1);
        if (raw.equals("spawn") || raw.equals("s") || raw.equals("hub") || raw.equals("lobby")) {
            event.setCancelled(true);
            msg(event.getPlayer(), cfg.getString("wiadomosci.spawn-blokada", "&cW duele nie mozesz isc na spawn."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Walka w = walki.get(event.getPlayer().getUniqueId());
        if (w == null || !w.zyje || !w.uczestnik(event.getPlayer().getUniqueId())) return;
        if (w.faza == Walka.Faza.KONIEC && w.wyszedl != null && w.wyszedl.equals(event.getPlayer().getUniqueId())) {
            Location spawn = spawnLoc();
            if (spawn != null) event.setRespawnLocation(spawn);
            Player p = event.getPlayer();
            Bukkit.getScheduler().runTask(host, () -> przywrocPozniej(p));
        }
    }

    private void zakonczTp(Walka walka) {
        if (walka == null || !walka.zyje) return;
        walka.zyje = false;
        if (walka.ticker != null) walka.ticker.cancel();
        if (walka.bar != null) {
            walka.bar.removeAll();
            walka.bar.setVisible(false);
        }
        walki.remove(walka.a, walka);
        walki.remove(walka.b, walka);

        for (UUID id : new UUID[]{walka.a, walka.b}) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            p.setWorldBorder(null);
            p.setCooldown(Material.ELYTRA, 0);
            p.resetTitle();
            przywrocPozniej(p);
            boolean wyszedl = walka.wyszedl != null && id.equals(walka.wyszedl);
            if (!wyszedl) tpSpawn(p);
            if (eventOn() && eventBar != null) eventBar.addPlayer(p);
        }
    }

    void zakonczWylaczanie() {
        Set<Walka> unikalne = new HashSet<>(walki.values());
        for (Walka w : unikalne) {
            if (w != null && w.zyje) zakonczTp(w);
        }
        walki.clear();
        stopEvent();
    }

    private void tpSpawn(Player p) {
        Location spawn = spawnLoc();
        if (spawn != null) p.teleport(spawn);
    }

    private Location spawnLoc() {
        String worldName = boniec.code.core.UstawieniaDatabase.getKv("spawn", "world", null);
        if (worldName == null || worldName.isEmpty()) {
            World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            return w == null ? null : w.getSpawnLocation();
        }
        return boniec.spawn.SpawnManager.wczytaj();
    }

    private void ukryjPare(Player a, Player b) {
        a.showPlayer(host, b);
        b.showPlayer(host, a);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(a) || o.equals(b) || o.hasMetadata("NPC")) continue;
            a.hidePlayer(host, o);
            o.hidePlayer(host, a);
            b.hidePlayer(host, o);
            o.hidePlayer(host, b);
        }
    }

    private void przywrocPozniej(Player p) {
        if (p == null || !p.isOnline()) return;
        przywroc(p);
        Bukkit.getScheduler().runTask(host, () -> {
            if (p.isOnline()) przywroc(p);
        });
    }

    private void przywroc(Player p) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || o.hasMetadata("NPC")) continue;
            if (koza != null && koza.wArenie(o.getUniqueId())) {
                p.hidePlayer(host, o);
                o.hidePlayer(host, p);
                continue;
            }
            if (wDuele(o.getUniqueId())) {
                p.hidePlayer(host, o);
                o.hidePlayer(host, p);
                continue;
            }
            if (ukrytyVanish(p, o)) p.hidePlayer(host, o);
            else p.showPlayer(host, o);
            if (ukrytyVanish(o, p)) o.hidePlayer(host, p);
            else o.showPlayer(host, p);
        }
    }

    private static boolean ukrytyVanish(Player viewer, Player other) {
        if (!AdminUtilManager.isVanished(other)) return false;
        return !viewer.hasPermission(AdminUtilManager.PERM_VANISH_SEE);
    }

    private static void zdejmijElytre(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack chest = inv.getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) return;
        inv.setChestplate(null);
        for (ItemStack rest : inv.addItem(chest).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        p.updateInventory();
    }

    static void msg(Player p, String text) {
        p.sendMessage(color(PREFIX + text));
    }

    private void broadcast(String text) {
        String out = color(PREFIX + text);
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(out);
        Bukkit.getConsoleSender().sendMessage(out);
    }

    static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    static String formatCzas(int sec) {
        sec = Math.max(0, sec);
        if (sec < 60) return sec + "s";
        int m = sec / 60;
        int s = sec % 60;
        if (s == 0) return m + "m";
        return m + "m " + s + "s";
    }

    private BarColor barKolor() {
        String n = cfg.getString("bossbar.kolor", "BLUE");
        if (n == null) return BarColor.BLUE;
        try {
            return BarColor.valueOf(n.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarColor.BLUE;
        }
    }

    private static double clamp(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static Player atakujacy(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private static final class Walka {
        enum Faza { WALKA, KONIEC }

        final UUID a;
        final UUID b;
        final long startMs = System.currentTimeMillis();
        Faza faza = Faza.WALKA;
        volatile boolean zyje = true;
        UUID wyszedl;
        int koniecTicks;
        int koniecMaxTicks;
        BossBar bar;
        BukkitTask ticker;

        Walka(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        boolean uczestnik(UUID uuid) {
            return a.equals(uuid) || b.equals(uuid);
        }

        UUID przeciwnik(UUID uuid) {
            return a.equals(uuid) ? b : a;
        }
    }
}
