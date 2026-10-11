package boniec.hitsystem.core;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Odpowiada WYŁĄCZNIE za identyfikację numeru seta przedmiotu na podstawie
 * WANILIOWYCH enchantów - Protection na zbroi, Sharpness na mieczu.
 * Nie korzysta z PersistentDataContainer/NBT ani z /hitsystem tag: numer seta
 * jest wyliczany bezpośrednio z poziomu enchantu, więc jest w 100% stabilny
 * między restartami serwera (to samo mechanizm co np. zwykły Sharpness).
 * <p>
 * Wzór:
 *   zbroja (helmet/chestplate/leggings/boots): set = poziomProtection / 2
 *   miecz:                                     set = (poziomSharpness - 1) / 2
 * (np. Protection 20 -> set 10; Sharpness 31 -> set 15, Sharpness 39 -> set 19).
 * Dzielenie całkowite - jeśli poziom enchantu nie wpadnie idealnie w wzór
 * (np. Protection nieparzyste, Sharpness parzyste), wynik jest zaokrąglony
 * w dół przez naturalne działanie dzielenia int w Javie.
 * <p>
 * Brak enchantu (poziom 0) = brak seta (-1), tak jak wcześniej brak tagu PDC.
 */
public final class SetIdentifier {

    /** Zwraca set-id miecza (na podstawie Sharpness) lub -1, jeśli item nie ma tego enchantu / jest null. */
    public int getSwordSetId(ItemStack item) {
        int poziom = enchantLevel(item, Enchantment.SHARPNESS);
        return poziom > 0 ? ((poziom - 1) / 2) : -1;
    }

