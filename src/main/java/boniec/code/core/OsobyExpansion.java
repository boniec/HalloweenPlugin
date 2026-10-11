package boniec.code.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * %osoby% i %osoby_online% = gracze na tej instancji x 3 (10 osob = 30).
 */
public class OsobyExpansion extends PlaceholderExpansion {

    public static final int MNOZNIK = 3;

    private final JavaPlugin plugin;

    public OsobyExpansion(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static String wartosc() {
        return String.valueOf(Bukkit.getOnlinePlayers().size() * MNOZNIK);
    }

    public static String podmien(String tekst) {
        if (tekst == null || tekst.indexOf('%') < 0) {
            return tekst;
        }
        String n = wartosc();
        return tekst.replace("%osoby%", n).replace("%osoby_online%", n);
    }

    public static boolean zarejestruj(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return false;
        }
        try {
            boniec.boss.PapiCleanup.wyrejestruj(plugin, "osoby");
            OsobyExpansion exp = new OsobyExpansion(plugin);
            boolean ok = exp.register();
            if (ok) {
                plugin.getLogger().info("Placeholder %osoby% aktywny (online x" + MNOZNIK + ").");
            } else {
                plugin.getLogger().warning("Nie udalo sie zarejestrowac %osoby% w PlaceholderAPI.");
            }
            return ok;
        } catch (Throwable t) {
            plugin.getLogger().warning("Blad rejestracji %osoby%: " + t.getMessage());
            return false;
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "osoby";
    }

    @Override
    public @NotNull String getAuthor() {
        return "boniec";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String getRequiredPlugin() {
        return plugin.getName();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return wartosc();
    }

    @Override
    public String onPlaceholderRequest(org.bukkit.entity.Player player, @NotNull String params) {
        return wartosc();
    }
}
