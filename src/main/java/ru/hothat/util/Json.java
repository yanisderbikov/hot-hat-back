package ru.hothat.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSONB-поля приезжают из JPA как Map/List. Хелперы повторяют защитное чтение
 * из JS (`Number(x||0)`, `Array.isArray(...) ? ... : []`), чтобы старые
 * документы с пропущенными полями не роняли логику.
 */
public final class Json {

    private Json() {
    }

    public static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }

    public static List<Object> list(Object value) {
        if (value instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }
        return new ArrayList<>();
    }

    public static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        for (Object item : list(value)) {
            if (item != null) {
                String s = String.valueOf(item);
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public static List<String> uniqueStrings(Object value) {
        List<String> out = new ArrayList<>();
        for (String s : strings(value)) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list(value)) {
            if (item instanceof Map<?, ?>) {
                out.add(map(item));
            }
        }
        return out;
    }

    public static long num(Object value) {
        return num(value, 0L);
    }

    public static long num(Object value, long fallback) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return (long) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static double dbl(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
    }

    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String str(Object value, int maxLength) {
        String s = str(value);
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }
}
