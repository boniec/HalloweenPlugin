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

public class LongResolver
implements ArgumentResolver<Long> {
    @Override
    public Class<Long> getType() {
        return Long.class;
    }

    @Override
    public Long resolve(CommandSender sender, String input) throws ArgumentParseException {
        try {
            return Long.parseLong(input);
        }
        catch (NumberFormatException e) {
            throw new ArgumentParseException("long", input, Long.class);
        }
    }

    @Override
    public List<String> suggest(CommandSender sender) {
        return Collections.emptyList();
    }
}
