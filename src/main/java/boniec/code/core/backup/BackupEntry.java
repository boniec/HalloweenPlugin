package boniec.code.core.backup;

import java.util.List;
import java.util.UUID;

/** Reprezentuje wiersz z tabeli backups. */
public class BackupEntry {

    public final int id;
    public final int deathId;
    public final String targetUuid;
    public final String targetName;
    public final String status;
    public final String requestedByName;
    public final String verifiedByName;
    public final String itemsJson;
    public final String armorJson;
    public final int delivered;
    public final long createdAt;
    public final long updatedAt;

    /** wyliczane asynchronicznie przed otwarciem GUI */
    public List<String> previewLines;
    public int itemCount;
    /** ranking ELO z chwili smierci; -1 = brak */
    public int ranking = -1;

    public BackupEntry(int id, int deathId, String targetUuid, String targetName, String status,
                       String requestedByName, String verifiedByName, String itemsJson, String armorJson,
                       int delivered, long createdAt, long updatedAt) {
        this.id = id;
        this.deathId = deathId;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.status = status;
        this.requestedByName = requestedByName;
        this.verifiedByName = verifiedByName;
        this.itemsJson = itemsJson;
        this.armorJson = armorJson;
        this.delivered = delivered;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID uuidOrNull() {
        try {
            return (targetUuid == null || targetUuid.isEmpty()) ? null : UUID.fromString(targetUuid);
        } catch (Exception e) {
            return null;
        }
    }
}