package dev.livingrealms.sim.world;

import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.social.CitizenMemory;
import dev.livingrealms.sim.social.CitizenRelationship;
import dev.livingrealms.sim.social.FamilyBond;
import dev.livingrealms.sim.social.MemoryType;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.underworld.UnderworldActions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wave 28 — retention / compaction policies for bounded collections.
 *
 * <p>Intentional retention (never deleted meaninglessly):
 * <ul>
 *   <li>Completed construction keys — spatial city truth; historic cores persist across tier growth.</li>
 *   <li>Active justice cases, open underworld contracts, in-flight shipments, unrecovered caches.</li>
 *   <li>High-importance citizen memories and non-snapshot history events (wars, battles, legends hooks).</li>
 * </ul>
 *
 * <p>Summarization preferred over silent deletion for monthly_snapshot history clusters.
 * Compaction must not change deterministic outcomes of active gameplay engines — it only
 * removes or folds inactive / decorative archival rows.
 */
public final class StateRetentionCompactor {
    /** Keep inactive justice cases this many days after open before pruning. */
    public static final int JUSTICE_INACTIVE_RETENTION_DAYS = 120;
    /** Keep recovered caches this many days after creation before pruning (if recovered). */
    public static final int CACHE_RECOVERED_RETENTION_DAYS = 90;
    /** Soft cap for history before summarizing old monthly snapshots. */
    public static final int HISTORY_SOFT_CAP = 6_000;
    /** Citizen memories older than this with low importance may fold into summaries. */
    public static final int MEMORY_SOFT_AGE_DAYS = 60;
    public static final double MEMORY_FOLD_IMPORTANCE = 0.35;

    public record Report(
            int historySummarized,
            int memoriesFolded,
            int justicePruned,
            int cachesPruned,
            int shipmentsPruned,
            int contractsPruned
    ) {
        public int totalRemoved() {
            return historySummarized + memoriesFolded + justicePruned + cachesPruned + shipmentsPruned + contractsPruned;
        }
    }

    private StateRetentionCompactor() {}

    /** Monthly light pass: prune closed inactive rows; fold low-value conversation noise. */
    public static Report compactMonthly(SimulationState state) {
        Objects.requireNonNull(state, "state");
        long day = state.clock().day();
        int justice = state.pruneInactiveJusticeCases(Math.max(0, day - JUSTICE_INACTIVE_RETENTION_DAYS));
        int caches = state.pruneRecoveredHiddenCaches(Math.max(0, day - CACHE_RECOVERED_RETENTION_DAYS));
        int shipments = pruneStaleShipments(state);
        int contracts = UnderworldActions.pruneClosedContracts(state);
        int memories = compactCitizenMemories(state, day, false);
        int history = 0;
        if (state.history().size() > HISTORY_SOFT_CAP) {
            history = summarizeOldMonthlySnapshots(state.history(), day);
        }
        return new Report(history, memories, justice, caches, shipments, contracts);
    }

    /**
     * Quarterly deeper pass: summarize older monthly_snapshot clusters even below soft cap,
     * and fold aged low-importance citizen memories into short summaries.
     */
    public static Report compactQuarterly(SimulationState state) {
        Objects.requireNonNull(state, "state");
        long day = state.clock().day();
        Report light = compactMonthly(state);
        int history = summarizeOldMonthlySnapshots(state.history(), day);
        // Second history pass with a nearer cutoff when still over soft cap.
        if (state.history().size() > HISTORY_SOFT_CAP) {
            history += summarizeOldMonthlySnapshots(state.history(), day);
        }
        int memories = compactCitizenMemories(state, day, true);
        // Deep pass may leave citizens still near MAX_MEMORIES — fold conversation noise harder.
        memories += compactCitizenMemories(state, day, true);
        return new Report(
                light.historySummarized() + history,
                light.memoriesFolded() + memories,
                light.justicePruned(),
                light.cachesPruned(),
                light.shipmentsPruned(),
                light.contractsPruned());
    }

    /**
     * TOTAL-loss shipments that somehow linger are removed; arrived shipments are already
     * cleared by {@code TradeEngine}. Active in-flight shipments are retained.
     */
    static int pruneStaleShipments(SimulationState state) {
        List<Long> remove = new ArrayList<>();
        for (TradeShipment s : state.shipments()) {
            if (s.lossState() == TradeShipment.LossState.TOTAL && s.arrived()) {
                remove.add(s.id());
            }
        }
        for (Long id : remove) state.removeShipment(id);
        return remove.size();
    }

