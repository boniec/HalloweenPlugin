package boniec.dev.polezamek.eventitems; // NO_BOM

import boniec.dev.polezamek.Main;
import boniec.dev.polezamek.util.ItemTagUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EventItemsManager {

    public static final Material KOKOS_MATERIAL = Material.ENCHANTED_GOLDEN_APPLE;
    public static final int KOKOS_CUSTOM_MODEL_DATA = 1001;

    public static final Material LAMPION_MATERIAL = Material.LANTERN;
    public static final int LAMPION_CUSTOM_MODEL_DATA = 1;

    public static final Material ANTYCOBWEB_MATERIAL = Material.BLAZE_ROD;
    public static final int ANTYCOBWEB_CUSTOM_MODEL_DATA = 1001;

    public static final Material OLEJ_MATERIAL = Material.SOUL_LANTERN;
    public static final int OLEJ_CUSTOM_MODEL_DATA = 2001;

    public static final Material MOC_VADERA_MATERIAL = Material.RED_DYE;
    public static final int MOC_VADERA_CUSTOM_MODEL_DATA = 3001;

    public static final Material MOC_YODY_MATERIAL = Material.GREEN_DYE;
    public static final int MOC_YODY_CUSTOM_MODEL_DATA = 4001;

    public static final Material CUKIERKI_MATERIAL = Material.SPIDER_EYE;
    public static final int CUKIERKI_CUSTOM_MODEL_DATA = 5001;

    public static final Material JABLKO_JEDI_MATERIAL = Material.GOLDEN_APPLE;
    public static final int JABLKO_JEDI_CUSTOM_MODEL_DATA = 1002;

    public static final Material KAMIEN_ODRODZENIA_MATERIAL = Material.MAGMA_CREAM;
    public static final int KAMIEN_ODRODZENIA_CUSTOM_MODEL_DATA = 6001;

    public static final Material KOZA_MATERIAL = Material.LEATHER_HORSE_ARMOR;
    public static final int KOZA_CUSTOM_MODEL_DATA = 7001;

    public static final Material EVENTOWKA_RANDOM_MATERIAL = Material.AMETHYST_CLUSTER;
    public static final int EVENTOWKA_RANDOM_CUSTOM_MODEL_DATA = 8001;

    public static final Material SAKIEWKA_MATERIAL = Material.BROWN_SHULKER_BOX;
    public static final int SAKIEWKA_CUSTOM_MODEL_DATA = 9001;

    public static final Material SMOCZA_FAJERWERKA_MATERIAL = Material.FIREWORK_ROCKET;
    public static final int SMOCZA_FAJERWERKA_CUSTOM_MODEL_DATA = 0;

    public static final Material KOSA_GRABAZA_MATERIAL = Material.NETHERITE_HOE;
    public static final int KOSA_GRABAZA_CUSTOM_MODEL_DATA = 1001;

    public static final Material GWOZDZ_TRUMNY_MATERIAL = Material.IRON_NUGGET;
    public static final int GWOZDZ_TRUMNY_CUSTOM_MODEL_DATA = 1001;
    private static final int SAKIEWKA_SLOTOW = 27;

    /** CustomModelData kazdego itemu (klucz = nazwa sekcji w eventowki.yml, 0 = brak). */
    private final Map<String, Integer> cmds = new java.util.HashMap<>();

    private Main plugin;
    private NamespacedKey itemKey;

    // Nazwy
    private String kokosName;
    private String lampionName;
    private String antycobwebName;
    private String olejName;
    private String mocVaderName;
    private String mocYodyName;
    private String cukierkiName;
    private String jablkoJediName;
    private String kamienOdrodzeniaName;
    private String kozaName;
    private String eventowkaRandomName;
    private String sakiewkaName;
    private String smoczaFajerwerkaName;
    private String kosaGrabazaName;
    private String gwozdzTrumnyName;

    // Ustawienia
    private int lampionCooldownSeconds;
    private int antycobwebCooldownSeconds;
    private int antycobwebRadius;
    private int kokosRegenAmplifier;
    private int kokosRegenDuration;
    private int kokosAbsorptionAmplifier;
    private int kokosAbsorptionDuration;
    private int kokosSpeedAmplifier;
    private int kokosSpeedDuration;
    private int lampionRegenAmplifier;
    private int lampionRegenDuration;
    
    private int olejCooldownSeconds;
    private int olejRegenAmplifier;
    private int olejRegenDuration;
    private int mocVaderCooldownSeconds;
    private int mocVaderDuration;
    private int mocYodyCooldownSeconds;
    private int mocYodyLuckDuration;
    private int cukierkiCooldownSeconds;
    private int cukierkiStrengthDuration;
    private int cukierkiStrengthAmplifier;
    private int jablkoJediRegenAmplifier;
    private int jablkoJediRegenDuration;
    private int jablkoJediAbsorptionAmplifier;
    private int jablkoJediAbsorptionDuration;
    private double jablkoJediBonusChance;
    private int jablkoJediBonusAbsorptionAmplifier;
    private int kamienOdrodzeniaCooldownSeconds;
    private int kamienOdrodzeniHearts;
    private int kozaCooldownSeconds;
    private int kozaCelCooldownSeconds;
    private int kozaWorldBorderDuration;
    private int kozaWorldBorderSize;
    /** Dodatek do srednicy WorldBorder (vanilla blokuje stawianie ~1 kratke przed linia). */
    private int kozaWorldBorderPlaceExtra;
    private int eventowkaRandomCooldownSeconds;
    private int eventowkaRandomEffectDuration;
    private boolean sakiewkaEnabled;
    private Material sakiewkaMaterial;
    private int kosaGrabazaCooldownSeconds;
    private int kosaGrabazaWeaknessDuration;
    private int kosaGrabazaGlowingDuration;
    private int kosaGrabazaFireworkBlockSeconds;
    private int kosaGrabazaHearts;
    private int gwozdzTrumnyCooldownSeconds;
    private int gwozdzTrumnyFreezeSeconds;
    private int gwozdzTrumnyWeaknessDuration;
    private int gwozdzTrumnyWeaknessAmplifier;

    // Wiadomości
    private String msgKokosEaten;
    private String msgKokosCooldown;
    private String msgLampionCooldown;
    private String msgLampionActivated;
    private String msgAntycobwebCooldown;
    private String msgAntycobwebRemoved;
    private String msgAntycobwebNone;
    private String msgOlejCooldown;
    private String msgOlejActivated;
    private String msgMocVaderCooldown;
    private String msgMocVaderActivated;
    private String msgMocYodyCooldown;
    private String msgMocYodyActivated;
    private String msgCukierkiCooldown;
    private String msgCukierkiActivated;
    private String msgJablkoJediEaten;
    private String msgJablkoJediBonus;
    private String msgKamienOdrodzeniaCooldown;
    private String msgKamienOdrodzeniActivated;
    private String msgKozaCooldown;
    private String msgKozaActivated;
    private String msgKozaTooFar;
    private String msgKozaBusy;
    private String msgKozaElytra;
    private String msgKozaAlreadyUsed;
    private String msgEventowkaRandomCooldown;
    private String msgEventowkaRandomActivated;
    private String msgSakiewkaReceived;
    private String msgSakiewkaFull;
    private String msgKosaGrabazaCooldown;
    private String msgKosaGrabazaHit;
    private String msgKosaGrabazaVictim;
    private String msgKosaGrabazaFajerwerka;
    private String msgGwozdzTrumnyCooldown;
    private String msgGwozdzTrumnyUsed;
    private String msgGwozdzTrumnyVictim;
    private String msgGuiTitle;
    private String msgGuiReceived;

    // Lore
    private List<String> kokosLore;
    private List<String> lampionLore;
    private List<String> antycobwebLore;
    private List<String> olejLore;
    private List<String> mocVaderLore;
    private List<String> mocYodyLore;
    private List<String> cukierkiLore;
    private List<String> jablkoJediLore;
    private List<String> kamienOdrodzeniLore;
    private List<String> kozaLore;
    private List<String> eventowkaRandomLore;
    private List<String> sakiewkaLore;
    private List<String> sakiewkaLootLore;
    private List<String> smoczaFajerwerkaLore;
    private List<String> kosaGrabazaLore;
    private List<String> gwozdzTrumnyLore;

    private final Map<UUID, Long> lampionCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> antycobwebCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> olejCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mocVaderCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mocYodyCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cukierkiCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> kamienOdrodzeniaCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> kozaCooldowns = new ConcurrentHashMap<>();
    /** Cooldown na CELU kozy - ten sam gracz nie moze byc zamkniety na arenie ponownie przed uplywem czasu. */
    private final Map<UUID, Long> kozaCelCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> eventowkaRandomCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> kosaGrabazaCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> gwozdzTrumnyCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blokadaFajerwerki = new ConcurrentHashMap<>();
    private final Map<UUID, Long> zamrozenie = new ConcurrentHashMap<>();

    public EventItemsManager(Main plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    public void reloadConfig(Main plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin.getHost(), "event_item");
        loadConfig(plugin);
    }

    private void loadConfig(Main plugin) {
        // Itemy eventowe czytamy z osobnego pliku eventowki.yml (folder pluginu).
        File eventFile = new File(plugin.getDataFolder(), "eventowki.yml");
        if (!eventFile.exists()) {
            plugin.saveResource("eventowki.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(eventFile);

        // Nazwy
        this.kokosName = col(cfg.getString("kokos.name", "&f&lᴋᴏᴋᴏꜱ"));
        this.lampionName = col(cfg.getString("lampion.name", "&6&lʟᴀᴍᴘɪᴏɴ"));
        this.antycobwebName = col(cfg.getString("antycobweb.name", "&6Usuwacz pajęczyn"));
        this.olejName = col(cfg.getString("olej.name", "&x&6&0&E&5&B&2ᴏ&x&6&0&E&8&A&Dʟ&x&6&0&E&B&A&8ᴇ&x&6&0&E&E&A&2ⱼ &x&6&0&D&5&C&Cᴄ&x&6&0&D&8&C&6-&x&6&0&D&B&C&1&l3&x&6&0&D&E&B&Cᴘ&x&6&0&E&2&B&7ᴏ"));
        this.mocVaderName = col(cfg.getString("moc-vadera.name", "&x&F&6&3&0&3&0&lᴍ&x&F&5&3&5&3&5&lᴏ&x&F&4&3&A&3&A&lᴄ &x&F&3&4&0&4&0&lᴅ&x&F&2&4&5&4&5&lᴀ&x&F&1&4&A&4&A&lʀ&x&F&0&5&0&5&0&lᴛ&x&E&F&5&5&5&5&lʜ&x&E&E&5&A&5&A&lᴀ &x&E&D&6&0&6&0&lᴠ&x&E&C&6&5&6&5&lᴀ&x&E&B&6&A&6&A&lᴅ&x&E&A&7&0&7&0&lᴇ&x&E&9&7&5&7&5&lʀ&x&E&8&7&A&7&A&lᴀ"));
        this.mocYodyName = col(cfg.getString("moc-yody.name", "&x&6&0&E&5&B&2&lᴍ&x&6&8&E&5&8&0&lᴏ&x&7&1&E&5&7&A&lᴄ &x&8&1&E&5&7&0&lʏ&x&8&9&E&5&6&B&lᴏ&x&9&2&E&5&6&5&lᴅ&x&9&A&E&5&6&0&lʏ"));
        this.cukierkiName = col(cfg.getString("cukierki.name", "&f乞 &x&F&F&8&A&D&8&lᴄ&x&F&F&8&6&D&6&lᴜ&x&F&F&8&3&D&3&lᴋ&x&F&F&7&F&D&1&lɪ&x&F&F&7&C&C&E&lᴇ&x&F&F&7&8&C&C&lʀ&x&F&F&7&5&C&9&lᴋ&x&F&F&7&1&C&7&lɪ &x&F&F&6&D&C&4&lʟ&x&F&F&6&A&C&2&lᴜ&x&F&F&6&6&B&F&lᴋ&x&F&F&6&3&B&D&lᴇ &x&F&F&5&F&B&A&lꜱ&x&F&F&5&C&B&8&lᴋ&x&F&F&5&8&B&5&lʏ&x&F&F&5&4&B&3&lᴡ&x&F&F&5&1&B&0&lᴀ&x&F&F&4&D&A&E&lʟ&x&F&F&4&A&A&B&lᴋ&x&F&F&4&6&A&9&lᴇ&x&F&F&4&3&A&6&lʀ&x&F&F&3&F&A&4&lᴀ"));
        this.jablkoJediName = col(cfg.getString("jablko-jedi.name", "&fꕃ &x&F&F&D&7&0&0&lᴊ&x&F&F&C&F&0&0&lᴀ&x&F&F&C&7&0&0&lʙ&x&F&F&B&F&0&0&lʟ&x&F&F&B&7&0&0&lᴋ&x&F&F&A&F&0&0&lᴏ &x&F&F&A&7&0&0&lᴊ&x&F&F&9&F&0&0&lᴇ&x&F&F&9&7&0&0&lᴅ&x&F&F&8&F&0&0&lɪ"));
        this.kamienOdrodzeniaName = col(cfg.getString("kamien-odrodzenia.name", "&fꕣ &x&F&F&7&A&9&3&lᴋ&x&F&F&7&5&8&E&lᴀ&x&F&F&7&0&8&A&lᴍ&x&F&F&6&B&8&5&lɪ&x&F&F&6&6&8&0&lᴇ&x&F&F&6&1&7&C&lɴ &x&F&F&5&C&7&7&lᴏ&x&F&F&5&7&7&2&lᴅ&x&F&F&5&1&6&E&lʀ&x&F&F&4&C&6&9&lᴏ&x&F&F&4&7&6&4&lᴅ&x&F&F&4&2&6&0&lᴢ&x&F&F&3&D&5&B&lᴇ&x&F&F&3&8&5&6&lɴ&x&F&F&3&3&5&2&lɪ&x&F&F&2&E&4&D&lᴀ"));
        this.kozaName = col(cfg.getString("koza.name", "&f乤 &x&F&2&E&6&C&8&lᴋ&x&D&9&C&7&A&3&lᴏ&x&C&1&A&8&7&F&lᴢ&x&A&8&8&9&5&A&lᴀ"));
        this.eventowkaRandomName = col(cfg.getString("eventowka-random.name", "&f乴 Eventówka losowa"));
        this.sakiewkaName = col(cfg.getString("sakiewka-mandalorianina.name", "&f丮 &6&lꜱᴀᴋɪᴇᴡᴋᴀ ᴍᴀɴᴅᴀʟᴏʀɪᴀɴɪɴᴀ"));
        this.smoczaFajerwerkaName = col(cfg.getString("smocza-fajerwerka.name",
                "&f丷 &x&C&0&2&F&D&4&lꜱᴍᴏᴄᴢᴀ ꜰᴀᴊᴇʀᴡᴇʀᴋᴀ"));
        this.kosaGrabazaName = col(cfg.getString("kosa-grabaza.name",
                "&f乥 &x&F&F&6&B&1&A&lᴋ&x&F&5&6&2&1&9&lᴏ&x&E&A&5&9&1&9&lꜱ&x&D&F&5&1&1&8&lᴀ&x&D&5&4&8&1&7&l &x&C&A&3&F&1&6&lɢ&x&C&0&3&6&1&6&lʀ&x&B&5&2&D&1&5&lᴀ&x&A&B&2&4&1&4&lʙ&x&A&0&1&C&1&3&lᴀ&x&9&6&1&3&1&3&lᴢ&x&8&B&0&A&1&2&lʏ"));
        this.gwozdzTrumnyName = col(cfg.getString("gwozdz-trumny.name",
                "&f与 &x&E&8&D&5&B&0&lɢ&x&D&C&C&8&A&4&lᴡ&x&D&1&B&B&9&8&lᴏ&x&C&5&A&E&8&C&lᴢ&x&B&9&A&1&7&F&lᴅ&x&A&E&9&4&7&3&lᴢ&x&A&2&8&8&6&7&l &x&9&6&7&B&5&B&lᴛ&x&8&B&6&E&4&F&lʀ&x&7&F&6&1&4&3&lᴜ&x&7&3&5&4&3&6&lᴍ&x&6&8&4&7&2&A&lɴ&x&5&C&3&A&1&E&lʏ"));

        // CustomModelData (0 = brak) - ustawiane w eventowki.yml: <item>.custom-model-data
        cmds.clear();
        cmds.put("kokos", cfg.getInt("kokos.custom-model-data", KOKOS_CUSTOM_MODEL_DATA));
        cmds.put("lampion", cfg.getInt("lampion.custom-model-data", LAMPION_CUSTOM_MODEL_DATA));
        cmds.put("antycobweb", cfg.getInt("antycobweb.custom-model-data", ANTYCOBWEB_CUSTOM_MODEL_DATA));
        cmds.put("olej", cfg.getInt("olej.custom-model-data", OLEJ_CUSTOM_MODEL_DATA));
        cmds.put("moc-vadera", cfg.getInt("moc-vadera.custom-model-data", MOC_VADERA_CUSTOM_MODEL_DATA));
        cmds.put("moc-yody", cfg.getInt("moc-yody.custom-model-data", MOC_YODY_CUSTOM_MODEL_DATA));
        cmds.put("cukierki", cfg.getInt("cukierki.custom-model-data", CUKIERKI_CUSTOM_MODEL_DATA));
        cmds.put("jablko-jedi", cfg.getInt("jablko-jedi.custom-model-data", JABLKO_JEDI_CUSTOM_MODEL_DATA));
        cmds.put("kamien-odrodzenia", cfg.getInt("kamien-odrodzenia.custom-model-data", KAMIEN_ODRODZENIA_CUSTOM_MODEL_DATA));
        cmds.put("koza", cfg.getInt("koza.custom-model-data", KOZA_CUSTOM_MODEL_DATA));
        cmds.put("eventowka-random", cfg.getInt("eventowka-random.custom-model-data", EVENTOWKA_RANDOM_CUSTOM_MODEL_DATA));
        cmds.put("sakiewka-mandalorianina", cfg.getInt("sakiewka-mandalorianina.custom-model-data", SAKIEWKA_CUSTOM_MODEL_DATA));
        cmds.put("smocza-fajerwerka", cfg.getInt("smocza-fajerwerka.custom-model-data", SMOCZA_FAJERWERKA_CUSTOM_MODEL_DATA));
        cmds.put("kosa-grabaza", cfg.getInt("kosa-grabaza.custom-model-data", KOSA_GRABAZA_CUSTOM_MODEL_DATA));
        cmds.put("gwozdz-trumny", cfg.getInt("gwozdz-trumny.custom-model-data", GWOZDZ_TRUMNY_CUSTOM_MODEL_DATA));

        // Ustawienia
        this.kokosRegenAmplifier = cfg.getInt("kokos.regen-amplifier", 1);
        this.kokosRegenDuration = cfg.getInt("kokos.regen-duration-seconds", 5);
        this.kokosAbsorptionAmplifier = cfg.getInt("kokos.absorption-amplifier", 1);
        this.kokosAbsorptionDuration = cfg.getInt("kokos.absorption-duration-seconds", 120);
        this.kokosSpeedAmplifier = cfg.getInt("kokos.speed-amplifier", 0);
        this.kokosSpeedDuration = cfg.getInt("kokos.speed-duration-seconds", 10);

        this.lampionCooldownSeconds = cfg.getInt("lampion.cooldown-seconds", 60);
        this.lampionRegenAmplifier = cfg.getInt("lampion.regen-amplifier", 3);
        this.lampionRegenDuration = cfg.getInt("lampion.regen-duration-seconds", 5);

        this.antycobwebCooldownSeconds = cfg.getInt("antycobweb.cooldown-seconds", 10);
        this.antycobwebRadius = cfg.getInt("antycobweb.radius", 7);
        
        this.olejCooldownSeconds = cfg.getInt("olej.cooldown-seconds", 60);
        this.olejRegenAmplifier = cfg.getInt("olej.regen-amplifier", 5);
        this.olejRegenDuration = cfg.getInt("olej.regen-duration-seconds", 5);
        
        this.mocVaderCooldownSeconds = cfg.getInt("moc-vadera.cooldown-seconds", 60);
        this.mocVaderDuration = cfg.getInt("moc-vadera.duration-seconds", 10);
        this.mocYodyCooldownSeconds = cfg.getInt("moc-yody.cooldown-seconds", 60);
        this.mocYodyLuckDuration = cfg.getInt("moc-yody.luck-duration-seconds", 10);

        this.cukierkiCooldownSeconds = cfg.getInt("cukierki.cooldown-seconds", 30);
        this.cukierkiStrengthDuration = cfg.getInt("cukierki.strength-duration-seconds", 5);
        // 0 = Sila I, 1 = Sila II
        this.cukierkiStrengthAmplifier = cfg.getInt("cukierki.strength-amplifier", 0);

        this.jablkoJediRegenAmplifier = cfg.getInt("jablko-jedi.regen-amplifier", 1);
        this.jablkoJediRegenDuration = cfg.getInt("jablko-jedi.regen-duration-seconds", 5);
        this.jablkoJediAbsorptionAmplifier = cfg.getInt("jablko-jedi.absorption-amplifier", 0);
        this.jablkoJediAbsorptionDuration = cfg.getInt("jablko-jedi.absorption-duration-seconds", 120);
        this.jablkoJediBonusChance = cfg.getDouble("jablko-jedi.bonus-chance", 0.10);
        this.jablkoJediBonusAbsorptionAmplifier = cfg.getInt("jablko-jedi.bonus-absorption-amplifier", 1);

        this.kamienOdrodzeniaCooldownSeconds = cfg.getInt("kamien-odrodzenia.cooldown-seconds", 20);
        this.kamienOdrodzeniHearts = cfg.getInt("kamien-odrodzenia.hearts", 4);

        this.kozaCooldownSeconds = cfg.getInt("koza.cooldown-seconds", 60);
        this.kozaCelCooldownSeconds = cfg.getInt("koza.cooldown-na-celu-seconds", 90);
        this.kozaWorldBorderDuration = cfg.getInt("koza.worldborder-duration-seconds", 10);
        this.kozaWorldBorderSize = cfg.getInt("koza.worldborder-size", 15);
        this.kozaWorldBorderPlaceExtra = Math.max(0, cfg.getInt("koza.worldborder-place-extra", 2));

        this.eventowkaRandomCooldownSeconds = cfg.getInt("eventowka-random.cooldown-seconds", 45);
        this.eventowkaRandomEffectDuration = cfg.getInt("eventowka-random.effect-duration-seconds", 3);

        this.kosaGrabazaCooldownSeconds = cfg.getInt("kosa-grabaza.cooldown-seconds", 120);
        this.kosaGrabazaWeaknessDuration = cfg.getInt("kosa-grabaza.weakness-duration-seconds", 5);
        this.kosaGrabazaGlowingDuration = cfg.getInt("kosa-grabaza.glowing-duration-seconds", 5);
        this.kosaGrabazaFireworkBlockSeconds = cfg.getInt("kosa-grabaza.firework-block-seconds", 3);
        this.kosaGrabazaHearts = cfg.getInt("kosa-grabaza.hearts", 3);
        this.gwozdzTrumnyCooldownSeconds = cfg.getInt("gwozdz-trumny.cooldown-seconds", 25);
        this.gwozdzTrumnyFreezeSeconds = cfg.getInt("gwozdz-trumny.freeze-seconds", 2);
        this.gwozdzTrumnyWeaknessDuration = cfg.getInt("gwozdz-trumny.weakness-duration-seconds", 6);
        this.gwozdzTrumnyWeaknessAmplifier = cfg.getInt("gwozdz-trumny.weakness-amplifier", 1);

        this.sakiewkaEnabled = cfg.getBoolean("sakiewka-mandalorianina.enabled", true);
        Material sakiewkaMat = Material.matchMaterial(cfg.getString("sakiewka-mandalorianina.material", SAKIEWKA_MATERIAL.name()));
        this.sakiewkaMaterial = (sakiewkaMat != null && sakiewkaMat.name().endsWith("SHULKER_BOX"))
                ? sakiewkaMat
                : SAKIEWKA_MATERIAL;

        // Wiadomości
        this.msgKokosEaten = cfg.getString("messages.kokos-eaten", "&aOtrzymałeś &f&lᴋᴏᴋᴏꜱ&a!");
        this.msgKokosCooldown = cfg.getString("messages.kokos-cooldown", "&cMusisz poczekać jeszcze &e{czas}s&c!");
        this.msgLampionCooldown = cfg.getString("messages.lampion-cooldown", "&6&lʟᴀᴍᴘɪᴏɴ &8| &cMusisz odczekać jeszcze &e{czas}s&c!");
        this.msgLampionActivated = cfg.getString("messages.lampion-activated", "&6&lʟᴀᴍᴘɪᴏɴ &8| &aLampion zaczął cię leczyć!");
        this.msgAntycobwebCooldown = cfg.getString("messages.antycobweb-cooldown", "&cMusisz poczekać jeszcze &e{czas}s &cprzed ponownym użyciem!");
        this.msgAntycobwebRemoved = cfg.getString("messages.antycobweb-removed", "&a&l✔ &aUsunięto &e{count} &apajęczyn w pobliżu!");
        this.msgAntycobwebNone = cfg.getString("messages.antycobweb-none", "&7Nie znaleziono żadnych pajęczyn w pobliżu!");
        this.msgOlejCooldown = cfg.getString("messages.olej-cooldown", "&x&6&0&E&5&B&2Olej &8| &cMusisz odczekać jeszcze &e{czas}s&c!");
        this.msgOlejActivated = cfg.getString("messages.olej-activated", "&x&6&0&E&5&B&2Olej &8| &aOlej zaczął cię leczyć!");
        this.msgMocVaderCooldown = cfg.getString("messages.moc-vader-cooldown", "&x&F&6&3&0&3&0Moc Vadera &8| &cMusisz odczekać jeszcze &e{czas}s&c!");
        this.msgMocVaderActivated = cfg.getString("messages.moc-vader-activated", "&x&F&6&3&0&3&0Moc Vadera &8| &aZa słabą mocę boli!");
        this.msgMocYodyCooldown = cfg.getString("messages.moc-yody-cooldown", "&x&9&A&E&5&6&0Moc Yody &8| &cMusisz odczekać jeszcze &e{czas}s&c!");
        this.msgMocYodyActivated = cfg.getString("messages.moc-yody-activated", "&x&9&A&E&5&6&0Moc Yody &8| &aMądrość chroni!");
        this.msgGuiTitle = cfg.getString("messages.gui-title", "&5&lEwentualne Itemy");
        this.msgGuiReceived = cfg.getString("messages.gui-received", "&aOtrzymałeś &f{item}&a!");

        this.msgCukierkiCooldown = cfg.getString("messages.cukierki-cooldown", "&cMusisz poczekać jeszcze &e{czas}s&c!");
        this.msgCukierkiActivated = cfg.getString("messages.cukierki-activated", "&aOtrzymałeś &c&lSiłę I&a!");
        this.msgJablkoJediEaten = cfg.getString("messages.jablko-jedi-eaten", "&fꕃ &x&F&F&C&7&0&0Jabłko Jedi &8| &aOtrzymałeś &e2 serca &aabsorpcji!");
        this.msgJablkoJediBonus = cfg.getString("messages.jablko-jedi-bonus", "&fꕃ &x&F&F&C&7&0&0Jabłko Jedi &8| &aOtrzymałeś &64 serca &aabsorpcji!");
        this.msgKamienOdrodzeniaCooldown = cfg.getString("messages.kamien-odrodzenia-cooldown", "&cMusisz poczekać jeszcze &e{czas}s&c!");
        this.msgKamienOdrodzeniActivated = cfg.getString("messages.kamien-odrodzenia-activated", "&aOtrzymałeś &c4 serca&a!");
        this.msgKozaCooldown = cfg.getString("messages.koza-cooldown", "&cMusisz poczekać jeszcze &e{czas}s&c!");
        this.msgKozaActivated = cfg.getString("messages.koza-activated", "&aZamknięto Cię na arenie z &e{gracz} &ana &e{czas}s&a!");
        this.msgKozaTooFar = cfg.getString("messages.koza-too-far", "&cTen gracz jest za daleko!");
        this.msgKozaBusy = cfg.getString("messages.koza-busy", "&cJeden z graczy jest już na arenie!");
        this.msgKozaElytra = cfg.getString("messages.koza-elytra", "&cElytra została zdjęta - nie możesz jej używać na arenie!");
        this.msgKozaAlreadyUsed = cfg.getString("messages.koza-already-used", "&cTen gracz był niedawno na arenie! Poczekaj jeszcze &e{czas}s&c.");
        this.msgEventowkaRandomCooldown = cfg.getString("messages.eventowka-random-cooldown", "&cMusisz poczekać jeszcze &e{czas}s&c!");
        this.msgEventowkaRandomActivated = cfg.getString("messages.eventowka-random-activated", "&aWylosowano: {efekt}");
        this.msgSakiewkaReceived = cfg.getString("messages.sakiewka-received", "&f丮 &x&F&F&C&C&6&6ꜱᴀᴋɪᴇᴡᴋᴀ &8| &aOtrzymałeś sakiewkę z łupami od &f{gracz}&a!");
        this.msgSakiewkaFull = cfg.getString("messages.sakiewka-full", "&f丮 &x&F&F&C&C&6&6ꜱᴀᴋɪᴇᴡᴋᴀ &8| &eSakiewka pełna - reszta dropu wypadła na ziemię!");
        this.msgKosaGrabazaCooldown = cfg.getString("messages.kosa-grabaza-cooldown", "&f乥 &x&F&F&6&B&1&Aᴋᴏꜱᴀ ɢʀᴀʙᴀᴢʏ &8| &cMusisz odczekać jeszcze &e{czas}s&c!");
        this.msgKosaGrabazaHit = cfg.getString("messages.kosa-grabaza-hit", "&f乥 &x&F&F&6&B&1&Aᴋᴏꜱᴀ ɢʀᴀʙᴀᴢʏ &8| &aTrafiłeś &f{gracz}&a!");
        this.msgKosaGrabazaVictim = cfg.getString("messages.kosa-grabaza-victim", "&f乥 &x&F&F&6&B&1&Aᴋᴏꜱᴀ ɢʀᴀʙᴀᴢʏ &8| &c{gracz} &7trafił Cię kosą!");
        this.msgKosaGrabazaFajerwerka = cfg.getString("messages.kosa-grabaza-fajerwerka", "&f乥 &x&F&F&6&B&1&Aᴋᴏꜱᴀ ɢʀᴀʙᴀᴢʏ &8| &cBlokada fajerwerki! Jeszcze &e{czas}s&c.");
        this.msgGwozdzTrumnyCooldown = cfg.getString("messages.gwozdz-trumny-cooldown", "&f与 &x&C&4&A&5&7&4ɢᴡᴏᴢᴅᴢ ᴛʀᴜᴍɴʏ &8| &cMusisz odczekać jeszcze &e{czas}s&c!");
        this.msgGwozdzTrumnyUsed = cfg.getString("messages.gwozdz-trumny-used", "&f与 &x&C&4&A&5&7&4ɢᴡᴏᴢᴅᴢ ᴛʀᴜᴍɴʏ &8| &aPrzebiłeś &f{gracz}&a!");
        this.msgGwozdzTrumnyVictim = cfg.getString("messages.gwozdz-trumny-victim", "&f与 &x&C&4&A&5&7&4ɢᴡᴏᴢᴅᴢ ᴛʀᴜᴍɴʏ &8| &c{gracz} &7przebił Cię gwoździem!");

        // Lore
        this.kokosLore = cfg.getStringList("kokos.lore");
        this.lampionLore = cfg.getStringList("lampion.lore");
        this.antycobwebLore = cfg.getStringList("antycobweb.lore");
        this.olejLore = cfg.getStringList("olej.lore");
        this.mocVaderLore = cfg.getStringList("moc-vadera.lore");
        this.mocYodyLore = cfg.getStringList("moc-yody.lore");
        this.cukierkiLore = cfg.getStringList("cukierki.lore");
        this.jablkoJediLore = cfg.getStringList("jablko-jedi.lore");
        this.kamienOdrodzeniLore = cfg.getStringList("kamien-odrodzenia.lore");
        this.kozaLore = cfg.getStringList("koza.lore");
        this.eventowkaRandomLore = cfg.getStringList("eventowka-random.lore");
        this.sakiewkaLore = cfg.getStringList("sakiewka-mandalorianina.lore");
        this.sakiewkaLootLore = cfg.getStringList("sakiewka-mandalorianina.lore-loot");
        this.smoczaFajerwerkaLore = cfg.getStringList("smocza-fajerwerka.lore");
        this.kosaGrabazaLore = cfg.getStringList("kosa-grabaza.lore");
        this.gwozdzTrumnyLore = cfg.getStringList("gwozdz-trumny.lore");

        // Wiadomości
    }

    // ===================== Tworzenie itemow =====================

    public ItemStack createKokos() {
        ItemStack item = new ItemStack(KOKOS_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kokosName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("kokos", 0));
        if (kokosLore != null && !kokosLore.isEmpty()) {
            meta.setLore(kokosLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fPo zjedzeniu otrzymujemy:"), col("&8-> &dEfekt regeneracji II &f(5s)"), col("&8-> &eEfekt absorpcja II &f(120s)"), col("&8-> &bEfekt szybkości I &f(10s)"), col("&0"), col("&8x &4&lUwaga! &cPrzedmiot jest jednorazowy!"), col("&0"), col("&8x &aPrzytrzymaj prawy aby zjeść!")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "kokos");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createLampion() {
        ItemStack item = new ItemStack(LAMPION_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(lampionName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("lampion", 0));
        if (lampionLore != null && !lampionLore.isEmpty()) {
            meta.setLore(lampionLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fPo użyciu otrzymujemy &cregenerację &fna kilka sekund"), col("&0"), col("&aKliknij prawy aby użyć")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "lampion");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAntycobweb() {
        ItemStack item = new ItemStack(ANTYCOBWEB_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(antycobwebName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("antycobweb", 0));
        if (antycobwebLore != null && !antycobwebLore.isEmpty()) {
            meta.setLore(antycobwebLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fKliknij prawym aby usunąć"), col("&8x &fwszystkie &epajęczyny &fw promieniu &e" + antycobwebRadius + " bloków"), col("&0")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "antycobweb");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createOlej() {
        ItemStack item = new ItemStack(OLEJ_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(olejName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("olej", 0));
        if (olejLore != null && !olejLore.isEmpty()) {
            meta.setLore(olejLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fPo użyciu otrzymujemy &cregenerację &fna kilka sekund"), col("&0"), col("&aKliknij prawy aby użyć")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "olej");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMocVadera() {
        ItemStack item = new ItemStack(MOC_VADERA_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(mocVaderName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("moc-vadera", 0));
        if (mocVaderLore != null && !mocVaderLore.isEmpty()) {
            meta.setLore(mocVaderLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fZa słabą mocę boli!"), col("&0"), col("&aKliknij prawy aby użyć")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "moc_vadera");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMocYody() {
        ItemStack item = new ItemStack(MOC_YODY_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(mocYodyName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("moc-yody", 0));
        if (mocYodyLore != null && !mocYodyLore.isEmpty()) {
            meta.setLore(mocYodyLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fMądrość chroni przed bólem!"), col("&0"), col("&aKliknij prawy aby użyć")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "moc_yody");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSmoczaFajerwerka() {
        ItemStack item = new ItemStack(SMOCZA_FAJERWERKA_MATERIAL);
        FireworkMeta meta = (FireworkMeta) item.getItemMeta();
        meta.setDisplayName(smoczaFajerwerkaName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("smocza-fajerwerka", 0));
        if (smoczaFajerwerkaLore != null && !smoczaFajerwerkaLore.isEmpty()) {
            meta.setLore(smoczaFajerwerkaLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(buildSmoczaFajerwerkaLore());
        }
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.fromRGB(0xC02FD4), Color.fromRGB(0x31EAFC), Color.ORANGE)
                .withFade(Color.RED, Color.BLACK)
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(1);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "smocza_fajerwerka");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCukierki() {
        ItemStack item = new ItemStack(CUKIERKI_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(cukierkiName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("cukierki", 0));
        if (cukierkiLore != null && !cukierkiLore.isEmpty()) {
            meta.setLore(cukierkiLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fNawiedzenie Luke Skywalkera"), col("&8x &cSiła I &fna 5 sekund"), col("&0"), col("&aKliknij prawy aby użyć")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "cukierki");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createJablkoJedi() {
        ItemStack item = new ItemStack(JABLKO_JEDI_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(jablkoJediName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("jablko-jedi", 0));
        if (jablkoJediLore != null && !jablkoJediLore.isEmpty()) {
            meta.setLore(jablkoJediLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(buildJablkoJediLore());
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "jablko_jedi");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKamienOdrodzenia() {
        ItemStack item = new ItemStack(KAMIEN_ODRODZENIA_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kamienOdrodzeniaName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("kamien-odrodzenia", 0));
        if (kamienOdrodzeniLore != null && !kamienOdrodzeniLore.isEmpty()) {
            meta.setLore(kamienOdrodzeniLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fKamień o magicznych mocach"), col("&8x &6Dodaje 4 serca"), col("&8x &eNieskończony &f- nie zużywa się"), col("&0"), col("&aKliknij prawy aby użyć")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "kamien_odrodzenia");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKoza() {
        ItemStack item = new ItemStack(KOZA_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kozaName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("koza", 0));
        if (kozaLore != null && !kozaLore.isEmpty()) {
            meta.setLore(kozaLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fArena 10x10 dla dwóch graczy na 10 sekund"), col("&8x &fElytra zostanie zdjęta, nikt ich nie widzi"), col("&8x &fTen sam gracz może dostać kozę co 90 sekund"), col("&8x &fKliknij prawym NA GRACZU"), col("&0"), col("&aKliknij prawy aby użyć")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "koza");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createEventowkaRandom() {
        ItemStack item = new ItemStack(EVENTOWKA_RANDOM_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(eventowkaRandomName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("eventowka-random", 0));
        if (eventowkaRandomLore != null && !eventowkaRandomLore.isEmpty()) {
            meta.setLore(eventowkaRandomLore.stream().map(EventItemsManager::col).toList());
        } else {
            meta.setLore(List.of(col("&0"), col("&8x &fMagiczny kryształ o nieprzewidywalnych mocach"), col("&8x &5Losowy efekt na 3 sekundy"), col("&0"), col("&aKliknij prawy aby użyć")));
        }
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "eventowka_random");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSakiewka() {
        ItemStack item = new ItemStack(sakiewkaMaterial != null ? sakiewkaMaterial : SAKIEWKA_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(sakiewkaName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("sakiewka-mandalorianina", 0));
        meta.setLore(buildSakiewkaLore());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "sakiewka");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKosaGrabaza() {
        ItemStack item = new ItemStack(KOSA_GRABAZA_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kosaGrabazaName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("kosa-grabaza", KOSA_GRABAZA_CUSTOM_MODEL_DATA));
        meta.setLore(buildKosaGrabazaLore());
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        ukryjAtrybutyNarzedzia(meta, KOSA_GRABAZA_MATERIAL);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "kosa_grabaza");
        item.setItemMeta(meta);
        return item;
    }

    /** 1.21 pokazuje attack damage motyki dopoki atrybuty nie sa na itemie + HIDE_ATTRIBUTES. */
    private static void ukryjAtrybutyNarzedzia(ItemMeta meta, Material material) {
        if (meta == null) return;
        if (!meta.hasAttributeModifiers()) {
            var defaults = material.getDefaultAttributeModifiers(EquipmentSlot.HAND);
            if (defaults != null && !defaults.isEmpty()) {
                meta.setAttributeModifiers(defaults);
            }
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static boolean ukryjAtrybutyNaItemie(ItemStack item, Material material) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        boolean extraOk;
        try {
            extraOk = meta.hasItemFlag(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
            extraOk = true;
        }
        if (meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES) && meta.hasAttributeModifiers() && extraOk) {
            return false;
        }
        ukryjAtrybutyNarzedzia(meta, material);
        item.setItemMeta(meta);
        return true;
    }

    public ItemStack createGwozdzTrumny() {
        ItemStack item = new ItemStack(GWOZDZ_TRUMNY_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(gwozdzTrumnyName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("gwozdz-trumny", GWOZDZ_TRUMNY_CUSTOM_MODEL_DATA));
        meta.setLore(buildGwozdzTrumnyLore());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "gwozdz_trumny");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Wklada drop do pierwszej sakiewki gracza, ktora ma wolne miejsce.
     * Pomija sakiewki otwarte wlasnie w GUI (token sesji ShulkerOpenListener) i stacki >1,
     * bo zapis do nich zostalby nadpisany albo powielony.
     * Sakiewki ofiary nie wpadaja do sakiewki zabojcy — zostaja w reszcie (spadaja na ziemie).
     * @return to, co sie nie zmiescilo (cala lista, gdy zadna sakiewka sie nie nadaje)
     */
    public List<ItemStack> wlozDoSakiewki(Player player, List<ItemStack> dropy) {
        NamespacedKey sesjaKey = new NamespacedKey(plugin.getHost(), "shulker_sesja");
        List<ItemStack> reszta = new ArrayList<>(dropy);
        ItemStack[] eq = player.getInventory().getContents();
        for (int i = 0; i < eq.length && !reszta.isEmpty(); i++) {
            ItemStack stack = eq[i];
            if (!isSakiewka(stack) || stack.getAmount() != 1) continue;
            if (!(stack.getItemMeta() instanceof BlockStateMeta meta)
                    || !(meta.getBlockState() instanceof ShulkerBox box)) continue;
            if (meta.getPersistentDataContainer().has(sesjaKey, PersistentDataType.STRING)) continue;

            Inventory bufor = Bukkit.createInventory(null, SAKIEWKA_SLOTOW);
            bufor.setContents(box.getInventory().getContents());
            List<ItemStack> nieWeszlo = new ArrayList<>();
            for (ItemStack drop : reszta) {
                if (isSakiewka(drop) || isSakiewkaLoot(drop)) {
                    nieWeszlo.add(drop);
                    continue;
                }
                nieWeszlo.addAll(bufor.addItem(drop).values());
            }
            box.getInventory().setContents(bufor.getContents());
            meta.setBlockState(box);
            stack.setItemMeta(meta);
            player.getInventory().setItem(i, stack);
            reszta = nieWeszlo;
        }
        return reszta;
    }

    public List<ItemStack> createSakiewkiZDropu(List<ItemStack> dropy, String ofiara) {
        List<ItemStack> wynik = new ArrayList<>();
        List<ItemStack> kolejka = new ArrayList<>(dropy);

        while (!kolejka.isEmpty()) {
            Inventory bufor = Bukkit.createInventory(null, SAKIEWKA_SLOTOW);
            List<ItemStack> nieWeszlo = new ArrayList<>();
            for (ItemStack drop : kolejka) {
                nieWeszlo.addAll(bufor.addItem(drop).values());
            }

            int zajete = 0;
            ItemStack[] zawartosc = bufor.getContents();
            for (ItemStack slot : zawartosc) {
                if (slot != null && !slot.getType().isAir()) {
                    zajete++;
                }
            }
            if (zajete == 0) {
                break;
            }

            ItemStack sakiewka = createSakiewkaLoot(ofiara, zajete);
            ItemMeta meta = sakiewka.getItemMeta();
            if (!(meta instanceof BlockStateMeta blockStateMeta)
                    || !(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
                return List.of();
            }
            shulkerBox.getInventory().setContents(zawartosc);
            blockStateMeta.setBlockState(shulkerBox);
            sakiewka.setItemMeta(blockStateMeta);
            wynik.add(sakiewka);
            kolejka = nieWeszlo;
        }
        return wynik;
    }

    private ItemStack createSakiewkaLoot(String ofiara, int ilosc) {
        ItemStack sakiewka = new ItemStack(sakiewkaMaterial != null ? sakiewkaMaterial : SAKIEWKA_MATERIAL);
        ItemMeta meta = sakiewka.getItemMeta();
        if (meta == null) {
            return sakiewka;
        }
        meta.setDisplayName(sakiewkaName);
        ItemTagUtil.applyModel(meta, cmds.getOrDefault("sakiewka-mandalorianina", 0));
        meta.setLore(buildSakiewkaLootLore(ofiara, ilosc));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "sakiewka_loot");
        sakiewka.setItemMeta(meta);
        return sakiewka;
    }

    public boolean consumeEmptySakiewka(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isSakiewka(stack)) {
                continue;
            }
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
            } else {
                player.getInventory().setItem(i, null);
            }
            return true;
        }
        return false;
    }

    public boolean hasEmptySakiewka(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isSakiewka(stack)) {
                return true;
            }
        }
        return false;
    }

    // ===================== Odswiezanie itemow =====================

    private List<String> buildKokosLore() {
        if (kokosLore != null && !kokosLore.isEmpty()) {
            return kokosLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fPo zjedzeniu otrzymujemy:"), col("&8-> &dEfekt regeneracji II &f(5s)"), col("&8-> &eEfekt absorpcja II &f(120s)"), col("&8-> &bEfekt szybkości I &f(10s)"), col("&0"), col("&8x &4&lUwaga! &cPrzedmiot jest jednorazowy!"), col("&0"), col("&8x &aPrzytrzymaj prawy aby zjeść!"));
    }

    private List<String> buildLampionLore() {
        if (lampionLore != null && !lampionLore.isEmpty()) {
            return lampionLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fPo użyciu otrzymujemy &cregenerację &fna kilka sekund"), col("&0"), col("&aKliknij prawy aby użyć"));
    }

    private List<String> buildAntycobwebLore() {
        if (antycobwebLore != null && !antycobwebLore.isEmpty()) {
            return antycobwebLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fKliknij prawym aby usunąć"), col("&8x &fwszystkie &epajęczyny &fw promieniu &e" + antycobwebRadius + " bloków"), col("&0"));
    }

    private List<String> buildOlejLore() {
        if (olejLore != null && !olejLore.isEmpty()) {
            return olejLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fPo użyciu otrzymujemy &cregenerację &fna kilka sekund"), col("&0"), col("&aKliknij prawy aby użyć"));
    }

    private List<String> buildMocVaderaLore() {
        if (mocVaderLore != null && !mocVaderLore.isEmpty()) {
            return mocVaderLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fZa słabą mocę boli!"), col("&0"), col("&aKliknij prawy aby użyć"));
    }

    private List<String> buildMocYodyLore() {
        if (mocYodyLore != null && !mocYodyLore.isEmpty()) {
            return mocYodyLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fMądrość chroni przed bólem!"), col("&0"), col("&aKliknij prawy aby użyć"));
    }

    private List<String> buildSmoczaFajerwerkaLore() {
        if (smoczaFajerwerkaLore != null && !smoczaFajerwerkaLore.isEmpty()) {
            return smoczaFajerwerkaLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(
                col("&f"),
                col("&f 丷 ɴɪᴇꜱᴋᴏɴᴄᴢᴏɴᴀ ꜱᴍᴏᴄᴢᴀ ꜰᴀᴊᴇʀᴡᴇʀᴋᴀ"),
                col("&f")
        );
    }

    private List<String> buildKosaGrabazaLore() {
        if (kosaGrabazaLore != null && !kosaGrabazaLore.isEmpty()) {
            return kosaGrabazaLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(
                col("&f"),
                col("&f 乥 ᴅᴢɪᴇᴋɪ ᴛᴇᴊ &x&F&F&6&B&1&Aʟᴇɢᴇɴᴅᴀʀɴᴇᴊ &fᴋᴏꜱɪᴇ"),
                col("&f ᴜᴅᴇʀᴢᴀꜱᴢ ɢʀᴀᴄᴢᴀ &x&F&F&6&B&1&Aᴋʟᴀᴛᴡᴀ ɢʀᴀʙᴀᴢʏ"),
                col("&f 亚 ᴏꜱʟᴀʙɪᴇɴɪᴇ &f+ ɢʟᴏᴡɪɴɢ ɴᴀ &x&F&F&6&B&1&A5 ꜱᴇᴋᴜɴᴅ"),
                col("&f 丷 ʙʟᴏᴋᴀᴅᴀ ꜰᴀᴊᴇʀᴡᴇʀᴋɪ ɴᴀ &x&F&F&6&B&1&A3 ꜱᴇᴋᴜɴᴅʏ"),
                col("&f ꕣ ᴢᴀᴅᴀᴊᴇ &x&F&F&6&B&1&A3 ꜱᴇʀᴄᴀ &fᴏʙʀᴀᴢᴇɴ"),
                col("&f 东 ᴄᴏᴏʟᴅᴏᴡɴ &x&F&F&6&B&1&A120 ꜱᴇᴋᴜɴᴅ"),
                col("&f"),
                col("&f 亝 ᴘʀᴢᴇᴅᴍɪᴏᴛ ᴍᴏᴢᴇꜱᴢ ᴡʏᴅʀᴏᴘɪᴄ"),
                col("&f ᴢ ɢᴀʟᴀᴋᴛʏᴄᴢɴᴇᴊ ꜱᴋʀᴢʏɴɪ ɴᴀ &x&F&F&6&B&1&Aꜱᴘᴀᴡɴɪᴇ"),
                col("&f")
        );
    }

    private List<String> buildGwozdzTrumnyLore() {
        if (gwozdzTrumnyLore != null && !gwozdzTrumnyLore.isEmpty()) {
            return gwozdzTrumnyLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(
                col("&f"),
                col("&f 与 ᴅᴢɪᴇᴋɪ ᴛᴇᴍᴜ &x&C&4&A&5&7&4ʟᴇɢᴇɴᴅᴀʀɴᴇᴍᴜ &fɢᴡᴏᴢᴅᴢɪᴏᴡɪ"),
                col("&f ᴡʙɪᴊᴀꜱᴢ ᴄᴇʟ &x&C&4&A&5&7&4ᴡ ᴛʀᴜᴍɴᴇ"),
                col("&f 东 ꜰʀᴇᴇᴢᴇ ɴᴀ &x&C&4&A&5&7&42 ꜱᴇᴋᴜɴᴅʏ"),
                col("&f 亚 ᴏꜱʟᴀʙɪᴇɴɪᴇ II ɴᴀ &x&C&4&A&5&7&46 ꜱᴇᴋᴜɴᴅ"),
                col("&f jednorazowy - zuzywa sie"),
                col("&f 东 ᴄᴏᴏʟᴅᴏᴡɴ &x&C&4&A&5&7&425 ꜱᴇᴋᴜɴᴅ"),
                col("&f 乁 ᴋʟɪᴋɴɪᴊ &x&C&4&A&5&7&4ᴘᴘᴍ ɴᴀ ɢʀᴀᴄᴢᴀ"),
                col("&f"),
                col("&f 亝 ᴘʀᴢᴇᴅᴍɪᴏᴛ ᴍᴏᴢᴇꜱᴢ ᴡʏᴅʀᴏᴘɪᴄ"),
                col("&f ᴢ ɢᴀʟᴀᴋᴛʏᴄᴢɴᴇᴊ ꜱᴋʀᴢʏɴɪ ɴᴀ &x&C&4&A&5&7&4ꜱᴘᴀᴡɴɪᴇ"),
                col("&f")
        );
    }

    private List<String> buildCukierkiLore() {
        if (cukierkiLore != null && !cukierkiLore.isEmpty()) {
            return cukierkiLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fNawiedzenie Luke Skywalkera"), col("&8x &cSiła I &fna 5 sekund"), col("&0"), col("&aKliknij prawy aby użyć"));
    }

    private List<String> buildJablkoJediLore() {
        if (jablkoJediLore != null && !jablkoJediLore.isEmpty()) {
            return jablkoJediLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(
                col("&f"),
                col("&f ꕃ ᴅᴢɪᴇᴋɪ ᴛᴇᴍᴜ &x&F&F&D&7&0&0ʟᴇɢᴇɴᴅᴀʀɴᴇᴍᴜ &fᴊᴀʙʟᴋᴜ"),
                col("&f ᴢʏꜱᴋᴜᴊᴇꜱᴢ &x&F&F&C&7&0&0ʀᴇɢᴇɴᴇʀᴀᴄᴊᴇ &fɪ &x&F&F&A&7&0&0ᴀʙꜱᴏʀᴘᴄᴊᴇ"),
                col("&f"),
                col("&f  &8» &x&F&F&D&7&0&0ᴀʙꜱᴏʀᴘᴄᴊᴀ I &7- 2 ꜱᴇʀᴄᴀ"),
                col("&f  &8» &x&F&F&B&7&0&0ʀᴇɢᴇɴᴇʀᴀᴄᴊᴀ II &7- 5 ꜱᴇᴋᴜɴᴅ"),
                col("&f  &8» &x&F&F&9&F&0&010% &7ꜱᴢᴀɴꜱʏ ɴᴀ &x&F&F&D&7&0&04 ꜱᴇʀᴄᴀ"),
                col("&f  &8» &x&F&F&8&F&0&0ɴɪᴇꜱᴋᴏɴᴄᴢᴏɴᴇ &7- ɴɪᴇ ᴢᴜᴢʏᴡᴀ ꜱɪᴇ"),
                col("&f")
        );
    }

    private List<String> buildKamienOdrodzeniLore() {
        if (kamienOdrodzeniLore != null && !kamienOdrodzeniLore.isEmpty()) {
            return kamienOdrodzeniLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fKamień o magicznych mocach"), col("&8x &6Dodaje 4 serca"), col("&8x &eNieskończony &f- nie zużywa się"), col("&0"), col("&aKliknij prawy aby użyć"));
    }

    private List<String> buildKozaLore() {
        if (kozaLore != null && !kozaLore.isEmpty()) {
            return kozaLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fArena 10x10 dla dwóch graczy na 10 sekund"), col("&8x &fElytra zostanie zdjęta, nikt ich nie widzi"), col("&8x &fTen sam gracz może dostać kozę co 90 sekund"), col("&8x &fKliknij prawym NA GRACZU"), col("&0"), col("&aKliknij prawy aby użyć"));
    }

    private List<String> buildEventowkaRandomLore() {
        if (eventowkaRandomLore != null && !eventowkaRandomLore.isEmpty()) {
            return eventowkaRandomLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(col("&0"), col("&8x &fMagiczny kryształ o nieprzewidywalnych mocach"), col("&8x &5Losowy efekt na 3 sekundy"), col("&0"), col("&aKliknij prawy aby użyć"));
    }

    private List<String> buildSakiewkaLore() {
        if (sakiewkaLore != null && !sakiewkaLore.isEmpty()) {
            return sakiewkaLore.stream().map(EventItemsManager::col).toList();
        }
        return List.of(
                col("&f"),
                col("&f 丮 Dzięki tej sakiewce zbierasz cały drop ofiary"),
                col("&f 乁 Sakiewka wypełni się łupami po zabójstwie"),
                col("&f")
        );
    }

    private List<String> buildSakiewkaLootLore(String ofiara, int ilosc) {
        List<String> zrodlo = (sakiewkaLootLore != null && !sakiewkaLootLore.isEmpty())
                ? sakiewkaLootLore
                : List.of("&f", "&f 丶 Ofiara: &6{gracz}", "&f 丮 Sloty: &6{ilosc}", "&f 乁 Kliknij PPM, aby otworzyć", "&f");
        List<String> lore = new ArrayList<>(zrodlo.size());
        for (String linia : zrodlo) {
            lore.add(col(linia.replace("{gracz}", ofiara).replace("{ilosc}", String.valueOf(ilosc))));
        }
        return lore;
    }

    /**
     * Odswieza nazwe i lore itemu eventowego (kokos/lampion/antycobweb/olej/moc_vadera/moc_yody) do aktualnych
     * wartosci z configu. Zwraca true jesli item zostal zmieniony.
     */
    public boolean refreshItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        boolean changed = refreshText(item);
        String key = configKeyOf(item);
        if (key != null) {
            // migracja materialu: stare Kamienie Odrodzenia (kompas) -> magma cream
            if ("kamien-odrodzenia".equals(key) && item.getType() != KAMIEN_ODRODZENIA_MATERIAL) {
                item.setType(KAMIEN_ODRODZENIA_MATERIAL);
                changed = true;
            }
            if ("sakiewka-mandalorianina".equals(key) && sakiewkaMaterial != null && item.getType() != sakiewkaMaterial) {
                item.setType(sakiewkaMaterial);
                changed = true;
            }
            changed |= ItemTagUtil.syncModel(item, cmds.getOrDefault(key, 0));
        }
        return changed;
    }

    private String configKeyOf(ItemStack item) {
        if (isKokos(item)) return "kokos";
        if (isLampion(item)) return "lampion";
        if (isAntycobweb(item)) return "antycobweb";
        if (isOlej(item)) return "olej";
        if (isMocVadera(item)) return "moc-vadera";
        if (isMocYody(item)) return "moc-yody";
        if (isCukierki(item)) return "cukierki";
        if (isJablkoJedi(item)) return "jablko-jedi";
        if (isKamienOdrodzenia(item)) return "kamien-odrodzenia";
        if (isKoza(item)) return "koza";
        if (isEventowkaRandom(item)) return "eventowka-random";
        if (isSakiewka(item)) return "sakiewka-mandalorianina";
        if (isSmoczaFajerwerka(item)) return "smocza-fajerwerka";
        if (isKosaGrabaza(item)) return "kosa-grabaza";
        if (isGwozdzTrumny(item)) return "gwozdz-trumny";
        return null;
    }

    private boolean refreshText(ItemStack item) {
        if (isKokos(item)) return ItemTagUtil.refresh(item, kokosName, buildKokosLore());
        if (isLampion(item)) return ItemTagUtil.refresh(item, lampionName, buildLampionLore());
        if (isAntycobweb(item)) return ItemTagUtil.refresh(item, antycobwebName, buildAntycobwebLore());
        if (isOlej(item)) return ItemTagUtil.refresh(item, olejName, buildOlejLore());
        if (isMocVadera(item)) return ItemTagUtil.refresh(item, mocVaderName, buildMocVaderaLore());
        if (isMocYody(item)) return ItemTagUtil.refresh(item, mocYodyName, buildMocYodyLore());
        if (isCukierki(item)) return ItemTagUtil.refresh(item, cukierkiName, buildCukierkiLore());
        if (isJablkoJedi(item)) return ItemTagUtil.refresh(item, jablkoJediName, buildJablkoJediLore());
        if (isKamienOdrodzenia(item)) return ItemTagUtil.refresh(item, kamienOdrodzeniaName, buildKamienOdrodzeniLore());
        if (isKoza(item)) return ItemTagUtil.refresh(item, kozaName, buildKozaLore());
        if (isEventowkaRandom(item)) return ItemTagUtil.refresh(item, eventowkaRandomName, buildEventowkaRandomLore());
        if (isSakiewka(item)) return ItemTagUtil.refresh(item, sakiewkaName, buildSakiewkaLore());
        if (isSmoczaFajerwerka(item)) return ItemTagUtil.refresh(item, smoczaFajerwerkaName, buildSmoczaFajerwerkaLore());
        if (isKosaGrabaza(item)) {
            boolean text = ItemTagUtil.refresh(item, kosaGrabazaName, buildKosaGrabazaLore());
            return ukryjAtrybutyNaItemie(item, KOSA_GRABAZA_MATERIAL) || text;
        }
        if (isGwozdzTrumny(item)) return ItemTagUtil.refresh(item, gwozdzTrumnyName, buildGwozdzTrumnyLore());
        return false;
    }

    // ===================== Sprawdzanie typu itemu =====================

    public boolean isKokos(ItemStack item) { return isEventItem(item, "kokos"); }
    public boolean isLampion(ItemStack item) { return isEventItem(item, "lampion"); }
    public boolean isAntycobweb(ItemStack item) { return isEventItem(item, "antycobweb"); }
    public boolean isOlej(ItemStack item) { return isEventItem(item, "olej"); }
    public boolean isMocVadera(ItemStack item) { return isEventItem(item, "moc_vadera"); }
    public boolean isMocYody(ItemStack item) { return isEventItem(item, "moc_yody"); }
    public boolean isCukierki(ItemStack item) { return isEventItem(item, "cukierki"); }
    public boolean isJablkoJedi(ItemStack item) { return isEventItem(item, "jablko_jedi"); }
    public boolean isKamienOdrodzenia(ItemStack item) { return isEventItem(item, "kamien_odrodzenia"); }
    public boolean isKoza(ItemStack item) { return isEventItem(item, "koza"); }
    public boolean isEventowkaRandom(ItemStack item) { return isEventItem(item, "eventowka_random"); }
    public boolean isSakiewka(ItemStack item) { return isEventItem(item, "sakiewka"); }
    public boolean isSmoczaFajerwerka(ItemStack item) { return isEventItem(item, "smocza_fajerwerka"); }
    public boolean isKosaGrabaza(ItemStack item) { return isEventItem(item, "kosa_grabaza"); }
    public boolean isGwozdzTrumny(ItemStack item) { return isEventItem(item, "gwozdz_trumny"); }

    public boolean isSmoczaFajerwerkaMeta(ItemMeta meta) {
        if (meta == null) return false;
        return "smocza_fajerwerka".equals(meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }
    public boolean isSakiewkaLoot(ItemStack item) { return isEventItem(item, "sakiewka_loot"); }

    public boolean jestEventowy(ItemStack item) {
        if (item == null || !item.hasItemMeta() || itemKey == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String stored = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return stored != null && !stored.isEmpty();
    }

    private boolean isEventItem(ItemStack item, String type) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String stored = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return type.equals(stored);
    }

    // ===================== Cooldowny =====================

    public boolean isLampionOnCooldown(UUID uuid) {
        Long until = lampionCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getLampionCooldownRemaining(UUID uuid) {
        Long until = lampionCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setLampionCooldown(UUID uuid) { lampionCooldowns.put(uuid, System.currentTimeMillis() + lampionCooldownSeconds * 1000L); }
    public void resetLampionCooldown(UUID uuid) { lampionCooldowns.remove(uuid); }

    public void resetAllCooldowns(UUID uuid) {
        lampionCooldowns.remove(uuid);
        antycobwebCooldowns.remove(uuid);
        olejCooldowns.remove(uuid);
        mocVaderCooldowns.remove(uuid);
        mocYodyCooldowns.remove(uuid);
        cukierkiCooldowns.remove(uuid);
        kamienOdrodzeniaCooldowns.remove(uuid);
        kozaCooldowns.remove(uuid);
        kozaCelCooldowns.remove(uuid);
        eventowkaRandomCooldowns.remove(uuid);
        kosaGrabazaCooldowns.remove(uuid);
        gwozdzTrumnyCooldowns.remove(uuid);
        blokadaFajerwerki.remove(uuid);
        zamrozenie.remove(uuid);
    }

    public boolean isAntycobwebOnCooldown(UUID uuid) {
        Long until = antycobwebCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getAntycobwebCooldownRemaining(UUID uuid) {
        Long until = antycobwebCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setAntycobwebCooldown(UUID uuid) { antycobwebCooldowns.put(uuid, System.currentTimeMillis() + antycobwebCooldownSeconds * 1000L); }
    public void resetAntycobwebCooldown(UUID uuid) { antycobwebCooldowns.remove(uuid); }

    public boolean isOlejOnCooldown(UUID uuid) {
        Long until = olejCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getOlejCooldownRemaining(UUID uuid) {
        Long until = olejCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setOlejCooldown(UUID uuid) { olejCooldowns.put(uuid, System.currentTimeMillis() + olejCooldownSeconds * 1000L); }
    public void resetOlejCooldown(UUID uuid) { olejCooldowns.remove(uuid); }

    public boolean isMocVaderaOnCooldown(UUID uuid) {
        Long until = mocVaderCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getMocVaderaCooldownRemaining(UUID uuid) {
        Long until = mocVaderCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setMocVaderaCooldown(UUID uuid) { mocVaderCooldowns.put(uuid, System.currentTimeMillis() + mocVaderCooldownSeconds * 1000L); }
    public void resetMocVaderaCooldown(UUID uuid) { mocVaderCooldowns.remove(uuid); }

    public boolean isMocYodyOnCooldown(UUID uuid) {
        Long until = mocYodyCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getMocYodyCooldownRemaining(UUID uuid) {
        Long until = mocYodyCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setMocYodyCooldown(UUID uuid) { mocYodyCooldowns.put(uuid, System.currentTimeMillis() + mocYodyCooldownSeconds * 1000L); }
    public void resetMocYodyCooldown(UUID uuid) { mocYodyCooldowns.remove(uuid); }

    public boolean isCukierkiOnCooldown(UUID uuid) {
        Long until = cukierkiCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getCukierkiCooldownRemaining(UUID uuid) {
        Long until = cukierkiCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setCukierkiCooldown(UUID uuid) { cukierkiCooldowns.put(uuid, System.currentTimeMillis() + cukierkiCooldownSeconds * 1000L); }
    public void resetCukierkiCooldown(UUID uuid) { cukierkiCooldowns.remove(uuid); }

    public boolean isKamienOdrodzeniOnCooldown(UUID uuid) {
        Long until = kamienOdrodzeniaCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getKamienOdrodzeniCooldownRemaining(UUID uuid) {
        Long until = kamienOdrodzeniaCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setKamienOdrodzeniCooldown(UUID uuid) { kamienOdrodzeniaCooldowns.put(uuid, System.currentTimeMillis() + kamienOdrodzeniaCooldownSeconds * 1000L); }
    public void resetKamienOdrodzeniCooldown(UUID uuid) { kamienOdrodzeniaCooldowns.remove(uuid); }

    public boolean isKozaOnCooldown(UUID uuid) {
        Long until = kozaCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getKozaCooldownRemaining(UUID uuid) {
        Long until = kozaCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setKozaCooldown(UUID uuid) { kozaCooldowns.put(uuid, System.currentTimeMillis() + kozaCooldownSeconds * 1000L); }
    public void resetKozaCooldown(UUID uuid) { kozaCooldowns.remove(uuid); }

    // Cooldown na gracza, NA KTORYM uzyto koze (nie mozna go zamknac na arenie ponownie przez kazda inna koze)
    public boolean isKozaCelOnCooldown(UUID targetUuid) {
        Long until = kozaCelCooldowns.get(targetUuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getKozaCelCooldownRemaining(UUID targetUuid) {
        Long until = kozaCelCooldowns.get(targetUuid);
        if (until == null) return 0;
        return Math.max(1, (until - System.currentTimeMillis() + 999L) / 1000L);
    }
    public void setKozaCelCooldown(UUID targetUuid) { kozaCelCooldowns.put(targetUuid, System.currentTimeMillis() + kozaCelCooldownSeconds * 1000L); }
    public int getKozaCelCooldownSeconds() { return kozaCelCooldownSeconds; }

    public boolean isEventowkaRandomOnCooldown(UUID uuid) {
        Long until = eventowkaRandomCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getEventowkaRandomCooldownRemaining(UUID uuid) {
        Long until = eventowkaRandomCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }
    public void setEventowkaRandomCooldown(UUID uuid) { eventowkaRandomCooldowns.put(uuid, System.currentTimeMillis() + eventowkaRandomCooldownSeconds * 1000L); }
    public void resetEventowkaRandomCooldown(UUID uuid) { eventowkaRandomCooldowns.remove(uuid); }

    public boolean isKosaGrabazaOnCooldown(UUID uuid) {
        Long until = kosaGrabazaCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getKosaGrabazaCooldownRemaining(UUID uuid) {
        Long until = kosaGrabazaCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999L) / 1000L);
    }
    public void setKosaGrabazaCooldown(UUID uuid) {
        kosaGrabazaCooldowns.put(uuid, System.currentTimeMillis() + kosaGrabazaCooldownSeconds * 1000L);
    }

    public boolean isGwozdzTrumnyOnCooldown(UUID uuid) {
        Long until = gwozdzTrumnyCooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }
    public long getGwozdzTrumnyCooldownRemaining(UUID uuid) {
        Long until = gwozdzTrumnyCooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999L) / 1000L);
    }
    public void setGwozdzTrumnyCooldown(UUID uuid) {
        gwozdzTrumnyCooldowns.put(uuid, System.currentTimeMillis() + gwozdzTrumnyCooldownSeconds * 1000L);
    }

    public void zablokujFajerwerki(UUID uuid, int sekundy) {
        if (sekundy <= 0) return;
        blokadaFajerwerki.put(uuid, System.currentTimeMillis() + sekundy * 1000L);
    }
    public boolean maBlokadeFajerwerki(UUID uuid) {
        Long until = blokadaFajerwerki.get(uuid);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            blokadaFajerwerki.remove(uuid);
            return false;
        }
        return true;
    }
    public long getBlokadaFajerwerkiRemaining(UUID uuid) {
        Long until = blokadaFajerwerki.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999L) / 1000L);
    }

    public void zamroz(UUID uuid, int sekundy) {
        if (sekundy <= 0) return;
        zamrozenie.put(uuid, System.currentTimeMillis() + sekundy * 1000L);
    }
    public boolean jestZamrozony(UUID uuid) {
        Long until = zamrozenie.get(uuid);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            zamrozenie.remove(uuid);
            return false;
        }
        return true;
    }

    // ===================== Wiadomosci =====================

    public String getMsgKokosEaten() { return msgKokosEaten; }
    public String getMsgKokosCooldown(long seconds) { return msgKokosCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgLampionCooldown(long seconds) { return msgLampionCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgLampionActivated() { return msgLampionActivated; }
    public String getMsgAntycobwebCooldown(long seconds) { return msgAntycobwebCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgAntycobwebRemoved(int count) { return msgAntycobwebRemoved.replace("{count}", String.valueOf(count)); }
    public String getMsgAntycobwebNone() { return msgAntycobwebNone; }
    public String getMsgOlejCooldown(long seconds) { return msgOlejCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgOlejActivated() { return msgOlejActivated; }
    public String getMsgMocVaderCooldown(long seconds) { return msgMocVaderCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgMocVaderActivated() { return msgMocVaderActivated; }
    public String getMsgMocYodyCooldown(long seconds) { return msgMocYodyCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgMocYodyActivated() { return msgMocYodyActivated; }
    public String getMsgCukierkiCooldown(long seconds) { return msgCukierkiCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgCukierkiActivated() { return msgCukierkiActivated; }
    public String getMsgJablkoJediEaten() { return msgJablkoJediEaten; }
    public String getMsgJablkoJediBonus() { return msgJablkoJediBonus; }
    public String getMsgKamienOdrodzeniaCooldown(long seconds) { return msgKamienOdrodzeniaCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgKamienOdrodzeniActivated() { return msgKamienOdrodzeniActivated; }
    public String getMsgKozaCooldown(long seconds) { return msgKozaCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgKozaActivated(String gracz, int seconds) { return msgKozaActivated.replace("{gracz}", gracz).replace("{czas}", String.valueOf(seconds)); }
    public String getMsgKozaTooFar() { return msgKozaTooFar; }
    public String getMsgKozaBusy() { return msgKozaBusy; }
    public String getMsgKozaElytra() { return msgKozaElytra; }
    public String getMsgKozaAlreadyUsed(long seconds) { return msgKozaAlreadyUsed.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgEventowkaRandomCooldown(long seconds) { return msgEventowkaRandomCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgEventowkaRandomActivated(String efekt) { return msgEventowkaRandomActivated.replace("{efekt}", efekt); }
    public String getMsgSakiewkaReceived(String gracz) { return msgSakiewkaReceived.replace("{gracz}", gracz); }
    public String getMsgSakiewkaFull() { return msgSakiewkaFull; }
    public String getMsgKosaGrabazaCooldown(long seconds) { return msgKosaGrabazaCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgKosaGrabazaHit(String gracz) { return msgKosaGrabazaHit.replace("{gracz}", gracz); }
    public String getMsgKosaGrabazaVictim(String gracz) { return msgKosaGrabazaVictim.replace("{gracz}", gracz); }
    public String getMsgKosaGrabazaFajerwerka(long seconds) { return msgKosaGrabazaFajerwerka.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgGwozdzTrumnyCooldown(long seconds) { return msgGwozdzTrumnyCooldown.replace("{czas}", String.valueOf(seconds)); }
    public String getMsgGwozdzTrumnyUsed(String gracz) { return msgGwozdzTrumnyUsed.replace("{gracz}", gracz); }
    public String getMsgGwozdzTrumnyVictim(String gracz) { return msgGwozdzTrumnyVictim.replace("{gracz}", gracz); }
    public String getMsgGuiTitle() { return col(msgGuiTitle); }
    public String getMsgGuiReceived(String itemName) { return msgGuiReceived.replace("{item}", itemName); }

    // ===================== Gettery =====================

    public int getKokosRegenAmplifier() { return kokosRegenAmplifier; }
    public int getKokosRegenDuration() { return kokosRegenDuration; }
    public int getKokosAbsorptionAmplifier() { return kokosAbsorptionAmplifier; }
    public int getKokosAbsorptionDuration() { return kokosAbsorptionDuration; }
    public int getKokosSpeedAmplifier() { return kokosSpeedAmplifier; }
    public int getKokosSpeedDuration() { return kokosSpeedDuration; }
    public int getLampionCooldownSeconds() { return lampionCooldownSeconds; }
    public int getLampionRegenAmplifier() { return lampionRegenAmplifier; }
    public int getLampionRegenDuration() { return lampionRegenDuration; }
    public int getAntycobwebCooldownSeconds() { return antycobwebCooldownSeconds; }
    public int getAntycobwebRadius() { return antycobwebRadius; }
    public int getOlejCooldownSeconds() { return olejCooldownSeconds; }
    public int getOlejRegenAmplifier() { return olejRegenAmplifier; }
    public int getOlejRegenDuration() { return olejRegenDuration; }
    public int getMocVaderCooldownSeconds() { return mocVaderCooldownSeconds; }
    public int getMocVaderDuration() { return mocVaderDuration; }
    public int getMocYodyCooldownSeconds() { return mocYodyCooldownSeconds; }
    public int getMocYodyLuckDuration() { return mocYodyLuckDuration; }

    public int getCukierkiCooldownSeconds() { return cukierkiCooldownSeconds; }
    public int getCukierkiStrengthDuration() { return cukierkiStrengthDuration; }
    public int getCukierkiStrengthAmplifier() { return cukierkiStrengthAmplifier; }
    public int getJablkoJediRegenAmplifier() { return jablkoJediRegenAmplifier; }
    public int getJablkoJediRegenDuration() { return jablkoJediRegenDuration; }
    public int getJablkoJediAbsorptionAmplifier() { return jablkoJediAbsorptionAmplifier; }
    public int getJablkoJediAbsorptionDuration() { return jablkoJediAbsorptionDuration; }
    public double getJablkoJediBonusChance() { return jablkoJediBonusChance; }
    public int getJablkoJediBonusAbsorptionAmplifier() { return jablkoJediBonusAbsorptionAmplifier; }
    public int getKamienOdrodzeniaCooldownSeconds() { return kamienOdrodzeniaCooldownSeconds; }
    public int getKamienOdrodzeniHearts() { return kamienOdrodzeniHearts; }
    public int getKozaCooldownSeconds() { return kozaCooldownSeconds; }
    public int getKozaWorldBorderDuration() { return kozaWorldBorderDuration; }
    public int getKozaWorldBorderSize() { return kozaWorldBorderSize; }
    public int getKozaWorldBorderPlaceExtra() { return kozaWorldBorderPlaceExtra; }
    public int getEventowkaRandomCooldownSeconds() { return eventowkaRandomCooldownSeconds; }
    public int getEventowkaRandomEffectDuration() { return eventowkaRandomEffectDuration; }
    public boolean isSakiewkaEnabled() { return sakiewkaEnabled; }
    public int getKosaGrabazaCooldownSeconds() { return kosaGrabazaCooldownSeconds; }
    public int getKosaGrabazaWeaknessDuration() { return kosaGrabazaWeaknessDuration; }
    public int getKosaGrabazaGlowingDuration() { return kosaGrabazaGlowingDuration; }
    public int getKosaGrabazaFireworkBlockSeconds() { return kosaGrabazaFireworkBlockSeconds; }
    public int getKosaGrabazaHearts() { return kosaGrabazaHearts; }
    public int getGwozdzTrumnyCooldownSeconds() { return gwozdzTrumnyCooldownSeconds; }
    public int getGwozdzTrumnyFreezeSeconds() { return gwozdzTrumnyFreezeSeconds; }
    public int getGwozdzTrumnyWeaknessDuration() { return gwozdzTrumnyWeaknessDuration; }
    public int getGwozdzTrumnyWeaknessAmplifier() { return gwozdzTrumnyWeaknessAmplifier; }

    public static String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
