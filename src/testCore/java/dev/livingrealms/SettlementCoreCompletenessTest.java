package dev.livingrealms;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.SettlementCoreCompleteness;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.SimPosition;
import java.util.List;

/** Tier-specific physical identity completion regression. */
public final class SettlementCoreCompletenessTest {
    private SettlementCoreCompletenessTest() {}

    public static void main(String[] args) {
        hamletContract();
        cityRequiresFullBoundaryAndGateRoads();
        System.out.println("PASS settlement core completeness: tier identity + full CITY boundary/gate approaches");
    }

    private static void hamletContract() {
        Faction faction = new Faction(81, "Hamlet Realm", "Reeve");
        Settlement hamlet = new Settlement(811, "Little Fold", new SimPosition(0, 0), 70, 90,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.HAMLET);
        faction.addSettlement(hamlet);
        var initial = SettlementCoreCompleteness.analyze(faction, hamlet);
        check(!initial.complete(), "fresh hamlet cannot be core-complete without receipts");
        check(initial.roadGap() == 1 && initial.houseGap() == 2 && initial.farmGap() == 1,
                "hamlet minimum counts");
        check(initial.missingRoles().contains(StructureRole.WELL), "hamlet requires water");

        List<ConstructionIntent> plan = SettlementPlanner.plan(faction, hamlet);
        completeFirst(hamlet, plan, StructureRole.ROAD, 1);
        completeFirst(hamlet, plan, StructureRole.HOUSE, 2);
        completeFirst(hamlet, plan, StructureRole.FARM, 1);
        completeFirst(hamlet, plan, StructureRole.WELL, 1);
        check(SettlementCoreCompleteness.analyze(faction, hamlet).complete(),
                "hamlet must complete with path + housing + food + water");
    }

    private static void cityRequiresFullBoundaryAndGateRoads() {
        Faction faction = new Faction(82, "City Realm", "Queen");
        Settlement city = new Settlement(821, "Gate City", new SimPosition(0, 0), 4200, 5000,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.CAPITAL);
        faction.addSettlement(city);
        faction.addSettlement(new Settlement(822, "West Town", new SimPosition(-900, 50), 850, 930,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.TOWN));
        faction.addSettlement(new Settlement(823, "East Town", new SimPosition(920, -40), 780, 860,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.TOWN));

        List<ConstructionIntent> plan = SettlementPlanner.plan(faction, city);
        check(plan.stream().filter(i -> i.role() == StructureRole.GATE).count() == 4, "four city gates");
        check(plan.stream().filter(i -> i.role() == StructureRole.ROAD && i.key().startsWith("roadgraph:gate:")).count() == 4,
                "four graph gate approaches");

        for (ConstructionIntent intent : plan) {
            if (intent.role() == StructureRole.WALL) continue;
            city.markConstructionCompleted(intent.key());
        }
        var noWalls = SettlementCoreCompleteness.analyze(faction, city);
        check(!noWalls.complete() && noWalls.missingRoles().contains(StructureRole.WALL),
                "city without curtain wall must remain incomplete");

        for (ConstructionIntent intent : plan) {
            if (intent.role() == StructureRole.WALL) city.markConstructionCompleted(intent.key());
        }
        var complete = SettlementCoreCompleteness.analyze(faction, city);
        check(complete.complete(), "fully receipted city core should be complete");

        ConstructionIntent optional = plan.stream()
                .filter(i -> i.role() == StructureRole.OBSERVATORY || i.role() == StructureRole.MONUMENT)
                .findFirst().orElse(null);
        if (optional != null) check(!complete.prioritizes(optional), "optional detail must not remain core-priority");
    }

    private static void completeFirst(Settlement settlement, List<ConstructionIntent> plan,
                                      StructureRole role, int count) {
        int done = 0;
        for (ConstructionIntent intent : plan) {
            if (intent.role() != role) continue;
            settlement.markConstructionCompleted(intent.key());
            if (++done >= count) return;
        }
        throw new AssertionError("not enough planned " + role + " intents: wanted " + count + " got " + done);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
