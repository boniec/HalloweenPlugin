package boniec.code.anticheat;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Anticheat dot. pajęczyn (cobweb, Material.COBWEB):
 *
 * 1) Blokuje bicie OFIARY, która stoi w pajęczynie, w tempie szybszym niż
 *    fizycznie możliwe. To jest główny przypadek killaury na cobwebach:
 *    ktoś wpycha/wabi gracza w pajęczynę (gdzie ten jest spowolniony i nie
 *    może się bronić/uciekać) i bije go killaurą non-stop. Cobweb spowalnia
 *    tylko RUCH ofiary, więc sam fakt "stania ofiary w pajęczynie" nie
 *    ogranicza w żaden sposób ataków killaury - dlatego pilnujemy minimalnego
 *    odstępu między hitami zadanymi TEJ SAMEJ ofierze stojącej w cobwebie,
 *    niezależnie od tego kto/ile różnych atakujących próbuje ją bić.
 *
 * 2) Blokuje też (pomocniczo) bicie PRZEZ atakującego, który sam stoi w
 *    pajęczynie, w tempie szybszym niż pozwala na to spowolnienie z cobweba -
 *    to twardy sygnał auto-clickera/killaury niezależnie od tego, kogo bije.
 *
 * 3) Blokuje stawianie pajęczyny, jeśli item, którym gracz stawia blok, nie
 *    jest trzymany w main handzie ani w off handzie w momencie eventu (typowy
 *    ślad po pluginach/wtyczkach klienckich stawiających bloki z dowolnego
 *    slotu ekwipunku przez spreparowane pakiety, z pominięciem ręki).
 */
public class CobwebGuard implements Listener {

    // Wlacz/wylacz logi diagnostyczne na konsole (kazde sprawdzenie w
    // cobwebie, z realnymi wartosciami dystansu/czasu) - do debugowania,
    // dlaczego jakis atak przechodzi mimo ze nie powinien. Zostaw true,
    // dopoki problem nie zostanie znaleziony, potem mozna wylaczyc.
    private static final boolean DEBUG = false;
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("CobwebGuard");

    // Maksymalny dystans (oko atakującego -> hitbox ofiary) dozwolony TYLKO
    // gdy ofiara stoi w pajęczynie. To NIE jest ogolny reach-check dla calej
    // walki (ten zostal celowo usuniety) - dziala wylacznie w tym jednym
    // przypadku, zeby killaura nie mogla bic kogos uwiezionego w cobwebie bez
    // fizycznego, wycelowanego kontaktu z jego realnym hitboxem (0.6x1.8x0.6).
    // Wartosc odpowiada realnemu zasiegowi vanilla (~3 bloki) plus niewielki
    // zapas na lag/ping.
    private static final double MAX_COBWEB_VICTIM_REACH = 3.2;

    // Minimalny odstęp (ms) między dwoma hitami wyprowadzonymi PRZEZ atakującego
    // stojącego w pajęczynie. Vanilla spowolnienie z cobweba fizycznie
    // uniemożliwia normalnemu graczowi trzymanie tempa killaury (>10 CPS) -
    // wartość dobrana z zapasem, żeby nie karać zwykłych, wolniejszych hitów.
    private static final long MIN_COBWEB_HIT_INTERVAL_MS = 350L;

    // Minimalny odstęp (ms) między dwoma hitami otrzymanymi PRZEZ ofiarę stojącą
    // w pajęczynie - niezależnie od tego, kto (lub ilu graczy) ją bije. Chroni
    // to gracza uwięzionego w cobwebie (gdzie nie może uciec ani się skutecznie
    // bronić) przed katowaniem go killaurą non-stop. Wartość zbliżona do
    // realnego tempa "poczciwego" bicia mieczem, z zapasem na lag/ping.
    private static final long MIN_COBWEB_VICTIM_HIT_INTERVAL_MS = 300L;

