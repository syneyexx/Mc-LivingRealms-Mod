package dev.livingrealms;

import dev.livingrealms.sim.civilization.DynastyState;
import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.civilization.SettlementCivilizationState;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.player.PlayerStanding;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.validation.CanonicalIntegrityService;
import dev.livingrealms.sim.validation.IntegrityReport;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimulationState;

/** Safe integrity repair: rebuildable projection indexes only; never invents missing realms. */
public final class IntegrityRecoveryTest {
    private IntegrityRecoveryTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x1A7E6L);
        DemoSeeder.seed(state);
        state.advanceDays(5);
        IntegrityReport clean = CanonicalIntegrityService.inspect(state);
        check(clean.fatalCanonical().isEmpty(), "seeded world must have no fatal integrity issues: " + clean.fatalCanonical());

        // Stale projection indexes (rebuildable)
        state.restoreSettlementCivilization(new SettlementCivilizationState(9_999_001L, state.factions().getFirst().id()));
        state.restoreFactionCivilization(new FactionCivilizationState(9_999_002L, "Ghost", "Null", "Void"));
        state.restoreDynasty(new DynastyState(9_999_003L, 0L, "House Phantom"));
        PlayerStanding standing = state.playerStanding("player:integrity");
        standing.restoreReputation(9_999_004L, 12.0);

        // Stale optional backlink: detach a household member so the backlink is unambiguous to clear
        SocialCitizen citizen = state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c -> c.householdId() > 0).findFirst()
                .orElseThrow(() -> new AssertionError("need a household-bound citizen"));
        long householdId = citizen.householdId();
        state.findHousehold(householdId).orElseThrow().removeMember(citizen.id());
        citizen.setHouseholdId(9_999_005L);

        IntegrityReport before = CanonicalIntegrityService.inspect(state);
        check(before.hasRebuildable(), "expected rebuildable projection issues");
        check(before.fatalCanonical().isEmpty(), "stale indexes alone must not be fatal: " + before.fatalCanonical());

        IntegrityReport repaired = CanonicalIntegrityService.repair(state);
        check(!repaired.repaired().isEmpty(), "repair must apply safe fixes");
        check(state.findSettlementCivilization(9_999_001L).isEmpty(), "stale settlement civilization purged");
        check(state.findFactionCivilization(9_999_002L).isEmpty(), "stale faction civilization purged");
        check(state.dynasties().get(9_999_003L) == null, "orphan dynasty purged");
        check(!standing.reputations().containsKey(9_999_004L), "stale reputation purged");
        check(citizen.householdId() == 0L, "stale household backlink cleared");
        check(!repaired.hasFatal(), "safe repair must not leave fatals: " + repaired.fatalCanonical());

        IntegrityReport after = CanonicalIntegrityService.inspect(state);
        check(after.rebuildableProjections().isEmpty(), "rebuildable issues should be cleared: " + after.rebuildableProjections());
        check(after.fatalCanonical().isEmpty(), "repair must not introduce fatals: " + after.fatalCanonical());

        // Fatal: missing settlement reference — never invent a settlement
        SocialCitizen doomed = state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c -> c.id() != citizen.id()).findFirst().orElse(citizen);
        long realSettlement = doomed.settlementId();
        doomed.migrateTo(doomed.factionId(), 9_999_100L);
        IntegrityReport fatal = CanonicalIntegrityService.repair(state);
        check(fatal.hasFatal(), "missing settlement reference must remain fatal");
        check(fatal.fatalCanonical().stream().anyMatch(m -> m.contains("missing settlement")), "fatal must name missing settlement");
        doomed.migrateTo(doomed.factionId(), realSettlement);

        byte[] encoded = SimulationStateCodec.encode(state);
        SimulationState restored = SimulationStateCodec.decode(encoded, state.species());
        IntegrityReport roundtrip = CanonicalIntegrityService.inspect(restored);
        check(roundtrip.fatalCanonical().isEmpty(), "round-trip after repair must stay fatally clean");

        // Never invent missing faction/settlement
        SimulationState empty = new SimulationState(7L);
        empty.restoreFactionCivilization(new FactionCivilizationState(42L, "X", "Y", "Z"));
        IntegrityReport emptyRepair = CanonicalIntegrityService.repair(empty);
        check(empty.factions().isEmpty(), "repair must never invent factions");
        check(empty.findFactionCivilization(42L).isEmpty(), "orphan faction civ purged without inventing faction");
        check(emptyRepair.repaired().stream().anyMatch(m -> m.contains("stale faction civilization")), "expected faction civ purge");

        System.out.println("PASS integrity recovery: rebuildable projection repair + fatal missing refs untouched + no invented realms");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
