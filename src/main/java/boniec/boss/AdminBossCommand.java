package boniec.boss;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminBossCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBKOMENDY = Arrays.asList(
            "spawn", "kill", "info", "reload", "respawn", "ustawspawn", "top", "editdrop"
    );

    private final MinotaurManager manager;
    private final MinotaurDropGui dropGui;

    public AdminBossCommand(MinotaurManager manager, MinotaurDropGui dropGui) {
        this.manager = manager;
        this.dropGui = dropGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boss.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Użycie: /adminboss <spawn|kill|info|reload|respawn|ustawspawn|top|editdrop>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "ustawspawn": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Ta subkomenda jest tylko dla graczy.");
                    return true;
                }
                Player player = (Player) sender;
                manager.ustawSpawn(player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Ustawiono spawn Minotaura w Twojej lokalizacji.");
                return true;
            }
            case "spawn": {
                if (!manager.czyMaZapisanySpawn()) {
                    sender.sendMessage(ChatColor.RED + "Nie ustawiono jeszcze spawnu bossa. Użyj /adminboss ustawspawn.");
                    return true;
                }
                boolean ok = manager.spawn();
                sender.sendMessage(ok
                        ? ChatColor.GREEN + "Zespawnowano Minotaura."
                        : ChatColor.RED + "Nie udało się zespawnować bossa.");
                return true;
            }
            case "kill": {
                if (!manager.isAlive()) {
                    sender.sendMessage(ChatColor.RED + "Boss aktualnie nie żyje.");
                    return true;
                }
                manager.zabijAdmin();
                sender.sendMessage(ChatColor.GREEN + "Zabito Minotaura (nagrody TOP zostały rozdane).");
                return true;
            }
            case "respawn": {
                if (manager.isAlive()) {
                    sender.sendMessage(ChatColor.RED + "Boss już żyje - najpierw użyj /adminboss kill.");
                    return true;
                }
                if (!manager.czyMaZapisanySpawn()) {
                    sender.sendMessage(ChatColor.RED + "Nie ustawiono jeszcze spawnu bossa. Użyj /adminboss ustawspawn.");
                    return true;
                }
                manager.spawn();
                sender.sendMessage(ChatColor.GREEN + "Wymuszono natychmiastowy respawn Minotaura.");
                return true;
            }
            case "editdrop": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Ta subkomenda jest tylko dla graczy.");
                    return true;
                }
                dropGui.otworz((Player) sender);
                return true;
            }
            case "reload": {
                manager.reload();
                sender.sendMessage(ChatColor.GREEN + "Przeładowano konfigurację bossa (boss.yml).");
                return true;
            }
            case "info": {
                wyslijInfo(sender);
                return true;
            }
            case "top": {
                wyslijTop(sender);
                return true;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Nieznana subkomenda.");
                return true;
        }
    }

    private void wyslijInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "── Minotaur ──");
        if (manager.isAlive()) {
            double hp = manager.getAktywny().getEntity().getHealth();
            double hpMax = manager.getAktywny().getHpMax();
            sender.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.GREEN + "ŻYJE");
            sender.sendMessage(ChatColor.GRAY + "HP: " + ChatColor.RED + (long) hp + "/" + (long) hpMax);
        } else {
            sender.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.RED + "MARTWY");
            long sek = manager.getSekundyDoRespawnu();
            sender.sendMessage(ChatColor.GRAY + "Respawn za: " + ChatColor.YELLOW
                    + (sek >= 0 ? manager.formatujCzasDoRespawnu() : "nie zaplanowano"));
        }
        sender.sendMessage(ChatColor.GRAY + "Ma ustawiony spawn: "
                + (manager.czyMaZapisanySpawn() ? ChatColor.GREEN + "tak" : ChatColor.RED + "nie"));
    }

    private void wyslijTop(CommandSender sender) {
        if (!manager.isAlive()) {
            sender.sendMessage(ChatColor.RED + "Boss aktualnie nie żyje.");
            return;
        }
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(manager.getAktywny().getObrazeniaGraczy().entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        if (ranking.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nikt jeszcze nie zaatakował bossa.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "── TOP obrażeń Minotaurowi ──");
        int limit = Math.min(10, ranking.size());
        for (int i = 0; i < limit; i++) {
            UUID uuid = ranking.get(i).getKey();
            double dmg = ranking.get(i).getValue();
            String nick = Bukkit.getOfflinePlayer(uuid).getName();
            if (nick == null) nick = uuid.toString();
            sender.sendMessage(ChatColor.YELLOW + "#" + (i + 1) + " " + ChatColor.WHITE + nick
                    + ChatColor.GRAY + " - " + ChatColor.RED + String.format("%.1f", dmg) + " obrażeń");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> wyniki = new ArrayList<>();
            for (String sub : SUBKOMENDY) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    wyniki.add(sub);
                }
            }
            return wyniki;
        }
        return new ArrayList<>();
    }
}