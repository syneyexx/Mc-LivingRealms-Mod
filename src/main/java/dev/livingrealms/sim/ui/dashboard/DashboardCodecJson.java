package dev.livingrealms.sim.ui.dashboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared JSON helpers for modular dashboard section codecs (protocol 20). */
public final class DashboardCodecJson {
    private DashboardCodecJson() {}

    public static Map<String, Object> map(Object... values) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) m.put((String) values[i], values[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        if (!(o instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected object");
        for (Object k : raw.keySet()) if (!(k instanceof String)) throw new IllegalArgumentException("Non-string key");
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        if (!(o instanceof List<?> raw)) throw new IllegalArgumentException("Expected list");
        return (List<Object>) raw;
    }

    public static Number number(Object o) {
        if (!(o instanceof Number n)) throw new IllegalArgumentException("Expected number");
        return n;
    }

    public static Object required(Map<String, Object> m, String k) {
        if (!m.containsKey(k)) throw new IllegalArgumentException("Missing field: " + k);
        return m.get(k);
    }

    public static long longNum(Map<String, Object> m, String k) {
        return number(required(m, k)).longValue();
    }

    public static int intNum(Map<String, Object> m, String k) {
        return Math.toIntExact(longNum(m, k));
    }

    public static double dbl(Map<String, Object> m, String k) {
        return number(required(m, k)).doubleValue();
    }

    public static String str(Map<String, Object> m, String k) {
        Object o = required(m, k);
        if (!(o instanceof String s)) throw new IllegalArgumentException("Expected string: " + k);
        return s;
    }

    public static boolean bool(Map<String, Object> m, String k) {
        Object o = required(m, k);
        if (!(o instanceof Boolean b)) throw new IllegalArgumentException("Expected boolean: " + k);
        return b;
    }
}
