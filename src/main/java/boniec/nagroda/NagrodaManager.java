package boniec.nagroda;

import boniec.szaman.util.Msg;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Nagroda za dolaczenie na Discorda.
 * <p>
 * Przeplyw:
 * 1) Gracz wpisuje /nagroda -> dostaje w grze jednorazowy kod (klikalny link do Discorda).
 * 2) Na kanale Discord bot trzyma embed z przyciskiem. Klikniecie otwiera formularz (nick + kod).
 * 3) Jesli nick i kod sie zgadzaja -> z konsoli leca komendy nagrody (nagroda.yml -> nagrody.komendy),
 *    na czacie leci broadcast, a osobie na Discordzie zmienia sie nick na nick z Minecrafta.
 * <p>
 * Cala logika stanu (kody, odbior nagrody) dziala WYLACZNIE na glownym watku serwera.
 * Watek JDA tylko odbiera interakcje i przekazuje je na glowny watek.
 */
public class NagrodaManager {

    public static final String BUTTON_ID = "nagroda_odbierz";
    public static final String MODAL_ID = "nagroda_modal";

    private static final String ALFABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private record Kod(UUID uuid, String nick, long wygasa) {}

    private final JavaPlugin plugin;
    private final File plikConfig;
    private FileConfiguration cfg;
    /** SQLite (nagroda.db): polaczenia Discord <-> Minecraft + ID wiadomosci z embedem. null = blad bazy. */
    private NagrodaBaza baza;

