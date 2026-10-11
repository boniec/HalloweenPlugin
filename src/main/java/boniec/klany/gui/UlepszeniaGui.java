package boniec.klany.gui;

import boniec.klany.Main;
import boniec.klany.core.Klan;
import boniec.klany.core.KlanWalutaManager;
import boniec.klany.util.Gradient;
import boniec.klany.util.ItemBuilder;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /klan ulepszenia - ulepszenia klanu kupowane POZIOM PO POZIOMIE za smocze odlamki
 * (zastapily dawne losowanie perkow Odpornosc/Sila/Zrecznosc/Inteligencja).
 * <p>
 * Dwa ulepszenia:
 *  - DROP    ({@link Klan#PERK_DROP})    - dodatkowy % dropu z generatorow dla KAZDEGO czlonka
 *                                          klanu (dolicza sie do Boostu Dropu Szamana),
 *  - RANKING ({@link Klan#PERK_RANKING}) - dodatkowy % rankingu (ELO) zdobywanego przez
 *                                          czlonkow klanu za zabojstwo PvP.
 * <p>
 * Poziomy, koszty i wartosci sa w klany-config.yml -> ulepszenia-gui.perki.&lt;drop|ranking&gt;.poziomy
 * (lista wpisow {koszt, wartosc}). Gdy configu brakuje (stary plik na serwerze), uzywane sa
 * domyslne wartosci z kodu, wiec GUI dziala od razu po aktualizacji. Wartosc ulepszenia jest
 * zapisywana w klanie jako procent (Klan#getPerk), poziom wynika z liczby progow, ktore ten
 * procent osiagnal.
 * <p>
 * Ulepszac moze lider/zastepca; odlamki sa brane z EKWIPUNKU klikajacego (jak wczesniej).
 */
public class UlepszeniaGui implements KlanGui, InventoryHolder {

    private static final String[] PERKI_ID = {Klan.PERK_DROP, Klan.PERK_RANKING, Klan.PERK_LIMIT_GRACZY};

    private static final class Poziom {
        final int koszt;
        final double wartosc;
        Poziom(int koszt, double wartosc) { this.koszt = koszt; this.wartosc = wartosc; }
    }

    private final Klan klan;
    private final Inventory inventory;
    private final ConfigurationSection sekcjaGui;
    private final Material waluta;
    /** true = podglad admina (/adminklan sprawdz) - nic nie mozna kupic. */
    private final boolean tylkoPodglad;

    public UlepszeniaGui(Klan klan) {
        this(klan, false);
    }

    public UlepszeniaGui(Klan klan, boolean tylkoPodglad) {
        this.klan = klan;
        this.tylkoPodglad = tylkoPodglad;
        this.sekcjaGui = Main.get().getConfig().getConfigurationSection("ulepszenia-gui");
        Material m = Material.matchMaterial(Main.get().getConfig().getString("ustawienia.waluta-material", "DRAGON_BREATH"));
        this.waluta = m != null ? m : Material.DRAGON_BREATH;
        String tytul = tylkoPodglad
                ? Msg.color("&8» &5Ulepszenia klanu &f" + klan.getTag() + " &8«")
                : Msg.guiTitle("tytul-ulepszenia");
        this.inventory = Bukkit.createInventory(this, 54, tytul);
        zbuduj();
    }

    // ── Konfiguracja perkow (z configu, z awaryjnymi domyslnymi) ──────────────

    private ConfigurationSection sekcjaPerku(String id) {
        return sekcjaGui == null ? null : sekcjaGui.getConfigurationSection("perki." + id);
    }

    private int slot(String id) {
        ConfigurationSection c = sekcjaPerku(id);
        int domyslny;
        if (id.equals(Klan.PERK_DROP)) domyslny = 21;
        else if (id.equals(Klan.PERK_RANKING)) domyslny = 23;
        else if (id.equals(Klan.PERK_LIMIT_GRACZY)) domyslny = 25;
        else domyslny = 21;
        return c != null && c.contains("slot") ? c.getInt("slot") : domyslny;
    }

    private String nazwa(String id) {
        ConfigurationSection c = sekcjaPerku(id);
        String domyslna;
        if (id.equals(Klan.PERK_DROP)) domyslna = "Drop z generatorów";
        else if (id.equals(Klan.PERK_RANKING)) domyslna = "Zwiększony ranking za zabójstwo";
        else if (id.equals(Klan.PERK_LIMIT_GRACZY)) domyslna = "Zwiększony limit graczy";
        else domyslna = "Perk";
        return c != null ? c.getString("nazwa", domyslna) : domyslna;
    }

    /** Lista poziomow perku: z configu (poziomy: - {koszt: 30, wartosc: 2}) albo domyslna. */
    private List<Poziom> poziomy(String id) {
        List<Poziom> wynik = new ArrayList<>();
        ConfigurationSection c = sekcjaPerku(id);
        if (c != null) {
            for (java.util.Map<?, ?> wpis : c.getMapList("poziomy")) {
                Object k = wpis.get("koszt");
                Object w = wpis.get("wartosc");
                if (!(k instanceof Number) || !(w instanceof Number)) continue;
                wynik.add(new Poziom(((Number) k).intValue(), ((Number) w).doubleValue()));
            }
        }
        if (!wynik.isEmpty()) return wynik;
        if (id.equals(Klan.PERK_LIMIT_GRACZY)) {
            for (int i = 1; i <= 5; i++) {
                wynik.add(new Poziom(100 * i, i));
            }
            return wynik;
        }
        double krok = 10.0;
        for (int i = 1; i <= 5; i++) {
            wynik.add(new Poziom(30 * i, krok * i));
        }
        return wynik;
    }

    /** Aktualny poziom klanu (0..max) - ile progow osiagnela zapisana wartosc. */
    private int aktualnyPoziom(String id) {
        if (id.equals(Klan.PERK_LIMIT_GRACZY)) {
            return Math.min(poziomy(id).size(), klan.getDodatkoweMiejsca());
        }
        double wartosc = klan.getPerk(id);
        int poziom = 0;
        for (Poziom p : poziomy(id)) {
            if (wartosc + 1e-9 >= p.wartosc) poziom++; else break;
        }
        return poziom;
    }

    // ── Budowanie GUI ────────────────────────────────────────────────────────

    private void zbuduj() {
        if (sekcjaGui == null) {
            Bukkit.getLogger().warning("[Klany] Brak sekcji 'ulepszenia-gui' w klany-config.yml - GUI ulepszen bedzie bez tla.");
        } else {
            zbudujTlo();
        }
        for (String id : PERKI_ID) {
            int s = slot(id);
            if (s >= 0 && s < inventory.getSize()) inventory.setItem(s, zbudujItemPerku(id));
        }
        zbudujPowrot();
    }

    private void zbudujTlo() {
        ConfigurationSection tlo = sekcjaGui.getConfigurationSection("tlo");
        if (tlo == null) return;
        wypelnijSzklem(tlo.getIntegerList("zolte"), Material.YELLOW_STAINED_GLASS_PANE);
        wypelnijSzklem(tlo.getIntegerList("pomaranczowe"), Material.ORANGE_STAINED_GLASS_PANE);
        wypelnijSzklem(tlo.getIntegerList("biale"), Material.WHITE_STAINED_GLASS_PANE);
    }

    private void wypelnijSzklem(List<Integer> sloty, Material material) {
        ItemStack szklo = new ItemBuilder(material).name(" ").hideFlags().build();
        for (int s : sloty) {
            if (s >= 0 && s < inventory.getSize()) inventory.setItem(s, szklo);
        }
    }

    private ItemStack zbudujItemPerku(String id) {
        ConfigurationSection c = sekcjaPerku(id);
        String domMat;
        if (id.equals(Klan.PERK_DROP)) domMat = "NETHERITE_SHOVEL";
        else if (id.equals(Klan.PERK_LIMIT_GRACZY)) domMat = "PLAYER_HEAD";
        else domMat = "NETHERITE_SWORD";
        Material material = Material.matchMaterial(c != null ? c.getString("material", domMat) : domMat);
        if (material == null) material = Material.PAPER;

        List<Poziom> poziomy = poziomy(id);
        int max = poziomy.size();
        int poziom = aktualnyPoziom(id);
        boolean naMaksie = poziom >= max;
        double obecny = Math.min(klan.getPerk(id), poziomy.get(max - 1).wartosc);
        Poziom nastepny = naMaksie ? null : poziomy.get(poziom);

        String nazwaSkolorowana = c != null
                ? Gradient.apply(nazwa(id), c.getString("gradient-od"), c.getString("gradient-do"), true)
                : Msg.color("&b&l" + nazwa(id));

        List<String> surowe = lorePerku(id, c);
        int extraTeraz = klan.getDodatkoweMiejsca();
        int extraNext = nastepny == null ? extraTeraz : Math.min(max, poziom + 1);
        int limitTeraz = Main.get().getKlanManager().getLimitCzlonkow(klan);
        int baza = Math.max(1, limitTeraz - extraTeraz);
        String limitNext = String.valueOf(Math.min(10, baza + extraNext));
        List<String> lore = new ArrayList<>();
        for (String linia : surowe) {
            if (naMaksie && linia.contains("%koszt%")) continue;
            if (naMaksie && (linia.contains("%nastepny%") || linia.contains("%limit-nastepny%") || linia.contains("%klik%"))) continue;
            if (!naMaksie && linia.contains("%max-poziom%")) continue;
            lore.add(Msg.color(linia
                    .replace("%poziom%", String.valueOf(poziom))
                    .replace("%max%", String.valueOf(max))
                    .replace("%bonus%", formatujLiczbe(id.equals(Klan.PERK_LIMIT_GRACZY) ? extraTeraz : obecny))
                    .replace("%nastepny%", nastepny == null ? "" : formatujLiczbe(
                            id.equals(Klan.PERK_LIMIT_GRACZY) ? extraNext : nastepny.wartosc))
                    .replace("%limit%", String.valueOf(limitTeraz))
                    .replace("%limit-nastepny%", limitNext)
                    .replace("%koszt%", nastepny == null ? "" : String.valueOf(nastepny.koszt))
                    .replace("%skarbiec%", String.valueOf(klan.getSkarbiec()))
                    .replace("%klik%", "")
                    .replace("%max-poziom%", "")));
        }

        ItemBuilder builder = new ItemBuilder(material).nameRaw(nazwaSkolorowana).loreRaw(lore).hideFlags();
        if (naMaksie) builder.glow();
        return ustawCustomModelData(builder.build(), c != null ? c.getInt("custom-model-data", 0) : 0);
    }

    private List<String> lorePerku(String id, ConfigurationSection c) {
        List<String> zCfg = c != null && c.isList("lore") ? c.getStringList("lore") : List.of();
        if (id.equals(Klan.PERK_LIMIT_GRACZY)) {
            boolean maLimit = zCfg.stream().anyMatch(s -> s.contains("%limit%"));
            boolean procent = zCfg.stream().anyMatch(s -> s.contains("%%"));
            if (zCfg.isEmpty() || !maLimit || procent) return domyslneLore(id);
        }
        return zCfg.isEmpty() ? domyslneLore(id) : zCfg;
    }

    private List<String> domyslneLore(String id) {
        List<String> l = new ArrayList<>();
        l.add("");
        if (id.equals(Klan.PERK_DROP)) {
            l.add("&7Zwiększa drop z generatorów");
            l.add("&7dla &fwszystkich członków &7klanu.");
        } else if (id.equals(Klan.PERK_RANKING)) {
            l.add("&7Zwiększa ranking zdobywany");
            l.add("&7za zabójstwo przez członków klanu.");
        } else if (id.equals(Klan.PERK_LIMIT_GRACZY)) {
            l.add("&7Zwiększa limit członków o +1");
            l.add("&7na poziom (max 10 osób).");
        } else {
            l.add("&7Perk klanowy.");
        }
        l.add("");
        l.add("&7Poziom: &a%poziom%&7/&f%max%");
        if (id.equals(Klan.PERK_LIMIT_GRACZY)) {
            l.add("&7Aktualny limit: &a%limit% &7osob");
            l.add("");
            l.add("&7Nastepny limit: &a%limit-nastepny% &7osob");
        } else {
            l.add("&7Aktualny bonus: &a+%bonus%%");
            l.add("");
            l.add("&7Nastepny poziom: &a+%nastepny%%");
        }
        l.add("&7Koszt: &f%koszt% &5smoczych odłamków");
        l.add("&aKliknij, aby ulepszyć!%klik%");
        l.add("&c&lMAKSYMALNY POZIOM%max-poziom%");
        return l;
    }

    private void zbudujPowrot() {
        if (sekcjaGui == null) return;
        ConfigurationSection powrot = sekcjaGui.getConfigurationSection("powrot");
        if (powrot == null) return;
        int s = powrot.getInt("slot", -1);
        if (s < 0 || s >= inventory.getSize()) return;
        Material material = Material.matchMaterial(powrot.getString("material", "ARROW"));
        if (material == null) material = Material.ARROW;
        String nazwaSkolorowana = Gradient.apply(powrot.getString("nazwa", "Powrót"),
                powrot.getString("gradient-od"), powrot.getString("gradient-do"), true);
        inventory.setItem(s, new ItemBuilder(material).nameRaw(nazwaSkolorowana).build());
    }

    private ItemStack ustawCustomModelData(ItemStack item, int customModelData) {
        if (customModelData <= 0) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatujLiczbe(double wartosc) {
        String tekst = (wartosc == Math.floor(wartosc)) ? String.valueOf((long) wartosc) : String.valueOf(wartosc);
        return tekst.replace('.', ',');
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ── Klikniecia / zakup ───────────────────────────────────────────────────

    @Override
    public void onClick(InventoryClickEvent event, Player gracz) {
        int s = event.getSlot();
        if (sekcjaGui != null && s == sekcjaGui.getInt("powrot.slot", -1)) {
            gracz.closeInventory();
            return;
        }
        if (tylkoPodglad) return;
        for (String id : PERKI_ID) {
            if (s == slot(id)) {
                kup(gracz, id);
                return;
            }
        }
    }

    private void kup(Player gracz, String id) {
        if (!klan.maUprawnieniaZarzadzania(gracz.getUniqueId())) {
            Msg.send(gracz, "brak-uprawnien");
            return;
        }
        List<Poziom> poziomy = poziomy(id);
        int poziom = aktualnyPoziom(id);
        if (poziom >= poziomy.size()) {
            Msg.sendGui(gracz, Main.get().getConfig().getString("wiadomosci.ulepszenie-max", "&cTo ulepszenie ma juz maksymalny poziom!"));
            gracz.playSound(gracz.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        Poziom nastepny = poziomy.get(poziom);
        if (policzOdlamkiWEq(gracz) < nastepny.koszt) {
            Msg.send(gracz, "niewystarczajace-srodki", "%ilosc%", nastepny.koszt, "%material%", waluta.name());
            gracz.playSound(gracz.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        zabierzOdlamkiZEq(gracz, nastepny.koszt);

        int nowyPoziom = poziom + 1;
        if (id.equals(Klan.PERK_LIMIT_GRACZY)) {
            int extra = Math.min(poziomy.size(), nowyPoziom);
            Main.get().getKlanManager().ustawPerk(klan, id, extra);
            int limit = Main.get().getKlanManager().getLimitCzlonkow(klan);
            gracz.playSound(gracz.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            String komunikat = Main.get().getConfig().getString("wiadomosci.ulepszenie-sukces-limit",
                    "&aUlepszono limit graczy na poziom &e%poziom% &a(limit &e%limit% &aosob, +1 miejsce)!");
            Msg.sendGui(gracz, komunikat
                    .replace("%perk%", Msg.color(nazwa(id)))
                    .replace("%poziom%", String.valueOf(extra))
                    .replace("%limit%", String.valueOf(limit))
                    .replace("%wartosc%", "1"));
        } else {
            double roznica = nastepny.wartosc - klan.getPerk(id);
            Main.get().getKlanManager().dodajDoPerku(klan, id, roznica);
            gracz.playSound(gracz.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            String komunikat = Main.get().getConfig().getString("wiadomosci.ulepszenie-sukces",
                    "&aUlepszono &e%perk% &ana poziom &e%poziom% &a(+%wartosc%%)!");
            Msg.sendGui(gracz, komunikat
                    .replace("%perk%", Msg.color(nazwa(id)))
                    .replace("%poziom%", String.valueOf(nowyPoziom))
                    .replace("%wartosc%", formatujLiczbe(nastepny.wartosc)));
        }
        boniec.ustawienia.UstawieniaManager.broadcast(boniec.ustawienia.UstawieniaKlucz.KLANY,
                Msg.prefix() + Msg.color(
                "轉 &8☰ &aKlan &2" + klan.getNazwa() + " &aulepszyl: &2" + nazwa(id) + " &a(poziom " + nowyPoziom + ")"));

        new UlepszeniaGui(klan).otworz(gracz);
    }

    /**
     * Liczy ile odlamkow (fizycznych itemow) gracz ma w swoim ekwipunku.
     * Jesli admin ustawil customowy szablon odlamka (klany-odlamek.yml), porownywane sa
     * itemy przez ItemStack#isSimilar (material + nazwa/lore/NBT), zeby nie liczyc "zwyklego"
     * DRAGON_BREATH jako waluty gdy serwer uzywa customowego przedmiotu. Jesli szablon nie
     * zostal jeszcze ustawiony, liczony jest po prostu material "ustawienia.waluta-material".
     */
    private int policzOdlamkiWEq(Player gracz) {
        ItemStack szablon = pobierzSzablonOdlamka();
        int suma = 0;
        for (ItemStack item : gracz.getInventory().getContents()) {
            if (item == null) continue;
            if (pasujeDoWaluty(item, szablon)) {
                suma += item.getAmount();
            }
        }
        return suma;
    }

    /** Zabiera podana ilosc odlamkow z ekwipunku gracza (zaklada, ze wczesniej sprawdzono ich ilosc). */
    private void zabierzOdlamkiZEq(Player gracz, int ile) {
        ItemStack szablon = pobierzSzablonOdlamka();
        int pozostaloDoZabrania = ile;
        ItemStack[] zawartosc = gracz.getInventory().getContents();
        for (int i = 0; i < zawartosc.length && pozostaloDoZabrania > 0; i++) {
            ItemStack item = zawartosc[i];
            if (item == null || !pasujeDoWaluty(item, szablon)) continue;

            int zabierz = Math.min(item.getAmount(), pozostaloDoZabrania);
            int nowaIlosc = item.getAmount() - zabierz;
            if (nowaIlosc <= 0) {
                gracz.getInventory().setItem(i, null);
            } else {
                item.setAmount(nowaIlosc);
            }
            pozostaloDoZabrania -= zabierz;
        }
        gracz.updateInventory();
    }

    /** Czy dany item w ekwipunku liczy sie jako "smoczy odlamek" (waluta klanowa)? */
    private boolean pasujeDoWaluty(ItemStack item, ItemStack szablon) {
        if (szablon != null) {
            // Customowy przedmiot ustawiony przez admina - porownanie pelne (material, nazwa, lore, NBT itd.),
            // ale bez uwzgledniania ilosci (isSimilar ignoruje amount).
            return item.isSimilar(szablon);
        }
        // Brak customowego szablonu - awaryjny fallback po samym materiale z configu.
        return item.getType() == waluta;
    }

    /** Zwraca szablon odlamka z klany-odlamek.yml (KlanWalutaManager) albo null, jesli admin go nie ustawil. */
    private ItemStack pobierzSzablonOdlamka() {
        KlanWalutaManager manager = Main.get().getKlanWalutaManager();
        return manager != null ? manager.getOdlamekTemplate() : null;
    }
}
