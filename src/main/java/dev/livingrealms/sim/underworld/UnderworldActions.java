package dev.livingrealms.sim.underworld;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.law.CrimeResult;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Objects;

/**
 * Underworld contracts are completed only by real {@link dev.livingrealms.sim.law.CrimeEngine}
 * incidents — never by fake counters alone.
 */
public final class UnderworldActions {
    private UnderworldActions() {}

    public record Result(boolean success, boolean dirty, String reason) {
        public static Result ok(String reason) { return new Result(true, true, reason == null ? "" : reason); }
        public static Result fail(String reason) { return new Result(false, false, reason == null ? "" : reason); }
    }

    /** Executes a contract by filing a real crime incident; success requires a witnessed report. */
    public static Result completeContract(SimulationState state, String actorKey, long jurisdictionFactionId,
                                          CrimeType type, double value, SimPosition position,
                                          int witnessCount, String victimKey) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        if (actorKey == null || actorKey.isBlank() || jurisdictionFactionId <= 0) return Result.fail("invalid");
        if (state.findFaction(jurisdictionFactionId).isEmpty()) return Result.fail("jurisdiction_missing");
        int witnesses = Math.max(1, witnessCount);
        CrimeResult crime = state.reportCrime(actorKey, jurisdictionFactionId, type, Math.max(1, value),
                position, true, witnesses, victimKey == null ? "" : victimKey, "underworld_contract");
        if (!crime.registered()) return Result.fail("contract_unreported:" + crime.reason());
        UnderworldProfile profile = state.underworldProfile(actorKey);
        double cred = 4 + type.notoriety() * .12 + Math.min(8, value * .02);
        profile.recordContract(state.clock().day(), cred);
        state.history().add(new WorldEvent(state.clock().day(), "underworld_contract",
                "actor=" + actorKey + ", crime=" + type.name() + ", contracts=" + profile.contractsCompleted()));
        return Result.ok("contract_" + type.name().toLowerCase());
    }

    public static Result bribeOfficial(SimulationState state, String actorKey, long factionId, double offer) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank() || factionId <= 0) return Result.fail("invalid");
        if (!(offer > 0) || !Double.isFinite(offer)) return Result.fail("invalid_offer");
        Faction faction = state.findFaction(factionId).orElse(null);
        if (faction == null) return Result.fail("faction_missing");
        UnderworldProfile profile = state.underworldProfile(actorKey);
        double corruption = faction.government().corruption();
        double need = Math.max(8, 28 - profile.briberySkill() * .15 - corruption * 18);
        if (offer + 1e-9 < need) return Result.fail("bribe_too_low");
        double paid = state.payFine(actorKey, factionId, offer);
        if (paid <= 0) {
            // Still allow bribery to reduce heat even without an open bounty — pay treasury directly.
            faction.addTreasury(offer * .85);
        }
        profile.recordBribe(state.clock().day(), 3 + Math.min(8, offer * .08));
        state.history().add(new WorldEvent(state.clock().day(), "underworld_bribe",
                "actor=" + actorKey + ", faction=" + factionId + ", offer=" + Math.round(offer)));
        return Result.ok("bribed");
    }

    public static boolean canAccessBlackMarket(SimulationState state, String actorKey) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank()) return false;
        return state.findUnderworldProfile(actorKey).map(UnderworldProfile::isBlackMarketEligible).orElse(false);
    }
}
