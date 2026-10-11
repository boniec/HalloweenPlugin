package boniec.code.core.metin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Random;

public class MetinDrop {

    private static final Random RANDOM = new Random();

    private final ItemStack item;
    private final double chance;

    public MetinDrop(ItemStack item, double chance) {
        this.item = item.clone();
        this.chance = chance;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getChance() {
        return chance;
    }

    public Material getMaterial() {
        return item.getType();
    }

    public int getAmount() {
        return item.getAmount();
    }

    public String getDisplayName() {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return null;
    }

    public boolean shouldDrop() {
        return RANDOM.nextDouble() * 100.0D <= chance;
    }

    public ItemStack createItemStack() {
        return item.clone();
    }

    @Override
    public String toString() {
        String displayName = getDisplayName();
        if (displayName != null) {
            return ChatColor.translateAlternateColorCodes('&', displayName) + ChatColor.RESET + " x" + item.getAmount() + " (" + chance + "%)";
        }
        return item.getType().name() + " x" + item.getAmount() + " (" + chance + "%)";
    }
}