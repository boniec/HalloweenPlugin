package boniec.generatory;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public class Generator {

    private final String id;
    private final JavaPlugin plugin;
    private final Location corner1;
    private final Location corner2;
    private final List<Material> materials;
    private int resetTime;
    private boolean autoResetEnabled;
    /** Pusta = kopanie dla wszystkich; inaczej wymagane player.hasPermission(...). */
    private String minePermission;
    private int currentCountdown;

    private final List<ArmorStand> holograms = new ArrayList<>();
    private static final Random random = new Random();
    static final String HOLO_TAG = "gen_holo";

    /**
     * Przetlumaczony raz tekst hologramu dla generatora z wylaczonym resetem.
     * ChatColor.translateAlternateColorCodes jest wolane RAZ przy zaladowaniu klasy
     * zamiast przy kazdym ticku (co sekunde) dla kazdego generatora z autoReset=false.
     */
    private static final String HOLOGRAM_DISABLED =
            ChatColor.translateAlternateColorCodes('&', "&cReset wyłączony");

    public Generator(JavaPlugin plugin, String id, Location corner1, Location corner2, List<Material> materials, int resetTime, boolean autoResetEnabled) {
        this(plugin, id, corner1, corner2, materials, resetTime, autoResetEnabled, null);
    }

    public Generator(JavaPlugin plugin, String id, Location corner1, Location corner2, List<Material> materials, int resetTime, boolean autoResetEnabled, String minePermission) {
        this.id = id;
        this.plugin = plugin;
        // Upewnij się, że corner1 to minimalny punkt, a corner2 to maksymalny
        this.corner1 = new Location(corner1.getWorld(), Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()), Math.min(corner1.getZ(), corner2.getZ()));
        this.corner2 = new Location(corner1.getWorld(), Math.max(corner1.getX(), corner2.getX()), Math.max(corner1.getY(), corner2.getY()), Math.max(corner1.getZ(), corner2.getZ()));
        this.materials = materials;
        this.resetTime = resetTime;
        this.autoResetEnabled = autoResetEnabled;
        this.minePermission = normalizeMinePermission(minePermission);
        this.currentCountdown = resetTime;
        createHolograms();
    }

    private static String normalizeMinePermission(String perm) {
        if (perm == null) {
            return null;
        }
        String trimmed = perm.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // --- Gettery i Settery ---
    public String getId() { return id; }
    public Location getCorner1() { return corner1; }
    public Location getCorner2() { return corner2; }
    public List<Material> getMaterials() { return materials; }
    public int getResetTime() { return resetTime; }
    public boolean isAutoResetEnabled() { return autoResetEnabled; }
    public int getCurrentCountdown() { return currentCountdown; }

    public void setResetTime(int resetTime) { this.resetTime = resetTime; }
    public void setAutoResetEnabled(boolean autoResetEnabled) { this.autoResetEnabled = autoResetEnabled; }

    public String getMinePermission() {
        return minePermission;
    }

    public void setMinePermission(String minePermission) {
        this.minePermission = normalizeMinePermission(minePermission);
    }

    public boolean hasMineRestriction() {
        return minePermission != null;
    }

    /** Admin permisja omija ograniczenie. */
    public boolean canMine(org.bukkit.entity.Player player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermission("core.admingenerator")) {
            return true;
        }
        if (minePermission == null) {
            return true;
        }
        return maUprawnienieKopaniaGeneratora(player, minePermission);
    }

    /**
     * Dokladna permisja generatora (np. {@code kopanie.szak}) albo nadrzedne {@code kopanie} / {@code vip}
     * (VIP ma oba w LuckPerms — bez tego nie przechodzi tylko {@code hasPermission("kopanie.szak")}).
     */
    public static boolean maUprawnienieKopaniaGeneratora(org.bukkit.entity.Player player, String wymagana) {
        if (player == null || wymagana == null || wymagana.isEmpty()) {
            return true;
        }
        if (player.hasPermission(wymagana)) {
            return true;
        }
        String req = wymagana.toLowerCase(Locale.ROOT);
        if (req.startsWith("kopanie.")) {
            if (player.hasPermission("kopanie")) {
                return true;
            }
            if (player.hasPermission("vip")) {
                return true;
            }
        }
        if ("kopanie".equals(req) && player.hasPermission("vip")) {
            return true;
        }
        return false;
    }

    // --- Logika Generatora ---

    public void forceReset() {
        World world = corner1.getWorld();
        if (world == null) return;

        int minX = corner1.getBlockX();
        int maxX = corner2.getBlockX();
        int minY = corner1.getBlockY();
        int maxY = corner2.getBlockY();
        int minZ = corner1.getBlockZ();
        int maxZ = corner2.getBlockZ();

        // Budujemy listę "linii" (rzędów wzdłuż osi X) posortowanych warstwami od
        // dołu do góry (Y), a w każdej warstwie od Z=minZ do Z=maxZ. Każda linia
        // to jeden rząd bloków o stałym Y i Z, zmieniającym się X.
        // Kierunek wypełniania naprzemiennie ZALEŻNIE OD WARSTWY (Y): cała warstwa
        // Y=0 leci od lewej do prawej (X rosnąco), cała warstwa Y=1 od prawej do
        // lewej (X malejąco), warstwa Y=2 znów od lewej itd. - klasyczny efekt
        // "wężyka" warstwa po warstwie.
        List<List<Location>> lines = new ArrayList<>();
        int layerIndex = 0;
        for (int y = minY; y <= maxY; y++) {
            boolean leftToRight = (layerIndex % 2 == 0);
            for (int z = minZ; z <= maxZ; z++) {
                List<Location> line = new ArrayList<>();
                if (leftToRight) {
                    for (int x = minX; x <= maxX; x++) {
                        line.add(new Location(world, x, y, z));
                    }
                } else {
                    for (int x = maxX; x >= minX; x--) {
                        line.add(new Location(world, x, y, z));
                    }
                }
                lines.add(line);
            }
            layerIndex++;
        }

        if (lines.isEmpty()) return;

        // Cały generator ma się wypełnić w ~3 sekundy (60 ticków), niezależnie od
        // liczby linii - rozkładamy więc równomiernie czas trwania jednego ticka
        // animacji na wszystkie linie (minimum 1 tick na linię).
        final int totalDurationTicks = 60; // 3 sekundy
        final long ticksPerLine = Math.max(1L, totalDurationTicks / lines.size());

        new BukkitRunnable() {
            private int currentLine = 0;

            @Override
            public void run() {
                if (currentLine >= lines.size()) {
                    this.cancel();
                    return;
                }

                List<Location> line = lines.get(currentLine);
                for (Location loc : line) {
                    Material newMaterial = materials.get(random.nextInt(materials.size()));
                    loc.getBlock().setType(newMaterial);
                }
                // Jeden dzwiek na cala linie zamiast jednego na KAZDY blok w niej -
                // przy szerokich generatorach (dlugie linie X) odtwarzanie tego samego
                // dzwieku dziesiatki razy w tym samym ticku nie jest slyszalnie inne
                // dla gracza, a generuje zbedne pakiety dzwiekowe do kazdego obserwatora.
                if (!line.isEmpty()) {
                    world.playSound(line.get(0), Sound.BLOCK_STONE_PLACE, 0.5f, 1.5f);
                }
                currentLine++;
            }
        }.runTaskTimer(plugin, 0L, ticksPerLine);

        this.currentCountdown = this.resetTime;
        updateHologram();
    }

    public void tick() {
        if (!autoResetEnabled) {
            if (currentCountdown != -1) {
                currentCountdown = -1; // Ustaw na -1, aby hologram pokazywał "Wyłączone"
                updateHologram();
            }
            return;
        }

        if (currentCountdown > 0) {
            currentCountdown--;
        } else if (currentCountdown == 0) {
            forceReset();
        }
        if (chunkHologramuZaladowany()) {
            updateHologram();
        }
    }

    // --- Hologramy ---

    public Location hologramLocation() {
        return new Location(
                corner1.getWorld(),
                (corner1.getX() + corner2.getX()) / 2.0 + 0.5,
                corner2.getY() + 1.0,
                (corner1.getZ() + corner2.getZ()) / 2.0 + 0.5
        );
    }

    private NamespacedKey holoKey() {
        return new NamespacedKey(plugin, HOLO_TAG);
    }

    static boolean jestHologramemGeneratora(ArmorStand as, NamespacedKey key) {
        if (as == null || !as.isValid()) {
            return false;
        }
        if (as.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return true;
        }
        if (!as.isMarker() || as.hasGravity()) {
            return false;
        }
        String name = as.getCustomName();
        if (name != null && (name.contains("Generator zresetuje się za") || name.contains("Reset wyłączony"))) {
            return true;
        }
        return as.isSmall()
                && as.getEquipment() != null
                && as.getEquipment().getHelmet() != null
                && !as.getEquipment().getHelmet().getType().isAir();
    }

    public void ensureHolograms() {
        if (hologramyAktualne()) {
            usunDuplikatyWokol();
            return;
        }
        createHolograms();
    }

    private boolean hologramyAktualne() {
        if (holograms.size() < 2) {
            return false;
        }
        for (ArmorStand as : holograms) {
            if (as == null || !as.isValid() || as.isDead()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hologramy sa nietrwale (setPersistent(false)) - znikaja przy wyladowaniu chunka.
     * Bez tej kontroli tick() co sekunde ladowal chunk i spawnowal je od nowa;
     * odbudowe przy powrocie gracza robi GeneratorManager.onChunkLoad.
     */
    private boolean chunkHologramuZaladowany() {
        World world = corner1.getWorld();
        if (world == null) {
            return false;
        }
        int x = (int) Math.floor((corner1.getX() + corner2.getX()) / 2.0 + 0.5);
        int z = (int) Math.floor((corner1.getZ() + corner2.getZ()) / 2.0 + 0.5);
        return world.isChunkLoaded(x >> 4, z >> 4);
    }

    private void createHolograms() {
        if (!chunkHologramuZaladowany()) {
            holograms.clear();
            return;
        }
        removeHolograms();
        Location hologramBaseLoc = hologramLocation();
        if (hologramBaseLoc.getWorld() == null) {
            return;
        }

        holograms.add(spawnBlockDisplay(hologramBaseLoc.clone().add(0, 0.5, 0), materials.get(0)));
        holograms.add(spawnHologramLine(hologramBaseLoc, ""));
        if (hologramyAktualne()) {
            aktualizujTekst();
        }
    }

    public void updateHologram() {
        if (!hologramyAktualne()) {
            createHolograms();
            return;
        }
        aktualizujTekst();
    }

    private void aktualizujTekst() {
        if (holograms.size() < 2) return;

        // Aktualizacja nazwy bloku (jeśli się zmieni)
        ArmorStand blockDisplay = holograms.get(0);
        if (blockDisplay.getEquipment().getHelmet() == null || blockDisplay.getEquipment().getHelmet().getType() != materials.get(0)) {
            blockDisplay.getEquipment().setHelmet(new ItemStack(materials.get(0)));
        }

        // Aktualizacja czasu
        String timeLine;
        if (!autoResetEnabled) {
            timeLine = HOLOGRAM_DISABLED; // stala, zero alokacji
        } else {
            timeLine = ChatColor.translateAlternateColorCodes('&', "&6⌚ &eGenerator zresetuje się za &6" + currentCountdown + "&e sekund!");
        }

        // setCustomName wysyla pakiet do kazdego obserwujacego gracza - wolane
        // co sekunde z tick() dla KAZDEGO generatora na mapie, wiec pomijamy
        // wywolanie calkowicie, jesli tekst faktycznie sie nie zmienil (co
        // dotyczy m.in. wywolan gdy autoResetEnabled=false i tekst to zawsze
        // ten sam "Reset wylaczony").
        ArmorStand textLine = holograms.get(1);
        if (!timeLine.equals(textLine.getCustomName())) {
            textLine.setCustomName(timeLine);
        }
    }

    private void oznaczHologram(ArmorStand as) {
        as.setGravity(false);
        as.setVisible(false);
        as.setMarker(true);
        as.setInvulnerable(true);
        as.setCollidable(false);
        as.setPersistent(false);
        as.getPersistentDataContainer().set(holoKey(), PersistentDataType.STRING, id);
    }

    private ArmorStand spawnHologramLine(Location loc, String text) {
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        oznaczHologram(as);
        as.setCustomNameVisible(true);
        as.setCustomName(text);
        return as;
    }

    private ArmorStand spawnBlockDisplay(Location loc, Material material) {
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        oznaczHologram(as);
        as.setSmall(true);
        if (as.getEquipment() != null) {
            as.getEquipment().setHelmet(new ItemStack(material));
        }
        return as;
    }

    public void usunDuplikatyWokol() {
        Location loc = hologramLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        NamespacedKey key = holoKey();
        Set<UUID> nasze = getHologramUUIDs();
        for (Entity entity : world.getNearbyEntities(loc, 3.0, 5.0, 3.0)) {
            if (!(entity instanceof ArmorStand as)) {
                continue;
            }
            if (nasze.contains(as.getUniqueId())) {
                continue;
            }
            if (jestHologramemGeneratora(as, key)) {
                as.remove();
            }
        }
    }

    public void removeHolograms() {
        for (ArmorStand as : holograms) {
            if (as != null) {
                as.remove();
            }
        }
        holograms.clear();
        usunDuplikatyWokol();
    }

    /** UUID-y aktualnie należących do tego generatora hologramów - używane przy globalnym sprzątaniu śmieci. */
    public Set<UUID> getHologramUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        for (ArmorStand as : holograms) {
            if (as != null) {
                uuids.add(as.getUniqueId());
            }
        }
        return uuids;
    }
}