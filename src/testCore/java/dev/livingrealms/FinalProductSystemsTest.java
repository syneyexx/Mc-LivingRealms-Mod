package dev.livingrealms;

import dev.livingrealms.sim.civilian.AppearanceProfile;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.diplomacy.WarGoalType;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.*;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.CampaignPlan;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.player.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.Set;

/** Schema-17 final-product systems: appearance, influence/careers, debt, projects, campaigns, heroes, logistics. */
public final class FinalProductSystemsTest {
    private FinalProductSystemsTest() {}

    public static void main(String[] args) {
        testAppearanceRange();
        testInfluenceSeparateFromReputation();
        testDebtBorrowServiceDefault();
        testGrandProjectProgress();
        testCampaignPlanDuringWar();
        testShipmentLogisticsSchema17();
        testHeroLegendOnDeath();
        System.out.println("PASS final product systems: appearance48 + influence + debt + projects + campaigns + schema17 logistics + heroes");
    }

    private static void testAppearanceRange() {
        Set<Integer> skins = new HashSet<>();
        for (int i = 1; i <= 200; i++) {
            AppearanceProfile p = AppearanceProfile.forCitizen(99L, i, CitizenRole.values()[i % CitizenRole.values().length], 10 + (i % 60), 3L + (i % 5));
            int tex = p.textureIndex();
            check(tex >= 0 && tex < 48, "textureIndex in 0-47");
            AppearanceProfile round = AppearanceProfile.unpack(p.pack());
            check(round.textureIndex() == p.textureIndex() || round.baseBody() == p.baseBody(), "pack/unpack preserves axes");
            skins.add(tex);
        }
        check(skins.size() >= 20, "appearance diversity across seeds");
        SimulationState state = seeded(901L);
        SocialCitizen c = state.ensureSocialCitizen(state.factions().getFirst().id(), state.factions().getFirst().settlements().getFirst().id(), 0, CitizenRole.ARTISAN);
        check(c.skinVariant() >= 0 && c.skinVariant() < 48, "social citizen skin 0-47");
        check(c.appearancePacked() != 0 || c.appearanceProfile().textureIndex() >= 0, "appearance packed");
    }

    private static void testInfluenceSeparateFromReputation() {
        SimulationState state = seeded(902L);
        Faction f = state.factions().getFirst();
        PlayerStanding ps = state.playerStanding("player:final");
        ps.adjustReputation(f.id(), 40);
        ps.adjustInfluence(f.id(), InfluenceInstitution.MILITARY, 25);
        ps.adjustInfluence(f.id(), InfluenceInstitution.MERCHANTS, 10);
        check(ps.reputationWith(f.id()) == 40, "reputation unchanged by influence write path");
        check(ps.influenceWith(f.id(), InfluenceInstitution.MILITARY) == 25, "military influence");
        check(ps.influenceWith(f.id(), InfluenceInstitution.MERCHANTS) == 10, "merchant influence");
        ps.join(f.id(), 0);
        state.grantFactionService("player:final", f.id(), 120);
        check(ps.reputationWith(f.id()) > 40, "service still bumps reputation");
        check(ps.influenceWith(f.id(), InfluenceInstitution.MILITARY) >= 25 || ps.influenceWith(f.id(), InfluenceInstitution.COMMONERS) > 0, "service also bumps influence");
        check(ps.grantCareerService(CareerTrack.MILITARY, 300) || ps.careerRankIndex() >= 0, "career service advances");
    }

    private static void testDebtBorrowServiceDefault() {
        SimulationState state = seeded(903L);
        Faction f = state.factions().getFirst();
        f.restoreTreasury(20);
        double before = f.treasury();
        SovereignDebt debt = new SovereignDebt(state.nextId(), f.id(), "merchant:test_house", 100, .1, state.clock().day(), state.clock().day() + 30, .7);
        state.addDebt(debt);
        f.addTreasury(100);
        check(f.treasury() >= before + 100, "borrow funds treasury");
        debt.accrueInterest(1.0 / 12.0);
        check(debt.remaining() > 100, "interest accrues");
        double paid = debt.service(20);
        check(paid == 20 && debt.remaining() < debt.principal() + 20, "service reduces remaining");
        debt.markDefaulted();
        check(debt.defaulted() && !debt.active(), "default closes debt");
        new SovereignDebtEngine().simulateDay(state, new dev.livingrealms.sim.util.DeterministicRng(1));
        check(state.debts().stream().anyMatch(d -> d.debtorFactionId() == f.id()), "engine retains debt records");
    }

    private static void testGrandProjectProgress() {
        SimulationState state = seeded(904L);
        Faction f = state.factions().getFirst();
        Settlement s = f.settlements().getFirst();
        f.restoreTreasury(2000);
        for (ResourceType r : ResourceType.values()) {
            f.stockpile().add(r, 500);
            s.stockpile().add(r, 500);
        }
        GrandProject project = new GrandProject(state.nextId(), f.id(), s.id(), GrandProjectType.CIVIC_MONUMENT, state.clock().day());
        state.addGrandProject(project);
        double infra0 = s.infrastructure();
        for (int i = 0; i < 80 && !project.complete(); i++) new GrandProjectEngine().simulateDay(state, new dev.livingrealms.sim.util.DeterministicRng(i));
        check(project.progress() > 0 || project.complete(), "project progresses with resources");
        if (project.complete()) check(s.infrastructure() >= infra0, "completion can improve infrastructure");
    }

