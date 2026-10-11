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

/**
 * /klan skarbiec - GUI skarbca klanowego (45 slotow, item na srodku - slot 22).
 * <p>
 * Caly wyglad (material, nazwa, lore, custom-model-data) jest wczytywany z
 * klany-config.yml -> sekcja "skarbiec-gui", wiec admin moze go zmienic bez
 * rekompilacji pluginu - tak jak np. tytul GUI ("gui.tytul-skarbiec").
 * <p>
 * Lewy klik = wplacanie do skarbca (gracz wpisuje ilosc na czacie, nastepnie z jego
 * ekwipunku zabierane sa fizyczne "smocze odlamki" - ten sam mechanizm co w
 * UlepszeniaGui: szablon z KlanWalutaManager, a jesli nieustawiony - material
 * "ustawienia.waluta-material"). Prawy klik = wyplacanie (gracz wpisuje ilosc,
 * ze skarbca odejmowana jest podana liczba, a graczowi wydawane sa fizyczne
 * odlamki do ekwipunku). Sama logika "czekania na wpis na czacie" zyje w
 * SkarbiecChatListener (rejestrowany raz globalnie, tak jak UpgradeMenuListener).
 */
public class SkarbiecGui implements KlanGui, InventoryHolder {

    private static final int ROZMIAR = 45;
    private static final int SLOT_ITEMU = 22; // srodek rzedu 3 w 45-slotowym GUI

    private final Klan klan;
    private final Inventory inventory;

    public SkarbiecGui(Klan klan) {
        this.klan = klan;
        this.inventory = Bukkit.createInventory(this, ROZMIAR, Msg.guiTitle("tytul-skarbiec"));
        zbuduj();
    }

    private void zbuduj() {
        ConfigurationSection sekcja = Main.get().getConfig().getConfigurationSection("skarbiec-gui");

        Material material = Material.SUNFLOWER;
        String nazwa = "&fSkarbiec klanu";
        List<String> loreSurowe = new ArrayList<>();
        int customModelData = 0;

        if (sekcja != null) {
            Material zConfigu = Material.matchMaterial(sekcja.getString("material", "SUNFLOWER"));
            if (zConfigu != null) material = zConfigu;
            nazwa = sekcja.getString("nazwa", nazwa);
            loreSurowe = sekcja.getStringList("lore");
            customModelData = sekcja.getInt("custom-model-data", 0);
        }

        List<String> lore = new ArrayList<>();
        for (String linia : loreSurowe) {
            lore.add(Msg.color(linia.replace("%ilejestodlamkow%", String.valueOf(klan.getSkarbiec()))));
        }

        ItemBuilder builder = new ItemBuilder(material)
                .name(nazwa)
                .loreRaw(lore);

        ItemStack item = builder.build();
        if (customModelData > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
        }

        inventory.setItem(SLOT_ITEMU, item);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player gracz) {
        int slot = event.getSlot();
        if (slot != SLOT_ITEMU) return;

        if (!klan.isCzlonek(gracz.getUniqueId())) {
            Msg.send(gracz, "brak-klanu");
            return;
        }

        if (event.isLeftClick()) {
            SkarbiecChatListener.rozpocznijWplate(gracz, klan);
        } else if (event.isRightClick()) {
            if (!klan.maUprawnieniaZarzadzania(gracz.getUniqueId())) {
                Msg.send(gracz, "brak-uprawnien");
                return;
            }
            SkarbiecChatListener.rozpocznijWyplate(gracz, klan);
        }
    }
}
