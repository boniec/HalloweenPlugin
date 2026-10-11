package boniec.szaman.gui;

import boniec.szaman.SzamanPlugin;
import boniec.szaman.core.DropPerkManager;
import boniec.szaman.core.Perk;
import boniec.szaman.core.PerkManager;
import boniec.szaman.core.WalutaManager;
import boniec.szaman.listener.ZyciaListener;
import boniec.szaman.util.ItemBuilder;
import boniec.szaman.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * JEDYNE GUI Szamana (/szaman). Wszystkie perki (artefakty) - Uwiezienie, Zycie, Sila,
 * Wampiryzm i Boost Dropu - sa tu w jednym oknie i ulepsza sie kliknieciem, za
 * Fragmenty Galaktyki (przedmiot ustawiany przez /aszaman ustawodlamek).
 * <p>
 * Perki z poziomami (data.&lt;perk&gt;.&lt;poziom&gt; w szaman.yml) kupuje sie po kolei,
 * poziom po poziomie. Boost Dropu dziala po staremu - kazde klikniecie DOLOSOWUJE
 * procent (patrz {@link DropPerkManager}).
 * <p>
 * Wyglad kazdego perku (slot, material, nazwa, lore, formaty linii poziomow) jest w
 * szaman.yml pod kluczem perku (confinement / health / strength / vampirism / boostdrop).
 * W lore dzialaja znaczniki: {info} (lista poziomow), {level} (aktualny poziom) i
 * {price-info} (koszt kolejnego poziomu albo napis "MAX POZIOM").
 */
public class SzamanGui implements SzamanGuiHolder {

