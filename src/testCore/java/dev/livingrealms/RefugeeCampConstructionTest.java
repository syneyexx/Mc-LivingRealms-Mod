package dev.livingrealms;

import dev.livingrealms.sim.civilization.MigrationGroup;
import dev.livingrealms.sim.civilization.MigrationReason;
import dev.livingrealms.sim.civilization.MigrationStatus;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Refugee camps must enqueue physical shelters rather than remaining abstract markers. */
public final class RefugeeCampConstructionTest {
    private RefugeeCampConstructionTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x52454643414D50L);
        Faction faction = new Faction(state.nextId(), "Exodus Realm", "Marshal");
        Settlement source = new Settlement(state.nextId(), "Burnedford", new SimPosition(0, 0), 40, 20);
        source.setFoodSecurity(.1);
        source.setPublicOrder(.05);
        faction.addSettlement(source);
        faction.stockpile().add(ResourceType.FOOD, 5);
        state.addFaction(faction);

        // Rejected arrival path: no viable destination → camp founding.
        MigrationGroup group = new MigrationGroup(state.nextId(), faction.id(), source.id(), 0, 0, 36, MigrationReason.WAR);
        group.advance(1.0);
        state.addMigrationGroup(group);
        // Drive arrival handling through normal day simulation.
        for (int i = 0; i < 3 && group.status() != MigrationStatus.CAMPED; i++) state.advanceDays(1);
        check(group.status() == MigrationStatus.CAMPED, "rejected migrants must found/join a camp: " + group.status());
        Settlement camp = state.findSettlement(group.campSettlementId()).orElseThrow();
        check(camp.name().startsWith("Refugee Camp ") || camp.name().startsWith("Haven "), "camp naming");
        check(camp.isConstructionCompleted("well:0"), "camps keep a completed well for water");
        check(!camp.isConstructionCompleted("farm:0"), "farms must remain pending for physical build");
        long pendingShelters = SettlementPlanner.pending(faction, camp).stream()
                .filter(i -> i.role() == StructureRole.HOUSE || i.role() == StructureRole.FARM || i.role() == StructureRole.ROAD)
                .count();
        check(pendingShelters > 0, "camp must expose pending house/farm/road intents for materialization");
        check(camp.stockpile().get(ResourceType.WOOD) > 0, "builders need timber on site");
        System.out.println("PASS refugee camp construction: pending shelters/farms + well seed + aid stockpile");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
