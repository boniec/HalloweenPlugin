package boniec.boss;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class GoblinInstance {

    private final LivingEntity entity;
    private final BossBar bossBar;
    private final double hpMax;
    private final double hpMaxRealne;

    private final Map<UUID, Double> obrazeniaGraczy = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldownUderzenia = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldownDropu = new LinkedHashMap<>();
    private UUID ostatniAtakujacy;

    /**
     * @param hpMax wartosc "docelowa" z configu (settings.hp-max), moze przekraczac 1024 -
     *              uzywana tylko do przeliczen wizualnych (bossbar, %).
     * @param hpMaxRealne realne HP encji faktycznie ustawione przez setHealth(), zawsze w 0-1024.
     */
    public GoblinInstance(LivingEntity entity, BossBar bossBar, double hpMax, double hpMaxRealne) {
        this.entity = entity;
        this.bossBar = bossBar;
        this.hpMax = hpMax;
        this.hpMaxRealne = hpMaxRealne > 0 ? hpMaxRealne : hpMax;
    }

    public LivingEntity getEntity() { return entity; }
    public BossBar getBossBar() { return bossBar; }
    public double getHpMax() { return hpMax; }

    public double getVisualHpMax() { return hpMax; }

    /**
     * Mnoznik przeliczajacy obrazenia zadane bossowi (liczone wobec realnego
     * HP encji, max hpMaxRealne <= 1024) tak, aby "odpowiadaly" obrazeniom
     * wobec docelowego hp-max z configu (moze byc > 1024, np. 5000).
     * Przy hp-max=5000 i hpMaxRealne=1024 mnoznik ~0.2048 - kazde uderzenie
     * odejmuje realnie mniej HP, wiec boss wytrzymuje tyle hitow, ile
     * odpowiadaloby jego docelowemu (wirtualnemu) hp-max.
     */
    public double getMnoznikObrazen() {
        return hpMaxRealne > 0 ? (hpMaxRealne / hpMax) : 1.0;
    }

    /**
     * Przelicza realne HP encji (0-hpMaxRealne) na skale "docelowa" z configu
     * (0-hpMax) - BEZ dodatkowego mnoznika. Dzieki temu bossbar pokazuje
     * dokladnie to co jest w settings.hp-max (np. 5000/5000), nawet jesli
     * hp-max przekracza limit Bukkita dla realnego HP encji (1024).
     */
    public double getVisualHp() {
        if (entity == null || entity.isDead()) return 0;
        double realHp = Math.max(0, entity.getHealth());
        double skala = hpMaxRealne > 0 ? (hpMax / hpMaxRealne) : 1.0;
        return realHp * skala;
    }

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