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
        List<ConstructionIntent> pending = new ArrayList<>(SettlementPlanner.pending(faction, settlement));
        pending.sort(Comparator.comparingInt(ConstructionIntent::priority).reversed().thenComparing(ConstructionIntent::key));

        int housesDone = (int) settlement.completedConstruction().stream().filter(k -> k.startsWith("house:")).count();
        int roadsDone = (int) settlement.completedConstruction().stream().filter(k -> k.startsWith("road:")).count();
        int civicDone = (int) settlement.completedConstruction().stream()
                .filter(k -> !(k.startsWith("house:") || k.startsWith("road:") || k.startsWith("farm:")))
                .count();

        int housesTarget = (int) pending.stream().filter(i -> i.role() == StructureRole.HOUSE).count() + housesDone;
        int roadsTarget = (int) pending.stream().filter(i -> i.role() == StructureRole.ROAD).count() + roadsDone;
        int civicTarget = (int) pending.stream()
                .filter(i -> i.role() != StructureRole.HOUSE && i.role() != StructureRole.ROAD && i.role() != StructureRole.FARM)
                .count() + civicDone;

        // Population-facing housing gap: each completed house intent is treated as ~8 capacity units
        // for deficit signalling (canonical housing stock remains Settlement#housing).
        int physicalHousingEstimate = housesDone * 8;
        int housingCapacityGap = Math.max(0, settlement.population() - Math.max(settlement.housing(), physicalHousingEstimate));

        int roadGap = Math.max(0, expectedRoads(settlement) - roadsDone);
        int civicGap = Math.max(0, expectedCivic(settlement) - civicDone);

        // Catch-up backlog: roads and housing first after large time jumps, then civic.
        List<ConstructionIntent> backlog = new ArrayList<>(pending);
        backlog.sort(Comparator
                .comparingInt((ConstructionIntent i) -> roleCatchupWeight(i.role()))
                .thenComparing(Comparator.comparingInt(ConstructionIntent::priority).reversed())
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
            case MARKET, WELL, TAVERN, TEMPLE -> 2;
            case WALL, GATE, KEEP -> 3;
            default -> 4;
        };
    }
}
