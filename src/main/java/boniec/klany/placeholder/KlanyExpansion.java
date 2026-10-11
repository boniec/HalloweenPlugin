package boniec.klany.placeholder;

import boniec.klany.Main;
import boniec.klany.core.Klan;
import boniec.klany.util.Msg;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Rejestruje placeholdery:
 * %klany_tag% - tag klanu gracza
 * %klany_nazwa% - pelna nazwa klanu gracza
 * %klany_ranga% - ranga gracza w klanie
 * %klany_skarbiec% - zawartosc skarbca klanu
 * %klany_czlonkowie% - liczba czlonkow klanu
 * %klany_pvp% - status pvp klanu
 * %klany_zabojstwa% - zabojstwa PvP gracza
 * %klany_smierci% - smierci PvP gracza
 * %klany_kd% - stosunek zabojstwa/smierci
 * %klany_ranking% - ranking ELO gracza (domyslnie 1000)
 * %klany_tier% - SAMA LICZBA tierow gracza (domyslnie 0); tier dostaje sie po osiagnieciu 2000
 *   rankingu, wtedy ranking wraca do 1000. Alias: %klany_gracz_tier%
 * %klany_zabojstwa_klanu% - suma zabojstw klanu
 * %klany_rel_klan_tag% - tag klanu z kolorem (zielony jesli ma klan) - wersja BEZ
 *   drugiego gracza (np. w chacie), nie porownuje klanow miedzy soba.
 *
 * %klany_tag_hex% - tag klanu z hexem &#E28217, np. "&#E28217TAG"
 * %klany_lider_hex% - nick lidera klanu z hexem &#E28217, np. "&#E28217Boniec"
 * %klany_online_hex% - graczy online / wszystkich czlonkow klanu, np. "&#E282173&8/&716"
 * %klany_ranking_hex% - suma rankingu ELO klanu z hexem &#E28217
 * %klany_pozycja_klanu_hex% - pozycja klanu w rankingu klanow z hexem &#E28217, np. "&#E28217#85"
 * %klany_ekonomia_formatted% - suma prawdziwej kasy (Vault/Economy) wszystkich czlonkow
 *   klanu, sformatowana skrotowo: "483.33$", "43.3k", "14.2M"
 * %klany_pozycja% - pozycja GRACZA w globalnym topie ELO, np. "#644"
 *
 * %klany_top_ekonomia_<N>_nick% - TOP 20 graczy wg REALNEJ kasy (Vault/Economy, np.
 *   EssentialsX), np. %klany_top_ekonomia_1_nick%
 * %klany_top_ekonomia_<N>_kasa% - surowa kwota gracza na danej pozycji, np. "48333.33"
 * %klany_top_ekonomia_<N>_kasa_formatted% - kwota sformatowana skrotowo: "483.33$",
 *   "43.3k", "14.2M". UWAGA: to NIE jest to samo co %klany_top_kasa_<N>_...% (skarbiec
 *   KLANU) - ten placeholder to prywatna kasa GRACZA.
 *
 * %klany_gracz_ranking% - ranking ELO gracza (alias, tozsamy z %klany_ranking%)
 * %klany_gracz_pozycja% - pozycja gracza w globalnym topie ELO, np. "#644"
 * %klany_gracz_zabojstwa% - zabojstwa PvP gracza
 * %klany_gracz_smierci% - smierci PvP gracza
 * %klany_gracz_kd% - stosunek zabojstwa/smierci gracza, np. "1.50"
 * UWAGA: wszystkie placeholdery zaczynaja sie od identyfikatora "klany_" (wymog PAPI) -
 *   samo "%gracz_ranking%" (bez "klany_") NIE zadziala.
 *
 * %rel_klany_rel_klan_tag% - PRAWDZIWY placeholder RELACYJNY (wymaga PAPI + pluginu TAB,
 *   jako %rel_klany_rel_klan_tag% wpisany w nametag-suffix w TAB'owym config.yml).
 *   W odroznieniu od %klany_rel_klan_tag% powyzej, ten zna OBU graczy (tego kto patrzy
 *   i tego kto jest wyswietlany), wiec realnie porownuje ich klany:
 *    - &#8FE5A3ZIELONY tag "[123]", jesli obaj gracze sa razem w tym samym klanie
 *    - &cCZERWONY tag "[123]", jesli wyswietlany gracz ma inny klan niz patrzacy
 *      (albo patrzacy nie ma klanu wcale)
 *    - &cCZERWONE "[Bez klanu]", jesli wyswietlany gracz w ogole nie nalezy do klanu
 */
public class KlanyExpansion extends PlaceholderExpansion implements Relational {

    /** Hex uzywany we wszystkich nowych placeholderach "_hex" (tag/lider/online/ranking/pozycja klanu). */
    private static final String HEX = "&#E28217";

    private final Main plugin;
    private Economy economy;

    /** Cache TOP 20 graczy wg realnej kasy (Vault/Economy) - patrz getTopEkonomia(). */
    private java.util.List<UUID> topEkonomiaCache = java.util.Collections.emptyList();
    private long topEkonomiaCacheCzas = 0L;
    private static final long TOP_EKONOMIA_CACHE_MS = 5 * 60 * 1000; // 5 minut

    public KlanyExpansion(Main plugin) {
        this.plugin = plugin;
    }

    private void setupEconomy() {
        if (economy != null && economy.isEnabled()) return;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
    }

    private boolean hasEconomy() {
        if (economy == null || !economy.isEnabled()) setupEconomy();
        return economy != null && economy.isEnabled();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "klany";
    }

    @Override
    public @NotNull String getAuthor() {
        return "boniec";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.1";
    }

    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    // ── Placeholders ──────────────────────────────────────────────────────

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        if (params.equals("rel_klan_tag")) {
            return getRelKlanTag(player);
        }

        if (params.equals("aura")) {
            return "";
        }

        if (params.equals("tag_hex")) {
            return getTagHex(player);
        }
        if (params.equals("lider_hex")) {
            return getLiderHex(player);
        }
        if (params.equals("online_hex")) {
            return getOnlineHex(player);
        }
        if (params.equals("ranking_hex")) {
            return getRankingHex(player);
        }
        if (params.equals("pozycja_klanu_hex")) {
            return getPozycjaKlanuHex(player);
        }
        if (params.equals("ekonomia_formatted")) {
            return getEkonomiaKlanuFormatted(player);
        }

        // --- Tab placeholdery: %klany_tab_1%, %klany_tab_2% ---
        if (params.equals("tab_1")) {
            return handleTab1(player);
        }
        if (params.equals("tab_2")) {
            return handleTab2(player);
        }
        if (params.equals("score_1")) {
            return handleScore1(player);
        }
        if (params.equals("score_2")) {
            return handleScore2(player);
        }

        // --- Info placeholdery: %klany_info_gracz%, %klany_info_tag% ---
        if (params.equals("info_gracz")) {
            return handleInfoGracz(player);
        }
        if (params.equals("info_tag")) {
            return handleInfoTag(player);
        }

        // --- Top klanow po ELO: %klany_top_<N>_<pole>% ---
        if (params.startsWith("top_kasa_")) {
            return handleTopKlanowKasa(params);
        }
        if (params.startsWith("top_zabojstwa_")) {
            return handleTopKlanowZabojstwa(params);
        }
        // --- Top graczy po REALNEJ kasie (Vault/Economy): %klany_top_ekonomia_<N>_<pole>% ---
        if (params.startsWith("top_ekonomia_")) {
            return handleTopEkonomia(params);
        }
        if (params.startsWith("top_")) {
            return handleTopKlanow(params);
        }

        // --- Gracz placeholdery: %klany_gracz_ranking%, %klany_gracz_pozycja%,
        //     %klany_gracz_zabojstwa%, %klany_gracz_smierci%, %klany_gracz_kd% ---
        if (params.equals("gracz_ranking")) {
            return handleGraczRanking(player);
        }
        if (params.equals("gracz_tier")) {
            return String.valueOf(plugin.getKlanManager().getTierGracza(player.getUniqueId()));
        }
        if (params.equals("gracz_pozycja")) {
            return handleGraczPozycja(player);
        }
        if (params.equals("gracz_zabojstwa")) {
            return handleGraczZabojstwa(player);
        }
        if (params.equals("gracz_zabojstwa_pozycja")) {
            return handleGraczZabojstwaPozycja(player);
        }
        if (params.equals("gracz_smierci")) {
            return handleGraczSmierci(player);
        }
        if (params.equals("gracz_smierci_pozycja")) {
            return handleGraczSmierciPozycja(player);
        }
        if (params.equals("gracz_kd")) {
            return handleGraczKd(player);
        }

        // --- Top graczy po smierciach: %klany_gracz_smierci_<N>_<pole>% ---
        if (params.startsWith("gracz_smierci_")) {
            return handleTopGraczySmierci(params);
        }

        // --- Top graczy po zabojstwach: %klany_gracz_zabojstwa_<N>_<pole>% ---
        if (params.startsWith("gracz_zabojstwa_")) {
            return handleTopGraczyZabojstwa(params);
        }

        // --- Top graczy po ELO: %klany_gracz_<N>_<pole>% ---
        if (params.startsWith("gracz_")) {
            return handleTopGraczy(params);
        }

        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());

        // Statystyki gracza (zabojstwa/smierci/kd/ranking/pozycja) sa NIEZALEZNE od
        // przynaleznosci do klanu - dzialaja identycznie z klanem i bez klanu, wiec
        // sprawdzamy je PRZED rozgalezieniem "klan == null", zamiast na sztywno
        // zwracac "0"/domyslna wartosc dla gracza bez klanu.
        int zabojstwaGracza = plugin.getKlanManager().getZabojstwaGracza(player.getUniqueId());
        int smierciGracza = plugin.getKlanManager().getSmierciGracza(player.getUniqueId());
        int rankingGracza = plugin.getKlanManager().getRankingGracza(player.getUniqueId());
        switch (params) {
            case "zabojstwa": return String.valueOf(zabojstwaGracza);
            case "smierci": return String.valueOf(smierciGracza);
            case "kd": return String.format(java.util.Locale.ROOT, "%.2f", Klan.kd(zabojstwaGracza, smierciGracza));
            case "ranking": return String.valueOf(rankingGracza);
            case "tier": return String.valueOf(plugin.getKlanManager().getTierGracza(player.getUniqueId()));
            case "pozycja": {
                int poz = plugin.getKlanManager().getPozycjaWTopie(player.getUniqueId());
                return poz > 0 ? "#" + poz : "";
            }
            default: break;
        }

        if (klan == null) {
            return switch (params) {
                case "tag", "nazwa", "ranga", "info_gracz", "info_tag", "info_lider" -> "";
                case "skarbiec", "czlonkowie", "zabojstwa_klanu", "info_online", "info_czlonkowie", "info_ranking_klanu" -> "0";
                case "info_pozycja_klanu" -> "";
                case "pvp" -> "";
                default -> null;
            };
        }
        return switch (params) {
            case "tag" -> klan.getTag();
            case "nazwa" -> klan.getNazwa();
            case "ranga" -> {
                Klan.Ranga ranga = klan.getRanga(player.getUniqueId());
                yield ranga == null ? "" : ranga.getEtykieta().replaceAll("&[0-9a-fk-or]", "");
            }
            case "skarbiec" -> String.valueOf(klan.getSkarbiec());
            case "czlonkowie" -> String.valueOf(klan.getLiczbaCzlonkow());
            case "info_lider" -> {
                String nick = org.bukkit.Bukkit.getOfflinePlayer(klan.getLider()).getName();
                yield nick != null ? nick : "";
            }
            case "info_online" -> {
                int online = 0;
                for (UUID uuid : klan.getCzlonkowie().keySet()) {
                    if (org.bukkit.Bukkit.getOfflinePlayer(uuid).isOnline()) online++;
                }
                yield String.valueOf(online);
            }
            case "info_czlonkowie" -> String.valueOf(klan.getLiczbaCzlonkow());
            case "info_ranking_klanu" -> String.valueOf(plugin.getKlanManager().getSumaRankinguKlanu(klan));
            case "info_pozycja_klanu" -> {
                java.util.List<Klan> top = plugin.getKlanManager().getTopKlanowPoRankingu(21);
                int poz = -1;
                for (int i = 0; i < top.size(); i++) {
                    if (top.get(i).getId() == klan.getId()) { poz = i + 1; break; }
                }
                yield poz > 0 ? "#" + poz : "";
            }
            case "pvp" -> klan.isPvpWlaczone() ? "Wlaczone" : "Wylaczone";
            case "zabojstwa_klanu" -> String.valueOf(plugin.getKlanManager().getSumaZabojstwKlanu(klan));
            default -> null;
        };
    }

    // ── Nowe placeholdery "_hex" (tag/lider/online/ranking/pozycja klanu) ──

    /** %klany_tag_hex% - "&#E28217TAG" */
    private String getTagHex(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) return "";
        return Msg.color(HEX + klan.getTag());
    }

    /** %klany_lider_hex% - "&#E28217NickLidera" */
    private String getLiderHex(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) return "";
        String nick = Bukkit.getOfflinePlayer(klan.getLider()).getName();
        if (nick == null) nick = "";
        return Msg.color(HEX + nick);
    }

    /** %klany_online_hex% - "&#E28217ONLINE&8/&7WSZYSCY", np. "&#E282173&8/&716" */
    private String getOnlineHex(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) return "";
        int online = 0;
        for (UUID uuid : klan.getCzlonkowie().keySet()) {
            if (Bukkit.getOfflinePlayer(uuid).isOnline()) online++;
        }
        int wszyscy = klan.getLiczbaCzlonkow();
        return Msg.color(HEX + online + "&8/&7" + wszyscy);
    }

    /** %klany_ranking_hex% - "&#E28217RANKING" (suma ELO calego klanu) */
    private String getRankingHex(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) return "";
        return Msg.color(HEX + plugin.getKlanManager().getSumaRankinguKlanu(klan));
    }

    /** %klany_pozycja_klanu_hex% - "&#E28217#85" (pozycja klanu w topie klanow po sumie ELO) */
    private String getPozycjaKlanuHex(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) return "";
        java.util.List<Klan> top = plugin.getKlanManager().getTopKlanowPoRankingu(21);
        int pozycja = -1;
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).getId() == klan.getId()) { pozycja = i + 1; break; }
        }
        if (pozycja < 0) return "";
        return Msg.color(HEX + "#" + pozycja);
    }

    /**
     * %klany_ekonomia_formatted% - suma PRAWDZIWEJ kasy (Vault/Economy, np. EssentialsX)
     * wszystkich czlonkow klanu (online i offline), sformatowana skrotowo:
     * ponizej 1000 -> "483.33$" (z groszami), od 1000 -> "43.3k", od 1_000_000 -> "14.2M".
     * Jesli Vault/plugin ekonomii nie jest podpiety, zwraca "0$".
     */
    private String getEkonomiaKlanuFormatted(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) return "0$";
        if (!hasEconomy()) return "0$";

        double suma = 0;
        for (UUID uuid : klan.getCzlonkowie().keySet()) {
            suma += economy.getBalance(Bukkit.getOfflinePlayer(uuid));
        }
        return formatujEkonomicznie(suma);
    }

    // ── Info placeholdery ──────────────────────────────────────────────────

    /** Formatuje kwote np. 483.333 -> "483.33$", 48333 -> "48.3k", 14200000 -> "14.2M" */
    private String formatujEkonomicznie(double wartosc) {
        if (wartosc >= 1_000_000) {
            double m = wartosc / 1_000_000.0;
            return String.format(java.util.Locale.ROOT, "%.1fM", m).replace(".0M", "M");
        } else if (wartosc >= 1_000) {
            double k = wartosc / 1_000.0;
            return String.format(java.util.Locale.ROOT, "%.1fk", k).replace(".0k", "k");
        } else {
            return String.format(java.util.Locale.ROOT, "%.2f$", wartosc);
        }
    }

    /**
     * %klany_info_gracz% - formatuje linijke: "&#BF7C23#5. &fNick &8(&franking&8)"
     * Pozycja gracza w globalnym topie ELO.
     */
    private String handleInfoGracz(OfflinePlayer player) {
        // Pozycja/ranking gracza sa niezalezne od przynaleznosci do klanu - patrz
        // KlanManager.getPozycjaWTopie()/getRankingGracza().
        int pozycja = plugin.getKlanManager().getPozycjaWTopie(player.getUniqueId());
        if (pozycja < 0) return "";
        int ranking = plugin.getKlanManager().getRankingGracza(player.getUniqueId());
        String nick = player.getName();
        if (nick == null) nick = "";
        return Msg.color("&7➥ &#BF7C23#" + pozycja + ". &f" + nick + " &8(&f" + ranking + "&8)");
    }

    /**
     * %klany_info_tag% - formatuje linijke: "&#F69EED#3. &fTAG &8(&franking&8)"
     * Pozycja klanu w topie klanow (po sumie ELO).
     */
    private String handleInfoTag(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) return "";
        // Znajdz pozycje klanu w topie
        java.util.List<Klan> top = plugin.getKlanManager().getTopKlanowPoRankingu(21);
        int pozycja = -1;
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).getId() == klan.getId()) {
                pozycja = i + 1;
                break;
            }
        }
        if (pozycja < 0) return "";
        int ranking = plugin.getKlanManager().getSumaRankinguKlanu(klan);
        return Msg.color("&7➥ &#F69EED#" + pozycja + ". &f" + klan.getTag() + " &8(&f" + ranking + "&8)");
    }

    /**
     * %klany_tab_1% - "&fTwój Klan: &8[&2TAG&8]" lub "&cNie posiadasz klanu &4/klan stworz <tag>"
     */
    private String handleTab1(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) {
            return Msg.color("&cNie posiadasz klanu");
        }
        return Msg.color("&fTwój Klan: &8[&2" + klan.getTag() + "&8]");
    }

    /**
     * %klany_tab_2% - "&fRanking: &#E28217RANKING &8(&f&#E28217#POZYCJA&8)" lub ""
     */
    private String handleTab2(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) {
            return Msg.color("&cUzyj &4/klan stworz <tag>");
        }

        java.util.List<Klan> top = plugin.getKlanManager().getTopKlanowPoRankingu(21);
        int pozycja = -1;
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).getId() == klan.getId()) {
                pozycja = i + 1;
                break;
            }
        }

        String pozycjaHex = pozycja > 0 ? Msg.color("&#E28217#" + pozycja) : "";
        String rankingHex = Msg.color("&#E28217" + plugin.getKlanManager().getSumaRankinguKlanu(klan));

        return Msg.color("&fRanking: " + rankingHex + " &8(&f" + pozycjaHex + "&8)");
    }

    private String handleScore1(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) {
            return Msg.color("&cStworz swoj klan");
        }
        return Msg.color("&fKlan: &8[&a" + klan.getTag() + "&8]");
    }

    private String handleScore2(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) {
            return Msg.color("&cUzywajac &4/klan stworz");
        }
        return handleTab2(player);
    }

    // ── Top klanow: %klany_top_<N>_<pole>% ─────────────────────────────────

    /**
     * Obsluguje placeholdery %klany_top_1_tag%, %klany_top_3_nazwa% itd.
     * Dostepne pola: tag, nazwa, lider, skarbiec, czlonkowie, ranking
     */
    private String handleTopKlanow(String params) {
        // format: top_<N>_<pole>
        String[] parts = params.split("_", 3);
        if (parts.length < 3) return null;
        int pozycja;
        try {
            pozycja = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        String pole = parts[2];
        Klan klan = plugin.getKlanManager().getKlanNaPozycji(pozycja);
        if (klan == null) return "";
        return switch (pole) {
            case "tag" -> klan.getTag();
            case "nazwa" -> klan.getNazwa();
            case "lider" -> {
                String nick = org.bukkit.Bukkit.getOfflinePlayer(klan.getLider()).getName();
                yield nick != null ? nick : "";
            }
            case "skarbiec" -> String.valueOf(klan.getSkarbiec());
            case "czlonkowie" -> String.valueOf(klan.getLiczbaCzlonkow());
            case "ranking" -> String.valueOf(plugin.getKlanManager().getSumaRankinguKlanu(klan));
            case "zabojstwa" -> String.valueOf(plugin.getKlanManager().getSumaZabojstwKlanu(klan));
            default -> null;
        };
    }

    /**
     * Obsluguje %klany_top_kasa_<N>_<pole>% - jak handleTopKlanow, ale POSORTOWANE po
     * skarbcu klanu (kasie), a nie po ELO. Uzywane do "topki kasy".
     */
    private String handleTopKlanowKasa(String params) {
        // format: top_kasa_<N>_<pole>
        String[] parts = params.split("_", 4);
        if (parts.length < 4) return null;
        int pozycja;
        try {
            pozycja = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        String pole = parts[3];
        Klan klan = plugin.getKlanManager().getKlanNaPozycjiKasy(pozycja);
        if (klan == null) return "";
        return switch (pole) {
            case "tag" -> klan.getTag();
            case "nazwa" -> klan.getNazwa();
            case "lider" -> {
                String nick = org.bukkit.Bukkit.getOfflinePlayer(klan.getLider()).getName();
                yield nick != null ? nick : "";
            }
            case "skarbiec" -> String.valueOf(klan.getSkarbiec());
            case "czlonkowie" -> String.valueOf(klan.getLiczbaCzlonkow());
            case "ranking" -> String.valueOf(plugin.getKlanManager().getSumaRankinguKlanu(klan));
            case "zabojstwa" -> String.valueOf(plugin.getKlanManager().getSumaZabojstwKlanu(klan));
            default -> null;
        };
    }

    /**
     * Zwraca TOP 20 graczy (UUID) posortowanych malejaco wg realnej kasy z Vault/Economy
     * (np. EssentialsX) - a NIE wg skarbca klanu. Obejmuje graczy online i offline
     * (Bukkit.getOfflinePlayers() - wszyscy, ktorzy kiedykolwiek weszli na serwer).
     *
     * Wynik jest cache'owany na 5 minut (TOP_EKONOMIA_CACHE_MS), zeby nie przeliczac
     * balansu WSZYSTKICH graczy przy kazdym odswiezeniu menu (moze byc kosztowne,
     * jesli na serwerze zagralo duzo osob).
     */
    private java.util.List<UUID> getTopEkonomia() {
        long teraz = System.currentTimeMillis();
        if (!topEkonomiaCache.isEmpty() && teraz - topEkonomiaCacheCzas < TOP_EKONOMIA_CACHE_MS) {
            return topEkonomiaCache;
        }
        if (!hasEconomy()) return java.util.Collections.emptyList();

        OfflinePlayer[] wszyscy = Bukkit.getOfflinePlayers();
        java.util.List<OfflinePlayer> posortowani = new java.util.ArrayList<>(java.util.Arrays.asList(wszyscy));
        posortowani.sort((a, b) -> Double.compare(economy.getBalance(b), economy.getBalance(a)));

        java.util.List<UUID> top20 = new java.util.ArrayList<>();
        for (int i = 0; i < posortowani.size() && i < 20; i++) {
            top20.add(posortowani.get(i).getUniqueId());
        }
        topEkonomiaCache = top20;
        topEkonomiaCacheCzas = teraz;
        return top20;
    }

    /**
     * Obsluguje %klany_top_ekonomia_<N>_<pole>% - TOP 20 graczy wg REALNEJ kasy
     * (Vault/Economy, np. EssentialsX), zamiast skarbca klanu. Uzywane do "topki kasy"
     * (graczy, a nie klanow).
     * Dostepne pola:
     *  - nick - nick gracza
     *  - kasa - surowa kwota, np. "48333.33"
     *  - kasa_formatted - kwota sformatowana skrotowo, np. "483.33$", "43.3k", "14.2M"
     * Jesli Vault/plugin ekonomii nie jest podpiety, wszystkie pozycje sa puste.
     */
    private String handleTopEkonomia(String params) {
        // format: top_ekonomia_<N>_<pole>
        String[] parts = params.split("_", 4);
        if (parts.length < 4) return null;
        int pozycja;
        try {
            pozycja = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        String pole = parts[3];

        java.util.List<UUID> top = getTopEkonomia();
        if (pozycja < 1 || pozycja > top.size()) return "";
        UUID uuid = top.get(pozycja - 1);
        OfflinePlayer gracz = Bukkit.getOfflinePlayer(uuid);
        String nick = gracz.getName();
        if (nick == null) nick = "";
        double kasa = hasEconomy() ? economy.getBalance(gracz) : 0;

        return switch (pole) {
            case "nick" -> nick;
            case "kasa" -> String.format(java.util.Locale.ROOT, "%.2f", kasa);
            case "kasa_formatted" -> formatujEkonomicznie(kasa);
            default -> null;
        };
    }

    /**
     * Obsluguje %klany_top_zabojstwa_<N>_<pole>% - jak handleTopKlanow, ale POSORTOWANE
     * po sumie zabojstw klanu, a nie po ELO. Uzywane do "topki zabojstw" (klanowej).
     */
    private String handleTopKlanowZabojstwa(String params) {
        // format: top_zabojstwa_<N>_<pole>
        String[] parts = params.split("_", 4);
        if (parts.length < 4) return null;
        int pozycja;
        try {
            pozycja = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        String pole = parts[3];
        Klan klan = plugin.getKlanManager().getKlanNaPozycjiZabojstw(pozycja);
        if (klan == null) return "";
        return switch (pole) {
            case "tag" -> klan.getTag();
            case "nazwa" -> klan.getNazwa();
            case "lider" -> {
                String nick = org.bukkit.Bukkit.getOfflinePlayer(klan.getLider()).getName();
                yield nick != null ? nick : "";
            }
            case "skarbiec" -> String.valueOf(klan.getSkarbiec());
            case "czlonkowie" -> String.valueOf(klan.getLiczbaCzlonkow());
            case "ranking" -> String.valueOf(plugin.getKlanManager().getSumaRankinguKlanu(klan));
            case "zabojstwa" -> String.valueOf(plugin.getKlanManager().getSumaZabojstwKlanu(klan));
            default -> null;
        };
    }

    /**
     * Obsluguje %klany_gracz_smierci_<N>_<pole>% - jak handleTopGraczy, ale POSORTOWANE
     * po liczbie smierci gracza, a nie po ELO. Uzywane do "topki smierci".
     */
    private String handleTopGraczySmierci(String params) {
        // format: gracz_smierci_<N>_<pole>
        String[] parts = params.split("_", 4);
        if (parts.length < 4) return null;
        int pozycja;
        try {
            pozycja = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        String pole = parts[3];
        UUID uuid = plugin.getKlanManager().getGraczNaPozycjiSmierci(pozycja);
        if (uuid == null) return "";
        OfflinePlayer gracz = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        Klan klan = plugin.getKlanManager().getKlanGracza(uuid);
        String nick = gracz.getName();
        if (nick == null) nick = "";
        return switch (pole) {
            case "nick" -> nick;
            case "tag" -> klan == null ? "" : klan.getTag();
            case "nazwa" -> klan == null ? "" : klan.getNazwa();
            case "ranking" -> String.valueOf(plugin.getKlanManager().getRankingGracza(uuid));
            case "tier" -> String.valueOf(plugin.getKlanManager().getTierGracza(uuid));
            case "zabojstwa" -> String.valueOf(plugin.getKlanManager().getZabojstwaGracza(uuid));
            case "smierci" -> String.valueOf(plugin.getKlanManager().getSmierciGracza(uuid));
            case "kd" -> String.format(java.util.Locale.ROOT, "%.2f",
                    Klan.kd(plugin.getKlanManager().getZabojstwaGracza(uuid), plugin.getKlanManager().getSmierciGracza(uuid)));
            default -> null;
        };
    }

    /**
     * Obsluguje %klany_gracz_zabojstwa_<N>_<pole>% - jak handleTopGraczy, ale POSORTOWANE
     * po liczbie zabojstw gracza, a nie po ELO. Uzywane do "topki zabojstw" (graczy).
     */
    private String handleTopGraczyZabojstwa(String params) {
        // format: gracz_zabojstwa_<N>_<pole>
        String[] parts = params.split("_", 4);
        if (parts.length < 4) return null;
        int pozycja;
        try {
            pozycja = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        String pole = parts[3];
        UUID uuid = plugin.getKlanManager().getGraczNaPozycjiZabojstw(pozycja);
        if (uuid == null) return "";
        OfflinePlayer gracz = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        Klan klan = plugin.getKlanManager().getKlanGracza(uuid);
        String nick = gracz.getName();
        if (nick == null) nick = "";
        return switch (pole) {
            case "nick" -> nick;
            case "tag" -> klan == null ? "" : klan.getTag();
            case "nazwa" -> klan == null ? "" : klan.getNazwa();
            case "ranking" -> String.valueOf(plugin.getKlanManager().getRankingGracza(uuid));
            case "tier" -> String.valueOf(plugin.getKlanManager().getTierGracza(uuid));
            case "zabojstwa" -> String.valueOf(plugin.getKlanManager().getZabojstwaGracza(uuid));
            case "smierci" -> String.valueOf(plugin.getKlanManager().getSmierciGracza(uuid));
            case "kd" -> String.format(java.util.Locale.ROOT, "%.2f",
                    Klan.kd(plugin.getKlanManager().getZabojstwaGracza(uuid), plugin.getKlanManager().getSmierciGracza(uuid)));
            default -> null;
        };
    }

    // ── Top graczy: %klany_gracz_<N>_<pole>% ───────────────────────────────

    /**
     * Obsluguje placeholdery %klany_gracz_1_nick%, %klany_gracz_3_ranking% itd.
     * Dostepne pola: nick, tag, nazwa, ranking, zabojstwa, smierci, kd
     * Pola ranking/zabojstwa/smierci/kd/nick dzialaja NIEZALEZNIE od tego, czy gracz na tej
     * pozycji ma klan - tylko tag/nazwa (ktore pochodza z klanu) sa puste dla gracza bez klanu.
     */
    private String handleTopGraczy(String params) {
        // format: gracz_<N>_<pole>
        String[] parts = params.split("_", 3);
        if (parts.length < 3) return null;
        int pozycja;
        try {
            pozycja = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        String pole = parts[2];
        UUID uuid = plugin.getKlanManager().getGraczNaPozycji(pozycja);
        if (uuid == null) return "";
        OfflinePlayer gracz = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        Klan klan = plugin.getKlanManager().getKlanGracza(uuid);
        String nick = gracz.getName();
        if (nick == null) nick = "";
        return switch (pole) {
            case "nick" -> nick;
            case "tag" -> klan == null ? "" : klan.getTag();
            case "nazwa" -> klan == null ? "" : klan.getNazwa();
            case "ranking" -> String.valueOf(plugin.getKlanManager().getRankingGracza(uuid));
            case "tier" -> String.valueOf(plugin.getKlanManager().getTierGracza(uuid));
            case "zabojstwa" -> String.valueOf(plugin.getKlanManager().getZabojstwaGracza(uuid));
            case "smierci" -> String.valueOf(plugin.getKlanManager().getSmierciGracza(uuid));
            case "kd" -> String.format(java.util.Locale.ROOT, "%.2f",
                    Klan.kd(plugin.getKlanManager().getZabojstwaGracza(uuid), plugin.getKlanManager().getSmierciGracza(uuid)));
            default -> null;
        };
    }

    /**
     * Wersja BEZ drugiego gracza (np. do uzycia w chacie, gdzie PAPI zna tylko nadawce).
     * Nie porownuje klanow miedzy soba - tylko koloruje na zielono, jesli gracz ma klan.
     *
     * Format: " &8[&#1F6B8;KOLOR%tag%&8]"
     */
    private String getRelKlanTag(OfflinePlayer player) {
        Klan klan = plugin.getKlanManager().getKlanGracza(player.getUniqueId());
        if (klan == null) return "";
        return Msg.color(" &8[&#8FE5A3\uD83C\uDFF9&#8FE5A3" + klan.getTag() + "&8]");
    }

    // ── Placeholder RELACYJNY (zna obu graczy - do nametagow/tablisty) ─────────

    @Override
    public String onPlaceholderRequest(Player one, Player two, @NotNull String params) {
        if (one == null || two == null) return "";

        if (params.equals("rel_klan_tag")) {
            return getRelKlanTagPorownanie(one, two);
        }
        return null;
    }

    /**
     * Zwraca SUFFIX z tagiem klanu gracza "two" (tego, ktory jest wyswietlany), do
     * doklejenia kolo nametagu w TAB (np. jako "%rel_klany_rel_klan_tag%" w
     * nametag-suffix). Skolorowany wzgledem gracza "one" (tego, ktory patrzy):
     *   Zielony (&#8FE5A3) jesli "one" i "two" sa RAZEM w tym samym klanie
     *   Czerwony (&c) jesli "two" ma klan, ale inny niz "one" (albo "one" nie ma klanu)
     *   Czerwony (&c), z etykieta "Bez klanu" zamiast tagu, jesli "two" w ogole nie
     *     nalezy do zadnego klanu (zeby bylo widac, ze faktycznie nie ma klanu, a nie
     *     ze placeholder po prostu nie zadzialal).
     */
    private String getRelKlanTagPorownanie(Player one, Player two) {
        Klan klanDrugiego = plugin.getKlanManager().getKlanGracza(two.getUniqueId());

        if (klanDrugiego == null) {
            return "";
        }

        Klan klanPierwszego = plugin.getKlanManager().getKlanGracza(one.getUniqueId());
        boolean tenSamKlan = klanPierwszego != null && klanPierwszego.getId() == klanDrugiego.getId();

        String kolor = tenSamKlan ? "&#8FE5A3" : "&c";
        return Msg.color(" &8[" + kolor + "\uD83C\uDFF9" + kolor + klanDrugiego.getTag() + "&8]");
    }

    /**
     * %klany_gracz_ranking% - ranking ELO gracza (np. "1000").
     * Ranking ELO domyslnie wynosi 1000 dla KAZDEGO gracza, dziala identycznie
     * niezaleznie od przynaleznosci do klanu - patrz KlanManager.getRankingGracza().
     */
    private String handleGraczRanking(OfflinePlayer player) {
        return String.valueOf(plugin.getKlanManager().getRankingGracza(player.getUniqueId()));
    }

    /**
     * %klany_gracz_pozycja% - pozycja gracza w globalnym topie ELO (np. "#5").
     * Gracz bez zadnych zapisanych statystyk (nigdy nie wzial udzialu w PvP liczonym do
     * rankingu) nie ma jeszcze sensownej pozycji - zwracamy "-" zamiast mylacego "0".
     */
    private String handleGraczPozycja(OfflinePlayer player) {
        int poz = plugin.getKlanManager().getPozycjaWTopie(player.getUniqueId());
        return poz > 0 ? "#" + poz : "-";
    }

    /** %klany_gracz_zabojstwa% - zabojstwa PvP gracza, niezaleznie od przynaleznosci do klanu. */
    private String handleGraczZabojstwa(OfflinePlayer player) {
        return String.valueOf(plugin.getKlanManager().getZabojstwaGracza(player.getUniqueId()));
    }

    private String handleGraczZabojstwaPozycja(OfflinePlayer player) {
        int poz = plugin.getKlanManager().getPozycjaWTopieZabojstw(player.getUniqueId());
        return poz > 0 ? "#" + poz : "-";
    }

    /** %klany_gracz_smierci% - smierci PvP gracza, niezaleznie od przynaleznosci do klanu. */
    private String handleGraczSmierci(OfflinePlayer player) {
        return String.valueOf(plugin.getKlanManager().getSmierciGracza(player.getUniqueId()));
    }

    private String handleGraczSmierciPozycja(OfflinePlayer player) {
        int poz = plugin.getKlanManager().getPozycjaWTopieSmierci(player.getUniqueId());
        return poz > 0 ? "#" + poz : "-";
    }

    /** %klany_gracz_kd% - stosunek zabojstwa/smierci gracza (np. "1.50"), niezaleznie od klanu. */
    private String handleGraczKd(OfflinePlayer player) {
        int zabojstwa = plugin.getKlanManager().getZabojstwaGracza(player.getUniqueId());
        int smierci = plugin.getKlanManager().getSmierciGracza(player.getUniqueId());
        return String.format(java.util.Locale.ROOT, "%.2f", Klan.kd(zabojstwa, smierci));
    }
}
