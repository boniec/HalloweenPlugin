package boniec.klany.gui;

import boniec.klany.Main;
import boniec.klany.core.Klan;
import boniec.klany.util.ItemBuilder;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GUI ze wszystkimi topkami w jednym miejscu.
 * <p>
 * Caly uklad (rozmiar, tytul, tlo, kazdy item topki, przycisk zamkniecia) jest w
 * 100% wczytywany z klany-config.yml -> sekcja "topki-gui", dokladnie jak w
 * UlepszeniaGui/SkarbiecGui. Kazdy item topki mozna dowolnie skonfigurowac:
 *  - material (albo glowa gracza z tekstura base64 - "glowa-tekstura")
 *  - slot
 *  - nazwa (zwykle kolorowanie & oraz &#rrggbb, jak wszedzie indziej w configu)
 *  - lore (lista linii, %numer%/%nazwa%/%wartosc% podstawiane per pozycja + naglowek/stopka) -
 *    kazda linia moze miec dowolne wlasne kolory/hex wpisane recznie, np. "&#71F3B4#1 GRACZ BONIEC"
 *  - ilosc (amount)
 * Admin moze wiec dodac/usunac/przesunac dowolna topke i zmienic caly jej wyglad
 * bez rekompilacji - wystarczy /adminklan reload.
 */
public class TopkiGui implements KlanGui, InventoryHolder {

    /** Topki ktore maja realne dane w tym buildzie (Klan#getSkarbiec / #getRankingKlanu). Reszta z configu jest czysto kosmetyczna/placeholder, dopoki nie podlaczy sie bazy danych. */
    private static final String TOPKA_PIENIADZE = "pieniadze";
    private static final String TOPKA_RANKING_KLANU = "ranking-klanu";

    private final Inventory inventory;
    private final ConfigurationSection sekcjaGui;

    public TopkiGui() {
        this.sekcjaGui = Main.get().getConfig().getConfigurationSection("topki-gui");
        int rozmiar = sekcjaGui != null ? normalizujRozmiar(sekcjaGui.getInt("rozmiar", 54)) : 54;
        String tytul = sekcjaGui != null
                ? Msg.color(sekcjaGui.getString("tytul", "&#F5EA50☀ &#F5EA50&lTOPKI SYSTEMOWE"))
                : Msg.guiTitle("tytul-topki");
        this.inventory = Bukkit.createInventory(this, rozmiar, tytul);
        zbuduj();
    }

    private void zbuduj() {
        if (sekcjaGui == null) {
            Bukkit.getLogger().warning("[Klany] Brak sekcji 'topki-gui' w klany-config.yml - GUI topek bedzie puste.");
            return;
        }

        zbudujTlo();
        zbudujTopki();
        zbudujPrzyciskZamkniecia();
    }

    private void zbudujTlo() {
        ConfigurationSection tlo = sekcjaGui.getConfigurationSection("tlo");
        if (tlo == null) return;

        for (String kluczMaterialu : tlo.getKeys(false)) {
            Material material = Material.matchMaterial(kluczMaterialu.toUpperCase());
            if (material == null) continue;
            wypelnijSzklem(tlo.getIntegerList(kluczMaterialu), material);
        }
    }

    private void wypelnijSzklem(List<Integer> sloty, Material material) {
        ItemStack wypelniacz = new ItemBuilder(material).name(" ").hideFlags().build();
        for (int slot : sloty) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, wypelniacz);
            }
        }
    }

    private void zbudujTopki() {
        ConfigurationSection topki = sekcjaGui.getConfigurationSection("topki");
        if (topki == null) return;

        for (String topkaId : topki.getKeys(false)) {
            ConfigurationSection topka = topki.getConfigurationSection(topkaId);
            if (topka == null) continue;

            int slot = topka.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) continue;

            inventory.setItem(slot, zbudujItemTopki(topkaId, topka));
        }
    }

    private ItemStack zbudujItemTopki(String topkaId, ConfigurationSection topka) {
        ItemBuilder builder = zbudujBazowyItem(topka);

        // Zwykle kolorowanie (& oraz &#rrggbb) - nazwe mozna wpisac calkowicie dowolnie w configu.
        builder.name(topka.getString("nazwa", topkaId));

        List<String> lore = zbudujLoreTopki(topkaId, topka);
        builder.loreRaw(lore);

        int ilosc = Math.max(1, topka.getInt("ilosc", 1));
        builder.amount(ilosc);

        ItemStack item = builder.build();
        return ustawCustomModelData(item, topka.getInt("custom-model-data", 0));
    }

    /** Tworzy bazowy ItemBuilder - albo zwykly material, albo glowa gracza z custom textura (base64). */
    private ItemBuilder zbudujBazowyItem(ConfigurationSection topka) {
        String tekstura = topka.getString("glowa-tekstura");
        if (tekstura != null && !tekstura.isBlank()) {
            return ItemBuilder.glowaTekstury(tekstura);
        }

        Material material = Material.matchMaterial(topka.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        return new ItemBuilder(material);
    }

    /**
     * Buduje lore topki: naglowek (jesli ustawiony), po jednej linii na kazda pozycje z
     * "wzor-pozycji" (z podstawionymi %numer%/%nazwa%/%wartosc%), stopka (jesli ustawiona).
     * Cala linia "wzor-pozycji" to zwykly tekst z configu - mozna w niej wpisac dowolne
     * wlasne kolory (& oraz &#rrggbb), np. "&#71F3B4#%numer% GRACZ %nazwa%".
     * Jesli dana topka nie ma jeszcze realnych danych (np. wymaga bazy danych), zamiast listy
     * pozycji wstawiana jest sekcja "lore-brak-danych" wprost z configu (bez podstawiania).
     */
    private List<String> zbudujLoreTopki(String topkaId, ConfigurationSection topka) {
        List<String> lore = new ArrayList<>();

        for (String linia : topka.getStringList("naglowek")) {
            lore.add(Msg.color(linia));
        }

        List<PozycjaTopki> pozycje = pobierzPozycje(topkaId, topka.getInt("limit", 15));

        if (pozycje == null) {
            for (String linia : topka.getStringList("lore-brak-danych")) {
                lore.add(Msg.color(linia));
            }
        } else {
            String wzor = topka.getString("wzor-pozycji", "&8#%numer% &f%nazwa% &8- &7%wartosc%");
            for (int i = 0; i < pozycje.size(); i++) {
                PozycjaTopki pozycja = pozycje.get(i);
                String numerStylizowany = getStylizowanyNumer(i + 1);
                String linia = wzor
                        .replace("%numer%", String.valueOf(i + 1))
                        .replace("%nazwa%", pozycja.nazwa())
                        .replace("%wartosc%", pozycja.wartosc());
                // %numer-styl% pozwala uzyc gotowego "#1/#2/..." z medalami (kolory z configu ponizej) zamiast zwyklej cyfry.
                linia = linia.replace("%numer-styl%", numerStylizowany);
                lore.add(Msg.color(linia));
            }
            if (pozycje.isEmpty()) {
                for (String linia : topka.getStringList("lore-pusta-lista")) {
                    lore.add(Msg.color(linia));
                }
            }
        }

        for (String linia : topka.getStringList("stopka")) {
            lore.add(Msg.color(linia));
        }

        return lore;
    }

    /** Zwraca posortowana liste pozycji danej topki, albo null jesli topka nie ma jeszcze zrodla danych (placeholder). */
    private List<PozycjaTopki> pobierzPozycje(String topkaId, int limit) {
        return switch (topkaId) {
            case TOPKA_PIENIADZE -> Main.get().getKlanManager().getWszystkieKlany()
                    .stream()
                    .sorted((a, b) -> Integer.compare(b.getSkarbiec(), a.getSkarbiec()))
                    .limit(limit)
                    .map(k -> new PozycjaTopki(k.getNazwa(), String.valueOf(k.getSkarbiec())))
                    .collect(Collectors.toList());
            case TOPKA_RANKING_KLANU -> Main.get().getKlanManager().getWszystkieKlany()
                    .stream()
                    .sorted((a, b) -> Integer.compare(b.getRankingKlanu(), a.getRankingKlanu()))
                    .limit(limit)
                    .map(k -> new PozycjaTopki(k.getNazwa(), k.getRankingKlanu() + " ELO"))
                    .collect(Collectors.toList());
            default -> null; // topka wymagajaca bazy danych, ktora nie jest jeszcze podpieta - lore-brak-danych z configu
        };
    }

    private record PozycjaTopki(String nazwa, String wartosc) {}

    private void zbudujPrzyciskZamkniecia() {
        ConfigurationSection przycisk = sekcjaGui.getConfigurationSection("przycisk-zamkniecia");
        if (przycisk == null) return;

        int slot = przycisk.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) return;

        Material material = Material.matchMaterial(przycisk.getString("material", "BARRIER"));
        if (material == null) material = Material.BARRIER;

        List<String> lore = new ArrayList<>();
        for (String linia : przycisk.getStringList("lore")) {
            lore.add(Msg.color(linia));
        }

        ItemBuilder builder = new ItemBuilder(material).name(przycisk.getString("nazwa", "&c&lZAMKNIJ"));
        if (!lore.isEmpty()) builder.loreRaw(lore);

        inventory.setItem(slot, ustawCustomModelData(builder.build(), przycisk.getInt("custom-model-data", 0)));
    }

    /** Ustawia CustomModelData na itemie, jesli wartosc > 0 (0 = wylaczone, zwykly item). */
    private ItemStack ustawCustomModelData(ItemStack item, int customModelData) {
        if (customModelData <= 0) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Rozmiar GUI musi byc wielokrotnoscia 9 w zakresie 9-54 (wymog Bukkit#createInventory). */
    private int normalizujRozmiar(int rozmiar) {
        if (rozmiar < 9) return 9;
        if (rozmiar > 54) return 54;
        return (rozmiar / 9) * 9 == rozmiar ? rozmiar : ((rozmiar / 9) + 1) * 9;
    }

    /**
     * Zwraca stylizowany numer pozycji (np. "&6&l#1 🥇") wzięty z listy "style-numerow" w
     * configu (topki-gui.style-numerow) - indeks 0 = pozycja #1, indeks 1 = pozycja #2, itd.
     * Kazda linia listy to zwykly, dowolnie kolorowany tekst (& oraz &#rrggbb) - admin moze
     * wpisac cokolwiek, np. "&#EF4141&l#1 &6[GRACZ BONIEC]". Jesli lista jest krotsza niz
     * pozycja albo w ogole nie ustawiona, uzywany jest prosty fallback "&8#<numer>".
     */
    private String getStylizowanyNumer(int pozycja) {
        List<String> style = sekcjaGui != null ? sekcjaGui.getStringList("style-numerow") : List.of();
        int indeks = pozycja - 1;
        if (indeks >= 0 && indeks < style.size()) {
            return Msg.color(style.get(indeks));
        }
        return Msg.color("&8#" + pozycja);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player gracz) {
        if (sekcjaGui == null) return;
        int slot = event.getSlot();

        int slotZamkniecia = sekcjaGui.getInt("przycisk-zamkniecia.slot", -1);
        if (slot == slotZamkniecia) {
            gracz.closeInventory();
        }
    }
}
