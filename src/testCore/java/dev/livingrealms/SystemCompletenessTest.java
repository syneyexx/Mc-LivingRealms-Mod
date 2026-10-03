package dev.livingrealms;

import dev.livingrealms.sim.aviation.*;
import dev.livingrealms.sim.biome.*;
import dev.livingrealms.sim.diplomacy.*;
import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.config.*;
import dev.livingrealms.sim.compat.*;
import dev.livingrealms.sim.military.*;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.player.*;
import dev.livingrealms.sim.property.*;
import dev.livingrealms.sim.economy.*;
import dev.livingrealms.sim.economy.primary.*;
import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.*;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.society.*;
import dev.livingrealms.sim.territory.*;
import dev.livingrealms.sim.transport.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.ui.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Cross-system regression suite for stateful strategic systems introduced after the original
 * ecology test. This deliberately tests negative paths as well as save/load persistence.
 */
public final class SystemCompletenessTest {
    private SystemCompletenessTest() {}

    public static void main(String[] args) {
        testSimulationConfigPersistence();
        testRuntimeProjectionPolicy();
        testCompatibilityPolicy();
        testTerritoryAndCrime();
        testContainerTheftAssessment();
        testPropertyRights();
        testBiomeClassification();
        testLawEnforcementCustodyAndBountyClaim();
        testBountyBoardAndNpcPlanner();
        testGovernmentSuccession();
        testMarketAndTransport();
        testPlayerMarketTransactions();
        testPrimaryEconomy();
        testCitizenRoutines();
        testRouteProjectionPlanner();
        testIndustrySitePlanner();
        testIndustrialSiteLifecycleAndPersistence();
        testMilitaryObjectivesDriveOperations();
        testWarTreatyAviationAndPersistence();
        testRebellion();
        testPhysicalProjectionAccounting();
        testSpeciesMobilityAndMorphology();
        testPlayerReputationAndMembership();
        testNavalSystems();
        testDashboardSnapshotAndCodec();
        testSettlementManagementAndTaxActions();
        System.out.println("PASS strategic completeness: territory + crime/bounty + government + market/routes + war/treaty + aviation + naval + player membership/reputation + rebellion + physical projection accounting + schema12 persistence");
    }


    private static void testSimulationConfigPersistence(){
        SimulationState state=new SimulationState(9L);
        state.setConfig(SimulationPreset.IMMERSIVE.config());
        check(SimulationPreset.labelFor(state.config()).equals("IMMERSIVE"),"preset label mismatch");
        byte[] encoded=SimulationStateCodec.encode(state);
        SimulationState restored=SimulationStateCodec.decode(encoded);
        check(restored.config().equals(SimulationPreset.IMMERSIVE.config()),"schema9 simulation config must persist exactly");
        check(restored.config().maxPhysicalWildlife()==220&&restored.config().constructionBlockOpsPerTick()==220,"restored preset budgets mismatch");
        for(SimulationPreset preset:SimulationPreset.values()){SimulationConfig c=preset.config();check(c.regionalRadiusBlocks()>c.physicalRadiusBlocks(),"preset radius ordering invalid: "+preset);}
    }

    private static void testRuntimeProjectionPolicy(){
        SimulationConfig performance=SimulationPreset.PERFORMANCE.config();
        SimulationConfig cinematic=SimulationPreset.CINEMATIC.config();
        var wildlifePerformance=RuntimeProjectionPolicy.wildlife(performance);
        var wildlifeCinematic=RuntimeProjectionPolicy.wildlife(cinematic);
        check(wildlifePerformance.physicalRadiusBlocks()==performance.physicalRadiusBlocks(),"wildlife physical radius must follow config");
        check(wildlifePerformance.regionalRadiusBlocks()==performance.regionalRadiusBlocks(),"wildlife regional radius must follow config");
        check(wildlifePerformance.maxAnimalsPerPlayer()==performance.maxPhysicalWildlife(),"wildlife budget must follow config");
        check(wildlifeCinematic.maxAnimalsPerPlayer()>wildlifePerformance.maxAnimalsPerPlayer(),"cinematic wildlife budget must exceed performance");
        var caravanPerformance=RuntimeProjectionPolicy.caravans(performance);
        var caravanCinematic=RuntimeProjectionPolicy.caravans(cinematic);
        check(caravanPerformance.physicalRadiusBlocks()==performance.physicalRadiusBlocks(),"caravan radius must follow config");
        check(caravanPerformance.maxPhysicalCaravans()==performance.maxPhysicalCaravans(),"caravan budget must follow config");
        check(caravanCinematic.maxPhysicalCaravans()>caravanPerformance.maxPhysicalCaravans(),"cinematic caravan budget must exceed performance");
        check(RuntimeProjectionPolicy.aircraftRadiusBlocks(cinematic)>RuntimeProjectionPolicy.aircraftRadiusBlocks(performance),"aircraft radius must scale with profile");
        check(RuntimeProjectionPolicy.aircraftBudget(cinematic)>RuntimeProjectionPolicy.aircraftBudget(performance),"aircraft budget must scale with profile");
        check(RuntimeProjectionPolicy.navalRadiusBlocks(cinematic)>=RuntimeProjectionPolicy.navalRadiusBlocks(performance),"naval radius must scale with profile");
        check(RuntimeProjectionPolicy.citizenBudget(cinematic)>RuntimeProjectionPolicy.citizenBudget(performance),"citizen budget must scale with profile");
        SimulationConfig disabledWildlife=new SimulationConfig(128,512,0,0,0,0,10,3,1,.1,.8,10);
        check(RuntimeProjectionPolicy.wildlife(disabledWildlife).maxAnimalsPerPlayer()==0,"zero wildlife budget must be supported");
        check(RuntimeProjectionPolicy.caravans(disabledWildlife).maxPhysicalCaravans()==0,"zero caravan budget must be supported");
        check(RuntimeProjectionPolicy.aircraftBudget(disabledWildlife)==0,"zero military budget must disable aircraft projection");
        check(disabledWildlife.strategicDaysPerStep()==3,"strategic step test fixture invalid");
    }

    private static void testCompatibilityPolicy(){
        ModCompatibilityPolicy.validate();
        check(ModCompatibilityPolicy.targets().size()>=55,"expanded target compatibility list must remain explicit");
        check(ModCompatibilityPolicy.find("irons_lib").isPresent(),"Iron\'s Lib must be part of the target pack");
        check(ModCompatibilityPolicy.isPlayerOnly("mr_guns"),"Guns++ must remain player-only for NPC equipment");
        check(!ModCompatibilityPolicy.mayUseForLivingWorld("mr_guns"),"Guns++ must be denied to faction/world equipment discovery");
        check(ModCompatibilityPolicy.isPlayerOnly("gamingbarns_guns")&&!ModCompatibilityPolicy.mayUseForLivingWorld("gamingbarns_guns"),"GamingBarn guns must remain NPC-forbidden");
        check(ModCompatibilityPolicy.find("armory_rpgs").isPresent(),"actual Armory mod id must be detected");
        check(ModCompatibilityPolicy.find("irons_spells_dynamic_skilltree").isPresent(),"actual Iron's skill-tree mod id must be detected");
        check(ModCompatibilityPolicy.find("pufferfish_unofficial_additions").isPresent(),"actual Pufferfish additions mod id must be detected");
        check(ModCompatibilityPolicy.removedFromTargetPack().contains("create_deep_seas")&&ModCompatibilityPolicy.removedFromTargetPack().contains("create_aeronautics"),"removed Create addons must stay excluded");
        check(ModCompatibilityPolicy.find("create").orElseThrow().strategy()==ModCompatibilityPolicy.Strategy.HARD_DEPENDENCY,"Create must remain the only hard dependency");
        check(ModCompatibilityPolicy.worldgenSensitive("biomesoplenty")&&ModCompatibilityPolicy.worldgenSensitive("terralith")&&ModCompatibilityPolicy.worldgenSensitive("terrablender")&&ModCompatibilityPolicy.worldgenSensitive("lithostitched"),"worldgen stack must be classified as sensitive");
        check(!ModCompatibilityPolicy.mayReplaceBiomeSource(),"Living Realms must never replace the modpack biome source");
        check(!ModCompatibilityPolicy.mayOverwriteBlockEntities(),"Living Realms builders must preserve foreign block entities");
    }

    private static void testContainerTheftAssessment(){
        Map<String,ContainerTheftAssessment.Lot> before=new LinkedHashMap<>();
        before.put("minecraft:iron_ingot",new ContainerTheftAssessment.Lot(20,4));
        before.put("minecraft:bread",new ContainerTheftAssessment.Lot(12,1));
        Map<String,ContainerTheftAssessment.Lot> after=new LinkedHashMap<>();
        after.put("minecraft:iron_ingot",new ContainerTheftAssessment.Lot(13,4));
        after.put("minecraft:bread",new ContainerTheftAssessment.Lot(12,1));
        after.put("minecraft:dirt",new ContainerTheftAssessment.Lot(64,.1));
        var result=ContainerTheftAssessment.assess(before,after);
        check(result.removedItems()==7,"container theft must count only removed property items");
        check(Math.abs(result.stolenValue()-28)<.001,"container theft value must use original item value");
        check(result.removedByKey().get("minecraft:iron_ingot")==7,"container theft item ledger mismatch");
        check(!ContainerTheftAssessment.assess(before,before).theftOccurred(),"unchanged container must not produce theft");
    }

