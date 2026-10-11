package boniec.ahalloween;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parsowanie przedmiotów sklepu AHalloween z ahalloween.yml. */
public final class HalloweenShopItems {

    public record StackDef(Material mat, int amount, String nazwa, List<String> lore,
                           Map<Enchantment, Integer> enchants, int customModelData, String texture, String nexo) {}

    private HalloweenShopItems() {}

    public static StackDef parseDisplay(ConfigurationSection s) {
        return parse(s, s);
    }

    public static List<StackDef> parseDaj(ConfigurationSection parent) {
        List<StackDef> out = new ArrayList<>();
        ConfigurationSection daj = parent.getConfigurationSection("daj");
        if (daj != null) {
            for (String key : daj.getKeys(false)) {
                ConfigurationSection sub = daj.getConfigurationSection(key);
                if (sub != null) out.add(parse(sub, parent));
            }
        }
        List<?> lista = parent.getList("daj-lista");
        if (lista != null) {
            org.bukkit.configuration.MemoryConfiguration mem = new org.bukkit.configuration.MemoryConfiguration();
            int i = 0;
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> map)) continue;
                ConfigurationSection sec = mem.createSection("i" + i++);
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) sec.set(String.valueOf(e.getKey()), e.getValue());
                }
                out.add(parse(sec, parent));
            }
        }
        return out;
    }

    private static StackDef parse(ConfigurationSection s, ConfigurationSection defaults) {
        String matName = s.getString("material", defaults.getString("material", "PAPER"));
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.PAPER;
        int amount = Math.max(1, Math.min(64, s.getInt("amount", defaults.getInt("amount", 1))));
        String nazwa = s.getString("nazwa", defaults.getString("nazwa", "Item"));
        List<String> lore = new ArrayList<>();
        if (s.contains("lore")) {
            lore.addAll(s.getStringList("lore"));
        } else if (defaults.contains("lore")) {
            lore.addAll(defaults.getStringList("lore"));
        }
        String opis = s.getString("opis", defaults.getString("opis", ""));
        if (!opis.isBlank() && lore.isEmpty()) lore.add(opis);
        Map<Enchantment, Integer> ench = parseEnchants(s.getConfigurationSection("enchanty"));
        if (ench.isEmpty()) ench = parseEnchants(defaults.getConfigurationSection("enchanty"));
        int cmd = s.getInt("custom-model-data", s.getInt("custom_model_data",
                defaults.getInt("custom-model-data", defaults.getInt("custom_model_data", 0))));
        String texture = s.getString("texture", defaults.getString("texture", ""));
        String nexo = s.getString("nexo", defaults.getString("nexo", ""));
        return new StackDef(mat, amount, nazwa, lore, ench, cmd, texture, nexo);
    }

    private static Map<Enchantment, Integer> parseEnchants(ConfigurationSection sec) {
        Map<Enchantment, Integer> map = new HashMap<>();
        if (sec == null) return map;
        for (String key : sec.getKeys(false)) {
            Enchantment en = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (en == null) {
                try {
                    en = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {
                }
            }
            if (en != null) map.put(en, sec.getInt(key));
        }
        return map;
    }

    public static ItemStack build(JavaPlugin plugin, StackDef def, boolean hideEnchants) {
        ItemStack it;
        if (def.texture() != null && !def.texture().isBlank()) {
            it = HalloweenUtil.skullFromTexture(def.texture());
        } else if (def.nexo() != null && !def.nexo().isBlank()) {
            it = HalloweenUtil.nexoOrFallback(plugin, def.nexo(), def.mat());
        } else {
            it = new ItemStack(def.mat());
        }
        it.setAmount(Math.max(1, Math.min(64, def.amount())));
        HalloweenUtil.named(it, def.nazwa(), def.lore());
        if (!def.enchants().isEmpty()) {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                for (Map.Entry<Enchantment, Integer> e : def.enchants().entrySet()) {
                    meta.addEnchant(e.getKey(), e.getValue(), true);
                }
                if (hideEnchants) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                if (def.customModelData() > 0) meta.setCustomModelData(def.customModelData());
                it.setItemMeta(meta);
            }
        } else if (def.customModelData() > 0) {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(def.customModelData());
                it.setItemMeta(meta);
            }
        }
        return it;
    }
}
