/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.CommandSender
 */
package dev.darkness.commands.argument.defaults;

import dev.darkness.commands.argument.ArgumentParseException;
import dev.darkness.commands.argument.ArgumentResolver;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.CommandSender;

public class IntegerResolver
implements ArgumentResolver<Integer> {
    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }

    @Override
    public Integer resolve(CommandSender sender, String input) throws ArgumentParseException {
        try {
            return Integer.parseInt(input);
        }
        catch (NumberFormatException e) {
            throw new ArgumentParseException("integer", input, Integer.class);
        }
    }

    @Override
    public List<String> suggest(CommandSender sender) {
        return Collections.emptyList();
    }
}
