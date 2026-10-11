/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.CommandSender
 */
package dev.darkness.commands;

import org.bukkit.command.CommandSender;

public interface MessageProvider {
    public void sendOnlyPlayer(CommandSender var1);

    public void sendNoPermission(CommandSender var1, String var2);

    public void sendUsage(CommandSender var1, String var2);
}
