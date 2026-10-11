package boniec.mapareset;

import boniec.code.core.CoreDatabase;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Co X minut przywraca stan sprzed postawienia wszystkich blokow postawionych przez graczy.
 * Pamietany jest oryginalny blok (zwykle powietrze/trawa/woda) - przywrocenie nigdy nie zamuruje gracza.
 * Wpisy w niezaladowanych chunkach sa przywracane przy ich zaladowaniu.
 */
public final class MapaResetManager implements Listener, CommandExecutor, TabCompleter {

    private static final String BYPASS = "mapareset.bypass";
    private static final int BLOKOW_NA_TICK = 400;
    private static final int[] OSTRZEZENIA = {300, 60, 10};

    private record Klucz(String swiat, int x, int y, int z) {
        static Klucz z(Block b) {
            return new Klucz(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }

        long chunk() {
            return ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
        }
    }

    private record Operacja(Klucz klucz, String dane) {
    }

    private final JavaPlugin plugin;
    private final int interwalSekund;
    /** Klucz -> oryginalny BlockData (jako string). */
    private final Map<Klucz, String> postawione = new HashMap<>();
    /** Swiat -> chunki, w ktorych przy resecie zostaly wpisy do przywrocenia po zaladowaniu. */
    private final Map<String, Set<Long>> zalegleChunki = new HashMap<>();
    private final Set<UUID> spadajaceZPostawionych = new HashSet<>();
    private final ConcurrentLinkedQueue<Operacja> doZapisu = new ConcurrentLinkedQueue<>();
    private long nastepnyReset;
    private boolean resetTrwa;

    public MapaResetManager(JavaPlugin plugin, int interwalMinut) {
        this.plugin = plugin;
        this.interwalSekund = Math.max(1, interwalMinut) * 60;
        wczytaj();
        nastepnyReset = System.currentTimeMillis() + interwalSekund * 1000L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::zapiszKolejke, 100L, 100L);
    }

    // ───────────────────────── baza ─────────────────────────

