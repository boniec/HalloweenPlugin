package boniec.hitsystem.listener;

import boniec.hitsystem.core.DamageCalculator;
import boniec.hitsystem.core.HitSystemManager;
import boniec.hitsystem.core.PotionDamageEffects;
import boniec.szaman.SzamanPlugin;
import boniec.szaman.core.BonusCalculator;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Przechwytuje ataki melee gracz-na-gracza i ustawia BAZOWY damage wyliczony
 * przez HitSystemManager (system setów), zamiast pozwalać liczyć go vanilla
 * Minecraftowi. Sharpness i Protection mogą nadal być fizycznie na
 * przedmiotach (nie usuwamy zaklęć), ale są aktywnie zerowane jako modifiery
 * obrażeń (patrz zeroOutVanillaEnchantModifiers) - NIE mają żadnego wpływu na
 * wynik. Do bazy z systemu setów doliczany jest mnożnik z Artefaktu Siły
 * (Szaman) - poziom kupiony w /szaman (+ bonus kostiumu/aury) to wprost +% do
 * obrażeń, patrz BonusCalculator#getObrazeniaProcent. Walkę z mobami liczy
 * CombatListener.
 *
 * Priorytet LOWEST: musimy zadziałać PRZED CombatListener (Szaman, priorytet
 * NORMAL), który dokłada swoje efekty walki (wampiryzm, uwięzienie, limit maxHit
 * itd.) NA WIERZCH wartości, którą tu ustawimy - w przeciwnym razie te efekty
 * byłyby wcześniej nadpisywane przez nas i przestawałyby działać. Wciąż
 * używamy ignoreCancelled = true, więc jeśli jakiś inny plugin/listener na
 * jeszcze niższym priorytecie już anulował event, nic tu nie robimy.
 */
public final class HitSystemListener implements Listener {

    private final HitSystemManager manager;

    /**
     * Ostatni hit zarejestrowany PRZEZ NAS na danej ofierze (nasze, a nie vanillowe
     * obrazenia + czas). Vanilla decyduje o i-frames (nietykalnosci po hicie) na
     * podstawie VANILLOWYCH obrazen miecza, wiec bez tego crit/zmiana broni tuz po
     * zwyklym hicie zadawal drugi PELNY hit z configu, a slabsze/mocniejsze hity
     * rejestrowaly sie zupelnie niezaleznie od setow i global-hits.
     */
    private static final class LastHit {
        final double damage;
        final long timeMs;

        LastHit(double damage, long timeMs) {
            this.damage = damage;
            this.timeMs = timeMs;
        }
    }

    /**
     * Okno i-frames w ms. Vanilla: pierwsze 10 ticków (500 ms) po hicie. Celowo 450 ms
     * (z zapasem), zeby przy laggu NIGDY nie zjadac prawidlowego hita.
     */
    private static final long IFRAME_WINDOW_MS = 450L;

    private final Map<UUID, LastHit> lastHits = new HashMap<>();

    // Rozwiazane RAZ przy starcie klasy (nie na kazdy hit - patrz zeroOutVanillaEnchantModifiers/
    // recalculateAbsorptionModifier nizej) nazwy stalych DamageModifier, ktore roznia sie
    // miedzy wersjami Spigot/Paper API. null oznacza "ta stala nie istnieje w tej wersji API"
    // - odpowiedniki po prostu sa wtedy cicho pomijane, dokladnie jak wczesniej przy
    // Enum.valueOf(String) per-hit, tylko bez powtarzania tego lookupu przy kazdym trafieniu.
    private static final EntityDamageEvent.DamageModifier MOD_MAGIC = resolveModifier("MAGIC");
    private static final EntityDamageEvent.DamageModifier MOD_ARMOR_ENCHANTMENTS = resolveModifier("ARMOR_ENCHANTMENTS");
    private static final EntityDamageEvent.DamageModifier MOD_ARMOR = resolveModifier("ARMOR");
    private static final EntityDamageEvent.DamageModifier MOD_INVULNERABILITY_REDUCTION = resolveModifier("INVULNERABILITY_REDUCTION");
    private static final EntityDamageEvent.DamageModifier MOD_ABSORPTION = resolveModifier("ABSORPTION");
    private static final EntityDamageEvent.DamageModifier MOD_RESISTANCE = resolveModifier("RESISTANCE");
    private static final EntityDamageEvent.DamageModifier MOD_BLOCKING = resolveModifier("BLOCKING");
    private static final EntityDamageEvent.DamageModifier MOD_HARD_HAT = resolveModifier("HARD_HAT");
    private static final EntityDamageEvent.DamageModifier MOD_FREEZING = resolveModifier("FREEZING");

    private static EntityDamageEvent.DamageModifier resolveModifier(String name) {
        try {
            return Enum.valueOf(EntityDamageEvent.DamageModifier.class, name);
        } catch (Exception | NoSuchFieldError e) {
            return null;
        }
    }

    /** Czas (ms) ostatniego hita zadanego PRZEZ danego atakującego (na kogokolwiek) - do anti-spam. */
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();

