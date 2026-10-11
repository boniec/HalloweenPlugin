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

public class DoubleResolver
implements ArgumentResolver<Double> {
    @Override
    public Class<Double> getType() {
        return Double.class;
    }

    @Override
    public Double resolve(CommandSender sender, String input) throws ArgumentParseException {
        try {
            return Double.parseDouble(input);
        }
        catch (NumberFormatException e) {
            throw new ArgumentParseException("double", input, Double.class);
        }
    }

    @Override
    public List<String> suggest(CommandSender sender) {
        return Collections.emptyList();
    }
}
