package boniec.code.core.backup;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.*;

public class BackupGUI implements Listener {

    private static final SimpleDateFormat DF = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public static final String T_DEATHS       = "§8Backup §7» §e";
    public static final String T_VERIFICATION = "§8Weryfikacja backupów";
    public static final String T_CLAIM        = "§8Odbiór backupu";
    public static final String T_REVOKE       = "§8Cofnij backup §7» §e";
    public static final String T_PREVIEW      = "§8Podgląd §7» §e";

    private static final Material GLOW_FRAME = BackupGuiLayout.material("GLOW_ITEM_FRAME", Material.ITEM_FRAME);

    private final JavaPlugin plugin;
    private final BackupManager manager;

    public BackupGUI(JavaPlugin plugin, BackupManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ==================================================================
    //                         /backup <nick>
    // ==================================================================

    public void openDeathList(Player viewer, UUID targetUuid, String targetName) {
        async(() -> {
            try {
                List<DeathRecord> deaths = manager.getDeaths(targetUuid, targetName);
                for (DeathRecord d : deaths) {
                    // Liczymy tylko ilość przedmiotów (preview itemów jest w osobnym oknie podglądu)
                    BackupSerializer.Preview pv = BackupSerializer.buildPreview(d.itemsJson, d.armorJson, 0);
                    d.itemCount = pv.count;
                }
                sync(() -> buildDeathList(viewer, targetUuid, targetName, deaths));
            } catch (Exception ex) { error(viewer, ex); }
        });
    }

    private void buildDeathList(Player viewer, UUID targetUuid, String targetName, List<DeathRecord> deaths) {
        if (!viewer.isOnline()) return;

        if (deaths.isEmpty()) {
            viewer.closeInventory();
            viewer.sendMessage("§cBrak zapisanych śmierci dla gracza: §e" + targetName);
            return;
        }

        BackupHolder holder = new BackupHolder(BackupHolder.Type.DEATH_LIST);
        holder.setTarget(targetUuid, targetName);

        Inventory inv = Bukkit.createInventory(holder, BackupGuiLayout.SIZE, T_DEATHS + targetName);
        holder.setInventory(inv);
        BackupGuiLayout.frame(inv);
        BackupGuiLayout.closeButton(inv);

        if (BackupGuiLayout.RESERVE_INFO_AND_ACTION) {
            inv.setItem(BackupGuiLayout.INFO_SLOT, head(targetUuid, targetName, Arrays.asList(
                    "§7Zapisanych śmierci: §f" + deaths.size(),
                    "",
                    "§8» §fItem Frame §7- niezgłoszony",
                    "§8» §fGlow Item Frame §7- w weryfikacji",
                    "§8» §fLime Dye §7- nadany",
                    "",
                    "§a► LPM §7- wyślij do weryfikacji",
                    "§b► PPM §7- podgląd przedmiotów")));
        }

        int idx = 0;
        for (DeathRecord d : deaths) {
            if (idx >= BackupGuiLayout.CONTENT_SLOTS.length) break;
            int slot = BackupGuiLayout.CONTENT_SLOTS[idx++];
            inv.setItem(slot, deathIcon(viewer, d));
            holder.putDeath(slot, d);
        }

        viewer.openInventory(inv);
    }

    private ItemStack deathIcon(Player viewer, DeathRecord d) {
        Material mat;
        String status;
        String action;

        if (BackupManager.PENDING.equals(d.backupStatus)) {
            mat = GLOW_FRAME;
            status = "§6Oczekuje na weryfikację";
            action = "§6► Czeka w §f/backup weryfikacja";
        } else if (BackupManager.APPROVED.equals(d.backupStatus)) {
            mat = Material.LIME_DYE;
            status = "§aNadany §7- czeka na odbiór";
            action = "§a► Gracz odbiera przez §f/odbierzbackup";
        } else if (BackupManager.GIVEN.equals(d.backupStatus)) {
            mat = Material.LIME_DYE;
            status = "§aNadany i odebrany";
            action = "§7► Cofniesz go przez §f/cofnijbackup";
        } else if (BackupManager.REJECTED.equals(d.backupStatus)) {
            mat = Material.ITEM_FRAME;
            status = "§cOdrzucony";
            action = BackupPerms.canRequest(viewer) ? "§a► LPM §7- zgłoś ponownie" : "§7► Brak akcji";
        } else if (BackupManager.REVOKED.equals(d.backupStatus)) {
            mat = Material.ITEM_FRAME;
            status = "§cCofnięty";
            action = BackupPerms.canRequest(viewer) ? "§a► LPM §7- zgłoś ponownie" : "§7► Brak akcji";
        } else {
            mat = Material.ITEM_FRAME;
            status = "§7Niezgłoszony";
            action = BackupPerms.canRequest(viewer) ? "§a► LPM §7- wyślij do weryfikacji" : "§7► Brak akcji";
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7Data śmierci: §f" + DF.format(new Date(d.deathTime)));
        if (d.killerName != null && !d.killerName.isEmpty()) {
            lore.add("§7Zabójca: §c" + d.killerName);
        } else {
            lore.add("§7Zabójca: §8nieznany");
        }
        lore.add("§7Przedmiotów: §f" + d.itemCount);
        if (d.ranking >= 0) {
            lore.add("§7Ranking: §e" + d.ranking);
        }
        lore.add("§7Status: " + status);
        lore.add("");
        lore.add(action);
        lore.add("§b► PPM §7- podgląd przedmiotów");

        return BackupGuiLayout.build(mat, "§e§lŚmierć #" + d.id, lore);
    }

    // ==================================================================
    //                      /backup weryfikacja
    // ==================================================================

    public void openVerification(Player viewer) {
        async(() -> {
            try {
                List<BackupEntry> pending = manager.getPending();
                for (BackupEntry e : pending) {
                    BackupSerializer.Preview pv = BackupSerializer.buildPreview(e.itemsJson, e.armorJson, 5);
                    e.previewLines = pv.lines;
                    e.itemCount = pv.count;
                }
                sync(() -> {
                    if (!viewer.isOnline()) return;

                    BackupHolder holder = new BackupHolder(BackupHolder.Type.VERIFICATION);
                    Inventory inv = Bukkit.createInventory(holder, BackupGuiLayout.SIZE, T_VERIFICATION);
                    holder.setInventory(inv);
                    BackupGuiLayout.frame(inv);
                    BackupGuiLayout.closeButton(inv);

                    if (BackupGuiLayout.RESERVE_INFO_AND_ACTION) {
                        inv.setItem(BackupGuiLayout.INFO_SLOT, BackupGuiLayout.build(Material.BOOK,
                                "§e§lWeryfikacja backupów",
                                "§7Oczekujących wniosków: §f" + pending.size(),
                                "",
                                "§a► LPM §7- zatwierdź",
                                "§c► PPM §7- odrzuć",
                                "§b► Shift + LPM §7- podgląd",
                                "",
                                "§8Zatwierdzenie NIE wydaje itemów -",
                                "§8gracz odbiera je przez /odbierzbackup"));
                    }

                    int idx = 0;
                    for (BackupEntry e : pending) {
                        if (idx >= BackupGuiLayout.CONTENT_SLOTS.length) break;
                        int slot = BackupGuiLayout.CONTENT_SLOTS[idx++];

                        List<String> lore = new ArrayList<>();
                        lore.add("§7Gracz: §f" + e.targetName);
                        lore.add("§7Zgłaszający: §f" + (e.requestedByName != null ? e.requestedByName : "?"));
                        lore.add("§7Zgłoszono: §f" + DF.format(new Date(e.createdAt)));
                        lore.add("§7Przedmiotów: §f" + e.itemCount);
                        if (e.ranking >= 0) {
                            lore.add("§7Ranking: §e" + e.ranking);
                        }
                        lore.add("§8§m-----------------------------");
                        if (e.previewLines != null) for (String l : e.previewLines) lore.add(" §8» " + l);
                        lore.add("§8§m-----------------------------");
                        lore.add("§a► LPM §7- zatwierdź (gracz odbierze sam)");
                        lore.add("§c► PPM §7- odrzuć");
                        lore.add("§b► Shift + LPM §7- podgląd przedmiotów");

                        inv.setItem(slot, BackupGuiLayout.build(GLOW_FRAME,
                                "§e§l" + e.targetName + " §7(#" + e.id + ")", lore));
                        holder.putEntry(slot, e);
                    }

                    viewer.openInventory(inv);
                });
            } catch (Exception ex) { error(viewer, ex); }
        });
    }

    // ==================================================================
    //                  /odbierzbackup  (odbiór przez gracza)
    // ==================================================================

    public void openClaim(Player viewer) {
        async(() -> {
            try {
                List<BackupEntry> list = manager.getClaimable(viewer.getUniqueId(), viewer.getName());
                for (BackupEntry e : list) {
                    BackupSerializer.Preview pv = BackupSerializer.buildPreview(e.itemsJson, e.armorJson, 5);
                    e.previewLines = pv.lines;
                    e.itemCount = pv.count;
                }
                sync(() -> {
                    if (!viewer.isOnline()) return;

                    if (list.isEmpty()) {
                        viewer.closeInventory();
                        viewer.sendMessage("§8[§cBACKUP§8] §cNie masz żadnych backupów do odebrania.");
                        return;
                    }

                    BackupHolder holder = new BackupHolder(BackupHolder.Type.CLAIM);
                    holder.setTarget(viewer.getUniqueId(), viewer.getName());
                    Inventory inv = Bukkit.createInventory(holder, BackupGuiLayout.SIZE, T_CLAIM);
                    holder.setInventory(inv);
                    BackupGuiLayout.frame(inv);
                    BackupGuiLayout.closeButton(inv);

                    if (BackupGuiLayout.RESERVE_INFO_AND_ACTION) {
                        inv.setItem(BackupGuiLayout.INFO_SLOT, head(viewer.getUniqueId(), viewer.getName(), Arrays.asList(
                                "§7Backupów do odebrania: §f" + list.size(),
                                "",
                                "§a► LPM §7- odbierz przedmioty",
                                "§b► PPM §7- podgląd",
                                "",
                                "§8Zadbaj o wolne miejsce w EQ!")));
                    }

                    int idx = 0;
                    for (BackupEntry e : list) {
                        if (idx >= BackupGuiLayout.CONTENT_SLOTS.length) break;
                        int slot = BackupGuiLayout.CONTENT_SLOTS[idx++];

                        List<String> lore = new ArrayList<>();
                        lore.add("§7Zatwierdził: §f" + (e.verifiedByName != null ? e.verifiedByName : "Administracja"));
                        lore.add("§7Data: §f" + DF.format(new Date(e.updatedAt)));
                        lore.add("§7Przedmiotów: §f" + e.itemCount);
                        if (e.ranking >= 0) {
                            lore.add("§7Ranking: §e" + e.ranking);
                        }
                        lore.add("§8§m-----------------------------");
                        if (e.previewLines != null) for (String l : e.previewLines) lore.add(" §8» " + l);
                        lore.add("§8§m-----------------------------");
                        lore.add("§a► LPM §7- odbierz przedmioty");
                        lore.add("§b► PPM §7- podgląd przedmiotów");

                        inv.setItem(slot, BackupGuiLayout.build(Material.LIME_DYE, "§a§lBackup #" + e.id, lore));
                        holder.putEntry(slot, e);
                    }

                    viewer.openInventory(inv);
                });
            } catch (Exception ex) { error(viewer, ex); }
        });
    }

    // ==================================================================
    //                  /cofnijbackup <nick>  (staff)
    // ==================================================================

    public void openRevoke(Player viewer, UUID targetUuid, String targetName) {
        async(() -> {
            try {
                List<BackupEntry> list = manager.getRevocable(targetUuid, targetName);
                for (BackupEntry e : list) {
                    BackupSerializer.Preview pv = BackupSerializer.buildPreview(e.itemsJson, e.armorJson, 5);
                    e.previewLines = pv.lines;
                    e.itemCount = pv.count;
                }
                sync(() -> {
                    if (!viewer.isOnline()) return;

                    if (list.isEmpty()) {
                        viewer.closeInventory();
                        viewer.sendMessage("§cGracz §e" + targetName + " §cnie ma żadnego aktywnego backupu.");
                        return;
                    }

                    BackupHolder holder = new BackupHolder(BackupHolder.Type.REVOKE);
                    holder.setTarget(targetUuid, targetName);
                    Inventory inv = Bukkit.createInventory(holder, BackupGuiLayout.SIZE, T_REVOKE + targetName);
                    holder.setInventory(inv);
                    BackupGuiLayout.frame(inv);
                    BackupGuiLayout.closeButton(inv);

                    if (BackupGuiLayout.RESERVE_INFO_AND_ACTION) {
                        inv.setItem(BackupGuiLayout.INFO_SLOT, head(targetUuid, targetName, Arrays.asList(
                                "§7Aktywnych backupów: §f" + list.size(),
                                "",
                                "§c► LPM §7- cofnij backup",
                                "§b► PPM §7- podgląd",
                                "",
                                "§8Odebrane itemy zostaną zabrane,",
                                "§8offline - przy następnym wejściu.")));
                    }

                    int idx = 0;
                    for (BackupEntry e : list) {
                        if (idx >= BackupGuiLayout.CONTENT_SLOTS.length) break;
                        int slot = BackupGuiLayout.CONTENT_SLOTS[idx++];

                        boolean claimed = BackupManager.GIVEN.equals(e.status) || e.delivered == 1;

                        List<String> lore = new ArrayList<>();
                        lore.add("§7Status: " + (claimed ? "§aOdebrany przez gracza" : "§eNieodebrany"));
                        lore.add("§7Zatwierdził: §f" + (e.verifiedByName != null ? e.verifiedByName : "-"));
                        lore.add("§7Data: §f" + DF.format(new Date(e.updatedAt)));
                        lore.add("§7Przedmiotów: §f" + e.itemCount);
                        if (e.ranking >= 0) {
                            lore.add("§7Ranking: §e" + e.ranking);
                        }
                        lore.add("§8§m-----------------------------");
                        if (e.previewLines != null) for (String l : e.previewLines) lore.add(" §8» " + l);
                        lore.add("§8§m-----------------------------");
                        lore.add("§c► LPM §7- cofnij backup graczowi");
                        lore.add("§b► PPM §7- podgląd przedmiotów");

                        inv.setItem(slot, BackupGuiLayout.build(claimed ? Material.LIME_DYE : GLOW_FRAME,
                                "§e§lBackup #" + e.id, lore));
                        holder.putEntry(slot, e);
                    }

                    viewer.openInventory(inv);
                });
            } catch (Exception ex) { error(viewer, ex); }
        });
    }

    // ==================================================================
    //           PODGLĄD - pełne 45-slotowe okno z tym samym szkłem
    // ==================================================================

    public void openPreview(Player viewer, String title, String itemsJson, String armorJson, int page, Runnable back) {
        async(() -> {
            List<ItemStack> all = BackupSerializer.flatten(itemsJson, armorJson);
            sync(() -> {
                if (!viewer.isOnline()) return;

                int perPage = BackupGuiLayout.CONTENT_SLOTS.length;
                int maxPage = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
                int current = Math.max(1, Math.min(page, maxPage));

                BackupHolder holder = new BackupHolder(BackupHolder.Type.PREVIEW);
                holder.setPreview(title, itemsJson, armorJson, current, maxPage, back);

                Inventory inv = Bukkit.createInventory(holder, BackupGuiLayout.SIZE, cut(title));
                holder.setInventory(inv);
                BackupGuiLayout.frame(inv);
                if (back != null) BackupGuiLayout.backButton(inv); else BackupGuiLayout.closeButton(inv);

                if (BackupGuiLayout.RESERVE_INFO_AND_ACTION) {
                    inv.setItem(BackupGuiLayout.INFO_SLOT, BackupGuiLayout.build(Material.CHEST,
                            "§e§lZawartość backupu",
                            "§7Przedmiotów: §f" + all.size(),
                            "§7Strona: §f" + current + "§7/§f" + maxPage,
                            "",
                            "§8To tylko podgląd -",
                            "§8nie można stąd nic zabrać."));
                }

                if (maxPage > 1) {
                    inv.setItem(BackupGuiLayout.PREV_SLOT, current > 1
                            ? BackupGuiLayout.build(Material.ARROW, "§e§lPoprzednia strona", "§7Strona " + (current - 1))
                            : BackupGuiLayout.pane(Material.WHITE_STAINED_GLASS_PANE));
                    inv.setItem(BackupGuiLayout.NEXT_SLOT, current < maxPage
                            ? BackupGuiLayout.build(Material.ARROW, "§e§lNastępna strona", "§7Strona " + (current + 1))
                            : BackupGuiLayout.pane(Material.WHITE_STAINED_GLASS_PANE));
                }

                int start = (current - 1) * perPage;
                for (int i = 0; i < perPage; i++) {
                    int index = start + i;
                    if (index >= all.size()) break;
                    inv.setItem(BackupGuiLayout.CONTENT_SLOTS[i], all.get(index));
                }

                if (all.isEmpty() && BackupGuiLayout.CONTENT_SLOTS.length > 0) {
                    inv.setItem(BackupGuiLayout.CONTENT_SLOTS[BackupGuiLayout.CONTENT_SLOTS.length / 2],
                            BackupGuiLayout.build(Material.BARRIER, "§c§lBrak przedmiotów",
                                    "§7Ten backup jest pusty."));
                }

                viewer.openInventory(inv);
            });
        });
    }

    // ==================================================================
    //                             KLIKNIĘCIA
    // ==================================================================

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof BackupHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof BackupHolder)) return;

