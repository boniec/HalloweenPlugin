package boniec.code.core;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Odpowiednik reszty skryptu Skript: komenda /ulepsz, GUI gracza, podglad kategorii,
 * klikanie w menu, zapis edycji admina przy zamknieciu, ustawianie ceny na czacie.
 */
public class UpgradeMenuListener implements Listener, CommandExecutor {

    private static final String MAIN_MENU_TITLE_DEFAULT = ChatColor.WHITE + "<shift:-8>&f都";
    private static final String PREVIEW_MENU_TITLE_DEFAULT = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ᴘᴏᴢɪᴏᴍʏ "
            + ChatColor.DARK_GRAY + "» " + ChatColor.LIGHT_PURPLE + "%kategoria%";
    private static final String EDIT_TITLE_PREFIX = ChatColor.RED + "Edycja: ";
    private final JavaPlugin plugin;
    private final UpgradeManager manager;

    // Tytuly GUI - wczytywane z ulepsz-config.yml (klucze gui.tytul-glowne /
    // gui.tytul-podglad), z fallbackiem do wartosci domyslnych powyzej jesli
    // config jest niedostepny lub klucz jest pusty.
    private final String mainMenuTitle;
    private final String previewMenuTitleTemplate;

    private boolean tloWlaczone = false;
    private Material tloWypelnienie = Material.GRAY_STAINED_GLASS_PANE;
    private Material tloRamka = Material.GRAY_STAINED_GLASS_PANE;
    private Material tloAkcent = Material.GRAY_STAINED_GLASS_PANE;
    private boolean tloAutoRamka = true;
    private List<Integer> tloSlotyAkcent = List.of(0, 8, 45, 53);

    private final List<DekoracjaWarstwa> dekoracje = new ArrayList<>();
    private final Set<Integer> dekoracjaSloty = new HashSet<>();

    private int powrotSlot = 49;
    private Material powrotMaterial = Material.ARROW;
    private String powrotNazwa = "&f並 &5&lᴘᴏᴡʀóᴛ";
    private List<String> powrotLore = List.of("&7Wróć do głównego menu ulepszeń.");

    private String loreSeparator = "&8&m                              ";
    private String loreNastepne = "&f亞 &7Następne ulepszenie: &f%item%";
    private String loreCena = "&f中 &7Cena: %price%";
    private String loreKlikLewy = "&a&l▶ &f嗨 &7LPM &8- &a&lulepsz / kup";
    private String loreKlikPrawy = "&6&l▶ &f七 &7PPM &8- &e&llista poziomów";
    private String loreMaxStatus = "&f両 &7Status: &a&lMaksymalny poziom!";
    private String loreMaxBrak = "&f个 &7Następne: &cBrak (max)";
    private String lorePodgladCena = "&f中 &7Cena poziomu: %price%";
    private String podgladNazwa = "&6&l%nazwa% &e★&6%poziom%";
    private List<String> podgladLore = List.of();
    private String podgladStatusZablokowany = "&c✖ &cNajpierw zdobądź niższe poziomy";
    private String podgladStatusAktualny = "&a✔ &aTwój aktualny poziom";
    private String podgladStatusNastepny = "&e▶ &eKolejne ulepszenie";
    private String podgladStatusPosiadany = "&a✔ &aJuż zdobyty";
    private String podgladStatusMax = "&a✔ &aMaksymalny poziom";

    private Economy econ;

    // gracz -> nazwa kategorii aktualnie edytowanej w GUI admina
    private final Map<UUID, String> editing = new HashMap<>();
    // gracz -> "kategoria|poziom" oczekujące na wpisanie ceny na czacie
    private final Map<UUID, String> settingPrice = new HashMap<>();
    // gracz -> kategoria aktualnie podglądana (do przycisku powrotu)
    private final Map<UUID, String> previewing = new HashMap<>();
    /** slot menu glownego /ulepsz -> cat|level|mode (bez ukrytego lore) */
    private final Map<UUID, Map<Integer, String>> mainMenuActions = new HashMap<>();

    public UpgradeMenuListener(JavaPlugin plugin, UpgradeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        loadGuiSettings();
        this.mainMenuTitle = loadTitle("gui.tytul-glowne", MAIN_MENU_TITLE_DEFAULT);
        this.previewMenuTitleTemplate = loadTitleRaw("gui.tytul-podglad", PREVIEW_MENU_TITLE_DEFAULT);
        setupEconomy();
    }

