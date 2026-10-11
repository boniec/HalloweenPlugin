package boniec.helpop;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HelpOpCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public HelpOpCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "helpop.yml");
        if (!file.exists()) {
            plugin.saveResource("helpop.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("helpop.admin")) {
                sendRaw(sender, "&cBrak uprawnień.");
                return true;
            }
            reload();
            sendRaw(sender, "&#55FF88&l✔ &fPrzeładowano &7helpop.yml&f.");
            return true;
        }

        if (args.length == 0) {
            sendRaw(sender, cfg.getString("brak-tresci", "&c/helpop <wiadomość>"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        if (!player.hasPermission("helpop.use")) {
            sendRaw(player, "&cNie masz uprawnień.");
            return true;
        }

        int cdSec = Math.max(0, cfg.getInt("cooldown-sekundy", 60));
        if (cdSec > 0 && !player.hasPermission("helpop.bypass")) {
            long now = System.currentTimeMillis();
            Long last = cooldown.get(player.getUniqueId());
            if (last != null) {
                long leftMs = last + cdSec * 1000L - now;
                if (leftMs > 0) {
                    int leftSec = (int) Math.ceil(leftMs / 1000.0);
                    sendRaw(player, cfg.getString("wiadomosc-cooldown", "&cPoczekaj %sekundy%s.")
                            .replace("%sekundy%", String.valueOf(leftSec)));
                    return true;
                }
            }
        }

        String tresc = String.join(" ", args).trim();
        if (tresc.isEmpty()) {
            sendRaw(player, cfg.getString("brak-tresci", "&c/helpop <wiadomość>"));
            return true;
        }

        List<String> staffLinie = cfg.getStringList("staff");
        if (staffLinie.isEmpty()) {
            staffLinie = List.of(
                    "&#FF5555&lHELPOP &8» &f%gracz%",
                    "&7%tresc%",
                    ""
            );
        }

        boolean wyslano = false;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("helpop.notify")) {
                continue;
            }
            for (String linia : staffLinie) {
                sendRaw(staff, linia
                        .replace("%gracz%", player.getName())
                        .replace("%tresc%", tresc));
            }
            wyslano = true;
        }

        if (!wyslano) {
            sendRaw(player, cfg.getString("brak-adminow", "&eBrak adminów online."));
            return true;
        }

        cooldown.put(player.getUniqueId(), System.currentTimeMillis());
        sendRaw(player, cfg.getString("wiadomosc-gracz", "&aWysłano prośbę."));
        return true;
    }

    /** Tekst z helpop.yml — nie używać Msg.send(sciezka), bo to szuka klucza w config.yml klanów. */
    private static void sendRaw(CommandSender odbiorca, String tekst) {
        if (tekst == null || tekst.isEmpty()) {
            return;
        }
        odbiorca.sendMessage(Msg.color(tekst));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("helpop.admin")) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return List.of("reload");
            }
        }
        return new ArrayList<>();
    }
}
