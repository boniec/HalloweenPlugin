package boniec.antylogout;

import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Wczytuje i udostepnia konfiguracje z antylogout.yml. Wszystkie wartosci sa
 * czytane RAZ przy load() (nie przy kazdym uzyciu) - to samo podejscie co
 * HitSystemConfig, zeby /antylogout reload bylo tanie i przewidywalne.
 */
public final class AntyLogoutConfig {

    private final Plugin plugin;
    private File file;
    private FileConfiguration yaml;

    private boolean enabled;
    private int combatSeconds;
    private BarColor bossbarColor;
    private BarStyle bossbarStyle;
    private String bossbarTitle;
    private final Set<String> allowedCommands = new HashSet<>();

    private String msgKomendaZablokowana;
    private String msgTylkoGracz;
    private String msgBrakUprawnien;
    private String msgConfigPrzeladowany;
    private String msgStatus;
    private String msgWlaczony;
    private String msgWylaczony;

    public AntyLogoutConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "antylogout.yml");
        if (!file.exists()) {
            plugin.saveResource("antylogout.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);

        // Stan wlacz/wylacz jest tylko w pamieci. Po restarcie zawsze wylaczony.
        combatSeconds = Math.max(1, yaml.getInt("antylogout.czas-walki-sekund", 15));

        bossbarColor = parseBarColor(yaml.getString("antylogout.bossbar.kolor", "RED"));
        bossbarStyle = parseBarStyle(yaml.getString("antylogout.bossbar.styl", "SOLID"));
        bossbarTitle = kolor(yaml.getString("antylogout.bossbar.tytul",
                "&cJestes w walce jeszcze przez &4%sekundy%s!"));

        allowedCommands.clear();
        for (String cmd : yaml.getStringList("antylogout.dozwolone-komendy")) {
            if (cmd != null && !cmd.isBlank()) {
                allowedCommands.add(stripSlash(cmd).toLowerCase(Locale.ROOT));
            }
        }
        if (allowedCommands.isEmpty()) {
            // Zabezpieczenie: nawet przy zle wypelnionym configu, /msg /r /helpop
            // maja zawsze dzialac (kontakt z administracja / graczami musi byc mozliwy).
            allowedCommands.add("msg");
            allowedCommands.add("r");
            allowedCommands.add("helpop");
        }

        msgKomendaZablokowana = kolor(yaml.getString("antylogout.wiadomosci.komenda-zablokowana",
                "&cNie mozesz uzyc tej komendy podczas walki!"));
        msgTylkoGracz = kolor(yaml.getString("antylogout.wiadomosci.tylko-gracz",
                "&cTa komenda jest dostepna tylko dla graczy!"));
        msgBrakUprawnien = kolor(yaml.getString("antylogout.wiadomosci.brak-uprawnien",
                "&cNie masz uprawnien do tej komendy!"));
        msgConfigPrzeladowany = kolor(yaml.getString("antylogout.wiadomosci.config-przeladowany",
                "&aPrzeladowano antylogout.yml."));
        msgStatus = kolor(yaml.getString("antylogout.wiadomosci.status",
                "&8[&cAntylogout&8] &7enabled=&f%enabled% &7czas-walki=&f%czas%s &7graczy-w-walce=&f%ile%"));
        msgWlaczony = kolor(yaml.getString("antylogout.wiadomosci.wlaczony",
                "&8[&cAntylogout&8] &aWlaczono antylogout."));
        msgWylaczony = kolor(yaml.getString("antylogout.wiadomosci.wylaczony",
                "&8[&cAntylogout&8] &cWylaczono antylogout."));
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    /** Po restarcie serwera antylogout jest zawsze wylaczony. */
    public void wylaczPoStarcie() {
        enabled = false;
    }

    private static String stripSlash(String s) {
        s = s.trim();
        return s.startsWith("/") ? s.substring(1) : s;
    }

    private static BarColor parseBarColor(String raw) {
        try {
            return BarColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BarColor.RED;
        }
    }

    private static BarStyle parseBarStyle(String raw) {
        try {
            return BarStyle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BarStyle.SOLID;
        }
    }

    private static String kolor(String tekst) {
        return tekst == null ? "" : ChatColor.translateAlternateColorCodes('&', tekst);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCombatSeconds() {
        return combatSeconds;
    }

    public BarColor getBossbarColor() {
        return bossbarColor;
    }

    public BarStyle getBossbarStyle() {
        return bossbarStyle;
    }

    /** Buduje tytul bossbara dla danej liczby pozostalych sekund (%sekundy%). */
    public String buildBossbarTitle(int remainingSeconds) {
        return bossbarTitle.replace("%sekundy%", String.valueOf(remainingSeconds));
    }

    /** true jesli podana etykieta komendy (bez ukosnika, dowolna wielkosc liter) jest dozwolona podczas walki. */
    public boolean isCommandAllowed(String label) {
        return allowedCommands.contains(label.toLowerCase(Locale.ROOT));
    }

    public String getMsgKomendaZablokowana() {
        return msgKomendaZablokowana;
    }

    public String getMsgTylkoGracz() {
        return msgTylkoGracz;
    }

    public String getMsgBrakUprawnien() {
        return msgBrakUprawnien;
    }

    public String getMsgConfigPrzeladowany() {
        return msgConfigPrzeladowany;
    }

    public String getMsgStatus() {
        return msgStatus;
    }

    public String getMsgWlaczony() {
        return msgWlaczony;
    }

    public String getMsgWylaczony() {
        return msgWylaczony;
    }
}
