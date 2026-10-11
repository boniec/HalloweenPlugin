package boniec.adminutil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InvSeeCommand implements TabExecutor, Listener {

    private static final String ICON = "亢";
    private static final int INV_SIZE = 54;
    private static final int ENDER_SIZE = 27;

    /** GUI 0-26 = storage 9-35, 27-35 = hotbar, 36 helm, 37 klata, 38 spodnie, 39 buty, 40 offhand. */
    private static final int GUI_HELM = 36;
    private static final int GUI_CHEST = 37;
    private static final int GUI_LEGS = 38;
    private static final int GUI_BOOTS = 39;
    private static final int GUI_OFF = 40;

    private final JavaPlugin plugin;
    private final NamespacedKey lockKey;
    private final Set<Holder> otwarte = ConcurrentHashMap.newKeySet();

    public InvSeeCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lockKey = new NamespacedKey(plugin, "invsee_lock");
    }

    public void register() {
        for (String name : List.of("invsee", "endersee")) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                plugin.getLogger().warning("Brak komendy /" + name + " w plugin.yml");
                continue;
            }
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::odswiezOtwarte, 4L, 4L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(color("&f" + ICON + " &cTylko gracz."));
            return true;
        }
        boolean ender = command.getName().equalsIgnoreCase("endersee");
        String perm = ender ? "core.endersee" : "core.invsee";
        if (!viewer.hasPermission(perm)) {
            viewer.sendMessage(color("&f" + ICON + " &cNie masz uprawnień!"));
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(color("&f" + ICON + " &cUżycie: &f/" + label + " <gracz>"));
            return true;
        }

        OfflinePlayer target = znajdz(args[0]);
        if (target == null || target.getUniqueId() == null) {
            viewer.sendMessage(color("&f" + ICON + " &cNie znaleziono gracza &f" + args[0] + "&c."));
            return true;
        }
        String nick = target.getName() != null ? target.getName() : args[0];
        Player online = target.isOnline() ? target.getPlayer() : Bukkit.getPlayer(target.getUniqueId());

        if (ender) {
            otworzEnder(viewer, target.getUniqueId(), nick, online);
        } else {
            otworzInv(viewer, target.getUniqueId(), nick, online);
        }
        return true;
    }

    private void otworzInv(Player viewer, UUID uuid, String nick, Player online) {
        ItemStack[] contents;
        Object nbt = null;
        if (online != null) {
            contents = kopiaEq(online.getInventory());
        } else {
            nbt = OfflinePlayerNbt.loadRoot(uuid);
            if (nbt == null) {
                viewer.sendMessage(color("&f" + ICON + " &cBrak danych gracza (nigdy nie wszedł / brak .dat)."));
                return;
            }
            contents = OfflinePlayerNbt.readInventory(nbt);
        }

        Holder holder = new Holder(uuid, nick, false, nbt);
        Inventory gui = Bukkit.createInventory(holder, INV_SIZE, color("&8» &f" + nick + " &8· &7ekwipunek"));
        holder.gui = gui;

        wstawEqDoGui(gui, contents);

        wypelnijBlokady(gui);
        otwarte.add(holder);
        viewer.openInventory(gui);
        if (online == null) {
            viewer.sendMessage(color("&f临 &7Gracz &f" + nick + " &7jest offline — zmiany zapiszą się do pliku."));
        }
    }

    private void otworzEnder(Player viewer, UUID uuid, String nick, Player online) {
        ItemStack[] contents;
        Object nbt = null;
        if (online != null) {
            contents = online.getEnderChest().getContents();
        } else {
            nbt = OfflinePlayerNbt.loadRoot(uuid);
            if (nbt == null) {
                viewer.sendMessage(color("&f" + ICON + " &cBrak danych gracza (nigdy nie wszedł / brak .dat)."));
                return;
            }
            contents = OfflinePlayerNbt.readEnder(nbt);
        }
        Holder holder = new Holder(uuid, nick, true, nbt);
        Inventory gui = Bukkit.createInventory(holder, ENDER_SIZE, color("&8» &f" + nick + " &8· &7enderchest"));
        holder.gui = gui;
        for (int i = 0; i < ENDER_SIZE && i < contents.length; i++) {
            gui.setItem(i, cloneOf(contents[i]));
        }
        otwarte.add(holder);
        viewer.openInventory(gui);
        if (online == null) {
            viewer.sendMessage(color("&f临 &7Gracz &f" + nick + " &7jest offline — zmiany zapiszą się do pliku."));
        }
    }

    private void wypelnijBlokady(Inventory gui) {
        String[] etykiety = {
                "&7Hełm", "&7Klata", "&7Spodnie", "&7Buty", "&7Druga ręka"
        };
        for (int i = 0; i < 5; i++) {
            gui.setItem(45 + i, klotka(Material.GRAY_STAINED_GLASS_PANE, etykiety[i]));
        }
        for (int slot = 41; slot <= 53; slot++) {
            if (gui.getItem(slot) != null) continue;
            gui.setItem(slot, klotka(Material.BLACK_STAINED_GLASS_PANE, "&8 "));
        }
    }

    private ItemStack klotka(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(lockKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean zablokowany(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(lockKey, PersistentDataType.BYTE);
    }

    private boolean slotZablokowany(Holder holder, int rawSlot) {
        if (holder.ender) return false;
        if (rawSlot < 0 || rawSlot >= INV_SIZE) return false;
        return rawSlot >= 41;
    }

    private void wstawEqDoGui(Inventory gui, ItemStack[] contents) {
        ItemStack[] storage = slice(contents, 9, 27);
        for (int i = 0; i < 27; i++) gui.setItem(i, cloneOf(storage[i]));
        for (int i = 0; i < 9; i++) gui.setItem(27 + i, cloneOf(contents[i]));
        gui.setItem(GUI_HELM, cloneOf(contents.length > 39 ? contents[39] : null));
        gui.setItem(GUI_CHEST, cloneOf(contents.length > 38 ? contents[38] : null));
        gui.setItem(GUI_LEGS, cloneOf(contents.length > 37 ? contents[37] : null));
        gui.setItem(GUI_BOOTS, cloneOf(contents.length > 36 ? contents[36] : null));
        gui.setItem(GUI_OFF, cloneOf(contents.length > 40 ? contents[40] : null));
    }

    private void odswiezOtwarte() {
        for (Holder holder : otwarte) {
            odswiezZGracza(holder);
        }
    }

    private void odswiezZGracza(Holder holder) {
        if (holder.gui == null || holder.nbt != null && Bukkit.getPlayer(holder.uuid) == null) return;
        if (kursorZajety(holder)) return;
        Player online = Bukkit.getPlayer(holder.uuid);
        if (online == null || !online.isOnline()) return;
        if (holder.ender) {
            ItemStack[] contents = online.getEnderChest().getContents();
            for (int i = 0; i < ENDER_SIZE; i++) {
                holder.gui.setItem(i, i < contents.length ? cloneOf(contents[i]) : null);
            }
            return;
        }
        wstawEqDoGui(holder.gui, kopiaEq(online.getInventory()));
        wypelnijBlokady(holder.gui);
    }

    private static boolean kursorZajety(Holder holder) {
        if (holder.gui == null) return false;
        for (HumanEntity v : holder.gui.getViewers()) {
            ItemStack cursor = v.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir()) return true;
        }
        return false;
    }

    void zapisz(Holder holder) {
        if (holder.gui == null) return;
        Player online = Bukkit.getPlayer(holder.uuid);
        if (holder.ender) {
            ItemStack[] ender = new ItemStack[27];
            for (int i = 0; i < 27; i++) ender[i] = cloneOf(holder.gui.getItem(i));
            if (online != null) {
                online.getEnderChest().setContents(ender);
            } else if (holder.nbt != null) {
                OfflinePlayerNbt.writeEnder(holder.nbt, ender);
                OfflinePlayerNbt.saveRoot(holder.uuid, holder.nbt);
            }
            return;
        }

        ItemStack[] contents = new ItemStack[41];
        for (int i = 0; i < 27; i++) contents[9 + i] = cloneOf(holder.gui.getItem(i));
        for (int i = 0; i < 9; i++) contents[i] = cloneOf(holder.gui.getItem(27 + i));
        contents[39] = cloneOf(holder.gui.getItem(GUI_HELM));
        contents[38] = cloneOf(holder.gui.getItem(GUI_CHEST));
        contents[37] = cloneOf(holder.gui.getItem(GUI_LEGS));
        contents[36] = cloneOf(holder.gui.getItem(GUI_BOOTS));
        contents[40] = cloneOf(holder.gui.getItem(GUI_OFF));

        if (online != null) {
            nanieśNaGracza(online.getInventory(), contents);
        } else if (holder.nbt != null) {
            OfflinePlayerNbt.writeInventory(holder.nbt, contents);
            OfflinePlayerNbt.saveRoot(holder.uuid, holder.nbt);
        }
    }

    private static void nanieśNaGracza(PlayerInventory inv, ItemStack[] contents) {
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, contents[i]);
        }
        inv.setBoots(contents[36]);
        inv.setLeggings(contents[37]);
        inv.setChestplate(contents[38]);
        inv.setHelmet(contents[39]);
        inv.setItemInOffHand(contents[40]);
    }

    private static ItemStack[] kopiaEq(PlayerInventory inv) {
        ItemStack[] out = new ItemStack[41];
        ItemStack[] storage = inv.getContents();
        for (int i = 0; i < 36 && i < storage.length; i++) {
            out[i] = cloneOf(storage[i]);
        }
        out[36] = cloneOf(inv.getBoots());
        out[37] = cloneOf(inv.getLeggings());
        out[38] = cloneOf(inv.getChestplate());
        out[39] = cloneOf(inv.getHelmet());
        out[40] = cloneOf(inv.getItemInOffHand());
        return out;
    }

    private static ItemStack[] slice(ItemStack[] src, int from, int len) {
        ItemStack[] out = new ItemStack[len];
        if (src == null) return out;
        for (int i = 0; i < len; i++) {
            int idx = from + i;
            if (idx < src.length) out[i] = src[idx];
        }
        return out;
    }

    private static ItemStack cloneOf(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static OfflinePlayer znajdz(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) return cached;
        } catch (Throwable ignored) {
        }
        String want = name.toLowerCase(Locale.ROOT);
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(want)) return op;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        int raw = event.getRawSlot();
        if (raw < event.getView().getTopInventory().getSize()) {
            ItemStack current = event.getCurrentItem();
            if (slotZablokowany(holder, raw) || zablokowany(current)) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> zapisz(holder));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        int top = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < top && (slotZablokowany(holder, raw) || zablokowany(event.getView().getTopInventory().getItem(raw)))) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> zapisz(holder));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        otwarte.remove(holder);
        zapisz(holder);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetDrop(PlayerDropItemEvent event) {
        odswiezCel(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p) odswiezCel(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetSwap(PlayerSwapHandItemsEvent event) {
        odswiezCel(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetInvClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p
                && !(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            odswiezCel(p.getUniqueId());
        }
    }

    private void odswiezCel(UUID uuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Holder holder : otwarte) {
                if (holder.uuid.equals(uuid)) odswiezZGracza(holder);
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        String perm = command.getName().equalsIgnoreCase("endersee") ? "core.endersee" : "core.invsee";
        if (!sender.hasPermission(perm)) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        }
        if (out.size() < 40) {
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() == null) continue;
                if (!op.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                if (!out.contains(op.getName())) out.add(op.getName());
                if (out.size() >= 40) break;
            }
        }
        return out;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    static final class Holder implements InventoryHolder {
        final UUID uuid;
        final String name;
        final boolean ender;
        final Object nbt;
        Inventory gui;

        Holder(UUID uuid, String name, boolean ender, Object nbt) {
            this.uuid = uuid;
            this.name = name;
            this.ender = ender;
            this.nbt = nbt;
        }

        @Override
        public Inventory getInventory() {
            return gui;
        }
    }
}