    private static Material safeMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        String normalized = normalizeUnicode(name).trim().toUpperCase(Locale.ROOT);
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Zamienia male kapitaliki (ʀᴇᴅ_ᴄᴏɴᴄʀᴇᴛᴇ) na zwykle litery - nazwy materialow bywaja tak wpisane w configu. */
    private static String normalizeUnicode(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= '\u1D00' && c <= '\u1D25') {
                sb.append((char) (c - '\u1D00' + 'A'));
            } else if (c >= '\u1D26' && c <= '\u1D4A') {
                sb.append((char) (c - '\u1D26' + 'a'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private final SzamanPlugin plugin;
    private final Inventory inventory;
    private final Random random = new Random();
    private final Player wlascicielGui;

    public SzamanGui(SzamanPlugin plugin, Player wlasciciel) {
        this.plugin = plugin;
        this.wlascicielGui = wlasciciel;
        FileConfiguration cfg = plugin.getSzamanConfig();
        String tytul = cfg.getString("gui.szaman-title", "&7Szaman");
        int rozmiar = cfg.getInt("gui.rozmiar", 45);
        if (rozmiar < 9 || rozmiar > 54 || rozmiar % 9 != 0) rozmiar = 45;
        this.inventory = Bukkit.createInventory(this, rozmiar, Msg.color(tytul));
        zbuduj();
    }

    // ── Budowanie GUI ────────────────────────────────────────────────────────

    private void zbuduj() {
        inventory.clear();
        FileConfiguration cfg = plugin.getSzamanConfig();

        // Perki
        Set<Integer> zajete = new HashSet<>();
        for (Perk perk : Perk.values()) {
            if (!perk.isWlaczony()) continue;
            int slot = perk.getSlot();
            if (slot < 0 || slot >= inventory.getSize()) {
                plugin.getLogger().warning("Perk '" + perk.getKlucz() + "' ma niepoprawny slot (" + slot + ") w szaman.yml - pomijam.");
                continue;
            }
            if (!zajete.add(slot)) {
                plugin.getLogger().warning("Dwa wlaczone perki maja ten sam slot (" + slot + ") w szaman.yml - jeden zakryje drugi!");
            }
            inventory.setItem(slot, zbudujItemPerku(perk));
        }
    }

    private ItemStack zbudujItemPerku(Perk perk) {
        Material mat = safeMaterial(perk.getMaterial(), Material.PAPER);
        ItemBuilder builder = new ItemBuilder(mat).name(perk.getDisplayName()).hideFlags();
        for (String linia : zbudujLore(perk)) {
            builder.lore(linia);
        }
        return builder.build();
    }

    private List<String> zbudujLore(Perk perk) {
        List<String> wynik = new ArrayList<>();
        for (String linia : perk.getLore()) {
            String przyciete = linia.trim();
            if (przyciete.equals("{info}")) {
                wynik.addAll(liniePoziomow(perk));
            } else if (przyciete.equals("{price-info}")) {
                wynik.addAll(linieCeny(perk));
            } else {
                wynik.add(linia.replace("{level}", String.valueOf(aktualnyPoziomDoWyswietlenia(perk))));
            }
        }
        return wynik;
    }

    /** Poziom pokazywany w {level}: dla perkow z poziomami - kupiony poziom; dla Boostu Dropu - liczba wykonanych losowan. */
    private int aktualnyPoziomDoWyswietlenia(Perk perk) {
        if (wlascicielGui == null) return 0;
        UUID uuid = wlascicielGui.getUniqueId();
        if (perk.isPoziomowy()) {
            return plugin.getPerkManager().getPoziom(uuid, perk);
        }
        return plugin.getDropPerkManager().getIloscLosowan(uuid);
    }

    /** Lista poziomow perku: kazdy poziom jako osobna linia - "active" (zielona) jesli juz wykupiony, inaczej "no-active" (czerwona). */
    private List<String> liniePoziomow(Perk perk) {
        List<String> linie = new ArrayList<>();
        FileConfiguration cfg = plugin.getSzamanConfig();

        if (perk.isPoziomowy()) {
            int poziom = wlascicielGui == null ? 0 : plugin.getPerkManager().getPoziom(wlascicielGui.getUniqueId(), perk);
            int max = perk.getMaxPoziom();
            for (int i = 1; i <= max; i++) {
                String format = poziom >= i ? perk.getFormatAktywny() : perk.getFormatNieaktywny();
                linie.add(format
                        .replace("{level}", String.valueOf(i))
                        .replace("{value}", perk.getWartoscTekst(i)));
            }
            return linie;
        }

        // Boost Dropu - jedna linia z aktualnym procentem (wlasny + klanowy), opcjonalnie dwie dodatkowe.
        DropPerkManager dpm = plugin.getDropPerkManager();
        UUID uuid = wlascicielGui == null ? null : wlascicielGui.getUniqueId();
        double wlasny = uuid == null ? 0.0 : dpm.getProcentGracza(uuid);
        double klanowy = uuid == null ? 0.0 : dpm.getBonusKlanowy(uuid);
        int losowania = uuid == null ? 0 : dpm.getIloscLosowan(uuid);

        String format = wlasny > 0 ? perk.getFormatAktywny() : perk.getFormatNieaktywny();
        linie.add(format
                .replace("{level}", String.valueOf(losowania))
                .replace("{value}", Perk.formatLiczby(wlasny)));

        String zakres = cfg.getString("boostdrop.range-info", "");
        if (!zakres.isEmpty()) {
            linie.add(zakres
                    .replace("{min}", Perk.formatLiczby(cfg.getDouble("perk-dropu.wartosc-min", 3.0)))
                    .replace("{max}", Perk.formatLiczby(cfg.getDouble("perk-dropu.wartosc-max", 10.0))));
        }
        String klanLinia = cfg.getString("boostdrop.clan-info", "");
        if (!klanLinia.isEmpty() && klanowy > 0) {
            linie.add(klanLinia
                    .replace("{value}", Perk.formatLiczby(klanowy))
                    .replace("{total}", Perk.formatLiczby(wlasny + klanowy)));
        }
        return linie;
    }

    /** Linie ceny: koszt kolejnego poziomu + "kliknij aby ulepszyc", albo napis o maksymalnym poziomie. */
    private List<String> linieCeny(Perk perk) {
        List<String> linie = new ArrayList<>();
        FileConfiguration cfg = plugin.getSzamanConfig();
        String maxTekst = cfg.getString("max-level", "&cMAX POZIOM");

        int koszt;
        if (perk.isPoziomowy()) {
            int poziom = wlascicielGui == null ? 0 : plugin.getPerkManager().getPoziom(wlascicielGui.getUniqueId(), perk);
            if (poziom >= perk.getMaxPoziom()) {
                linie.add(maxTekst);
                return linie;
            }
            koszt = perk.getKoszt(poziom + 1);
        } else {
            DropPerkManager dpm = plugin.getDropPerkManager();
            if (wlascicielGui != null && dpm.maMaksymalnyPerk(wlascicielGui.getUniqueId())) {
                linie.add(maxTekst);
                return linie;
            }
            koszt = wlascicielGui == null ? 0 : dpm.getKosztNastepnegoLosowania(wlascicielGui.getUniqueId());
        }

        linie.add(cfg.getString("price-info", "&f &8▶ &7Koszt ulepszenia: &f&lx{price}")
                .replace("{price}", String.valueOf(koszt)));
        String kliknij = cfg.getString("extra-line-buy", "");
        if (!kliknij.isEmpty()) linie.add(kliknij);
        return linie;
    }

    public void otworz(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ── Klikniecia ───────────────────────────────────────────────────────────

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ClickType typ = event.getClick();
        if (typ != ClickType.LEFT && typ != ClickType.RIGHT && typ != ClickType.SHIFT_LEFT && typ != ClickType.SHIFT_RIGHT) {
            return;
        }
        int slot = event.getSlot();
        for (Perk perk : Perk.values()) {
            if (!perk.isWlaczony() || perk.getSlot() != slot) continue;
            if (perk.isPoziomowy()) {
                ulepszPoziom(perk, player);
            } else {
                losujBoostDropu(player);
            }
            return;
        }
    }

    private String lang(String klucz, String domyslnie) {
        return plugin.getSzamanConfig().getString("lang." + klucz, domyslnie);
    }

    /**
     * Pobiera "koszt" Fragmentow Galaktyki z ekwipunku gracza. Zwraca false (i wysyla
     * komunikat), gdy waluta nie jest ustawiona albo gracz ma za malo.
     */
    private boolean zaplac(Player player, int koszt) {
        if (koszt <= 0) return true;

        WalutaManager waluta = plugin.getWalutaManager();
        ItemStack wymagany = waluta.getOdlamek();
        if (wymagany == null) {
            Msg.send(player, lang("no-currency-set", "&cAdmin nie ustawil jeszcze waluty (Fragmentu Galaktyki)!"));
            return false;
        }
        wymagany.setAmount(koszt);
        if (!player.getInventory().containsAtLeast(wymagany, koszt)) {
            Msg.send(player, lang("no-points", "&cNie posiadasz tylu Fragmentów Galaktyki!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        player.getInventory().removeItem(wymagany);
        return true;
    }

    /** Zakup kolejnego poziomu perku z tabeli "data". */
    private void ulepszPoziom(Perk perk, Player player) {
        PerkManager pm = plugin.getPerkManager();
        UUID uuid = player.getUniqueId();

        int max = perk.getMaxPoziom();
        int poziom = pm.getPoziom(uuid, perk);
        if (max <= 0) {
            plugin.getLogger().warning("Perk '" + perk.getKlucz() + "' nie ma zadnych poziomow w sekcji data szaman.yml.");
            return;
        }
        if (poziom >= max) {
            Msg.send(player, lang("max-level", "&cPosiadasz najwyzszy poziom!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int koszt = perk.getKoszt(poziom + 1);
        if (koszt < 0) {
            plugin.getLogger().warning("Perk '" + perk.getKlucz() + "' nie ma kosztu dla poziomu " + (poziom + 1) + " w szaman.yml.");
            return;
        }
        if (!zaplac(player, koszt)) return;

        pm.ustawPoziom(uuid, perk, poziom + 1);
        Msg.send(player, lang("success-upgrade", "&aPomyslnie ulepszono!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        if (perk == Perk.HEALTH) {
            ZyciaListener.odswiezZdrowie(player);
        }
        zbuduj();
    }

    /**
     * Boost Dropu - kazde klikniecie dolosowuje procent do dropu z generatorow. Koszt rosnie
     * progresywnie z liczba wykonanych losowan, a po przekroczeniu progu wysokiego kosztu
     * kazde losowanie kosztuje stala, wyzsza cene. Twardy limit procentu - po jego osiagnieciu
     * losowanie jest zablokowane.
     */
    private void losujBoostDropu(Player player) {
        FileConfiguration cfg = plugin.getSzamanConfig();
        DropPerkManager dpm = plugin.getDropPerkManager();
        UUID uuid = player.getUniqueId();

        if (dpm.maMaksymalnyPerk(uuid)) {
            Msg.send(player, lang("boostdrop-max", "&cMasz juz maksymalny Boost Dropu ({limit}%)!")
                    .replace("{limit}", Perk.formatLiczby(dpm.getLimit())));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int koszt = dpm.getKosztNastepnegoLosowania(uuid);
        if (!zaplac(player, koszt)) return;

        double wartoscMin = cfg.getDouble("perk-dropu.wartosc-min", 3.0);
        double wartoscMax = cfg.getDouble("perk-dropu.wartosc-max", 10.0);
        if (wartoscMax < wartoscMin) wartoscMax = wartoscMin;
        double wylosowana = wartoscMin + random.nextDouble() * (wartoscMax - wartoscMin);

        double dodane = dpm.dodajProcent(uuid, wylosowana);
        double nowy = dpm.getProcentGracza(uuid);

        Msg.send(player, lang("boostdrop-success", "&aTwoj Boost Dropu wzrosl o &e+{value}% &a(teraz: &e{total}%&a)")
                .replace("{value}", Perk.formatLiczby(Math.round(dodane * 100.0) / 100.0))
                .replace("{total}", Perk.formatLiczby(Math.round(nowy * 100.0) / 100.0)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        zbuduj();
    }
}
