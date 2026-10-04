package dev.livingrealms.sim.construction;

import java.util.Objects;

/**
 * Globally stable construction retry identity: {@code settlementId:intentKey}.
 *
 * <p>Local intent keys such as {@code house:4} or {@code road:2:5} repeat across settlements.
 * Retry/backoff maps must never key by the local intent key alone.
 */
public final class ConstructionRetryKey {
    private final long settlementId;
    private final String intentKey;

    public ConstructionRetryKey(long settlementId, String intentKey) {
        if (settlementId <= 0) throw new IllegalArgumentException("settlementId");
        if (intentKey == null || intentKey.isBlank()) throw new IllegalArgumentException("intentKey");
        this.settlementId = settlementId;
        this.intentKey = intentKey;
    }

    public static ConstructionRetryKey of(ConstructionIntent intent) {
        Objects.requireNonNull(intent, "intent");
        return new ConstructionRetryKey(intent.settlementId(), intent.key());
    }

    public static ConstructionRetryKey of(ConstructionJob job) {
        Objects.requireNonNull(job, "job");
        return of(job.intent());
    }

    public static ConstructionRetryKey parse(String wire) {
        if (wire == null || wire.isBlank()) throw new IllegalArgumentException("wire");
        int split = wire.indexOf(':');
        if (split <= 0 || split >= wire.length() - 1) throw new IllegalArgumentException("wire");
        long settlementId = Long.parseLong(wire.substring(0, split));
        return new ConstructionRetryKey(settlementId, wire.substring(split + 1));
    }

    public long settlementId() { return settlementId; }
    public String intentKey() { return intentKey; }
    public String wire() { return settlementId + ":" + intentKey; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConstructionRetryKey that)) return false;
        return settlementId == that.settlementId && intentKey.equals(that.intentKey);
    }

    @Override public int hashCode() { return Long.hashCode(settlementId) * 31 + intentKey.hashCode(); }
    @Override public String toString() { return wire(); }
}