    private FileConfiguration ulepszCfg() {
        return (plugin instanceof boniec.dev.inventory.Main main) ? main.getUlepszConfig() : null;
    }

    private void loadGuiSettings() {
        FileConfiguration cfg = ulepszCfg();
        if (cfg == null) return;

        tloWlaczone = cfg.getBoolean("tlo.wlaczone", false);
        Material w = Material.matchMaterial(cfg.getString("tlo.wypelnienie", "GRAY_STAINED_GLASS_PANE"));
        if (w != null) tloWypelnienie = w;
        Material r = Material.matchMaterial(cfg.getString("tlo.ramka", "GRAY_STAINED_GLASS_PANE"));
        if (r != null) tloRamka = r;
        Material a = Material.matchMaterial(cfg.getString("tlo.akcent", "GRAY_STAINED_GLASS_PANE"));
        if (a != null) tloAkcent = a;
        tloAutoRamka = cfg.getBoolean("tlo.auto-ramka", true);
        tloSlotyAkcent = cfg.getIntegerList("tlo.sloty-akcent");

        dekoracje.clear();
        dekoracjaSloty.clear();
        if (cfg.isConfigurationSection("dekoracja")) {
            var sekcja = cfg.getConfigurationSection("dekoracja");
            for (String klucz : sekcja.getKeys(false)) {
                if ("info".equalsIgnoreCase(klucz)) continue;
                var warstwa = sekcja.getConfigurationSection(klucz);
                if (warstwa == null) continue;
                Material mat = Material.matchMaterial(warstwa.getString("material", "GRAY_STAINED_GLASS_PANE"));
                if (mat == null) continue;
                List<Integer> sloty = warstwa.getIntegerList("slot");
                dekoracje.add(new DekoracjaWarstwa(sloty, mat));
                dekoracjaSloty.addAll(sloty);
            }
        }

        if (cfg.isConfigurationSection("powrot")) {
            var p = cfg.getConfigurationSection("powrot");
            powrotSlot = p.getInt("slot", powrotSlot);
            Material pm = Material.matchMaterial(p.getString("material", powrotMaterial.name()));
            if (pm != null) powrotMaterial = pm;
            powrotNazwa = p.getString("nazwa", powrotNazwa);
            if (p.isList("lore")) powrotLore = p.getStringList("lore");
        }

        loreSeparator = cfg.getString("lore.separator", loreSeparator);
        loreNastepne = cfg.getString("lore.nastepne", loreNastepne);
        loreCena = cfg.getString("lore.cena", loreCena);
        loreKlikLewy = cfg.getString("lore.klik-lewy", loreKlikLewy);
        loreKlikPrawy = cfg.getString("lore.klik-prawy", loreKlikPrawy);
        loreMaxStatus = cfg.getString("lore.max-status", loreMaxStatus);
        loreMaxBrak = cfg.getString("lore.max-brak", loreMaxBrak);
        lorePodgladCena = cfg.getString("lore.podglad-cena", lorePodgladCena);
        podgladNazwa = cfg.getString("podglad.nazwa", podgladNazwa);
        if (cfg.isList("podglad.lore")) podgladLore = cfg.getStringList("podglad.lore");
        podgladStatusZablokowany = cfg.getString("podglad.status-zablokowany", podgladStatusZablokowany);
        podgladStatusAktualny = cfg.getString("podglad.status-aktualny", podgladStatusAktualny);
        podgladStatusNastepny = cfg.getString("podglad.status-nastepny", podgladStatusNastepny);
        podgladStatusPosiadany = cfg.getString("podglad.status-posiadany", podgladStatusPosiadany);
        podgladStatusMax = cfg.getString("podglad.status-max", podgladStatusMax);
        fillerCacheWypelnienie = zrobFiller(tloWypelnienie);
        fillerCacheRamka = zrobFiller(tloRamka);
        fillerCacheAkcent = zrobFiller(tloAkcent);
    }

    private String loadTitleRaw(String path, String fallback) {
        FileConfiguration cfg = ulepszCfg();
        if (cfg == null) return fallback;
        String raw = cfg.getString(path);
        if (raw == null || raw.isEmpty()) return fallback;
        return raw;
    }

    private String loadTitle(String path, String fallback) {
        return ChatColor.translateAlternateColorCodes('&', loadTitleRaw(path, fallback));
    }

