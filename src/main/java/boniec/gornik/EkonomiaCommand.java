package boniec.gornik;

import boniec.szaman.util.ItemBuilder;
import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /ekonomia - GUI (5 rzedow) z cenami blokow gornika.
 * Obramowka: yellow / white / orange stained glass pane na zmiane.
 * Na srodku kazdy blok z kopanie.yml i lore z zarobkiem za 1 blok.
 */
public class EkonomiaCommand implements CommandExecutor, Listener {

    private static final int ROWS = 5;
    private static final int SIZE = ROWS * 9;

    /** Obramowka zgodnie z ruchem wskazowek - kolory ida po kolei na zmiane. */
    private static final int[] BORDER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            17, 26, 35,
            44, 43, 42, 41, 40, 39, 38, 37, 36,
            27, 18, 9
    };

    private static final Material[] BORDER_GLASS = {
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE
    };

    private static final int[] INNER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final GornikManager manager;

    public EkonomiaCommand(GornikManager manager) {
        this.manager = manager;
    }

    private static final class EkonomiaHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        if (!manager.isEnabled()) {
            Msg.send(player, manager.getConfig().getString("messages.regiona-wylaczona",
                    "&cEkonomia kopania jest wylaczona!"));
            return true;
        }

        LinkedHashMap<String, Double> ceny = manager.getCenyBlokw();
        if (ceny.isEmpty()) {
            Msg.send(player, "&cBrak ustawionych blokow w kopanie.yml!");
            return true;
        }

        player.openInventory(zbudujGui(ceny));
        return true;
    }

    private Inventory zbudujGui(LinkedHashMap<String, Double> ceny) {
        EkonomiaHolder holder = new EkonomiaHolder();
        String tytul = manager.getConfig().getString("messages.tytul-gui", "&8Ekonomia");
        Inventory inv = Bukkit.createInventory(holder, SIZE, Msg.color(tytul));
        holder.inventory = inv;

        ItemStack[] panes = {
                szyba(BORDER_GLASS[0]),
                szyba(BORDER_GLASS[1]),
                szyba(BORDER_GLASS[2])
        };
        for (int i = 0; i < BORDER_SLOTS.length; i++) {
            inv.setItem(BORDER_SLOTS[i], panes[i % panes.length]);
        }

        String loreWzor = manager.getConfig().getString("messages.lore-blok",
                "&7Zarobek za 1 blok: &e%cena%$");

        List<ItemStack> bloki = new ArrayList<>();
        for (Map.Entry<String, Double> entry : ceny.entrySet()) {
            Material material = materialLubFallback(entry.getKey());
            String nazwa = ladnaNazwa(entry.getKey());
            String lore = loreWzor.replace("%cena%", formatuj(entry.getValue()));
            bloki.add(new ItemBuilder(material)
                    .name("&e" + nazwa)
                    .lore(lore)
                    .hideFlags()
                    .build());
        }

        umiescNaSrodku(inv, bloki);
        return inv;
    }

    private void umiescNaSrodku(Inventory inv, List<ItemStack> bloki) {
        int count = Math.min(bloki.size(), INNER_SLOTS.length);
        if (count <= 0) {
            return;
        }
        if (count <= 7) {
            int start = 19 + (7 - count) / 2;
            for (int i = 0; i < count; i++) {
                inv.setItem(start + i, bloki.get(i));
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            inv.setItem(INNER_SLOTS[i], bloki.get(i));
        }
    }

    private static ItemStack szyba(Material material) {
        return new ItemBuilder(material).name(" ").hideFlags().build();
    }

    private static Material materialLubFallback(String key) {
        try {
            Material material = Material.valueOf(key.toUpperCase(Locale.ROOT));
            if (material.isItem()) {
                return material;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return Material.STONE;
    }

    private static String ladnaNazwa(String key) {
        String raw = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean wielka = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                sb.append(c);
                wielka = true;
            } else if (wielka) {
                sb.append(Character.toUpperCase(c));
                wielka = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String formatuj(double wartosc) {
        if (wartosc == Math.floor(wartosc)) {
            return String.valueOf((long) wartosc);
        }
        return String.format(Locale.US, "%.2f", wartosc);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EkonomiaHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EkonomiaHolder)) {
            return;
        }
        event.setCancelled(true);
    }
}
