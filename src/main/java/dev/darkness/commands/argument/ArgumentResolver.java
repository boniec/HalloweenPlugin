/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.CommandSender
 */
package dev.darkness.commands.argument;

import dev.darkness.commands.argument.ArgumentParseException;
import java.util.List;
import org.bukkit.command.CommandSender;

public interface ArgumentResolver<T> {
    public Class<T> getType();

    public T resolve(CommandSender var1, String var2) throws ArgumentParseException;

    public List<String> suggest(CommandSender var1);
}
