package boniec.code.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Odpowiednik funkcji Skriptowych: compareItems + zmienne {upg.*} + zapis/odczyt do pliku.
 * Kazda kategoria trzyma liste itemow (poziomow) 1..N oraz mape poziom->cena.
 */
public class UpgradeManager {

    private final JavaPlugin plugin;
    private File file;
    private YamlConfiguration cfg;

    // nazwa kategorii -> lista itemow (index 0 = poziom 1)
    private final Map<String, List<ItemStack>> categoryItems = new LinkedHashMap<>();
    // nazwa kategorii -> mapa poziom(1-based) -> cena
    private final Map<String, Map<Integer, Double>> categoryPrices = new LinkedHashMap<>();
    // nazwa kategorii -> slot w glownym menu /ulepsz (brak wpisu = uklad automatyczny)
    private final Map<String, Integer> categorySlots = new LinkedHashMap<>();
    /** Odciski zapisanych poziomow — bez clone/getItemMeta przy kazdym porownaniu. */
    private final Map<String, List<ItemOdcisk>> odliski = new LinkedHashMap<>();

    public UpgradeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "ulepszenia.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udalo sie utworzyc ulepszenia.yml: " + e.getMessage());
            }
        }
        cfg = YamlConfiguration.loadConfiguration(file);

        categoryItems.clear();
        categoryPrices.clear();
        categorySlots.clear();

        ConfigurationSection catsSection = cfg.getConfigurationSection("kategorie");
        if (catsSection == null) return;

        for (String cat : catsSection.getKeys(false)) {
            List<ItemStack> items = new ArrayList<>();
            Map<Integer, Double> prices = new LinkedHashMap<>();

            ConfigurationSection catSection = catsSection.getConfigurationSection(cat);
            if (catSection == null) {
                categoryItems.put(cat, items);
                categoryPrices.put(cat, prices);
                continue;
            }

            if (catSection.contains("slot")) {
                int savedSlot = catSection.getInt("slot", -1);
                if (savedSlot >= 0 && savedSlot < 54) categorySlots.put(cat, savedSlot);
            }

            List<?> rawItems = catSection.getList("items");
            if (rawItems != null) {
                for (Object o : rawItems) {
                    if (o instanceof ItemStack) {
                        items.add((ItemStack) o);
                    }
                }
            }

            ConfigurationSection priceSection = catSection.getConfigurationSection("prices");
            if (priceSection != null) {
                for (String key : priceSection.getKeys(false)) {
                    try {
                        int lvl = Integer.parseInt(key);
                        prices.put(lvl, priceSection.getDouble(key));
                    } catch (NumberFormatException ignored) {}
                }
            }

            categoryItems.put(cat, items);
            categoryPrices.put(cat, prices);
        }
        przebudujOdliski();
    }

    private void przebudujOdliski() {
        odliski.clear();
        for (Map.Entry<String, List<ItemStack>> e : categoryItems.entrySet()) {
            List<ItemOdcisk> lista = new ArrayList<>(e.getValue().size());
            for (ItemStack it : e.getValue()) lista.add(ItemOdcisk.z(it));
            odliski.put(e.getKey(), lista);
        }
    }

    public void save() {
        cfg = new YamlConfiguration();
        for (String cat : categoryItems.keySet()) {
            String base = "kategorie." + cat;
            Integer slot = categorySlots.get(cat);
            if (slot != null) cfg.set(base + ".slot", slot);
            cfg.set(base + ".items", categoryItems.get(cat));
            Map<Integer, Double> prices = categoryPrices.getOrDefault(cat, new LinkedHashMap<>());
            for (Map.Entry<Integer, Double> e : prices.entrySet()) {
                cfg.set(base + ".prices." + e.getKey(), e.getValue());
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udalo sie zapisac ulepszenia.yml: " + e.getMessage());
        }
    }

    // ── Kategorie ─────────────────────────────────────────────────────────

    public boolean categoryExists(String name) {
        return categoryItems.containsKey(name);
    }

    public void createCategory(String name) {
        createCategory(name, null);
    }

    /** @param slot slot w glownym menu /ulepsz (0-53) albo null = uklad automatyczny */
    public void createCategory(String name, Integer slot) {
        categoryItems.put(name, new ArrayList<>());
        categoryPrices.put(name, new LinkedHashMap<>());
        odliski.put(name, new ArrayList<>());
        if (slot != null) categorySlots.put(name, slot);
        save();
    }

    public void removeCategory(String name) {
        categoryItems.remove(name);
        categoryPrices.remove(name);
        categorySlots.remove(name);
        odliski.remove(name);
        save();
    }

    /** Slot kategorii w glownym menu albo null, jesli nie ustawiono. */
    public Integer getSlot(String cat) {
        return categorySlots.get(cat);
    }

    /** Kategoria zajmujaca dany slot (inna niz {@code except}) albo null. */
    public String getCategoryAtSlot(int slot, String except) {
        for (Map.Entry<String, Integer> e : categorySlots.entrySet()) {
            if (e.getValue() == slot && !e.getKey().equals(except)) return e.getKey();
        }
        return null;
    }

    public void setSlot(String cat, Integer slot) {
        if (slot == null) categorySlots.remove(cat);
        else categorySlots.put(cat, slot);
        save();
    }

    public List<String> getCategoryNames() {
        return new ArrayList<>(categoryItems.keySet());
    }

    public List<ItemStack> getItems(String cat) {
        return categoryItems.getOrDefault(cat, new ArrayList<>());
    }

    public void setItems(String cat, List<ItemStack> items) {
        categoryItems.put(cat, items);
        List<ItemOdcisk> lista = new ArrayList<>(items.size());
        for (ItemStack it : items) lista.add(ItemOdcisk.z(it));
        odliski.put(cat, lista);
        save();
    }

    public ItemStack peekItem(String cat, int level) {
        List<ItemStack> items = categoryItems.get(cat);
        if (items == null || level < 1 || level > items.size()) return null;
        return items.get(level - 1);
    }

    public ItemStack getItem(String cat, int level) {
        ItemStack peek = peekItem(cat, level);
        return peek == null ? null : peek.clone();
    }

    public ItemOdcisk[] odciskiEkwipunku(ItemStack[] contents) {
        ItemOdcisk[] out = new ItemOdcisk[contents.length];
        for (int i = 0; i < contents.length; i++) out[i] = ItemOdcisk.z(contents[i]);
        return out;
    }

    public int poziomZOdciskow(ItemOdcisk[] player, String cat) {
        List<ItemOdcisk> saved = odliski.get(cat);
        if (saved == null || saved.isEmpty() || player == null) return 0;
        int best = 0;
        for (ItemOdcisk p : player) {
            if (p == null) continue;
            for (int i = saved.size() - 1; i >= best; i--) {
                ItemOdcisk s = saved.get(i);
                if (s != null && p.pasuje(s)) {
                    best = i + 1;
                    break;
                }
            }
        }
        return best;
    }

    public int getMaxLevel(String cat) {
        return getItems(cat).size();
    }

    /** Poziom ulepszenia pasujacego itemu (1-based) albo 0. */
    public int findLevel(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        for (List<ItemStack> items : categoryItems.values()) {
            for (int i = 0; i < items.size(); i++) {
                if (compareItems(item, items.get(i))) return i + 1;
            }
        }
        return 0;
    }

    public double getPrice(String cat, int level) {
        Map<Integer, Double> prices = categoryPrices.get(cat);
        if (prices == null) return 0;
        return prices.getOrDefault(level, 0.0);
    }

    public void setPrice(String cat, int level, double price) {
        categoryPrices.computeIfAbsent(cat, k -> new LinkedHashMap<>()).put(level, price);
        save();
    }

    // ── Porównywanie itemów (odpowiednik function compareItems) ─────────────

    /**
     * Porownuje dwa itemy na potrzeby /ulepsz.
     * <p>
     * CustomModelData jest CELOWO ignorowane (miecz ma je ustawiane automatycznie
     * przy biciu - patrz SwordHitListener, wiec ten sam miecz moze miec CMD albo nie).
     * Item jest tez rozpoznawany NIEZALEZNIE od tego, czy CMD jest w ogole ustawione.
     * <p>
     * O tym, ktory to poziom ulepszenia, decyduje: material, nazwa, lore oraz
     * poziomy zakleć (Sharpness/Protection/...). Porownujemy realne poziomy enchantow
     * (a nie tylko ich liczbe), zeby miecz 14 (Sharpness 29) nie zostal pomylony
     * z mieczem 15 (Sharpness 31).
     */
    public static boolean compareItems(ItemStack i1, ItemStack i2) {
        ItemOdcisk a = ItemOdcisk.z(i1);
        ItemOdcisk b = ItemOdcisk.z(i2);
        return a != null && b != null && a.pasuje(b);
    }

    /** Material + nazwa + lore + enchanty. CMD celowo pominiete (patrz compareItems). */
    public static final class ItemOdcisk {
        private final org.bukkit.Material type;
        private final String name;
        private final List<String> lore;
        private final Map<Enchantment, Integer> enchants;

        private ItemOdcisk(org.bukkit.Material type, String name, List<String> lore, Map<Enchantment, Integer> enchants) {
            this.type = type;
            this.name = name;
            this.lore = lore;
            this.enchants = enchants;
        }

        public static ItemOdcisk z(ItemStack item) {
            if (item == null || item.getType().isAir()) return null;
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
            String name = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : "";
            List<String> lore = (meta != null && meta.hasLore()) ? meta.getLore() : List.of();
            if (lore == null) lore = List.of();
            return new ItemOdcisk(item.getType(), name, lore, item.getEnchantments());
        }

        public boolean pasuje(ItemOdcisk o) {
            if (o == null || type != o.type || !name.equals(o.name)) return false;
            if (lore.size() != o.lore.size() || !lore.equals(o.lore)) return false;
            return enchants.equals(o.enchants);
        }
    }
}