    /**
     * Historia atakujących danego obrońcy w ostatnim oknie czasowym (gang-up-penalty):
     * obrońca (uuid) -> mapa (atakujący uuid -> czas ostatniego trafienia przez niego).
     * Używana do policzenia ilu RÓŻNYCH graczy biło ofiarę w ostatnich window-ms.
     */
    private final Map<UUID, Map<UUID, Long>> recentAttackersByDefender = new HashMap<>();

    /**
     * Combat-lock: ofiara (uuid) -> (aktualny "wlasciciel" ataku, czas jego ostatniego trafienia).
     * Dopoki wlasciciel trafia ofiare czesciej niz co combat-lock.timeout-ms, tylko JEGO hity
     * przechodza - kazdy inny gracz probujacy trafic te sama ofiare w tym oknie dostaje
     * calkowicie anulowany hit (patrz onEntityDamageByEntity, sekcja combat-lock).
     */
    private final Map<UUID, LockOwner> combatLockOwner = new HashMap<>();

    private static final class LockOwner {
        final UUID attackerId;
        long timeMs;

        LockOwner(UUID attackerId, long timeMs) {
            this.attackerId = attackerId;
            this.timeMs = timeMs;
        }
    }

    /** Event, ktory wlasnie obsluzylismy na LOWEST - do domkniecia na HIGHEST (patrz onFinalize). */
    private EntityDamageByEntityEvent pendingFinalize;
    private double pendingIntendedDamage;
    private boolean antySpamKaraTegoHita;

    private final NamespacedKey armorZeroKey;
    private final NamespacedKey toughnessZeroKey;

    public HitSystemListener(HitSystemManager manager) {
        this.manager = manager;
        this.armorZeroKey = new NamespacedKey(manager.getOwningPlugin(), "hitsystem_zero_armor");
        this.toughnessZeroKey = new NamespacedKey(manager.getOwningPlugin(), "hitsystem_zero_toughness");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Sprawdzone RAZ na wejsciu i przekazywane do skip() - bez tego kazde wywolanie
        // skip(...) budowalo (konkatenacja stringow, event.getCause(), damager.getType() itd.)
        // swoj argument NAWET gdy /hitsystem debug jest wylaczony (co jest domyslne i normalne
        // na produkcji) i skip() i tak od razu go odrzucal. Przy tym, ze ten handler odpala sie
        // na KAZDE obrazenia miedzy encjami na calym serwerze (strzaly, upadki, lawa, mobowie...),
        // nie tylko PvP melee - to bylo mnostwo zbednych alokacji na sekunde przy ruchu serwera.
        boolean debug = manager.getConfig().isDebugEnabled();

        if (!manager.getConfig().isEnabled()) {
            if (debug) skip("damage-system.enabled = false");
            return;
        }

        // Wyłącznie DamageCause.ENTITY_ATTACK - to jedyny cause dla bezpośredniego
        // melee. Projectile (strzały/trójzęby), ogień, lawa, upadek itd. mają inne
        // cause i NIGDY nie trafiają w tę gałąź - system dotyczy tylko PvP melee,
        // zgodnie z założeniem "damage-system ma dotyczyć przede wszystkim PvP melee".
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            if (debug) skip("cause=" + event.getCause() + " (system dziala tylko na ENTITY_ATTACK); damager=" + event.getDamager().getType());
            return;
        }

        if (!(event.getEntity() instanceof Player defender)) {
            if (debug) skip("ofiara nie jest graczem: " + event.getEntity().getType());
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            if (debug) skip("atakujacy nie jest graczem: " + event.getDamager().getType() + " -> " + defender.getName());
            return;
        }

        // Zabezpieczenie przed samobiciem / dziwnymi edge-case'ami silnika.
        if (attacker.getUniqueId().equals(defender.getUniqueId())) {
            if (debug) skip("samobicie");
            return;
        }

        // System obrazen setowych: Netherite Sword albo dowolny przedmiot z Sharpness
        // (np. blaze rod). Goła ręka / item bez Sharpness zostaje vanillą.
        org.bukkit.inventory.ItemStack heldItem = attacker.getInventory().getItemInMainHand();
        if (!manager.getSetIdentifier().isHitSystemWeapon(heldItem)) {
            if (debug) skip("atakujacy nie trzyma broni HitSystem (" + (heldItem != null ? heldItem.getType() : "AIR") + ") -> system setow/perkow pominiety, zostaje vanilla dmg");
            return;
        }

        // WorldGuard: system setow nie dziala w regionach z pvp = deny.
        // manager.isWorldGuardEnabled() to cache policzony RAZ przy load()/reload
        // (patrz HitSystemManager) - bez lookupu pluginu po nazwie na kazdy hit.
        try {
            if (manager.isWorldGuardEnabled() && boniec.code.core.WorldGuardHook.isPvpDenied(defender.getLocation())) {
                if (debug) skip("WorldGuard: pvp=deny w regionie ofiary (" + defender.getName() + ") - system setow wylaczony w tym miejscu");
                return;
            }
        } catch (Throwable ignored) {}

