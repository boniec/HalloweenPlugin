package boniec.gornik;

import boniec.szaman.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /freeturbo - aktywuje PRYWATNY (widoczny tylko dla uzytkownika) TurboDrop
 * na czas z freeturbo.yml (domyslnie 15 minut, leci w czasie rzeczywistym).
 * Wymaga uprawnienia freeturbo.use. Cooldown (domyslnie 12h, freeturbo.yml)
 * jest trwaly - przetrwa restart serwera.
 * <p>
 * /freeturbo reload (freeturbo.admin) - przeladowuje freeturbo.yml (wyglad
 * bossbara, wiadomosci, mnoznik, czas trwania, cooldown) bez restartu.
 * Wszystkie wiadomosci sa w freeturbo.yml -> wiadomosci.
 */
public class FreeTurboCommand implements CommandExecutor {

    private final PrivateTurboManager manager;

    public FreeTurboCommand(PrivateTurboManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("freeturbo.admin")) {
                sender.sendMessage(Msg.color("&cNie masz uprawnień do tej komendy."));
                return true;
            }
            manager.przeladuj();
            sender.sendMessage(Msg.color("&aPrzeładowano &ffreeturbo.yml&a."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }

        if (!player.hasPermission("freeturbo.use")) {
            Msg.send(player, manager.getWiadomosc("brak-uprawnien", "&cNie masz uprawnień do tej komendy."));
            return true;
        }

        long pozostalyCooldown = manager.getPozostalyCooldownMs(player.getUniqueId());
        if (pozostalyCooldown > 0) {
            Msg.send(player, manager.getWiadomosc("cooldown",
                            "&cMusisz jeszcze poczekać &e%cooldown% &cprzed ponownym użyciem /freeturbo.")
                    .replace("%cooldown%", PrivateTurboManager.formatujCzasStatyczny(pozostalyCooldown)));
            return true;
        }

        boolean przedluzenie = manager.isActive(player.getUniqueId());
        manager.aktywuj(player);

        String mnoznik = formatujMnoznik(manager.getMnoznik());
        String czasTrwania = formatujMinuty(manager.getCzasTrwaniaSekund());

        String klucz = przedluzenie ? "przedluzono" : "aktywowano";
        String domyslna = przedluzenie
                ? "&aPrzedłużono Twój prywatny FreeTurbo &e&lx%x% &ado pełnych &e%czas%&a!"
                : "&aAktywowano Twój prywatny FreeTurbo &e&lx%x% &ana &e%czas%&a! Widzisz go tylko Ty.";
        Msg.send(player, manager.getWiadomosc(klucz, domyslna)
                .replace("%x%", mnoznik)
                .replace("%czas%", czasTrwania));
        return true;
    }

    private String formatujMnoznik(double m) {
        return m == Math.floor(m) ? String.valueOf((int) m) : String.valueOf(m);
    }

    private String formatujMinuty(int sekundy) {
        if (sekundy % 60 == 0) {
            return (sekundy / 60) + " min";
        }
        return sekundy + "s";
    }
}