    private String previewTitleFor(String kategoria) {
        return ChatColor.translateAlternateColorCodes('&',
                previewMenuTitleTemplate.replace("%kategoria%", kategoria == null ? "" : kategoria));
    }

    private boolean isRamkaSlot(int slot) {
        if (!tloAutoRamka) return false;
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == 5 || col == 0 || col == 8;
    }

    private ItemStack fillerCacheWypelnienie;
    private ItemStack fillerCacheRamka;
    private ItemStack fillerCacheAkcent;

    private ItemStack fillerItem(Material material) {
        if (material == tloWypelnienie && fillerCacheWypelnienie != null) return fillerCacheWypelnienie.clone();
        if (material == tloRamka && fillerCacheRamka != null) return fillerCacheRamka.clone();
        if (material == tloAkcent && fillerCacheAkcent != null) return fillerCacheAkcent.clone();
        return zrobFiller(material);
    }

    private static ItemStack zrobFiller(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void wypelnijTlo(Inventory inv) {
        if (!tloWlaczone) return;
        java.util.Set<Integer> akcent = new java.util.HashSet<>(tloSlotyAkcent);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            Material mat;
            if (akcent.contains(slot)) {
                mat = tloAkcent;
            } else if (isRamkaSlot(slot)) {
                mat = tloRamka;
            } else {
                mat = tloWypelnienie;
            }
            inv.setItem(slot, fillerItem(mat));
        }
    }

