package dev.livingrealms;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.underworld.BlackMarketService;
import dev.livingrealms.sim.underworld.UnderworldActions;
import dev.livingrealms.sim.underworld.UnderworldContract;
import dev.livingrealms.sim.underworld.UnderworldContractType;
import dev.livingrealms.sim.underworld.UnderworldProfile;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Wave 7: accept → real crime → match completes; wrong target / no crime do not; black market ledger; schema 20. */
public final class UnderworldGameplayTest {
    private UnderworldGameplayTest() {}

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        SimulationState state = new SimulationState(0x554E4457L);
        Faction realm = new Faction(state.nextId(), "Shadowvale", "Mayor");
        realm.government().adjustCorruption(.43); // ~0.55 with default .12
        Settlement town = new Settlement(state.nextId(), "Shadowport", new SimPosition(100, 100), 600, 700);
        realm.addSettlement(town);
        state.addFaction(realm);

        String actor = "player:thief";
        check(!UnderworldActions.canAccessBlackMarket(state, actor), "no black market before contracts");

        // Fabricating via the old API must not invent crimes or complete jobs.
        var fabricated = UnderworldActions.completeContract(state, actor, realm.id(), CrimeType.THEFT, 40,
                town.position(), 2, "merchant");
        check(!fabricated.success(), "completeContract must not fabricate crimes");
        check(state.crimeLedger().incidents().isEmpty(), "no crime from fabricate path");
        check(state.findUnderworldProfile(actor).isEmpty()
                || state.findUnderworldProfile(actor).orElseThrow().contractsCompleted() == 0,
                "no fabricated contract completion");

        var offerTheft = UnderworldActions.offerContract(state, UnderworldContractType.THEFT, realm.id(),
                "merchant", 20, 50, 14);
        check(offerTheft.success(), "offer theft: " + offerTheft.reason());
        long theftId = state.underworldContracts().getFirst().id();

        var acceptTheft = UnderworldActions.acceptContract(state, actor, theftId);
        check(acceptTheft.success(), "accept theft: " + acceptTheft.reason());
        check(state.findUnderworldContract(theftId).orElseThrow().status() == UnderworldContract.Status.ACCEPTED,
                "theft accepted");

        // Wrong victim must not complete the accepted contract.
        var wrong = state.reportCrime(actor, realm.id(), CrimeType.THEFT, 40, town.position(), true, 2,
                "wrong_target", "alley_pick");
        check(wrong.registered(), "wrong-target crime still registers with law");
        check(state.findUnderworldContract(theftId).orElseThrow().status() == UnderworldContract.Status.ACCEPTED,
                "wrong target does not complete contract");
        check(state.findUnderworldProfile(actor).map(UnderworldProfile::contractsCompleted).orElse(0) == 0,
                "wrong target does not count as contract");

        // Matching real crime completes the contract.
        var match = state.reportCrime(actor, realm.id(), CrimeType.THEFT, 40, town.position(), true, 2,
                "merchant", "market_stall");
        check(match.registered(), "matching crime registers");
        UnderworldContract completed = state.findUnderworldContract(theftId).orElseThrow();
        check(completed.status() == UnderworldContract.Status.COMPLETED, "matching crime completes contract");
        check(completed.matchingCrimeId() > 0, "matching crime id recorded");
        UnderworldProfile profile = state.findUnderworldProfile(actor).orElseThrow();
        check(profile.contractsCompleted() == 1, "one contract completed");
        check(profile.streetCred() > 0, "street cred gained");
        check(!state.stolenGoodsLedger().unsoldFor(actor).isEmpty(), "stolen goods deposited");

        // Second contract → black market eligibility.
        var offerSmuggle = UnderworldActions.offerContract(state, UnderworldContractType.SMUGGLING, realm.id(),
                "fence", 10, 55, 14);
        check(offerSmuggle.success(), "offer smuggle: " + offerSmuggle.reason());
        long smuggleId = state.underworldContracts().stream()
                .filter(c -> c.type() == UnderworldContractType.SMUGGLING)
                .findFirst().orElseThrow().id();
        check(UnderworldActions.acceptContract(state, actor, smuggleId).success(), "accept smuggle");
        var smuggleCrime = state.reportCrime(actor, realm.id(), CrimeType.SMUGGLING, 55, town.position(), true, 1,
                "fence", "harbor_crate");
        check(smuggleCrime.registered(), "smuggle crime");
        check(state.findUnderworldContract(smuggleId).orElseThrow().status() == UnderworldContract.Status.COMPLETED,
                "smuggle completed");
        check(UnderworldActions.canAccessBlackMarket(state, actor), "black market after two contracts");
        check(profile.isBlackMarketEligible(), "profile eligibility");

        long lotId = state.stolenGoodsLedger().unsoldFor(actor).getFirst().id();
        var sale = BlackMarketService.sell(state, actor, lotId);
        check(sale.success() && sale.paid() > 0, "black market sells ledger lot: " + sale.reason());
        check(state.stolenGoodsLedger().find(lotId).orElseThrow().sold(), "lot marked sold");
        var freeSell = BlackMarketService.sell(state, actor, 999_999_999L);
        check(!freeSell.success(), "cannot sell non-ledger goods");

        double treasuryBefore = realm.treasury();
        var bribe = UnderworldActions.bribeOfficial(state, actor, realm.id(), 55);
        check(bribe.success(), "bribe: " + bribe.reason());
        check(profile.briberySkill() > 0, "bribery skill gained");
        check(profile.lastBribeDay() == state.clock().day(), "bribe day stamped");
        check(realm.treasury() >= treasuryBefore, "bribe money enters treasury or fine path");

        byte[] bytes = SimulationStateCodec.encode(state);
        check(SimulationStateCodec.inspectSchema(bytes) == 20, "schema 20 encode");
        SimulationState round = SimulationStateCodec.decode(bytes);
        UnderworldProfile restored = round.findUnderworldProfile(actor).orElseThrow();
        check(restored.contractsCompleted() == 2, "contracts survive codec");
        check(restored.isBlackMarketEligible(), "black market survives codec");
        check(restored.briberySkill() > 0, "bribery skill survives codec");
        check(round.crimeLedger().incidents().size() >= 3, "crime incidents survive codec");
        check(round.underworldContracts().stream().filter(c -> c.status() == UnderworldContract.Status.COMPLETED).count() == 2,
                "completed contracts survive codec");
        check(round.stolenGoodsLedger().entries().stream().anyMatch(e -> e.sold()), "sold lot survives codec");

        System.out.println("PASS underworld gameplay: accept→crime→match + black market ledger + bribery + schema 20");
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
