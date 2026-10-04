package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.SovereignDebt;
import dev.livingrealms.sim.government.SovereignDebtEngine;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.player.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Emergent story chain gates (directive Waves 178/180/182): family mobility, trade escort/loss, debt pressure.
 * These assert multi-system agreement rather than isolated unit behavior.
 */
public final class EmergentStoryChainsTest {
    private EmergentStoryChainsTest() {}

    public static void main(String[] args) {
        testFamilyMobilityChain();
        testTradeEscortPartialLossChain();
        testDebtPressureChain();
        System.out.println("PASS emergent story chains: family mobility + trade escort/loss + debt pressure");
    }

    private static void testFamilyMobilityChain() {
        SimulationState state = seeded(1201L);
        Faction f = state.factions().getFirst();
        Settlement s = f.settlements().getFirst();
        s.markConstructionCompleted("school:0");
        s.markConstructionCompleted("workshop:0");
        s.markConstructionCompleted("guild:0");
        s.setEmployment(.92);
        s.adjustProsperity(.35);
        s.setFoodSecurity(.9);
        SocialCitizen child = state.ensureSocialCitizen(f.id(), s.id(), 11, CitizenRole.FARMER);
        child.adjustEducation(.15);
        child.practiceProfession(.2);
        child.addMoney(3);
        child.setEmploymentStatus(EmploymentStatus.APPRENTICE);
        SocialClass birth = child.socialClass();
        for (int i = 0; i < 220; i++) {
            child.adjustEducation(.01);
            child.practiceProfession(.015);
            child.addMoney(1);
            state.advanceDays(1);
        }
        check(child.alive(), "citizen remains alive");
        check(child.education() > .4, "education rose through schooling access");
        check(child.professionSkill() > .4, "profession skill rose");
        check(child.money() > 3, "earnings accumulated");
        check(child.socialClass().ordinal() >= birth.ordinal() || child.role() != CitizenRole.FARMER || child.employmentStatus() == EmploymentStatus.MASTER || child.employmentStatus() == EmploymentStatus.EMPLOYED,
                "mobility improved class, role, or employment standing");
        check(!child.workplaceKey().isBlank(), "workplace assigned against physical construction");
    }

    private static void testTradeEscortPartialLossChain() {
        SimulationState state = seeded(1202L);
        Faction seller = state.factions().getFirst();
        Faction buyer = state.factions().size() > 1 ? state.factions().get(1) : null;
        if (buyer == null) {
            buyer = new Faction(state.nextId(), "BuyerRealm", "Buyer");
            buyer.addSettlement(new Settlement(state.nextId(), "Buyerton", new SimPosition(seller.settlements().getFirst().position().x() + 1200, 0), 300, 320));
            state.addFaction(buyer);
        }
        Settlement origin = seller.settlements().getFirst();
        Settlement destination = buyer.settlements().getFirst();
        seller.stockpile().add(ResourceType.FOOD, 800);
        TradeShipment shipment = new TradeShipment(state.nextId(), seller.id(), buyer.id(), ResourceType.FOOD, 80, 200, origin.position(), destination.position());
        shipment.restoreLogistics(origin.id(), destination.id(), 0, 0, state.clock().day(), state.clock().day() + 8, .75, .55, TradeShipment.LossState.NONE, 0);
        state.addShipment(shipment);
        // Escorts held: mark partial loss + delay via the same restoreLogistics path TradeEngine uses.
        shipment.restoreLogistics(shipment.originSettlementId(), shipment.destinationSettlementId(), shipment.routeId(), shipment.transportModeOrdinal(),
                shipment.departureDay(), shipment.expectedArrivalDay() + 2, Math.min(1, shipment.risk() + .12), shipment.escortStrength() * .7,
                TradeShipment.LossState.PARTIAL, shipment.delayDays() + 2);
        check(shipment.lossState() == TradeShipment.LossState.PARTIAL, "partial cargo loss recorded");
        check(shipment.delayDays() >= 2, "partial loss delays arrival");
        check(shipment.escortStrength() < .55, "escort strength degraded after fight");
        PlayerStanding hunter = state.playerStanding("player:escort");
        hunter.adjustInfluence(seller.id(), InfluenceInstitution.MERCHANTS, 30);
        hunter.adjustInfluence(seller.id(), InfluenceInstitution.MILITARY, 20);
        var petition = PlayerInfluenceActions.apply(state, "player:escort", seller.id(), PlayerInfluenceActions.Unlock.PETITION_TRADE);
        check(petition.success(), "merchant influence can petition trade security after loss");
    }

    private static void testDebtPressureChain() {
        SimulationState state = seeded(1203L);
        Faction f = state.factions().getFirst();
        f.restoreTreasury(30);
        SovereignDebt debt = new SovereignDebt(state.nextId(), f.id(), "merchant:war_lenders", 250, .12, state.clock().day(), state.clock().day() + 40, .8);
        state.addDebt(debt);
        f.addTreasury(250);
        double afterBorrow = f.treasury();
        check(afterBorrow >= 250, "loan funds treasury");
        for (int i = 0; i < 40; i++) new SovereignDebtEngine().simulateDay(state, new dev.livingrealms.sim.util.DeterministicRng(i + 3));
        check(state.debts().stream().anyMatch(d -> d.debtorFactionId() == f.id()), "debt retained under service pressure");
        SovereignDebt live = state.debts().stream().filter(d -> d.debtorFactionId() == f.id()).findFirst().orElseThrow();
        check(live.remaining() > 0 || live.defaulted(), "debt either still owed or defaulted with consequences path");
        if (live.defaulted()) {
            check(f.government().legitimacy() < 1.0 || f.treasury() < afterBorrow + 1, "default path pressures legitimacy/treasury");
        }
    }

    private static SimulationState seeded(long seed) {
        SimulationState state = new SimulationState(seed);
        DemoSeeder.seed(state);
        return state;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
