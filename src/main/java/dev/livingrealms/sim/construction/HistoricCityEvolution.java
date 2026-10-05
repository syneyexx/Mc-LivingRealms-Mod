package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Wave 33 — settlement growth appends spatial memory; the old core remains.
 * Tier-up and planning must never erase completed construction keys.
 */
public final class HistoricCityEvolution {
    private HistoricCityEvolution() {}

    /** Immutable snapshot of completed keys — the city's built spatial memory. */
    public static Set<String> spatialMemory(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return Set.copyOf(settlement.completedConstruction());
    }

    /**
     * Pending intents for the current tier — append-only relative to completed keys.
     * Does not mutate or clear {@link Settlement#completedConstruction()}.
     */
    public static List<ConstructionIntent> appendPlan(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        Set<String> before = spatialMemory(settlement);
        List<ConstructionIntent> pending = SettlementPlanner.pending(faction, settlement);
        Set<String> after = spatialMemory(settlement);
        if (!before.equals(after)) {
            throw new IllegalStateException("appendPlan must not mutate completed construction keys");
        }
        return pending;
    }

    /**
     * Growth-layer label for a completed key based on ring hints encoded in road/keep keys
     * or historic-core default for early civic structures.
     */
    public static SettlementGrowthLayer layerOf(Settlement settlement, String key) {
        Objects.requireNonNull(settlement, "settlement");
        if (key == null || key.isBlank()) return SettlementGrowthLayer.HISTORIC_CORE;
        SettlementMorphology morph = SettlementMorphology.derive(settlement);
        // road:tier:index and wall/gate keys expand with tier — older completed keys stay as core memory.
        if (key.startsWith("road:") || key.startsWith("wall:") || key.startsWith("gate:") || key.startsWith("keep:")) {
            String[] parts = key.split(":");
            if (parts.length >= 2) {
                try {
                    int keyTier = Integer.parseInt(parts[1]);
                    int current = settlement.tier().ordinal();
                    int ring = Math.max(0, current - keyTier);
                    return SettlementGrowthLayer.forRing(ring, morph, current);
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        if (key.startsWith("house:")) {
            try {
                int index = Integer.parseInt(key.substring("house:".length()));
                int ring = Math.min(5, index / 12);
                return SettlementGrowthLayer.forRing(ring, morph, settlement.tier().ordinal());
            } catch (NumberFormatException ignored) {
                return SettlementGrowthLayer.EARLY_EXPANSION;
            }
        }
        return SettlementGrowthLayer.HISTORIC_CORE;
    }

    /** True when every key in {@code earlier} is still present after growth. */
    public static boolean preserves(Set<String> earlier, Settlement settlement) {
        Objects.requireNonNull(earlier, "earlier");
        Objects.requireNonNull(settlement, "settlement");
        return settlement.completedConstruction().containsAll(earlier);
    }

    /** Keys present in memory that belong to the historic core layer. */
    public static List<String> historicCoreKeys(Settlement settlement) {
        List<String> out = new ArrayList<>();
        for (String key : settlement.completedConstruction()) {
            if (layerOf(settlement, key) == SettlementGrowthLayer.HISTORIC_CORE) out.add(key);
        }
        return List.copyOf(out);
    }

    /** Copy helper for tests — LinkedHashSet preserves insertion order of completions. */
    public static Set<String> copyMemory(Settlement settlement) {
        return new LinkedHashSet<>(settlement.completedConstruction());
    }
}
