package boniec.skrzynki;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Opis przedmiotu z configu (material, nazwa, lore, custom-model-data, blysk, ilosc).
 * Uzywany do budowania itemow w GUI oraz do rozpoznawania KLUCZY (material + nazwa + opcjonalnie CMD).
 */
final class ItemSpec {

    final Material material;
    final String name;
    final List<String> lore;
    final Integer customModelData;
    final boolean glow;
    final int amount;
    final boolean unbreakable;

    ItemSpec(Material material, String name, List<String> lore, Integer customModelData, boolean glow, int amount) {
        this(material, name, lore, customModelData, glow, amount, false);
    }

    ItemSpec(Material material, String name, List<String> lore, Integer customModelData, boolean glow, int amount,
             boolean unbreakable) {
        this.unbreakable = unbreakable;
        this.material = material;
        this.name = name;
        this.lore = lore == null ? new ArrayList<>() : lore;
        this.customModelData = customModelData;
        this.glow = glow;
        this.amount = amount;
    }

    static ItemSpec from(ConfigurationSection s, Material fallback) {
        if (s == null) return new ItemSpec(fallback, null, new ArrayList<>(), null, false, 1);
        Material m = Material.matchMaterial(s.getString("material", fallback.name()));
        if (m == null || m.isAir()) m = fallback;
        // Akceptujemy oba zapisy kluczy: displayname/modelData/glow/unbreakable/amount oraz nazwa/custom-model-data/blysk/ilosc.
        String name = s.isString("displayname") ? s.getString("displayname")
                : (s.isString("nazwa") ? s.getString("nazwa") : null);
        List<String> lore = new ArrayList<>(s.getStringList("lore"));
        Integer cmd = null;
        if (s.isInt("modelData") && s.getInt("modelData") > 0) cmd = s.getInt("modelData");
        else if (s.isInt("custom-model-data") && s.getInt("custom-model-data") > 0) cmd = s.getInt("custom-model-data");
        boolean glow = s.getBoolean("glow", s.getBoolean("blysk", false));
        boolean unbreakable = s.getBoolean("unbreakable", false);
        int amount = Math.max(1, Math.min(64, s.getInt("amount", s.getInt("ilosc", 1))));
        return new ItemSpec(m, name, lore, cmd, glow, amount, unbreakable);
    }

    /** Buduje spec z przedmiotu w rece (np. /acase klucz). */
    static ItemSpec fromItem(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        String name = null;
        List<String> lore = new ArrayList<>();
        Integer cmd = null;
        if (meta != null) {
            if (meta.hasDisplayName()) name = meta.getDisplayName().replace('\u00a7', '&');
            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) lore.add(line.replace('\u00a7', '&'));
            }
            if (meta.hasCustomModelData()) cmd = meta.getCustomModelData();
        }
        return new ItemSpec(stack.getType(), name, lore, cmd, false, 1);
    }

    void saveTo(ConfigurationSection s) {
        s.set("material", material.name());
        if (name != null) s.set("displayname", name);
        if (!lore.isEmpty()) s.set("lore", new ArrayList<>(lore));
        if (customModelData != null) s.set("modelData", customModelData);
        if (glow) s.set("glow", true);
        if (unbreakable) s.set("unbreakable", true);
    }

    ItemStack build(Map<String, String> placeholders, boolean gui) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(Txt.color(Txt.replace(name, placeholders)));
            if (!lore.isEmpty()) {
                List<String> out = new ArrayList<>();
                for (String line : lore) out.add(Txt.color(Txt.replace(line, placeholders)));
                meta.setLore(out);
            }
            if (customModelData != null) meta.setCustomModelData(customModelData);
            if (glow) {
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (unbreakable) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            if (gui) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Material musi sie zgadzac; nazwa (bez kolorow) i custom-model-data tylko jesli sa ustawione w specu. */
    boolean matches(ItemStack stack) {
        if (stack == null || stack.getType() != material) return false;
        ItemMeta meta = stack.getItemMeta();
        if (name != null) {
            if (meta == null || !meta.hasDisplayName()) return false;
            String wanted = Txt.strip(Txt.color(name));
            if (!wanted.equals(Txt.strip(meta.getDisplayName()))) return false;
        }
        if (customModelData != null) {
            if (meta == null || !meta.hasCustomModelData()) return false;
            if (meta.getCustomModelData() != customModelData.intValue()) return false;
        }
        return true;
    }
}