    private static void testTerritoryAndCrime() {
        SimulationState state = new SimulationState(11L);
        Faction a = faction(state, "Aster", "Elara", 0, 0, 800, 900);
        Faction b = faction(state, "Veyra", "Oren", 900, 0, 800, 900);

        Jurisdiction nearA = TerritoryEngine.resolve(state.factions(), new SimPosition(20, 0));
        check(nearA.primaryFactionId() == a.id(), "Aster must own its core jurisdiction");
        Jurisdiction nearB = TerritoryEngine.resolve(state.factions(), new SimPosition(880, 0));
        check(nearB.primaryFactionId() == b.id(), "Veyra must own its core jurisdiction");
        Jurisdiction middle = TerritoryEngine.resolve(state.factions(), new SimPosition(450, 0), 1000);
        check(middle.contested(), "equidistant border should be contested under a wide margin");

        String actor = "player:romy-test";
        CrimeResult secretTheft = state.reportCrime(actor, a.id(), CrimeType.THEFT, 100,
                new SimPosition(10, 0), false, 0, "shop", "none");
        check(!secretTheft.registered(), "unwitnessed theft must not register");
        check(state.crimeLedger().findProfile(actor).map(p -> p.find(a.id()).map(JurisdictionWanted::bounty).orElse(0.0)).orElse(0.0) == 0,
                "unwitnessed theft must not create bounty");

        CrimeResult witnessedTheft = state.reportCrime(actor, a.id(), CrimeType.THEFT, 100,
                new SimPosition(10, 0), true, 2, "shop", "two witnesses");
        check(witnessedTheft.registered() && witnessedTheft.bountyAdded() > 0, "witnessed theft must create bounty");
        double bountyAfterTheft = state.crimeLedger().profile(actor).in(a.id()).bounty();
        check(bountyAfterTheft > 0, "wanted profile must store bounty");
        check(state.crimeLedger().profile(actor).find(b.id()).isEmpty(), "wanted status must be jurisdiction-local");

        CrimeResult murder = state.reportCrime(actor, a.id(), CrimeType.MURDER, 0,
                new SimPosition(11, 0), true, 4, "citizen:test", "eyewitnesses");
        check(murder.bountyAdded() > witnessedTheft.bountyAdded(), "murder bounty must exceed theft bounty");
        check(state.crimeLedger().profile(actor).in(a.id()).violentCrimes() == 1, "violent crime counter must increment");

        state.advanceDays(1); // posts bounty contract and performs daily decay
        check(state.bounties().stream().anyMatch(x -> x.actorKey().equals(actor) && x.issuerFactionId() == a.id()),
                "large wanted bounty must produce a bounty contract");
        double beforePayment = state.crimeLedger().profile(actor).in(a.id()).bounty();
        double paid = state.payFine(actor, a.id(), Math.min(20, beforePayment));
        check(paid > 0 && state.crimeLedger().profile(actor).in(a.id()).bounty() < beforePayment,
                "fine payment must reduce bounty");
        double remaining = state.crimeLedger().profile(actor).in(a.id()).bounty();
        double captured = state.captureCriminal(actor, a.id());
        check(close(captured, remaining), "capture must return the outstanding bounty");
        check(state.crimeLedger().profile(actor).in(a.id()).bounty() == 0, "capture must clear bounty");
        state.advanceDays(1);
        check(state.bounties().stream().filter(x -> x.actorKey().equals(actor)).allMatch(x -> x.status() == BountyContract.Status.CANCELLED || x.status() == BountyContract.Status.CLAIMED),
                "cleared wanted record must not leave an open bounty contract");
    }




    private static void testBiomeClassification(){
        var catalog=BiomeCatalog.defaults();
        check(BiomeClassifier.classify(new BiomeObservation("minecraft:deep_ocean",Set.of("ocean","deep_ocean"),.5,.5),catalog).id().equals("deep_ocean"),"deep ocean classification");
        check(BiomeClassifier.classify(new BiomeObservation("minecraft:mangrove_swamp",Set.of("mangrove","swamp","wetland"),.8,.95),catalog).id().equals("mangrove"),"mangrove classification");
        check(BiomeClassifier.classify(new BiomeObservation("minecraft:frozen_peaks",Set.of("mountain","peak","frozen"),-.5,.3),catalog).id().equals("tundra"),"frozen priority classification");
        check(BiomeClassifier.classify(new BiomeObservation("minecraft:old_growth_pine_taiga",Set.of("forest","taiga"),.25,.8),catalog).id().equals("taiga"),"taiga classification");
        check(BiomeClassifier.classify(new BiomeObservation("minecraft:savanna",Set.of("savanna","grassland"),1.2,.2),catalog).id().equals("savanna"),"savanna classification");
        check(BiomeClassifier.classify(new BiomeObservation("modded:humid_forest",Set.of("forest"),.7,.95),catalog).id().equals("temperate_rainforest"),"modded forest climate fallback");
        var bop=BiomeSignalNormalizer.observation("biomesoplenty:redwood_forest",Set.of(),.65,.72);check(bop.tags().contains("forest"),"BOP redwood ids must normalize as forest");
        var bopBog=BiomeSignalNormalizer.observation("biomesoplenty:bayou",Set.of(),.8,.9);check(bopBog.tags().contains("wetland")&&bopBog.tags().contains("freshwater"),"BOP bayou must normalize as wetland");
        var terralith=BiomeSignalNormalizer.observation("terralith:rocky_mountains",Set.of(),.35,.45);check(terralith.tags().contains("mountain")&&terralith.tags().contains("rocky"),"Terralith rocky mountains must preserve terrain signals");
        var terralithSteppe=BiomeSignalNormalizer.observation("terralith:steppe",Set.of(),.75,.25);check(terralithSteppe.tags().contains("grassland"),"Terralith steppe must normalize as grassland");
    }


    private static void testPlayerMarketTransactions(){
        SimulationState state=new SimulationState(5150L);Faction faction=faction(state,"Market Realm","Mara",0,0,500,700);
        faction.stockpile().add(ResourceType.IRON,100);faction.restoreTreasury(500);
        double stock0=faction.stockpile().get(ResourceType.IRON),treasury0=faction.treasury();
        var buy=MarketTransactionEngine.quote(faction,ResourceType.IRON,MarketTransactionEngine.Side.BUY_FROM_REALM);
        check(buy.available()&&buy.units()==MarketTransactionEngine.PACKAGE_UNITS&&buy.emeralds()>0,"player buy quote invalid");
        var buyCommit=MarketTransactionEngine.commit(faction,buy);check(buyCommit.success(),"player buy commit failed");
        check(close(faction.stockpile().get(ResourceType.IRON),stock0-buy.units()),"buy must remove exact canonical stock");
        check(close(faction.treasury(),treasury0+buy.emeralds()),"buy must credit exact realm treasury");
        var stale=MarketTransactionEngine.commit(faction,buy);check(!stale.success()&&stale.reason().equals("quote_changed"),"stale market quote must not duplicate stock");
        double beforeSellStock=faction.stockpile().get(ResourceType.IRON),beforeSellTreasury=faction.treasury();
        var sell=MarketTransactionEngine.quote(faction,ResourceType.IRON,MarketTransactionEngine.Side.SELL_TO_REALM);
        check(sell.available(),"player sell quote invalid");check(MarketTransactionEngine.commit(faction,sell).success(),"player sell commit failed");
        check(close(faction.stockpile().get(ResourceType.IRON),beforeSellStock+sell.units()),"sell must add exact canonical stock");
        check(close(faction.treasury(),beforeSellTreasury-sell.emeralds()),"sell must debit exact realm treasury");
        faction.stockpile().take(ResourceType.IRON,100000);check(!MarketTransactionEngine.quote(faction,ResourceType.IRON,MarketTransactionEngine.Side.BUY_FROM_REALM).available(),"realm cannot sell stock it does not have");
        faction.restoreTreasury(0);check(!MarketTransactionEngine.quote(faction,ResourceType.IRON,MarketTransactionEngine.Side.SELL_TO_REALM).available(),"realm cannot buy without treasury funds");
        check(!MarketTransactionEngine.playerTradable().contains(ResourceType.MACHINERY),"abstract machinery must not be exposed as an unsafe generic player item");
    }

    private static void testPrimaryEconomy(){
        SimulationState state=new SimulationState(222L);
        EcosystemRegion forest=new EcosystemRegion(state.nextId(),state.biomes().get("temperate_forest"),40,new SimPosition(0,0));state.addRegion(forest);
        Faction faction=faction(state,"Resource Realm","Forester",0,0,900,1000);
        Settlement settlement=faction.settlements().getFirst();
        List<ConstructionIntent> plan=PrimaryEconomyPlanner.plan(state,faction,settlement);
        check(plan.stream().anyMatch(i->i.role()==StructureRole.LUMBER_CAMP),"forest settlement must plan lumber camp");
        check(plan.stream().anyMatch(i->i.role()==StructureRole.MINE),"temperate settlement must permit basic mine");
        check(plan.stream().anyMatch(i->i.role()==StructureRole.FISHERY),"freshwater forest settlement must permit fishery");
        for(ConstructionIntent i:plan)settlement.markConstructionCompleted(i.key());
        double wood=settlement.stockpile().get(ResourceType.WOOD),food=settlement.stockpile().get(ResourceType.FOOD),iron=settlement.stockpile().get(ResourceType.IRON);
        new PrimaryEconomyEngine().simulateDay(state);
        check(settlement.stockpile().get(ResourceType.WOOD)>wood,"completed lumber camp must produce wood into settlement stockpile");
        check(settlement.stockpile().get(ResourceType.FOOD)>food,"completed fishery must produce food into settlement stockpile");
        check(settlement.stockpile().get(ResourceType.IRON)>iron,"completed mine must produce ore into settlement stockpile");
        for(StructureRole role:List.of(StructureRole.MINE,StructureRole.LUMBER_CAMP,StructureRole.FISHERY)){
            ConstructionIntent intent=new ConstructionIntent(role.name().toLowerCase()+":test",faction.id(),settlement.id(),role,settlement.position(),13,11,0,50);
            check(!StructureBlueprintFactory.create(intent).placements().isEmpty(),"primary economy blueprint must contain blocks: "+role);
        }
        SimulationState desert=new SimulationState(223L);desert.addRegion(new EcosystemRegion(desert.nextId(),desert.biomes().get("hot_desert"),50,new SimPosition(0,0)));Faction d=faction(desert,"Desert Realm","Dune",0,0,900,1000);
        check(PrimaryEconomyPlanner.plan(desert,d,d.settlements().getFirst()).stream().noneMatch(i->i.role()==StructureRole.FISHERY),"dry desert must not plan a fishery");
    }


