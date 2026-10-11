package boniec.code.core;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

@SuppressWarnings("deprecation")
public class MeteoriteManager implements Listener {

    private final boniec.dev.inventory.Main plugin;
    private final File file;
    private FileConfiguration config;
    private final List<Location> spawnLocations = new ArrayList<>();
    private final List<ItemStack> drops = new ArrayList<>();
    private long nextSpawnTime = 0;
    private int respawnIntervalHours = 6;
    private BukkitTask respawnTask;

    public int getRespawnIntervalHours() {
        return respawnIntervalHours;
    }

    // Ustawiane na true DOPIERO po wczytaniu locations/drops/activeMeteorites z pliku.
    // Dopoki jest false, saveConfig() niczego nie zapisuje - inaczej nadpisalby plik pustymi listami.
    private boolean loaded = false;

    private static final String EDIT_DROP_TITLE = ChatColor.DARK_PURPLE + "ᴇᴅʏᴛᴜᴊ ᴅʀᴏᴘ ᴍᴇᴛᴇᴏʀʏᴛᴜ";
    private static final String CONTROL_TITLE = ChatColor.DARK_PURPLE + "ᴢᴀʀᴢᴀᴅᴢᴀɴɪᴇ ᴍᴇᴛᴇᴏʀʏᴛᴀᴍɪ";

    // Active meteorites: Location -> HP
    private final Map<Location, Integer> activeMeteorites = new HashMap<>();
    private BossBar bossBar;

    // Hex color from request: #9D4BEF -> &x&9&D&4&B&E&F
    private final String hexColor = "&x&9&D&4&B&E&F";

    public MeteoriteManager(boniec.dev.inventory.Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "meteorites.yml");
        
