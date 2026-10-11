package boniec.ekonomia.ogloszenia;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class OgloszeniaService {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public OgloszeniaService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "admintitle.yml");
        if (!file.exists()) {
            plugin.saveResource("admintitle.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void wyslijTitle(String tresc) {
        String title = Msg.color(cfg.getString("title.tytul", "&eOgłoszenie"));
        int fadeIn = cfg.getInt("title.fade-in", 20);
        int stay = cfg.getInt("title.stay", 100);
        int fadeOut = cfg.getInt("title.fade-out", 20);
        Sound sound = parseSound(cfg.getString("title.dzwiek", "ENTITY_ENDER_DRAGON_GROWL"));
        float vol = (float) cfg.getDouble("title.glosnosc", 1.0);
        float pitch = (float) cfg.getDouble("title.ton", 1.0);
        String subtitle = Msg.color(tresc);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!boniec.ustawienia.UstawieniaManager.on(p, boniec.ustawienia.UstawieniaKlucz.OGLOSZENIA)) continue;
            p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            if (sound != null) {
                p.playSound(p.getLocation(), sound, vol, pitch);
            }
        }
    }

    public void wyslijChat(String tresc) {
        String colored = Msg.color(tresc);
        for (String linia : cfg.getStringList("chat.linie")) {
            String tekst = Msg.color(linia.replace("%tresc%", colored));
            boniec.ustawienia.UstawieniaManager.broadcast(boniec.ustawienia.UstawieniaKlucz.OGLOSZENIA, tekst);
        }
    }

    public String potwierdzenieTitle() {
        return Msg.color(cfg.getString("potwierdzenie-title", "&aWysłano title."));
    }

    public String potwierdzenieChat() {
        return Msg.color(cfg.getString("potwierdzenie-chat", "&aWysłano chat."));
    }

    private static Sound parseSound(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Sound.ENTITY_ENDER_DRAGON_GROWL;
        }
    }
}
