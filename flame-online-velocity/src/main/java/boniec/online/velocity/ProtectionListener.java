package boniec.online.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

final class ProtectionListener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final FlameOnlineVelocity plugin;
    private final ProxyServer proxy;
    private final Logger logger;

    /** ip|nick -> czas pierwszej (odrzuconej) proby wejscia. */
    private final Map<String, Long> pierwszeWejscie = new ConcurrentHashMap<>();
    /** ip|nick -> do kiedy wejscie nie wymaga ponownej weryfikacji. */
    private final Map<String, Long> zweryfikowani = new ConcurrentHashMap<>();
    private final Map<String, long[]> connWindow = new ConcurrentHashMap<>();

    ProtectionListener(FlameOnlineVelocity plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> sprawdz(event));
    }

    private void sprawdz(PreLoginEvent event) {
        SimpleYaml cfg = plugin.cfg();
        if (cfg == null) return;
        if (!cfg.getBoolean("ochrona.anti-bot", true)) return;

        String ip = ipOf(event.getConnection().getRemoteAddress());
        String nick = event.getUsername();
        if (ip == null) return;
        InetAddress addr = event.getConnection().getRemoteAddress().getAddress();
        if (addr != null && (addr.isLoopbackAddress() || addr.isSiteLocalAddress())) return;
        if (whitelistedIp(cfg, ip) || whitelistedNick(cfg, nick)) return;

        String regex = cfg.get("ochrona.nick-regex", "^[A-Za-z0-9_]{3,16}$");
        if (nick == null || !Pattern.compile(regex).matcher(nick).matches()) {
            deny(event, cfg.get("ochrona.kick-nick", "&cNieprawidlowy nick."));
            return;
        }
        int maxConn = cfg.getInt("ochrona.max-polaczen-na-10s", 4);
        if (zaDuzaCzestosc(ip, maxConn)) {
            deny(event, cfg.get("ochrona.kick-bot", "&cZa szybko. Sprobuj ponownie."));
            return;
        }
        int maxKonta = cfg.getInt("ochrona.max-kont-na-ip", 3);
        if (kontaNaIp(ip) >= maxKonta) {
            deny(event, cfg.get("ochrona.kick-ip", "&cZa duzo kont z tego IP."));
            return;
        }

        if (cfg.getBoolean("ochrona.podwojne-wejscie", true)) {
            String blad = podwojneWejscie(cfg, ip + "|" + nick.toLowerCase(java.util.Locale.ROOT));
            if (blad != null) deny(event, blad);
        }
    }

    /** @return null gdy gracz moze wejsc, inaczej powod kicka. */
    private String podwojneWejscie(SimpleYaml cfg, String klucz) {
        long now = System.currentTimeMillis();
        Long wazne = zweryfikowani.get(klucz);
        if (wazne != null && wazne > now) return null;

        long minMs = cfg.getInt("ochrona.podwojne-wejscie-min-sekund", 2) * 1000L;
        long maxMs = cfg.getInt("ochrona.podwojne-wejscie-max-sekund", 300) * 1000L;
        Long pierwsze = pierwszeWejscie.get(klucz);
        if (pierwsze != null && now - pierwsze <= maxMs) {
            if (now - pierwsze < minMs) {
                return cfg.get("ochrona.kick-za-szybko", "&cOdczekaj chwile i wejdz ponownie.");
            }
            pierwszeWejscie.remove(klucz);
            long godziny = cfg.getInt("ochrona.weryfikacja-wazna-godzin", 24);
            zweryfikowani.put(klucz, now + godziny * 60L * 60L * 1000L);
            sprzataj(now);
            return null;
        }
        pierwszeWejscie.put(klucz, now);
        sprzataj(now);
        return cfg.get("ochrona.kick-weryfikacja",
                "&aWeryfikacja anty-bot zaliczona!\n&7Wejdz na serwer &fjeszcze raz&7, aby zagrac.");
    }

    private void sprzataj(long now) {
        if (pierwszeWejscie.size() > 5000) {
            pierwszeWejscie.entrySet().removeIf(en -> now - en.getValue() > 10 * 60 * 1000L);
        }
        if (zweryfikowani.size() > 20000) {
            zweryfikowani.entrySet().removeIf(en -> en.getValue() < now);
        }
    }

    private boolean zaDuzaCzestosc(String ip, int max) {
        long now = System.currentTimeMillis();
        long[] hits = connWindow.computeIfAbsent(ip, k -> new long[16]);
        synchronized (hits) {
            int n = 0;
            for (int i = 0; i < hits.length; i++) {
                if (now - hits[i] <= 10_000L && hits[i] > 0) n++;
            }
            if (n >= max) return true;
            for (int i = 0; i < hits.length; i++) {
                if (hits[i] == 0 || now - hits[i] > 10_000L) {
                    hits[i] = now;
                    break;
                }
            }
        }
        return false;
    }

    private int kontaNaIp(String ip) {
        int n = 0;
        for (Player p : proxy.getAllPlayers()) {
            if (ip.equals(ipOf(p.getRemoteAddress()))) n++;
        }
        return n;
    }

    private static boolean whitelistedIp(SimpleYaml cfg, String ip) {
        return cfg.getBoolean("ochrona.whitelist-ip." + ip, false);
    }

    private static boolean whitelistedNick(SimpleYaml cfg, String nick) {
        if (nick == null || nick.isBlank()) return false;
        if (cfg.getBoolean("ochrona.whitelist-nick." + nick, false)) return true;
        for (Map.Entry<String, String> e : cfg.children("ochrona.whitelist-nick").entrySet()) {
            if (e.getKey().equalsIgnoreCase(nick) && "true".equalsIgnoreCase(e.getValue())) return true;
        }
        return false;
    }

    private static void deny(PreLoginEvent event, String msg) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(LEGACY.deserialize(msg.replace("\\n", "\n"))));
    }

    private static String ipOf(InetSocketAddress addr) {
        if (addr == null) return null;
        InetAddress a = addr.getAddress();
        return a == null ? null : a.getHostAddress();
    }
}
