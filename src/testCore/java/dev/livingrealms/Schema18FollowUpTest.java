package dev.livingrealms;

import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.diplomacy.WarGoalType;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.ecology.SpeciesCatalog;
import dev.livingrealms.sim.faction.DevelopmentPriority;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.industry.IndustryCatalog;
import dev.livingrealms.sim.industry.IndustryKind;
import dev.livingrealms.sim.ui.RealmDashboardBuilder;
import dev.livingrealms.sim.ui.RealmDashboardCodec;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Follow-up gates for schema-18 goods trade/industry inputs, FOOD policy order, and war target wire. */
public final class Schema18FollowUpTest {
    private Schema18FollowUpTest() {}

    public static void main(String[] args) {
        textileMillUsesWool();
        machineryUsesIronAndTools();
        foodPolicyBoostsFarms();
        warTargetRoundTrip();
        System.out.println("PASS schema18 follow-up: WOOL mill + MACHINERY inputs + FOOD policy + war target protocol "
                + RealmDashboardSnapshot.PROTOCOL_VERSION);
    }

    private static void textileMillUsesWool() {
        var process = IndustryCatalog.defaults().stream()
                .filter(p -> p.kind() == IndustryKind.TEXTILE_MILL)
                .findFirst()
                .orElseThrow();
        check(process.inputs().containsKey(ResourceType.WOOL), "textile mill must consume WOOL");
        check(!process.inputs().containsKey(ResourceType.FOOD), "textile mill must not mint from FOOD");
        check(process.outputs().containsKey(ResourceType.TEXTILES), "textile mill outputs TEXTILES");
    }

    private static void machineryUsesIronAndTools() {
        var process = IndustryCatalog.defaults().stream()
                .filter(p -> p.kind() == IndustryKind.MACHINERY_WORKS)
                .findFirst()
                .orElseThrow();
        check(process.inputs().containsKey(ResourceType.IRON), "machinery needs IRON");
        check(process.inputs().containsKey(ResourceType.TOOLS), "machinery needs TOOLS");
        check(!process.inputs().containsKey(ResourceType.COPPER), "machinery no longer mints from COPPER alone");
    }

    private static void foodPolicyBoostsFarms() {
        SimulationState state = new SimulationState(0xF00D_5011L, SpeciesCatalog.starter());
        Faction faction = new Faction(1, "Agraria", "Mayor");
        Settlement settlement = new Settlement(2, "Wheatford", new SimPosition(0, 0), 220, 260);
        settlement.setDevelopmentPriority(DevelopmentPriority.FOOD);
        faction.addSettlement(settlement);
        state.addFaction(faction);
        var intents = SettlementPlanner.plan(faction, settlement);
        int farmPri = intents.stream().filter(i -> i.role() == StructureRole.FARM).mapToInt(i -> i.priority()).max().orElse(-1);
        int decorPri = intents.stream()
                .filter(i -> i.role() == StructureRole.MONUMENT || i.role() == StructureRole.PLAZA || i.role() == StructureRole.TAVERN)
                .mapToInt(i -> i.priority()).max().orElse(999);
        check(farmPri >= 0, "FOOD policy settlement must plan farms");
        check(farmPri >= decorPri, "FOOD policy must rank farm intents at/above decorative peers");
    }

    private static void warTargetRoundTrip() {
        SimulationState state = new SimulationState(0xA27C_0719L, SpeciesCatalog.starter());
        Faction a = new Faction(1, "North", "King A");
        Faction b = new Faction(2, "South", "King B");
        Settlement capital = new Settlement(10, "Southkeep", new SimPosition(400, 0), 900, 1200);
        Settlement outpost = new Settlement(11, "Outpost", new SimPosition(100, 0), 80, 90);
        // Capital not first in list order.
        b.addSettlement(outpost);
        b.addSettlement(capital);
        a.addSettlement(new Settlement(12, "Northgate", new SimPosition(-200, 0), 500, 700));
        a.relationWith(b.id()).declareWar();
        b.relationWith(a.id()).declareWar();
        state.addFaction(a);
        state.addFaction(b);
        WarState war = new WarState(99, a.id(), b.id(), WarGoalType.CONQUEST, capital.id(), 0);
        state.addWar(war);

        RealmDashboardSnapshot snap = RealmDashboardBuilder.build(state, "player:test", new SimPosition(0, 0));
        check(snap.protocolVersion() == RealmDashboardSnapshot.PROTOCOL_VERSION,
                "builder emits protocol " + RealmDashboardSnapshot.PROTOCOL_VERSION);
        check(!snap.wars().isEmpty(), "war visible");
        var view = snap.wars().getFirst();
        check(view.targetSettlementId() == capital.id(), "war view targets capital id");
        check(view.targetSettlementName().equals(capital.name()), "war view names capital");

        String json = RealmDashboardCodec.encode(snap);
        RealmDashboardSnapshot round = RealmDashboardCodec.decode(json);
        check(round.wars().getFirst().targetSettlementId() == capital.id(), "codec keeps target id");
        boolean rejected = false;
        try {
            RealmDashboardCodec.decode(json.replaceFirst("\"v\":" + RealmDashboardSnapshot.PROTOCOL_VERSION, "\"v\":18"));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "stale protocol must be rejected after bump");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
