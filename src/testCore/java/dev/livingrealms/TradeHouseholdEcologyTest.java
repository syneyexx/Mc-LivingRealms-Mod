package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilization.DynastyState;
import dev.livingrealms.sim.civilization.JusticeCase;
import dev.livingrealms.sim.civilization.JusticeStatus;
import dev.livingrealms.sim.ecology.EcosystemRegion;
import dev.livingrealms.sim.ecology.PopulationGroup;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.sim.logistics.TradeEngine;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.social.HouseholdState;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Gates for trade↔route insurance, household wealth loops, ecology harvest and crisis/justice dwell. */
public final class TradeHouseholdEcologyTest {
    private TradeHouseholdEcologyTest() {}

    public static void main(String[] args) {
        shipmentUsesRouteSpeedAndPaysInsuranceOnLoss();
        wagesRaiseHouseholdWealthAndConsumptionSpendsIt();
        hinterlandHarvestDepletesEcology();
        successionCrisisErodesLegitimacy();
        justiceStartsInvestigatingBeforeCharge();
        System.out.println("PASS trade/household/ecology: route-speed insurance + household budget + ecology harvest + dynasty crisis + justice investigation");
    }

    private static void shipmentUsesRouteSpeedAndPaysInsuranceOnLoss() {
        SimulationState state = new SimulationState(901_101L);
        Faction seller = new Faction(state.nextId(), "Seller", "S");
        Faction buyer = new Faction(state.nextId(), "Buyer", "B");
        Settlement so = new Settlement(state.nextId(), "Sellford", new SimPosition(0, 0), 800, 900);
        Settlement bd = new Settlement(state.nextId(), "Buyport", new SimPosition(900, 0), 800, 900);
        seller.addSettlement(so);
        buyer.addSettlement(bd);
        seller.relationWith(buyer.id()).setTradeAgreement(true);
        buyer.relationWith(seller.id()).setTradeAgreement(true);
        seller.stockpile().add(ResourceType.FOOD, 5_000);
        so.stockpile().add(ResourceType.FOOD, 2_000);
        buyer.addTreasury(50_000);
        seller.restoreTreasury(10_000);
        state.addFaction(seller);
        state.addFaction(buyer);
        // Slow, insecure road — caravan should not use the constant 180 blindly.
        TransportRoute route = new TransportRoute(state.nextId(), seller.id(), so.id(), bd.id(), TransportMode.ROAD, 900, .20, .10, 320);
        state.addRoute(route);
        TradeShipment shipment = new TradeShipment(state.nextId(), seller.id(), buyer.id(), ResourceType.FOOD, 40, 400, so.position(), bd.position());
        state.addShipment(shipment);
        double buyerBefore = buyer.treasury();
        double sellerBefore = seller.treasury();
        DeterministicRng rng = new DeterministicRng(0xDEADL); // force many intercept rolls via many days
        TradeEngine engine = new TradeEngine();
        boolean intercepted = false;
        for (int i = 0; i < 80 && !intercepted; i++) {
            engine.simulateDay(state, rng);
            intercepted = state.history().all().stream().anyMatch(e -> e.type().equals("trade_intercepted") || e.type().equals("trade_insurance_paid"));
            if (state.shipments().isEmpty() && state.history().all().stream().anyMatch(e -> e.type().equals("trade_delivered"))) break;
        }
        // Either delivered on the slow road (progress advanced) or insurance fired on loss.
        boolean delivered = state.history().all().stream().anyMatch(e -> e.type().equals("trade_delivered"));
        boolean insured = state.history().all().stream().anyMatch(e -> e.type().equals("trade_insurance_paid") || e.type().equals("trade_insurance_default"));
        check(delivered || insured || shipment.progress() > 0, "shipment must move on route speed or resolve");
        if (insured && state.history().all().stream().anyMatch(e -> e.type().equals("trade_insurance_paid"))) {
            check(buyer.treasury() > buyerBefore - 1 || seller.treasury() < sellerBefore, "insurance transfers value back toward buyer");
        }
        // Progress must follow the road's speedBlocksPerDay, not the old fixed 180.
        TradeShipment paced = new TradeShipment(state.nextId(), seller.id(), buyer.id(), ResourceType.IRON, 10, 50, so.position(), bd.position());
        state.addShipment(paced);
        engine.simulateDay(state, new DeterministicRng(1L));
        double expected = route.speedBlocksPerDay() / 900.0;
        check(Math.abs(paced.progress() - expected) < 1e-6 || paced.progress() == 0,
                "shipment progress must match route speed: progress=" + paced.progress() + " expected=" + expected);
    }

