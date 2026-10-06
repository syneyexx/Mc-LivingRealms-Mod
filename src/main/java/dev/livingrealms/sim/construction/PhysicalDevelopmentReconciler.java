package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Compares canonical settlement development targets with completed construction keys and produces
 * a prioritized physical catch-up backlog. Used after large day jumps ({@code /setday}) so abstract
 * population/economy growth cannot permanently outrun visible buildings and streets.
 *
 * <p>This is not a second planner: it consumes {@link SettlementPlanner} / economy intents and only
 * ranks what is still incomplete relative to current tier/population.
 */
public final class PhysicalDevelopmentReconciler {
    public record Deficit(
            Settlement settlement,
            Faction faction,
            int pendingCount,
            int housingCapacityGap,
            int roadGap,
            int civicGap,
            List<ConstructionIntent> backlog
    ) {
        public Deficit {
            Objects.requireNonNull(settlement, "settlement");
            Objects.requireNonNull(faction, "faction");
            Objects.requireNonNull(backlog, "backlog");
            backlog = List.copyOf(backlog);
        }
        public boolean needsCatchup() { return pendingCount > 0 || housingCapacityGap > 0; }
    }

    private PhysicalDevelopmentReconciler() {}

    public static Deficit analyze(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        SettlementDistrictPlan districts = SettlementDistrictPlan.derive(faction, settlement);
        List<ConstructionIntent> pending = new ArrayList<>(SettlementPlanner.pending(faction, settlement));
        pending.sort(Comparator.comparingInt(ConstructionIntent::priority).reversed().thenComparing(ConstructionIntent::key));

        List<ConstructionIntent> currentPlan = SettlementPlanCache.plan(faction, settlement);
        int housesDone = 0, roadsDone = 0, civicDone = 0;
        for (ConstructionIntent intent : currentPlan) {
            if (!settlement.isConstructionCompleted(intent.key())) continue;
            switch (intent.role()) {
                case HOUSE -> housesDone++;
                case ROAD -> roadsDone++;
                case FARM, PASTURE -> { }
                default -> civicDone++;
            }
        }

        // Population-facing housing gap uses density compression (cottage/townhouse/apartment).
        // Canonical housing stock remains Settlement#housing; this estimates visible capacity.
        int physicalHousingEstimate = 0;
        for (ConstructionIntent intent : currentPlan) {
            if (intent.role() != StructureRole.HOUSE) continue;
            if (!settlement.isConstructionCompleted(intent.key())) continue;
            physicalHousingEstimate += HousingCapacity.representedResidents(intent);
        }
        if (physicalHousingEstimate == 0 && housesDone > 0) physicalHousingEstimate = housesDone * 8;
        int housingCapacityGap = Math.max(0, settlement.population() - Math.max(settlement.housing(), physicalHousingEstimate));

        int roadGap = Math.max(0, expectedRoads(settlement) - roadsDone);
        int civicGap = Math.max(0, expectedCivic(settlement) - civicDone);

        // Catch-up backlog: roads and housing first after large time jumps, then civic.
        // Under housing pressure, denser house intents sort ahead of cottages. District anchors
        // give market/harbor/government a bounded priority nudge without relocating geometry.
        // Founder / grand-project / hero landmarks float to the front of the growth ring.
        boolean denseHousingPreferred = housingCapacityGap > 80 || settlement.tier().ordinal() >= Settlement.Tier.CITY.ordinal();
        SettlementCoreCompleteness.Status core = SettlementCoreCompleteness.analyze(faction, settlement);
        List<ConstructionIntent> backlog = new ArrayList<>(pending);
        backlog.sort(Comparator
                .comparingInt((ConstructionIntent i) -> settlement.priorityLandmarks().contains(i.key()) ? 0 : 1)
                .thenComparingInt((ConstructionIntent i) -> core.prioritizes(i) ? 0 : 1)
                .thenComparingInt((ConstructionIntent i) -> roleCatchupWeight(i.role()))
                .thenComparingInt((ConstructionIntent i) -> denseHousingPreferred && i.role() == StructureRole.HOUSE
                        ? -HousingCapacity.representedResidents(i) : 0)
                .thenComparing(Comparator.comparingInt((ConstructionIntent i) -> i.priority() + districts.priorityBoost(i)).reversed())
                .thenComparing(ConstructionIntent::key));

        return new Deficit(settlement, faction, pending.size(), housingCapacityGap, roadGap, civicGap, backlog);
    }

    public static List<Deficit> analyzeWorld(Iterable<Faction> factions) {
        Objects.requireNonNull(factions, "factions");
        List<Deficit> out = new ArrayList<>();
        for (Faction faction : factions) {
            for (Settlement settlement : faction.settlements()) {
                Deficit d = analyze(faction, settlement);
                if (d.needsCatchup()) out.add(d);
            }
        }
        out.sort(Comparator.comparingInt((Deficit d) -> d.housingCapacityGap() + d.pendingCount()).reversed());
        return List.copyOf(out);
    }

    /** How many construction intents to force-discover per settlement during a simulated day jump. */
    public static int catchupIntentsPerSettlement(long simulatedDays, Deficit deficit) {
        if (deficit == null || !deficit.needsCatchup()) return 1;
        long days = Math.max(0, simulatedDays);
        int base = 1 + (int) Math.min(8L, days / 12L);
        if (deficit.housingCapacityGap() > 80) base += 2;
        if (deficit.roadGap() > 4) base += 1;
        return Math.min(12, base);
    }

    private static int expectedRoads(Settlement settlement) {
        return switch (settlement.tier()) {
            case CAMP -> 1;
            case HAMLET -> 4;
            case VILLAGE -> 8;
            case TOWN -> 14;
            case CITY -> 20;
            case METROPOLIS -> 28;
        };
    }

    private static int expectedCivic(Settlement settlement) {
        return switch (settlement.tier()) {
            case CAMP -> 1;
            case HAMLET -> 2;
            case VILLAGE -> 6;
            case TOWN -> 12;
            case CITY -> 18;
            case METROPOLIS -> 24;
        };
    }

    private static int roleCatchupWeight(StructureRole role) {
        return switch (role) {
            case ROAD -> 0;
            case HOUSE -> 1;
            case MARKET, PLAZA, WELL, TAVERN, TEMPLE, TOWN_HALL -> 2;
            case WALL, GATE, KEEP -> 3;
            default -> 4;
        };
    }
}