    private static void testCitizenRoutines(){
        SimulationState state=new SimulationState(224L);state.addRegion(new EcosystemRegion(state.nextId(),state.biomes().get("temperate_forest"),45,new SimPosition(0,0)));Faction f=faction(state,"Working Realm","Steward",0,0,1400,1500);Settlement s=f.settlements().getFirst();
        for(ConstructionIntent i:SettlementPlanner.plan(f,s))if(Set.of(StructureRole.KEEP,StructureRole.FARM,StructureRole.MARKET,StructureRole.WORKSHOP).contains(i.role()))s.markConstructionCompleted(i.key());
        for(ConstructionIntent i:PrimaryEconomyPlanner.plan(state,f,s))s.markConstructionCompleted(i.key());
        check(CitizenRoutinePlanner.plan(state,f,s,CitizenRole.FARMER,0).activity()==CitizenActivity.WORK_FARM,"farmer must target farm");
        check(CitizenRoutinePlanner.plan(state,f,s,CitizenRole.MINER,0).activity()==CitizenActivity.WORK_MINE,"miner must target mine");
        check(CitizenRoutinePlanner.plan(state,f,s,CitizenRole.LUMBERJACK,0).activity()==CitizenActivity.WORK_FORESTRY,"lumberjack must target lumber camp");
        check(CitizenRoutinePlanner.plan(state,f,s,CitizenRole.FISHER,0).activity()==CitizenActivity.WORK_FISHERY,"fisher must target fishery");
        check(CitizenRoutinePlanner.plan(state,f,s,CitizenRole.TRADER,0).activity()==CitizenActivity.TRADE,"trader must target market/warehouse");
        check(CitizenRoutinePlanner.plan(state,f,s,CitizenRole.OFFICIAL,0).activity()==CitizenActivity.ADMINISTER,"official must target keep");
        check(CitizenRoutinePlanner.plan(state,f,s,CitizenRole.BUILDER,0).activity()==CitizenActivity.BUILD,"builder must target highest-priority unfinished construction");
        List<CitizenProjection> citizens=CitizenMaterializationPlanner.plan(List.of(f),List.of(new SimPosition(0,0)),300,24);
        check(citizens.stream().anyMatch(c->c.role()==CitizenRole.GUARD),"citizen projection must include guard");
        check(citizens.stream().anyMatch(c->c.role()==CitizenRole.MINER||c.role()==CitizenRole.LUMBERJACK||c.role()==CitizenRole.FISHER),"completed primary economy must unlock specialist citizen roles");
    }

    private static void testPropertyRights(){
        SimulationState state=new SimulationState(110L);
        Faction faction=faction(state,"Owners","Keeper",100,100,600,700);
        var settlement=faction.settlements().getFirst();
        var plan=dev.livingrealms.sim.construction.SettlementPlanner.plan(faction,settlement);
        var keep=plan.stream().filter(i->i.role()==dev.livingrealms.sim.construction.StructureRole.KEEP).findFirst().orElseThrow();
        check(PropertyRightsEngine.resolve(state.factions(),keep.center()).isEmpty(),"unfinished structure must not create property rights");
        settlement.markConstructionCompleted(keep.key());
        PropertyClaim claim=PropertyRightsEngine.resolve(state.factions(),keep.center()).orElseThrow();
        check(claim.factionId()==faction.id()&&claim.settlementId()==settlement.id()&&claim.structureKey().equals(keep.key()),"completed structure property must resolve to owner");
        check(PropertyRightsEngine.resolve(state.factions(),new SimPosition(10000,10000)).isEmpty(),"wilderness must not become faction property");
        var warehouse=plan.stream().filter(i->i.role()==StructureRole.WAREHOUSE).findFirst().orElseThrow();
        settlement.markConstructionCompleted(warehouse.key());
        var storagePlacement=StructureBlueprintFactory.create(warehouse).placements().stream().filter(p->p.slot()==PaletteSlot.STORAGE).findFirst().orElseThrow();
        SimPosition storagePos=new SimPosition(warehouse.center().x()+storagePlacement.dx()+.5,warehouse.center().z()+storagePlacement.dz()+.5);
        var storageClaim=PropertyRightsEngine.resolveStorage(state.factions(),storagePos).orElseThrow();
        check(storageClaim.factionId()==faction.id()&&storageClaim.role()==StructureRole.WAREHOUSE,"generated storage must resolve to faction warehouse");
        check(PropertyRightsEngine.resolveStorage(state.factions(),warehouse.center()).isEmpty(),"ordinary floor position must not count as faction storage");
        String thief="player:property-thief";
        CrimeResult unseen=PropertyCrimeEngine.reportTheft(state,thief,claim,40,0,"removed faction goods");
        check(!unseen.registered(),"unseen property theft must not create official bounty");
        CrimeResult seen=PropertyCrimeEngine.reportTheft(state,thief,claim,40,2,"removed faction goods");
        check(seen.registered()&&seen.bountyAdded()>0,"witnessed property theft must create bounty");
        check(PropertyCrimeEngine.crimeType(dev.livingrealms.sim.construction.StructureRole.KEEP)==CrimeType.BURGLARY,"keep theft must classify as burglary");
        check(PropertyCrimeEngine.crimeType(dev.livingrealms.sim.construction.StructureRole.BARRACKS)==CrimeType.SABOTAGE,"military property interference must classify as sabotage");
    }

    private static void testLawEnforcementCustodyAndBountyClaim() {
        SimulationState state=new SimulationState(111L);
        Faction realm=faction(state,"Lawland","Justice",0,0,1200,1300);

        String suspicious="player:suspicious";
        state.reportCrime(suspicious,realm.id(),CrimeType.THEFT,0,new SimPosition(2,2),true,1,"shop","seen");
        check(state.lawResponse(suspicious,realm.id()).action()==EnforcementAction.QUESTION,"small first offence should trigger questioning rather than instant attack");

        String thief="player:thief";
        for(int i=0;i<3;i++) state.reportCrime(thief,realm.id(),CrimeType.THEFT,5,new SimPosition(2,2),true,1,"shop","seen");
        LawResponse fine=state.lawResponse(thief,realm.id());
        check(fine.action()==EnforcementAction.DEMAND_FINE&&fine.requestedFine()>0,"repeat nonviolent theft should create fine demand");

        String robber="player:robber";
        state.reportCrime(robber,realm.id(),CrimeType.ROBBERY,50,new SimPosition(3,3),true,2,"merchant","violent robbery");
        state.advanceDays(1);
        LawResponse arrest=state.lawResponse(robber,realm.id());
        check(arrest.action()==EnforcementAction.ARREST,"violent wanted offender should receive arrest response");
        BountyContract robberContract=state.bounties().stream().filter(b->b.actorKey().equals(robber)).findFirst().orElseThrow();
        ArrestOutcome arrested=state.arrestCriminal(robber,realm.id(),"guard arrest");
        check(arrested.arrested()&&arrested.bountyCleared()>0&&state.activeCustody(robber,realm.id()).isPresent(),"arrest must clear bounty and create persistent custody");
        check(robberContract.status()==BountyContract.Status.CANCELLED,"lawful arrest must cancel open bounty contract");
        byte[] custodyBytes=SimulationStateCodec.encode(state);
        SimulationState custodyReload=SimulationStateCodec.decode(custodyBytes);
        check(custodyReload.activeCustody(robber,realm.id()).isPresent(),"custody must survive save/load");
        custodyReload.advanceDays(arrested.sentenceDays()+1);
        check(custodyReload.activeCustody(robber,realm.id()).isEmpty(),"expired sentence must release prisoner");

        String fugitive="player:fugitive";
        state.reportCrime(fugitive,realm.id(),CrimeType.MURDER,500,new SimPosition(4,4),true,4,"citizen","murder witnesses");
        state.advanceDays(1);
        check(state.lawResponse(fugitive,realm.id()).action()==EnforcementAction.LETHAL_FORCE,"high-heat most-wanted murderer should permit lethal force");
        BountyContract contract=state.bounties().stream().filter(b->b.actorKey().equals(fugitive)&&(b.status()==BountyContract.Status.OPEN||b.status()==BountyContract.Status.ASSIGNED)).findFirst().orElseThrow();
        double treasuryBefore=realm.treasury();
        BountyClaim claim=state.claimBounty(contract.id(),"hunter:one");
        check(claim.claimed()&&claim.paidReward()>0,"valid bounty hunter must be able to claim reward");
        check(realm.treasury()<treasuryBefore,"claimed bounty must be paid from issuing faction treasury");
        check(state.crimeLedger().profile(fugitive).in(realm.id()).bounty()==0,"bounty claim must clear wanted bounty");
        check(contract.status()==BountyContract.Status.CLAIMED,"contract must become claimed");
        check(!state.claimBounty(contract.id(),"hunter:two").claimed(),"bounty contract must not be claimable twice");
    }