        e.setCancelled(true); // blokuje też shift-click i numerki z dolnego EQ
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        BackupHolder holder = (BackupHolder) top.getHolder();
        int slot = e.getRawSlot();

        // ---------- PODGLĄD ----------
        if (holder.getType() == BackupHolder.Type.PREVIEW) {
            if (slot == BackupGuiLayout.ACTION_SLOT && BackupGuiLayout.RESERVE_INFO_AND_ACTION) {
                Runnable back = holder.getBackAction();
                if (back != null) back.run(); else p.closeInventory();
                click(p);
            } else if (slot == BackupGuiLayout.PREV_SLOT && holder.getPage() > 1) {
                openPreview(p, holder.getPreviewTitle(), holder.getPreviewItems(), holder.getPreviewArmor(),
                        holder.getPage() - 1, holder.getBackAction());
                click(p);
            } else if (slot == BackupGuiLayout.NEXT_SLOT && holder.getPage() < holder.getMaxPage()) {
                openPreview(p, holder.getPreviewTitle(), holder.getPreviewItems(), holder.getPreviewArmor(),
                        holder.getPage() + 1, holder.getBackAction());
                click(p);
            }
            return;
        }

        if (BackupGuiLayout.RESERVE_INFO_AND_ACTION && slot == BackupGuiLayout.ACTION_SLOT) {
            p.closeInventory();
            return;
        }

