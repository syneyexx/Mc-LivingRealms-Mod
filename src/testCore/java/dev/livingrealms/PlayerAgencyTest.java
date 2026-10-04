package dev.livingrealms;

import dev.livingrealms.sim.diplomacy.TreatyType;
import dev.livingrealms.sim.diplomacy.WarGoalType;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.military.MilitaryObjectiveType;
import dev.livingrealms.sim.player.FactionRank;
import dev.livingrealms.sim.player.PlayerAgencyActions;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.DashboardActionService;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Hands-on agency: abdication, peace, trade pact, army defend order. */
public final class PlayerAgencyTest {
    private PlayerAgencyTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x4147454E4359L);
        var founded = PlayerSettlementFounder.found(state, "player:ruler", "Ruler", "Agencyburg", new SimPosition(8000, 8000));
        check(founded.success(), "found");
        long selfId = founded.factionId();
        Faction self = state.findFaction(selfId).orElseThrow();
        Faction enemy = new Faction(state.nextId(), "Rival Host", "Enemy");
        Settlement enemyTown = new Settlement(state.nextId(), "Rivalkeep", new SimPosition(8600, 8000), 900, 1000);
        enemy.addSettlement(enemyTown);
        enemy.addArmy(new Army(state.nextId(), enemy.id(), new SimPosition(8500, 8000), 200));
        self.addArmy(new Army(state.nextId(), selfId, new SimPosition(8050, 8000), 180));
        state.addFaction(enemy);
        self.relationWith(enemy.id()).declareWar();
        enemy.relationWith(selfId).declareWar();
        state.addWar(new WarState(state.nextId(), selfId, enemy.id(), WarGoalType.CONQUEST, enemyTown.id(), state.clock().day()));

        var peace = PlayerAgencyActions.petitionPeace(state, "player:ruler", enemy.id());
        check(peace.success(), "ruler peace: " + peace.reason());
        check(self.relationWith(enemy.id()).status() != RelationStatus.WAR, "war relation cleared");
        check(state.treaties().stream().anyMatch(t -> t.type() == TreatyType.PEACE_TREATY && t.active()), "peace treaty");

        var pact = PlayerAgencyActions.proposeTradePact(state, "player:ruler", enemy.id());
        check(pact.success(), "trade pact: " + pact.reason());
        check(self.relationWith(enemy.id()).tradeAgreement(), "trade agreement flag");
        check(state.treaties().stream().anyMatch(t -> t.type() == TreatyType.TRADE_PACT && t.active()), "trade pact treaty");

        long armyId = self.armies().getFirst().id();
        var order = PlayerAgencyActions.armyOrder(state, "player:ruler", armyId, MilitaryObjectiveType.DEFEND);
        check(order.success(), "defend order: " + order.reason());
        check(state.objectives().stream().anyMatch(o -> !o.complete() && o.armyId() == armyId
                && o.type() == MilitaryObjectiveType.DEFEND), "defend objective active");

        var abdicate = DashboardActionService.apply(state, "player:ruler", new SimPosition(8000, 8000),
                new DashboardActionCommand(DashboardActionCommand.Action.ABDICATE, selfId));
        check(abdicate.success(), "abdicate: " + abdicate.reason());
        check(state.playerStanding("player:ruler").rank() == FactionRank.OUTSIDER, "no longer ruler");
        check(!self.rulerName().equals("Ruler"), "court successor renamed ruler");

        System.out.println("PASS player agency: peace + trade pact + army defend + abdication escape");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