    private static void testBountyBoardAndNpcPlanner() {
        SimulationState state=new SimulationState(112L);
        Faction realm=faction(state,"Hunters","Marshal",0,0,1200,1600);
        String target="player:target";
        for(int i=0;i<2;i++) state.reportCrime(target,realm.id(),CrimeType.ROBBERY,80,new SimPosition(5,5),true,3,"merchant","identified");
        state.advanceDays(1);
        BountyContract contract=state.bounties().stream().filter(b->b.actorKey().equals(target)&&b.status()==BountyContract.Status.OPEN).findFirst().orElseThrow();
        BountyAssignment self=state.acceptBounty(contract.id(),target);
        check(!self.success()&&self.reason().equals("cannot_hunt_self"),"target must not accept own bounty");
        String hunter="player:hunter";
        BountyAssignment accepted=state.acceptBounty(contract.id(),hunter);
        check(accepted.success()&&contract.status()==BountyContract.Status.ASSIGNED&&contract.hunterKey().equals(hunter),"player bounty assignment");
        BountyAssignment second=state.acceptBounty(contract.id(),"player:other");
        check(!second.success(),"assigned bounty must reject another hunter");
        BountyAssignment abandoned=state.abandonBounty(contract.id(),hunter);
        check(abandoned.success()&&contract.status()==BountyContract.Status.OPEN&&contract.hunterKey().isEmpty(),"abandon must reopen contract");

        var targets=List.of(new BountyHunterPlanner.OnlineTarget(target,new SimPosition(10,20),true));
        var plan=BountyHunterPlanner.reconcile(state.bounties(),targets,List.of(),4);
        check(plan.spawns().size()==1&&plan.spawns().getFirst().contractId()==contract.id(),"open online bounty should spawn one npc hunter");
        var existing=List.of(new BountyHunterPlanner.Existing(contract.id(),"npc-bounty:"+contract.id()),new BountyHunterPlanner.Existing(contract.id(),"npc-bounty:"+contract.id()+":duplicate"));
        var dedupe=BountyHunterPlanner.reconcile(state.bounties(),targets,existing,4);
        check(dedupe.spawns().isEmpty()&&dedupe.keepContractIds().contains(contract.id())&&dedupe.despawnContractIds().contains(contract.id()),"npc hunter projection dedupe");

        String npc="npc-bounty:"+contract.id();
        BountyAssignment npcAssigned=state.acceptBounty(contract.id(),npc);
        check(npcAssigned.success(),"npc hunter should be able to reserve open contract");
        BountyClaim captured=state.captureBountyAlive(contract.id(),npc,"npc bounty capture");
        check(captured.claimed()&&contract.status()==BountyContract.Status.CLAIMED,"npc live capture must claim contract");
        check(state.activeCustody(target,realm.id()).isPresent(),"live bounty capture must create custody");
        BountyClaim duplicate=state.claimBounty(contract.id(),npc);
        check(!duplicate.claimed(),"bounty must not pay twice");
    }

    private static void testGovernmentSuccession() {
        SimulationState state = new SimulationState(12L);
        Faction a = faction(state, "Aster", "Elara", 0, 0, 300, 340);
        String oldRuler = a.rulerName();
        a.government().ruler().adjustHealth(-10);
        state.advanceDays(1);
        check(!a.rulerName().equals(oldRuler), "dead ruler must be succeeded");
        check(a.government().ruler().health() > 0, "successor must be alive");
        check(state.history().all().stream().anyMatch(e -> e.type().equals("succession")), "succession must be in world history");
    }

    private static void testMarketAndTransport() {
        SimulationState scarceState = new SimulationState(13L);
        Faction scarce = faction(scarceState, "Scarce", "S", 0, 0, 1000, 1100);
        for(ResourceType r:ResourceType.values()){scarce.stockpile().set(r,0);for(Settlement s:scarce.settlements())s.stockpile().set(r,0);}
        scarce.stockpile().add(ResourceType.FOOD, 5);
        double scarceFood = MarketEngine.unitPrice(scarce, ResourceType.FOOD);

        SimulationState richState = new SimulationState(14L);
        Faction rich = faction(richState, "Rich", "R", 0, 0, 1000, 1100);
        for(Settlement s:rich.settlements())s.stockpile().set(ResourceType.FOOD,0);
        rich.stockpile().add(ResourceType.FOOD, 100000);
        double richFood = MarketEngine.unitPrice(rich, ResourceType.FOOD);
        check(scarceFood > richFood, "scarcity must increase market price");

        Faction builder = rich;
        builder.addSettlement(new Settlement(richState.nextId(), "Second", new SimPosition(600, 0), 2500, 2700));
        builder.advanceTechnology(2.0);
        builder.stockpile().add(ResourceType.IRON, 100000);
        builder.stockpile().add(ResourceType.STONE, 100000);
        richState.advanceDays(1);
        check(!richState.routes().isEmpty(), "eligible settlements must get a transport route");
        TransportRoute route = richState.routes().getFirst();
        check(route.operational() && route.speedBlocksPerDay() > 0 && route.capacityPerDay() > 0,
                "transport route must be operational with speed/capacity");
    }

    private static void testRouteProjectionPlanner(){
        TransportRoute route=new TransportRoute(901,1,11,12,TransportMode.ROAD,1000,.7,.8,300);
        SimPosition from=new SimPosition(0,0),to=new SimPosition(1000,0);
        var near=RouteProjectionPlanner.plan(route,from,to,List.of(new SimPosition(500,0)),80,1000);
        check(!near.isEmpty(),"route projection should materialize near observer");
        check(near.size()<=170,"observer-radius route projection unexpectedly huge");
        check(near.stream().allMatch(p->Math.hypot(p.x()-500,p.z())<=85),"route projection escaped observer window");
        var repeat=RouteProjectionPlanner.plan(route,from,to,List.of(new SimPosition(500,0)),80,1000);
        check(near.equals(repeat),"route projection must be deterministic");
        var capped=RouteProjectionPlanner.plan(route,from,to,List.of(new SimPosition(500,0)),500,12);
        check(capped.size()==12,"route projection must obey hard point budget");
        route.setOperational(false);
        check(RouteProjectionPlanner.plan(route,from,to,List.of(new SimPosition(500,0)),80,1000).isEmpty(),"non-operational route must not project");
    }

    private static void testIndustrySitePlanner(){
        SimulationState state=new SimulationState(913L);Faction f=faction(state,"Industry","Forge",0,0,2500,4000);f.advanceTechnology(1.0);
        Settlement s=f.settlements().getFirst();s.addHousing(4000);
        var plan=SettlementPlanner.plan(f,s);for(var i:plan)if(i.role()==StructureRole.WORKSHOP||i.role()==StructureRole.FACTORY)s.markConstructionCompleted(i.key());
        var sites=dev.livingrealms.sim.industry.IndustrySitePlanner.plan(f);check(sites.stream().anyMatch(x->x.kind()==dev.livingrealms.sim.industry.IndustryKind.SAWMILL),"completed workshop should host sawmill");check(sites.stream().anyMatch(x->x.kind()==dev.livingrealms.sim.industry.IndustryKind.METALWORKS),"completed factory should host metalworks");
        var repeat=dev.livingrealms.sim.industry.IndustrySitePlanner.plan(f);check(sites.equals(repeat),"industry site projection must be deterministic");
        SimulationState lockedState=new SimulationState(914L);Faction locked=faction(lockedState,"NoFactory","N",0,0,2500,4000);locked.advanceTechnology(1.0);locked.settlements().getFirst().addHousing(4000);check(dev.livingrealms.sim.industry.IndustrySitePlanner.plan(locked).isEmpty(),"unfinished host structure must not project Create machinery");
    }

