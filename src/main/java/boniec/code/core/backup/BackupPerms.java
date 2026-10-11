package boniec.code.core.backup;

import org.bukkit.entity.Player;

public final class BackupPerms {

    private BackupPerms() {}

    public static boolean admin(Player p) {
        return p.hasPermission("backup.admin");
    }

    /** Zatwierdzanie / odrzucanie wniosków w /backup weryfikacja */
    public static boolean canGrant(Player p) {
        return admin(p) || p.hasPermission("backup.nadanie") || p.hasPermission("backup.nadawanie");
    }

    /** Podgląd listy weryfikacji */
    public static boolean canViewVerification(Player p) {
        return canGrant(p) || p.hasPermission("backup.weryfikacja");
    }

    /** Przeglądanie śmierci i wysyłanie wniosków */
    public static boolean canRequest(Player p) {
        return canViewVerification(p) || p.hasPermission("backup.uzyj");
    }

    /** Cofanie nadanych backupów (/cofnijbackup) */
    public static boolean canRevoke(Player p) {
        return admin(p) || p.hasPermission("backup.cofnij") || p.hasPermission("backup.odbierz");
    }
}