    private final Map<UUID, Long> lastCobwebHit = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCobwebVictimHit = new ConcurrentHashMap<>();

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastCobwebHit.remove(uuid);
        lastCobwebVictimHit.remove(uuid);
    }

    // ── 1) i 2) Blokada bicia zwiazanego z cobwebem w tempie killaury ───────
    // HIGHEST + ignoreCancelled=true: sprawdzamy na samym końcu, żeby nie
    // kolidować z HitSystem (LOWEST) ani z innymi pluginami liczącymi obrażenia -
    // jeśli event już został anulowany wcześniej, nic tu więcej nie robimy.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (DEBUG) {
            LOG.info("[CobwebGuard] onEntityDamageByEntity: attacker=" + attacker.getName()
                    + " victimEntity=" + event.getEntity()
                    + " victimIsPlayer=" + (event.getEntity() instanceof Player)
                    + " cancelledBefore=" + event.isCancelled());
        }

        // ── Ofiara stojąca w pajęczynie: atak musi realnie pochodzić z
        // kontaktu z jej hitboxem (killaura potrafi bić z dystansu, bez
        // faktycznego zbliżenia się do gracza uwięzionego w cobwebie -
        // to blokujemy w pierwszej kolejności, niezależnie od tempa ciosów). ──
        if (event.getEntity() instanceof Player victim && isStandingInCobweb(victim)) {
            boolean inRange = jestWZasieguHitboxa(attacker, victim);
            if (DEBUG) {
                Location eye = attacker.getEyeLocation();
                org.bukkit.util.BoundingBox box = victim.getBoundingBox();
                double distToCenter = box.getCenter().toLocation(victim.getWorld()).distance(eye);
                LOG.info("[CobwebGuard] victim=" + victim.getName() + " IS in cobweb. "
                        + "distToCenter=" + distToCenter + " maxAllowed=" + MAX_COBWEB_VICTIM_REACH
                        + " inRange=" + inRange);
            }
            if (!inRange) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&4&l❌ &cNie jesteś wystarczająco blisko, by bić gracza w pajęczynie."));
                if (DEBUG) {
                    LOG.info("[CobwebGuard] -> CANCELLED (out of range). isCancelledAfter="
                            + event.isCancelled());
                }
                return;
            }

            UUID victimUuid = victim.getUniqueId();
            long now = System.currentTimeMillis();
            Long lastVictimHit = lastCobwebVictimHit.get(victimUuid);

            if (lastVictimHit != null && (now - lastVictimHit) < MIN_COBWEB_VICTIM_HIT_INTERVAL_MS) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&4&l❌ &cNie możesz bić gracza w pajęczynie tak szybko (killaura?)."));
                if (DEBUG) {
                    LOG.info("[CobwebGuard] -> CANCELLED (victim hit interval). deltaMs="
                            + (now - lastVictimHit) + " isCancelledAfter=" + event.isCancelled());
                }
                // Nie nadpisujemy licznika ofiary - kolejny hit tuż po tym
                // nadal liczy się od czasu OSTATNIEGO przepuszczonego ciosu,
                // więc kilku atakujących nie może "przeplatać" hitów, by
                // obejść limit.
                return;
            }

            lastCobwebVictimHit.put(victimUuid, now);
            if (DEBUG) {
                LOG.info("[CobwebGuard] -> ALLOWED (victim-in-cobweb checks passed).");
            }
        }

        if (!isStandingInCobweb(attacker)) {
            return;
        }

        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastCobwebHit.get(uuid);

        if (last != null && (now - last) < MIN_COBWEB_HIT_INTERVAL_MS) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&4&l❌ &cBijesz zbyt szybko stojąc w pajęczynie (killaura?)."));
            // Nie nadpisujemy lastCobwebHit - kolejny hit tuż po tym nadal
            // liczy się od czasu OSTATNIEGO przepuszczonego hitu, więc gracz
            // nie może "rozpędzić się" spamując kliknięcia.
            return;
        }

        lastCobwebHit.put(uuid, now);
    }

    /**
     * Sprawdza, czy atakujący realnie CELUJE i DOTYKA hitboxa ofiary (0.6 szer.
     * x 1.8 wys. x 0.6 dł. - dokladnie tak, jak Minecraft liczy trafienia).
     * Rzucamy promien od oka atakującego w kierunku, w ktorym patrzy, i
     * sprawdzamy czy w zasiegu ataku przecina on bounding box ofiary. Samo
     * "bycie blisko" nie wystarczy - trzeba faktycznie patrzec na gracza,
     * dokladnie jak przy normalnym biciu mieczem w vanilla. Uzywane WYLACZNIE
     * gdy ofiara stoi w pajeczynie (patrz komentarz przy MAX_COBWEB_VICTIM_REACH) -
     * to nie jest ogolny anti-reach dla calej walki.
     */
    private boolean jestWZasieguHitboxa(Player attacker, Player victim) {
        Location eye = attacker.getEyeLocation();
        org.bukkit.util.BoundingBox hitbox = victim.getBoundingBox();

        if (eye.getWorld() == null || victim.getWorld() == null
                || !eye.getWorld().equals(victim.getWorld())) {
            return false;
        }

        // 1) Twardy limit dystansu do samego hitboxa (nie do srodka) - odcina
        // od razu przypadki, gdzie ktos bije z drugiego konca mapy.
        double distToBox = hitbox.getCenter().toLocation(victim.getWorld()).distance(eye);
        if (distToBox - hitboxRadius(hitbox) > MAX_COBWEB_VICTIM_REACH) {
            return false;
        }

        // 2) Ray-trace: promien od oka atakującego w kierunku patrzenia MUSI
        // faktycznie przeciac realny hitbox ofiary (0.6x1.8x0.6) w zasiegu
        // ataku - to odwzorowuje dokladnie to, jak Minecraft decyduje, czy
        // dany atak trafia cel, zamiast tylko "czy jest wystarczajaco blisko".
        org.bukkit.util.RayTraceResult hit = hitbox.rayTrace(
                eye.toVector(),
                eye.getDirection(),
                MAX_COBWEB_VICTIM_REACH
        );
        return hit != null;
    }

    /** Promien "kuli otaczajacej" hitbox - polowa przekatnej bounding boxa. */
    private double hitboxRadius(org.bukkit.util.BoundingBox box) {
        double dx = box.getWidthX() / 2.0;
        double dy = box.getHeight() / 2.0;
        double dz = box.getWidthZ() / 2.0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Sprawdza, czy gracz fizycznie stoi w bloku pajęczyny (jego hitbox
     * przecina blok COBWEB) - sprawdzamy blok na wysokości stóp, oczu ORAZ
     * w połowie ciała (feet+1), żeby złapać sytuację niezależnie od tego,
     * jak dokładnie serwer zaokrągla lokalizację gracza i niezależnie od
     * tego, na jakiej dokładnie wysokości w hitboxie (1.8 bloku) jest cobweb -
     * hitbox gracza rozciąga się od stóp do oczu, a sam blok cobweb może
     * dotykać tylko środka tego zakresu, nie równo stóp ani równo oczu.
     */
    private boolean isStandingInCobweb(Player player) {
        Location base = player.getLocation();
        Location eye = player.getEyeLocation();

        if (base.getBlock().getType() == Material.COBWEB) {
            return true;
        }
        if (eye.getBlock().getType() == Material.COBWEB) {
            return true;
        }
        // Punkt posredni (okolice bioder/klatki) - hitbox gracza to ok. 1.8
        // bloku wysokosci, wiec sam sprawdzenie stop i oczu moze pominac
        // cobweb dotykajacy srodka hitboxa przy niektorych ulozeniach bloku.
        double midY = base.getY() + (eye.getY() - base.getY()) / 2.0;
        Location mid = new Location(base.getWorld(), base.getX(), midY, base.getZ());
        if (mid.getBlock().getType() == Material.COBWEB) {
            return true;
        }
        if (DEBUG) {
            LOG.info("[CobwebGuard] isStandingInCobweb(" + player.getName() + ")=false. "
                    + "feetBlock=" + base.getBlock().getType()
                    + " midBlock=" + mid.getBlock().getType()
                    + " eyeBlock=" + eye.getBlock().getType());
        }
        return false;
    }

    // ── 3) Blokada stawiania cobweba spoza main/off handa ───────────────────
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.COBWEB) {
            return;
        }

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();

        // Brak informacji o ręce (niektóre starsze wywołania eventu) - żeby nie
        // blokować legalnego stawiania w niejednoznacznym przypadku, wymagamy
        // JAWNIE main lub off hand; wszystko inne traktujemy jako podejrzane.
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            event.setCancelled(true);
            flagIllegalPlace(player);
            return;
        }

        ItemStack usedItem = event.getItemInHand();
        ItemStack actualInHand = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        // Item zgłoszony przez event MUSI faktycznie być pajęczyną trzymaną w
        // tej samej ręce, którą event deklaruje - jeśli serwer widzi co innego
        // (lub pustkę) w tym slocie, to znaczy że blok został postawiony bez
        // realnego trzymania cobweba w ręce (np. spreparowany pakiet).
        if (usedItem == null || usedItem.getType() != Material.COBWEB
                || actualInHand == null || actualInHand.getType() != Material.COBWEB) {
            event.setCancelled(true);
            flagIllegalPlace(player);
        }
    }

    private void flagIllegalPlace(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&4&l❌ &cNie możesz postawić tej pajęczyny (musisz trzymać ją w ręce)."));
    }
}
