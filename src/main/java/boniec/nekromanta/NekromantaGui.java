package boniec.nekromanta;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Menu /nekromanta (bez szklanego tla):
 *  - LPM na pakiecie = wymiana gluty -> losowy miecz,
 *  - PPM na pakiecie = podglad nagrod z szansami (efektywnymi, przeliczonymi do 100%),
 *  - przycisk wplaty: LPM = wplac ilosc z configu, PPM = wplac wszystkie gluty (-> saldo),
 *  - przycisk ulepszen: otwiera menu, w ktorym za saldo kupuje sie kolejne poziomy (+% dropu gluta z welny).
 */
public class NekromantaGui implements Listener {

    private static final int ROZMIAR = 27;
    private static final int ROZMIAR_PODGLADU = 54;
    private static final int SLOT_POWROT = 49;
    private static final int ROZMIAR_ULEPSZEN = 27;

    public static final class MenuHolder implements InventoryHolder {
        private Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    public static final class PodgladHolder implements InventoryHolder {
        private Inventory inv;
        private final int tierId;
        PodgladHolder(int tierId) { this.tierId = tierId; }
        @Override
        public Inventory getInventory() { return inv; }
    }

    public static final class UlepszeniaHolder implements InventoryHolder {
        private Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    private final NekromantaManager manager;

    public NekromantaGui(NekromantaManager manager) {
        this.manager = manager;
    }

    // ───────────────────────── menu glowne ─────────────────────────

    public void otworz(Player p) {
        YamlConfiguration cfg = manager.cfg();
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, ROZMIAR, Msg.color(cfg.getString("gui.tytul", "&5Nekromanta")));
        holder.inv = inv;
        wypelnij(inv, p);
        p.openInventory(inv);
    }

    private void wypelnij(Inventory inv, Player p) {
        YamlConfiguration cfg = manager.cfg();
        inv.clear(); // brak szklanego tla - puste sloty zostaja puste

        int masz = manager.ileGlutow(p);
        long saldo = manager.saldo(p.getUniqueId());
        long lacznie = saldo + masz;
        for (NekromantaManager.Tier t : manager.getTiery()) {
            if (t.slot >= 0 && t.slot < inv.getSize()) {
                inv.setItem(t.slot, przycisk(t, masz, saldo, lacznie, p));
            }
        }

        // przycisk wplaty
        ustaw(inv, cfg.getInt("wplata.slot", 20), cfg.getString("wplata.material", "HOPPER"),
                cfg.getString("wplata.nazwa", "&aWplac gluty"), cfg.getStringList("wplata.lore"),
                "%saldo%", String.valueOf(saldo), "%masz%", String.valueOf(masz),
                "%lacznie%", String.valueOf(lacznie),
                "%ilosc-lpm%", String.valueOf(iloscLpm()));

        // przycisk ulepszen
        ustaw(inv, cfg.getInt("ulepszenia-przycisk.slot", 24), cfg.getString("ulepszenia-przycisk.material", "NETHER_STAR"),
                cfg.getString("ulepszenia-przycisk.nazwa", "&dUlepszenia"), cfg.getStringList("ulepszenia-przycisk.lore"),
                "%poziom%", String.valueOf(manager.poziom(p.getUniqueId())), "%max%", String.valueOf(manager.maxPoziom()),
                "%bonus%", NekromantaManager.formatSzansy(manager.bonusProcent(p.getUniqueId())));

        // info
        ustaw(inv, cfg.getInt("gui.info-slot", 22), cfg.getString("gui.info-material", "SKELETON_SKULL"),
                cfg.getString("gui.info-nazwa", "&aTwoje gluty"), cfg.getStringList("gui.info-lore"),
                "%masz%", String.valueOf(masz), "%saldo%", String.valueOf(saldo),
                "%lacznie%", String.valueOf(lacznie),
                "%poziom%", String.valueOf(manager.poziom(p.getUniqueId())), "%max%", String.valueOf(manager.maxPoziom()),
                "%bonus%", NekromantaManager.formatSzansy(manager.bonusProcent(p.getUniqueId())));
    }

    /** Ustawia w slocie przedmiot z nazwa i lore (z podmiana par placeholderow). Zly slot/material = pomijane. */
    private void ustaw(Inventory inv, int slot, String material, String nazwa, List<String> lore, String... pary) {
        if (slot < 0 || slot >= inv.getSize()) return;
        Material mat = Material.matchMaterial(material == null ? "" : material);
        if (mat == null || mat.isAir()) return;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(manager.zamien(nazwa, pary));
            List<String> l = new ArrayList<>();
            for (String linia : lore) l.add(manager.zamien(linia, pary));
            m.setLore(l);
            m.addItemFlags(ItemFlag.values());
            it.setItemMeta(m);
        }
        inv.setItem(slot, it);
    }

