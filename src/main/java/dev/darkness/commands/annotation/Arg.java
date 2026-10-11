/*
 * Decompiled with CFR 0.152.
 */
package dev.darkness.commands.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ElementType.PARAMETER})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface Arg {
    public String value() default "";

    public boolean required() default true;
}
