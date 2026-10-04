package dev.livingrealms;

import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.dialogue.DialogueTradeBridge;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.naval.PortState;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Locks A–Z production deepening that does not require linked Minecraft smoke. */
public final class ProductionCompletionPassTest {
    private ProductionCompletionPassTest() {}

    public static void main(String[] args) {
        portDiscoveryFromGeography();
        dockIntentsForShipSuitable();
        entranceExtremeRejection();
        constructionKnowledgeGrows();
        courtBindsDynastyIdentities();
        adoptionRoleInference();
        dialogueTradeBridgeQuotes();
        locatePortAfterDiscovery();
        epidemicRoutineDampensActivity();
        System.out.println("PASS production completion pass: ports/docks + entrance reject + construction knowledge + court dynasty + adoption roles + trade bridge + epidemic routines");
    }

    private static void portDiscoveryFromGeography() {
        SimulationState state = new SimulationState(424242L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        check(state.ports().isEmpty(), "fresh seed should start without ports before naval day");
        // Force at least one coastal profile so discovery is deterministic even if names miss harbour tokens.
        Faction faction = state.factions().getFirst();
        Settlement settlement = faction.settlements().stream()
                .filter(s -> s.tier().ordinal() >= Settlement.Tier.VILLAGE.ordinal())
                .findFirst().orElseThrow();
        settlement.setGeography(new SettlementGeographyProfile(
                true, false, true, false, 0.8, 62, 0.1, .55, .2, .2, "minecraft:beach", true));
        state.advanceDays(1);
        check(!state.ports().isEmpty(), "naval day must discover ports for ship-suitable settlements");
        check(state.ports().stream().anyMatch(p -> p.settlementId() == settlement.id()), "forced coastal settlement must gain a port");
        int before = state.ports().size();
        state.advanceDays(1);
        check(state.ports().size() == before, "port discovery must be idempotent");
    }

    private static void dockIntentsForShipSuitable() {
        Faction faction = new Faction(1, "Harbor Realm", "Admiral");
        Settlement settlement = new Settlement(11, "Seaport Bay", new SimPosition(100, 100), 400, 420);
        settlement.setGeography(new SettlementGeographyProfile(
                true, false, true, false, 0.75, 63, 0.05, .5, .2, .15, "minecraft:beach", true));
        faction.addSettlement(settlement);
        List<ConstructionIntent> plan = SettlementPlanner.plan(faction, settlement);
        check(plan.stream().anyMatch(i -> i.role() == StructureRole.DOCK), "ship-suitable village+ must plan a dock");
        Settlement inland = new Settlement(12, "Hillford", new SimPosition(300, 300), 400, 420);
        inland.setGeography(SettlementGeographyProfile.unknown());
        Faction inlandFaction = new Faction(2, "Inland", "Warden");
        inlandFaction.addSettlement(inland);
        check(SettlementPlanner.plan(inlandFaction, inland).stream().noneMatch(i -> i.role() == StructureRole.DOCK),
                "inland settlements must not invent docks");
    }

    private static void entranceExtremeRejection() {
        check(EntranceAccessPlanner.isExtremeSite(80, 70), "10-block grade is extreme");
        check(!EntranceAccessPlanner.canRepair(80, 70), "extreme sites are not repairable");
        check(EntranceAccessPlanner.plan(0, -5, 80, 70).isEmpty(), "extreme grade must return no fix (reject/re-site)");
        List<EntranceAccessPlanner.AccessFix> down = EntranceAccessPlanner.plan(0, -5, 64, 70);
        check(down.stream().anyMatch(f -> f.slot() == PaletteSlot.PATH), "down-grade switchback must include path landing");
    }

    private static void constructionKnowledgeGrows() {
        SimulationState state = new SimulationState(515151L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        Faction faction = state.factions().getFirst();
        Settlement settlement = faction.settlements().getFirst();
        settlement.markConstructionCompleted("keep:0");
        settlement.markConstructionCompleted("workshop:0");
        settlement.markConstructionCompleted("house:0");
        SettlementCivilizationState civ = state.ensureSettlementCivilization(settlement.id(), faction.id());
        double before = civ.knowledge(KnowledgeDomain.CONSTRUCTION);
        // Weekly knowledge diffusion runs on day%7==0.
        state.advanceDays(7);
        double after = state.ensureSettlementCivilization(settlement.id(), faction.id()).knowledge(KnowledgeDomain.CONSTRUCTION);
        check(after > before, "keep/workshop/house must grow CONSTRUCTION knowledge");
    }

    private static void courtBindsDynastyIdentities() {
        SimulationState state = new SimulationState(616161L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        state.advanceDays(2); // found dynasties / ruler personification
        Faction faction = state.factions().stream()
                .filter(f -> state.dynasties().get(f.id()) != null && state.dynasties().get(f.id()).rulerCitizenId() > 0)
                .findFirst().orElseThrow();
        DynastyState dynasty = state.dynasties().get(faction.id());
        SocialCitizen ruler = state.findSocialCitizen(dynasty.rulerCitizenId()).orElseThrow();
        Settlement capital = faction.settlements().stream()
                .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                .orElseThrow();
        List<CitizenProjection> projections = CitizenMaterializationPlanner.plan(
                state, List.of(faction), List.of(capital.position()), 800, 64);
        check(projections.stream().anyMatch(p -> p.settlementId() == capital.id() && p.slot() == ruler.projectionSlot()),
                "capital projections must include dynasty ruler slot");
    }

    private static void adoptionRoleInference() {
        check(CivilianRoleInference.fromSignals("guard_captain") == CitizenRole.GUARD, "guard path");
        check(CivilianRoleInference.fromSignals("village_farmer") == CitizenRole.FARMER, "farmer path");
        check(CivilianRoleInference.fromSignals("wandering_merchant") == CitizenRole.TRADER, "merchant path");
        check(CivilianRoleInference.fromSignals("town_priest") == CitizenRole.PRIEST, "priest path");
        check(CivilianRoleInference.fromSignals("blacksmith_npc") == CitizenRole.ARTISAN, "smith path");
        check(CivilianRoleInference.fromSignals("generic_villager") == CitizenRole.TRADER, "fallback trader");
    }

    private static void dialogueTradeBridgeQuotes() {
        SimulationState state = new SimulationState(717171L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        Faction faction = state.factions().getFirst();
        Settlement settlement = faction.settlements().getFirst();
        SocialCitizen citizen = state.ensureSocialCitizen(faction.id(), settlement.id(), 3, CitizenRole.TRADER);
        String quote = DialogueTradeBridge.quoteSummary(state, citizen);
        check(quote.contains("Local market"), "trade bridge must mention local market");
        check(quote.contains("FOOD="), "trade bridge must quote FOOD");
        check(quote.contains("F12 Economy"), "trade bridge must point to authoritative Economy UI");
    }

    private static void epidemicRoutineDampensActivity() {
        SimulationState state = new SimulationState(919191L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        Faction faction = state.factions().getFirst();
        Settlement settlement = faction.settlements().getFirst();
        settlement.markConstructionCompleted("house:0");
        settlement.markConstructionCompleted("clinic:0");
        state.addEpidemic(new EpidemicRecord(state.nextId(), settlement.id(), state.clock().day(), "plague", .7, .4));
        CitizenRoutine routine = CitizenRoutinePlanner.plan(state, faction, settlement, CitizenRole.FARMER, 2, 6000);
        check(routine.activity() == CitizenActivity.REST || routine.activity() == CitizenActivity.HEAL,
                "epidemic must push non-essential citizens to rest/clinic");
        CitizenRoutine healer = CitizenRoutinePlanner.plan(state, faction, settlement, CitizenRole.HEALER, 1, 6000);
        check(healer.activity() != CitizenActivity.REST || settlement.isConstructionCompleted("clinic:0"),
                "healers remain on duty during outbreaks");
    }

    private static void locatePortAfterDiscovery() {
        SimulationState state = new SimulationState(818181L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        for (Faction f : state.factions()) {
            for (Settlement s : f.settlements()) {
                if (s.tier().ordinal() >= Settlement.Tier.TOWN.ordinal()) {
                    s.setGeography(new SettlementGeographyProfile(
                            true, true, true, false, 0.9, 61, 0.05, .5, .2, .2, "minecraft:beach", true));
                }
            }
        }
        state.advanceDays(1);
        check(!state.ports().isEmpty(), "seeded coastal towns must create ports");
        Optional<LocateQuery.Hit> hit = LocateQuery.nearestPort(state, new SimPosition(0, 0));
        check(hit.isPresent(), "locate port must find discovered ports");
        check(hit.get().label().equals("port"), "port locate label");
        PortState port = state.ports().getFirst();
        check(port.operational(), "fresh ports start operational");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