    private int iloscLpm() {
        return Math.max(1, manager.cfg().getInt("wplata.ilosc-lpm", 64));
    }

    private ItemStack przycisk(NekromantaManager.Tier t, int masz, long saldo, long lacznie, Player p) {
        YamlConfiguration cfg = manager.cfg();
        ItemStack it = new ItemStack(t.material);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        meta.setDisplayName(Msg.color(t.nazwa));
        meta.addItemFlags(ItemFlag.values());

        boolean stac = lacznie >= t.koszt;
        String status = cfg.getString(stac ? "gui.status-ok" : "gui.status-brak", "");
        status = status.replace("%brakuje%", String.valueOf(Math.max(0L, t.koszt - lacznie)));

        List<String> lore = new ArrayList<>();
        for (String linia : cfg.getStringList("gui.lore")) {
            if (linia.trim().equals("%drop%")) {
                lore.addAll(liniePodgladu(t));
            } else if (linia.trim().equals("%status%")) {
                lore.add(manager.podstaw(status, t, masz, null, 0, p));
            } else {
                lore.add(manager.podstaw(linia, t, masz, null, 0, p));
            }
        }
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    /** Skrocona lista nagrod w lore przycisku (najwyzsze szanse na gorze). */
    private List<String> liniePodgladu(NekromantaManager.Tier t) {
        YamlConfiguration cfg = manager.cfg();
        List<NekromantaManager.Wpis> wpisy = new ArrayList<>();
        for (NekromantaManager.Wpis w : manager.getWpisy(t)) if (w.getSzansa() > 0) wpisy.add(w);
        wpisy.sort(Comparator.comparingDouble(NekromantaManager.Wpis::getSzansa).reversed());

        List<String> wynik = new ArrayList<>();
        if (wpisy.isEmpty()) {
            wynik.add(Msg.color(cfg.getString("gui.brak-dropu", "&7brak nagrod")));
            return wynik;
        }
        int max = Math.max(1, cfg.getInt("gui.max-linii-dropu", 6));
        String format = cfg.getString("gui.format-dropu", "&f%item% &8- &e%szansa%%");
        for (int i = 0; i < wpisy.size() && i < max; i++) {
            NekromantaManager.Wpis w = wpisy.get(i);
            wynik.add(manager.podstaw(format, t, 0, w.getItem(), manager.szansaEfektywna(t, w), null));
        }
        if (wpisy.size() > max) {
            wynik.add(Msg.color(cfg.getString("gui.wiecej-dropu", "&7...i %ile% wiecej")
                    .replace("%ile%", String.valueOf(wpisy.size() - max))));
        }
        return wynik;
    }

    // ───────────────────────── podglad nagrod ─────────────────────────

    public void otworzPodglad(Player p, NekromantaManager.Tier t) {
        YamlConfiguration cfg = manager.cfg();
        PodgladHolder holder = new PodgladHolder(t.id);
        String tytul = manager.podstaw(cfg.getString("gui.tytul-podgladu", "&5Nagrody"), t, 0, null, 0, p);
        Inventory inv = Bukkit.createInventory(holder, ROZMIAR_PODGLADU, tytul);
        holder.inv = inv;

        List<NekromantaManager.Wpis> wpisy = new ArrayList<>();
        for (NekromantaManager.Wpis w : manager.getWpisy(t)) if (w.getSzansa() > 0) wpisy.add(w);
        wpisy.sort(Comparator.comparingDouble(NekromantaManager.Wpis::getSzansa).reversed());

        for (int i = 0; i < wpisy.size() && i < 45; i++) {
            NekromantaManager.Wpis w = wpisy.get(i);
            ItemStack kopia = w.getItem().clone();
            ItemMeta meta = kopia.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(manager.podstaw(cfg.getString("gui.szansa-w-podgladzie", "&7Szansa: %kolor%%szansa%%"), t, 0, null,
                        manager.szansaEfektywna(t, w), null));
                meta.setLore(lore);
                kopia.setItemMeta(meta);
            }
            inv.setItem(i, kopia);
        }

        Material powrot = Material.matchMaterial(cfg.getString("gui.powrot-material", "ARROW"));
        if (powrot != null) {
            ItemStack b = new ItemStack(powrot);
            ItemMeta m = b.getItemMeta();
            if (m != null) {
                m.setDisplayName(Msg.color(cfg.getString("gui.powrot-nazwa", "&7Powrot")));
                b.setItemMeta(m);
            }
            inv.setItem(SLOT_POWROT, b);
        }
        p.openInventory(inv);
    }