    private void wczytaj() {
        try (Statement st = CoreDatabase.connection().createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS mapa_reset (
                    swiat TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    dane TEXT NOT NULL,
                    PRIMARY KEY (swiat, x, y, z)
                )
                """);
            try (ResultSet rs = st.executeQuery("SELECT swiat, x, y, z, dane FROM mapa_reset")) {
                while (rs.next()) {
                    postawione.put(new Klucz(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)),
                            rs.getString(5));
                }
            }
            plugin.getLogger().info("[MapaReset] Wczytano " + postawione.size() + " postawionych blokow.");
        } catch (SQLException e) {
            plugin.getLogger().severe("[MapaReset] Nie udalo sie wczytac: " + e.getMessage());
        }
    }

    /** Operacja z dane == null oznacza usuniecie wpisu. */
    private void zapiszKolejke() {
        if (doZapisu.isEmpty()) return;
        // Ostatnia operacja na danym bloku wygrywa, wiec kolejnosc batchy nie ma znaczenia.
        Map<Klucz, Operacja> ostatnie = new java.util.LinkedHashMap<>();
        Operacja op;
        while ((op = doZapisu.poll()) != null) ostatnie.put(op.klucz(), op);
        List<Operacja> ops = new ArrayList<>(ostatnie.values());
        try {
            CoreDatabase.runInTransaction(c -> {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT OR REPLACE INTO mapa_reset (swiat, x, y, z, dane) VALUES (?, ?, ?, ?, ?)");
                     PreparedStatement del = c.prepareStatement(
                             "DELETE FROM mapa_reset WHERE swiat = ? AND x = ? AND y = ? AND z = ?")) {
                    for (Operacja o : ops) {
                        PreparedStatement ps = o.dane() != null ? ins : del;
                        ps.setString(1, o.klucz().swiat());
                        ps.setInt(2, o.klucz().x());
                        ps.setInt(3, o.klucz().y());
                        ps.setInt(4, o.klucz().z());
                        if (o.dane() != null) ps.setString(5, o.dane());
                        ps.addBatch();
                    }
                    ins.executeBatch();
                    del.executeBatch();
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().warning("[MapaReset] Zapis: " + e.getMessage());
        }
    }

    public void zamknij() {
        zapiszKolejke();
    }

    // ───────────────────────── sledzenie ─────────────────────────

    private void zapamietaj(Block b, BlockData oryginal) {
        Klucz k = Klucz.z(b);
        if (postawione.containsKey(k)) return;
        String dane = oryginal.getAsString();
        postawione.put(k, dane);
        doZapisu.add(new Operacja(k, dane));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getPlayer().hasPermission(BYPASS)) return;
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState stan : multi.getReplacedBlockStates()) {
                zapamietaj(stan.getBlock(), stan.getBlockData());
            }
        } else {
            zapamietaj(event.getBlock(), event.getBlockReplacedState().getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (event.getPlayer().hasPermission(BYPASS)) return;
        Block b = event.getBlock();
        zapamietaj(b, b.getBlockData());
    }

    /** Piasek/zwir/concrete powder postawiony przez gracza spada - sledzimy, gdzie wyladuje. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFalling(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        Block b = event.getBlock();
        if (event.getTo() == Material.AIR) {
            if (postawione.containsKey(Klucz.z(b))) spadajaceZPostawionych.add(fb.getUniqueId());
        } else if (spadajaceZPostawionych.remove(fb.getUniqueId())) {
            zapamietaj(b, b.getBlockData());
        }
    }

    // ───────────────────────── reset ─────────────────────────

    private void tick() {
        if (resetTrwa) return;
        long zostalo = (nastepnyReset - System.currentTimeMillis() + 500) / 1000;
        for (int s : OSTRZEZENIA) {
            if (zostalo == s) {
                ogloszenie("&8[&6MAPA&8] &7Reset postawionych blokow za &e" + opisCzasu(s) + "&7!");
            }
        }
        if (zostalo <= 0) resetuj();
    }

    private static String opisCzasu(int s) {
        if (s >= 60) return (s / 60) + " min";
        return s + " s";
    }

    private void ogloszenie(String tekst) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', tekst));
    }

    public void resetuj() {
        if (resetTrwa) return;
        resetTrwa = true;
        nastepnyReset = System.currentTimeMillis() + interwalSekund * 1000L;

        List<Map.Entry<Klucz, String>> doPrzywrocenia = new ArrayList<>();
        Iterator<Map.Entry<Klucz, String>> it = postawione.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Klucz, String> e = it.next();
            Klucz k = e.getKey();
            World w = Bukkit.getWorld(k.swiat());
            if (w == null) continue;
            if (!w.isChunkLoaded(k.x() >> 4, k.z() >> 4)) {
                zalegleChunki.computeIfAbsent(k.swiat(), s -> new HashSet<>()).add(k.chunk());
                continue;
            }
            doPrzywrocenia.add(Map.entry(k, e.getValue()));
            it.remove();
        }
        final int ile = doPrzywrocenia.size();
        Bukkit.getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<org.bukkit.scheduler.BukkitTask>() {
            int i = 0;

            @Override
            public void accept(org.bukkit.scheduler.BukkitTask task) {
                int koniec = Math.min(i + BLOKOW_NA_TICK, doPrzywrocenia.size());
                for (; i < koniec; i++) {
                    Map.Entry<Klucz, String> e = doPrzywrocenia.get(i);
                    przywroc(e.getKey(), e.getValue());
                }
                if (i >= doPrzywrocenia.size()) {
                    task.cancel();
                    resetTrwa = false;
                    ogloszenie("&8[&6MAPA&8] &aMapa zostala zresetowana &7(" + ile + " blokow)&a.");
                }
            }
        }, 1L, 1L);
    }

    private void przywroc(Klucz k, String dane) {
        World w = Bukkit.getWorld(k.swiat());
        if (w != null) {
            try {
                BlockData data = Bukkit.createBlockData(dane);
                w.getBlockAt(k.x(), k.y(), k.z()).setBlockData(data, false);
            } catch (IllegalArgumentException ex) {
                w.getBlockAt(k.x(), k.y(), k.z()).setType(Material.AIR, false);
            }
        }
        doZapisu.add(new Operacja(k, null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk ch = event.getChunk();
        Set<Long> zalegle = zalegleChunki.get(ch.getWorld().getName());
        if (zalegle == null) return;
        long klucz = ((long) ch.getX() << 32) ^ (ch.getZ() & 0xffffffffL);
        if (!zalegle.remove(klucz)) return;
        String swiat = ch.getWorld().getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Iterator<Map.Entry<Klucz, String>> it = postawione.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Klucz, String> e = it.next();
                Klucz k = e.getKey();
                if (k.swiat().equals(swiat) && k.chunk() == klucz) {
                    przywroc(k, e.getValue());
                    it.remove();
                }
            }
        });
    }

    // ───────────────────────── komenda ─────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("mapareset.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnien!");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("teraz")) {
            if (resetTrwa) {
                sender.sendMessage(ChatColor.RED + "Reset juz trwa.");
                return true;
            }
            resetuj();
            sender.sendMessage(ChatColor.GREEN + "Reset mapy uruchomiony.");
            return true;
        }
        long zostalo = Math.max(0, (nastepnyReset - System.currentTimeMillis()) / 1000);
        sender.sendMessage(ChatColor.YELLOW + "Postawione bloki: " + ChatColor.WHITE + postawione.size()
                + ChatColor.YELLOW + " | Reset za: " + ChatColor.WHITE + (zostalo / 60) + "m " + (zostalo % 60) + "s");
        sender.sendMessage(ChatColor.GRAY + "/mapareset teraz - reset od razu");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && "teraz".startsWith(args[0].toLowerCase(Locale.ROOT))) return List.of("teraz");
        return List.of();
    }
}
