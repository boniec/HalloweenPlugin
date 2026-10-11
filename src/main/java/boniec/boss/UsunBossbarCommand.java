package boniec.boss;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /usunbossbar - usuwa wszystkie zbedne bossbary pluginu (Minotaur, TurboDrop,
 * event odlamkow, godziny bogow), ktore zostaly na ekranach graczy po przeladowaniu pluginu.
 *
 * Dziala na paskach zarejestrowanych w serwerze (KeyedBossBar) - patrz {@link BossbarUtil}.
 * Po usunieciu zywe eventy (boss, turbodrop) NIE sa przerywane - ich paski pojawia sie
 * ponownie przy nastepnym odswiezeniu / respawnie. Do calkowitego wylaczenia uzyj
 * /adminboss kill, /gornik turbodrop off, /eventodlamki stop.
 */
public class UsunBossbarCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public UsunBossbarCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boss.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnien.");
            return true;
        }

        int usuniete = BossbarUtil.usunWszystkie(plugin);
        sender.sendMessage(ChatColor.GREEN + "Usunieto " + ChatColor.YELLOW + usuniete
                + ChatColor.GREEN + " bossbar(ow) pluginu.");
        return true;
    }
}
