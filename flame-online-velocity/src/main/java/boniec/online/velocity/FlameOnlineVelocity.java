package boniec.online.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Plugin(id = "flameonline", name = "FlameOnline", version = "1.0", authors = {"Boniec"})
public final class FlameOnlineVelocity {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private SimpleYaml cfg;

    @Inject
    public FlameOnlineVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    SimpleYaml cfg() {
        return cfg;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getEventManager().register(this, new ProtectionListener(this, proxy, logger));
        proxy.getCommandManager().register("flameonline", new ReloadCommand());
        StringBuilder names = new StringBuilder();
        for (RegisteredServer s : proxy.getAllServers()) {
            if (names.length() > 0) names.append(", ");
            names.append(s.getServerInfo().getName());
        }
        logger.info("FlameOnline Velocity: SkyPvP x{}, lobby 1:1 | serwery w toml: [{}]", mnoznik(), names);
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        if (cfg == null || !cfg.getBoolean("motd.enabled", true)) return;
        ServerPing.Builder b = event.getPing().asBuilder();
        if (cfg.getBoolean("motd.fake-count", true)) {
            b.onlinePlayers(shownAll());
        }
        if (cfg.getBoolean("motd.fake-max", true)) {
            int extra = Math.max(0, shownAll() - proxy.getPlayerCount());
            b.maximumPlayers(Math.max(1, b.getMaximumPlayers() + extra));
        }
        String l1 = cfg.get("motd.linia1", "");
        String l2 = cfg.get("motd.linia2", "");
        if (l1 != null && !l1.isEmpty()) {
            String motd = apply(l1) + (l2 == null || l2.isEmpty() ? "" : "\n" + apply(l2));
            b.description(LEGACY.deserialize(motd));
        }
        event.setPing(b.build());
    }

    Optional<RegisteredServer> findById(String id) {
        if (id == null || cfg == null) return Optional.empty();
        String mapped = cfg.get("serwery." + id, id);
        if (mapped == null || mapped.isBlank()) mapped = id;
        Optional<RegisteredServer> srv = proxy.getServer(mapped);
        if (srv.isPresent()) return srv;
        for (RegisteredServer s : proxy.getAllServers()) {
            String n = s.getServerInfo().getName();
            if (n.equalsIgnoreCase(mapped) || n.equalsIgnoreCase(id)) return Optional.of(s);
        }
        return Optional.empty();
    }

    int mnoznik() {
        return cfg == null ? 3 : Math.max(1, cfg.getInt("mnoznik", 3));
    }

    int real(String id) {
        if (id == null || cfg == null) return 0;
        if (id.equalsIgnoreCase("all")) return proxy.getPlayerCount();
        String mapped = cfg.get("serwery." + id, id);
        if (mapped == null || mapped.isBlank()) mapped = id;
        Optional<RegisteredServer> srv = proxy.getServer(mapped);
        if (srv.isEmpty()) {
            for (RegisteredServer s : proxy.getAllServers()) {
                if (s.getServerInfo().getName().equalsIgnoreCase(mapped)
                        || s.getServerInfo().getName().equalsIgnoreCase(id)) {
                    return s.getPlayersConnected().size();
                }
            }
            return 0;
        }
        return srv.get().getPlayersConnected().size();
    }

    boolean mnoznikDlaKlucza(String id) {
        if (id == null || cfg == null) return false;
        return cfg.getBoolean("mnoznik-na." + id, id.equalsIgnoreCase("skypvp"));
    }

    boolean mnoznikDlaSerwera(String velocityName) {
        if (velocityName == null || cfg == null) return false;
        for (Map.Entry<String, String> e : cfg.children("serwery").entrySet()) {
            if (e.getKey().contains(".")) continue;
            if (velocityName.equalsIgnoreCase(e.getValue()) && mnoznikDlaKlucza(e.getKey())) return true;
        }
        return velocityName.equalsIgnoreCase("skypvp");
    }

