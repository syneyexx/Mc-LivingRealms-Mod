package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenMaterializationPlanner;
import dev.livingrealms.sim.civilian.CitizenIdentity;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.government.GovernmentType;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SettlementExpansionEngine;
import dev.livingrealms.sim.world.SettlementSpacingPolicy;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Product gate for the living world density, organic layouts and manual progress semantics. */
public final class LivingWorldDensityTest {
    private LivingWorldDensityTest() {}

    public static void main(String[] args) {
        denseStarterWorldIsHierarchicalAndIdempotent();
        starterRealmsHaveNoAccidentalSettlementGaps();
        settlementsRespectAuthoredSpacing();
        settlementsUseDiverseOrganicBlueprints();
        populatedSettlementsProjectVisibleCrowdsWithinBudget();
        absoluteDayProgressActuallySimulates();
        playerCanFoundARealGrowingRealm();
        citizenIdentityIsStableAndVaried();
        frontierExplorationDoesNotSeedSettlements();
        System.out.println("PASS living-world density: hierarchical starter realms + role-aware spacing"
                + " + causal expansion (no exploration spawn) + organic streets + bounded crowds + player realms + stable NPC identities + absolute setday progression");
    }

    private static void denseStarterWorldIsHierarchicalAndIdempotent() {
        SimulationState state = new SimulationState(0x51A7E5L);
        DemoSeeder.seed(state);
        check(state.factions().size() == 13, "starter world must contain twelve kingdoms plus Wizard Trees: " + state.factions().size());
        int surface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .mapToInt(f -> f.settlements().size()).sum();
        check(surface >= SettlementDensitySeeder.MIN_SURFACE_STARTER_SETTLEMENTS
                        && surface <= SettlementDensitySeeder.MAX_SURFACE_STARTER_SETTLEMENTS,
                "surface starter count outside bounded hierarchy range: " + surface);
        for (Faction faction : state.factions()) {
            if (faction.name().equals("Wizard Trees")) continue;
            long capitals = faction.settlements().stream().filter(s -> s.role() == SettlementRole.CAPITAL).count();
            long towns = faction.settlements().stream().filter(s -> s.role() == SettlementRole.TOWN).count();
            long villages = faction.settlements().stream().filter(s -> s.role() == SettlementRole.VILLAGE).count();
            long hamlets = faction.settlements().stream().filter(s -> s.role() == SettlementRole.HAMLET).count();
            check(capitals == 1, faction.name() + " capital count " + capitals);
            check(towns >= SettlementDensitySeeder.MIN_TOWNS_PER_REALM
                            && towns <= SettlementDensitySeeder.MAX_TOWNS_PER_REALM,
                    faction.name() + " town count " + towns);
            check(villages >= SettlementDensitySeeder.MIN_VILLAGES_PER_REALM
                            && villages <= SettlementDensitySeeder.MAX_VILLAGES_PER_REALM,
                    faction.name() + " village count " + villages);
            check(hamlets >= SettlementDensitySeeder.MIN_RURAL_HAMLETS_PER_REALM
                            && hamlets <= SettlementDensitySeeder.MAX_RURAL_HAMLETS_PER_REALM,
                    faction.name() + " hamlet count " + hamlets);
        }
        long monarchies=state.factions().stream().filter(f->f.government().type()==GovernmentType.FEUDAL_MONARCHY).count();
        check(monarchies==12,"starter world must retain twelve ordinary kingdoms: "+monarchies);
        Faction wizard=state.factions().stream().filter(f->f.name().equals("Wizard Trees")).findFirst().orElseThrow();
        check(wizard.government().type()==GovernmentType.THEOCRACY&&wizard.settlements().size()==3,"Wizard Trees must be a distinct hidden theocratic faction");
        check(SettlementDensitySeeder.ensureStarterDensity(state) == 0, "density migration is not idempotent");
        state.advanceDays(1);
        check(state.routes().size() >= 8 && state.routes().size() <= 400, "kingdom road graph density out of range: " + state.routes().size());

        Set<Long> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Faction faction : state.factions()) for (Settlement settlement : faction.settlements()) {
            check(ids.add(settlement.id()), "duplicate settlement id " + settlement.id());
            check(names.add(settlement.name()), "duplicate settlement name " + settlement.name());
        }
    }

    private static void starterRealmsHaveNoAccidentalSettlementGaps() {
        SimulationState state = new SimulationState(0x6A4F11L);
        DemoSeeder.seed(state);
        for (Faction faction : state.factions()) {
            if (faction.name().equals("Wizard Trees")) continue;
            List<Settlement> ordinary = faction.settlements().stream()
                    .filter(s -> s.role().ordinarySurfaceSettlement())
                    .toList();
            Settlement capital = ordinary.stream()
                    .filter(s -> s.role() == SettlementRole.CAPITAL)
                    .findFirst().orElseThrow();
            double nearestTown = ordinary.stream()
                    .filter(s -> s.role() == SettlementRole.TOWN)
                    .mapToDouble(s -> s.position().distanceTo(capital.position()))
                    .min().orElseThrow();
            check(nearestTown >= 650.0 - 1.0 && nearestTown <= 800.0 + 1.0,
                    faction.name() + " must have an inner town gap-anchor within 650-800 blocks: "
                            + Math.round(nearestTown));

            for (Settlement settlement : ordinary) {
                double nearest = ordinary.stream()
                        .filter(other -> other.id() != settlement.id())
                        .mapToDouble(other -> other.position().distanceTo(settlement.position()))
                        .min().orElse(Double.POSITIVE_INFINITY);
                check(nearest <= 800.0 + 1.0,
                        "accidental inhabited-territory gap: " + faction.name() + " / "
                                + settlement.name() + " nearest true settlement=" + Math.round(nearest));
            }
        }
    }

    private static void settlementsRespectAuthoredSpacing() {
        SimulationState state = new SimulationState(0x2000L);
        DemoSeeder.seed(state);
        List<Settlement> all = state.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            Settlement a = all.get(i), b = all.get(j);
            double dist = a.position().distanceTo(b.position());
            double floor = SettlementSpacingPolicy.minimumDistance(a, b);
            check(dist >= floor - 1.0,
                    "settlement role floor violated: " + a.name() + "(" + a.role() + ") ↔ "
                            + b.name() + "(" + b.role() + ") = " + Math.round(dist)
                            + " < " + Math.round(floor));
        }
        Settlement capital = state.factions().getFirst().settlements().stream()
                .filter(s -> s.role() == SettlementRole.CAPITAL).findFirst().orElseThrow();
        var blocked = PlayerSettlementFounder.found(state, "player:near", "Near", "Tooclose", capital.position());
        check(!blocked.success(), "founding on top of a capital must fail");
        check(blocked.reason().contains("2500") || blocked.reason().contains("blocks"),
                "founding error should mention role-aware clearance: " + blocked.reason());
    }

    private static void frontierExplorationDoesNotSeedSettlements() {
        SimulationState state = new SimulationState(99L);
        DemoSeeder.seed(state);
        int before = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        check(SettlementExpansionEngine.ensureNear(state, new SimPosition(40_000, -35_000)) == 0,
                "player proximity must not create settlements");
        check(SettlementExpansionEngine.ensureNear(state, new SimPosition(40_000, -35_000)) == 0,
                "expansion ensureNear must refuse proximity founding");
        check(state.factions().stream().mapToInt(f -> f.settlements().size()).sum() == before,
                "settlement count must be unchanged by exploration");

        Faction host = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees") && !f.settlements().isEmpty())
                .findFirst().orElseThrow();
        Settlement capital = host.settlements().stream()
                .max(java.util.Comparator.comparingInt(Settlement::population)).orElseThrow();
        capital.addPopulation(2_400);
        host.stockpile().add(dev.livingrealms.sim.faction.ResourceType.GRAIN, 5_000);
        host.addTreasury(12_000);
        int hostBefore = host.settlements().size();
        int founded = 0;
        for (int i = 0; i < 80 && founded == 0; i++) {
            founded = SettlementExpansionEngine.simulateDay(state, new DeterministicRng(0xCA05A1L ^ (i * 17L)));
        }
        check(founded >= 1 || host.settlements().size() > hostBefore,
                "causal expansion must found under pressure");
        List<Settlement> all = state.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Settlement a = all.get(i), b = all.get(j);
                check(a.position().distanceTo(b.position())
                                >= SettlementSpacingPolicy.minimumDistance(a, b) - 1.0,
                        "causal colony violated role-aware spacing: " + a.name() + " ↔ " + b.name());
            }
        }
        check(host.settlements().stream().anyMatch(s -> s.origin() == SettlementOrigin.CAUSAL_EXPANSION
                        || SettlementDensitySeeder.authoredExpansionCatalogNames().contains(s.name())),
                "new colony should be causal or claimed catalog Spec");
    }

    private static void settlementsUseDiverseOrganicBlueprints() {
        SimulationState state = new SimulationState(712_991L);
        DemoSeeder.seed(state);
        Set<String> layouts = new HashSet<>();
        Set<String> houseFootprints = new HashSet<>();
        Set<String> houseBlueprints = new HashSet<>();
        Set<String> marketBlueprints = new HashSet<>();
        int roadRichSettlements = 0;
        int countrysideSinglePath = 0;
        for (Faction faction : state.factions()) for (Settlement settlement : faction.settlements()) {
            layouts.add(SettlementPlanner.layoutArchetype(faction, settlement));
            var plan = SettlementPlanner.plan(faction, settlement);
            long roads = plan.stream().filter(i -> i.role() == StructureRole.ROAD).count();
            if (roads >= 5) roadRichSettlements++;
            if (settlement.tier().ordinal() <= Settlement.Tier.HAMLET.ordinal() && roads <= 2) countrysideSinglePath++;
            plan.stream().filter(i -> i.role() == StructureRole.HOUSE).limit(12).forEach(intent -> {
                houseFootprints.add(intent.width() + "x" + intent.depth());
                houseBlueprints.add(StructureBlueprintFactory.create(intent).id());
            });
            plan.stream().filter(i -> i.role() == StructureRole.MARKET).forEach(intent -> marketBlueprints.add(StructureBlueprintFactory.create(intent).id()));
        }
        check(layouts.size() >= 4, "not enough settlement layout archetypes: " + layouts);
        check(houseFootprints.size() >= 3, "house footprints remain too repetitive: " + houseFootprints);
        check(houseBlueprints.size() >= 4, "house geometry remains too repetitive: " + houseBlueprints);
        check(marketBlueprints.size() >= 2, "market geometry did not diversify: " + marketBlueprints);
        check(roadRichSettlements >= 8, "towns/cities should keep real street networks: " + roadRichSettlements);
        check(countrysideSinglePath >= 4, "hamlets should use a single countryside path, not a grid: " + countrysideSinglePath);
    }

    private static void populatedSettlementsProjectVisibleCrowdsWithinBudget() {
        SimulationState state = new SimulationState(123_456L);
        DemoSeeder.seed(state);
        Settlement capital = state.factions().getFirst().settlements().stream()
                .filter(s -> s.tier().ordinal() >= Settlement.Tier.CITY.ordinal()).findFirst().orElseThrow();
        int budget = RuntimeProjectionPolicy.citizenBudget(state.config());
        double radius = RuntimeProjectionPolicy.citizenRadiusBlocks(state.config());
        var projections = CitizenMaterializationPlanner.plan(state.factions(), List.of(capital.position()), radius, budget);
        long local = projections.stream().filter(p -> p.settlementId() == capital.id()).count();
        check(local >= 30, "capital still looks underpopulated: projected=" + local);
        check(projections.size() <= budget, "citizen projection exceeded global budget");
        check(radius >= 520.0D, "citizen visibility radius is still too short");
    }

    private static void absoluteDayProgressActuallySimulates() {
        SimulationState state = new SimulationState(987_654L);
        DemoSeeder.seed(state);
        long beforeHistory = state.history().all().size();
        long moved = state.advanceToDay(50);
        check(moved == 50 && state.clock().day() == 50, "setday progression did not reach target day");
        check(state.history().all().size() > beforeHistory, "setday changed clock without simulation progress");
        check(state.advanceToDay(50) == 0, "same-day setday must be a no-op");
        boolean backwardRejected = false;
        try { state.advanceToDay(49); } catch (IllegalArgumentException expected) { backwardRejected = true; }
        check(backwardRejected, "setday allowed history rewind");
        boolean runawayRejected = false;
        try { state.advanceToDay(50 + SimulationState.MAX_MANUAL_DAY_JUMP + 1); } catch (IllegalArgumentException expected) { runawayRejected = true; }
        check(runawayRejected, "setday allowed an unbounded synchronous jump");
    }


    private static void playerCanFoundARealGrowingRealm() {
        SimulationState state=new SimulationState(456_771L);DemoSeeder.seed(state);
        int factionsBefore=state.factions().size();
        var result=PlayerSettlementFounder.found(state,"player:test-uuid","Romy","Newhaven",new SimPosition(20_000,20_000));
        check(result.success(),"player realm founding failed: "+result.reason());
        check(state.factions().size()==factionsBefore+1,"player realm was not canonical");
        var faction=state.findFaction(result.factionId()).orElseThrow();
        var settlement=state.findSettlement(result.settlementId()).orElseThrow();
        check(faction.name().equals("Realm of Newhaven"),"player realm naming");
        check(state.playerStanding("player:test-uuid").isMemberOf(faction.id()),"founder membership missing");
        check(state.playerStanding("player:test-uuid").rank()==dev.livingrealms.sim.player.FactionRank.RULER,"founder must be canonical ruler, not merely a noble");
        check(state.playerRuler(faction.id()).isPresent(),"player ruler lookup missing");
        int population=settlement.population();
        // Founding camps start without free farms; clear a plot then let attraction run.
        check(settlement.markConstructionCompleted("farm:0"), "founding camp can clear a farm plot");
        settlement.stockpile().add(dev.livingrealms.sim.faction.ResourceType.GRAIN, 200);
        settlement.stockpile().add(dev.livingrealms.sim.faction.ResourceType.BREAD, 120);
        settlement.setFoodSecurity(.85);
        state.advanceDays(60);
        check(settlement.population()>population,"player settlement did not attract civilians");
        check(state.socialCitizens().stream().noneMatch(c->c.factionId()==faction.id()&&c.name().equals("Romy")),"player ruler was duplicated as a fake NPC citizen");
        check(faction.relations().size()>=factionsBefore,"player realm lacks relations with existing kingdoms");
        check(SettlementPlanner.plan(faction,settlement).stream().anyMatch(i->i.role()==StructureRole.ROAD),"player settlement has no physical street plan");
    }

    private static void citizenIdentityIsStableAndVaried(){
        Set<String> names=new HashSet<>();Set<Integer> skins=new HashSet<>();
        for(int slot=0;slot<40;slot++){var a=CitizenIdentity.forProjection(11,22,slot,CitizenRole.FARMER);var b=CitizenIdentity.forProjection(11,22,slot,CitizenRole.FARMER);check(a.equals(b),"citizen identity is not deterministic");names.add(a.name());skins.add(a.skinVariant());}
        check(names.size()>=30,"citizen names lack variation: "+names.size());
        check(skins.size()>=8,"citizen skins lack variation: "+skins.size());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
