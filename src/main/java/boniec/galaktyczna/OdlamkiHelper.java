package boniec.galaktyczna;

import boniec.dev.inventory.Main;
import boniec.klany.core.KlanWalutaManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Liczenie i zabieranie Smoczych Odłamków (szablon klanowy + patron). */
public final class OdlamkiHelper {

    private OdlamkiHelper() {}

    public static boolean maSzablon(Main plugin) {
        return !szablony(plugin).isEmpty();
    }

    public static int policz(Player gracz, Main plugin) {
        List<ItemStack> szablony = szablony(plugin);
        if (szablony.isEmpty()) return 0;
        int suma = 0;
        for (ItemStack item : gracz.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (pasuje(item, szablony)) suma += item.getAmount();
        }
        return suma;
    }

    public static boolean zabierz(Player gracz, Main plugin, int ile) {
        if (ile <= 0) return true;
        if (policz(gracz, plugin) < ile) return false;
        List<ItemStack> szablony = szablony(plugin);
        int pozostalo = ile;
        ItemStack[] zawartosc = gracz.getInventory().getContents();
        for (int i = 0; i < zawartosc.length && pozostalo > 0; i++) {
            ItemStack item = zawartosc[i];
            if (item == null || !pasuje(item, szablony)) continue;
            int zabierz = Math.min(item.getAmount(), pozostalo);
            int nowa = item.getAmount() - zabierz;
            if (nowa <= 0) {
                gracz.getInventory().setItem(i, null);
            } else {
                item.setAmount(nowa);
            }
            pozostalo -= zabierz;
        }
        gracz.updateInventory();
        return pozostalo == 0;
    }

    /** Wklada odlamki do EQ. Zwraca ile faktycznie weszlo (reszta zostaje do odbioru). */
    public static int dajDoEq(Player gracz, Main plugin, int ile) {
        if (ile <= 0) return 0;
        ItemStack szablon = null;
        List<ItemStack> lista = szablony(plugin);
        if (!lista.isEmpty()) szablon = lista.get(0);
        if (szablon == null) return 0;
        int zostalo = ile;
        int weszlo = 0;
        int max = Math.max(1, szablon.getMaxStackSize());
        while (zostalo > 0) {
            ItemStack part = szablon.clone();
            int szt = Math.min(max, zostalo);
            part.setAmount(szt);
            var reszta = gracz.getInventory().addItem(part);
            int nieWeszlo = 0;
            for (ItemStack extra : reszta.values()) {
                nieWeszlo += extra.getAmount();
            }
            int teraz = szt - nieWeszlo;
            weszlo += teraz;
            zostalo -= szt;
            if (nieWeszlo > 0) break;
        }
        gracz.updateInventory();
        return weszlo;
    }

    private static boolean pasuje(ItemStack item, List<ItemStack> szablony) {
        for (ItemStack szablon : szablony) {
            if (szablon != null && item.isSimilar(szablon)) return true;
        }
        return false;
    }

    private static List<ItemStack> szablony(Main plugin) {
        List<ItemStack> out = new ArrayList<>();
        KlanWalutaManager km = plugin.getKlanWalutaManager();
        if (km != null) {
            ItemStack t = km.getOdlamekTemplate();
            if (t != null) out.add(t);
        }
        if (plugin.getMistrzSystem() != null) {
            ItemStack t = plugin.getMistrzSystem().getOdlamkiTemplate();
            if (t != null && out.stream().noneMatch(x -> x.isSimilar(t))) {
                out.add(t);
            }
        }
        return out;
    }
}
