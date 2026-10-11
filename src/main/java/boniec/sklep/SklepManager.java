package boniec.sklep;

import boniec.szaman.util.Msg;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class SklepManager {

    private final JavaPlugin plugin;
    private final SklepDatabase database;
    private final File folderGui;
    private FileConfiguration config;
    private FileConfiguration messages;
    private Economy economy;

    public SklepManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new SklepDatabase(plugin);
        this.folderGui = new File(plugin.getDataFolder(), "sklep/gui");
    }

    public void start() {
        zapiszDomyslne();
        database.connect();
        wczytaj();
        setupEconomy();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                database.tickPlaytime(p.getUniqueId(), p.getName(), 1);
            }
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, database::flushPlaytime, 1200L, 1200L);
    }

    public void stop() {
        database.flushPlaytime();
        database.disconnect();
    }

    public void wczytaj() {
        File cfg = new File(plugin.getDataFolder(), "sklep/config.yml");
        File msg = new File(plugin.getDataFolder(), "sklep/messages.yml");
        config = wczytaj(cfg, "sklep/config.yml");
        messages = wczytaj(msg, "sklep/messages.yml");
        folderGui.mkdirs();
        for (String name : new String[]{
                "main-shop.yml", "virtual-shop-main.yml", "virtual-shop-buy.yml",
                "virtual-shop-sell.yml", "time-shop.yml", "time-shop-buy.yml",
                "buy-confirm.yml", "buy-more.yml"
        }) {
            File f = new File(folderGui, name);
            if (!f.exists()) zapiszZasob("sklep/gui/" + name, f);
        }
    }

    private void zapiszDomyslne() {
        zapiszZasob("sklep/config.yml", new File(plugin.getDataFolder(), "sklep/config.yml"));
        zapiszZasob("sklep/messages.yml", new File(plugin.getDataFolder(), "sklep/messages.yml"));
        zapiszZasob("sklep/shop-default.db", new File(plugin.getDataFolder(), "sklep/shop-default.db"));
        folderGui.mkdirs();
    }

    private void zapiszZasob(String path, File cel) {
        if (!cel.exists()) {
            cel.getParentFile().mkdirs();
            plugin.saveResource(path, false);
        }
    }

    private FileConfiguration wczytaj(File plik, String zasob) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(plik);
        InputStream def = plugin.getResource(zasob);
        if (def != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
        return yaml;
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
    }

    public SklepDatabase database() {
        return database;
    }

    public File folderGui() {
        return folderGui;
    }

    public YamlConfiguration gui(String plik) {
        return YamlConfiguration.loadConfiguration(new File(folderGui, plik));
    }

    public String msg(String key) {
        String path = key + ".message";
        String raw = messages.getString(path, messages.getString(key, "&cBrak wiadomosci: " + key));
        String prefix = messages.getString("prefix", "&8» ");
        return Msg.color(prefix + raw);
    }

    public String msgRaw(String key) {
        return messages.getString(key + ".message", messages.getString(key, key));
    }

    public double balance(Player p) {
        return economy != null ? economy.getBalance(p) : 0;
    }

    public boolean charge(Player p, double amount) {
        if (economy == null) return true;
        if (economy.getBalance(p) < amount) return false;
        economy.withdrawPlayer(p, amount);
        return true;
    }

    public void deposit(Player p, double amount) {
        if (economy != null) economy.depositPlayer(p, amount);
    }

    public String formatMoney(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    public String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return s + "s";
    }

    public String podstaw(Player p, String tekst) {
        if (tekst == null) return "";
        return tekst
                .replace("{balans}", formatMoney(balance(p)))
                .replace("{BALANCE}", formatMoney(balance(p)))
                .replace("{gracz}", p.getName())
                .replace("{PLAYER}", p.getName())
                .replace("{czas_gracza}", formatTime(database.getPlaytimeSeconds(p.getUniqueId())))
                .replace("{czas}", formatTime(database.getPlaytimeSeconds(p.getUniqueId())));
    }

    public ConfigurationSection sekcjaGui(YamlConfiguration yaml, String... keys) {
        ConfigurationSection s = yaml;
        for (String k : keys) {
            if (s == null) return null;
            s = s.getConfigurationSection(k);
        }
        return s;
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
