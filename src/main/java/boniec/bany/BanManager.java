package boniec.bany;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System /alts: konfiguracja (bany.yml), zapis IP i ladowanie kont wspoldzielacych adres.
 */
public final class BanManager {

    public static final class AltEntry {
        public final String uuid;
        public final String name;
        public final long firstSeen;
        public final long lastSeen;
        public final int shared;
        public final String ips;

        public AltEntry(String uuid, String name, long firstSeen, long lastSeen, int shared, String ips) {
            this.uuid = uuid;
            this.name = name;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.shared = shared;
            this.ips = ips == null ? "" : ips;
        }
    }

    public static final class Target {
        public final String uuid;
        public final String name;
        public final String ip;
        public final boolean literalIp;

        Target(String uuid, String name, String ip, boolean literalIp) {
            this.uuid = uuid;
            this.name = name;
            this.ip = ip;
            this.literalIp = literalIp;
        }
    }

    private static final Pattern HEX = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private final JavaPlugin plugin;
    private BanDatabase db;
    private YamlConfiguration cfg;
    private volatile boolean shuttingDown;

    public BanManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        loadConfig();
        db = new BanDatabase(plugin);
    }

    public void stop() {
        shuttingDown = true;
        if (db != null) db.close();
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        File f = new File(plugin.getDataFolder(), "bany.yml");
        if (!f.exists()) plugin.saveResource("bany.yml", false);
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        try (InputStreamReader r = new InputStreamReader(plugin.getResource("bany.yml"), StandardCharsets.UTF_8)) {
            c.setDefaults(YamlConfiguration.loadConfiguration(r));
        } catch (Exception ignored) {
        }
        this.cfg = c;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public BanDatabase db() {
        return db;
    }

    public YamlConfiguration cfg() {
        return cfg;
    }

    public void async(Runnable r) {
        if (shuttingDown || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (shuttingDown || !boniec.code.core.CoreDatabase.isOpen()) return;
            r.run();
        });
    }

    public void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    public String color(String text) {
        if (text == null) return "";
        Matcher m = HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) rep.append('§').append(c);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static String rep(String s, String... ph) {
        if (s == null) return "";
        for (int i = 0; i + 1 < ph.length; i += 2) {
            s = s.replace(ph[i], ph[i + 1] == null ? "" : ph[i + 1]);
        }
        return s;
    }

    public String str(String path, String... ph) {
        return color(rep(cfg.getString(path, ""), ph));
    }

    public String prefix() {
        return color(cfg.getString("wiadomosci.prefix", ""));
    }

    public void send(CommandSender to, String path, String... ph) {
        if (cfg.isList(path)) {
            for (String l : cfg.getStringList(path)) to.sendMessage(color(rep(l, ph)));
        } else {
            to.sendMessage(prefix() + str(path, ph));
        }
    }

    public void notifyStaff(String path, String... ph) {
        String perm = cfg.getString("ogolne.permisja-powiadomien", "bany.powiadomienia");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(perm)) send(p, path, ph);
        }
        send(Bukkit.getConsoleSender(), path, ph);
    }

    public String date(long ms) {
        if (ms <= 0) return "-";
        SimpleDateFormat f = new SimpleDateFormat(cfg.getString("ogolne.format-daty", "dd.MM.yyyy HH:mm"));
        try {
            f.setTimeZone(TimeZone.getTimeZone(cfg.getString("ogolne.strefa-czasowa", "Europe/Warsaw")));
        } catch (Exception ignored) {
        }
        return f.format(new Date(ms));
    }

    public static boolean isIpv4(String s) {
        return s != null && IPV4.matcher(s).matches();
    }

    public static String maskIp(String ip) {
        if (isIpv4(ip)) {
            String[] p = ip.split("\\.");
            return p[0] + "." + p[1] + ".*.*";
        }
        return "IP";
    }

    public static String ipOf(Player p) {
        return p.getAddress() == null ? "" : p.getAddress().getAddress().getHostAddress();
    }

    public Target resolve(String input, UUID onlineUuid, String onlineName, String onlineIp) {
        if (isIpv4(input)) return new Target("", maskIp(input), input, true);
        if (onlineUuid != null) return new Target(onlineUuid.toString(), onlineName, onlineIp, false);
        BanDatabase.KnownPlayer kp = db.findPlayerByName(input);
        if (kp != null) return new Target(kp.uuid, kp.name, kp.lastIp, false);
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
        if (cached != null && cached.getName() != null) {
            String u = cached.getUniqueId().toString();
            return new Target(u, cached.getName(), db.lastIp(u), false);
        }
        return null;
    }

    public List<AltEntry> loadAlts(String uuid) {
        List<AltEntry> list = db.sharedAccounts(uuid);
        String sort = cfg.getString("alts.sortowanie", "ostatnio");
        Comparator<AltEntry> byLast = Comparator.comparingLong((AltEntry e) -> e.lastSeen).reversed();
        if ("nick".equalsIgnoreCase(sort)) {
            list.sort(Comparator.comparing((AltEntry e) -> e.name.toLowerCase()));
        } else {
            list.sort(byLast);
        }
        return list;
    }

    public int slot(String path, int def) {
        return cfg.getInt(path + ".slot", def);
    }

    public List<Integer> contentSlots() {
        List<Integer> slots = cfg.getIntegerList("gui.sloty-zawartosci");
        if (slots.isEmpty()) {
            for (int r = 1; r <= 4; r++) for (int c = 1; c <= 7; c++) slots.add(r * 9 + c);
        }
        return slots;
    }

    public ItemStack item(String path, List<String> extraLore, String... ph) {
        return item(path, null, extraLore, ph);
    }

    public ItemStack item(String path, Material forceMaterial, List<String> extraLore, String... ph) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        Material mat = forceMaterial;
        if (mat == null) {
            mat = Material.matchMaterial(sec == null ? "" : sec.getString("material", "STONE"));
            if (mat == null || mat.isAir()) mat = Material.STONE;
        }
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta == null) return is;
        meta.setDisplayName(color(rep(cfg.getString(path + ".nazwa", " "), ph)));
        List<String> lore = new ArrayList<>();
        for (String l : cfg.getStringList(path + ".lore")) lore.add(color(rep(l, ph)));
        if (extraLore != null) for (String l : extraLore) lore.add(color(rep(l, ph)));
        meta.setLore(lore);
        int cmd = cfg.getInt(path + ".model-data", -1);
        if (cmd >= 0) meta.setCustomModelData(cmd);
        if (cfg.getBoolean(path + ".swiecenie", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.values());
        is.setItemMeta(meta);
        return is;
    }

    public ItemStack head(String path, String uuid, List<String> extraLore, String... ph) {
        ItemStack is = item(path, Material.PLAYER_HEAD, extraLore, ph);
        if (uuid != null && !uuid.isEmpty() && is.getItemMeta() instanceof SkullMeta sm) {
            try {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
                is.setItemMeta(sm);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return is;
    }
}
