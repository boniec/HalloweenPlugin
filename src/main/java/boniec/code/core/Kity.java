package boniec.code.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.*;
import java.util.*;

public class Kity implements CommandExecutor, Listener {

    private final JavaPlugin plugin;
    private Connection connection;
    private File kitsFile;
    private YamlConfiguration kitsConfig;

    // Tytuły GUI - wczytywane z config.yml, odświeżane przy /adminkit reload
    private String guiTitle;
    private String previewTitlePrefix;
    private String adminGuiTitle;
    private String editGuiTitlePrefix;
    private String actionsGuiTitlePrefix;

    // Znacznik NBT/PDC zapisywany na ikonach kitów w GUI, żeby rozpoznawać kit
    // po KLIKNIĘTYM PRZEDMIOCIE, a nie po jego wyświetlanej nazwie (display_name).
    // Wcześniej kliknięcia (zwłaszcza PPM w /kit) potrafiły "nic nie robić", gdy
    // display_name z kity.yml różnił się od prawdziwej nazwy kitu w bazie -
    // wyszukiwanie w bazie po nazwie z display_name po prostu nie znajdowało wiersza.
    private final NamespacedKey kitNameKey;

    // Znacznik akcji na przyciskach w menu zarządzania kitem (/adminkit -> klik kitu),
    // np. "permission", "cooldown", "slot", "lore", "items", "preview", "delete".
    private final NamespacedKey kitActionKey;

    // Gracze, którzy właśnie kliknęli przycisk typu "Zmień permisję/cooldown/slot/lore"
    // i mają teraz wpisać nową wartość na czacie (patrz onPlayerChat).
    private final Map<UUID, PendingEdit> pendingEdits = new HashMap<>();

    // NAPRAWA: wcześniej rozpoznawaliśmy "swoje" GUI WYŁĄCZNIE po tytule okna
    // (title.equals(...) / title.startsWith(...)). To działa dla własnych menu,
    // ale jest ryzykowne na serwerze z wieloma pluginami - jeśli jakikolwiek inny
    // plugin (menu/sklep/inne GUI) stworzy ekwipunek z identycznym lub podobnym
    // tytułem (nawet przypadkiem, np. sam kolor "&8" bez tekstu), nasz handler
    // wchodził w "if" i robił event.setCancelled(true) na CUDZYM GUI - stąd
    // objawy "przez ten plugin w innych GUI nie da się nic włożyć".
    // Teraz oprócz tytułu sprawdzamy też, czy to FIZYCZNIE ten sam obiekt
    // Inventory, który sami stworzyliśmy (identity, nie tytuł) - dzięki temu
    // nawet przy zbieżności tytułów nigdy nie ruszymy cudzego ekwipunku.
    private final Set<Inventory> ownedInventories = Collections.newSetFromMap(new WeakHashMap<>());

    private enum EditType { PERMISSION, COOLDOWN, SLOT, LORE }

    private static class PendingEdit {
        final String kitName;
        final EditType type;

        PendingEdit(String kitName, EditType type) {
            this.kitName = kitName;
            this.type = type;
        }
    }

