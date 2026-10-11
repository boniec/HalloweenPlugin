package boniec.dev.polezamek.witchwand;

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.util.ItemTagUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HookManager implements Listener {

    public static final Material HOOK_MATERIAL = Material.FISHING_ROD;
    public static final int HOOK_CUSTOM_MODEL_DATA = 1001;
    private int customModelData = HOOK_CUSTOM_MODEL_DATA;

    private String hookName;
    private NamespacedKey itemKey;

    private final java.util.Set<String> legacyNames = new java.util.HashSet<>();

    private List<String> hookLore;

    private int cooldownSeconds;

    /** Max kratek do przodu przy dalekim zarzucie haka. */
    private double launchDistance;
    /** Min kratek do przodu przy krotkim zarzucie. */
    private double launchDistanceMin;
    /** O ile kratek unosi w gore w trakcie lotu (np. 1.5). */
    private double launchLift;
    /** Jak dlugo po rzucie drugi PPM nadal liczy sie jako przyciagniecie (sekundy). */
    private int armSeconds;
    /** Krotkie impulsy knockback (vanilla tnie predkosc; 2-4 = dalszy lot). */
    private int knockbackPulsy;

    private String msgOnCooldown;
    private String msgUsed;
    private String msgGiven;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /*
     * Aktywne haki graczy.
     *
     * NIE wymagamy tutaj, żeby hak był zaczepiony o blok.
     * Trzymamy po prostu obiekt FishHook i jego aktualną pozycję.
     */
    private final Map<UUID, FishHook> activeHooks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> hookPos = new ConcurrentHashMap<>();
    /** Najdalej polecial hak od gracza (poziomo) — przy PPM wedka juz wraca, bez tego dystans ~1. */
    private final Map<UUID, Double> maxZarzut = new ConcurrentHashMap<>();

    /*
     * Gracze, ktorzy rzucili hak i czekaja na przyciagniecie.
     *
     * WAZNE: to jest niezalezne od tego, czy encja FishHook jeszcze zyje.
     * W evencie FISHING hak nie jest jeszcze dodany do swiata (isValid()==false),
     * a podczas lotu na elytrze vanilla usuwa hak, gdy oddali sie >32 kratki od
     * gracza. Dlatego 'uzbrojenie' trzymamy jako znacznik czasu.
     */
    private final Map<UUID, Long> armedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> armedAt = new ConcurrentHashMap<>();

    /* Ochrona przed podwojnym wykonaniem (Interact HAND + OFF_HAND + FishEvent w jednym kliku). */
    private final Map<UUID, Long> lastPullMs = new ConcurrentHashMap<>();

    /*
     * Gracze, którzy w tej chwili są w oknie "braku obrażeń od upadku"
     * po użyciu haka (krótko po pociągnięciu, nie przez cały cooldown).
     */
    private final java.util.Set<UUID> fallDamageImmune =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final long FALL_IMMUNITY_TICKS = 40L; // 2 sekundy

    private final Main plugin;

    public HookManager(Main plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::trackHooks, 1L, 1L);
    }

    private void trackHooks() {
        for (Map.Entry<UUID, FishHook> e : activeHooks.entrySet()) {
            FishHook hook = e.getValue();
            if (hook != null && hook.isValid() && !hook.isDead()) {
                hookPos.put(e.getKey(), hook.getLocation().clone());
                Player p = hook.getShooter() instanceof Player pl ? pl : null;
                if (p != null && p.getUniqueId().equals(e.getKey())) {
                    double d = dystansPoziomy(p.getLocation(), hook.getLocation());
                    maxZarzut.merge(e.getKey(), d, Math::max);
                }
            }
        }
    }

    public void reloadConfig(Main plugin) {
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    private void loadConfig(Main plugin) {
        FileConfiguration cfg = plugin.getConfig();

        if (this.hookName != null) {
            legacyNames.add(this.hookName);
        }

        this.hookName = col(cfg.getString("hook.name",
                "&f丮 &f&lʜᴀᴋ &x&3&1&E&A&F&C&lɢ&x&3&1&E&C&E&F&lᴀ&x&3&1&E&D&E&2&lʟ&x&3&1&E&F&D&5&lᴀ&x&3&1&F&1&C&8&lᴋ&x&3&1&F&2&B&B&lᴛ&x&3&1&F&4&A&D&lʏ&x&3&1&F&5&A&0&lᴄ&x&3&1&F&7&9&3&lᴢ&x&3&1&F&9&8&6&lɴ&x&3&1&F&A&7&9&lʏ"));
        this.customModelData = cfg.getInt("hook.custom-model-data", HOOK_CUSTOM_MODEL_DATA);
        legacyNames.add(this.hookName);

        this.hookLore = cfg.getStringList("hook.lore");

        this.cooldownSeconds =
                cfg.getInt("hook.cooldown-seconds", 45);

        double maxD = cfg.contains("hook.dystans")
                ? cfg.getDouble("hook.dystans")
                : cfg.getDouble("hook.launch-distance", 25.0);
        this.launchDistance = Math.max(3.0, Math.min(80.0, maxD));
        this.launchDistanceMin = Math.max(3.0, Math.min(
                launchDistance,
                cfg.getDouble("hook.launch-distance-min", 8.0)
        ));
        this.launchLift = Math.max(0.0, Math.min(4.0, cfg.getDouble("hook.launch-lift", 1.5)));
        this.armSeconds = Math.max(1, cfg.getInt("hook.arm-seconds", 20));
        this.knockbackPulsy = Math.max(1, Math.min(6, cfg.getInt("hook.sila", 3)));

        this.msgOnCooldown =
                cfg.getString(
                        "messages.hook-on-cooldown",
                        "&cHak Galaktyczny jest jeszcze na cooldownie &8(&4{czas}s&8)"
                );

        this.msgUsed = cfg.getString("messages.hook-used", "");

        this.msgGiven =
                cfg.getString(
                        "messages.hook-given",
                        "&aOtrzymałeś Hak Galaktyczny!"
                );
    }

    public ItemStack createHookItem() {

        ItemStack item = new ItemStack(HOOK_MATERIAL);

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(hookName);

        ItemTagUtil.applyModel(meta, customModelData);

        if (hookLore != null && !hookLore.isEmpty()) {

            meta.setLore(
                    hookLore.stream()
                            .map(HookManager::col)
                            .toList()
            );

        } else {

            meta.setLore(buildDefaultLore());

        }

        meta.addEnchant(
                Enchantment.UNBREAKING,
                1,
                true
        );

        meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS
        );

        ItemTagUtil.setTag(
                itemKey,
                meta,
                "hook"
        );

        item.setItemMeta(meta);

        return item;
    }

    private List<String> buildDefaultLore() {

        return List.of(
                col("&f"),
                col("&f 丮 ᴅᴢɪᴇᴋɪ ᴛᴇᴍᴜ &x&3&1&E&A&F&Cɢᴀʟᴀᴋᴛʏᴄᴢɴᴇᴍᴜ &fʜᴀᴋᴏᴡɪ"),
                col("&f ᴡʏʀᴢᴜᴄᴀꜱᴢ ꜱɪᴇ ᴡ ᴘᴏᴡɪᴇᴛʀᴢᴇ ɪ ꜱᴢʏʙᴜᴊᴇꜱᴢ ᴡ ᴅᴀʟ"),
                col("&f"),
                col("&f  &8» &eᴘᴘᴍ &7- ᴢᴀʀᴢᴜᴄ ʜᴀᴋ"),
                col("&f  &8» &eᴘᴘᴍ &7- ᴘᴏɴᴏᴡɴɪᴇ, ᴀʙʏ ꜱɪᴇ ᴘʀᴢʏᴄɪᴀɢɴᴀᴄ"),
                col("&f")
        );
    }

    public boolean isHookItem(ItemStack item) {

        return ItemTagUtil.matches(
                itemKey,
                item,
                "hook",
                legacyNames.toArray(new String[0])
        );
    }

    public boolean refreshItem(ItemStack item) {

        if (!isHookItem(item)) {
            return false;
        }

        List<String> lore =
                (hookLore != null && !hookLore.isEmpty())
                        ? hookLore.stream()
                        .map(HookManager::col)
                        .toList()
                        : buildDefaultLore();

        boolean changed = ItemTagUtil.refresh(
                item,
                hookName,
                lore
        );
        changed |= ItemTagUtil.syncModel(item, customModelData);
        return changed;
    }

    public boolean isOnCooldown(Player player) {

        Long until =
                cooldowns.get(player.getUniqueId());

        return until != null
                && until > System.currentTimeMillis();
    }

    public long getCooldownRemainingSeconds(Player player) {

        Long until =
                cooldowns.get(player.getUniqueId());

        if (until == null) {
            return 0;
        }

        return Math.max(
                0,
                (until - System.currentTimeMillis()) / 1000L
        );
    }

    public void setCooldownNow(Player player) {

        cooldowns.put(
                player.getUniqueId(),
                System.currentTimeMillis()
                        + cooldownSeconds * 1000L
        );

        // Cooldown widoczny na hotbarze (szary "zegar" na itemie) - tak jak w pozostalych eventowkach.
        player.setCooldown(HOOK_MATERIAL, cooldownSeconds * 20);
    }

    public void resetPlayer(Player player) {

        UUID uuid = player.getUniqueId();

        cooldowns.remove(uuid);
        armedUntil.remove(uuid);
        armedAt.remove(uuid);
        lastPullMs.remove(uuid);
        hookPos.remove(uuid);
        maxZarzut.remove(uuid);
        player.setCooldown(HOOK_MATERIAL, 0);

        FishHook hook =
                activeHooks.remove(uuid);

        if (hook != null
                && hook.isValid()
                && !hook.isDead()) {

            hook.remove();
        }
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public double getPullStrength() {
        return launchDistance;
    }

    /** Odleglosc pozioma miedzy graczem a hakiem (jak daleko zarzucil). */
    private double odlegloscZarzutu(Player player, FishHook hook) {
        Location dest = null;
        if (hook != null && hook.isValid() && !hook.isDead()) {
            dest = hook.getLocation();
        }
        if (dest == null) {
            dest = hookPos.get(player.getUniqueId());
        }
        if (dest == null || dest.getWorld() == null || player.getWorld() == null
                || !dest.getWorld().equals(player.getWorld())) {
            return launchDistanceMin;
        }
        return dystansPoziomy(player.getLocation(), dest);
    }

    /**
     * Krotki zarzut = launchDistanceMin, daleki (ok. 28+ kratek) = launchDistance.
     */
    private double dystansLotu(Player player, FishHook hook, UUID uuid) {
        Double zapisany = maxZarzut.remove(uuid);
        double zarzut = zapisany != null ? zapisany : odlegloscZarzutu(player, hook);
        double refMin = 4.0;
        double refMax = 28.0;
        double t = (zarzut - refMin) / (refMax - refMin);
        t = Math.max(0.0, Math.min(1.0, t));
        return launchDistanceMin + t * (launchDistance - launchDistanceMin);
    }

    private static Vector kierunekDoPrzodu(Player player) {
        Vector v = player.getLocation().getDirection().clone();
        v.setY(0);
        if (v.lengthSquared() <= 0.0001) {
            float yaw = player.getLocation().getYaw();
            double rad = Math.toRadians(yaw);
            return new Vector(-Math.sin(rad), 0, Math.cos(rad));
        }
        return v.normalize();
    }

    private static double dystansPoziomy(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return 0;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Ile kratek w poziomie przeleci przy starcie z predkoscia 1.0 (tarcie 0.91/tick). */
    private static double symulacjaPozioma(double v0) {
        double vx = v0;
        double sum = 0;
        for (int t = 0; t < 400; t++) {
            sum += vx;
            vx *= 0.91;
            if (vx < 0.004) {
                break;
            }
        }
        return sum;
    }

    private static double predkoscPoziomaDlaDystansu(double kratki) {
        double naJeden = symulacjaPozioma(1.0);
        if (naJeden < 0.001) {
            return 2.0;
        }
        return Math.min(3.9, kratki / naJeden);
    }

    public String getMsgOnCooldown(long seconds) {

        return msgOnCooldown.replace(
                "{czas}",
                String.valueOf(seconds)
        );
    }

    public String getMsgUsed() {
        return msgUsed;
    }

    public String getMsgGiven() {
        return msgGiven;
    }

    private static String col(String s) {

        return ChatColor.translateAlternateColorCodes(
                '&',
                s
        );
    }

    /*
     * ==========================================================
     * RZUT I PRZYCIAGNIECIE (PlayerFishEvent)
     * ==========================================================
     *
     * Flow:
     *   1. PPM  -> stan FISHING: rzut. Zapisujemy 'uzbrojenie' (znacznik czasu).
     *              NIE sprawdzamy hook.isValid() - w tym evencie hak nie jest
     *              jeszcze dodany do swiata, wiec isValid() zawsze zwraca false
     *              (to byl glowny powod, dla ktorego hak nigdy nie dzialal).
     *   2. PPM  -> albo REEL_IN / IN_GROUND / CAUGHT_* (hak jeszcze istnieje),
     *              albo ponownie FISHING (hak zniknal - np. podczas lotu na
     *              elytrze vanilla usuwa hak oddalony o >32 kratki).
     *              W obu przypadkach: przyciagniecie.
     */

    private boolean isPullState(PlayerFishEvent.State state) {

        return state == PlayerFishEvent.State.REEL_IN
                || state == PlayerFishEvent.State.IN_GROUND
                || state == PlayerFishEvent.State.CAUGHT_ENTITY
                || state == PlayerFishEvent.State.CAUGHT_FISH
                || state == PlayerFishEvent.State.FAILED_ATTEMPT;
    }

    private boolean isArmed(UUID uuid) {

        Long until = armedUntil.get(uuid);

        if (until == null) {
            return false;
        }

        if (until < System.currentTimeMillis()) {
            armedUntil.remove(uuid);
            return false;
        }

        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFish(PlayerFishEvent event) {

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ItemStack item =
                player.getInventory().getItemInMainHand();

        if (!isHookItem(item)) {

            item =
                    player.getInventory().getItemInOffHand();

            if (!isHookItem(item)) {
                return;
            }
        }

        PlayerFishEvent.State state = event.getState();

        /*
         * Strefy bezpieczne: hak nie dziala (ciche anulowanie).
         */
        if (plugin.isEventItemUseBlocked(player.getLocation())) {

            event.setCancelled(true);
            disarm(player);
            return;
        }

        /*
         * BITE / LURING (hak w wodzie) - ignorujemy, to nie jest klikniecie gracza.
         */
        if (state == PlayerFishEvent.State.BITE) {
            return;
        }

        /*
         * Rzut albo ponowny PPM, gdy hak juz zniknal.
         */
        if (state == PlayerFishEvent.State.FISHING) {

            if (isArmed(uuid)) {

                // Hak juz nie istnieje, a gracz kliknal drugi raz = przyciagniecie.
                event.setCancelled(true);
                pullNow(player, null);
                return;
            }

            if (isOnCooldown(player)) {

                player.sendMessage(
                        col(getMsgOnCooldown(getCooldownRemainingSeconds(player)))
                );

                event.setCancelled(true);
                return;
            }

            FishHook hook = event.getHook();

            if (hook != null) {
                activeHooks.put(uuid, hook);
                hookPos.put(uuid, hook.getLocation().clone());
                maxZarzut.put(uuid, dystansPoziomy(player.getLocation(), hook.getLocation()));
            }

            long teraz = System.currentTimeMillis();
            armedUntil.put(uuid, teraz + armSeconds * 1000L);
            armedAt.put(uuid, teraz);

            return;
        }

        /*
         * Drugi PPM, hak jeszcze istnieje.
         * Anulujemy event, zeby wedka nie lapala encji / ryb i nie
         * zuzywala trwalosci po swojemu.
         */
        if (isPullState(state)) {

            event.setCancelled(true);

            FishHook hook = event.getHook();

            if (hook == null) {
                hook = activeHooks.get(uuid);
            }

            pullNow(player, hook);
        }
    }

    /*
     * Fallback: zwykly PlayerInteractEvent. Jesli gracz ma rzucony hak
     * (uzbrojony) i kliknie PPM trzymajac hak, przyciagamy od razu.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {

        Action action =
                event.getAction();

        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK) {

            return;
        }

        Player player =
                event.getPlayer();

        ItemStack item =
                event.getItem();

        if (item == null
                || !isHookItem(item)) {

            return;
        }

        UUID uuid = player.getUniqueId();
        if (!isArmed(uuid)) {
            return;
        }

        // UWAGA: zadnego sztucznego opoznienia tutaj - gracz ma mozliwosc
        // przyciagniecia natychmiast po zarzuceniu haka. Ochrone przed
        // podwojnym wykonaniem TEGO SAMEGO kliknicia (ten handler + FishEvent
        // dla tego samego PPM) daje juz lastPullMs w pullNow() (okno 250ms).
        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) {
            disarm(player);
            return;
        }

        pullNow(player, activeHooks.get(player.getUniqueId()));
    }

    /*
     * Zmiana slotu = vanilla usuwa hak, wiec zdejmujemy uzbrojenie.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        disarm(event.getPlayer());
    }

    /*
     * Ciche usuniecie haka i uzbrojenia gracza - bez wiadomosci.
     */
    private void disarm(Player player) {

        UUID uuid = player.getUniqueId();

        armedUntil.remove(uuid);
        armedAt.remove(uuid);
        hookPos.remove(uuid);
        maxZarzut.remove(uuid);

        FishHook hook = activeHooks.remove(uuid);

        if (hook != null
                && hook.isValid()
                && !hook.isDead()) {

            hook.remove();
        }
    }

    /*
     * Wspolny punkt wejscia przyciagniecia (z FishEvent i z Interact).
     * 'hook' moze byc null / martwy - nie ma to znaczenia.
     */
    private void pullNow(Player player, FishHook hook) {

        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        Long last = lastPullMs.get(uuid);

        if (last != null && now - last < 250L) {
            return; // ten sam klik obsluzony juz przez inny handler
        }

        if (isOnCooldown(player)) {

            player.sendMessage(
                    col(getMsgOnCooldown(getCooldownRemainingSeconds(player)))
            );

            disarm(player);
            return;
        }

        lastPullMs.put(uuid, now);

        if (hook == null) {
            hook = activeHooks.get(uuid);
        }

        armedUntil.remove(uuid);
        armedAt.remove(uuid);
        activeHooks.remove(uuid);

        executePull(player, hook);
    }

    /**
     * Knockback do przodu (jak odrzut). Impuls po 1 ticku, zeby wedka nie zerowala predkosci.
     */
    private void executePull(Player player, FishHook hook) {
        UUID uuid = player.getUniqueId();
        hookPos.remove(uuid);

        // Zawsze ten sam, przewidywalny dystans z configu. Nie zależy od
        // chwili, w której vanilla zaczęła zwijać żyłkę.
        double cel = launchDistance;
        Vector doPrzodu = kierunekDoPrzodu(player);
        double hSpeed = predkoscPoziomaDlaDystansu(cel);

        Vector impuls = doPrzodu.multiply(hSpeed);
        impuls.setY(Math.min(0.55, 0.42 + launchLift * 0.06));

        nadajKnockback(player, impuls);

        fallDamageImmune.add(uuid);
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin.getHost(),
                () -> fallDamageImmune.remove(uuid),
                FALL_IMMUNITY_TICKS
        );

        setCooldownNow(player);

        if (hook != null && hook.isValid() && !hook.isDead()) {
            hook.remove();
        }
    }

    private void nadajKnockback(Player player, Vector impuls) {
        // Jeden normalny impuls knockbacku. Wielokrotne impulsy powodowały
        // nienaturalne szarpanie i niestabilny dystans.
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin.getHost(), () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            player.setVelocity(impuls);
            player.setFallDistance(0);
        }, 1L);
    }

    /*
     * Hak jest niezniszczalny - nigdy nie traci trwalosci (ani od uzycia,
     * ani od zadnej innej akcji wedki). Tak samo jak Elytra/Netherite w
     * NiezniszczalnoscListener, bez dodawania "Unbreakable" do lore.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHookItemDamage(PlayerItemDamageEvent event) {

        if (isHookItem(event.getItem())) {
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    /*
     * Wywoływane z listenera obrażeń: mówi, czy dany gracz jest
     * właśnie w krótkim oknie braku obrażeń od upadku po haku.
     */
    public boolean isFallDamageImmune(Player player) {
        return fallDamageImmune.contains(player.getUniqueId());
    }

    /*
     * ==========================================================
     * BRAK OBRAŻEŃ OD UPADKU PO UŻYCIU HAKA
     * ==========================================================
     *
     * Tylko w krótkim oknie czasowym po pociągnięciu (nie przez
     * cały cooldown), żeby uniknąć darmowej, długiej nieśmiertelności
     * na fall damage.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (isFallDamageImmune(player)) {
            event.setCancelled(true);
        }
    }

    /*
     * ==========================================================
     * WYJŚCIE GRACZA
     * ==========================================================
     */

    @EventHandler
    public void onPlayerQuit(
            PlayerQuitEvent event
    ) {

        UUID uuid =
                event.getPlayer().getUniqueId();

        FishHook hook =
                activeHooks.remove(uuid);

        if (hook != null
                && hook.isValid()
                && !hook.isDead()) {

            hook.remove();
        }

        cooldowns.remove(uuid);
        armedUntil.remove(uuid);
        armedAt.remove(uuid);
        lastPullMs.remove(uuid);
        fallDamageImmune.remove(uuid);
    }
}