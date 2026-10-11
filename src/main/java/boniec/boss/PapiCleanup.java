package boniec.boss;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wyrejestrowywanie starych ekspansji PlaceholderAPI po przeladowaniu pluginu.
 */
public final class PapiCleanup {

    private PapiCleanup() {
    }

    public static void wyrejestruj(JavaPlugin plugin, String... identyfikatory) {
        try {
            var manager = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager();
            for (String id : identyfikatory) {
                PlaceholderExpansion stara = manager.getExpansion(id);
                if (stara != null) {
                    stara.unregister();
                    plugin.getLogger().info("Wyrejestrowano stara ekspansje PlaceholderAPI: " + id);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Nie udalo sie sprawdzic starych ekspansji PAPI: " + t);
        }
    }
}