        // --- Combat-lock: jesli ofiare "trzyma" inny atakujacy (trafil ja niedawniej
        // niz timeout-ms temu), ten hit jest CALKOWICIE anulowany - zapobiega to
        // stackowaniu obrazen, gdy kilku graczy bije jedna osobe naraz. Sprawdzane
        // PRZED wszystkim innym (anti-spam, obliczenia dmg), zeby zablokowany hit
        // w ogole nic nie liczyl i nie zostawil sladu (np. w anti-spam/i-frames).
        if (manager.getConfig().isCombatLockEnabled()) {
            long now = System.currentTimeMillis();
            long timeout = manager.getConfig().getCombatLockTimeoutMs();
            UUID defenderId = defender.getUniqueId();
            UUID attackerId = attacker.getUniqueId();

            LockOwner owner = combatLockOwner.get(defenderId);
            if (owner != null && !owner.attackerId.equals(attackerId) && (now - owner.timeMs) < timeout) {
                // Inny gracz wciaz "trzyma" ofiare - ten hit nie przechodzi w ogole.
                if (debug) skip("combat-lock: " + owner.attackerId + " trzyma " + defender.getName()
                        + " (ostatni hit " + (now - owner.timeMs) + "ms temu < " + timeout + "ms) -> hit " + attacker.getName() + " zignorowany");
                event.setDamage(0.0);
                event.setCancelled(true);
                return;
            }
            // Brak locka, lock wygasl, albo to ten sam atakujacy - przejmujemy/odswiezamy lock.
            combatLockOwner.put(defenderId, new LockOwner(attackerId, now));
            if (combatLockOwner.size() > 256) {
                combatLockOwner.values().removeIf(l -> now - l.timeMs > 5000L);
            }
        }

        boolean critical = isVanillaCritical(attacker);
        // Jesli admin wylaczyl wymog warunkow vanilla, critical i tak liczymy
        // identycznie na podstawie tego, co ustalilismy powyzej - druga galaz
        // configu nie zmienia sposobu liczenia, wiec NIE wolamy funkcji ponownie
        // (wczesniej bylo to zbedne podwojne wywolanie isVanillaCritical()
        // przy KAZDYM hicie melee gracz-na-gracza na serwerze).

        // --- Wymog naladowania miecza (attack strength): jesli atakujacy klika
        // szybciej, niz pozwala na to cooldown miecza, kazdy kolejny klik ma
        // NIZSZA realna sile ataku - to ta sama wartosc, co pasek naladowania
        // nad hotbarem (0.0 = 0%, 1.0 = 100%). Sprawdzane PRZED anti-spam i
        // przed policzeniem obrazen: bez tego spamowanie LPM dalej zadawalo
        // pelne obrazenia z systemu setow (anti-spam pilnuje tylko ODSTEPU
        // CZASOWEGO miedzy hitami, NIE realnej sily ataku), przez co mozna
        // bylo bic np. co 500ms zamiast czekac na pelne naladowanie miecza.
        if (manager.getConfig().isAttackStrengthRequirementEnabled()) {
            double attackStrength = attacker.getAttackCooldown(); // 0.0 - 1.0
            double minRequired = manager.getConfig().getAttackStrengthMinPercent() / 100.0;
            if (attackStrength < minRequired) {
                if (debug) skip("attack-strength: " + attacker.getName() + " naladowanie ataku "
                        + String.format("%.0f", attackStrength * 100.0) + "% < wymagane "
                        + String.format("%.0f", minRequired * 100.0) + "% -> hit zignorowany");
                event.setCancelled(true);
                return;
            }
        }

        // --- Anti-spam: blokuje spamowanie LPM mieczem szybciej niz normalny
        // czas ataku. Sprawdzane PRZED policzeniem obrazen, zeby przy trybie
        // "cancel" w ogole nie liczyc/wysylac niczego dla spamowanego hita -
        // dokladnie jakby atak w ogole sie nie odbyl.
        if (manager.getConfig().isAntiSpamEnabled()) {
            long now = System.currentTimeMillis();
            UUID attackerId = attacker.getUniqueId();
            Long ostatni = lastAttackTime.get(attackerId);
            long minInterval = manager.getConfig().getAntiSpamMinIntervalMs();
            boolean spam = ostatni != null && (now - ostatni) < minInterval;

            if (spam && "cancel".equalsIgnoreCase(manager.getConfig().getAntiSpamBlockMode())) {
                // Hit calkowicie zignorowany - NIE aktualizujemy lastAttackTime, zeby kolejny,
                // prawidlowo odczekany hit byl liczony od ostatniego PRAWDZIWEGO trafienia,
                // a nie od tego spamowanego (inaczej spam mogloby "przyspieszac" limit).
                if (debug) skip("anti-spam: " + attacker.getName() + " atakowal " + (now - ostatni) + "ms temu (< " + minInterval + "ms) -> hit zignorowany");
                event.setCancelled(true);
                return;
            }
            // W trybie "penalize" (albo gdy to nie byl spam) zapisujemy aktualny czas normalnie.
            lastAttackTime.put(attackerId, now);
            if (lastAttackTime.size() > 256) {
                lastAttackTime.values().removeIf(t -> now - t > 5000L);
            }
            // Flaga do ewentualnej kary procentowej nizej (tryb "penalize").
            antySpamKaraTegoHita = spam && "penalize".equalsIgnoreCase(manager.getConfig().getAntiSpamBlockMode());
        } else {
            antySpamKaraTegoHita = false;
        }

