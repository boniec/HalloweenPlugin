package boniec.szaman.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Prosty builder ItemStack-ow uzywany przez GUI Szamana. Samodzielny (nie zaleza
 * od klasy ItemBuilder z modulu klanow), zeby ten plugin dalo sie zbudowac i
 * uzywac niezaleznie od tego, czy klasy pomocnicze z boniec.klany.util sa
 * dostepne w classpath.
 */
public class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder(ItemStack bazowy) {
        this.stack = bazowy.clone();
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(String nazwa) {
        if (meta != null) meta.setDisplayName(Msg.color(nazwa));
        return this;
    }

    public ItemBuilder lore(String linia) {
        lore.add(Msg.color(linia));
        return this;
    }

    public ItemBuilder lore(List<String> linie) {
        for (String l : linie) lore(l);
        return this;
    }

    public ItemBuilder amount(int ilosc) {
        stack.setAmount(Math.max(1, ilosc));
        return this;
    }

    /** Ukrywa wszystkie dodatkowe atrybuty/informacje w tooltipie (np. dla szkla dekoracyjnego). */
    public ItemBuilder hideFlags() {
        if (meta != null) meta.addItemFlags(ItemFlag.values());
        return this;
    }

    /** Odpowiednik pola "CustomModelData" z NBT (0/ujemne = brak zmian). */
    public ItemBuilder customModelData(int data) {
        if (meta != null && data > 0) meta.setCustomModelData(data);
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            if (!lore.isEmpty()) meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
