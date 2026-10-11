/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.CommandSender
 */
package dev.darkness.commands.internal;

import dev.darkness.commands.annotation.Arg;
import dev.darkness.commands.annotation.Context;
import dev.darkness.commands.annotation.Execute;
import dev.darkness.commands.argument.ArgumentResolver;
import dev.darkness.commands.internal.ParameterInfo;
import dev.darkness.commands.internal.ResolverRegistry;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.CommandSender;

public class ExecuteMethodHandle {
    private final Method method;
    private final String subCommand;
    private final String permission;
    private final List<ParameterInfo> parameters;
    private final int minArgs;
    private final String autoUsage;

    public ExecuteMethodHandle(Method method, String commandName, ResolverRegistry resolverRegistry) {
        Execute execute = method.getAnnotation(Execute.class);
        this.method = method;
        this.method.setAccessible(true);
        this.subCommand = execute.value().trim();
        this.permission = execute.permission();
        this.parameters = new ArrayList<ParameterInfo>();
        int required = 0;
        for (Parameter p : method.getParameters()) {
            ParameterInfo info = this.buildParameterInfo(p, resolverRegistry);
            this.parameters.add(info);
            if (!info.isArg() || !info.isRequired()) continue;
            ++required;
        }
        this.minArgs = required;
        this.autoUsage = this.buildAutoUsage(commandName);
    }

    private ParameterInfo buildParameterInfo(Parameter p, ResolverRegistry resolverRegistry) {
        if (p.isAnnotationPresent(Context.class)) {
            return ParameterInfo.context(p.getType());
        }
        if (p.isAnnotationPresent(Arg.class)) {
            Arg arg = p.getAnnotation(Arg.class);
            String argName = arg.value().isEmpty() ? p.getName() : arg.value();
            boolean required = arg.required();
            Class<?> rawType = p.getType();
            boolean isOptional = rawType == Optional.class;
            Class<?> resolvedType = isOptional ? this.resolveOptionalGeneric(p) : rawType;
            ArgumentResolver<?> resolver = resolverRegistry.get(resolvedType);
            if (resolver == null) {
                throw new IllegalStateException();
            }
            return ParameterInfo.arg(argName, resolvedType, resolver, required && !isOptional, isOptional);
        }
        if (this.isContextType(p.getType())) {
            return ParameterInfo.context(p.getType());
        }
        throw new IllegalStateException();
    }

    private Class<?> resolveOptionalGeneric(Parameter p) {
        ParameterizedType paramType = (ParameterizedType)p.getParameterizedType();
        return (Class)paramType.getActualTypeArguments()[0];
    }

    private boolean isContextType(Class<?> type) {
        return CommandSender.class.isAssignableFrom(type);
    }

    private String buildAutoUsage(String commandName) {
        StringBuilder sb = new StringBuilder("/" + commandName);
        if (!this.subCommand.isEmpty()) {
            sb.append(" ").append(this.subCommand);
        }
        for (ParameterInfo param : this.parameters) {
            if (!param.isArg()) continue;
            if (param.isRequired()) {
                sb.append(" <").append(param.getName()).append(">");
                continue;
            }
            sb.append(" [").append(param.getName()).append("]");
        }
        return sb.toString();
    }

    public Method getMethod() {
        return this.method;
    }

    public String getSubCommand() {
        return this.subCommand;
    }

    public String getPermission() {
        return this.permission;
    }

    public List<ParameterInfo> getParameters() {
        return this.parameters;
    }

    public int getMinArgs() {
        return this.minArgs;
    }

    public String getAutoUsage() {
        return this.autoUsage;
    }

    public boolean isRootExecute() {
        return this.subCommand.isEmpty();
    }
}
