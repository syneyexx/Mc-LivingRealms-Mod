package dev.livingrealms.sim.military;

import dev.livingrealms.sim.diplomacy.Treaty;
import dev.livingrealms.sim.diplomacy.TreatyType;
import dev.livingrealms.sim.diplomacy.WarGoalType;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.player.FactionRank;
import dev.livingrealms.sim.player.PlayerStanding;
import dev.livingrealms.sim.world.SettlementTransfer;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure sim options for the Wars dashboard / war-room agency verbs.
 * UI and tests share this so escort never targets an enemy settlement and armies are not truncated to the first entry.
 */
public final class WarRoomOptionsBuilder {
    /** Treaties that must be broken (with legitimacy/reputation cost) before an offensive declare. */
    private static final Set<TreatyType> WAR_BREAKING = EnumSet.of(
            TreatyType.NON_AGGRESSION, TreatyType.DEFENSIVE_ALLIANCE, TreatyType.PEACE_TREATY, TreatyType.MILITARY_ACCESS);

    public enum EscortKind { SHIPMENT, FRIENDLY_SETTLEMENT }

    public record TreatyBreakCost(long treatyId, TreatyType type, double legitimacyCost, double reputationCost) {
        public TreatyBreakCost {
            if (treatyId <= 0) throw new IllegalArgumentException("treatyId");
            Objects.requireNonNull(type, "type");
            legitimacyCost = Math.max(0, legitimacyCost);
            reputationCost = Math.max(0, reputationCost);
        }
    }

    public record EscortTarget(long id, EscortKind kind, String label) {
        public EscortTarget {
            if (id <= 0) throw new IllegalArgumentException("id");
            Objects.requireNonNull(kind, "kind");
            label = label == null ? "" : label;
        }
    }

    public record DeclareOptions(
            boolean canDeclareUnilateral,
            boolean canPetitionOnly,
            List<WarGoalType> validGoals,
            List<TreatyBreakCost> treatiesToBreak,
            long suggestedTargetSettlementId,
            String denyReason
    ) {
        public DeclareOptions {
            validGoals = List.copyOf(validGoals == null ? List.of() : validGoals);
            treatiesToBreak = List.copyOf(treatiesToBreak == null ? List.of() : treatiesToBreak);
            denyReason = denyReason == null ? "" : denyReason;
        }
    }

    public record ArmyOrderOptions(
            List<Long> armyIds,
            List<Long> hostileSettlementIds,
            List<EscortTarget> escortTargets,
            List<Long> patrolSettlementIds,
            long defaultHostileSettlementId,
            long defaultEscortTargetId,
            long defaultPatrolSettlementId
    ) {
        public ArmyOrderOptions {
            armyIds = List.copyOf(armyIds == null ? List.of() : armyIds);
            hostileSettlementIds = List.copyOf(hostileSettlementIds == null ? List.of() : hostileSettlementIds);
            escortTargets = List.copyOf(escortTargets == null ? List.of() : escortTargets);
            patrolSettlementIds = List.copyOf(patrolSettlementIds == null ? List.of() : patrolSettlementIds);
        }
    }

    private WarRoomOptionsBuilder() {}

