package boniec.code.core.backup;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class BackupSerializer {

    public static String serializeItemArray(ItemStack[] items) {
        if (items == null || items.length == 0) return "";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(out)) {
            oos.writeInt(items.length);
            for (ItemStack item : items) oos.writeObject(item);
            oos.flush();
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static ItemStack[] deserializeItemArray(String base64) {
        if (base64 == null || base64.isEmpty() || base64.equals("[]") || base64.equals("{}")) {
            return new ItemStack[0];
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(in)) {
            int length = ois.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) items[i] = (ItemStack) ois.readObject();
            return items;
        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack[0];
        }
    }

    /** Zwraca wszystkie niepuste itemy (zbroja najpierw) - do podglądu. */
    public static List<ItemStack> flatten(String itemsBase64, String armorBase64) {
        List<ItemStack> all = new ArrayList<>();
        for (ItemStack it : deserializeItemArray(armorBase64))
            if (it != null && it.getType() != Material.AIR) all.add(it.clone());
        for (ItemStack it : deserializeItemArray(itemsBase64))
            if (it != null && it.getType() != Material.AIR) all.add(it.clone());
        return all;
    }

    /** Podgląd tekstowy + licznik (deserializacja tylko raz). */
    public static Preview buildPreview(String itemsBase64, String armorBase64, int maxLines) {
        ItemStack[] armor = deserializeItemArray(armorBase64);
        ItemStack[] items = deserializeItemArray(itemsBase64);

        List<String> lines = new ArrayList<>();
        int count = 0;

        for (ItemStack it : armor) {
            if (it == null || it.getType() == Material.AIR) continue;
            count++;
            if (lines.size() < maxLines) lines.add("§b[Zbroja] §f" + displayName(it) + " §7x" + it.getAmount());
        }
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            count++;
            if (lines.size() < maxLines) lines.add("§e" + displayName(it) + " §7x" + it.getAmount());
        }

        if (count == 0) lines.add("§8Brak przedmiotów");
        else if (count > maxLines) lines.add("§7... i §e" + (count - maxLines) + " §7innych");

        return new Preview(lines, count);
    }

    public static List<String> createItemPreviewLines(String itemsBase64, String armorBase64, int maxLines) {
        return buildPreview(itemsBase64, armorBase64, maxLines).lines;
    }

    private static String displayName(ItemStack it) {
        return (it.hasItemMeta() && it.getItemMeta().hasDisplayName())
                ? it.getItemMeta().getDisplayName()
                : formatItemName(it.getType());
    }

    private static String formatItemName(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    public static class Preview {
        public final List<String> lines;
        public final int count;
        public Preview(List<String> lines, int count) {
            this.lines = lines;
            this.count = count;
        }
    }
}