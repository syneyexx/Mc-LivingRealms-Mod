package dev.livingrealms.sim.underworld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Server-side stolen-goods inventory — black market may sell only what this ledger holds. */
public final class StolenGoodsLedger {
    public static final int DEFAULT_MAX_ENTRIES = 4_096;

    private final ArrayList<StolenGoodsEntry> entries = new ArrayList<>();
    private final int maxEntries;

    public StolenGoodsLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public StolenGoodsLedger(int maxEntries) {
        this.maxEntries = Math.max(64, maxEntries);
    }

    public List<StolenGoodsEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public Optional<StolenGoodsEntry> find(long id) {
        return entries.stream().filter(e -> e.id() == id).findFirst();
    }

    public List<StolenGoodsEntry> unsoldFor(String actorKey) {
        if (actorKey == null || actorKey.isBlank()) return List.of();
        List<StolenGoodsEntry> out = new ArrayList<>();
        for (StolenGoodsEntry e : entries) {
            if (!e.sold() && e.actorKey().equals(actorKey)) out.add(e);
        }
        return Collections.unmodifiableList(out);
    }

    public void deposit(StolenGoodsEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entries.size() >= maxEntries) pruneSold(0);
        if (entries.size() >= maxEntries) {
            StolenGoodsEntry drop = null;
            for (StolenGoodsEntry e : entries) {
                if (drop == null || e.acquiredDay() < drop.acquiredDay()
                        || (e.acquiredDay() == drop.acquiredDay() && e.id() < drop.id())) {
                    drop = e;
                }
            }
            if (drop != null) entries.remove(drop);
        }
        if (entries.size() >= maxEntries) return;
        entries.add(entry);
    }

    public void restore(StolenGoodsEntry entry) {
        deposit(entry);
    }

    public int pruneSold(long olderThanDay) {
        int before = entries.size();
        entries.removeIf(e -> e.sold() && (olderThanDay <= 0 || e.soldDay() < olderThanDay));
        return before - entries.size();
    }

    public int size() {
        return entries.size();
    }
}
