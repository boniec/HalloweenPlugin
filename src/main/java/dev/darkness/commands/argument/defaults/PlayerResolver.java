/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package dev.darkness.commands.argument.defaults;

import dev.darkness.commands.argument.ArgumentParseException;
import dev.darkness.commands.argument.ArgumentResolver;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlayerResolver
implements ArgumentResolver<Player> {
    @Override
    public Class<Player> getType() {
        return Player.class;
    }

    @Override
    public Player resolve(CommandSender sender, String input) throws ArgumentParseException {
        Player player = Bukkit.getPlayer((String)input);
        if (player == null) {
            throw new ArgumentParseException("player", input, Player.class);
        }
        return player;
    }

    @Override
    public List<String> suggest(CommandSender sender) {
        ArrayList<String> names = new ArrayList<String>();
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return names;
    }
}
