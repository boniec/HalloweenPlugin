package boniec.misje;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AMisjeCommand implements CommandExecutor, TabCompleter {

    private final MisjeManager manager;
    private final MisjeGui gui;

    public AMisjeCommand(MisjeManager manager, MisjeGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("misje.admin")) {
            sender.sendMessage(Msg.color(manager.txt("wiadomosci.brak-uprawnien",
                    "&8[&f亄&8] &f亢 &cɴɪᴇ ᴍᴀꜱᴢ ᴜᴘʀᴀᴡɴɪᴇɴ.")));
            return true;
        }
        if (args.length == 0) {
            for (String linia : manager.getCfg().getStringList("wiadomosci.pomoc-admin")) {
                sender.sendMessage(Msg.color(linia));
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                manager.wczytaj();
                sender.sendMessage(Msg.color(manager.txt("wiadomosci.przeladowano",
                        "&8[&f亄&8] &f両 &aᴘʀᴢᴇʟᴀᴅᴏᴡᴀɴᴏ ᴍɪꜱᴊᴇ.ʏᴍʟ")));
            }
            case "ustawnagroda" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.color("&8[&f亄&8] &f乁 &#FFC34D/amisje ustawnagroda <id>"));
                    sender.sendMessage(Msg.color("&7  ɪᴅ: &f" + String.join("&8, &f", manager.getMisje().keySet())));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Msg.color(manager.txt("wiadomosci.tylko-gracz",
                            "&8[&f亄&8] &f亢 &cᴛᴀ ᴋᴏᴍᴇɴᴅᴀ ᴏᴛᴡɪᴇʀᴀ ɢᴜɪ — ᴜᴢʏᴊ ᴊᴇᴊ ᴡ ɢʀᴢᴇ.")));
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (manager.getMisja(id) == null) {
                    sender.sendMessage(Msg.color(manager.txt("wiadomosci.nieznana-misja",
                            "&8[&f亄&8] &f亢 &cɴɪᴇᴢɴᴀɴᴀ ᴍɪꜱᴊᴀ: &f%id%").replace("%id%", id)));
                    return true;
                }
                gui.otworzEdytor(player, id);
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.color("&8[&f亄&8] &f乁 &#FFC34D/amisje reset <gracz>"));
                    return true;
                }
                Player cel = Bukkit.getPlayerExact(args[1]);
                if (cel == null) {
                    sender.sendMessage(Msg.color("&8[&f亄&8] &f亢 &cɴɪᴇ ᴍᴀ ɢʀᴀᴄᴢᴀ ᴏɴʟɪɴᴇ."));
                    return true;
                }
                manager.resetGracza(cel.getUniqueId());
                sender.sendMessage(Msg.color(manager.txt("wiadomosci.zresetowano-gracza",
                        "&8[&f亄&8] &f両 &aᴢʀᴇꜱᴇᴛᴏᴡᴀɴᴏ ᴍɪꜱᴊᴇ ɢʀᴀᴄᴢᴀ &f%gracz%")
                        .replace("%gracz%", cel.getName())));
            }
            default -> {
                for (String linia : manager.getCfg().getStringList("wiadomosci.pomoc-admin")) {
                    sender.sendMessage(Msg.color(linia));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("misje.admin")) return out;
        if (args.length == 1) {
            for (String s : List.of("ustawnagroda", "reload", "reset")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ustawnagroda")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            for (String id : manager.getMisje().keySet()) {
                if (id.startsWith(p)) out.add(id);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(pl.getName());
            }
        }
        return out;
    }
}