    public Kity(JavaPlugin plugin) {
        this.plugin = plugin;
        this.kitNameKey = new NamespacedKey(plugin, "kit_name");
        this.kitActionKey = new NamespacedKey(plugin, "kit_action");
        plugin.saveDefaultConfig();
        loadGuiSettings();
        setupDatabase();
        setupConfig();
        if (plugin.getCommand("kit") != null) plugin.getCommand("kit").setExecutor(this);
        if (plugin.getCommand("adminkit") != null) plugin.getCommand("adminkit").setExecutor(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ================= Config / komunikaty =================

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private void loadGuiSettings() {
        guiTitle = color(cfg().getString("gui.titles.kit-list", "&8Zestawy"));
        previewTitlePrefix = color(cfg().getString("gui.titles.preview-prefix", "&8Podgląd: "));
        adminGuiTitle = color(cfg().getString("gui.titles.admin-list", "&4Admin: &8Wybierz zestaw"));
        editGuiTitlePrefix = color(cfg().getString("gui.titles.edit-prefix", "&4Edycja: &8"));
        actionsGuiTitlePrefix = color(cfg().getString("gui.titles.actions-prefix", "&4Zarządzaj: &8"));
    }

    private int guiSize() {
        int size = cfg().getInt("gui.size", 45);
        // Rozmiar musi być wielokrotnością 9, w zakresie 9-54
        size = Math.max(9, Math.min(54, size));
        return (size / 9) * 9 == size ? size : 45;
    }

    private String msg(String path, String def) {
        return color(cfg().getString("messages.prefix", "") + cfg().getString("messages." + path, def));
    }

    private String msg(String path, String def, String... placeholders) {
        String result = cfg().getString("messages." + path, def);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }
        return color(cfg().getString("messages.prefix", "") + result);
    }

    // ================= Setup =================

    private void setupDatabase() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            connection = UstawieniaDatabase.open(plugin);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS kits (" +
                        "name VARCHAR(32) PRIMARY KEY," +
                        "cooldown INT," +
                        "permission VARCHAR(64)," +
                        "slot INT," +
                        "icon TEXT," +
                        "items TEXT," +
                        "enabled INTEGER NOT NULL DEFAULT 1" +
                        ")");
                try {
                    stmt.execute("ALTER TABLE kits ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1");
                } catch (SQLException ignored) {
                }
                stmt.execute("CREATE TABLE IF NOT EXISTS player_cooldowns (" +
                        "uuid VARCHAR(36)," +
                        "kit_name VARCHAR(32)," +
                        "last_use LONG," +
                        "PRIMARY KEY (uuid, kit_name)" +
                        ")");
            }
            plugin.getLogger().info("Połączono z bazą danych kitów (" + UstawieniaDatabase.PLIK + ").");
        } catch (Exception e) {
            connection = null;
            plugin.getLogger().severe("NIE udało się połączyć z bazą danych kitów! /kit oraz /adminkit nie będą działać, dopóki to nie zostanie naprawione.");
            e.printStackTrace();
        }
    }

    private void setupConfig() {
        kitsFile = new File(plugin.getDataFolder(), "kity.yml");
        if (!kitsFile.exists()) {
            // Kopiuje domyślny kity.yml z przykładem i komentarzami z jara pluginu.
            plugin.saveResource("kity.yml", false);
            plugin.getLogger().info("Nie znaleziono kity.yml w folderze pluginu - utworzono domyślny plik.");
        }

        String startupError = validateYaml(kitsFile);
        if (startupError != null) {
            plugin.getLogger().severe("=================================================================");
            plugin.getLogger().severe("kity.yml MA BŁĄD SKŁADNI YAML I NIE ZOSTAŁ WCZYTANY POPRAWNIE!");
            plugin.getLogger().severe("Błąd: " + startupError);
            plugin.getLogger().severe("Dopóki to nie zostanie naprawione, ŻADNE zmiany w kity.yml (w tym");
            plugin.getLogger().severe("nowe kity z polem 'items') NIE ZADZIAŁAJĄ - wczytana zostanie");
            plugin.getLogger().severe("ostatnia poprawna wersja / pusty config.");
            plugin.getLogger().severe("=================================================================");
        }

        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        syncKitsFromYaml(null);
    }

    /**
     * Pozwala zdefiniować SZKIELET kitu w kity.yml (bez potrzeby /adminkit create) -
     * każdy wpis pod "kits:", którego nazwy NIE MA jeszcze w bazie danych, zostaje
     * automatycznie utworzony w bazie przy starcie pluginu oraz przy /adminkit reload,
     * ze slotem/cooldownem/permisją/ikoną z kity.yml i ZAWSZE zaczynając od 0
     * przedmiotów (chyba że jawnie podasz "items" - patrz niżej).
     *
     * Przedmioty do kitu dorzucasz PÓŹNIEJ w grze: /adminkit -> kliknij kit ->
     * "Aktualizuj Przedmioty i Ikonę" (trzymając docelowy ekwipunek) - dzięki temu
     * możesz dawać zaklęcia, nazwy, NBT itd., czego samym tekstem w yamlu się nie da.
     *
     * Jeśli kit o danej nazwie JUŻ ISTNIEJE w bazie, ten kod go NIE RUSZA (żeby nie
     * nadpisać przedmiotów, które już ustawiłeś przez panel) - kity.yml nadal tylko
     * nadpisuje wygląd (slot/material/display_name/lore/cooldown) w GUI, tak jak wcześniej.
     */
    private void syncKitsFromYaml(CommandSender sender) {
        if (!bindConnection()) {
            plugin.getLogger().warning("kity.yml: pomijam synchronizację kitów - brak połączenia z bazą danych.");
            return;
        }
        if (!kitsConfig.contains("kits")) {
            plugin.getLogger().warning("kity.yml: sekcja 'kits:' nie istnieje albo plik jest pusty/uszkodzony - nic nie zsynchronizowano.");
            return;
        }
        org.bukkit.configuration.ConfigurationSection section = kitsConfig.getConfigurationSection("kits");
        if (section == null) {
            plugin.getLogger().warning("kity.yml: sekcja 'kits:' jest niepoprawna - nic nie zsynchronizowano.");
            return;
        }

        Set<String> allKitKeys = section.getKeys(false);
        plugin.getLogger().info("kity.yml: znaleziono " + allKitKeys.size() + " wpis(ów) pod 'kits:': " + allKitKeys);

        int created = 0;
        for (String name : allKitKeys) {
            String path = "kits." + name;

            if (kitExistsInDb(name)) {
                plugin.getLogger().info("kity.yml: kit '" + name + "' już istnieje w bazie - pomijam tworzenie (nadal działają nadpisania wizualne z kity.yml).");
                continue;
            }

            int cooldown = kitsConfig.getInt(path + ".cooldown", 0);
            int slot = kitsConfig.getInt(path + ".slot", -1);
            // .trim() jest istotny - spacja na końcu permisji w kity.yml (łatwa do
            // niezauważenia, np. po skopiowaniu z czatu) powodowała, że hasPermission()
            // nigdy nie zwracało true, bo w LuckPermsie/na serwerze permisja nie ma spacji.
            String permission = kitsConfig.getString(path + ".permission", "none").trim();

            Material iconMat = null;
            if (kitsConfig.contains(path + ".material")) {
                iconMat = Material.matchMaterial(kitsConfig.getString(path + ".material", ""));
            }
            if (iconMat == null) iconMat = Material.STONE;
            ItemStack icon = new ItemStack(iconMat);

            // Domyślnie kit startuje z 0 przedmiotów - "items" w yamlu jest opcjonalne
            // i służy tylko do prostych przypadków bez enchantów/NBT.
            ItemStack[] items = kitsConfig.contains(path + ".items")
                    ? parseSimpleItems(name, kitsConfig.getStringList(path + ".items"))
                    : new ItemStack[0];

            try {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO kits (name, cooldown, permission, slot, icon, items) VALUES (?, ?, ?, ?, ?, ?)");
                ps.setString(1, name);
                ps.setInt(2, cooldown);
                ps.setString(3, permission);
                ps.setInt(4, slot);
                ps.setString(5, itemToString(icon));
                ps.setString(6, itemsToString(items));
                ps.executeUpdate();
                ps.close();
                created++;
                plugin.getLogger().info("kity.yml: utworzono nowy kit '" + name + "' w bazie (start: " + items.length + " przedmiot(ów) - uzupełnij przez /adminkit -> \"Aktualizuj Przedmioty i Ikonę\").");
            } catch (SQLException e) {
                plugin.getLogger().severe("Nie udało się utworzyć kitu '" + name + "' z kity.yml w bazie danych.");
                e.printStackTrace();
            }
        }

        usunKitDiscordZBazy();

        plugin.getLogger().info("kity.yml: zakończono synchronizację - utworzono " + created + " nowy(ch) kit(ów) z " + allKitKeys.size() + " zdefiniowanych w pliku.");
        if (sender != null && created > 0) {
            sender.sendMessage(color("&aUtworzono " + created + " nowy(ch) kit(ów) z kity.yml (0 przedmiotów - uzupełnij je w panelu)."));
        }
    }

    private void usunKitDiscordZBazy() {
        if (connection == null) return;
        for (String name : List.of("dc", "DC", "discord", "Discord")) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM kits WHERE name = ?")) {
                ps.setString(1, name);
                int n = ps.executeUpdate();
                if (n > 0) {
                    plugin.getLogger().info("kity.yml: usunieto kit '" + name + "' (Discord) z bazy.");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Nie udalo sie usunac kitu '" + name + "': " + e.getMessage());
            }
        }
    }

    /** Sprawdza, czy kit o danej nazwie już istnieje w bazie danych. */
    private boolean kitExistsInDb(String name) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM kits WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            rs.close();
            ps.close();
            return exists;
        } catch (SQLException e) {
            plugin.getLogger().severe("Błąd sprawdzania, czy kit '" + name + "' istnieje w bazie danych.");
            e.printStackTrace();
            return true; // bezpieczniej założyć, że istnieje, niż przypadkiem nadpisać dane
        }
    }

    /** Parsuje listę "MATERIAL" / "MATERIAL:ILOŚĆ" na przedmioty. Nieznane materiały są pomijane z ostrzeżeniem. */
    private ItemStack[] parseSimpleItems(String kitName, List<String> lines) {
        List<ItemStack> items = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            String[] parts = line.trim().split(":");
            Material mat = Material.matchMaterial(parts[0].trim());
            if (mat == null || mat == Material.AIR) {
                plugin.getLogger().warning("kity.yml: kit '" + kitName + "' ma nieznany przedmiot '" + parts[0].trim() + "' w liście items - pomijam.");
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    amount = 1;
                }
            }
            amount = Math.max(1, Math.min(64, amount));
            items.add(new ItemStack(mat, amount));
        }
        return items.toArray(new ItemStack[0]);
    }

    /**
     * Zwraca cooldown kitu, respektując ewentualny override w kity.yml
     * (kits.<nazwa>.cooldown). Dzięki temu cooldown można edytować
     * ręcznie w pliku config bez ruszania bazy danych.
     */
    private int effectiveCooldown(String kitName, int dbCooldown) {
        String path = "kits." + kitName + ".cooldown";
        if (kitsConfig.contains(path)) {
            return kitsConfig.getInt(path);
        }
        return dbCooldown;
    }

    public void close() {
        connection = null;
    }

    /** Odswieza wskaznik na wspolne polaczenie data.db (stare Connection po reloadzie bywa zamkniete). */
    private boolean bindConnection() {
        if (!CoreDatabase.isOpen()) {
            connection = null;
            return false;
        }
        try {
            connection = CoreDatabase.connection();
            return !connection.isClosed();
        } catch (SQLException e) {
            connection = null;
            return false;
        }
    }

    /**
     * Sprawdza, czy baza danych jest gotowa do użycia. Jeśli nie - informuje gracza
     * zamiast pozwolić na cichy wyjątek (NullPointerException), przez który GUI
     * wcześniej w ogóle się nie otwierało.
     */
    private boolean ensureDatabase(Player p) {
        if (!bindConnection()) {
            try {
                UstawieniaDatabase.open(plugin);
            } catch (SQLException ignored) {
            }
            bindConnection();
        }
        if (connection == null) {
            Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
            plugin.getLogger().warning("Próba użycia kitów przy braku połączenia z bazą danych (gracz: " + p.getName() + ").");
            return false;
        }
        return true;
    }

    /**
     * Przeładowuje config.yml i kity.yml. Najpierw WALIDUJE oba pliki (próbuje je
     * sparsować do tymczasowego obiektu) - jeśli któryś ma błąd składni YAML
     * (np. zły cudzysłów, tabulacja zamiast spacji, niecapytany dwukropek w tekście),
     * reload zostaje przerwany i admin dostaje DOKŁADNY komunikat błędu zamiast
     * cichego wyzerowania ustawień do wartości domyślnych.
     */
    private void reloadEverything(CommandSender sender) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        String configError = validateYaml(configFile);
        if (configError != null) {
            sender.sendMessage(ChatColor.RED + "Błąd w config.yml, reload przerwany: " + configError);
            plugin.getLogger().severe("config.yml ma błąd składni YAML (sprawdź wcięcia - tylko spacje, oraz cudzysłowy wokół tekstu z dwukropkiem): " + configError);
            return;
        }

        String kitsError = validateYaml(kitsFile);
        if (kitsError != null) {
            sender.sendMessage(ChatColor.RED + "Błąd w kity.yml, reload przerwany: " + kitsError);
            plugin.getLogger().severe("kity.yml ma błąd składni YAML (sprawdź wcięcia - tylko spacje, oraz cudzysłowy wokół tekstu z dwukropkiem): " + kitsError);
            return;
        }

        plugin.reloadConfig();
        if (plugin instanceof boniec.dev.inventory.Main core) {
            boniec.dev.polezamek.Main pz = core.getPoleZamek();
            if (pz != null) {
                pz.reloadManagers();
            }
        }
        loadGuiSettings();
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        syncKitsFromYaml(sender);
        sender.sendMessage(msg("config-reloaded", "&aPrzeładowano config.yml oraz kity.yml"));
    }

    /** Zwraca null jeśli plik jest poprawnym YAML-em, w przeciwnym razie komunikat błędu. */
    private String validateYaml(File file) {
        if (!file.exists()) return null;
        try {
            YamlConfiguration test = new YamlConfiguration();
            test.load(file);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }


    private ItemStack safeIcon(ItemStack icon) {
        if (icon == null || icon.getType() == Material.AIR) {
            return new ItemStack(Material.CHEST);
        }
        return icon;
    }

    /**
     * Odczytuje nazwę kitu z klikniętej ikony w GUI. Najpierw sprawdza znacznik PDC
     * (ustawiany świeżo za każdym razem, gdy budujemy GUI - patrz openKitGUI/openAdminGUI),
     * a dopiero jak go nie ma (stare/nietypowe przedmioty), wraca do starego sposobu
     * (nazwa wyświetlana bez kolorów). Dzięki temu kliknięcie działa nawet, gdy
     * display_name w kity.yml różni się od prawdziwej nazwy kitu w bazie.
     */
    private String extractKitName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String fromTag = meta.getPersistentDataContainer().get(kitNameKey, PersistentDataType.STRING);
        if (fromTag != null && !fromTag.isEmpty()) return fromTag;
        if (meta.hasDisplayName()) return ChatColor.stripColor(meta.getDisplayName());
        return null;
    }

    // ================= Komendy =================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("kit")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(msg("players-only", "&cKomenda tylko dla graczy!"));
                return true;
            }
            openKitGUI((Player) sender);
            return true;
        }

        if (command.getName().equalsIgnoreCase("adminkit")) {
            if (!sender.hasPermission("core.adminkit")) {
                sender.sendMessage(msg("no-permission", "&cBrak uprawnień!"));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadEverything(sender);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("edit")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(msg("players-only", "&cKomenda tylko dla graczy!"));
                    return true;
                }
                // Bezpośrednie wejście w edycję przedmiotów kitu, bez klikania w GUI listy
                // kitów - przydatne, gdy nazwa kitu jest znana z góry (np. skrypt/komenda
                // w tabliczce). openItemsEditGUI() sama sprawdzi, czy kit istnieje w bazie.
                openItemsEditGUI((Player) sender, args[1]);
                return true;
            }
            if (args.length == 0) {
                if (sender instanceof Player) {
                    openAdminGUI((Player) sender);
                } else {
                    sender.sendMessage("GUI dostępne tylko dla graczy.");
                }
                return true;
            }
            if (args.length >= 5 && args[0].equalsIgnoreCase("create")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(msg("players-only", "&cKomenda tylko dla graczy!"));
                    return true;
                }
                Player p = (Player) sender;
                String name = args[1];
                int cooldown;
                int slot;
                try {
                    cooldown = Integer.parseInt(args[2]);
                    slot = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(msg("invalid-number", "&cCooldown i slot muszą być liczbami!"));
                    return true;
                }
                String permission = args[3].trim();

                ItemStack icon = safeIcon(p.getInventory().getItemInMainHand());

                List<ItemStack> items = new ArrayList<>();
                for (ItemStack is : p.getInventory().getContents()) {
                    if (is != null && is.getType() != Material.AIR) {
                        items.add(is);
                    }
                }

                createKit(name, cooldown, permission, slot, icon, items.toArray(new ItemStack[0]));
                sender.sendMessage(msg("kit-created", "&aUtworzono zestaw %kit%", "%kit%", name));
                return true;
            }
            sender.sendMessage(msg("usage-adminkit", "&cUżycie: /adminkit create <nazwa> <cooldown> <permisja> <slot> | edit <nazwa> | reload"));
            return true;
        }
        return false;
    }

    private void createKit(String name, int cooldown, String permission, int slot, ItemStack icon, ItemStack[] items) {
        if (connection == null) {
            plugin.getLogger().warning("Nie można utworzyć kitu '" + name + "' - brak połączenia z bazą danych.");
            return;
        }

        // Zapis warstwy wizualnej + cooldownu do YAML (można je potem edytować ręcznie w kity.yml)
        String path = "kits." + name;
        kitsConfig.set(path + ".slot", slot);
        kitsConfig.set(path + ".cooldown", cooldown);
        kitsConfig.set(path + ".material", icon.getType().name());
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) kitsConfig.set(path + ".display_name", meta.getDisplayName().replace("§", "&"));
            else kitsConfig.set(path + ".display_name", "&a" + name);

            if (meta.hasLore() && meta.getLore() != null) {
                List<String> lore = new ArrayList<>();
                for (String l : meta.getLore()) lore.add(l.replace("§", "&"));
                kitsConfig.set(path + ".lore", lore);
            }
        }
        try {
            kitsConfig.save(kitsFile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!bindConnection()) return;
                try {
                    CoreDatabase.runInTransaction(c -> {
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT OR REPLACE INTO kits (name, cooldown, permission, slot, icon, items) VALUES (?, ?, ?, ?, ?, ?)")) {
                            ps.setString(1, name);
                            ps.setInt(2, cooldown);
                            ps.setString(3, permission);
                            ps.setInt(4, slot);
                            ps.setString(5, itemToString(icon));
                            ps.setString(6, itemsToString(items));
                            ps.executeUpdate();
                        }
                    });
                } catch (Exception e) {
                    plugin.getLogger().severe("Błąd podczas zapisu kitu '" + name + "' do bazy danych.");
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // ================= GUI =================

    private void openKitGUI(Player p) {
        if (!ensureDatabase(p)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!bindConnection()) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
                    return;
                }
                try {
                    Inventory inv = CoreDatabase.runLockedQuery(c -> {
                        Inventory gui = Bukkit.createInventory(null, guiSize(), guiTitle);
                        setupDecorations(gui);
                        try (Statement stmt = c.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT * FROM kits")) {
                            while (rs.next()) {
                                String name = rs.getString("name");
                                if (name != null && (name.equalsIgnoreCase("dc") || name.equalsIgnoreCase("discord"))) {
                                    continue;
                                }
                                int slot = rs.getInt("slot");
                                String iconStr = rs.getString("icon");
                                ItemStack icon = safeIcon(stringToItem(iconStr));

                                String path = "kits." + name;
                                if (kitsConfig.contains(path)) {
                                    if (kitsConfig.contains(path + ".slot")) slot = kitsConfig.getInt(path + ".slot");
                                    if (kitsConfig.contains(path + ".material")) {
                                        Material mat = Material.matchMaterial(kitsConfig.getString(path + ".material", ""));
                                        if (mat != null && mat != Material.AIR) icon.setType(mat);
                                    }
                                }

                                boolean kitOn = kitEnabled(rs);
                                if (slot >= 0 && slot < guiSize()) {
                                    ItemMeta meta = icon.getItemMeta();
                                    if (meta == null) continue;

                                    if (kitsConfig.contains(path + ".display_name")) {
                                        meta.setDisplayName(color(kitsConfig.getString(path + ".display_name")));
                                    } else {
                                        meta.setDisplayName((kitOn ? ChatColor.GREEN : ChatColor.RED) + name);
                                    }

                                    List<String> lore = new ArrayList<>();
                                    if (kitsConfig.contains(path + ".lore")) {
                                        for (String line : kitsConfig.getStringList(path + ".lore")) {
                                            lore.add(color(line));
                                        }
                                    }
                                    if (!kitOn) lore.add(color("&cWylaczony"));
                                    if (!lore.isEmpty()) meta.setLore(lore);
                                    meta.getPersistentDataContainer().set(kitNameKey, PersistentDataType.STRING, name);
                                    icon.setItemMeta(meta);
                                    gui.setItem(slot, icon);
                                }
                            }
                        }
                        return gui;
                    });
                    ownedInventories.add(inv);
                    Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(inv));
                } catch (Exception e) {
                    plugin.getLogger().severe("Błąd podczas otwierania GUI kitów dla gracza " + p.getName() + ".");
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void openAdminGUI(Player p) {
        if (!ensureDatabase(p)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!bindConnection()) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
                    return;
                }
                try {
                    Inventory inv = CoreDatabase.runLockedQuery(c -> {
                        Inventory gui = Bukkit.createInventory(null, 54, adminGuiTitle);
                        try (Statement stmt = c.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT name, icon, enabled FROM kits")) {
                            while (rs.next()) {
                                String name = rs.getString("name");
                                if (name != null && (name.equalsIgnoreCase("dc") || name.equalsIgnoreCase("discord"))) {
                                    continue;
                                }
                                boolean kitOn = kitEnabled(rs);
                                ItemStack icon = safeIcon(stringToItem(rs.getString("icon")));
                                ItemMeta meta = icon.getItemMeta();
                                if (meta == null) continue;
                                meta.setDisplayName((kitOn ? ChatColor.YELLOW : ChatColor.RED) + name);
                                List<String> lore = new ArrayList<>();
                                lore.add(ChatColor.GRAY + "Kliknij, aby zarzadzac");
                                lore.add(kitOn ? color("&aWlaczony") : color("&cWylaczony"));
                                meta.setLore(lore);
                                meta.getPersistentDataContainer().set(kitNameKey, PersistentDataType.STRING, name);
                                icon.setItemMeta(meta);
                                gui.addItem(icon);
                            }
                        }
                        return gui;
                    });
                    ownedInventories.add(inv);
                    Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(inv));
                } catch (Exception e) {
                    plugin.getLogger().severe("Błąd podczas otwierania panelu admina kitów dla gracza " + p.getName() + ".");
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Menu zarządzania jednym kitem (/adminkit -> klik kitu). Wcześniej klik od razu
     * otwierał edycję przedmiotów, a przyciski "Zmień permisję/cooldown/slot/lore"
     * (mimo że komunikaty dla nich już istniały w config.yml) w ogóle nie istniały -
     * jedynym sposobem poprawienia np. literówki/wielkości liter w permisji kitu było
     * ręczne grzebanie w bazie SQLite. To menu daje do tego realny przycisk w GUI.
     */
    private void openKitActionsGUI(Player p, String kitName) {
        if (!ensureDatabase(p)) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean on = true;
                try {
                    PreparedStatement ps = connection.prepareStatement("SELECT enabled FROM kits WHERE name = ?");
                    ps.setString(1, kitName);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) on = kitEnabled(rs);
                    rs.close();
                    ps.close();
                } catch (Exception e) {
                    on = true;
                }
                boolean enabled = on;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Inventory inv = Bukkit.createInventory(null, 18, actionsGuiTitlePrefix + kitName);
                    if (enabled) {
                        inv.setItem(0, actionButton(Material.LIME_DYE, "&aWlaczony",
                                List.of("&7Kliknij, aby &cwylaczyc &7ten kit."), "toggle", kitName));
                    } else {
                        inv.setItem(0, actionButton(Material.GRAY_DYE, "&cWylaczony",
                                List.of("&7Kliknij, aby &awlaczyc &7ten kit."), "toggle", kitName));
                    }
                    inv.setItem(2, actionButton(Material.CHEST, "&fEdytuj przedmioty i ikonę", "items", kitName));
                    inv.setItem(3, actionButton(Material.PAPER, "&fZmień permisję", "permission", kitName));
                    inv.setItem(4, actionButton(Material.CLOCK, "&fZmień cooldown", "cooldown", kitName));
                    inv.setItem(5, actionButton(Material.HOPPER, "&fZmień slot", "slot", kitName));
                    inv.setItem(6, actionButton(Material.WRITABLE_BOOK, "&fZmień lore", "lore", kitName));
                    inv.setItem(11, actionButton(Material.ITEM_FRAME, "&fPodgląd", "preview", kitName));
                    inv.setItem(13, actionButton(Material.BARRIER, "&cUsuń kit", "delete", kitName));
                    inv.setItem(17, actionButton(Material.ARROW, "&7« Wróć", "back", kitName));
                    ownedInventories.add(inv);
                    p.openInventory(inv);
                });
            }
        }.runTaskAsynchronously(plugin);
    }

    private ItemStack actionButton(Material material, String displayName, String action, String kitName) {
        return actionButton(material, displayName, null, action, kitName);
    }

    private ItemStack actionButton(Material material, String displayName, List<String> lore, String action, String kitName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(displayName));
            if (lore != null && !lore.isEmpty()) {
                List<String> kolor = new ArrayList<>();
                for (String l : lore) kolor.add(color(l));
                meta.setLore(kolor);
            }
            meta.getPersistentDataContainer().set(kitActionKey, PersistentDataType.STRING, action);
            meta.getPersistentDataContainer().set(kitNameKey, PersistentDataType.STRING, kitName);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Sloty "prostokąta" na przedmioty w edytorze kitu (bez slotu 4 - ten zostaje wolny/
    // ozdobny, tak jak w podglądzie). Te same sloty są używane przy zapisie zawartości.
    private static final int[] EDIT_ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    /**
     * Otwiera zwykły, EDYTOWALNY ekwipunek wypełniony aktualną zawartością kitu.
     * Żadnych przycisków, żadnego podmenu - admin normalnie wkłada/wyjmuje/przekłada
     * przedmioty (można też przeciągać z własnego ekwipunku), a po zamknięciu okna
     * cała zawartość zostaje zapisana jako nowa lista przedmiotów kitu (patrz
     * onInventoryClose). Ikona kitu w GUI /kit i /adminkit ustawiana jest automatycznie
     * na pierwszy przedmiot z tej listy. Przedmioty wchodzą TYLKO do prostokąta
     * EDIT_ITEM_SLOTS (slot 4 i sloty dekoracyjne są zablokowane).
     */
    private void openItemsEditGUI(Player p, String kitName) {
        if (!ensureDatabase(p)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement("SELECT items FROM kits WHERE name = ?");
                    ps.setString(1, kitName);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        rs.close();
                        ps.close();
                        Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(color("&cNie znaleziono kitu '" + kitName + "' w bazie danych.")));
                        return;
                    }
                    ItemStack[] items = stringToItems(rs.getString("items"));
                    rs.close();
                    ps.close();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Inventory inv = Bukkit.createInventory(null, guiSize(), editGuiTitlePrefix + kitName);
                        setupDecorations(inv);
                        for (int i = 0; i < items.length && i < EDIT_ITEM_SLOTS.length; i++) {
                            inv.setItem(EDIT_ITEM_SLOTS[i], items[i]);
                        }
                        ownedInventories.add(inv);
                        p.openInventory(inv);
                    });
                } catch (Exception e) {
                    plugin.getLogger().severe("Błąd podczas otwierania edycji przedmiotów kitu '" + kitName + "' dla gracza " + p.getName() + ".");
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void openPreviewGUI(Player p, String kitName) {
        if (!ensureDatabase(p)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement("SELECT items FROM kits WHERE name = ?");
                    ps.setString(1, kitName);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String itemsStr = rs.getString("items");
                        ItemStack[] items = stringToItems(itemsStr);

                        Inventory inv = Bukkit.createInventory(null, guiSize(), previewTitlePrefix + kitName);
                        setupDecorations(inv);

                        int[] itemSlots = EDIT_ITEM_SLOTS;
                        for (int i = 0; i < items.length && i < itemSlots.length; i++) {
                            inv.setItem(itemSlots[i], items[i]);
                        }

                        ItemStack back = new ItemStack(Material.BARRIER);
                        ItemMeta meta = back.getItemMeta();
                        meta.setDisplayName(ChatColor.RED + "Powrót");
                        back.setItemMeta(meta);
                        inv.setItem(40, back);

                        ownedInventories.add(inv);
                        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(inv));
                    }
                    rs.close();
                    ps.close();
                } catch (Exception e) {
                    plugin.getLogger().severe("Błąd podczas otwierania podglądu kitu '" + kitName + "' dla gracza " + p.getName() + ".");
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void setupDecorations(Inventory inv) {
        if (!cfg().getBoolean("gui.decorations.enabled", true)) return;

        ItemStack orange = createGlass(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack yellow = createGlass(Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack white = createGlass(Material.WHITE_STAINED_GLASS_PANE);

        for (int s : cfg().getIntegerList("gui.decorations.orange-glass-slots")) {
            if (s >= 0 && s < inv.getSize()) inv.setItem(s, orange);
        }
        for (int s : cfg().getIntegerList("gui.decorations.yellow-glass-slots")) {
            if (s >= 0 && s < inv.getSize()) inv.setItem(s, yellow);
        }
        for (int s : cfg().getIntegerList("gui.decorations.white-glass-slots")) {
            if (s >= 0 && s < inv.getSize()) inv.setItem(s, white);
        }
    }

    private ItemStack createGlass(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    // ================= Eventy =================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Strażnik: jeśli górny ekwipunek NIE JEST obiektem stworzonym przez ten
        // plugin (patrz ownedInventories), kończymy natychmiast - nie ruszamy
        // (nie robimy setCancelled) żadnego cudzego GUI, niezależnie od tego,
        // jaki ma tytuł. To eliminuje ryzyko blokowania menu innych pluginów.
        if (!ownedInventories.contains(event.getView().getTopInventory())) return;

        String title = event.getView().getTitle();
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();

        if (title.equals(guiTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            String kitName = extractKitName(event.getCurrentItem());
            if (kitName == null || kitName.trim().isEmpty()) return;
            if (event.getClick() == ClickType.RIGHT) {
                openPreviewGUI(p, kitName);
            } else if (event.getClick() == ClickType.LEFT) {
                giveKit(p, kitName);
            }
        } else if (title.startsWith(previewTitlePrefix)) {
            event.setCancelled(true);
            if (event.getSlot() == 40 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                openKitGUI(p);
            }
        } else if (title.equals(adminGuiTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            String kitName = extractKitName(event.getCurrentItem());
            if (kitName == null || kitName.trim().isEmpty()) return;
            openKitActionsGUI(p, kitName);
        } else if (title.startsWith(actionsGuiTitlePrefix)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            String kitName = title.substring(actionsGuiTitlePrefix.length());
            ItemMeta clickedMeta = event.getCurrentItem().getItemMeta();
            String action = clickedMeta == null ? null
                    : clickedMeta.getPersistentDataContainer().get(kitActionKey, PersistentDataType.STRING);
            if (action == null) return;

            switch (action) {
                case "items":
                    openItemsEditGUI(p, kitName);
                    break;
                case "preview":
                    openPreviewGUI(p, kitName);
                    break;
                case "permission":
                    pendingEdits.put(p.getUniqueId(), new PendingEdit(kitName, EditType.PERMISSION));
                    p.closeInventory();
                    p.sendMessage(msg("edit-set-permission", "&eWpisz nową permisję na czacie (lub 'none'):"));
                    break;
                case "cooldown":
                    pendingEdits.put(p.getUniqueId(), new PendingEdit(kitName, EditType.COOLDOWN));
                    p.closeInventory();
                    p.sendMessage(msg("edit-set-cooldown", "&eWpisz nowy cooldown w sekundach na czacie:"));
                    break;
                case "slot":
                    pendingEdits.put(p.getUniqueId(), new PendingEdit(kitName, EditType.SLOT));
                    p.closeInventory();
                    p.sendMessage(msg("edit-set-slot", "&eWpisz nowy numer slotu (0-44) na czacie:"));
                    break;
                case "lore":
                    pendingEdits.put(p.getUniqueId(), new PendingEdit(kitName, EditType.LORE));
                    p.closeInventory();
                    p.sendMessage(msg("edit-set-lore", "&eWpisz linijki lore na czacie oddzielone znakiem \"|\" (lub \"brak\" aby usunąć):"));
                    break;
                case "toggle":
                    toggleKitEnabled(p, kitName);
                    break;
                case "delete":
                    deleteKit(kitName);
                    p.closeInventory();
                    p.sendMessage(msg("kit-deleted", "&cUsunięto zestaw &f%kit%", "%kit%", kitName));
                    break;
                case "back":
                    openAdminGUI(p);
                    break;
                default:
                    break;
            }
        } else if (title.startsWith(editGuiTitlePrefix)) {
            // Zwykły, edytowalny ekwipunek - ALE przedmioty wolno wkładać tylko do
            // prostokąta EDIT_ITEM_SLOTS (slot 4 i sloty dekoracyjne są zablokowane).
            //
            // Shift-klik (i podobne "automatyczne" akcje) blokujemy CAŁKOWICIE, bo przy
            // nich to Minecraft/Bukkit sam wybiera docelowy slot w górnym GUI (pierwszy
            // wolny) - może nim być dowolny pusty slot spoza prostokąta (np. 4, 0-3, 17,
            // 18, 26, 27, 35-44), więc samo sprawdzanie "event.getSlot()" tutaj nic by nie
            // dało - trzeba wymusić ręczne przeciąganie przedmiotu na konkretny slot.
            if (event.isShiftClick() || event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                return;
            }
            Inventory clickedInv = event.getClickedInventory();
            if (clickedInv != null && clickedInv.equals(event.getView().getTopInventory())) {
                boolean isItemSlot = false;
                for (int s : EDIT_ITEM_SLOTS) {
                    if (s == event.getSlot()) {
                        isItemSlot = true;
                        break;
                    }
                }
                if (!isItemSlot) event.setCancelled(true);
            }
            // Kliknięcie w ekwipunku własnym gracza (bez shiftu) - bez zmian, dozwolone.
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!ownedInventories.contains(event.getView().getTopInventory())) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(editGuiTitlePrefix)) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) continue; // slot w ekwipunku gracza - OK
            boolean isItemSlot = false;
            for (int s : EDIT_ITEM_SLOTS) {
                if (s == rawSlot) {
                    isItemSlot = true;
                    break;
                }
            }
            if (!isItemSlot) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        ownedInventories.remove(inv);
        String title = event.getView().getTitle();
        if (!title.startsWith(editGuiTitlePrefix)) return;
        if (!(event.getPlayer() instanceof Player)) return;

        String kitName = title.substring(editGuiTitlePrefix.length());
        List<ItemStack> items = new ArrayList<>();
        ItemStack icon = null;
        for (int slot : EDIT_ITEM_SLOTS) {
            ItemStack is = inv.getItem(slot);
            if (is != null && is.getType() != Material.AIR) {
                items.add(is);
                if (icon == null) icon = is.clone();
            }
        }

        updateKitItems(kitName, safeIcon(icon), items.toArray(new ItemStack[0]));
        event.getPlayer().sendMessage(msg("kit-updated-items", "&aZaktualizowano przedmioty kitu %kit%", "%kit%", kitName));
    }

    /**
     * Obsługuje wpisanie nowej wartości na czacie po kliknięciu "Zmień permisję/
     * cooldown/slot/lore" w menu zarządzania kitem (patrz openKitActionsGUI).
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        PendingEdit edit = pendingEdits.get(p.getUniqueId());
        if (edit == null) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();
        pendingEdits.remove(p.getUniqueId());

        // Walidacja/zapis może dotykać bazy i configu - z powrotem na główny wątek serwera.
        Bukkit.getScheduler().runTask(plugin, () -> applyPendingEdit(p, edit, input));
    }

    private void applyPendingEdit(Player p, PendingEdit edit, String input) {
        String kitName = edit.kitName;
        switch (edit.type) {
            case PERMISSION: {
                if (input.isEmpty()) {
                    p.sendMessage(msg("edit-error", "&cBłąd: %error%", "%error%", "Permisja nie może być pusta."));
                    return;
                }
                // Bez spacji na końcu/początku - patrz komentarz w giveKit().
                String permission = input.trim();
                updateKitPermission(kitName, permission);
                p.sendMessage(msg("edit-success-permission", "&aUstawiono permisję na %value%", "%value%", permission));
                break;
            }
            case COOLDOWN: {
                int cooldown;
                try {
                    cooldown = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    p.sendMessage(msg("edit-error", "&cBłąd: %error%", "%error%", "Cooldown musi być liczbą całkowitą (w sekundach)."));
                    return;
                }
                updateKitCooldown(kitName, cooldown);
                // Jeśli kity.yml ma nadpisanie cooldownu dla tego kitu, samo UPDATE w bazie
                // nic by nie zmieniło w grze (effectiveCooldown() zawsze wybiera kity.yml,
                // gdy klucz tam istnieje) - więc aktualizujemy też i ten override.
                String path = "kits." + kitName + ".cooldown";
                if (kitsConfig.contains(path)) {
                    kitsConfig.set(path, cooldown);
                    saveKitsConfig();
                }
                p.sendMessage(msg("edit-success-cooldown", "&aUstawiono cooldown na %value%s.", "%value%", String.valueOf(cooldown)));
                break;
            }
            case SLOT: {
                int slot;
                try {
                    slot = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    p.sendMessage(msg("edit-error", "&cBłąd: %error%", "%error%", "Slot musi być liczbą."));
                    return;
                }
                if (slot < 0 || slot >= guiSize()) {
                    p.sendMessage(msg("edit-error", "&cBłąd: %error%", "%error%",
                            "Slot musi być w zakresie 0-" + (guiSize() - 1) + "."));
                    return;
                }
                updateKitSlot(kitName, slot);
                // Analogicznie jak przy cooldownie - kity.yml (jeśli ma wpis dla tego kitu)
                // nadpisuje slot z bazy w openKitGUI(), więc trzeba zaktualizować oba miejsca.
                String path = "kits." + kitName + ".slot";
                if (kitsConfig.contains(path)) {
                    kitsConfig.set(path, slot);
                    saveKitsConfig();
                }
                p.sendMessage(msg("edit-success-slot", "&aUstawiono slot na %value%", "%value%", String.valueOf(slot)));
                break;
            }
            case LORE: {
                String path = "kits." + kitName + ".lore";
                if (input.equalsIgnoreCase("brak")) {
                    kitsConfig.set(path, null);
                } else {
                    List<String> lore = new ArrayList<>();
                    for (String line : input.split("\\|")) {
                        lore.add(line.trim());
                    }
                    kitsConfig.set(path, lore);
                }
                saveKitsConfig();
                p.sendMessage(msg("edit-success-lore", "&aZaktualizowano lore kitu %kit%", "%kit%", kitName));
                break;
            }
        }
    }

    private void saveKitsConfig() {
        try {
            kitsConfig.save(kitsFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Nie udało się zapisać kity.yml po edycji z panelu admina.");
            e.printStackTrace();
        }
    }

    // ================= Operacje na bazie =================

    private void updateKitItems(String name, ItemStack icon, ItemStack[] items) {
        if (connection == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement("UPDATE kits SET icon = ?, items = ? WHERE name = ?");
                    ps.setString(1, itemToString(icon));
                    ps.setString(2, itemsToString(items));
                    ps.setString(3, name);
                    ps.executeUpdate();
                    ps.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void updateKitPermission(String name, String permission) {
        if (connection == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement("UPDATE kits SET permission = ? WHERE name = ?");
                    ps.setString(1, permission);
                    ps.setString(2, name);
                    ps.executeUpdate();
                    ps.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void updateKitCooldown(String name, int cooldown) {
        if (connection == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement("UPDATE kits SET cooldown = ? WHERE name = ?");
                    ps.setInt(1, cooldown);
                    ps.setString(2, name);
                    ps.executeUpdate();
                    ps.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void updateKitSlot(String name, int slot) {
        if (connection == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement("UPDATE kits SET slot = ? WHERE name = ?");
                    ps.setInt(1, slot);
                    ps.setString(2, name);
                    ps.executeUpdate();
                    ps.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void deleteKit(String name) {
        if (connection == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement("DELETE FROM kits WHERE name = ?");
                    ps.setString(1, name);
                    ps.executeUpdate();
                    ps.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private static boolean kitEnabled(ResultSet rs) {
        try {
            return rs.getInt("enabled") != 0;
        } catch (SQLException e) {
            return true;
        }
    }

    private void toggleKitEnabled(Player p, String kitName) {
        if (!ensureDatabase(p)) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement q = connection.prepareStatement("SELECT enabled FROM kits WHERE name = ?");
                    q.setString(1, kitName);
                    ResultSet rs = q.executeQuery();
                    if (!rs.next()) {
                        rs.close();
                        q.close();
                        return;
                    }
                    int next = kitEnabled(rs) ? 0 : 1;
                    rs.close();
                    q.close();
                    PreparedStatement u = connection.prepareStatement("UPDATE kits SET enabled = ? WHERE name = ?");
                    u.setInt(1, next);
                    u.setString(2, kitName);
                    u.executeUpdate();
                    u.close();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (next == 1) {
                            p.sendMessage(msg("kit-enabled", "&aWlaczono zestaw &f%kit%", "%kit%", kitName));
                        } else {
                            p.sendMessage(msg("kit-turned-off", "&cWylaczono zestaw &f%kit%", "%kit%", kitName));
                        }
                        openKitActionsGUI(p, kitName);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () ->
                            p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void giveKit(Player p, String kitName) {
        if (!ensureDatabase(p)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement("SELECT * FROM kits WHERE name = ?");
                    ps.setString(1, kitName);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        if (!kitEnabled(rs)) {
                            rs.close();
                            ps.close();
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    p.sendMessage(msg("kit-disabled", "&cTen zestaw jest obecnie wylaczony.")));
                            return;
                        }
                        int cooldown = effectiveCooldown(kitName, rs.getInt("cooldown"));
                        // .trim() - w bazie mogła się kiedyś zapisać permisja ze spacją na końcu
                        // (np. wpisana ręcznie w /adminkit create), a hasPermission() nigdy nie
                        // zwróci true dla "grupa.vip " (ze spacją), skoro w LuckPermsie/permissions.yml
                        // permisja jest zapisana bez niej.
                        String permission = rs.getString("permission");
                        permission = permission == null ? "none" : permission.trim();
                        String itemsStr = rs.getString("items");
                        rs.close();
                        ps.close();

                        if (!permission.equalsIgnoreCase("none") && !p.hasPermission(permission)) {
                            // Log w konsoli, żeby admin mógł od razu porównać ten dokładny
                            // ciąg znaków z tym, co ma nadane w permission-managerze (LuckPerms itp.)
                            // - literówka lub inna wielkość liter najczęściej widać dopiero tutaj.
                            plugin.getLogger().info("Gracz " + p.getName() + " nie ma permisji '" + permission
                                    + "' wymaganej do kitu '" + kitName + "' (isOp: " + p.isOp() + ").");
                            p.sendMessage(msg("kit-no-permission", "&cNie masz uprawnień do tego zestawu! &7(%permission%)",
                                    "%permission%", permission));
                            return;
                        }

                        long lastUse = getPlayerLastUse(p.getUniqueId(), kitName);
                        long now = System.currentTimeMillis();
                        long cooldownMillis = cooldown * 1000L;

                        if (now - lastUse < cooldownMillis) {
                            long timeLeft = (lastUse + cooldownMillis) - now;
                            p.sendMessage(msg("kit-cooldown", "&cMusisz poczekać jeszcze: %time%", "%time%", formatTime(timeLeft)));
                            return;
                        }

                        ItemStack[] items = stringToItems(itemsStr);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (ItemStack item : items) {
                                if (item != null) {
                                    HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                                    for (ItemStack l : left.values()) {
                                        p.getWorld().dropItemNaturally(p.getLocation(), l);
                                    }
                                }
                            }
                            p.sendMessage(msg("kit-received", "&aOtrzymano zestaw: %kit%", "%kit%", kitName));
                            p.closeInventory();
                        });

                        setPlayerLastUse(p.getUniqueId(), kitName, now);
                    } else {
                        rs.close();
                        ps.close();
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Błąd podczas wydawania kitu '" + kitName + "' graczowi " + p.getName() + ".");
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(msg("db-error", "&cWystąpił błąd bazy danych.")));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private long getPlayerLastUse(UUID uuid, String kitName) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT last_use FROM player_cooldowns WHERE uuid = ? AND kit_name = ?");
            ps.setString(1, uuid.toString());
            ps.setString(2, kitName);
            ResultSet rs = ps.executeQuery();
            long lastUse = 0;
            if (rs.next()) {
                lastUse = rs.getLong("last_use");
            }
            rs.close();
            ps.close();
            return lastUse;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private void setPlayerLastUse(UUID uuid, String kitName, long time) {
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO player_cooldowns (uuid, kit_name, last_use) VALUES (?, ?, ?)");
            ps.setString(1, uuid.toString());
            ps.setString(2, kitName);
            ps.setLong(3, time);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    private String itemsToString(ItemStack[] items) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items", items);
        return config.saveToString();
    }

    private ItemStack[] stringToItems(String data) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack[0];
        }
        List<?> list = config.getList("items");
        if (list == null) return new ItemStack[0];
        return list.toArray(new ItemStack[0]);
    }

    private String itemToString(ItemStack item) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        return config.saveToString();
    }

    private ItemStack stringToItem(String data) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack(Material.AIR);
        }
        return config.getItemStack("item");
    }
}
