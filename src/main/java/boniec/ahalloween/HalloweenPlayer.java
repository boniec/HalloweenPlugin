package boniec.ahalloween;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HalloweenPlayer {

    public final UUID uuid;
    public String nick;
    public int exp;
    public int poziom = 1;
    public int pojemnosc = 200;
    public final Map<String, Integer> cukierki = new HashMap<>();
    public final Map<String, Integer> ulepszenia = new HashMap<>();
    public final Map<String, Integer> sklep = new HashMap<>();
    public final Map<String, Integer> misjeProgress = new HashMap<>();
    public final Map<String, Boolean> misjeOdebrane = new HashMap<>();
    public final Map<String, String> misjeOkres = new HashMap<>();
    public int statCukierki;
    public int statMoby;
    public int statMisje;
    public int statBossy;
    public volatile boolean brudny;

    public HalloweenPlayer(UUID uuid, String nick) {
        this.uuid = uuid;
        this.nick = nick;
    }

    public int cukierek(String id) {
        return cukierki.getOrDefault(id, 0);
    }

    public int sumaCukierkow() {
        int s = 0;
        for (int v : cukierki.values()) s += v;
        return s;
    }

    public int ulepszenie(String id) {
        return ulepszenia.getOrDefault(id, 0);
    }

    public void oznacz() {
        brudny = true;
    }
}
