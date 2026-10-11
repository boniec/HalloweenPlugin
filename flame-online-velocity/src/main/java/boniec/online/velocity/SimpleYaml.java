package boniec.online.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimalny reader YAML dla prostego configu (klucze, zagniezdzenia, stringi). */
final class SimpleYaml {

    private final Map<String, String> values = new LinkedHashMap<>();

    static SimpleYaml load(Path file) throws IOException {
        SimpleYaml y = new SimpleYaml();
        if (!Files.isRegularFile(file)) return y;
        List<String> lines = Files.readAllLines(file);
        String[] stack = new String[8];
        int[] indents = new int[8];
        int depth = 0;
        for (String raw : lines) {
            String line = raw.replace("\t", "  ");
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            while (depth > 0 && indent <= indents[depth - 1]) depth--;
            int colon = trimmed.indexOf(':');
            if (colon < 0) continue;
            String key = trimmed.substring(0, colon).trim();
            String val = trimmed.substring(colon + 1).trim();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            String prefix = "";
            for (int i = 0; i < depth; i++) {
                prefix += stack[i] + ".";
            }
            if (val.isEmpty()) {
                stack[depth] = key;
                indents[depth] = indent;
                depth++;
            } else {
                y.values.put(prefix + key, val);
            }
        }
        return y;
    }

    String get(String key, String def) {
        return values.getOrDefault(key, def);
    }

    int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    boolean getBoolean(String key, boolean def) {
        String v = get(key, String.valueOf(def)).trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return def;
    }

    Map<String, String> children(String prefix) {
        String p = prefix.endsWith(".") ? prefix : prefix + ".";
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey().startsWith(p)) {
                out.put(e.getKey().substring(p.length()), e.getValue());
            }
        }
        return out;
    }
}