    private volatile JDA jda;
    private final Object jdaLock = new Object();
    private final Object startStopLock = new Object();
    private final AtomicInteger generacja = new AtomicInteger();
    private final Map<String, Kod> kody = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public NagrodaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plikConfig = new File(plugin.getDataFolder(), "nagroda.yml");
        wczytajConfig();
        try {
            this.baza = new NagrodaBaza(plugin);
        } catch (Exception e) {
            this.baza = null;
            plugin.getLogger().log(Level.SEVERE, "[Nagroda] Nie udalo sie otworzyc bazy SQLite (nagroda.db) - system nagrod wylaczony.", e);
        }
    }

    // ── Config ─────────────────────────────────────────────────

    private void wczytajConfig() {
        if (!plikConfig.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource("nagroda.yml")) {
                if (in != null) Files.copy(in, plikConfig.toPath());
                else plikConfig.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[Nagroda] Nie udalo sie utworzyc nagroda.yml", e);
            }
        }
        cfg = YamlConfiguration.loadConfiguration(plikConfig);
        try (InputStream def = plugin.getResource("nagroda.yml")) {
            if (def != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // brak defaultow nie jest krytyczny - getX() ma wlasne wartosci domyslne
        }
    }

    public String t(String sciezka, String domyslna) {
        return cfg.getString(sciezka, domyslna);
    }

    private List<String> lista(String sciezka, List<String> domyslna) {
        if (cfg.isList(sciezka)) return cfg.getStringList(sciezka);
        if (cfg.isString(sciezka)) return List.of(cfg.getString(sciezka));
        return domyslna;
    }

    // ── Discord (start / stop / embed) ─────────────────────────

    public boolean czyPolaczony() {
        return jda != null;
    }

    public void start() {
        if (baza == null) {
            plugin.getLogger().severe("[Nagroda] Brak bazy danych - bot Discord nie wystartuje.");
            return;
        }
        if (!cfg.getBoolean("discord.wlaczony", false)) {
            plugin.getLogger().info("[Nagroda] Discord wylaczony w nagroda.yml (discord.wlaczony: false).");
            return;
        }
        String token = cfg.getString("discord.token", "");
        if (token.isBlank() || token.contains("WKLEJ")) {
            plugin.getLogger().warning("[Nagroda] Brak tokena bota w nagroda.yml (discord.token) - bot nie wystartuje.");
            return;
        }
        final int gen = generacja.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> uruchomBota(gen, token));
    }

    private void uruchomBota(int gen, String token) {
        synchronized (startStopLock) {
            zatrzymajJda(false);
            if (gen != generacja.get()) return;
            JDA nowa;
            try {
                nowa = JDABuilder.createLight(token, EnumSet.noneOf(GatewayIntent.class))
                        .addEventListeners(new NagrodaDiscordListener(plugin, this))
                        .build();
                nowa.awaitReady();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[Nagroda] Nie udalo sie uruchomic bota Discord (sprawdz token).", e);
                return;
            }
            if (gen != generacja.get()) {
                wylaczJda(nowa, false);
                return;
            }
            synchronized (jdaLock) {
                if (gen != generacja.get()) {
                    wylaczJda(nowa, false);
                    return;
                }
                jda = nowa;
            }
            plugin.getLogger().info("[Nagroda] Bot Discord polaczony jako " + nowa.getSelfUser().getName());
            if (cfg.getBoolean("discord.auto-embed", true) && baza != null && baza.pobierz("wiadomosc-id", "").isEmpty()) {
                wyslijEmbed();
            }
        }
    }

    public void zamknij() {
        generacja.incrementAndGet();
        zatrzymajJda(true);
        kody.clear();
    }

    private void zatrzymajJda(boolean natychmiast) {
        JDA j;
        synchronized (jdaLock) {
            j = jda;
            jda = null;
        }
        if (j != null) wylaczJda(j, natychmiast);
    }

    private void wylaczJda(JDA j, boolean natychmiast) {
        if (j == null) return;
        try {
            if (natychmiast) {
                j.shutdownNow();
                return;
            }
            j.shutdown();
            if (!j.awaitShutdown(Duration.ofSeconds(8))) {
                j.shutdownNow();
                j.awaitShutdown(Duration.ofSeconds(3));
            }
        } catch (InterruptedException e) {
            j.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            try {
                j.shutdownNow();
            } catch (Exception ignored) {
                // sesja i tak do wymiany
            }
        }
    }

    /** /nagroda reload - przeladowuje config i restartuje bota (np. po zmianie tokena). */
    public void przeladuj() {
        wczytajConfig();
        start();
    }

    /** Wylaczenie pluginu: zatrzymuje bota i zamyka baze. */
    public void wylacz() {
        zamknij();
        if (baza != null) {
            baza.zamknij();
            baza = null;
        }
    }

    /** /nagroda odlacz <nick> - zdejmuje polaczenie (np. gdy gracz zmienil konto Discord). */
    public int odlacz(String nick) {
        return baza == null ? 0 : baza.odlaczPoNicku(nick);
    }

    /** Wysyla (lub odswieza) embed z przyciskiem na kanal z configu. Zwraca komunikat dla admina (kolory &). */
    public String wyslijEmbed() {
        JDA j = jda;
        if (j == null) return "&cBot Discord nie jest polaczony (sprawdz token i discord.wlaczony w nagroda.yml).";
        String kanalId = cfg.getString("discord.kanal-id", "");
        TextChannel kanal = kanalId.isBlank() ? null : j.getTextChannelById(kanalId);
        if (kanal == null) return "&cNie znaleziono kanalu tekstowego o ID '" + kanalId + "' (discord.kanal-id).";

        String stareId = baza.pobierz("wiadomosc-id", "");
        String stareKanal = baza.pobierz("wiadomosc-kanal-id", kanalId);
        if (stareKanal.isEmpty()) stareKanal = kanalId;
        if (!stareId.isEmpty()) {
            TextChannel sk = j.getTextChannelById(stareKanal);
            if (sk != null) sk.deleteMessageById(stareId).queue(null, e -> { });
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(cfg.getString("embed.tytul", "🎁 Nagroda za Discorda"));
        eb.setDescription(String.join("\n", lista("embed.opis", List.of(
                "Wpisz na serwerze **/nagroda**, aby dostać kod,",
                "a następnie kliknij przycisk poniżej i podaj swój **nick** oraz **kod**."))));
        try {
            eb.setColor(Integer.parseInt(cfg.getString("embed.kolor", "#5865F2").replace("#", ""), 16));
        } catch (NumberFormatException ignored) {
            // zly kolor - zostaje domyslny
        }
        String stopka = cfg.getString("embed.stopka", "");
        if (!stopka.isBlank()) eb.setFooter(stopka);
        String miniatura = cfg.getString("embed.miniatura", "");
        if (!miniatura.isBlank()) eb.setThumbnail(miniatura);
        String obrazek = cfg.getString("embed.obrazek", "");
        if (!obrazek.isBlank()) eb.setImage(obrazek);

        ButtonStyle styl = ButtonStyle.SUCCESS;
        try {
            styl = ButtonStyle.valueOf(cfg.getString("embed.przycisk.styl", "SUCCESS").toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // zly styl - SUCCESS
        }
        Button przycisk = Button.of(styl, BUTTON_ID, cfg.getString("embed.przycisk.tekst", "Odbierz nagrodę"));
        String emoji = cfg.getString("embed.przycisk.emoji", "🎁");
        if (!emoji.isBlank()) przycisk = przycisk.withEmoji(Emoji.fromUnicode(emoji));

        kanal.sendMessageEmbeds(eb.build()).setActionRow(przycisk).queue(msg -> {
            if (baza != null) {
                baza.ustaw("wiadomosc-id", msg.getId());
                baza.ustaw("wiadomosc-kanal-id", msg.getChannel().getId());
            }
        }, err -> plugin.getLogger().log(Level.SEVERE, "[Nagroda] Nie udalo sie wyslac embeda (uprawnienia bota do kanalu?).", err));
        return "&aWyslano embed na kanal #" + kanal.getName() + ".";
    }

    /** Formularz (modal) z polami: nick + kod. */
    public Modal zbudujModal() {
        TextInput nick = TextInput.create("nick", cfg.getString("modal.nick-etykieta", "Twój nick z Minecrafta"), TextInputStyle.SHORT)
                .setPlaceholder(cfg.getString("modal.nick-placeholder", "np. Steve"))
                .setRequired(true).setMinLength(1).setMaxLength(32).build();
        TextInput kod = TextInput.create("kod", cfg.getString("modal.kod-etykieta", "Kod weryfikacyjny"), TextInputStyle.SHORT)
                .setPlaceholder(cfg.getString("modal.kod-placeholder", "kod z komendy /nagroda"))
                .setRequired(true).setMinLength(1).setMaxLength(16).build();
        return Modal.create(MODAL_ID, cfg.getString("modal.tytul", "Odbierz nagrodę"))
                .addActionRow(nick)
                .addActionRow(kod)
                .build();
    }

    // ── Kody (glowny watek) ────────────────────────────────────

    /** Czy to konto Minecraft ma juz polaczone konto Discord (max 1 polaczone konto). */
    public boolean czyOdebrano(UUID uuid) {
        return baza != null && baza.czyMcPolaczone(uuid);
    }

    private void wyczyscWygasle() {
        long teraz = System.currentTimeMillis();
        kody.values().removeIf(k -> k.wygasa() <= teraz);
    }

    /** Zwraca aktualny kod gracza (tworzy nowy, jesli brak / wygasl). */
    public String pobierzLubStworzKod(Player player) {
        wyczyscWygasle();
        for (Map.Entry<String, Kod> e : kody.entrySet()) {
            if (e.getValue().uuid().equals(player.getUniqueId())) return e.getKey();
        }
        int dlugosc = Math.max(4, Math.min(12, cfg.getInt("kod.dlugosc", 6)));
        String kod;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dlugosc; i++) sb.append(ALFABET.charAt(random.nextInt(ALFABET.length())));
            kod = sb.toString();
        } while (kody.containsKey(kod));
        long wygasa = System.currentTimeMillis() + Math.max(1, cfg.getInt("kod.wazny-minuty", 15)) * 60_000L;
        kody.put(kod, new Kod(player.getUniqueId(), player.getName(), wygasa));
        return kod;
    }

    /** Wysyla graczowi wiadomosc z kodem; linia z linkiem do Discorda jest klikalna. */
    public void wyslijKodGraczowi(Player player, String kod) {
        String link = cfg.getString("discord.link", "https://discord.gg/gzibmc");
        String host = link.replaceFirst("^https?://", "");
        String hover = cfg.getString("wiadomosci.hover-link", "&7Kliknij, aby dołączyć na Discorda");
        List<String> linie = lista("wiadomosci.kod", List.of(
                "", "&fTwoj weryfikacyjny kod: &c%kod%", "", "&4discord.gg/gzibmc", ""));
        for (String linia : linie) {
            String kolor = Msg.color(linia.replace("%kod%", kod).replace("%player%", player.getName()));
            if (ChatColor.stripColor(kolor).isEmpty()) {
                player.sendMessage("");
                continue;
            }
            BaseComponent[] skladniki = TextComponent.fromLegacyText(kolor);
            if (ChatColor.stripColor(kolor).contains(host)) {
                for (BaseComponent c : skladniki) {
                    c.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
                    c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Msg.color(hover))));
                }
            }
            player.spigot().sendMessage(skladniki);
        }
    }

    // ── Odbior nagrody (glowny watek) ──────────────────────────

    /** Zwraca odpowiedz dla osoby na Discordzie (zwykly tekst, bez kolorow Minecrafta). */
    public String odbierz(long discordId, Member member, String podanyNick, String podanyKod) {
        wyczyscWygasle();
        Kod kod = kody.get(podanyKod.trim().toUpperCase());
        if (kod == null) {
            return t("odpowiedzi.bledny-kod", "❌ Nieprawidłowy lub wygasły kod. Wpisz **/nagroda** na serwerze, aby dostać nowy.");
        }
        if (!kod.nick().equalsIgnoreCase(podanyNick.trim())) {
            return t("odpowiedzi.bledny-nick", "❌ Ten kod nie należy do podanego nicku.");
        }
        if (baza == null) {
            return "❌ Baza danych jest niedostępna. Spróbuj później lub napisz do administracji.";
        }
        // MAX 1 POLACZONE KONTO: jedno konto Discord <-> jeden nick MC (w obie strony).
        String polaczonyNick = baza.nickDlaDiscord(discordId);
        if (polaczonyNick != null) {
            return t("odpowiedzi.juz-odebrano-dc", "❌ To konto Discord ma już połączone konto Minecraft: **%player%**.")
                    .replace("%player%", polaczonyNick);
        }
        if (baza.czyMcPolaczone(kod.uuid())) {
            return t("odpowiedzi.juz-odebrano-mc", "❌ Ten nick jest już połączony z innym kontem Discord.");
        }
        if (cfg.getBoolean("nagrody.wymagaj-online", false) && Bukkit.getPlayerExact(kod.nick()) == null) {
            return t("odpowiedzi.gracz-offline", "❌ Musisz być online na serwerze, aby odebrać nagrodę.");
        }

        // Zapisujemy polaczenie w SQLite ZANIM wykonamy nagrode. Baza sama odrzuci drugie
        // polaczenie tego samego konta (PRIMARY KEY / UNIQUE) - zadnych podwojnych odbiorow.
        String dcNazwa = member != null ? member.getUser().getName() : null;
        if (!baza.polacz(discordId, dcNazwa, kod.uuid(), kod.nick())) {
            return t("odpowiedzi.juz-odebrano-mc", "❌ Ten nick jest już połączony z innym kontem Discord.");
        }
        kody.remove(podanyKod.trim().toUpperCase());

        // Komendy z konsoli
        for (String komenda : cfg.getStringList("nagrody.komendy")) {
            if (komenda == null || komenda.isBlank()) continue;
            String gotowa = komenda.replace("%player%", kod.nick()).replace("%uuid%", kod.uuid().toString());
            if (gotowa.startsWith("/")) gotowa = gotowa.substring(1);
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), gotowa);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Nagroda] Blad komendy nagrody: " + gotowa, e);
            }
        }

        // Broadcast na czacie
        for (String linia : lista("wiadomosci.broadcast", List.of(
                "", "&8» &6%player% &7odebrał nagrodę za dołączenie na &9Discorda&7!", ""))) {
            Bukkit.broadcastMessage(Msg.color(linia.replace("%player%", kod.nick())));
        }

        // Zmiana nicku na Discordzie
        if (cfg.getBoolean("discord.zmien-nick", true) && member != null && !member.isOwner()) {
            String nowy = cfg.getString("discord.nick-format", "%player%").replace("%player%", kod.nick());
            if (nowy.length() > 32) nowy = nowy.substring(0, 32);
            try {
                member.modifyNickname(nowy).queue(null, err -> plugin.getLogger().warning(
                        "[Nagroda] Nie udalo sie zmienic nicku na Discordzie (bot potrzebuje uprawnienia 'Zarzadzaj pseudonimami' i roli wyzej niz osoba): " + err.getMessage()));
            } catch (Exception e) {
                plugin.getLogger().warning("[Nagroda] Nie udalo sie zmienic nicku na Discordzie: " + e.getMessage());
            }
        }

        return t("odpowiedzi.sukces", "✅ Nagroda dla **%player%** została odebrana! Sprawdź serwer.").replace("%player%", kod.nick());
    }

    public List<String> podpowiedzi() {
        return new ArrayList<>(List.of("embed", "reload", "odlacz"));
    }
}
