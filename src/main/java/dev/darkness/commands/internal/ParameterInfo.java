/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.commands.internal;

import dev.darkness.commands.argument.ArgumentResolver;

public class ParameterInfo {
    private final String name;
    private final Class<?> type;
    private final ArgumentResolver<?> resolver;
    private final boolean arg;
    private final boolean required;
    private final boolean optional;

    private ParameterInfo(String name, Class<?> type, ArgumentResolver<?> resolver, boolean arg, boolean required, boolean optional) {
        this.name = name;
        this.type = type;
        this.resolver = resolver;
        this.arg = arg;
        this.required = required;
        this.optional = optional;
    }

    public static ParameterInfo context(Class<?> type) {
        return new ParameterInfo(null, type, null, false, false, false);
    }

    public static ParameterInfo arg(String name, Class<?> type, ArgumentResolver<?> resolver, boolean required, boolean optional) {
        return new ParameterInfo(name, type, resolver, true, required, optional);
    }

    public String getName() {
        return this.name;
    }

    public Class<?> getType() {
        return this.type;
    }

    public ArgumentResolver<?> getResolver() {
        return this.resolver;
    }

    public boolean isArg() {
        return this.arg;
    }

    public boolean isRequired() {
        return this.required;
    }

    public boolean isOptional() {
        return this.optional;
    }
}
