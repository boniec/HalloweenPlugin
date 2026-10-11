package boniec.ahalloween;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cukierek albo psikus — PPM na drzwi domu eventowego.
 */
public final class HalloweenTrickOrTreat {

    private final AHalloweenManager manager;
    private final JavaPlugin plugin;

    private boolean enabled;
    private boolean wszystkieDrzwi;
    private double szansaCukierka;
    private boolean bonusSzczescie;
    private int cooldownGraczSec;
    private int cooldownDomSec;
    private int opoznienieTicki;
    private int cukierkiMin;
    private int cukierkiMax;
    private int trickMinSec;
    private int trickMaxSec;
    private List<PotionEffectType> trickEfekty = List.of();

    /** spawn.dom.{id} → nazwa wyswietlana */
    private final Map<String, String> domy = new ConcurrentHashMap<>();
    /** world;x;y;z bloku → spawn.dom.{id} */
    private final Map<String, String> blokDoDomu = new ConcurrentHashMap<>();

    private final Map<UUID, Long> cooldownGracz = new ConcurrentHashMap<>();
    /** gracz|blok → czas */
    private final Map<String, Long> cooldownDom = new ConcurrentHashMap<>();
    private final Set<UUID> puka = ConcurrentHashMap.newKeySet();

    public HalloweenTrickOrTreat(AHalloweenManager manager, JavaPlugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration cfg = manager.cfg();
        ConfigurationSection s = cfg.getConfigurationSection("trick-or-treat");
        enabled = s == null || s.getBoolean("wlaczone", true);
        wszystkieDrzwi = s != null && s.getBoolean("wszystkie-drzwi", false);
        szansaCukierka = s == null ? 0.62 : Math.max(0.05, Math.min(0.98, s.getDouble("szansa-cukierka", 0.62)));
        bonusSzczescie = s == null || s.getBoolean("bonus-szczescie", true);
        cooldownGraczSec = Math.max(5, s == null ? 30 : s.getInt("cooldown-gracz-sekund", 30));
        cooldownDomSec = Math.max(10, s == null ? 120 : s.getInt("cooldown-dom-sekund", 120));
        opoznienieTicki = Math.max(5, s == null ? 14 : s.getInt("opoznienie-odpowiedzi-ticki", 14));
        cukierkiMin = Math.max(1, s == null ? 1 : s.getInt("cukierki-min", 1));
        cukierkiMax = Math.max(cukierkiMin, s == null ? 3 : s.getInt("cukierki-max", 3));
        trickMinSec = Math.max(2, s == null ? 4 : s.getInt("trick.czas-min-sekund", 4));
        trickMaxSec = Math.max(trickMinSec, s == null ? 7 : s.getInt("trick.czas-max-sekund", 7));
        trickEfekty = wczytajEfekty(s == null ? null : s.getStringList("trick.efekty"));
        wczytajDomy();
    }

    private static List<PotionEffectType> wczytajEfekty(List<String> lista) {
        if (lista == null || lista.isEmpty()) {
            return List.of(PotionEffectType.SLOWNESS, PotionEffectType.NAUSEA, PotionEffectType.DARKNESS);
        }
        List<PotionEffectType> out = new ArrayList<>();
        for (String raw : lista) {
            if (raw == null || raw.isBlank()) continue;
            PotionEffectType typ = PotionEffectType.getByName(raw.trim().toUpperCase(Locale.ROOT));
            if (typ != null) out.add(typ);
        }
        return out.isEmpty()
                ? List.of(PotionEffectType.SLOWNESS, PotionEffectType.NAUSEA, PotionEffectType.DARKNESS)
                : List.copyOf(out);
    }

    public void wczytajDomy() {
        domy.clear();
        blokDoDomu.clear();
        for (Map.Entry<String, String> e : manager.baza().wszystkieKv("spawn.dom.").entrySet()) {
            Location loc = manager.deserializuj(e.getValue());
            if (loc == null || loc.getWorld() == null) continue;
            String id = e.getKey();
            String nazwa = manager.nazwaDomu(id);
            if (nazwa == null || nazwa.isBlank()) {
                nazwa = id.replace("spawn.dom.", "").replace('_', ' ');
            }
            domy.put(id, nazwa);
            powiazBlokiDrzwi(loc.getBlock(), id);
        }
    }

    private void powiazBlokiDrzwi(Block block, String domId) {
        for (Block czesc : czesciDrzwi(block)) {
            blokDoDomu.put(kluczBloku(czesc), domId);
        }
    }

