package boniec.rynek;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import boniec.klany.core.KlanWalutaManager;
import boniec.code.core.MistrzSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

@SuppressWarnings("deprecation")
public class Rynek implements CommandExecutor, Listener, TabCompleter {

    private static Rynek instance;
    private final boniec.dev.inventory.Main plugin;
    private static Economy econ = null;
    private KlanWalutaManager klanWalutaManager;

    private final RynekDatabase db;
    private File categoriesFile;
    private FileConfiguration categoriesConfig;

    private File rynekFile;
    private FileConfiguration rynekConfig;

    // Wlasny szablon "Smoczych Odlamkow" rynku (osobny plik danych rynek-odlamek.yml),
    // ustawiany komenda /arynek ustawodlamek (trzymajac item w rece). Ma pierwszenstwo
    // przed szablonami z KlanWalutaManager/MistrzSystem - dzieki temu admin moze
    // jednoznacznie okreslic, co rynek ma uznawac za odlamki, niezaleznie od tego,
    // czy klany/patron maja inny (lub zaden) wlasny szablon ustawiony.
    private File rynekOdlamekFile;
    private FileConfiguration rynekOdlamekConfig;
    private ItemStack rynekOdlamekTemplate;

    private final List<MarketItem> marketItems = new ArrayList<>();
    private final Map<UUID, MarketItem> marketItemsById = new HashMap<>();
    // Przedmioty z wygaslych/zresetowanych ofert, ktorych sprzedawca byl offline. Czekaja na odbior przy wejsciu.
    // Klucz: UUID sprzedawcy. Zapisywane w rynek.db (tabela "zwroty"), wiec przetrwaja restart.
    private final Map<UUID, List<ItemStack>> oczekujaceZwroty = new HashMap<>();
    // Odlamki ze sprzedazy za odlamki - sprzedawca odbiera je komenda /rynek odbierz.
    // Klucz: UUID sprzedawcy, wartosc: liczba odlamkow. Zapisywane w rynek.db (tabela "odlamki").
    private final Map<UUID, Integer> oczekujaceOdlamki = new HashMap<>();
    // Mapa playerStates przechowuje stan DLA OSTATNIO OTWARTEGO GUI
    private final Map<UUID, PlayerMarketState> playerStates = new HashMap<>();

    /** CustomModelData przyciskow kategorii (z NBT: "CustomModelData": 9999). */
    private static final int KATEGORIA_CUSTOM_MODEL_DATA = 9999;

    private static final String DOMYSLNY_TYTUL_RYNKU = "&8ʀʏɴᴇᴋ ꜱᴇʀᴡᴇʀᴏᴡʏ &8(&7{strona}&8/&7{strony}&8)";
    private static final String DOMYSLNY_TYTUL_MOJE = "&8ᴍᴏᴊᴇ ᴏғᴇʀᴛʏ";

