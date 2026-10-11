package boniec.dev.polezamek.eventitems;

import boniec.adminutil.AdminUtilManager;
import boniec.dev.polezamek.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kazda koza to osobna walka 1v1: wlasny border, niewidocznosc wobec reszty
 * (w tym innych koz) i brak obrazen spoza pary. Koniec jednej areny nie odslania drugiej.
 */
public final class KozaArenaManager implements Listener {

    private final Main plugin;
    private final EventItemsManager items;
    private final JavaPlugin host;
    private final Map<UUID, Walka> walki = new ConcurrentHashMap<>();

    public KozaArenaManager(Main plugin, EventItemsManager items) {
        this.plugin = plugin;
        this.items = items;
        this.host = plugin.getHost();
    }

    public boolean wArenie(UUID uuid) {
        return walki.containsKey(uuid);
    }

    @EventHandler
    public void onKozaUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player player = event.getPlayer();
        if (!items.isKoza(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        if (target.equals(player) || target.hasMetadata("NPC")) return;
        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (items.isKozaOnCooldown(uuid)) {
            player.sendMessage(color(items.getMsgKozaCooldown(items.getKozaCooldownRemaining(uuid))));
            return;
        }
        if (wArenie(uuid) || wArenie(target.getUniqueId())
                || boniec.dev.polezamek.DuelManager.blokujeSpawn(uuid)
                || boniec.dev.polezamek.DuelManager.blokujeSpawn(target.getUniqueId())) {
            player.sendMessage(color(items.getMsgKozaBusy()));
            return;
        }

        int size = items.getKozaWorldBorderSize();
        if (!player.getWorld().equals(target.getWorld())
                || player.getLocation().distance(target.getLocation()) > size - 2) {
            player.sendMessage(color(items.getMsgKozaTooFar()));
            return;
        }

        if (items.isKozaCelOnCooldown(target.getUniqueId())) {
            player.sendMessage(color(items.getMsgKozaAlreadyUsed(items.getKozaCelCooldownRemaining(target.getUniqueId()))));
            return;
        }
        items.setKozaCelCooldown(target.getUniqueId());
        items.setKozaCooldown(uuid);

        int seconds = items.getKozaWorldBorderDuration();
        if (!startArena(player, target, size, seconds)) return;

        player.sendMessage(color(items.getMsgKozaActivated(target.getName(), seconds)));
        target.sendMessage(color(items.getMsgKozaActivated(player.getName(), seconds)));
        player.playSound(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1f);
        target.playSound(target.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1f);
        player.setCooldown(EventItemsManager.KOZA_MATERIAL, items.getKozaCooldownSeconds() * 20);
    }

    /** Arena 1v1 (koza / duel). seconds <= 0 = aż do śmierci. */
    public boolean startArena(Player player, Player target, int size, int seconds) {
        if (player == null || target == null || player.equals(target)) return false;
        if (wArenie(player.getUniqueId()) || wArenie(target.getUniqueId())) return false;
        if (!player.getWorld().equals(target.getWorld())
                || player.getLocation().distance(target.getLocation()) > Math.max(4, size - 2)) {
            return false;
        }
        int ticks = Math.max(0, seconds) * 20;
        Location center = player.getLocation().clone().add(target.getLocation()).multiply(0.5);

        Walka walka = new Walka(player.getUniqueId(), target.getUniqueId());
        walka.center = center.clone();
        walka.logicznaSrednica = size;
        walki.put(player.getUniqueId(), walka);
        walki.put(target.getUniqueId(), walka);

        double borderDiameter = size + items.getKozaWorldBorderPlaceExtra();
        for (Player p : new Player[]{player, target}) {
            WorldBorder border = Bukkit.createWorldBorder();
            border.setCenter(center);
            border.setSize(borderDiameter);
            border.setWarningDistance(0);
            border.setDamageAmount(0);
            border.setDamageBuffer(0);
            p.setWorldBorder(border);
            if (ticks > 0) p.setCooldown(Material.ELYTRA, ticks);
            if (p.isGliding()) {
                p.setGliding(false);
                removeElytra(p);
            }
        }
        ukryjPare(player, target);
        if (ticks > 0) {
            walka.task = Bukkit.getScheduler().runTaskLater(host, () -> zakoncz(walka), ticks);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKozaDamage(EntityDamageByEntityEvent event) {
        Player ofiara = event.getEntity() instanceof Player p ? p : null;
        Player atak = atakujacy(event);
        if (ofiara == null || atak == null || ofiara.equals(atak)) return;
        if (!taSamaWalka(atak.getUniqueId(), ofiara.getUniqueId())) {
            if (wArenie(atak.getUniqueId()) || wArenie(ofiara.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKozaPotion(PotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player strzelec)) return;
        Walka walka = walki.get(strzelec.getUniqueId());
        if (walka == null) {
            event.getAffectedEntities().removeIf(e -> e instanceof Player p && wArenie(p.getUniqueId()));
            return;
        }
        event.getAffectedEntities().removeIf(e -> {
            if (!(e instanceof Player p)) return false;
            return !walka.uczestnik(p.getUniqueId());
        });
    }

    /**
     * Zamek/pole i inne pluginy moga blokowac stawianie w promieniu 25 blokow od ostatniego uzycia.
     * Na kozie gracze maja stawiac do krawedzi areny (worldborder-size), wiec pomijamy te blokady.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onKozaPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) return;
        Walka walka = walki.get(event.getPlayer().getUniqueId());
        if (walka == null || !walka.aktywna) return;
        if (!wGranicachAreny(walka, event.getBlock())) return;
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onKozaBucket(PlayerBucketEmptyEvent event) {
        if (!event.isCancelled()) return;
        Walka walka = walki.get(event.getPlayer().getUniqueId());
        if (walka == null || !walka.aktywna) return;
        if (!wGranicachAreny(walka, event.getBlock())) return;
        event.setCancelled(false);
    }

    /** Czy blok miesci sie w kwadracie worldborder-size (nie w powiekszonym borderze vanilla). */
    private static boolean wGranicachAreny(Walka walka, Block block) {
        if (walka.center == null || walka.logicznaSrednica <= 0 || block.getWorld() == null) return false;
        if (!block.getWorld().equals(walka.center.getWorld())) return false;
        double pol = walka.logicznaSrednica / 2.0;
        double dx = Math.abs(block.getX() + 0.5 - walka.center.getX());
        double dz = Math.abs(block.getZ() + 0.5 - walka.center.getZ());
        return dx <= pol && dz <= pol;
    }

    @EventHandler
    public void onKozaGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!event.isGliding() || !wArenie(p.getUniqueId())) return;
        event.setCancelled(true);
        removeElytra(p);
        p.sendMessage(color(items.getMsgKozaElytra()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKozaDeath(PlayerDeathEvent event) {
        Walka walka = walki.get(event.getEntity().getUniqueId());
        if (walka != null) zakoncz(walka);
    }

    @EventHandler
    public void onKozaQuit(PlayerQuitEvent event) {
        Walka walka = walki.get(event.getPlayer().getUniqueId());
        if (walka != null) zakoncz(walka);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKozaJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        Bukkit.getScheduler().runTask(host, () -> {
            if (!joined.isOnline() || walki.isEmpty()) return;
            for (UUID id : walki.keySet()) {
                Player wArenie = Bukkit.getPlayer(id);
                if (wArenie == null || wArenie.equals(joined) || wArenie.hasMetadata("NPC")) continue;
                joined.hidePlayer(host, wArenie);
                wArenie.hidePlayer(host, joined);
            }
        });
    }

    private void zakoncz(Walka walka) {
        if (walka == null || !walka.aktywna) return;
        walka.aktywna = false;
        if (walka.task != null) {
            walka.task.cancel();
            walka.task = null;
        }
        walki.remove(walka.a);
        walki.remove(walka.b);
        Player pa = Bukkit.getPlayer(walka.a);
        Player pb = Bukkit.getPlayer(walka.b);
        if (pa != null && pa.isOnline()) {
            pa.setWorldBorder(null);
            przywrocGracza(pa);
        }
        if (pb != null && pb.isOnline()) {
            pb.setWorldBorder(null);
            przywrocGracza(pb);
        }
    }

    /** Ukrywa pare przed reszta serwera i przed innymi kozami. Nie rusza widocznosci reszty graczy miedzy soba. */
    private void ukryjPare(Player a, Player b) {
        a.showPlayer(host, b);
        b.showPlayer(host, a);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(a) || o.equals(b) || o.hasMetadata("NPC")) continue;
            a.hidePlayer(host, o);
            o.hidePlayer(host, a);
            b.hidePlayer(host, o);
            o.hidePlayer(host, b);
        }
    }

    private void przywrocGracza(Player p) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || o.hasMetadata("NPC")) continue;
            if (wArenie(o.getUniqueId())) {
                p.hidePlayer(host, o);
                o.hidePlayer(host, p);
                continue;
            }
            if (ukrytyVanish(p, o)) {
                p.hidePlayer(host, o);
            } else {
                p.showPlayer(host, o);
            }
            if (ukrytyVanish(o, p)) {
                o.hidePlayer(host, p);
            } else {
                o.showPlayer(host, p);
            }
        }
    }

    private static boolean ukrytyVanish(Player viewer, Player other) {
        if (!AdminUtilManager.isVanished(other)) return false;
        return !viewer.hasPermission(AdminUtilManager.PERM_VANISH_SEE);
    }

    private boolean taSamaWalka(UUID a, UUID b) {
        Walka wa = walki.get(a);
        Walka wb = walki.get(b);
        return wa != null && wa == wb;
    }

    private static Player atakujacy(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private static void removeElytra(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack chest = inv.getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) return;
        inv.setChestplate(null);
        for (ItemStack rest : inv.addItem(chest).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        p.updateInventory();
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static final class Walka {
        final UUID a;
        final UUID b;
        volatile boolean aktywna = true;
        Location center;
        int logicznaSrednica;
        BukkitTask task;

        Walka(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        boolean uczestnik(UUID uuid) {
            return a.equals(uuid) || b.equals(uuid);
        }
    }
}
