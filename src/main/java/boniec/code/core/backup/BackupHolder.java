package boniec.code.core.backup;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackupHolder implements InventoryHolder {

    public enum Type { DEATH_LIST, VERIFICATION, CLAIM, REVOKE, PREVIEW }

    private final Type type;
    private Inventory inventory;

    private UUID targetUuid;
    private String targetName;

    private final Map<Integer, DeathRecord> deathSlots = new HashMap<>();
    private final Map<Integer, BackupEntry> entrySlots = new HashMap<>();

    // dane podglądu
    private String previewTitle;
    private String previewItems;
    private String previewArmor;
    private int page;
    private int maxPage;
    private Runnable backAction;

    public BackupHolder(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }

    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public void setTarget(UUID uuid, String name) { this.targetUuid = uuid; this.targetName = name; }

    public void putDeath(int slot, DeathRecord record) { deathSlots.put(slot, record); }
    public DeathRecord getDeath(int slot) { return deathSlots.get(slot); }

    public void putEntry(int slot, BackupEntry entry) { entrySlots.put(slot, entry); }
    public BackupEntry getEntry(int slot) { return entrySlots.get(slot); }

    public String getPreviewTitle() { return previewTitle; }
    public String getPreviewItems() { return previewItems; }
    public String getPreviewArmor() { return previewArmor; }
    public int getPage() { return page; }
    public int getMaxPage() { return maxPage; }
    public Runnable getBackAction() { return backAction; }

    public void setPreview(String title, String items, String armor, int page, int maxPage, Runnable back) {
        this.previewTitle = title;
        this.previewItems = items;
        this.previewArmor = armor;
        this.page = page;
        this.maxPage = maxPage;
        this.backAction = back;
    }
}