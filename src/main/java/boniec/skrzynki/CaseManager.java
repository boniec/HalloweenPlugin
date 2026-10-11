package boniec.skrzynki;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dane i logika systemu skrzynek.
 *  - skrzynki-config.yml : ustawienia globalne (GUI podgladu, animacja, wiadomosci) - NIE jest nadpisywany przez plugin,
 *  - skrzynki.yml        : skrzynki (typ, klucz, lokalizacje, dropy) - zapisywany przez komendy /acase.
 * Kazda sekcja z configu globalnego mozna nadpisac dla jednej skrzynki: skrzynki.<id>.<sciezka>.
 */
public final class CaseManager {

    /** Oczekiwanie na wpisanie wagi dropu na czacie. */
    record Pending(CaseDef def, CaseDrop drop) {
    }

    private final JavaPlugin plugin;
    private final File dataFile;
    private YamlConfiguration cfg = new YamlConfiguration();
    private YamlConfiguration data = new YamlConfiguration();

    private final Map<String, CaseDef> cases = new LinkedHashMap<>();
    private final Map<String, String> locations = new HashMap<>();
    private final Map<UUID, CaseAnimation> running = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public CaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "skrzynki.yml");
    }

    JavaPlugin plugin() {
        return plugin;
    }

    // ------------------------------------------------------------------ wczytywanie / zapis

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        File cfgFile = new File(plugin.getDataFolder(), "skrzynki-config.yml");
        if (!cfgFile.exists()) plugin.saveResource("skrzynki-config.yml", false);
        cfg = YamlConfiguration.loadConfiguration(cfgFile);
        data = dataFile.exists() ? YamlConfiguration.loadConfiguration(dataFile) : new YamlConfiguration();

        cases.clear();
        ConfigurationSection root = data.getConfigurationSection("skrzynki");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                CaseDef c = readCase(id, s);
                cases.put(c.id, c);
            }
        }
        rebuildLocations();
        plugin.getLogger().info("Skrzynki: wczytano " + cases.size() + " skrzynek.");
    }

    public void reload() {
        shutdown();
        load();
    }

    private CaseDef readCase(String id, ConfigurationSection s) {
        CaseDef c = new CaseDef(id.toLowerCase(Locale.ROOT));
        c.displayName = s.getString("nazwa", "&6" + id);
        c.type = CaseType.parse(s.getString("typ"), CaseType.SKRZYNIA);
        c.key = ItemSpec.from(s.getConfigurationSection("klucz"), Material.TRIPWIRE_HOOK);
        c.keyInHand = s.getBoolean("klucz.z-reki", false);
        c.locations.addAll(s.getStringList("lokalizacje"));
        c.enabled = s.getBoolean("wlaczona", true);

        ConfigurationSection ds = s.getConfigurationSection("drops");
        if (ds != null) {
            List<String> keys = new ArrayList<>(ds.getKeys(false));
            keys.sort(Comparator.comparingInt(CaseManager::parseIntSafe));
            for (String k : keys) {
                ConfigurationSection d = ds.getConfigurationSection(k);
                if (d == null) continue;
                ItemStack item = d.getItemStack("item");
                if (item == null || item.getType().isAir()) continue;
                c.drops.add(new CaseDrop(item, d.getDouble("waga", 1.0), d.getBoolean("ogloszenie", false),
                        d.getInt("slot", -1)));
            }
        }
        return c;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private void writeCase(CaseDef c) {
        String p = "skrzynki." + c.id + ".";
        data.set(p + "nazwa", c.displayName);
        data.set(p + "typ", c.type.name());

        data.set(p + "klucz", null);
        ConfigurationSection ks = data.createSection(p + "klucz");
        c.key.saveTo(ks);
        ks.set("z-reki", c.keyInHand);

        data.set(p + "lokalizacje", new ArrayList<>(c.locations));
        data.set(p + "wlaczona", c.enabled);

        data.set(p + "drops", null);
        int i = 0;
        for (CaseDrop d : c.drops) {
            String dp = p + "drops." + i + ".";
            data.set(dp + "item", d.item);
            data.set(dp + "waga", d.weight);
            data.set(dp + "ogloszenie", d.announce);
            data.set(dp + "slot", d.slot);
            i++;
        }
    }

    public void save() {
        for (CaseDef c : cases.values()) writeCase(c);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udalo sie zapisac skrzynki.yml: " + e.getMessage());
        }
    }

    /** Konczy trwajace animacje (nagrody wydawane od razu) i czyszczy stan - przy wylaczaniu / reloadzie. */
    public void shutdown() {
        for (CaseAnimation a : new ArrayList<>(running.values())) a.finishNow();
        running.clear();
        pending.clear();
    }

    // ------------------------------------------------------------------ skrzynki

    CaseDef get(String id) {
        return id == null ? null : cases.get(id.toLowerCase(Locale.ROOT));
    }

    Collection<CaseDef> all() {
        return cases.values();
    }

    public boolean istnieje(String caseId) {
        return get(caseId) != null;
    }

    /** Daje klucz skrzynki o podanym ID (nadmiar ląduje na ziemi). @return nazwa klucza albo null, gdy brak skrzynki. */
    public String dajKlucz(org.bukkit.entity.Player p, String caseId, int amount) {
        CaseDef c = get(caseId);
        if (c == null || c.key == null) return null;
        org.bukkit.inventory.ItemStack stack = c.key.build(basePlaceholders(c), false);
        stack.setAmount(Math.max(1, amount));
        for (org.bukkit.inventory.ItemStack rest : p.getInventory().addItem(stack).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        return c.keyLabel();
    }

    CaseDef create(String id, CaseType type) {
        CaseDef c = new CaseDef(id.toLowerCase(Locale.ROOT));
        c.type = type;
        c.displayName = "&6" + id;
        c.key = new ItemSpec(Material.TRIPWIRE_HOOK, "&6Klucz: " + c.displayName, new ArrayList<>(), null, false, 1);
        cases.put(c.id, c);
        save();
        return c;
    }

    void delete(CaseDef c) {
        cases.remove(c.id);
        data.set("skrzynki." + c.id, null);
        rebuildLocations();
        save();
    }

    // ------------------------------------------------------------------ lokalizacje

    static String locKey(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void rebuildLocations() {
        locations.clear();
        for (CaseDef c : cases.values()) {
            for (String l : c.locations) locations.put(l, c.id);
        }
    }

    /** Skrzynka zarejestrowana na tym bloku (SKRZYNIA albo LOOTBOX-podglad) albo null. */
    CaseDef caseAt(Block b) {
        if (locations.isEmpty() || b == null) return null;
        String id = locations.get(locKey(b));
        if (id == null) return null;
        return cases.get(id);
    }

    /** @return false, jesli ten blok jest juz lokalizacja jakiejs skrzynki. */
    boolean addLocation(CaseDef c, Block b) {
        String k = locKey(b);
        if (locations.containsKey(k)) return false;
        c.locations.add(k);
        rebuildLocations();
        save();
        return true;
    }

    boolean removeLocation(CaseDef c, Block b) {
        boolean removed = c.locations.remove(locKey(b));
        if (removed) {
            rebuildLocations();
            save();
        }
        return removed;
    }

    int clearLocations(CaseDef c) {
        int n = c.locations.size();
        c.locations.clear();
        rebuildLocations();
        save();
        return n;
    }

    CaseDef findLootbox(ItemStack stack) {
        for (CaseDef c : cases.values()) {
            if (c.type == CaseType.LOOTBOX && c.key != null && c.key.matches(stack)) return c;
        }
        return null;
    }

    // ------------------------------------------------------------------ config (globalny + nadpisania per skrzynka)

    private ConfigurationSection caseSec(CaseDef c) {
        return c == null ? null : data.getConfigurationSection("skrzynki." + c.id);
    }

    String str(CaseDef c, String path, String def) {
        ConfigurationSection cs = caseSec(c);
        if (cs != null && cs.isSet(path)) return cs.getString(path, def);
        return cfg.getString(path, def);
    }

    int integer(CaseDef c, String path, int def) {
        ConfigurationSection cs = caseSec(c);
        if (cs != null && cs.isSet(path)) return cs.getInt(path, def);
        return cfg.getInt(path, def);
    }

    boolean bool(CaseDef c, String path, boolean def) {
        ConfigurationSection cs = caseSec(c);
        if (cs != null && cs.isSet(path)) return cs.getBoolean(path, def);
        return cfg.getBoolean(path, def);
    }

    List<String> stringList(CaseDef c, String path) {
        ConfigurationSection cs = caseSec(c);
        if (cs != null && cs.isList(path)) return cs.getStringList(path);
        return cfg.getStringList(path);
    }

    List<Integer> intList(CaseDef c, String path) {
        ConfigurationSection cs = caseSec(c);
        if (cs != null && cs.isList(path)) return cs.getIntegerList(path);
        return cfg.getIntegerList(path);
    }

    /** Lista map z configu (np. itemDecorations) jako lista sekcji. */
    List<ConfigurationSection> sectionList(CaseDef c, String path) {
        ConfigurationSection cs = caseSec(c);
        List<Map<?, ?>> raw = cs != null && cs.isList(path) ? cs.getMapList(path) : cfg.getMapList(path);
        List<ConfigurationSection> out = new ArrayList<>();
        int i = 0;
        for (Map<?, ?> m : raw) {
            out.add(new MemoryConfiguration().createSection("e" + i++, m));
        }
        return out;
    }

    /**
     * Dekoracje GUI w formacie:
     *   itemDecorations:
     *   - item: {material: ..., displayname: ..., lore: [...], glow: false, modelData: 0}
     *     slots: [0, 8, 36]
     */
    void applyDecorations(Inventory inv, CaseDef c, String path, Map<String, String> ph) {
        for (ConfigurationSection s : sectionList(c, path)) {
            ConfigurationSection itemSec = s.getConfigurationSection("item");
            if (itemSec == null) continue;
            String mat = itemSec.getString("material", "");
            if (mat.isEmpty() || mat.equalsIgnoreCase("AIR")) continue;
            ItemStack it = ItemSpec.from(itemSec, Material.STONE).build(ph, true);
            for (int slot : s.getIntegerList("slots")) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, it.clone());
            }
        }
    }

    ConfigurationSection section(CaseDef c, String path) {
        ConfigurationSection cs = caseSec(c);
        if (cs != null && cs.isConfigurationSection(path)) return cs.getConfigurationSection(path);
        return cfg.getConfigurationSection(path);
    }

    // ------------------------------------------------------------------ wiadomosci / dzwieki

    Map<String, String> basePlaceholders(CaseDef c) {
        return Txt.map("skrzynka", c.displayName, "klucz", c.keyLabel());
    }

    String msg(CaseDef c, String key, Map<String, String> ph) {
        String raw = str(c, "wiadomosci." + key, "");
        if (raw.isEmpty()) return "";
        String prefix = str(c, "wiadomosci.prefix", "");
        return Txt.color(prefix + Txt.replace(raw, ph));
    }

    void send(Player p, CaseDef c, String key, Map<String, String> ph) {
        String m = msg(c, key, ph);
        if (!m.isEmpty()) p.sendMessage(m);
    }

    /** Dzwiek z configu: nazwa Sound (np. ENTITY_VILLAGER_NO) albo klucz vanilla (np. entity.villager.no). */
    void playConfigSound(Player p, CaseDef c, String path) {
        playSound(p, str(c, path, ""));
    }

    void playSound(Player p, String name) {
        if (name == null || name.isBlank()) return;
        String n = name.trim();
        try {
            if (n.indexOf('.') >= 0 || n.indexOf(':') >= 0) {
                p.playSound(p.getLocation(), n.toLowerCase(Locale.ROOT), 1f, 1f);
                return;
            }
            Sound sound = null;
            try {
                sound = Sound.valueOf(n.toUpperCase(Locale.ROOT));
            } catch (Throwable ignored) {
                // inna wersja API / literowka - probujemy jako klucz vanilla
            }
            if (sound != null) p.playSound(p.getLocation(), sound, 1f, 1f);
            else p.playSound(p.getLocation(), n.toLowerCase(Locale.ROOT).replace('_', '.'), 1f, 1f);
        } catch (Throwable ignored) {
            // zla nazwa dzwieku w configu - nie psujemy otwierania
        }
    }

    static String itemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return Txt.humanize(stack.getType());
    }

    // ------------------------------------------------------------------ losowanie

    double totalWeight(CaseDef c) {
        double total = 0;
        for (CaseDrop d : c.drops) if (d.weight > 0) total += d.weight;
        return total;
    }

    CaseDrop roll(CaseDef c) {
        double total = totalWeight(c);
        if (total <= 0) return c.drops.get(random.nextInt(c.drops.size()));
        double r = random.nextDouble() * total;
        for (CaseDrop d : c.drops) {
            if (d.weight <= 0) continue;
            r -= d.weight;
            if (r < 0) return d;
        }
        return c.drops.get(c.drops.size() - 1);
    }

    // ------------------------------------------------------------------ klucze

    /** @return slot z kluczem albo -1. Lootbox = reka. Skrzynia = caly ekwipunek (nie trzeba trzymac). */
    int findKeySlot(Player p, CaseDef c) {
        if (c.key == null) return -1;
        PlayerInventory inv = p.getInventory();
        if (c.type == CaseType.LOOTBOX) {
            int held = inv.getHeldItemSlot();
            ItemStack it = inv.getItem(held);
            return it != null && c.key.matches(it) ? held : -1;
        }
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && c.key.matches(contents[i])) return i;
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && c.key.matches(off)) return 40;
        return -1;
    }

    boolean hasKey(Player p, CaseDef c) {
        return findKeySlot(p, c) >= 0;
    }

    private void consumeKey(Player p, int slot) {
        PlayerInventory inv = p.getInventory();
        ItemStack it = slot == 40 ? inv.getItemInOffHand() : inv.getItem(slot);
        if (it == null) return;
        if (it.getAmount() <= 1) {
            if (slot == 40) inv.setItemInOffHand(null);
            else inv.setItem(slot, null);
        } else {
            it.setAmount(it.getAmount() - 1);
        }
    }

    boolean cooldownOk(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < 250L) return false;
        lastUse.put(p.getUniqueId(), now);
        return true;
    }

    void forget(UUID id) {
        lastUse.remove(id);
        pending.remove(id);
    }

    // ------------------------------------------------------------------ otwieranie

    /**
     * Otwiera skrzynke: zabiera 1 klucz, losuje nagrode (wynik jest ustalony od razu), a potem pokazuje animacje
     * albo (instant / animacja wylaczona) wydaje nagrode od razu.
     */
    void tryOpen(Player p, CaseDef c, boolean instant, boolean all) {
        if (!c.enabled) {
            Map<String, String> ph = basePlaceholders(c);
            String m = msg(c, "wylaczona", ph);
            if (m.isEmpty()) {
                p.sendMessage(Txt.color("&8[&6Skrzynki&8] &cTa skrzynka jest obecnie wylaczona."));
            } else {
                p.sendMessage(m);
            }
            return;
        }
        if (running.containsKey(p.getUniqueId())) {
            send(p, c, "zajety", basePlaceholders(c));
            return;
        }
        if (c.drops.isEmpty()) {
            send(p, c, "brak-dropu", basePlaceholders(c));
            return;
        }
        int slot = findKeySlot(p, c);
        if (slot < 0) {
            playConfigSound(p, c, "noKeySound");
            send(p, c, "brak-klucza", basePlaceholders(c));
            return;
        }

        if (instant || !bool(c, "animationEnabled", true)) {
            int count = 1;
            if (instant && all && bool(c, "quickOpenAll", false)) {
                ItemStack stack = p.getInventory().getItem(slot);
                int max = Math.max(1, integer(c, "quickOpenMax", 64));
                count = stack == null ? 1 : Math.max(1, Math.min(stack.getAmount(), max));
            }
            for (int i = 0; i < count; i++) {
                consumeKey(p, slot);
                award(p, c, roll(c));
            }
            return;
        }

        consumeKey(p, slot);
        CaseDrop win = roll(c);
        CaseAnimation anim = new CaseAnimation(this, p, c, win);
        running.put(p.getUniqueId(), anim);
        anim.start();
    }

    void unregister(UUID id) {
        running.remove(id);
    }

    /** Konczy trwajaca animacje gracza (jesli jest) wydajac nagrode od razu. */
    void finishAnimation(UUID id) {
        CaseAnimation a = running.get(id);
        if (a != null) a.finishNow();
    }

    /** Wydaje nagrode + wiadomosci (gracz, czat), title i dzwiek. */
    void award(Player p, CaseDef c, CaseDrop win) {
        ItemStack reward = win.item.clone();
        Map<Integer, ItemStack> left = p.getInventory().addItem(reward);
        boolean overflow = false;
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
            overflow = true;
        }

        Map<String, String> ph = basePlaceholders(c);
        ph.put("gracz", p.getName());
        ph.put("przedmiot", itemName(win.item));
        ph.put("ilosc", String.valueOf(win.item.getAmount()));

        if (overflow) send(p, c, "przepelnienie", ph);
        send(p, c, "otwarcie-gracz", ph);

        String mode = str(c, "broadcast", "ALL").toUpperCase(Locale.ROOT);
        boolean broadcast = mode.equals("ALL") || mode.equals("WSZYSTKIE")
                || ((mode.equals("MARKED") || mode.equals("OZNACZONE")) && win.announce);
        if (broadcast) {
            String m = msg(c, "otwarcie-broadcast", ph);
            if (!m.isEmpty()) {
                Bukkit.getConsoleSender().sendMessage(m);
                for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                    if (online.equals(p)
                            || boniec.ustawienia.UstawieniaManager.on(online, boniec.ustawienia.UstawieniaKlucz.SKRZYNKI)) {
                        online.sendMessage(m);
                    }
                }
            }
        }

        // lootbox: bez title/subtitle po wylosowaniu
        if (c.type != CaseType.LOOTBOX && bool(c, "wygrana.title", true)) {
            p.sendTitle(
                    Txt.color(Txt.replace(str(c, "wygrana.tytul", "&6&lWYGRANA!"), ph)),
                    Txt.color(Txt.replace(str(c, "wygrana.podtytul", "{przedmiot} &7x{ilosc}"), ph)),
                    5, 40, 10);
        }
        playConfigSound(p, c, "winItemSound");
    }
}
