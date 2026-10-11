package boniec.ahalloween;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class AHalloweenCommand implements CommandExecutor, TabCompleter {

    private final AHalloweenManager manager;

    public AHalloweenCommand(AHalloweenManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("kociol")) {
            kociol(sender, args);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("kociol")) {
            kociol(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(manager.msg("tylko-gracz"));
                return true;
            }
            if (!sender.hasPermission("ahalloween.use")) {
                sender.sendMessage(manager.msg("brak-uprawnien"));
                return true;
            }
            HalloweenGuis.otworz(manager, p, HalloweenGuis.Typ.MENU);
            return true;
        }
        String a0 = args[0].toLowerCase(Locale.ROOT);
        if (a0.equals("help")) {
            help(sender);
            return true;
        }
        if (a0.equals("worek")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(manager.msg("tylko-gracz"));
                return true;
            }
            HalloweenGuis.otworz(manager, p, HalloweenGuis.Typ.BAG);
            return true;
        }
        if (a0.equals("stats")) {
            if (args.length >= 2 && sender.hasPermission("ahalloween.admin")) {
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&cGracz musi byc online."));
                    return true;
                }
                HalloweenGuis.otworz(manager, t, HalloweenGuis.Typ.STATS);
                return true;
            }
            if (sender instanceof Player p) HalloweenGuis.otworz(manager, p, HalloweenGuis.Typ.STATS);
            return true;
        }
        if (!sender.hasPermission("ahalloween.admin")) {
            sender.sendMessage(manager.msg("brak-uprawnien"));
            return true;
        }
        switch (a0) {
            case "reload" -> {
                manager.reload();
                sender.sendMessage(manager.msg("reload"));
            }
            case "start" -> {
                int s = args.length >= 2 ? parseInt(args[1], manager.cfg().getInt("event.czas-sekund", 86400)) : manager.cfg().getInt("event.czas-sekund", 86400);
                manager.start(s);
                sender.sendMessage(manager.msg("event-start"));
            }
            case "stop" -> manager.stop();
            case "set" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(manager.msg("tylko-gracz"));
                    return true;
                }
                boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
                if (manager.maSzablonWorka() && !confirm) {
                    sender.sendMessage(manager.msg("set-confirm"));
                    return true;
                }
                if (manager.ustawWorekZReki(p, confirm || !manager.maSzablonWorka())) {
                    sender.sendMessage(manager.msg("set-worek"));
                } else {
                    sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&cTrzymaj worek w rece."));
                }
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&cUzycie: &e/ahalloween give <gracz> <item> [ilosc]"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) return true;
                int ile = args.length >= 4 ? parseInt(args[3], 1) : 1;
                if (args[2].equalsIgnoreCase("worek") || args[2].equalsIgnoreCase("bag")) {
                    t.getInventory().addItem(manager.stworzWorek());
                } else {
                    manager.dodajCukierki(t, args[2].toLowerCase(Locale.ROOT), ile, "admin");
                }
                sender.sendMessage(manager.msg("give", "item", args[2], "ilosc", String.valueOf(ile), "gracz", t.getName()));
            }
            case "spawn" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&cUzycie: &e/ahalloween spawn <typ> [ilosc]"));
                    return true;
                }
                int ile = args.length >= 3 ? parseInt(args[2], 1) : 1;
                int n = manager.recznySpawnMoba(args[1].toLowerCase(Locale.ROOT), p.getLocation(), ile);
                sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&aSpawn mobow: &2" + n));
            }
            case "candy" -> candy(sender, args);
            case "dom", "trick", "trickortreat" -> dom(sender, args);
            default -> {
                if (manager.mobIds().contains(a0)) {
                    mobCmd(sender, a0, args);
                } else {
                    help(sender);
                }
            }
        }
        return true;
    }

    private void kociol(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.msg("tylko-gracz"));
            return;
        }
        if (!sender.hasPermission("ahalloween.use")) {
            sender.sendMessage(manager.msg("brak-uprawnien"));
            return;
        }
        if (args.length == 0) {
            HalloweenGuis.otworz(manager, player, HalloweenGuis.Typ.CAULDRON);
            return;
        }
        if (args.length != 1) {
            sender.sendMessage(HalloweenUtil.colorMsg("&cUżyj: /kociol [ilość|all]"));
            return;
        }
        int amount;
        try {
            amount = args[0].equalsIgnoreCase("all") ? manager.dane(player).sumaCukierkow() : Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(HalloweenUtil.colorMsg("&cPodaj dodatnią liczbę cukierków lub all."));
            return;
        }
        manager.kociol().deposit(player, amount);
    }

    private void dom(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX
                    + "&c/ahalloween dom dodaj [nazwa] | usun | lista | wyczysc"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "dodaj", "add", "set" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(manager.msg("tylko-gracz"));
                    return;
                }
                String nazwa = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
                String key = manager.trickOrTreat().dodajDom(p, nazwa);
                if (key == null) {
                    sender.sendMessage(manager.msg("trick-brak-drzwi"));
                    return;
                }
                sender.sendMessage(manager.msg("trick-dom-dodany", "id", key, "nazwa",
                        nazwa.isBlank() ? "Dom Halloween" : nazwa));
            }
            case "usun", "remove", "delete" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(manager.msg("tylko-gracz"));
                    return;
                }
                if (!manager.trickOrTreat().usunDom(p)) {
                    sender.sendMessage(manager.msg("trick-dom-brak"));
                    return;
                }
                sender.sendMessage(manager.msg("trick-dom-usuniety"));
            }
            case "lista", "list" -> {
                int n = manager.trickOrTreat().liczbaDomow();
                if (n == 0) {
                    sender.sendMessage(manager.msg("trick-dom-lista-pusta"));
                    return;
                }
                sender.sendMessage(manager.msg("trick-dom-lista", "ilosc", String.valueOf(n)));
                for (String linia : manager.trickOrTreat().opisDomow()) {
                    sender.sendMessage(HalloweenUtil.colorMsg(" &8» " + linia));
                }
            }
            case "wyczysc", "clear" -> {
                int n = manager.trickOrTreat().wyczyscDomy();
                sender.sendMessage(manager.msg("trick-dom-wyczyszczone", "ilosc", String.valueOf(n)));
            }
            default -> sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&cNieznana podkomenda dom."));
        }
    }

    private void candy(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX
                    + "&c/ahalloween candy setspawn [typ] | listspawns | reload | clear | clearspawn"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(manager.msg("tylko-gracz"));
                    return;
                }
                String typ = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "any";
                String key = manager.dodajSpawnCukierka(p.getLocation(), typ);
                sender.sendMessage(manager.msg("spawn-ustawiony", "id", key));
                if (!manager.aktywny()) sender.sendMessage(manager.msg("spawn-event-off"));
            }
            case "listspawns" -> {
                int n = manager.liczbaSpawnowCukierkow();
                if (n == 0) {
                    sender.sendMessage(manager.msg("spawns-brak"));
                    return;
                }
                sender.sendMessage(manager.msg("spawns-naglowek", "ilosc", String.valueOf(n)));
                for (String linia : manager.opisSpawnowCukierkow()) {
                    sender.sendMessage(HalloweenUtil.colorMsg(" &8» " + linia));
                }
            }
            case "reload" -> {
                manager.reload();
                sender.sendMessage(manager.msg("reload"));
            }
            case "clear" -> {
                manager.wyczyscCukierkiSwiata();
                sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&aUsunieto cukierki ze swiata."));
            }
            case "clearspawn" -> {
                int n = manager.wyczyscSpawny("candy.");
                sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&aUsunieto &2" + n + " &apunktow spawnu cukierkow."));
            }
            default -> sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&cNieznana podkomenda candy."));
        }
    }

    private void mobCmd(CommandSender sender, String mob, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&c/ahalloween " + mob + " setspawn | clearspawn"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(manager.msg("tylko-gracz"));
                    return;
                }
                String key = manager.dodajSpawnMoba(p.getLocation(), mob);
                sender.sendMessage(manager.msg("spawn-ustawiony", "id", key));
            }
            case "clearspawn" -> {
                int n = manager.wyczyscSpawny("mob." + mob + ".");
                sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&aUsunieto &2" + n + " &aspawnow &e" + mob + "&a."));
            }
            default -> sender.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&csetspawn | clearspawn"));
        }
    }

    private void help(CommandSender s) {
        s.sendMessage(HalloweenUtil.colorMsg(HalloweenUtil.PREFIX + "&eKomendy:"));
        s.sendMessage(HalloweenUtil.colorMsg(" &8» &a/ahalloween &7- menu"));
        s.sendMessage(HalloweenUtil.colorMsg(" &8» &a/ahalloween worek &7- magazyn"));
        s.sendMessage(HalloweenUtil.colorMsg(" &8» &a/kociol [ilość|all] &7- wspólny kocioł i bonus cukierków"));
        if (s.hasPermission("ahalloween.admin")) {
            s.sendMessage(HalloweenUtil.colorMsg(" &8» &e/ahalloween candy setspawn [typ]"));
            s.sendMessage(HalloweenUtil.colorMsg(" &8» &e/ahalloween dom dodaj [nazwa] &7— drzwi trick-or-treat"));
            s.sendMessage(HalloweenUtil.colorMsg(" &8» &e/ahalloween <mob> setspawn"));
            s.sendMessage(HalloweenUtil.colorMsg(" &8» &estart &8| &estop &8| &ereload &8| &egive &8| &espawn"));
        }
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kociol")) {
            return args.length == 1 && "all".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("all") : List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kociol")) {
            return "all".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("all") : List.of();
        }
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("help", "worek", "stats", "kociol"));
            if (sender.hasPermission("ahalloween.admin")) {
                out.addAll(List.of("reload", "start", "stop", "set", "give", "spawn", "candy", "dom"));
                out.addAll(manager.mobIds());
            }
        } else if (args.length == 2 && sender.hasPermission("ahalloween.admin")) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            if (a0.equals("candy")) out.addAll(List.of("setspawn", "listspawns", "reload", "clear", "clearspawn"));
            else if (a0.equals("dom") || a0.equals("trick") || a0.equals("trickortreat")) {
                out.addAll(List.of("dodaj", "usun", "lista", "wyczysc"));
            }
            else if (manager.mobIds().contains(a0)) out.addAll(List.of("setspawn", "clearspawn"));
            else if (a0.equals("give") || a0.equals("stats")) {
                return null;
            } else if (a0.equals("spawn")) out.addAll(manager.mobIds());
            else if (a0.equals("set")) out.add("confirm");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("candy") && args[1].equalsIgnoreCase("setspawn")) {
            out.add("any");
            out.addAll(manager.candyIds());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.add("worek");
            out.addAll(manager.candyIds());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
