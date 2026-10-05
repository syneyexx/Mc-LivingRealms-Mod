package dev.livingrealms;

import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.military.MilitaryObjectiveType;
import dev.livingrealms.sim.player.PlayerAgencyActions;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.DashboardActionService;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 6: declare war + army capture/siege/raid/escort/patrol through agency and dashboard. */
public final class PlayerWarRoomTest {
    private PlayerWarRoomTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x57415236L);
        var founded = PlayerSettlementFounder.found(state, "player:warlord", "Warlord", "Warford",
                new SimPosition(12_000, 12_000));
        check(founded.success(), "found: " + founded.reason());
        long selfId = founded.factionId();
        Faction self = state.findFaction(selfId).orElseThrow();
        Faction enemy = new Faction(state.nextId(), "Hostile March", "Rival");
        Settlement enemyTown = new Settlement(state.nextId(), "Rivalmarch", new SimPosition(14_200, 12_000), 800, 900);
        enemy.addSettlement(enemyTown);
        enemy.addArmy(new Army(state.nextId(), enemy.id(), new SimPosition(14_100, 12_000), 160));
        self.addArmy(new Army(state.nextId(), selfId, new SimPosition(12_080, 12_000), 200));
        state.addFaction(enemy);
        self.relationWith(enemy.id()).adjust(-20);
        enemy.relationWith(selfId).adjust(-20);

        var war = PlayerAgencyActions.declareWar(state, "player:warlord", enemy.id(), enemyTown.id());
        check(war.success(), "declare war: " + war.reason());
        check(self.relationWith(enemy.id()).status() == RelationStatus.WAR, "self at war");
        check(enemy.relationWith(selfId).status() == RelationStatus.WAR, "enemy at war");
        check(state.wars().stream().anyMatch(WarState::active), "active war record");

        long armyId = self.armies().getFirst().id();
        var capture = PlayerAgencyActions.armyOrder(state, "player:warlord", armyId,
                MilitaryObjectiveType.CAPTURE_SETTLEMENT, enemyTown.id());
        check(capture.success(), "capture: " + capture.reason());
        check(state.objectives().stream().anyMatch(o -> !o.complete() && o.armyId() == armyId
                && o.type() == MilitaryObjectiveType.CAPTURE_SETTLEMENT
                && o.targetSettlementId() == enemyTown.id()), "capture objective");

        var siege = DashboardActionService.apply(state, "player:warlord", new SimPosition(12_000, 12_000),
                new DashboardActionCommand(DashboardActionCommand.Action.ARMY_SIEGE, armyId, enemyTown.id()));
        check(siege.success(), "siege action: " + siege.reason());
        check(state.objectives().stream().anyMatch(o -> !o.complete() && o.armyId() == armyId
                && o.type() == MilitaryObjectiveType.SIEGE), "siege objective");

        var raid = DashboardActionService.apply(state, "player:warlord", new SimPosition(12_000, 12_000),
                new DashboardActionCommand(DashboardActionCommand.Action.ARMY_RAID, armyId, enemyTown.id()));
        check(raid.success(), "raid action: " + raid.reason());

        Settlement home = self.settlements().getFirst();
        var escort = DashboardActionService.apply(state, "player:warlord", new SimPosition(12_000, 12_000),
                new DashboardActionCommand(DashboardActionCommand.Action.ARMY_ESCORT, armyId, home.id()));
        check(escort.success(), "escort action: " + escort.reason());
        check(state.objectives().stream().anyMatch(o -> !o.complete() && o.armyId() == armyId
                && o.type() == MilitaryObjectiveType.ESCORT), "escort objective");

        var patrol = DashboardActionService.apply(state, "player:warlord", new SimPosition(12_000, 12_000),
                new DashboardActionCommand(DashboardActionCommand.Action.ARMY_PATROL, armyId, home.id()));
        check(patrol.success(), "patrol action: " + patrol.reason());
        check(state.objectives().stream().anyMatch(o -> !o.complete() && o.armyId() == armyId
                && o.type() == MilitaryObjectiveType.PATROL_BORDER), "patrol objective");

        DashboardActionCommand encoded = new DashboardActionCommand(
                DashboardActionCommand.Action.ARMY_CAPTURE, armyId, enemyTown.id());
        check(DashboardActionCommand.parse(encoded.encode()).equals(encoded), "secondary target round-trip");

        var dup = PlayerAgencyActions.declareWar(state, "player:warlord", enemy.id());
        check(!dup.success(), "duplicate war rejected");

        System.out.println("PASS player war room: declare war + capture/siege/raid/escort/patrol");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
