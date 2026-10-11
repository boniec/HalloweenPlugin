package boniec.klany.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prosty builder itemow do GUI - w tym glowki graczy (do listy czlonkow) oraz
 * dekoracyjne glowki (custom textures z base64) do upiekszenia panelu.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack base) {
        this.item = base.clone();
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder glowaGracza(OfflinePlayer gracz) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(gracz);
            skull.setItemMeta(meta);
        }
        return new ItemBuilder(skull);
    }

    /**
     * Dekoracyjna glowka z custom textura (base64), np. strzalki/ikony do panelu.
     * Uzywa reflection na wewnetrznym GameProfile, zeby dzialac zarowno na Paper jak
     * i na czystym Spigot (bez twardej zaleznosci od klas Paper w classpath).
     */
    public static ItemBuilder glowaTekstury(String base64Texture) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            try {
                Object gameProfile = Class
                        .forName("com.mojang.authlib.GameProfile")
                        .getConstructor(UUID.class, String.class)
                        .newInstance(UUID.randomUUID(), null);

                Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                Object property = propertyClass.getConstructor(String.class, String.class)
                        .newInstance("textures", base64Texture);

                Object propertyMap = gameProfile.getClass().getMethod("getProperties").invoke(gameProfile);
                propertyMap.getClass().getMethod("put", Object.class, Object.class)
                        .invoke(propertyMap, "textures", property);

                java.lang.reflect.Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, gameProfile);
            } catch (Throwable ignored) {
                // Reflection sie nie powiodla (inna wersja serwera / NMS) - zostaje zwykla,
                // pusta glowka gracza zamiast custom textury. Nie przerywamy dzialania GUI.
            }
            skull.setItemMeta(meta);
        }
        return new ItemBuilder(skull);
    }

    public ItemBuilder name(String name) {
        meta.setDisplayName(Msg.color(name));
        return this;
    }

    /** Ustawia nazwe, ktora jest JUZ w pelni skolorowana (np. wynik Gradient#apply) - bez ponownego Msg.color. */
    public ItemBuilder nameRaw(String nazwaSkolorowana) {
        meta.setDisplayName(nazwaSkolorowana);
        return this;
    }

    public ItemBuilder lore(String... linie) {
        List<String> lore = new ArrayList<>();
        for (String linia : linie) {
            lore.add(Msg.color(linia));
        }
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder lore(List<String> linie) {
        List<String> lore = new ArrayList<>();
        for (String linia : linie) {
            lore.add(Msg.color(linia));
        }
        meta.setLore(lore);
        return this;
    }

    /** Jak lore(List), ale linie sa JUZ w pelni skolorowane (np. zawieraja gradienty) - bez Msg.color. */
    public ItemBuilder loreRaw(List<String> linieSkolorowane) {
        meta.setLore(new ArrayList<>(linieSkolorowane));
        return this;
    }

    public ItemBuilder glow() {
        // UWAGA: w Bukkit/Spigot 1.20.5+ stara nazwa "Enchantment.LUCK" zostala usunieta
        // z API (przebudowa systemu enchantow) - poprawna, aktualna stala to LUCK_OF_THE_SEA.
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder glow(boolean warunek) {
        if (warunek) glow();
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
