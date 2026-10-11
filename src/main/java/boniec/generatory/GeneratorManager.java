package boniec.generatory;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GeneratorManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, Generator> generators = new HashMap<>();
    private final File generatorsFile;
    private FileConfiguration generatorsConfig;

    private final Map<UUID, String> editingGenerator = new HashMap<>();
    /** Gracz wpisuje na czacie permisję kopania dla generatora z GUI. */
    private final Map<UUID, String> pendingMinePermissionEdit = new HashMap<>();
    private final String guiTitle = ChatColor.DARK_AQUA + "Edytuj Generator";

    public GeneratorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.generatorsFile = new File(plugin.getDataFolder(), "generatory.yml");
        wczytajNocnyNerf();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadGenerators();
        startTickTask();
        plugin.getServer().getScheduler().runTaskLater(plugin, this::posprzatajHologramy, 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::posprzatajHologramy, 60L);
    }

    private void posprzatajHologramy() {
        cleanOrphanedHolograms();
        for (Generator gen : generators.values()) {
            gen.ensureHolograms();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Generator gen : generators.values()) {
            Location holo = gen.hologramLocation();
            if (holo.getWorld() == null || !holo.getWorld().equals(chunk.getWorld())) {
                continue;
            }
            if ((holo.getBlockX() >> 4) != chunk.getX() || (holo.getBlockZ() >> 4) != chunk.getZ()) {
                continue;
            }
            gen.ensureHolograms();
        }
    }

    public void createGenerator(Location center, int radius, int height, List<Material> materials) {
        String id = "gen-" + UUID.randomUUID().toString().substring(0, 8);
        Location corner1 = center.clone().subtract(radius, 0, radius);
        Location corner2 = center.clone().add(radius, height - 1, radius);

        Generator generator = new Generator(plugin, id, corner1, corner2, materials, 60, true); // Domyślne wartości
        generator.forceReset(); // Wypełnij blokami od razu
        generators.put(id, generator);
        saveGenerators();
    }

    public void deleteGenerator(String id) {
        Generator gen = generators.get(id);
        if (gen != null) {
            gen.removeHolograms();
            generators.remove(id);
            saveGenerators();
        }
    }

    private boolean nocnyNerfEnabled = true;
    private String nocnyNerfRegion = "pvp";
    private int nocnyNerfOd = 0;
    private int nocnyNerfDo = 10;
    private double nocnyNerfMnoznik = 0.7;
    private java.time.ZoneId nocnyNerfStrefa = java.time.ZoneId.of("Europe/Warsaw");

    public void wczytajNocnyNerf() {
        File kopanie = new File(plugin.getDataFolder(), "kopanie.yml");
        FileConfiguration cfg = kopanie.exists()
                ? YamlConfiguration.loadConfiguration(kopanie)
                : new YamlConfiguration();
        nocnyNerfEnabled = cfg.getBoolean("nocny-nerf-pvp.enabled", true);
        nocnyNerfRegion = cfg.getString("nocny-nerf-pvp.region", "pvp");
        nocnyNerfOd = Math.max(0, Math.min(23, cfg.getInt("nocny-nerf-pvp.od-godziny", 0)));
        nocnyNerfDo = Math.max(0, Math.min(24, cfg.getInt("nocny-nerf-pvp.do-godziny", 10)));
        nocnyNerfMnoznik = Math.max(0.0, cfg.getDouble("nocny-nerf-pvp.mnoznik", 0.7));
        try {
            nocnyNerfStrefa = java.time.ZoneId.of(cfg.getString("nocny-nerf-pvp.strefa-czasowa", "Europe/Warsaw"));
        } catch (Exception e) {
            nocnyNerfStrefa = java.time.ZoneId.of("Europe/Warsaw");
        }
    }

    /**
     * 1.0 normalnie, albo mnoznik nocny (domyslnie 0.7 = -30%) gdy:
     * blok jest w generatorze, region WorldGuard pvp, godzina 00:00-10:00.
     */
    public double mnoznikNocnyPvp(Location location) {
        if (!nocnyNerfEnabled || location == null) return 1.0;
        if (getGeneratorAt(location) == null) return 1.0;
        int godzina = java.time.LocalTime.now(nocnyNerfStrefa).getHour();
        boolean wOknie = nocnyNerfOd < nocnyNerfDo
                ? (godzina >= nocnyNerfOd && godzina < nocnyNerfDo)
                : (godzina >= nocnyNerfOd || godzina < nocnyNerfDo);
        if (!wOknie) return 1.0;
        try {
            if (!boniec.code.core.WorldGuardHook.isInRegion(location, nocnyNerfRegion)) return 1.0;
        } catch (Throwable t) {
            return 1.0;
        }
        return nocnyNerfMnoznik;
    }

    public Generator getGeneratorAt(Location location) {
        for (Generator gen : generators.values()) {
            if (location.getWorld().equals(gen.getCorner1().getWorld()) &&
                location.getX() >= gen.getCorner1().getX() && location.getX() <= gen.getCorner2().getX() &&
                location.getY() >= gen.getCorner1().getY() && location.getY() <= gen.getCorner2().getY() &&
                location.getZ() >= gen.getCorner1().getZ() && location.getZ() <= gen.getCorner2().getZ()) {
                return gen;
            }
        }
        return null;
    }

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Generator generator : generators.values()) {
                    generator.tick();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Co sekundę
    }

    public void openEditGUI(Player player, Generator gen) {
        editingGenerator.put(player.getUniqueId(), gen.getId());
        Inventory gui = Bukkit.createInventory(player, 27, guiTitle);

        gui.setItem(10, createGuiItem(Material.CLOCK, "&eZmień czas resetu", Arrays.asList("&7Aktualnie: &f" + gen.getResetTime() + "s", "", "&aLPM: &f+10s", "&cPPM: &f-10s")));
        gui.setItem(12, createGuiItem(Material.LEVER, "&eWłącz/Wyłącz autoreset", Collections.singletonList(gen.isAutoResetEnabled() ? "&aWłączony" : "&cWyłączony")));

        String perm = gen.getMinePermission();
        List<String> permLore = new ArrayList<>();
        permLore.add("&7Kto może &fkopać &7bloki w tym generatorze");
        permLore.add("");
        if (perm == null) {
            permLore.add("&7Permisja: &a(brak — wszyscy)");
        } else {
            permLore.add("&7Permisja: &f" + perm);
        }
        permLore.add("");
        permLore.add("&eLPM: &fwpisz permisję na czacie");
        permLore.add("&ePPM: &fwyłącz ograniczenie");
        gui.setItem(13, createGuiItem(Material.IRON_PICKAXE, "&ePermisja kopania", permLore));

        gui.setItem(14, createGuiItem(Material.TNT, "&6Zresetuj teraz", Collections.singletonList("&7Kliknij, aby wymusić reset")));
        gui.setItem(16, createGuiItem(Material.BARRIER, "&cUsuń generator", Collections.singletonList("&7Kliknij, aby trwale usunąć")));
        // TODO: Opcja zmiany bloków

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        String genId = editingGenerator.get(player.getUniqueId());
        if (genId == null) return;

        Generator gen = generators.get(genId);
        if (gen == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Ten generator już nie istnieje.");
            return;
        }

        switch (event.getSlot()) {
            case 10: // Zmiana czasu
                if (event.isLeftClick()) {
                    gen.setResetTime(gen.getResetTime() + 10);
                    player.sendMessage(ChatColor.GREEN + "Dodano 10s. Nowy czas: " + gen.getResetTime() + "s.");
                } else if (event.isRightClick()) {
                    int newTime = Math.max(10, gen.getResetTime() - 10); // Minimalny czas to 10s
                    gen.setResetTime(newTime);
                    player.sendMessage(ChatColor.GREEN + "Odjęto 10s. Nowy czas: " + gen.getResetTime() + "s.");
                }
                openEditGUI(player, gen); // Odśwież GUI
                break;
            case 12: // Toggle autoreset
                gen.setAutoResetEnabled(!gen.isAutoResetEnabled());
                player.sendMessage(ChatColor.GREEN + "Autoreset został " + (gen.isAutoResetEnabled() ? "włączony." : "wyłączony."));
                openEditGUI(player, gen); // Refresh GUI
                break;
            case 13: // Permisja kopania
                if (event.isRightClick()) {
                    gen.setMinePermission(null);
                    player.sendMessage(ChatColor.GREEN + "Usunięto ograniczenie — generator kopie każdy.");
                    openEditGUI(player, gen);
                } else {
                    pendingMinePermissionEdit.put(player.getUniqueId(), genId);
                    player.closeInventory();
                    player.sendMessage(ChatColor.YELLOW + "Wpisz na czacie permisję (np. generator.vip).");
                    player.sendMessage(ChatColor.GRAY + "Aby anulować: wpisz " + ChatColor.WHITE + "anuluj" + ChatColor.GRAY + ".");
                    player.sendMessage(ChatColor.GRAY + "Aby wyłączyć limit: " + ChatColor.WHITE + "wylacz" + ChatColor.GRAY + " lub " + ChatColor.WHITE + "off");
                }
                break;
            case 14: // Force reset
                gen.forceReset();
                player.sendMessage(ChatColor.GREEN + "Generator został zresetowany.");
                player.closeInventory();
                break;
            case 16: // Delete
                deleteGenerator(genId);
                player.sendMessage(ChatColor.GREEN + "Generator został usunięty.");
                player.closeInventory();
                break;
        }
        saveGenerators();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGeneratorBlockBreak(BlockBreakEvent event) {
        Generator gen = getGeneratorAt(event.getBlock().getLocation());
        if (gen == null) {
            return;
        }
        if (gen.canMine(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Nie masz uprawnień, aby kopać ten generator.");
        if (gen.getMinePermission() != null) {
            event.getPlayer().sendMessage(ChatColor.GRAY + "Wymagana permisja: " + ChatColor.WHITE + gen.getMinePermission());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMinePermissionChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String genId = pendingMinePermissionEdit.remove(uuid);
        if (genId == null) {
            return;
        }
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> finishMinePermissionPrompt(event.getPlayer(), genId, msg));
    }

    @EventHandler
    public void onQuitClearPending(PlayerQuitEvent event) {
        pendingMinePermissionEdit.remove(event.getPlayer().getUniqueId());
        editingGenerator.remove(event.getPlayer().getUniqueId());
    }

    private void finishMinePermissionPrompt(Player player, String genId, String msg) {
        if (msg.equalsIgnoreCase("anuluj") || msg.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "Anulowano ustawianie permisji.");
            Generator gen = generators.get(genId);
            if (gen != null) {
                openEditGUI(player, gen);
            }
            return;
        }
        Generator gen = generators.get(genId);
        if (gen == null) {
            player.sendMessage(ChatColor.RED + "Generator już nie istnieje.");
            return;
        }
        if (msg.equalsIgnoreCase("wylacz") || msg.equalsIgnoreCase("wyłącz") || msg.equalsIgnoreCase("off")
                || msg.equalsIgnoreCase("brak") || msg.equalsIgnoreCase("-")) {
            gen.setMinePermission(null);
            player.sendMessage(ChatColor.GREEN + "Usunięto ograniczenie kopania.");
        } else {
            gen.setMinePermission(msg);
            player.sendMessage(ChatColor.GREEN + "Ustawiono permisję kopania: " + ChatColor.WHITE + gen.getMinePermission());
        }
        saveGenerators();
        openEditGUI(player, gen);
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(lore.stream().map(l -> ChatColor.translateAlternateColorCodes('&', l)).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    // --- Zapis i Odczyt ---

    public void loadGenerators() {
        if (!generatorsFile.exists()) {
            return;
        }
        generatorsConfig = YamlConfiguration.loadConfiguration(generatorsFile);
        ConfigurationSection section = generatorsConfig.getConfigurationSection("generators");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            Location corner1 = section.getLocation(id + ".corner1");
            Location corner2 = section.getLocation(id + ".corner2");
            List<String> matNames = section.getStringList(id + ".materials");
            List<Material> materials = matNames.stream().map(Material::valueOf).collect(Collectors.toList());
            int resetTime = section.getInt(id + ".resetTime");
            boolean autoReset = section.getBoolean(id + ".autoReset");
            String minePermission = section.getString(id + ".minePermission");

            if (corner1 != null && !materials.isEmpty()) {
                Generator gen = new Generator(plugin, id, corner1, corner2, materials, resetTime, autoReset, minePermission);
                generators.put(id, gen);
            }
        }
    }

    public void saveGenerators() {
        generatorsConfig = new YamlConfiguration();
        for (Generator gen : generators.values()) {
            String path = "generators." + gen.getId();
            generatorsConfig.set(path + ".corner1", gen.getCorner1());
            generatorsConfig.set(path + ".corner2", gen.getCorner2());
            generatorsConfig.set(path + ".materials", gen.getMaterials().stream().map(Enum::name).collect(Collectors.toList()));
            generatorsConfig.set(path + ".resetTime", gen.getResetTime());
            generatorsConfig.set(path + ".autoReset", gen.isAutoResetEnabled());
            if (gen.getMinePermission() != null) {
                generatorsConfig.set(path + ".minePermission", gen.getMinePermission());
            }
        }
        try {
            generatorsConfig.save(generatorsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać pliku generatory.yml!");
            e.printStackTrace();
        }
    }

    public void cleanup() {
        for (Generator generator : generators.values()) {
            generator.removeHolograms();
        }
    }

    /**
     * Przeszukuje WSZYSTKIE swiaty w poszukiwaniu "osieroconych" ArmorStandow-hologramow
     * generatorow (np. po crashu serwera, recznej edycji generatory.yml, przesunieciu
     * generatora) i usuwa te, ktore nie naleza do zadnego aktualnie zaladowanego generatora.
     * Rozpoznawane po tresci nazwy wlasnej ustawianej w {@link Generator#spawnHologramLine}
     * / {@link Generator#spawnBlockDisplay} (marker=true, brak grawitacji, niewidoczny model).
     *
     * @return liczba usunietych, martwych ArmorStandow.
     */
    public int cleanOrphanedHolograms() {
        Set<UUID> zywe = new HashSet<>();
        for (Generator gen : generators.values()) {
            zywe.addAll(gen.getHologramUUIDs());
        }

        NamespacedKey key = new NamespacedKey(plugin, Generator.HOLO_TAG);
        int usuniete = 0;
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : new ArrayList<>(world.getEntities())) {
                if (!(entity instanceof ArmorStand as)) continue;
                if (zywe.contains(as.getUniqueId())) continue;
                if (!as.isMarker()) continue;

                boolean tag = as.getPersistentDataContainer().has(key, PersistentDataType.STRING);
                String name = as.getCustomName();
                boolean tekst = name != null && (name.contains("Generator zresetuje się za") || name.contains("Reset wyłączony"));
                boolean kopiaBloku = !tag && !tekst && Generator.jestHologramemGeneratora(as, key)
                        && bliskoHologramuGeneratora(as.getLocation());
                if (tag || tekst || kopiaBloku) {
                    as.remove();
                    usuniete++;
                }
            }
        }
        return usuniete;
    }

    private boolean bliskoHologramuGeneratora(Location loc) {
        if (loc.getWorld() == null) {
            return false;
        }
        for (Generator gen : generators.values()) {
            Location holo = gen.hologramLocation();
            if (holo.getWorld() != null && holo.getWorld().equals(loc.getWorld())
                    && holo.distanceSquared(loc) <= 36.0) {
                return true;
            }
        }
        return false;
    }
}