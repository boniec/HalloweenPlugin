package boniec.welna;

import boniec.code.core.WorldGuardHook;
import boniec.klany.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dropy z kolorowych welen (RED_WOOL / GREEN_WOOL / PURPLE_WOOL / WHITE_WOOL).
 * <p>
 * - lista dropow dla kazdej welny jest ustawiana komenda /awelna (przedmiot z reki + szansa w %),
 *   zapis w welnadrop.yml,
 * - ustawienia (dzwiek, wiadomosc, regiony) w welna.yml,
 * - kazdy wpis jest losowany OSOBNO (jak w dropie bossow): wpis z szansa 30% wypada w 30% zniszczen bloku,
 * - wpisy, ktore sa glutem nekromanty, dostaja bonus z ulepszen gracza (+punkty procentowe do szansy).
 */
public class WelnaDropManager implements Listener {

    public static final List<Material> OBSLUGIWANE = List.of(
            Material.RED_WOOL, Material.GREEN_WOOL, Material.PURPLE_WOOL, Material.WHITE_WOOL);

    public static final double SZANSA_MIN = 0.0;
    public static final double SZANSA_MAX = 100.0;

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

    private final JavaPlugin plugin;
    private final boolean wgWlaczony;
    private final File plikDropow;
    private final File plikConfig;
    private final Map<Material, List<Wpis>> dropy = new LinkedHashMap<>();
    private YamlConfiguration cfg;
    /** Ustawiane z Main po utworzeniu nekromanty (null = brak bonusow z ulepszen). */
    private boniec.nekromanta.NekromantaManager nekromanta;

    /** Wylosowany wpis razem z szansa, z jaka wypadl (po doliczeniu bonusu z ulepszen). */
    private record Traf(Wpis wpis, double szansa) {}

    public WelnaDropManager(JavaPlugin plugin, boolean wgWlaczony) {
        this.plugin = plugin;
        this.wgWlaczony = wgWlaczony;
        this.plikDropow = new File(plugin.getDataFolder(), "welnadrop.yml");
        this.plikConfig = new File(plugin.getDataFolder(), "welna.yml");
        for (Material m : OBSLUGIWANE) dropy.put(m, new ArrayList<>());
        przeladuj();
    }

    public void setNekromanta(boniec.nekromanta.NekromantaManager nekromanta) {
        this.nekromanta = nekromanta;
    }

    // ───────────────────────── konfiguracja / dane ─────────────────────────

    public void przeladuj() {
        plugin.getDataFolder().mkdirs();
        if (!plikConfig.exists()) plugin.saveResource("welna.yml", false);
        cfg = YamlConfiguration.loadConfiguration(plikConfig);
        wczytajDropy();
    }