        DamageCalculator.Result result = manager.computeDamage(attacker, defender, critical);
        double finalDamage = result.finalDamagePoints;

        if (antySpamKaraTegoHita) {
            double redukcja = manager.getConfig().getAntiSpamPenaltyPercent() / 100.0;
            finalDamage = finalDamage * (1 - redukcja);
        }

        // --- Elytra jako klata: obrońca noszący elytrę w slocie chestplate
        // (zamiast zwykłej klaty) dostaje WIĘCEJ obrażeń - elytra nie chroni
        // jak pancerz z seta. Działa z każdą zbroją, niezależnie od tagów
        // seta - liczy się tylko fizyczny Material.ELYTRA w slocie klaty.
        if (manager.getConfig().isElytraAsChestplateEnabled() && isWearingElytraAsChestplate(defender)) {
            double extraHearts = manager.getConfig().getElytraExtraHearts();
            if (extraHearts != 0.0) {
                finalDamage = finalDamage + extraHearts * 2.0;
            } else {
                double elytraPercent = manager.getConfig().getElytraDamagePercent();
                finalDamage = finalDamage * Math.max(0.0, 1.0 + elytraPercent / 100.0);
            }
        }

        // --- Kara za brakujące części zbroi: za KAŻDY puste slot (helmet/
        // chestplate/leggings/boots) dolicz dodatkowe obrażenia. Bonusy się
        // SUMUJĄ (2 brakujące części = 2x per-missing-piece). Elytra w
        // slocie chestplate NIE liczy się jako "brakująca część" - dostaje
        // wtedy tylko bonus z elytra-as-chestplate powyżej (oba bonusy się
        // mnożą, jeśli gracz ma elytrę + inny puste sloty).
        if (manager.getConfig().isMissingArmorPenaltyEnabled()) {
            int missingPieces = countMissingArmorPieces(defender);
            if (missingPieces > 0) {
                double perPiecePercent = manager.getConfig().getMissingArmorExtraDamagePercent();
                finalDamage = finalDamage * Math.max(0.0, 1.0 + (missingPieces * perPiecePercent / 100.0));
            }
        }

        // --- Gang-up penalty: jesli obronce w krotkim oknie czasowym bije
        // wiecej niz 1 rozny atakujacy (czyli 2+ osob na raz), kazdy z tych
        // hitow jest zredukowany o okreslony procent - zapobiega to
        // nienaturalnie szybkiemu zabijaniu w walkach grupowych.
        // Aktualizujemy historie PRZED sprawdzeniem, zeby ten atak sam
        // rowniez liczyl sie jako jeden z "roznych atakujacych" w oknie.
        if (manager.getConfig().isGangUpPenaltyEnabled()) {
            long now = System.currentTimeMillis();
            long window = manager.getConfig().getGangUpWindowMs();
            UUID defenderId = defender.getUniqueId();

            Map<UUID, Long> atakujacy = recentAttackersByDefender.computeIfAbsent(defenderId, k -> new HashMap<>());
            atakujacy.put(attacker.getUniqueId(), now);
            // Usuwamy wpisy starsze niz okno - licza sie tylko atakujacy, ktorzy trafili "niedawno".
            atakujacy.values().removeIf(t -> now - t > window);

            int roznychAtakujacych = atakujacy.size();
            if (roznychAtakujacych > 1) {
                double redukcja = manager.getConfig().getGangUpPenaltyPercent() / 100.0;
                finalDamage = finalDamage * (1 - redukcja);
            }

            if (recentAttackersByDefender.size() > 256) {
                recentAttackersByDefender.values().removeIf(Map::isEmpty);
            }
        }

        // --- Artefakt Siły (Szaman): +% do zadawanych obrażeń (poziom kupiony w /szaman
        // + ewentualny bonus kostiumu/aury). Wartość jest już w realnych procentach obrażeń.
        // Tu liczymy PvP; walkę z mobami liczy CombatListener (HitSystem obsługuje tylko
        // Player vs Player), więc bonus nigdy nie jest naliczany podwójnie.
        double perkBonusPercent = getSilaObrazeniaBonus(attacker);
        if (perkBonusPercent > 0) {
            finalDamage = finalDamage * (1 + (perkBonusPercent / 100.0));
        }

        // Siła / Zła wróżba atakującego, Luck / Odporność broniącego.
        finalDamage = finalDamage * PotionDamageEffects.multiplier(attacker, defender);