    private static void testIndustrialSiteLifecycleAndPersistence(){
        SimulationState state=new SimulationState(915L);Faction owner=faction(state,"IndustryCore","Forge",0,0,4200,5200);owner.advanceTechnology(1.4);
        for(ResourceType r:ResourceType.values())owner.stockpile().add(r,50_000);
        state.advanceDays(1);
        check(!state.industrialSites().isEmpty(),"eligible settlements must create canonical industrial sites");
        IndustrialSite site=state.industrialSites().stream().filter(x->x.factionId()==owner.id()).findFirst().orElseThrow();
        check(site.lastCycles()>0,"supplied industrial site must execute production cycles");
        check(site.condition()>0&&site.condition()<=1,"industrial condition must be normalized");
        site.damage(.72,3);double damaged=site.condition();state.advanceDays(1);
        check(site.status()==IndustrialSiteStatus.OFFLINE&&site.downtimeDays()>0,"damaged site with downtime must be offline");
        for(int i=0;i<8;i++){owner.stockpile().add(ResourceType.TOOLS,100);owner.stockpile().add(ResourceType.IRON,100);owner.stockpile().add(ResourceType.MACHINERY,100);state.advanceDays(1);}
        check(site.condition()>damaged,"maintained damaged industry must recover condition");
        check(site.downtimeDays()==0,"industrial downtime must expire");

        Faction conqueror=faction(state,"NewOwner","N",2000,0,1200,1500);Settlement settlement=state.findSettlement(site.settlementId()).orElseThrow();
        Settlement moved=owner.removeSettlement(settlement.id());check(moved!=null,"test settlement transfer failed");conqueror.addSettlement(moved);conqueror.advanceTechnology(1.5);for(ResourceType r:ResourceType.values())conqueror.stockpile().add(r,10_000);
        state.advanceDays(1);check(site.factionId()==conqueror.id(),"industrial site must transfer with conquered settlement");

        byte[] encoded=SimulationStateCodec.encode(state);SimulationState restored=SimulationStateCodec.decode(encoded);
        IndustrialSite restoredSite=restored.findIndustrialSite(site.id()).orElseThrow();
        check(restoredSite.factionId()==site.factionId()&&restoredSite.settlementId()==site.settlementId()&&restoredSite.kind()==site.kind(),"industrial site identity must persist");
        check(close(restoredSite.condition(),site.condition())&&restoredSite.status()==site.status()&&restoredSite.downtimeDays()==site.downtimeDays(),"industrial site operational state must persist");
    }

    private static void testMilitaryObjectivesDriveOperations(){
        SimulationState state=new SimulationState(1701L);
        Faction attacker=faction(state,"Command Realm","Marshal",0,0,900,1000);
        Faction defender=faction(state,"Target Realm","Defender",500,0,900,1000);
        Settlement far=new Settlement(state.nextId(),"Western Target",new SimPosition(-1600,0),600,700);defender.addSettlement(far);
        attacker.relationWith(defender.id()).declareWar();defender.relationWith(attacker.id()).declareWar();
        Army army=new Army(state.nextId(),attacker.id(),new SimPosition(0,0),300);attacker.addArmy(army);
        MilitaryObjective forced=new MilitaryObjective(state.nextId(),army.id(),attacker.id(),MilitaryObjectiveType.CAPTURE_SETTLEMENT,defender.id(),far.id(),far.position(),state.clock().day(),999);state.addObjective(forced);
        new FactionEngine().simulateDay(state,new DeterministicRng(77));
        check(army.position().x()<0,"assigned capture objective must drive movement instead of nearest-enemy fallback");

        // A defensive objective must operate even when there is no active enemy.
        attacker.relationWith(defender.id()).makePeace();defender.relationWith(attacker.id()).makePeace();forced.markComplete();
        Settlement home=attacker.settlements().getFirst();Army reserve=new Army(state.nextId(),attacker.id(),new SimPosition(-700,400),160);attacker.addArmy(reserve);
        MilitaryObjective defend=new MilitaryObjective(state.nextId(),reserve.id(),attacker.id(),MilitaryObjectiveType.DEFEND,0,home.id(),home.position(),state.clock().day(),200);state.addObjective(defend);
        double before=reserve.position().distanceTo(home.position());new FactionEngine().simulateDay(state,new DeterministicRng(78));double after=reserve.position().distanceTo(home.position());
        check(after<before,"defend objective must move reserve toward friendly settlement during peace");

        // Command cleanup must invalidate a capture objective once the target is no longer hostile.
        attacker.relationWith(defender.id()).declareWar();defender.relationWith(attacker.id()).declareWar();
        MilitaryObjective stale=new MilitaryObjective(state.nextId(),reserve.id(),attacker.id(),MilitaryObjectiveType.CAPTURE_SETTLEMENT,defender.id(),defender.settlements().getFirst().id(),defender.settlements().getFirst().position(),state.clock().day(),500);state.addObjective(stale);
        attacker.relationWith(defender.id()).makePeace();defender.relationWith(attacker.id()).makePeace();
        new MilitaryCommandEngine().simulateDay(state);
        check(stale.complete(),"peace must invalidate stale offensive objectives");
    }

    private static void testWarTreatyAviationAndPersistence() {
        SimulationState state = new SimulationState(15L);
        Faction a = faction(state, "Aster", "Elara", 0, 0, 2500, 2700);
        Faction b = faction(state, "Veyra", "Oren", 1600, 0, 2500, 2700);
        a.advanceTechnology(1.6); b.advanceTechnology(1.6);
        for (Faction f : new Faction[]{a,b}) {
            f.stockpile().add(ResourceType.MACHINERY, 1000);
            f.stockpile().add(ResourceType.FUEL, 1000);
            f.stockpile().add(ResourceType.IRON, 1000);
            f.stockpile().add(ResourceType.FOOD, 10000);
        }
        a.relationWith(b.id()).declareWar(); b.relationWith(a.id()).declareWar();
        state.advanceDays(1);
        check(state.activeWar(a.id(), b.id()).isPresent(), "WAR relation must create explicit WarState");
        check(state.airWings().stream().anyMatch(w -> w.factionId() == a.id()), "high-tech city faction must form an air wing");
        check(state.airWings().stream().anyMatch(w -> w.factionId() == b.id()), "enemy high-tech city faction must form an air wing");

        Treaty treaty = new Treaty(state.nextId(), a.id(), b.id(), TreatyType.NON_AGGRESSION,
                state.clock().day(), state.clock().day() + 100);
        state.addTreaty(treaty);
        state.reportCrime("player:persistent", a.id(), CrimeType.ROBBERY, 70, new SimPosition(4, 4), true, 1, "merchant", "witness");
        state.advanceDays(1);

        byte[] encoded = SimulationStateCodec.encode(state);
        SimulationState restored = SimulationStateCodec.decode(encoded);
        check(restored.wars().size() == state.wars().size(), "wars must persist");
        check(restored.treaties().size() == state.treaties().size(), "treaties must persist");
        check(restored.airWings().size() == state.airWings().size(), "air wings must persist");
        check(restored.routes().size() == state.routes().size(), "routes must persist");
        check(restored.crimeLedger().findProfile("player:persistent").isPresent(), "wanted profile must persist");
        check(restored.crimeLedger().profile("player:persistent").in(a.id()).bounty() > 0, "bounty amount must persist");
        check(restored.bounties().stream().anyMatch(x -> x.actorKey().equals("player:persistent")), "bounty contract must persist");
        check(restored.peekNextId() == state.peekNextId(), "nextId must survive schema4 round-trip");
    }

    private static void testRebellion() {
        SimulationState state = new SimulationState(16L);
        Faction parent = faction(state, "Empire", "Imperator", 0, 0, 1200, 1300);
        Settlement frontier = new Settlement(state.nextId(), "Frontier", new SimPosition(800, 0), 700, 750);
        frontier.adjustUnrest(1.0);
        parent.addSettlement(frontier);
        parent.government().adjustStability(-1.0);
        parent.stockpile().add(ResourceType.FOOD, 10000);
        parent.stockpile().add(ResourceType.WOOD, 1000);

        RebellionEngine engine = new RebellionEngine();
        DeterministicRng rng = new DeterministicRng(0xC0FFEE);
        int attempts = 0;
        while (state.factions().size() == 1 && attempts++ < 5000) engine.simulateDay(state, rng, .20);
        check(state.factions().size() == 2, "chronically unstable peripheral settlement must eventually be able to secede");
        Faction splinter = state.factions().stream().filter(f -> f.id() != parent.id()).findFirst().orElseThrow();
        check(splinter.settlements().stream().anyMatch(s -> s.id() == frontier.id()), "rebel settlement must transfer to splinter faction");
        check(parent.relationWith(splinter.id()).status() == RelationStatus.WAR && splinter.relationWith(parent.id()).status() == RelationStatus.WAR,
                "secession must create bilateral war state at relation level");
        check(state.history().all().stream().anyMatch(e -> e.type().equals("rebellion")), "rebellion must be recorded in history");
    }

    private static void testPhysicalProjectionAccounting() {
        SimulationState state = new SimulationState(17L);
        Faction faction = faction(state, "Projectors", "Ada", 0, 0, 1600, 1700);
        Army army = new Army(state.nextId(), faction.id(), new SimPosition(30, 0), 240);
        army.addCavalry(20); army.addArtillery(8); army.addArmor(4); faction.addArmy(army);
        AirWing wing = new AirWing(state.nextId(), faction.id(), AircraftModel.LIGHT_FIGHTER, 4, new SimPosition(0, 0));
        state.addAirWing(wing);

        var citizens = CitizenMaterializationPlanner.plan(state.factions(), java.util.List.of(new SimPosition(0,0)), 320, 40);
        check(!citizens.isEmpty() && citizens.size() <= 40, "citizen projection must be bounded and non-empty");
        check(citizens.stream().anyMatch(c -> c.role() == CitizenRole.GUARD), "citizen projection must include law-enforcement representation");
        var military = MilitaryMaterializationPlanner.plan(state.factions(), java.util.List.of(new SimPosition(0,0)), 320, 20);
        check(!military.isEmpty() && military.size() <= 20, "military projection must obey global budget");
        int beforePeople=faction.settlements().getFirst().population();
        check(state.recordPhysicalCitizenDeath(faction.settlements().getFirst().id(), "test"), "physical citizen death must reconcile");
        check(faction.settlements().getFirst().population()==beforePeople-1, "physical citizen death must decrement aggregate population exactly once");
        int beforeArmy=army.totalPersonnel();
        check(state.recordPhysicalArmyLoss(army.id(), 5, "test"), "physical army loss must reconcile");
        check(army.totalPersonnel()<beforeArmy, "physical army loss must reduce aggregate army strength");
        var aircraft = AircraftMaterializationPlanner.plan(state.airWings(), java.util.List.of(new SimPosition(0,0)), 900, 2);
        check(aircraft.size()==2 && aircraft.stream().allMatch(x->x.wingId()==wing.id()), "aircraft projection must obey physical budget");
        check(state.recordPhysicalAircraftLoss(wing.id(),1,"test"), "physical aircraft loss must reconcile");
        check(wing.aircraft()==3, "physical aircraft loss must remove one canonical aircraft");
    }

