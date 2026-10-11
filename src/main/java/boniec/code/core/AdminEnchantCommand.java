package boniec.code.core;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminEnchantCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public AdminEnchantCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("core.adminenchant")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnień do użycia tej komendy.");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Użycie: /adminenchant <enchant> <poziom>");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Trzymaj przedmiot w ręce!");
            return true;
        }

        String enchantArg = args[0];
        Enchantment ench = parseEnchantment(enchantArg);
        if (ench == null) {
            player.sendMessage(ChatColor.RED + "Nieprawidłowy enchant: " + enchantArg);
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Podaj poprawny poziom enchantu (liczba).");
            return true;
        }
        if (level <= 0) {
            player.sendMessage(ChatColor.RED + "Poziom musi być większy od 0.");
            return true;
        }

        // Apply enchant (unsafe, so admins can set any level)
        item.addUnsafeEnchantment(ench, level);

        // Check if enchant is Sharpness for attack speed
        boolean isSharpness = isSharpnessEnchantment(ench);
        
        // Check if item is leather armor or player head
        boolean isLeatherOrHead = isLeatherArmorOrHead(item.getType());

        // Make unbreakable + hide everything + add attack speed (only for Sharpness)
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);

            // Add attack speed modifier -2.4 ONLY if adding Sharpness
            if (isSharpness) {
                addAttackSpeedModifier(meta);
            }

            // For leather/player head: hide ALL possible flags and remove all attribute modifiers
            if (isLeatherOrHead) {
                // Remove all attribute modifiers (clear NBT) - remove for each attribute
                if (meta.hasAttributeModifiers()) {
                    for (org.bukkit.attribute.Attribute attr : meta.getAttributeModifiers().keySet()) {
                        meta.removeAttributeModifier(attr);
                    }
                }
                
                // Note: Color is NOT reset - keeps original color
                
                // Hide ALL possible flags - use addFlagIfExists for all to avoid errors
                addFlagIfExists(meta, "HIDE_ATTRIBUTES");
                addFlagIfExists(meta, "HIDE_ENCHANTS");
                addFlagIfExists(meta, "HIDE_UNBREAKABLE");
                addFlagIfExists(meta, "HIDE_DESTROYS");
                addFlagIfExists(meta, "HIDE_PLACED_ON");
                addFlagIfExists(meta, "HIDE_POTION_EFFECTS");
                
                // Add any standard flags that exist
                for (ItemFlag flag : ItemFlag.values()) {
                    try {
                        if (!meta.hasItemFlag(flag)) {
                            meta.addItemFlags(flag);
                        }
                    } catch (Exception ignored) {}
                }
                
                // Try to hide additional flags that might exist (1.21+)
                addFlagIfExists(meta, "HIDE_ADDITIONAL_TOOLTIP");
                addFlagIfExists(meta, "HIDE_DYE");
                addFlagIfExists(meta, "HIDE_ARMOR_TRIM");
                addFlagIfExists(meta, "HIDE_STORED_ENCHANTS");
            } else {
                // For other items: hide common flags
                addFlagIfExists(meta, "HIDE_ATTRIBUTES");
                addFlagIfExists(meta, "HIDE_ENCHANTS");
                addFlagIfExists(meta, "HIDE_UNBREAKABLE");
                addFlagIfExists(meta, "HIDE_DESTROYS");
                addFlagIfExists(meta, "HIDE_PLACED_ON");
                addFlagIfExists(meta, "HIDE_POTION_EFFECTS");
                // 1.21+ extra flags (guarded)
                addFlagIfExists(meta, "HIDE_ADDITIONAL_TOOLTIP");
                addFlagIfExists(meta, "HIDE_DYE");
                addFlagIfExists(meta, "HIDE_ARMOR_TRIM");
                addFlagIfExists(meta, "HIDE_STORED_ENCHANTS");
            }

            item.setItemMeta(meta);
        }

        // Apply your custom enchant lore formatting (empty line + ordered lines)
        EnchantLoreManager.updateEnchantLore(item, isLeatherOrHead);

        player.sendMessage(ChatColor.GREEN + "Dodano enchant " + ench.getKey().getKey() + " " + level + ", ustawiono unbreakable i ukryto atrybuty.");
        return true;
    }

    private boolean isSharpnessEnchantment(Enchantment ench) {
        if (ench == null) return false;
        String key = ench.getKey().getKey().toLowerCase();
        return key.equals("sharpness") || 
               key.equals("damage_all") ||
               ench.equals(Enchantment.getByName("DAMAGE_ALL")) ||
               ench.equals(Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("sharpness")));
    }

    private boolean isLeatherArmorOrHead(Material material) {
        return material == Material.LEATHER_HELMET ||
               material == Material.LEATHER_CHESTPLATE ||
               material == Material.LEATHER_LEGGINGS ||
               material == Material.LEATHER_BOOTS ||
               material == Material.PLAYER_HEAD;
    }

    private Enchantment parseEnchantment(String input) {
        if (input == null) return null;
        String raw = input.trim();
        if (raw.isEmpty()) return null;

        // Try direct Bukkit names (legacy)
        Enchantment byName = Enchantment.getByName(raw.toUpperCase(Locale.ROOT));
        if (byName != null) return byName;

        // Try minecraft key
        String key = raw.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        try {
            Enchantment byKey = Enchantment.getByKey(NamespacedKey.minecraft(key));
            if (byKey != null) return byKey;
        } catch (Exception ignored) {}

        if (key.equals("sharp") || key.equals("ostrosc") || key.equals("ostrość")) key = "sharpness";
        if (key.equals("eff") || key.equals("wydajnosc") || key.equals("wydajność") || key.equals("dig_speed")) key = "efficiency";
        if (key.equals("fort") || key.equals("szczescie") || key.equals("szczęście") || key.equals("loot_bonus_blocks")) key = "fortune";
        if (key.equals("unbr") || key.equals("durability") || key.equals("niezniszczalnosc") || key.equals("niezniszczalność")) key = "unbreaking";
        if (key.equals("fire") || key.equals("ogien") || key.equals("ogień")) key = "fire_aspect";
        if (key.equals("prot") || key.equals("ochrona")) key = "protection";

        try {
            return Enchantment.getByKey(NamespacedKey.minecraft(key));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addFlagIfExists(ItemMeta meta, String flagName) {
        try {
            ItemFlag flag = ItemFlag.valueOf(flagName);
            meta.addItemFlags(flag);
        } catch (Exception ignored) {}
    }

    private void addAttackSpeedModifier(ItemMeta meta) {
        try {
            // Get attack speed attribute using reflection to avoid deprecated valueOf
            org.bukkit.attribute.Attribute attackSpeedAttr = getAttackSpeedAttribute();
            
            if (attackSpeedAttr != null) {
                org.bukkit.attribute.AttributeModifier attackSpeedMod = new org.bukkit.attribute.AttributeModifier(
                        org.bukkit.NamespacedKey.minecraft("adminenchant_attack_speed"),
                        -2.4,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                        org.bukkit.inventory.EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(attackSpeedAttr, attackSpeedMod);
            }
        } catch (Exception ignored) {
            // If fails, attribute won't be added (silent fail)
        }
    }

    @SuppressWarnings("deprecation")
    private org.bukkit.attribute.Attribute getAttackSpeedAttribute() {
        // Try to get using reflection first (avoid valueOf if possible)
        try {
            // Try accessing enum values directly
            java.lang.reflect.Field field = org.bukkit.attribute.Attribute.class.getField("ATTACK_SPEED");
            if (field != null) {
                return (org.bukkit.attribute.Attribute) field.get(null);
            }
        } catch (Exception ignored) {
            // Reflection failed, try valueOf
        }
        
        // Fallback to valueOf (deprecated but works)
        try {
            return org.bukkit.attribute.Attribute.valueOf("ATTACK_SPEED");
        } catch (Exception e) {
            try {
                return org.bukkit.attribute.Attribute.valueOf("GENERIC_ATTACK_SPEED");
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> nazwy = new ArrayList<>();
            nazwy.add("efficiency");
            nazwy.add("fortune");
            nazwy.add("unbreaking");
            nazwy.add("sharpness");
            nazwy.add("protection");
            nazwy.add("fire_aspect");
            for (Enchantment e : Enchantment.values()) {
                String k = e.getKey().getKey();
                if (!nazwy.contains(k)) nazwy.add(k);
            }
            return nazwy.stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            List<String> out = new ArrayList<>();
            out.add("1");
            out.add("2");
            out.add("3");
            out.add("4");
            out.add("5");
            out.add("10");
            return out;
        }
        return Collections.emptyList();
    }
}