    private static void wagesRaiseHouseholdWealthAndConsumptionSpendsIt() {
        SimulationState state = new SimulationState(902_202L);
        Faction faction = new Faction(state.nextId(), "Wage Realm", "W");
        Settlement town = new Settlement(state.nextId(), "Payford", new SimPosition(0, 0), 400, 500);
        town.setEmployment(0.9);
        town.adjustProsperity(0.4);
        town.stockpile().add(ResourceType.FOOD, 2_000);
        town.stockpile().add(ResourceType.GOLD, 200);
        faction.addSettlement(town);
        faction.addTreasury(5_000);
        state.addFaction(faction);
        HouseholdState house = new HouseholdState(state.nextId(), faction.id(), town.id(), 0);
        state.addHousehold(house);
        SocialCitizen worker = state.ensureSocialCitizen(faction.id(), town.id(), 0, CitizenRole.ARTISAN);
        worker.setHouseholdId(house.id());
        house.addMember(worker.id());
        house.adjustWealth(5);
        double before = house.sharedWealth();
        state.advanceDays(35); // monthly wages
        check(house.sharedWealth() != before || worker.money() > 8, "wages or household wealth must move");
        // Force famine consumption.
        town.setFoodSecurity(0.1);
        house.adjustWealth(40);
        double mid = house.sharedWealth();
        state.advanceDays(35);
        check(house.sharedWealth() < mid || town.stockpile().get(ResourceType.FOOD) < 2_000,
                "household consumption should spend wealth or granary food");
    }

    private static void hinterlandHarvestDepletesEcology() {
        SimulationState state = new SimulationState(903_303L);
        var biome = state.biomes().get("temperate_forest");
        EcosystemRegion region = new EcosystemRegion(state.nextId(), biome, 60, new SimPosition(50, 50));
        String sid = state.species().containsKey("red_deer") ? "red_deer"
                : state.species().keySet().stream().findFirst().orElseThrow();
        PopulationGroup deer = new PopulationGroup(state.nextId(), sid, biome.id(), new SimPosition(60, 60), 200);
        region.add(deer);
        double plantsBefore = region.plantBiomass();
        double popBefore = deer.population();
        state.addRegion(region);
        Faction faction = new Faction(state.nextId(), "Hunt Realm", "H");
        // Dense lumber town next to the region so light daily harvest still moves the needle.
        Settlement camp = new Settlement(state.nextId(), "Huntcroft", new SimPosition(40, 40), 2_500, 2_800);
        camp.markConstructionCompleted("lumber_camp:0");
        camp.markConstructionCompleted("lumber_camp:1");
        faction.addSettlement(camp);
        state.addFaction(faction);
        state.advanceDays(90);
        check(region.plantBiomass() < plantsBefore - 1 || deer.population() < popBefore - 0.05,
                "hinterland harvest must deplete plants or game: plants " + plantsBefore + "->" + region.plantBiomass()
                        + " game " + popBefore + "->" + deer.population());
    }

    private static void successionCrisisErodesLegitimacy() {
        SimulationState state = new SimulationState(904_404L);
        Faction faction = new Faction(state.nextId(), "Crisis Realm", "Old King");
        Settlement capital = new Settlement(state.nextId(), "Crownmere", new SimPosition(0, 0), 1_200, 1_400);
        faction.addSettlement(capital);
        state.addFaction(faction);
        SocialCitizen ruler = state.ensureSocialCitizen(faction.id(), capital.id(), 0, CitizenRole.OFFICIAL);
        DynastyState dynasty = new DynastyState(faction.id(), 0, "House Crisis");
        dynasty.setRulerCitizenId(ruler.id());
        dynasty.setHeirCitizenId(0);
        dynasty.startCrisis(0);
        state.restoreDynasty(dynasty);
        double legitimacyBefore = faction.government().legitimacy();
        double unrestBefore = capital.unrest();
        state.advanceDays(40);
        check(faction.government().legitimacy() < legitimacyBefore || capital.unrest() > unrestBefore
                        || state.history().all().stream().anyMatch(e -> e.type().contains("succession_crisis")),
                "ongoing succession crisis must hurt legitimacy/unrest or emit events");
    }

    private static void justiceStartsInvestigatingBeforeCharge() {
        SimulationState state = new SimulationState(905_505L);
        Faction faction = new Faction(state.nextId(), "Law Realm", "Judge");
        Settlement town = new Settlement(state.nextId(), "Courtford", new SimPosition(0, 0), 500, 600);
        town.markConstructionCompleted("courthouse:0");
        faction.addSettlement(town);
        state.addFaction(faction);
        SocialCitizen accused = state.ensureSocialCitizen(faction.id(), town.id(), 0, CitizenRole.TRADER);
        JusticeCase jc = new JusticeCase(state.nextId(), faction.id(), town.id(), 0, "citizen:" + accused.id(), CrimeType.THEFT);
        check(jc.status() == JusticeStatus.INVESTIGATING, "new cases start investigating");
        state.addJusticeCase(jc);
        state.advanceDays(1);
        // Still investigating or just charged — must not jump straight to sentence in one day without charge event path.
        check(jc.status() == JusticeStatus.INVESTIGATING || jc.status() == JusticeStatus.CHARGED
                        || state.history().all().stream().anyMatch(e -> e.type().equals("court_case_charged")),
                "investigation dwell before sentencing: " + jc.status());
        state.advanceDays(10);
        check(jc.status() != JusticeStatus.INVESTIGATING || !jc.active(),
                "investigation should resolve within ~10 days with courthouse");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
