package boniec.afk;

import boniec.code.core.WorldGuardHook;
import boniec.skrzynki.CaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Strefa AFK (region WorldGuard z afk.yml): co minute pieniadze, co 15 minut szansa na klucz.
 * W strefie gracz widzi 2 bossbary (klucz + kasa). Wyjscie ze strefy zeruje licznik.
 * Tworzone tylko gdy WorldGuard jest zainstalowany.
 */
public final class AfkStrefa implements Listener, CommandExecutor {

    private static final String SMALL_FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL_TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private final JavaPlugin plugin;
    private final CaseManager caseManager;
    private final File plik;

    private String region;
    private int kasaCoSekund;
    private double kasaKwota;
    private int kluczCoSekund;
    private double kluczSzansa;
    private String kluczSkrzynka;
    private int kluczIlosc;
    private String msgKasa;
    private String msgKlucz;
    private String msgPech;

    private boolean bossbary;
    private String barKluczTytul;
    private BarColor barKluczKolor;
    private String barKasaTytul;
    private BarColor barKasaKolor;
    private BarStyle barStyl;

    /** Sekundy spedzone w strefie bez przerwy. */
    private final Map<UUID, Integer> czas = new HashMap<>();
    /** [0] = klucz, [1] = kasa. */
    private final Map<UUID, BossBar[]> bary = new HashMap<>();
    private Economy economy;

