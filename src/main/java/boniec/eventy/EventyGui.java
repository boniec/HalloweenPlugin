package boniec.eventy;

import boniec.boss.GoblinManager;
import boniec.boss.MinotaurManager;
import boniec.boss.ZeusManager;
import boniec.code.core.MeteoriteManager;
import boniec.dev.inventory.Main;
import boniec.eventodlamkow.EventOdlamkowManager;
import boniec.klany.util.Msg;
import boniec.koth.KothManager;
import boniec.serce.SerceManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /eventy — skrzynia z eventami, opisem startu i live statusem. */
public final class EventyGui implements Listener {

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Main plugin;
    private final EventyCommand command;

    public EventyGui(Main plugin, EventyCommand command) {
        this.plugin = plugin;
        this.command = command;
    }

    public void otworz(Player player) {
        FileConfiguration cfg = command.getCfg();
        int size = cfg.getInt("gui.wielkosc", 54);
        if (size != 9 && size != 18 && size != 27 && size != 36 && size != 45 && size != 54) {
            size = 54;
        }

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, size, Msg.color(command.txt("gui.tytul", "&f丮 &#FFC34D&lᴇᴠᴇɴᴛʏ")));
        holder.inventory = inv;

        ItemStack ramka = item(
                material(cfg.getString("gui.ramka"), Material.BLACK_STAINED_GLASS_PANE),
                " ",
                List.of());
        for (int i = 0; i < size; i++) {
            inv.setItem(i, ramka);
        }

        wstaw(inv, size, "koth", Material.RED_BANNER, statusKoth(), kiedyKoth(), czasTrwaniaKoth());
        wstaw(inv, size, "serce", Material.HEART_OF_THE_SEA, statusSerce(), kiedySerce(), czasTrwaniaSerce());
        wstaw(inv, size, "meteoryty", Material.FIRE_CHARGE, statusMeteoryty(), kiedyMeteoryty(), "");
        wstaw(inv, size, "minotaur", Material.IRON_AXE, statusMinotaur(), kiedyBoss(plugin.getMinotaurManager()), "");
        wstaw(inv, size, "zeus", Material.LIGHTNING_ROD, statusZeus(), kiedyBoss(plugin.getZeusManager()), "");
        wstaw(inv, size, "goblin", Material.EMERALD, statusGoblin(), kiedyBoss(plugin.getGoblinManager()), "");
        wstaw(inv, size, "szal", Material.AMETHYST_SHARD, statusSzal(), kiedySzal(), czasTrwaniaSzal());

        int slotZamknij = cfg.getInt("gui.zamknij.slot", 49);
        if (slotZamknij >= 0 && slotZamknij < size) {
            inv.setItem(slotZamknij, item(
                    material(cfg.getString("gui.zamknij.material"), Material.BARRIER),
                    command.txt("gui.zamknij.nazwa", "&c与 ᴢᴀᴍᴋɴɪᴊ"),
                    cfg.getStringList("gui.zamknij.lore")));
        }

