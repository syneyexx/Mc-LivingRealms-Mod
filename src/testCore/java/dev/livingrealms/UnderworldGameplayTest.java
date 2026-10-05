package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.underworld.UnderworldActions;
import dev.livingrealms.sim.underworld.UnderworldProfile;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 7: underworld contracts via CrimeEngine, black-market eligibility, bribery, schema 19. */
public final class UnderworldGameplayTest {
    private UnderworldGameplayTest() {}

    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x554E4457L);
        Faction realm = new Faction(state.nextId(), "Shadowvale", "Mayor");
        realm.government().adjustCorruption(.43); // ~0.55 with default .12
        Settlement town = new Settlement(state.nextId(), "Shadowport", new SimPosition(100, 100), 600, 700);
        realm.addSettlement(town);
        state.addFaction(realm);

        String actor = "player:thief";
        check(!UnderworldActions.canAccessBlackMarket(state, actor), "no black market before contracts");

        var first = UnderworldActions.completeContract(state, actor, realm.id(), CrimeType.THEFT, 40,
                town.position(), 2, "merchant");
        check(first.success(), "first contract: " + first.reason());
        check(state.crimeLedger().incidents().stream().anyMatch(c -> c.actorKey().equals(actor)
                && c.type() == CrimeType.THEFT), "crime incident recorded");
        UnderworldProfile profile = state.findUnderworldProfile(actor).orElseThrow();
        check(profile.contractsCompleted() == 1, "one contract");
        check(profile.streetCred() > 0, "street cred gained");

        var second = UnderworldActions.completeContract(state, actor, realm.id(), CrimeType.SMUGGLING, 55,
                town.position(), 1, "fence");
        check(second.success(), "second contract: " + second.reason());
        check(UnderworldActions.canAccessBlackMarket(state, actor), "black market after two contracts");
        check(profile.isBlackMarketEligible(), "profile eligibility");

        double treasuryBefore = realm.treasury();
        var bribe = UnderworldActions.bribeOfficial(state, actor, realm.id(), 40);
        check(bribe.success(), "bribe: " + bribe.reason());
        check(profile.briberySkill() > 0, "bribery skill gained");
        check(profile.lastBribeDay() == state.clock().day(), "bribe day stamped");
        check(realm.treasury() >= treasuryBefore, "bribe money enters treasury or fine path");

        byte[] bytes = SimulationStateCodec.encode(state);
        check(SimulationStateCodec.inspectSchema(bytes) == 19, "schema 19 encode");
        SimulationState round = SimulationStateCodec.decode(bytes);
        UnderworldProfile restored = round.findUnderworldProfile(actor).orElseThrow();
        check(restored.contractsCompleted() == 2, "contracts survive codec");
        check(restored.isBlackMarketEligible(), "black market survives codec");
        check(restored.briberySkill() > 0, "bribery skill survives codec");
        check(round.crimeLedger().incidents().size() >= 2, "crime incidents survive codec");

        System.out.println("PASS underworld gameplay: contracts + black market + bribery + schema 19");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
