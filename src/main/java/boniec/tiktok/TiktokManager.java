package boniec.tiktok;

import boniec.dev.inventory.Main;
import boniec.klany.util.Msg;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TiktokManager {

    public enum Status { SZKIC, OCZEKUJE, ZAAKCEPTOWANE, ODRZUCONE }

    public static final class Film {
        public final String id;
        public final UUID wlasciciel;
        public final String nickMinecraft;
        public String url;
        public String videoId;
        public Status status;
        public long dodano;
        public long wyslano;
        public String admin;
        public String tytul = "";
        public String opis = "";
        public long wyswietlenia;

        Film(String id, UUID wlasciciel, String nickMinecraft) {
            this.id = id;
            this.wlasciciel = wlasciciel;
            this.nickMinecraft = nickMinecraft;
        }
    }

    public static final class Konto {
        public final UUID uuid;
        public String minecraft;
        public String tiktok;
        public String opisProfilu = "";
        public final Map<String, Film> filmy = new LinkedHashMap<>();
        long ostatniePobranie;

        Konto(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9._]{2,24}$");
    private static final Pattern VIDEO = Pattern.compile("/video/(\\d+)");
    private static final Pattern EMBED_FILM = Pattern.compile(
            "\"id\":\"(\\d{15,})\",\"desc\":\"((?:\\\\.|[^\"\\\\])*)\".*?\"playCount\":(\\d+)",
            Pattern.DOTALL);
    private static final Pattern EMBED_BIO = Pattern.compile(
            "\"signature\":\"((?:\\\\.|[^\"\\\\])*)\"");

    private final Main plugin;
    private final File cfgFile;
    private final File daneFile;
    private FileConfiguration cfg;
    private final Map<UUID, Konto> konta = new LinkedHashMap<>();
    private final Map<String, UUID> nickNaUuid = new LinkedHashMap<>();

    public TiktokManager(Main plugin) {
        this.plugin = plugin;
        this.cfgFile = new File(plugin.getDataFolder(), "tiktok.yml");
        this.daneFile = new File(plugin.getDataFolder(), "tiktok-dane.yml");
        wczytaj();
    }

    public FileConfiguration getCfg() {
        return cfg;
    }

    public String txt(String path, String domyslna) {
        String v = cfg.getString("wiadomosci." + path);
        if (v == null || v.isEmpty()) v = cfg.getString(path);
        return (v == null || v.isEmpty()) ? domyslna : v;
    }

    public String kolor(String tekst) {
        return Msg.color(cfg.getString("wiadomosci.prefix", "&8[&f临&8] ") + tekst);
    }

    public void wczytaj() {
        if (!cfgFile.exists()) {
            plugin.saveResource("tiktok.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(cfgFile);
        uzupelnijZJara();
        wczytajDane();
    }

    private void uzupelnijZJara() {
        InputStream in = plugin.getResource("tiktok.yml");
        if (in == null) return;
        FileConfiguration jar = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        boolean zmieniono = false;
        int wersjaJar = jar.getInt("gui.wersja", 1);
        boolean nadpiszGui = cfg.getInt("gui.wersja", 0) < wersjaJar;
        for (String klucz : jar.getKeys(true)) {
            if (jar.isConfigurationSection(klucz)) continue;
            boolean brak = !cfg.contains(klucz);
            boolean lore = nadpiszGui && (klucz.startsWith("gui.") || klucz.startsWith("film.")
                    || klucz.startsWith("nagrody.") || klucz.startsWith("wymagania.")
                    || klucz.equals("wiadomosci.pusta-lista")
                    || klucz.equals("wiadomosci.brak-nicku-w-opisie")
                    || klucz.equals("wiadomosci.brak-hashtagu"));
            if (brak || lore) {
                cfg.set(klucz, jar.get(klucz));
                zmieniono = true;
            }
        }
        if (nadpiszGui) {
            cfg.set("gui.wersja", wersjaJar);
            zmieniono = true;
        }
        if (zmieniono) {
            try {
                cfg.save(cfgFile);
            } catch (IOException e) {
                plugin.getLogger().warning("[TikTok] Nie zapisano tiktok.yml: " + e.getMessage());
            }
        }
    }

    private void wczytajDane() {
        konta.clear();
        nickNaUuid.clear();
        if (!daneFile.exists()) return;
        FileConfiguration dane = YamlConfiguration.loadConfiguration(daneFile);
        ConfigurationSection gracze = dane.getConfigurationSection("gracze");
        if (gracze == null) return;
        for (String uuidS : gracze.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidS);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = gracze.getConfigurationSection(uuidS);
            if (s == null) continue;
            Konto k = new Konto(uuid);
            k.minecraft = s.getString("minecraft", "");
            k.tiktok = s.getString("tiktok", "");
            k.opisProfilu = s.getString("opis-profilu", "");
            ConfigurationSection filmy = s.getConfigurationSection("filmy");
            if (filmy != null) {
                for (String id : filmy.getKeys(false)) {
                    ConfigurationSection f = filmy.getConfigurationSection(id);
                    if (f == null) continue;
                    Film film = new Film(id, uuid, k.minecraft);
                    film.url = f.getString("url", "");
                    film.videoId = f.getString("video-id", id);
                    try {
                        film.status = Status.valueOf(f.getString("status", "SZKIC"));
                    } catch (IllegalArgumentException e) {
                        film.status = Status.SZKIC;
                    }
                    film.dodano = f.getLong("dodano", 0L);
                    film.wyslano = f.getLong("wyslano", 0L);
                    film.admin = f.getString("admin", "");
                    film.tytul = f.getString("tytul", "");
                    film.opis = f.getString("opis", film.tytul);
                    film.wyswietlenia = f.getLong("wyswietlenia", 0L);
                    k.filmy.put(id, film);
                }
            }
            konta.put(uuid, k);
            if (k.tiktok != null && !k.tiktok.isEmpty()) {
                nickNaUuid.put(k.tiktok.toLowerCase(Locale.ROOT), uuid);
            }
        }
    }

    public void zapiszPrzyWylaczaniu() {
        zapiszDane();
    }

    private void zapiszDane() {
        YamlConfiguration dane = new YamlConfiguration();
        for (Konto k : konta.values()) {
            String p = "gracze." + k.uuid;
            dane.set(p + ".minecraft", k.minecraft);
            dane.set(p + ".tiktok", k.tiktok);
            dane.set(p + ".opis-profilu", k.opisProfilu);
            for (Film f : k.filmy.values()) {
                String fp = p + ".filmy." + f.id;
                dane.set(fp + ".url", f.url);
                dane.set(fp + ".video-id", f.videoId);
                dane.set(fp + ".status", f.status.name());
                dane.set(fp + ".dodano", f.dodano);
                dane.set(fp + ".wyslano", f.wyslano);
                dane.set(fp + ".admin", f.admin);
                dane.set(fp + ".tytul", f.tytul);
                dane.set(fp + ".opis", f.opis);
                dane.set(fp + ".wyswietlenia", f.wyswietlenia);
            }
        }
        try {
            dane.save(daneFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[TikTok] Nie zapisano tiktok-dane.yml: " + e.getMessage());
        }
    }

    public Main plugin() {
        return plugin;
    }

    public Konto getKonto(UUID uuid) {
        return konta.get(uuid);
    }

    public String polacz(Player player, String nick) {
        if (nick == null) {
            return txt("zly-nick", "&f与 &cNick TikTok: litery, cyfry, kropka i _ (2-24 znaki).");
        }
        if (nick.startsWith("@")) nick = nick.substring(1);
        nick = nick.trim();
        if (nick.isEmpty() || !NICK.matcher(nick).matches()) {
            return txt("zly-nick", "&f与 &cNick TikTok: litery, cyfry, kropka i _ (2-24 znaki).");
        }
        Konto obecne = konta.get(player.getUniqueId());
        if (obecne != null && obecne.tiktok != null && !obecne.tiktok.isEmpty()) {
            return txt("juz-polaczony", "&f亢 &eMasz już konto: &#FF0050@%nick% &7— /tiktok odlacz")
                    .replace("%nick%", obecne.tiktok);
        }
        UUID zajety = nickNaUuid.get(nick.toLowerCase(Locale.ROOT));
        if (zajety != null && !zajety.equals(player.getUniqueId())) {
            return txt("nick-zajety", "&f与 &cTen nick TikTok jest już połączony z innym kontem.");
        }
        Konto k = konta.computeIfAbsent(player.getUniqueId(), Konto::new);
        k.minecraft = player.getName();
        k.tiktok = nick;
        nickNaUuid.put(nick.toLowerCase(Locale.ROOT), player.getUniqueId());
        zapiszDane();
        return txt("polaczono", "&f両 &aPołączono TikToka: &#FF0050@%nick%").replace("%nick%", nick);
    }

    public String odlacz(Player player) {
        Konto k = konta.get(player.getUniqueId());
        if (k == null || k.tiktok == null || k.tiktok.isEmpty()) {
            return txt("nie-polaczony", "&f与 &cNajpierw: &e/tiktok polacz <nick>");
        }
        nickNaUuid.remove(k.tiktok.toLowerCase(Locale.ROOT));
        k.tiktok = "";
        zapiszDane();
        return txt("odlaczono", "&f両 &aOdłączono konto TikTok.");
    }

    public String dodajFilm(Player player, String surowyUrl) {
        Konto k = konta.get(player.getUniqueId());
        if (k == null || k.tiktok == null || k.tiktok.isEmpty()) {
            return txt("nie-polaczony", "&f与 &cNajpierw: &e/tiktok polacz <nick>");
        }
        String url = normalizujUrl(surowyUrl);
        if (url == null) {
            return txt("zly-link", "&f与 &cTo nie jest link do TikToka.");
        }
        String videoId = wyciagnijId(url);
        if (filmIstnieje(videoId, url)) {
            return txt("juz-jest", "&f与 &cTen film jest już na liście (Twój lub kogoś innego).");
        }
        Film film = new Film(videoId, player.getUniqueId(), player.getName());
        film.url = url;
        film.videoId = videoId;
        film.status = Status.SZKIC;
        film.dodano = System.currentTimeMillis();
        k.minecraft = player.getName();
        k.filmy.put(videoId, film);
        zapiszDane();
        return txt("dodano", "&f両 &aDodano film. Otwórz &e/tiktok gui &ai wyślij do weryfikacji.");
    }

    public String wyslijDoWeryfikacji(Player player, String filmId) {
        Konto k = konta.get(player.getUniqueId());
        if (k == null) return txt("nie-polaczony", "&f与 &cNajpierw: &e/tiktok polacz <nick>");
        Film f = k.filmy.get(filmId);
        if (f == null) return txt("zly-link", "&f与 &cNie znaleziono filmu.");
        if (f.status == Status.OCZEKUJE) {
            return txt("juz-oczekuje", "&f亢 &eTen film już czeka na admina.");
        }
        if (f.status == Status.ZAAKCEPTOWANE) {
            return txt("juz-ok", "&f両 &aTen film był już zaakceptowany.");
        }
        k.minecraft = player.getName();
        String blad = sprawdzWymagania(f, player.getName());
        if (blad != null) return blad;
        f.status = Status.OCZEKUJE;
        f.wyslano = System.currentTimeMillis();
        zapiszDane();
        String adminMsg = kolor(txt("admin-nowy", "&f亢 &e%gracz% &7wysłał film TikTok &8» &e/atiktok")
                .replace("%gracz%", player.getName()));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("tiktok.admin")) {
                online.sendMessage(adminMsg);
            }
        }
        return txt("wyslano", "&f両 &aWysłano do weryfikacji. Czekaj na admina.");
    }

    public String zaakceptuj(Player admin, String filmId) {
        Film f = znajdzFilm(filmId);
        if (f == null || f.status != Status.OCZEKUJE) {
            return txt("pusta-weryfikacja", "&f両 &aBrak oczekujących filmów.");
        }
        String nickGry = nickGry(f);
        String blad = sprawdzWymagania(f, nickGry);
        if (blad != null) return blad;
        double vpln = wyliczVpln(f, false);
        f.status = Status.ZAAKCEPTOWANE;
        f.admin = admin.getName();
        String nagroda = dajNagrode(f, vpln);
        zapiszDane();
        Player wlasciciel = Bukkit.getPlayer(f.wlasciciel);
        if (wlasciciel != null) {
            wlasciciel.sendMessage(kolor(txt("nagroda-gracz", "&f両 &aAdmin zatwierdził Twój TikTok! Nagroda: &f%nagroda%")
                    .replace("%nagroda%", nagroda)));
        }
        return txt("zaakceptowano", "&f両 &aFilm zaakceptowany. Nagroda: &f%nagroda%").replace("%nagroda%", nagroda);
    }

    public String odrzuc(Player admin, String filmId) {
        Film f = znajdzFilm(filmId);
        if (f == null || f.status != Status.OCZEKUJE) {
            return txt("pusta-weryfikacja", "&f両 &aBrak oczekujących filmów.");
        }
        f.status = Status.ODRZUCONE;
        f.admin = admin.getName();
        zapiszDane();
        Player wlasciciel = Bukkit.getPlayer(f.wlasciciel);
        if (wlasciciel != null) {
            wlasciciel.sendMessage(kolor(txt("odrzucenie-gracz", "&f与 &cAdmin odrzucił Twój film z TikToka.")));
        }
        return txt("odrzucono", "&f与 &cFilm odrzucony.");
    }

    public List<Film> filmyGracza(UUID uuid) {
        Konto k = konta.get(uuid);
        if (k == null) return List.of();
        List<Film> out = new ArrayList<>();
        for (Film f : k.filmy.values()) {
            if (f.status != Status.SZKIC || maWymaganyHashtag(tekstOpisu(f))) out.add(f);
        }
        return out;
    }

    public List<Film> oczekujace() {
        List<Film> out = new ArrayList<>();
        for (Konto k : konta.values()) {
            for (Film f : k.filmy.values()) {
                if (f.status == Status.OCZEKUJE) out.add(f);
            }
        }
        return out;
    }

    public Film znajdzFilm(String id) {
        for (Konto k : konta.values()) {
            Film f = k.filmy.get(id);
            if (f != null) return f;
        }
        return null;
    }

    public int odswiezFilmy(UUID uuid, boolean wymus) {
        Konto k = konta.get(uuid);
        if (k == null || k.tiktok == null || k.tiktok.isEmpty()) return -1;
        long cacheMs = Math.max(10, cfg.getInt("api.cache-sekund", 90)) * 1000L;
        if (!wymus && k.ostatniePobranie > 0 && System.currentTimeMillis() - k.ostatniePobranie < cacheMs) {
            return 0;
        }
        PobranieProfilu zdalne;
        try {
            zdalne = pobierzZEmbed(k.tiktok);
        } catch (Exception e) {
            plugin.getLogger().warning("[TikTok] Pobieranie filmów @" + k.tiktok + ": " + e.getMessage());
            return -2;
        }
        k.ostatniePobranie = System.currentTimeMillis();
        if (zdalne.bio != null) {
            k.opisProfilu = zdalne.bio;
        }
        if (zdalne.filmy.isEmpty()) {
            zapiszDane();
            return 0;
        }

        Map<String, Film> scalone = new LinkedHashMap<>();
        int nowe = 0;
        for (FilmZSieci z : zdalne.filmy) {
            Film istniejacy = k.filmy.get(z.id);
            if (istniejacy != null) {
                istniejacy.tytul = z.tytul;
                istniejacy.opis = z.opis;
                istniejacy.wyswietlenia = z.wyswietlenia;
                if (istniejacy.url == null || istniejacy.url.isEmpty()) {
                    istniejacy.url = z.url;
                }
                scalone.put(z.id, istniejacy);
                continue;
            }
            if (filmKogosInnego(z.id, uuid)) continue;
            Film film = new Film(z.id, uuid, k.minecraft);
            film.url = z.url;
            film.videoId = z.id;
            film.status = Status.SZKIC;
            film.dodano = System.currentTimeMillis();
            film.tytul = z.tytul;
            film.opis = z.opis;
            film.wyswietlenia = z.wyswietlenia;
            scalone.put(z.id, film);
            nowe++;
        }
        for (Film stary : k.filmy.values()) {
            scalone.putIfAbsent(stary.id, stary);
        }
        k.filmy.clear();
        k.filmy.putAll(scalone);
        zapiszDane();
        return nowe;
    }

    private boolean filmKogosInnego(String videoId, UUID wlasciciel) {
        for (Konto k : konta.values()) {
            if (k.uuid.equals(wlasciciel)) continue;
            if (k.filmy.containsKey(videoId)) return true;
        }
        return false;
    }

    private PobranieProfilu pobierzZEmbed(String nick) throws IOException {
        String html = pobierzHtml("https://www.tiktok.com/embed/@" + nick);
        PobranieProfilu wynik = new PobranieProfilu();
        wynik.bio = wyciagnijBio(html, nick);
        if (wynik.bio.isEmpty()) {
            try {
                String profil = pobierzHtml("https://www.tiktok.com/@" + nick);
                wynik.bio = wyciagnijBio(profil, nick);
            } catch (IOException ignored) {
            }
        }
        List<FilmZSieci> out = new ArrayList<>();
        Matcher m = EMBED_FILM.matcher(html);
        int limit = Math.max(1, cfg.getInt("api.max-filmow", 30));
        while (m.find() && out.size() < limit) {
            String id = m.group(1);
            boolean dup = false;
            for (FilmZSieci f : out) {
                if (f.id.equals(id)) {
                    dup = true;
                    break;
                }
            }
            if (dup) continue;
            String pelny = odkodujJson(m.group(2));
            if (!maWymaganyHashtag(pelny)) continue;
            FilmZSieci z = new FilmZSieci();
            z.id = id;
            z.opis = pelny;
            z.tytul = skroc(pelny, 48);
            z.wyswietlenia = Long.parseLong(m.group(3));
            z.url = "https://www.tiktok.com/@" + nick + "/video/" + id;
            out.add(z);
        }
        wynik.filmy = out;
        return wynik;
    }

    private String pobierzHtml(String adres) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(adres).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setRequestProperty("Accept-Encoding", "identity");
        int kod = conn.getResponseCode();
        InputStream in = kod >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) {
            conn.disconnect();
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        in.transferTo(buf);
        conn.disconnect();
        if (kod >= 400) return "";
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String wyciagnijBio(String html, String nick) {
        if (html == null || html.isEmpty()) return "";
        if (nick != null && !nick.isBlank()) {
            Pattern spec = Pattern.compile(
                    "\"uniqueId\"\\s*:\\s*\"" + Pattern.quote(nick) + "\".{0,1500}?\"signature\":\"((?:\\\\.|[^\"\\\\])*)\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = spec.matcher(html);
            if (m.find()) {
                String bio = odkodujJson(m.group(1));
                if (!bio.isBlank() && !bio.equalsIgnoreCase("null")) return bio;
            }
        }
        Matcher m = EMBED_BIO.matcher(html);
        while (m.find()) {
            String bio = odkodujJson(m.group(1));
            if (!bio.isBlank() && !bio.equalsIgnoreCase("null")) return bio;
        }
        return "";
    }

    private static String odkodujJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n', 'r', 't' -> out.append(' ');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            try {
                                out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                out.append('u');
                            }
                        }
                    }
                    default -> out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString().replace('\n', ' ').trim();
    }

    private static String skroc(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    static String formatujWyswietlenia(long n) {
        if (n >= 1_000_000L) return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000L) return String.format(Locale.US, "%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }

    private static final class PobranieProfilu {
        String bio = "";
        List<FilmZSieci> filmy = List.of();
    }

    private static final class FilmZSieci {
        String id;
        String url;
        String tytul;
        String opis;
        long wyswietlenia;
    }

    private boolean filmIstnieje(String videoId, String url) {
        for (Konto k : konta.values()) {
            for (Film f : k.filmy.values()) {
                if (videoId.equals(f.videoId) || url.equalsIgnoreCase(f.url)) return true;
            }
        }
        return false;
    }

    private String dajNagrode(Film film, double vpln) {
        double kasa = cfg.getDouble("nagrody.pieniadze", 0);
        OfflinePlayer off = Bukkit.getOfflinePlayer(film.wlasciciel);
        if (vpln > 0 && plugin.getPortfelManager() != null) {
            plugin.getPortfelManager().dodaj(film.wlasciciel, vpln, "TIKTOK", "Weryfikacja filmu TikTok");
        }
        if (kasa > 0) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                rsp.getProvider().depositPlayer(off, kasa);
            }
        }
        String nick = off.getName() != null ? off.getName() : film.nickMinecraft;
        for (String cmd : cfg.getStringList("nagrody.komendy")) {
            if (cmd == null || cmd.isBlank()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%gracz%", nick));
        }
        String opis = txt("nagroda-opis", "&e%vpln% vPLN").replace("%vpln%", formatujVpln(vpln));
        if (kasa > 0) opis += " &8+ &e" + (long) kasa + "$";
        return Msg.color(opis);
    }

    private String sprawdzWymagania(Film f, String nickGry) {
        long min = Math.max(1, cfg.getLong("nagrody.min-wyswietlen", cfg.getLong("nagrody.wyswietlen-na-vpln", 300)));
        if (f.wyswietlenia < min) {
            return txt("za-malo-wyswietlen", "&f与 &cZa mało wyświetleń. Minimum: &e%min%&c.")
                    .replace("%min%", String.valueOf(min))
                    .replace("%wyswietlenia%", String.valueOf(f.wyswietlenia));
        }
        if (!maWymaganyHashtag(tekstOpisu(f))) {
            return txt("brak-hashtagu", "&f与 &cFilm musi mieć w opisie: &e%hashtagi%")
                    .replace("%hashtagi%", formatHashtagi());
        }
        if (!opisMaNick(f, nickGry)) {
            return txt("brak-nicku-w-opisie",
                    "&f与 &cW opisie profilu TikTok musi być: &e%wymagany-opis% &7(/tiktok odswiez)")
                    .replace("%wymagany-opis%", wymaganyOpis(nickGry))
                    .replace("%gracz%", nickGry == null ? "" : nickGry);
        }
        return null;
    }

    public boolean maWymaganyHashtag(String tekst) {
        if (tekst == null || tekst.isBlank()) return false;
        String lower = tekst.toLowerCase(Locale.ROOT);
        for (String tag : hashtagi()) {
            String szukaj = "#" + tag.toLowerCase(Locale.ROOT);
            int idx = 0;
            while ((idx = lower.indexOf(szukaj, idx)) >= 0) {
                int po = idx + szukaj.length();
                if (po >= lower.length() || !jestZnakHashtagu(lower.charAt(po))) return true;
                idx = po;
            }
        }
        return false;
    }

    private static boolean jestZnakHashtagu(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }

    public List<String> hashtagi() {
        List<String> raw = cfg.getStringList("wymagania.hashtagi");
        if (raw.isEmpty()) raw = List.of("szakaria", "szakariapl");
        List<String> out = new ArrayList<>();
        for (String t : raw) {
            if (t == null || t.isBlank()) continue;
            String tag = t.trim();
            if (tag.startsWith("#")) tag = tag.substring(1);
            if (!tag.isEmpty()) out.add(tag);
        }
        return out.isEmpty() ? List.of("szakaria", "szakariapl") : out;
    }

    public String formatHashtagi() {
        List<String> tagi = hashtagi();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tagi.size(); i++) {
            if (i > 0) sb.append(" / ");
            sb.append('#').append(tagi.get(i));
        }
        return sb.toString();
    }

    public boolean opisMaNick(Film f, String nickGry) {
        if (nickGry == null || nickGry.isBlank()) return false;
        Konto k = f == null ? null : konta.get(f.wlasciciel);
        String tekst = k != null && k.opisProfilu != null ? k.opisProfilu : "";
        if (tekst.isEmpty()) return false;
        String wzor = cfg.getString("wymagania.opis-wzor", "nick\\s*:\\s*%gracz%");
        String regex = wzor.replace("%gracz%", Pattern.quote(nickGry));
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(tekst).find();
    }

    public String tekstOpisu(Film f) {
        if (f.opis != null && !f.opis.isBlank()) return f.opis;
        return f.tytul == null ? "" : f.tytul;
    }

    public String nickGry(Film f) {
        Konto k = konta.get(f.wlasciciel);
        if (k != null && k.minecraft != null && !k.minecraft.isBlank()) return k.minecraft;
        if (f.nickMinecraft != null && !f.nickMinecraft.isBlank()) return f.nickMinecraft;
        OfflinePlayer off = Bukkit.getOfflinePlayer(f.wlasciciel);
        return off.getName() != null ? off.getName() : "";
    }

    public String wymaganyOpis(String nickGry) {
        return cfg.getString("wymagania.opis-przyklad", "nick: %gracz%")
                .replace("%gracz%", nickGry == null ? "" : nickGry);
    }

    public int ileOdebranych(UUID uuid) {
        Konto k = konta.get(uuid);
        if (k == null) return 0;
        int n = 0;
        for (Film f : k.filmy.values()) {
            if (f.status == Status.ZAAKCEPTOWANE) n++;
        }
        return n;
    }

    public boolean maBooster(UUID uuid) {
        return ileOdebranych(uuid) >= cfg.getInt("nagrody.booster.po-filmow", 5);
    }

    public double wyliczVpln(Film film, boolean szacunek) {
        long naVpln = Math.max(1, cfg.getLong("nagrody.wyswietlen-na-vpln", 300));
        double zaPakiet = cfg.getDouble("nagrody.vpln-za-pakiet", cfg.getDouble("nagrody.vpln", 1.0));
        long pakiety = film.wyswietlenia / naVpln;
        if (pakiety < 1) return 0;
        double suma = pakiety * zaPakiet;
        int odebrane = ileOdebranych(film.wlasciciel);
        if (szacunek && film.status == Status.ZAAKCEPTOWANE) {
            odebrane = Math.max(0, odebrane - 1);
        }
        int po = cfg.getInt("nagrody.booster.po-filmow", 5);
        if (odebrane >= po) {
            suma *= 1.0 + (cfg.getDouble("nagrody.booster.procent", 10.0) / 100.0);
        }
        return Math.round(suma * 100.0) / 100.0;
    }

    public String formatujVpln(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf((long) Math.round(v));
        return String.format(Locale.US, "%.2f", v);
    }

    public String opisNickStatus(Film f) {
        return opisMaNick(f, nickGry(f))
                ? txt("opis-ok", "&ajest")
                : txt("opis-brak", "&cbrak");
    }

    public String boosterStatus(UUID uuid) {
        int po = cfg.getInt("nagrody.booster.po-filmow", 5);
        int odebrane = ileOdebranych(uuid);
        if (odebrane >= po) {
            return txt("booster-on", "&a+%procent%%")
                    .replace("%procent%", formatujVpln(cfg.getDouble("nagrody.booster.procent", 10.0)))
                    .replace("%odebrane%", String.valueOf(odebrane))
                    .replace("%wymagane%", String.valueOf(po));
        }
        return txt("booster-off", "&7%odebrane%/%wymagane%")
                .replace("%odebrane%", String.valueOf(odebrane))
                .replace("%wymagane%", String.valueOf(po))
                .replace("%procent%", formatujVpln(cfg.getDouble("nagrody.booster.procent", 10.0)));
    }

    static String normalizujUrl(String raw) {
        if (raw == null) return null;
        String u = raw.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        String lower = u.toLowerCase(Locale.ROOT);
        if (!(lower.contains("tiktok.com"))) return null;
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            u = "https://" + u;
            lower = u.toLowerCase(Locale.ROOT);
        }
        if (!(lower.contains("tiktok.com/") || lower.contains("vm.tiktok.com") || lower.contains("vt.tiktok.com"))) {
            return null;
        }
        return u;
    }

    static String wyciagnijId(String url) {
        Matcher m = VIDEO.matcher(url);
        if (m.find()) return m.group(1);
        return Integer.toHexString(url.toLowerCase(Locale.ROOT).hashCode());
    }
}
