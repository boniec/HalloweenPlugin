package boniec.dev.polezamek.util;

import boniec.dev.polezamek.Main;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ochrona OSOBY przed "podwojnym" dzialaniem pola smierci. Jesli na gracza zadzialalo
 * pole smierci, to NASTEPNE pole (ktorejkolwiek osoby) nie zadziala na niego
 * dopiero po {@code ochrona-celu-sekundy} (domyslnie 60s) - liczone od momentu,
 * w ktorym efekt zadzialal po raz pierwszy.
 * <p>
 * Zamek NIE ma tej ochrony - na te sama osobe mozna go dawac wielokrotnie, pod warunkiem
 * zachowania 25 kratek odleglosci od poprzedniego (patrz ChunkLockManager).
 * Lod na patyku ma osobna, analogiczna ochrone w LodManager (ten sam czas z configu).
 */
public class TargetImmunity {

    public enum Type { POLE_SMIERCI }

    private static final class Mark {
        final Object source;
        final long until;

        Mark(Object source, long until) {
            this.source = source;
            this.until = until;
        }
    }

    private int seconds = 60;
    private final Map<Type, Map<UUID, Mark>> marks = new EnumMap<>(Type.class);

    public TargetImmunity(Main plugin) {
        for (Type t : Type.values()) marks.put(t, new ConcurrentHashMap<>());
        reloadConfig(plugin);
    }

    public void reloadConfig(Main plugin) {
        this.seconds = Math.max(0, plugin.getConfig().getInt("ochrona-celu-sekundy", 60));
    }

    public int getSeconds() {
        return seconds;
    }

    /** Czy gracz jest chroniony przed KOLEJNYM efektem tego typu (jeszcze nie minelo 60s). */
    public boolean isImmune(Type type, UUID player) {
        return remainingSeconds(type, player) > 0;
    }

    public long remainingSeconds(Type type, UUID player) {
        Mark m = marks.get(type).get(player);
        if (m == null) return 0;
        long left = m.until - System.currentTimeMillis();
        if (left <= 0) {
            marks.get(type).remove(player);
            return 0;
        }
        return (left + 999) / 1000L;
    }

    /** Zapisuje, ze na gracza zadzialal efekt (od teraz {@code seconds} sekund kolejny go nie dotknie). */
    public void mark(Type type, UUID player, Object source) {
        marks.get(type).put(player, new Mark(source, System.currentTimeMillis() + seconds * 1000L));
    }

    /**
     * Dla efektow dzialajacych w czasie (pole smierci): true = efekt tego zrodla moze dzialac
     * na gracza TERAZ. Gracz bez ochrony jest oznaczany (od tej chwili liczy sie 60s);
     * gracz juz oznaczony przez TO SAMO zrodlo dalej podlega efektowi (do konca jego trwania);
     * gracz chroniony przez INNE zrodlo - false.
     */
    public boolean tryApply(Type type, UUID player, Object source) {
        Map<UUID, Mark> map = marks.get(type);
        Mark m = map.get(player);
        long now = System.currentTimeMillis();
        if (m != null && m.until > now) {
            return m.source == source;
        }
        map.put(player, new Mark(source, now + seconds * 1000L));
        return true;
    }

    public void clear(UUID player) {
        for (Map<UUID, Mark> map : marks.values()) map.remove(player);
    }
}
