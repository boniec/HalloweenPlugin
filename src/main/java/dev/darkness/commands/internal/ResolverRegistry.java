/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.commands.internal;

import dev.darkness.commands.argument.ArgumentResolver;
import java.util.HashMap;
import java.util.Map;

public class ResolverRegistry {
    private final Map<Class<?>, ArgumentResolver<?>> resolvers = new HashMap();

    public <T> void register(ArgumentResolver<T> resolver) {
        this.resolvers.put(resolver.getType(), resolver);
        Class<?> primitive = this.getPrimitive(resolver.getType());
        if (primitive != null) {
            this.resolvers.put(primitive, resolver);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> ArgumentResolver<T> get(Class<T> type) {
        return (ArgumentResolver<T>) this.resolvers.get(type);
    }

    public boolean has(Class<?> type) {
        return this.resolvers.containsKey(type);
    }

    private Class<?> getPrimitive(Class<?> boxed) {
        if (boxed == Integer.class) {
            return Integer.TYPE;
        }
        if (boxed == Double.class) {
            return Double.TYPE;
        }
        if (boxed == Float.class) {
            return Float.TYPE;
        }
        if (boxed == Long.class) {
            return Long.TYPE;
        }
        if (boxed == Boolean.class) {
            return Boolean.TYPE;
        }
        if (boxed == Short.class) {
            return Short.TYPE;
        }
        if (boxed == Byte.class) {
            return Byte.TYPE;
        }
        if (boxed == Character.class) {
            return Character.TYPE;
        }
        return null;
    }
}
