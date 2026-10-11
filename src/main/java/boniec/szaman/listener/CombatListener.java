package boniec.szaman.listener;

import boniec.hitsystem.core.SetIdentifier;
import boniec.szaman.SzamanPlugin;
import boniec.szaman.core.BonusCalculator;
import boniec.szaman.core.Perk;
import boniec.szaman.core.PerkManager;
import boniec.szaman.util.Msg;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Efekty walki artefaktow Szamana (jeden handler "on damage"):
 *  0) Artefakt Blokady - szansa, ze BRONIACY sie gracz calkiem zablokuje cios,
 *  1) mnoznik glowing (domyslnie +7,5% obrazen na oznaczonym celu, weakening.damage-bonus),
 *  1b) Artefakt Oslabienia - szansa na nalozenie glowingu na trafionym celu,
 *  2) Artefakt Sily - +% do zadawanych obrazen (tylko vs moby - PvP liczy HitSystemListener),
 *  3) Artefakt Wampiryzmu - szansa na wyleczenie atakujacego,
 *  4) Artefakt Uwiezienia - szansa na blokade fajerwerk u trafionego GRACZA (patrz MovementListener),
 *  5) limit obrazen PvP (maxHit = 15).
 * <p>
 * Wartosci (szanse, % obrazen) pochodza z {@link BonusCalculator} - czyli z kupionego poziomu
 * w /szaman plus ewentualny bonus z kostiumu/aury.
 * <p>
 * Perki walki dzialaja gdy atakujacy trzyma Netherite Sword albo item z Sharpness.
 * WorldGuard: perki nie dzialaja w regionach z flaga pvp = deny.
 */
public class CombatListener implements Listener {

    private final SzamanPlugin plugin;
    private final Random random = new Random();
    private final SetIdentifier setIdentifier = new SetIdentifier();

    // Cache flagi WorldGuard - obliczane RAZ w konstruktorze zamiast przy KAZDYM hicie.
    private final boolean worldGuardEnabled;

    // Mapa: attackerUuid -> (victimUuid -> czyZatrafiony)
    // Sledzenie pierwszych trafien na gracze dla artefaktu Skrytobojcy
    // Resetuje sie po wylogowaniu ofiary (zob. onQuit)
    private final java.util.Map<UUID, java.util.Set<UUID>> celownicyPierwszegohitu = new java.util.HashMap<>();

    public CombatListener(SzamanPlugin plugin) {
        this.plugin = plugin;
        org.bukkit.plugin.Plugin wg = org.bukkit.Bukkit.getPluginManager().getPlugin("WorldGuard");
        this.worldGuardEnabled = wg != null && wg.isEnabled();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamage(EntityDamageByEntityEvent event) {
        // Hit anulowany wczesniej (np. ignorowany przez i-frames w HitSystemListener) nie moze
        // odpalac perkow - w vanilla takie hity w ogole nie sa eventem.
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victimEntity)) return;

        // WorldGuard: perki nie dzialaja w regionach z pvp = deny
        if (isWorldGuardPvpDenied(attacker, victimEntity)) return;

        // --- ARTEFAKT BLOKADY (perk OBRONCY) ---
        // Dziala przeciwko KAZDEMU ciosowi gracza, niezaleznie od tego co trzyma atakujacy.
        if (victimEntity instanceof Player blokujacy) {
            double szansaBlokady = plugin.getBonusCalculator().getSzansaBlokady(blokujacy.getUniqueId());
            if (szansaBlokady > 0 && szansa(szansaBlokady)) {
                event.setCancelled(true);
                FileConfiguration c = plugin.getSzamanConfig();
                Msg.send(attacker, c.getString("lang.block-attacker", "&b&lBlokada &8» &fTwój cios został zablokowany przez &b{player}&f!")
                        .replace("{player}", blokujacy.getName()));
                Msg.send(blokujacy, c.getString("lang.block-victim", "&b&lBlokada &8» &fZablokowałeś cios gracza &b{player}&f!")
                        .replace("{player}", attacker.getName()));
                blokujacy.getWorld().playSound(blokujacy.getLocation(), Sound.ITEM_SHIELD_BLOCK, org.bukkit.SoundCategory.PLAYERS, 1.2f, 1.0f);
                return;
            }
        }

        // Perki ATAKUJACEGO: Netherite Sword albo dowolny item z Sharpness (jak HitSystem).
        org.bukkit.inventory.ItemStack heldItem = attacker.getInventory().getItemInMainHand();
        if (!setIdentifier.isHitSystemWeapon(heldItem)) {
            return;
        }

        BonusCalculator calc = plugin.getBonusCalculator();
        PerkManager pm = plugin.getPerkManager();
        UUID attackerUuid = attacker.getUniqueId();

        Player victim = victimEntity instanceof Player p ? p : null;
        UUID victimUuid = victim != null ? victim.getUniqueId() : null;

        double damage = event.getDamage();

