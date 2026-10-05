package dev.livingrealms;

import dev.livingrealms.sim.diplomacy.Treaty;
import dev.livingrealms.sim.diplomacy.TreatyType;
import dev.livingrealms.sim.diplomacy.WarGoalType;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.MilitaryObjectiveType;
import dev.livingrealms.sim.military.WarRoomOptionsBuilder;
import dev.livingrealms.sim.player.FactionRank;
import dev.livingrealms.sim.player.PlayerAgencyActions;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.player.PlayerStanding;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;

/** Wave 11: WarRoomOptionsBuilder goals, treaty break, army/escort target selection. */
public final class WarRoomOptionsBuilderTest {
    private WarRoomOptionsBuilderTest() {}

    public static void main(String[] args) {
        escortNeverEnemySettlement();
        armySelectorListsAll();
        defenseNotOffensive();
        rulerUnilateralNoblePetition();
        treatyBreakCosts();
        System.out.println("PASS war room options: escort targets + armies + goals + treaties + ranks");
    }

    private static void escortNeverEnemySettlement() {
        SimulationState state = new SimulationState(0x45534352L);
        var founded = PlayerSettlementFounder.found(state, "player:escort", "Escorter", "Escortford",
                new SimPosition(20_000, 20_000));
        check(founded.success(), "found: " + founded.reason());
        Faction self = state.findFaction(founded.factionId()).orElseThrow();
        Settlement home = self.settlements().getFirst();
        Faction enemy = new Faction(state.nextId(), "Enemy Escort", "Rival");
        Settlement enemyTown = new Settlement(state.nextId(), "Enemyhold", new SimPosition(22_200, 20_000), 700, 800);
        enemy.addSettlement(enemyTown);
        state.addFaction(enemy);
        self.relationWith(enemy.id()).declareWar();
        enemy.relationWith(self.id()).declareWar();
        state.addWar(new WarState(state.nextId(), self.id(), enemy.id(), WarGoalType.CONQUEST,
                enemyTown.id(), state.clock().day()));

        TradeShipment shipment = new TradeShipment(state.nextId(), self.id(), enemy.id(), ResourceType.FOOD,
                20, 40, home.position(), enemyTown.position());
        shipment.restoreLogistics(home.id(), enemyTown.id(), 0, 0, state.clock().day(),
                state.clock().day() + 8, .3, 0, TradeShipment.LossState.NONE, 0);
        state.addShipment(shipment);

        List<WarRoomOptionsBuilder.EscortTarget> escorts =
                WarRoomOptionsBuilder.escortTargets(state, self.id());
        check(escorts.stream().anyMatch(t -> t.id() == shipment.id()
                        && t.kind() == WarRoomOptionsBuilder.EscortKind.SHIPMENT),
                "own shipment is escortable");
        check(escorts.stream().anyMatch(t -> t.id() == home.id()
                        && t.kind() == WarRoomOptionsBuilder.EscortKind.FRIENDLY_SETTLEMENT),
                "own settlement is escortable");
        check(escorts.stream().noneMatch(t -> t.id() == enemyTown.id()),
                "enemy settlement must NEVER be an escort target");
        check(!WarRoomOptionsBuilder.isValidEscortTarget(state, self.id(), enemyTown.id()),
                "isValidEscortTarget rejects enemy town");

        var orders = WarRoomOptionsBuilder.armyOrderOptions(state, "player:escort");
        check(orders.defaultEscortTargetId() != enemyTown.id(), "default escort is not enemy");
        check(orders.defaultEscortTargetId() == shipment.id() || orders.defaultEscortTargetId() == home.id(),
                "default escort is shipment or home");
        check(orders.hostileSettlementIds().contains(enemyTown.id()), "enemy listed as hostile");
        check(orders.defaultPatrolSettlementId() == home.id(), "patrol defaults to owned settlement");

        long armyId = self.armies().getFirst().id();
        var bad = PlayerAgencyActions.armyOrder(state, "player:escort", armyId,
                MilitaryObjectiveType.ESCORT, enemyTown.id());
        check(!bad.success() && "escort_target_invalid".equals(bad.reason()),
                "agency rejects enemy escort: " + bad.reason());
        var good = PlayerAgencyActions.armyOrder(state, "player:escort", armyId,
                MilitaryObjectiveType.ESCORT, home.id());
        check(good.success(), "agency accepts friendly escort: " + good.reason());
    }

    private static void armySelectorListsAll() {
        SimulationState state = new SimulationState(0x41524D59L);
        var founded = PlayerSettlementFounder.found(state, "player:armies", "Marshal", "Armyburg",
                new SimPosition(24_000, 24_000));
        check(founded.success(), "found");
        Faction self = state.findFaction(founded.factionId()).orElseThrow();
        long first = self.armies().getFirst().id();
        self.addArmy(new Army(state.nextId(), self.id(), new SimPosition(24_080, 24_000), 90));
        self.addArmy(new Army(state.nextId(), self.id(), new SimPosition(24_160, 24_000), 70));
        var options = WarRoomOptionsBuilder.armyOrderOptions(state, "player:armies");
        check(options.armyIds().size() == 3, "all three armies listed, got " + options.armyIds().size());
        check(options.armyIds().contains(first), "includes founding army");
    }