    // Uklad 6 rzedow: gora = kategorie, srodek = 4x7 ofert w czarnej ramce, dol = sterowanie.
    private static final int[] MARKET_ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] RAMKA_SLOTS = {0, 1, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 52};

    private static final int SLOT_WSZYSTKO = 2;
    private static final int SLOT_NARZEDZIA = 3;
    private static final int SLOT_UZBROJENIE = 4;
    private static final int SLOT_SPECJALNE = 5;
    private static final int SLOT_PREMIUM = 6;

    // Dolny pasek: « (45) | szklo | filtr moje strona sort jak | szklo | » (53)
    private static final int SLOT_POPRZEDNIA = 45;
    private static final int SLOT_FILTR = 47;
    private static final int SLOT_MOJE = 48;
    private static final int SLOT_LICZNIK = 49;
    private static final int SLOT_SORT = 50;
    private static final int SLOT_JAK = 51;
    private static final int SLOT_NASTEPNA = 53;

    private static final int SLOT_PUSTO = 22;
    private static final int SLOT_INFO_MOJE = 4;
    private static final int SLOT_POWROT_MOJE_OFERTY = 49;

    // Ile ms gracz ma na kliknięcie DRUGI RAZ w ten sam przedmiot, aby potwierdzić zakup.
    private static final long POTWIERDZENIE_ZAKUPU_OKNO_MS = 5000L;

    public Rynek(boniec.dev.inventory.Main plugin) {
        instance = this;
        this.plugin = plugin;
        this.db = new RynekDatabase(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("rynek") != null) {
            plugin.getCommand("rynek").setExecutor(this);
            plugin.getCommand("rynek").setTabCompleter(this);
        } else {
            plugin.getLogger().warning("Komenda 'rynek' nie jest zarejestrowana w plugin.yml - Rynek nie bedzie dostepny.");
        }

        setupEconomy();
        createDataFiles();
        db.connect();
        loadMarketItems();
    }

    public void reload() {
        rynekConfig = wczytajYamlUtf8(rynekFile);
        uzupelnijBrakujaceKlucze();
        categoriesConfig = YamlConfiguration.loadConfiguration(categoriesFile);
        categoryMaterialsCache.clear(); // categories.yml mogl sie zmienic - wymusza ponowne wczytanie przy nastepnym uzyciu
        loadMarketItems();
    }

    /**
     * Ustawia wlasny szablon "Smoczych Odlamkow" rynku na podstawie itemu trzymanego
     * w rece przez gracza. Wspolna logika dla /rynek ustawodlamek i /arynek ustawodlamek.
     */
    public void wykonajUstawOdlamek(CommandSender sender) {
        if (!sender.hasPermission("rynek.admin")) {
            wyslijDo(sender, msg("reload.brak_uprawnien"));
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda z konsoli nie zadziala - trzymaj item w rece, wykonaj ja jako gracz.");
            return;
        }
        Player p = (Player) sender;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            sender.sendMessage("Trzymaj w rece przedmiot, ktory ma byc uznawany przez rynek za Smocze Odlamki.");
            return;
        }
        setRynekOdlamekTemplate(item);
        sender.sendMessage("Ustawiono szablon Smoczych Odlamkow rynku: " + item.getType().name() + " (pelne NBT zapisane, uzywane 1:1).");
    }

    /**
     * Czy handel w danej walucie jest wylaczony komenda /arynek wylacz (flaga w rynek.yml).
     * Wylaczenie blokuje WYSTAWIANIE nowych ofert platnych ta waluta oraz KUPNO istniejacych -
     * same oferty zostaja widoczne na liscie, ale zakup konczy sie komunikatem bledu.
     *
     * @param waluta "pieniadze" albo "odlamki"
     */
    public boolean handelWylaczony(String waluta) {
        return rynekConfig.getBoolean("handel.wylaczone." + waluta, false);
    }

    /** Zapisuje flage wylaczenia handlu dla podanej waluty do rynek.yml. */
    private void ustawHandelWylaczony(String waluta, boolean wylaczony) {
        rynekConfig.set("handel.wylaczone." + waluta, wylaczony);
        try {
            rynekConfig.save(rynekFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udalo sie zapisac rynek.yml: " + e.getMessage());
        }
    }

    /** Normalizuje argument waluty z komendy (np. "Odlamki") do klucza configu; null gdy niepoprawny. */
    private String znormalizujWalute(String arg) {
        if (arg == null) return null;
        String a = arg.toLowerCase(Locale.ROOT);
        if (a.startsWith("odlam")) return "odlamki";
        if (a.startsWith("pienie") || a.startsWith("pieni")) return "pieniadze";
        return null;
    }

    /** Czytelna nazwa waluty do komunikatow (dla odlamkow z configu gui.waluta-odlamki). */
    private String nazwaWalutyDla(String waluta) {
        return "odlamki".equals(waluta) ? nazwaWalutyOdlamki() : "Pieniądze";
    }

    /**
     * /arynek wylacz|wlacz &lt;pieniadze|odlamki&gt; - blokuje/odblokowuje handel w wybranej walucie
     * (wystawianie i kupno ofert platnych ta waluta).
     *
     * @param wylacz true = wylacz, false = wlacz z powrotem
     * @param walutaArg argument z komendy (null gdy gracz go podal), toleruje pisownie typu "Odlamki"
     */
    public void wykonajPrzelaczHandel(CommandSender sender, boolean wylacz, String walutaArg) {
        if (!sender.hasPermission("rynek.admin")) {
            wyslijDo(sender, msg("reload.brak_uprawnien"));
            return;
        }
        String waluta = znormalizujWalute(walutaArg);
        if (waluta == null) {
            wyslijDo(sender, msg("handel.uzycie"));
            return;
        }
        ustawHandelWylaczony(waluta, wylacz);
        wyslijDo(sender, msgReplace(wylacz ? "handel.wylaczono" : "handel.wlaczono",
                "{waluta}", nazwaWalutyDla(waluta)));
    }

    /** Zamyka polaczenie z baza danych rynek.db - wywolywane z Main#onDisable() po flushPendingSaveSync(). */
    public void zamknij() {
        db.disconnect();
    }

    public static Rynek getInstance() {
        return instance;
    }

    // --- SETUP ---

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault not found! Disabling Rynek functionality.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("Economy provider not found! Disabling Rynek functionality.");
            return;
        }
        econ = rsp.getProvider();
    }

    private KlanWalutaManager getKlanWalutaManager() {
        if (klanWalutaManager != null) return klanWalutaManager;
        try {
            boniec.dev.inventory.Main main = (boniec.dev.inventory.Main) plugin.getServer().getPluginManager().getPlugin("SzamanPlugin");
            if (main != null) {
                klanWalutaManager = main.getKlanWalutaManager();
                return klanWalutaManager;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private MistrzSystem getMistrzSystem() {
        try {
            boniec.dev.inventory.Main main = (boniec.dev.inventory.Main) plugin.getServer().getPluginManager().getPlugin("SzamanPlugin");
            if (main != null) {
                return main.getMistrzSystem();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Zwraca WSZYSTKIE poprawne szablony "Smoczych Odlamkow" w tej chwili obowiazujace.
     * NAPRAWA: w pluginie istnieja DWA niezalezne zrodla odlamkow:
     *   - KlanWalutaManager (klany-odlamek.yml, /adminklan ustawodlamek) - waluta klanowa
     *   - MistrzSystem (patron.yml, /adminpatron setodlamek) - odlamki wydawane przez
     *     patronow klanow (giveodlamek / losowania patrona)
     * Oba systemy trzymaja WLASNY szablon ItemStack (osobne NBT/lore/custom model data),
     * wiec porownanie isSimilar(...) do TYLKO jednego z nich powodowalo, ze rynek nie
     * rozpoznawal odlamkow pochodzacych z drugiego zrodla - gracz je mial w ekwipunku,
     * ale rynek liczyl mu zero (lub odmawial wystawienia/kupna oferty za odlamki).
     * Odtad rynek akceptuje item pasujacy 1:1 do KTOREGOKOLWIEK z ustawionych szablonow.
     */
    private List<ItemStack> getOdlamkiSzablony() {
        List<ItemStack> szablony = new ArrayList<>();
        // Wlasny szablon rynku (/arynek ustawodlamek) ma najwyzszy priorytet - admin
        // ustawia go wprost dla rynku, wiec jesli istnieje, jest sprawdzany jako pierwszy
        // i uzywany jako wzor przy WYDAWANIU odlamkow sprzedawcy.
        ItemStack rynekSzablon = getRynekOdlamekTemplate();
        if (rynekSzablon != null) szablony.add(rynekSzablon);
        KlanWalutaManager klanManager = getKlanWalutaManager();
        if (klanManager != null) {
            ItemStack s = klanManager.getOdlamekTemplate();
            if (s != null) szablony.add(s);
        }
        MistrzSystem mistrzSystem = getMistrzSystem();
        if (mistrzSystem != null) {
            try {
                ItemStack s = mistrzSystem.getOdlamkiTemplate();
                if (s != null) szablony.add(s);
            } catch (Exception ignored) {}
        }
        return szablony;
    }

    /** Czy item pasuje 1:1 do dowolnego z aktualnych szablonow odlamkow. */
    private boolean pasujeDoOdlamkow(ItemStack item, List<ItemStack> szablony) {
        if (item == null) return false;
        if (szablony.isEmpty()) {
            return item.getType() == Material.DRAGON_BREATH;
        }
        for (ItemStack szablon : szablony) {
            if (item.isSimilar(szablon)) return true;
        }
        return false;
    }

    /**
     * Zlicza liczbe smoczych odlamkow w ekwipunku gracza na podstawie WSZYSTKICH znanych
     * szablonow (KlanWalutaManager i MistrzSystem - patrz getOdlamkiSzablony).
     * Jesli zaden szablon nie jest ustawiony, liczy zwykly material DRAGON_BREATH.
     */
    private int policzOdlamkiGracza(Player player) {
        List<ItemStack> szablony = getOdlamkiSzablony();
        int suma = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (pasujeDoOdlamkow(item, szablony)) {
                suma += item.getAmount();
            }
        }
        return suma;
    }

    private String tytul(String klucz, String domyslny, int strona, int strony) {
        String t = gui(klucz);
        if (t == null || t.isEmpty()) t = domyslny;
        return color(t.replace("{strona}", String.valueOf(strona)).replace("{strony}", String.valueOf(strony)));
    }

    private void createDataFiles() {
        categoriesFile = new File(plugin.getDataFolder(), "categories.yml");
        if (!categoriesFile.exists()) {
            plugin.saveResource("categories.yml", false);
        }
        categoriesConfig = YamlConfiguration.loadConfiguration(categoriesFile);

        rynekFile = new File(plugin.getDataFolder(), "rynek.yml");
        if (!rynekFile.exists()) {
            plugin.saveResource("rynek.yml", false);
        }
        rynekConfig = wczytajYamlUtf8(rynekFile);
        uzupelnijBrakujaceKlucze();

        rynekOdlamekFile = new File(plugin.getDataFolder(), "rynek-odlamek.yml");
        rynekOdlamekConfig = YamlConfiguration.loadConfiguration(rynekOdlamekFile);
        wczytajRynekOdlamekTemplate();
    }

    /** Wczytuje wlasny szablon odlamkow rynku z rynek-odlamek.yml (klucz "item-nbt"). */
    private void wczytajRynekOdlamekTemplate() {
        String data = rynekOdlamekConfig.getString("item-nbt");
        rynekOdlamekTemplate = deserializeItemFull(data);
    }

    /**
     * Ustawia wlasny szablon "Smoczych Odlamkow" rynku, zapisujac PELNE NBT itemu
     * (bajt w bajt: nazwa, lore, zaklecia, custom model data, PDC itd.) do
     * rynek-odlamek.yml. Uzywane przez /arynek ustawodlamek.
     */
    public void setRynekOdlamekTemplate(ItemStack item) {
        ItemStack kopia = item.clone();
        kopia.setAmount(1);
        rynekOdlamekTemplate = kopia;
        String serialized = serializeItemFull(kopia);
        rynekOdlamekConfig.set("item-nbt", serialized);
        try {
            rynekOdlamekConfig.save(rynekOdlamekFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udalo sie zapisac rynek-odlamek.yml: " + e.getMessage());
        }
    }

    /** Zwraca klone wlasnego szablonu odlamkow rynku (lub null, jesli nie ustawiono). */
    public ItemStack getRynekOdlamekTemplate() {
        return rynekOdlamekTemplate == null ? null : rynekOdlamekTemplate.clone();
    }

    private String serializeItemFull(ItemStack item) {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(byteOut)) {
            out.writeObject(item);
            out.flush();
            return Base64.getEncoder().encodeToString(byteOut.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udalo sie zserializowac szablonu odlamkow rynku: " + e.getMessage());
            return null;
        }
    }

    private ItemStack deserializeItemFull(String data) {
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(byteIn)) {
            Object obj = in.readObject();
            return obj instanceof ItemStack ? (ItemStack) obj : null;
        } catch (Exception e) {
            plugin.getLogger().severe("Nie udalo sie odczytac szablonu odlamkow rynku (uszkodzone dane?): " + e.getMessage());
            return null;
        }
    }

    // --- MESSAGES FROM CONFIG ---

    private void reloadConfig() {
        rynekConfig = wczytajYamlUtf8(rynekFile);
        uzupelnijBrakujaceKlucze();
    }

    /**
     * Wszystkie wiadomosci w rynek.yml sa zagniezdzone pod kluczem "wiadomosci:",
     * wiec kazda sciezke doklejamy do tego prefixu tutaj - w JEDNYM miejscu,
     * zeby wywolania w kodzie mogly zostac krotkie (np. msg("bledy.limit")).
     */
    private String pelnaSciezka(String path) {
        return "wiadomosci." + path;
    }

    /** Wczytanie UTF-8 - ikony Nexo w rynek.yml nie moga padac na Windowsowym Cp1250. */
    private YamlConfiguration wczytajYamlUtf8(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (file == null || !file.exists()) return cfg;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            cfg.load(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("Nie udalo sie wczytac " + file.getName() + ": " + e.getMessage()
                    + " (kopia: " + file.getName().replace(".yml", "-broken.yml") + ")");
            File kopia = new File(file.getParentFile(), file.getName().replace(".yml", "-broken.yml"));
            try {
                java.nio.file.Files.copy(file.toPath(), kopia.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
        return cfg;
    }

    /** Domyslna konfiguracja z jar-a - uzywana, gdy na serwerze brakuje jakiegos klucza. */
    private FileConfiguration domyslnaKonfiguracja;

    private FileConfiguration getDomyslna() {
        if (domyslnaKonfiguracja == null) {
            domyslnaKonfiguracja = new YamlConfiguration();
            try (java.io.InputStream in = plugin.getResource("rynek.yml")) {
                if (in != null) {
                    domyslnaKonfiguracja = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udalo sie wczytac domyslnego rynek.yml z jar-a: " + e.getMessage());
            }
        }
        return domyslnaKonfiguracja;
    }

    /**
     * Dopisuje do rynek.yml na serwerze KAZDY klucz, ktorego tam brakuje (np. po aktualizacji
     * pluginu - saveResource() nie nadpisuje istniejacego pliku, wiec nowe wiadomosci
     * nigdy by sie same nie pojawily). Istniejace wartosci NIE sa zmieniane.
     */
    private void uzupelnijBrakujaceKlucze() {
        FileConfiguration def = getDomyslna();
        boolean zmieniono = false;

        // Nowy uklad GUI nie pasuje do starych tekstow - gdy wersja w jar-ze jest wyzsza,
        // cala sekcja "gui" jest podmieniana (kopia starej trafia do rynek-gui-backup.yml).
        int wersjaGui = def.getInt("gui.wersja", 0);
        int wersjaMsg = def.getInt("wiadomosci.wersja", 0);
        boolean aktualizujGui = rynekConfig.getInt("gui.wersja", 0) < wersjaGui;
        boolean aktualizujMsg = rynekConfig.getInt("wiadomosci.wersja", 0) < wersjaMsg;
        if (aktualizujGui || aktualizujMsg) {
            try {
                java.nio.file.Files.copy(rynekFile.toPath(),
                        new File(rynekFile.getParentFile(), "rynek-gui-backup.yml").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udalo sie zrobic kopii rynek.yml: " + e.getMessage());
            }
        }
        if (aktualizujGui) {
            rynekConfig.set("gui", null);
            zmieniono = true;
            plugin.getLogger().info("Zaktualizowano wyglad rynku do wersji " + wersjaGui
                    + " (stara sekcja gui w rynek-gui-backup.yml).");
        }
        if (aktualizujMsg) {
            rynekConfig.set("wiadomosci", null);
            zmieniono = true;
            plugin.getLogger().info("Zaktualizowano wiadomosci rynku do wersji " + wersjaMsg
                    + " (stara kopia w rynek-gui-backup.yml).");
        }

        for (String key : def.getKeys(true)) {
            if (def.isConfigurationSection(key)) continue;
            if (!rynekConfig.contains(key)) {
                rynekConfig.set(key, def.get(key));
                zmieniono = true;
            }
        }
        if (zmieniono) {
            try {
                rynekConfig.save(rynekFile);
                plugin.getLogger().info("Uzupelniono brakujace klucze w rynek.yml.");
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udalo sie zapisac rynek.yml: " + e.getMessage());
            }
        }
    }

    /** Pobiera string z configu serwera, a gdy go brak - z domyslnego pliku w jar-ze. */
    private String getString(String fullPath) {
        String v = rynekConfig.getString(fullPath);
        if (v == null) v = getDomyslna().getString(fullPath, "");
        return v;
    }

    /** Pobiera liste z configu serwera, a gdy jej brak - z domyslnego pliku w jar-ze. */
    private List<String> getList(String fullPath) {
        if (rynekConfig.isList(fullPath)) return rynekConfig.getStringList(fullPath);
        if (rynekConfig.isString(fullPath)) return List.of(rynekConfig.getString(fullPath, ""));
        if (getDomyslna().isList(fullPath)) return getDomyslna().getStringList(fullPath);
        if (getDomyslna().isString(fullPath)) return List.of(getDomyslna().getString(fullPath, ""));
        return new ArrayList<>();
    }

    /** Tekst GUI z sekcji "gui." w rynek.yml (nieprzetworzony - bez color()). */
    private String gui(String path) {
        return getString("gui." + path);
    }

    /** Lista tekstow GUI z sekcji "gui." w rynek.yml (nieprzetworzona - bez color()). */
    private List<String> guiList(String path) {
        return getList("gui." + path);
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");

    /**
     * Tlumaczy kody koloru '&' NA standardowe (np. &a, &c, &l) ORAZ hex w formacie
     * &#RRGGBB (np. &#FF00AA) na sekwencje sekcji, ktora Minecraft rozumie natywnie
     * (§x§F§F§0§0§A§A). Bez tego kroku ChatColor.translateAlternateColorCodes sam
     * w sobie NIE zna formatu &#RRGGBB i wypisuje go doslownie jako tekst -
     * to byla przyczyna "nie dziala hex w rynku np &#COSS w configu".
     * <p>
     * UWAGA: &#COSS nie jest poprawnym hexem (litery O/S nie sa cyframi 0-9/A-F),
     * wiec taki wpis w configu i tak nigdy sie nie przetlumaczy - to trzeba
     * poprawic w rynek.yml na prawdziwy kod, np. &#C0FFEE albo &#FFFFFF.
     */
    private static String color(String text) {
        if (text == null || text.isEmpty()) return "";
        if (!text.contains("&#")) {
            return ChatColor.translateAlternateColorCodes('&', text);
        }
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length() + 16);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private String msg(String path) {
        return color(getString(pelnaSciezka(path)));
    }

    private String msgReplace(String path, String placeholder, String value) {
        return color(getString(pelnaSciezka(path)).replace(placeholder, value));
    }

    private List<String> msgListReplace(String path, String placeholder, String value) {
        List<String> result = new ArrayList<>();
        for (String line : getList(pelnaSciezka(path))) {
            result.add(color(line.replace(placeholder, value)));
        }
        return result;
    }

    /**
     * Wysyla wiadomosc graczowi, ale NIGDY pusta - jesli tekst jest pusty
     * (np. klucz wyczyszczony w configu), nic nie jest wysylane, zamiast pustej linii na czacie.
     */
    private void wyslij(Player player, String tekst) {
        wyslijDo(player, tekst);
    }

    private void wyslijDo(CommandSender odbiorca, String tekst) {
        if (tekst == null || ChatColor.stripColor(tekst).trim().isEmpty()) return;
        odbiorca.sendMessage(tekst);
    }

    private void sendTitle(Player player, String path, String placeholder, String value) {
        String title = msgReplace(path + ".title", placeholder, value);
        String subtitle = msgReplace(path + ".subtitle", placeholder, value);
        String soundStr = getString(pelnaSciezka(path) + ".sound");
        if (!title.isEmpty() || !subtitle.isEmpty()) {
            player.sendTitle(title, subtitle, 10, 40, 10);
        }
        if (!soundStr.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundStr);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void loadMarketItems() {
        marketItems.clear();
        marketItemsById.clear();
        for (MarketItem mi : db.wczytajWszystkieItemy()) {
            marketItems.add(mi);
            marketItemsById.put(mi.getId(), mi);
        }
        loadZwroty();
        loadOczekujaceOdlamki();
    }

    /** Wczytuje oczekujace odlamki sprzedawcow (zebrane ze sprzedazy za odlamki, niewyodbrane przez /rynek odbierz). */
    private void loadOczekujaceOdlamki() {
        oczekujaceOdlamki.clear();
        oczekujaceOdlamki.putAll(db.wczytajWszystkieOdlamki());
    }

    private void loadZwroty() {
        oczekujaceZwroty.clear();
        oczekujaceZwroty.putAll(db.wczytajWszystkieZwroty());
    }

    // --- Async zapis do rynek.db (SQLite) -------------------------------------------
    // saveMarketItems() jest wolane synchronicznie na glownym watku przy KAZDYM
    // zakupie/wystawieniu/wygasnieciu oferty (patrz wywolania nizej) - to jeden z
    // najgoretszych miejsc w Rynek.onInventoryClick()/onCommand() w profilerze.
    // Samo skopiowanie kolekcji (marketItems/oczekujaceZwroty/oczekujaceOdlamki) musi
    // zostac na glownym watku (nie sa thread-safe), ALE faktyczny zapis do bazy
    // (blokujace I/O, najdrozsza czesc) NIE musi - przenosimy go asynchronicznie.
    // "saveInProgress"/pendingXxx dzialaja jak coalescing: jesli kolejny zakup
    // przyjdzie zanim poprzedni zapis do bazy sie skonczy, NIE odpalamy drugiego
    // rownoleglego zapisu - po prostu podmieniamy "co zapisac" na najnowsza wersje
    // i biezacy watek zapisujacy sam ja dopisze zaraz po skonczeniu poprzedniego
    // zapisu. Przy nagle burst zakupow (np. wyprzedaz) to tez realnie mniej operacji
    // dyskowych, bo posrednie (juz nieaktualne) wersje sa pomijane zamiast kazda
    // osobno trafiac na dysk.
    private final Object saveLock = new Object();
    private List<MarketItem> pendingItems;
    private Map<UUID, List<ItemStack>> pendingZwroty;
    private Map<UUID, Integer> pendingOdlamki;
    private boolean saveInProgress;

    private void saveMarketItems() {
        synchronized (saveLock) {
            pendingItems = new ArrayList<>(marketItems);
            pendingZwroty = new HashMap<>(oczekujaceZwroty);
            pendingOdlamki = new HashMap<>(oczekujaceOdlamki);
            if (saveInProgress) {
                return;
            }
            saveInProgress = true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::runPendingSaveLoop);
    }

    private void runPendingSaveLoop() {
        while (true) {
            List<MarketItem> items;
            Map<UUID, List<ItemStack>> zwroty;
            Map<UUID, Integer> odlamki;
            synchronized (saveLock) {
                if (pendingItems == null) {
                    saveInProgress = false;
                    return;
                }
                items = pendingItems;
                zwroty = pendingZwroty;
                odlamki = pendingOdlamki;
                pendingItems = null;
                pendingZwroty = null;
                pendingOdlamki = null;
            }
            persistToDatabase(items, zwroty, odlamki);
        }
    }

    private void persistToDatabase(List<MarketItem> items, Map<UUID, List<ItemStack>> zwroty,
                                    Map<UUID, Integer> odlamki) {
        db.zapiszWszystkieItemy(items);
        db.zapiszWszystkieZwroty(zwroty != null ? zwroty : Collections.emptyMap());
        db.zapiszWszystkieOdlamki(odlamki != null ? odlamki : Collections.emptyMap());
    }

    /**
     * Wymusza NATYCHMIASTOWY, SYNCHRONICZNY zapis do rynek.db na biezacym watku -
     * wylacznie do uzycia w onDisable(), gdzie po zamknieciu pluginu scheduler nie
     * gwarantuje juz wykonania zaplanowanych zadan asynchronicznych (patrz Main.onDisable()).
     */
    public void flushPendingSaveSync() {
        List<MarketItem> items;
        Map<UUID, List<ItemStack>> zwroty;
        Map<UUID, Integer> odlamki;
        synchronized (saveLock) {
            items = pendingItems != null ? pendingItems : new ArrayList<>(marketItems);
            zwroty = pendingZwroty != null ? pendingZwroty : new HashMap<>(oczekujaceZwroty);
            odlamki = pendingOdlamki != null ? pendingOdlamki : new HashMap<>(oczekujaceOdlamki);
            pendingItems = null;
            pendingZwroty = null;
            pendingOdlamki = null;
            saveInProgress = false;
        }
        persistToDatabase(items, zwroty, odlamki);
    }

    /**
     * Zwraca bieżący stan gracza. Jeśli nie istnieje (np. po otwarciu GUI), tworzy nowy.
     */
    private PlayerMarketState getPlayerState(Player player) {
        // computeIfAbsent MUSI działać i zwracać ten sam obiekt, dopóki nie zostanie usunięty.
        return playerStates.computeIfAbsent(player.getUniqueId(), k -> new PlayerMarketState());
    }

    // --- COMMANDS & TABS ---

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // /rynek reload - dostepne tez z konsoli
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rynek.admin")) {
                wyslijDo(sender, msg("reload.brak_uprawnien"));
                return true;
            }
            reload();
            wyslijDo(sender, msg("reload.wiadomosc"));
            return true;
        }

        // /rynek resetall - dostepne tez z konsoli. Zdejmuje z rynku WSZYSTKIE
        // aktywne oferty i oddaje przedmioty sprzedawcom (online od razu do
        // ekwipunku, offline - do odbioru przy nastepnym wejsciu), dokladnie
        // tym samym mechanizmem co wygasniecie oferty - zeby nikt nic nie stracil.
        if (args.length > 0 && args[0].equalsIgnoreCase("resetall")) {
            if (!sender.hasPermission("rynek.admin")) {
                wyslijDo(sender, msg("reload.brak_uprawnien"));
                return true;
            }
            int ile = resetujWszystkieOferty();
            if (ile == 0) {
                wyslijDo(sender, msg("resetall.brak_ofert"));
            } else {
                wyslijDo(sender, msgReplace("resetall.wiadomosc", "{ilosc}", String.valueOf(ile)));
            }
            return true;
        }

        // /rynek ustawodlamek - ustawia WLASNY szablon rynku uzywany do rozpoznawania
        // "Smoczych Odlamkow" (trzymaj item w rece). Dostepne tez pod osobna komenda
        // /arynek ustawodlamek (patrz ARynekCommand) - obie wolaja ten sam kod.
        if (args.length > 0 && args[0].equalsIgnoreCase("ustawodlamek")) {
            wykonajUstawOdlamek(sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            String t = msg("bledy.tylko_gracze");
            sender.sendMessage(ChatColor.stripColor(t).trim().isEmpty() ? "Ta komenda jest tylko dla graczy!" : t);
            return true;
        }
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("wystaw")) {
            if (args.length != 2) {
                wyslij(player, msg("bledy.uzycie"));
                return true;
            }

            int limit = getPlayerLimit(player);
            long now = System.currentTimeMillis();
            long currentListings = marketItems.stream()
                    .filter(item -> item.getSeller().equals(player.getUniqueId()))
                    .filter(item -> item.getExpirationTimestamp() > now)
                    .count();
            if (currentListings >= limit) {
                wyslij(player, msgReplace("bledy.limit", "{limit}", String.valueOf(limit)));
                return true;
            }

            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand == null || itemInHand.getType() == Material.AIR) {
                wyslij(player, msg("bledy.brak_przedmiotu"));
                return true;
            }

            // Rynek przyjmuje wylacznie pieniadze - odlamkow nie da sie wystawic.
            String waluta = "pieniadze";

            // Handel w tej walucie moze byc wylaczony komenda /arynek wylacz <waluta>.
            if (handelWylaczony(waluta)) {
                wyslij(player, msgReplace("bledy.handel_wylaczony", "{waluta}", nazwaWalutyDla(waluta)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }

            double price;
            try {
                price = Double.parseDouble(args[1].replace(",", "."));
                if (price <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                wyslij(player, msg("bledy.zla_cena"));
                return true;
            }

            MarketItem marketItem = new MarketItem(UUID.randomUUID(), itemInHand.clone(), price,
                    player.getUniqueId(), player.getName(), System.currentTimeMillis(), waluta);
            marketItems.add(marketItem);
            marketItemsById.put(marketItem.getId(), marketItem);
            saveMarketItems();

            player.getInventory().setItemInMainHand(null);

            String cena = formatPrice(price, "pieniadze");
            for (String line : msgListReplace("wystawianie.wiadomosc", "{cena}", cena)) {
                wyslij(player, line);
            }
            sendTitle(player, "wystawianie", "{cena}", cena);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("odbierz")) {
            int ile = oczekujaceOdlamki.getOrDefault(player.getUniqueId(), 0);
            if (ile <= 0) {
                wyslij(player, msg("odbierz.brak"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
            oczekujaceOdlamki.remove(player.getUniqueId());
            saveMarketItems();
            wydajOdlamkiGraczowi(player, ile);
            for (String line : msgListReplace("odbierz.wiadomosc", "{ilosc}", String.valueOf(ile))) {
                wyslij(player, line);
            }
            sendTitle(player, "odbierz", "{ilosc}", String.valueOf(ile));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            return true;
        }

        // **WAŻNE**: Wymuś nowy, domyślny stan (Category.ALL) przy każdym otwarciu komendą.
        // Jeśli stan istnieje (bo GUI jest otwarte), to to go usunie i wymusi nowy stan w openMarketGUI.
        playerStates.remove(player.getUniqueId());

        openMarketGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opcje = new ArrayList<>();
            opcje.add("wystaw");
            opcje.add("odbierz");
            if (sender.hasPermission("rynek.admin")) {
                opcje.add("reload");
                opcje.add("resetall");
                opcje.add("ustawodlamek");
            }
            return filtrujStart(args[0], opcje);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("wystaw")) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    /** Filtrowanie podpowiedzi po wpisanym prefiksie (Bukkit nie zawsze robi to sam). */
    private List<String> filtrujStart(String wpisane, List<String> opcje) {
        String prefix = wpisane.toLowerCase(Locale.ROOT);
        List<String> wynik = new ArrayList<>();
        for (String o : opcje) {
            if (o.toLowerCase(Locale.ROOT).startsWith(prefix)) wynik.add(o);
        }
        return wynik;
    }

    // --- GUI ---

    /**
     * Lore przycisku kategorii: opis z configu, lista podkategorii (aktywna oznaczona
     * gui.opcja-aktywna) zakonczona "Cala kategoria" - w tej samej kolejnosci, w jakiej
     * cykluje je PlayerMarketState#cycleSubCategory.
     */
    private List<String> zbudujLoreKategorii(String[] podkategorie, Category kategoria,
                                             PlayerMarketState state, String kluczPrzycisku) {
        List<String> lore = new ArrayList<>(guiList("przyciski." + kluczPrzycisku + ".opis"));
        lore.add("");
        boolean wKategorii = state.category == kategoria;
        for (int i = 0; i < podkategorie.length; i++) {
            lore.add(opcja(wKategorii && state.subCategoryIndex == i, nazwaPodkategorii(podkategorie[i])));
        }
        lore.add(opcja(wKategorii && state.subCategoryIndex == -1, gui("podkategorie.calosc")));
        lore.add("");
        lore.add(gui("klik-przelacz"));
        return lore;
    }

    private String nazwaPodkategorii(String klucz) {
        String nazwa = gui("podkategorie." + klucz);
        if (nazwa == null || nazwa.isEmpty()) {
            nazwa = klucz.substring(0, 1).toUpperCase() + klucz.substring(1);
        }
        return nazwa;
    }

    private String opcja(boolean aktywna, String tekst) {
        return gui("opcja-prefix") + (aktywna ? gui("opcja-aktywna") : gui("opcja-nieaktywna")) + tekst;
    }

    private List<String> loreOpcji(List<String> opcje, int aktywnyIndeks) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        for (int i = 0; i < opcje.size(); i++) {
            lore.add(opcja(i == aktywnyIndeks, opcje.get(i)));
        }
        lore.add("");
        lore.add(gui("klik-przelacz"));
        return lore;
    }

    private String tekstLubDomyslny(String sciezka, String domyslny) {
        String t = gui(sciezka);
        return (t == null || t.isEmpty()) ? domyslny : t;
    }

    private List<String> podstawLicznik(List<String> linie, Player player) {
        long wystawione = marketItems.stream().filter(m -> m.getSeller().equals(player.getUniqueId())).count();
        int limit = getPlayerLimit(player);
        List<String> wynik = new ArrayList<>(linie.size());
        for (String l : linie) {
            wynik.add(l.replace("{wystawione}", String.valueOf(wystawione)).replace("{limit}", String.valueOf(limit)));
        }
        return wynik;
    }

    /**
     * Tworzy przycisk kategorii: nazwa + lore z rynek.yml oraz CustomModelData 9999
     * (model z resource packa - taki sam jak w oryginalnych NBT przyciskow).
     */
    private ItemStack kategoriaItem(Material material, String klucz, List<String> lore) {
        return kategoriaItem(material, klucz, lore, false);
    }

    private ItemStack kategoriaItem(Material material, String klucz, List<String> lore, boolean polysk) {
        ItemStack item = createItem(material, gui("przyciski." + klucz + ".nazwa"), lore, polysk);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(KATEGORIA_CUSTOM_MODEL_DATA);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openMarketGUI(Player player) {
        // Pobrane getPlayerState() gwarantuje, że stan gracza jest świeży (ALL),
        // jeśli został usunięty w onCommand.
        PlayerMarketState state = getPlayerState(player);

        List<MarketItem> filteredItems = getFilteredAndSortedItems(player);
        int listSize = filteredItems.size();
        int itemsPerPage = MARKET_ITEM_SLOTS.length;
        int strony = Math.max(1, (listSize + itemsPerPage - 1) / itemsPerPage);
        if (state.page >= strony) state.page = strony - 1;
        int startIndex = state.page * itemsPerPage;

        // Zapamietujemy DOKLADNIE te liste (kolejnosc ID), ktora zostanie teraz
        // wyrenderowana w slotach - handleMarketClick musi mapowac klikniecia na
        // NIA, a nie przeliczac filtr/sort na nowo (patrz komentarz przy polu).
        state.lastRenderedItemIds.clear();
        for (MarketItem mi : filteredItems) {
            state.lastRenderedItemIds.add(mi.getId());
        }
        state.potwierdzenieSlot = -1;

        RynekHolder holder = new RynekHolder(false);
        Inventory gui = Bukkit.createInventory(holder, 54,
                tytul("tytul-rynku", DOMYSLNY_TYTUL_RYNKU, state.page + 1, strony));
        holder.inv = gui;
        wypelnijRamkeOfert(gui);

        // --- Gorny pasek: kategorie ---
        List<String> wszystkoLore = new ArrayList<>(guiList("przyciski.wszystko.opis"));
        wszystkoLore.add("");
        wszystkoLore.add(gui("klik-pokaz"));
        gui.setItem(SLOT_WSZYSTKO, kategoriaItem(Material.QUARTZ, "wszystko", wszystkoLore,
                state.category == Category.ALL));
        gui.setItem(SLOT_NARZEDZIA, kategoriaItem(Material.NETHERITE_SWORD, "narzedzia",
                zbudujLoreKategorii(Category.TOOLS_SUB, Category.TOOLS, state, "narzedzia"),
                state.category == Category.TOOLS));
        gui.setItem(SLOT_UZBROJENIE, kategoriaItem(Material.DIAMOND_CHESTPLATE, "uzbrojenie",
                zbudujLoreKategorii(Category.ARMOR_SUB, Category.ARMOR, state, "uzbrojenie"),
                state.category == Category.ARMOR));
        gui.setItem(SLOT_SPECJALNE, kategoriaItem(Material.END_ROD, "specjalne",
                zbudujLoreKategorii(Category.SPECJALNE_SUB, Category.SPECJALNE, state, "specjalne"),
                state.category == Category.SPECJALNE));
        gui.setItem(SLOT_PREMIUM, kategoriaItem(Material.NETHER_STAR, "premium",
                zbudujLoreKategorii(Category.PREMIUM_SUB, Category.PREMIUM, state, "premium"),
                state.category == Category.PREMIUM));

        // --- Dolny pasek: strony, filtry, moje oferty, sortowanie, pomoc ---
        ustawNawigacje(gui, state.page, strony);

        List<String> filtry = List.of(gui("przyciski.filtrowanie.wszystkie"), gui("przyciski.filtrowanie.stac_mnie"));
        gui.setItem(SLOT_FILTR, createItem(Material.HOPPER, gui("przyciski.filtrowanie.nazwa"),
                loreOpcji(filtry, state.showAffordableOnly ? 1 : 0), state.showAffordableOnly));

        gui.setItem(SLOT_MOJE, kategoriaItem(Material.CHEST, "twoje",
                podstawLicznik(guiList("przyciski.twoje.lore"), player)));

        List<String> sorty = new ArrayList<>();
        for (SortType type : SortType.values()) {
            sorty.add(tekstLubDomyslny("sortowanie." + type.name(), type.getDisplayName()));
        }
        gui.setItem(SLOT_SORT, createItem(Material.COMPARATOR, gui("przyciski.sortowanie.nazwa"),
                loreOpcji(sorty, state.sortType.ordinal())));

        gui.setItem(SLOT_JAK, createItem(Material.WRITABLE_BOOK, gui("przyciski.jak_wystawic.nazwa"),
                guiList("przyciski.jak_wystawic.lore")));

        // --- Oferty ---
        for (int i = 0; i < itemsPerPage && startIndex + i < listSize; i++) {
            gui.setItem(MARKET_ITEM_SLOTS[i], wyswietlOferte(filteredItems.get(startIndex + i)));
        }
        if (listSize == 0) {
            gui.setItem(SLOT_PUSTO, createItem(Material.COBWEB, gui("przyciski.pusto.nazwa"),
                    guiList("przyciski.pusto.lore")));
        }

        player.openInventory(gui);
    }

    /** Oferta tak, jak widzi ja kupujacy: item + lore z configu (+ aktualny czas do wygasniecia). */
    private ItemStack wyswietlOferte(MarketItem marketItem) {
        String cena = marketItem.jestZaOdlamki()
                ? formatPrice(marketItem.getPrice(), "odlamki")
                : formatPrice(marketItem.getPrice());
        List<String> loreOdlamki = guiList("lore-oferty-odlamki");
        List<String> szablon = (marketItem.jestZaOdlamki() && !loreOdlamki.isEmpty())
                ? loreOdlamki
                : guiList("lore-oferty");
        String waluta = marketItem.jestZaOdlamki() ? nazwaWalutyOdlamki() : "Pieniądze";
        return podstawCzas(marketItem.getDisplayItem(gui("linia-ceny"), szablon, cena, waluta), marketItem);
    }

    /** Display item jest cache'owany, wiec {wygasa} podmieniamy na kopii przy kazdym renderze. */
    private ItemStack podstawCzas(ItemStack display, MarketItem marketItem) {
        ItemMeta meta = display.getItemMeta();
        if (meta == null || !meta.hasLore()) return display;
        List<String> lore = meta.getLore();
        if (lore == null || lore.stream().noneMatch(l -> l.contains("{wygasa}"))) return display;
        String czas = formatRemainingTime(marketItem.getExpirationTimestamp() - System.currentTimeMillis());
        ItemStack kopia = display.clone();
        ItemMeta kopiaMeta = kopia.getItemMeta();
        kopiaMeta.setLore(lore.stream().map(l -> l.replace("{wygasa}", czas)).collect(Collectors.toList()));
        kopia.setItemMeta(kopiaMeta);
        return kopia;
    }

    private ItemStack oznaczPotwierdzenie(ItemStack item) {
        ItemStack kopia = item.clone();
        ItemMeta meta = kopia.getItemMeta();
        if (meta == null) return kopia;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        for (String linia : guiList("potwierdz-lore")) {
            lore.add(color(linia));
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        kopia.setItemMeta(meta);
        return kopia;
    }

    public void openMyOffersGUI(Player player) {
        List<MarketItem> myItems = marketItems.stream()
                .filter(item -> item.getSeller().equals(player.getUniqueId()))
                .collect(Collectors.toList());

        // Zapamietujemy kolejnosc ID DOKLADNIE tak, jak zostanie wyrenderowana -
        // handleMyOffersClick mapuje klikniecia na ta liste, nie na przeliczenie na zywo.
        PlayerMarketState state = getPlayerState(player);
        int itemsPerPage = MARKET_ITEM_SLOTS.length;
        int strony = Math.max(1, (myItems.size() + itemsPerPage - 1) / itemsPerPage);
        if (state.myPage >= strony) state.myPage = strony - 1;
        int startIndex = state.myPage * itemsPerPage;

        // Zapamietujemy kolejnosc ID DOKLADNIE tak, jak zostanie wyrenderowana -
        // handleMyOffersClick mapuje klikniecia na ta liste, nie na przeliczenie na zywo.
        state.lastRenderedMyOfferIds.clear();
        for (MarketItem mi : myItems) {
            state.lastRenderedMyOfferIds.add(mi.getId());
        }

        RynekHolder holder = new RynekHolder(true);
        Inventory gui = Bukkit.createInventory(holder, 54,
                tytul("tytul-moje-oferty", DOMYSLNY_TYTUL_MOJE, state.myPage + 1, strony));
        holder.inv = gui;

        gui.setItem(SLOT_INFO_MOJE, createItem(Material.NAME_TAG, gui("przyciski.info_moje.nazwa"),
                podstawLicznik(guiList("przyciski.info_moje.lore"), player)));

        long teraz = System.currentTimeMillis();
        for (int i = 0; i < itemsPerPage && startIndex + i < myItems.size(); i++) {
            MarketItem marketItem = myItems.get(startIndex + i);
            ItemStack displayItem = marketItem.getItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                String cena = marketItem.jestZaOdlamki()
                        ? formatPrice(marketItem.getPrice(), "odlamki") + " &d" + nazwaWalutyOdlamki()
                        : formatPrice(marketItem.getPrice());
                String wygasa = formatRemainingTime(marketItem.getExpirationTimestamp() - teraz);
                for (String szablon : guiList("lore-moja-oferta")) {
                    lore.add(color(szablon.replace("{cena}", cena).replace("{wygasa}", wygasa)));
                }
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }
            gui.setItem(MARKET_ITEM_SLOTS[i], displayItem);
        }
        if (myItems.isEmpty()) {
            gui.setItem(SLOT_PUSTO, createItem(Material.COBWEB, gui("przyciski.pusto_moje.nazwa"),
                    guiList("przyciski.pusto_moje.lore")));
        }

        ustawNawigacje(gui, state.myPage, strony);
        gui.setItem(SLOT_POWROT_MOJE_OFERTY, createItem(Material.ARROW,
                gui("przyciski.powrot"), guiList("przyciski.powrot_lore")));
        player.openInventory(gui);
    }

    /** Czarna ramka wokol siatki ofert; null gdy obramowanie wylaczone w configu. */
    private ItemStack ramkaItem() {
        if (!rynekConfig.getBoolean("gui.obramowanie.wlaczone", true)) {
            return null;
        }
        Material ramka = Material.matchMaterial(getString("gui.obramowanie.material"));
        return createItem(ramka != null ? ramka : Material.BLACK_STAINED_GLASS_PANE, " ", null);
    }

    private void wypelnijRamkeOfert(Inventory inv) {
        ItemStack ramka = ramkaItem();
        if (ramka == null) return;
        for (int slot : RAMKA_SLOTS) {
            inv.setItem(slot, ramka);
        }
    }

    private void ustawNawigacje(Inventory gui, int strona, int strony) {
        gui.setItem(SLOT_POPRZEDNIA, przyciskStrony(strona > 0, true, strona, strony));
        gui.setItem(SLOT_LICZNIK, przyciskLicznika(strona + 1, strony));
        gui.setItem(SLOT_NASTEPNA, przyciskStrony(strona + 1 < strony, false, strona + 2, strony));
    }

    /** Strzalki stron sa zawsze widoczne; nieaktywna jest szara i bez blasku. */
    private ItemStack przyciskStrony(boolean aktywny, boolean poprzednia, int docelowaStrona, int strony) {
        String klucz = poprzednia
                ? (aktywny ? "przyciski.poprzednia_strona" : "przyciski.poprzednia_strona_nieaktywna")
                : (aktywny ? "przyciski.nastepna_strona" : "przyciski.nastepna_strona_nieaktywna");
        String nazwa = tekstLubDomyslny(klucz, poprzednia ? "&7« Poprzednia" : "&7Następna »");
        List<String> lore = new ArrayList<>();
        String loreKlucz = poprzednia
                ? (aktywny ? "przyciski.poprzednia_strona_lore" : "przyciski.poprzednia_strona_lore_nieaktywna")
                : (aktywny ? "przyciski.nastepna_strona_lore" : "przyciski.nastepna_strona_lore_nieaktywna");
        for (String l : guiList(loreKlucz)) {
            lore.add(l.replace("{strona}", String.valueOf(docelowaStrona)).replace("{strony}", String.valueOf(strony)));
        }
        return createItem(poprzednia ? Material.ARROW : Material.SPECTRAL_ARROW, nazwa, lore, aktywny);
    }

    private ItemStack przyciskLicznika(int strona, int strony) {
        String nazwa = tekstLubDomyslny("przyciski.strona.nazwa", "&f{strona}&8/&f{strony}")
                .replace("{strona}", String.valueOf(strona))
                .replace("{strony}", String.valueOf(strony));
        List<String> lore = new ArrayList<>();
        for (String l : guiList("przyciski.strona.lore")) {
            lore.add(l.replace("{strona}", String.valueOf(strona)).replace("{strony}", String.valueOf(strony)));
        }
        return createItem(Material.PAPER, nazwa, lore);
    }

    private int indeksSlotuOferty(int slot) {
        for (int i = 0; i < MARKET_ITEM_SLOTS.length; i++) {
            if (MARKET_ITEM_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    // --- EVENT HANDLERS ---

    // Usuwamy ten handler, aby stan nie był usuwany, dopóki gracz nie użyje /rynek ponownie.
    // @EventHandler
    // public void onInventoryClose(InventoryCloseEvent event) {
    //     if (event.getView().getTitle().equals(marketTitle)) {
    //          playerStates.remove(event.getPlayer().getUniqueId());
    //     }
    // }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RynekHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (holder.mojeOferty) {
            handleMyOffersClick(player, slot, event.getCurrentItem());
        } else {
            handleMarketClick(player, slot, event.getCurrentItem());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RynekHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMarketClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        PlayerMarketState state = getPlayerState(player);
        boolean shouldRefresh = false;
        switch (slot) {
            case SLOT_WSZYSTKO: state.setCategory(Category.ALL); shouldRefresh = true; break;
            case SLOT_NARZEDZIA: state.cycleSubCategory(Category.TOOLS); shouldRefresh = true; break;
            case SLOT_UZBROJENIE: state.cycleSubCategory(Category.ARMOR); shouldRefresh = true; break;
            case SLOT_SPECJALNE: state.cycleSubCategory(Category.SPECJALNE); shouldRefresh = true; break;
            case SLOT_PREMIUM: state.cycleSubCategory(Category.PREMIUM); shouldRefresh = true; break;
            case SLOT_FILTR: state.toggleAffordable(); shouldRefresh = true; break;
            case SLOT_SORT: state.cycleSort(); shouldRefresh = true; break;
            case SLOT_MOJE:
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                openMyOffersGUI(player);
                return;
            case SLOT_JAK:
            case SLOT_LICZNIK: return;
            case SLOT_POPRZEDNIA:
                if (state.page > 0) {
                    state.page--;
                    shouldRefresh = true;
                }
                break;
            case SLOT_NASTEPNA:
                // UWAGA: nie liczymy tu ponownie getFilteredAndSortedItems() - state.lastRenderedItemIds
                // to DOKLADNIE ta sama lista (po filtrach/sortowaniu), zapisana przy ostatnim renderze GUI.
                // Liczenie jej od nowa tylko po to, zeby sprawdzic .size(), to podwojny koszt
                // filtrowania+sortowania calego rynku na KAZDE klikniecie "nastepna strona".
                if ((state.page + 1) * MARKET_ITEM_SLOTS.length < state.lastRenderedItemIds.size()) {
                    state.page++;
                    shouldRefresh = true;
                }
                break;
            default:
                // Check if it's a market item
                int clickedItemIndex = indeksSlotuOferty(slot);

                if (clickedItemIndex != -1) {
                    // UWAGA: mapujemy klikniecie na liste ID zapamietana przy OSTATNIM
                    // renderowaniu GUI (state.lastRenderedItemIds), a NIE na swiezo
                    // przeliczony getFilteredAndSortedItems(). Miedzy otwarciem GUI a
                    // kliknieciem marketItems mogla sie zmienic (inny gracz kupil/
                    // wystawil cos, oferta wygasla) - przeliczenie "na zywo" moglo dac
                    // inna kolejnosc/inny rozmiar listy niz to, co gracz FAKTYCZNIE
                    // widzial na ekranie, przez co klikniecie kupowalo sasiedni item.
                    int itemIndex = (state.page * MARKET_ITEM_SLOTS.length) + clickedItemIndex;
                    if (itemIndex < state.lastRenderedItemIds.size()) {
                        UUID clickedId = state.lastRenderedItemIds.get(itemIndex);
                        MarketItem toBuy = marketItemsById.get(clickedId);
                        if (toBuy == null) {
                            // Oferta zdazyla zniknac (kupiona przez kogos innego/wygasla/anulowana)
                            // miedzy renderowaniem GUI a tym kliknieciem.
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            wyslij(player, msg("oferta_niedostepna.wiadomosc"));
                            openMarketGUI(player);
                            return;
                        }

                        // Wlasna oferta - nie ma sensu prosic o potwierdzenie zakupu, bo i tak sie nie da.
                        if (toBuy.getSeller().equals(player.getUniqueId())) {
                            state.potwierdzenieZakupuId = null;
                            state.potwierdzenieZakupuCzas = 0L;
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                            String wlasna = msg("wlasna_oferta.wiadomosc");
                            wyslij(player, ChatColor.stripColor(wlasna).trim().isEmpty()
                                    ? msg("bledy.wlasny_przedmiot") : wlasna);
                            return;
                        }

                        // Wlasciwa waluta oferty - uzywana tez do kontroli wylaczenia handlu.
                        String walutaOferty = toBuy.getWaluta();
                        if (handelWylaczony(walutaOferty)) {
                            state.potwierdzenieZakupuId = null;
                            state.potwierdzenieZakupuCzas = 0L;
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            wyslij(player, msgReplace("bledy.handel_wylaczony", "{waluta}", nazwaWalutyDla(walutaOferty)));
                            return;
                        }

                        long now = System.currentTimeMillis();
                        boolean potwierdzone = toBuy.getId().equals(state.potwierdzenieZakupuId)
                                && (now - state.potwierdzenieZakupuCzas) <= POTWIERDZENIE_ZAKUPU_OKNO_MS;

                        if (potwierdzone) {
                            // Drugie kliknięcie w to samo ogłoszenie w oknie czasowym -> finalizuj zakup.
                            state.potwierdzenieZakupuId = null;
                            state.potwierdzenieZakupuCzas = 0L;
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                            buyItem(player, toBuy);
                            openMarketGUI(player);
                        } else {
                            // Pierwsze kliknięcie -> tylko prośba o potwierdzenie, BEZ zakupu.
                            Inventory top = player.getOpenInventory().getTopInventory();
                            if (state.potwierdzenieSlot >= 0 && state.potwierdzenieSlot != slot) {
                                MarketItem poprzedni = state.potwierdzenieZakupuId == null
                                        ? null : marketItemsById.get(state.potwierdzenieZakupuId);
                                top.setItem(state.potwierdzenieSlot, poprzedni != null ? wyswietlOferte(poprzedni) : null);
                            }
                            top.setItem(slot, oznaczPotwierdzenie(wyswietlOferte(toBuy)));
                            state.potwierdzenieSlot = slot;
                            state.potwierdzenieZakupuId = toBuy.getId();
                            state.potwierdzenieZakupuCzas = now;
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                            String cenaPotwierdzenie = toBuy.jestZaOdlamki()
                                    ? formatPrice(toBuy.getPrice(), "odlamki") + " &7" + nazwaWalutyOdlamki()
                                    : formatPrice(toBuy.getPrice());
                            wyslij(player, msgReplace("kupowanie.potwierdzenie", "{cena}",
                                    cenaPotwierdzenie));
                            String tytulPotw = msgReplace("kupowanie.potwierdzenie-title", "{cena}", cenaPotwierdzenie);
                            String subPotw = msgReplace("kupowanie.potwierdzenie-subtitle", "{cena}", cenaPotwierdzenie);
                            if (!ChatColor.stripColor(tytulPotw + subPotw).trim().isEmpty()) {
                                player.sendTitle(tytulPotw, subPotw, 5, 50, 10);
                            }
                        }
                    }
                }
                return;
        }

        if (shouldRefresh) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);

            // Reset strony do 0, chyba że to kliknięcie nawigacyjne
            if (slot != SLOT_POPRZEDNIA && slot != SLOT_NASTEPNA) {
                state.page = 0;
            }
            // Zmiana widoku (filtr/sort/strona/kategoria) unieważnia oczekujące potwierdzenie zakupu,
            // żeby gracz nie potwierdził przypadkiem zakupu innego przedmiotu, który wylądował w tym samym slocie.
            state.potwierdzenieZakupuId = null;
            state.potwierdzenieZakupuCzas = 0L;
            openMarketGUI(player);
        }
    }

    private void handleMyOffersClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        PlayerMarketState state = getPlayerState(player);
        if (slot == SLOT_POWROT_MOJE_OFERTY) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openMarketGUI(player);
            return;
        }
        if (slot == SLOT_POPRZEDNIA) {
            if (state.myPage > 0) {
                state.myPage--;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                openMyOffersGUI(player);
            }
            return;
        }
        if (slot == SLOT_NASTEPNA) {
            if ((state.myPage + 1) * MARKET_ITEM_SLOTS.length < state.lastRenderedMyOfferIds.size()) {
                state.myPage++;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                openMyOffersGUI(player);
            }
            return;
        }
        if (slot == SLOT_LICZNIK || slot == SLOT_INFO_MOJE) return;

        int offerIndex = indeksSlotuOferty(slot);
        if (offerIndex < 0) return;

        int itemIndex = (state.myPage * MARKET_ITEM_SLOTS.length) + offerIndex;
        if (itemIndex >= state.lastRenderedMyOfferIds.size()) {
            return;
        }
        UUID clickedId = state.lastRenderedMyOfferIds.get(itemIndex);
        MarketItem itemToCancel = marketItemsById.get(clickedId);

        if (itemToCancel != null) {
            marketItems.remove(itemToCancel);
            marketItemsById.remove(itemToCancel.getId());
            saveMarketItems();

            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(itemToCancel.getItem());
                wyslij(player, msg("anulowanie.wiadomosc"));
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), itemToCancel.getItem());
                wyslij(player, msg("anulowanie.wiadomosc_upuszczenie"));
            }
            sendTitle(player, "anulowanie", "{cena}", "");

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            openMyOffersGUI(player);
        }
    }

    // --- LOGIC ---

    private void buyItem(Player player, MarketItem marketItem) {
        if (marketItem.getSeller().equals(player.getUniqueId())) {
            wyslij(player, msg("bledy.wlasny_przedmiot"));
            return;
        }
        // Oferta mogla zostac kupiona/anulowana/wygasnac miedzy pierwszym a drugim klikniem.
        if (!marketItemsById.containsKey(marketItem.getId())) {
            wyslij(player, msg("oferta_niedostepna.wiadomosc"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Handel w tej walucie moze zostac wylaczony /arynek wylacz - wtedy zakup jest zablokowany.
        if (handelWylaczony(marketItem.getWaluta())) {
            wyslij(player, msgReplace("bledy.handel_wylaczony", "{waluta}", nazwaWalutyDla(marketItem.getWaluta())));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double price = marketItem.getPrice();

        if (marketItem.jestZaOdlamki()) {
            wyslij(player, msg("bledy.zla_waluta"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (econ == null) {
            wyslij(player, msg("bledy.brak_economy"));
            return;
        }
        if (econ.getBalance(player) < price) {
            wyslij(player, msg("bledy.brak_pieniedzy"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        econ.withdrawPlayer(player, price);
        econ.depositPlayer(Bukkit.getOfflinePlayer(marketItem.getSeller()), price);

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(marketItem.getItem());
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), marketItem.getItem());
            wyslij(player, msg("brak_miejsca.wiadomosc"));
        }

        marketItems.remove(marketItem);
        marketItemsById.remove(marketItem.getId());
        saveMarketItems();

        String cena = formatPrice(price, "pieniadze");
        for (String line : msgListReplace("kupowanie.wiadomosc", "{cena}", cena)) {
            wyslij(player, line);
        }
        sendTitle(player, "kupowanie", "{cena}", cena);

        Player sellerPlayer = Bukkit.getPlayer(marketItem.getSeller());
        if (sellerPlayer != null) {
            wyslij(sellerPlayer, msgReplace("sprzedaz.wiadomosc", "{cena}", cena));
            sendTitle(sellerPlayer, "sprzedaz", "{cena}", cena);
        }
    }

    /**
     * Odejmuje smocze odlamki z ekwipunku gracza.
     * @param ile liczba odlamkow do odbicia (zazwyczaj cena oferty)
     */
    private void odejmijOdlamkiGraczowi(Player player, int ile) {
        List<ItemStack> szablony = getOdlamkiSzablony();
        int pozostaloDoOdebrania = ile;
        ItemStack[] zawartosc = player.getInventory().getContents();
        for (int i = 0; i < zawartosc.length && pozostaloDoOdebrania > 0; i++) {
            ItemStack item = zawartosc[i];
            if (item == null) continue;
            if (!pasujeDoOdlamkow(item, szablony)) continue;
            int zabierz = Math.min(item.getAmount(), pozostaloDoOdebrania);
            int nowaIlosc = item.getAmount() - zabierz;
            if (nowaIlosc <= 0) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(nowaIlosc);
            }
            pozostaloDoOdebrania -= zabierz;
        }
        player.updateInventory();
    }

    /**
     * Wydaje graczowi "ile" smoczych odlamkow do ekwipunku.
     * Uzywane przez /rynek odbierz (odbior odlamkow ze sprzedazy na rynku).
     */
    private void wydajOdlamkiGraczowi(Player player, int ile) {
        List<ItemStack> szablony = getOdlamkiSzablony();
        // Do wydania (np. po /rynek odbierz) uzywamy pierwszego dostepnego szablonu
        // (KlanWalutaManager ma pierwszenstwo, potem MistrzSystem), zeby item wygladal
        // spojnie z tym, co gracze normalnie widza jako "Smocze Odlamki".
        ItemStack wzor = !szablony.isEmpty() ? szablony.get(0).clone() : new ItemStack(Material.DRAGON_BREATH);

        int maxStack = wzor.getMaxStackSize();
        int pozostalo = ile;
        while (pozostalo > 0) {
            int wStosie = Math.min(pozostalo, maxStack);
            ItemStack doWydania = wzor.clone();
            doWydania.setAmount(wStosie);
            Map<Integer, ItemStack> nadmiar = player.getInventory().addItem(doWydania);
            for (ItemStack niezmieszczony : nadmiar.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), niezmieszczony);
            }
            pozostalo -= wStosie;
        }
        player.updateInventory();
    }

    private List<MarketItem> getFilteredAndSortedItems(Player player) {
        PlayerMarketState state = getPlayerState(player);
        double balanceMoney = (econ != null) ? econ.getBalance(player) : 0;
        // UWAGA: sprawdzWygasniecia() celowo usuniete z tego miejsca.
        // Bylo wolane przy KAZDYM kliknieciu w GUI (kategoria, strona, sort),
        // co przy ruchu na rynku powodowalo iteracje po wszystkich ofertach
        // + potencjalny saveMarketItems() na watku serwera. Wygasniecia sa
        // obslugiwane przez dedykowany scheduler co minute (patrz startTasks()).

        List<MarketItem> filtered = marketItems.stream()
                .filter(item -> item != null)
                .filter(item -> !item.jestZaOdlamki())
                .filter(item -> {
                    if (!state.showAffordableOnly) return true;
                    return balanceMoney >= item.getPrice();
                })
                .filter(item -> {
                    switch (state.category) {
                        case ALL: return true;
                        case TOOLS:
                            if (state.subCategoryIndex == -1) return isAnyTool(item.getItem().getType());
                            return isMaterialInCategory(item.getItem().getType(), "narzedzia." + Category.TOOLS_SUB[state.subCategoryIndex]);
                        case ARMOR:
                            if (state.subCategoryIndex == -1) return isAnyArmor(item.getItem().getType());
                            return isMaterialInCategory(item.getItem().getType(), "uzbrojenie." + Category.ARMOR_SUB[state.subCategoryIndex]);
                        case SPECJALNE:
                            return pasujeDoSpecjalnych(item.getItem(), state.subCategoryIndex);
                        case PREMIUM:
                            return pasujeDoPodkategorii(item.getItem().getType(), "premium",
                                    Category.PREMIUM_SUB, state.subCategoryIndex);
                        default: return false;
                    }
                })
                .collect(Collectors.toList());

        // Sort
        filtered.sort(state.sortType.getComparator());
        return filtered;
    }

    /** Indeksy podkategorii Specjalne - liczone po nazwie, wiec kolejnosc w SPECJALNE_SUB nie ma znaczenia. */
    private static final int SPEC_KAMIENIE = Arrays.asList(Category.SPECJALNE_SUB).indexOf("kamienie");

    private boolean pasujeDoSpecjalnych(ItemStack item, int indeks) {
        if (item == null) return false;
        if (indeks == SPEC_KAMIENIE) return jestKamieniem(item);
        if (indeks >= 0) {
            return pasujeDoPodkategorii(item.getType(), "specjalne", Category.SPECJALNE_SUB, indeks);
        }
        return jestKamieniem(item)
                || pasujeDoPodkategorii(item.getType(), "specjalne", Category.SPECJALNE_SUB, -1);
    }

    /**
     * Kamienie Szamana zostaly usuniete (perki kupuje sie teraz bezposrednio w /szaman),
     * wiec zaden item nie jest juz "kamieniem". Metoda zostaje, zeby podkategoria "kamienie"
     * w categories.yml / rynek.yml nie wymagala zmian ukladu GUI - jest po prostu pusta.
     */
    private boolean jestKamieniem(ItemStack item) {
        return false;
    }

    /**
     * Czy material nalezy do (pod)kategorii z categories.yml.
     * indeks -1 = "Wszystkie" -> suma materialow wszystkich podkategorii danej kategorii.
     * Sciezki w pliku: &lt;kategoria&gt;.&lt;podkategoria&gt; (np. specjalne.kostiumy).
     */
    private boolean pasujeDoPodkategorii(Material material, String kategoria, String[] podkategorie, int indeks) {
        if (indeks >= 0 && indeks < podkategorie.length) {
            return isMaterialInCategory(material, kategoria + "." + podkategorie[indeks]);
        }
        for (String sub : podkategorie) {
            if (isMaterialInCategory(material, kategoria + "." + sub)) return true;
        }
        return false;
    }

    // Cache wynikow categoriesConfig.getStringList(sciezka) jako Set<Material> - bez tego
    // kazde wywolanie isMaterialInCategory() (a wiec kazdy filtr KAZDEGO przedmiotu na
    // rynku, przy KAZDYM otwarciu/odswiezeniu GUI) parsowalo na nowo YAML i budowalo
    // nowa liste/tablice, mimo ze categories.yml zmienia sie tylko przy /rynek reload.
    // Set zamiast List - kontains() O(1) zamiast O(n).
    private final Map<String, Set<Material>> categoryMaterialsCache = new HashMap<>();

    private boolean isMaterialInCategory(Material material, String categoryPath) {
        Set<Material> materials = categoryMaterialsCache.computeIfAbsent(categoryPath, path -> {
            List<String> nazwy = categoriesConfig.getStringList(path);
            Set<Material> zbior = new HashSet<>(nazwy.size());
            for (String nazwa : nazwy) {
                try {
                    zbior.add(Material.valueOf(nazwa));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("[Rynek] Nieznany material '" + nazwa + "' w categories.yml (" + path + ")");
                }
            }
            return zbior;
        });
        return materials.contains(material);
    }

    private boolean isAnyTool(Material material) {
        String matName = material.name();
        return matName.endsWith("_SWORD") || matName.endsWith("_PICKAXE") || matName.endsWith("_AXE") || matName.endsWith("_SHOVEL") || matName.endsWith("_HOE");
    }

    private boolean isAnyArmor(Material material) {
        String matName = material.name();
        return matName.endsWith("_HELMET") || matName.endsWith("_CHESTPLATE") || matName.endsWith("_LEGGINGS") || matName.endsWith("_BOOTS") || matName.equals("ELYTRA");
    }

    /**
     * Zwraca przedmiot z wygaslej oferty. Sprzedawca online dostaje go od razu; offline - przedmiot
     * trafia do zwrotow i jest wydawany przy nastepnym wejsciu na serwer (nic nie przepada).
     * Nie zapisuje pliku - wywolujacy odpowiada za saveMarketItems() po zakonczeniu petli.
     */
    private void handleExpiredItemReturn(MarketItem item) {
        Player seller = Bukkit.getPlayer(item.getSeller());
        if (seller != null && seller.isOnline()) {
            oddajGraczowi(seller, item.getItem());
            wyslij(seller, msg("wygasniecie.wiadomosc"));
            sendTitle(seller, "wygasniecie", "{ilosc}", "1");
            seller.playSound(seller.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        } else {
            oczekujaceZwroty.computeIfAbsent(item.getSeller(), k -> new ArrayList<>()).add(item.getItem());
            plugin.getLogger().info("Oferta " + item.getId() + " gracza " + item.getSellerName()
                    + " wygasla - przedmiot czeka na odbior przy wejsciu.");
        }
    }

    /** Wklada przedmiot do ekwipunku, a gdy nie ma miejsca - upuszcza obok gracza. */
    private void oddajGraczowi(Player player, ItemStack item) {
        Map<Integer, ItemStack> reszta = player.getInventory().addItem(item.clone());
        for (ItemStack nieumieszczony : reszta.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), nieumieszczony);
        }
    }

    /** Wydaje graczowi wszystko, co czekalo na niego po wygasnieciu ofert (wolane po wejsciu). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> zwroty = oczekujaceZwroty.remove(player.getUniqueId());
        if (zwroty == null || zwroty.isEmpty()) return;

        // Opoznienie, zeby gracz zdazyl sie w pelni zaladowac (ekwipunek / swiat).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                // Wyszedl przed odbiorem - odloz z powrotem, nic nie moze zginac.
                oczekujaceZwroty.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(zwroty);
                saveMarketItems();
                return;
            }
            for (ItemStack zwrot : zwroty) {
                oddajGraczowi(player, zwrot);
            }
            saveMarketItems();
            wyslij(player, msgReplace("wygasniecie.zwrot_po_wejsciu", "{ilosc}", String.valueOf(zwroty.size())));
            sendTitle(player, "wygasniecie", "{ilosc}", String.valueOf(zwroty.size()));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        }, 40L);
    }

    /** Usuwa z rynku wszystkie wygasle oferty i zwraca je wlascicielom. Zapisuje plik tylko gdy cos sie zmienilo. */
    private void sprawdzWygasniecia() {
        long now = System.currentTimeMillis();
        List<MarketItem> wygasle = new ArrayList<>();
        Iterator<MarketItem> it = marketItems.iterator();
        while (it.hasNext()) {
            MarketItem item = it.next();
            if (item == null) {
                it.remove();
                continue;
            }
            if (now > item.getExpirationTimestamp()) {
                wygasle.add(item);
                it.remove();
            }
        }
        if (wygasle.isEmpty()) return;
        for (MarketItem item : wygasle) {
            marketItemsById.remove(item.getId());
            handleExpiredItemReturn(item);
        }
        saveMarketItems();
    }

    /**
     * Usuwa z rynku WSZYSTKIE aktywne oferty (niezaleznie od tego, kiedy wygasaja) i zwraca
     * je wystawiajacym - dokladnie tym samym mechanizmem co naturalne wygasniecie oferty
     * (patrz handleExpiredItemReturn): sprzedawca online dostaje przedmiot od razu do
     * ekwipunku, sprzedawca offline - do odbioru przy nastepnym wejsciu na serwer ("Moje
     * Przedmioty"). Nic nie ginie. Uzywane przez komende /rynek resetall.
     * Zwraca liczbe zresetowanych ofert.
     */
    private synchronized int resetujWszystkieOferty() {
        List<MarketItem> wszystkie = new ArrayList<>(marketItems);
        if (wszystkie.isEmpty()) return 0;
        marketItems.clear();
        marketItemsById.clear();
        for (MarketItem item : wszystkie) {
            handleExpiredItemReturn(item);
        }
        saveMarketItems();
        // Odswiez GUI kazdemu, kto akurat ma otwarty rynek, zeby nie widzial "widmo" ofert.
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof RynekHolder) {
                online.closeInventory();
            }
        }
        return wszystkie.size();
    }

    /** Uruchamia cykliczne sprawdzanie wygasniec (co minute), niezaleznie od tego czy ktos otwiera GUI. */
    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sprawdzWygasniecia, 20L * 30, 20L * 60);
    }

    private String formatRemainingTime(long millis) {
        if (millis < 0) return "Wygasło";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || (days == 0 && hours == 0)) sb.append(minutes).append("m");

        return sb.toString().trim();
    }

    /**
     * Cena do pokazania graczowi: sformatowana skrotowo na zielono, a od 1000 w gore
     * obok niej dokladna kwota na szaro w nawiasie, np. "&a$33.2k &7($33,200)".
     * Ponizej 1000 skrot i wartosc dokladna sa takie same, wiec nawiasu nie dopisujemy.
     * Uzywane wszedzie, gdzie gracz widzi cene (lista, wiadomosci, tytuly, Moje Oferty).
     */
    private String formatPrice(double amount) {
        String krotka = formatShortPrice(amount);
        if (amount < 1000) {
            return "&a" + krotka;
        }
        String pelna = String.format(Locale.US, "$%,.2f", amount).replace(".00", "");
        return "&a" + krotka + " &7(" + pelna + ")";
    }

    /**
     * Cena sformatowana wg waluty oferty:
     * - "pieniadze" -> domyslny format "$..." (Vault),
     * - "odlamki"   -> "&d500" / "&d33.2k &7(33,200)" (SAMA liczba, kolor &d;
     *   nazwa waluty NIE jest tu doklejana - dodaje ja osobno {waluta} w lore/wiadomosciach).
     */
    private String formatPrice(double amount, String waluta) {
        if ("odlamki".equals(waluta)) {
            long ilosc = Math.round(amount);
            if (ilosc < 1000) {
                return "&d" + ilosc;
            }
            String krotka = formatShortNumber(ilosc);
            String pelna = String.format(Locale.US, "%,d", ilosc);
            return "&d" + krotka + " &7(" + pelna + ")";
        }
        return formatPrice(amount);
    }

    /** Nazwa waluty "odlamki" z configu (gui.waluta-odlamki), domyslnie "Smocze Odlamki". */
    private String nazwaWalutyOdlamki() {
        String nazwa = gui("waluta-odlamki");
        return (nazwa == null || nazwa.isEmpty()) ? "Smocze Odlamki" : nazwa;
    }

    private String formatShortPrice(double amount) {
        return "$" + formatShortNumber(Math.round(amount));
    }

    private String formatShortNumber(long amount) {
        if (amount >= 1_000_000_000) {
            return String.format(Locale.US, "%.1fB", amount / 1_000_000_000.0).replace(".0B", "B");
        } else if (amount >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", amount / 1_000_000.0).replace(".0M", "M");
        } else if (amount >= 1_000) {
            return String.format(Locale.US, "%.1fk", amount / 1_000.0).replace(".0k", "k");
        } else {
            return String.format(Locale.US, "%d", amount);
        }
    }

    private int getPlayerLimit(Player player) {
        int limit = 6; // Domyślny limit
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (perm.toLowerCase().startsWith("rynek.limit.")) {
                try {
                    int val = Integer.parseInt(perm.substring("rynek.limit.".length()));
                    if (val > limit) limit = val;
                } catch (NumberFormatException ignored) {}
            }
        }
        return limit;
    }

    // --- UTILS ---

    private ItemStack createItem(Material material, String name, List<String> lore, boolean enchanted) {
        ItemStack item = createItem(material, name, lore);
        if (enchanted) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(color(name));
            }
            if (lore != null) {
                meta.setLore(lore.stream().map(Rynek::color).collect(Collectors.toList()));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    // --- INNER CLASSES ---

    private static final class RynekHolder implements InventoryHolder {
        final boolean mojeOferty;
        Inventory inv;

        RynekHolder(boolean mojeOferty) {
            this.mojeOferty = mojeOferty;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public static class MarketItem {
        private final UUID id;
        private final ItemStack item;
        private final double price;
        private final UUID seller;
        private final String sellerName;
        private final long timestamp;
        private final long expirationTimestamp;
        // Waluta oferty: "pieniadze" (Vault) albo "odlamki" (smocze odlamki).
        private final String waluta;
        private ItemStack cachedDisplayItem;

        MarketItem(UUID id, ItemStack item, double price, UUID seller, String sellerName, long timestamp, String waluta) {
            this.id = id;
            this.item = item;
            this.price = price;
            this.seller = seller;
            this.sellerName = sellerName;
            this.timestamp = timestamp;
            this.expirationTimestamp = timestamp + TimeUnit.DAYS.toMillis(7); // 7 days expiration
            this.waluta = "odlamki".equalsIgnoreCase(waluta) ? "odlamki" : "pieniadze";
        }

        public UUID getId() { return id; }
        public ItemStack getItem() { return item; }
        public double getPrice() { return price; }
        public UUID getSeller() { return seller; }
        public String getSellerName() { return sellerName; }
        public long getTimestamp() { return timestamp; }
        public long getExpirationTimestamp() { return expirationTimestamp; }
        public String getWaluta() { return waluta; }
        public boolean jestZaOdlamki() { return "odlamki".equals(waluta); }

        public void invalidateDisplayCache() {
            this.cachedDisplayItem = null;
        }

        public ItemStack getDisplayItem(String liniaCenySzablon, List<String> loreOfertySzablony, String cenaSformatowana, String walutaNazwa) {
            if (cachedDisplayItem != null) {
                return cachedDisplayItem;
            }
            ItemStack display = item.clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = new ArrayList<>();
            if (meta != null && meta.hasLore()) {
                lore.addAll(meta.getLore());
            }
            String linia = (liniaCenySzablon != null ? liniaCenySzablon : "").replace("{cena_krotka}", cenaSformatowana);
            if (loreOfertySzablony != null) {
                for (String szablon : loreOfertySzablony) {
                    lore.add(szablon
                            .replace("{gracz}", sellerName)
                            .replace("{linia_ceny}", linia)
                            .replace("%cena%", cenaSformatowana != null ? cenaSformatowana : "")
                            .replace("{waluta}", walutaNazwa != null ? walutaNazwa : ""));
                }
            }
            if (meta != null) {
                meta.setLore(lore.stream().map(Rynek::color).collect(Collectors.toList()));
                display.setItemMeta(meta);
            }
            cachedDisplayItem = display;
            return cachedDisplayItem;
        }
    }

    private enum Category {
        ALL, TOOLS, ARMOR, SPECJALNE, PREMIUM;
        // Klucze używane do ładowania z categories.yml (małe litery, bez spacji)
        public static final String[] TOOLS_SUB = {"bronie", "siekiery", "lopaty"};
        public static final String[] ARMOR_SUB = {"helmy", "napiersniki", "spodnie", "buty", "elytry"};
        // Podkategorie Specjalne / Premium (zgodne z lore przyciskow z NBT).
        public static final String[] SPECJALNE_SUB = {"mistyczne", "eventowe", "kostiumy", "kamienie", "aury"};
        public static final String[] PREMIUM_SUB = {"odlamki", "klucze", "luckyblocki", "vouchery"};

        /** Podkategorie danej kategorii (pusta tablica, gdy kategoria ich nie ma). */
        public static String[] subKategorie(Category kategoria) {
            switch (kategoria) {
                case TOOLS: return TOOLS_SUB;
                case ARMOR: return ARMOR_SUB;
                case SPECJALNE: return SPECJALNE_SUB;
                case PREMIUM: return PREMIUM_SUB;
                default: return new String[0];
            }
        }
    }

    private enum SortType {
        // Doklejony tie-break po ID (comparing(MarketItem::getId)) na koniec kazdego
        // comparatora: bez niego dwie oferty o IDENTYCZNYM timestamp/price (np. wystawione
        // w tej samej milisekundzie) mialy niezdefiniowana kolejnosc wzgledem siebie przy
        // kazdym ponownym posortowaniu, co mogloby przestawiac ich pozycje w GUI miedzy
        // kolejnymi odswiezeniami/stronicowaniem.
        DATE_ASC("Rosnąca Data", Comparator.comparingLong(MarketItem::getTimestamp).thenComparing(MarketItem::getId)),
        DATE_DESC("Malejąca Data", Comparator.comparingLong(MarketItem::getTimestamp).reversed().thenComparing(MarketItem::getId)),
        PRICE_ASC("Rosnąca Cena", Comparator.comparingDouble(MarketItem::getPrice).thenComparing(MarketItem::getId)),
        PRICE_DESC("Malejąca Cena", Comparator.comparingDouble(MarketItem::getPrice).reversed().thenComparing(MarketItem::getId));

        private final String displayName;
        private final Comparator<MarketItem> comparator;

        SortType(String displayName, Comparator<MarketItem> comparator) {
            this.displayName = displayName;
            this.comparator = comparator;
        }

        public String getDisplayName() { return displayName; }
        public Comparator<MarketItem> getComparator() { return comparator; }

        public SortType next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private static class PlayerMarketState {
        int page = 0;
        int myPage = 0;
        Category category = Category.ALL;
        int subCategoryIndex = -1; // -1 oznacza 'Wszystkie Narzędzia/Uzbrojenie'
        boolean showAffordableOnly = false;
        SortType sortType = SortType.DATE_DESC;

        // Potwierdzenie zakupu: id przedmiotu, w ktory gracz kliknal jako pierwszy raz,
        // oraz czas tego kliknieca (do liczenia okna czasowego na potwierdzenie).
        UUID potwierdzenieZakupuId = null;
        long potwierdzenieZakupuCzas = 0L;
        // Slot oznaczony "kliknij ponownie" - przywracany do zwyklego wygladu przy kliknieciu w inna oferte.
        int potwierdzenieSlot = -1;

        // Lista ID przedmiotow DOKLADNIE w takiej kolejnosci, w jakiej zostaly
        // wyswietlone graczowi w GUI przy ostatnim openMarketGUI(). Klikniecia w
        // sloty z przedmiotami MUSZA byc mapowane na te zapamietana liste, a NIE
        // na ponowne wywolanie getFilteredAndSortedItems() - w miedzyczasie
        // marketItems mogla sie zmienic (ktos inny kupil/wystawil/wygasla oferta),
        // co przy przeliczeniu "na zywo" przesuwaloby indeksy i sprawialo, ze
        // klikniecie w item na ekranie kupowalo zupelnie inny, "sasiedni" item.
        List<UUID> lastRenderedItemIds = new ArrayList<>();

        // To samo co lastRenderedItemIds powyzej, ale dla GUI "Moje Oferty"
        // (openMyOffersGUI / handleMyOffersClick) - z tych samych powodow:
        // marketItems moze sie zmienic (np. ktos kupi jedna z Twoich ofert)
        // miedzy otwarciem tego GUI a kliknieciem w nim.
        List<UUID> lastRenderedMyOfferIds = new ArrayList<>();

        void setCategory(Category newCategory) {
            if (this.category == newCategory) {
                this.category = Category.ALL;
            } else {
                this.category = newCategory;
            }
            this.subCategoryIndex = -1;
        }

        // KRYTYCZNIE POPRAWIONA I JAWNA LOGIKA CYKLOWANIA
        void cycleSubCategory(Category newCategory) {
            String[] subCats = Category.subKategorie(newCategory);

            // 1. Zaczynamy cykl (przejście z innej kategorii lub ALL na tę)
            if (this.category != newCategory) {
                this.category = newCategory;
                this.subCategoryIndex = 0; // Zaczyna się od Mieczy/Hełmów
                return;
            }

            // Jesteśmy już w tej kategorii (TOOLS lub ARMOR)

            if (this.subCategoryIndex >= 0 && this.subCategoryIndex < subCats.length - 1) {
                // 2. Przechodzenie do następnej podkategorii (0 -> 1 -> 2)
                this.subCategoryIndex++;
            } else if (this.subCategoryIndex == subCats.length - 1) {
                // 3. Z ostatniej podkategorii -> 'Wszystkie' (-1)
                this.subCategoryIndex = -1;
            } else if (this.subCategoryIndex == -1) {
                // 4. Z 'Wszystkie' (-1) -> Wróć do stanu domyślnego ALL
                this.category = Category.ALL;
                this.subCategoryIndex = -1;
            } else {
                // WARUNEK ZABEZPIECZAJĄCY (awaryjny reset, jeśli indeks jest niepoprawny)
                this.category = Category.ALL;
                this.subCategoryIndex = -1;
            }
        }

        void toggleAffordable() {
            this.showAffordableOnly = !this.showAffordableOnly;
        }

        void cycleSort() {
            this.sortType = this.sortType.next();
        }
    }
}