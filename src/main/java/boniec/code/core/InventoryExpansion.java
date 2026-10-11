package boniec.code.core;

import boniec.dev.inventory.Main;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.util.Locale;

/**
 * Integracja z PlaceholderAPI dla systemu patronów/bogów.
 *
 * Placeholdery:
 *   %inventory_patron%  ->  skolorowana nazwa aktualnego patrona gracza
 *                           (np. z patron.yml: "afrodytaName"), a jeśli gracz
 *                           nie ma żadnego patrona - skolorowany tekst "nullPatron"
 *                           z patron.yml (domyślnie "&c&lʙʀᴀᴋ").
 *
 *   %inventory_sharpness%   -> ŚREDNIA poziomów zaklęcia "Ostrość/Sharpness" ze
 *                              WSZYSTKICH FAKTYCZNIE ZAŁOŻONYCH części zbroi
 *                              (puste sloty pomijane, również w dzielniku),
 *                              DODATKOWO podzielona na połowę w dół.
 *                              Np. 4 części po 18 -> 18 -> /2 = 9.
 *                              2 części: 18 i 16 -> 17 -> /2 = 8.
 *   %inventory_protection%  -> ŚREDNIA poziomów zaklęcia "Ochrona/Protection" ze
 *                              WSZYSTKICH FAKTYCZNIE ZAŁOŻONYCH części zbroi
 *                              (puste sloty pomijane, również w dzielniku),
 *                              DODATKOWO podzielona na połowę w dół.
 *                              Np. 4 części po 18 -> 18 -> /2 = 9.
 *                              2 części: 18 i 16 -> 17 -> /2 = 8.
 *   %inventory_ench_<nazwa>%       -> j.w. co %inventory_sharpness%, ale dla
 *                                     DOWOLNEGO zaklęcia (klucz vanilla, np.
 *                                     "sharpness", "power", "protection"...),
 *                                     liczone z przedmiotu w ręce.
 *   %inventory_armorench_<nazwa>%  -> j.w. co %inventory_protection%, ale dla
 *                                     DOWOLNEGO zaklęcia, sumowane z całej zbroi.
 *
 * Dzielenie "w dół" (a nie zaokrąglanie) - odpowiada to zwykłemu całkowitemu
 * dzieleniu przez 2 w Javie dla liczb nieujemnych (37 / 2 == 18).
 *
 * Rejestrowana TYLKO jeśli PlaceholderAPI jest zainstalowane na serwerze
 * (patrz Main.onEnable() oraz plugin.yml -> softdepend).
 */
public class InventoryExpansion extends PlaceholderExpansion {

    private final Main plugin;