    private void wczytajDropy() {
        for (List<Wpis> l : dropy.values()) l.clear();
        if (!plikDropow.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(plikDropow);
        ConfigurationSection root = data.getConfigurationSection("drop");
        if (root == null) return;

        for (Material m : OBSLUGIWANE) {
            wczytajSekcje(root.getConfigurationSection(m.name()), dropy.get(m));
        }
        List<Wpis> zielona = dropy.get(Material.GREEN_WOOL);
        if (zielona != null && zielona.isEmpty()) {
            wczytajSekcje(root.getConfigurationSection("LIME_WOOL"), zielona);
            if (!zielona.isEmpty()) zapisz();
        }
    }

    private static void wczytajSekcje(ConfigurationSection sekcja, List<Wpis> cel) {
        if (sekcja == null || cel == null) return;
        for (String klucz : sekcja.getKeys(false)) {
            ConfigurationSection wpis = sekcja.getConfigurationSection(klucz);
            if (wpis == null) continue;
            ItemStack item = wpis.getItemStack("item");
            if (item == null || item.getType().isAir()) continue;
            cel.add(new Wpis(item, wpis.getDouble("szansa", 10.0)));
        }
    }

    public void zapisz() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<Material, List<Wpis>> e : dropy.entrySet()) {
            List<Wpis> lista = e.getValue();
            for (int i = 0; i < lista.size(); i++) {
                data.set("drop." + e.getKey().name() + "." + i + ".item", lista.get(i).getItem());
                data.set("drop." + e.getKey().name() + "." + i + ".szansa", lista.get(i).getSzansa());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(plikDropow);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Welna] Nie udalo sie zapisac welnadrop.yml: " + ex.getMessage());
        }
    }

    public List<Wpis> getWpisy(Material welna) {
        return dropy.getOrDefault(welna, new ArrayList<>());
    }

    public void dodaj(Material welna, ItemStack item, double szansa) {
        List<Wpis> lista = dropy.get(welna);
        if (lista == null) return;
        lista.add(new Wpis(item.clone(), szansa));
        zapisz();
    }

    public boolean usun(Material welna, int numer) {
        List<Wpis> lista = dropy.get(welna);
        if (lista == null || numer < 1 || numer > lista.size()) return false;
        lista.remove(numer - 1);
        zapisz();
        return true;
    }

    public boolean ustawSzanse(Material welna, int numer, double szansa) {
        List<Wpis> lista = dropy.get(welna);
        if (lista == null || numer < 1 || numer > lista.size()) return false;
        lista.get(numer - 1).setSzansa(szansa);
        zapisz();
        return true;
    }

    /** Skrot: "red" / "green" / "lime" / "purple" / "white" / "red_wool" -> Material, albo null. */
    public static Material parsujWelne(String tekst) {
        if (tekst == null) return null;
        String t = tekst.toUpperCase(Locale.ROOT);
        if (t.equals("LIME") || t.equals("LIME_WOOL")) return Material.GREEN_WOOL;
        if (!t.endsWith("_WOOL")) t = t + "_WOOL";
        for (Material m : OBSLUGIWANE) {
            if (m.name().equals(t)) return m;
        }
        return null;
    }

    public static String nazwaPrzedmiotu(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        String[] czesci = item.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String c : czesci) {
            if (c.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(c.charAt(0))).append(c.substring(1));
        }
        return sb.toString();
    }

    private static double clamp(double v) {
        return Math.max(SZANSA_MIN, Math.min(SZANSA_MAX, v));
    }

    // ───────────────────────── niszczenie bloku ─────────────────────────

    public static boolean toWelna(Material m) {
        return m != null && m.name().endsWith("_WOOL");
    }

    public static boolean maNozyce(Player p) {
        if (p == null) return false;
        ItemStack wRece = p.getInventory().getItemInMainHand();
        return wRece != null && wRece.getType() == Material.SHEARS;
    }

    private void zablokujBezNozyc(BlockBreakEvent e) {
        if (!toWelna(e.getBlock().getType())) return;
        Player gracz = e.getPlayer();
        if (gracz.getGameMode() == GameMode.CREATIVE || gracz.getGameMode() == GameMode.SPECTATOR) return;
        if (maNozyce(gracz)) return;
        e.setCancelled(true);
        e.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void tylkoNozyceWczesnie(BlockBreakEvent e) {
        zablokujBezNozyc(e);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void tylkoNozyce(BlockBreakEvent e) {
        zablokujBezNozyc(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void tylkoNozyceObrazenia(org.bukkit.event.block.BlockDamageEvent e) {
        if (!toWelna(e.getBlock().getType())) return;
        Player gracz = e.getPlayer();
        if (gracz.getGameMode() == GameMode.CREATIVE || gracz.getGameMode() == GameMode.SPECTATOR) return;
        if (maNozyce(gracz)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Material typ = e.getBlock().getType();
        List<Wpis> lista = dropy.get(typ);
        if (lista == null) return; // nie nasza welna

        Player gracz = e.getPlayer();
        if (gracz.getGameMode() == GameMode.CREATIVE || gracz.getGameMode() == GameMode.SPECTATOR) return;
        if (!maNozyce(gracz)) return;
        if (!wRegionieDropu(e.getBlock().getLocation())) return;

        // Zwykly drop welny wylaczamy - zamiast niej leca itemy z komendy.
        if (cfg.getBoolean("wylacz-zwykly-drop", true)) {
            e.setDropItems(false);
        }

        // Bonus z ulepszen nekromanty (punkty procentowe) - tylko dla wpisow bedacych glutem.
        double bonus = nekromanta == null ? 0.0 : nekromanta.bonusProcent(gracz.getUniqueId());

        List<Traf> wylosowane = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Wpis w : lista) {
            if (w.getSzansa() <= 0) continue;
            double szansa = w.getSzansa();
            if (bonus > 0 && nekromanta.jestGlut(w.getItem())) {
                szansa = Math.min(SZANSA_MAX, szansa + bonus);
            }
            if (rnd.nextDouble(100.0) < szansa) wylosowane.add(new Traf(w, szansa));
        }
        if (wylosowane.isEmpty()) return;

        boolean doEkwipunku = cfg.getBoolean("do-ekwipunku", true);
        Location miejsce = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        String format = cfg.getString("wiadomosc",
                "&8[&a+&8] &7Wydobyto: &f%item% &7x%ilosc% &8(&e%szansa%%&8)");

        for (Traf traf : wylosowane) {
            ItemStack item = traf.wpis().getItem().clone();
            if (doEkwipunku) {
                for (ItemStack reszta : gracz.getInventory().addItem(item).values()) {
                    gracz.getWorld().dropItemNaturally(gracz.getLocation(), reszta);
                }
            } else {
                gracz.getWorld().dropItemNaturally(miejsce, item);
            }

            if (!format.isBlank()) {
                gracz.sendMessage(Msg.color(format
                        .replace("%item%", nazwaPrzedmiotu(item))
                        .replace("%ilosc%", String.valueOf(item.getAmount()))
                        .replace("%szansa%", formatSzansy(traf.szansa()))));
            }
        }

        zagrajDzwiek(gracz);
    }

    private void zagrajDzwiek(Player gracz) {
        String dzwiek = cfg.getString("dzwiek.nazwa", "entity.experience_orb.pickup");
        if (dzwiek == null || dzwiek.isBlank()) return;
        float glosnosc = (float) cfg.getDouble("dzwiek.glosnosc", 1.0);
        float ton = (float) cfg.getDouble("dzwiek.ton", 1.2);
        gracz.playSound(gracz.getLocation(), dzwiek, glosnosc, ton);
    }

    private boolean wRegionieDropu(Location loc) {
        List<String> regiony = cfg.getStringList("regiony");
        if (regiony.isEmpty()) return true; // pusta lista = wszedzie
        if (!wgWlaczony) return true;
        return WorldGuardHook.isInAnyRegion(loc, regiony.toArray(new String[0]));
    }

    public static String formatSzansy(double szansa) {
        if (szansa == Math.rint(szansa)) return String.valueOf((long) szansa);
        return String.format(Locale.US, "%.2f", szansa).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
