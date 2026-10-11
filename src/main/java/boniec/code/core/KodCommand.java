package boniec.code.core;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class KodCommand implements CommandExecutor {

    private final CodeManager codeManager;

    public KodCommand(CodeManager codeManager) {
        this.codeManager = codeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(CodeManager.color("&6&lKOD &8» &7Poprawne uzycie: &e/kod <nazwa_kodu>"));
            return true;
        }

        String codeName = args[0].toUpperCase();

        if (!codeManager.codeExists(codeName)) {
            player.sendMessage(CodeManager.color("&c&lBLAD &8» &7Podany kod nie istnieje lub wygasl!"));
            return true;
        }

        if (codeManager.hasUsed(player.getUniqueId(), codeName)) {
            player.sendMessage(CodeManager.color("&c&lBLAD &8» &7Uzyles juz tego kodu!"));
            return true;
        }

        if (codeManager.wymagaDiscord(codeName)) {
            boniec.nagroda.NagrodaManager nagroda = boniec.dev.inventory.Main.getInstance().getNagrodaManager();
            if (nagroda == null || !nagroda.czyOdebrano(player.getUniqueId())) {
                player.sendTitle(
                        CodeManager.color("&4Blad"),
                        CodeManager.color("&cPolacz swoje konto &4/nagroda"),
                        10, 50, 10);
                return true;
            }
        }

        long playedTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long playedSeconds = playedTicks / 20;

        int requiredSeconds = codeManager.getRequiredSeconds(codeName);

        if (playedSeconds < requiredSeconds) {
            long remainingSeconds = requiredSeconds - playedSeconds;
            player.sendMessage(CodeManager.color("&c&lBLAD &8» &7Nie posiadasz wymaganego czasu gry!"));
            player.sendMessage(CodeManager.color("&7Wymagany czas: &e" + formatTime(requiredSeconds)));
            player.sendMessage(CodeManager.color("&7Twoj czas gry: &e" + formatTime(playedSeconds)));
            player.sendMessage(CodeManager.color("&7Pozostalo do odebrania: &c" + formatTime(remainingSeconds)));
            return true;
        }

        // Najpierw zapis "uzyty", dopiero potem nagrody - spam /kod nie przejdzie drugi raz.
        if (!codeManager.tryRedeem(player.getUniqueId(), codeName)) {
            player.sendMessage(CodeManager.color("&c&lBLAD &8» &7Uzyles juz tego kodu!"));
            return true;
        }

        List<String> commands = codeManager.getCommands(codeName);
        for (String cmd : commands) {
            String formattedCmd = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
        }

        // Wysyłanie wielolinijkowego ogłoszenia (broadcastu)
        List<String> broadcastMsgs = codeManager.getBroadcast(codeName);
        for (String msg : broadcastMsgs) {
            if (msg != null && !msg.isEmpty()) {
                String formattedBroadcast = CodeManager.color(msg.replace("%player%", player.getName()));
                boniec.ustawienia.UstawieniaManager.broadcast(boniec.ustawienia.UstawieniaKlucz.KODY, formattedBroadcast);
            }
        }

        player.sendMessage(CodeManager.color("&a&lSUKCES &8» &7Pomyslnie odebrales kod &e" + codeName + "&7!"));
        return true;
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds <= 0) return "0 sek.";

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" godz. ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append(" min. ");
        }
        if (seconds > 0 || (hours == 0 && minutes == 0)) {
            sb.append(seconds).append(" sek.");
        }
        return sb.toString().trim();
    }
}