    public InventoryExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        // %inventory_<...>%
        return "inventory";
    }

    @Override
    public @NotNull String getAuthor() {
        return "boniec";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    // true = ekspansja zostaje zarejestrowana "na stałe" (nie wymaga reload po /papi reload)
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return plugin.getMistrzSystem() != null;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (!(offlinePlayer instanceof Player player) || !player.isOnline()) {
            // Patron dotyczy zalogowanego gracza - offline/nieznany gracz nie ma sensownej wartości.
            return "";
        }

        if (params.equalsIgnoreCase("ulepsz") || params.equalsIgnoreCase("eq")
                || params.equalsIgnoreCase("staty")) {
            return formatUlepsz(player);
        }
        if (params.equalsIgnoreCase("miecz")) {
            return formatPoziom("placeholder.miecz", mieczPoziom(player));
        }
        if (params.equalsIgnoreCase("zbroja")) {
            return formatPoziom("placeholder.zbroja", zbrojaPoziom(player));
        }

        if (params.equalsIgnoreCase("patron")) {
            if (plugin.getMistrzSystem() == null) return "";
            return plugin.getMistrzSystem().getPatronDisplayName(player);
        }

        if (params.equalsIgnoreCase("sharpness")) {
            return formatPoziom("placeholder.sharpness", halvedArmorEnchant(player, Enchantment.SHARPNESS));
        }

        if (params.equalsIgnoreCase("protection")) {
            return formatPoziom("placeholder.protection", halvedArmorEnchant(player, Enchantment.PROTECTION));
        }

        // %inventory_sharpness_protection% - suma "ostrości" z całej zbroi oraz "ochrony"
        // z całej zbroi (oba już podzielone na połowę), przydatne np. na jednej
        // linijce w tabliście/scoreboardzie zamiast dwóch osobnych placeholderów.
        if (params.equalsIgnoreCase("sharpness_protection")) {
            int total = halvedArmorEnchant(player, Enchantment.SHARPNESS) + halvedArmorEnchant(player, Enchantment.PROTECTION);
            return String.valueOf(total);
        }

        if (params.equalsIgnoreCase("nastepna_godzina_boga")) {
            return getNastepnaGodzinaBoga();
        }

        if (params.toLowerCase(Locale.ROOT).startsWith("ench_")) {
            Enchantment enchantment = resolveEnchant(params.substring("ench_".length()));
            if (enchantment == null) return "0";
            return String.valueOf(halvedHandEnchant(player, enchantment));
        }

        if (params.toLowerCase(Locale.ROOT).startsWith("armorench_")) {
            Enchantment enchantment = resolveEnchant(params.substring("armorench_".length()));
            if (enchantment == null) return "0";
            return String.valueOf(halvedArmorEnchant(player, enchantment));
        }

        return null; // nieznany placeholder spod %inventory_...%
    }

    private int mieczPoziom(Player player) {
        UpgradeManager upg = plugin.getUpgradeManager();
        if (upg == null) return 0;
        return upg.findLevel(player.getInventory().getItemInMainHand());
    }

    private int zbrojaPoziom(Player player) {
        UpgradeManager upg = plugin.getUpgradeManager();
        if (upg == null) return 0;
        int sum = 0;
        int n = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            int lvl = upg.findLevel(armor);
            if (lvl <= 0) continue;
            sum += lvl;
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    private String formatPoziom(String path, int poziom) {
        String fmt = plugin.getUlepszConfig().getString(path, "%poziom%");
        if (fmt == null || fmt.isBlank()) fmt = "%poziom%";
        String brak = plugin.getUlepszConfig().getString("placeholder.brak", "0");
        String val = poziom <= 0 ? brak : String.valueOf(poziom);
        return kolor(fmt.replace("%poziom%", val).replace("%value%", val));
    }

    private String formatUlepsz(Player player) {
        int miecz = mieczPoziom(player);
        int zbroja = zbrojaPoziom(player);
        String brak = plugin.getUlepszConfig().getString("placeholder.brak", "0");
        String ms = miecz <= 0 ? brak : String.valueOf(miecz);
        String zs = zbroja <= 0 ? brak : String.valueOf(zbroja);
        String linia = plugin.getUlepszConfig().getString("placeholder.linia",
                "&#B878F8🔥%miecz% &9🛡%zbroja%");
        return kolor(linia.replace("%miecz%", ms).replace("%zbroja%", zs)
                .replace("%miecz_poziom%", ms).replace("%zbroja_poziom%", zs));
    }

    private static String kolor(String s) {
        return boniec.klany.util.Msg.color(s);
    }

    private Enchantment resolveEnchant(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (enchantment != null) return enchantment;
        return Enchantment.getByName(key.toUpperCase(Locale.ROOT));
    }

    // Poziom zaklęcia na przedmiocie w ręce (miecz/inne narzędzie), podzielony
    // na połowę w dół (np. 36 -> 18, 37 -> 18, 39 -> 19). Zwykłe całkowitoliczbowe
    // dzielenie w Javie ("/ 2") dla liczb nieujemnych już samo w sobie zaokrągla
    // w dół, więc nic dodatkowego nie trzeba tu robić.
    private int halvedHandEnchant(Player player, Enchantment enchantment) {
        if (enchantment == null) return 0;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        int level = meta.getEnchantLevel(enchantment);
        return level / 2;
    }

    // Średnia (nie suma!) poziomów danego zaklęcia z FAKTYCZNIE założonych części
    // zbroi (hełm/napierśnik/spodnie/buty), DODATKOWO podzielona na połowę w dół -
    // puste sloty pomijane są zarówno z sumy, jak i z dzielnika średniej. Np. gracz
    // z 4 częściami po Protection 18 -> (18+18+18+18)/4 = 18 -> /2 = 9. Gracz z
    // tylko 2 częściami (18 i 16) -> (18+16)/2 = 17 -> /2 = 8.
    // Gracz bez żadnej zbroi założonej -> 0 (unikamy dzielenia przez zero).
    private int halvedArmorEnchant(Player player, Enchantment enchantment) {
        if (enchantment == null) return 0;
        int total = 0;
        int worn = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            worn++;
            ItemMeta meta = armor.getItemMeta();
            if (meta == null) continue;
            total += meta.getEnchantLevel(enchantment);
        }
        if (worn == 0) return 0;
        int average = total / worn;
        return average / 2;
    }

    private String getNastepnaGodzinaBoga() {
        if (plugin.getMistrzSystem() == null) return "";

        LocalTime now = LocalTime.now(boniec.eventy.EventHarmonogram.STREFA);
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        int[] godHours = plugin.getMistrzSystem().godzinyBogow();
        if (godHours.length == 0) {
            godHours = new int[]{13, 14, 15, 16, 17};
        }

        int nextHour = -1;
        String aktywna = MistrzSystem.aktualnaGodzinaTyp;
        boolean godzinaTrwa = aktywna != null
                && !"BRAK".equalsIgnoreCase(aktywna)
                && !"GLOSOWANIE".equalsIgnoreCase(aktywna);
        boolean pominBiezaca = currentMinute > 0 || now.getSecond() > 5 || godzinaTrwa;
        for (int hour : godHours) {
            if (hour > currentHour || (hour == currentHour && !pominBiezaca)) {
                nextHour = hour;
                break;
            }
        }

        // Jeśli nie ma następnej dziś, weź pierwszą jutro
        if (nextHour == -1) {
            nextHour = godHours[0] + 24; // jutro
        }

        // Oblicz różnicę w minutach
        int minutesLeft = Math.max(0, (nextHour - currentHour) * 60 - currentMinute);

        int hours = minutesLeft / 60;
        int minutes = minutesLeft % 60;

        // Formatuj tekst
        StringBuilder sb = new StringBuilder();
        sb.append("&8☰ ");
        sb.append("&#0B9FBC&lɢ");
        sb.append("&#1AA9C5&lᴏ");
        sb.append("&#29B3CE&lᴅ");
        sb.append("&#38BDD7&lᴢ");
        sb.append("&#46C6DF&lɪ");
        sb.append("&#55D0E8&lɴ");
        sb.append("&#64DAF1&lᴀ");
        sb.append(" &lʙ&lᴏ&lɢ&lᴀ");
        sb.append(" &8(&fza ");

        if (hours <= 0 && minutes <= 0) {
            sb.append("&#97F653").append("teraz");
        } else {
            if (hours > 0) {
                sb.append("&#97F653").append(hours).append("h");
            }
            if (hours > 0 && minutes > 0) sb.append(", ");
            if (minutes > 0) {
                sb.append("&#97F653").append(minutes).append("m");
            }
        }

        sb.append("&8)");
        return sb.toString();
    }
}