        // Twórz bossBar przed loadConfig(), ponieważ loadConfig() może wywołać updateBossBar()
        bossBar = boniec.boss.BossbarUtil.utworz(plugin,
                format(hexColor + "☄ &8ᛝ &fɴᴀ ᴍᴀᴘɪᴇ ᴢɴᴀᴊᴅᴜᴊᴇ sɪᴇ &8(&x&9&D&4&B&E&F0&8/&x&9&D&4&B&E&F15&8) &x&9&D&4&B&E&Fᴍᴇᴛᴇᴏʀʏᴛᴏᴡ"),
                BarColor.PURPLE,
                BarStyle.SOLID
        );
        bossBar.setVisible(false);
        
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startRespawnTask();
    }

    private void startRespawnTask() {
        nextSpawnTime = boniec.eventy.EventHarmonogram.epochNastepnego(boniec.eventy.EventHarmonogram.METEORYTY);
        respawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (nextSpawnTime > 0 && System.currentTimeMillis() >= nextSpawnTime) {
                    spawnMeteorites();
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    public void odswiezHarmonogram() {
        if (!activeMeteorites.isEmpty()) return;
        nextSpawnTime = boniec.eventy.EventHarmonogram.epochNastepnego(boniec.eventy.EventHarmonogram.METEORYTY);
    }

    public void spawnMeteorites() {
        despawnMeteorites(); // Clear existing first

        if (spawnLocations.isEmpty()) return;

        // Spawn up to 15 meteorites (or less if fewer locations defined)
        List<Location> locs = new ArrayList<>(spawnLocations);
        Collections.shuffle(locs);
        int count = Math.min(15, locs.size());

        for (int i = 0; i < count; i++) {
            Location loc = locs.get(i);
            loc.getBlock().setType(Material.BLACK_WOOL); // Meteorite block
            int hp = ThreadLocalRandom.current().nextInt(100, 201); // 100-200 HP
            activeMeteorites.put(loc, hp);
        }

        nextSpawnTime = boniec.eventy.EventHarmonogram.epochNastepnegoPo(boniec.eventy.EventHarmonogram.METEORYTY);
        saveConfig();

        updateBossBar();
        bossBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }
        Bukkit.broadcastMessage(format(hexColor + "&lᴍᴇᴛᴇᴏʀʏᴛʏ &fᴢᴏsᴛᴀʟʏ ᴢʀᴇsᴘɪᴏɴᴇ"));
    }

    public void despawnMeteorites() {
        for (Location loc : activeMeteorites.keySet()) {
            loc.getBlock().setType(Material.AIR);
        }
        activeMeteorites.clear();
        bossBar.setVisible(false);
        bossBar.removeAll();
    }

    public boolean isActive() {
        return !activeMeteorites.isEmpty();
    }

    public int getAktywneIlosc() {
        return activeMeteorites.size();
    }

    public String getNextSpawnTimeFormatted() {
        if (!activeMeteorites.isEmpty()) return "Meteoryty aktywne";
        long now = System.currentTimeMillis();
        if (nextSpawnTime <= now) return "Do zrespienia";
        
        long diff = nextSpawnTime - now;
        long seconds = diff / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        return hours + "godz " + minutes + "m " + secs + "s";
    }

    public void addSpawnLocation(Location loc) {
        spawnLocations.add(loc);
        saveConfig();
    }

    public void openEditDropGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, EDIT_DROP_TITLE);
        for (int i = 0; i < drops.size() && i < 54; i++) {
            inv.setItem(i, drops.get(i));
        }
        player.openInventory(inv);
    }

    public void openControlGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, CONTROL_TITLE);

        ItemStack spawnItem = new ItemStack(Material.LIME_DYE);
        var meta = spawnItem.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "ᴢʀᴇsᴘ ᴍᴇᴛᴇᴏʀʏᴛʏ");
        spawnItem.setItemMeta(meta);

        ItemStack despawnItem = new ItemStack(Material.RED_DYE);
        meta = despawnItem.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "ᴜsᴜɴ ᴍᴇᴛᴇᴏʀʏᴛʏ");
        despawnItem.setItemMeta(meta);

        inv.setItem(11, spawnItem);
        inv.setItem(15, despawnItem);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (activeMeteorites.containsKey(block.getLocation())) {
            Player player = event.getPlayer();
            ItemStack hand = player.getInventory().getItemInMainHand();

            // Must use Shears
            if (hand.getType() != Material.SHEARS) {
                player.sendMessage(ChatColor.RED + "ᴍᴜsɪsᴢ ᴜᴢʏᴄ ɴᴏᴢʏᴄ ᴀʙʏ ᴢɴɪsᴢᴄᴢʏᴄ ᴍᴇᴛᴇᴏʀʏᴛ");
                event.setCancelled(true); // Prevent breaking with hand/other items
                return;
            }

            event.setCancelled(true); // Handle damage manually
            handleMeteoriteHit(player, block.getLocation());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (activeMeteorites.containsKey(event.getBlock().getLocation())) {
            event.setCancelled(true); // Prevent normal breaking
        }
    }

    private void handleMeteoriteHit(Player player, Location loc) {
        int hp = activeMeteorites.get(loc);
        hp--;

        // Title & Subtitle
        player.sendTitle(
                format(hexColor + "&lᴍᴇᴛᴇᴏʀʏᴛ"),
                format("&3" + hp + " &cʜᴘ"),
                0, 20, 10
        );

        // Effects (Chance)
        try {
            if (ThreadLocalRandom.current().nextInt(100) < 15) { // 15% chance
                if (ThreadLocalRandom.current().nextBoolean()) {
                    // Knockback
                    Vector dir = player.getLocation().toVector().subtract(loc.toVector()).normalize();
                    player.setVelocity(dir.multiply(1.5).setY(0.5));
                    player.sendMessage(format(hexColor + "&lᴍᴇᴛᴇᴏʀʏᴛ &cᴏᴅᴇᴘᴄʜɴᴀʟ ᴄɪᴇ"));
                } else {
                    // Lightning
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    player.damage(2.0);
                    player.sendMessage(format(hexColor + "&lᴍᴇᴛᴇᴏʀʏᴛ &cᴘᴏʀᴀᴢɪʟ ᴄɪᴇ ᴘɪᴏʀᴜɴᴇᴍ"));
                }
            }
        } catch (Exception ex) {
            // Efekt (odrzut/piorun) nigdy nie moze zablokowac zniszczenia meteorytu ani dropu.
            plugin.getLogger().warning("[Meteoryty] Blad efektu przy uderzeniu meteorytu: " + ex);
        }

        if (hp <= 0) {
            destroyMeteorite(loc, player);
        } else {
            activeMeteorites.put(loc, hp);
        }
    }

    private void destroyMeteorite(Location loc, Player killer) {
        activeMeteorites.remove(loc);
        World world = loc.getWorld();
        loc.getBlock().setType(Material.AIR);

        // Drop NAJPIERW - zanim cokolwiek innego (dzwiek, wiadomosc) zdazy rzucic wyjatkiem.
        dropReward(loc, killer);

        try {
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        } catch (Exception ignored) {}

        killer.sendMessage(format(hexColor + "&lᴍᴇᴛᴇᴏʀʏᴛ &fᴢᴏsᴛᴀʟ ᴢɴɪsᴢᴄᴢᴏɴʏ"));
        saveConfig(); // zapisz, ze ten meteoryt juz nie istnieje
        updateBossBar();
    }

    private void dropReward(Location blockLoc, Player killer) {
        if (drops.isEmpty()) {
            plugin.getLogger().warning("[Meteoryty] Meteoryt zniszczony, ale lista dropu jest PUSTA - ustaw ja: /adminmeteoryt editdrop");
            if (killer.hasPermission("core.adminmeteoryt")) {
                killer.sendMessage(ChatColor.RED + "ʙʀᴀᴋ ᴅʀᴏᴘᴜ ᴍᴇᴛᴇᴏʀʏᴛᴜ! ᴜsᴛᴀᴡ ɢᴏ: /adminmeteoryt editdrop");
            }
            return;
        }

        ItemStack reward = drops.get(ThreadLocalRandom.current().nextInt(drops.size())).clone();

        // Nagroda leci prosto do ekwipunku gracza, ktory zniszczyl meteoryt.
        String name = (reward.hasItemMeta() && reward.getItemMeta().hasDisplayName())
                ? reward.getItemMeta().getDisplayName()
                : reward.getType().name();
        int amount = reward.getAmount();

        // addItem zwraca to, co sie NIE zmiescilo - to wyrzucamy pod nogi gracza, zeby nic nie przepadlo.
        Map<Integer, ItemStack> leftover = killer.getInventory().addItem(reward);
        if (leftover.isEmpty()) {
            killer.sendMessage(format(hexColor + "&lᴍᴇᴛᴇᴏʀʏᴛ &fᴏᴛʀᴢʏᴍᴀʟᴇs&7: &f" + amount + "x " + name));
        } else {
            for (ItemStack rest : leftover.values()) {
                killer.getWorld().dropItemNaturally(killer.getLocation(), rest);
            }
            killer.sendMessage(format(hexColor + "&lᴍᴇᴛᴇᴏʀʏᴛ &cᴇǫ ᴘᴇʟɴᴇ &7- &fᴘʀᴢᴇᴅᴍɪᴏᴛ ʟᴇᴄɪ ɴᴀ ᴢɪᴇᴍɪᴇ&7: &f" + amount + "x " + name));
        }
    }

    private void updateBossBar() {
        if (bossBar == null) return; // BossBar jeszcze nie został utworzony
        int count = activeMeteorites.size();
        bossBar.setTitle(format(hexColor + "☄ &8ᛝ &fɴᴀ ᴍᴀᴘɪᴇ ᴢɴᴀᴊᴅᴜᴊᴇ sɪᴇ &8(" + hexColor + count + "&8/" + hexColor + "15&8) " + hexColor + "ᴍᴇᴛᴇᴏʀʏᴛᴏᴡ"));
        double progress = count / 15.0;
        if (progress > 1.0) progress = 1.0;
        if (progress < 0.0) progress = 0.0;
        bossBar.setProgress(progress);
    }

    /** Porownanie tytulu GUI bez kodow kolorow - odporne na roznice w serializacji tytulu miedzy wersjami serwera. */
    private static boolean titleMatches(String actual, String expected) {
        if (actual == null) return false;
        return ChatColor.stripColor(actual).equals(ChatColor.stripColor(expected));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!titleMatches(event.getView().getTitle(), EDIT_DROP_TITLE)) return;

        drops.clear();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                drops.add(item.clone());
            }
        }
        saveConfig();
        event.getPlayer().sendMessage(ChatColor.GREEN + "ᴢᴀᴘɪsᴀɴᴏ ᴅʀᴏᴘ ᴍᴇᴛᴇᴏʀʏᴛᴏᴡ" + ChatColor.GRAY + " (" + drops.size() + ")");
    }

    @EventHandler
    public void onGuiClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!titleMatches(event.getView().getTitle(), CONTROL_TITLE)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        if (event.getSlot() == 11) { // Spawn
            spawnMeteorites();
            event.getWhoClicked().closeInventory();
        } else if (event.getSlot() == 15) { // Despawn
            despawnMeteorites();
            event.getWhoClicked().closeInventory();
            event.getWhoClicked().sendMessage(ChatColor.RED + "ᴜsᴜɴɪᴇᴛᴏ ᴍᴇᴛᴇᴏʀʏᴛʏ");
        }
    }

    @SuppressWarnings("unchecked")
    public void loadConfig() {
        // Dopoki dane nie sa wczytane, saveConfig() nie moze nic zapisac (patrz pole "loaded").
        loaded = false;

        if (!file.exists()) {
            plugin.saveResource("meteorites.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        respawnIntervalHours = config.getInt("respawnIntervalHours", 6);
        nextSpawnTime = config.getLong("nextSpawnTime", 0);

        // 1) Lokalizacje spawnu
        spawnLocations.clear();
        if (config.isList("locations")) {
            for (String s : config.getStringList("locations")) {
                try {
                    String[] parts = s.split(",");
                    World w = Bukkit.getWorld(parts[0]);
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    if (w != null) {
                        spawnLocations.add(new Location(w, x, y, z));
                    } else {
                        plugin.getLogger().warning("[Meteoryty] Pominieto lokalizacje - swiat '" + parts[0] + "' nie jest zaladowany: " + s);
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2) Drop - obsluguje zarowno gotowe ItemStack, jak i surowe mapy (gdyby YAML nie zdeserializowal itemu)
        drops.clear();
        List<?> rawDrops = config.getList("drops");
        if (rawDrops != null) {
            for (Object obj : rawDrops) {
                ItemStack item = null;
                if (obj instanceof ItemStack) {
                    item = (ItemStack) obj;
                } else if (obj instanceof Map) {
                    try {
                        item = ItemStack.deserialize((Map<String, Object>) obj);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("[Meteoryty] Nie udalo sie wczytac itemu z dropu: " + ex);
                    }
                }
                if (item != null && item.getType() != Material.AIR) {
                    drops.add(item);
                }
            }
        }

        // 3) Aktywne meteoryty po restarcie
        activeMeteorites.clear();
        if (config.isList("activeMeteorites")) {
            for (String s : config.getStringList("activeMeteorites")) {
                try {
                    String[] parts = s.split(",");
                    World w = Bukkit.getWorld(parts[0]);
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    int hp = parts.length > 4 ? Integer.parseInt(parts[4]) : 100;
                    if (w != null) {
                        Location loc = new Location(w, x, y, z);
                        // Sprawdz czy blok nadal jest meteorytem
                        if (loc.getBlock().getType() == Material.BLACK_WOOL) {
                            activeMeteorites.put(loc, hp);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Dane wczytane - od tego momentu saveConfig() jest bezpieczne.
        loaded = true;
        plugin.getLogger().info("[Meteoryty] Wczytano: " + spawnLocations.size() + " lokalizacji, "
                + drops.size() + " itemow dropu, " + activeMeteorites.size() + " aktywnych meteorytow.");

        // 4) Naprawa timera - DOPIERO PO wczytaniu danych (wczesniej zapis kasowal drop i lokalizacje z pliku)
        nextSpawnTime = boniec.eventy.EventHarmonogram.epochNastepnego(boniec.eventy.EventHarmonogram.METEORYTY);
        saveConfig();

        // Jesli sa aktywne meteoryty, pokaz bossbar
        if (!activeMeteorites.isEmpty()) {
            updateBossBar();
            bossBar.setVisible(true);
            for (Player p : Bukkit.getOnlinePlayers()) {
                bossBar.addPlayer(p);
            }
        }
    }

    private void saveConfig() {
        // Nigdy nie zapisuj, zanim loadConfig() wczytal dane - inaczej nadpiszemy plik pustymi listami.
        if (!loaded) return;

        List<String> locs = new ArrayList<>();
        for (Location l : spawnLocations) {
            locs.add(l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        }
        config.set("locations", locs);
        config.set("drops", new ArrayList<>(drops));
        config.set("respawnIntervalHours", respawnIntervalHours);
        config.set("nextSpawnTime", nextSpawnTime);

        // Zapis aktywnych meteorytow
        List<String> activeLocs = new ArrayList<>();
        for (Map.Entry<Location, Integer> e : activeMeteorites.entrySet()) {
            Location loc = e.getKey();
            activeLocs.add(loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," + e.getValue());
        }
        config.set("activeMeteorites", activeLocs);

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void cleanup() {
        // Zapisz aktywne meteoryty przed wyłączeniem
        saveConfig();
        if (respawnTask != null) respawnTask.cancel();
        boniec.boss.BossbarUtil.usun(bossBar);
    }

    private String format(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