    int shown(String id) {
        int n = real(id);
        return mnoznikDlaKlucza(id) ? n * mnoznik() : n;
    }

    int shownAll() {
        int sum = 0;
        for (Player p : proxy.getAllPlayers()) {
            java.util.Optional<ServerConnection> conn = p.getCurrentServer();
            if (conn.isEmpty()) {
                sum += 1;
            } else if (mnoznikDlaSerwera(conn.get().getServerInfo().getName())) {
                sum += mnoznik();
            } else {
                sum += 1;
            }
        }
        return sum;
    }

    String apply(String text) {
        if (text == null) return "";
        String out = text.replace("{mnoznik}", String.valueOf(mnoznik()))
                .replace("{online_all}", String.valueOf(shownAll()))
                .replace("{online_all_real}", String.valueOf(proxy.getPlayerCount()));
        if (cfg != null) {
            for (Map.Entry<String, String> e : cfg.children("serwery").entrySet()) {
                out = out.replace("{online_" + e.getKey() + "}", String.valueOf(shown(e.getKey())));
                out = out.replace("{online_" + e.getKey() + "_real}", String.valueOf(real(e.getKey())));
            }
        }
        return out;
    }

    void loadConfig() {
        try {
            Files.createDirectories(dataDir);
            Path file = dataDir.resolve("config.yml");
            if (!Files.exists(file)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            cfg = SimpleYaml.load(file);
        } catch (Exception e) {
            logger.error("Nie udalo sie wczytac config.yml", e);
            cfg = new SimpleYaml();
        }
    }

    private final class ReloadCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!invocation.source().hasPermission("flameonline.admin")) {
                invocation.source().sendMessage(Component.text("Brak permisji."));
                return;
            }
            String[] args = invocation.arguments();
            boolean whitelistCmd = args.length >= 3
                    && (args[0].equalsIgnoreCase("whitelist") || args[0].equalsIgnoreCase("antivpn"));
            if (whitelistCmd && args[1].equalsIgnoreCase("add")) {
                String nick = args[2].trim();
                if (dodajDoWhitelist("whitelist-nick", nick)) {
                    invocation.source().sendMessage(Component.text("Whitelist ochrony: dodano nick " + nick));
                } else {
                    invocation.source().sendMessage(Component.text("Nie udalo sie zapisac config.yml"));
                }
                return;
            }
            if (whitelistCmd && args[1].equalsIgnoreCase("ip")) {
                String ip = args[2].trim();
                if (dodajDoWhitelist("whitelist-ip", ip)) {
                    invocation.source().sendMessage(Component.text("Whitelist ochrony: dodano IP " + ip));
                } else {
                    invocation.source().sendMessage(Component.text("Nie udalo sie zapisac config.yml"));
                }
                return;
            }
            loadConfig();
            invocation.source().sendMessage(Component.text("FlameOnline Velocity przeladowany. x" + mnoznik()));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("flameonline.admin");
        }
    }

    private boolean dodajDoWhitelist(String sekcja, String klucz) {
        Path file = dataDir.resolve("config.yml");
        try {
            List<String> lines = Files.exists(file)
                    ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                    : new ArrayList<>();
            String wpis = "    " + klucz + ": true";
            for (String line : lines) {
                if (line.trim().equalsIgnoreCase(klucz + ": true")
                        || line.trim().equalsIgnoreCase(klucz + ":true")) {
                    loadConfig();
                    return true;
                }
            }
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.equals(sekcja + ":") || t.startsWith(sekcja + ":")) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                lines.add(idx + 1, wpis);
            } else {
                lines.add("  " + sekcja + ":");
                lines.add(wpis);
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
            loadConfig();
            return true;
        } catch (Exception e) {
            logger.error("Whitelist ochrony: nie zapisano {}", klucz, e);
            return false;
        }
    }
}
