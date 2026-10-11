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

public class StringResolver
implements ArgumentResolver<String> {
    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String resolve(CommandSender sender, String input) throws ArgumentParseException {
        return input;
    }

    @Override
    public List<String> suggest(CommandSender sender) {
        return Collections.emptyList();
    }
}
