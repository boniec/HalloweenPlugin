package boniec.coinflip;

import boniec.klany.util.Msg;

final class CoinflipText {

    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private CoinflipText() {}

    static String small(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%') {
                int koniec = s.indexOf('%', i + 1);
                if (koniec > i) {
                    sb.append(s, i, koniec + 1);
                    i = koniec;
                    continue;
                }
            }
            if (c == '&' || c == '§') {
                sb.append(c);
                if (i + 1 >= s.length()) break;
                char n = s.charAt(++i);
                sb.append(n);
                if (n == '#') {
                    for (int k = 0; k < 6 && i + 1 < s.length(); k++) sb.append(s.charAt(++i));
                } else if (n == 'x' || n == 'X') {
                    for (int k = 0; k < 12 && i + 1 < s.length(); k++) sb.append(s.charAt(++i));
                }
                continue;
            }
            char l = (c >= 'A' && c <= 'Z') ? Character.toLowerCase(c) : c;
            int idx = FROM.indexOf(l);
            sb.append(idx >= 0 ? TO.charAt(idx) : c);
        }
        return sb.toString();
    }

    static String kolor(String s) {
        return Msg.color(small(s));
    }
}
