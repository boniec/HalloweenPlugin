package boniec.klany.gui;

import boniec.klany.Main;
import boniec.klany.core.Klan;
import boniec.klany.core.KlanWalutaManager;
import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Obsluguje wpisywanie ilosci na czacie po kliknieciu w item skarbca w SkarbiecGui
 * (lewy klik = wplata, prawy klik = wyplata). Rejestrowany RAZ, globalnie (jak
 * UpgradeMenuListener#onChat) - trzyma mape "gracz -> co robi", zeby wiedziec,
 * czy w ogole przechwycic wiadomosc na czacie danego gracza.
 * <p>
 * Fizyczne "smocze odlamki" liczone/zabierane/wydawane sa dokladnie tak samo jak w
 * UlepszeniaGui#losuj: przez KlanWalutaManager (customowy szablon z pelnym NBT,
 * ustawiany /adminklan ustawodlamek), z fallbackiem do zwyklego materialu
 * "ustawienia.waluta-material" (domyslnie DRAGON_BREATH), jesli szablon nie zostal
 * jeszcze ustawiony.
 */
public class SkarbiecChatListener implements Listener {

    private enum Tryb { WPLATA, WYPLATA }

    // gracz -> (tryb, id klanu) ktorego dotyczy oczekiwana odpowiedz na czacie
    private static final Map<UUID, Stan> OCZEKUJACY = new HashMap<>();

    // Prawdziwy JavaPlugin hosta - potrzebny wylacznie do przelaczenia obslugi wiadomosci
    // z watku asynchronicznego (AsyncPlayerChatEvent) na glowny watek serwera (Bukkit.getScheduler().runTask).
    // Ustawiany raz przy rejestracji listenera w boniec.dev.inventory.Main.onEnable().
    private static JavaPlugin plugin;

    public SkarbiecChatListener(JavaPlugin plugin) {
        SkarbiecChatListener.plugin = plugin;
    }

    private static final class Stan {
        final Tryb tryb;
        final int klanId;

        Stan(Tryb tryb, int klanId) {
            this.tryb = tryb;
            this.klanId = klanId;
        }
    }

    public static void rozpocznijWplate(Player gracz, Klan klan) {
        OCZEKUJACY.put(gracz.getUniqueId(), new Stan(Tryb.WPLATA, klan.getId()));
        gracz.closeInventory();
        Msg.send(gracz, "skarbiec-pytanie-wplata");
    }

    public static void rozpocznijWyplate(Player gracz, Klan klan) {
        OCZEKUJACY.put(gracz.getUniqueId(), new Stan(Tryb.WYPLATA, klan.getId()));
        gracz.closeInventory();
        Msg.send(gracz, "skarbiec-pytanie-wyplata");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player gracz = event.getPlayer();
        Stan stan = OCZEKUJACY.get(gracz.getUniqueId());
        if (stan == null) return;

        event.setCancelled(true);
        String wiadomosc = event.getMessage().trim();

        Runnable zadanie = () -> obsluz(gracz, stan, wiadomosc);
        if (plugin != null) {
            Bukkit.getScheduler().runTask(plugin, zadanie);
        } else {
            zadanie.run();
        }
    }

    private void obsluz(Player gracz, Stan stan, String wiadomosc) {
        // Gracz mogl w miedzyczasie zaczac inna akcje (np. otworzyc GUI ponownie i kliknac
        // drugi raz) - upewniamy sie, ze wciaz przetwarzamy TEN sam, aktualny stan.
        if (OCZEKUJACY.get(gracz.getUniqueId()) != stan) return;

        if (wiadomosc.equalsIgnoreCase("anuluj")) {
            OCZEKUJACY.remove(gracz.getUniqueId());
            Msg.send(gracz, "skarbiec-anulowano");
            return;
        }

        Klan klan = Main.get().getKlanManager().getKlanGracza(gracz.getUniqueId());
        if (klan == null || klan.getId() != stan.klanId) {
            OCZEKUJACY.remove(gracz.getUniqueId());
            Msg.send(gracz, "brak-klanu");
            return;
        }

        int ilosc;
        try {
            ilosc = Integer.parseInt(wiadomosc);
        } catch (NumberFormatException e) {
            Msg.send(gracz, "skarbiec-zla-liczba");
            return;
        }

        if (ilosc <= 0) {
            Msg.send(gracz, "skarbiec-liczba-ujemna");
            return;
        }

        OCZEKUJACY.remove(gracz.getUniqueId());

        if (stan.tryb == Tryb.WPLATA) {
            wplac(gracz, klan, ilosc);
        } else {
            wyplac(gracz, klan, ilosc);
        }
    }

