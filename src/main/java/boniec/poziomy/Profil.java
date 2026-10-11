package boniec.poziomy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Dane gracza w systemie poziomow (poziomy-dane.yml). */
public class Profil {

    private final UUID uuid;
    private String nazwa;
    private int poziom = 1;
    private double exp;
    private boolean zmigrowany;
    private final Set<Integer> odebraneNagrody = new HashSet<>();

    public Profil(UUID uuid, String nazwa) {
        this.uuid = uuid;
        this.nazwa = nazwa;
    }

    public UUID getUuid() { return uuid; }
    public String getNazwa() { return nazwa; }
    public void setNazwa(String nazwa) { this.nazwa = nazwa; }
    public int getPoziom() { return poziom; }
    public void setPoziom(int poziom) { this.poziom = Math.max(1, poziom); }
    public double getExp() { return exp; }
    public void setExp(int exp) { setExp((double) exp); }
    public void setExp(double exp) { this.exp = Math.max(0, exp); }
    public boolean isZmigrowany() { return zmigrowany; }
    public void setZmigrowany(boolean zmigrowany) { this.zmigrowany = zmigrowany; }
    public Set<Integer> getOdebraneNagrody() { return odebraneNagrody; }
}
