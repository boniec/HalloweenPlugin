package boniec.bezelek;

import boniec.code.core.WorldGuardHook;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zakaz elytr na regionie WorldGuard (domyslnie "bezelek", patrz bezelek.yml).
 * Proba uzycia elytry (start lotu) = elytra zostaje zdjeta z gracza, a na itemie pojawia sie
 * wizualny cooldown (domyslnie 1 s). Gracz, ktory wleci na region juz lecac, jest sprowadzany w ten sam sposob.
 * <p>
 * Klasa odwoluje sie do WorldGuardHook - tworz ja TYLKO gdy WorldGuard jest zainstalowany.
 */
public class BezelekElytraListener implements Listener {

    private final JavaPlugin plugin;
    private String region;
    private int cooldownTicks;
    private String wiadomosc;
    private String dzwiek;
    private String titleWejscie;
    private String subtitleWejscie;
    private String titleWyjscie;
    private String subtitleWyjscie;
    private BukkitTask zadanie;
    private BukkitTask titleTask;
    private final Set<UUID> wRegionie = ConcurrentHashMap.newKeySet();

    public BezelekElytraListener(JavaPlugin plugin) {
        this.plugin = plugin;
        przeladuj();
    }

    public void przeladuj() {
        File plik = new File(plugin.getDataFolder(), "bezelek.yml");
        if (!plik.exists()) plugin.saveResource("bezelek.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(plik);
        region = cfg.getString("region", "bezelek");
        cooldownTicks = Math.max(0, cfg.getInt("cooldown-ticks", 20));
        wiadomosc = cfg.getString("wiadomosc", "&cNa tym terenie nie mozna uzywac elytr!");
        dzwiek = cfg.getString("dzwiek", "entity.villager.no");
        titleWejscie = cfg.getString("title.wejscie",
                "&#660404&ls&#760707&lᴛ&#860B0B&lʀ&#960E0E&lᴇ&#A61111&lꜰ&#B51414&lᴀ &#D51B1B&lʙ&#E51E1E&lᴇ&#D31A1A&lᴢ &#AF1313&lᴇ&#9C0F0F&lʟ&#8A0B0B&lʏ&#780808&lᴛ&#660404&lʀ");
        subtitleWejscie = cfg.getString("title.subtitle-wejscie", "&aWszedles");
        titleWyjscie = cfg.getString("title.wyjscie", titleWejscie);
        subtitleWyjscie = cfg.getString("title.subtitle-wyjscie", "&cWyszedles");
    }

    /** Uruchamia zadanie lapiace graczy, ktorzy WLECIELI na region (start lotu lapie zdarzenie). */
    public void start() {
        stop();
        zadanie = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.isGliding()) continue;
                if (p.hasPermission("bezelek.elytra.bypass")) continue;
                if (!wBezelku(p)) continue;
                p.setGliding(false);
                sciagnijElytre(p);
            }
        }, 2L, 2L);
        titleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sprawdzWejsciaWyjscia, 10L, 10L);
    }

    public void stop() {
        if (zadanie != null) {
            zadanie.cancel();
            zadanie = null;
        }
        if (titleTask != null) {
            titleTask.cancel();
            titleTask = null;
        }
        wRegionie.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        wRegionie.remove(e.getPlayer().getUniqueId());
    }

    private void sprawdzWejsciaWyjscia() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean teraz = wBezelku(p);
            boolean bylo = wRegionie.contains(id);
            if (teraz && !bylo) {
                wRegionie.add(id);
                p.sendTitle(Msg.color(titleWejscie), Msg.color(subtitleWejscie), 10, 40, 10);
            } else if (!teraz && bylo) {
                wRegionie.remove(id);
                p.sendTitle(Msg.color(titleWyjscie), Msg.color(subtitleWyjscie), 10, 40, 10);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!e.isGliding()) return; // interesuje nas tylko START lotu
        if (p.hasPermission("bezelek.elytra.bypass")) return;
        if (!wBezelku(p)) return;

        e.setCancelled(true);
        sciagnijElytre(p);
    }

    private boolean wBezelku(Player p) {
        return WorldGuardHook.isInRegion(p.getLocation(), region);
    }

    private void sciagnijElytre(Player p) {
        boolean pierwszyRaz = !p.hasCooldown(Material.ELYTRA);

        PlayerInventory inv = p.getInventory();
        ItemStack klatka = inv.getChestplate();
        if (klatka != null && klatka.getType() == Material.ELYTRA) {
            inv.setChestplate(null);
            for (ItemStack reszta : inv.addItem(klatka).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), reszta);
            }
            p.updateInventory();
        }

        // Wizualny cooldown na elytrze (szara nakladka na itemie w ekwipunku/hotbarze).
        if (cooldownTicks > 0) {
            p.setCooldown(Material.ELYTRA, cooldownTicks);
        }

        // Wiadomosc/dzwiek tylko raz na cooldown, zeby nie spamowac przy ponawianych probach.
        if (pierwszyRaz) {
            if (wiadomosc != null && !wiadomosc.isBlank()) p.sendMessage(Msg.color(wiadomosc));
            if (dzwiek != null && !dzwiek.isBlank()) p.playSound(p.getLocation(), dzwiek, 1.0f, 1.0f);
        }
    }
}
