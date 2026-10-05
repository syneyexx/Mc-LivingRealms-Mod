package dev.livingrealms;

import dev.livingrealms.sim.construction.SettlementInfrastructure;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.player.PlayerActorIdentity;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.UUID;

/** Wave 5: player-founded camps plan TOWN_HALL, not an instant KEEP. */
public final class PlayerFoundedTownHallTest {
    private PlayerFoundedTownHallTest() {}

    public static void main(String[] args) {
        String actor = PlayerActorIdentity.of(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        SimulationState state = new SimulationState(0x544F574EL);
        var founded = PlayerSettlementFounder.found(state, actor, "Founder", "Chartercamp",
                new SimPosition(70_000, 70_000));
        check(founded.success(), founded.reason());
        var faction = state.findFaction(founded.factionId()).orElseThrow();
        var settlement = state.findSettlement(founded.settlementId()).orElseThrow();
        check(settlement.origin() == SettlementOrigin.PLAYER_FOUNDED, "origin");
        check(settlement.tier() == settlement.tier(), "camp tier");
        check(settlement.population() == PlayerSettlementFounder.FOUNDING_POPULATION, "small camp");

        var plan = SettlementPlanner.plan(faction, settlement);
        check(plan.stream().anyMatch(i -> i.role() == StructureRole.TOWN_HALL), "plans town hall");
        check(plan.stream().noneMatch(i -> i.role() == StructureRole.KEEP),
                "no keep at founding camp");

        // Functional town hall via registration capability (no forged LR receipt).
        check(!SettlementInfrastructure.hasFunctionalRole(state, settlement, StructureRole.TOWN_HALL),
                "no functional town hall yet");

        // Grow to TOWN — keep becomes available.
        settlement.addPopulation(400);
        settlement.addHousing(450);
        var townPlan = SettlementPlanner.plan(faction, settlement);
        check(settlement.tier().ordinal() >= dev.livingrealms.sim.faction.Settlement.Tier.TOWN.ordinal()
                        || townPlan.stream().anyMatch(i -> i.role() == StructureRole.TOWN_HALL),
                "town hall remains in progression");
        if (settlement.tier().ordinal() >= dev.livingrealms.sim.faction.Settlement.Tier.TOWN.ordinal()) {
            check(townPlan.stream().anyMatch(i -> i.role() == StructureRole.KEEP),
                    "keep unlocks at town+");
        }

        System.out.println("PASS PlayerFoundedTownHallTest: camp→town_hall, no instant keep");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
