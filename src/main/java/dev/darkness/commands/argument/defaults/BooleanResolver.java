/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.CommandSender
 */
package dev.darkness.commands.argument.defaults;

import dev.darkness.commands.argument.ArgumentParseException;
import dev.darkness.commands.argument.ArgumentResolver;
import java.util.Arrays;
import java.util.List;
import org.bukkit.command.CommandSender;

public class BooleanResolver
implements ArgumentResolver<Boolean> {
    private static final List<String> SUGGESTIONS = Arrays.asList("true", "false");

    @Override
    public Class<Boolean> getType() {
        return Boolean.class;
    }

    @Override
    public Boolean resolve(CommandSender sender, String input) throws ArgumentParseException {
        if (input.equalsIgnoreCase("true")) {
            return true;
        }
        if (input.equalsIgnoreCase("false")) {
            return false;
        }
        throw new ArgumentParseException("boolean", input, Boolean.class);
    }

    @Override
    public List<String> suggest(CommandSender sender) {
        return SUGGESTIONS;
    }
}
