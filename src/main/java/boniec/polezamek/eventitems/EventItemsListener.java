package boniec.dev.polezamek.eventitems;

import boniec.dev.polezamek.Main;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EventItemsListener implements Listener {

    private final Main plugin;
    private final EventItemsManager manager;
    private final NamespacedKey smoczaRakietaKey;

    public EventItemsListener(Main plugin, EventItemsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.smoczaRakietaKey = new NamespacedKey(plugin.getHost(), "smocza_rakieta");
    }

    // ===================== KOKOS - jedzenie =====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (manager.isJablkoJedi(item)) {
            handleJablkoJedi(event);
            return;
        }
        if (!manager.isKokos(item)) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        EquipmentSlot handSlot = event.getHand();
        ItemStack handItem = (handSlot == EquipmentSlot.OFF_HAND)
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (handItem.getAmount() > 1) {
            handItem.setAmount(handItem.getAmount() - 1);
        } else {
            if (handSlot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        }

        int regenDur = manager.getKokosRegenDuration() * 20;
        int absDur = manager.getKokosAbsorptionDuration() * 20;
        int speedDur = manager.getKokosSpeedDuration() * 20;

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDur, manager.getKokosRegenAmplifier()));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absDur, manager.getKokosAbsorptionAmplifier()));
        if (manager.getKokosSpeedDuration() > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDur, manager.getKokosSpeedAmplifier()));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
        player.sendMessage(color(manager.getMsgKokosEaten()));
    }

    private void handleJablkoJedi(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        int regenDur = manager.getJablkoJediRegenDuration() * 20;
        int absDur = manager.getJablkoJediAbsorptionDuration() * 20;
        boolean bonus = ThreadLocalRandom.current().nextDouble() < manager.getJablkoJediBonusChance();
        int absAmp = bonus
                ? manager.getJablkoJediBonusAbsorptionAmplifier()
                : manager.getJablkoJediAbsorptionAmplifier();

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDur, manager.getJablkoJediRegenAmplifier()));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absDur, absAmp));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1.15f);
        if (bonus) {
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.35f, 1.6f);
        }
    }

    // ===================== LAMPION - prawy klik =====================

    @EventHandler
    public void onLampionInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!manager.isLampion(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (manager.isLampionOnCooldown(uuid)) {
            long remaining = manager.getLampionCooldownRemaining(uuid);
            player.sendMessage(color(manager.getMsgLampionCooldown(remaining)));
            return;
        }

        manager.setLampionCooldown(uuid);
        player.setCooldown(Material.LANTERN, manager.getLampionCooldownSeconds() * 20);

        int regenDur = manager.getLampionRegenDuration() * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDur, manager.getLampionRegenAmplifier()));
        player.sendMessage(color(manager.getMsgLampionActivated()));

        new BukkitRunnable() {
            @Override
            public void run() {
                manager.resetLampionCooldown(uuid);
            }
        }.runTaskLater(plugin.getHost(), manager.getLampionCooldownSeconds() * 20L);
    }

    // ===================== ANTYCOBWEB - prawy klik =====================

    @EventHandler
    public void onAntycobwebInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!manager.isAntycobweb(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (manager.isAntycobwebOnCooldown(uuid)) {
            long remaining = manager.getAntycobwebCooldownRemaining(uuid);
            player.sendMessage(color(manager.getMsgAntycobwebCooldown(remaining)));
            return;
        }

        manager.setAntycobwebCooldown(uuid);

        Location loc = player.getLocation();
        int radius = manager.getAntycobwebRadius();
        int count = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = loc.getWorld().getBlockAt(
                            loc.getBlockX() + x,
                            loc.getBlockY() + y,
                            loc.getBlockZ() + z
                    );
                    if (block.getType() == Material.COBWEB) {
                        block.setType(Material.AIR);
                        count++;
                    }
                }
            }
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        if (count > 0) {
            player.sendMessage(color(manager.getMsgAntycobwebRemoved(count)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
        } else {
            player.sendMessage(color(manager.getMsgAntycobwebNone()));
        }
    }

    // ===================== OLEJ - prawy klik =====================

    @EventHandler
    public void onOlejInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!manager.isOlej(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (manager.isOlejOnCooldown(uuid)) {
            long remaining = manager.getOlejCooldownRemaining(uuid);
            player.sendMessage(color(manager.getMsgOlejCooldown(remaining)));
            return;
        }

        manager.setOlejCooldown(uuid);
        player.setCooldown(Material.SOUL_LANTERN, manager.getOlejCooldownSeconds() * 20);

        int regenDur = manager.getOlejRegenDuration() * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDur, manager.getOlejRegenAmplifier()));
        player.sendMessage(color(manager.getMsgOlejActivated()));

        new BukkitRunnable() {
            @Override
            public void run() {
                manager.resetOlejCooldown(uuid);
            }
        }.runTaskLater(plugin.getHost(), manager.getOlejCooldownSeconds() * 20L);
    }

    // ===================== MOC VADERA - prawy klik =====================

    @EventHandler
    public void onMocVaderaInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!manager.isMocVadera(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (manager.isMocVaderaOnCooldown(uuid)) {
            long remaining = manager.getMocVaderaCooldownRemaining(uuid);
            player.sendMessage(color(manager.getMsgMocVaderCooldown(remaining)));
            return;
        }

        manager.setMocVaderaCooldown(uuid);
        player.setCooldown(Material.RED_DYE, manager.getMocVaderCooldownSeconds() * 20);

        player.addPotionEffect(new PotionEffect(PotionEffectType.BAD_OMEN, manager.getMocVaderDuration() * 20, 0));
        player.sendMessage(color(manager.getMsgMocVaderActivated()));

        new BukkitRunnable() {
            @Override
            public void run() {
                manager.resetMocVaderaCooldown(uuid);
            }
        }.runTaskLater(plugin.getHost(), manager.getMocVaderCooldownSeconds() * 20L);
    }

    // ===================== MOC YODY - prawy klik =====================

    @EventHandler
    public void onMocYodyInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!manager.isMocYody(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (manager.isMocYodyOnCooldown(uuid)) {
            long remaining = manager.getMocYodyCooldownRemaining(uuid);
            player.sendMessage(color(manager.getMsgMocYodyCooldown(remaining)));
            return;
        }

        manager.setMocYodyCooldown(uuid);
        player.setCooldown(Material.GREEN_DYE, manager.getMocYodyCooldownSeconds() * 20);

        int luckDur = manager.getMocYodyLuckDuration() * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, luckDur, 0));
        player.sendMessage(color(manager.getMsgMocYodyActivated()));

        new BukkitRunnable() {
            @Override
            public void run() {
                manager.resetMocYodyCooldown(uuid);
            }
        }.runTaskLater(plugin.getHost(), manager.getMocYodyCooldownSeconds() * 20L);
    }

    // ===================== CUKIERKI - prawy klik =====================

    @EventHandler
    public void onCukierkiInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!manager.isCukierki(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (manager.isCukierkiOnCooldown(uuid)) {
            player.sendMessage(color(manager.getMsgCukierkiCooldown(manager.getCukierkiCooldownRemaining(uuid))));
            return;
        }

        manager.setCukierkiCooldown(uuid);
        player.setCooldown(EventItemsManager.CUKIERKI_MATERIAL, manager.getCukierkiCooldownSeconds() * 20);

        // Sila I (amplifier 0) - zmienialne w eventowki.yml: cukierki.strength-amplifier
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, manager.getCukierkiStrengthDuration() * 20, manager.getCukierkiStrengthAmplifier()));
        player.sendMessage(color(manager.getMsgCukierkiActivated()));

        consumeOne(player);
    }

    // ===================== SMOCZA FAJERWERKA - nieskonczona =====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSmoczaFajerwerka(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean mainOurs = manager.isSmoczaFajerwerka(main);
        ItemStack rocket;
        if (mainOurs) {
            if (event.getHand() != EquipmentSlot.HAND) return;
            rocket = main;
        } else if (manager.isSmoczaFajerwerka(off)) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) return;
            rocket = off;
        } else {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;
        if (zablokujFajerwerke(player)) return;

        ItemStack clone = rocket.clone();
        clone.setAmount(1);
        if (player.isGliding()) {
            try {
                player.fireworkBoost(clone);
                oznaczSmoczeRakiety(player);
                return;
            } catch (Throwable ignored) {
            }
        }
        Location loc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.4));
        Firework fw = player.getWorld().spawn(loc, Firework.class);
        if (clone.getItemMeta() instanceof FireworkMeta fm) {
            fw.setFireworkMeta(fm);
        }
        tagSmoczaRakieta(fw);
        fw.setShotAtAngle(player.isGliding());
        if (player.isGliding()) {
            fw.setVelocity(player.getLocation().getDirection().multiply(1.6));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSmoczaFajerwerkaWybuch(FireworkExplodeEvent event) {
        if (!jestSmoczaRakieta(event.getEntity())) return;
        event.setCancelled(true);
        event.getEntity().remove();
    }

    private void oznaczSmoczeRakiety(Player player) {
        oznaczPrzy(player);
        Bukkit.getScheduler().runTask(plugin.getHost(), () -> oznaczPrzy(player));
    }

    private void oznaczPrzy(Player player) {
        for (Entity e : player.getNearbyEntities(3, 3, 3)) {
            if (!(e instanceof Firework fw)) continue;
            if (player.equals(fw.getShooter())
                    || manager.isSmoczaFajerwerkaMeta(fw.getFireworkMeta())
                    || fw.getLocation().distanceSquared(player.getLocation()) < 4) {
                tagSmoczaRakieta(fw);
            }
        }
    }

    private void tagSmoczaRakieta(Firework fw) {
        fw.getPersistentDataContainer().set(smoczaRakietaKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean jestSmoczaRakieta(Firework fw) {
        if (fw.getPersistentDataContainer().has(smoczaRakietaKey, PersistentDataType.BYTE)) return true;
        try {
            if (manager.isSmoczaFajerwerka(fw.getItem())) return true;
        } catch (Throwable ignored) {
        }
        return manager.isSmoczaFajerwerkaMeta(fw.getFireworkMeta());
    }

    // ===================== KAMIEN ODRODZENIA - prawy klik =====================

    @EventHandler
    public void onKamienOdrodzeniInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!manager.isKamienOdrodzenia(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (manager.isKamienOdrodzeniOnCooldown(uuid)) {
            player.sendMessage(color(manager.getMsgKamienOdrodzeniaCooldown(manager.getKamienOdrodzeniCooldownRemaining(uuid))));
            return;
        }

        manager.setKamienOdrodzeniCooldown(uuid);
        player.setCooldown(EventItemsManager.KAMIEN_ODRODZENIA_MATERIAL, manager.getKamienOdrodzeniaCooldownSeconds() * 20);

        // 4 serca = 8 pol-serc zdrowia (leczenie do maksymalnego zdrowia)
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(Math.min(player.getHealth() + manager.getKamienOdrodzeniHearts() * 2.0, maxHealth));

        player.sendMessage(color(manager.getMsgKamienOdrodzeniActivated()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        // Kamien Odrodzenia jest nieskonczony - nie zuzywa sie, ogranicza go tylko cooldown
    }

    // ===================== EVENTOWKA LOSOWA - prawy klik =====================

    @EventHandler
    public void onEventowkaRandomInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack tool = event.getItem();
        if (!manager.isEventowkaRandom(tool)) return;

        event.setCancelled(true);

        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        UUID uuid = player.getUniqueId();

        if (manager.isEventowkaRandomOnCooldown(uuid)) {
            player.sendMessage(color(manager.getMsgEventowkaRandomCooldown(manager.getEventowkaRandomCooldownRemaining(uuid))));
            return;
        }

        manager.setEventowkaRandomCooldown(uuid);
        player.setCooldown(Material.AMETHYST_CLUSTER, manager.getEventowkaRandomCooldownSeconds() * 20);

        int duration = manager.getEventowkaRandomEffectDuration() * 20;
        String effectName;

        // kazdy z 5 efektow ma po 20% szans
        switch (ThreadLocalRandom.current().nextInt(5)) {
            case 0 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 2));
                effectName = "&f亞 &cSiła III";
            }
            case 1 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 0));
                effectName = "&f亚 &7Osłabienie I";
            }
            case 2 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 2));
                effectName = "&f亐 &2Trucizna III";
            }
            case 3 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 0));
                effectName = "&f临 &8Oślepienie";
            }
            default -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
                effectName = "&f互 &bSpeed II";
            }
        }

        player.sendMessage(color(manager.getMsgEventowkaRandomActivated(effectName)));
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);

        consumeOne(player);
    }

    // ===================== SAKIEWKA MANDALORIANINA =====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSakiewkaKill(PlayerDeathEvent event) {
        if (!manager.isSakiewkaEnabled()) {
            return;
        }

        Player ofiara = event.getEntity();
        Player zabojca = ofiara.getKiller();
        if (zabojca == null || zabojca.equals(ofiara)) {
            return;
        }
        if (!manager.hasEmptySakiewka(zabojca)) {
            return;
        }

        List<ItemStack> dropy = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            dropy.add(drop.clone());
        }
        if (dropy.isEmpty()) {
            return;
        }

        List<ItemStack> reszta = manager.wlozDoSakiewki(zabojca, dropy);

        event.getDrops().clear();

        boolean cosNaZiemie = false;
        for (ItemStack item : reszta) {
            ofiara.getWorld().dropItemNaturally(ofiara.getLocation(), item);
            cosNaZiemie = true;
        }

        zabojca.sendMessage(color(manager.getMsgSakiewkaReceived(ofiara.getName())));
        if (cosNaZiemie) {
            zabojca.sendMessage(color(manager.getMsgSakiewkaFull()));
        }
        zabojca.playSound(zabojca.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSakiewkaPlace(BlockPlaceEvent event) {
        if (manager.isSakiewka(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSakiewkaInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!manager.isSakiewka(event.getItem())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKosaGrabazaHit(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (attacker.equals(victim)) return;
        if (boniec.dev.polezamek.DuelManager.izolujEventowki(attacker.getUniqueId(), victim.getUniqueId())) return;
        if (!manager.isKosaGrabaza(attacker.getInventory().getItemInMainHand())) return;
        if (plugin.isEventItemUseBlocked(attacker.getLocation())) return;

        if (manager.isKosaGrabazaOnCooldown(attacker.getUniqueId())) {
            return;
        }

        manager.setKosaGrabazaCooldown(attacker.getUniqueId());
        attacker.setCooldown(EventItemsManager.KOSA_GRABAZA_MATERIAL, manager.getKosaGrabazaCooldownSeconds() * 20);

        int weakTicks = manager.getKosaGrabazaWeaknessDuration() * 20;
        int glowTicks = manager.getKosaGrabazaGlowingDuration() * 20;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weakTicks, 0, true, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0, true, true, true));
        manager.zablokujFajerwerki(victim.getUniqueId(), manager.getKosaGrabazaFireworkBlockSeconds());
        event.setDamage(event.getDamage() + manager.getKosaGrabazaHearts() * 2.0);

        attacker.sendMessage(color(manager.getMsgKosaGrabazaHit(victim.getName())));
        victim.sendMessage(color(manager.getMsgKosaGrabazaVictim(attacker.getName())));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.7f, 0.7f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.2f);
    }

    @EventHandler
    public void onKosaGrabazaTill(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!manager.isKosaGrabaza(event.getItem())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onGwozdzTrumnyUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player player = event.getPlayer();
        if (!manager.isGwozdzTrumny(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        if (target.equals(player) || target.hasMetadata("NPC")) return;
        if (boniec.dev.polezamek.DuelManager.izolujEventowki(player.getUniqueId(), target.getUniqueId())) return;
        if (plugin.isEventItemUseBlocked(player.getLocation())) return;

        if (manager.isGwozdzTrumnyOnCooldown(player.getUniqueId())) {
            player.sendMessage(color(manager.getMsgGwozdzTrumnyCooldown(manager.getGwozdzTrumnyCooldownRemaining(player.getUniqueId()))));
            return;
        }

        manager.setGwozdzTrumnyCooldown(player.getUniqueId());
        player.setCooldown(EventItemsManager.GWOZDZ_TRUMNY_MATERIAL, manager.getGwozdzTrumnyCooldownSeconds() * 20);

        int freezeTicks = manager.getGwozdzTrumnyFreezeSeconds() * 20;
        int weakTicks = manager.getGwozdzTrumnyWeaknessDuration() * 20;
        manager.zamroz(target.getUniqueId(), manager.getGwozdzTrumnyFreezeSeconds());
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks, 255, true, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weakTicks, manager.getGwozdzTrumnyWeaknessAmplifier(), true, true, true));
        consumeOne(player);

        player.sendMessage(color(manager.getMsgGwozdzTrumnyUsed(target.getName())));
        target.sendMessage(color(manager.getMsgGwozdzTrumnyVictim(player.getName())));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 0.6f);
        target.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlokadaFajerwerkiUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREWORK_ROCKET) return;
        if (zablokujFajerwerke(event.getPlayer())) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlokadaFajerwerkiLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Firework)) return;
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        if (zablokujFajerwerke(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onGwozdzFreezeMove(PlayerMoveEvent event) {
        if (!manager.jestZamrozony(event.getPlayer().getUniqueId())) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        var look = event.getFrom().clone();
        look.setYaw(event.getTo().getYaw());
        look.setPitch(event.getTo().getPitch());
        event.setTo(look);
    }

    private boolean zablokujFajerwerke(Player player) {
        if (!manager.maBlokadeFajerwerki(player.getUniqueId())) return false;
        long left = manager.getBlokadaFajerwerkiRemaining(player.getUniqueId());
        player.sendMessage(color(manager.getMsgKosaGrabazaFajerwerka(left)));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        return true;
    }

    private void consumeOne(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
