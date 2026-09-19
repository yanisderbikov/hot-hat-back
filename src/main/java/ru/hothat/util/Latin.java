package ru.hothat.util;

import java.util.Map;

/** Транслитерация из portal.js: ник по умолчанию строится латиницей. */
public final class Latin {

    private static final Map<Character, String> MAP = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"),
            Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"),
            Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "sch"), Map.entry('ъ', ""),
            Map.entry('ы', "y"), Map.entry('ь', ""), Map.entry('э', "e"), Map.entry('ю', "yu"),
            Map.entry('я', "ya"));

    private Latin() {
    }

    public static String latinize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            char lower = Character.toLowerCase(c);
            String replacement = MAP.get(lower);
            if (replacement == null) {
                out.append(c);
                continue;
            }
            if (c != lower && !replacement.isEmpty()) {
                out.append(Character.toUpperCase(replacement.charAt(0))).append(replacement.substring(1));
            } else {
                out.append(replacement);
            }
        }
        return out.toString();
    }

    /** База ника: транслит → NFKD → только [A-Za-z0-9_] → обязательно буква в начале. */
    public static String nicknameBase(String source, String uid) {
        String base = java.text.Normalizer.normalize(latinize(source == null ? "" : source), java.text.Normalizer.Form.NFKD)
                .replaceAll("[^A-Za-z0-9_]", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = base.isEmpty() ? "player" + tail(uid) : "p" + base;
        }
        base = base.length() > 16 ? base.substring(0, 16) : base;
        return base.isEmpty() ? "player" + tail(uid) : base;
    }

    public static String tail(String uid) {
        String value = uid == null ? "" : uid;
        return value.length() <= 6 ? value : value.substring(value.length() - 6);
    }
}
