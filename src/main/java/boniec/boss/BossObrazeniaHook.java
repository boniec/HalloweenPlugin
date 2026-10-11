package boniec.boss;

import org.bukkit.entity.Player;

import java.util.function.ObjDoubleConsumer;

/** Zglasza obrazenia zadane bossom (w HP widocznym na bossbarze) np. do osiagniec. */
public final class BossObrazeniaHook {

    private static ObjDoubleConsumer<Player> sluchacz;

    private BossObrazeniaHook() {}

    public static void ustaw(ObjDoubleConsumer<Player> nowy) {
        sluchacz = nowy;
    }

    public static void zglos(Player gracz, double realneObrazenia, double mnoznik) {
        if (sluchacz == null || gracz == null || realneObrazenia <= 0) return;
        sluchacz.accept(gracz, mnoznik > 0 ? realneObrazenia / mnoznik : realneObrazenia);
    }
}
