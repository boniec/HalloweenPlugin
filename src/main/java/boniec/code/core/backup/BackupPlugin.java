package boniec.code.core.backup;

import boniec.dev.inventory.Main;
import org.bukkit.plugin.java.JavaPlugin;

public class BackupPlugin {
    public static JavaPlugin getInstance() {
        return Main.getInstance();
    }
}