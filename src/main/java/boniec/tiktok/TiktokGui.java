package boniec.tiktok;

import boniec.klany.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class TiktokGui implements Listener {

    private static final int[] SLOTY = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final class Holder implements InventoryHolder {
        final boolean admin;
        final int strona;
        final List<String> ids;
        Inventory inventory;

        Holder(boolean admin, int strona, List<String> ids) {
            this.admin = admin;
            this.strona = strona;
            this.ids = ids;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final TiktokManager manager;

    public TiktokGui(TiktokManager manager) {
        this.manager = manager;
    }

    public void otworzGracza(Player player, int strona) {
        otworzGracza(player, strona, strona == 0);
    }

    public void otworzGracza(Player player, int strona, boolean odswiez) {
        TiktokManager.Konto k = manager.getKonto(player.getUniqueId());
        if (k == null || k.tiktok == null || k.tiktok.isEmpty()) {
            player.sendMessage(manager.kolor(manager.txt("nie-polaczony", "&f与 &cNajpierw: &e/tiktok polacz <nick>")));
            return;
        }
        if (odswiez) {
            player.sendMessage(manager.kolor(manager.txt("pobieranie", "&f东 &7Pobieram filmy z TikToka...")));
            Bukkit.getScheduler().runTaskAsynchronously(manager.plugin(), () -> {
                int wynik = manager.odswiezFilmy(player.getUniqueId(), false);
                Bukkit.getScheduler().runTask(manager.plugin(), () -> {
                    if (!player.isOnline()) return;
                    if (wynik == -2) {
                        player.sendMessage(manager.kolor(manager.txt("blad-api",
                                "&f亢 &eNie udało się pobrać filmów z TikToka. Pokazuję zapisane.")));
                    } else if (wynik > 0) {
                        player.sendMessage(manager.kolor(manager.txt("pobrano", "&f両 &aPobrano &f%ilosc% &anowych filmów.")
                                .replace("%ilosc%", String.valueOf(wynik))));
                    }
                    TiktokManager.Konto po = manager.getKonto(player.getUniqueId());
                    List<TiktokManager.Film> filmy = manager.filmyGracza(player.getUniqueId());
                    otworz(player, false, strona, filmy, po != null ? po.tiktok : k.tiktok);
                });
            });
            return;
        }
        List<TiktokManager.Film> filmy = manager.filmyGracza(player.getUniqueId());
        otworz(player, false, strona, filmy, k.tiktok);
    }

    public void otworzAdmin(Player player, int strona) {
        List<TiktokManager.Film> filmy = manager.oczekujace();
        if (filmy.isEmpty()) {
            player.closeInventory();
            player.sendMessage(manager.kolor(manager.txt("pusta-weryfikacja", "&f両 &aBrak oczekujących filmów.")));
            return;
        }
        otworz(player, true, strona, filmy, "");
    }

    private void otworz(Player player, boolean admin, int strona, List<TiktokManager.Film> filmy, String nick) {
        FileConfiguration cfg = manager.getCfg();
        int size = cfg.getInt("gui.wielkosc", 54);
        if (size != 54 && size != 45) size = 54;
        int naStrone = SLOTY.length;
        int stron = Math.max(1, (int) Math.ceil(filmy.size() / (double) naStrone));
        strona = Math.max(0, Math.min(strona, stron - 1));

        List<String> ids = new ArrayList<>();
        for (TiktokManager.Film f : filmy) ids.add(f.id);

        Holder holder = new Holder(admin, strona, ids);
        String tytul = admin
                ? cfg.getString("gui.tytul-admin", "&f临 &#FF0050&lᴡᴇʀʏꜰɪᴋᴀᴄᴊᴀ")
                : cfg.getString("gui.tytul", "&f临 &#FF0050&lᴛɪᴋᴛᴏᴋ");
        Inventory inv = Bukkit.createInventory(holder, size, Msg.color(tytul));
        holder.inventory = inv;

        ItemStack ramka = item(material(cfg.getString("gui.ramka"), Material.BLACK_STAINED_GLASS_PANE), " ", List.of());
        for (int i = 0; i < size; i++) inv.setItem(i, ramka);

        int start = strona * naStrone;
        for (int i = 0; i < naStrone; i++) {
            int idx = start + i;
            if (idx >= filmy.size()) {
                inv.setItem(SLOTY[i], new ItemStack(Material.AIR));
                continue;
            }
            inv.setItem(SLOTY[i], itemFilmu(filmy.get(idx), admin));
        }

        String infoPath = admin ? "gui.info-admin" : "gui.info";
        int infoSlot = cfg.getInt(infoPath + ".slot", 4);
        List<String> infoLore = new ArrayList<>();
        String stawka = manager.formatujVpln(cfg.getDouble("nagrody.vpln-za-pakiet", cfg.getDouble("nagrody.vpln", 1.0)));
        String min = String.valueOf(cfg.getLong("nagrody.min-wyswietlen", cfg.getLong("nagrody.wyswietlen-na-vpln", 300)));
        String procent = manager.formatujVpln(cfg.getDouble("nagrody.booster.procent", 10.0));
        for (String linia : cfg.getStringList(infoPath + ".lore")) {
            infoLore.add(linia
                    .replace("%nick%", nick.isEmpty() ? "-" : nick)
                    .replace("%gracz%", player.getName())
                    .replace("%wymagany-opis%", manager.wymaganyOpis(player.getName()))
                    .replace("%hashtagi%", manager.formatHashtagi())
                    .replace("%stawka%", stawka)
                    .replace("%min%", min)
                    .replace("%procent%", procent)
                    .replace("%booster%", manager.boosterStatus(player.getUniqueId()))
                    .replace("%odebrane%", String.valueOf(manager.ileOdebranych(player.getUniqueId()))));
        }
        inv.setItem(infoSlot, item(
                material(cfg.getString(infoPath + ".material"), admin ? Material.BOOK : Material.PLAYER_HEAD),
                cfg.getString(infoPath + ".nazwa", "&f临 &#FF0050&lᴋᴏɴᴛᴏ"),
                infoLore));

        if (strona > 0) {
            inv.setItem(cfg.getInt("gui.poprzednia.slot", 48), item(
                    material(cfg.getString("gui.poprzednia.material"), Material.ARROW),
                    cfg.getString("gui.poprzednia.nazwa", "&f七 &7ᴘᴏᴘʀᴢᴇᴅɴɪᴀ"),
                    List.of()));
        }
        if (strona < stron - 1) {
            inv.setItem(cfg.getInt("gui.nastepna.slot", 50), item(
                    material(cfg.getString("gui.nastepna.material"), Material.SPECTRAL_ARROW),
                    cfg.getString("gui.nastepna.nazwa", "&f丅 &7ɴᴀꜱᴛᴇᴘɴᴀ"),
                    List.of()));
        }
        inv.setItem(cfg.getInt("gui.zamknij.slot", 49), item(
                material(cfg.getString("gui.zamknij.material"), Material.BARRIER),
                cfg.getString("gui.zamknij.nazwa", "&f与 &#FF4D4D&lᴢᴀᴍᴋɴɪᴊ"),
                List.of()));

        player.openInventory(inv);
    }

    private ItemStack itemFilmu(TiktokManager.Film f, boolean admin) {
        FileConfiguration cfg = manager.getCfg();
        String klucz = switch (f.status) {
            case OCZEKUJE -> "oczekuje";
            case ZAAKCEPTOWANE -> "zaakceptowane";
            case ODRZUCONE -> "odrzucone";
            default -> "szkic";
        };
        Material mat = switch (f.status) {
            case OCZEKUJE -> Material.CLOCK;
            case ZAAKCEPTOWANE -> Material.LIME_DYE;
            case ODRZUCONE -> Material.RED_DYE;
            default -> Material.MAP;
        };
        String nick = manager.getKonto(f.wlasciciel) != null ? manager.getKonto(f.wlasciciel).tiktok : "";
        String tytul = (f.tytul == null || f.tytul.isEmpty()) ? "film" : f.tytul;
        String views = TiktokManager.formatujWyswietlenia(f.wyswietlenia);
        String urlKrotki = f.url != null && f.url.length() > 40 ? f.url.substring(0, 37) + "..." : (f.url == null ? "" : f.url);
        List<String> lore = new ArrayList<>();
        for (String linia : cfg.getStringList("film." + klucz + ".lore")) {
            lore.add(linia
                    .replace("%nick%", nick == null ? "-" : nick)
                    .replace("%gracz%", f.nickMinecraft == null ? "-" : f.nickMinecraft)
                    .replace("%url%", urlKrotki)
                    .replace("%tytul%", tytul)
                    .replace("%wyswietlenia%", views)
                    .replace("%nagroda%", manager.formatujVpln(manager.wyliczVpln(f, true)))
                    .replace("%opis-ok%", manager.opisNickStatus(f))
                    .replace("%wymagany-opis%", manager.wymaganyOpis(manager.nickGry(f)))
                    .replace("%booster%", manager.boosterStatus(f.wlasciciel)));
        }
        return item(
                material(cfg.getString("film." + klucz + ".material"), mat),
                cfg.getString("film." + klucz + ".nazwa", "&f东 &#FFC34D&lꜰɪʟᴍ"),
                lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        FileConfiguration cfg = manager.getCfg();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (slot == cfg.getInt("gui.zamknij.slot", 49)) {
            player.closeInventory();
            return;
        }
        if (slot == cfg.getInt("gui.poprzednia.slot", 48) && holder.strona > 0) {
            if (holder.admin) otworzAdmin(player, holder.strona - 1);
            else otworzGracza(player, holder.strona - 1, false);
            return;
        }
        if (slot == cfg.getInt("gui.nastepna.slot", 50)) {
            ItemStack klik = event.getCurrentItem();
            if (klik != null && klik.getType() == Material.SPECTRAL_ARROW) {
                if (holder.admin) otworzAdmin(player, holder.strona + 1);
                else otworzGracza(player, holder.strona + 1, false);
            }
            return;
        }

        int idxWSiatce = -1;
        for (int i = 0; i < SLOTY.length; i++) {
            if (SLOTY[i] == slot) {
                idxWSiatce = i;
                break;
            }
        }
        if (idxWSiatce < 0) return;
        int idx = holder.strona * SLOTY.length + idxWSiatce;
        if (idx < 0 || idx >= holder.ids.size()) return;
        String id = holder.ids.get(idx);
        TiktokManager.Film film = manager.znajdzFilm(id);
        if (film == null) return;

        ClickType click = event.getClick();
        if (holder.admin) {
            if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                player.sendMessage(manager.kolor(manager.txt("link-czat", "&f东 &7Film: &f%url%").replace("%url%", film.url)));
                return;
            }
            if (click.isLeftClick()) {
                player.sendMessage(manager.kolor(manager.zaakceptuj(player, id)));
                otworzAdmin(player, holder.strona);
            } else if (click.isRightClick()) {
                player.sendMessage(manager.kolor(manager.odrzuc(player, id)));
                otworzAdmin(player, holder.strona);
            }
            return;
        }

        if (click.isRightClick() || click == ClickType.SHIFT_LEFT) {
            player.sendMessage(manager.kolor(manager.txt("link-czat", "&f东 &7Film: &f%url%").replace("%url%", film.url)));
            return;
        }
        if (click.isLeftClick()) {
            player.sendMessage(manager.kolor(manager.wyslijDoWeryfikacji(player, id)));
            otworzGracza(player, holder.strona, false);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static ItemStack item(Material material, String nazwa, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Msg.color(nazwa));
        List<String> kolorowe = new ArrayList<>(lore.size());
        for (String linia : lore) kolorowe.add(Msg.color(linia));
        meta.setLore(kolorowe);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static Material material(String nazwa, Material domyslny) {
        Material m = nazwa == null ? null : Material.matchMaterial(nazwa);
        return (m != null && m.isItem()) ? m : domyslny;
    }
}
