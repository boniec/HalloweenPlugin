package boniec.azbroja;

import boniec.szaman.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AzbrojaManager {

    public enum TrybKoloru { OD, DO, BARWNIK }

    public static final class Sesja {
        public String tekst;
        public Integer numer;
        public String hexOd;
        public String hexDo;
        public String hexBarwnik;
        public TrybKoloru tryb = TrybKoloru.OD;
        public int stronaPalety;
    }

    public static final class KolorWelny {
        public Material material;
        public String hex;
    }

    private final JavaPlugin plugin;
    private final File plik;
    private FileConfiguration cfg;
    private final Map<UUID, Sesja> sesje = new HashMap<>();
    private final List<KolorWelny> paleta = new ArrayList<>();

    private String hexOdDomyslny;
    private String hexDoDomyslny;
    private String hexBarwnikDomyslny;
    private String formatNumeru;
    private int koloryNaStrone;

    public AzbrojaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "azbroja.yml");
        if (!plik.exists()) plugin.saveResource("azbroja.yml", false);
        wczytaj();
    }

    public void wczytaj() {
        cfg = YamlConfiguration.loadConfiguration(plik);
        InputStream def = plugin.getResource("azbroja.yml");
        if (def != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
        hexOdDomyslny = cfg.getString("domyslny-hex-od", "#555555");
        hexDoDomyslny = cfg.getString("domyslny-hex-do", "#AAAAAA");
        hexBarwnikDomyslny = cfg.getString("domyslny-hex-barwnik", "#8B6914");
        formatNumeru = cfg.getString("format-numeru", "&6★&6&l%numer%");
        List<Integer> slotyPalety = cfg.getIntegerList("gui.sloty-palety");
        koloryNaStrone = slotyPalety.isEmpty() ? 28 : Math.max(1, slotyPalety.size());
        paleta.clear();
        for (Map<?, ?> row : cfg.getMapList("paleta")) {
            Object mat = row.get("material");
            Object hex = row.get("hex");
            if (!(mat instanceof String) || !(hex instanceof String)) continue;
            Material material = Material.matchMaterial((String) mat);
            if (material == null) continue;
            KolorWelny k = new KolorWelny();
            k.material = material;
            k.hex = (String) hex;
            paleta.add(k);
        }
    }

    public Sesja rozpocznij(Player gracz, AzbrojaUtil.ParsedNazwa parsed) {
        Sesja s = new Sesja();
        s.tekst = parsed.tekst();
        s.numer = parsed.numer();
        s.hexOd = hexOdDomyslny;
        s.hexDo = hexDoDomyslny;
        s.hexBarwnik = hexBarwnikDomyslny;
        s.stronaPalety = 0;
        sesje.put(gracz.getUniqueId(), s);
        return s;
    }

    public Sesja getSesja(UUID uuid) {
        return sesje.get(uuid);
    }

    public void usunSesje(UUID uuid) {
        sesje.remove(uuid);
    }

    public String zbudujRaw(Sesja s) {
        return AzbrojaUtil.zbudujNazwe(s.tekst, s.hexOd, s.hexDo, s.numer, formatNumeru);
    }

    public int getKoloryNaStrone() {
        return koloryNaStrone;
    }

    public int getStronPalety() {
        if (paleta.isEmpty()) return 1;
        return (paleta.size() + koloryNaStrone - 1) / koloryNaStrone;
    }

    public String msg(String klucz) {
        return Msg.color(cfg.getString("wiadomosci." + klucz, "&c" + klucz));
    }

    public FileConfiguration getCfg() {
        return cfg;
    }

    public List<KolorWelny> getPaleta() {
        return paleta;
    }
}
