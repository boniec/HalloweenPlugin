package boniec.ahalloween;

import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class HalloweenUtil {

    /** Prefiks czatu — glyph 丷 + small caps (patrz {@link #small(String)}). */
    public static final String PREFIX = "&8[&f丷&8] &6Halloween &8» &r";

    private static final String SMALL_FROM = "abcdefghijklmnopqrstuvwxyz";
    /** Alfabet small caps serwera — zwykłe {@code s} i {@code x}, {@code v} bez kapitalika. */
    private static final String SMALL_TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜvᴡxʏᴢ";
    public static final String HEX_ORANGE = "FF7518";
    public static final String HEX_PURPLE = "9B30FF";
    public static final String HEX_DARK = "35105E";
    public static final String HEX_MAGENTA = "FF36D1";

    private static final Pattern GRADIENT = Pattern.compile(
            "<gradient:#([0-9A-Fa-f]{6}):#([0-9A-Fa-f]{6})>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern MINI_HEX = Pattern.compile("<#([0-9A-Fa-f]{6})>");
    /** Glify z ikony_lista.md — przed każdą w tekście wstawiane jest &f (bialeIkony). */
    private static final String GLIFY_IKONY = "丷價亄亞點澀圍丧个丮ꔰ亃嗨ꕋꕌꕍ子亐";
    private static final Pattern KOD_KOLORU_PRZED_GLIFFEM = Pattern.compile(
            "((?:&#[0-9A-Fa-f]{6})|(?:&[0-9a-fk-or])|(?:§#[0-9A-Fa-f]{6})|(?:§[0-9a-fk-or]))+");

    private HalloweenUtil() {}

    public static String color(String tekst) {
        return format(tekst, true);
    }

    /** Tytuly GUI (Nexo {@code <shift>}, glify) — bez small caps, zeby nie psuc tagow. */
    public static String colorGuiTitle(String tekst) {
        return format(tekst, false);
    }

    /** Czat i wiadomosci — kolory {@code &} / hex, bez small caps. */
    public static String colorMsg(String tekst) {
        return format(tekst, false);
    }

    private static String format(String tekst, boolean smallCaps) {
        if (tekst == null) return "";
        Matcher g = GRADIENT.matcher(tekst);
        StringBuffer sb = new StringBuffer();
        while (g.find()) {
            g.appendReplacement(sb, Matcher.quoteReplacement(gradient(g.group(3), g.group(1), g.group(2))));
        }
        g.appendTail(sb);
        String out = bialeIkony(MINI_HEX.matcher(sb.toString()).replaceAll("&#$1"));
        return Msg.color(smallCaps ? small(out) : out);
    }

    /** Usuwa kolor tuż przed glifem i wstawia {@code &f} — ikony z listy zawsze biale. */
    public static String bialeIkony(String tekst) {
        if (tekst == null || tekst.isEmpty()) return "";
        String out = tekst;
        for (int i = 0; i < GLIFY_IKONY.length(); ) {
            int cp = GLIFY_IKONY.codePointAt(i);
            String g = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            try {
                out = out.replaceAll(KOD_KOLORU_PRZED_GLIFFEM.pattern() + Pattern.quote(g), "&f" + g);
            } catch (PatternSyntaxException ignored) {
            }
        }
        return out;
    }

    /** Małe kapitaliki (jak coinflip / misje), bez ruszania kolorów i %placeholderów%. */
    public static String small(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%') {
                int koniec = s.indexOf('%', i + 1);
                if (koniec > i) {
                    sb.append(s, i, koniec + 1);
                    i = koniec;
                    continue;
                }
            }
            if (c == '&' || c == '§') {
                sb.append(c);
                if (i + 1 >= s.length()) break;
                char n = s.charAt(++i);
                sb.append(n);
                if (n == '#') {
                    for (int k = 0; k < 6 && i + 1 < s.length(); k++) sb.append(s.charAt(++i));
                } else if (n == 'x' || n == 'X') {
                    for (int k = 0; k < 12 && i + 1 < s.length(); k++) sb.append(s.charAt(++i));
                }
                continue;
            }
            char l = (c >= 'A' && c <= 'Z') ? Character.toLowerCase(c) : c;
            int idx = SMALL_FROM.indexOf(l);
            sb.append(idx >= 0 ? SMALL_TO.charAt(idx) : c);
        }
        return sb.toString();
    }

    public static String gradient(String text, String fromHex, String toHex) {
        String plain = text.replaceAll("&[0-9A-Fa-fK-Ok-orR]", "");
        if (plain.isEmpty()) return "";
        int[] a = rgb(fromHex);
        int[] b = rgb(toHex);
        StringBuilder out = new StringBuilder();
        int n = plain.length();
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : (double) i / (n - 1);
            int r = (int) Math.round(a[0] + (b[0] - a[0]) * t);
            int g = (int) Math.round(a[1] + (b[1] - a[1]) * t);
            int bl = (int) Math.round(a[2] + (b[2] - a[2]) * t);
            out.append(String.format(Locale.ROOT, "&#%02X%02X%02X", r, g, bl)).append(plain.charAt(i));
        }
        return out.toString();
    }

    private static int[] rgb(String hex) {
        return new int[]{
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    public static ItemStack skullFromTexture(String textureOrUrl) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (textureOrUrl == null || textureOrUrl.isBlank()) return head;
        ItemMeta raw = head.getItemMeta();
        if (!(raw instanceof SkullMeta meta)) return head;
        String hash = textureOrUrl;
        int idx = textureOrUrl.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < textureOrUrl.length()) hash = textureOrUrl.substring(idx + 1);
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "halloween");
            profile.getTextures().setSkin(URI.create("http://textures.minecraft.net/texture/" + hash).toURL());
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
        } catch (Exception ignored) {
            // zostaje pusta glowka
        }
        return head;
    }

    public static ItemStack named(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        if (lore != null && !lore.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String l : lore) out.add(color(l));
            meta.setLore(out);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack pane(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            it.setItemMeta(meta);
        }
        return it;
    }

    public static void tag(PersistentDataContainer pdc, NamespacedKey key, String value) {
        pdc.set(key, PersistentDataType.STRING, value);
    }

    public static String tag(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.get(key, PersistentDataType.STRING);
    }

    public static boolean has(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.has(key, PersistentDataType.STRING);
    }

    public static ItemStack nexoOrFallback(JavaPlugin plugin, String nexoId, Material fallback) {
        if (nexoId != null && !nexoId.isBlank() && Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            try {
                Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
                Object builder = api.getMethod("itemFromId", String.class).invoke(null, nexoId);
                if (builder != null) {
                    Object built = builder.getClass().getMethod("build").invoke(builder);
                    if (built instanceof ItemStack stack) return stack;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[AHalloween] Brak itemu Nexo '" + nexoId + "' - fallback " + fallback);
            }
        }
        return new ItemStack(fallback == null ? Material.PAPER : fallback);
    }

    public static void ustawHp(LivingEntity entity, double hp) {
        Attribute attr = atrybut("MAX_HEALTH", "GENERIC_MAX_HEALTH");
        if (attr == null) return;
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(hp);
            entity.setHealth(Math.min(hp, inst.getValue()));
        }
    }

    public static void ustawObrazenia(LivingEntity entity, double dmg) {
        Attribute attr = atrybut("ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE");
        if (attr == null) return;
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) inst.setBaseValue(dmg);
    }

    @SuppressWarnings("deprecation")
    private static Attribute atrybut(String nowa, String stara) {
        try {
            return Attribute.valueOf(nowa);
        } catch (Exception ignored) {
        }
        try {
            return Attribute.valueOf(stara);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Color orange() {
        return Color.fromRGB(255, 117, 24);
    }

    public static Color purple() {
        return Color.fromRGB(155, 48, 255);
    }

    public static void efektKlikniecia(org.bukkit.Location loc, int postep, int wymagane) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.55, 0), 2, 0.12, 0.12, 0.12, 0.01);
        float pitch = 0.9f + 0.4f * ((float) postep / Math.max(1, wymagane));
        loc.getWorld().playSound(loc, Sound.UI_BUTTON_CLICK, 0.35f, pitch);
    }

    public static void efektZbierania(org.bukkit.Location loc) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(0, 0.6, 0), 18, 0.25, 0.35, 0.25, 0.02);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.4, 0), 8, 0.2, 0.2, 0.2, 0.01);
        loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.6f);
    }

    public static String pasek(int teraz, int max, int dlugosc) {
        int cap = Math.max(1, max);
        int filled = (int) Math.round((dlugosc * (double) Math.min(teraz, cap)) / cap);
        StringBuilder sb = new StringBuilder("&#FF7518");
        for (int i = 0; i < dlugosc; i++) {
            if (i == filled) sb.append("&#35105E");
            sb.append("▌");
        }
        return sb.append(" &7").append(teraz).append("&8/&7").append(max).toString();
    }

    public static String formatCzas(long sekundy) {
        if (sekundy < 0) return "—";
        long h = sekundy / 3600;
        long m = (sekundy % 3600) / 60;
        long s = sekundy % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