    private String domDlaBloku(Block block) {
        if (block == null) return null;
        String id = blokDoDomu.get(kluczBloku(block));
        if (id != null) return id;
        for (Block czesc : czesciDrzwi(block)) {
            id = blokDoDomu.get(kluczBloku(czesc));
            if (id != null) return id;
        }
        return null;
    }

    private static List<Block> czesciDrzwi(Block block) {
        List<Block> out = new ArrayList<>();
        out.add(block);
        if (block.getBlockData() instanceof Door door) {
            Block drugi = door.getHalf() == Bisected.Half.BOTTOM
                    ? block.getRelative(BlockFace.UP)
                    : block.getRelative(BlockFace.DOWN);
            if (drugi.getType() == block.getType()) {
                out.add(drugi);
            }
        }
        return out;
    }

    public int liczbaDomow() {
        return domy.size();
    }

    public List<String> opisDomow() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : domy.entrySet()) {
            Location loc = manager.deserializuj(manager.baza().kv(e.getKey(), ""));
            if (loc == null || loc.getWorld() == null) {
                out.add(e.getValue() + " &8(&cbrak swiata&8)");
                continue;
            }
            Block b = loc.getBlock();
            out.add("&f" + e.getValue() + " &8→ &7" + loc.getWorld().getName()
                    + " &8(&2" + b.getX() + "&8, &2" + b.getY() + "&8, &2" + b.getZ() + "&8)");
        }
        return out;
    }

    public String dodajDom(Player p, String nazwa) {
        Block block = p.getTargetBlockExact(6);
        if (block == null || !jestDrzwi(block.getType())) {
            return null;
        }
        String id = (nazwa == null || nazwa.isBlank())
                ? "dom_" + System.currentTimeMillis()
                : nazwa.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_-]", "");
        if (id.isBlank()) id = "dom_" + System.currentTimeMillis();
        String key = "spawn.dom." + id;
        String wyswietl = (nazwa == null || nazwa.isBlank()) ? "Dom Halloween" : nazwa.trim();
        manager.zapiszDom(key, wyswietl, block.getLocation());
        wczytajDomy();
        return key;
    }

    public boolean usunDom(Player p) {
        Block block = p.getTargetBlockExact(6);
        if (block == null) return false;
        String domId = domDlaBloku(block);
        if (domId == null) return false;
        manager.usunDom(domId);
        wczytajDomy();
        return true;
    }

    public int wyczyscDomy() {
        int n = manager.baza().wszystkieKv("spawn.dom.").size();
        manager.wyczyscSpawny("dom.");
        wczytajDomy();
        return n;
    }

    /**
     * @return true gdy interakcja zostala obsluzona (pukanie / cooldown info)
     */
    public boolean obsluzPukanie(Player player, Block block) {
        if (block == null) return false;
        if (!jestDrzwi(block.getType())) return false;

        String domId = domDlaBloku(block);
        boolean znanyDom = domId != null;
        if (!znanyDom && !wszystkieDrzwi) {
            return false;
        }

        if (!enabled) {
            if (znanyDom) {
                player.sendMessage(manager.msg("trick-wylaczone"));
            }
            return znanyDom;
        }

        if (!manager.swiatEventu(block.getLocation())) {
            if (znanyDom || wszystkieDrzwi) {
                player.sendMessage(manager.msg("trick-zly-swiata"));
            }
            return znanyDom || wszystkieDrzwi;
        }

        if (!manager.aktywny()) {
            player.sendMessage(manager.msg("trick-event-wylaczony"));
            return true;
        }

        String nazwaDomu = domId == null
                ? manager.msgPlain("trick-dom-domyslny", "nazwa", "Dom")
                : domy.getOrDefault(domId, "Dom");

        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        if (puka.contains(uid)) return true;

        Long cdGracz = cooldownGracz.get(uid);
        if (cdGracz != null && cdGracz > now) {
            player.sendMessage(manager.msg("trick-cooldown", "czas",
                    String.valueOf((cdGracz - now + 999) / 1000)));
            return true;
        }

        String blokKey = kluczBloku(block);
        String cdDomKey = uid + "|" + blokKey;
        Long cdDom = cooldownDom.get(cdDomKey);
        if (cdDom != null && cdDom > now) {
            player.sendMessage(manager.msg("trick-cooldown-dom", "czas",
                    String.valueOf((cdDom - now + 999) / 1000)));
            return true;
        }

        puka.add(uid);
        cooldownGracz.put(uid, now + cooldownGraczSec * 1000L);
        cooldownDom.put(cdDomKey, now + cooldownDomSec * 1000L);

        Location drzwi = block.getLocation().add(0.5, 1.0, 0.5);
        efektPukania(drzwi, nazwaDomu, player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            puka.remove(uid);
            if (!player.isOnline()) return;
            odpowiedzDomu(player, drzwi, nazwaDomu);
        }, opoznienieTicki);

        return true;
    }

    private void efektPukania(Location loc, String nazwaDomu, Player player) {
        if (loc.getWorld() == null) return;
        loc.getWorld().playSound(loc, Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.9f, 0.75f);
        loc.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_AMBIENT, 0.35f, 0.6f);
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 6, 0.15, 0.1, 0.15, 0.01);
        player.sendMessage(manager.msg("trick-pukanie", "dom", nazwaDomu));
        player.sendTitle(
                HalloweenUtil.colorMsg("&#35105E&lpuk... puk..."),
                HalloweenUtil.colorMsg("&7" + nazwaDomu),
                5, 20, 5);
        actionBar(player, manager.msgPlain("trick-pukanie-action", "dom", nazwaDomu));
    }

    private void odpowiedzDomu(Player player, Location loc, String nazwaDomu) {
        HalloweenPlayer hp = manager.dane(player);
        manager.dodajPostep(hp, "trickortreat", 1);

        double szansa = szansaCukierka;
        if (bonusSzczescie) {
            szansa = Math.min(0.95, szansa + manager.bonusSzczescie(hp) * 0.35);
        }

        if (ThreadLocalRandom.current().nextDouble() < szansa) {
            int ile = ThreadLocalRandom.current().nextInt(cukierkiMin, cukierkiMax + 1);
            String candyId = manager.losujCukierek();
            AHalloweenManager.CandyDef def = manager.candy(candyId);
            String candyName = def == null ? candyId : HalloweenUtil.colorMsg(def.name());

            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.35f);
                loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 24, 0.35, 0.45, 0.35, 0.08);
                loc.getWorld().spawnParticle(Particle.WITCH, loc, 16, 0.3, 0.35, 0.3, 0.02);
            }
            player.sendTitle(
                    HalloweenUtil.colorMsg("&#FF7518&lCUKIEREK!"),
                    HalloweenUtil.colorMsg("&7" + nazwaDomu + " &8» &a+" + ile + " &7" + candyName),
                    5, 45, 12);
            player.sendMessage(manager.msg("trick-treat", "dom", nazwaDomu, "ilosc", String.valueOf(ile), "nazwa", candyName));
            manager.dodajCukierki(player, candyId, ile, "trick-or-treat");
        } else {
            psikus(player, loc, nazwaDomu);
        }
    }

    private void psikus(Player player, Location loc, String nazwaDomu) {
        int sec = ThreadLocalRandom.current().nextInt(trickMinSec, trickMaxSec + 1);
        int ticks = sec * 20;
        PotionEffectType typ = trickEfekty.get(ThreadLocalRandom.current().nextInt(trickEfekty.size()));
        player.addPotionEffect(new PotionEffect(typ, ticks, 0, false, true, true));

        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.ENTITY_GHAST_SCREAM, 0.35f, 1.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_BAT_AMBIENT, 0.8f, 0.7f);
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 30, 0.4, 0.5, 0.4, 0.03);
            loc.getWorld().spawnParticle(Particle.SOUL, loc, 12, 0.25, 0.35, 0.25, 0.02);
            for (int i = 0; i < 3; i++) {
                Bat bat = loc.getWorld().spawn(loc, Bat.class, b -> b.setAwake(true));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (bat.isValid()) bat.remove();
                }, 40L + i * 10L);
            }
        }

        String efekt = switch (typ.getKey().getKey().toLowerCase(Locale.ROOT)) {
            case "slowness" -> "spowolnienie";
            case "nausea" -> "nudności";
            case "darkness" -> "ciemność";
            case "weakness" -> "oslabienie";
            case "blindness" -> "ślepotę";
            default -> "psikus";
        };

        player.sendTitle(
                HalloweenUtil.colorMsg("&#9B30FF&lPSIKUS!"),
                HalloweenUtil.colorMsg("&7" + nazwaDomu + " &8» &c" + efekt),
                5, 40, 10);
        player.sendMessage(manager.msg("trick-trick", "dom", nazwaDomu, "efekt", efekt, "czas", String.valueOf(sec)));
    }

    private static boolean jestDrzwi(Material material) {
        return Tag.DOORS.isTagged(material)
                || Tag.TRAPDOORS.isTagged(material)
                || Tag.FENCE_GATES.isTagged(material);
    }

    static String kluczBloku(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    private static void actionBar(Player player, String message) {
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(HalloweenUtil.colorMsg(message)));
    }
}