    private static void defenseNotOffensive() {
        SimulationState state = new SimulationState(0x4445464EL);
        var founded = PlayerSettlementFounder.found(state, "player:goals", "Warlord", "Goalburg",
                new SimPosition(26_000, 26_000));
        check(founded.success(), "found");
        Faction self = state.findFaction(founded.factionId()).orElseThrow();
        Faction enemy = new Faction(state.nextId(), "Goal Rival", "Rival");
        enemy.addSettlement(new Settlement(state.nextId(), "Rivalgoal", new SimPosition(28_200, 26_000), 900, 1000));
        state.addFaction(enemy);
        self.relationWith(enemy.id()).adjust(-40);
        enemy.relationWith(self.id()).adjust(-40);
        List<WarGoalType> goals = WarRoomOptionsBuilder.offensiveGoals(state, self, enemy);
        check(goals.contains(WarGoalType.CONQUEST), "conquest always offered");
        check(!goals.contains(WarGoalType.DEFENSE), "DEFENSE never for offensive initiation");
        check(!WarRoomOptionsBuilder.isValidOffensiveGoal(WarGoalType.DEFENSE), "defense invalid offensive");
        var deny = PlayerAgencyActions.declareWar(state, "player:goals", enemy.id(), 0, WarGoalType.DEFENSE);
        check(!deny.success() && "defense_not_offensive".equals(deny.reason()),
                "agency rejects DEFENSE declare: " + deny.reason());
    }

    private static void rulerUnilateralNoblePetition() {
        SimulationState state = new SimulationState(0x52414E4BL);
        var founded = PlayerSettlementFounder.found(state, "player:rank", "Sovereign", "Rankford",
                new SimPosition(30_000, 30_000));
        check(founded.success(), "found");
        Faction self = state.findFaction(founded.factionId()).orElseThrow();
        Faction enemy = new Faction(state.nextId(), "Rank Rival", "Rival");
        enemy.addSettlement(new Settlement(state.nextId(), "Rivalrank", new SimPosition(32_200, 30_000), 800, 900));
        state.addFaction(enemy);
        var rulerOpts = WarRoomOptionsBuilder.declareOptions(state, "player:rank", enemy.id());
        check(rulerOpts.canDeclareUnilateral(), "ruler may declare");
        check(!rulerOpts.canPetitionOnly(), "ruler is not petition-only");

        PlayerStanding standing = state.playerStanding("player:rank");
        standing.restoreMembership(self.id(), FactionRank.NOBLE, state.clock().day(), 1200, 0);
        check(standing.rank() == FactionRank.NOBLE, "forced noble");
        var nobleOpts = WarRoomOptionsBuilder.declareOptions(state, "player:rank", enemy.id());
        check(!nobleOpts.canDeclareUnilateral(), "noble cannot unilaterally declare");
        check(nobleOpts.canPetitionOnly(), "noble may petition");
        var declare = PlayerAgencyActions.declareWar(state, "player:rank", enemy.id());
        check(!declare.success() && "petition_only".equals(declare.reason()),
                "declareWar rejects noble: " + declare.reason());
        var petition = PlayerAgencyActions.petitionWar(state, "player:rank", enemy.id());
        check(petition.success(), "noble petition ok: " + petition.reason());
        check(self.relationWith(enemy.id()).status() != RelationStatus.WAR, "petition did not open war");
    }

    private static void treatyBreakCosts() {
        SimulationState state = new SimulationState(0x54525459L);
        var founded = PlayerSettlementFounder.found(state, "player:treaty", "Breaker", "Treatyburg",
                new SimPosition(34_000, 34_000));
        check(founded.success(), "found");
        Faction self = state.findFaction(founded.factionId()).orElseThrow();
        Faction other = new Faction(state.nextId(), "Treaty Peer", "Peer");
        Settlement capital = new Settlement(state.nextId(), "Peerkeep", new SimPosition(36_200, 34_000), 1000, 1100);
        other.addSettlement(capital);
        state.addFaction(other);
        state.addTreaty(new Treaty(state.nextId(), self.id(), other.id(), TreatyType.NON_AGGRESSION,
                state.clock().day(), state.clock().day() + 200));
        state.addTreaty(new Treaty(state.nextId(), self.id(), other.id(), TreatyType.PEACE_TREATY,
                state.clock().day(), state.clock().day() + 180));
        var options = WarRoomOptionsBuilder.declareOptions(state, "player:treaty", other.id());
        check(options.treatiesToBreak().size() >= 2, "peace + non-aggression listed");
        check(options.treatiesToBreak().stream().allMatch(c -> c.legitimacyCost() > 0 && c.reputationCost() > 0),
                "break costs are real");
        double legitBefore = self.government().legitimacy();
        var war = PlayerAgencyActions.declareWar(state, "player:treaty", other.id(), capital.id(), WarGoalType.CONQUEST);
        check(war.success(), "declare with treaty break: " + war.reason());
        check(self.relationWith(other.id()).status() == RelationStatus.WAR, "at war");
        check(state.treaties().stream().noneMatch(t -> t.active() && t.between(self.id(), other.id())
                        && (t.type() == TreatyType.NON_AGGRESSION || t.type() == TreatyType.PEACE_TREATY)),
                "war-breaking treaties terminated");
        check(self.government().legitimacy() < legitBefore, "legitimacy cost applied");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