        switch (holder.getType()) {
            case DEATH_LIST:   handleDeathClick(p, holder, slot, e.isRightClick()); break;
            case VERIFICATION: handleVerificationClick(p, holder, slot, e); break;
            case CLAIM:        handleClaimClick(p, holder, slot, e.isRightClick()); break;
            case REVOKE:       handleRevokeClick(p, holder, slot, e.isRightClick()); break;
            default: break;
        }
    }

    // ------------------------ lista śmierci ------------------------

    private void handleDeathClick(Player p, BackupHolder holder, int slot, boolean right) {
        DeathRecord d = holder.getDeath(slot);
        if (d == null) return;

        final UUID targetUuid = holder.getTargetUuid();
        final String targetName = holder.getTargetName();

        if (right) {
            openPreview(p, T_PREVIEW + "Śmierć #" + d.id, d.itemsJson, d.armorJson, 1,
                    () -> openDeathList(p, targetUuid, targetName));
            click(p);
            return;
        }

        if (!BackupPerms.canRequest(p)) {
            p.sendMessage("§cBrak uprawnień do zgłaszania backupów.");
            return;
        }
        if (BackupManager.PENDING.equals(d.backupStatus)) {
            p.sendMessage("§cTen backup czeka już na weryfikację §7(§f/backup weryfikacja§7).");
            return;
        }
        if (BackupManager.APPROVED.equals(d.backupStatus)) {
            p.sendMessage("§cTen backup jest już nadany - gracz musi go odebrać przez §e/odbierzbackup§c.");
            return;
        }
        if (BackupManager.GIVEN.equals(d.backupStatus)) {
            p.sendMessage("§cTen backup został już odebrany przez gracza.");
            return;
        }

        async(() -> {
            try {
                int id = manager.createVerificationRequest(p, targetUuid, targetName, d.id);
                int active = manager.countActive(targetUuid, targetName);
                sync(() -> {
                    if (id == -1) {
                        p.sendMessage("§cDla tej śmierci istnieje już aktywny wniosek o backup.");
                    } else if (id == 0) {
                        p.sendMessage("§cNie udało się utworzyć wniosku - spróbuj ponownie.");
                    } else {
                        p.sendMessage("§8§m------------------------------------");
                        p.sendMessage("§8[§aBACKUP§8] §aWysłano wniosek §7#" + id + " §adla gracza §e" + targetName);
                        p.sendMessage("§8[§aBACKUP§8] §7Musi go zatwierdzić osoba z §fbackup.nadanie§7.");
                        if (active > 0) p.sendMessage("§8[§eBACKUP§8] §eUwaga: gracz ma już " + active + " aktywny(ch) backup(ów).");
                        p.sendMessage("§8§m------------------------------------");
                        notifyGrantStaff(targetName, p.getName(), id);
                    }
                    openDeathList(p, targetUuid, targetName);
                });
            } catch (Exception ex) { error(p, ex); }
        });
    }

    // ------------------------- weryfikacja -------------------------

    private void handleVerificationClick(Player p, BackupHolder holder, int slot, InventoryClickEvent e) {
        BackupEntry entry = holder.getEntry(slot);
        if (entry == null) return;

        if (e.isShiftClick() && e.isLeftClick()) {
            openPreview(p, T_PREVIEW + entry.targetName, entry.itemsJson, entry.armorJson, 1, () -> openVerification(p));
            click(p);
            return;
        }

        if (!BackupPerms.canGrant(p)) {
            p.sendMessage("§cBrak uprawnień do zatwierdzania backupów §7(backup.nadanie)§c.");
            return;
        }

        // PPM = odrzucenie
        if (e.isRightClick()) {
            async(() -> {
                try {
                    boolean ok = manager.reject(entry.id, p);
                    sync(() -> {
                        if (ok) {
                            p.sendMessage("§8[§cBACKUP§8] §cOdrzucono wniosek #" + entry.id + " dla gracza §e" + entry.targetName);
                            Player t = findOnline(entry.uuidOrNull(), entry.targetName);
                            if (t != null) t.sendMessage("§8[§cBACKUP§8] §cTwój wniosek o backup został odrzucony przez §e" + p.getName() + "§c.");
                        } else {
                            p.sendMessage("§cTen wniosek został już rozpatrzony.");
                        }
                        openVerification(p);
                    });
                } catch (Exception ex) { error(p, ex); }
            });
            return;
        }

        // LPM = zatwierdzenie (BEZ wydania itemów)
        async(() -> {
            try {
                boolean ok = manager.approve(entry.id, p);
                sync(() -> {
                    if (!ok) {
                        p.sendMessage("§cTen wniosek został już rozpatrzony.");
                    } else {
                        p.sendMessage("§8[§aBACKUP§8] §aZatwierdzono backup #" + entry.id + " dla §e" + entry.targetName);
                        p.sendMessage("§8[§aBACKUP§8] §7Gracz odbierze przedmioty komendą §f/odbierzbackup§7.");

                        Player target = findOnline(entry.uuidOrNull(), entry.targetName);
                        if (target != null) {
                            target.sendMessage("§8§m------------------------------------");
                            target.sendMessage("§8[§aBACKUP§8] §aTwój backup został zatwierdzony przez §e" + p.getName() + "§a!");
                            target.sendMessage("§8[§aBACKUP§8] §7Wpisz §e/odbierzbackup §7aby odebrać przedmioty.");
                            target.sendMessage("§8§m------------------------------------");
                            sound(target, "ENTITY_PLAYER_LEVELUP");
                        }
                    }
                    openVerification(p);
                });
            } catch (Exception ex) { error(p, ex); }
        });
    }

    // ---------------------------- odbiór ----------------------------

    private void handleClaimClick(Player p, BackupHolder holder, int slot, boolean right) {
        BackupEntry entry = holder.getEntry(slot);
        if (entry == null) return;

        if (right) {
            openPreview(p, T_PREVIEW + "Backup #" + entry.id, entry.itemsJson, entry.armorJson, 1, () -> openClaim(p));
            click(p);
            return;
        }

        async(() -> {
            try {
                boolean ok = manager.claim(entry.id);
                final ItemStack[] items = ok ? BackupSerializer.deserializeItemArray(entry.itemsJson) : null;
                final ItemStack[] armor = ok ? BackupSerializer.deserializeItemArray(entry.armorJson) : null;

                sync(() -> {
                    if (!ok) {
                        p.sendMessage("§cTen backup został już odebrany lub cofnięty.");
                    } else {
                        BackupManager.giveItemsToPlayer(p, items, armor);
                        if (entry.ranking >= 0 && plugin instanceof boniec.dev.inventory.Main main
                                && main.getKlanManager() != null) {
                            main.getKlanManager().nadpiszRanking(p.getUniqueId(), entry.ranking);
                            p.sendMessage("§8[§aBACKUP§8] §7Przywrócono ranking: §e" + entry.ranking);
                        }
                        p.sendMessage("§8[§aBACKUP§8] §aOdebrano backup #" + entry.id + "§a!");
                        p.sendMessage("§8[§aBACKUP§8] §7Jeśli zabrakło miejsca, reszta wypadła pod Twoje nogi.");
                        sound(p, "ENTITY_PLAYER_LEVELUP");
                    }
                    openClaim(p);
                });
            } catch (Exception ex) { error(p, ex); }
        });
    }

    // ---------------------------- cofanie ----------------------------

    private void handleRevokeClick(Player p, BackupHolder holder, int slot, boolean right) {
        BackupEntry entry = holder.getEntry(slot);
        if (entry == null) return;

        final UUID tUuid = holder.getTargetUuid();
        final String tName = holder.getTargetName();

        if (right) {
            openPreview(p, T_PREVIEW + entry.targetName, entry.itemsJson, entry.armorJson, 1,
                    () -> openRevoke(p, tUuid, tName));
            click(p);
            return;
        }

        if (!BackupPerms.canRevoke(p)) {
            p.sendMessage("§cBrak uprawnień do cofania backupów.");
            return;
        }

        async(() -> {
            try {
                BackupEntry fresh = manager.getEntry(entry.id);
                if (fresh == null || !(BackupManager.APPROVED.equals(fresh.status) || BackupManager.GIVEN.equals(fresh.status))) {
                    sync(() -> {
                        p.sendMessage("§cTen backup nie jest już aktywny.");
                        openRevoke(p, tUuid, tName);
                    });
                    return;
                }

                manager.markRevoked(fresh.id);
                boolean claimed = BackupManager.GIVEN.equals(fresh.status) || fresh.delivered == 1;

                if (!claimed) {
                    sync(() -> {
                        p.sendMessage("§8[§aBACKUP§8] §aAnulowano backup #" + fresh.id + " graczowi §e" + fresh.targetName
                                + " §7(nie zdążył go odebrać).");
                        Player t = findOnline(fresh.uuidOrNull(), fresh.targetName);
                        if (t != null) t.sendMessage("§8[§cBACKUP§8] §cTwój backup został anulowany przez §e" + p.getName() + "§c.");
                        openRevoke(p, tUuid, tName);
                    });
                    return;
                }

                final ItemStack[] items = BackupSerializer.deserializeItemArray(fresh.itemsJson);
                final ItemStack[] armor = BackupSerializer.deserializeItemArray(fresh.armorJson);

                sync(() -> {
                    Player target = findOnline(fresh.uuidOrNull(), fresh.targetName);
                    if (target != null) {
                        int removed = BackupManager.stripItemsFromPlayer(target, items, armor);
                        target.sendMessage("§8[§cBACKUP§8] §cTwój backup został cofnięty przez §e" + p.getName() + "§c!");
                        target.sendMessage("§8[§cBACKUP§8] §cUsunięto §f" + removed + " §cszt. przedmiotów z ekwipunku.");
                        p.sendMessage("§8[§aBACKUP§8] §aCofnięto backup graczowi §e" + target.getName()
                                + " §7(usunięto §f" + removed + " §7szt.).");
                    } else {
                        async(() -> {
                            try { manager.setNeedsStrip(fresh.id, true); } catch (Exception ignored) {}
                        });
                        p.sendMessage("§8[§aBACKUP§8] §aCofnięto backup graczowi §e" + fresh.targetName
                                + " §7(offline - itemy znikną po jego wejściu).");
                    }
                    openRevoke(p, tUuid, tName);
                });
            } catch (Exception ex) { error(p, ex); }
        });
    }

    // ==================================================================
    //                            POMOCNICZE
    // ==================================================================

    private void notifyGrantStaff(String targetName, String requester, int id) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (BackupPerms.canGrant(staff)) {
                staff.sendMessage("§8[§eBACKUP§8] §e" + requester + " §7zgłosił backup §f#" + id + " §7dla §e" + targetName
                        + " §8» §f/backup weryfikacja");
            }
        }
    }

    private Player findOnline(UUID uuid, String name) {
        Player pl = uuid != null ? Bukkit.getPlayer(uuid) : null;
        if (pl == null && name != null) pl = Bukkit.getPlayerExact(name);
        return (pl != null && pl.isOnline()) ? pl : null;
    }

    private ItemStack head(UUID uuid, String name, List<String> lore) {
        ItemStack is = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = is.getItemMeta();
        if (meta instanceof SkullMeta && uuid != null) {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                ((SkullMeta) meta).setOwningPlayer(op);
            } catch (Throwable ignored) {}
        }
        if (meta != null) {
            meta.setDisplayName("§e§l" + name);
            meta.setLore(new ArrayList<>(lore));
            is.setItemMeta(meta);
        }
        return is;
    }

    private static String cut(String title) {
        return title.length() > 32 ? title.substring(0, 32) : title;
    }

    private void click(Player p) { sound(p, "UI_BUTTON_CLICK"); }

    private void sound(Player p, String name) {
        try { p.playSound(p.getLocation(), Sound.valueOf(name), 0.6f, 1.2f); } catch (Throwable ignored) {}
    }

    private void async(Runnable r) { Bukkit.getScheduler().runTaskAsynchronously(plugin, r); }
    private void sync(Runnable r)  { Bukkit.getScheduler().runTask(plugin, r); }

    private void error(Player p, Exception ex) {
        sync(() -> p.sendMessage("§cBłąd bazy danych: " + ex.getMessage()));
        ex.printStackTrace();
    }
}