        // --- Rejestracja hitow (i-frames) liczona NASZYMI obrazeniami.
        // Vanilla porownuje hity po vanillowym damage miecza (atrybuty/Sharpness/crit),
        // a my i tak nadpisujemy wartosc - przez to hit w oknie nietykalnosci potrafil
        // zadac drugi PELNY hit (np. crit zaraz po zwyklym). Tu odtwarzamy dokladnie
        // vanillowa regule (tylko roznica, a slabszy/rowny hit jest ignorowany),
        // ale na wartosciach z systemu setow / global-hits.
        double appliedDamage = finalDamage;
        if (manager.getConfig().isCustomIframesEnabled()) {
            long now = System.currentTimeMillis();
            UUID defenderId = defender.getUniqueId();
            LastHit previous = lastHits.get(defenderId);
            boolean inIframes = previous != null
                    && defender.getNoDamageTicks() > defender.getMaximumNoDamageTicks() / 2.0
                    && (now - previous.timeMs) < IFRAME_WINDOW_MS;
            if (inIframes) {
                if (finalDamage <= previous.damage) {
                    // Jak w vanilla: hit slabszy/rowny poprzedniemu w oknie nietykalnosci nic nie robi.
                    if (debug) skip("i-frames: hit " + String.format("%.2f", finalDamage) + " <= poprzedni " + String.format("%.2f", previous.damage) + " -> " + defender.getName());
                    event.setCancelled(true);
                    return;
                }
                appliedDamage = finalDamage - previous.damage;
                // okno liczymy nadal od PIERWSZEGO hita (jak vanilla), ale zapamietujemy wyzsza wartosc
                lastHits.put(defenderId, new LastHit(finalDamage, previous.timeMs));
            } else {
                lastHits.put(defenderId, new LastHit(finalDamage, now));
            }
            if (lastHits.size() > 256) {
                lastHits.values().removeIf(h -> now - h.timeMs > 2000L);
            }
        }

        // Ustawiamy bazowy damage TUTAJ (LOWEST) - CombatListener (NORMAL) i
        // ewentualne inne pluginy na wyższych priorytetach nadal mogą dołożyć
        // swoje efekty (extrahit, glowing, limit maxHit itd.) na tej bazie,
        // zamiast być przez nas nadpisywane.
        event.setDamage(appliedDamage);
        suppressVanillaArmor(defender);
        zeroOutVanillaEnchantModifiers(event);
        pendingFinalize = event;
        pendingIntendedDamage = appliedDamage;
        playShieldBlockSound(defender);

        // Absorption (żółte serca) jest liczona przez Bukkit/Paper jako osobny
        // DamageModifier.ABSORPTION, wyliczony WCZEŚNIEJ - w momencie stworzenia
        // eventu, na bazie orygin. obrażeń vanilla (ustalonych jeszcze zanim ten
        // listener w ogóle się wykonał). Skoro powyżej podmieniamy BASE na
        // finalDamage z systemu setów, stary modifier ABSORPTION przestaje mieć
        // sens - dalej "myśli", że obrażenia bazowe są takie jak w oryginalnym
        // (vanilla) evencie, więc pochłania za mało (albo za dużo) żółtych serc,
        // niż powinien względem naszej NOWEJ wartości. Przeliczamy go tutaj
        // ręcznie na podstawie realnego stanu absorpcji obrońcy, żeby np.
        // 2.5 serca finalDamage faktycznie zdjęło 2.5 serca absorpcji (5.0 HP),
        // a nie tylko tyle, ile "zmieściłoby się" w oryginalnym mniejszym hicie.
        recalculateAbsorptionModifier(event, defender, appliedDamage);

        // --- Actionbar dla atakującego: HP ofiary PO tym hicie (+ ewentualna
        // pozostała absorpcja). Liczone RĘCZNIE tutaj (nie na podstawie
        // defender.getHealth() już zmienionego), bo w tym momencie event
        // jeszcze nie odjął obrażeń - defender.getHealth()/getAbsorptionAmount()
        // wciąż mają wartości SPRZED tego hitu. Wysyłane synchronicznie, w tym
        // samym ticku co event - przy szybkich/wielu hitach każdy kolejny hit
        // po prostu NADPISUJE actionbar poprzedniego (Minecraft action bar nie
        // "kolejkuje" wiadomości), więc zawsze widać wynik NAJNOWSZEGO uderzenia.
        sendHitActionBar(attacker, defender, appliedDamage);

