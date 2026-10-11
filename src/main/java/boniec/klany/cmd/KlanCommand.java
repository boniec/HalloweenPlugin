package boniec.klany.cmd;

import boniec.klany.Main;
import boniec.klany.core.Klan;
import boniec.klany.core.KlanManager;
import boniec.klany.core.Zaproszenie;
import boniec.klany.gui.SkarbiecGui;
import boniec.klany.gui.TopkiGui;
import boniec.klany.gui.UlepszeniaGui;
import boniec.klany.util.KlanNotify;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /klan <subkomenda> - jedna komenda, jak na screenie referencyjnym.
 */
public class KlanCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final KlanManager manager;

    private static final List<String> SUBKOMENDY = List.of(
            "stworz", "usun", "opusc", "dolacz", "wyrzuc", "zaproszenie", "akceptuj",
            "lider", "zastepca", "info", "ulepszenia", "skarbiec", "pvp", "topki", "pomoc"
    );

    public KlanCommand(Main plugin) {
        this.plugin = plugin;
        this.manager = plugin.getKlanManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player gracz)) {
            Msg.send(sender, "tylko-gracz");
            return true;
        }

        if (args.length == 0) {
            pomoc(gracz);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "stworz" -> stworz(gracz, args);
            case "usun" -> usun(gracz);
            case "opusc" -> opusc(gracz);
            case "dolacz" -> dolacz(gracz, args); // alias do "akceptuj" - patrz opis nizej
            case "akceptuj" -> akceptuj(gracz, args);
            case "wyrzuc" -> wyrzuc(gracz, args);
            case "zaproszenie", "zapros" -> zapros(gracz, args);
            case "lider" -> lider(gracz, args);
            case "zastepca" -> zastepca(gracz, args);
            case "info" -> info(gracz, args);
            case "ulepszenia", "ulepsz" -> ulepszenia(gracz);
            case "skarbiec" -> skarbiec(gracz);
            case "pvp" -> pvp(gracz);
            case "topki" -> topki(gracz);
            case "pomoc", "help" -> pomoc(gracz);
            default -> pomoc(gracz);
        }
        return true;
    }

    // ── Subkomendy ──────────────────────────────────────────────────────────

    private void stworz(Player gracz, String[] args) {
        if (manager.getKlanGracza(gracz.getUniqueId()) != null) {
            Klan aktualny = manager.getKlanGracza(gracz.getUniqueId());
            Msg.send(gracz, "juz-w-klanie", "%klan%", aktualny.getNazwa());
            return;
        }
        if (args.length < 2) {
            Msg.sendGui(gracz, "&cUzycie: /klan stworz <tag>");
            return;
        }
        String tag = args[1];
        // Nazwa klanu = tag (wielkimi literami) - gracz podaje tylko tag.
        String nazwa = tag.toUpperCase(Locale.ROOT);

        int minTag = plugin.getConfig().getInt("ustawienia.min-dlugosc-tagu", 2);
        int maxTag = plugin.getConfig().getInt("ustawienia.max-dlugosc-tagu", 6);
        if (tag.length() < minTag || tag.length() > maxTag) {
            Msg.send(gracz, "zla-dlugosc-tagu", "%min%", minTag, "%max%", maxTag);
            return;
        }
        if (manager.istniejeTag(tag)) {
            Msg.send(gracz, "tag-zajety", "%tag%", tag);
            return;
        }
        if (manager.istniejeNazwa(nazwa)) {
            Msg.send(gracz, "nazwa-zajeta", "%nazwa%", nazwa);
            return;
        }

        int koszt = plugin.getConfig().getInt("ustawienia.koszt-zalozenia-odlamki", 10);
        Material waluta = Material.matchMaterial(plugin.getConfig().getString("ustawienia.waluta-material", "DRAGON_BREATH"));
        if (koszt > 0) {
            ItemStack wRece = gracz.getInventory().getItemInMainHand();
            if (wRece.getType() != waluta || wRece.getAmount() < koszt) {
                Msg.send(gracz, "niewystarczajace-srodki", "%ilosc%", koszt, "%material%", waluta.name());
                return;
            }
            wRece.setAmount(wRece.getAmount() - koszt);
        }

        Klan klan = manager.stworzKlan(nazwa, tag.toUpperCase(Locale.ROOT), gracz.getUniqueId());
        if (klan == null) {
            Msg.sendGui(gracz, "&cWystapil blad przy tworzeniu klanu. Sprobuj ponownie.");
            return;
        }
        manager.dodajLog(klan, gracz.getName() + " zalozyl klan.");
        KlanNotify.broadcast("utworzenie",
                "%gracz%", gracz.getName(), "%klan%", nazwa, "%tag%", tag.toUpperCase(Locale.ROOT));
    }

    private void usun(Player gracz) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        if (!klan.getLider().equals(gracz.getUniqueId())) {
            Msg.send(gracz, "brak-uprawnien");
            return;
        }
        String nazwa = klan.getNazwa();
        String tag = klan.getTag();
        manager.usunKlan(klan);
        KlanNotify.broadcast("usuniecie",
                "%gracz%", gracz.getName(), "%klan%", nazwa, "%tag%", tag);
    }

    private void opusc(Player gracz) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        if (klan.getLider().equals(gracz.getUniqueId())) {
            Msg.send(gracz, "lider-nie-moze-opuscic");
            return;
        }
        manager.dodajLog(klan, gracz.getName() + " opuscil klan.");
        manager.usunCzlonka(klan, gracz.getUniqueId());
        Msg.send(gracz, "opuszczono-klan", "%klan%", klan.getNazwa());
        KlanNotify.broadcast("opuszczenie",
                "%gracz%", gracz.getName(), "%klan%", klan.getNazwa(), "%tag%", klan.getTag());
    }

    /** /klan dolacz <tag> - alias na akceptowanie zaproszenia (spojne ze screenem). */
    private void dolacz(Player gracz, String[] args) {
        akceptuj(gracz, args);
    }

    private void akceptuj(Player gracz, String[] args) {
        if (manager.getKlanGracza(gracz.getUniqueId()) != null) {
            Msg.send(gracz, "juz-w-klanie", "%klan%", manager.getKlanGracza(gracz.getUniqueId()).getNazwa());
            return;
        }
        if (args.length < 2) {
            Msg.sendGui(gracz, "&cUzycie: /klan dolacz <tag>");
            return;
        }
        String tag = args[1];
        Klan klan = manager.getKlanByTag(tag);
        if (klan == null) {
            Msg.send(gracz, "klan-nie-istnieje", "%tag%", tag);
            return;
        }
        Zaproszenie zaproszenie = manager.pobierzZaproszenie(gracz.getUniqueId(), tag);
        if (zaproszenie == null) {
            Msg.send(gracz, "brak-zaproszenia");
            return;
        }
        if (zaproszenie.isWygasle()) {
            manager.usunZaproszenia(gracz.getUniqueId(), klan.getId());
            Msg.send(gracz, "zaproszenie-wygaslo");
            return;
        }
        int aktualnyLimit = manager.getLimitCzlonkow(klan);
        if (klan.getLiczbaCzlonkow() >= aktualnyLimit) {
            Msg.sendGui(gracz, "&cTen klan osiagnal maksymalna liczbe czlonkow (" + aktualnyLimit + ").");
            return;
        }

        manager.usunZaproszenia(gracz.getUniqueId(), klan.getId());
        manager.dodajCzlonka(klan, gracz.getUniqueId(), Klan.Ranga.CZLONEK);
        manager.dodajLog(klan, gracz.getName() + " dolaczyl do klanu.");
        Msg.send(gracz, "dolaczono", "%klan%", klan.getNazwa());
        KlanNotify.broadcast("dolaczenie",
                "%gracz%", gracz.getName(), "%klan%", klan.getNazwa(), "%tag%", klan.getTag());
        // Title/subtitle tylko dla reszty klanu (nowa osoba dostala juz zwykla wiadomosc "dolaczono").
        KlanNotify.titleDoKlanuOprocz("nowy-czlonek", klan, gracz.getUniqueId(),
                "%gracz%", gracz.getName(), "%klan%", klan.getNazwa(), "%tag%", klan.getTag());
    }

    private void wyrzuc(Player gracz, String[] args) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        if (!klan.maUprawnieniaZarzadzania(gracz.getUniqueId())) {
            Msg.send(gracz, "brak-uprawnien");
            return;
        }
        if (args.length < 2) {
            Msg.sendGui(gracz, "&cUzycie: /klan wyrzuc <nick>");
            return;
        }
        OfflinePlayer cel = manager.znajdzCzlonka(klan, args[1]);
        if (cel == null) {
            Msg.send(gracz, "gracz-nie-w-klanie");
            return;
        }
        manager.wyrzucCzlonka(gracz, klan, cel);
    }

    private void zapros(Player gracz, String[] args) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        if (!klan.maUprawnieniaZarzadzania(gracz.getUniqueId())) {
            Msg.send(gracz, "brak-uprawnien");
            return;
        }
        if (args.length < 2) {
            Msg.sendGui(gracz, "&cUzycie: /klan zaproszenie <nick>");
            return;
        }
        OfflinePlayer cel = Bukkit.getOfflinePlayer(args[1]);
        if (!cel.hasPlayedBefore() && !cel.isOnline()) {
            Msg.send(gracz, "gracz-offline");
            return;
        }
        if (manager.getKlanGracza(cel.getUniqueId()) != null) {
            Msg.sendGui(gracz, "&cTen gracz jest juz w klanie.");
            return;
        }
        if (manager.maZaproszenieOd(cel.getUniqueId(), klan.getTag())) {
            Msg.send(gracz, "gracz-juz-zaproszony");
            return;
        }
        int aktualnyLimit = manager.getLimitCzlonkow(klan);
        if (klan.getLiczbaCzlonkow() >= aktualnyLimit) {
            Msg.sendGui(gracz, "&cTwoj klan osiagnal maksymalna liczbe czlonkow (" + aktualnyLimit + ").");
            return;
        }

        manager.dodajZaproszenie(cel.getUniqueId(), new Zaproszenie(klan.getId(), klan.getTag(), gracz.getUniqueId()));
        Msg.send(gracz, "zaproszono", "%gracz%", cel.getName());
        if (cel.isOnline()) {
            Player celOnline = (Player) cel;
            Msg.send(celOnline, "otrzymano-zaproszenie",
                    "%nadawca%", gracz.getName(), "%klan%", klan.getNazwa(), "%tag%", klan.getTag());
            KlanNotify.dzwiek("otrzymano-zaproszenie", celOnline);
        }
    }

    private void lider(Player gracz, String[] args) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        if (!klan.getLider().equals(gracz.getUniqueId())) {
            Msg.send(gracz, "brak-uprawnien");
            return;
        }
        if (args.length < 2) {
            Msg.sendGui(gracz, "&cUzycie: /klan lider <nick>");
            return;
        }
        OfflinePlayer cel = manager.znajdzCzlonka(klan, args[1]);
        if (cel == null) {
            Msg.send(gracz, "gracz-nie-w-klanie");
            return;
        }
        if (cel.getUniqueId().equals(gracz.getUniqueId())) {
            Msg.send(gracz, "nie-mozna-siebie");
            return;
        }
        manager.ustawLidera(klan, cel.getUniqueId());
        manager.dodajLog(klan, gracz.getName() + " przekazal przywodztwo " + cel.getName() + ".");
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> klan.isCzlonek(p.getUniqueId()))
                .forEach(p -> Msg.send(p, "nowy-lider", "%gracz%", cel.getName()));
    }

    private void zastepca(Player gracz, String[] args) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        if (!klan.getLider().equals(gracz.getUniqueId())) {
            Msg.send(gracz, "brak-uprawnien");
            return;
        }
        if (args.length < 2) {
            Msg.sendGui(gracz, "&cUzycie: /klan zastepca <nick>");
            return;
        }
        OfflinePlayer cel = manager.znajdzCzlonka(klan, args[1]);
        if (cel == null) {
            Msg.send(gracz, "gracz-nie-w-klanie");
            return;
        }
        Klan.Ranga aktualna = klan.getRanga(cel.getUniqueId());
        Klan.Ranga nowa = aktualna == Klan.Ranga.ZASTEPCA ? Klan.Ranga.CZLONEK : Klan.Ranga.ZASTEPCA;
        manager.ustawRange(klan, cel.getUniqueId(), nowa);
        manager.dodajLog(klan, gracz.getName() + " zmienil range " + cel.getName() + " na " + nowa.name() + ".");
        Msg.send(gracz, nowa == Klan.Ranga.ZASTEPCA ? "nowy-zastepca" : "odebrano-zastepce", "%gracz%", cel.getName());
    }

    private void info(Player gracz, String[] args) {
        Klan klan;
        if (args.length >= 2) {
            klan = manager.getKlanByTag(args[1]);
            if (klan == null) {
                Msg.send(gracz, "klan-nie-istnieje", "%tag%", args[1]);
                return;
            }
        } else {
            klan = wymagajKlanu(gracz);
            if (klan == null) return;
        }
        gracz.sendMessage("");
        gracz.sendMessage(Msg.color("&#F5EA50☀ &#F5EA50&lɪɴꜰᴏʀᴍᴀᴄᴊᴇ ᴏ ᴋʟᴀɴɪᴇ &#F5EA50☀"));
        gracz.sendMessage("");

        gracz.sendMessage(Msg.color("&#B7F550☂ &8⋮⋮ &fNazwa: &8[&#B7F550" + klan.getTag() + "&8]"));
        gracz.sendMessage(Msg.color("&#71F3B4☁ &8⋮⋮ &fPunkty: &8[&#71F3B4" + klan.getSkarbiec() + "&8]"));
        gracz.sendMessage(Msg.color("&#A771F3★ &8⋮⋮ &fLider: &#A771F3" + Bukkit.getOfflinePlayer(klan.getLider()).getName()));

        List<String> zastepcy = klan.getCzlonkowie().entrySet().stream()
                .filter(e -> e.getValue() == Klan.Ranga.ZASTEPCA)
                .map(e -> Bukkit.getOfflinePlayer(e.getKey()).getName())
                .filter(n -> n != null)
                .collect(Collectors.toList());
        String zastepcyTekst = zastepcy.isEmpty() ? "&8brak" : String.join(", ", zastepcy);
        gracz.sendMessage(Msg.color("&#F1935D✎ &8⋮⋮ &fZastepcy: &#F1935D" + zastepcyTekst));

        List<String> online = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        for (Map.Entry<UUID, Klan.Ranga> e : klan.getCzlonkowie().entrySet()) {
            String nick = Bukkit.getOfflinePlayer(e.getKey()).getName();
            if (nick == null) continue;
            if (Bukkit.getOfflinePlayer(e.getKey()).isOnline()) {
                online.add(nick);
            } else {
                offline.add(nick);
            }
        }
        String onlineStr = online.isEmpty() ? "&2brak" : String.join(", ", online);
        String offlineStr = offline.isEmpty() ? "" : String.join(", ", offline);
        int ileOnline = online.size();
        int ileCzlonkow = klan.getLiczbaCzlonkow();
        int limit = manager.getLimitCzlonkow(klan);
        gracz.sendMessage(Msg.color("&#F5E350✉ &8⋮⋮ &fCzlonkowie: &8(&2" + ileCzlonkow + "&8/&2" + limit + "&8) &7online: &a" + ileOnline + " &#F33C5A" + offlineStr + " &a" + onlineStr));

        gracz.sendMessage("");
    }

    private void ulepszenia(Player gracz) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        new UlepszeniaGui(klan).otworz(gracz);
    }

    private void skarbiec(Player gracz) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        new SkarbiecGui(klan).otworz(gracz);
    }

    private void pvp(Player gracz) {
        Klan klan = wymagajKlanu(gracz);
        if (klan == null) return;
        if (!klan.maUprawnieniaZarzadzania(gracz.getUniqueId())) {
            Msg.send(gracz, "brak-uprawnien");
            return;
        }
        boolean nowyStan = !klan.isPvpWlaczone();
        manager.ustawPvp(klan, nowyStan);
        Msg.send(gracz, nowyStan ? "pvp-wlaczone" : "pvp-wylaczone");
    }

    private void topki(Player gracz) {
        new TopkiGui().otworz(gracz);
    }

    /**
     * Wysyla blok pomocy zdefiniowany w klany-config.yml pod "pomoc.linie" (lista linii,
     * dokladnie tak jak np. "broadcasty.utworzenie.linie") - kazda linia to osobna
     * wiadomosc na czacie, w kolejnosci z configu. Caly wyglad (gradienty, kolory,
     * tresc) jest w 100% edytowalny z poziomu configu, bez zmian w kodzie.
     */
    private void pomoc(Player gracz) {
        FileConfiguration cfg = plugin.getConfig();
        List<String> linie = cfg.getStringList("pomoc.linie");
        for (String linia : linie) {
            gracz.sendMessage(Msg.color(linia));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Klan wymagajKlanu(Player gracz) {
        Klan klan = manager.getKlanGracza(gracz.getUniqueId());
        if (klan == null) {
            Msg.send(gracz, "brak-klanu");
        }
        return klan;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String wpis = args[0].toLowerCase(Locale.ROOT);
            return SUBKOMENDY.stream().filter(s -> s.startsWith(wpis)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("dolacz", "akceptuj").contains(sub)) {
                return manager.getWszystkieKlany().stream()
                        .map(Klan::getTag)
                        .filter(t -> t.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
            if (List.of("wyrzuc", "lider", "zastepca").contains(sub)) {
                if (!(sender instanceof Player p)) return List.of();
                Klan klan = manager.getKlanGracza(p.getUniqueId());
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return manager.nickiCzlonkow(klan).stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .collect(Collectors.toList());
            }
            if (List.of("zaproszenie", "zapros").contains(sub)) {
                List<String> nicki = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(pl -> nicki.add(pl.getName()));
                return nicki.stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
            if (sub.equals("info")) {
                return manager.getWszystkieKlany().stream()
                        .map(Klan::getTag)
                        .filter(t -> t.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
