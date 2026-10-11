package boniec.eventy;

import boniec.dev.inventory.Main;
import boniec.klany.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** /eventy — GUI eventow (bossy, KOTH, serce, meteoryty, szal). */
public final class EventyCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final EventyGui gui;
    private FileConfiguration cfg;
    private final File plik;

    public EventyCommand(Main plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "eventy.yml");
        this.gui = new EventyGui(plugin, this);
        wczytaj();
    }

    public EventyGui getGui() {
        return gui;
    }

    public FileConfiguration getCfg() {
        return cfg;
    }

    public void wczytaj() {
        if (!plik.exists()) {
            plugin.saveResource("eventy.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(plik);
        uzupelnijZJara();
        EventHarmonogram.wczytaj(cfg);
        odswiezSilniki();
    }

    private void odswiezSilniki() {
        if (plugin.getEventOdlamkowManager() != null) plugin.getEventOdlamkowManager().odswiezHarmonogram();
        if (plugin.getMeteoriteManager() != null) plugin.getMeteoriteManager().odswiezHarmonogram();
        if (plugin.getMinotaurManager() != null) plugin.getMinotaurManager().odswiezHarmonogram();
        if (plugin.getZeusManager() != null) plugin.getZeusManager().odswiezHarmonogram();
        if (plugin.getGoblinManager() != null) plugin.getGoblinManager().odswiezHarmonogram();
    }

    private void uzupelnijZJara() {
        InputStream in = plugin.getResource("eventy.yml");
        if (in == null) return;
        FileConfiguration jar = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        int wersjaJar = jar.getInt("gui.wersja", 1);
        if (cfg.getInt("gui.wersja", 0) < wersjaJar) {
            nadpiszSekcje(jar, "gui");
            nadpiszSekcje(jar, "eventy");
            nadpiszSekcje(jar, "kiedy");
            zapisz();
        }
    }

    private void nadpiszSekcje(FileConfiguration zrodlo, String sciezka) {
        cfg.set(sciezka, null);
        if (!zrodlo.isConfigurationSection(sciezka)) return;
        for (String klucz : zrodlo.getConfigurationSection(sciezka).getKeys(true)) {
            String full = sciezka + "." + klucz;
            Object v = zrodlo.get(full);
            if (!(v instanceof org.bukkit.configuration.ConfigurationSection)) {
                cfg.set(full, v);
            }
        }
    }

    private void zapisz() {
        try {
            cfg.save(plik);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udalo sie zapisac eventy.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("eventy.admin")) {
                sender.sendMessage(kolor(txt("brak-uprawnien", "&f亢 &cNie masz uprawnień.")));
                return true;
            }
            wczytaj();
            sender.sendMessage(kolor(txt("przeladowano", "&f両 &aPrzeładowano eventy.yml")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(kolor(txt("tylko-gracz", "&cTa komenda otwiera GUI — uzyj jej w grze.")));
            wypiszCzat(sender);
            return true;
        }
        gui.otworz(player);
        return true;
    }

    private void wypiszCzat(CommandSender sender) {
        for (String linia : cfg.getStringList("naglowek")) {
            sender.sendMessage(kolor(linia));
        }
        sender.sendMessage(kolor(skladaj("koth", "世", "&#FF4D4D&lᴋᴏᴛʜ", gui.statusKoth())));
        sender.sendMessage(kolor(skladaj("serce", "乜", "&#d016f7&lꜱᴇʀᴄᴇ ᴋᴏꜱᴍᴏꜱᴜ", gui.statusSerce())));
        sender.sendMessage(kolor(skladaj("meteoryty", "东", "&#FFC34D&lᴍᴇᴛᴇᴏʀʏᴛʏ", gui.statusMeteoryty())));
        sender.sendMessage(kolor(skladaj("minotaur", "世", "&#E2933A&lᴍɪɴᴏᴛᴀᴜʀ", gui.statusMinotaur())));
        sender.sendMessage(kolor(skladaj("zeus", "亗", "&#FDD835&lᴢᴇᴜꜱ", gui.statusZeus())));
        sender.sendMessage(kolor(skladaj("goblin", "乚", "&#55FF55&lɢᴏʙʟɪɴ", gui.statusGoblin())));
        sender.sendMessage(kolor(skladaj("szal", "亗", "&#C02FD4&lꜱᴢᴀʟ ᴏᴅʟᴀᴍᴋᴏᴡ", gui.statusSzal())));
        for (String linia : cfg.getStringList("stopka")) {
            sender.sendMessage(kolor(linia));
        }
    }

    private String skladaj(String klucz, String ikonaDomyslna, String nazwaDomyslna, String status) {
        String ikona = txt("eventy." + klucz + ".ikona", ikonaDomyslna);
        String nazwa = txt("eventy." + klucz + ".nazwa", nazwaDomyslna);
        return txt("linia", "&f 丶 &f%ikona% %nazwa% &8» %status%")
                .replace("%ikona%", ikona)
                .replace("%nazwa%", nazwa)
                .replace("%status%", status);
    }

    String txt(String path, String domyslna) {
        String v = cfg.getString(path);
        return (v == null || v.isEmpty()) ? domyslna : v;
    }

    static String formatujCzas(int sekundy) {
        if (sekundy < 0) sekundy = 0;
        if (sekundy >= 3600) return (sekundy / 3600) + "h " + ((sekundy % 3600) / 60) + "m";
        if (sekundy >= 60) return (sekundy / 60) + "m " + (sekundy % 60) + "s";
        return sekundy + "s";
    }

    private static String kolor(String tekst) {
        return Msg.color(tekst);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("eventy.admin")) {
            if ("reload".startsWith(args[0].toLowerCase())) out.add("reload");
        }
        return out;
    }
}
