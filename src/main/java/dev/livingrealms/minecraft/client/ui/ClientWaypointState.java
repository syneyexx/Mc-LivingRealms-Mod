package dev.livingrealms.minecraft.client.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Ephemeral client waypoints (dialogue marks, history pins). */
public final class ClientWaypointState {
    public record Waypoint(String label, double x, double z, long expiresAtMs) {}

    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();

    private ClientWaypointState() {}

    public static void add(String label, double x, double z, int ttlSeconds) {
        if (label == null || label.isBlank() || !Double.isFinite(x) || !Double.isFinite(z) || ttlSeconds <= 0) return;
        long expires = System.currentTimeMillis() + ttlSeconds * 1000L;
        String key = label.trim();
        WAYPOINTS.removeIf(w -> w.label().equalsIgnoreCase(key));
        WAYPOINTS.add(new Waypoint(key, x, z, expires));
        if (WAYPOINTS.size() > 24) WAYPOINTS.removeFirst();
    }

    public static List<Waypoint> active() {
        long now = System.currentTimeMillis();
        Iterator<Waypoint> it = WAYPOINTS.iterator();
        while (it.hasNext()) {
            if (it.next().expiresAtMs() <= now) it.remove();
        }
        return List.copyOf(WAYPOINTS);
    }

    public static void clear() {
        WAYPOINTS.clear();
    }
}