    // ───────────────────────── menu ulepszen ─────────────────────────

    public void otworzUlepszenia(Player p) {
        YamlConfiguration cfg = manager.cfg();
        UlepszeniaHolder holder = new UlepszeniaHolder();
        Inventory inv = Bukkit.createInventory(holder, ROZMIAR_ULEPSZEN,
                Msg.color(cfg.getString("ulepszenia.tytul", "&5Ulepszenia")));
        holder.inv = inv;
        wypelnijUlepszenia(inv, p);
        p.openInventory(inv);
    }

    private void wypelnijUlepszenia(Inventory inv, Player p) {
        YamlConfiguration cfg = manager.cfg();
        inv.clear();

        long saldo = manager.saldo(p.getUniqueId());
        int poziom = manager.poziom(p.getUniqueId());
        int max = manager.maxPoziom();
        double bonusPoziom = manager.bonusNaPoziom();
        List<Integer> koszty = manager.kosztyUlepszen();

        // saldo
        ustaw(inv, cfg.getInt("ulepszenia.info-slot", 4), cfg.getString("ulepszenia.info-material", "SLIME_BALL"),
                cfg.getString("ulepszenia.info-nazwa", "&aSaldo"), cfg.getStringList("ulepszenia.info-lore"),
                "%saldo%", String.valueOf(saldo), "%poziom%", String.valueOf(poziom), "%max%", String.valueOf(max),
                "%bonus%", NekromantaManager.formatSzansy(poziom * bonusPoziom));

        // kolejne poziomy
        int pierwszy = cfg.getInt("ulepszenia.pierwszy-slot", 10);
        for (int i = 0; i < koszty.size(); i++) {
            int slot = pierwszy + i;
            if (slot < 0 || slot >= inv.getSize()) continue;
            int nr = i + 1;
            int koszt = Math.max(1, koszty.get(i));

            String stan; // kupione / nastepne / zablokowane
            if (nr <= poziom) stan = "kupione";
            else if (nr == poziom + 1) stan = "nastepne";
            else stan = "zablokowane";

            String materialKlucz = "ulepszenia.material-" + stan;
            Material mat = Material.matchMaterial(cfg.getString(materialKlucz, "GRAY_DYE"));
            if (mat == null || mat.isAir()) mat = Material.GRAY_DYE;

            String status;
            if (stan.equals("kupione")) {
                status = cfg.getString("ulepszenia.status-kupione", "");
            } else if (stan.equals("nastepne")) {
                status = cfg.getString(saldo >= koszt ? "ulepszenia.status-nastepne-ok" : "ulepszenia.status-nastepne-brak", "");
            } else {
                status = cfg.getString("ulepszenia.status-zablokowane", "");
            }

            String[] pary = {
                    "%nr%", String.valueOf(nr),
                    "%koszt%", String.valueOf(koszt),
                    "%bonus%", NekromantaManager.formatSzansy(bonusPoziom),
                    "%suma%", NekromantaManager.formatSzansy(nr * bonusPoziom),
                    "%saldo%", String.valueOf(saldo),
                    "%brakuje%", String.valueOf(Math.max(0L, koszt - saldo))
            };

            ItemStack it = new ItemStack(mat);
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName(manager.zamien(cfg.getString("ulepszenia.nazwa-" + stan, "&fPoziom %nr%"), pary));
                List<String> lore = new ArrayList<>();
                for (String linia : cfg.getStringList("ulepszenia.lore")) {
                    lore.add(manager.zamien(linia.trim().equals("%status%") ? status : linia, pary));
                }
                m.setLore(lore);
                m.addItemFlags(ItemFlag.values());
                if (stan.equals("kupione")) m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
            }
            inv.setItem(slot, it);
        }

