package dev.livingrealms.sim.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Structured, rate-limited failure reporting (Wave 35). */
public final class StructuredErrorReporter {
    public record Context(String subsystem, String operation, String recovery, long canonicalDay, long settlementId, long factionId) {
        public Context {
            subsystem = safe(subsystem, "unknown");
            operation = safe(operation, "unknown");
            recovery = safe(recovery, "none");
        }
        public static Context of(String subsystem, String operation, String recovery) {
            return new Context(subsystem, operation, recovery, -1L, 0L, 0L);
        }
        public Context withDay(long day) { return new Context(subsystem, operation, recovery, day, settlementId, factionId); }
        public Context withSettlement(long id) { return new Context(subsystem, operation, recovery, canonicalDay, id, factionId); }
        public Context withFaction(long id) { return new Context(subsystem, operation, recovery, canonicalDay, settlementId, id); }
        public String fingerprint(Throwable error) {
            String type = error == null ? "null" : error.getClass().getName();
            String msg = error == null || error.getMessage() == null ? "" : error.getMessage();
            return subsystem + "|" + operation + "|" + type + "|" + msg;
        }
        public String format(Throwable error) {
            StringBuilder sb = new StringBuilder(160);
            sb.append('[').append(subsystem).append("] op=").append(operation);
            if (canonicalDay >= 0) sb.append(" day=").append(canonicalDay);
            if (settlementId > 0) sb.append(" settlement=").append(settlementId);
            if (factionId > 0) sb.append(" faction=").append(factionId);
            sb.append(" recovery=").append(recovery);
            if (error != null) {
                sb.append(" error=").append(error.getClass().getSimpleName());
                if (error.getMessage() != null && !error.getMessage().isBlank()) sb.append(": ").append(error.getMessage());
            }
            return sb.toString();
        }
        private static String safe(String v, String fallback) {
            return v == null || v.isBlank() ? fallback : v.trim().toLowerCase(Locale.ROOT);
        }
    }

    private final ConcurrentHashMap<String, Long> lastLogTick = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> suppressed = new ConcurrentHashMap<>();
    private final long minTicksBetweenIdentical;
    private final Consumer<String> sink;

    public StructuredErrorReporter(long minTicksBetweenIdentical, Consumer<String> sink) {
        if (minTicksBetweenIdentical < 1) throw new IllegalArgumentException("minTicksBetweenIdentical");
        this.minTicksBetweenIdentical = minTicksBetweenIdentical;
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public boolean report(long tick, Context context, Throwable error) {
        Objects.requireNonNull(context, "context");
        String key = context.fingerprint(error);
        Long previous = lastLogTick.get(key);
        if (previous != null && tick >= previous && tick - previous < minTicksBetweenIdentical) {
            suppressed.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
            return false;
        }
        long skipped = 0L;
        AtomicLong counter = suppressed.remove(key);
        if (counter != null) skipped = counter.get();
        lastLogTick.put(key, tick);
        String message = context.format(error);
        if (skipped > 0) message = message + " (suppressedRepeats=" + skipped + ")";
        sink.accept(message);
        return true;
    }

    public void clear() { lastLogTick.clear(); suppressed.clear(); }
    public int trackedFingerprints() { return lastLogTick.size(); }
}