        // --- MECHANIKA: +X% dmg na oznaczonym (glowing) przeciwniku (domyslnie 7,5%) ---
        if (victimEntity.hasPotionEffect(PotionEffectType.GLOWING)) {
            double bonusGlowing = plugin.getSzamanConfig().getDouble("weakening.damage-bonus", 7.5);
            damage = damage * (1.0 + bonusGlowing / 100.0);
        }

        // --- ARTEFAKT SILY ---
        // UWAGA: dla walki Player-vs-Player ten bonus jest JUZ doliczony w HitSystemListener
        // (priorytet LOWEST, dziala PRZED tym listenerem i ustawia bazowy damage z systemu
        // setow). Doliczenie go tutaj jeszcze raz dla PvP podwajaloby bonus. Dla walki z
        // mobami (victim == null) HitSystemListener sie nie odpala, wiec doliczamy tu.
        if (victim == null) {
            double dmgProcent = calc.getObrazeniaProcent(attackerUuid);
            if (dmgProcent > 0) {
                damage = damage * (1 + (dmgProcent / 100.0));
            }
            // Siła/Odporność vs moby zostają vanillowe; Zła wróżba nie daje dmg sama z siebie.
            org.bukkit.potion.PotionEffect zlaWrozba = attacker.getPotionEffect(PotionEffectType.BAD_OMEN);
            if (zlaWrozba != null) {
                damage = damage * (1.0 + 0.10 * (zlaWrozba.getAmplifier() + 1));
            }
        }

        // --- ARTEFAKT SKRYTOBOJCY: bonus na pierwsze trafienie gracza ---
        // Dziala TYLKO gdy atakujacy trzyma Netherite Sword i atakuje GRACZA (nie moba)
        if (victim != null) {
            double skrytobojcaProcent = calc.getSkrytobojcaProcent(attackerUuid);
            if (skrytobojcaProcent > 0) {
                java.util.Set<UUID> zaatakownymi = celownicyPierwszegohitu.getOrDefault(attackerUuid, new java.util.HashSet<>());
                if (!zaatakownymi.contains(victimUuid)) {
                    // Pierwsze trafienie na tego gracza!
                    damage = damage * (1 + (skrytobojcaProcent / 100.0));
                    zaatakownymi.add(victimUuid);
                    celownicyPierwszegohitu.put(attackerUuid, zaatakownymi);
                    
                    FileConfiguration c = plugin.getSzamanConfig();
                    Msg.send(attacker, c.getString("lang.backstab-attacker", "&c&lSkrytobójca &8» &fPierwsze trafienie na &c{player}&f! Otrzymałeś bonus &c+{bonus}% &fobrażeń!")
                            .replace("{player}", victim.getName())
                            .replace("{bonus}", Perk.formatLiczby(skrytobojcaProcent)));
                    Msg.send(victim, c.getString("lang.backstab-victim", "&c&lSkrytobójca &8» &fGracz &c{player}&f zaatakował cię ze &cSkrytobójcy &f!")
                            .replace("{player}", attacker.getName()));
                    attacker.playSound(attacker.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.5f);
                    victim.playSound(victim.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 1f);
                }
            }
        }

        double wampiryzm = calc.getSzansaWampiryzmu(attackerUuid);
        if (wampiryzm > 0 && szansa(wampiryzm)) {
            wyleczAtakujacego(attacker);
        }

        double oslabienie = calc.getSzansaOslabienia(attackerUuid);
        if (oslabienie > 0 && szansa(oslabienie)) {
            oslab(attacker, victimEntity, victim);
        }

        if (victim != null) {
            double uwiezienie = calc.getSzansaUwiezienia(attackerUuid);
            if (uwiezienie > 0 && !pm.czyUwieziony(victimUuid) && szansa(uwiezienie)) {
                uwiezij(attacker, victim, calc, pm);
            }
        }

        // --- LIMIT OBRAZEN PvP (maxHit = 15) ---
        if (victim != null) {
            double maxHit = 15;
            if (damage > maxHit) {
                damage = maxHit;
            }
        }

