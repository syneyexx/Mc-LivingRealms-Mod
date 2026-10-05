package dev.livingrealms.sim.persistence;

import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Wave 29 — measures canonical save payload size and identifies dominant contributors
 * via structural counts (history, citizens, shipments, construction keys, etc.).
 */
public final class SaveSizeAuditor {
    /** Soft advisory ceiling for a healthy long-run world (hard cap remains {@link SimulationStateCodec#MAX_STATE_BYTES}). */
    public static final int ADVISORY_SOFT_BYTES = 24 * 1024 * 1024;

    public record Contributor(String name, long estimateBytes, int count) {}

    public record Report(
            long day,
            int totalBytes,
            List<Contributor> contributors,
            String dominantContributor
    ) {
        public String documentLine() {
            return String.format(Locale.ROOT,
                    "day=%d totalBytes=%d softCap=%d hardCap=%d dominant=%s top=[%s]",
                    day, totalBytes, ADVISORY_SOFT_BYTES, SimulationStateCodec.MAX_STATE_BYTES,
                    dominantContributor,
                    contributors.stream().limit(5)
                            .map(c -> c.name() + "≈" + c.estimateBytes() + "B×" + c.count())
                            .reduce((a, b) -> a + "; " + b).orElse(""));
        }
    }

    private SaveSizeAuditor() {}

    public static Report measure(SimulationState state) {
        Objects.requireNonNull(state, "state");
        byte[] encoded = SimulationStateCodec.encode(state);
        Map<String, Contributor> raw = new LinkedHashMap<>();

        int history = state.history().size();
        add(raw, "history_events", history, 48 + 64); // day+type+message estimate

        int citizens = state.socialCitizens().size();
        int memories = state.socialCitizens().stream().mapToInt(c -> c.memories().size()).sum();
        add(raw, "social_citizens", citizens, 220);
        add(raw, "citizen_memories", memories, 96);

        int shipments = state.shipments().size();
        add(raw, "shipments", shipments, 96);

        int justice = state.justiceCases().size();
        add(raw, "justice_cases", justice, 64);

        int contracts = state.underworldContracts().size();
        add(raw, "underworld_contracts", contracts, 80);

        int caches = state.hiddenCaches().size();
        add(raw, "hidden_caches", caches, 72);

        int legends = state.legends().size();
        add(raw, "legends", legends, 120);

        int constructionKeys = 0;
        int factions = state.factions().size();
        int settlements = 0;
        for (var f : state.factions()) {
            settlements += f.settlements().size();
            for (var s : f.settlements()) constructionKeys += s.completedConstruction().size();
        }
        add(raw, "factions", factions, 180);
        add(raw, "settlements", settlements, 240);
        add(raw, "construction_keys", constructionKeys, 28);

        int households = state.households().size();
        add(raw, "households", households, 64);

        int regions = state.regions().size();
        int groups = state.regions().stream().mapToInt(r -> r.populations().size()).sum();
        add(raw, "ecology_regions", regions, 80);
        add(raw, "population_groups", groups, 48);

        List<Contributor> ranked = new ArrayList<>(raw.values());
        ranked.sort(Comparator.comparingLong(Contributor::estimateBytes).reversed()
                .thenComparing(Contributor::name));
        String dominant = ranked.isEmpty() ? "none" : ranked.getFirst().name();
        return new Report(state.clock().day(), encoded.length, List.copyOf(ranked), dominant);
    }

    private static void add(Map<String, Contributor> into, String name, int count, int bytesEach) {
        into.put(name, new Contributor(name, (long) Math.max(0, count) * bytesEach, Math.max(0, count)));
    }
}
