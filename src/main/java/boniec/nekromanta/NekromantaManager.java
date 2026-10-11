package boniec.nekromanta;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nekromanta - wymiana glutow na losowe miecze.
 * <p>
 * - konfiguracja i teksty: nekromanta.yml,
 * - pule nagrod, saldo/poziomy i szablon gluta: nekromanta.db (SQLite),
 * - gluty rozpoznawane po tagu PDC (zmiana nazwy w configu nie psuje wydanych glutow),
 * - gluty mozna WPLACAC do nekromanty (saldo gracza w nekromanta.db),
 * - saldo + ekwipunek licza sie przy wymianie pakietow (najpierw schodzi saldo, reszta z EQ),
 * - za saldo kupuje sie tez ULEPSZENIA, ktore zwiekszaja szanse dropu gluta z welny (patrz {@link #bonusProcent}).
 */
public class NekromantaManager {

    public static final double SZANSA_MIN = 0.0;
    public static final double SZANSA_MAX = 100.0;
    private static final long COOLDOWN_MS = 600L;

    public static final class Wpis {
        private final ItemStack item;
        private double szansa;

        public Wpis(ItemStack item, double szansa) {
            this.item = item;
            this.szansa = clamp(szansa);
        }

        public ItemStack getItem() { return item; }
        public double getSzansa() { return szansa; }
        public void setSzansa(double szansa) { this.szansa = clamp(szansa); }
    }

    public static final class Tier {
        public final int id;
        public final int koszt;
        public final int slot;
        public final Material material;
        public final String kolor;   // hex bez '#', np. A6E34A
        public final String nazwa;

        Tier(int id, int koszt, int slot, Material material, String kolor, String nazwa) {
            this.id = id;
            this.koszt = koszt;
            this.slot = slot;
            this.material = material;
            this.kolor = kolor;
            this.nazwa = nazwa;
        }

        /** Kolor w formacie &x&R&R&G&G&B&B (do podstawienia za %kolor%). */
        public String kolorKod() {
            StringBuilder sb = new StringBuilder("&x");
            for (char c : kolor.toCharArray()) sb.append('&').append(c);
            return sb.toString();
        }
    }

    /** Saldo (wplacone gluty) i poziom ulepszen jednego gracza. */
    private static final class Dane {
        private long saldo;
        private int poziom;
    }

    public enum WynikUlepszenia { OK, MAX, BRAK_SALDA }

    private static final List<Integer> DOMYSLNE_KOSZTY = List.of(500, 1000, 1500, 2000, 2500, 3000);

    private final JavaPlugin plugin;
    private final File plikConfig;
    private final NekromantaDatabase baza;
    /** Szablon gluta ustawiony komenda /anekromanta ustawglut (null = glut z nekromanta.yml). */
    private ItemStack glutSzablon;
    private final NamespacedKey kluczGlut;
    /** Klucz PDC dla czerwonego gluta (1 sztuka = 10 zwykłych). */
    private final NamespacedKey kluczCzerwonyGlut;
    private YamlConfiguration cfg;
    private final Map<Integer, Tier> tiery = new TreeMap<>();
    private final Map<Integer, List<Wpis>> dropy = new HashMap<>();
    private final Map<UUID, Long> ostatniaWymiana = new HashMap<>();
    private final Map<UUID, Dane> gracze = new HashMap<>();

    public NekromantaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plikConfig = new File(plugin.getDataFolder(), "nekromanta.yml");
        this.baza = new NekromantaDatabase(plugin);
        this.kluczGlut = new NamespacedKey(plugin, "nekromanta_glut");
        this.kluczCzerwonyGlut = new NamespacedKey(plugin, "nekromanta_czerwony_glut");
        this.baza.open();
        przeladuj();
    }

    // ───────────────────────── konfiguracja / dane ─────────────────────────

    public void przeladuj() {
        zapiszGraczy();
        plugin.getDataFolder().mkdirs();
        if (!plikConfig.exists()) plugin.saveResource("nekromanta.yml", false);
        cfg = YamlConfiguration.loadConfiguration(plikConfig);
        wczytajTiery();
        wczytajDropy();
        wczytajGlut();
        wczytajGraczy();
    }

    private void wczytajTiery() {
        tiery.clear();
        ConfigurationSection sekcja = cfg.getConfigurationSection("tiery");
        if (sekcja == null) return;
        for (String klucz : sekcja.getKeys(false)) {
            ConfigurationSection t = sekcja.getConfigurationSection(klucz);
            if (t == null) continue;
            int id;
            try {
                id = Integer.parseInt(klucz);
            } catch (NumberFormatException e) {
                continue;
            }
            Material mat = Material.matchMaterial(t.getString("material", "IRON_SWORD"));
            if (mat == null) mat = Material.IRON_SWORD;
            String kolor = t.getString("kolor", "FFFFFF").replace("#", "").toUpperCase(Locale.ROOT);
            if (!kolor.matches("[0-9A-F]{6}")) kolor = "FFFFFF";
            tiery.put(id, new Tier(id, Math.max(1, t.getInt("koszt", 100)), t.getInt("slot", 13),
                    mat, kolor, t.getString("nazwa", "&fPakiet " + id)));
        }
    }

    private void wczytajDropy() {
        dropy.clear();
        for (int id : tiery.keySet()) dropy.put(id, new ArrayList<>());
        for (Map.Entry<Integer, List<NekromantaDatabase.WpisDanych>> e : baza.wczytajDropy().entrySet()) {
            List<Wpis> lista = dropy.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            for (NekromantaDatabase.WpisDanych w : e.getValue()) {
                lista.add(new Wpis(w.item, w.szansa));
            }
        }
    }

    public void zapisz() {
        baza.zapiszDropy(dropy);
    }

    public YamlConfiguration cfg() { return cfg; }

    public java.util.Collection<Tier> getTiery() { return tiery.values(); }

    public Tier getTier(int id) { return tiery.get(id); }

    /** Pakiet po ID (1,2,3) albo po koszcie (100,200,300). */
    public Tier znajdzTier(String arg) {
        if (arg == null) return null;
        int n;
        try {
            n = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return null;
        }
        for (Tier t : tiery.values()) if (t.koszt == n) return t;
        return tiery.get(n);
    }

    public List<Wpis> getWpisy(Tier t) {
        return dropy.getOrDefault(t.id, new ArrayList<>());
    }

    public void dodaj(Tier t, ItemStack item, double szansa) {
        dropy.computeIfAbsent(t.id, k -> new ArrayList<>()).add(new Wpis(item.clone(), szansa));
        zapisz();
    }

    public boolean usun(Tier t, int numer) {
        List<Wpis> l = getWpisy(t);
        if (numer < 1 || numer > l.size()) return false;
        l.remove(numer - 1);
        zapisz();
        return true;
    }

    public boolean ustawSzanse(Tier t, int numer, double szansa) {
        List<Wpis> l = getWpisy(t);
        if (numer < 1 || numer > l.size()) return false;
        l.get(numer - 1).setSzansa(szansa);
        zapisz();
        return true;
    }

    /** Suma szans wpisow w pakiecie (do przeliczania na prawdziwe procenty). */
    public double sumaSzans(Tier t) {
        double s = 0;
        for (Wpis w : getWpisy(t)) if (w.getSzansa() > 0) s += w.getSzansa();
        return s;
    }

    /** Szansa wpisu po przeliczeniu na 100% (to, co faktycznie obowiazuje przy losowaniu). */
    public double szansaEfektywna(Tier t, Wpis w) {
        double suma = sumaSzans(t);
        if (suma <= 0 || w.getSzansa() <= 0) return 0;
        return w.getSzansa() / suma * 100.0;
    }

    // ───────────────────────── glut ─────────────────────────

    private void wczytajGlut() {
        glutSzablon = null;
        ItemStack it = baza.wczytajGlut();
        if (it != null && !it.getType().isAir()) {
            glutSzablon = oznaczZwyklyGlut(it);
        }
    }

    /**
     * Ustawia gluta na podstawie itemu (nazwa, lore, model, enchanty...). Zwraca kopie z tagiem gluta
     * (ilosc bez zmian), ktora trzeba podmienic w rece admina - dzieki temu od razu da sie jej uzyc w /awelna dodaj.
     */
    public ItemStack ustawGlut(ItemStack zrodlo) {
        ItemStack szablon = zrodlo.clone();
        szablon.setAmount(1);
        ItemMeta meta = szablon.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(kluczGlut, PersistentDataType.BYTE, (byte) 1);
            szablon.setItemMeta(meta);
        }
        glutSzablon = szablon;
        baza.zapiszGlut(szablon);

        ItemStack wynik = szablon.clone();
        wynik.setAmount(zrodlo.getAmount());
        return wynik;
    }

    /** Wraca do gluta z nekromanta.yml. @return true, jesli szablon byl ustawiony. */
    public boolean resetujGlut() {
        boolean byl = glutSzablon != null;
        glutSzablon = null;
        baza.usunGlut();
        return byl;
    }

    public boolean maWlasnegoGluta() {
        return glutSzablon != null;
    }

    public ItemStack stworzGlut(int ilosc) {
        if (glutSzablon != null) {
            ItemStack it = glutSzablon.clone();
            it.setAmount(Math.max(1, Math.min(ilosc, it.getMaxStackSize())));
            return oznaczZwyklyGlut(it);
        }
        Material m = Material.matchMaterial(cfg.getString("glut.material", "SLIME_BALL"));
        if (m == null || m.isAir()) m = Material.SLIME_BALL;
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(ilosc, m.getMaxStackSize())));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(Msg.color(cfg.getString("glut.name", "&aGlut Nekromanty")));
        List<String> lore = new ArrayList<>();
        for (String l : cfg.getStringList("glut.lore")) lore.add(Msg.color(l));
        meta.setLore(lore);
        int cmd = cfg.getInt("glut.custom-model-data", 0);
        if (cmd > 0) meta.setCustomModelData(cmd);
        if (cfg.getBoolean("glut.blysk", true)) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(kluczGlut, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    /** Tworzy czerwony gluta (1 sztuka = 10 zwykłych). */
    public ItemStack stworzCzerwonyGlut(int ilosc) {
        Material m = Material.matchMaterial(cfg.getString("czerwony-glut.material", "SLIME_BALL"));
        if (m == null || m.isAir()) m = Material.SLIME_BALL;
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(ilosc, m.getMaxStackSize())));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(Msg.color(cfg.getString("czerwony-glut.nazwa", "&cCzerwony Glut Nekromanty")));
        List<String> lore = new ArrayList<>();
        for (String l : cfg.getStringList("czerwony-glut.lore")) lore.add(Msg.color(l));
        meta.setLore(lore);
        int cmd = cfg.getInt("czerwony-glut.custom-model-data", 0);
        if (cmd > 0) meta.setCustomModelData(cmd);
        if (cfg.getBoolean("czerwony-glut.blysk", true)) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(kluczCzerwonyGlut, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean jestGlut(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (maTagZwyklyGlut(it)) return true;
        return wygladaJakZwyklyGlut(it);
    }

    private boolean maTagZwyklyGlut(ItemStack it) {
        if (!it.hasItemMeta()) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(kluczGlut, PersistentDataType.BYTE);
    }

    /** Sprawdza czy item to czerwony gluta (1 sztuka = 10 zwykłych). */
    public boolean jestCzerwonyGlut(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (maTagCzerwonyGlut(it)) return true;
        return wygladaJakCzerwonyGlut(it);
    }

    private boolean maTagCzerwonyGlut(ItemStack it) {
        if (!it.hasItemMeta()) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(kluczCzerwonyGlut, PersistentDataType.BYTE);
    }

    /** Zwraca wartość gluta w "jednostkach": zwykły = 1, czerwony = 10. */
    private int wartoscGluta(ItemStack it) {
        if (jestCzerwonyGlut(it)) return it.getAmount() * 10;
        if (jestGlut(it)) return it.getAmount();
        return 0;
    }

    public int ileGlutow(Player p) {
        int suma = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            suma += wartoscGluta(it);
        }
        return suma;
    }

    /** Gluty w EQ + wplacone saldo (do wymiany i wyswietlania w GUI). */
    public long lacznieGlutow(Player p) {
        return saldo(p.getUniqueId()) + ileGlutow(p);
    }

    /**
     * Pobiera koszt wymiany: najpierw z salda, reszte z ekwipunku.
     * @return false, jesli lacznie brakuje glutow (nic nie zmienia)
     */
    private boolean pobierzKosztWymiany(Player p, int koszt) {
        if (koszt <= 0) return true;
        if (lacznieGlutow(p) < koszt) return false;
        UUID id = p.getUniqueId();
        Dane d = dane(id);
        int zSalda = (int) Math.min(d.saldo, koszt);
        if (zSalda > 0) {
            d.saldo -= zSalda;
            zapiszGracza(id);
        }
        int zEq = koszt - zSalda;
        if (zEq > 0) {
            int zabrano = zabierzGluty(p, zEq);
            if (zabrano < zEq) {
                d.saldo += zSalda;
                zapiszGracza(id);
                return false;
            }
        }
        return true;
    }

    /** Zabiera do {@code ile} glutow z ekwipunku. @return ile faktycznie zabrano (w jednostkach). */
    private int zabierzGluty(Player p, int ile) {
        ItemStack[] zawartosc = p.getInventory().getContents();
        int zostalo = ile;
        int zabrano = 0;

        // Najpierw zabieramy czerwone gluty (wartość 10)
        for (int i = 0; i < zawartosc.length && zostalo > 0; i++) {
            ItemStack it = zawartosc[i];
            if (!jestCzerwonyGlut(it)) continue;
            int wartoscItemu = it.getAmount() * 10;
            int doZabrania = Math.min(zostalo, wartoscItemu);
            int iloscSztych = (doZabrania + 9) / 10; // Zaokrąglenie w górę
            int faktycznieZabrane = iloscSztych * 10;
            zostalo -= faktycznieZabrane;
            zabrano += faktycznieZabrane;
            if (iloscSztych >= it.getAmount()) zawartosc[i] = null;
            else it.setAmount(it.getAmount() - iloscSztych);
        }

        // Potem zwykłe gluty
        for (int i = 0; i < zawartosc.length && zostalo > 0; i++) {
            ItemStack it = zawartosc[i];
            if (!jestGlut(it)) continue;
            int bierz = Math.min(zostalo, it.getAmount());
            zostalo -= bierz;
            zabrano += bierz;
            if (bierz >= it.getAmount()) zawartosc[i] = null;
            else it.setAmount(it.getAmount() - bierz);
        }

        p.getInventory().setContents(zawartosc);
        return zabrano;
    }

    /** Przywraca tag PDC (np. po reloadzie pluginu stary klucz przestaje pasowac). */
    public ItemStack oznaczZwyklyGlut(ItemStack it) {
        if (it == null || it.getType().isAir()) return it;
        ItemStack out = it.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta == null) return out;
        meta.getPersistentDataContainer().set(kluczGlut, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().remove(kluczCzerwonyGlut);
        out.setItemMeta(meta);
        return out;
    }

    private ItemStack wzorZwyklyGlut() {
        if (glutSzablon != null) {
            ItemStack w = glutSzablon.clone();
            w.setAmount(1);
            return oznaczZwyklyGlut(w);
        }
        return stworzGlut(1);
    }

    private ItemStack wzorCzerwonyGlut() {
        return stworzCzerwonyGlut(1);
    }

    private boolean wygladaJakZwyklyGlut(ItemStack it) {
        return tenSamWygladGluta(it, wzorZwyklyGlut());
    }

    private boolean wygladaJakCzerwonyGlut(ItemStack it) {
        return tenSamWygladGluta(it, wzorCzerwonyGlut());
    }

    /** Porownanie wygladu bez PDC (isSimilar bywa falszywe gdy brakuje tagu). */
    private static boolean tenSamWygladGluta(ItemStack a, ItemStack wzor) {
        if (a == null || wzor == null || a.getType() != wzor.getType()) return false;
        ItemMeta ma = a.getItemMeta();
        ItemMeta mw = wzor.getItemMeta();
        if (ma == null && mw == null) return true;
        if (ma == null || mw == null) return false;
        if (!java.util.Objects.equals(ma.getDisplayName(), mw.getDisplayName())) return false;
        if (!java.util.Objects.equals(ma.getLore(), mw.getLore())) return false;
        if (ma.hasCustomModelData() != mw.hasCustomModelData()) return false;
        return !ma.hasCustomModelData() || ma.getCustomModelData() == mw.getCustomModelData();
    }

    /** Wydaje gluty graczowi (dzieli na stacki, nadmiar spada pod nogi). */
    public void dajGluty(Player p, int ilosc) {
        int zostalo = ilosc;
        while (zostalo > 0) {
            ItemStack stack = stworzGlut(zostalo);
            zostalo -= stack.getAmount();
            dajItem(p, stack);
        }
    }

    public static void dajItem(Player p, ItemStack item) {
        for (ItemStack reszta : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), reszta);
        }
    }

    /** Daje graczowi czerwony gluta (1 sztuka = 10 zwykłych). */
    public void dajCzerwonyGluty(Player p, int ilosc) {
        int zostalo = ilosc;
        while (zostalo > 0) {
            ItemStack stack = stworzCzerwonyGlut(zostalo);
            zostalo -= stack.getAmount();
            dajItem(p, stack);
        }
    }

    // ───────────────────────── saldo (wplacone gluty) ─────────────────────────

    private void wczytajGraczy() {
        gracze.clear();
        for (Map.Entry<UUID, NekromantaDatabase.DaneGracza> e : baza.wczytajGraczy().entrySet()) {
            Dane d = new Dane();
            d.saldo = e.getValue().saldo;
            d.poziom = e.getValue().poziom;
            gracze.put(e.getKey(), d);
        }
    }

    private void zapiszGracza(UUID id) {
        Dane d = gracze.get(id);
        if (d == null) {
            baza.zapiszGracza(id, 0, 0);
            return;
        }
        baza.zapiszGracza(id, d.saldo, d.poziom);
    }

    /** Zapis salda/poziomow graczy (flush calego cache, np. reload / wylaczenie). */
    public void zapiszGraczy() {
        Map<UUID, NekromantaDatabase.DaneGracza> kopia = new HashMap<>();
        for (Map.Entry<UUID, Dane> e : gracze.entrySet()) {
            NekromantaDatabase.DaneGracza d = new NekromantaDatabase.DaneGracza();
            d.saldo = e.getValue().saldo;
            d.poziom = e.getValue().poziom;
            kopia.put(e.getKey(), d);
        }
        baza.zapiszGraczy(kopia);
    }

    public void zamknij() {
        zapiszGraczy();
        zapisz();
        baza.close();
    }

    private Dane dane(UUID id) {
        return gracze.computeIfAbsent(id, k -> new Dane());
    }

    public long saldo(UUID id) {
        Dane d = gracze.get(id);
        return d == null ? 0L : d.saldo;
    }

    /** Dodaje (lub odejmuje - ujemna wartosc) gluty do salda. Saldo nie spada ponizej 0. */
    public void dodajDoSalda(UUID id, long ile) {
        Dane d = dane(id);
        d.saldo = Math.max(0L, d.saldo + ile);
        zapiszGracza(id);
    }

    public void ustawSaldo(UUID id, long wartosc) {
        dane(id).saldo = Math.max(0L, wartosc);
        zapiszGracza(id);
    }

    /**
     * Wplaca gluty z ekwipunku do salda.
     * @param limit maksymalna ilosc glutow do wplaty (Integer.MAX_VALUE = wszystkie)
     * @return ile glutow faktycznie wplacono
     */
    public int wplac(Player p, int limit) {
        int wziete = zabierzGluty(p, Math.max(0, limit));
        if (wziete > 0) {
            Dane d = dane(p.getUniqueId());
            d.saldo += wziete;
            zapiszGracza(p.getUniqueId());
            plugin.getLogger().info("[Nekromanta] " + p.getName() + " wplacil " + wziete
                    + " glutow (saldo=" + d.saldo + ")");
        }
        return wziete;
    }

    // ───────────────────────── ulepszenia ─────────────────────────

    /** Koszty kolejnych poziomow (indeks 0 = poziom 1). */
    public List<Integer> kosztyUlepszen() {
        List<Integer> lista = cfg.getIntegerList("ulepszenia.koszty");
        if (lista.isEmpty()) return DOMYSLNE_KOSZTY;
        // maksymalnie 9 poziomow - tyle miesci sie w jednym rzedzie menu
        return lista.size() > 9 ? lista.subList(0, 9) : lista;
    }

    public int maxPoziom() {
        return kosztyUlepszen().size();
    }

    /** O ile punktow procentowych jeden poziom zwieksza szanse dropu gluta z welny. */
    public double bonusNaPoziom() {
        return Math.max(0.0, cfg.getDouble("ulepszenia.bonus-na-poziom", 1.0));
    }

    public int poziom(UUID id) {
        Dane d = gracze.get(id);
        return d == null ? 0 : Math.min(d.poziom, maxPoziom());
    }

    public void ustawPoziom(UUID id, int poziom) {
        dane(id).poziom = Math.max(0, Math.min(poziom, maxPoziom()));
        zapiszGracza(id);
    }

    /** Laczny bonus gracza (w punktach procentowych) do szansy dropu gluta z welny. */
    public double bonusProcent(UUID id) {
        return poziom(id) * bonusNaPoziom();
    }

    /** Kupuje kolejny poziom ulepszenia za saldo i wysyla graczowi odpowiednia wiadomosc. */
    public WynikUlepszenia ulepsz(Player p) {
        Dane d = dane(p.getUniqueId());
        List<Integer> koszty = kosztyUlepszen();
        int obecny = Math.min(d.poziom, koszty.size());
        if (obecny >= koszty.size()) {
            wiadomoscRaw(p, "ulepszenie-max");
            dzwiek(p, "dzwiek.brak");
            return WynikUlepszenia.MAX;
        }
        int koszt = Math.max(1, koszty.get(obecny));
        if (d.saldo < koszt) {
            wiadomoscRaw(p, "ulepszenie-brak-salda", "%koszt%", String.valueOf(koszt), "%saldo%", String.valueOf(d.saldo));
            dzwiek(p, "dzwiek.brak");
            return WynikUlepszenia.BRAK_SALDA;
        }
        d.saldo -= koszt;
        d.poziom = obecny + 1;
        zapiszGracza(p.getUniqueId());
        wiadomoscRaw(p, "ulepszenie-kupione", "%poziom%", String.valueOf(d.poziom),
                "%bonus%", formatSzansy(d.poziom * bonusNaPoziom()));
        dzwiek(p, "dzwiek.ulepszenie");
        return WynikUlepszenia.OK;
    }

    /** Podmienia pary {@code "%klucz%", "wartosc"} w tekscie i koloruje go. */
    public String zamien(String tekst, String... pary) {
        String s = tekst == null ? "" : tekst;
        for (int i = 0; i + 1 < pary.length; i += 2) s = s.replace(pary[i], pary[i + 1]);
        return Msg.color(s);
    }

    /** Wiadomosc z wiadomosci.* (z prefixem) z podmiana par {@code "%klucz%", "wartosc"}. */
    public void wiadomoscRaw(Player p, String klucz, String... pary) {
        String tresc = cfg.getString("wiadomosci." + klucz, "");
        if (tresc.isBlank()) return;
        p.sendMessage(zamien(cfg.getString("wiadomosci.prefix", "") + tresc, pary));
    }

    // ───────────────────────── wymiana ─────────────────────────

    /** Losowanie wazone - szansy sa przeliczane proporcjonalnie do sumy w pakiecie. */
    private Wpis losuj(Tier t) {
        double suma = sumaSzans(t);
        if (suma <= 0) return null;
        double r = ThreadLocalRandom.current().nextDouble(suma);
        double acc = 0;
        Wpis ostatni = null;
        for (Wpis w : getWpisy(t)) {
            if (w.getSzansa() <= 0) continue;
            ostatni = w;
            acc += w.getSzansa();
            if (r < acc) return w;
        }
        return ostatni;
    }

    public void wymien(Player p, Tier t) {
        long teraz = System.currentTimeMillis();
        Long ostatnia = ostatniaWymiana.get(p.getUniqueId());
        if (ostatnia != null && teraz - ostatnia < COOLDOWN_MS) {
            wiadomosc(p, "za-szybko", t, 0, null, 0);
            return;
        }
        ostatniaWymiana.put(p.getUniqueId(), teraz);

        if (sumaSzans(t) <= 0) {
            wiadomosc(p, "brak-nagrod", t, 0, null, 0);
            dzwiek(p, "dzwiek.brak");
            return;
        }
        long lacznie = lacznieGlutow(p);
        if (lacznie < t.koszt) {
            wiadomosc(p, "brak-glutow", t, ileGlutow(p), null, 0);
            dzwiek(p, "dzwiek.brak");
            return;
        }

        Wpis wygrana = losuj(t);
        if (wygrana == null) return;
        double szansa = szansaEfektywna(t, wygrana);
        ItemStack nagroda = wygrana.getItem().clone();

        if (!pobierzKosztWymiany(p, t.koszt)) {
            wiadomosc(p, "brak-glutow", t, ileGlutow(p), null, 0);
            dzwiek(p, "dzwiek.brak");
            return;
        }
        dajItem(p, nagroda);

        int maszPo = (int) Math.min(Integer.MAX_VALUE, lacznieGlutow(p));
        wiadomosc(p, "nagroda", t, maszPo, nagroda, szansa);
        zagrajSukces(p);
        if (cfg.getBoolean("title.wlaczony", true)) {
            p.sendTitle(podstaw(cfg.getString("title.tytul", ""), t, maszPo, nagroda, szansa, p),
                    podstaw(cfg.getString("title.podtytul", ""), t, maszPo, nagroda, szansa, p), 5, 50, 15);
        }

        double prog = cfg.getDouble("ogloszenie-szansa-do", 5.0);
        if (prog > 0 && szansa <= prog) {
            for (String linia : cfg.getStringList("ogloszenie")) {
                Bukkit.broadcastMessage(podstaw(linia, t, maszPo, nagroda, szansa, p));
            }
        }
    }

    // ───────────────────────── teksty / dzwieki ─────────────────────────

    public String podstaw(String tekst, Tier t, int masz, ItemStack item, double szansa, Player p) {
        String s = tekst == null ? "" : tekst;
        long saldo = p == null ? 0L : saldo(p.getUniqueId());
        long lacznie = saldo + masz;
        s = s.replace("%kolor%", t.kolorKod())
                .replace("%koszt%", String.valueOf(t.koszt))
                .replace("%masz%", String.valueOf(masz))
                .replace("%saldo%", String.valueOf(saldo))
                .replace("%lacznie%", String.valueOf(lacznie))
                .replace("%gracz%", p == null ? "" : p.getName())
                .replace("%item%", item == null ? "" : nazwaPrzedmiotu(item))
                .replace("%szansa%", formatSzansy(szansa));
        return Msg.color(s);
    }

    private void wiadomosc(Player p, String klucz, Tier t, int masz, ItemStack item, double szansa) {
        String prefix = cfg.getString("wiadomosci.prefix", "");
        String tresc = cfg.getString("wiadomosci." + klucz, "");
        if (tresc.isBlank()) return;
        p.sendMessage(podstaw(prefix + tresc, t, masz, item, szansa, p));
    }

    public void dzwiek(Player p, String sciezka) {
        String nazwa = cfg.getString(sciezka, "");
        if (nazwa == null || nazwa.isBlank()) return;
        p.playSound(p.getLocation(), nazwa, 1.0f, 1.0f);
    }

    private void zagrajSukces(Player p) {
        String nazwa = cfg.getString("dzwiek.sukces", "");
        if (nazwa == null || nazwa.isBlank()) return;
        p.playSound(p.getLocation(), nazwa,
                (float) cfg.getDouble("dzwiek.sukces-glosnosc", 0.6),
                (float) cfg.getDouble("dzwiek.sukces-ton", 1.4));
    }

    public static String nazwaPrzedmiotu(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        StringBuilder sb = new StringBuilder();
        for (String c : item.getType().name().toLowerCase(Locale.ROOT).split("_")) {
            if (c.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(c.charAt(0))).append(c.substring(1));
        }
        return sb.toString();
    }

    public static String formatSzansy(double szansa) {
        if (szansa == Math.rint(szansa)) return String.valueOf((long) szansa);
        return String.format(Locale.US, "%.2f", szansa).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static double clamp(double v) {
        return Math.max(SZANSA_MIN, Math.min(SZANSA_MAX, v));
    }
}
