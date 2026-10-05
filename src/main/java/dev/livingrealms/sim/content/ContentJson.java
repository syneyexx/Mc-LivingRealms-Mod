package dev.livingrealms.sim.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Shared MiniJson field helpers for Living Realms content packs. */
public final class ContentJson {
    private ContentJson() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object root, String label) {
        if (!(root instanceof Map<?, ?> raw)) throw new IllegalArgumentException(label + " root must be an object");
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            if (!(e.getKey() instanceof String k)) throw new IllegalArgumentException(label + " keys must be strings");
            out.put(k, e.getValue());
        }
        return out;
    }

    public static String requireString(Map<String, Object> m, String key) {
        Object v = require(m, key);
        if (!(v instanceof String s) || s.isBlank()) throw new IllegalArgumentException(key + " must be non-empty string");
        return s;
    }

    public static String optionalString(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (!(v instanceof String s) || s.isBlank()) throw new IllegalArgumentException(key + " must be non-empty string");
        return s;
    }

    public static double requireNumber(Map<String, Object> m, String key) {
        Object v = require(m, key);
        if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue())) {
            throw new IllegalArgumentException(key + " must be finite number");
        }
        return n.doubleValue();
    }

    public static double optionalNumber(Map<String, Object> m, String key, double fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue())) {
            throw new IllegalArgumentException(key + " must be finite number");
        }
        return n.doubleValue();
    }

    public static int requireInt(Map<String, Object> m, String key) {
        double n = requireNumber(m, key);
        if (n != Math.rint(n)) throw new IllegalArgumentException(key + " must be integer");
        return (int) n;
    }

    public static boolean optionalBool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (!(v instanceof Boolean b)) throw new IllegalArgumentException(key + " must be boolean");
        return b;
    }

    public static List<String> optionalStringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return List.of();
        if (!(v instanceof List<?> list)) throw new IllegalArgumentException(key + " must be array");
        List<String> out = new ArrayList<>();
        for (Object x : list) {
            if (!(x instanceof String s) || s.isBlank()) throw new IllegalArgumentException(key + " entries must be strings");
            out.add(s);
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> optionalObjectList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return List.of();
        if (!(v instanceof List<?> list)) throw new IllegalArgumentException(key + " must be array");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object x : list) out.add(object(x, key));
        return List.copyOf(out);
    }

    public static Map<String, String> optionalStringMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return Map.of();
        Map<String, Object> raw = object(v, key);
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            if (!(e.getValue() instanceof String s) || s.isBlank()) {
                throw new IllegalArgumentException(key + "." + e.getKey() + " must be non-empty string");
            }
            out.put(e.getKey(), s);
        }
        return Map.copyOf(out);
    }

    public static <E extends Enum<E>> E requireEnum(Class<E> type, Map<String, Object> m, String key) {
        return enumValue(type, requireString(m, key));
    }

    public static <E extends Enum<E>> E optionalEnum(Class<E> type, Map<String, Object> m, String key, E fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (!(v instanceof String s) || s.isBlank()) throw new IllegalArgumentException(key + " must be string");
        return enumValue(type, s);
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        Objects.requireNonNull(value, "value");
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + type.getSimpleName() + ": " + value, e);
        }
    }

    private static Object require(Map<String, Object> m, String key) {
        if (!m.containsKey(key)) throw new IllegalArgumentException("Missing field: " + key);
        return m.get(key);
    }
}