        player.openInventory(inv);
    }

    private void wstaw(Inventory inv, int size, String klucz, Material domyslny,
                       String status, String kiedy, String czasTrwania) {
        FileConfiguration cfg = command.getCfg();
        int slot = cfg.getInt("eventy." + klucz + ".slot", -1);
        if (slot < 0 || slot >= size) return;

        String ikona = command.txt("eventy." + klucz + ".ikona", "");
        String nazwa = command.txt("eventy." + klucz + ".nazwa", klucz);
        String tytul = "&f" + ikona + " " + nazwa;

        List<String> lore = new ArrayList<>();
        for (String linia : cfg.getStringList("eventy." + klucz + ".lore")) {
            lore.add(linia
                    .replace("%status%", status)
                    .replace("%kiedy%", kiedy)
                    .replace("%czas-trwania%", czasTrwania));
        }
        inv.setItem(slot, item(material(cfg.getString("eventy." + klucz + ".material"), domyslny), tytul, lore));
    }

    String statusKoth() {
        KothManager koth = plugin.getKothManager();
        if (koth == null) return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        return switch (koth.getStan()) {
            case TRWA -> command.txt("status.trwa", "&a両 ᴛʀᴡᴀ &8| &f东 %czas%").replace("%czas%", koth.czasPozostaly());
            case ODLICZANIE -> command.txt("status.odliczanie", "&e亢 ꜱᴛᴀʀᴛ ᴢᴀ &8| &f东 %czas%").replace("%czas%", koth.czasPozostaly());
            case PO_ZAKONCZENIU -> command.txt("status.koniec", "&6东 ᴢᴀᴋᴏɴᴄᴢᴏɴʏ");
            default -> command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        };
    }

    String statusSerce() {
        SerceManager serce = plugin.getSerceManager();
        if (serce == null) return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        return switch (serce.getStan()) {
            case TRWA -> command.txt("status.trwa", "&a両 ᴛʀᴡᴀ &8| &f东 %czas%").replace("%czas%", serce.czasPozostaly());
            case PO_ZAKONCZENIU -> command.txt("status.koniec", "&6东 ᴢᴀᴋᴏɴᴄᴢᴏɴʏ");
            default -> command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        };
    }

    String statusMeteoryty() {
        MeteoriteManager m = plugin.getMeteoriteManager();
        if (m == null) return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        if (m.isActive()) {
            return command.txt("status.meteoryty-trwaja", "&a両 ᴛʀᴡᴀ &8| &f%ilosc% &7aktywnych")
                    .replace("%ilosc%", String.valueOf(m.getAktywneIlosc()));
        }
        String czas = m.getNextSpawnTimeFormatted();
        if (czas == null || czas.isEmpty() || czas.equals("Do zrespienia")) {
            return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        }
        return command.txt("status.za", "&7za &e%czas%").replace("%czas%", czas);
    }

    String statusMinotaur() {
        MinotaurManager m = plugin.getMinotaurManager();
        if (m == null) return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        return statusBossa(m.isAlive(), m.getSekundyDoRespawnu(), m.formatujCzasDoRespawnu(), m.czyMaZapisanySpawn());
    }

    String statusZeus() {
        ZeusManager m = plugin.getZeusManager();
        if (m == null) return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        return statusBossa(m.isAlive(), m.getSekundyDoRespawnu(), m.formatujCzasDoRespawnu(), m.czyMaZapisanySpawn());
    }

    String statusGoblin() {
        GoblinManager m = plugin.getGoblinManager();
        if (m == null) return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        return statusBossa(m.isAlive(), m.getSekundyDoRespawnu(), m.formatujCzasDoRespawnu(), m.czyMaZapisanySpawn());
    }

    private String statusBossa(boolean zyje, long sekundyDo, String czas, boolean maSpawn) {
        if (zyje) return command.txt("status.zyje", "&a両 ᴢʏᴊᴇ");
        if (sekundyDo >= 0) return command.txt("status.za", "&7za &e%czas%").replace("%czas%", czas);
        if (!maSpawn) return command.txt("status.brak-spawnu", "&8ʙʀᴀᴋ ꜱᴘᴀᴡɴᴜ");
        return command.txt("status.martwy", "&c与 ᴍᴀʀᴛᴡʏ");
    }

    String statusSzal() {
        EventOdlamkowManager e = plugin.getEventOdlamkowManager();
        if (e == null) return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
        if (e.isActive()) {
            return command.txt("status.trwa", "&a両 ᴛʀᴡᴀ &8| &f东 %czas%")
                    .replace("%czas%", EventyCommand.formatujCzas(e.getPozostaloSekund()));
        }
        long doStartu = e.getSekundyDoNastepnegoStartu();
        if (doStartu >= 0) {
            return command.txt("status.za", "&7za &e%czas%").replace("%czas%", EventyCommand.formatujCzas((int) doStartu));
        }
        return command.txt("status.nieaktywny", "&8ɴɪᴇᴀᴋᴛʏᴡɴʏ");
    }

    private String kiedyKoth() {
        return EventHarmonogram.opis(EventHarmonogram.KOTH);
    }

    private String czasTrwaniaKoth() {
        KothManager koth = plugin.getKothManager();
        int sek = koth != null && koth.getCfg() != null ? koth.getCfg().getInt("czas-trwania-sekundy", 300) : 300;
        return EventyCommand.formatujCzas(sek);
    }

    private String kiedySerce() {
        return EventHarmonogram.opis(EventHarmonogram.SERCE);
    }

    private String czasTrwaniaSerce() {
        SerceManager serce = plugin.getSerceManager();
        int sek = serce != null && serce.getCfg() != null ? serce.getCfg().getInt("czas-trwania-sekundy", 1200) : 1200;
        return EventyCommand.formatujCzas(sek);
    }

    private String kiedyMeteoryty() {
        return EventHarmonogram.opis(EventHarmonogram.METEORYTY);
    }

    private String kiedyBoss(Object manager) {
        if (manager instanceof MinotaurManager) return EventHarmonogram.opis(EventHarmonogram.MINOTAUR);
        if (manager instanceof ZeusManager) return EventHarmonogram.opis(EventHarmonogram.ZEUS);
        if (manager instanceof GoblinManager) return EventHarmonogram.opis(EventHarmonogram.GOBLIN);
        return EventHarmonogram.opis(EventHarmonogram.MINOTAUR);
    }

    private String kiedySzal() {
        return EventHarmonogram.opis(EventHarmonogram.SZAL);
    }

    private String czasTrwaniaSzal() {
        EventOdlamkowManager e = plugin.getEventOdlamkowManager();
        int sek = e != null && e.getEventConfig() != null
                ? e.getEventConfig().getInt("harmonogram.czas-trwania-sekund", 300)
                : 300;
        return EventyCommand.formatujCzas(sek);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == command.getCfg().getInt("gui.zamknij.slot", 49)) {
            player.closeInventory();
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
        for (String linia : lore) {
            kolorowe.add(Msg.color(linia));
        }
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
