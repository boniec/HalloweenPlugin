package boniec.online;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlameOnlinePlugin extends JavaPlugin {

    private OnlineCounts counts;
    private OnlineExpansion expansion;
    private OsobyExpansion osobyExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        counts = new OnlineCounts(this);
        counts.start();
        Bukkit.getPluginManager().registerEvents(new MotdListener(this), this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new OnlineExpansion(this);
            expansion.register();
            osobyExpansion = new OsobyExpansion(this);
            osobyExpansion.register();
            getLogger().info("PAPI: %osoby% %online_skypvp% %online_all% | this-server="
                    + getConfig().getString("this-server", "?") + " x" + counts.mnoznik());
        } else {
            getLogger().warning("Brak PlaceholderAPI — placeholdery wylaczone, MOTD dziala.");
        }
        if (getCommand("flameonline") != null) {
            getCommand("flameonline").setExecutor(this);
        }
        ChatLock chat = new ChatLock(this);
        Bukkit.getPluginManager().registerEvents(chat, this);
        if (getCommand("chat") != null) {
            getCommand("chat").setExecutor(chat);
            getCommand("chat").setTabCompleter(chat);
        }
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
        if (osobyExpansion != null) {
            osobyExpansion.unregister();
        }
    }

    public OnlineCounts counts() {
        return counts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("flameonline.admin")) {
            sender.sendMessage("Brak permisji.");
            return true;
        }
        reloadConfig();
        counts.reload();
        sender.sendMessage("FlameOnline przeladowany. Mnoznik x" + counts.mnoznik());
        return true;
    }
}
