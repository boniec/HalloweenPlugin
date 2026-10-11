package boniec.code.core;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EnchantLoreManager implements Listener {

    private final JavaPlugin plugin;

    public EnchantLoreManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;

        AnvilInventory anvil = (AnvilInventory) event.getInventory();
        ItemStack result = anvil.getItem(2); // Slot wynikowy w kowadle

        if (result != null && result.getType() != Material.AIR) {
            updateEnchantLore(result);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;

        // Sprawdź czy kliknięto w slot wynikowy (slot 2)
        if (event.getSlot() == 2) {
            ItemStack result = event.getCurrentItem();
            if (result != null && result.getType() != Material.AIR) {
                updateEnchantLore(result);
            }
        }
    }

    public static void updateEnchantLore(ItemStack item) {
        updateEnchantLore(item, false);
    }

    public static void updateEnchantLore(ItemStack item, boolean isLeatherOrHead) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty()) {
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.removeIf(EnchantLoreManager::czyLiniaEnchantu);
            meta.setLore(lore);
            item.setItemMeta(meta);
            return;
        }

        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(EnchantLoreManager::czyLiniaEnchantu);

        // Znajdź indeks pierwszej pustej linii lub utwórz ją
        int emptyLineIndex = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i) != null && lore.get(i).trim().isEmpty()) {
                emptyLineIndex = i;
                break;
            }
        }

        // Jeśli nie ma pustej linii, dodaj na początku
        if (emptyLineIndex == -1) {
            lore.add(0, "");
            emptyLineIndex = 0;
        }

        List<String> enchantLines = new ArrayList<>();

        dodajLinie(enchantLines, enchants, Enchantment.FIRE_ASPECT, "&f丷 &8| &6Zaklęty Ogień ");
        dodajLinie(enchantLines, enchants, Enchantment.SHARPNESS, "&f婊 &8| &4Ostrość ");
        dodajLinie(enchantLines, enchants, Enchantment.EFFICIENCY, "&f乚 &8| &eWydajność ");
        dodajLinie(enchantLines, enchants, Enchantment.FORTUNE, "&f丮 &8| &2Szczęście ");

        if (isLeatherOrHead) {
            dodajLinie(enchantLines, enchants, Enchantment.PROTECTION, "&f世 &8| &3Ochrona ");
            dodajLinie(enchantLines, enchants, Enchantment.UNBREAKING, "&f乜 &8| &bNiezniszczalność ");
        } else {
            dodajLinie(enchantLines, enchants, Enchantment.UNBREAKING, "&f乜 &8| &bNiezniszczalność ");
            dodajLinie(enchantLines, enchants, Enchantment.PROTECTION, "&f世 &8| &3Ochrona ");
        }

        // Wstaw linie enchantów po pustej linii
        if (!enchantLines.isEmpty()) {
            lore.addAll(emptyLineIndex + 1, enchantLines);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public static List<String> linieEnchantow(ItemStack item) {
        List<String> out = new ArrayList<>();
        if (item == null) return out;
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty()) return out;
        dodajLinie(out, enchants, Enchantment.SHARPNESS, "&f婊 &8| &4Ostrość ");
        dodajLinie(out, enchants, Enchantment.UNBREAKING, "&f乜 &8| &bNiezniszczalność ");
        dodajLinie(out, enchants, Enchantment.FIRE_ASPECT, "&f丷 &8| &6Zaklęty Ogień ");
        dodajLinie(out, enchants, Enchantment.EFFICIENCY, "&f乚 &8| &eWydajność ");
        dodajLinie(out, enchants, Enchantment.FORTUNE, "&f丮 &8| &2Szczęście ");
        dodajLinie(out, enchants, Enchantment.PROTECTION, "&f世 &8| &3Ochrona ");
        return out;
    }

    private static void dodajLinie(List<String> linie, Map<Enchantment, Integer> enchants,
                                   Enchantment enchant, String prefix) {
        if (enchant == null || !enchants.containsKey(enchant)) return;
        linie.add(ChatColor.translateAlternateColorCodes('&', prefix + enchants.get(enchant)));
    }

    private static boolean czyLiniaEnchantu(String line) {
        if (line == null) return false;
        return line.contains("🪓")
                || line.contains("❤")
                || line.contains("🔥")
                || line.contains("🛡")
                || line.contains("⛏")
                || line.contains("☘")
                || line.contains("Ostrość")
                || line.contains("Niezniszczalność")
                || line.contains("Zaklęty Ogień")
                || line.contains("Ochrona")
                || line.contains("Wydajność")
                || line.contains("Szczęście");
    }
}