        // powrot
        int slotPowrotu = cfg.getInt("ulepszenia.powrot-slot", 22);
        Material powrot = Material.matchMaterial(cfg.getString("gui.powrot-material", "ARROW"));
        if (powrot != null && !powrot.isAir() && slotPowrotu >= 0 && slotPowrotu < inv.getSize()) {
            ItemStack b = new ItemStack(powrot);
            ItemMeta m = b.getItemMeta();
            if (m != null) {
                m.setDisplayName(Msg.color(cfg.getString("gui.powrot-nazwa", "&7Powrot")));
                b.setItemMeta(m);
            }
            inv.setItem(slotPowrotu, b);
        }
    }

    private void kliknijUlepszenie(Player p, Inventory top, int rawSlot) {
        YamlConfiguration cfg = manager.cfg();
        if (rawSlot == cfg.getInt("ulepszenia.powrot-slot", 22)) {
            manager.dzwiek(p, "dzwiek.klik");
            otworz(p);
            return;
        }
        int indeks = rawSlot - cfg.getInt("ulepszenia.pierwszy-slot", 10);
        if (indeks < 0 || indeks >= manager.maxPoziom()) return;

        int nr = indeks + 1;
        int poziom = manager.poziom(p.getUniqueId());
        if (nr <= poziom) {
            manager.wiadomoscRaw(p, "ulepszenie-kupione-juz");
            manager.dzwiek(p, "dzwiek.brak");
        } else if (nr > poziom + 1) {
            manager.wiadomoscRaw(p, "ulepszenie-zablokowane");
            manager.dzwiek(p, "dzwiek.brak");
        } else {
            manager.ulepsz(p);
        }
        wypelnijUlepszenia(top, p); // odswiez stan poziomow i saldo
    }

    // ───────────────────────── wplata ─────────────────────────

    private void wplac(Player p, ClickType klik) {
        boolean wszystkie = klik == ClickType.RIGHT || klik == ClickType.SHIFT_RIGHT;
        int wplacono = manager.wplac(p, wszystkie ? Integer.MAX_VALUE : iloscLpm());
        if (wplacono <= 0) {
            manager.wiadomoscRaw(p, "wplata-brak");
            manager.dzwiek(p, "dzwiek.brak");
            return;
        }
        manager.wiadomoscRaw(p, "wplata", "%ilosc%", String.valueOf(wplacono),
                "%saldo%", String.valueOf(manager.saldo(p.getUniqueId())));
        manager.dzwiek(p, "dzwiek.wplata");
    }

    // ───────────────────────── eventy ─────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof MenuHolder) && !(holder instanceof PodgladHolder)
                && !(holder instanceof UlepszeniaHolder)) return;

        e.setCancelled(true); // nic nie wolno wyjmowac/wkladac (takze shift-click z ekwipunku gracza)
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != top) return;

        if (holder instanceof PodgladHolder) {
            if (e.getRawSlot() == SLOT_POWROT) {
                manager.dzwiek(p, "dzwiek.klik");
                otworz(p);
            }
            return;
        }

        if (holder instanceof UlepszeniaHolder) {
            kliknijUlepszenie(p, top, e.getRawSlot());
            return;
        }

        // przyciski wplaty i ulepszen (menu glowne)
        YamlConfiguration cfg = manager.cfg();
        ClickType rodzaj = e.getClick();
        boolean lewy = rodzaj == ClickType.LEFT || rodzaj == ClickType.SHIFT_LEFT;
        boolean prawy = rodzaj == ClickType.RIGHT || rodzaj == ClickType.SHIFT_RIGHT;
        if (e.getRawSlot() == cfg.getInt("wplata.slot", 20)) {
            if (lewy || prawy) {
                wplac(p, rodzaj);
                wypelnij(top, p);
            }
            return;
        }
        if (e.getRawSlot() == cfg.getInt("ulepszenia-przycisk.slot", 24)) {
            if (lewy || prawy) {
                manager.dzwiek(p, "dzwiek.klik");
                otworzUlepszenia(p);
            }
            return;
        }

        NekromantaManager.Tier kliknity = null;
        for (NekromantaManager.Tier t : manager.getTiery()) {
            if (t.slot == e.getRawSlot()) {
                kliknity = t;
                break;
            }
        }
        if (kliknity == null) return;

        ClickType klik = e.getClick();
        if (klik == ClickType.RIGHT || klik == ClickType.SHIFT_RIGHT) {
            manager.dzwiek(p, "dzwiek.klik");
            otworzPodglad(p, kliknity);
        } else if (klik == ClickType.LEFT || klik == ClickType.SHIFT_LEFT) {
            manager.wymien(p, kliknity);
            wypelnij(top, p); // odswiez liczniki glutow
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof MenuHolder || holder instanceof PodgladHolder || holder instanceof UlepszeniaHolder) {
            e.setCancelled(true);
        }
    }
}