    private static void testSpeciesMobilityAndMorphology() {
        var species=SpeciesCatalog.starter();
        check(SpeciesMobilityResolver.resolve(species.get("salmon"))==SpeciesMobility.AQUATIC,"salmon must use aquatic locomotion");
        check(SpeciesMobilityResolver.resolve(species.get("golden_eagle"))==SpeciesMobility.FLYING,"eagle must use flying locomotion");
        check(SpeciesMobilityResolver.resolve(species.get("seal"))==SpeciesMobility.AMPHIBIOUS,"seal must support land/water locomotion");
        check(SpeciesMorphologyResolver.resolve(species.get("great_white_shark"))==SpeciesMorphology.FISH,"shark must use fish morphology");
        check(SpeciesMorphologyResolver.resolve(species.get("orca"))==SpeciesMorphology.CETACEAN,"orca must use cetacean morphology");
        check(SpeciesMorphologyResolver.resolve(species.get("saltwater_crocodile"))==SpeciesMorphology.CROCODILIAN,"crocodile must use reptile morphology");
    }

    private static void testPlayerReputationAndMembership() {
        SimulationState state=new SimulationState(18L);
        Faction realm=faction(state,"Membership Realm","Regent",0,0,900,1000);
        String actor="player:member";
        PlayerStanding standing=state.playerStanding(actor);
        check(!state.joinFaction(actor,realm.id()).success(),"neutral outsider must not join without reputation");
        standing.adjustReputation(realm.id(),40);
        FactionJoinResult joined=state.joinFaction(actor,realm.id());
        check(joined.success()&&standing.isMemberOf(realm.id())&&standing.rank()==FactionRank.CITIZEN,"qualified actor must join as citizen");
        state.grantFactionService(actor,realm.id(),120);
        check(standing.rank()==FactionRank.SOLDIER,"service plus reputation must promote citizen to soldier");

        String persistent="player:persistent-member";
        PlayerStanding ps=state.playerStanding(persistent);ps.adjustReputation(realm.id(),70);
        check(state.joinFaction(persistent,realm.id()).success(),"persistent actor join");
        state.grantFactionService(persistent,realm.id(),350);
        check(ps.rank()==FactionRank.OFFICER,"service ladder must reach officer");

        String wanted="player:wanted-applicant";
        state.playerStanding(wanted).adjustReputation(realm.id(),50);
        state.reportCrime(wanted,realm.id(),CrimeType.MURDER,0,new SimPosition(1,1),true,3,"citizen","witnesses");
        check(!state.joinFaction(wanted,realm.id()).success(),"wanted murderer must not be admitted");

        state.reportCrime(actor,realm.id(),CrimeType.MURDER,0,new SimPosition(2,2),true,4,"citizen","caught");
        check(!standing.isMember()&&standing.expulsions()==1,"serious crime against own faction must expel member");
        check(standing.reputationWith(realm.id())<0,"crime must damage faction reputation");

        byte[] encoded=SimulationStateCodec.encode(state);
        SimulationState restored=SimulationStateCodec.decode(encoded);
        PlayerStanding restoredPersistent=restored.findPlayerStanding(persistent).orElseThrow();
        check(restoredPersistent.isMemberOf(realm.id())&&restoredPersistent.rank()==FactionRank.OFFICER,"membership/rank must survive schema6 round-trip");
        check(close(restoredPersistent.servicePoints(),ps.servicePoints()),"service points must persist");
        PlayerStanding restoredExpelled=restored.findPlayerStanding(actor).orElseThrow();
        check(!restoredExpelled.isMember()&&restoredExpelled.expulsions()==1,"expulsion history must persist");
    }

    private static void testNavalSystems() {
        SimulationState state=new SimulationState(19L);
        Faction a=faction(state,"Maritime A","Admiral A",0,0,2500,2700);
        Faction b=faction(state,"Maritime B","Admiral B",50,0,2500,2700);
        for(Faction f:List.of(a,b)){
            f.advanceTechnology(1.6);f.stockpile().add(ResourceType.MACHINERY,5000);f.stockpile().add(ResourceType.IRON,5000);f.stockpile().add(ResourceType.FUEL,5000);f.stockpile().add(ResourceType.FOOD,5000);f.stockpile().add(ResourceType.TOOLS,500);
        }
        a.relationWith(b.id()).declareWar();b.relationWith(a.id()).declareWar();
        PortState pa=new PortState(state.nextId(),a.id(),a.settlements().getFirst().id(),new SimPosition(0,0),3);
        PortState pb=new PortState(state.nextId(),b.id(),b.settlements().getFirst().id(),new SimPosition(50,0),3);
        state.addPort(pa);state.addPort(pb);
        state.advanceDays(1);
        check(state.history().all().stream().filter(e->e.type().equals("ship_built")).count()>=2,"operational high-tech ports must produce ships");
        check(state.history().all().stream().anyMatch(e->e.type().equals("naval_battle")),"hostile nearby fleets must be able to fight");

        SimulationState blockadeState=new SimulationState(191L);
        Faction attacker=faction(blockadeState,"Blockader","A",0,0,1200,1300);
        Faction defender=faction(blockadeState,"Harbor Realm","B",100,0,1200,1300);
        attacker.relationWith(defender.id()).declareWar();defender.relationWith(attacker.id()).declareWar();
        PortState attackPort=new PortState(blockadeState.nextId(),attacker.id(),attacker.settlements().getFirst().id(),new SimPosition(0,0),2);
        PortState targetPort=new PortState(blockadeState.nextId(),defender.id(),defender.settlements().getFirst().id(),new SimPosition(100,0),2);
        blockadeState.addPort(attackPort);blockadeState.addPort(targetPort);
        Fleet raider=new Fleet(blockadeState.nextId(),attacker.id(),attackPort.id(),targetPort.position(),ShipClass.FRIGATE,4);
        raider.assign(NavalMission.BLOCKADE,targetPort.position());blockadeState.addFleet(raider);
        double conditionBefore=targetPort.condition();double prosperityBefore=defender.settlements().getFirst().prosperity();
        blockadeState.advanceDays(1);
        check(targetPort.condition()<conditionBefore,"blockade must damage hostile port condition");
        check(defender.settlements().getFirst().prosperity()<prosperityBefore,"blockade must damage port-city prosperity");

        Fleet transport=new Fleet(blockadeState.nextId(),attacker.id(),attackPort.id(),attackPort.position(),ShipClass.LANDING_SHIP,2);
        transport.embark(100);check(transport.embarkedPersonnel()==100&&transport.troopCapacity()>=100,"landing ships must carry personnel");
        check(transport.disembarkAll()==100&&transport.embarkedPersonnel()==0,"amphibious transport must disembark canonical personnel");
        blockadeState.addFleet(transport);
        List<FleetProjection> projections=NavalMaterializationPlanner.plan(blockadeState.fleets(),List.of(new SimPosition(0,0)),320,2);
        check(!projections.isEmpty()&&projections.size()<=2,"naval physical projection must obey entity budget");
        FleetProjection projected=projections.stream().filter(p->p.fleetId()==transport.id()).findFirst().orElse(null);
        if(projected!=null){int before=transport.count(projected.shipClass());check(blockadeState.recordPhysicalShipLoss(transport.id(),projected.shipClass(),1,"test"),"physical ship loss must reconcile");check(transport.count(projected.shipClass())==before-1,"canonical fleet must lose represented ship");}

        byte[] encoded=SimulationStateCodec.encode(blockadeState);
        SimulationState restored=SimulationStateCodec.decode(encoded);
        check(restored.ports().size()==blockadeState.ports().size(),"ports must persist");
        check(restored.fleets().size()==blockadeState.fleets().size(),"fleets must persist");
        Fleet restoredRaider=restored.findFleet(raider.id()).orElseThrow();
        check(restoredRaider.mission()==raider.mission()&&restoredRaider.totalShips()==raider.totalShips(),"fleet mission/composition must persist");
        check(close(restored.findPort(targetPort.id()).orElseThrow().condition(),targetPort.condition()),"port damage must persist");
    }


