package boniec.code.core;

import boniec.szaman.util.Msg;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class Osiagniecia implements CommandExecutor, TabCompleter, Listener {

    private static final int POZIOMOW_NA_STRONE = 18;
    private static final int SLOT_POPRZEDNIA = 27;
    private static final int SLOT_POWROT = 30;
    private static final int SLOT_ODBIERZ_WSZYSTKO = 32;
    private static final int SLOT_NASTEPNA = 35;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_PODSUMOWANIE = 40;

    private final boniec.dev.inventory.Main plugin;
    private final NamespacedKey blocksKey;
    private final NamespacedKey placedKey;
    private final NamespacedKey bossDmgKey;
    private final NamespacedKey killsKey;
    private Economy econ = null;
    private File configFile;
    private FileConfiguration config;
    private final Map<String, Double> baseMoney = new HashMap<>();
    private final Map<String, Map<Integer, List<ItemStack>>> itemRewards = new HashMap<>();
    private final Map<String, Kategoria> kategorie = new LinkedHashMap<>();
    private final Map<String, ToLongFunction<Player>> zewnetrzneZrodla = new HashMap<>();
    private final Map<UUID, Set<String>> powiadomione = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask zadaniePowiadomien;
    private String wiadomoscPowiadomienia;
    private String wiadomoscWiele;

    private ToIntFunction<Player> tierProvider = p -> 0;

    private static final class Kategoria {
        String id;
        String klucz;
        String nazwa;
        Material ikona;
        int slot;
        String zrodlo;
        Statistic statystyka;
        long dzielnik;
        String jednostka;
        double nagrodaBazowa;
        long[] progi;
        Map<Integer, List<String>> komendy = new HashMap<>();
        NamespacedKey claimedKey;
    }

    private static final class Holder implements InventoryHolder {
        final String kategoria;
        final int strona;
        final Map<Integer, Integer> slotNaPoziom = new HashMap<>();
        final Map<Integer, String> slotNaKategorie = new HashMap<>();
        Inventory inv;

        Holder(String kategoria, int strona) {
            this.kategoria = kategoria;
            this.strona = strona;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public Osiagniecia(boniec.dev.inventory.Main plugin) {
        this.plugin = plugin;
        this.blocksKey = new NamespacedKey(plugin, "osiagniecia_blocks");
        this.placedKey = new NamespacedKey(plugin, "osiagniecia_placed");
        this.bossDmgKey = new NamespacedKey(plugin, "osiagniecia_boss_dmg");
        this.killsKey = new NamespacedKey(plugin, "osiagniecia_kills");
        boniec.boss.BossObrazeniaHook.ustaw(this::dodajObrazeniaBossom);
        setupEconomy();
        createConfig();
        loadConfig();

        if (plugin.getCommand("osiagniecia") != null) {
            plugin.getCommand("osiagniecia").setExecutor(this);
            plugin.getCommand("osiagniecia").setTabCompleter(this);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private void createConfig() {
        configFile = new File(plugin.getDataFolder(), "osiagniecia.yml");
        if (!configFile.exists()) {
            plugin.saveResource("osiagniecia.yml", false);
        }
    }

    private void loadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        var zasob = plugin.getResource("osiagniecia.yml");
        if (zasob != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(zasob, StandardCharsets.UTF_8)));
            if (!config.contains("kategorie", true) || !config.contains("powiadomienia", true)) {
                config.options().copyDefaults(true);
                zapiszPlik();
                config.options().copyDefaults(false);
            }
        }

        baseMoney.clear();
        itemRewards.clear();
        kategorie.clear();

        if (config.isConfigurationSection("base_money")) {
            for (String key : config.getConfigurationSection("base_money").getKeys(false)) {
                baseMoney.put(key, config.getDouble("base_money." + key));
            }
        }

        if (config.isConfigurationSection("rewards")) {
            for (String category : config.getConfigurationSection("rewards").getKeys(false)) {
                Map<Integer, List<ItemStack>> categoryRewards = new HashMap<>();
                for (String levelStr : config.getConfigurationSection("rewards." + category).getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelStr);
                        List<ItemStack> items = new ArrayList<>();
                        List<?> raw = config.getList("rewards." + category + "." + levelStr);
                        if (raw != null) {
                            for (Object o : raw) {
                                if (o instanceof ItemStack) items.add((ItemStack) o);
                            }
                        }
                        categoryRewards.put(level, items);
                    } catch (NumberFormatException ignored) {}
                }
                itemRewards.put(category, categoryRewards);
            }
        }

        ConfigurationSection sekcja = config.getConfigurationSection("kategorie");
        if (sekcja != null) {
            for (String id : sekcja.getKeys(false)) {
                ConfigurationSection s = sekcja.getConfigurationSection(id);
                if (s == null) continue;
                Kategoria k = wczytajKategorie(id, s);
                if (k != null) kategorie.put(id.toLowerCase(Locale.ROOT), k);
            }
        }

        wiadomoscPowiadomienia = config.getString("powiadomienia.wiadomosc", "");
        wiadomoscWiele = config.getString("powiadomienia.wiele", "");
        uruchomPowiadomienia(config.getInt("powiadomienia.co-ile-sekund", 60));
    }

    private Kategoria wczytajKategorie(String id, ConfigurationSection s) {
        List<Long> progi = s.getLongList("progi");
        if (progi.isEmpty()) {
            plugin.getLogger().warning("[Osiagniecia] Kategoria " + id + " nie ma progow - pomijam.");
            return null;
        }
        Kategoria k = new Kategoria();
        k.id = id.toLowerCase(Locale.ROOT);
        k.klucz = s.getString("klucz", id);
        k.nazwa = s.getString("nazwa", id);
        Material ikona = Material.matchMaterial(s.getString("ikona", "PAPER"));
        k.ikona = ikona != null ? ikona : Material.PAPER;
        k.slot = s.getInt("slot", -1);
        k.zrodlo = s.getString("zrodlo", "STATYSTYKA").toUpperCase(Locale.ROOT);
        if (k.zrodlo.equals("STATYSTYKA")) {
            try {
                k.statystyka = Statistic.valueOf(s.getString("statystyka", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Osiagniecia] Kategoria " + id + ": nieznana statystyka - pomijam.");
                return null;
            }
            if (k.statystyka.getType() != Statistic.Type.UNTYPED) {
                plugin.getLogger().warning("[Osiagniecia] Kategoria " + id + ": statystyka musi byc bez typu (UNTYPED) - pomijam.");
                return null;
            }
        }
        k.dzielnik = Math.max(1L, s.getLong("dzielnik", 1L));
        k.jednostka = s.getString("jednostka", "");
        k.nagrodaBazowa = s.getDouble("nagroda-bazowa", 0.0);
        k.progi = progi.stream().mapToLong(Long::longValue).toArray();
        ConfigurationSection komendy = s.getConfigurationSection("komendy");
        if (komendy != null) {
            for (String lvl : komendy.getKeys(false)) {
                try {
                    k.komendy.put(Integer.parseInt(lvl), komendy.getStringList(lvl));
                } catch (NumberFormatException ignored) {}
            }
        }
        k.claimedKey = new NamespacedKey(plugin, "osiagniecia_claimed_" + getSafeCategoryKey(k.klucz));
        return k;
    }

    private void zapiszPlik() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        config.set("base_money", baseMoney);
        config.set("rewards", null);
        for (Map.Entry<String, Map<Integer, List<ItemStack>>> catEntry : itemRewards.entrySet()) {
            for (Map.Entry<Integer, List<ItemStack>> levelEntry : catEntry.getValue().entrySet()) {
                config.set("rewards." + catEntry.getKey() + "." + levelEntry.getKey(), levelEntry.getValue());
            }
        }
        zapiszPlik();
    }

    // ── Komendy ──────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Msg.color("&cTa komenda jest tylko dla graczy!"));
            return true;
        }
        Player p = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("admin") && p.hasPermission("core.adminosiagniecia")) {
            handleAdminCommand(p, args);
            return true;
        }

        openMainGui(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("core.adminosiagniecia")) return List.of();
        List<String> opcje;
        if (args.length == 1) opcje = List.of("admin");
        else if (args.length == 2 && args[0].equalsIgnoreCase("admin"))
            opcje = List.of("setmoney", "additem", "clearitems", "reload", "lista");
        else if (args.length == 3 && args[0].equalsIgnoreCase("admin"))
            opcje = new ArrayList<>(kategorie.keySet());
        else return List.of();
        String start = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opcje.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(start)).collect(Collectors.toList());
    }

    private void handleAdminCommand(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "&cUżycie: /osiagniecia admin <setmoney|additem|clearitems|reload|lista>");
            return;
        }

        if (args[1].equalsIgnoreCase("reload")) {
            loadConfig();
            Msg.send(p, "&aPrzeładowano konfigurację osiągnięć &7(" + kategorie.size() + " kategorii).");
            return;
        }

        if (args[1].equalsIgnoreCase("lista")) {
            Msg.send(p, "&6Kategorie osiągnięć:");
            for (Kategoria k : kategorie.values()) {
                Msg.send(p, "&8» &e" + k.id + " &7- " + k.nazwa + " &7(" + k.progi.length + " poz., "
                        + formatujLiczbe(getBaseMoney(k)) + "$/poz.)");
            }
            return;
        }

        if (args.length < 4) {
            Msg.send(p, "&cUżycie: /osiagniecia admin <akcja> <kategoria> <wartość/poziom>");
            Msg.send(p, "&7Kategorie: " + String.join(", ", kategorie.keySet()));
            return;
        }

        Kategoria k = mapCategory(args[2]);
        if (k == null) {
            Msg.send(p, "&cNieznana kategoria! Dostępne: " + String.join(", ", kategorie.keySet()));
            return;
        }

        if (args[1].equalsIgnoreCase("setmoney")) {
            try {
                double amount = Double.parseDouble(args[3]);
                baseMoney.put(k.klucz, amount);
                saveConfig();
                Msg.send(p, "&aUstawiono bazową nagrodę dla " + k.id + " na " + amount + "$ &7(× poziom)");
            } catch (NumberFormatException e) {
                Msg.send(p, "&cPodaj poprawną liczbę!");
            }
        } else if (args[1].equalsIgnoreCase("additem")) {
            Integer level = parsujPoziom(p, k, args[3]);
            if (level == null) return;
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                Msg.send(p, "&cMusisz trzymać przedmiot w ręce!");
                return;
            }
            itemRewards.computeIfAbsent(k.klucz, x -> new HashMap<>())
                    .computeIfAbsent(level, x -> new ArrayList<>())
                    .add(item.clone());
            saveConfig();
            Msg.send(p, "&aDodano przedmiot do nagród " + k.id + " poziom " + level);
        } else if (args[1].equalsIgnoreCase("clearitems")) {
            Integer level = parsujPoziom(p, k, args[3]);
            if (level == null) return;
            Map<Integer, List<ItemStack>> kat = itemRewards.get(k.klucz);
            if (kat != null && kat.remove(level) != null) {
                saveConfig();
                Msg.send(p, "&aWyczyszczono przedmioty dla " + k.id + " poziom " + level);
            } else {
                Msg.send(p, "&cBrak nagród przedmiotowych dla tego poziomu.");
            }
        } else {
            Msg.send(p, "&cNieznana akcja.");
        }
    }

    private Integer parsujPoziom(Player p, Kategoria k, String arg) {
        try {
            int level = Integer.parseInt(arg);
            if (level < 1 || level > k.progi.length) {
                Msg.send(p, "&cPoziom musi być w zakresie 1-" + k.progi.length + "!");
                return null;
            }
            return level;
        } catch (NumberFormatException e) {
            Msg.send(p, "&cPodaj poprawny poziom!");
            return null;
        }
    }

    private Kategoria mapCategory(String input) {
        String in = input.toLowerCase(Locale.ROOT);
        if (in.equals("tiery")) in = "ranking";
        else if (in.equals("bloki")) in = "wykopane_bloki";
        else if (in.equals("czas")) in = "czas_gry";
        Kategoria k = kategorie.get(in);
        if (k != null) return k;
        for (Kategoria kat : kategorie.values()) {
            if (getSafeCategoryKey(kat.klucz).equals(in)) return kat;
        }
        return null;
    }

    // ── GUI ──────────────────────────────────────────────────────────────────

    private void openMainGui(Player p) {
        Holder holder = new Holder(null, 0);
        Inventory inv = Bukkit.createInventory(holder, 45, Msg.color("&8ᴏsɪᴀɢɴɪᴇᴄɪᴀ"));
        holder.inv = inv;

        int wszystkie = 0;
        int odebraneLacznie = 0;
        int gotoweLacznie = 0;
        List<Integer> wolneSloty = new ArrayList<>(List.of(10, 12, 14, 16, 20, 22, 24, 28, 30, 32, 34, 19, 21, 23, 25));

        for (Kategoria k : kategorie.values()) {
            long wartosc = getWartosc(p, k);
            Set<Integer> odebrane = getOdebrane(p, k);
            int odebraneTu = (int) odebrane.stream().filter(l -> l >= 1 && l <= k.progi.length).count();
            int gotowe = 0;
            int nastepny = -1;
            for (int lvl = 1; lvl <= k.progi.length; lvl++) {
                boolean ukonczone = wartosc >= k.progi[lvl - 1];
                if (ukonczone && !odebrane.contains(lvl)) gotowe++;
                if (!ukonczone && nastepny == -1) nastepny = lvl;
            }
            wszystkie += k.progi.length;
            odebraneLacznie += odebraneTu;
            gotoweLacznie += gotowe;

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("&8☰ &fᴏᴅᴇʙʀᴀɴᴇ &b" + odebraneTu + "&8/&3" + k.progi.length);
            lore.add("&8☰ &fᴛᴡᴏᴊᴀ ᴡᴀʀᴛᴏść &e" + formatujLiczbe(wartosc) + " &7" + k.jednostka);
            if (nastepny != -1) {
                long cel = k.progi[nastepny - 1];
                lore.add("&8☰ &fɴᴀsᴛęᴘɴʏ ᴘʀóɢ &6" + formatujLiczbe(cel) + " &7(#" + nastepny + ")");
                lore.add("&8☰ " + getProgressBar(wartosc, cel));
            } else {
                lore.add("&8☰ &a&lᴡsᴢʏsᴛᴋᴏ ᴜᴋᴏńᴄᴢᴏɴᴇ!");
            }
            lore.add("");
            if (gotowe > 0) lore.add("&a&l» &aᴍᴀsᴢ " + gotowe + " ɴᴀɢʀóᴅ ᴅᴏ ᴏᴅᴇʙʀᴀɴɪᴀ!");
            lore.add("&8» &7ᴋʟɪᴋɴɪᴊ, ᴀʙʏ ᴏᴛᴡᴏʀᴢʏć");

            ItemStack item = createGuiItem(k.ikona, k.nazwa, lore.toArray(new String[0]));
            if (gotowe > 0) dodajPoswiate(item);

            int slot = k.slot;
            if (slot < 0 || slot >= 45 || inv.getItem(slot) != null || slot == SLOT_PODSUMOWANIE) {
                slot = -1;
                for (int s : wolneSloty) {
                    if (inv.getItem(s) == null) { slot = s; break; }
                }
                if (slot == -1) continue;
            }
            inv.setItem(slot, item);
            holder.slotNaKategorie.put(slot, k.id);
        }

        ItemStack info = createGuiItem(Material.KNOWLEDGE_BOOK, "&6&lᴛᴡᴏᴊᴇ ᴏsɪᴀɢɴɪęᴄɪᴀ",
                "",
                "&8☰ &fᴏᴅᴇʙʀᴀɴᴇ &b" + odebraneLacznie + "&8/&3" + wszystkie,
                "&8☰ " + getProgressBar(odebraneLacznie, Math.max(1, wszystkie)),
                "&8☰ &fᴅᴏ ᴏᴅᴇʙʀᴀɴɪᴀ &a" + gotoweLacznie,
                "");
        if (gotoweLacznie > 0) dodajPoswiate(info);
        inv.setItem(SLOT_PODSUMOWANIE, info);

        rysujRamke(inv);
        p.openInventory(inv);
    }

    private void openCategoryGui(Player p, Kategoria k, int strona) {
        int stron = Math.max(1, (k.progi.length + POZIOMOW_NA_STRONE - 1) / POZIOMOW_NA_STRONE);
        strona = Math.max(0, Math.min(strona, stron - 1));

        Holder holder = new Holder(k.id, strona);
        String tytul = "&8ᴏsɪᴀɢɴɪᴇᴄɪᴀ: " + Msg.color(k.nazwa).replaceAll("§[0-9a-fk-orx]", "");
        if (stron > 1) tytul += " &8(" + (strona + 1) + "/" + stron + ")";
        Inventory inv = Bukkit.createInventory(holder, 36, Msg.color(tytul));
        holder.inv = inv;

        long wartosc = getWartosc(p, k);
        Set<Integer> odebrane = getOdebrane(p, k);
        double bazowa = getBaseMoney(k);
        int gotowe = 0;

        for (int lvl = 1; lvl <= k.progi.length; lvl++) {
            if (wartosc >= k.progi[lvl - 1] && !odebrane.contains(lvl)) gotowe++;
        }

        int start = strona * POZIOMOW_NA_STRONE;
        for (int i = 0; i < POZIOMOW_NA_STRONE && start + i < k.progi.length; i++) {
            int level = start + i + 1;
            long goal = k.progi[level - 1];
            boolean completed = wartosc >= goal;
            boolean claimed = odebrane.contains(level);
            double reward = bazowa * level;

            Material mat = claimed ? Material.LIME_DYE : completed ? Material.EMERALD : Material.GRAY_DYE;

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("&8● &fᴡʏᴍᴀɢᴀɴɪᴇ: &6" + formatujLiczbe(goal) + " &7" + k.jednostka);
            lore.add("");
            lore.add("&8● &6&lɴᴀɢʀᴏᴅᴀ:");
            if (reward > 0) lore.add("&8» &a" + formatujLiczbe(reward) + "$");
            for (ItemStack is : getItemy(k, level)) {
                String itemName = is.hasItemMeta() && is.getItemMeta().hasDisplayName()
                        ? is.getItemMeta().getDisplayName() : is.getType().name();
                lore.add("&8» &f" + itemName + " &7x" + is.getAmount());
            }
            if (!k.komendy.getOrDefault(level, List.of()).isEmpty()) lore.add("&8» &dɴᴀɢʀᴏᴅᴀ sᴘᴇᴄᴊᴀʟɴᴀ");
            if (reward <= 0 && getItemy(k, level).isEmpty() && k.komendy.getOrDefault(level, List.of()).isEmpty()) {
                lore.add("&8» &7ʙʀᴀᴋ");
            }
            lore.add("");
            lore.add("&8● &fᴘᴏsᴛęᴘ &e" + formatujLiczbe(Math.min(wartosc, goal)) + "&8/&6" + formatujLiczbe(goal));
            lore.add("&8● " + getProgressBar(wartosc, goal));
            lore.add("");
            if (claimed) lore.add("&2✔ ᴏᴅᴇʙʀᴀɴᴏ");
            else if (completed) lore.add("&a» ᴋʟɪᴋɴɪᴊ, ᴀʙʏ ᴏᴅᴇʙʀᴀć");
            else lore.add("&c✘ ɴɪᴇ ᴜᴋᴏńᴄᴢᴏɴᴏ");

            ItemStack item = createGuiItem(mat, k.nazwa + " &f#" + level, lore.toArray(new String[0]));
            item.setAmount(Math.min(64, level));
            if (completed && !claimed) dodajPoswiate(item);
            int slot = 9 + i;
            inv.setItem(slot, item);
            holder.slotNaPoziom.put(slot, level);
        }

        inv.setItem(SLOT_INFO, createGuiItem(k.ikona, k.nazwa,
                "",
                "&8☰ &fᴛᴡᴏᴊᴀ ᴡᴀʀᴛᴏść &e" + formatujLiczbe(wartosc) + " &7" + k.jednostka,
                "&8☰ &fᴏᴅᴇʙʀᴀɴᴇ &b" + odebrane.stream().filter(l -> l >= 1 && l <= k.progi.length).count()
                        + "&8/&3" + k.progi.length,
                "&8☰ &fᴅᴏ ᴏᴅᴇʙʀᴀɴɪᴀ &a" + gotowe,
                ""));

        inv.setItem(SLOT_POWROT, createGuiItem(Material.ARROW, "&cᴘᴏᴡʀóᴛ", "", "&8» &7ᴡʀóć ᴅᴏ ᴍᴇɴᴜ"));
        if (gotowe > 0) {
            ItemStack wszystko = createGuiItem(Material.CHEST_MINECART, "&a&lᴏᴅʙɪᴇʀᴢ ᴡsᴢʏsᴛᴋᴏ",
                    "", "&8» &7ᴅᴏsᴛęᴘɴᴇ ɴᴀɢʀᴏᴅʏ: &a" + gotowe, "", "&a» ᴋʟɪᴋɴɪᴊ, ᴀʙʏ ᴏᴅᴇʙʀᴀć");
            dodajPoswiate(wszystko);
            inv.setItem(SLOT_ODBIERZ_WSZYSTKO, wszystko);
        }
        if (strona > 0) {
            inv.setItem(SLOT_POPRZEDNIA, createGuiItem(Material.SPECTRAL_ARROW, "&eᴘᴏᴘʀᴢᴇᴅɴɪᴀ sᴛʀᴏɴᴀ"));
        }
        if (strona < stron - 1) {
            inv.setItem(SLOT_NASTEPNA, createGuiItem(Material.SPECTRAL_ARROW, "&eɴᴀsᴛęᴘɴᴀ sᴛʀᴏɴᴀ"));
        }

        ItemStack pane = createGuiItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) if (inv.getItem(i) == null) inv.setItem(i, pane);
        for (int i = 27; i < 36; i++) if (inv.getItem(i) == null) inv.setItem(i, pane);

        p.openInventory(inv);
    }

    private void rysujRamke(Inventory inv) {
        ItemStack cyan = createGuiItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        ItemStack blue = createGuiItem(Material.BLUE_STAINED_GLASS_PANE, " ");
        ItemStack white = createGuiItem(Material.WHITE_STAINED_GLASS_PANE, " ");
        int[] cyanSlots = {0, 8, 36, 44};
        int[] blueSlots = {1, 2, 6, 7, 9, 17, 27, 35, 37, 38, 42, 43};
        int[] whiteSlots = {3, 4, 5, 39, 41};
        for (int s : cyanSlots) if (inv.getItem(s) == null) inv.setItem(s, cyan);
        for (int s : blueSlots) if (inv.getItem(s) == null) inv.setItem(s, blue);
        for (int s : whiteSlots) if (inv.getItem(s) == null) inv.setItem(s, white);
        fillBg(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof Holder)) return;
        int slot = e.getRawSlot();

        if (holder.kategoria == null) {
            String id = holder.slotNaKategorie.get(slot);
            Kategoria k = id != null ? kategorie.get(id) : null;
            if (k != null) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                openCategoryGui(p, k, 0);
            }
            return;
        }

        Kategoria k = kategorie.get(holder.kategoria);
        if (k == null) {
            p.closeInventory();
            return;
        }

        if (slot == SLOT_POWROT) {
            openMainGui(p);
        } else if (slot == SLOT_POPRZEDNIA && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.SPECTRAL_ARROW) {
            openCategoryGui(p, k, holder.strona - 1);
        } else if (slot == SLOT_NASTEPNA && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.SPECTRAL_ARROW) {
            openCategoryGui(p, k, holder.strona + 1);
        } else if (slot == SLOT_ODBIERZ_WSZYSTKO && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHEST_MINECART) {
            long wartosc = getWartosc(p, k);
            int ile = 0;
            for (int lvl = 1; lvl <= k.progi.length; lvl++) {
                if (wartosc >= k.progi[lvl - 1] && !isClaimed(p, k, lvl)) {
                    odbierz(p, k, lvl);
                    ile++;
                }
            }
            if (ile > 0) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            openCategoryGui(p, k, holder.strona);
        } else {
            Integer level = holder.slotNaPoziom.get(slot);
            if (level == null) return;
            if (getWartosc(p, k) >= k.progi[level - 1] && !isClaimed(p, k, level)) {
                odbierz(p, k, level);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                openCategoryGui(p, k, holder.strona);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void odbierz(Player p, Kategoria k, int level) {
        setClaimed(p, k, level);

        double reward = getBaseMoney(k) * level;
        if (reward > 0) {
            if (econ != null) {
                econ.depositPlayer(p, reward);
                Msg.send(p, "&8» &aᴏᴅᴇʙʀᴀɴᴏ ɴᴀɢʀᴏᴅę: &6" + formatujLiczbe(reward) + "$ &8(" + k.nazwa + " &f#" + level + "&8)");
            } else {
                Msg.send(p, "&cBłąd ekonomii! Skontaktuj się z administracją.");
            }
        }

        List<ItemStack> items = getItemy(k, level);
        boolean upuszczono = false;
        for (ItemStack is : items) {
            for (ItemStack reszta : p.getInventory().addItem(is.clone()).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), reszta);
                upuszczono = true;
            }
        }
        if (!items.isEmpty()) Msg.send(p, "&8» &aᴏᴛʀᴢʏᴍᴀɴᴏ ᴘʀᴢᴇᴅᴍɪᴏᴛʏ!");
        if (upuszczono) Msg.send(p, "&eEkwipunek pełny! Część przedmiotów upuszczono na ziemię.");

        for (String cmd : k.komendy.getOrDefault(level, List.of())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }

        Set<String> zgloszone = powiadomione.get(p.getUniqueId());
        if (zgloszone != null) zgloszone.remove(k.id + ":" + level);
    }

    // ── Powiadomienia ────────────────────────────────────────────────────────

    private void uruchomPowiadomienia(int coIleSekund) {
        if (zadaniePowiadomien != null) {
            zadaniePowiadomien.cancel();
            zadaniePowiadomien = null;
        }
        if (coIleSekund <= 0) return;
        long ticki = coIleSekund * 20L;
        zadaniePowiadomien = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sprawdzGotowe(p);
        }, ticki, ticki);
    }

    private void sprawdzGotowe(Player p) {
        Set<String> zgloszone = powiadomione.computeIfAbsent(p.getUniqueId(), u -> new HashSet<>());
        List<String> nowe = new ArrayList<>();
        Kategoria ostatnia = null;
        int ostatniPoziom = 0;
        for (Kategoria k : kategorie.values()) {
            long wartosc = getWartosc(p, k);
            Set<Integer> odebrane = null;
            for (int lvl = 1; lvl <= k.progi.length; lvl++) {
                if (wartosc < k.progi[lvl - 1]) break;
                String id = k.id + ":" + lvl;
                if (zgloszone.contains(id)) continue;
                if (odebrane == null) odebrane = getOdebrane(p, k);
                if (odebrane.contains(lvl)) continue;
                zgloszone.add(id);
                nowe.add(id);
                ostatnia = k;
                ostatniPoziom = lvl;
            }
        }
        if (nowe.isEmpty()) return;
        if (nowe.size() == 1) {
            if (wiadomoscPowiadomienia.isEmpty()) return;
            Msg.send(p, wiadomoscPowiadomienia.replace("{nazwa}", ostatnia.nazwa)
                    .replace("{poziom}", String.valueOf(ostatniPoziom)));
        } else {
            if (wiadomoscWiele.isEmpty()) return;
            Msg.send(p, wiadomoscWiele.replace("{ilosc}", String.valueOf(nowe.size())));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        powiadomione.remove(e.getPlayer().getUniqueId());
    }

    // ── Liczniki ─────────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent e) {
        PersistentDataContainer data = e.getPlayer().getPersistentDataContainer();
        int current = data.getOrDefault(blocksKey, PersistentDataType.INTEGER, 0);
        data.set(blocksKey, PersistentDataType.INTEGER, current + 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent e) {
        PersistentDataContainer data = e.getPlayer().getPersistentDataContainer();
        int current = data.getOrDefault(placedKey, PersistentDataType.INTEGER, 0);
        data.set(placedKey, PersistentDataType.INTEGER, current + 1);
    }

    public void dodajObrazeniaBossom(Player p, double obrazenia) {
        PersistentDataContainer data = p.getPersistentDataContainer();
        double current = data.getOrDefault(bossDmgKey, PersistentDataType.DOUBLE, 0.0);
        data.set(bossDmgKey, PersistentDataType.DOUBLE, current + obrazenia);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            int current = getKills(killer);
            killer.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, current + 1);
        }
    }

    // ── Zrodla danych ────────────────────────────────────────────────────────

    public void setTierProvider(ToIntFunction<Player> provider) {
        this.tierProvider = provider != null ? provider : p -> 0;
    }

    /** Podpina zewnetrzne zrodlo postepu, np. "POZIOM" z modulu poziomow. */
    public void setZrodlo(String nazwa, ToLongFunction<Player> zrodlo) {
        if (zrodlo == null) zewnetrzneZrodla.remove(nazwa.toUpperCase(Locale.ROOT));
        else zewnetrzneZrodla.put(nazwa.toUpperCase(Locale.ROOT), zrodlo);
    }

    private long getWartosc(Player p, Kategoria k) {
        switch (k.zrodlo) {
            case "ZABOJSTWA": return getKills(p);
            case "RANKING": return tierProvider.applyAsInt(p);
            case "WYKOPANE_BLOKI": return getMinedBlocks(p);
            case "POSTAWIONE_BLOKI": return p.getPersistentDataContainer().getOrDefault(placedKey, PersistentDataType.INTEGER, 0);
            case "CZAS_GRY": return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 3600L;
            case "BOSS_DMG": return (long) p.getPersistentDataContainer().getOrDefault(bossDmgKey, PersistentDataType.DOUBLE, 0.0).doubleValue();
            case "STATYSTYKA": return k.statystyka != null ? p.getStatistic(k.statystyka) / k.dzielnik : 0;
            default: {
                ToLongFunction<Player> zrodlo = zewnetrzneZrodla.get(k.zrodlo);
                return zrodlo != null ? zrodlo.applyAsLong(p) : 0;
            }
        }
    }

    private int getKilleZKlanow(Player p) {
        boniec.klany.Main klany = boniec.klany.Main.get();
        if (klany == null || klany.getKlanManager() == null) return 0;
        return klany.getKlanManager().getZabojstwaGracza(p.getUniqueId());
    }

    public int getMinedBlocks(Player p) {
        return p.getPersistentDataContainer().getOrDefault(blocksKey, PersistentDataType.INTEGER, 0);
    }

    public int getKills(Player p) {
        PersistentDataContainer data = p.getPersistentDataContainer();
        if (!data.has(killsKey, PersistentDataType.INTEGER)) {
            int klanKills = getKilleZKlanow(p);
            if (klanKills > 0) data.set(killsKey, PersistentDataType.INTEGER, klanKills);
        }
        return data.getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
    }

    // ── Nagrody i dane gracza ────────────────────────────────────────────────

    private double getBaseMoney(Kategoria k) {
        return baseMoney.getOrDefault(k.klucz, k.nagrodaBazowa);
    }

    private List<ItemStack> getItemy(Kategoria k, int level) {
        Map<Integer, List<ItemStack>> kat = itemRewards.get(k.klucz);
        if (kat == null) return List.of();
        return kat.getOrDefault(level, List.of());
    }

    private String getSafeCategoryKey(String category) {
        return category.toLowerCase()
                .replace(" ", "_")
                .replace("ą", "a")
                .replace("ć", "c")
                .replace("ę", "e")
                .replace("ł", "l")
                .replace("ń", "n")
                .replace("ó", "o")
                .replace("ś", "s")
                .replace("ź", "z")
                .replace("ż", "z");
    }

    private Set<Integer> getOdebrane(Player p, Kategoria k) {
        String claimed = p.getPersistentDataContainer().getOrDefault(k.claimedKey, PersistentDataType.STRING, "");
        Set<Integer> wynik = new HashSet<>();
        if (claimed.isEmpty()) return wynik;
        for (String s : claimed.split(",")) {
            try {
                wynik.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return wynik;
    }

    private boolean isClaimed(Player p, Kategoria k, int level) {
        return getOdebrane(p, k).contains(level);
    }

    private void setClaimed(Player p, Kategoria k, int level) {
        PersistentDataContainer data = p.getPersistentDataContainer();
        String claimed = data.getOrDefault(k.claimedKey, PersistentDataType.STRING, "");
        if (Arrays.asList(claimed.split(",")).contains(String.valueOf(level))) return;
        data.set(k.claimedKey, PersistentDataType.STRING, claimed.isEmpty() ? String.valueOf(level) : claimed + "," + level);
    }

    // ── Pomocnicze ───────────────────────────────────────────────────────────

    private String getProgressBar(long current, long max) {
        int totalBars = 10;
        double percent = max <= 0 ? 1.0 : Math.min(1.0, (double) current / max);
        int filledBars = (int) (percent * totalBars);
        StringBuilder sb = new StringBuilder("&8[");
        for (int i = 0; i < totalBars; i++) sb.append(i < filledBars ? "&a:" : "&c:");
        sb.append("&8] &f").append(String.format(Locale.US, "%.1f", percent * 100)).append("%");
        return sb.toString();
    }

    private String formatujLiczbe(double liczba) {
        return String.format(Locale.US, "%,.0f", liczba).replace(',', ' ');
    }

    private void dodajPoswiate(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Msg.color(name));
        List<String> loreList = new ArrayList<>();
        for (String s : lore) loreList.add(Msg.color(s));
        meta.setLore(loreList);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBg(Inventory inv) {
        ItemStack glass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }
}