    /**
     * Fold clusters of old {@code monthly_snapshot} events into one era summary per 90-day window.
     * Non-snapshot history (wars, battles, civic events, etc.) is always retained.
     */
    public static int summarizeOldMonthlySnapshots(WorldHistory history, long day) {
        Objects.requireNonNull(history, "history");
        List<WorldEvent> all = new ArrayList<>(history.all());
        if (all.isEmpty()) return 0;
        long cutoff = Math.max(0, day - 180);
        List<WorldEvent> keep = new ArrayList<>();
        Map<Long, List<WorldEvent>> buckets = new LinkedHashMap<>();
        int folded = 0;
        for (WorldEvent e : all) {
            if (!"monthly_snapshot".equals(e.type()) || e.day() >= cutoff) {
                keep.add(e);
                continue;
            }
            long bucket = e.day() / 90;
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(e);
        }
        for (var entry : buckets.entrySet()) {
            List<WorldEvent> cluster = entry.getValue();
            if (cluster.size() <= 1) {
                keep.addAll(cluster);
                continue;
            }
            WorldEvent first = cluster.getFirst();
            WorldEvent last = cluster.getLast();
            folded += cluster.size() - 1;
            keep.add(new WorldEvent(
                    last.day(),
                    "era_summary",
                    "days=" + first.day() + ".." + last.day()
                            + ", snapshots=" + cluster.size()
                            + ", last=" + truncate(last.message(), 180)));
        }
        keep.sort(Comparator.comparingLong(WorldEvent::day).thenComparing(WorldEvent::type).thenComparing(WorldEvent::message));
        // Prefer keeping important types if still over max after rebuild.
        history.replaceAll(keep);
        return folded;
    }

    static int compactCitizenMemories(SimulationState state, long day, boolean deep) {
        int folded = 0;
        for (SocialCitizen citizen : state.socialCitizens()) {
            if (!citizen.alive()) {
                folded += compactDeceasedCitizenMemories(citizen);
                compactDeceasedCitizenRelationships(citizen);
                continue;
            }
            folded += compactOneCitizen(citizen, day, deep);
        }
        return folded;
    }

    /**
     * Dead named citizens remain canonical identities, but their full conversational working set
     * is no longer active gameplay state. Preserve the four most important memories and fold the
     * rest into one deterministic legacy summary. This bounds multi-generation saves without
     * deleting the person, family links, role, appearance or core identity.
     */
    static int compactDeceasedCitizenMemories(SocialCitizen citizen) {
        List<CitizenMemory> memories = new ArrayList<>(citizen.memories());
        final int detailedKeep = 4;
        if (memories.size() <= detailedKeep + 1) return 0;

        memories.sort(Comparator
                .comparingDouble(CitizenMemory::importance).reversed()
                .thenComparing(Comparator.comparingLong(CitizenMemory::day).reversed())
                .thenComparing(CitizenMemory::subjectKey)
                .thenComparing(CitizenMemory::sourceKey));
        List<CitizenMemory> keep = new ArrayList<>(memories.subList(0, detailedKeep));
        List<CitizenMemory> foldedRows = new ArrayList<>(memories.subList(detailedKeep, memories.size()));
        CitizenMemory sample = foldedRows.stream()
                .max(Comparator.comparingLong(CitizenMemory::day)
                        .thenComparingDouble(CitizenMemory::importance)
                        .thenComparing(CitizenMemory::subjectKey))
                .orElseThrow();
        keep.add(new CitizenMemory(
                sample.day(),
                MemoryType.LOCAL_EVENT,
                "memory-legacy:" + citizen.id(),
                "self",
                "A lifetime leaves " + foldedRows.size() + " older memories summarized around "
                        + truncate(sample.summary(), 72) + ".",
                sample.position(),
                Math.min(0.75, Math.max(0.45, sample.importance())),
                Math.max(0.4, sample.confidence() * 0.9)));
        keep.sort(Comparator.comparingLong(CitizenMemory::day)
                .thenComparing(CitizenMemory::subjectKey)
                .thenComparing(CitizenMemory::sourceKey));
        citizen.replaceMemories(keep);
        return foldedRows.size();
    }

    /**
     * Dead identities retain every explicit family bond plus at most two strongest non-family ties.
     * Active relationship graphs are untouched.
     */
    static int compactDeceasedCitizenRelationships(SocialCitizen citizen) {
        List<CitizenRelationship> relationships = new ArrayList<>(citizen.relationships().values());
        if (relationships.size() <= 4) return 0;

        List<CitizenRelationship> family = relationships.stream()
                .filter(r -> r.familyBond() != FamilyBond.NONE)
                .sorted(Comparator.comparing(CitizenRelationship::targetKey))
                .toList();
        List<CitizenRelationship> social = relationships.stream()
                .filter(r -> r.familyBond() == FamilyBond.NONE)
                .sorted(Comparator
                        .comparingDouble(StateRetentionCompactor::relationshipStrength).reversed()
                        .thenComparing(CitizenRelationship::targetKey))
                .limit(2)
                .toList();

        List<CitizenRelationship> keep = new ArrayList<>(family.size() + social.size());
        keep.addAll(family);
        keep.addAll(social);
        keep.sort(Comparator.comparing(CitizenRelationship::targetKey));
        int removed = Math.max(0, relationships.size() - keep.size());
        if (removed > 0) citizen.replaceRelationships(keep);
        return removed;
    }

