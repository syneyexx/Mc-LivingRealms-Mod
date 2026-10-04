package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilization.CivilizationCalendar;
import dev.livingrealms.sim.civilization.FaithCatalog;
import dev.livingrealms.sim.dialogue.DialogueContext;
import dev.livingrealms.sim.dialogue.DialogueIntent;
import dev.livingrealms.sim.dialogue.NaturalLanguageDialogueEngine;
import dev.livingrealms.sim.economy.AgrarianProfile;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Gates for concrete faith calendars, agrarian crop mix and route maintenance/decay. */
public final class FaithAndInfrastructureTest {
    private FaithAndInfrastructureTest() {}

    public static void main(String[] args) {
        faithCatalogIsConcreteAndDeterministic();
        agrarianMixVariesBySettlementAndSeason();
        holyDayDialogueMentionsDeityAndScripture();
        neglectedRoutesDecayWithoutTreasury();
        waterySettlementsPreferRiverOrShipRoutes();
        System.out.println("PASS faith + infrastructure: concrete faith catalog/holy days + agrarian crop mix + route decay/repair + water-mode discovery");
    }

    private static void faithCatalogIsConcreteAndDeterministic() {
        FaithCatalog.FaithProfile a = FaithCatalog.of("The Lantern Faith");
        FaithCatalog.FaithProfile b = FaithCatalog.of("The Lantern Faith");
        check(a.primaryDeity().equals(b.primaryDeity()), "faith profile must be deterministic");
        check(FaithCatalog.known("The Lantern Faith"), "cataloged faith missing");
        check(a.holyDaysOfYear().length >= 2 && a.scripture() != null && !a.scripture().isBlank(), "faith lacks calendar/scripture");
        check(a.isHolyDay(30) || a.isHolyDay(120) || a.isHolyDay(210) || a.isHolyDay(300), "lantern holy days");
        FaithCatalog.FaithProfile custom = FaithCatalog.of("Cult of Test");
        check(custom.name().equals("Cult of Test") && custom.primaryDeity() != null, "custom faith fallback");
    }

    private static void agrarianMixVariesBySettlementAndSeason() {
        Settlement vine = new Settlement(11, "Vineford", new SimPosition(100, 100), 200, 240);
        Settlement sheep = new Settlement(12, "Sheepfold", new SimPosition(200, 200), 80, 100);
        AgrarianProfile.Mix autumnVine = AgrarianProfile.of(vine, CivilizationCalendar.Season.AUTUMN);
        AgrarianProfile.Mix springSheep = AgrarianProfile.of(sheep, CivilizationCalendar.Season.SPRING);
        check(autumnVine.primaryCrop() == AgrarianProfile.Crop.GRAPES, "vineford should specialize in grapes");
        check(springSheep.primaryStock() == AgrarianProfile.Livestock.SHEEP, "sheepfold should raise sheep");
        double year1 = AgrarianProfile.rotationMultiplier(0);
        double year3 = AgrarianProfile.rotationMultiplier(720);
        check(year1 != year3, "three-field rotation must vary across years: " + year1 + " vs " + year3);
    }

    private static void holyDayDialogueMentionsDeityAndScripture() {
        SimulationState state = new SimulationState(64123L);
        Faction faction = new Faction(state.nextId(), "Test Realm", "Abbot");
        Settlement settlement = new Settlement(state.nextId(), "Chapelton", new SimPosition(0, 0), 400, 500);
        settlement.markConstructionCompleted("temple:0");
        faction.addSettlement(settlement);
        state.addFaction(faction);
        var priest = state.ensureSocialCitizen(faction.id(), settlement.id(), 0, CitizenRole.PRIEST);
        FaithCatalog.FaithProfile profile = FaithCatalog.of(state.ensureFactionCivilization(faction.id()).faithName());
        NaturalLanguageDialogueEngine engine = new NaturalLanguageDialogueEngine();
        var result = engine.respond(state, priest, "player:faith", "Vertel over jullie religie en geloof", new DialogueContext());
        check(result.intent() == DialogueIntent.ASK_RELIGION, "religion intent");
        String answer = result.response();
        check(answer.contains(profile.primaryDeity()) || answer.contains(profile.scripture()) || answer.contains(profile.symbol()),
                "priest must mention deity/scripture/symbol: " + answer);
    }

    private static void neglectedRoutesDecayWithoutTreasury() {
        SimulationState state = new SimulationState(778_811L);
        Faction faction = new Faction(state.nextId(), "Broke March", "Pauper");
        Settlement a = new Settlement(state.nextId(), "Eastwick", new SimPosition(0, 0), 80, 100);
        Settlement b = new Settlement(state.nextId(), "Westwick", new SimPosition(400, 0), 70, 90);
        faction.addSettlement(a);
        faction.addSettlement(b);
        faction.stockpile().set(ResourceType.STONE, 0);
        faction.restoreTreasury(0); // default Faction treasury is 1000
        faction.government().setTaxRate(0); // no fiscal recovery during the neglect window
        state.addFaction(faction);
        TransportRoute route = new TransportRoute(state.nextId(), faction.id(), a.id(), b.id(), TransportMode.ROAD, 400, .40, .40, 320);
        state.addRoute(route);
        double before = route.quality();
        state.advanceDays(60);
        check(route.quality() < before - 0.02, "neglected route must decay: " + before + " -> " + route.quality());
        faction.addTreasury(5_000);
        faction.stockpile().add(ResourceType.STONE, 500);
        double mid = route.quality();
        state.advanceDays(28); // four weekly repair ticks
        check(route.quality() > mid, "funded maintenance should improve route: " + mid + " -> " + route.quality());
    }

    private static void waterySettlementsPreferRiverOrShipRoutes() {
        SimulationState state = new SimulationState(991_002L);
        Faction faction = new Faction(state.nextId(), "Coast Realm", "Admiral");
        Settlement port = new Settlement(state.nextId(), "Seaport", new SimPosition(0, 0), 900, 1_100);
        Settlement bay = new Settlement(state.nextId(), "Thunderbay", new SimPosition(500, 40), 700, 850);
        Settlement inland = new Settlement(state.nextId(), "Stoneham", new SimPosition(200, 200), 400, 480);
        faction.addSettlement(port);
        faction.addSettlement(bay);
        faction.addSettlement(inland);
        faction.stockpile().add(ResourceType.STONE, 50_000);
        faction.stockpile().add(ResourceType.IRON, 20_000);
        faction.addTreasury(8_000);
        state.addFaction(faction);
        state.advanceDays(7); // weekly route discovery
        boolean waterRoute = state.routes().stream().anyMatch(r ->
                r.ownerFactionId() == faction.id()
                        && (r.mode() == TransportMode.RIVER || r.mode() == TransportMode.SHIP));
        check(waterRoute, "water-named settlements should discover river/ship routes; modes="
                + state.routes().stream().map(r -> r.mode().name()).toList());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