        if (Math.abs(damage - event.getDamage()) > 0.0001) {
            event.setDamage(damage);
        }
    }

    private void wyleczAtakujacego(Player attacker) {
        FileConfiguration cfg = plugin.getSzamanConfig();
        double min = cfg.getDouble("vampirism.heal-min", 0.5);
        double max = cfg.getDouble("vampirism.heal-max", 3.0);
        if (max < min) max = min;

        double serca = min + random.nextDouble() * (max - min);
        double heal = serca * 2;

        org.bukkit.attribute.AttributeInstance maxAi = ZyciaListener.instancjaMaxHealth(attacker);
        double maxZdrowie = maxAi != null ? maxAi.getValue() : 20.0;
        attacker.setHealth(Math.min(maxZdrowie, attacker.getHealth() + heal));

        Msg.send(attacker, cfg.getString("lang.vampirism-heal", "&c&lWampiryzm &8» &fZostałeś pomyślnie wyleczony o &c{hp}♥")
                .replace("{hp}", formatuj(serca)));
        attacker.playSound(attacker.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
    }

    private void oslab(Player attacker, LivingEntity cel, Player celGracz) {
        FileConfiguration cfg = plugin.getSzamanConfig();
        int sekundy = Math.max(1, cfg.getInt("weakening.duration-seconds", 5));
        cel.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.GLOWING, sekundy * 20, 0));
        Msg.send(attacker, cfg.getString("lang.weakening-attacker", "&e&lOsłabienie &8» &fOznaczyłeś przeciwnika! Otrzymuje o &e{bonus}% &fwiększe obrażenia &e({time}s)")
                .replace("{bonus}", Perk.formatLiczby(cfg.getDouble("weakening.damage-bonus", 7.5)))
                .replace("{time}", String.valueOf(sekundy)));
        if (celGracz != null) {
            Msg.send(celGracz, cfg.getString("lang.weakening-victim", "&e&lOsłabienie &8» &fGracz &e{player}&f oznaczył cię! Otrzymujesz większe obrażenia.")
                    .replace("{player}", attacker.getName()));
        }
        attacker.playSound(attacker.getLocation(), Sound.BLOCK_SHROOMLIGHT_BREAK, 1f, 1f);
    }

    private void uwiezij(Player attacker, Player victim, BonusCalculator calc, PerkManager pm) {
        FileConfiguration cfg = plugin.getSzamanConfig();
        double sekundy = calc.getCzasUwiezieniaSekundy(attacker.getUniqueId());
        if (sekundy <= 0) return;

        pm.ustawUwiezienie(victim.getUniqueId(), System.currentTimeMillis() + (long) (sekundy * 1000.0));

        String czas = Perk.formatLiczby(sekundy);
        Msg.send(victim, cfg.getString("lang.get-confinement", "&a&lUwięzienie &8» &fGracz &a{player}&f zablokował ci fajerwerki! &a({time} sekund)")
                .replace("{player}", attacker.getName())
                .replace("{time}", czas));
        Msg.send(attacker, cfg.getString("lang.put-in-confinement", "&a&lUwięzienie &8» &fZablokowano fajerwerki atakowanego gracza! &a({time} sekund)")
                .replace("{time}", czas));

        Sound dzwiekOfiary = parseSound(cfg.getString("music.victim", "BLOCK_ANVIL_DESTROY"), Sound.BLOCK_ANVIL_DESTROY);
        Sound dzwiekGracza = parseSound(cfg.getString("music.player", "BLOCK_ANVIL_PLACE"), Sound.BLOCK_ANVIL_PLACE);
        victim.playSound(victim.getLocation(), dzwiekOfiary, 1f, 1f);
        attacker.playSound(attacker.getLocation(), dzwiekGracza, 1f, 1f);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Smierc konczy uwiezienie (blokade fajerwerk).
        plugin.getPerkManager().usunUwiezienie(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getPerkManager().wyczyscPoWyjsciu(uuid);
        
        // Resetuj tracking Skrytobójcy: usun ten gracz jako cel i jako atakujacego
        celownicyPierwszegohitu.remove(uuid);
        celownicyPierwszegohitu.values().forEach(set -> set.remove(uuid));
    }

    private boolean szansa(double procent) {
        return random.nextDouble() * 100.0 < procent;
    }

    /**
     * Dzwiek z configu po nazwie (BLOCK_ANVIL_DESTROY). NIE uzywamy Sound.valueOf - w nowszych
     * wersjach Paper "Sound" nie jest juz enumem i valueOf wywala sie w runtime; Registry.SOUNDS
     * dziala niezaleznie od tego (ten sam wzorzec co w MistrzSystem).
     */
    private Sound parseSound(String nazwa, Sound zapasowy) {
        if (nazwa == null || nazwa.isBlank()) return zapasowy;
        String klucz = nazwa.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        try {
            NamespacedKey ns = klucz.contains(":") ? NamespacedKey.fromString(klucz) : NamespacedKey.minecraft(klucz);
            if (ns != null) {
                Sound dzwiek = Registry.SOUNDS.get(ns);
                if (dzwiek != null) return dzwiek;
            }
        } catch (Throwable ignored) {
            // zly klucz - uzywamy dzwieku zapasowego
        }
        return zapasowy;
    }

    private String kolor(String tekst) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', tekst);
    }

    // Klasyczna, zawsze dostepna w Spigot API metoda na action bar - niezaleznie od tego,
    // czy konkretny build API ma wygodne Player#sendActionBar(String).
    private void actionBar(Player gracz, String tekst) {
        gracz.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(kolor(tekst)));
    }

    private String formatuj(double wartosc) {
        return String.format(Locale.ROOT, "%.1f", wartosc);
    }

    /** WorldGuard: sprawdza czy pvp jest zablokowane w regionie ofiary. */
    private boolean isWorldGuardPvpDenied(Player attacker, LivingEntity victim) {
        if (!worldGuardEnabled) return false;
        try {
            return boniec.code.core.WorldGuardHook.isPvpDenied(victim.getLocation());
        } catch (Throwable t) {
            return false;
        }
    }
}
