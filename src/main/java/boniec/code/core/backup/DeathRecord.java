package boniec.code.core.backup;

import java.util.List;

public class DeathRecord {
    public final int id;
    public final String uuid, name, itemsJson, armorJson;
    public final long deathTime;

    /** nick zabójcy (null jeśli nieznany / śmierć od środowiska) */
    public String killerName = null;

    /** ranking ELO w chwili smierci (-1 = brak w starych wpisach) */
    public int ranking = -1;

    /** status najnowszego wniosku powiązanego z tą śmiercią (null = brak) */
    public String backupStatus = null;
    public int backupId = -1;

    /** wyliczane asynchronicznie przed otwarciem GUI */
    public List<String> previewLines;
    public int itemCount;

    public DeathRecord(int id, String uuid, String name, long deathTime, String itemsJson, String armorJson) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.deathTime = deathTime;
        this.itemsJson = itemsJson;
        this.armorJson = armorJson;
    }
}