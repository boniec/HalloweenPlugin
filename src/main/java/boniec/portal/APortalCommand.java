package boniec.portal;

import boniec.klany.util.Msg;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class APortalCommand implements CommandExecutor, TabCompleter {

    private static final String P = "&8[&f丆&8] ";

    private final PortalManager manager;
    private final PortalListener listener;

    public APortalCommand(PortalManager manager, PortalListener listener) {
        this.manager = manager;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("portal.admin")) {
            sender.sendMessage(Msg.color(P + "&f亢 &cɴɪᴇ ᴍᴀꜱᴢ ᴜᴘʀᴀᴡɴɪᴇɴ."));
            return true;
        }
        if (args.length == 0) {
            pomoc(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cᴛʏʟᴋᴏ ᴡ ɢʀᴢᴇ."));
                    return true;
                }
                player.getInventory().addItem(listener.stworzWand());
                player.sendMessage(Msg.color(P + "&f両 &aᴅᴏꜱᴛᴀʟᴇꜱ ᴡᴀɴᴅ. &7ʟᴘᴍ / ᴘᴘᴍ ɴᴀ ʙʟᴏᴋɪ."));
            }
            case "stworz", "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cᴛʏʟᴋᴏ ᴡ ɢʀᴢᴇ."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Msg.color(P + "&f乁 &#FFC34D/aportal stworz <nazwa>"));
                    return true;
                }
                if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cɴᴇᴛʜᴇʀ ᴊᴇꜱᴛ ᴡʏʟᴀᴄᴢᴏɴʏ."));
                    return true;
                }
                String nazwa = args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
                if (nazwa.isEmpty()) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cᴢʟᴀ ɴᴀᴢᴡᴀ."));
                    return true;
                }
                PortalManager.Zaznaczenie z = manager.zaznaczenie(player.getUniqueId());
                if (z.pos1 == null || z.pos2 == null) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cᴢᴀᴢɴᴀᴄᴢ ᴅᴡᴀ ʀᴏɢɪ ᴡᴀɴᴅᴇᴍ. &7/aportal wand"));
                    return true;
                }
                if (z.pos1.getWorld() == null || z.pos2.getWorld() == null
                        || !z.pos1.getWorld().equals(z.pos2.getWorld())) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cᴏʙᴀ ʀᴏɢɪ ᴍᴜꜱᴢᴀ ʙʏᴄ ᴡ ᴛʏᴍ ꜱᴀᴍʏᴍ ꜱᴡɪᴇᴄɪᴇ."));
                    return true;
                }
                Location cel = player.getLocation();
                Portal portal = new Portal(
                        nazwa,
                        z.pos1.getWorld().getName(),
                        z.pos1.getBlockX(), z.pos1.getBlockY(), z.pos1.getBlockZ(),
                        z.pos2.getBlockX(), z.pos2.getBlockY(), z.pos2.getBlockZ(),
                        cel.getWorld().getName(),
                        cel.getX(), cel.getY(), cel.getZ(), cel.getYaw(), cel.getPitch());
                manager.zapisz(portal);
                player.sendMessage(Msg.color(P + "&f両 &aᴘᴏʀᴛᴀʟ &#FFC34D" + nazwa
                        + " &a— ᴛᴇᴘᴀ ɴᴀ ᴛᴡᴏᴊᴀ ᴘᴏᴢʏᴄᴊᴇ."));
            }
            case "cel", "dest" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cᴛʏʟᴋᴏ ᴡ ɢʀᴢᴇ."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Msg.color(P + "&f乁 &#FFC34D/aportal cel <nazwa>"));
                    return true;
                }
                if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cɴᴇᴛʜᴇʀ ᴊᴇꜱᴛ ᴡʏʟᴀᴄᴢᴏɴʏ."));
                    return true;
                }
                Portal stary = manager.get(args[1]);
                if (stary == null) {
                    sender.sendMessage(Msg.color(P + "&f亢 &cɴɪᴇ ᴍᴀ ᴘᴏʀᴛᴀʟᴜ &f" + args[1]));
                    return true;
                }
                Location cel = player.getLocation();
                Portal nowy = new Portal(
                        stary.nazwa, stary.world,
                        stary.minX, stary.minY, stary.minZ, stary.maxX, stary.maxY, stary.maxZ,
                        cel.getWorld().getName(),
                        cel.getX(), cel.getY(), cel.getZ(), cel.getYaw(), cel.getPitch());
                manager.zapisz(nowy);
                player.sendMessage(Msg.color(P + "&f両 &aɴᴏᴡʏ ᴄᴇʟ ᴘᴏʀᴛᴀʟᴜ &#FFC34D" + stary.nazwa));
            }
            case "usun", "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.color(P + "&f乁 &#FFC34D/aportal usun <nazwa>"));
                    return true;
                }
                if (manager.usun(args[1])) {
                    sender.sendMessage(Msg.color(P + "&f両 &aᴜꜱᴜɴɪᴇᴛᴏ ᴘᴏʀᴛᴀʟ &#FFC34D" + args[1].toLowerCase(Locale.ROOT)));
                } else {
                    sender.sendMessage(Msg.color(P + "&f亢 &cɴɪᴇ ᴍᴀ ᴘᴏʀᴛᴀʟᴜ &f" + args[1]));
                }
            }
            case "lista", "list" -> {
                List<Portal> lista = manager.wszystkie();
                if (lista.isEmpty()) {
                    sender.sendMessage(Msg.color(P + "&7ʙʀᴀᴋ ᴘᴏʀᴛᴀʟɪ."));
                    return true;
                }
                sender.sendMessage(Msg.color(P + "&f亄 &#FFC34Dᴘᴏʀᴛᴀʟᴇ&8:"));
                for (Portal p : lista) {
                    sender.sendMessage(Msg.color("&8x &f" + p.nazwa + " &8· &7" + p.world
                            + " " + p.minX + "," + p.minY + "," + p.minZ
                            + " &8» &7" + p.destWorld));
                }
            }
            default -> pomoc(sender);
        }
        return true;
    }

    private void pomoc(CommandSender sender) {
        sender.sendMessage(Msg.color(P + "&f乁 &#FFC34D/aportal wand"));
        sender.sendMessage(Msg.color(P + "&f乁 &#FFC34D/aportal stworz <nazwa> &7(zaznaczenie + tu tepasz)"));
        sender.sendMessage(Msg.color(P + "&f乁 &#FFC34D/aportal cel <nazwa>"));
        sender.sendMessage(Msg.color(P + "&f乁 &#FFC34D/aportal usun <nazwa>"));
        sender.sendMessage(Msg.color(P + "&f乁 &#FFC34D/aportal lista"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("portal.admin")) return out;
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("wand", "stworz", "cel", "usun", "lista")) {
                if (s.startsWith(p)) out.add(s);
            }
        } else if (args.length == 2 && List.of("cel", "usun", "stworz", "dest", "delete").contains(args[0].toLowerCase(Locale.ROOT))) {
            String p = args[1].toLowerCase(Locale.ROOT);
            for (Portal portal : manager.wszystkie()) {
                if (portal.nazwa.startsWith(p)) out.add(portal.nazwa);
            }
        }
        return out;
    }
}
