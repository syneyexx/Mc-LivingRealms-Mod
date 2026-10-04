package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilization.BanditArchetype;
import dev.livingrealms.sim.civilization.BanditEconomyEngine;
import dev.livingrealms.sim.civilization.PirateBand;
import dev.livingrealms.sim.civilization.PirateHideout;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.social.CitizenMemory;
import dev.livingrealms.sim.social.MemoryType;
import dev.livingrealms.sim.social.RumorEngine;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Gates for typed bandit causes, road extortion and trader price rumors. */
public final class BanditAndPriceRumorTest {
    private BanditAndPriceRumorTest() {}

    public static void main(String[] args) {
        typedBanditsSpawnFromPressure();
        landBandsExtortInsecureRoutes();
        tradersSeedLocalPriceRumors();
        System.out.println("PASS bandits + price rumors: typed band formation + route extortion + trader scarcity rumors");
    }

    private static void typedBanditsSpawnFromPressure() {
        SimulationState state = new SimulationState(441_200L);
        Faction faction = new Faction(state.nextId(), "Hard Realm", "Warden");
        Settlement town = new Settlement(state.nextId(), "Starvehollow", new SimPosition(0, 0), 600, 700);
        town.setFoodSecurity(0.15);
        town.setEmployment(0.25);
        town.adjustUnrest(0.5);
        faction.addSettlement(town);
        faction.government().setTaxRate(0.40);
        faction.restoreTreasury(100);
        state.addFaction(faction);
        var civ = state.ensureSettlementCivilization(town.id(), faction.id());
        civ.adjustBanditPressure(0.85);
        // Advance to a weekly tick with civilization engines.
        state.advanceDays(14);
        boolean formed = state.history().all().stream().anyMatch(e ->
                e.type().equals("bandit_band_formed") || e.type().equals("deserter_band_formed")
                        || e.type().equals("bandit_raid_departed"));
        check(formed || state.pirateBands().stream().anyMatch(PirateBand::active) || state.raids().stream().anyMatch(r -> r.bandit()),
                "pressure must produce typed bandits/raids");
        BanditArchetype kind = BanditArchetype.choose(town, faction, civ, 7);
        check(kind != null && kind.key() != null, "archetype chooser");
    }

    private static void landBandsExtortInsecureRoutes() {
        SimulationState state = new SimulationState(552_301L);
        Faction faction = new Faction(state.nextId(), "Toll Realm", "Count");
        Settlement a = new Settlement(state.nextId(), "Northford", new SimPosition(0, 0), 400, 480);
        Settlement b = new Settlement(state.nextId(), "Southmere", new SimPosition(600, 0), 380, 450);
        faction.addSettlement(a);
        faction.addSettlement(b);
        faction.addTreasury(2_000);
        state.addFaction(faction);
        TransportRoute route = new TransportRoute(state.nextId(), faction.id(), a.id(), b.id(), TransportMode.ROAD, 600, .50, .30, 300);
        state.addRoute(route);
        PirateBand band = new PirateBand(state.nextId(), a.id(), 0, new SimPosition(300, 10), 18);
        state.addPirateBand(band);
        PirateHideout hideout = new PirateHideout(state.nextId(), band.id(), a.id(), 0, new SimPosition(300, 10));
        state.addPirateHideout(hideout);
        double securityBefore = route.security();
        double treasuryBefore = faction.treasury();
        DeterministicRng rng = new DeterministicRng(state.seed() ^ 99);
        for (int i = 0; i < 40; i++) BanditEconomyEngine.extortRoutes(state, rng);
        check(route.security() < securityBefore || faction.treasury() < treasuryBefore || band.loot() > 0,
                "extortion must hurt security/treasury or enrich the band");
    }

    private static void tradersSeedLocalPriceRumors() {
        SimulationState state = new SimulationState(663_402L);
        Faction faction = new Faction(state.nextId(), "Market Realm", "Mayor");
        Settlement town = new Settlement(state.nextId(), "Dearbrook", new SimPosition(0, 0), 500, 600);
        town.stockpile().set(ResourceType.FOOD, 5); // scarce
        faction.addSettlement(town);
        state.addFaction(faction);
        SocialCitizen trader = state.ensureSocialCitizen(faction.id(), town.id(), 0, CitizenRole.TRADER);
        // Jump clock to a weekly day without needing full year of economy.
        state.advanceDays(7);
        new RumorEngine().simulateDay(state);
        CitizenMemory price = trader.latestMemory(m -> m.subjectKey().startsWith("price:food:")).orElse(null);
        check(price != null || state.history().all().stream().anyMatch(e -> e.type().equals("price_rumor")),
                "traders must seed food price rumors under scarcity");
        if (price != null) {
            check(price.type() == MemoryType.RUMOR, "price talk is a rumor");
            check(price.summary().toLowerCase().contains("food") || price.summary().contains("dear")
                            || price.summary().contains("cheap") || price.summary().contains("fair"),
                    "price rumor mentions market tone");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
