package boniec.code.anticheat;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Elytratarget implements Listener {

    private final Set<UUID> elytraBlock = new HashSet<>();
    private final Map<UUID, Long> lastSwitch = new HashMap<>();
    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {

        lastSwitch.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSwitch.remove(event.getPlayer().getUniqueId());
        elytraBlock.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!elytraBlock.contains(player.getUniqueId())) return;

        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() == Material.ELYTRA) {
                event.setCancelled(true);
            }
        }

        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() == Material.ELYTRA) {
                if (player.getInventory().getChestplate() == null || player.getInventory().getChestplate().getType() == Material.AIR) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (elytraBlock.contains(player.getUniqueId()) && event.getItem() != null && event.getItem().getType() == Material.ELYTRA) {
            if (event.getAction().name().contains("RIGHT")) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getItem() == null) return;
        if (event.getItem().getType() != Material.FIREWORK_ROCKET) return;

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', "&4&l❌"),
                    ChatColor.translateAlternateColorCodes('&', "&cɴɪᴇ ᴍᴏᴢᴇsᴢ ᴛᴇɢᴏ ᴢʀᴏʙɪᴄ"),
                    10, 40, 10
            );
            return;
        }

        if (event.getHand() == EquipmentSlot.HAND) {
            Material held = player.getInventory().getItemInMainHand().getType();

            if (held != Material.FIREWORK_ROCKET && held != Material.AIR) {
                event.setCancelled(true);
                punishPlayer(player);
                return;
            }

            long last = lastSwitch.getOrDefault(player.getUniqueId(), 0L);
            if (System.currentTimeMillis() - last < 150) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) return;
                        Material current = player.getInventory().getItemInMainHand().getType();
                        // Jeśli w następnym ticku gracz już nie trzyma fajerwerki (i nie zużył jej do zera)
                        if (current != Material.FIREWORK_ROCKET && current != Material.AIR) {
                            punishPlayer(player);
                        }
                    }
                }.runTaskLater(JavaPlugin.getProvidingPlugin(Elytratarget.class), 1L);
            }
        }
    }

    private void punishPlayer(Player player) {
        if (elytraBlock.contains(player.getUniqueId())) return;

        ItemStack chest = player.getInventory().getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA) {
            player.getInventory().setChestplate(null);
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(chest);
            if (!left.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left.get(0));
            }
        }

        elytraBlock.add(player.getUniqueId());

        new BukkitRunnable() {
            @Override
            public void run() {
                elytraBlock.remove(player.getUniqueId());
            }
        }.runTaskLater(JavaPlugin.getProvidingPlugin(Elytratarget.class), 20L);
    }
}
