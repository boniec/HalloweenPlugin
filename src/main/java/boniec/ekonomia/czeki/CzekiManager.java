package boniec.ekonomia.czeki;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * System "czekow" administracyjnych: /adminczek <kwota> tworzy w rece admina
 * papier z zapisana kwota (w lore, jako dokladna liczba - reszta lore to sam
 * wyswietlany skrot, np. "4k"). Gracz klika PPM trzymanym czekiem, aby go
 * zrealizowac: kwota trafia na jego konto (Vault), a przedmiot znika.
 * <p>
 * Odpowiednik oryginalnego skryptu Skript "SKRYPT NA ADMIN CZEKI".
 * <p>
 * UWAGA (naprawiony bug z oryginalu): skrypt tworzyl czek z custom model data
 * 49, ale sprawdzal przy realizacji model data 50 - w efekcie czek NIGDY nie
 * dawal sie zrealizowac. Tutaj obie strony uzywaja tej samej stalej
 * {@link #CUSTOM_MODEL_DATA}.
 */
public class CzekiManager implements CommandExecutor, Listener {

    /** Custom model data papieru-czeku - musi byc identyczne przy tworzeniu i przy realizacji. */
    private static final int CUSTOM_MODEL_DATA = 49;

    /** Klucz w lore, po ktorym odczytujemy dokladna (nie skrocona) kwote czeku. */
    private static final String LORE_KWOTA_PREFIX = "Kwota: ";

    private final JavaPlugin plugin;
    private final String prefix;
    private Economy econ;

    public CzekiManager(JavaPlugin plugin, String prefix) {
        this.plugin = plugin;
        this.prefix = prefix;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault nie jest zainstalowany! System czekow (CzekiManager) nie bedzie dzialac.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("Nie znaleziono dostawcy Economy (Vault)! System czekow nie bedzie dzialac.");
            return;
        }
        econ = rsp.getProvider();
    }

    /**
     * Formatuje kwote do skroconej, czytelnej postaci: 4000 -> "4k", 2500000 -> "2.5M" itd.
     * Odpowiednik funkcji formatAmount() z oryginalnego skryptu.
     */
    public static String formatAmount(double n) {
        if (n >= 1_000_000_000d) {
            return formatSkrot(n / 1_000_000_000d) + "B";
        } else if (n >= 1_000_000d) {
            return formatSkrot(n / 1_000_000d) + "M";
        } else if (n >= 1_000d) {
            return formatSkrot(n / 1_000d) + "k";
        }
        return formatSkrot(n);
    }

    /** Usuwa zbedne ".0" przy liczbach calkowitych (np. 4.0 -> "4", ale 2.5 zostaje "2.5"). */
    private static String formatSkrot(double val) {
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
            return String.valueOf((long) val);
        }
        return String.format(Locale.US, "%.1f", val);
    }

    // ── /adminczek <kwota> ──────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest dostepna tylko dla graczy.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(color(prefix + " &cUzyj: &f/adminczek <kwota>"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[0].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(color(prefix + " &cPodaj poprawna liczbe!"));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(color(prefix + " &cKwota musi byc wieksza od 0!"));
            return true;
        }

        String formatted = formatAmount(amount);
        ItemStack item = stworzCzek(amount, formatted);
        player.getInventory().addItem(item);

        player.sendMessage(color(prefix + " &aStworzono czek o wartosci &2" + formatted + "$ &7("
                + formatKwotaDokladna(amount) + "$)!"));
        return true;
    }

    private ItemStack stworzCzek(double amount, String formatted) {
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&2" + formatted + "$"));
            meta.setCustomModelData(CUSTOM_MODEL_DATA);

            List<String> lore = new ArrayList<>();
            lore.add(color("&0"));
            lore.add(color("&8x &aKliknij prawym aby wykorzystac!"));
            lore.add(color("&0"));
            lore.add(color("&8" + LORE_KWOTA_PREFIX + "&7" + formatKwotaDokladna(amount) + "$"));
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    /** Kwota bez zbednych miejsc po przecinku, do zapisu w lore i wiadomosciach. */
    private String formatKwotaDokladna(double amount) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    // ── Realizacja czeku (PPM) ──────────────────────────────────────────────

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack tool = event.getItem();
        if (tool == null || tool.getType() != org.bukkit.Material.PAPER) return;
        ItemMeta meta = tool.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != CUSTOM_MODEL_DATA) return;

        Player player = event.getPlayer();

        double value = odczytajKwoteZLore(meta);
        if (value <= 0) return;

        event.setCancelled(true);

        if (econ == null) {
            player.sendMessage(color(prefix + " &cSystem ekonomii jest niedostepny - zglos to administracji."));
            return;
        }

        ItemStack reka = player.getInventory().getItemInMainHand();
        if (reka.getAmount() > 1) {
            reka.setAmount(reka.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        econ.depositPlayer(player, value);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.sendMessage(color(prefix + " &aPomyslnie wykorzystano czek o wartosci &2"
                + formatKwotaDokladna(value) + "$&a!"));
    }

    /** Odczytuje dokladna kwote czeku zapisana w lore (linia "Kwota: X$"). */
    private double odczytajKwoteZLore(ItemMeta meta) {
        List<String> lore = meta.getLore();
        if (lore == null) return 0;
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line);
            if (stripped == null || !stripped.contains(LORE_KWOTA_PREFIX)) continue;
            String valText = stripped.substring(stripped.indexOf(LORE_KWOTA_PREFIX) + LORE_KWOTA_PREFIX.length());
            valText = valText.replace("$", "").trim();
            try {
                return Double.parseDouble(valText);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
