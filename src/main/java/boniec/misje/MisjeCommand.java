package boniec.misje;

import boniec.klany.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class MisjeCommand implements CommandExecutor, TabCompleter {

    private final MisjeManager manager;
    private final MisjeGui gui;

    public MisjeCommand(MisjeManager manager, MisjeGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.color(manager.txt("wiadomosci.tylko-gracz",
                    "&8[&f亄&8] &f亢 &cᴛᴀ ᴋᴏᴍᴇɴᴅᴀ ᴏᴛᴡɪᴇʀᴀ ɢᴜɪ — ᴜᴢʏᴊ ᴊᴇᴊ ᴡ ɢʀᴢᴇ.")));
            return true;
        }
        gui.otworz(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
