package boniec.skup;

import boniec.code.core.UpgradeManager;
import boniec.klany.util.Msg;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /skup - GUI (45 slotow), do ktorego gracz wklada itemy. Po zamknieciu GUI
 * itemy, ktore sa w systemie /ulepsz (UpgradeManager), zostaja "skupione" za
 * SKUP_PERCENT ich ceny (cena z /ulepsz dla danego poziomu), a pieniadze
 * trafiaja na konto gracza (Vault). Itemy spoza /ulepsz NIE sa skupowane i
 * wracaja do gracza.
 * <p>
 * Ten modul tylko CZYTA UpgradeManager (getCategoryNames / getMaxLevel /
 * getItem / getPrice + compareItems) - nie zmienia nic w /ulepsz.
 */
public class SkupCommand implements CommandExecutor, Listener {

    /** Ile procent ceny z /ulepsz dostaje gracz (25% = 1000 -> 250). */
    private static final double SKUP_PERCENT = 7.5;

    private static final int GUI_SIZE = 45;

    // Title/subtitle po sprzedazy. Kolory hex w formacie &#RRGGBB (parsuje Msg.color),
    // %kasa% jest podmieniane na sume za sprzedane itemy.
    private static final String SELL_TITLE = "&#25B094sᴋᴜᴘ";
    private static final String SELL_SUBTITLE = "&fSprzedano przedmioty za &#25B094%kasa%";
    private static final int TITLE_FADE_IN = 5;
    private static final int TITLE_STAY = 40;
    private static final int TITLE_FADE_OUT = 10;
    private static final String GUI_TITLE = ChatColor.DARK_GREEN + "Skup itemow";
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "SKUP" + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;

    private final JavaPlugin plugin;
    private final UpgradeManager upgradeManager;
    private Economy econ;

    /** Holder rozpoznajacy nasze GUI (nie po tytule, zeby nie dalo sie tego podrobic). */
    private static final class SkupHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public SkupCommand(JavaPlugin plugin, UpgradeManager upgradeManager) {
        this.plugin = plugin;
        this.upgradeManager = upgradeManager;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault nie jest zainstalowany! /skup nie bedzie dzialac.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("Nie znaleziono dostawcy Economy (Vault)! /skup nie bedzie dzialac.");
            return;
        }
        econ = rsp.getProvider();
    }

    // ── /skup ────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest dostepna tylko dla graczy.");
            return true;
        }
        if (econ == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "System ekonomii jest niedostepny - zglos to administracji.");
            return true;
        }

        SkupHolder holder = new SkupHolder();
        Inventory inv = plugin.getServer().createInventory(holder, GUI_SIZE, GUI_TITLE);
        holder.inventory = inv;
        player.openInventory(inv);
        player.sendMessage(PREFIX + ChatColor.GRAY + "Wloz itemy z " + ChatColor.WHITE + "/ulepsz"
                + ChatColor.GRAY + " i zamknij okno. Dostaniesz " + ChatColor.GREEN
                + formatPercent(SKUP_PERCENT) + "%" + ChatColor.GRAY + " ich ceny.");
        return true;
    }

    // ── Zamkniecie GUI = sprzedaz ───────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof SkupHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Bierzemy zawartosc i CZYSCIMY GUI od razu - dzieki temu nic nie da sie
        // wyplacic dwa razy (np. przy podwojnym evencie zamkniecia).
        ItemStack[] contents = inv.getContents();
        inv.clear();

        double total = 0.0;
        int soldCount = 0;
        List<ItemStack> toReturn = new ArrayList<>();

        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) continue;

            double unitPrice = findUpgradePrice(item);
            if (unitPrice > 0) {
                total += unitPrice * (SKUP_PERCENT / 100.0) * item.getAmount();
                soldCount += item.getAmount();
            } else {
                // Nie nalezy do /ulepsz (albo cena 0) -> wraca do gracza.
                toReturn.add(item);
            }
        }

        if (soldCount > 0) {
            econ.depositPlayer(player, total);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            player.sendMessage(PREFIX + ChatColor.GREEN + "Sprzedano " + ChatColor.WHITE + soldCount
                    + ChatColor.GREEN + " przedm. za " + ChatColor.GREEN + "$" + formatPrice(total)
                    + ChatColor.GRAY + " (" + formatPercent(SKUP_PERCENT) + "% ceny z /ulepsz).");
            player.sendTitle(
                    Msg.color(SELL_TITLE),
                    Msg.color(SELL_SUBTITLE.replace("%kasa%", "$" + formatPrice(total))),
                    TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
        }

        if (!toReturn.isEmpty()) {
            giveBack(player, toReturn);
            player.sendMessage(PREFIX + ChatColor.RED + "Czesc itemow nie jest skupowana (nie ma ich w /ulepsz) - zwrocono do ekwipunku.");
        }
    }

    /** Gdy gracz wyjdzie z otwartym GUI, Bukkit i tak wywola InventoryCloseEvent - to tylko zabezpieczenie. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof SkupHolder) {
            player.closeInventory();
        }
    }

    // ── Wycena z /ulepsz ────────────────────────────────────────────────

    /**
     * Zwraca cene JEDNEJ sztuki itemu wg /ulepsz albo 0, gdy item nie jest
     * zadnym poziomem zadnej kategorii. Gdy ten sam item wystepuje w kilku
     * kategoriach/poziomach, bierzemy najnizsza dodatnia cene (zeby nie dalo
     * sie zarobic na pomylce w konfiguracji).
     */
    private double findUpgradePrice(ItemStack item) {
        double best = 0.0;
        for (String cat : upgradeManager.getCategoryNames()) {
            int max = upgradeManager.getMaxLevel(cat);
            for (int lvl = 1; lvl <= max; lvl++) {
                ItemStack saved = upgradeManager.getItem(cat, lvl);
                if (!UpgradeManager.compareItems(item, saved)) continue;
                double price = upgradeManager.getPrice(cat, lvl);
                if (price <= 0) continue;
                if (best == 0.0 || price < best) best = price;
            }
        }
        return best;
    }

    private void giveBack(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            var left = player.getInventory().addItem(item);
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    private static String formatPrice(double price) {
        if (price == Math.floor(price)) return String.valueOf((long) price);
        return String.format(Locale.US, "%.2f", price);
    }

    private static String formatPercent(double p) {
        if (p == Math.floor(p)) return String.valueOf((long) p);
        return String.format(Locale.US, "%.1f", p);
    }
}
