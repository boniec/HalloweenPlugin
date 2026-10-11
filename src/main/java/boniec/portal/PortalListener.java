package boniec.portal;

import boniec.klany.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalListener implements Listener {

    private final Plugin plugin;
    private final PortalManager manager;
    private final NamespacedKey wandKey;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public PortalListener(Plugin plugin, PortalManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.wandKey = new NamespacedKey(plugin, "aportal_wand");
    }

    public ItemStack stworzWand() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color("&f丆 &#FFC34D&lᴡᴀɴᴅ ᴘᴏʀᴛᴀʟɪ"));
            meta.setLore(List.of(
                    Msg.color(""),
                    Msg.color("&7  丶 ʟᴘᴍ — ᴘɪᴇʀᴡꜱᴢʏ ʀᴏɢ"),
                    Msg.color("&7  丶 ᴘᴘᴍ — ᴅʀᴜɢɪ ʀᴏɢ"),
                    Msg.color("&f 乁 /ᴀᴘᴏʀᴛᴀʟ ꜱᴛᴡᴏʀᴢ <ɴᴀᴢᴡᴀ>"),
                    Msg.color("")));
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isWand(player.getInventory().getItemInMainHand())) return;
        if (!player.hasPermission("portal.admin")) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        event.setCancelled(true);

        PortalManager.Zaznaczenie z = manager.zaznaczenie(player.getUniqueId());
        Location loc = block.getLocation();
        if (action == Action.LEFT_CLICK_BLOCK) {
            z.pos1 = loc;
            player.sendMessage(Msg.color("&8[&f丆&8] &f両 &aᴘᴏꜱ 1: &f" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
        } else {
            z.pos2 = loc;
            player.sendMessage(Msg.color("&8[&f丆&8] &f両 &aᴘᴏꜱ 2: &f" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWandBreak(BlockBreakEvent event) {
        if (isWand(event.getPlayer().getInventory().getItemInMainHand())
                && event.getPlayer().hasPermission("portal.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }
        Player player = event.getPlayer();
        long teraz = System.currentTimeMillis();
        Long cd = cooldown.get(player.getUniqueId());
        if (cd != null && teraz < cd) return;

        Portal portal = manager.portalNa(to);
        if (portal == null) return;
        if (portal.zawiera(from)) return;

        Location cel = portal.cel();
        if (cel == null || cel.getWorld() == null) return;
        if (cel.getWorld().getEnvironment() == World.Environment.NETHER) return;

        cooldown.put(player.getUniqueId(), teraz + 1500L);
        player.teleport(cel, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
}
