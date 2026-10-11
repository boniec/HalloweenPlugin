/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.commands.argument;

public class ArgumentParseException
extends Exception {
    private final String argName;

    public ArgumentParseException(String argName, String input, Class<?> expectedType) {
        super("cannot parse '" + input + "' (" + expectedType.getSimpleName() + ") for '" + argName + "'");
        this.argName = argName;
    }

    public String getArgName() {
        return this.argName;
    }
}
