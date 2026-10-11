package boniec.boss;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MinotaurInstance {

    private final LivingEntity entity;
    private final BossBar bossBar;
    private final double hpMax;

    private final Map<UUID, Double> obrazeniaGraczy = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldownUderzenia = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldownDropu = new LinkedHashMap<>();
    private UUID ostatniAtakujacy;

    public MinotaurInstance(LivingEntity entity, BossBar bossBar, double hpMax) {
        this.entity = entity;
        this.bossBar = bossBar;
        this.hpMax = hpMax;
    }

    public LivingEntity getEntity() { return entity; }
    public BossBar getBossBar() { return bossBar; }
    public double getHpMax() { return hpMax; }

    public void dodajObrazenia(UUID gracz, double kwota) {
        obrazeniaGraczy.merge(gracz, kwota, Double::sum);
        ostatniAtakujacy = gracz;
    }

    public double getObrazeniaGracza(UUID gracz) {
        return obrazeniaGraczy.getOrDefault(gracz, 0.0);
    }

    public Map<UUID, Double> getObrazeniaGraczy() {
        return obrazeniaGraczy;
    }

    public UUID getOstatniAtakujacy() {
        return ostatniAtakujacy;
    }

    public boolean czyCooldownUderzeniaMinal(UUID gracz, long cooldownMs) {
        Long nastepnaDostepna = cooldownUderzenia.get(gracz);
        return nastepnaDostepna == null || System.currentTimeMillis() >= nastepnaDostepna;
    }

    public void ustawCooldownUderzenia(UUID gracz, long cooldownMs) {
        cooldownUderzenia.put(gracz, System.currentTimeMillis() + cooldownMs);
    }

    public boolean czyCooldownDropuMinal(UUID gracz) {
        Long nastepna = cooldownDropu.get(gracz);
        return nastepna == null || System.currentTimeMillis() >= nastepna;
    }

    public void ustawCooldownDropu(UUID gracz, long cooldownMs) {
        cooldownDropu.put(gracz, System.currentTimeMillis() + cooldownMs);
    }

    public void usun() {
        BossbarUtil.usun(bossBar);
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }
}