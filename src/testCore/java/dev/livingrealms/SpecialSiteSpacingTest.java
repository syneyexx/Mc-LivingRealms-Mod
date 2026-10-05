package dev.livingrealms;

import dev.livingrealms.sim.civilization.BanditEconomyEngine;
import dev.livingrealms.sim.civilization.MigrationEngine;
import dev.livingrealms.sim.civilization.MigrationGroup;
import dev.livingrealms.sim.civilization.MigrationReason;
import dev.livingrealms.sim.civilization.PiracyEngine;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WizardTreesSeeder;
import java.util.List;

/** Context-specific spacing policy for sites that are intentionally not ordinary villages. */
public final class SpecialSiteSpacingTest {
    private SpecialSiteSpacingTest() {}

    public static void main(String[] args) {
        refugeeCampBand();
        banditCorridorBand();
        pirateCoastalBand();
        resourceParentBand();
        wizardTreesUndergroundLayer();
        System.out.println("PASS special site spacing: refugees/bandits/pirates/resources/Wizard Trees use context-specific bands");
    }

    private static void refugeeCampBand() {
        SimulationState state = new SimulationState(0xCA4F55L);
        Faction faction = new Faction(state.nextId(), "Refuge Realm", "Marshal");
        Settlement source = new Settlement(state.nextId(), "Source", new SimPosition(0, 0), 80, 90);
        faction.addSettlement(source);
        state.addFaction(faction);
        MigrationGroup group = new MigrationGroup(state.nextId(), faction.id(), source.id(), 0, 0, 24, MigrationReason.WAR);
        state.addMigrationGroup(group);
        MigrationEngine.establishRefugeeCamp(state, group, source);
        Settlement camp = state.findSettlement(group.campSettlementId()).orElseThrow();
        double d = camp.position().distanceTo(source.position());
        check(d >= 100 && d <= 350, "refugee camp distance=" + d);
        check(camp.role() == SettlementRole.SPECIAL, "refugee camp must not enter ordinary settlement graph");
    }

    private static void banditCorridorBand() {
        SimulationState state = new SimulationState(0xBADD17L);
        Faction faction = new Faction(state.nextId(), "Road Realm", "Count");
        Settlement a = new Settlement(state.nextId(), "A", new SimPosition(0, 0), 700, 800);
        Settlement b = new Settlement(state.nextId(), "B", new SimPosition(900, 0), 650, 760);
        faction.addSettlement(a);
        faction.addSettlement(b);
        state.addFaction(faction);
        TransportRoute route = new TransportRoute(state.nextId(), faction.id(), a.id(), b.id(),
                TransportMode.ROAD, 900, .55, .20, 420);
        state.addRoute(route);
        SimPosition hideout = BanditEconomyEngine.landHideoutPosition(state, a, 991L);
        double d = hideout.distanceTo(a.position());
        check(d >= 250 && d <= 660, "land bandit distance=" + d);
        // Valuable corridor runs east from A. Candidate may side-jitter, but should remain on that half-plane.
        check(hideout.x() > a.position().x(), "bandit hideout should bias toward valuable road corridor");
        check(hideout.distanceTo(b.position()) >= 180, "bandit hideout must not spawn inside another settlement");
    }

    private static void pirateCoastalBand() {
        SimulationState state = new SimulationState(0x51A5EA55L);
        Settlement portTown = new Settlement(1, "Port", new SimPosition(0, 0), 800, 900);
        SimPosition port = new SimPosition(20, 10);
        for (long bandId : List.of(3L, 17L, 101L, 999L)) {
            SimPosition hideout = PiracyEngine.pirateHideoutPosition(state, portTown, port, bandId);
            double d = hideout.distanceTo(port);
            check(d >= 399.0 && d <= 1001.0, "pirate hideout outside 400-1000 band: " + d);
        }
    }

    private static void resourceParentBand() {
        SimulationState state = new SimulationState(0xEC055L);
        Faction faction = new Faction(state.nextId(), "Resource Realm", "Steward");
        Settlement town = new Settlement(state.nextId(), "Resource Town", new SimPosition(1000, 1000), 900, 1000);
        faction.addSettlement(town);
        state.addFaction(faction);
        var sites = PrimaryEconomyPlanner.plan(state, faction, town).stream()
                .filter(i -> i.role() == StructureRole.MINE
                        || i.role() == StructureRole.LUMBER_CAMP
                        || i.role() == StructureRole.FISHERY)
                .toList();
        check(!sites.isEmpty(), "resource fixture must produce at least one geography-supported site");
        for (var site : sites) {
            double d = site.center().distanceTo(town.position());
            check(d >= 120 && d <= 500, site.role() + " parent distance=" + d);
        }
    }

    private static void wizardTreesUndergroundLayer() {
        SimulationState state = new SimulationState(0x715A7DL);
        WizardTreesSeeder.ensure(state);
        Faction wizard = state.factions().stream().filter(WizardTreesSeeder::isWizardTrees).findFirst().orElseThrow();
        check(wizard.settlements().size() == 3, "three underground colonies");
        for (Settlement settlement : wizard.settlements()) {
            check(settlement.role() == SettlementRole.SPECIAL, "Wizard Trees must use separate SPECIAL layer");
        }
        for (int i = 0; i < wizard.settlements().size(); i++) {
            for (int j = i + 1; j < wizard.settlements().size(); j++) {
                double d = wizard.settlements().get(i).position().distanceTo(wizard.settlements().get(j).position());
                check(d >= 600 && d <= 1500, "Wizard Trees colony separation=" + d);
            }
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
