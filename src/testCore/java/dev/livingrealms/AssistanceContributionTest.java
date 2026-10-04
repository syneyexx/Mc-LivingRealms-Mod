package dev.livingrealms;

import dev.livingrealms.sim.civilization.AssistanceContributionEngine;
import dev.livingrealms.sim.civilization.AssistanceTask;
import dev.livingrealms.sim.civilization.AssistanceTaskStatus;
import dev.livingrealms.sim.civilization.AssistanceTaskType;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.validation.SimulationValidator;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Verifies player aid mutates canonical settlement pressure and cannot invent task completion. */
public final class AssistanceContributionTest {
    private AssistanceContributionTest() {}

    public static void main(String[] args) {
        verifiedFoodAidRelievesPressureAndCanResolve();
        distantContributionRejected();
        closedTaskRejected();
        infrastructureRepairRelievesRouteAndIndustryWear();
        System.out.println("PASS assistance contribution: verified delivery + radius/auth checks + persistence");
    }

    private static void verifiedFoodAidRelievesPressureAndCanResolve() {
        SimulationState state = new SimulationState(0xA55157L);
        Faction faction = new Faction(state.nextId(), "Aid Realm", "Mayor");
        Settlement town = new Settlement(state.nextId(), "Hungerford", new SimPosition(0, 0), 180, 120);
        town.setFoodSecurity(.10);
        faction.addSettlement(town);
        faction.stockpile().add(ResourceType.FOOD, 10);
        state.addFaction(faction);
        AssistanceTask task = new AssistanceTask(state.nextId(), faction.id(), town.id(), 0, 45,
                AssistanceTaskType.FOOD_RELIEF, "pressure:food_relief", .9);
        state.addAssistanceTask(task);
        double foodBefore = town.stockpile().get(ResourceType.FOOD);
        var far = AssistanceContributionEngine.contributeVerified(state, "player:helper", new SimPosition(5000, 5000), task.id(), 8);
        check(!far.success(), "too far must fail");
        var ok = AssistanceContributionEngine.contributeVerified(state, "player:helper", town.position(), task.id(), 8);
        check(ok.success(), "nearby verified contribution must succeed: " + ok.reason());
        check(town.stockpile().get(ResourceType.FOOD) > foodBefore, "settlement stockpile must receive goods");
        check(town.foodSecurity() > .10, "food security must improve");
        check(state.playerStanding("player:helper").reputationWith(faction.id()) > 0, "reputation must rise");
        // Repeated aid can resolve the task through pressure collapse.
        for (int i = 0; i < 12 && task.active(); i++) {
            AssistanceContributionEngine.contributeVerified(state, "player:helper", town.position(), task.id(), 8);
        }
        check(task.status() == AssistanceTaskStatus.RESOLVED || town.foodSecurity() > .45,
                "sustained aid must resolve or strongly relieve food pressure");
        SimulationValidator.validate(state).throwIfInvalid();
        SimulationState restored = SimulationStateCodec.decode(SimulationStateCodec.encode(state), state.species());
        AssistanceTask restoredTask = restored.assistanceTasks().stream().filter(t -> t.id() == task.id()).findFirst().orElseThrow();
        check(restoredTask.status() == task.status(), "task status persists after contribution");
    }

    private static void distantContributionRejected() {
        SimulationState state = new SimulationState(0xA55158L);
        Faction faction = new Faction(state.nextId(), "Far Realm", "Mayor");
        Settlement town = new Settlement(state.nextId(), "Farford", new SimPosition(10, 10), 100, 100);
        faction.addSettlement(town);
        state.addFaction(faction);
        AssistanceTask task = new AssistanceTask(state.nextId(), faction.id(), town.id(), 0, 45,
                AssistanceTaskType.SECURITY_SUPPORT, "pressure:security", .8);
        state.addAssistanceTask(task);
        var result = AssistanceContributionEngine.contributeVerified(state, "player:x", new SimPosition(10_000, 10_000), task.id(), 8);
        check(!result.success() && result.reason().equals("too_far"), "distance gate");
        check(task.active(), "rejected aid must leave task open");
    }

    private static void closedTaskRejected() {
        SimulationState state = new SimulationState(0xA55159L);
        Faction faction = new Faction(state.nextId(), "Closed Realm", "Mayor");
        Settlement town = new Settlement(state.nextId(), "Closedton", new SimPosition(0, 0), 100, 100);
        faction.addSettlement(town);
        state.addFaction(faction);
        AssistanceTask task = new AssistanceTask(state.nextId(), faction.id(), town.id(), 0, 45,
                AssistanceTaskType.HOUSING_SUPPLIES, "pressure:housing", .7);
        state.addAssistanceTask(task);
        task.cancel();
        var result = AssistanceContributionEngine.contributeVerified(state, "player:x", town.position(), task.id(), 8);
        check(!result.success(), "closed task rejected");
    }

    private static void infrastructureRepairRelievesRouteAndIndustryWear() {
        SimulationState state = new SimulationState(0xA5515AL);
        Faction faction = new Faction(state.nextId(), "Stone Realm", "Mayor");
        Settlement town = new Settlement(state.nextId(), "Wearford", new SimPosition(0, 0), 220, 200);
        Settlement peer = new Settlement(state.nextId(), "Peerwick", new SimPosition(400, 0), 120, 120);
        faction.addSettlement(town);
        faction.addSettlement(peer);
        state.addFaction(faction);
        var route = new dev.livingrealms.sim.transport.TransportRoute(
                state.nextId(), faction.id(), town.id(), peer.id(),
                dev.livingrealms.sim.transport.TransportMode.ROAD, 400, .2, .7, 40);
        route.setOperational(false);
        state.addRoute(route);
        double infraBefore = town.infrastructure();
        double pressure = AssistanceContributionEngine.infrastructurePressure(state, town);
        check(pressure > .3, "worn route must create infrastructure pressure");
        AssistanceTask task = new AssistanceTask(state.nextId(), faction.id(), town.id(), 0, 45,
                AssistanceTaskType.INFRASTRUCTURE_REPAIR, "pressure:infrastructure_repair", pressure);
        state.addAssistanceTask(task);
        check(AssistanceContributionEngine.requiredResource(AssistanceTaskType.INFRASTRUCTURE_REPAIR) == ResourceType.STONE,
                "infrastructure repair consumes stone");
        var ok = AssistanceContributionEngine.contributeVerified(state, "player:mason", town.position(), task.id(), 8);
        check(ok.success(), "stone contribution must succeed: " + ok.reason());
        check(town.infrastructure() > infraBefore, "settlement infrastructure must improve");
        check(route.quality() > .2, "route quality must improve");
        for (int i = 0; i < 4 && !route.operational(); i++) {
            AssistanceContributionEngine.contributeVerified(state, "player:mason", town.position(), task.id(), 8);
        }
        check(route.operational(), "sustained repair must reopen a worn route");
        SimulationValidator.validate(state).throwIfInvalid();
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