    private static void testSettlementManagementAndTaxActions(){
        SimulationState state=new SimulationState(190L);Faction a=faction(state,"Policy Realm","Regent",0,0,1600,1800);Faction b=faction(state,"Other Realm","Other",1200,0,900,1000);
        String actor="player:policy";state.playerStanding(actor).adjustReputation(a.id(),30);check(state.joinFaction(actor,a.id()).success(),"policy actor join");Settlement own=a.settlements().getFirst();Settlement foreign=b.settlements().getFirst();
        double tax=a.government().taxRate();var up=DashboardActionService.apply(state,actor,new SimPosition(99999,99999),new DashboardActionCommand(DashboardActionCommand.Action.TAX_RAISE,a.id()));check(up.success()&&close(a.government().taxRate(),tax+.01),"tax raise must mutate own government");
        var wrongTax=DashboardActionService.apply(state,actor,new SimPosition(0,0),new DashboardActionCommand(DashboardActionCommand.Action.TAX_RAISE,b.id()));check(!wrongTax.success(),"must reject remote tax mutation");
        var policy=DashboardActionService.apply(state,actor,new SimPosition(99999,99999),new DashboardActionCommand(DashboardActionCommand.Action.SETTLEMENT_HOUSING,own.id()));check(policy.success()&&own.developmentPriority()==DevelopmentPriority.HOUSING,"owned settlement policy must update");
        var foreignPolicy=DashboardActionService.apply(state,actor,new SimPosition(0,0),new DashboardActionCommand(DashboardActionCommand.Action.SETTLEMENT_DEFENSE,foreign.id()));check(!foreignPolicy.success()&&foreign.developmentPriority()==DevelopmentPriority.BALANCED,"foreign settlement policy must be rejected");
        var balanced=SettlementPlanner.plan(a,own);int house=balanced.stream().filter(i->i.role()==StructureRole.HOUSE).mapToInt(ConstructionIntent::priority).max().orElse(0);int road=balanced.stream().filter(i->i.role()==StructureRole.ROAD).mapToInt(ConstructionIntent::priority).max().orElse(0);check(house>road,"housing policy must materially reprioritize housing construction");
        byte[] save=SimulationStateCodec.encode(state);SimulationState restored=SimulationStateCodec.decode(save);Faction ra=restored.findFaction(a.id()).orElseThrow();Settlement rs=ra.settlements().stream().filter(s->s.id()==own.id()).findFirst().orElseThrow();check(rs.developmentPriority()==DevelopmentPriority.HOUSING,"schema10 development policy must persist");check(close(ra.government().taxRate(),a.government().taxRate()),"tax policy must persist");
    }

    private static void testDashboardSnapshotAndCodec() {
        SimulationState state=new SimulationState(191L);
        Faction a=faction(state,"Dashboard Realm","Regent",0,0,1600,1800);
        Faction b=faction(state,"Dashboard Rival","Rival",1200,0,900,1100);
        Faction c=faction(state,"Dashboard Ally","Ally",2400,0,700,900);
        a.stockpile().add(ResourceType.FOOD,4200);a.stockpile().add(ResourceType.IRON,330);
        a.advanceTechnology(.8);
        Army army=new Army(state.nextId(),a.id(),new SimPosition(20,0),240);a.addArmy(army);
        a.relationWith(b.id()).declareWar();b.relationWith(a.id()).declareWar();
        Settlement dashboardTarget=b.settlements().getFirst();
        MilitaryObjective dashboardObjective=new MilitaryObjective(state.nextId(),army.id(),a.id(),MilitaryObjectiveType.CAPTURE_SETTLEMENT,b.id(),dashboardTarget.id(),dashboardTarget.position(),state.clock().day(),250);state.addObjective(dashboardObjective);
        SiegeState dashboardSiege=new SiegeState(state.nextId(),a.id(),b.id(),dashboardTarget.id(),state.clock().day());dashboardSiege.advance(.35,.6);state.addSiege(dashboardSiege);
        a.relationWith(c.id()).adjust(78);a.relationWith(c.id()).setTradeAgreement(true);c.relationWith(a.id()).adjust(78);c.relationWith(a.id()).setTradeAgreement(true);
        state.addTreaty(new Treaty(state.nextId(),a.id(),c.id(),TreatyType.DEFENSIVE_ALLIANCE,state.clock().day(),state.clock().day()+360));
        state.advanceDays(1);
        String actor="player:dashboard";state.playerStanding(actor).adjustReputation(a.id(),25);state.joinFaction(actor,a.id());
        state.reportCrime(actor,a.id(),CrimeType.THEFT,20,new SimPosition(4,0),true,2,"warehouse","witnessed");
        state.reportCrime("player:dashboard_outlaw",a.id(),CrimeType.MURDER,300,new SimPosition(7,0),true,3,"citizen","witnessed murder");
        state.advanceDays(1);
        EcosystemRegion dashboardRegion=new EcosystemRegion(state.nextId(),state.biomes().get("temperate_forest"),36,new SimPosition(32,24));
        PopulationGroup dashboardWolves=new PopulationGroup(state.nextId(),"gray_wolf","temperate_forest",new SimPosition(34,22),18);
        dashboardWolves.setHealth(.82);dashboardWolves.setHunger(.31);dashboardWolves.setThirst(.14);
        dashboardRegion.add(dashboardWolves);state.addRegion(dashboardRegion);
        RealmDashboardSnapshot snapshot=RealmDashboardBuilder.build(state,actor,new SimPosition(5,0));
        check(snapshot.jurisdiction().primaryFactionId()==a.id(),"dashboard must resolve current jurisdiction");
        check(snapshot.realm().factionId()==a.id()&&snapshot.realm().population()==a.population(),"dashboard local realm summary mismatch");
        check(snapshot.player().memberFactionId()==a.id()&&snapshot.player().bounty()>0,"dashboard player standing/wanted view mismatch");
        check(snapshot.settings().profile().equals("BALANCED")&&snapshot.settings().wildlifeBudget()==state.config().maxPhysicalWildlife(),"dashboard settings must reflect canonical config");
        DashboardActionCommand configAction=new DashboardActionCommand(DashboardActionCommand.Action.CONFIG_IMMERSIVE,1);
        check(DashboardActionCommand.parse(configAction.encode()).equals(configAction),"config dashboard action round-trip mismatch");
        var configured=DashboardActionService.apply(state,actor,new SimPosition(100000,100000),configAction);
        check(configured.success()&&configured.dirty()&&state.config().equals(SimulationPreset.IMMERSIVE.config()),"singleplayer config action must be canonical and work outside jurisdiction");
        RealmDashboardSnapshot afterConfig=RealmDashboardBuilder.build(state,actor,new SimPosition(5,0));
        check(afterConfig.settings().profile().equals("IMMERSIVE")&&afterConfig.settings().militaryBudget()==128,"fresh dashboard must expose applied preset");
        String recruit="player:dashboard_recruit";state.playerStanding(recruit).adjustReputation(a.id(),25);
        var joinedAction=DashboardActionService.apply(state,recruit,new SimPosition(5,0),new DashboardActionCommand(DashboardActionCommand.Action.FACTION_JOIN_LOCAL,a.id()));
        check(joinedAction.success()&&state.findPlayerStanding(recruit).orElseThrow().isMemberOf(a.id()),"dashboard local-faction join must use canonical membership rules");
        var wrongJoin=DashboardActionService.apply(state,"player:wrong_join",new SimPosition(5,0),new DashboardActionCommand(DashboardActionCommand.Action.FACTION_JOIN_LOCAL,b.id()));
        check(!wrongJoin.success(),"dashboard join must reject spoofed non-local faction id");
        var leftAction=DashboardActionService.apply(state,recruit,new SimPosition(100000,100000),new DashboardActionCommand(DashboardActionCommand.Action.FACTION_LEAVE,1));
        check(leftAction.success()&&!state.findPlayerStanding(recruit).orElseThrow().isMember(),"dashboard leave must work outside jurisdiction");
        DashboardActionCommand action=new DashboardActionCommand(DashboardActionCommand.Action.BOUNTY_ACCEPT,123);
        check(DashboardActionCommand.parse(action.encode()).equals(action),"dashboard action command round-trip mismatch");
        boolean badAction=false;try{DashboardActionCommand.parse("BOUNTY_ACCEPT:0");}catch(IllegalArgumentException expected){badAction=true;}check(badAction,"dashboard action parser must reject invalid target ids");
        check(!snapshot.settlements().isEmpty()&&snapshot.factions().size()<=RealmDashboardBuilder.MAX_FACTIONS,"dashboard bounded lists invalid");
        var cityView=snapshot.settlements().getFirst();
        check(cityView.developmentPriority().equals("BALANCED"),"dashboard must expose settlement development priority");
        check(snapshot.realm().marketPrices().containsKey("IRON")&&snapshot.realm().marketPrices().get("IRON")>0,"dashboard must expose server-side player market prices");
        check(snapshot.realm().marketBuyCosts().get("IRON")>0&&snapshot.realm().marketSellPayouts().get("IRON")>0,"dashboard must expose authoritative buy/sell package quotes");
        var marketGeneric=DashboardActionService.apply(state,actor,new SimPosition(5,0),new DashboardActionCommand(DashboardActionCommand.Action.MARKET_BUY,ResourceType.IRON.ordinal()+1L));
        check(!marketGeneric.success()&&marketGeneric.reason().equals("runtime_inventory_required"),"generic dashboard service must not mutate player market inventory transactions");
        var assessed=SocietyDiagnostics.assess(a,a.settlements().stream().filter(x->x.id()==cityView.id()).findFirst().orElseThrow());
        check(close(cityView.societySatisfaction(),assessed.satisfaction()),"dashboard society satisfaction must use canonical diagnostics");
        check(cityView.primaryPressure().equals(assessed.primaryPressure().name()),"dashboard primary pressure must match canonical diagnostics");
        check(close(cityView.housingSatisfaction(),assessed.needs().housing())&&close(cityView.goodsAccess(),assessed.needs().goods()),"dashboard must expose housing/goods needs");
        check(!snapshot.bounties().isEmpty()&&snapshot.bounties().size()<=RealmDashboardBuilder.MAX_BOUNTIES,"dashboard local bounty board must be bounded and visible");
        check(snapshot.ecology().catalogSpecies()==state.species().size(),"dashboard ecology catalog count mismatch");
        check(snapshot.ecology().regions().size()<=RealmDashboardBuilder.MAX_ECOLOGY_REGIONS,"dashboard ecology region bound exceeded");
        check(!snapshot.ecology().regions().isEmpty()&&snapshot.ecology().regions().getFirst().dominantSpecies().stream().anyMatch(v->v.speciesId().equals("gray_wolf")&&close(v.health(),.82)),"dashboard ecology must expose bounded canonical population telemetry");
        check(snapshot.forces().airWings().size()<=RealmDashboardBuilder.MAX_FORCE_AIR_WINGS&&snapshot.forces().fleets().size()<=RealmDashboardBuilder.MAX_FORCE_FLEETS&&snapshot.forces().ports().size()<=RealmDashboardBuilder.MAX_FORCE_PORTS,"dashboard force bounds exceeded");
        check(snapshot.politics().relations().stream().anyMatch(v->v.factionId()==b.id()&&v.status().equals("WAR")),"dashboard politics must expose active-war relation");
        check(snapshot.politics().relations().stream().anyMatch(v->v.factionId()==c.id()&&v.tradeAgreement()&&v.opinion()>70),"dashboard politics must expose friendly trade relation");
        check(snapshot.politics().treaties().stream().anyMatch(v->v.otherFactionId()==c.id()&&v.type().equals("DEFENSIVE_ALLIANCE")),"dashboard politics must expose active treaty");
        check(snapshot.warfare().objectives().stream().anyMatch(v->v.id()==dashboardObjective.id()&&v.target().equals(dashboardTarget.name())),"dashboard warfare must expose active military objective");
        check(snapshot.warfare().sieges().stream().anyMatch(v->v.id()==dashboardSiege.id()&&close(v.progress(),.35)&&close(v.blockade(),.6)),"dashboard warfare must expose active siege progress");
        long boardContract=snapshot.bounties().getFirst().id();
        var accepted=DashboardActionService.apply(state,actor,new SimPosition(5,0),new DashboardActionCommand(DashboardActionCommand.Action.BOUNTY_ACCEPT,boardContract));
        check(accepted.success()&&accepted.dirty(),"dashboard bounty accept must mutate only through canonical service");
        RealmDashboardSnapshot assigned=RealmDashboardBuilder.build(state,actor,new SimPosition(5,0));
        check(assigned.bounties().stream().anyMatch(v->v.id()==boardContract&&v.assignedToYou()),"accepted bounty must return assigned in fresh server snapshot");
        var rejectedRemote=DashboardActionService.apply(state,actor,new SimPosition(100000,100000),new DashboardActionCommand(DashboardActionCommand.Action.BOUNTY_ABANDON,boardContract));
        check(!rejectedRemote.success()&&!rejectedRemote.dirty(),"dashboard action must reject wilderness mutation");
        var abandoned=DashboardActionService.apply(state,actor,new SimPosition(5,0),new DashboardActionCommand(DashboardActionCommand.Action.BOUNTY_ABANDON,boardContract));
        check(abandoned.success(),"dashboard bounty abandon must succeed at issuing board");
        check(!snapshot.map().settlements().isEmpty(),"strategic map must include settlement markers");
        check(snapshot.map().armies().stream().anyMatch(m->m.id()==army.id()),"strategic map must include nearby armies");
        check(snapshot.map().minX()<snapshot.map().playerX()&&snapshot.map().maxX()>snapshot.map().playerX(),"strategic map bounds must contain player");
        String json=RealmDashboardCodec.encode(snapshot);
        check(json.length()<RealmDashboardCodec.MAX_JSON_CHARS,"dashboard payload exceeded hard size bound");
        RealmDashboardSnapshot restored=RealmDashboardCodec.decode(json);
        check(restored.equals(snapshot),"dashboard JSON round-trip must be exact");
        boolean rejected=false;try{RealmDashboardCodec.decode("x".repeat(RealmDashboardCodec.MAX_JSON_CHARS+1));}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"oversized dashboard packet must be rejected");

