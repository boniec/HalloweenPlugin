/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandMap
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.darkness.commands;

import dev.darkness.commands.MessageProvider;
import dev.darkness.commands.argument.ArgumentResolver;
import dev.darkness.commands.argument.defaults.BooleanResolver;
import dev.darkness.commands.argument.defaults.DoubleResolver;
import dev.darkness.commands.argument.defaults.FloatResolver;
import dev.darkness.commands.argument.defaults.IntegerResolver;
import dev.darkness.commands.argument.defaults.LongResolver;
import dev.darkness.commands.argument.defaults.PlayerResolver;
import dev.darkness.commands.argument.defaults.StringResolver;
import dev.darkness.commands.internal.BukkitCommand;
import dev.darkness.commands.internal.ResolverRegistry;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandRegistry {
    private final JavaPlugin plugin;
    private final MessageProvider messageProvider;
    private final ResolverRegistry resolverRegistry;
    private final CommandMap commandMap;

    public CommandRegistry(JavaPlugin plugin, MessageProvider messageProvider) {
        this.plugin = plugin;
        this.messageProvider = messageProvider;
        this.resolverRegistry = new ResolverRegistry();
        this.commandMap = Bukkit.getServer().getCommandMap();
        this.registerDefaults();
    }

    private void registerDefaults() {
        this.resolverRegistry.register(new StringResolver());
        this.resolverRegistry.register(new IntegerResolver());
        this.resolverRegistry.register(new DoubleResolver());
        this.resolverRegistry.register(new FloatResolver());
        this.resolverRegistry.register(new LongResolver());
        this.resolverRegistry.register(new BooleanResolver());
        this.resolverRegistry.register(new PlayerResolver());
    }

    public <T> CommandRegistry resolver(ArgumentResolver<T> resolver) {
        this.resolverRegistry.register(resolver);
        return this;
    }

    public CommandRegistry register(Object commandInstance) {
        BukkitCommand command = new BukkitCommand(commandInstance, this.messageProvider, this.resolverRegistry);
        this.commandMap.register(this.plugin.getName(), (Command)command);
        return this;
    }

    public void sync() {
        try {
            Method syncMethod = this.plugin.getServer().getClass().getMethod("syncCommands", new Class[0]);
            syncMethod.invoke((Object)this.plugin.getServer(), new Object[0]);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}
