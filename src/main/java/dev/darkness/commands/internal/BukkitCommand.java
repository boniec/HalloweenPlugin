/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package dev.darkness.commands.internal;

import dev.darkness.commands.MessageProvider;
import dev.darkness.commands.annotation.Command;
import dev.darkness.commands.annotation.Execute;
import dev.darkness.commands.argument.ArgumentParseException;
import dev.darkness.commands.internal.ExecuteMethodHandle;
import dev.darkness.commands.internal.ParameterInfo;
import dev.darkness.commands.internal.ResolverRegistry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BukkitCommand
extends org.bukkit.command.Command {
    private static final Logger LOGGER = Logger.getLogger("777-Commands");
    private final Object commandInstance;
    private final Command info;
    private final List<ExecuteMethodHandle> handles;
    private final MessageProvider messageProvider;

    public BukkitCommand(Object instance, MessageProvider messageProvider, ResolverRegistry resolverRegistry) {
        super("");
        this.commandInstance = instance;
        this.messageProvider = messageProvider;
        this.info = instance.getClass().getAnnotation(Command.class);
        if (this.info == null) {
            throw new IllegalStateException("missing @Command on " + instance.getClass().getName());
        }
        this.handles = new ArrayList<ExecuteMethodHandle>();
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Execute.class)) continue;
            this.handles.add(new ExecuteMethodHandle(method, this.info.name(), resolverRegistry));
        }
        if (this.handles.isEmpty()) {
            throw new IllegalStateException("no @Execute in " + instance.getClass().getName());
        }
        this.setName(this.info.name());
        this.setAliases(Arrays.asList(this.info.aliases()));
        if (!this.info.description().isEmpty()) {
            this.setDescription(this.info.description());
        }
        if (!this.info.permission().isEmpty()) {
            this.setPermission(this.info.permission());
        }
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String[] methodArgs;
        if (this.info.playerOnly() && !(sender instanceof Player)) {
            this.messageProvider.sendOnlyPlayer(sender);
            return true;
        }
        if (this.getPermission() != null && !this.getPermission().isEmpty() && !sender.hasPermission(this.getPermission())) {
            this.messageProvider.sendNoPermission(sender, this.getPermission());
            return true;
        }
        ExecuteMethodHandle handle = this.resolveHandle(args);
        if (handle == null) {
            this.messageProvider.sendUsage(sender, this.buildAllUsages());
            return true;
        }
        String subPerm = handle.getPermission();
        if (!subPerm.isEmpty() && !sender.hasPermission(subPerm)) {
            this.messageProvider.sendNoPermission(sender, subPerm);
            return true;
        }
        String[] stringArray = methodArgs = handle.isRootExecute() ? args : Arrays.copyOfRange(args, 1, args.length);
        if (methodArgs.length < handle.getMinArgs()) {
            this.messageProvider.sendUsage(sender, handle.getAutoUsage());
            return true;
        }
        try {
            Object[] invokeArgs = this.prepareArgs(sender, methodArgs, handle);
            handle.getMethod().invoke(this.commandInstance, invokeArgs);
        }
        catch (ArgumentParseException e) {
            this.messageProvider.sendUsage(sender, handle.getAutoUsage());
        }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "error executing command /" + this.info.name(), e);
        }
        return true;
    }

    private ExecuteMethodHandle resolveHandle(String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            for (ExecuteMethodHandle handle : this.handles) {
                if (!handle.getSubCommand().equalsIgnoreCase(sub)) continue;
                return handle;
            }
        }
        for (ExecuteMethodHandle handle : this.handles) {
            if (!handle.isRootExecute()) continue;
            return handle;
        }
        return null;
    }

    private Object[] prepareArgs(CommandSender sender, String[] args, ExecuteMethodHandle handle) throws ArgumentParseException {
        List<ParameterInfo> params = handle.getParameters();
        Object[] values = new Object[params.size()];
        int argIndex = 0;
        for (int i = 0; i < params.size(); ++i) {
            ParameterInfo param = params.get(i);
            if (!param.isArg()) {
                values[i] = this.castSender(sender, param.getType());
                continue;
            }
            if (argIndex < args.length) {
                Object resolved = param.getResolver().resolve(sender, args[argIndex++]);
                values[i] = param.isOptional() ? Optional.of(resolved) : resolved;
                continue;
            }
            values[i] = param.isOptional() ? Optional.empty() : null;
        }
        return values;
    }

    private Object castSender(CommandSender sender, Class<?> type) {
        if (type.isAssignableFrom(sender.getClass())) {
            return type.cast(sender);
        }
        return sender;
    }

    private String buildAllUsages() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.handles.size(); ++i) {
            sb.append(this.handles.get(i).getAutoUsage());
            if (i >= this.handles.size() - 1) continue;
            sb.append("\n");
        }
        return sb.toString();
    }

    @NotNull
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            ArrayList<String> suggestions = new ArrayList<String>();
            for (ExecuteMethodHandle handle : this.handles) {
                String sub;
                if (handle.isRootExecute() || !(sub = handle.getSubCommand()).toLowerCase().startsWith(args[0].toLowerCase())) continue;
                suggestions.add(sub);
            }
            if (!suggestions.isEmpty()) {
                return suggestions;
            }
            ExecuteMethodHandle rootHandle = this.getRootHandle();
            if (rootHandle != null) {
                return this.getArgSuggestions(sender, args, rootHandle, 0);
            }
            return Collections.emptyList();
        }
        ExecuteMethodHandle handle = this.resolveHandleForTab(args);
        if (handle == null) {
            return Collections.emptyList();
        }
        int argOffset = handle.isRootExecute() ? 0 : 1;
        int paramArgIndex = args.length - 1 - argOffset;
        return this.getArgSuggestions(sender, args, handle, paramArgIndex);
    }

    private List<String> getArgSuggestions(CommandSender sender, String[] args, ExecuteMethodHandle handle, int paramArgIndex) {
        List<ParameterInfo> params = handle.getParameters();
        int currentParamArg = 0;
        for (ParameterInfo param : params) {
            if (!param.isArg()) continue;
            if (currentParamArg == paramArgIndex) {
                String current = args[args.length - 1].toLowerCase();
                List<String> all = param.getResolver().suggest(sender);
                ArrayList<String> filtered = new ArrayList<String>();
                for (String s : all) {
                    if (!s.toLowerCase().startsWith(current)) continue;
                    filtered.add(s);
                }
                return filtered;
            }
            ++currentParamArg;
        }
        return Collections.emptyList();
    }

    private ExecuteMethodHandle resolveHandleForTab(String[] args) {
        if (args.length > 1) {
            String sub = args[0].toLowerCase();
            for (ExecuteMethodHandle handle : this.handles) {
                if (!handle.getSubCommand().equalsIgnoreCase(sub)) continue;
                return handle;
            }
        }
        return this.getRootHandle();
    }

    private ExecuteMethodHandle getRootHandle() {
        for (ExecuteMethodHandle handle : this.handles) {
            if (!handle.isRootExecute()) continue;
            return handle;
        }
        return null;
    }
}