        for(int i=0;i<RealmDashboardBuilder.MAX_FACTIONS+12;i++){
            Faction remote=faction(state,"Remote "+i,"Ruler "+i,5000+i*300.0,5000,100+i,140+i);
            if(i<RealmDashboardBuilder.MAX_POLITICS_TREATIES+8)state.addTreaty(new Treaty(state.nextId(),a.id(),remote.id(),TreatyType.NON_AGGRESSION,state.clock().day(),state.clock().day()+90+i));
        }
        for(int i=0;i<RealmDashboardBuilder.MAX_MAP_SETTLEMENTS+40;i++) a.addSettlement(new Settlement(state.nextId(),"District "+i,new SimPosition(50+i*8,80),50+i,80+i));
        for(int i=0;i<RealmDashboardBuilder.MAX_HISTORY+25;i++) state.history().add(new WorldEvent(state.clock().day(),"dashboard_test","Bounded history event "+i));
        for(int i=0;i<RealmDashboardBuilder.MAX_MAP_ARMIES+20;i++) a.addArmy(new Army(state.nextId(),a.id(),new SimPosition(100+i*3,120+i*2),40+i));
        List<Settlement> mapSettlements=a.settlements();
        for(int i=1;i<mapSettlements.size()&&i<=RealmDashboardBuilder.MAX_MAP_ROUTES+10;i++){
            Settlement from=mapSettlements.get(i-1),to=mapSettlements.get(i);
            state.addRoute(new TransportRoute(state.nextId(),a.id(),from.id(),to.id(),TransportMode.ROAD,Math.max(1,from.position().distanceTo(to.position())),.6,.7,300));
        }
        RealmDashboardSnapshot bounded=RealmDashboardBuilder.build(state,actor,new SimPosition(5,0));
        check(bounded.factions().size()==RealmDashboardBuilder.MAX_FACTIONS,"dashboard faction bound must be exact under overflow");
        check(bounded.settlements().size()==RealmDashboardBuilder.MAX_SETTLEMENTS,"dashboard settlement bound must be exact under overflow");
        check(bounded.history().size()==RealmDashboardBuilder.MAX_HISTORY,"dashboard history bound must be exact under overflow");
        check(bounded.map().settlements().size()==RealmDashboardBuilder.MAX_MAP_SETTLEMENTS,"map settlement bound must be exact under overflow");
        check(bounded.map().claims().size()==RealmDashboardBuilder.MAX_MAP_SETTLEMENTS,"map claim bound must be exact under overflow");
        check(bounded.map().armies().size()==RealmDashboardBuilder.MAX_MAP_ARMIES,"map army bound must be exact under overflow");
        check(bounded.map().routes().size()<=RealmDashboardBuilder.MAX_MAP_ROUTES,"map route bound exceeded");
        check(bounded.operations().routes().size()<=RealmDashboardBuilder.MAX_OPERATION_ROUTES,"operations route bound exceeded");
        check(bounded.operations().industry().size()<=RealmDashboardBuilder.MAX_OPERATION_INDUSTRY,"operations industry bound exceeded");
        check(bounded.operations().shipments().size()<=RealmDashboardBuilder.MAX_OPERATION_SHIPMENTS,"operations shipment bound exceeded");
        check(bounded.ecology().regions().size()<=RealmDashboardBuilder.MAX_ECOLOGY_REGIONS,"ecology region bound exceeded");
        check(bounded.ecology().regions().stream().allMatch(r->r.dominantSpecies().size()<=RealmDashboardBuilder.MAX_ECOLOGY_SPECIES_PER_REGION),"ecology species-per-region bound exceeded");
        check(bounded.forces().airWings().size()<=RealmDashboardBuilder.MAX_FORCE_AIR_WINGS&&bounded.forces().fleets().size()<=RealmDashboardBuilder.MAX_FORCE_FLEETS&&bounded.forces().ports().size()<=RealmDashboardBuilder.MAX_FORCE_PORTS,"force snapshot bound exceeded");
        check(bounded.politics().relations().size()==RealmDashboardBuilder.MAX_POLITICS_RELATIONS,"politics relation bound must be exact under overflow");
        check(bounded.politics().treaties().size()==RealmDashboardBuilder.MAX_POLITICS_TREATIES,"politics treaty bound must be exact under overflow");
        String boundedJson=RealmDashboardCodec.encode(bounded);
        check(boundedJson.length()<RealmDashboardCodec.MAX_JSON_CHARS,"bounded dashboard must stay below transport hard limit");
        boolean immutable=false;try{bounded.factions().clear();}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"dashboard lists must be immutable");
        boolean badProtocol=false;try{RealmDashboardCodec.decode(boundedJson.replaceFirst("\"v\":"+RealmDashboardSnapshot.PROTOCOL_VERSION,"\"v\":99"));}catch(IllegalArgumentException expected){badProtocol=true;}
        check(badProtocol,"dashboard codec must reject protocol mismatch");
        boolean malformed=false;try{RealmDashboardCodec.decode("{\"v\":"+RealmDashboardSnapshot.PROTOCOL_VERSION+"}");}catch(IllegalArgumentException expected){malformed=true;}
        check(malformed,"dashboard codec must reject incomplete payloads");
    }

    private static Faction faction(SimulationState state, String name, String ruler, double x, double z, int pop, int housing) {
        Faction f = new Faction(state.nextId(), name, ruler);
        f.addSettlement(new Settlement(state.nextId(), name + " City", new SimPosition(x, z), pop, housing));
        state.addFaction(f);
        return f;
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) <= 1.0e-9; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
