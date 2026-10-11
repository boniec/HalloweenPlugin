/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 */
package dev.darkness.utilities.item;

import dev.darkness.utilities.text.TextUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;
    private Map<String, String> placeholders = Collections.emptyMap();

    public ItemBuilder(Material m) {
        this(m, 1);
    }

    public ItemBuilder(Material m, int a) {
        this.item = new ItemStack(m, a);
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder(ItemStack stack) {
        this.item = stack != null ? stack.clone() : new ItemStack(Material.AIR);
        this.meta = this.item.getItemMeta();
    }

    public static ItemBuilder of(Material m) {
        return new ItemBuilder(m);
    }

    public static ItemBuilder of(ItemStack s) {
        return new ItemBuilder(s);
    }

    public ItemBuilder amount(int a) {
        this.item.setAmount(a);
        return this;
    }

    public ItemBuilder name(String name) {
        if (this.meta != null && name != null) {
            this.meta.displayName(TextUtil.toComponent(TextUtil.applyPlaceholders(name, this.placeholders)));
        }
        return this;
    }

    public ItemBuilder name(Component component) {
        if (this.meta != null && component != null) {
            this.meta.displayName(component);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (this.meta != null) {
            if (lines == null || lines.isEmpty()) {
                this.meta.lore(null);
            } else {
                this.meta.lore(TextUtil.applyPlaceholders(lines, this.placeholders).stream().map(TextUtil::toComponent).toList());
            }
        }
        return this;
    }

    public ItemBuilder lore(String ... lines) {
        return this.lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(Component ... components) {
        if (this.meta != null) {
            this.meta.lore(Arrays.asList(components));
        }
        return this;
    }

    public ItemBuilder addLoreLine(String line) {
        if (this.meta == null || line == null) {
            return this;
        }
        ArrayList<Component> lore = this.meta.lore() == null ? new ArrayList<Component>() : new ArrayList(this.meta.lore());
        lore.add(TextUtil.toComponent(TextUtil.applyPlaceholders(line, this.placeholders)));
        this.meta.lore(lore);
        return this;
    }

    public ItemBuilder clearLore() {
        if (this.meta != null) {
            this.meta.lore(null);
        }
        return this;
    }

    public ItemBuilder enchant(Enchantment e, int l) {
        if (this.meta != null) {
            this.meta.addEnchant(e, l, true);
        }
        return this;
    }

    public ItemBuilder flag(ItemFlag ... f) {
        if (this.meta != null) {
            this.meta.addItemFlags(f);
        }
        return this;
    }

    public ItemBuilder glow() {
        this.enchant(Enchantment.UNBREAKING, 1);
        this.flag(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder unbreakable(boolean u) {
        if (this.meta != null) {
            this.meta.setUnbreakable(u);
        }
        return this;
    }

    public ItemBuilder customModelData(int d) {
        if (this.meta != null) {
            this.meta.setCustomModelData(Integer.valueOf(d));
        }
        return this;
    }

    public <T, Z> ItemBuilder tag(NamespacedKey k, PersistentDataType<T, Z> t, Z v) {
        if (this.meta != null) {
            this.meta.getPersistentDataContainer().set(k, t, v);
        }
        return this;
    }

    public ItemBuilder tagString(NamespacedKey k, String v) {
        return this.tag(k, PersistentDataType.STRING, v);
    }

    public ItemBuilder tagInt(NamespacedKey k, int v) {
        return this.tag(k, PersistentDataType.INTEGER, v);
    }

    public ItemBuilder placeholders(Map<String, String> p) {
        this.placeholders = p;
        return this;
    }

    public ItemStack build() {
        if (this.meta != null) {
            this.item.setItemMeta(this.meta);
        }
        return this.item;
    }
}
