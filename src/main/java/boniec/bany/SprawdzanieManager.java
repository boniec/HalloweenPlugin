package boniec.bany;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /sprawdz - zamrozenie, teleport i wynik przez komendy konsoli (zewnetrzny plugin banow). */
public final class SprawdzanieManager implements Listener, CommandExecutor, TabCompleter {

    private static final List<String> WYNIKI = List.of("czysty", "cheater", "brakwspolpracy", "przyznanie");

    private record Sesja(String admin, Location powrot) {
    }

    private final JavaPlugin plugin;
    private final BanManager bany;
    private final File plik;
    private YamlConfiguration cfg;
    private final Map<UUID, Sesja> sprawdzani = new ConcurrentHashMap<>();
    /** Teleporty wykonywane przez ten modul - nie blokujemy ich w onTeleport. */
    private final Set<UUID> wlasnyTeleport = new HashSet<>();

    public SprawdzanieManager(JavaPlugin plugin, BanManager bany) {
        this.plugin = plugin;
        this.bany = bany;
        this.plik = new File(plugin.getDataFolder(), "sprawdzanie.yml");
        if (!plik.exists()) plugin.saveResource("sprawdzanie.yml", false);
        this.cfg = YamlConfiguration.loadConfiguration(plik);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> pokazTitle(), 20L, 40L);
    }

    public boolean jestSprawdzany(Player p) {
        return sprawdzani.containsKey(p.getUniqueId());
    }

    // ───────────────────────── komenda ─────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("bany.sprawdz") && !sender.hasPermission("bany.admin")) {
            sender.sendMessage(color("&cNie masz uprawnien!"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(color("&cUzycie: /sprawdz <gracz> [czysty|cheater|brakwspolpracy|przyznanie]"));
            sender.sendMessage(color("&c/sprawdz ustaw &7- miejsce sprawdzania &8| &c/sprawdz lista"));
            return true;
        }
        if (args[0].equalsIgnoreCase("ustaw")) {
            ustawLokacje(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("lista")) {
            lista(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            cfg = YamlConfiguration.loadConfiguration(plik);
            sender.sendMessage(color("&aPrzeladowano sprawdzanie.yml"));
            return true;
        }

        Player cel = Bukkit.getPlayerExact(args[0]);
        if (cel == null) {
            sender.sendMessage(color("&cGracz &f" + args[0] + " &cnie jest online."));
            return true;
        }
        if (args.length == 1) {
            rozpocznij(sender, cel);
            return true;
        }
        String wynik = args[1].toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (!WYNIKI.contains(wynik)) {
            sender.sendMessage(color("&cWynik: czysty, cheater, brakwspolpracy, przyznanie"));
            return true;
        }
        if (!jestSprawdzany(cel)) {
            sender.sendMessage(color("&f" + cel.getName() + " &cnie jest teraz sprawdzany."));
            return true;
        }
        zakonczWynikiem(sender, cel, wynik);
        return true;
    }

    private void rozpocznij(CommandSender sender, Player cel) {
        if (jestSprawdzany(cel)) {
            Sesja s = sprawdzani.get(cel.getUniqueId());
            sender.sendMessage(color("&f" + cel.getName() + " &cjest juz sprawdzany przez &f" + s.admin() + "&c."));
            return;
        }
        if (cel.equals(sender)) {
            sender.sendMessage(color("&cNie mozesz sprawdzac samego siebie."));
            return;
        }
        if (cel.hasPermission("bany.exempt")) {
            sender.sendMessage(color("&cTego gracza nie mozna sprawdzac."));
            return;
        }
        Location cele = lokacja();
        if (cele == null) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(color("&cUstaw najpierw miejsce: /sprawdz ustaw (z gry)."));
                return;
            }
            cele = admin.getLocation();
        }

        String admin = sender instanceof Player p ? p.getName() : "Konsola";
        Location powrot = cel.getLocation().clone();
        teleportuj(cel, cele);
        sprawdzani.put(cel.getUniqueId(), new Sesja(admin, powrot));
        cel.closeInventory();

        for (String linia : linie("wiadomosci.start")) {
            cel.sendMessage(linia.replace("%admin%", admin).replace("%gracz%", cel.getName()));
        }
        pokazTitle(cel);
        sender.sendMessage(color("&aRozpoczeto sprawdzanie &f" + cel.getName() + "&a. Zakoncz: &f/sprawdz "
                + cel.getName() + " <czysty|cheater|brakwspolpracy|przyznanie>"));
        doStaffu(sender, color("&8[&eSPRAWDZANIE&8] &f" + admin + " &7sprawdza &f" + cel.getName()));
    }

    private void zakonczWynikiem(CommandSender sender, Player cel, String wynik) {
        Sesja s = sprawdzani.remove(cel.getUniqueId());
        cel.resetTitle();
        String admin = sender instanceof Player p ? p.getName() : "Konsola";
        String powod = powodWyniku(wynik);
        if (wynik.equals("czysty")) {
            if (s != null && s.powrot() != null && s.powrot().getWorld() != null) {
                teleportuj(cel, s.powrot());
            }
            cel.sendMessage(color(cfg.getString("wiadomosci.czysty", "&aJestes czysty.")));
            sender.sendMessage(color("&aZakonczono sprawdzanie - &f" + cel.getName() + " &ajest czysty."));
            doStaffu(sender, color("&8[&eSPRAWDZANIE&8] &f" + cel.getName() + " &ajest czysty &8(&7" + admin + "&8)"));
        } else {
            sender.sendMessage(color("&cZakonczono sprawdzanie &f" + cel.getName() + " &8(&7" + wynik + "&8) &7- " + powod));
            doStaffu(sender, color("&8[&eSPRAWDZANIE&8] &f" + cel.getName() + " &c" + wynik + " &8(&7" + admin + "&8) &7- " + powod));
        }
        wykonajKomendy(cel.getName(), cel.getUniqueId().toString(), admin, wynik, powod);
    }

    private String powodWyniku(String wynik) {
        String v = cfg.getString("wyniki." + wynik + ".powod");
        if (v == null || v.isBlank()) v = cfg.getString("bany." + wynik + ".powod");
        return v == null || v.isBlank() ? "Sprawdzanie: " + wynik : v;
    }

    /** Konsola odpala komendy z sprawdzanie.yml (np. LiteBans /ban), nie nasz stary system. */
    private void wykonajKomendy(String nick, String uuid, String admin, String wynik, String powod) {
        List<String> komendy = cfg.getStringList("wyniki." + wynik + ".komendy");
        if (komendy.isEmpty()) komendy = cfg.getStringList("bany." + wynik + ".komendy");
        if (komendy.isEmpty() && !wynik.equals("czysty")) {
            String czas = cfg.getString("wyniki." + wynik + ".czas");
            if (czas == null || czas.isBlank()) czas = cfg.getString("bany." + wynik + ".czas", "perm");
            if (czas == null || czas.equalsIgnoreCase("perm") || czas.equalsIgnoreCase("permanent")) {
                komendy = List.of("ban %gracz% %powod%");
            } else {
                komendy = List.of("tempban %gracz% " + czas + " %powod%");
            }
        }
        for (String raw : komendy) {
            if (raw == null || raw.isBlank()) continue;
            String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
            cmd = cmd.replace("%gracz%", nick)
                    .replace("%nick%", nick)
                    .replace("%uuid%", uuid == null ? "" : uuid)
                    .replace("%admin%", admin == null ? "Konsola" : admin)
                    .replace("%powod%", powod)
                    .replace("%wynik%", wynik);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private void lista(CommandSender sender) {
        if (sprawdzani.isEmpty()) {
            sender.sendMessage(color("&7Nikt nie jest teraz sprawdzany."));
            return;
        }
        sender.sendMessage(color("&eSprawdzani:"));
        for (Map.Entry<UUID, Sesja> e : sprawdzani.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            sender.sendMessage(color(" &8- &f" + (p != null ? p.getName() : e.getKey()) + " &7(sprawdza: &f"
                    + e.getValue().admin() + "&7)"));
        }
    }

    // ───────────────────────── lokacja ─────────────────────────

    private void ustawLokacje(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(color("&cTylko z gry."));
            return;
        }
        Location l = p.getLocation();
        cfg.set("lokacja", l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";"
                + l.getYaw() + ";" + l.getPitch());
        try {
            cfg.save(plik);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie zapisano sprawdzanie.yml: " + e.getMessage());
        }
        p.sendMessage(color("&aUstawiono miejsce sprawdzania."));
    }

    private Location lokacja() {
        String raw = cfg.getString("lokacja", "");
        if (raw == null || raw.isBlank()) return null;
        String[] c = raw.split(";");
        if (c.length < 6) return null;
        World w = Bukkit.getWorld(c[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(c[1]), Double.parseDouble(c[2]), Double.parseDouble(c[3]),
                    Float.parseFloat(c[4]), Float.parseFloat(c[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ───────────────────────── pomocnicze ─────────────────────────

    private void teleportuj(Player p, Location l) {
        wlasnyTeleport.add(p.getUniqueId());
        try {
            p.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            wlasnyTeleport.remove(p.getUniqueId());
        }
    }

    private String color(String s) {
        return bany.color(s);
    }

    private List<String> linie(String path) {
        List<String> out = new ArrayList<>();
        if (cfg.isList(path)) {
            for (String l : cfg.getStringList(path)) out.add(color(l));
        } else {
            out.add(color(cfg.getString(path, "")));
        }
        return out;
    }

    private void doStaffu(CommandSender pomin, String tekst) {
        String perm = bany.cfg().getString("ogolne.permisja-powiadomien", "bany.powiadomienia");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(pomin) && p.hasPermission(perm)) p.sendMessage(tekst);
        }
        if (!(pomin instanceof ConsoleCommandSender)) Bukkit.getConsoleSender().sendMessage(tekst);
    }

    private void pokazTitle() {
        for (UUID uuid : sprawdzani.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) pokazTitle(p);
        }
    }

    private void pokazTitle(Player p) {
        p.sendTitle(color(cfg.getString("wiadomosci.title", "&c&lSPRAWDZANIE")),
                color(cfg.getString("wiadomosci.subtitle", "")), 0, 60, 10);
    }

    // ───────────────────────── blokady ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (sprawdzani.remove(p.getUniqueId()) == null) return;
        doStaffu(null, color("&8[&eSPRAWDZANIE&8] &f" + p.getName() + " &cwylogowal sie podczas sprawdzania!"));
        String wynik = "wylogowanie";
        wykonajKomendy(p.getName(), p.getUniqueId().toString(), "SYSTEM", wynik, powodWyniku(wynik));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!jestSprawdzany(event.getPlayer())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        Location stoj = from.clone();
        stoj.setYaw(to.getYaw());
        stoj.setPitch(to.getPitch());
        event.setTo(stoj);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (jestSprawdzany(p) && !wlasnyTeleport.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!jestSprawdzany(event.getPlayer())) return;
        String msg = event.getMessage();
        String komenda = msg.length() < 2 ? "" : msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int dwukropek = komenda.indexOf(':');
        if (dwukropek >= 0) komenda = komenda.substring(dwukropek + 1);
        for (String dozwolona : cfg.getStringList("dozwolone-komendy")) {
            if (dozwolona.equalsIgnoreCase(komenda)) return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(color("&cNie mozesz uzywac komend podczas sprawdzania."));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && jestSprawdzany(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player atakujacy = null;
        if (event.getDamager() instanceof Player p) {
            atakujacy = p;
        } else if (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            atakujacy = p;
        }
        if (atakujacy != null && jestSprawdzany(atakujacy)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (jestSprawdzany(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (jestSprawdzany(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (jestSprawdzany(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (jestSprawdzany(event.getPlayer())) event.setCancelled(true);
    }

    // ───────────────────────── tab ─────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("bany.sprawdz") && !sender.hasPermission("bany.admin")) return out;
        String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.addAll(List.of("ustaw", "lista", "reload"));
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2) {
            out.addAll(WYNIKI);
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