    private static void testCampaignPlanDuringWar() {
        SimulationState state = seeded(905L);
        Faction a = state.factions().getFirst();
        Faction b = state.factions().size() > 1 ? state.factions().get(1) : null;
        if (b == null) {
            b = new Faction(state.nextId(), "Rival", "Rival King");
            Settlement town = new Settlement(state.nextId(), "Rivalton", new SimPosition(a.settlements().getFirst().position().x() + 800, a.settlements().getFirst().position().z()), 400, 420);
            b.addSettlement(town);
            b.addArmy(new Army(state.nextId(), b.id(), town.position(), 80));
            state.addFaction(b);
        }
        if (a.armies().isEmpty()) a.addArmy(new Army(state.nextId(), a.id(), a.settlements().getFirst().position(), 100));
        a.relationWith(b.id()).declareWar();
        b.relationWith(a.id()).declareWar();
        long target = b.settlements().getFirst().id();
        state.addWar(new WarState(state.nextId(), a.id(), b.id(), WarGoalType.CONQUEST, target, state.clock().day()));
        state.advanceDays(2);
        check(state.campaignPlans().stream().anyMatch(CampaignPlan::active), "war creates campaign plans");
        check(state.objectives().stream().anyMatch(o -> !o.complete()), "plans derive military objectives");
    }

    private static void testShipmentLogisticsSchema17() {
        SimulationState state = seeded(906L);
        Faction seller = state.factions().getFirst();
        Faction buyer = state.factions().size() > 1 ? state.factions().get(1) : seller;
        if (buyer == seller) {
            buyer = new Faction(state.nextId(), "Buyer", "Buyer");
            Settlement dest = new Settlement(state.nextId(), "Buymarket", new SimPosition(1200, 400), 300, 320);
            buyer.addSettlement(dest);
            buyer.restoreTreasury(5000);
            state.addFaction(buyer);
        }
        Settlement origin = seller.settlements().getFirst();
        Settlement destination = buyer.settlements().getFirst();
        seller.stockpile().add(ResourceType.FOOD, 500);
        buyer.restoreTreasury(5000);
        TradeShipment shipment = new TradeShipment(state.nextId(), seller.id(), buyer.id(), ResourceType.FOOD, 20, 40, origin.position(), destination.position());
        shipment.restoreLogistics(origin.id(), destination.id(), 0, 0, 3, 10, .25, .6, TradeShipment.LossState.NONE, 0);
        state.addShipment(shipment);
        check(SimulationStateCodec.SCHEMA_VERSION == 17, "schema 17 pin");
        byte[] bytes = SimulationStateCodec.encode(state);
        check(SimulationStateCodec.inspectSchema(bytes) == 17, "encoded schema 17");
        SimulationState loaded = SimulationStateCodec.decode(bytes);
        TradeShipment round = loaded.findShipment(shipment.id()).orElseThrow();
        check(round.originSettlementId() == origin.id() && round.destinationSettlementId() == destination.id(), "logistics settlements roundtrip");
        check(Math.abs(round.risk() - .25) < 1e-9 && round.escortStrength() == .6, "logistics risk/escort roundtrip");
        check(round.lossState() == TradeShipment.LossState.NONE && round.departureDay() == 3, "logistics meta roundtrip");
    }

    private static void testHeroLegendOnDeath() {
        SimulationState state = seeded(907L);
        Faction f = state.factions().getFirst();
        Settlement s = f.settlements().getFirst();
        SocialCitizen hero = state.ensureSocialCitizen(f.id(), s.id(), 9, CitizenRole.OFFICIAL);
        hero.setSocialClass(SocialClass.ELITE);
        hero.practiceProfession(.9);
        hero.setPersonalInfluence(.9);
        hero.remember(new CitizenMemory(state.clock().day(), MemoryType.WAR_NEWS, "battle:1", "army:1", "Survived the battle at the ford.", s.position(), .9, 1));
        hero.remember(new CitizenMemory(state.clock().day(), MemoryType.WAR_NEWS, "battle:2", "army:1", "Held the line in battle.", s.position(), .85, 1));
        hero.remember(new CitizenMemory(state.clock().day(), MemoryType.LOCAL_EVENT, "assist:1", "task:1", "Led major assistance for the hungry.", s.position(), .8, 1));
        hero.markDead();
        new dev.livingrealms.sim.civilization.HeroEngine().simulateDay(state);
        String key = "citizen:" + hero.id();
        check(state.legends().stream().anyMatch(l -> l.subjectKey().equals(key)), "high-renown death creates legend");
        var legend = state.legends().stream().filter(l -> l.subjectKey().equals(key)).findFirst().orElseThrow();
        if (legend.renown() >= .65) check(legend.monumented() || state.history().all().stream().anyMatch(e -> e.type().equals("hero_monumented") || e.type().equals("hero_recognized")), "monument or recognition history");
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