    private static double relationshipStrength(CitizenRelationship relationship) {
        return Math.max(
                Math.max(relationship.friendship(), relationship.hostility()),
                Math.max(Math.max(relationship.romance(), relationship.rivalry()),
                        Math.abs(relationship.trust() - 0.5) * 2.0));
    }

    static int compactOneCitizen(SocialCitizen citizen, long day, boolean deep) {
        List<CitizenMemory> memories = new ArrayList<>(citizen.memories());
        if (memories.size() < (deep ? SocialCitizen.MAX_MEMORIES / 3 : SocialCitizen.MAX_MEMORIES / 2) && !deep) return 0;
        if (deep && memories.size() < 8) return 0;
        List<CitizenMemory> keep = new ArrayList<>();
        List<CitizenMemory> foldable = new ArrayList<>();
        long ageCutoff = Math.max(0, day - (deep ? MEMORY_SOFT_AGE_DAYS / 2 : MEMORY_SOFT_AGE_DAYS));
        for (CitizenMemory m : memories) {
            boolean oldNoise = m.day() < ageCutoff
                    && m.importance() < (deep ? MEMORY_FOLD_IMPORTANCE + 0.08 : MEMORY_FOLD_IMPORTANCE)
                    && (m.type() == MemoryType.CONVERSATION || m.type() == MemoryType.RUMOR
                    || (deep && m.type() == MemoryType.LOCAL_EVENT && m.importance() < 0.4));
            if (oldNoise) foldable.add(m);
            else keep.add(m);
        }
        int softMax = deep ? Math.max(16, SocialCitizen.MAX_MEMORIES * 2 / 3) : SocialCitizen.MAX_MEMORIES;
        if (foldable.isEmpty() && keep.size() <= softMax) return 0;
        int folded = foldable.size();
        if (!foldable.isEmpty()) {
            // Important history may summarize rather than delete meaninglessly.
            CitizenMemory sample = foldable.getLast();
            keep.add(new CitizenMemory(
                    sample.day(),
                    MemoryType.LOCAL_EVENT,
                    "memory-summary:" + citizen.id(),
                    "self",
                    "I retain a faded sense of " + foldable.size()
                            + " older small matters around " + truncate(sample.summary(), 80) + ".",
                    sample.position(),
                    Math.min(0.45, sample.importance() + 0.08),
                    Math.max(0.35, sample.confidence() * 0.85)));
        }
        // Soft-cap pressure: when still over softMax (all memories "important"/recent),
        // fold oldest low-importance rows into one summary instead of silent drop.
        keep.sort(Comparator.comparingLong(CitizenMemory::day).thenComparing(CitizenMemory::subjectKey)
                .thenComparingDouble(CitizenMemory::importance));
        if (keep.size() > softMax) {
            int excess = keep.size() - softMax + 1; // +1 room for the summary row
            List<CitizenMemory> force = new ArrayList<>(keep.subList(0, Math.min(excess, keep.size())));
            keep.subList(0, force.size()).clear();
            CitizenMemory sample = force.getLast();
            keep.add(0, new CitizenMemory(
                    sample.day(),
                    MemoryType.LOCAL_EVENT,
                    "memory-era:" + citizen.id(),
                    "self",
                    "Older chapters of my life blur together — about " + force.size()
                            + " matters near " + truncate(sample.summary(), 80) + ".",
                    sample.position(),
                    Math.min(0.5, sample.importance() + 0.05),
                    Math.max(0.3, sample.confidence() * 0.8)));
            folded += force.size();
            keep.sort(Comparator.comparingLong(CitizenMemory::day).thenComparing(CitizenMemory::subjectKey));
        }
        while (keep.size() > softMax) keep.removeFirst();
        citizen.replaceMemories(keep);
        return folded;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** Construction completion keys are intentionally retained — documented no-op for audits. */
    public static int constructionRecordsRetained(SimulationState state) {
        Objects.requireNonNull(state, "state");
        int total = 0;
        for (var faction : state.factions()) {
            for (var settlement : faction.settlements()) {
                total += settlement.completedConstruction().size();
            }
        }
        return total;
    }
}
