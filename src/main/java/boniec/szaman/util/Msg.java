package boniec.szaman.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Msg {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");

    // Kolor hex w formacie "&x&R&R&G&G&B&B" (jak w displayname perkow w szaman.yml) jest
    // poprawny tylko wtedy, gdy po "&x" idzie dokladnie 6 par "&<cyfra hex>". Zdarza sie, ze
    // w configu zostaje "zablakany" znacznik "&x" bez kompletu cyfr (np. "...&lɪ&xᴇɴɪᴇ") -
    // klient rysuje wtedy dziwne znaki/resetuje format, wiec takie "&x" po prostu usuwamy.
    private static final Pattern BLEDNY_ZNACZNIK_X = Pattern.compile("&[xX](?!(?:&[0-9A-Fa-f]){6})");

    private Msg() {
    }

    public static String color(String tekst) {
        if (tekst == null) return "";
        tekst = BLEDNY_ZNACZNIK_X.matcher(tekst).replaceAll("");
        Matcher matcher = HEX_PATTERN.matcher(tekst);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static void send(CommandSender odbiorca, String tekst) {
        odbiorca.sendMessage(color(tekst));
    }
}
