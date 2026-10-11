/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.commands.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ElementType.TYPE})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Command {
    public String name();

    public String[] aliases() default {};

    public String permission() default "";

    public String description() default "";

    public boolean playerOnly() default false;
}
