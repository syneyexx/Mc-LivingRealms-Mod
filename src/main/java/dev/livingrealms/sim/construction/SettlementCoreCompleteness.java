package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tier-specific physical identity contract for a settlement core.
 *
 * <p>This does not make construction canonical; {@link SettlementPlanner} remains the plan source.
 * It only evaluates completion receipts and identifies which pending intents belong to the minimum
 * recognizable settlement fabric that should be reconciled before optional expansion/detail.</p>
 */
public final class SettlementCoreCompleteness {
    public record Contract(
            int minRoads,
            int minHouses,
            int minFarms,
            int minGates,
            boolean requireWater,
            boolean requireMarket,
            boolean requirePlaza,
            boolean requireGovernment,
            boolean requireBoundary,
            boolean requireExternalRoad
    ) {
        public Contract {
            if (minRoads < 0 || minHouses < 0 || minFarms < 0 || minGates < 0) {
                throw new IllegalArgumentException("negative core minimum");
            }
        }
    }

    public record Status(
            Contract contract,
            int roadGap,
            int houseGap,
            int farmGap,
            int gateGap,
            int externalRoadGap,
            Set<StructureRole> missingRoles,
            boolean complete
    ) {
        public Status {
            Objects.requireNonNull(contract, "contract");
            missingRoles = Set.copyOf(Objects.requireNonNull(missingRoles, "missingRoles"));
        }

        /** True when this pending intent directly closes a current core-completeness deficit. */
        public boolean prioritizes(ConstructionIntent intent) {
            Objects.requireNonNull(intent, "intent");
            if (complete) return false;
            return switch (intent.role()) {
                case ROAD -> roadGap > 0 || (externalRoadGap > 0 && intent.key().startsWith("roadgraph:gate:"));
                case HOUSE -> houseGap > 0;
                case FARM -> farmGap > 0;
                case GATE -> gateGap > 0 || missingRoles.contains(StructureRole.GATE);
                case WALL -> missingRoles.contains(StructureRole.WALL);
                default -> missingRoles.contains(intent.role());
            };
        }
    }

    private SettlementCoreCompleteness() {}

    public static Contract contract(Settlement.Tier tier) {
        Objects.requireNonNull(tier, "tier");
        return switch (tier) {
            case CAMP -> new Contract(1, 1, 0, 0, false, false, false, false, false, false);
            case HAMLET -> new Contract(1, 2, 1, 0, true, false, false, false, false, false);
            case VILLAGE -> new Contract(2, 4, 1, 0, true, true, false, false, false, false);
            case TOWN -> new Contract(4, 8, 1, 0, true, true, true, true, false, false);
            case CITY -> new Contract(6, 16, 1, 4, true, true, true, true, true, true);
            case METROPOLIS -> new Contract(8, 24, 1, 4, true, true, true, true, true, true);
        };
    }

    public static Status analyze(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        Contract contract = contract(settlement.tier());
        List<ConstructionIntent> plan = SettlementPlanCache.plan(faction, settlement);

        Map<StructureRole, Integer> completedByRole = new EnumMap<>(StructureRole.class);
        int externalRoads = 0;
        for (ConstructionIntent intent : plan) {
            if (!settlement.isConstructionCompleted(intent.key())) continue;
            completedByRole.merge(intent.role(), 1, Integer::sum);
            if (intent.role() == StructureRole.ROAD && intent.key().startsWith("roadgraph:gate:")) externalRoads++;
        }

        int roads = completedByRole.getOrDefault(StructureRole.ROAD, 0);
        int houses = completedByRole.getOrDefault(StructureRole.HOUSE, 0);
        int farms = completedByRole.getOrDefault(StructureRole.FARM, 0);
        int gates = completedByRole.getOrDefault(StructureRole.GATE, 0);

        EnumSet<StructureRole> missing = EnumSet.noneOf(StructureRole.class);
        if (contract.requireWater() && completedByRole.getOrDefault(StructureRole.WELL, 0) == 0) missing.add(StructureRole.WELL);
        if (contract.requireMarket() && completedByRole.getOrDefault(StructureRole.MARKET, 0) == 0) missing.add(StructureRole.MARKET);
        if (contract.requirePlaza() && completedByRole.getOrDefault(StructureRole.PLAZA, 0) == 0) missing.add(StructureRole.PLAZA);
        if (contract.requireGovernment()
                && completedByRole.getOrDefault(StructureRole.KEEP, 0) == 0
                && completedByRole.getOrDefault(StructureRole.TOWN_HALL, 0) == 0) {
            // Both satisfy the government anchor; KEEP is the canonical missing-role signal.
            missing.add(StructureRole.KEEP);
            missing.add(StructureRole.TOWN_HALL);
        }
        if (contract.requireBoundary() && completedByRole.getOrDefault(StructureRole.WALL, 0) == 0) {
            missing.add(StructureRole.WALL);
        }
        if (contract.minGates() > gates) missing.add(StructureRole.GATE);

        int roadGap = Math.max(0, contract.minRoads() - roads);
        int houseGap = Math.max(0, contract.minHouses() - houses);
        int farmGap = Math.max(0, contract.minFarms() - farms);
        int gateGap = Math.max(0, contract.minGates() - gates);
        int externalGap = contract.requireExternalRoad() && externalRoads == 0 ? 1 : 0;

        boolean complete = roadGap == 0 && houseGap == 0 && farmGap == 0 && gateGap == 0
                && externalGap == 0 && missing.isEmpty();
        return new Status(contract, roadGap, houseGap, farmGap, gateGap, externalGap, missing, complete);
    }
}