    /** Offensive war goals only — {@link WarGoalType#DEFENSE} is never offered for initiation. */
    public static List<WarGoalType> offensiveGoals(SimulationState state, Faction attacker, Faction defender) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        List<WarGoalType> goals = new ArrayList<>();
        double ratio = power(attacker) / Math.max(1, power(defender));
        double opinion = attacker.relationWith(defender.id()).opinion();
        boolean liberationCue = defender.settlements().stream().anyMatch(s -> s.unrest() > .55)
                || defender.government().stability() < .45;
        boolean poorAttacker = attacker.treasury() < defender.treasury() * .9;
        // Contextual offensive slate; DEFENSE is excluded by construction.
        goals.add(WarGoalType.CONQUEST);
        if (liberationCue && opinion < -10) goals.add(WarGoalType.LIBERATION);
        if (opinion < -35 && ratio > 1.05 && poorAttacker) goals.add(WarGoalType.REPARATIONS);
        if (opinion < -25 && ratio > 1.25) goals.add(WarGoalType.HUMILIATION);
        if (!goals.contains(WarGoalType.LIBERATION) && opinion < -20 && ratio > 1.1) {
            goals.add(WarGoalType.LIBERATION);
        }
        if (!goals.contains(WarGoalType.REPARATIONS) && poorAttacker && opinion < -20) {
            goals.add(WarGoalType.REPARATIONS);
        }
        if (!goals.contains(WarGoalType.HUMILIATION) && ratio > 1.6) {
            goals.add(WarGoalType.HUMILIATION);
        }
        return List.copyOf(goals);
    }

    public static boolean isValidOffensiveGoal(WarGoalType goal) {
        return goal != null && goal != WarGoalType.DEFENSE;
    }

    public static DeclareOptions declareOptions(SimulationState state, String actorKey, long enemyFactionId) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank() || enemyFactionId <= 0) {
            return denied("invalid");
        }
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null || !standing.isMember()) return denied("not_member");
        long selfId = standing.memberFactionId();
        if (selfId == enemyFactionId) return denied("self_target");
        Faction self = state.findFaction(selfId).orElse(null);
        Faction enemy = state.findFaction(enemyFactionId).orElse(null);
        if (self == null || enemy == null) return denied("faction_missing");
        if (self.relationWith(enemyFactionId).status() == RelationStatus.WAR
                || state.wars().stream().anyMatch(w -> w.active() && w.between(selfId, enemyFactionId))) {
            return denied("already_at_war");
        }
        boolean ruler = standing.rank() == FactionRank.RULER;
        boolean noble = standing.rank() == FactionRank.NOBLE;
        if (!ruler && !noble) return denied("rank_too_low");
        List<WarGoalType> goals = offensiveGoals(state, self, enemy);
        List<TreatyBreakCost> breaks = treatiesToBreak(state, selfId, enemyFactionId);
        Settlement target = SettlementTransfer.capitalTarget(enemy);
        long targetId = target == null ? 0 : target.id();
        if (ruler) {
            return new DeclareOptions(true, false, goals, breaks, targetId, "");
        }
        // Nobles may petition the court; they cannot unilaterally declare.
        return new DeclareOptions(false, true, goals, breaks, targetId, "petition_only");
    }

    public static List<TreatyBreakCost> treatiesToBreak(SimulationState state, long selfId, long enemyId) {
        Objects.requireNonNull(state, "state");
        List<TreatyBreakCost> out = new ArrayList<>();
        for (Treaty t : state.treaties()) {
            if (!t.active() || !t.between(selfId, enemyId) || !WAR_BREAKING.contains(t.type())) continue;
            out.add(new TreatyBreakCost(t.id(), t.type(), legitimacyCost(t.type()), reputationCost(t.type())));
        }
        out.sort(Comparator.comparingLong(TreatyBreakCost::treatyId));
        return List.copyOf(out);
    }

    public static ArmyOrderOptions armyOrderOptions(SimulationState state, String actorKey) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank()) {
            return new ArmyOrderOptions(List.of(), List.of(), List.of(), List.of(), 0, 0, 0);
        }
        PlayerStanding standing = state.findPlayerStanding(actorKey).orElse(null);
        if (standing == null || !standing.isMember()) {
            return new ArmyOrderOptions(List.of(), List.of(), List.of(), List.of(), 0, 0, 0);
        }
        long selfId = standing.memberFactionId();
        Faction self = state.findFaction(selfId).orElse(null);
        if (self == null) {
            return new ArmyOrderOptions(List.of(), List.of(), List.of(), List.of(), 0, 0, 0);
        }
        List<Long> armies = self.armies().stream()
                .filter(a -> !a.destroyed())
                .sorted(Comparator.comparingLong(Army::id))
                .map(Army::id)
                .toList();
        List<Long> hostile = new ArrayList<>();
        for (var war : state.wars()) {
            if (!war.active() || !war.involves(selfId)) continue;
            long enemyId = war.attackerFactionId() == selfId ? war.defenderFactionId() : war.attackerFactionId();
            Faction enemy = state.findFaction(enemyId).orElse(null);
            if (enemy == null) continue;
            if (war.targetSettlementId() > 0
                    && enemy.settlements().stream().anyMatch(s -> s.id() == war.targetSettlementId())) {
                if (!hostile.contains(war.targetSettlementId())) hostile.add(war.targetSettlementId());
            }
            for (Settlement s : enemy.settlements()) {
                if (!hostile.contains(s.id())) hostile.add(s.id());
            }
        }
        List<EscortTarget> escort = escortTargets(state, selfId);
        List<Long> patrol = self.settlements().stream()
                .sorted(Comparator.comparingInt(Settlement::population).reversed().thenComparingLong(Settlement::id))
                .map(Settlement::id)
                .toList();
        long defaultHostile = hostile.isEmpty() ? 0 : hostile.getFirst();
        long defaultEscort = escort.isEmpty() ? 0 : escort.getFirst().id();
        long defaultPatrol = patrol.isEmpty() ? 0 : patrol.getFirst();
        return new ArmyOrderOptions(armies, hostile, escort, patrol, defaultHostile, defaultEscort, defaultPatrol);
    }

    /**
     * Escort targets are the player's in-flight shipments and own/friendly settlements.
     * Enemy settlements are never eligible.
     */
    public static List<EscortTarget> escortTargets(SimulationState state, long factionId) {
        Objects.requireNonNull(state, "state");
        if (factionId <= 0) return List.of();
        Faction self = state.findFaction(factionId).orElse(null);
        if (self == null) return List.of();
        List<EscortTarget> out = new ArrayList<>();
        for (TradeShipment shipment : state.shipments()) {
            if (shipment.arrived()) continue;
            if (shipment.sellerFactionId() != factionId && shipment.buyerFactionId() != factionId) continue;
            out.add(new EscortTarget(shipment.id(), EscortKind.SHIPMENT,
                    shipment.resource().name() + " caravan"));
        }
        out.sort(Comparator.comparingLong(EscortTarget::id));
        List<EscortTarget> settlements = new ArrayList<>();
        for (Settlement s : self.settlements()) {
            settlements.add(new EscortTarget(s.id(), EscortKind.FRIENDLY_SETTLEMENT, s.name()));
        }
        // Friendly allied settlements (not at war with us) may also be escorted to.
        for (Faction other : state.factions()) {
            if (other.id() == factionId) continue;
            RelationStatus status = self.relationWith(other.id()).status();
            if (status != RelationStatus.ALLIED && status != RelationStatus.FRIENDLY) continue;
            if (state.wars().stream().anyMatch(w -> w.active() && w.between(factionId, other.id()))) continue;
            for (Settlement s : other.settlements()) {
                settlements.add(new EscortTarget(s.id(), EscortKind.FRIENDLY_SETTLEMENT, s.name()));
            }
        }
        settlements.sort(Comparator.comparingLong(EscortTarget::id));
        out.addAll(settlements);
        return List.copyOf(out);
    }

    /** True when {@code targetId} is a valid escort target for the faction (shipment or friendly settlement). */
    public static boolean isValidEscortTarget(SimulationState state, long factionId, long targetId) {
        if (targetId <= 0 || factionId <= 0) return false;
        return escortTargets(state, factionId).stream().anyMatch(t -> t.id() == targetId);
    }

    private static DeclareOptions denied(String reason) {
        return new DeclareOptions(false, false, List.of(), List.of(), 0, reason);
    }

    private static double legitimacyCost(TreatyType type) {
        return switch (type) {
            case PEACE_TREATY -> .08;
            case NON_AGGRESSION -> .06;
            case DEFENSIVE_ALLIANCE -> .10;
            case MILITARY_ACCESS -> .04;
            default -> .03;
        };
    }

    private static double reputationCost(TreatyType type) {
        return switch (type) {
            case PEACE_TREATY -> 18;
            case NON_AGGRESSION -> 14;
            case DEFENSIVE_ALLIANCE -> 22;
            case MILITARY_ACCESS -> 8;
            default -> 6;
        };
    }

    private static double power(Faction f) {
        return f.armies().stream().mapToDouble(Army::combatPower).sum() + f.population() * .02;
    }
}
