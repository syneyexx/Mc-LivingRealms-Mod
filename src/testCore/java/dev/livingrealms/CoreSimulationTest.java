package dev.livingrealms;

import dev.livingrealms.sim.world.*;
import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.animal.*;
import dev.livingrealms.sim.materialization.*;
import dev.livingrealms.sim.data.*;
import dev.livingrealms.sim.construction.*;
import java.nio.file.*;
import java.util.*;

public final class CoreSimulationTest {
    public static void main(String[] args){
        SimulationState a=new SimulationState(123456789L);DemoSeeder.seed(a);
        String start=a.summary();
        a.advanceDays(365);
        String end=a.summary();
        if(a.clock().day()!=365) throw new AssertionError("clock");
        if(a.factions().size()<8) throw new AssertionError("factions");
        if(a.regions().size()!=2) throw new AssertionError("regions");
        double animals=a.regions().stream().flatMap(r->r.populations().stream()).mapToDouble(PopulationGroup::population).sum();
        if(!Double.isFinite(animals)||animals<0) throw new AssertionError("animals");
        long survivingGroups=a.regions().stream().mapToLong(r->r.populations().size()).sum();
        if(survivingGroups<5 || animals<100) throw new AssertionError("ecosystem collapsed: groups="+survivingGroups+", animals="+animals);
        for(var r:a.regions()) for(var p:r.populations()){
            if(!Double.isFinite(p.population())||p.population()<0) throw new AssertionError("invalid population");
            if(p.hunger()<0||p.hunger()>1) throw new AssertionError("invalid hunger");
        }


        Path speciesDir=Path.of("src/main/resources/data/livingrealms/livingrealms/species");
        try {
            long jsonCount=Files.list(speciesDir).filter(x->x.toString().endsWith(".json")).count();
            if(jsonCount<a.species().size()) throw new AssertionError("bundled species pack smaller than fallback catalog: "+jsonCount);
            for(var sp:a.species().values()){
                Path resource=speciesDir.resolve(sp.id()+".json");
                if(!Files.exists(resource))throw new AssertionError("fallback species missing from bundled datapack: "+sp.id());
                var decoded=SpeciesJsonCodec.decode(Files.readString(resource));
                if(!decoded.id().equals(sp.id()))throw new AssertionError("species id mismatch: "+sp.id());
            }
            boolean rejected=false;
            try{SpeciesJsonCodec.decode("{\"id\":\"broken\"}");}catch(IllegalArgumentException expected){rejected=true;}
            if(!rejected) throw new AssertionError("malformed species data must be rejected");
            String explicit=SpeciesJsonCodec.encode(a.species().get("golden_eagle"));
            SpeciesDefinition explicitDecoded=SpeciesJsonCodec.decode(explicit);
            if(explicitDecoded.locomotion()!=LocomotionMode.FLYING||explicitDecoded.morphology()!=MorphologyFamily.RAPTOR_BIRD||explicitDecoded.flightSpeedFactor()<=0) throw new AssertionError("explicit physical species traits roundtrip");
            if(SpeciesMobilityResolver.resolve(explicitDecoded)!=SpeciesMobility.FLYING||SpeciesMorphologyResolver.resolve(explicitDecoded)!=SpeciesMorphology.BIRD) throw new AssertionError("physical trait runtime bridge");
        } catch(java.io.IOException e){throw new RuntimeException(e);}


        a.replaceSpeciesCatalog(a.species());
        var missingLive=new LinkedHashMap<>(a.species());
        missingLive.remove("rabbit");
        boolean liveRemovalRejected=false;
        try{a.replaceSpeciesCatalog(missingLive);}catch(IllegalArgumentException expected){liveRemovalRejected=true;}
        if(!liveRemovalRejected) throw new AssertionError("catalog reload must not orphan live populations");

        var validation=SpeciesCatalogValidator.validate(a.species());
        validation.throwIfInvalid();

        var wolf=a.species().get("gray_wolf");
        var hunt=AnimalBrain.decide(wolf,new AnimalStimulus(.9,.2,.1,1,.1,true,false,false,true,true,false,false,true));
        if(hunt.intent()!=AnimalIntent.HUNT) throw new AssertionError("hungry wolf should hunt");
        var flee=AnimalBrain.decide(a.species().get("rabbit"),new AnimalStimulus(.2,.2,.9,1,.1,false,true,false,true,true,false,false,true));
        if(flee.intent()!=AnimalIntent.FLEE) throw new AssertionError("rabbit should flee predator");

        var planner=new MaterializationPlanner(MaterializationConfig.defaults());
        var allGroups=a.regions().stream().flatMap(r->r.populations().stream()).toList();
        var plans=planner.plan(allGroups,a.species(),List.of(new SimPosition(300,200)));
        if(plans.stream().noneMatch(x->x.lod()==SimulationLod.PHYSICAL && x.desiredPhysicalCount()>0)) throw new AssertionError("nearby wildlife should materialize");
        if(plans.stream().anyMatch(x->x.desiredPhysicalCount()>MaterializationConfig.defaults().maxAnimalsPerGroup())) throw new AssertionError("materialization cap");



        // Slot-level reconciliation must be deterministic and idempotent.
        var nearRequests=planner.plan(allGroups,a.species(),List.of(new SimPosition(300,200)));
        var firstProjectionPlan=MaterializationReconciler.reconcile(nearRequests,List.of());
        if(firstProjectionPlan.spawns().isEmpty()) throw new AssertionError("reconciler should spawn nearby wildlife");
        var projected=new ArrayList<PhysicalProjection>();
        int key=0;
        for(var spawn:firstProjectionPlan.spawns()) projected.add(new PhysicalProjection("entity-"+(key++),spawn.populationGroupId(),spawn.speciesId(),spawn.slot()));
        var stableProjectionPlan=MaterializationReconciler.reconcile(nearRequests,projected);
        if(!stableProjectionPlan.isEmpty()) throw new AssertionError("reconciler must be idempotent once slots are satisfied");
        var duplicate=new ArrayList<>(projected);
        var exemplar=projected.getFirst();
        duplicate.add(new PhysicalProjection("zz-duplicate",exemplar.populationGroupId(),exemplar.speciesId(),exemplar.slot()));
        var duplicatePlan=MaterializationReconciler.reconcile(nearRequests,duplicate);
        if(duplicatePlan.despawns().stream().noneMatch(x->x.reason()==ProjectionRemovalReason.DUPLICATE_SLOT)) throw new AssertionError("duplicate slot not removed");
        var abstractRequests=planner.plan(allGroups,a.species(),List.of(new SimPosition(100000,100000)));
        double beforeDematerializeAnimals=a.regions().stream().flatMap(r->r.populations().stream()).mapToDouble(PopulationGroup::population).sum();
        var dematerializePlan=MaterializationReconciler.reconcile(abstractRequests,projected);
        if(dematerializePlan.despawns().isEmpty()) throw new AssertionError("far wildlife should dematerialize");
        if(dematerializePlan.despawns().stream().anyMatch(x->x.reason()!=ProjectionRemovalReason.OUTSIDE_PHYSICAL_LOD)) throw new AssertionError("far removal reason");
        double afterDematerializeAnimals=a.regions().stream().flatMap(r->r.populations().stream()).mapToDouble(PopulationGroup::population).sum();
        if(Math.abs(beforeDematerializeAnimals-afterDematerializeAnimals)>1e-9) throw new AssertionError("dematerialization must not kill aggregate animals");

        var orphan=new ArrayList<>(projected);
        orphan.add(new PhysicalProjection("orphan",999999,"rabbit",0));
        var orphanPlan=MaterializationReconciler.reconcile(nearRequests,orphan);
        if(orphanPlan.despawns().stream().noneMatch(x->x.reason()==ProjectionRemovalReason.ORPHANED_GROUP)) throw new AssertionError("orphan projection not removed");
        for(var request:nearRequests){
            var group=a.findPopulationGroup(request.populationGroupId()).orElseThrow();
            var p1=SpawnPlacementPlanner.position(a.seed(),group,0);
            var p2=SpawnPlacementPlanner.position(a.seed(),group,0);
            if(!p1.equals(p2)) throw new AssertionError("spawn placement must be deterministic");
        }

        // A real physical death decrements aggregate population exactly once, without contaminating the determinism fixture.
        var deathState=new SimulationState(777L); DemoSeeder.seed(deathState);
        var deathGroup=deathState.regions().getFirst().populations().getFirst();
        long deathGroupId=deathGroup.id();
        double beforePhysicalDeath=deathGroup.population();
        if(!deathState.recordPhysicalAnimalDeath(deathGroupId,1,"test")) throw new AssertionError("physical death was not applied");
        double afterPhysicalDeath=deathState.findPopulationGroup(deathGroupId).orElseThrow().population();
        if(Math.abs((beforePhysicalDeath-1)-afterPhysicalDeath)>1e-9) throw new AssertionError("physical death accounting");

        // Create-inspired strategic industry consumes real inputs under a mechanical stress budget.
        var industrialFaction=new dev.livingrealms.sim.faction.Faction(9001,"Industry","Engineer");
        industrialFaction.addSettlement(new dev.livingrealms.sim.faction.Settlement(9002,"Factory City",new SimPosition(0,0),2500,3000));
        industrialFaction.advanceTechnology(.55);
        for(var resource:dev.livingrealms.sim.faction.ResourceType.values()) industrialFaction.stockpile().add(resource,10000);
        double machineryBefore=industrialFaction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.MACHINERY);
        var industryReport=new dev.livingrealms.sim.industry.IndustryEngine().simulateDay(industrialFaction);
        if(industryReport.cycles()<=0||industryReport.stressUsed()>industryReport.stressCapacity()+1e-9) throw new AssertionError("industry stress budget");
        if(industrialFaction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.MACHINERY)<=machineryBefore) throw new AssertionError("machinery industry did not produce");

        // Strategic trade and conquest must work without scripts and remain deterministic.
        var tradeState=new SimulationState(1234L);
        var traderA=new dev.livingrealms.sim.faction.Faction(1001,"Trader A","A");
        var traderB=new dev.livingrealms.sim.faction.Faction(1002,"Trader B","B");
        traderA.addSettlement(new dev.livingrealms.sim.faction.Settlement(1101,"A-town",new SimPosition(0,0),100,120));
        traderB.addSettlement(new dev.livingrealms.sim.faction.Settlement(1102,"B-town",new SimPosition(1000,0),100,120));
        traderA.stockpile().add(dev.livingrealms.sim.faction.ResourceType.FOOD,1000);
        traderA.relationWith(traderB.id()).adjust(50); traderB.relationWith(traderA.id()).adjust(50);
        traderA.relationWith(traderB.id()).setTradeAgreement(true); traderB.relationWith(traderA.id()).setTradeAgreement(true);
        tradeState.addFaction(traderA); tradeState.addFaction(traderB);
        var tradeEngine=new dev.livingrealms.sim.logistics.TradeEngine();
        tradeEngine.simulateDay(tradeState,new dev.livingrealms.sim.util.DeterministicRng(1));
        if(tradeState.shipments().isEmpty()) throw new AssertionError("trade must dispatch a persistent shipment");
        if(traderB.stockpile().get(dev.livingrealms.sim.faction.ResourceType.FOOD)>0) throw new AssertionError("trade goods teleported before caravan arrival");
        var inFlight=tradeState.shipments().getFirst();
        byte[] shipmentSave=SimulationStateCodec.encode(tradeState);
        var tradeRestored=SimulationStateCodec.decode(shipmentSave,tradeState.species());
        if(tradeRestored.shipments().size()!=tradeState.shipments().size()) throw new AssertionError("shipment not persisted");
        if(Math.abs(tradeRestored.shipments().getFirst().progress()-inFlight.progress())>1e-12) throw new AssertionError("shipment progress not persisted");
        int travelGuard=20;
        while(!tradeRestored.shipments().isEmpty()&&travelGuard-->0) tradeEngine.simulateDay(tradeRestored,new dev.livingrealms.sim.util.DeterministicRng(100+travelGuard));
        if(tradeRestored.findFaction(traderB.id()).orElseThrow().stockpile().get(dev.livingrealms.sim.faction.ResourceType.FOOD)<=0) throw new AssertionError("trade shipment never delivered");

        // Only nearby in-flight shipments may become physical caravans, with a hard global budget and one entity per shipment.
        var caravanState=new SimulationState(992L);
        var caravanSeller=new dev.livingrealms.sim.faction.Faction(1201,"Caravan Seller","S");
        var caravanBuyer=new dev.livingrealms.sim.faction.Faction(1202,"Caravan Buyer","B");
        caravanSeller.addSettlement(new dev.livingrealms.sim.faction.Settlement(1211,"Origin",new SimPosition(0,0),80,100));
        caravanBuyer.addSettlement(new dev.livingrealms.sim.faction.Settlement(1212,"Destination",new SimPosition(1200,0),80,100));
        caravanSeller.stockpile().add(dev.livingrealms.sim.faction.ResourceType.FOOD,1000);
        caravanSeller.relationWith(caravanBuyer.id()).adjust(50); caravanBuyer.relationWith(caravanSeller.id()).adjust(50);
        caravanSeller.relationWith(caravanBuyer.id()).setTradeAgreement(true); caravanBuyer.relationWith(caravanSeller.id()).setTradeAgreement(true);
        caravanState.addFaction(caravanSeller); caravanState.addFaction(caravanBuyer);
        tradeEngine.simulateDay(caravanState,new dev.livingrealms.sim.util.DeterministicRng(55));
        var caravanPlanner=new dev.livingrealms.sim.logistics.projection.CaravanMaterializationPlanner(new dev.livingrealms.sim.logistics.projection.CaravanProjectionConfig(500,1));
        var desiredCaravans=caravanPlanner.plan(caravanState.shipments(),List.of(new SimPosition(0,0)));
        if(desiredCaravans.size()!=1) throw new AssertionError("nearby shipment should project as one caravan");
        long caravanShipmentId=desiredCaravans.getFirst().shipmentId();
        var caravanDelta=caravanPlanner.reconcile(desiredCaravans,List.of(),Set.of(caravanShipmentId));
        if(caravanDelta.spawns().size()!=1||!caravanDelta.despawns().isEmpty()) throw new AssertionError("caravan initial projection");
        var existingCaravans=List.of(
                new dev.livingrealms.sim.logistics.projection.CaravanProjectionSnapshot("a",caravanShipmentId),
                new dev.livingrealms.sim.logistics.projection.CaravanProjectionSnapshot("b",caravanShipmentId));
        var duplicateDelta=caravanPlanner.reconcile(desiredCaravans,existingCaravans,Set.of(caravanShipmentId));
        if(!duplicateDelta.spawns().isEmpty()||duplicateDelta.despawns().size()!=1||duplicateDelta.despawns().getFirst().reason()!=dev.livingrealms.sim.logistics.projection.CaravanProjectionRemovalReason.DUPLICATE) throw new AssertionError("duplicate caravan reconciliation");
        var finishedDelta=caravanPlanner.reconcile(List.of(),List.of(existingCaravans.getFirst()),Set.of());
        if(finishedDelta.despawns().size()!=1||finishedDelta.despawns().getFirst().reason()!=dev.livingrealms.sim.logistics.projection.CaravanProjectionRemovalReason.SHIPMENT_FINISHED) throw new AssertionError("finished caravan cleanup");

        var conqueror=new dev.livingrealms.sim.faction.Faction(2001,"Conqueror","C");
        var defender=new dev.livingrealms.sim.faction.Faction(2002,"Defender","D");
        conqueror.addSettlement(new dev.livingrealms.sim.faction.Settlement(2101,"Home",new SimPosition(0,0),100,120));
        defender.addSettlement(new dev.livingrealms.sim.faction.Settlement(2102,"Frontier",new SimPosition(30,0),80,100));
        conqueror.addArmy(new dev.livingrealms.sim.faction.Army(2201,conqueror.id(),new SimPosition(0,0),120));
        conqueror.relationWith(defender.id()).declareWar(); defender.relationWith(conqueror.id()).declareWar();
        new dev.livingrealms.sim.faction.FactionEngine().simulateDay(new ArrayList<>(List.of(conqueror,defender)),new dev.livingrealms.sim.util.DeterministicRng(2));
        if(conqueror.settlements().stream().noneMatch(x->x.id()==2102)) throw new AssertionError("undefended settlement should be conquerable");
        if(defender.settlements().stream().anyMatch(x->x.id()==2102)) throw new AssertionError("captured settlement retained by defender");

        // Settlement blueprints are stable and append-only as a town grows.
        var buildFaction=new dev.livingrealms.sim.faction.Faction(3001,"Builders","Architect");
        var buildSettlement=new dev.livingrealms.sim.faction.Settlement(3101,"Buildtown",new SimPosition(500,500),450,500);
        buildFaction.addSettlement(buildSettlement);
        var blueprint1=SettlementPlanner.plan(buildFaction,buildSettlement);
        var blueprint2=SettlementPlanner.plan(buildFaction,buildSettlement);
        if(!blueprint1.equals(blueprint2)) throw new AssertionError("settlement blueprint must be deterministic");
        if(blueprint1.stream().noneMatch(x->x.role()==StructureRole.KEEP)||blueprint1.stream().noneMatch(x->x.role()==StructureRole.HOUSE)) throw new AssertionError("settlement blueprint missing core structures");
        String completedKey=blueprint1.getFirst().key();
        buildSettlement.markConstructionCompleted(completedKey);
        if(SettlementPlanner.pending(buildFaction,buildSettlement).stream().anyMatch(x->x.key().equals(completedKey))) throw new AssertionError("completed construction returned as pending");
        buildSettlement.addHousing(500);
        var grownBlueprint=SettlementPlanner.plan(buildFaction,buildSettlement);
        for(var old:blueprint1){
            var same=grownBlueprint.stream().filter(x->x.key().equals(old.key())).findFirst().orElseThrow();
            if(!same.center().equals(old.center())) throw new AssertionError("existing structure moved during growth: "+old.key());
        }
        if(grownBlueprint.size()<=blueprint1.size()) throw new AssertionError("growth should append structures");

        // Every strategic structure role must resolve to a non-empty deterministic physical block plan.
        for (var role : StructureRole.values()) {
            var physicalIntent=new ConstructionIntent("test:"+role.name().toLowerCase(java.util.Locale.ROOT), buildFaction.id(), buildSettlement.id(),
                    role, new SimPosition(100,200), Math.max(7, role==StructureRole.AIRFIELD?25:9), Math.max(7, role==StructureRole.AIRFIELD?31:9), 1, 50);
            var physicalA=StructureBlueprintFactory.create(physicalIntent);
            var physicalB=StructureBlueprintFactory.create(physicalIntent);
            if(physicalA.placements().isEmpty()) throw new AssertionError("empty physical blueprint: "+role);
            if(!physicalA.equals(physicalB)) throw new AssertionError("non-deterministic physical blueprint: "+role);
        }

        // Physical construction is resumable and bounded. A blocked high-priority job may not starve other jobs.
        var q=new ConstructionQueue();
        var blockedIntent=new ConstructionIntent("blocked",buildFaction.id(),buildSettlement.id(),StructureRole.HOUSE,new SimPosition(10,10),7,7,0,100);
        var liveIntent=new ConstructionIntent("live",buildFaction.id(),buildSettlement.id(),StructureRole.ROAD,new SimPosition(40,40),5,9,0,10);
        var blockedJob=new ConstructionJob(blockedIntent,64);
        var liveJob=new ConstructionJob(liveIntent,64);
        q.enqueue(blockedJob); q.enqueue(liveJob);
        int beforeLive=liveJob.remaining();
        var tickResult=q.tick(12,(job,op)->job==blockedJob?BuildApplyResult.BLOCKED:BuildApplyResult.APPLIED);
        if(tickResult.attempted()>12) throw new AssertionError("construction exceeded per-tick budget");
        if(liveJob.remaining()>=beforeLive) throw new AssertionError("blocked job starved another construction job");
        if(tickResult.blocked()==0||tickResult.applied()==0) throw new AssertionError("construction scheduler result accounting");

        var finishQ=new ConstructionQueue();
        var finishIntent=new ConstructionIntent("finish",buildFaction.id(),buildSettlement.id(),StructureRole.ROAD,new SimPosition(80,80),3,3,0,1);
        var finishJob=new ConstructionJob(finishIntent,70); finishQ.enqueue(finishJob);
        String finishKey=finishJob.key();
        int guard=1000;
        while(!finishQ.isEmpty()&&guard-->0) {
            var r=finishQ.tick(7,(job,op)->BuildApplyResult.SKIPPED);
            if(r.attempted()>7) throw new AssertionError("finish queue budget");
        }
        if(!finishQ.isEmpty()||guard<=0||!finishJob.complete()) throw new AssertionError("construction job did not complete");
        if(!finishKey.equals(finishJob.key())) throw new AssertionError("construction key changed while building");

        // Schema migration: the current decoder must still read an actual schema-1 fixture.
        try {
            byte[] legacyBytes=Files.readAllBytes(Path.of("src/testCore/resources/legacy-schema1.bin"));
            var legacy=SimulationStateCodec.decode(legacyBytes,a.species());
            if(legacy.clock().day()!=17||legacy.factions().size()!=2) throw new AssertionError("legacy schema-1 migration");
            byte[] schema2Bytes=Files.readAllBytes(Path.of("src/testCore/resources/legacy-schema2.bin"));
            var schema2=SimulationStateCodec.decode(schema2Bytes,a.species());
            if(schema2.clock().day()!=23||schema2.factions().size()!=2||!schema2.shipments().isEmpty()) throw new AssertionError("legacy schema-2 migration");
        } catch(java.io.IOException e){throw new RuntimeException(e);}

        var customCatalog=new LinkedHashMap<>(a.species());
        var rabbit=a.species().get("rabbit");
        var customSpecies=new SpeciesDefinition("test_mouse","Test Mouse",rabbit.diet(),rabbit.activityCycle(),rabbit.socialPattern(),
                0.025,730,45,20,5,6,0.004,0.005,2.0,0.05,0.95,0.0,0.02,2,40,
                rabbit.climates(),rabbit.habitatTags(),Set.of(),Set.of(),false);
        customCatalog.put(customSpecies.id(),customSpecies);
        var customState=new SimulationState(42L,customCatalog);
        var customRegion=new EcosystemRegion(customState.nextId(),customState.biomes().get("temperate_forest"),1);
        customRegion.add(new PopulationGroup(customState.nextId(),customSpecies.id(),"temperate_forest",new SimPosition(0,0),25));
        customState.addRegion(customRegion);
        byte[] customBytes=SimulationStateCodec.encode(customState);
        var customRestored=SimulationStateCodec.decode(customBytes,customCatalog);
        if(!customRestored.species().containsKey("test_mouse")) throw new AssertionError("datapack-only species lost on reload");
        if(!customRestored.regions().getFirst().populations().getFirst().speciesId().equals("test_mouse")) throw new AssertionError("custom population lost");

        var persistentSettlement=a.factions().getFirst().settlements().getFirst();
        persistentSettlement.markConstructionCompleted("keep:0");
        persistentSettlement.markConstructionCompleted("house:0");
        byte[] encoded=SimulationStateCodec.encode(a);
        SimulationState restored=SimulationStateCodec.decode(encoded,a.species());
        var restoredSettlement=restored.factions().getFirst().settlements().getFirst();
        if(!restoredSettlement.isConstructionCompleted("keep:0")||!restoredSettlement.isConstructionCompleted("house:0")) throw new AssertionError("construction completion not persisted");
        if(!a.summary().equals(restored.summary())) throw new AssertionError("save/load summary mismatch");
        if(encoded.length<100) throw new AssertionError("save unexpectedly small");
        SimulationState b=new SimulationState(123456789L);DemoSeeder.seed(b);b.advanceDays(365);
        if(!a.summary().equals(b.summary())) throw new AssertionError("simulation must be deterministic\n"+a.summary()+"\n"+b.summary());
        a.advanceDays(30); restored.advanceDays(30);
        if(!a.summary().equals(restored.summary())) throw new AssertionError("save/load continuation diverged");
        System.out.println("START "+start);System.out.println("END   "+end);System.out.println("PASS ecology + behavior + materialization + deterministic save/load ("+encoded.length+" bytes)");
    }
}