        if (manager.getConfig().isDebugEnabled()) {
            var eq = attacker.getEquipment();
            var ident = manager.getSetIdentifier();
            int helmetSet = eq != null ? ident.getHelmetSetId(eq.getHelmet()) : -1;
            int chestSet = eq != null ? ident.getChestplateSetId(eq.getChestplate()) : -1;
            int legsSet = eq != null ? ident.getLeggingsSetId(eq.getLeggings()) : -1;
            int bootsSet = eq != null ? ident.getBootsSetId(eq.getBoots()) : -1;
            int missingPieces = countMissingArmorPieces(defender);
            manager.debug(String.format(
                    "Attacker: %s | Helmet:%d Chest:%d Legs:%d Boots:%d -> FullArmorSet:%d | Sword Set: %d | Defender: %s | Defender Set: %.2f | Defender Elytra-as-Chest: %b | Defender Missing Pieces: %d | Critical: %b | Base Damage (przed global-hits): %.2f | Global Hits Multiplier: x%.2f | Damage z systemu setow (po global-hits): %.2f | Artefakt Sily (+%% dmg): %.1f%% | Anti-spam kara: %b | Final Base (przed dalszymi efektami Szamana): %.2f (%.2f serca) | Applied (po i-frames): %.2f",
                    attacker.getName(),
                    helmetSet, chestSet, legsSet, bootsSet,
                    ident.getFullArmorSetId(attacker),
                    ident.getHeldSwordSetId(attacker),
                    defender.getName(),
                    result.effectiveDefenderSetExact,
                    isWearingElytraAsChestplate(defender),
                    missingPieces,
                    critical,
                    result.heartsBeforeGlobalMultiplier * 2.0,
                    manager.getConfig().getGlobalHitsMultiplier(),
                    result.finalDamagePoints,
                    perkBonusPercent,
                    antySpamKaraTegoHita,
                    finalDamage,
                    finalDamage / 2.0,
                    appliedDamage
            ));
        }
    }

    /**
     * Domkniecie eventu na HIGHEST - PO CombatListener (NORMAL) i innych listenerach.
     * Kazde wywolanie event.setDamage(double) w Bukkicie PRZELICZA modifiery
     * (ARMOR/MAGIC/...) wzgledem zmiany BASE, wiec gdy ktos po nas zmienil damage
     * (glowing x1.5, extrahit +6 itd.), czesc zerowanych przez nas modifierow
     * (pancerz/Protection ofiary) moglaby wrocic i znowu zmniejszac hit -
     * wtedy realny damage zalezal od zbroi zamiast od setow i global-hits.
     * Tu jeszcze raz zerujemy je i przeliczamy absorpcje na finalna baze.
     * Dziala WYLACZNIE na evencie, ktory obsluzyl onEntityDamageByEntity.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFinalize(EntityDamageByEntityEvent event) {
        if (pendingFinalize != event) {
            return;
        }
        pendingFinalize = null;
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }
        forceExactDamage(event, defender, pendingIntendedDamage);
    }

    /** Log powodu pominiecia hita - tylko gdy /hitsystem debug on. */
    private void skip(String reason) {
        manager.debug("POMINIETY hit (vanilla damage): " + reason);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastHits.remove(id);
        lastAttackTime.remove(id);
        recentAttackersByDefender.remove(id);
        // Usuwamy tez wpisy o tym graczu jako ATAKUJACYM w historii innych obroncow.
        for (Map<UUID, Long> atakujacy : recentAttackersByDefender.values()) {
            atakujacy.remove(id);
        }
        combatLockOwner.remove(id);
        // Jesli wychodzacy gracz byl "wlascicielem" locka nad kims innym, zwalniamy go,
        // zeby ofiara nie zostala zablokowana na zawsze przez juz nieobecnego gracza.
        combatLockOwner.values().removeIf(owner -> owner.attackerId.equals(id));
        restoreVanillaArmor(event.getPlayer());
    }

    /**
     * Łączny bonus do zadawanych obrażeń w procentach: poziom Artefaktu Siły kupiony
     * w /szaman + bonus z kostiumu/aury (już przeliczony, patrz
     * BonusCalculator#getObrazeniaProcent). Zwraca 0 jeśli system Szamana nie jest
     * jeszcze gotowy (np. wczesna kolejność ładowania) - hitsystem wtedy po prostu
     * nie dolicza bonusu, bez wyjątku/NPE.
     */
    private double getSilaObrazeniaBonus(Player attacker) {
        SzamanPlugin szaman = SzamanPlugin.get();
        if (szaman == null) {
            return 0.0;
        }
        BonusCalculator calc = szaman.getBonusCalculator();
        if (calc == null) {
            return 0.0;
        }
        return calc.getObrazeniaProcent(attacker.getUniqueId());
    }

    /**
     * Zeruje wewnętrzne modyfikatory obrażeń Bukkita/Paper odpowiedzialne za
     * Sharpness (MAGIC) i Protection (ARMOR_ENCHANTMENTS), żeby żadne z tych
     * zaklęć nie mogło doliczyć/odjąć niczego do bazy ustawionej przez
     * DamageCalculator. Nazwy stałych DamageModifier są ustalane RAZ, przy
     * starcie klasy (patrz pola MOD_* niżej) - poprzednio Enum.valueOf(String)
     * był wywoływany dla kilku nazw PRZY KAŻDYM pojedynczym hicie PvP (a ta
     * metoda odpala się dwa razy na hit - tu i w onFinalize), czyli kilkanaście
     * niepotrzebnych lookupów enuma po nazwie na sekundę przy aktywnym PvP.
     * Sama treść/efekt są identyczne - zmienia się tylko KIEDY nazwa jest
     * rozwiązywana (raz przy starcie, nie za każdym razem).
     */
    private void forceExactDamage(EntityDamageByEntityEvent event, Player defender, double intended) {
        event.setDamage(intended);
        zeroOutVanillaEnchantModifiers(event);
        recalculateAbsorptionModifier(event, defender, intended);
    }

    /** HitSystem zeruje modifier BLOCKING, wiec vanilla nie odtwarza dzwieku tarczy - gramy go sami. */
    private static void playShieldBlockSound(Player defender) {
        if (!defender.isBlocking()) return;
        defender.getWorld().playSound(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.2f, 1.0f);
    }

    private void suppressVanillaArmor(Player defender) {
        zeroAttribute(defender, Attribute.GENERIC_ARMOR, armorZeroKey);
        zeroAttribute(defender, Attribute.GENERIC_ARMOR_TOUGHNESS, toughnessZeroKey);
        UUID id = defender.getUniqueId();
        Bukkit.getScheduler().runTask(manager.getOwningPlugin(), () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restoreVanillaArmor(p);
            }
        });
    }

    private void zeroAttribute(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        removeModifier(instance, key);
        instance.addModifier(new AttributeModifier(key, -1.0, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
    }

    private void restoreVanillaArmor(Player player) {
        AttributeInstance armor = player.getAttribute(Attribute.GENERIC_ARMOR);
        AttributeInstance toughness = player.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS);
        if (armor != null) {
            removeModifier(armor, armorZeroKey);
        }
        if (toughness != null) {
            removeModifier(toughness, toughnessZeroKey);
        }
    }

    private static void removeModifier(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier modifier : new java.util.ArrayList<>(instance.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
    }

    private void zeroOutVanillaEnchantModifiers(EntityDamageByEntityEvent event) {
        trySetModifier(event, MOD_MAGIC);
        trySetModifier(event, MOD_ARMOR_ENCHANTMENTS);
        trySetModifier(event, MOD_ARMOR);
        trySetModifier(event, MOD_RESISTANCE);
        trySetModifier(event, MOD_BLOCKING);
        trySetModifier(event, MOD_HARD_HAT);
        trySetModifier(event, MOD_FREEZING);
        if (manager.getConfig().isCustomIframesEnabled()) {
            trySetModifier(event, MOD_INVULNERABILITY_REDUCTION);
        }
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier == EntityDamageEvent.DamageModifier.BASE || modifier == MOD_ABSORPTION) {
                continue;
            }
            trySetModifier(event, modifier);
        }
    }

    /**
     * Ustawia dany (juz rozwiazany raz przy starcie - patrz pola MOD_*) modifier
     * obrażeń na 0.0, jeśli w ogóle istnieje w tej wersji API (modifier != null)
     * i jest aplikowalny do tego eventu.
     */
    private void trySetModifier(EntityDamageByEntityEvent event, EntityDamageEvent.DamageModifier modifier) {
        if (modifier == null) {
            // Ta stała nie istnieje w używanej wersji API (ustalone raz przy starcie) -
            // nic nie robimy, baza ustawiona przez event.setDamage(finalDamage) i tak zostaje
            // (a np. Protection mogło już wcześniej wjechać w "ARMOR", które
            // celowo zostawiamy - patrz komentarz w onEntityDamageByEntity).
            return;
        }
        try {
            if (event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0);
            }
        } catch (Exception ignored) {
            // Zabezpieczenie na wszelki inny nieoczekiwany wyjątek API.
        }
    }

    /**
     * Przelicza modifier DamageModifier.ABSORPTION tak, żeby odpowiadał
     * RZECZYWISTEJ wartości obrażeń ustalonej przez system setów
     * (finalDamage), a nie oryginalnej wartości vanilla, na podstawie której
     * Bukkit/Paper wyliczył go domyślnie PRZED wejściem w ten listener.
     * Modifier ABSORPTION musi być <= 0 (Bukkit odejmuje go od obrażeń), więc
     * ustawiamy -min(finalDamage, absorptionHP) - dokładnie tyle "żółtych"
     * punktów HP, ile obrońca faktycznie ma i ile hit jest w stanie zdjąć.
     * Reszta (jeśli finalDamage > absorptionHP) trafia normalnie w serca
     * życia, tak jak w vanilla Minecrafcie.
     */
    private void recalculateAbsorptionModifier(EntityDamageByEntityEvent event, Player defender, double finalDamage) {
        EntityDamageEvent.DamageModifier absorptionModifier = MOD_ABSORPTION;
        if (absorptionModifier == null) {
            // Ta wersja API nie ma osobnego DamageModifier.ABSORPTION (starszy
            // Spigot liczy absorpcję inaczej, poza modifierami) - nic nie robimy,
            // vanilla mechanika sama sobie z tym poradzi na podstawie BASE.
            return;
        }
        try {
            if (!event.isApplicable(absorptionModifier)) {
                return;
            }
            double absorptionHp = defender.getAbsorptionAmount();
            if (absorptionHp <= 0.0 || finalDamage <= 0.0) {
                event.setDamage(absorptionModifier, 0.0);
                return;
            }
            double absorbed = Math.min(finalDamage, absorptionHp);
            event.setDamage(absorptionModifier, -absorbed);
        } catch (Exception ignored) {
            // Zabezpieczenie na wszelki inny nieoczekiwany wyjątek API.
        }
    }

    /**
     * Wysyła atakującemu actionbar z HP ofiary PO tym hicie (surowe punkty
     * HP Minecrafta, nie serca) w formacie:
     *   &f%gracz% &c%hp%[ &e+%absorpcja%&6❤]
     * np. "&fboniec &c14.33 &e+0.46&6❤" (fragment absorpcji tylko gdy > 0
     * po tym hicie). Liczone ręcznie na podstawie stanu obrońcy PRZED
     * odjęciem obrażeń przez silnik (ten listener działa PRZED faktycznym
     * odjęciem HP), żeby pokazać dokładnie to, co gracz zobaczy o ułamek
     * sekundy później - bez osobnego taska/delay, więc kolejne hity po
     * prostu nadpisują poprzedni actionbar w tym samym ticku.
     */
    private void sendHitActionBar(Player attacker, Player defender, double finalDamage) {
        double healthBefore = defender.getHealth();
        double absorptionBefore = defender.getAbsorptionAmount();

        // Absorpcja (żółte serca) jest zdejmowana NAJPIERW, reszta dmg trafia w HP -
        // dokładnie tak samo jak w recalculateAbsorptionModifier powyżej.
        double absorbed = Math.min(Math.max(finalDamage, 0.0), Math.max(absorptionBefore, 0.0));
        double remainingAbsorption = Math.max(0.0, absorptionBefore - absorbed);
        double dmgToHealth = Math.max(0.0, finalDamage - absorbed);
        double healthAfter = Math.max(0.0, healthBefore - dmgToHealth);

        StringBuilder bar = new StringBuilder();
        bar.append("&f").append(defender.getName())
                .append(" &c").append(formatHp(healthAfter));
        if (remainingAbsorption > 0.0) {
            bar.append(" &e+").append(formatHp(remainingAbsorption)).append("&6❤");
        }

        actionBar(attacker, bar.toString());
    }

    /**
     * Formatuje wartość HP z dokładnością do 2 miejsc po przecinku (np. 14.33).
     * Własna implementacja zamiast String.format(Locale.US, "%.2f", value) -
     * format() alokuje Formatter + StringBuilder i parsuje pattern string przy
     * kazdym wywolaniu; przy intensywnym PvP (wiele hitow/sekunde) ta metoda
     * jest wolana kilkadziesiat razy na sekunde, wiec eliminacja alokacji ma znaczenie.
     */
    private String formatHp(double value) {
        if (value < 0.0) value = 0.0;
        long rounded = Math.round(value * 100);
        long intPart = rounded / 100;
        long fracPart = rounded % 100;
        if (fracPart < 0) fracPart = -fracPart;
        return intPart + "." + (fracPart < 10 ? "0" : "") + fracPart;
    }

    /**
     * Klasyczna, zawsze dostępna w Spigot API metoda na action bar - niezależnie
     * od tego, czy konkretny build API ma (czy nie ma) wygodnego
     * Player#sendActionBar(String). Ten sam wzorzec co w CombatListener.
     */
    private void actionBar(Player gracz, String tekst) {
        gracz.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(kolor(tekst)));
    }

    private String kolor(String tekst) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', tekst);
    }

    /**
     * Sprawdza, czy gracz ma fizycznie założoną elytrę w slocie chestplate
     * (Bukkit trzyma elytrę w tym samym slocie co klatę - jeśli gracz ją
     * nosi, to po prostu ZAMIAST klaty). Sprawdzamy wyłącznie Material, bez
     * tagów seta - elytra ma działać z każdą zbroją.
     */
    private boolean isWearingElytraAsChestplate(Player player) {
        var eq = player.getEquipment();
        if (eq == null) {
            return false;
        }
        org.bukkit.inventory.ItemStack chest = eq.getChestplate();
        return chest != null && chest.getType() == org.bukkit.Material.ELYTRA;
    }

    /**
     * Liczy ile z 4 slotów zbroi (helmet/chestplate/leggings/boots) jest
     * PUSTYCH (brak itemu). Slot chestplate z założoną elytrą NIE jest
     * traktowany jako pusty/brakujący - elytra "coś" tam nosi, tylko dostaje
     * osobny bonus z elytra-as-chestplate (patrz isWearingElytraAsChestplate).
     */
    private int countMissingArmorPieces(Player player) {
        var eq = player.getEquipment();
        if (eq == null) {
            return 4;
        }
        int missing = 0;
        if (isEmptySlot(eq.getHelmet())) missing++;
        if (isEmptySlot(eq.getChestplate()) && !isWearingElytraAsChestplate(player)) missing++;
        if (isEmptySlot(eq.getLeggings())) missing++;
        if (isEmptySlot(eq.getBoots())) missing++;
        return missing;
    }

    private boolean isEmptySlot(org.bukkit.inventory.ItemStack item) {
        return item == null || item.getType().isAir();
    }

    /**
     * Sprawdza, czy atak spełnia standardowe (vanilla) warunki critical hita:
     * gracz spada (fallDistance > 0), nie jest na drabinie/w wodzie/w pajęczynie,
     * nie ma efektu Blindness ani nie jest w trakcie sprintu-riposty itd.
     * Korzystamy z tej samej heurystyki co vanilla Minecraft, żeby zachowanie
     * "kiedy hit jest critical" pozostało znajome graczom - różni się tylko
     * WARTOŚĆ obrażeń critical hita (ta jest z configu, nie x1.5).
     */
    private boolean isVanillaCritical(Player attacker) {
        return attacker.getFallDistance() > 0.0f
                && !attacker.isOnGround()
                && !attacker.isInsideVehicle()
                && !attacker.isClimbing()
                && !attacker.isInWater()
                && !attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
                && attacker.getVelocity().getY() < 0;
    }
}