    private void wplac(Player gracz, Klan klan, int ilosc) {
        if (policzOdlamkiWEq(gracz) < ilosc) {
            Msg.send(gracz, "skarbiec-brak-odlamkow-eq", "%ilosc%", ilosc);
            return;
        }
        zabierzOdlamkiZEq(gracz, ilosc);
        klan.dodajDoSkarbca(ilosc);
        Main.get().getKlanManager().zapiszSkarbiec(klan);
        Main.get().getKlanManager().dodajLog(klan, gracz.getName() + " wplacil " + ilosc + " odlamkow do skarbca.");
        Msg.send(gracz, "skarbiec-wplacono", "%ilosc%", ilosc, "%stan%", klan.getSkarbiec());
    }

    private void wyplac(Player gracz, Klan klan, int ilosc) {
        if (!klan.odejmijZeSkarbca(ilosc)) {
            Msg.send(gracz, "skarbiec-brak-odlamkow-skarbiec", "%ilosc%", ilosc, "%stan%", klan.getSkarbiec());
            return;
        }
        Main.get().getKlanManager().zapiszSkarbiec(klan);
        wydajOdlamki(gracz, ilosc);
        Main.get().getKlanManager().dodajLog(klan, gracz.getName() + " wyplacil " + ilosc + " odlamkow ze skarbca.");
        Msg.send(gracz, "skarbiec-wyplacono", "%ilosc%", ilosc, "%stan%", klan.getSkarbiec());
    }

    // ── Fizyczne odlamki w ekwipunku gracza (ten sam mechanizm co UlepszeniaGui) ──

    private int policzOdlamkiWEq(Player gracz) {
        ItemStack szablon = pobierzSzablonOdlamka();
        int suma = 0;
        for (ItemStack item : gracz.getInventory().getContents()) {
            if (item == null) continue;
            if (pasujeDoWaluty(item, szablon)) {
                suma += item.getAmount();
            }
        }
        return suma;
    }

    private void zabierzOdlamkiZEq(Player gracz, int ile) {
        ItemStack szablon = pobierzSzablonOdlamka();
        int pozostaloDoZabrania = ile;
        ItemStack[] zawartosc = gracz.getInventory().getContents();
        for (int i = 0; i < zawartosc.length && pozostaloDoZabrania > 0; i++) {
            ItemStack item = zawartosc[i];
            if (item == null || !pasujeDoWaluty(item, szablon)) continue;

            int zabierz = Math.min(item.getAmount(), pozostaloDoZabrania);
            int nowaIlosc = item.getAmount() - zabierz;
            if (nowaIlosc <= 0) {
                gracz.getInventory().setItem(i, null);
            } else {
                item.setAmount(nowaIlosc);
            }
            pozostaloDoZabrania -= zabierz;
        }
        gracz.updateInventory();
    }

    /** Wydaje graczowi "ile" fizycznych odlamkow do ekwipunku (przy wyplacie ze skarbca). */
    private void wydajOdlamki(Player gracz, int ile) {
        ItemStack szablon = pobierzSzablonOdlamka();
        ItemStack wzor = szablon != null ? szablon.clone() : new ItemStack(pobierzMaterialFallback());

        int maxStack = wzor.getMaxStackSize();
        int pozostalo = ile;
        while (pozostalo > 0) {
            int wStosie = Math.min(pozostalo, maxStack);
            ItemStack doWydania = wzor.clone();
            doWydania.setAmount(wStosie);
            java.util.Map<Integer, ItemStack> nadmiar = gracz.getInventory().addItem(doWydania);
            for (ItemStack niezmieszczony : nadmiar.values()) {
                gracz.getWorld().dropItemNaturally(gracz.getLocation(), niezmieszczony);
            }
            pozostalo -= wStosie;
        }
    }

    private boolean pasujeDoWaluty(ItemStack item, ItemStack szablon) {
        if (szablon != null) {
            return item.isSimilar(szablon);
        }
        return item.getType() == pobierzMaterialFallback();
    }

    private ItemStack pobierzSzablonOdlamka() {
        KlanWalutaManager manager = Main.get().getKlanWalutaManager();
        return manager != null ? manager.getOdlamekTemplate() : null;
    }

    private Material pobierzMaterialFallback() {
        Material material = Material.matchMaterial(Main.get().getConfig().getString("ustawienia.waluta-material", "DRAGON_BREATH"));
        return material == null ? Material.DRAGON_BREATH : material;
    }
}
