package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.SettlementCoreCompleteness;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-fast physical contract for one day-zero starter settlement plan.
 *
 * <p>Worldgen must never silently downgrade a city into a handful of surviving pieces. This
 * validator runs before any chunk indexes the plan and guarantees that the immutable plan contains
 * the minimum recognizable fabric for its tier. Terrain/block placement is a later phase; this
 * contract protects planner completeness and gate/street topology.</p>
 */
public final class StarterSettlementWorldgenContract {
    public record Report(
            long settlementId,
            String settlementName,
            int roads,
            int houses,
            int farms,
            int walls,
            int gates,
            int gateApproaches,
            int connectedGates,
            List<String> problems
    ) {
        public Report {
            if (settlementId <= 0) throw new IllegalArgumentException("settlementId");
            settlementName = Objects.requireNonNull(settlementName, "settlementName");
            problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        }

        public boolean complete() {
            return problems.isEmpty();
        }
    }

    private StarterSettlementWorldgenContract() {}

    public static Report inspect(SettlementInitialWorldgenPlan plan) {
        Objects.requireNonNull(plan, "plan");
        SettlementCoreCompleteness.Contract contract =
                SettlementCoreCompleteness.contract(plan.tier());

        Map<StructureRole, Integer> counts = new EnumMap<>(StructureRole.class);
        Set<String> keys = new HashSet<>();
        List<ConstructionIntent> gateIntents = new ArrayList<>();
        List<ConstructionIntent> gateRoads = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (ConstructionIntent intent : plan.intents()) {
            if (!keys.add(intent.key())) {
                problems.add("duplicate intent key " + intent.key());
            }
            counts.merge(intent.role(), 1, Integer::sum);
            if (intent.role() == StructureRole.GATE) gateIntents.add(intent);
            if (intent.role() == StructureRole.ROAD && intent.key().startsWith("roadgraph:gate:")) {
                gateRoads.add(intent);
            }
            if (intent.role() == StructureRole.ROAD && intent.hasPath() && intent.path().size() < 2) {
                problems.add("road has fewer than two path points: " + intent.key());
            }
        }

        int roads = count(counts, StructureRole.ROAD);
        int houses = count(counts, StructureRole.HOUSE);
        int farms = count(counts, StructureRole.FARM);
        int walls = count(counts, StructureRole.WALL);
        int gates = count(counts, StructureRole.GATE);

        requireAtLeast(problems, "roads", roads, contract.minRoads());
        requireAtLeast(problems, "houses", houses, contract.minHouses());
        requireAtLeast(problems, "farms", farms, contract.minFarms());
        requireAtLeast(problems, "gates", gates, contract.minGates());

        if (contract.requireWater() && count(counts, StructureRole.WELL) == 0) {
            problems.add("missing well");
        }
        if (contract.requireMarket() && count(counts, StructureRole.MARKET) == 0) {
            problems.add("missing market");
        }
        if (contract.requirePlaza() && count(counts, StructureRole.PLAZA) == 0) {
            problems.add("missing plaza");
        }
        if (contract.requireGovernment()
                && count(counts, StructureRole.KEEP) == 0
                && count(counts, StructureRole.TOWN_HALL) == 0) {
            problems.add("missing government anchor");
        }
        if (contract.requireBoundary()) {
            if (walls < 4) problems.add("boundary has fewer than four wall runs");
            if (gates < 4) problems.add("boundary has fewer than four gates");
        }
        if (contract.requireExternalRoad() && gateRoads.size() < contract.minGates()) {
            problems.add("missing gate approach roads: "
                    + gateRoads.size() + "/" + contract.minGates());
        }

        int connectedGates = 0;
        for (ConstructionIntent gate : gateIntents) {
            if (touchesAnyGateRoad(gate.center(), gateRoads)) connectedGates++;
        }
        if (contract.requireExternalRoad() && connectedGates < contract.minGates()) {
            problems.add("gates not connected to settlement streets: "
                    + connectedGates + "/" + contract.minGates());
        }

        return new Report(
                plan.settlementId(),
                plan.settlementName(),
                roads,
                houses,
                farms,
                walls,
                gates,
                gateRoads.size(),
                connectedGates,
                problems);
    }

    public static void requireComplete(SettlementInitialWorldgenPlan plan) {
        Report report = inspect(plan);
        if (report.complete()) return;
        throw new IllegalStateException(
                "Incomplete starter worldgen plan for "
                        + report.settlementName() + " (" + report.settlementId() + "): "
                        + String.join("; ", report.problems()));
    }

    private static int count(Map<StructureRole, Integer> counts, StructureRole role) {
        return counts.getOrDefault(role, 0);
    }

    private static void requireAtLeast(
            List<String> problems, String label, int actual, int minimum) {
        if (actual < minimum) problems.add(label + " " + actual + "/" + minimum);
    }

    private static boolean touchesAnyGateRoad(
            SimPosition gate,
            List<ConstructionIntent> gateRoads) {
        for (ConstructionIntent road : gateRoads) {
            if (!road.hasPath()) continue;
            for (SimPosition point : road.path()) {
                if (gate.distanceTo(point) <= 1.5) return true;
            }
        }
        return false;
    }
}
