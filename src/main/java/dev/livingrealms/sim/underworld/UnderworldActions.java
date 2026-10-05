package dev.livingrealms.sim.underworld;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.law.CrimeIncident;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.sim.law.JurisdictionWanted;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Underworld contracts complete only when a matching real {@link CrimeIncident} is observed —
 * never by fabricating crimes from the contract API alone.
 */
public final class UnderworldActions {
    public static final int DEFAULT_CONTRACT_TTL_DAYS = 21;
    public static final int CLOSED_CONTRACT_RETENTION_DAYS = 60;

    private UnderworldActions() {}

    public record Result(boolean success, boolean dirty, String reason) {
        public static Result ok(String reason) { return new Result(true, true, reason == null ? "" : reason); }
        public static Result fail(String reason) { return new Result(false, false, reason == null ? "" : reason); }
    }

    public record MatchResult(boolean matched, long contractId, String reason) {
        public static MatchResult none(String reason) { return new MatchResult(false, 0, reason == null ? "" : reason); }
        public static MatchResult hit(long contractId, String reason) {
            return new MatchResult(true, contractId, reason == null ? "" : reason);
        }
    }

    /** Posts an AVAILABLE contract. Does not complete anything. */
    public static Result offerContract(SimulationState state, UnderworldContractType type,
                                       long jurisdictionFactionId, String targetVictimKey,
                                       double minValue, double reward, int ttlDays) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
        if (jurisdictionFactionId <= 0) return Result.fail("invalid");
        if (state.findFaction(jurisdictionFactionId).isEmpty()) return Result.fail("jurisdiction_missing");
        if (!Double.isFinite(minValue) || minValue < 0) return Result.fail("invalid_min_value");
        if (!Double.isFinite(reward) || reward < 0) return Result.fail("invalid_reward");
        expireDue(state);
        if (state.underworldContracts().size() >= SimulationState.MAX_UNDERWORLD_CONTRACTS) {
            pruneClosedContracts(state);
        }
        if (state.underworldContracts().size() >= SimulationState.MAX_UNDERWORLD_CONTRACTS) {
            return Result.fail("contract_cap");
        }
        long day = state.clock().day();
        int ttl = Math.max(1, ttlDays <= 0 ? DEFAULT_CONTRACT_TTL_DAYS : ttlDays);
        UnderworldContract contract = new UnderworldContract(
                state.nextId(), type, jurisdictionFactionId,
                targetVictimKey == null ? "" : targetVictimKey,
                minValue, reward, day, day + ttl);
        state.addUnderworldContract(contract);
        state.history().add(new WorldEvent(day, "underworld_contract_offered",
                "id=" + contract.id() + ", type=" + type.name() + ", faction=" + jurisdictionFactionId));
        return Result.ok("offered_" + contract.id());
    }

    public static Result acceptContract(SimulationState state, String actorKey, long contractId) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank() || contractId <= 0) return Result.fail("invalid");
        expireDue(state);
        UnderworldContract contract = state.findUnderworldContract(contractId).orElse(null);
        if (contract == null) return Result.fail("unknown_contract");
        if (contract.status() != UnderworldContract.Status.AVAILABLE) return Result.fail("not_available");
        if (state.clock().day() > contract.expiresDay()) {
            contract.expire(state.clock().day());
            return Result.fail("expired");
        }
        long openAccepted = state.underworldContracts().stream()
                .filter(c -> c.status() == UnderworldContract.Status.ACCEPTED && actorKey.equals(c.acceptorActorKey()))
                .count();
        if (openAccepted >= SimulationState.MAX_ACCEPTED_CONTRACTS_PER_ACTOR) {
            return Result.fail("actor_contract_cap");
        }
        if (!contract.accept(actorKey, state.clock().day())) return Result.fail("accept_failed");
        state.underworldProfile(actorKey); // ensure profile exists
        state.history().add(new WorldEvent(state.clock().day(), "underworld_contract_accepted",
                "id=" + contractId + ", actor=" + actorKey + ", type=" + contract.type().name()));
        return Result.ok("accepted_" + contractId);
    }

    /**
     * Matches ACCEPTED contracts against a real crime incident. Does not invent crimes.
     * Safe to call for every reported incident (witnessed or not).
     */
    public static MatchResult observeCrime(SimulationState state, CrimeIncident incident) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(incident, "incident");
        expireDue(state);
        depositStolenGoods(state, incident);
        Optional<UnderworldContract> match = state.underworldContracts().stream()
                .filter(c -> c.matchesCrime(incident))
                .min(Comparator.comparingLong(UnderworldContract::acceptedDay)
                        .thenComparingLong(UnderworldContract::id));
        if (match.isEmpty()) return MatchResult.none("no_match");
        UnderworldContract contract = match.get();
        if (!contract.complete(incident.id(), state.clock().day())) return MatchResult.none("complete_failed");
        UnderworldProfile profile = state.underworldProfile(incident.actorKey());
        double cred = 4 + contract.type().crimeType().notoriety() * .12
                + Math.min(8, Math.max(contract.reward(), incident.stolenOrDamageValue()) * .02);
        profile.recordContract(state.clock().day(), cred);
        state.history().add(new WorldEvent(state.clock().day(), "underworld_contract",
                "actor=" + incident.actorKey() + ", contract=" + contract.id()
                        + ", crime=" + incident.type().name()
                        + ", contracts=" + profile.contractsCompleted()));
        pruneClosedContracts(state);
        return MatchResult.hit(contract.id(), "completed_" + contract.type().name().toLowerCase());
    }

    /**
     * @deprecated Contracts cannot fabricate crimes. Use {@link #acceptContract} then a real
     * {@link SimulationState#reportCrime} / {@link #observeCrime} path.
     */
    @Deprecated
    public static Result completeContract(SimulationState state, String actorKey, long jurisdictionFactionId,
                                          CrimeType type, double value, Object position,
                                          int witnessCount, String victimKey) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        if (actorKey == null || actorKey.isBlank() || jurisdictionFactionId <= 0) return Result.fail("invalid");
        if (!Double.isFinite(value) || value < 0 || witnessCount < 0) return Result.fail("invalid");
        if (victimKey == null) return Result.fail("invalid");
        return Result.fail("contracts_require_real_crime");
    }

    public static Result bribeOfficial(SimulationState state, String actorKey, long factionId, double offer) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank() || factionId <= 0) return Result.fail("invalid");
        if (!(offer > 0) || !Double.isFinite(offer)) return Result.fail("invalid_offer");
        Faction faction = state.findFaction(factionId).orElse(null);
        if (faction == null) return Result.fail("faction_missing");
        UnderworldProfile profile = state.underworldProfile(actorKey);
        double corruption = faction.government().corruption();
        double heat = state.crimeLedger().findProfile(actorKey)
                .flatMap(p -> p.find(factionId))
                .map(JurisdictionWanted::heat)
                .orElse(0.0);
        double bounty = state.crimeLedger().findProfile(actorKey)
                .flatMap(p -> p.find(factionId))
                .map(JurisdictionWanted::bounty)
                .orElse(0.0);
        boolean hasOpenContract = state.underworldContracts().stream()
                .anyMatch(c -> c.status() == UnderworldContract.Status.ACCEPTED
                        && actorKey.equals(c.acceptorActorKey())
                        && c.jurisdictionFactionId() == factionId);
        // Heat, open bounty, and an active job in this realm raise the bribe floor; corruption and skill lower it.
        double need = Math.max(8, 28 - profile.briberySkill() * .15 - corruption * 18
                + heat * .22 + Math.min(40, bounty * .04) + (hasOpenContract ? 6 : 0));
        if (offer + 1e-9 < need) return Result.fail("bribe_too_low");
        double paid = state.payFine(actorKey, factionId, offer);
        if (paid <= 0) {
            faction.addTreasury(offer * .85);
        }
        profile.recordBribe(state.clock().day(), 3 + Math.min(8, offer * .08));
        state.history().add(new WorldEvent(state.clock().day(), "underworld_bribe",
                "actor=" + actorKey + ", faction=" + factionId + ", offer=" + Math.round(offer)
                        + ", need=" + Math.round(need) + ", heat=" + Math.round(heat)));
        return Result.ok("bribed");
    }

    public static boolean canAccessBlackMarket(SimulationState state, String actorKey) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank()) return false;
        return state.findUnderworldProfile(actorKey).map(UnderworldProfile::isBlackMarketEligible).orElse(false);
    }

    public static void expireDue(SimulationState state) {
        Objects.requireNonNull(state, "state");
        long day = state.clock().day();
        for (UnderworldContract c : state.underworldContracts()) {
            if (c.open() && day > c.expiresDay()) c.expire(day);
        }
    }

    public static int pruneClosedContracts(SimulationState state) {
        Objects.requireNonNull(state, "state");
        long day = state.clock().day();
        long cutoff = Math.max(0, day - CLOSED_CONTRACT_RETENTION_DAYS);
        List<UnderworldContract> keep = new ArrayList<>();
        int removed = 0;
        for (UnderworldContract c : state.underworldContracts()) {
            if (c.closed() && c.closedDay() >= 0 && c.closedDay() < cutoff) {
                removed++;
                continue;
            }
            keep.add(c);
        }
        if (keep.size() > SimulationState.MAX_UNDERWORLD_CONTRACTS) {
            keep.sort(Comparator
                    .comparing((UnderworldContract c) -> c.open() ? 1 : 0)
                    .thenComparingLong(c -> c.closed() ? c.closedDay() : Long.MAX_VALUE)
                    .thenComparingLong(UnderworldContract::id));
            while (keep.size() > SimulationState.MAX_UNDERWORLD_CONTRACTS) {
                if (keep.getFirst().open()) break;
                keep.removeFirst();
                removed++;
            }
        }
        if (removed > 0) state.replaceUnderworldContracts(keep);
        state.stolenGoodsLedger().pruneSold(cutoff);
        return removed;
    }

    private static void depositStolenGoods(SimulationState state, CrimeIncident incident) {
        CrimeType type = incident.type();
        boolean loot = type == CrimeType.THEFT || type == CrimeType.BURGLARY
                || type == CrimeType.ROBBERY || type == CrimeType.SMUGGLING || type == CrimeType.POACHING;
        if (!loot || incident.stolenOrDamageValue() <= 0) return;
        String goodKey = StolenGoodsEntry.goodKeyForCrime(type, incident.evidence());
        StolenGoodsEntry entry = new StolenGoodsEntry(
                state.nextId(), incident.actorKey(), goodKey,
                incident.stolenOrDamageValue(), 1, incident.id(), incident.day());
        state.stolenGoodsLedger().deposit(entry);
    }
}
