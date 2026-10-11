package boniec.gornik;

import boniec.code.core.WorldGuardHook;
import boniec.eventodlamkow.EventOdlamkowManager;
import boniec.generatory.GeneratorManager;
import boniec.szaman.SzamanPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MiningListener implements Listener {

    private final GornikManager manager;
    private Economy economy;

    // Opcjonalne - moga byc null, jesli te systemy nie sa jeszcze zainicjowane
    // (np. inna kolejnosc ladowania w Main#onEnable). Bez nich event Szal
    // Odlamkow po prostu nie dziala przy kopaniu (brak wykrywania generatorow
    // / brak managera eventu), ale sama ekonomia kopania dziala normalnie.
    private GeneratorManager generatorManager;
    private EventOdlamkowManager eventOdlamkowManager;
    private PrivateTurboManager privateTurboManager;
    private boniec.poziomy.PoziomyManager poziomyManager;
    private boniec.misje.MisjeManager misjeManager;

    public MiningListener(GornikManager manager) {
        this.manager = manager;
        setupEconomy();
    }

    /** Wpina prywatny TurboDrop (/freeturbo) - patrz Main#onEnable (kolejnosc inicjalizacji). */
    public void setPrivateTurboManager(PrivateTurboManager privateTurboManager) {
        this.privateTurboManager = privateTurboManager;
    }

    public void setPoziomyManager(boniec.poziomy.PoziomyManager poziomyManager) {
        this.poziomyManager = poziomyManager;
    }

    public void setMisjeManager(boniec.misje.MisjeManager misjeManager) {
        this.misjeManager = misjeManager;
    }
    public void setEventOdlamkow(GeneratorManager generatorManager, EventOdlamkowManager eventOdlamkowManager) {
        this.generatorManager = generatorManager;
        this.eventOdlamkowManager = eventOdlamkowManager;
    }

    private void setupEconomy() {
        if (economy != null && economy.isEnabled()) return;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            Bukkit.getLogger().warning("[Gornik] Plugin Vault nie jest zainstalowany - system ekonomii kopania nie bedzie dzialac!");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        } else {
            Bukkit.getLogger().warning("[Gornik] Nie znaleziono zadnego providera ekonomii (Vault) - upewnij sie, ze masz zainstalowany plugin ekonomii (np. EssentialsX).");
        }
    }

    public boolean hasEconomy() {
        // Provider ekonomii moze zarejestrowac sie PO utworzeniu tego listenera
        // (kolejnosc wlaczania pluginow), wiec probujemy pobrac go ponownie,
        // jesli wczesniej sie nie udalo.
        if (economy == null || !economy.isEnabled()) {
            setupEconomy();
        }
        return economy != null && economy.isEnabled();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();
        UUID uuid = player.getUniqueId();

        double expBloku = manager.getExp(material);
        if (expBloku > 0 && !isGornikDenied(block.getLocation())) {
            if (poziomyManager != null) {
                poziomyManager.dodajExp(player, expBloku, false);
            }
            if (misjeManager != null) {
                misjeManager.dodajPostep(player, boniec.misje.TypMisji.WYKOP_BLOKI, 1);
            }
        }

        // --- Event "Szal Odlamkow": NIEZALEZNY od ekonomii kopania ponizej -
        // dziala nawet dla blokow generatora, ktore nie maja ceny w kopanie.yml
        // (albo gdy manager.isEnabled() = false). Jedyny warunek to blok musi
        // byc w obrebie generatora (system boniec.generatory) i event musi
        // byc aktualnie aktywny - reszta (szansa%, ilosc) jest w EventOdlamkowManager.
        if (generatorManager != null && eventOdlamkowManager != null && eventOdlamkowManager.isActive()) {
            if (generatorManager.getGeneratorAt(block.getLocation()) != null) {
                double nocny = generatorManager.mnoznikNocnyPvp(block.getLocation());
                if (nocny >= 1.0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() < nocny) {
                    eventOdlamkowManager.probujZadropicOdlamki(player);
                }
            }
        }

        if (!manager.isEnabled()) return;

        double cena = manager.getCena(material);
        if (cena <= 0) return;

        if (isGornikDenied(block.getLocation())) return;

        event.setDropItems(false);

        if (!hasEconomy()) {
            // Brak podpietej ekonomii (Vault) - informujemy raz w konsoli i nie kontynuujemy,
            // zeby nie okrasc gracza z bloku bez wyplaty.
            Bukkit.getLogger().warning("[Gornik] Gracz " + player.getName()
                    + " wykopal blok z cena, ale brak podpietej ekonomii (Vault) - nie wyplacono $.");
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        double cenaZFortune = manager.obliczCene(material, fortuneLevel);

        // Buty smoka (krolewskie x1.5, ogniste x1.25) - wiekszy drop z generatorow.
        boniec.dev.polezamek.eventitems.SauronGearManager smoczeButy = boniec.dev.polezamek.eventitems.SauronGearManager.get();
        if (smoczeButy != null) {
            double mnoznikButow = smoczeButy.getDropMultiplier(player);
            if (mnoznikButow != 1.0) {
                cenaZFortune *= mnoznikButow;
            }
        }

        // Boost Dropu Szamana (wlasny procent 0-200%) + klanowy perk dropu (/klan ulepszenia),
        // patrz DropPerkManager#getProcentLaczny - dokladany jako dodatkowy mnoznik do ceny
        // bloku, dokladnie tak samo jak Fortune/TurboDrop powyzej - to bezposredni
        // "zwiekszony zarobek podczas kopania generatorow" opisany w GUI /szaman.
        double mnoznikPerkuDropu = mnoznikPerkuDropuSzamana(uuid);
        if (mnoznikPerkuDropu != 1.0) {
            cenaZFortune *= mnoznikPerkuDropu;
        }

        // Prywatny TurboDrop (/freeturbo) - mnozy TYLKO cene TEGO gracza, niezaleznie
        // od globalnego TurboDropa (oba mnozniki sie mnoza, jesli aktywne naraz).
        // Czas bafu leci w czasie rzeczywistym (zegar w PrivateTurboManager) - liczba
        // wykopanych blokow nie ma wplywu na to, ile baf jeszcze trwa.
        if (privateTurboManager != null && privateTurboManager.isActive(uuid)) {
            cenaZFortune *= privateTurboManager.getMnoznikDlaGracza(uuid);
        }

        if (generatorManager != null) {
            double nocny = generatorManager.mnoznikNocnyPvp(block.getLocation());
            if (nocny != 1.0) {
                cenaZFortune *= nocny;
            }
        }

        // Boba Fett: pasywny drop posiadacza + godzina patrona x1.5 dla wszystkich.
        boniec.code.core.MistrzSystem mistrz = boniec.dev.inventory.Main.getInstance() != null
                ? boniec.dev.inventory.Main.getInstance().getMistrzSystem() : null;
        if (mistrz != null) {
            double boba = mistrz.getBobaDropMultiplier(player);
            if (boba != 1.0) {
                cenaZFortune *= boba;
            }
        }

        // Kwota trafia do kolejki zamiast byc wyplacana natychmiast - Essentials
        // (i wiekszosc innych ekonomii) zapisuje/serializuje dane gracza przy
        // KAZDYM depositPlayer(), a to przy masowym kopaniu potrafi mega
        // zlagowac serwer. Manager wyplaca zbiorczo co kilka sekund.
        manager.dodajDoWyplaty(uuid, cenaZFortune);
        manager.dodajZarobek(uuid, cenaZFortune);

        double exp = poziomyManager != null ? poziomyManager.wezExpKopaniaDoActionbara(uuid) : 0;
        if (manager.isActionbarEnabled()
                && boniec.ustawienia.UstawieniaManager.on(player, boniec.ustawienia.UstawieniaKlucz.ACTIONBAR)) {
            wyslijActionbar(player, cenaZFortune, exp);
        }
    }

    /**
     * Mnoznik z Boostu Dropu Szamana + klanowego perku dropu (0% = 1.0x, 100% = 2.0x -
     * kazdy "punkt procentowy" to +1% do ceny bloku).
     * 1.0, jesli system Szamana nie jest jeszcze zaladowany.
     */
    private double mnoznikPerkuDropuSzamana(UUID uuid) {
        SzamanPlugin szaman = SzamanPlugin.get();
        if (szaman == null || szaman.getDropPerkManager() == null) return 1.0;
        double procent = szaman.getDropPerkManager().getProcentLaczny(uuid);
        if (procent <= 0) return 1.0;
        return 1.0 + (procent / 100.0);
    }

    // Cache surowego szablonu actionbara (prefix+szablon w formacie '&'), zeby nie
    // odpytywac FileConfiguration (2x config.getString + string concat) przy KAZDYM
    // wykopanym bloku - config zmienia sie tylko przy /gornik reload.
    private String actionbarSzablonCache = null;

    /** Wywolaj po kazdym reloadzie configu (manager.reload()), zeby wyczyscic cache szablonu. */
    public void invalidateActionbarCache() {
        actionbarSzablonCache = null;
    }

    private String getActionbarSzablon() {
        if (actionbarSzablonCache == null) {
            String szablon = manager.getConfig().getString("messages.actionbar",
                    "&a+&e%zarobki%$ &8| %poziom-actionbar%");
            if (szablon != null && !szablon.contains("%poziom-actionbar%")) {
                szablon = szablon.replace("&#C02FD4+%exp% ᴇxᴘ", "%poziom-actionbar%");
                if (!szablon.contains("%poziom-actionbar%")) {
                    szablon = szablon + " &8| %poziom-actionbar%";
                }
            }
            actionbarSzablonCache = szablon;
        }
        return actionbarSzablonCache;
    }

    // Throttling wysylki actionbara per gracz - najdrozsza czesc tej metody to
    // player.spigot().sendMessage(ACTION_BAR, ...), ktore po stronie serwera
    // (ClientboundSystemChatPacket -> Component$Serializer -> Gson: JsonParser.parseString,
    // Codec.decode, EitherCodec.decode...) serializuje komponent do JSON-a. To bylo widac
    // w profilerze jako realny hotspot watku serwera (0.39%+ dla samego sendMessage) przy
    // masowym kopaniu, bo metoda leciala przy KAZDYM wykopanym bloku, czesto kilka razy
    // na sekunde na gracza. Actionbar i tak wisi u klienta ~3-4s, wiec wysylanie go czesciej
    // niz raz na kilka ticks nic nie daje wizualnie, a kosztuje. 4 ticks = ok. 5x/s, w zupelnosci
    // wystarczy zeby wygladalo na "live" a jednoczesnie realnie odciaza watek serwera.
    private static final long ACTIONBAR_MIN_INTERVAL_MS = 1000;
    private final ConcurrentHashMap<UUID, Long> ostatniActionbar = new ConcurrentHashMap<>();

    private void wyslijActionbar(Player player, double kwotaZTegoBloku, double exp) {
        UUID uuid = player.getUniqueId();
        long teraz = System.currentTimeMillis();
        Long ostatni = ostatniActionbar.get(uuid);
        if (ostatni != null && teraz - ostatni < ACTIONBAR_MIN_INTERVAL_MS) {
            if (poziomyManager != null && exp > 1e-9) {
                poziomyManager.oddajExpKopaniaDoActionbara(uuid, exp);
            }
            return;
        }
        ostatniActionbar.put(uuid, teraz);

        String poziomBar = poziomyManager != null
                ? poziomyManager.zbudujActionbarPostep(player, exp)
                : "";
        String tekst = getActionbarSzablon()
                .replace("%zarobki%", formatuj(kwotaZTegoBloku))
                .replace("%exp%", boniec.poziomy.PoziomyManager.formatujExp(exp))
                .replace("%poziom-actionbar%", poziomBar)
                .replace("%na-minute%", formatuj(manager.getZarobkiNaMinute(uuid)));

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(boniec.szaman.util.Msg.color(tekst)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.dodajGraczaDoBossBara(event.getPlayer());
        if (eventOdlamkowManager != null) {
            eventOdlamkowManager.dodajGraczaDoBossBara(event.getPlayer());
        }
        if (privateTurboManager != null) {
            privateTurboManager.onJoin(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.usunGraczaZBossBara(event.getPlayer());
        manager.wyplacNatychmiast(event.getPlayer().getUniqueId());
        manager.czysc(event.getPlayer().getUniqueId());
        ostatniActionbar.remove(event.getPlayer().getUniqueId());
        if (eventOdlamkowManager != null) {
            eventOdlamkowManager.usunGraczaZBossBara(event.getPlayer());
        }
        if (privateTurboManager != null) {
            privateTurboManager.onQuit(event.getPlayer().getUniqueId());
        }
    }

    private boolean isGornikDenied(org.bukkit.Location location) {
        try {
            // Jedno zapytanie do WorldGuard sprawdzajace oba id-ki regionow naraz
            // (zamiast dwoch pelnych getApplicableRegions() na kazdy wykopany blok).
            return WorldGuardHook.isInAnyRegion(location, "gornik-deny", "no-mining-economy");
        } catch (Throwable t) {
            return false;
        }
    }

    private String formatuj(double wartosc) {
        if (wartosc == Math.floor(wartosc)) {
            return String.valueOf((long) wartosc);
        }
        long grosze = Math.round(wartosc * 100.0);
        long calkowite = grosze / 100;
        long reszta = Math.abs(grosze % 100);
        return calkowite + "." + (reszta < 10 ? "0" : "") + reszta;
    }
}
