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

public class FloatResolver
implements ArgumentResolver<Float> {
    @Override
    public Class<Float> getType() {
        return Float.class;
    }

    @Override
    public Float resolve(CommandSender sender, String input) throws ArgumentParseException {
        try {
            return Float.valueOf(Float.parseFloat(input));
        }
        catch (NumberFormatException e) {
            throw new ArgumentParseException("float", input, Float.class);
        }
    }

    @Override
    public List<String> suggest(CommandSender sender) {
        return Collections.emptyList();
    }
}