    public AfkStrefa(JavaPlugin plugin, CaseManager caseManager) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.plik = new File(plugin.getDataFolder(), "afk.yml");
        if (!plik.exists()) plugin.saveResource("afk.yml", false);
        wczytaj();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("aafk") != null) plugin.getCommand("aafk").setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    private void wczytaj() {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(plik);
        region = c.getString("region", "afk");
        kasaCoSekund = Math.max(1, c.getInt("pieniadze.co-sekund", 60));
        kasaKwota = c.getDouble("pieniadze.kwota", 0.5);
        kluczCoSekund = Math.max(1, c.getInt("klucz.co-sekund", 900));
        kluczSzansa = c.getDouble("klucz.szansa-procent", 50.0);
        kluczSkrzynka = c.getString("klucz.skrzynka", "rzadka");
        kluczIlosc = Math.max(1, c.getInt("klucz.ilosc", 1));
        msgKasa = c.getString("wiadomosci.kasa", "&f價 &8» &7ᴏᴛʀᴢʏᴍᴀʟᴇꜱ &a{kwota}$ &7ᴢᴀ ᴀꜰᴋ");
        msgKlucz = c.getString("wiadomosci.klucz", "&f乁 &8» &7ᴡʏʟᴏꜱᴏᴡᴀʟᴇꜱ &r{klucz}&7!");
        msgPech = c.getString("wiadomosci.pech", "&f乁 &8» &7ᴛʏᴍ ʀᴀᴢᴇᴍ ʙᴇᴢ ᴋʟᴜᴄᴢᴀ, ᴋᴏʟᴇᴊɴᴀ ꜱᴢᴀɴꜱᴀ ᴢᴀ &f15ᴍ");

        bossbary = c.getBoolean("bossbar.enabled", true);
        barKluczTytul = c.getString("bossbar.klucz.tytul",
                "&f乁 &6&lklucz rzadki &8» &e{szansa}% &7szansy &8| &f五 &e{czas}");
        barKluczKolor = kolor(c.getString("bossbar.klucz.kolor", "YELLOW"), BarColor.YELLOW);
        barKasaTytul = c.getString("bossbar.kasa.tytul",
                "&f價 &a&lkasa &8» &a+{kwota}$ &8| &f五 &a{czas}");
        barKasaKolor = kolor(c.getString("bossbar.kasa.kolor", "GREEN"), BarColor.GREEN);
        try {
            barStyl = BarStyle.valueOf(c.getString("bossbar.styl", "SOLID").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            barStyl = BarStyle.SOLID;
        }
    }

    private static BarColor kolor(String nazwa, BarColor def) {
        try {
            return BarColor.valueOf(nazwa.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return def;
        }
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Integer>> it = czas.entrySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next().getKey();
            if (Bukkit.getPlayer(id) == null) {
                it.remove();
                usunBary(id);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!WorldGuardHook.isInRegion(p.getLocation(), region)) {
                if (czas.remove(p.getUniqueId()) != null) usunBary(p.getUniqueId());
                continue;
            }
            int s = czas.merge(p.getUniqueId(), 1, Integer::sum);
            if (s % kasaCoSekund == 0) dajKase(p);
            if (s % kluczCoSekund == 0) losujKlucz(p);
            if (bossbary) odswiezBary(p, s);
        }
    }

    private void odswiezBary(Player p, int s) {
        BossBar[] b = bary.get(p.getUniqueId());
        if (b == null) {
            b = new BossBar[] {
                    Bukkit.createBossBar("", barKluczKolor, barStyl),
                    Bukkit.createBossBar("", barKasaKolor, barStyl)
            };
            b[0].addPlayer(p);
            b[1].addPlayer(p);
            bary.put(p.getUniqueId(), b);
        }
        int kluczPozostalo = kluczCoSekund - s % kluczCoSekund;
        int kasaPozostalo = kasaCoSekund - s % kasaCoSekund;

        b[0].setTitle(tytul(barKluczTytul
                .replace("{szansa}", kwota(kluczSzansa))
                .replace("{czas}", formatuj(kluczPozostalo))));
        b[0].setProgress(postep(s, kluczCoSekund));

        b[1].setTitle(tytul(barKasaTytul
                .replace("{kwota}", kwota(kasaKwota))
                .replace("{czas}", formatuj(kasaPozostalo))));
        b[1].setProgress(postep(s, kasaCoSekund));
    }

    private static double postep(int s, int okres) {
        double v = (double) (s % okres) / okres;
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void usunBary(UUID id) {
        BossBar[] b = bary.remove(id);
        if (b == null) return;
        for (BossBar bar : b) bar.removeAll();
    }

    /** Wywoływane z onDisable. */
    public void zamknij() {
        for (UUID id : new java.util.ArrayList<>(bary.keySet())) usunBary(id);
        czas.clear();
    }

    private void dajKase(Player p) {
        if (kasaKwota <= 0) return;
        Economy econ = economy();
        if (econ == null) return;
        econ.depositPlayer(p, kasaKwota);
        p.sendMessage(color(msgKasa.replace("{kwota}", kwota(kasaKwota))));
    }

    private void losujKlucz(Player p) {
        if (ThreadLocalRandom.current().nextDouble(100.0) >= kluczSzansa) {
            p.sendMessage(color(msgPech));
            return;
        }
        String nazwa = caseManager != null ? caseManager.dajKlucz(p, kluczSkrzynka, kluczIlosc) : null;
        if (nazwa == null) {
            plugin.getLogger().warning("[AFK] Brak skrzynki '" + kluczSkrzynka + "' (/acase lista) - ustaw klucz.skrzynka w afk.yml.");
            return;
        }
        p.sendMessage(color(msgKlucz.replace("{klucz}", nazwa)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    private Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        czas.remove(event.getPlayer().getUniqueId());
        usunBary(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("afk.admin")) {
            sender.sendMessage(color("&cBrak permisji."));
            return true;
        }
        zamknij();
        wczytaj();
        sender.sendMessage(color("&8[&aAFK&8] &aPrzeladowano afk.yml &8(&7region: &f" + region
                + "&8, &7skrzynka: &f" + kluczSkrzynka + "&8)"));
        if (caseManager != null && caseManager.istnieje(kluczSkrzynka)) return true;
        sender.sendMessage(color("&8[&aAFK&8] &cUwaga: skrzynka &f" + kluczSkrzynka + " &cnie istnieje!"));
        return true;
    }

    /** Smalltext (litery a-z -> małe kapitaliki) z pominięciem kodów kolorów, potem kolory. */
    private static String tytul(String s) {
        return color(smallText(s));
    }

    private static String smallText(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean kod = false;
        for (char c : s.toCharArray()) {
            if (kod) {
                sb.append(c);
                kod = false;
                continue;
            }
            if (c == '&' || c == '§') {
                sb.append(c);
                kod = true;
                continue;
            }
            char l = (c >= 'A' && c <= 'Z') ? Character.toLowerCase(c) : c;
            int idx = SMALL_FROM.indexOf(l);
            sb.append(idx >= 0 ? SMALL_TO.charAt(idx) : c);
        }
        return sb.toString();
    }

    private static String formatuj(int sekundy) {
        int m = sekundy / 60;
        int s = sekundy % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    private static String kwota(double v) {
        return java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
