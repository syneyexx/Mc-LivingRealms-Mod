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
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.FrontierExplorationSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
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
        settlementsRespectTwoThousandBlockSpacing();
        settlementsUseDiverseOrganicBlueprints();
        populatedSettlementsProjectVisibleCrowdsWithinBudget();
        absoluteDayProgressActuallySimulates();
        playerCanFoundARealGrowingRealm();
        citizenIdentityIsStableAndVaried();
        frontierExplorationSeedsFarWorld();
        System.out.println("PASS living-world density: 12 kingdoms + Wizard Trees / 2000-block spacing + frontier continuity + organic streets + bounded crowds + player realms + stable NPC identities + absolute setday progression");
    }

    private static void denseStarterWorldIsHierarchicalAndIdempotent() {
        SimulationState state = new SimulationState(0x51A7E5L);
        DemoSeeder.seed(state);
        check(state.factions().size() == 13, "starter world must contain twelve kingdoms plus Wizard Trees: " + state.factions().size());
        int settlements = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        // 12 realms × ~3 (capital+satellite+rural) + Wizard Trees ≈ 36–45
        check(settlements >= 30 && settlements <= 80, "starter countryside should be sparse 2000m lattice: " + settlements);
        long cities = state.factions().stream().flatMap(f -> f.settlements().stream())
                .filter(s -> s.tier().ordinal() >= Settlement.Tier.CITY.ordinal()).count();
        long towns = state.factions().stream().flatMap(f -> f.settlements().stream())
                .filter(s -> s.tier() == Settlement.Tier.TOWN).count();
        long villages = state.factions().stream().flatMap(f -> f.settlements().stream())
                .filter(s -> s.tier() == Settlement.Tier.VILLAGE).count();
        long hamlets = state.factions().stream().flatMap(f -> f.settlements().stream())
                .filter(s -> s.tier() == Settlement.Tier.HAMLET).count();
        check(cities >= 12 && (towns + villages + hamlets) >= 12,
                "starter hierarchy lacks cities plus supporting towns/villages/hamlets: cities="+cities+" towns="+towns+" villages="+villages+" hamlets="+hamlets);
        long monarchies=state.factions().stream().filter(f->f.government().type()==GovernmentType.FEUDAL_MONARCHY).count();
        check(monarchies==12,"starter world must retain twelve ordinary kingdoms: "+monarchies);
        Faction wizard=state.factions().stream().filter(f->f.name().equals("Wizard Trees")).findFirst().orElseThrow();
        check(wizard.government().type()==GovernmentType.THEOCRACY&&wizard.settlements().size()==3,"Wizard Trees must be a distinct hidden theocratic faction");
        check(SettlementDensitySeeder.ensureStarterDensity(state) == 0, "density migration is not idempotent");
        state.advanceDays(1);
        check(state.routes().size() >= 8 && state.routes().size() <= 160, "kingdom road graph density out of range: " + state.routes().size());

        Set<Long> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Faction faction : state.factions()) for (Settlement settlement : faction.settlements()) {
            check(ids.add(settlement.id()), "duplicate settlement id " + settlement.id());
            check(names.add(settlement.name()), "duplicate settlement name " + settlement.name());
        }
    }

    private static void settlementsRespectTwoThousandBlockSpacing() {
        SimulationState state = new SimulationState(0x2000L);
        DemoSeeder.seed(state);
        List<Settlement> all = state.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            double dist = all.get(i).position().distanceTo(all.get(j).position());
            check(dist >= PlayerSettlementFounder.MIN_SETTLEMENT_SPACING - 1.0,
                    "settlements closer than 2000m: " + all.get(i).name() + " ↔ " + all.get(j).name() + " = " + Math.round(dist));
        }
        var blocked = PlayerSettlementFounder.found(state, "player:near", "Near", "Tooclose",
                state.factions().getFirst().settlements().getFirst().position());
        check(!blocked.success(), "founding on top of a capital must fail");
        check(blocked.reason().contains("2000") || blocked.reason().contains("too close"),
                "founding error should mention clearance: " + blocked.reason());
    }

    private static void frontierExplorationSeedsFarWorld() {
        SimulationState state = new SimulationState(99L);
        DemoSeeder.seed(state);
        int before = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        int added = FrontierExplorationSeeder.ensureNear(state, new SimPosition(40_000, -35_000));
        check(added == 1, "frontier seeder must plant one outpost in empty far-world");
        check(state.factions().stream().mapToInt(f -> f.settlements().size()).sum() == before + 1, "frontier settlement must be attached");
        check(FrontierExplorationSeeder.ensureNear(state, new SimPosition(40_000, -35_000)) == 0, "frontier seeding must be idempotent near the new outpost");
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
        check(countrysideSinglePath >= 8, "hamlets should use a single countryside path, not a grid: " + countrysideSinglePath);
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