    /**
     * Czy ten przedmiot ma liczyć się w HitSystem (PvP melee).
     * Netherite Sword zawsze (jak dotychczas, nawet bez Sharpness).
     * Każdy inny item (blaze rod, diamentowy miecz itd.) — gdy ma Sharpness.
     * Goła ręka / powietrze zostaje vanillą.
     */
    public boolean isHitSystemWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getType() == Material.NETHERITE_SWORD) {
            return true;
        }
        return enchantLevel(item, Enchantment.SHARPNESS) > 0;
    }

    /** Zwraca set-id danego elementu zbroi (na podstawie Protection) lub -1. */
    private int armorSetId(ItemStack item) {
        int poziom = enchantLevel(item, Enchantment.PROTECTION);
        return poziom > 0 ? (poziom / 2) : -1;
    }

    public int getHelmetSetId(ItemStack item) {
        return armorSetId(item);
    }

    public int getChestplateSetId(ItemStack item) {
        return armorSetId(item);
    }

    public int getLeggingsSetId(ItemStack item) {
        return armorSetId(item);
    }

    public int getBootsSetId(ItemStack item) {
        return armorSetId(item);
    }

    /** Ogólny set-id zbroi - używany tylko do /hitsystem info jako podgląd Protection na trzymanym/dowolnym elemencie. */
    public int getGenericArmorSetId(ItemStack item) {
        return armorSetId(item);
    }

    private int enchantLevel(ItemStack item, Enchantment enchant) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        int direct = item.getEnchantmentLevel(enchant);
        if (direct > 0) {
            return direct;
        }
        if (enchant.equals(Enchantment.SHARPNESS) && item.hasItemMeta()) {
            int best = 0;
            for (var entry : item.getEnchantments().entrySet()) {
                String key = entry.getKey().getKey().getKey();
                if ("sharpness".equals(key) || "damage_all".equals(key)) {
                    best = Math.max(best, entry.getValue());
                }
            }
            return best;
        }
        return 0;
    }

    /**
     * Ustala numer "Full Setu" gracza: zwraca ten numer TYLKO jeśli wszystkie
     * 4 elementy zbroi (helmet/chest/legs/boots) mają identyczny poziom
     * Protection (a więc identyczny set-id). W przeciwnym razie (brak
     * elementu, różne poziomy) zwraca -1 - gracz nie jest uznawany za żaden
     * pełny set.
     * <p>
     * WYJĄTEK: jeśli w slocie chestplate jest fizycznie założona elytra
     * (Material.ELYTRA), slot chest jest CAŁKOWICIE ignorowany przy tym
     * sprawdzeniu (tak jakby gracz nie miał tam żadnego wymogu tagu) - Full
     * Set jest wtedy ustalany wyłącznie na podstawie helmet/legs/boots.
     * Dzięki temu elytra "zamiast klaty" działa z KAŻDĄ zbroją, bez potrzeby
     * Protection w niej - patrz elytra-as-chestplate w hitsystem.yml,
     * obsługiwane w HitSystemListener.
     */
    public int getFullArmorSetId(Player player) {
        EntityEquipment eq = player.getEquipment();
        if (eq == null) {
            return -1;
        }

        ItemStack chestItem = eq.getChestplate();
        boolean chestIsElytra = chestItem != null && chestItem.getType().name().equals("ELYTRA");

        int helmet = getHelmetSetId(eq.getHelmet());
        int legs = getLeggingsSetId(eq.getLeggings());
        int boots = getBootsSetId(eq.getBoots());

        if (chestIsElytra) {
            // Chest ignorowany - Full Set liczony tylko z helmet/legs/boots.
            if (helmet == -1 || legs == -1 || boots == -1) {
                return -1;
            }
            if (helmet == legs && legs == boots) {
                return helmet;
            }
            return -1;
        }

        int chest = getChestplateSetId(chestItem);
        if (helmet == -1 || chest == -1 || legs == -1 || boots == -1) {
            return -1;
        }
        if (helmet == chest && chest == legs && legs == boots) {
            return helmet;
        }
        return -1;
    }

    /**
     * Set-id zbroi gracza z UWZGLEDNIENIEM mieszanych czesci (np. helm 14, klata 13,
     * spodnie 16, buty 13). Zwraca efektywny set liczony wg armor-mix z hitsystem.yml:
     *   efektywny = najslabsza czesc + udzial * (srednia - najslabsza)
     * Dzieki temu jedna mocna czesc nie podbija calej zbroi (np. 18/12/12/12 ~ 12.5,
     * a nie 18), a full 14 i tak zawsze wychodzi lepiej niz zbroja z jedna czescia 18 i reszta 12.
     * <p>
     * Zwraca -1 (brak seta), gdy brakuje ktorejs czesci albo zbroja nie ma Protection - tak samo
     * jak {@link #getFullArmorSetId(Player)}. Elytra w slocie klaty jest ignorowana jak wczesniej
     * (liczy sie tylko helm/spodnie/buty).
     * <p>
     * Gdy wszystkie 4 czesci maja ten sam set, wynik jest IDENTYCZNY z getFullArmorSetId.
     *
     * @param share ulamek nadwyzki ponad najslabsza czescia, ktory sie liczy (0.0 = tylko najslabsza,
     *              1.0 = zwykla srednia). Wartosc jest ograniczana do przedzialu 0..1.
     */
    public double getEffectiveArmorSet(Player player, double share) {
        EntityEquipment eq = player.getEquipment();
        if (eq == null) {
            return -1;
        }

        ItemStack chestItem = eq.getChestplate();
        boolean chestIsElytra = chestItem != null && chestItem.getType().name().equals("ELYTRA");

        int helmet = getHelmetSetId(eq.getHelmet());
        int legs = getLeggingsSetId(eq.getLeggings());
        int boots = getBootsSetId(eq.getBoots());

        int[] parts;
        if (chestIsElytra) {
            parts = new int[]{helmet, legs, boots};
        } else {
            parts = new int[]{helmet, getChestplateSetId(chestItem), legs, boots};
        }

        int min = Integer.MAX_VALUE;
        long sum = 0;
        for (int part : parts) {
            if (part == -1) {
                return -1;
            }
            min = Math.min(min, part);
            sum += part;
        }
        double average = (double) sum / parts.length;
        double clampedShare = Math.max(0.0, Math.min(1.0, share));
        return min + clampedShare * (average - min);
    }

    /** Set-id miecza aktualnie trzymanego w ręce głównej gracza, lub -1. */
    public int getHeldSwordSetId(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        return getSwordSetId(mainHand);
    }
}
