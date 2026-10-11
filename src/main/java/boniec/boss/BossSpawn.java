package boniec.boss;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/**
 * Spawn bossow (Goblin, Zeus, Minotaur) omija blokady mobow: SwiatKontrola, flage WorldGuard
 * mob-spawning / deny-spawn i inne pluginy anulujace spawn. Dotyczy WYLACZNIE spawnu
 * wykonanego wewnatrz {@link #pozwol(Supplier)} - zwykle moby nadal sa blokowane.
 */
public final class BossSpawn implements Listener {

    private static int glebokosc;
    private static NamespacedKey klucz;

    private BossSpawn() {}

    public static void init(JavaPlugin plugin) {
        klucz = new NamespacedKey(plugin, "flame_boss");
        Bukkit.getPluginManager().registerEvents(new BossSpawn(), plugin);
    }

    /** Wykonuje spawn z pominieciem blokad; zwrocona encja (jesli jest) dostaje znacznik bossa. */
    public static <T extends Entity> T pozwol(Supplier<T> spawn) {
        T entity = bezBlokad(spawn);
        oznacz(entity);
        return entity;
    }

    /** Jak {@link #pozwol}, ale bez znacznika - dla spawnow zwracajacych wrapper (np. MythicMobs ActiveMob). */
    public static <T> T bezBlokad(Supplier<T> spawn) {
        glebokosc++;
        try {
            return spawn.get();
        } finally {
            glebokosc--;
        }
    }

    public static boolean trwa() {
        return glebokosc > 0;
    }

    public static void oznacz(Entity entity) {
        if (entity == null || klucz == null) return;
        entity.getPersistentDataContainer().set(klucz, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean jestBossem(Entity entity) {
        return entity != null && klucz != null
                && entity.getPersistentDataContainer().has(klucz, PersistentDataType.BYTE);
    }

    /** CreatureSpawnEvent dziedziczy po EntitySpawnEvent, wiec ten handler lapie oba. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent event) {
        if (trwa() && event.isCancelled()) {
            event.setCancelled(false);
        }
    }
}
