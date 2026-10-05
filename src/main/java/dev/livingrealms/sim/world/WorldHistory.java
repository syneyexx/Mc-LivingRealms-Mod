package dev.livingrealms.sim.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorldHistory {
    private final ArrayDeque<WorldEvent> events = new ArrayDeque<>();
    private final int maxEvents;

    public WorldHistory(int maxEvents) {
        this.maxEvents = Math.max(100, maxEvents);
    }

    public void add(WorldEvent e) {
        events.addLast(Objects.requireNonNull(e, "event"));
        while (events.size() > maxEvents) events.removeFirst();
    }

    public int size() {
        return events.size();
    }

    public int maxEvents() {
        return maxEvents;
    }

    public List<WorldEvent> recent(int count) {
        List<WorldEvent> all = new ArrayList<>(events);
        int start = Math.max(0, all.size() - count);
        return List.copyOf(all.subList(start, all.size()));
    }

    public List<WorldEvent> all() {
        return List.copyOf(events);
    }

    /**
     * Wave 28 — replace the log after summarization/compaction.
     * Still respects {@link #maxEvents} by dropping oldest if needed.
     */
    public void replaceAll(List<WorldEvent> next) {
        events.clear();
        if (next == null) return;
        for (WorldEvent e : next) {
            if (e != null) add(e);
        }
    }
}
