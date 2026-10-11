package boniec.adminutil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Odczyt/zapis Inventory i EnderItems z playerdata/*.dat (gracze offline).
 * Paper 1.21, Mojang mappings, przez refleksję — bez paperweight.
 */
final class OfflinePlayerNbt {

    private static final int TAG_COMPOUND = 10;

    private OfflinePlayerNbt() {}

    static File playerDat(UUID uuid) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) return null;
        return new File(world.getWorldFolder(), "playerdata" + File.separator + uuid + ".dat");
    }

    static Object loadRoot(UUID uuid) {
        File file = playerDat(uuid);
        if (file == null || !file.isFile()) return null;
        try {
            return readCompressed(file.toPath());
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[InvSee] Nie udalo sie wczytac " + file.getName(), t);
            return null;
        }
    }

    static boolean saveRoot(UUID uuid, Object root) {
        File file = playerDat(uuid);
        if (file == null || root == null) return false;
        try {
            writeCompressed(root, file.toPath());
            return true;
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[InvSee] Nie udalo sie zapisac " + file.getName(), t);
            return false;
        }
    }

    static ItemStack[] readInventory(Object root) {
        ItemStack[] out = new ItemStack[41];
        Object list = getList(root, "Inventory", TAG_COMPOUND);
        if (list == null) return out;
        int size = listSize(list);
        for (int i = 0; i < size; i++) {
            Object tag = listCompound(list, i);
            if (tag == null) continue;
            int idx = nbtSlotToIndex(getByte(tag, "Slot"));
            ItemStack item = itemFromTag(copyWithoutSlot(tag));
            if (item == null || item.getType().isAir()) continue;
            if (idx >= 0 && idx < out.length) out[idx] = item;
        }
        return out;
    }

    static ItemStack[] readEnder(Object root) {
        ItemStack[] out = new ItemStack[27];
        Object list = getList(root, "EnderItems", TAG_COMPOUND);
        if (list == null) return out;
        int size = listSize(list);
        for (int i = 0; i < size; i++) {
            Object tag = listCompound(list, i);
            if (tag == null) continue;
            int slot = getByte(tag, "Slot") & 0xFF;
            ItemStack item = itemFromTag(copyWithoutSlot(tag));
            if (item == null || item.getType().isAir()) continue;
            if (slot >= 0 && slot < out.length) out[slot] = item;
        }
        return out;
    }

    static void writeInventory(Object root, ItemStack[] contents) {
        Object list = newList();
        if (contents != null) {
            for (int i = 0; i < contents.length && i < 41; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getType().isAir()) continue;
                Object tag = itemToTag(stack);
                if (tag == null) continue;
                putByte(tag, "Slot", (byte) indexToNbtSlot(i));
                listAdd(list, tag);
            }
        }
        put(root, "Inventory", list);
    }

    static void writeEnder(Object root, ItemStack[] contents) {
        Object list = newList();
        if (contents != null) {
            for (int i = 0; i < contents.length && i < 27; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getType().isAir()) continue;
                Object tag = itemToTag(stack);
                if (tag == null) continue;
                putByte(tag, "Slot", (byte) i);
                listAdd(list, tag);
            }
        }
        put(root, "EnderItems", list);
    }

    /** Bukkit: 0-35 eq, 36 buty, 37 spodnie, 38 klata, 39 helm, 40 offhand. */
    private static int nbtSlotToIndex(int slot) {
        if (slot >= 0 && slot <= 35) return slot;
        if (slot == 100) return 36;
        if (slot == 101) return 37;
        if (slot == 102) return 38;
        if (slot == 103) return 39;
        if (slot == -106) return 40;
        return -1;
    }

    private static int indexToNbtSlot(int index) {
        if (index >= 0 && index <= 35) return index;
        if (index == 36) return 100;
        if (index == 37) return 101;
        if (index == 38) return 102;
        if (index == 39) return 103;
        if (index == 40) return -106;
        return index;
    }

    private static Object readCompressed(Path path) throws Exception {
        Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo");
        Class<?> acc = Class.forName("net.minecraft.nbt.NbtAccounter");
        Object unlimited = acc.getMethod("unlimitedHeap").invoke(null);
        return nbtIo.getMethod("readCompressed", Path.class, acc).invoke(null, path, unlimited);
    }

    private static void writeCompressed(Object root, Path path) throws Exception {
        Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo");
        Class<?> compound = Class.forName("net.minecraft.nbt.CompoundTag");
        nbtIo.getMethod("writeCompressed", compound, Path.class).invoke(null, root, path);
    }

    private static Object registries() throws Exception {
        Object server = Bukkit.getServer();
        Object mc = server.getClass().getMethod("getServer").invoke(server);
        return mc.getClass().getMethod("registryAccess").invoke(mc);
    }

    private static ItemStack itemFromTag(Object tag) {
        if (tag == null) return null;
        try {
            Class<?> nmsStack = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> provider = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Class<?> compound = Class.forName("net.minecraft.nbt.CompoundTag");
            Method parse = nmsStack.getMethod("parseOptional", provider, compound);
            Object nms = parse.invoke(null, registries(), tag);
            if (nms == null) return null;
            Boolean empty = (Boolean) nmsStack.getMethod("isEmpty").invoke(nms);
            if (Boolean.TRUE.equals(empty)) return new ItemStack(Material.AIR);
            Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            return (ItemStack) craft.getMethod("asBukkitCopy", nmsStack).invoke(null, nms);
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[InvSee] parse item", t);
            return null;
        }
    }

    private static Object itemToTag(ItemStack bukkit) {
        try {
            Class<?> nmsStack = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Object nms = craft.getMethod("asNMSCopy", ItemStack.class).invoke(null, bukkit);
            if (nms == null) return null;
            Boolean empty = (Boolean) nmsStack.getMethod("isEmpty").invoke(nms);
            if (Boolean.TRUE.equals(empty)) return null;
            Class<?> provider = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Object saved = nmsStack.getMethod("save", provider).invoke(nms, registries());
            return saved;
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[InvSee] save item", t);
            return null;
        }
    }

    private static Object getList(Object compound, String key, int type) {
        try {
            return compound.getClass().getMethod("getList", String.class, int.class).invoke(compound, key, type);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int listSize(Object list) {
        try {
            return (Integer) list.getClass().getMethod("size").invoke(list);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Object listCompound(Object list, int i) {
        try {
            return list.getClass().getMethod("getCompound", int.class).invoke(list, i);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void listAdd(Object list, Object tag) {
        try {
            Class<?> tagClass = Class.forName("net.minecraft.nbt.Tag");
            list.getClass().getMethod("add", tagClass).invoke(list, tag);
        } catch (Throwable ignored) {
            try {
                list.getClass().getMethod("add", Object.class).invoke(list, tag);
            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.WARNING, "[InvSee] list add", t);
            }
        }
    }

    private static Object newList() {
        try {
            return Class.forName("net.minecraft.nbt.ListTag").getConstructor().newInstance();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static void put(Object compound, String key, Object tag) {
        try {
            Class<?> tagClass = Class.forName("net.minecraft.nbt.Tag");
            compound.getClass().getMethod("put", String.class, tagClass).invoke(compound, key, tag);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static void putByte(Object compound, String key, byte value) {
        try {
            compound.getClass().getMethod("putByte", String.class, byte.class).invoke(compound, key, value);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static byte getByte(Object compound, String key) {
        try {
            return (Byte) compound.getClass().getMethod("getByte", String.class).invoke(compound, key);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Object copyWithoutSlot(Object tag) {
        try {
            Object copy = tag.getClass().getMethod("copy").invoke(tag);
            copy.getClass().getMethod("remove", String.class).invoke(copy, "Slot");
            return copy;
        } catch (Throwable t) {
            return tag;
        }
    }
}
