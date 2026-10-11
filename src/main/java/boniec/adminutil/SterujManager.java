package boniec.adminutil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /steruj — admin wciela się w gracza: chodzi, patrzy i używa jego ekwipunku,
 * a inni widzą postać ofiary.
 */
public final class SterujManager implements Listener {

    public static final String PERM = "core.steruj";
    public static final String PERM_BYPASS = "core.steruj.bypass";

    static final String ICON = "临";

    private final JavaPlugin plugin;
    private final Map<UUID, Sesja> poKontrolerze = new ConcurrentHashMap<>();
    private final Map<UUID, Sesja> poOfierze = new ConcurrentHashMap<>();
    private BukkitTask task;
    private int actionBarTick;

    public SterujManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        for (Sesja sesja : poKontrolerze.values().toArray(Sesja[]::new)) {
            zakoncz(sesja, false);
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean steruje(Player player) {
        return player != null && poKontrolerze.containsKey(player.getUniqueId());
    }

    public boolean jestSterowany(Player player) {
        return player != null && poOfierze.containsKey(player.getUniqueId());
    }

    public String rozpocznij(Player kontroler, Player ofiara) {
        if (kontroler.equals(ofiara)) {
            return "&f亢 &cNie możesz sterować samym sobą.";
        }
        if (!ofiara.isOnline()) {
            return "&f与 &cTen gracz jest obecnie offline!";
        }
        if (ofiara.hasPermission(PERM_BYPASS)) {
            return "&f与 &cTego gracza nie można przejąć.";
        }
        if (steruje(kontroler)) {
            return "&f亢 &cJuż kimś sterujesz. Wpisz &f/steruj &cżeby przestać.";
        }
        if (jestSterowany(kontroler)) {
            return "&f亢 &cKimś steruje Twoją postacią — najpierw musi Cię puścić.";
        }
        if (steruje(ofiara) || jestSterowany(ofiara)) {
            return "&f与 &cTen gracz jest już w sesji sterowania.";
        }

        if (kontroler.getVehicle() != null) {
            kontroler.leaveVehicle();
        }
        if (ofiara.getVehicle() != null) {
            ofiara.leaveVehicle();
        }

        Sesja sesja = new Sesja(kontroler, ofiara);
        poKontrolerze.put(kontroler.getUniqueId(), sesja);
        poOfierze.put(ofiara.getUniqueId(), sesja);

        kontroler.teleport(ofiara.getLocation());
        ukryjKontrolera(kontroler, ofiara);
        kontroler.setCollidable(false);
        ofiara.setCollidable(false);
        kontroler.setSilent(true);
        kontroler.setSleepingIgnored(true);
        kontroler.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        kontroler.setGameMode(ofiara.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : ofiara.getGameMode());
        kopiujEq(ofiara, kontroler);

        ofiara.sendTitle(color("&c&lSTERUJĄ TOBĄ"), color("&7" + kontroler.getName()), 5, 60, 15);
        start();
        return null;
    }

    public boolean zakonczKontrolera(Player kontroler, boolean powiadom) {
        Sesja sesja = poKontrolerze.get(kontroler.getUniqueId());
        if (sesja == null) {
            return false;
        }
        zakoncz(sesja, powiadom);
        return true;
    }

    private void zakoncz(Sesja sesja, boolean powiadom) {
        poKontrolerze.remove(sesja.kontrolerId);
        poOfierze.remove(sesja.ofiaraId);

        Player kontroler = Bukkit.getPlayer(sesja.kontrolerId);
        Player ofiara = Bukkit.getPlayer(sesja.ofiaraId);

        if (kontroler != null && kontroler.isOnline()) {
            if (ofiara != null && ofiara.isOnline()) {
                kopiujEq(kontroler, ofiara);
            }
            przywrocKontrolera(kontroler, ofiara, sesja);
            if (powiadom) {
                kontroler.sendMessage(color("&f" + ICON + " &7Przestałeś sterować &f" + sesja.ofiaraNick + "&7."));
            }
        } else if (ofiara != null && ofiara.isOnline() && sesja.eqOfiary != null) {
            wgrajZapisaneEq(ofiara, sesja.eqOfiary, sesja.slotOfiary);
        }

        if (ofiara != null && ofiara.isOnline()) {
            ofiara.setCollidable(true);
            if (powiadom) {
                ofiara.sendTitle(color("&a&lKONIEC"), color("&7Znowu sterujesz sobą"), 5, 40, 10);
                ofiara.sendMessage(color("&f" + ICON + " &7Znowu sterujesz swoją postacią."));
            }
        }

        if (poKontrolerze.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        actionBarTick++;
        for (Sesja sesja : poKontrolerze.values().toArray(Sesja[]::new)) {
            Player kontroler = Bukkit.getPlayer(sesja.kontrolerId);
            Player ofiara = Bukkit.getPlayer(sesja.ofiaraId);
            if (kontroler == null || !kontroler.isOnline() || ofiara == null || !ofiara.isOnline()) {
                zakoncz(sesja, true);
                continue;
            }
            synchronizujCialo(sesja, kontroler, ofiara);
            if (actionBarTick % 5 == 0) {
                kopiujEq(kontroler, ofiara);
            }
            if (actionBarTick % 20 == 0) {
                actionBar(kontroler, "&f" + ICON + " &7Sterujesz: &f" + ofiara.getName() + " &8| &f/steruj &7żeby wyjść");
                actionBar(ofiara, "&cKimś steruje Twoją postacią");
            }
        }
    }

    private void synchronizujCialo(Sesja sesja, Player kontroler, Player ofiara) {
        Location cel = kontroler.getLocation();
        Location teraz = ofiara.getLocation();
        boolean trzebaTp = teraz.getWorld() != cel.getWorld()
                || teraz.distanceSquared(cel) > 0.0004
                || Math.abs(teraz.getYaw() - cel.getYaw()) > 0.8
                || Math.abs(teraz.getPitch() - cel.getPitch()) > 0.8;
        if (trzebaTp) {
            sesja.syncing = true;
            ofiara.teleport(cel);
            sesja.syncing = false;
        }
        ofiara.setVelocity(kontroler.getVelocity());
        ofiara.setFallDistance(kontroler.getFallDistance());
        ofiara.setSneaking(kontroler.isSneaking());
        ofiara.setSprinting(kontroler.isSprinting());
        ofiara.setSwimming(kontroler.isSwimming());
        if (kontroler.isGliding() != ofiara.isGliding()) {
            ofiara.setGliding(kontroler.isGliding());
        }
        int slot = kontroler.getInventory().getHeldItemSlot();
        if (ofiara.getInventory().getHeldItemSlot() != slot) {
            ofiara.getInventory().setHeldItemSlot(slot);
        }
    }

    private void ukryjKontrolera(Player kontroler, Player ofiara) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(kontroler)) {
                viewer.hidePlayer(plugin, kontroler);
            }
        }
        kontroler.hidePlayer(plugin, ofiara);
    }

    private void przywrocKontrolera(Player kontroler, Player ofiara, Sesja sesja) {
        kontroler.removePotionEffect(PotionEffectType.INVISIBILITY);
        kontroler.setCollidable(true);
        kontroler.setSilent(false);
        kontroler.setSleepingIgnored(false);
        kontroler.setGameMode(sesja.gmKontrolera);
        kontroler.setAllowFlight(sesja.allowFlight);
        kontroler.setFlying(sesja.flying && sesja.allowFlight);
        wgrajZapisaneEq(kontroler, sesja.eqKontrolera, sesja.slotKontrolera);

        if (sesja.lokacjaPowrotu != null && sesja.lokacjaPowrotu.getWorld() != null) {
            kontroler.teleport(sesja.lokacjaPowrotu);
        }

        boolean vanished = AdminUtilManager.isVanished(kontroler);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(kontroler)) {
                continue;
            }
            if (vanished && !viewer.hasPermission(AdminUtilManager.PERM_VANISH_SEE)) {
                viewer.hidePlayer(plugin, kontroler);
            } else {
                viewer.showPlayer(plugin, kontroler);
            }
        }
        if (ofiara != null && ofiara.isOnline()) {
            kontroler.showPlayer(plugin, ofiara);
        }
    }

    static void kopiujEq(Player z, Player doKogo) {
        PlayerInventory src = z.getInventory();
        PlayerInventory dst = doKogo.getInventory();
        dst.setStorageContents(klon(src.getStorageContents()));
        dst.setArmorContents(klon(src.getArmorContents()));
        dst.setItemInOffHand(klonJeden(src.getItemInOffHand()));
        dst.setHeldItemSlot(src.getHeldItemSlot());
    }

    private static void wgrajZapisaneEq(Player player, ZapisaneEq zapis, int slot) {
        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(klon(zapis.storage));
        inv.setArmorContents(klon(zapis.armor));
        inv.setItemInOffHand(klonJeden(zapis.offhand));
        inv.setHeldItemSlot(slot);
        player.updateInventory();
    }

    private static ItemStack[] klon(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = klonJeden(src[i]);
        }
        return out;
    }

    private static ItemStack klonJeden(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static void actionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(message)));
    }

    // ───────────────────────── eventy ─────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (Sesja sesja : poKontrolerze.values()) {
            Player kontroler = Bukkit.getPlayer(sesja.kontrolerId);
            if (kontroler != null && !kontroler.equals(joined)) {
                joined.hidePlayer(plugin, kontroler);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Sesja jakoKontroler = poKontrolerze.get(player.getUniqueId());
        if (jakoKontroler != null) {
            zakoncz(jakoKontroler, true);
            return;
        }
        Sesja jakoOfiara = poOfierze.get(player.getUniqueId());
        if (jakoOfiara != null) {
            zakoncz(jakoOfiara, true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Sesja sesja = poKontrolerze.get(player.getUniqueId());
        if (sesja == null) {
            sesja = poOfierze.get(player.getUniqueId());
        }
        if (sesja != null) {
            zakoncz(sesja, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Sesja sesja = poOfierze.get(event.getPlayer().getUniqueId());
        if (sesja != null && !sesja.syncing) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Sesja sesja = poOfierze.get(event.getPlayer().getUniqueId());
        if (sesja != null && !sesja.syncing && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetInteract(PlayerInteractEvent event) {
        if (poOfierze.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetDrop(PlayerDropItemEvent event) {
        if (poOfierze.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && poOfierze.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (poOfierze.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Sesja sesja = poKontrolerze.get(player.getUniqueId());
        if (sesja != null) {
            Player ofiara = Bukkit.getPlayer(sesja.ofiaraId);
            if (ofiara != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && ofiara.isOnline() && poKontrolerze.containsKey(player.getUniqueId())) {
                        kopiujEq(player, ofiara);
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && poOfierze.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetSwap(PlayerSwapHandItemsEvent event) {
        if (poOfierze.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetFly(PlayerToggleFlightEvent event) {
        if (poOfierze.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetPlace(BlockPlaceEvent event) {
        if (poOfierze.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTargetBreak(BlockBreakEvent event) {
        if (poOfierze.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTargetCommand(PlayerCommandPreprocessEvent event) {
        if (poOfierze.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&f" + ICON + " &cKimś steruje Twoją postacią."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (poOfierze.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onControllerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && poKontrolerze.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onControllerFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && poKontrolerze.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player damager = damagerGracz(event.getDamager());
        if (damager == null) {
            return;
        }
        if (poOfierze.containsKey(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Sesja sesja = poKontrolerze.get(damager.getUniqueId());
        if (sesja == null) {
            return;
        }
        if (event.getEntity() instanceof Player hit && hit.getUniqueId().equals(sesja.ofiaraId)) {
            event.setCancelled(true);
        }
    }

    private static Player damagerGracz(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        Sesja sesja = poKontrolerze.get(event.getPlayer().getUniqueId());
        if (sesja == null) {
            return;
        }
        Player ofiara = Bukkit.getPlayer(sesja.ofiaraId);
        if (ofiara != null) {
            ofiara.swingMainHand();
        }
    }

    static final class Sesja {
        final UUID kontrolerId;
        final UUID ofiaraId;
        final String ofiaraNick;
        final Location lokacjaPowrotu;
        final GameMode gmKontrolera;
        final boolean allowFlight;
        final boolean flying;
        final ZapisaneEq eqKontrolera;
        final ZapisaneEq eqOfiary;
        final int slotKontrolera;
        final int slotOfiary;
        boolean syncing;

        Sesja(Player kontroler, Player ofiara) {
            this.kontrolerId = kontroler.getUniqueId();
            this.ofiaraId = ofiara.getUniqueId();
            this.ofiaraNick = ofiara.getName();
            this.lokacjaPowrotu = kontroler.getLocation().clone();
            this.gmKontrolera = kontroler.getGameMode();
            this.allowFlight = kontroler.getAllowFlight();
            this.flying = kontroler.isFlying();
            this.eqKontrolera = ZapisaneEq.z(kontroler);
            this.eqOfiary = ZapisaneEq.z(ofiara);
            this.slotKontrolera = kontroler.getInventory().getHeldItemSlot();
            this.slotOfiary = ofiara.getInventory().getHeldItemSlot();
        }
    }

    private static final class ZapisaneEq {
        final ItemStack[] storage;
        final ItemStack[] armor;
        final ItemStack offhand;

        private ZapisaneEq(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
            this.storage = storage;
            this.armor = armor;
            this.offhand = offhand;
        }

        static ZapisaneEq z(Player player) {
            PlayerInventory inv = player.getInventory();
            return new ZapisaneEq(klon(inv.getStorageContents()), klon(inv.getArmorContents()), klonJeden(inv.getItemInOffHand()));
        }
    }
}
