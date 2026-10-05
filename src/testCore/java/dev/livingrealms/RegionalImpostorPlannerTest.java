package dev.livingrealms;

import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.config.SimulationPreset;
import dev.livingrealms.sim.ecology.EcosystemRegion;
import dev.livingrealms.sim.ecology.PopulationGroup;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.presentation.RegionalImpostorPlanner;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Regional impostor band: between physical and regional radii, budget-capped, unique keys. */
public final class RegionalImpostorPlannerTest {
    private RegionalImpostorPlannerTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x524547494F4EL);
        Faction faction = new Faction(state.nextId(), "Farlands", "Warden");
        Faction buyer = new Faction(state.nextId(), "Buyers", "Broker");
        Settlement near = new Settlement(state.nextId(), "Nearkeep", new SimPosition(50, 0), 400, 500);
        Settlement far = new Settlement(state.nextId(), "Farwatch", new SimPosition(900, 0), 800, 900);
        Settlement beyond = new Settlement(state.nextId(), "Beyond", new SimPosition(4000, 0), 600, 700);
        Settlement buyerTown = new Settlement(state.nextId(), "Buyport", new SimPosition(1600, 0), 500, 600);
        faction.addSettlement(near);
        faction.addSettlement(far);
        faction.addSettlement(beyond);
        buyer.addSettlement(buyerTown);
        faction.addArmy(new Army(state.nextId(), faction.id(), new SimPosition(850, 40), 120));
        state.addFaction(faction);
        state.addFaction(buyer);
        state.addShipment(new TradeShipment(
                state.nextId(), faction.id(), buyer.id(), ResourceType.FOOD, 40, 120,
                new SimPosition(0, 0), new SimPosition(1600, 0)));
        // Nudge shipment into the regional band (~900).
        state.shipments().getFirst().restoreProgress(0.55);
        EcosystemRegion region = new EcosystemRegion(state.nextId(), state.biomes().get("temperate_forest"), 40, new SimPosition(1000, 80));
        region.add(new PopulationGroup(state.nextId(), "deer", "temperate_forest", new SimPosition(1000, 80), 80));
        state.addRegion(region);

        var config = SimulationPreset.BALANCED.config();
        double inner = RuntimeProjectionPolicy.regionalImpostorInnerRadius(config);
        double outer = RuntimeProjectionPolicy.regionalImpostorOuterRadius(config);
        int budget = RuntimeProjectionPolicy.regionalImpostorBudget(config);
        check(outer > inner, "regional outer must exceed physical inner");
        check(budget >= 48 && budget <= 160, "impostor budget envelope");

        List<SimPosition> players = List.of(new SimPosition(0, 0));
        List<RegionalImpostorPlanner.Token> tokens = RegionalImpostorPlanner.plan(state, players, inner, outer, budget);
        check(!tokens.isEmpty(), "regional band must yield impostors");
        Set<String> keys = tokens.stream().map(RegionalImpostorPlanner.Token::key).collect(Collectors.toSet());
        check(keys.size() == tokens.size(), "impostor keys must be unique");
        Set<RegionalImpostorPlanner.Kind> kinds = tokens.stream().map(RegionalImpostorPlanner.Token::kind).collect(Collectors.toCollection(() -> EnumSet.noneOf(RegionalImpostorPlanner.Kind.class)));
        check(kinds.contains(RegionalImpostorPlanner.Kind.SETTLEMENT_BUSTLE), "far settlement bustle");
        check(kinds.contains(RegionalImpostorPlanner.Kind.MILITARY_BANNER), "army banner in band");
        check(kinds.contains(RegionalImpostorPlanner.Kind.CARAVAN_DUST), "caravan dust in band");
        check(kinds.contains(RegionalImpostorPlanner.Kind.HERD), "herd token in band");
        for (RegionalImpostorPlanner.Token t : tokens) {
            double d = players.getFirst().distanceTo(t.position());
            check(d > inner && d <= outer, "token must sit in regional band: " + t.key() + " d=" + d);
        }
        check(!tokens.stream().anyMatch(t -> t.canonicalId() == near.id()), "near settlement must not use regional impostor");
        check(!tokens.stream().anyMatch(t -> t.canonicalId() == beyond.id()), "beyond-regional settlement must be abstract-only");

        List<RegionalImpostorPlanner.Token> capped = RegionalImpostorPlanner.plan(state, players, inner, outer, 2);
        check(capped.size() == 2, "budget must hard-cap impostors");
        check(RegionalImpostorPlanner.plan(state, List.of(), inner, outer, budget).isEmpty(), "no players => no impostors");
        check(RuntimeProjectionPolicy.citizenNearBudget(config) + RuntimeProjectionPolicy.citizenFarBudget(config)
                        == RuntimeProjectionPolicy.citizenBudget(config),
                "citizen near+far must partition total budget");

        System.out.println("PASS regional impostor planner: band filter + budget + settlement/army/caravan/herd tokens");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
