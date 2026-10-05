package dev.livingrealms.sim.player;

import dev.livingrealms.sim.diplomacy.Treaty;
import dev.livingrealms.sim.diplomacy.TreatyType;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.military.MilitaryObjective;
import dev.livingrealms.sim.military.MilitaryObjectiveType;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Comparator;
import java.util.Objects;

/** Hands-on ruler/diplomat/commander verbs with real canonical state effects. */
public final class PlayerAgencyActions {
    private PlayerAgencyActions() {}

    public record Result(boolean success, boolean dirty, String reason) {
        public static Result ok(String reason) { return new Result(true, true, reason == null ? "" : reason); }
        public static Result fail(String reason) { return new Result(false, false, reason == null ? "" : reason); }
    }

    public static Result abdicate(SimulationState state, String actorKey) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank()) return Result.fail("invalid_actor");
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null || !standing.isMember() || standing.rank() != FactionRank.RULER) {
            return Result.fail("not_ruler");
        }
        long factionId = standing.memberFactionId();
        Faction faction = state.findFaction(factionId).orElse(null);
        if (faction == null) return Result.fail("faction_missing");
        // Prefer an existing noble/heir social citizen; otherwise demote player to NOBLE then leave.
        SocialCitizen heir = state.socialCitizens().stream()
                .filter(c -> c.alive() && c.factionId() == factionId)
                .filter(c -> c.role() == CitizenRole.OFFICIAL || c.role() == CitizenRole.PRIEST
                        || c.role() == CitizenRole.SCHOLAR || c.role() == CitizenRole.GUARD)
                .max(Comparator.comparingInt((SocialCitizen c) -> switch (c.role()) {
                    case OFFICIAL -> 4;
                    case PRIEST -> 3;
                    case SCHOLAR -> 2;
                    default -> 1;
                }).thenComparingLong(SocialCitizen::id))
                .orElse(null);
        String heirName = heir == null ? "the court" : heir.name();
        if (heir != null) {
            faction.setRulerName(heir.name());
        } else {
            faction.setRulerName("Regent Council");
        }
        standing.leave(true);
        state.history().add(new WorldEvent(state.clock().day(), "player_abdicated",
                "actor=" + actorKey + ", faction=" + factionId + ", successor=" + heirName));
        return Result.ok("abdicated");
    }

    public static Result petitionPeace(SimulationState state, String actorKey, long enemyFactionId) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank() || enemyFactionId <= 0) return Result.fail("invalid");
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null || !standing.isMember()) return Result.fail("not_member");
        if (standing.rank() != FactionRank.RULER && standing.rank() != FactionRank.NOBLE) {
            return Result.fail("rank_too_low");
        }
        long selfId = standing.memberFactionId();
        if (selfId == enemyFactionId) return Result.fail("self_target");
        Faction self = state.findFaction(selfId).orElse(null);
        Faction enemy = state.findFaction(enemyFactionId).orElse(null);
        if (self == null || enemy == null) return Result.fail("faction_missing");
        WarState war = state.wars().stream()
                .filter(w -> w.active() && w.between(selfId, enemyFactionId))
                .findFirst().orElse(null);
        if (war == null && self.relationWith(enemyFactionId).status() != RelationStatus.WAR) {
            return Result.fail("not_at_war");
        }
        // Peace requires some exhaustion or long duration, or ruler with enough crown influence.
        long duration = war == null ? 0 : state.clock().day() - war.startDay();
        double exhaustion = war == null ? 0 : Math.max(war.attackerExhaustion(), war.defenderExhaustion());
        boolean influenceOk = standing.influenceWith(selfId, InfluenceInstitution.CROWN) >= 15
                || standing.rank() == FactionRank.RULER;
        if (!influenceOk) return Result.fail("insufficient_influence");
        if (duration < 8 && exhaustion < .35 && standing.rank() != FactionRank.RULER) {
            return Result.fail("war_not_ripe");
        }
        self.relationWith(enemyFactionId).makePeace();
        enemy.relationWith(selfId).makePeace();
        if (war != null) war.end();
        Treaty peace = new Treaty(state.nextId(), selfId, enemyFactionId, TreatyType.PEACE_TREATY,
                state.clock().day(), state.clock().day() + 180);
        state.addTreaty(peace);
        standing.adjustInfluence(selfId, InfluenceInstitution.CROWN, -4);
        standing.adjustReputation(enemyFactionId, 5);
        state.history().add(new WorldEvent(state.clock().day(), "player_peace_petition",
                "actor=" + actorKey + ", with=" + enemyFactionId));
        return Result.ok("peace_treaty");
    }

    public static Result proposeTradePact(SimulationState state, String actorKey, long otherFactionId) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank() || otherFactionId <= 0) return Result.fail("invalid");
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null || !standing.isMember()) return Result.fail("not_member");
        long selfId = standing.memberFactionId();
        if (selfId == otherFactionId) return Result.fail("self_target");
        Faction self = state.findFaction(selfId).orElse(null);
        Faction other = state.findFaction(otherFactionId).orElse(null);
        if (self == null || other == null) return Result.fail("faction_missing");
        if (self.relationWith(otherFactionId).status() == RelationStatus.WAR) return Result.fail("at_war");
        if (standing.influenceWith(selfId, InfluenceInstitution.MERCHANTS) < 12
                && standing.rank() != FactionRank.RULER) {
            return Result.fail("insufficient_influence");
        }
        if (state.treaties().stream().anyMatch(t -> t.active() && t.type() == TreatyType.TRADE_PACT
                && ((t.factionA() == selfId && t.factionB() == otherFactionId)
                || (t.factionA() == otherFactionId && t.factionB() == selfId)))) {
            return Result.fail("pact_exists");
        }
        self.relationWith(otherFactionId).setTradeAgreement(true);
        other.relationWith(selfId).setTradeAgreement(true);
        Treaty pact = new Treaty(state.nextId(), selfId, otherFactionId, TreatyType.TRADE_PACT,
                state.clock().day(), state.clock().day() + 240);
        state.addTreaty(pact);
        standing.adjustInfluence(selfId, InfluenceInstitution.MERCHANTS, -3);
        standing.grantCareerService(CareerTrack.ECONOMIC, 12);
        state.history().add(new WorldEvent(state.clock().day(), "player_trade_pact",
                "actor=" + actorKey + ", with=" + otherFactionId));
        return Result.ok("trade_pact");
    }

    public static Result requestMilitarySupportMission(SimulationState state, String actorKey, long factionId) {
        // Stronger military support: issue DEFEND objective on home for first army.
        Objects.requireNonNull(state, "state");
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null) return Result.fail("no_standing");
        if (!PlayerInfluenceActions.can(standing, factionId, PlayerInfluenceActions.Unlock.REQUEST_MILITARY_SUPPORT)) {
            return Result.fail("insufficient_influence");
        }
        Faction faction = state.findFaction(factionId).orElse(null);
        if (faction == null) return Result.fail("faction_missing");
        Army army = faction.armies().stream().findFirst().orElse(null);
        if (army == null) return Result.fail("no_army");
        Settlement home = faction.settlements().stream()
                .max(Comparator.comparingInt(Settlement::population)).orElse(null);
        if (home == null) return Result.fail("no_home");
        replaceArmyObjective(state, army, factionId, MilitaryObjectiveType.DEFEND, home.id(), home.position(), 120);
        standing.adjustInfluence(factionId, InfluenceInstitution.MILITARY, -5);
        standing.grantCareerService(CareerTrack.MILITARY, 20);
        army.adjustMorale(.04);
        state.history().add(new WorldEvent(state.clock().day(), "player_military_support",
                "actor=" + actorKey + ", faction=" + factionId + ", army=" + army.id() + ", defend=" + home.id()));
        return Result.ok("military_support_defend");
    }

    public static Result armyOrder(SimulationState state, String actorKey, long armyId, MilitaryObjectiveType type) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
        if (actorKey == null || actorKey.isBlank() || armyId <= 0) return Result.fail("invalid");
        if (type != MilitaryObjectiveType.DEFEND && type != MilitaryObjectiveType.RETREAT
                && type != MilitaryObjectiveType.PATROL_BORDER) {
            return Result.fail("unsupported_order");
        }
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null || !standing.isMember()) return Result.fail("not_member");
        if (standing.rank() != FactionRank.RULER && standing.rank() != FactionRank.NOBLE
                && standing.influenceWith(standing.memberFactionId(), InfluenceInstitution.MILITARY) < 20) {
            return Result.fail("insufficient_command");
        }
        Army army = state.findArmy(armyId).orElse(null);
        if (army == null || army.destroyed()) return Result.fail("army_missing");
        if (army.factionId() != standing.memberFactionId()) return Result.fail("not_own_army");
        Faction faction = state.findFaction(army.factionId()).orElse(null);
        if (faction == null) return Result.fail("faction_missing");
        Settlement home = faction.settlements().stream()
                .min(Comparator.comparingDouble(s -> s.position().distanceTo(army.position())))
                .orElse(null);
        if (home == null) return Result.fail("no_home");
        int priority = type == MilitaryObjectiveType.DEFEND ? 150
                : type == MilitaryObjectiveType.RETREAT ? 140 : 80;
        replaceArmyObjective(state, army, faction.id(), type, home.id(), home.position(), priority);
        if (type == MilitaryObjectiveType.RETREAT) {
            army.resupply(.05);
            army.adjustMorale(.02);
        } else if (type == MilitaryObjectiveType.DEFEND) {
            army.moveToward(home.position(), Math.min(80, army.position().distanceTo(home.position())));
            army.adjustMorale(.03);
        } else {
            army.adjustMorale(.01);
        }
        state.history().add(new WorldEvent(state.clock().day(), "player_army_order",
                "actor=" + actorKey + ", army=" + armyId + ", order=" + type.name() + ", home=" + home.id()));
        return Result.ok("army_" + type.name().toLowerCase());
    }

    private static void replaceArmyObjective(SimulationState state, Army army, long factionId,
                                             MilitaryObjectiveType type, long settlementId,
                                             dev.livingrealms.sim.world.SimPosition pos, int priority) {
        for (MilitaryObjective o : state.objectives()) {
            if (!o.complete() && o.armyId() == army.id()) o.markComplete();
        }
        state.addObjective(new MilitaryObjective(state.nextId(), army.id(), factionId, type,
                0, settlementId, pos, state.clock().day(), priority));
    }
}
