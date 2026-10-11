package boniec.boss;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ZeusManager {

    private final JavaPlugin plugin;
    private final File plik;
    private FileConfiguration config;

    private ZeusInstance aktywny;
    private BukkitTask bossbarUpdateTask;
    private BukkitTask respawnTask;
    private BukkitTask specjalnyAtakTask;
    private BukkitTask teleportDoSpawnuTask;
    private long respawnAtEpochMs = -1;
    private long respawnCaloscMs = 1;

    private BossBar respawnBossBar;
    private Economy economy;
    private final ZeusDropManager dropManager;

    private final Map<UUID, Long> coldownElytry = new ConcurrentHashMap<>();

    public ZeusManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "zeus.yml");
        this.config = wczytajKonfig();
        this.dropManager = new ZeusDropManager(plugin);
        setupEconomy();
        uruchomBossbarUpdater();
        uruchomSpecjalnyAtakTimer();
        uruchomTeleportDoSpawnu();

        if (config.getBoolean("respawn.automatyczny", true) && czyMaZapisanySpawn()) {
            if (config.getBoolean("respawn.spawn-przy-starcie", false)) {
                Bukkit.getScheduler().runTaskLater(plugin, this::spawn, 20L);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> zaplanujRespawn(true), 20L);
            }
        }
    }

    private FileConfiguration wczytajKonfig() {
        if (!plik.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("zeus.yml")) {
                if (in != null) {
                    plik.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, plik.toPath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(plik);
        InputStream defStream = getClass().getClassLoader().getResourceAsStream("zeus.yml");
        if (defStream != null) {
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            cfg.setDefaults(def);
        }
        return cfg;
    }

    public void reload() {
        this.config = wczytajKonfig();
        dropManager.wczytaj();
        setupEconomy();
    }

    public ZeusDropManager getDropManager() { return dropManager; }

    public void zapisz() {
        try {
            config.save(plik);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public FileConfiguration getConfig() { return config; }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
    }

    private Economy getEconomy() {
        if (economy == null || !economy.isEnabled()) {
            setupEconomy();
        }
        return economy;
    }

    public boolean czyMaZapisanySpawn() {
        String swiat = config.getString("respawn.swiat", "");
        return swiat != null && !swiat.isEmpty() && Bukkit.getWorld(swiat) != null;
    }

    public void ustawSpawn(Location loc) {
        config.set("respawn.swiat", loc.getWorld().getName());
        config.set("respawn.x", loc.getX());
        config.set("respawn.y", loc.getY());
        config.set("respawn.z", loc.getZ());
        config.set("respawn.yaw", (double) loc.getYaw());
        zapisz();
    }

    private Location getSpawnLocation() {
        String swiatNazwa = config.getString("respawn.swiat", "");
        World world = swiatNazwa == null || swiatNazwa.isEmpty() ? null : Bukkit.getWorld(swiatNazwa);
        if (world == null) return null;
        double x = config.getDouble("respawn.x");
        double y = config.getDouble("respawn.y");
        double z = config.getDouble("respawn.z");
        float yaw = (float) config.getDouble("respawn.yaw");
        return new Location(world, x, y, z, yaw, 0f);
    }

    public boolean isEnabled() {
        return config.getBoolean("settings.enabled", true);
    }

    public boolean isAlive() {
        return aktywny != null && aktywny.getEntity() != null && !aktywny.getEntity().isDead();
    }

    public boolean czyToBoss(org.bukkit.entity.Entity entity) {
        return aktywny != null && aktywny.getEntity() != null && aktywny.getEntity().equals(entity);
    }

    public double getObrazeniaStaleHp() {
        return config.getDouble("settings.obrazenia-stale-hp", 6.0);
    }

    public ZeusInstance getAktywny() {
        return aktywny;
    }

    public boolean spawn() {
        Location loc = getSpawnLocation();
        if (loc == null) return false;
        spawnAt(loc);
        return true;
    }

    public void spawnAt(Location loc) {
        if (isAlive()) {
            aktywny.usun();
        }
        anulujRespawnTask();

        LivingEntity entity = spawnMythicZeus(loc);
        if (entity == null) {
            plugin.getLogger().warning("[Boss] Nie udało się zespawnować MythicMoba " + mythicMobId()
                    + " - sprawdź czy MythicMobs jest wlaczony i czy mob istnieje.");
            return;
        }

        double hpMaxRealne = config.getDouble("settings.hp-max", 2000.0);
        ustawAtrybut(entity, "MAX_HEALTH", "GENERIC_MAX_HEALTH", hpMaxRealne);
        // Bukkit/Paper ogranicza setHealth() do zakresu 0-1024 niezaleznie od
        // wartosci atrybutu MAX_HEALTH (ktory moze byc dowolnie duzy). Zeby
        // wspierac hp-max > 1024 (np. 2000) uzywamy systemu "visual HP" z
        // ZeusInstance (realne HP entity x5 = wyswietlane HP na bossbarze),
        // wiec tutaj trzeba realna wartosc HP entity zawsze zmiescic w 0-1024.
        double hpMaxRealneCap = Math.min(hpMaxRealne, 1024.0);
        if (hpMaxRealne > 1024.0) {
            plugin.getLogger().info("[Boss] settings.hp-max (" + hpMaxRealne
                    + ") przekracza limit Bukkita (1024) dla realnego HP encji. "
                    + "Uzyto realnego HP " + hpMaxRealneCap + ", a bossbar/visual HP nadal pokaze "
                    + hpMaxRealne + " (przez mnoznik w ZeusInstance).");
        }
        entity.setHealth(hpMaxRealneCap);

        ustawAtrybut(entity, "ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE", config.getDouble("settings.obrazenia", 6.0));
        ustawAtrybut(entity, "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED", config.getDouble("settings.predkosc", 0.28));
        entity.setCustomName(kolor(config.getString("settings.nazwa", "&e&lZEUS")));
        entity.setCustomNameVisible(true);
        entity.setGlowing(config.getBoolean("settings.swiecacy", true));
        entity.setPersistent(config.getBoolean("settings.persistent", true));
        entity.setRemoveWhenFarAway(false);

        BossBar bossBar = BossbarUtil.utworz(plugin, "", parseBarColor(), parseBarStyle());
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }
        bossBar.setVisible(true);

        aktywny = new ZeusInstance(entity, bossBar, hpMaxRealne, hpMaxRealneCap);
        respawnAtEpochMs = -1;

        rozglosKomunikat(config.getString("messages.spawn", "&eZeus przebudził się i zstąpił z Olimpu!"));
        aktualizujBossbar();
    }

    /**
     * Internal Name moba Zeus zdefiniowanego w MythicMobs (Mobs/*.yml).
     * Mozna nadpisac w zeus.yml pod kluczem "settings.mythicmob-id".
     */
    private String mythicMobId() {
        return config.getString("settings.mythicmob-id", "MSO_Zeus");
    }

    /**
     * Spawnuje moba Zeus przez MythicMobs (model/skille z configu MM) zamiast
     * goleg Bukkitowego EntityType.SKELETON. Zwraca null jesli MythicMobs nie jest
     * wlaczony albo mob o danym Internal Name nie istnieje.
     */
    private LivingEntity spawnMythicZeus(Location loc) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            plugin.getLogger().warning("[Boss] Plugin MythicMobs nie jest wlaczony na serwerze.");
            return null;
        }

        Optional<MythicMob> mobOpt = MythicBukkit.inst().getMobManager().getMythicMob(mythicMobId());
        if (mobOpt.isEmpty()) {
            plugin.getLogger().warning("[Boss] Nie znaleziono MythicMoba o Internal Name '" + mythicMobId()
                    + "'. Sprawdz plik Mobs/*.yml w MythicMobs.");
            return null;
        }

        double poziom = config.getDouble("settings.mythicmob-level", 1.0);

        // MythicMob#spawn(...) zwraca ActiveMob (wrapper Mythic), a nie bezposrednio
        // Bukkitowa encje. Najpewniejszy sposob zeby dostac org.bukkit.entity.Entity
        // to zlapac ja z MythicMobSpawnEvent, ktory Mythic odpala synchronicznie
        // w momencie spawnu (getEntity() na tym evencie zwraca juz Bukkit Entity).
        final LivingEntity[] zlapanaEncja = new LivingEntity[1];
        org.bukkit.event.Listener chwilowyListener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onMythicSpawn(MythicMobSpawnEvent event) {
                if (event.getMob() != null && event.getEntity() instanceof LivingEntity le) {
                    zlapanaEncja[0] = le;
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(chwilowyListener, plugin);
        try {
            ActiveMob activeMob = BossSpawn.bezBlokad(() -> mobOpt.get().spawn(BukkitAdapter.adapt(loc), poziom));
            if (activeMob == null) return null;
        } finally {
            org.bukkit.event.HandlerList.unregisterAll(chwilowyListener);
        }

        BossSpawn.oznacz(zlapanaEncja[0]);
        return zlapanaEncja[0];
    }

    public void despawnBezSmierci() {
        anulujRespawnTask();
        if (aktywny != null) {
            aktywny.usun();
            aktywny = null;
        }
    }

    public void zabijAdmin() {
        if (!isAlive()) return;
        obsluzSmierc(null);
    }

    public void zablokujElytre(Player player) {
        coldownElytry.put(player.getUniqueId(), System.currentTimeMillis() + 2000L);
        player.setGliding(false);
    }

    public boolean czyMaZablokowanaElytre(Player player) {
        Long koniec = coldownElytry.get(player.getUniqueId());
        if (koniec == null) return false;
        if (System.currentTimeMillis() >= koniec) {
            coldownElytry.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void uruchomSpecjalnyAtakTimer() {
        if (specjalnyAtakTask != null) specjalnyAtakTask.cancel();
        specjalnyAtakTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isAlive()) return;
                ZeusInstance zeus = aktywny;
                zeus.dekrementujSpecjal();

                if (zeus.getSekundyDoSpecjala() <= 0) {
                    wykonajAtakPiorunem(zeus);
                    zeus.losujCzasDoSpecjala();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Zeus wraca na zapisany spawn co 10 sekund, jeśli aktualnie żyje.
     * Dzięki temu nie zostaje odciągnięty przez graczy poza arenę.
     */
    private void uruchomTeleportDoSpawnu() {
        if (teleportDoSpawnuTask != null) {
            teleportDoSpawnuTask.cancel();
        }
        teleportDoSpawnuTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isAlive()) {
                    return;
                }
                Location spawn = getSpawnLocation();
                LivingEntity entity = aktywny.getEntity();
                if (spawn == null || entity == null || entity.isDead()) {
                    return;
                }
                if (!spawn.getChunk().isLoaded()) {
                    spawn.getChunk().load();
                }
                entity.teleport(spawn);
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    private void wykonajAtakPiorunem(ZeusInstance zeus) {
        LivingEntity entity = zeus.getEntity();
        Location loc = entity.getLocation();
        double promien = config.getDouble("settings.zasieg-pioruna", 15.0);

        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) <= promien) {
                p.getWorld().strikeLightningEffect(p.getLocation());

                if (!boniec.adminutil.AdminUtilManager.isGod(p)) { // /god - setHealth omija eventy obrażeń
                    double noweHp = Math.max(0, p.getHealth() - 6.0);
                    p.damage(6.0, entity);
                    p.setHealth(noweHp);
                }

                p.setVelocity(new Vector(0, 1.25, 0));

                zablokujElytre(p);
            }
        }
    }

    public void zarejestrujObrazenia(Player gracz, double kwota) {
        if (aktywny == null) return;
        aktywny.dodajObrazenia(gracz.getUniqueId(), kwota);
        BossObrazeniaHook.zglos(gracz, kwota, aktywny.getMnoznikObrazen());
    }

    public boolean sprobujNagrodeZaUderzenie(Player gracz) {
        if (aktywny == null) return false;
        if (!config.getBoolean("nagroda-za-uderzenie.wlaczone", true)) return false;

        long cooldownMs = config.getInt("nagroda-za-uderzenie.cooldown-sekund", 5) * 1000L;
        if (!aktywny.czyCooldownUderzeniaMinal(gracz.getUniqueId(), cooldownMs)) return false;

        aktywny.ustawCooldownUderzenia(gracz.getUniqueId(), cooldownMs);

        double min = config.getDouble("nagroda-za-uderzenie.pieniadze-min", 5.0);
        double max = config.getDouble("nagroda-za-uderzenie.pieniadze-max", 15.0);
        double kwota = min >= max ? min : ThreadLocalRandom.current().nextDouble(min, max);

        Economy econ = getEconomy();
        if (econ != null) {
            econ.depositPlayer(gracz, kwota);
        }

        String msg = config.getString("nagroda-za-uderzenie.wiadomosc", "&a+%kwota%$ za trafienie Zeusa!");
        gracz.sendMessage(prefix() + kolor(msg.replace("%kwota%", formatuj(kwota))));
        return true;
    }

    public boolean sprobujDropZaTrafienie(Player gracz) {
        if (aktywny == null || aktywny.getEntity() == null) return false;
        if (!config.getBoolean("drop-za-trafienie.wlaczone", true)) return false;
        if (dropManager.getWpisy().isEmpty()) return false;

        long cooldownMs = (long) (config.getDouble("drop-za-trafienie.cooldown-sekund", 1.0) * 1000L);
        if (!aktywny.czyCooldownDropuMinal(gracz.getUniqueId())) return false;
        aktywny.ustawCooldownDropu(gracz.getUniqueId(), cooldownMs);

        int ile = dropManager.daj(gracz);
        return ile > 0;
    }

    /** Wyswietla graczom title/subtitle po smierci bossa: title = nazwa bossa, subtitle = "Zostal pokonany!". */
    private void rozglosTytulSmierci() {
        String tytul = kolor(config.getString("settings.nazwa", "&e&lZEUS"));
        String podtytul = kolor(config.getString("messages.tytul-smierci-podtytul", "&fZostal pokonany!"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(tytul, podtytul, 10, 40, 10);
        }
    }

    public void obsluzSmierc(Player ostatniCiosGracz) {
        if (aktywny == null) return;
        ZeusInstance instancja = aktywny;
        aktywny = null;
        Location deathLoc = instancja.getEntity() != null ? instancja.getEntity().getLocation() : null;

        try {
            String zabojcaNazwa = ostatniCiosGracz != null ? ostatniCiosGracz.getName() : "Administrator";
            rozglosKomunikat(config.getString("messages.smierc", "&eZeus został strącony przez %zabojca%!")
                    .replace("%zabojca%", zabojcaNazwa));
            rozglosTytulSmierci();

            wyplacNagrodyTop(instancja);
            if (ostatniCiosGracz != null) {
                wyplacNagrodeOstatniegoCiosu(ostatniCiosGracz);
            }
            if (deathLoc != null || ostatniCiosGracz != null) {
                wylosujDrop(deathLoc, ostatniCiosGracz);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Boss] Błąd przy rozdawaniu nagród za śmierć Zeusa: " + e);
        } finally {
            instancja.usun();
        }

        if (config.getBoolean("respawn.automatyczny", true)) {
            zaplanujRespawn();
        }
    }

    private void wyplacNagrodyTop(ZeusInstance instancja) {
        if (!config.getBoolean("nagrody-top.wlaczone", true)) return;

        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(instancja.getObrazeniaGraczy().entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        if (ranking.isEmpty()) return;

        String[] miejsca = {"top-1", "top-2", "top-3"};
        for (int i = 0; i < miejsca.length && i < ranking.size(); i++) {
            UUID uuid = ranking.get(i).getKey();
            Player gracz = Bukkit.getPlayer(uuid);
            String sciezka = "nagrody-top." + miejsca[i];

            double pieniadze = config.getDouble(sciezka + ".pieniadze", 0);
            List<String> komendy = config.getStringList(sciezka + ".komendy");
            String wiadomosc = config.getString(sciezka + ".wiadomosc", "");

            String nickDlaKomend = gracz != null ? gracz.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            if (nickDlaKomend == null) nickDlaKomend = uuid.toString();

            if (pieniadze > 0) {
                Economy econ = getEconomy();
                if (econ != null) {
                    econ.depositPlayer(Bukkit.getOfflinePlayer(uuid), pieniadze);
                }
            }
            for (String komenda : komendy) {
                String finalna = komenda.replace("%gracz%", nickDlaKomend);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalna);
            }
            if (gracz != null && !wiadomosc.isEmpty()) {
                gracz.sendMessage(prefix() + kolor(wiadomosc));
            }
        }
    }

    private void wyplacNagrodeOstatniegoCiosu(Player gracz) {
        if (!config.getBoolean("nagrody-top.ostatni-cios.wlaczone", true)) return;
        double pieniadze = config.getDouble("nagrody-top.ostatni-cios.pieniadze", 0);
        List<String> komendy = config.getStringList("nagrody-top.ostatni-cios.komendy");
        String wiadomosc = config.getString("nagrody-top.ostatni-cios.wiadomosc", "");

        if (pieniadze > 0) {
            Economy econ = getEconomy();
            if (econ != null) econ.depositPlayer(gracz, pieniadze);
        }
        for (String komenda : komendy) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), komenda.replace("%gracz%", gracz.getName()));
        }
        if (!wiadomosc.isEmpty()) {
            gracz.sendMessage(prefix() + kolor(wiadomosc));
        }
    }

    private void wylosujDrop(Location loc, Player odbiorca) {
        List<?> dropList = config.getList("drop");
        if (dropList == null) return;

        for (Object o : dropList) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> wpis = (Map<?, ?>) o;
            try {
                String materialNazwa = String.valueOf(wpis.get("material"));
                Material material = Material.valueOf(materialNazwa.toUpperCase());
                int min = wpis.get("ilosc-min") != null ? ((Number) wpis.get("ilosc-min")).intValue() : 1;
                int max = wpis.get("ilosc-max") != null ? ((Number) wpis.get("ilosc-max")).intValue() : min;
                double szansa = wpis.get("szansa") != null ? ((Number) wpis.get("szansa")).doubleValue() : 100.0;

                if (ThreadLocalRandom.current().nextDouble(100.0) > szansa) continue;
                int ilosc = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
                if (ilosc <= 0) continue;

                ItemStack stack = new ItemStack(material, ilosc);
                if (odbiorca != null && odbiorca.isOnline()) {
                    ZeusDropManager.dajGraczowi(odbiorca, stack);
                } else if (loc != null && loc.getWorld() != null) {
                    loc.getWorld().dropItemNaturally(loc, stack);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Boss] Błąd dropu Zeusa: " + e.getMessage());
            }
        }
    }

    public void odswiezHarmonogram() {
        if (isAlive()) return;
        if (!config.getBoolean("respawn.automatyczny", true) || !czyMaZapisanySpawn()) return;
        zaplanujRespawn(true);
    }

    private void zaplanujRespawn() {
        zaplanujRespawn(false);
    }

    private void zaplanujRespawn(boolean wlaczBiezacaMinute) {
        int sekundy = wlaczBiezacaMinute
                ? boniec.eventy.EventHarmonogram.sekundyDoNastepnegoWlacznie(boniec.eventy.EventHarmonogram.ZEUS)
                : boniec.eventy.EventHarmonogram.sekundyDoNastepnego(boniec.eventy.EventHarmonogram.ZEUS);
        respawnAtEpochMs = System.currentTimeMillis() + sekundy * 1000L;
        respawnCaloscMs = Math.max(1L, sekundy * 1000L);
        pokazRespawnBossBar();

        anulujRespawnTask();
        respawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                respawnAtEpochMs = -1;
                ukryjRespawnBossBar();
                spawn();
            }
        }.runTaskLater(plugin, sekundy * 20L);
    }

    private void anulujRespawnTask() {
        if (respawnTask != null) {
            respawnTask.cancel();
            respawnTask = null;
        }
        ukryjRespawnBossBar();
    }

    private void pokazRespawnBossBar() {
        if (respawnBossBar == null) {
            respawnBossBar = BossbarUtil.utworz(plugin, "", BarColor.YELLOW, BarStyle.SOLID);
        }
        respawnBossBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!respawnBossBar.getPlayers().contains(p)) {
                respawnBossBar.addPlayer(p);
            }
        }
    }

    private void ukryjRespawnBossBar() {
        if (respawnBossBar != null) {
            respawnBossBar.removeAll();
            respawnBossBar.setVisible(false);
        }
    }

    public long getSekundyDoRespawnu() {
        if (respawnAtEpochMs < 0) return -1;
        return Math.max(0, (respawnAtEpochMs - System.currentTimeMillis()) / 1000L);
    }

    private void uruchomBossbarUpdater() {
        if (bossbarUpdateTask != null) bossbarUpdateTask.cancel();
        bossbarUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                aktualizujBossbar();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void aktualizujBossbar() {
        if (respawnBossBar != null && respawnBossBar.isVisible() && respawnAtEpochMs > 0) {
            String tytulMartwy = config.getString("bossbar.tytul-martwy", "&8Zeus odrodzi się za: &e%czas%")
                    .replace("%czas%", formatujCzasDoRespawnu());
            respawnBossBar.setTitle(kolor(tytulMartwy));
            long sekundy = Math.max(1L, respawnCaloscMs / 1000L);
            long pozostalo = getSekundyDoRespawnu();
            respawnBossBar.setProgress(sekundy > 0 ? Math.min(1.0, Math.max(0.0, 1.0 - ((double) pozostalo / sekundy))) : 0);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!respawnBossBar.getPlayers().contains(p)) {
                    respawnBossBar.addPlayer(p);
                }
            }
        }

        if (aktywny == null || aktywny.getEntity() == null) return;
        BossBar bar = aktywny.getBossBar();
        LivingEntity entity = aktywny.getEntity();

        if (entity.isDead()) {
            bar.setVisible(false);
            bar.removeAll();
            return;
        }

        double visualHp = aktywny.getVisualHp();
        double visualHpMax = aktywny.getVisualHpMax();
        double procent = visualHpMax > 0 ? (visualHp / visualHpMax) : 0;

        String tytul = config.getString("bossbar.tytul-zywy", "&e&lZEUS &8[&c%hp%/%hpmax% HP&8]")
                .replace("%hp%", String.valueOf((long) Math.ceil(visualHp)))
                .replace("%hpmax%", String.valueOf((long) visualHpMax))
                .replace("%procent%", String.valueOf((int) Math.round(procent * 100)));

        bar.setTitle(kolor(tytul));
        bar.setProgress(Math.min(1.0, Math.max(0.0, procent)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
        }
    }

    public String formatujCzasDoRespawnu() {
        long sekundy = getSekundyDoRespawnu();
        if (sekundy < 0) return "-";
        long godz = sekundy / 3600;
        long min = (sekundy % 3600) / 60;
        long sec = sekundy % 60;
        if (godz > 0) return godz + "h " + min + "m " + sec + "s";
        return min > 0 ? (min + "m " + sec + "s") : (sec + "s");
    }

    private void rozglosKomunikat(String tekst) {
        String finalny = prefix() + kolor(tekst);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(finalny);
        }
    }

    private String prefix() {
        return kolor(config.getString("messages.prefix", ""));
    }

    private String kolor(String tekst) {
        return boniec.szaman.util.Msg.color(tekst);
    }

    private BarColor parseBarColor() {
        try {
            return BarColor.valueOf(config.getString("bossbar.kolor", "YELLOW"));
        } catch (Exception e) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle parseBarStyle() {
        try {
            return BarStyle.valueOf(config.getString("bossbar.styl", "SOLID"));
        } catch (Exception e) {
            return BarStyle.SOLID;
        }
    }

    private void ustawAtrybut(LivingEntity entity, String nowaNazwa, String staraNazwa, double wartosc) {
        Attribute atrybut = resolveAttribute(nowaNazwa, staraNazwa);
        if (atrybut == null) return;
        AttributeInstance instancja = entity.getAttribute(atrybut);
        if (instancja != null) {
            instancja.setBaseValue(wartosc);
        }
    }

    @SuppressWarnings("deprecation")
    private Attribute resolveAttribute(String nowaNazwa, String staraNazwa) {
        try {
            return Attribute.valueOf(nowaNazwa);
        } catch (Exception ignored) {}
        try {
            return Attribute.valueOf(staraNazwa);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatuj(double wartosc) {
        if (wartosc == Math.floor(wartosc)) {
            return String.valueOf((long) wartosc);
        }
        return String.format("%.2f", wartosc);
    }

    public void shutdown() {
        if (respawnTask != null) {
            respawnTask.cancel();
            respawnTask = null;
        }
        if (specjalnyAtakTask != null) {
            specjalnyAtakTask.cancel();
            specjalnyAtakTask = null;
        }
        if (teleportDoSpawnuTask != null) {
            teleportDoSpawnuTask.cancel();
            teleportDoSpawnuTask = null;
        }
        if (bossbarUpdateTask != null) bossbarUpdateTask.cancel();
        BossbarUtil.usun(respawnBossBar);
        respawnBossBar = null;
        if (aktywny != null) {
            aktywny.usun();
            aktywny = null;
        }
    }

    public void dodajGraczaDoBossbarow(Player player) {
        if (isAlive()) {
            aktywny.getBossBar().addPlayer(player);
        }
        if (respawnBossBar != null && respawnBossBar.isVisible()) {
            respawnBossBar.addPlayer(player);
        }
    }
}