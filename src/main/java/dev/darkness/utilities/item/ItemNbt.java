/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.NamespacedKey
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 */
package dev.darkness.utilities.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ItemNbt {
    private ItemNbt() {
    }

    public static <T, Z> ItemStack set(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, type, value);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    public static <T, Z> Z get(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return (Z)(meta == null ? null : meta.getPersistentDataContainer().get(key, type));
    }

    public static boolean has(ItemStack item, NamespacedKey key, PersistentDataType<?, ?> type) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, type);
    }
}
