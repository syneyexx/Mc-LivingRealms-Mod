package dev.livingrealms;

import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.SettlementMorphology;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementGeographyProfile;
import dev.livingrealms.sim.society.WorldCauseExplainer;
import dev.livingrealms.sim.ui.RealmDashboardBuilder;
import dev.livingrealms.sim.ui.RealmDashboardCodec;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.Set;

/** Gates geography-derived urban morphology, plazas/street furniture, and player-facing causality. */
public final class OrganicMorphologyAndCauseTest {
    private OrganicMorphologyAndCauseTest() {}

    public static void main(String[] args) {
        morphologiesAreGeographyDerived();
        denseWorldUsesMultipleStreetPatterns();
        plazasAndStreetLightsExist();
        causeExplainerAndDashboardProtocol15();
        System.out.println("PASS organic morphology + cause summaries: geography morphologies, plazas/lights, protocol 17 causality");
    }

    private static void morphologiesAreGeographyDerived() {
        Faction f = new Faction(1, "Test", "Ruler");
        Settlement coast = new Settlement(10, "Harbor Bay", new SimPosition(0, 0), 800, 700);
        coast.setGeography(new SettlementGeographyProfile(true, false, true, false, 0.8, 64, 1, .4, .2, .1, "minecraft:beach", true));
        f.addSettlement(coast);
        check(SettlementMorphology.derive(f, coast) == SettlementMorphology.COASTAL_PORT, "ship-suitable coast must be coastal_port");

        Settlement river = new Settlement(11, "River Ford", new SimPosition(200, 0), 400, 360);
        river.setGeography(new SettlementGeographyProfile(false, true, true, true, 0.3, 68, 2, .6, .3, .2, "minecraft:river", true));
        SettlementMorphology riverMorph = SettlementMorphology.derive(f, river);
        check(riverMorph == SettlementMorphology.RIVER_TOWN || riverMorph == SettlementMorphology.LINEAR_VALLEY,
                "river geography must yield river/linear morphology: " + riverMorph);

        Settlement hill = new Settlement(12, "High Peak", new SimPosition(400, 0), 500, 450);
        hill.setGeography(new SettlementGeographyProfile(false, false, false, false, 0.05, 140, 12, .3, .4, .5, "minecraft:windswept_hills", true));
        check(SettlementMorphology.derive(f, hill) == SettlementMorphology.HILL_TOWN, "steep high elevation must be hill_town");
    }

    private static void denseWorldUsesMultipleStreetPatterns() {
        SimulationState state = new SimulationState(991_122L);
        DemoSeeder.seed(state);
        Set<String> layouts = new HashSet<>();
        int coastal = 0, organic = 0;
        for (Faction faction : state.factions()) for (Settlement settlement : faction.settlements()) {
            String layout = SettlementPlanner.layoutArchetype(faction, settlement);
            layouts.add(layout);
            if (layout.contains("coastal")) coastal++;
            if (layout.contains("organic")) organic++;
            var plan = SettlementPlanner.plan(faction, settlement);
            long roads = plan.stream().filter(i -> i.role() == StructureRole.ROAD).count();
            check(roads >= 1 || settlement.tier().ordinal() <= Settlement.Tier.CAMP.ordinal(),
                    "settlement without roads: " + settlement.name());
        }
        check(layouts.size() >= 5, "expected diverse geography morphologies, got " + layouts);
        check(organic + coastal >= 1, "expected organic or coastal morphologies in dense world");
    }

    private static void plazasAndStreetLightsExist() {
        Faction f = new Faction(2, "Plaza Realm", "Mayor");
        Settlement town = new Settlement(20, "Market Cross", new SimPosition(0, 0), 1200, 1100);
        f.addSettlement(town);
        var plan = SettlementPlanner.plan(f, town);
        check(plan.stream().anyMatch(i -> i.role() == StructureRole.PLAZA), "villages+ must plan plazas");
        var road = plan.stream().filter(i -> i.role() == StructureRole.ROAD).findFirst().orElseThrow();
        var bp = StructureBlueprintFactory.create(road);
        check(bp.placements().stream().anyMatch(p -> p.slot() == PaletteSlot.LIGHT),
                "roads must include sidewalk lighting");
        var plaza = plan.stream().filter(i -> i.role() == StructureRole.PLAZA).findFirst().orElseThrow();
        var plazaBp = StructureBlueprintFactory.create(plaza);
        check(plazaBp.id().contains("plaza"), "plaza blueprint id: " + plazaBp.id());
    }

    private static void causeExplainerAndDashboardProtocol15() {
        check(RealmDashboardSnapshot.PROTOCOL_VERSION == 18, "dashboard protocol must be 18");
        SimulationState state = new SimulationState(44L);
        DemoSeeder.seed(state);
        Faction faction = state.factions().getFirst();
        Settlement settlement = faction.settlements().getFirst();
        settlement.setFoodSecurity(0.1);
        settlement.stockpile().take(ResourceType.FOOD, settlement.stockpile().get(ResourceType.FOOD));
        String cause = WorldCauseExplainer.settlementPressureCause(state, faction, settlement);
        check(cause != null && !cause.isBlank(), "cause explainer must return text");
        var snap = RealmDashboardBuilder.build(state, "player:test", settlement.position());
        check(snap.protocolVersion() == 17, "builder emits protocol 17");
        check(!snap.settlements().isEmpty(), "snapshot has settlements");
        check(snap.settlements().stream().anyMatch(s -> s.causeSummary() != null && !s.causeSummary().isBlank()),
                "settlement views must include cause summaries");
        check(snap.operations().assistanceTasks() != null, "operations must expose assistance task board");
        String json = RealmDashboardCodec.encode(snap);
        var round = RealmDashboardCodec.decode(json);
        check(round.protocolVersion() == 17, "codec roundtrip keeps protocol 17");
        check(round.settlements().getFirst().causeSummary().equals(snap.settlements().getFirst().causeSummary()),
                "cause summary must survive codec roundtrip");
    }

    private static void check(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }
}
