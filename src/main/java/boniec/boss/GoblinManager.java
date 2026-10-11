package boniec.boss;

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

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class GoblinManager {

    private final JavaPlugin plugin;
    private final File plik;
    private FileConfiguration config;

    private GoblinInstance aktywny;
    private BukkitTask bossbarUpdateTask;
    private BukkitTask respawnTask;
    private long respawnAtEpochMs = -1;
    private long respawnCaloscMs = 1;

    private BossBar respawnBossBar;
    private Economy economy;
    private final GoblinDropManager dropManager;


    public GoblinManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "goblin.yml");
        this.config = wczytajKonfig();
        this.dropManager = new GoblinDropManager(plugin);
        setupEconomy();
        uruchomBossbarUpdater();

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
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("goblin.yml")) {
                if (in != null) {
                    plik.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, plik.toPath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(plik);
        InputStream defStream = getClass().getClassLoader().getResourceAsStream("goblin.yml");
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

    public GoblinDropManager getDropManager() { return dropManager; }

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
        return Math.max(10.0, config.getDouble("settings.obrazenia-stale-hp", 10.0));
    }

    public double getObrazeniaOdGraczaHp() {
        return config.getDouble("settings.obrazenia-od-gracza-hp", 0.0);
    }

    /**
     * Odejmuje bossowi realne HP RECZNIE (przez setHealth), zamiast polegac na
     * event.setDamage(). Uzywane dla "stalej" wartosci obrazen od gracza -
     * niezalezne od tego, czy inny plugin (np. MythicMobs) modyfikuje event
     * EntityDamageByEntityEvent po nas i nadpisuje nasza wartosc obrazen.
     * Wywolywane z priorytetu MONITOR, po tym jak oryginalny event zostal
     * anulowany, wiec vanilla damage nigdy nie zostanie zaaplikowany.
     */
    public void zadajStaleObrazeniaBossowi(double hp) {
        if (aktywny == null || aktywny.getEntity() == null) return;
        LivingEntity entity = aktywny.getEntity();
        if (entity.isDead()) return;
        double nowe = Math.max(0.0, entity.getHealth() - hp);
        if (nowe <= 0.0) {
            // Zamiast setHealth(0) (co nie zawsze odpala normalna smierc/loot),
            // uzywamy damage(9999) zeby wymusic prawidlowa smierc encji.
            entity.damage(9999.0);
        } else {
            entity.setHealth(nowe);
        }
    }

    public GoblinInstance getAktywny() {
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

        LivingEntity entity = spawnZombieGoblin(loc);
        if (entity == null) {
            plugin.getLogger().warning("[Boss] Nie udalo sie zespawnowac zombie goblina.");
            return;
        }

        double hpMaxRealne = config.getDouble("settings.hp-max", 5000.0);
        ustawAtrybut(entity, "MAX_HEALTH", "GENERIC_MAX_HEALTH", hpMaxRealne);
        // Bukkit/Paper ogranicza setHealth() do zakresu 0-1024 niezaleznie od
        // wartosci atrybutu MAX_HEALTH (ktory moze byc dowolnie duzy). Zeby
        // wspierac hp-max > 1024 (np. 5000) uzywamy systemu "visual HP" z
        // GoblinInstance (realne HP entity x5 = wyswietlane HP na bossbarze),
        // wiec tutaj trzeba realna wartosc HP entity zawsze zmiescic w 0-1024.
        double hpMaxRealneCap = Math.min(hpMaxRealne, 1024.0);
        if (hpMaxRealne > 1024.0) {
            plugin.getLogger().info("[Boss] settings.hp-max (" + hpMaxRealne
                    + ") przekracza limit Bukkita (1024) dla realnego HP encji. "
                    + "Uzyto realnego HP " + hpMaxRealneCap + ", a bossbar/visual HP nadal pokaze "
                    + hpMaxRealne + " (przez mnoznik w GoblinInstance).");
        }
        entity.setHealth(hpMaxRealneCap);

        ustawAtrybut(entity, "ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE",
                Math.max(20.0, config.getDouble("settings.obrazenia", 20.0)));
        ustawAtrybut(entity, "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED",
                Math.max(0.42, config.getDouble("settings.predkosc", 0.42)));
        entity.setCustomName(kolor(config.getString("settings.nazwa", "&a&lGOBLIN")));
        entity.setCustomNameVisible(true);
        entity.setGlowing(config.getBoolean("settings.swiecacy", true));
        entity.setPersistent(config.getBoolean("settings.persistent", true));
        entity.setRemoveWhenFarAway(false);

        BossBar bossBar = BossbarUtil.utworz(plugin, "", parseBarColor(), parseBarStyle());
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }
        bossBar.setVisible(true);

        aktywny = new GoblinInstance(entity, bossBar, hpMaxRealne, hpMaxRealneCap);
        respawnAtEpochMs = -1;

        rozglosKomunikat(config.getString("messages.spawn", "&aGoblin wyszedł z nor i grasuje po świecie!"));
        aktualizujBossbar();
    }

    private LivingEntity spawnZombieGoblin(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        org.bukkit.entity.Entity spawned = BossSpawn.pozwol(
                () -> loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.ZOMBIE));
        if (!(spawned instanceof org.bukkit.entity.Zombie zombie)) {
            if (spawned != null) spawned.remove();
            return null;
        }
        zombie.setAdult();
        zombie.setShouldBurnInDay(false);
        zombie.setCanPickupItems(false);
        zombie.setConversionTime(-1);
        if (zombie.getEquipment() != null) {
            zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            zombie.getEquipment().setItemInMainHandDropChance(0f);
            zombie.getEquipment().setHelmetDropChance(0f);
            zombie.getEquipment().setChestplateDropChance(0f);
            zombie.getEquipment().setLeggingsDropChance(0f);
            zombie.getEquipment().setBootsDropChance(0f);
        }
        return zombie;
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

        String msg = config.getString("nagroda-za-uderzenie.wiadomosc", "&a+%kwota%$ za trafienie Goblina!");
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
        String tytul = kolor(config.getString("settings.nazwa", "&a&lGOBLIN"));
        String podtytul = kolor(config.getString("messages.tytul-smierci-podtytul", "&fZostal pokonany!"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(tytul, podtytul, 10, 40, 10);
        }
    }

    public void obsluzSmierc(Player ostatniCiosGracz) {
        if (aktywny == null) return;
        GoblinInstance instancja = aktywny;
        aktywny = null;
        Location deathLoc = instancja.getEntity() != null ? instancja.getEntity().getLocation() : null;

        try {
            String zabojcaNazwa = ostatniCiosGracz != null ? ostatniCiosGracz.getName() : "Administrator";
            rozglosKomunikat(config.getString("messages.smierc", "&a%zabojca% &7zadal ostateczny cios Goblinowi! Boss zostal pokonany!")
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
            plugin.getLogger().warning("[Boss] Błąd przy rozdawaniu nagród za śmierć Goblina: " + e);
        } finally {
            instancja.usun();
        }

        if (config.getBoolean("respawn.automatyczny", true)) {
            zaplanujRespawn();
        }
    }

    private void wyplacNagrodyTop(GoblinInstance instancja) {
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
                    GoblinDropManager.dajGraczowi(odbiorca, stack);
                } else if (loc != null && loc.getWorld() != null) {
                    loc.getWorld().dropItemNaturally(loc, stack);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Boss] Błąd dropu Goblina: " + e.getMessage());
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
                ? boniec.eventy.EventHarmonogram.sekundyDoNastepnegoWlacznie(boniec.eventy.EventHarmonogram.GOBLIN)
                : boniec.eventy.EventHarmonogram.sekundyDoNastepnego(boniec.eventy.EventHarmonogram.GOBLIN);
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
            respawnBossBar = BossbarUtil.utworz(plugin, "", BarColor.GREEN, BarStyle.SOLID);
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
            String tytulMartwy = config.getString("bossbar.tytul-martwy", "&8Goblin odrodzi się za: &e%czas%")
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

        String tytul = config.getString("bossbar.tytul-zywy", "&a&lGOBLIN &8[&c%hp%/%hpmax% HP&8]")
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
            return BarColor.valueOf(config.getString("bossbar.kolor", "GREEN"));
        } catch (Exception e) {
            return BarColor.GREEN;
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