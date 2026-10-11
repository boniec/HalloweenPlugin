package boniec.rynek;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Komenda administracyjna rynku: /arynek. Obslugiwane podkomendy:
 * <ul>
 *   <li><b>ustawodlamek</b> - ustawia wlasny szablon "Smoczych Odlamkow" rynku
 *       (trzymaj item w rece), 1:1 (pelne NBT), niezaleznie od szablonow uzywanych
 *       przez klany (KlanWalutaManager) czy patronow (MistrzSystem). Dzieki temu admin
 *       ma jedno, pewne miejsce do ustawienia tego, co rynek ma uznawac za odlamki.
 *       Ta sama logika jest tez dostepna jako /rynek ustawodlamek.</li>
 *   <li><b>wylacz|wlacz &lt;pieniadze|odlamki&gt;</b> - blokuje/odblokowuje handel
 *       w wybranej walucie (wystawianie i kupno ofert platnych ta waluta), flaga
 *       zapisywana w rynek.yml pod "handel.wylaczone.*".</li>
 * </ul>
 */
public class ARynekCommand implements CommandExecutor, TabCompleter {

    private final Rynek rynek;

    public ARynekCommand(Rynek rynek) {
        this.rynek = rynek;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("ustawodlamek")) {
            rynek.wykonajUstawOdlamek(sender);
            return true;
        }
        // /arynek wylacz|wlacz <pieniadze|odlamki> - wylacza/odlaczana handel w walucie.
        if (args.length >= 1 && (args[0].equalsIgnoreCase("wylacz") || args[0].equalsIgnoreCase("wlacz"))) {
            rynek.wykonajPrzelaczHandel(sender, args[0].equalsIgnoreCase("wylacz"),
                    args.length >= 2 ? args[1] : null);
            return true;
        }
        sender.sendMessage("Usage: /arynek ustawodlamek | wylacz|wlacz <pieniadze|odlamki>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filtruj(args[0], List.of("ustawodlamek", "wylacz", "wlacz"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("wylacz") || args[0].equalsIgnoreCase("wlacz"))) {
            return filtruj(args[1], List.of("pieniadze", "odlamki"));
        }
        return List.of();
    }

    /** Proste filtrowanie podpowiedzi po prefiksie wpisanego tekstu. */
    private List<String> filtruj(String wpisane, List<String> opcje) {
        String p = wpisane.toLowerCase(Locale.ROOT);
        List<String> wynik = new ArrayList<>();
        for (String opcja : opcje) {
            if (opcja.startsWith(p)) wynik.add(opcja);
        }
        return wynik;
    }
}
