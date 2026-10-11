package boniec.code.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AdminKodCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "stworz", "create", "usun", "lista", "dodajkomende", "usunkomende", "reload");

    private final CodeManager codeManager;

    public AdminKodCommand(CodeManager codeManager) {
        this.codeManager = codeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("admin.kod")) {
            sender.sendMessage(CodeManager.color("&c&lBLAD &8» &7Nie masz permisji do tej komendy."));
            return true;
        }

        if (args.length == 0) {
            usage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                codeManager.reloadConfigs();
                sender.sendMessage(CodeManager.color("&a&lSUKCES &8» &7Przeladowano kody.yml."));
            }
            case "stworz", "create" -> create(sender, args);
            case "usun" -> delete(sender, args);
            case "lista" -> list(sender);
            case "dodajkomende" -> addCmd(sender, args);
            case "usunkomende" -> removeCmd(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        s.sendMessage(CodeManager.color("&6&lKODY ADMIN &8» &fUzycie:"));
        s.sendMessage(CodeManager.color("&8» &e/adminkod stworz <kod> [sekundy_gry]"));
        s.sendMessage(CodeManager.color("&8» &e/adminkod usun <kod>"));
        s.sendMessage(CodeManager.color("&8» &e/adminkod lista"));
        s.sendMessage(CodeManager.color("&8» &e/adminkod dodajkomende <kod> <komenda...> &7(%player%)"));
        s.sendMessage(CodeManager.color("&8» &e/adminkod usunkomende <kod> <nr>"));
        s.sendMessage(CodeManager.color("&8» &e/adminkod reload"));
        s.sendMessage(CodeManager.color("&7Gracz odbiera: &e/kod <kod>"));
    }

    private void create(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Uzycie: &e/adminkod stworz <kod> [sekundy]"));
            return;
        }
        String name = args[1].toUpperCase(Locale.ROOT);
        int seconds = 0;
        if (args.length >= 3) {
            try {
                seconds = Math.max(0, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Czas musi byc liczba sekund (0 = bez wymogu)."));
                return;
            }
        }
        if (codeManager.codeExists(name)) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Kod &e" + name + " &cjuz istnieje."));
            return;
        }
        codeManager.createCode(name, seconds);
        s.sendMessage(CodeManager.color("&a&lSUKCES &8» &7Stworzono kod &e" + name
                + (seconds > 0 ? " &7(wymagany czas gry: &e" + seconds + "s&7)" : " &7(bez wymogu czasu)")));
        s.sendMessage(CodeManager.color("&7Dodaj nagrody: &e/adminkod dodajkomende " + name + " give %player% diamond 1"));
    }

    private void delete(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Uzycie: &e/adminkod usun <kod>"));
            return;
        }
        String name = args[1].toUpperCase(Locale.ROOT);
        if (!codeManager.deleteCode(name)) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Nie ma kodu &e" + name + "&c."));
            return;
        }
        s.sendMessage(CodeManager.color("&a&lSUKCES &8» &7Usunieto kod &e" + name + "&a."));
    }

    private void list(CommandSender s) {
        List<String> codes = codeManager.listCodes();
        if (codes.isEmpty()) {
            s.sendMessage(CodeManager.color("&7Brak kodow. &e/adminkod stworz <kod>"));
            return;
        }
        s.sendMessage(CodeManager.color("&6&lKODY &8» &7" + codes.size() + " szt.:"));
        for (String id : codes) {
            int used = codeManager.getUsedCount(id);
            int req = codeManager.getRequiredSeconds(id);
            int cmds = codeManager.getCommands(id).size();
            s.sendMessage(CodeManager.color("&8- &e" + id + " &7odebran: &f" + used
                    + " &8| &7komend: &f" + cmds
                    + (req > 0 ? " &8| &7czas gry: &f" + req + "s" : "")));
        }
    }

    private void addCmd(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Uzycie: &e/adminkod dodajkomende <kod> <komenda...>"));
            s.sendMessage(CodeManager.color("&7Przyklad: &e/adminkod dodajkomende START give %player% diamond 5"));
            return;
        }
        String name = args[1].toUpperCase(Locale.ROOT);
        if (!codeManager.codeExists(name)) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Nie ma kodu &e" + name + "&c."));
            return;
        }
        StringBuilder cmd = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) cmd.append(' ');
            cmd.append(args[i]);
        }
        String line = cmd.toString().trim();
        if (line.startsWith("/")) line = line.substring(1);
        codeManager.addCommand(name, line);
        s.sendMessage(CodeManager.color("&a&lSUKCES &8» &7Dodano komende do &e" + name + "&7: &f" + line));
    }

    private void removeCmd(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Uzycie: &e/adminkod usunkomende <kod> <nr>"));
            return;
        }
        String name = args[1].toUpperCase(Locale.ROOT);
        if (!codeManager.codeExists(name)) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Nie ma kodu &e" + name + "&c."));
            return;
        }
        int nr;
        try {
            nr = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Numer komendy musi byc liczba."));
            return;
        }
        List<String> cmds = codeManager.getCommands(name);
        if (!codeManager.removeCommand(name, nr)) {
            s.sendMessage(CodeManager.color("&c&lBLAD &8» &7Zly numer. Ten kod ma &e" + cmds.size() + " &ckomend(y)."));
            return;
        }
        s.sendMessage(CodeManager.color("&a&lSUKCES &8» &7Usunieto komende nr &e" + nr + " &7z kodu &e" + name + "&a."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("admin.kod")) return List.of();
        String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(pre)).collect(Collectors.toList());
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("usun") || sub.equals("dodajkomende") || sub.equals("usunkomende"))) {
            return codeManager.listCodes().stream()
                    .filter(c -> c.toLowerCase(Locale.ROOT).startsWith(pre))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && sub.equals("stworz") || (args.length == 3 && sub.equals("create"))) {
            return List.of("0", "3600", "7200");
        }
        return new ArrayList<>();
    }
}