    private void ustawDekoracje(Inventory inv) {
        for (DekoracjaWarstwa warstwa : dekoracje) {
            ItemStack item = fillerItem(warstwa.material);
            for (int slot : warstwa.sloty) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, item);
                }
            }
        }
    }

    private static final class DekoracjaWarstwa {
        final List<Integer> sloty;
        final Material material;

        DekoracjaWarstwa(List<Integer> sloty, Material material) {
            this.sloty = sloty;
            this.material = material;
        }
    }

    private ItemStack przyciskPowrotu() {
        ItemStack item = new ItemStack(powrotMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(powrotNazwa));
            List<String> lore = new ArrayList<>();
            for (String linia : powrotLore) {
                lore.add(color(linia));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String color(String s) {
        return boniec.klany.util.Msg.color(s);
    }

    private int poziomGracza(Player player, String cat) {
        return manager.poziomZOdciskow(manager.odciskiEkwipunku(player.getInventory().getContents()), cat);
    }

    private String nazwaItemu(ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }
        return item.getType().name();
    }

    private String statusPodgladu(int lvl, int userLevel, int maxLevel) {
        if (userLevel >= maxLevel && lvl == maxLevel) return podgladStatusMax;
        if (lvl == userLevel) return podgladStatusAktualny;
        if (lvl < userLevel) return podgladStatusPosiadany;
        if (lvl == userLevel + 1) return podgladStatusNastepny;
        return podgladStatusZablokowany;
    }

    private void zastosujWygladPodgladu(ItemStack item, String cat, int lvl, double price, int userLevel, int maxLevel) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        // Nazwa zostaje taka, jak w /aulepsz add — nic nie dopisujemy (★, poziom, zestaw).
        String nazwa = nazwaItemu(item);
        String cena = formatPriceShort(price) + "$";
        List<String> lore = new ArrayList<>();
        List<String> szablon = podgladLore.isEmpty()
                ? List.of(
                "",
                "&f丅 &aPoziom: &f%poziom%",
                "&f中 &eCena: &a%price%",
                "",
                "%enchanty%")
                : podgladLore;
        for (String linia : szablon) {
            if (linia.contains("%status%") || linia.toLowerCase(Locale.ROOT).contains("%zestaw%")
                    || linia.toLowerCase(Locale.ROOT).contains("zestaw:")) {
                continue;
            }
            if (linia.contains("%enchanty%")) {
                List<String> ench = EnchantLoreManager.linieEnchantow(item);
                if (ench.isEmpty()) {
                    lore.add(color("&8Brak zaklęć"));
                } else {
                    lore.addAll(ench);
                }
                continue;
            }
            lore.add(color(linia
                    .replace("%nazwa%", nazwa)
                    .replace("%poziom%", String.valueOf(lvl))
                    .replace("%zestaw%", cat)
                    .replace("%price%", cena)));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault nie znaleziony - system ulepszen bedzie dzialal bez ekonomii (ceny 0).");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Economy provider nie znaleziony - system ulepszen bedzie dzialal bez ekonomii (ceny 0).");
            return;
        }
        econ = rsp.getProvider();
    }

    public void setEditing(UUID uuid, String category) {
        editing.put(uuid, category);
    }

    // ── /ulepsz ───────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy!");
            return true;
        }
        openMainMenu((Player) sender);
        return true;
    }

    private void openMainMenu(Player player) {
        previewing.remove(player.getUniqueId());
        Map<Integer, String> actions = new HashMap<>();
        mainMenuActions.put(player.getUniqueId(), actions);
        Inventory inv = plugin.getServer().createInventory(player, 54, mainMenuTitle);
        wypelnijTlo(inv);
        ustawDekoracje(inv);
        wypelnijMenuGlowne(player, inv, actions);
        player.openInventory(inv);
    }

    private void wypelnijMenuGlowne(Player player, Inventory current, Map<Integer, String> actions) {
        actions.clear();
        int slot = 10;
        java.util.Set<Integer> reservedSlots = new java.util.HashSet<>(dekoracjaSloty);
        reservedSlots.add(powrotSlot);
        List<String> kategorie = manager.getCategoryNames();
        for (String c : kategorie) {
            Integer pinned = manager.getSlot(c);
            if (pinned != null) reservedSlots.add(pinned);
        }

        UpgradeManager.ItemOdcisk[] ekwipunek = manager.odciskiEkwipunku(player.getInventory().getContents());
        for (String cat : kategorie) {
            int maxLevel = manager.getMaxLevel(cat);
            if (maxLevel <= 0) continue;

            int userLevel = manager.poziomZOdciskow(ekwipunek, cat);
            String mode;
            int nextLevel;
            if (userLevel == 0) {
                mode = "BUY";
                nextLevel = 1;
            } else if (userLevel < maxLevel) {
                mode = "UPG";
                nextLevel = userLevel + 1;
            } else {
                mode = "MAX";
                nextLevel = maxLevel;
            }

            ItemStack displayItem = manager.getItem(cat, nextLevel);
            if (displayItem == null) continue;
            double price = manager.getPrice(cat, nextLevel);
            zastosujWygladPodgladu(displayItem, cat, nextLevel, price, userLevel, maxLevel);

            String actionKey = cat + "|" + nextLevel + "|" + mode;
            Integer pinnedSlot = manager.getSlot(cat);
            if (pinnedSlot != null) {
                current.setItem(pinnedSlot, displayItem);
                actions.put(pinnedSlot, actionKey);
                continue;
            }

            while (slot < 54 && reservedSlots.contains(slot)) {
                slot++;
            }
            if (slot < 54) {
                current.setItem(slot, displayItem);
                actions.put(slot, actionKey);
                slot++;
            }
        }
    }

    private String formatPrice(double price) {
        if (price == Math.floor(price)) {
            return String.valueOf((long) price);
        }
        return String.valueOf(price);
    }

    /**
     * Skraca cene do formatu K/M/B (np. 57600 -> "57.6K", 2500000 -> "2.5M").
     * Ponizej 1000 zwraca pelna liczbe bez skracania.
     */
    private String formatPriceShort(double price) {
        long value = (long) Math.floor(price);
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return trimTrailingZero(value / 1_000_000_000.0) + "B";
        } else if (abs >= 1_000_000L) {
            return trimTrailingZero(value / 1_000_000.0) + "M";
        } else if (abs >= 1_000L) {
            return trimTrailingZero(value / 1_000.0) + "K";
        }
        return formatPrice(price);
    }

    private String trimTrailingZero(double d) {
        String s = String.format(Locale.US, "%.1f", d);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }

    /**
     * Pelny string do wyswietlenia: skrocona forma, a obok w nawiasie pelna kwota,
     * np. "$57.6K §8(§a57600$§8)". Jesli liczba jest mniejsza niz 1000, nawias sie nie pojawia.
     */
    private String formatPriceDisplay(double price) {
        String full = formatPrice(price);
        String shortForm = formatPriceShort(price);
        if (shortForm.equals(full)) {
            return ChatColor.GREEN + "$" + full;
        }
        return ChatColor.GREEN + "$" + shortForm + ChatColor.DARK_GRAY + " (" + ChatColor.GREEN + full + "$" + ChatColor.DARK_GRAY + ")";
    }

    private void openCategoryPreview(Player player, String cat) {
        String tytul = previewTitleFor(cat);
        Inventory inv = plugin.getServer().createInventory(player, 54, tytul);
        wypelnijTlo(inv);
        ustawDekoracje(inv);
        if (powrotSlot >= 0 && powrotSlot < inv.getSize()) {
            inv.setItem(powrotSlot, przyciskPowrotu());
        }
        int slot = 10;
        int maxLevel = manager.getMaxLevel(cat);
        int userLevel = poziomGracza(player, cat);
        for (int lvl = 1; lvl <= maxLevel; lvl++) {
            ItemStack item = manager.getItem(cat, lvl);
            if (item == null) continue;
            double price = manager.getPrice(cat, lvl);
            zastosujWygladPodgladu(item, cat, lvl, price, userLevel, maxLevel);
            while (slot < 54 && (dekoracjaSloty.contains(slot) || slot == powrotSlot)) {
                slot++;
            }
            if (slot < 54) {
                inv.setItem(slot, item);
                slot += 1;
            }
        }
        player.openInventory(inv);
        previewing.put(player.getUniqueId(), cat);
    }

    // ── Zapis edycji admina przy zamknięciu GUI ──────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        String title = ChatColor.stripColor(event.getView().getTitle());

        String catPreview = previewing.get(player.getUniqueId());
        if (catPreview != null && title.equals(ChatColor.stripColor(previewTitleFor(catPreview)))) {
            previewing.remove(player.getUniqueId());
        }
        if (title.equals(ChatColor.stripColor(mainMenuTitle))) {
            mainMenuActions.remove(player.getUniqueId());
        }

        if (title.startsWith(ChatColor.stripColor(EDIT_TITLE_PREFIX))) {
            String cat = editing.get(player.getUniqueId());
            if (cat == null) return;

            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 54; i++) {
                ItemStack item = event.getInventory().getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    items.add(item.clone());
                }
            }
            manager.setItems(cat, items);

            if (!settingPrice.containsKey(player.getUniqueId())) {
                player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.GREEN + "Zapisano ścieżkę ulepszeń dla " + ChatColor.WHITE + cat + ChatColor.GREEN + "!");
                editing.remove(player.getUniqueId());
            }
        }
    }

    // ── Klikanie w GUI edycji admina (SHIFT+PPM = ustaw cenę) ────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = ChatColor.stripColor(event.getView().getTitle());

        // 1. GUI edycji admina
        if (title.startsWith(ChatColor.stripColor(EDIT_TITLE_PREFIX))) {
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() != Material.AIR && event.getClickedInventory() == event.getView().getTopInventory()) {
                    event.setCancelled(true);
                    String cat = editing.get(player.getUniqueId());
                    if (cat == null) return;

                    List<ItemStack> items = new ArrayList<>();
                    int targetIdx = -1;
                    int idx = 1;
                    for (int s = 0; s < 54; s++) {
                        ItemStack i = event.getInventory().getItem(s);
                        if (i != null && i.getType() != Material.AIR) {
                            items.add(i.clone());
                            if (event.getSlot() == s) {
                                targetIdx = idx;
                            }
                            idx++;
                        }
                    }
                    manager.setItems(cat, items);

                    if (targetIdx > 0) {
                        settingPrice.put(player.getUniqueId(), cat + "|" + targetIdx);
                        player.closeInventory();
                        player.sendMessage("");
                        player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.GREEN + "Wpisz na chacie cenę ulepszenia na ten poziom.");
                        player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.GRAY + "Wpisz " + ChatColor.RED + "anuluj" + ChatColor.GRAY + ", aby zrezygnować.");
                        player.sendMessage("");
                    }
                }
            }
            return;
        }

        // 2. MENU GŁÓWNE /ulepsz
        if (title.equals(ChatColor.stripColor(mainMenuTitle))) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Map<Integer, String> actions = mainMenuActions.get(player.getUniqueId());
            String data = actions != null ? actions.get(event.getSlot()) : null;
            if (data == null) return;

            String[] parts = data.split("\\|");
            if (parts.length < 3) return;
            String cat = parts[0];
            int nextIdx;
            try {
                nextIdx = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return;
            }
            String mode = parts[2];

            ClickType click = event.getClick();

            // PRAWY PRZYCISK -> podgląd
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                openCategoryPreview(player, cat);
                return;
            }

            // LEWY PRZYCISK -> kupno/ulepszenie
            if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
                if (mode.equals("MAX")) {
                    player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.RED + "Osiągnąłeś już maksymalny poziom w tej kategorii!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                double price = manager.getPrice(cat, nextIdx);
                ItemStack nextItem = manager.getItem(cat, nextIdx);
                if (nextItem == null) return;

                double balance = (econ != null) ? econ.getBalance(player) : Double.MAX_VALUE;
                if (balance < price) {
                    player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.RED + "Nie masz wystarczająco pieniędzy! Potrzebujesz " + ChatColor.GREEN + "$" + formatPrice(price) + ChatColor.RED + ".");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                if (mode.equals("BUY")) {
                    if (econ != null) econ.withdrawPlayer(player, price);
                    giveItem(player, nextItem);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
                    player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.GREEN + "Zakupiono poziom 1 z kategorii " + ChatColor.WHITE + cat + ChatColor.GREEN + " za " + ChatColor.GREEN + "$" + formatPrice(price) + ChatColor.GREEN + "!");
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 1L);
                    return;
                }

                if (mode.equals("UPG")) {
                    int prevIdx = nextIdx - 1;
                    ItemStack prevItem = manager.getItem(cat, prevIdx);
                    if (prevItem == null) return;

                    boolean hasItem = false;
                    for (int i = 0; i < player.getInventory().getSize(); i++) {
                        ItemStack invItem = player.getInventory().getItem(i);
                        if (invItem != null && UpgradeManager.compareItems(invItem, prevItem)) {
                            ItemStack toRemove = invItem.clone();
                            toRemove.setAmount(1);
                            if (invItem.getAmount() <= 1) {
                                player.getInventory().setItem(i, null);
                            } else {
                                invItem.setAmount(invItem.getAmount() - 1);
                            }
                            hasItem = true;
                            break;
                        }
                    }

                    if (!hasItem) {
                        player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.RED + "Nie znaleziono przedmiotu do ulepszenia w ekwipunku!");
                        player.closeInventory();
                        return;
                    }

                    if (econ != null) econ.withdrawPlayer(player, price);
                    giveItem(player, nextItem);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
                    player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.GREEN + "Ulepszono przedmiot w kategorii " + ChatColor.WHITE + cat + ChatColor.GREEN + " za " + ChatColor.GREEN + "$" + formatPrice(price) + ChatColor.GREEN + "!");
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 1L);
                }
            }
            return;
        }

        // 3. MENU PODGLĄDU KATEGORII
        String catPodglad = previewing.get(player.getUniqueId());
        if (catPodglad != null && title.equals(ChatColor.stripColor(previewTitleFor(catPodglad)))) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;

            if (event.getSlot() == powrotSlot) {
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 1L);
            }
        }
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.equals(ChatColor.stripColor(mainMenuTitle))) {
            event.setCancelled(true);
            return;
        }
        String cat = previewing.get(event.getWhoClicked().getUniqueId());
        if (cat != null && title.equals(ChatColor.stripColor(previewTitleFor(cat)))) {
            event.setCancelled(true);
        }
    }

    // ── Ustawianie ceny na czacie ────────────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!settingPrice.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        String msg = event.getMessage();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("anuluj")) {
                player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.RED + "Anulowano.");
                settingPrice.remove(player.getUniqueId());
                editing.remove(player.getUniqueId());
                return;
            }

            double price;
            try {
                price = Double.parseDouble(msg.replace(",", "."));
            } catch (NumberFormatException e) {
                player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.RED + "To nie jest liczba!");
                return;
            }

            String data = settingPrice.get(player.getUniqueId());
            String[] parts = data.split("\\|");
            String cat = parts[0];
            int lvl = Integer.parseInt(parts[1]);

            manager.setPrice(cat, lvl, price);
            player.sendMessage(AdminUpgradeCommand.PREFIX + ChatColor.GREEN + "Ustawiono cenę " + ChatColor.YELLOW + formatPrice(price) + "$ " + ChatColor.GREEN + "na poziom " + ChatColor.WHITE + lvl + ChatColor.GREEN + "!");
            settingPrice.remove(player.getUniqueId());

            player.performCommand("aulepsz add " + cat);
        });
    }
}
