package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.BuildApplyResult;
import dev.livingrealms.sim.construction.BuildOperation;
import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.ConstructionIntentChunkSelector;
import dev.livingrealms.sim.construction.ConstructionJob;
import dev.livingrealms.sim.construction.ConstructionQueue;
import dev.livingrealms.sim.construction.ConstructionRetryKey;
import dev.livingrealms.sim.construction.EntranceAccessPlanner;
import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.PhysicalDevelopmentReconciler;
import dev.livingrealms.sim.construction.ResolvedBuildSite;
import dev.livingrealms.sim.construction.SettlementParcelPlanner;
import dev.livingrealms.sim.construction.SettlementPlanCache;
import dev.livingrealms.sim.construction.StructureAccessValidator;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureGeometryRules;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.construction.WizardTreesPlanner;
import dev.livingrealms.sim.compat.ModCompatibilityPolicy;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.runtime.ProjectionBudget;
import dev.livingrealms.sim.world.WizardTreesSeeder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Projects strategic settlement blueprints into loaded Overworld chunks under a hard per-tick
 * operation budget. Canonical completion remains in the simulation save; the queue is disposable.
 */
public final class SettlementConstructionMaterializer {
    /** Entity/presentation LOD only. This radius does not gate settlement block existence. */
    private static final double PRESENTATION_RADIUS=640.0D;
    private static final double PRESENTATION_RADIUS_SQR=PRESENTATION_RADIUS*PRESENTATION_RADIUS;
    private static final int MAX_QUEUED_JOBS=48;
    private static final int MAX_SETTLEMENTS_PER_DISCOVERY=12;
    private static final ConstructionQueue QUEUE=new ConstructionQueue();
    private static final Map<String,Settlement> JOB_OWNERS=new HashMap<>();
    /** Globally stable construction key (settlementId:intentKey) -> earliest rediscovery day. */
    private static final Map<String,Long> RETRY_AFTER_DAY=new HashMap<>();
    /** ServerLevel identity this static queue is bound to; other levels are no-ops until clear. */
    private static Object boundLevelIdentity;
    private static int catchupTicks;
    private static long catchupSimulatedDays;
    private static int catchupIntentsPerSettlement=1;
    private static int catchupOpsBoost;
    private static int settlementScanCursor;

    private SettlementConstructionMaterializer() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        if(level==null)return;
        if(boundLevelIdentity==null)boundLevelIdentity=level;
        else if(boundLevelIdentity!=level)return; // E8: foreign level must not drain this world's queue
        long catchup=data.state().consumeConstructionCatchup();
        if(catchup>0)requestCatchup(catchup);
        refreshPresentationScope(level,data);
        discoverLoadedWork(level,data);
        int operationBudget=ProjectionBudget.forPlayers(data.state().config(),Math.max(1,level.players().size()))
                .constructionBlockOpsPerTick();
        if(catchupTicks>0){
            // Soft-ramp catch-up ops to avoid a 960 ops/tick hitch with many nearby settlements/players.
            int players=Math.max(1,level.players().size());
            int peak=Math.max(480,960/Math.min(4,players));
            if(catchupOpsBoost<=0)catchupOpsBoost=Math.min(peak,operationBudget+80);
            else catchupOpsBoost=Math.min(peak,catchupOpsBoost+48);
            operationBudget=Math.max(operationBudget,catchupOpsBoost);
            catchupTicks--;
            if(catchupTicks<=0)catchupOpsBoost=0;
        }
        AuthoredBlockLedger ledger=data.authoredBlocks();
        java.util.Map<String,ConstructionJob> beforeTick=new HashMap<>();
        for(ConstructionJob j:QUEUE.jobs()) beforeTick.put(j.key(),j);
        var result=QUEUE.tick(operationBudget,(job,operation)->ConstructionBlockApplier.apply(level,job,operation,ledger));
        boolean dirty=result.applied()>0;
        long day=data.state().clock().day();
        for(String completed:result.completedJobKeys()) {
            Settlement owner=JOB_OWNERS.remove(completed);
            ConstructionRetryKey retryKey=ConstructionRetryKey.parse(completed);
            ConstructionJob finished=beforeTick.get(completed);
            // Access gate before completedConstructionReceipt (StructureAccessValidator).
            if(finished!=null && WorldStructureAccessProbe.requiresAccessGate(finished.intent().role())) {
                var access=WorldStructureAccessProbe.probe(level,finished.intent(),finished.operations());
                if(WorldStructureAccessProbe.shouldDefer(access)) {
                    RETRY_AFTER_DAY.put(retryKey.wire(),day+1);
                    dirty=true;
                    continue;
                }
                if(!access.pass()) {
                    RETRY_AFTER_DAY.put(retryKey.wire(),day+3);
                    dirty=true;
                    continue;
                }
            }
            RETRY_AFTER_DAY.remove(retryKey.wire());
            if(owner!=null && owner.markConstructionCompleted(retryKey.intentKey())) {
                dirty=true;
                creditHousingFromCompletedHouse(data,owner,retryKey.intentKey());
                if(retryKey.intentKey().equals(owner.activeConstructionKey()))owner.setActiveConstructionKey("");
            }
        }
        for(String rejected:result.rejectedJobKeys()) {
            Settlement owner=JOB_OWNERS.remove(rejected);
            ConstructionRetryKey retryKey=ConstructionRetryKey.parse(rejected);
            // Back off so an impossible/obstructed building cannot replan every tick.
            long backoff=owner!=null && owner.isConstructionCompleted(retryKey.intentKey())?day+1:day+3;
            RETRY_AFTER_DAY.put(retryKey.wire(),backoff);
            dirty=true;
        }
        if(dirty) data.setDirty();
    }

    public static void requestCatchup(long simulatedDays){
        if(simulatedDays<=0)return;
        int requested=(int)Math.min(400L,40L+Math.min(120L,simulatedDays)*3L);
        boolean fresh=catchupTicks<=0;
        catchupTicks=Math.max(catchupTicks,requested);
        catchupSimulatedDays=Math.max(catchupSimulatedDays,simulatedDays);
        // Per-settlement refinement uses PhysicalDevelopmentReconciler inside discoverLoadedWork.
        catchupIntentsPerSettlement=Math.max(2,Math.min(12,1+(int)Math.min(8L,simulatedDays/12L)));
        if(fresh)catchupOpsBoost=0; // restart soft ramp for a new catch-up wave
    }

    public static boolean catchupActive(){return catchupTicks>0;}

    public static void clear() {
        QUEUE.clear(); JOB_OWNERS.clear(); RETRY_AFTER_DAY.clear();
        boundLevelIdentity=null;
        catchupTicks=0; catchupSimulatedDays=0; catchupIntentsPerSettlement=1; catchupOpsBoost=0; settlementScanCursor=0;
    }

    /** Test/hook: whether the static queue currently owns jobs. */
    public static boolean queueEmpty(){return QUEUE.size()==0;}

    /** Test/hook: identity currently bound, or null after clear. */
    public static Object boundLevelIdentity(){return boundLevelIdentity;}

    private static void discoverLoadedWork(ServerLevel level, LivingRealmsSavedData data) {
        if(QUEUE.size()>=MAX_QUEUED_JOBS) return;
        boolean catchingUp=catchupTicks>0;
        long day=data.state().clock().day();

        java.util.List<CivilizationFabricChunkQueue.ChunkRef> chunkHints =
                CivilizationFabricChunkQueue.pollSettlement(level,16);
        java.util.List<ConstructionIntentChunkSelector.ChunkWindow> windows=chunkHints.stream()
                .map(c->new ConstructionIntentChunkSelector.ChunkWindow(c.x(),c.z())).toList();

        java.util.List<Faction> factions=new java.util.ArrayList<>(data.state().factions());
        java.util.List<Settlement> candidates=new java.util.ArrayList<>();
        java.util.Map<Long,Faction> owners=new HashMap<>();
        for(Faction faction:factions) for(Settlement settlement:faction.settlements()) {
            candidates.add(settlement);
            owners.put(settlement.id(),faction);
        }
        if(candidates.isEmpty()) return;

        // Newly available chunks get priority so skyline/core fabric is queued as the chunk becomes
        // usable. With no fresh hint, a fair global rotation reconciles any already-loaded chunk
        // (important for player-founded settlements and routes created after a chunk was loaded).
        if(!windows.isEmpty()){
            candidates.sort(java.util.Comparator
                    .comparingDouble((Settlement s)->hintDistanceSq(s,windows))
                    .thenComparingLong(Settlement::id));
        }else{
            settlementScanCursor=Math.floorMod(settlementScanCursor,candidates.size());
        }

        Set<Long> queuedSettlements=new HashSet<>();
        for(ConstructionJob job:QUEUE.jobs()) queuedSettlements.add(job.intent().settlementId());
        int scanned=0;
        for(int n=0;n<candidates.size()&&scanned<MAX_SETTLEMENTS_PER_DISCOVERY&&QUEUE.size()<MAX_QUEUED_JOBS;n++){
            Settlement settlement=windows.isEmpty()
                    ?candidates.get(Math.floorMod(settlementScanCursor+n,candidates.size()))
                    :candidates.get(n);
            scanned++;
            if(queuedSettlements.contains(settlement.id()) && !catchingUp) continue;
            Faction faction=owners.get(settlement.id());
            if(faction==null) continue;
            boolean wizardTrees=WizardTreesSeeder.isWizardTrees(faction);
            java.util.List<ConstructionIntent> pending;
            int allow;
            if(wizardTrees){
                pending=new java.util.ArrayList<>(WizardTreesPlanner.pending(faction,settlement));
                allow=catchingUp?Math.min(4,catchupIntentsPerSettlement):1;
            }else{
                PhysicalDevelopmentReconciler.Deficit deficit=PhysicalDevelopmentReconciler.analyze(faction,settlement);
                pending=new java.util.ArrayList<>(deficit.backlog());
                pending.addAll(PrimaryEconomyPlanner.pending(data.state(),faction,settlement));
                allow=catchingUp?PhysicalDevelopmentReconciler.catchupIntentsPerSettlement(catchupSimulatedDays,deficit):1;
            }
            pending.sort(java.util.Comparator.comparingInt(ConstructionIntent::priority).reversed()
                    .thenComparing(ConstructionIntent::key));
            int enqueued=0;
            for(ConstructionIntent intent:pending) {
                if(QUEUE.size()>=MAX_QUEUED_JOBS) break;
                if(!windows.isEmpty() && !ConstructionIntentChunkSelector.intersectsAny(intent,windows)) continue;
                String retryWire=ConstructionRetryKey.of(intent).wire();
                Long retryAfter=RETRY_AFTER_DAY.get(retryWire);
                if(retryAfter!=null && day<retryAfter) continue;
                BlockPos center=new BlockPos((int)Math.round(intent.center().x()),level.getSeaLevel(),
                        (int)Math.round(intent.center().z()));
                // World fabric is load-driven: no player-distance check. We only mutate a chunk
                // that the server already has available, and never force-load it.
                if(!level.hasChunkAt(center)) continue;
                ConstructionJob job=createTerrainAwareJob(level,intent);
                if(job==null) {
                    RETRY_AFTER_DAY.put(retryWire,day+2);
                    continue;
                }
                if(QUEUE.enqueue(job)){
                    JOB_OWNERS.put(job.key(),settlement);
                    settlement.setActiveConstructionKey(intent.key());
                    queuedSettlements.add(settlement.id());
                    enqueued++;
                }
                if(enqueued>=allow) break;
            }
        }
        if(windows.isEmpty()){
            settlementScanCursor=Math.floorMod(settlementScanCursor+Math.max(1,scanned),candidates.size());
        }
    }

    private static double hintDistanceSq(Settlement settlement,
                                         java.util.List<ConstructionIntentChunkSelector.ChunkWindow> windows){
        double best=Double.POSITIVE_INFINITY;
        for(var window:windows){
            double cx=(window.minX()+window.maxX())*.5,cz=(window.minZ()+window.maxZ())*.5;
            double dx=settlement.position().x()-cx,dz=settlement.position().z()-cz;
            best=Math.min(best,dx*dx+dz*dz);
        }
        return best;
    }

    private static void refreshPresentationScope(ServerLevel level,LivingRealmsSavedData data){
        java.util.Set<Long> activated=new HashSet<>();
        for(Faction faction:data.state().factions())for(Settlement settlement:faction.settlements()){
            if(nearPlayerForPresentation(level,settlement))activated.add(settlement.id());
        }
        data.state().presentationScope().setActivated(activated);
    }

    private static void creditHousingFromCompletedHouse(LivingRealmsSavedData data,Settlement owner,String intentKey){
        if(intentKey==null||!intentKey.startsWith("house:"))return;
        Faction faction=null;
        for(Faction f:data.state().factions()){
            if(f.settlements().stream().anyMatch(s->s.id()==owner.id())){faction=f;break;}
        }
        if(faction==null){owner.addHousing(8);return;}
        ConstructionIntent intent=SettlementPlanCache.plan(faction,owner).stream()
                .filter(i->i.key().equals(intentKey)&&i.role()==StructureRole.HOUSE).findFirst().orElse(null);
        int beds=intent==null?HousingCapacity.representedResidents(7,7):HousingCapacity.representedResidents(intent);
        owner.addHousing(Math.max(4,beds));
    }


    /**
     * Resolves a blueprint against real terrain and parcel law. Parcel-bound buildings stay inside
     * their legal lot (porch/stairs/frontage excepted); never free-search 8–20 blocks onto other lots.
     * Unbound roles use a modest ≤4-block pad search.
     */
    private static ConstructionJob createTerrainAwareJob(ServerLevel level,ConstructionIntent original) {
        if(isWizardRole(original.role()))return createWizardUndergroundJob(level,original);
        if(original.role()==StructureRole.ROAD) {
            RoadPlan plan=roadOperations(level,original);
            return plan.operations().isEmpty()?null:new ConstructionJob(original,plan.operations(),0,plan.omittedRequired());
        }
        if(original.role()==StructureRole.WALL || original.role()==StructureRole.GATE) {
            java.util.List<BuildOperation> ops=terrainFollowingOperations(level,original);
            return ops.isEmpty()?null:new ConstructionJob(original,ops,0);
        }
        ResolvedBuildSite site=findBuildSite(level,original);
        if(site==null||site.adaptation()==ResolvedBuildSite.Adaptation.REJECTED)return null;
        ConstructionIntent intent=site.toIntent(original);
        java.util.List<BuildOperation> ops=buildingOperations(level,intent,site);
        return ops.isEmpty()?null:new ConstructionJob(intent,ops,0);
    }

    static boolean isWizardRole(StructureRole role){return role==StructureRole.WIZARD_HALL||role==StructureRole.WIZARD_GROVE||role==StructureRole.WIZARD_HOME||role==StructureRole.WIZARD_TUNNEL;}

    private static ConstructionJob createWizardUndergroundJob(ServerLevel level,ConstructionIntent original){
        int[][] offsets={{0,0},{16,0},{-16,0},{0,16},{0,-16},{28,28},{28,-28},{-28,28},{-28,-28}};
        ResolvedBuildSite best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int[] off:offsets){
            int x=(int)Math.round(original.center().x()+off[0]),z=(int)Math.round(original.center().z()+off[1]);
            BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;
            int surface=naturalSurfaceY(level,x,z);int base=Math.max(level.getMinBuildHeight()+8,surface-18);
            if(base+10>=surface)continue;
            double score=-surface*4.0+Math.hypot(off[0],off[1]);
            if(score<bestScore){
                var center=new dev.livingrealms.sim.world.SimPosition(original.center().x()+off[0],original.center().z()+off[1]);
                best=new ResolvedBuildSite(original.center(),center,original.width(),original.depth(),
                        original.rotationQuarterTurns(),null,null,base,null,"",0,0,ResolvedBuildSite.Adaptation.NONE);
                bestScore=score;
            }
        }
        if(best==null)return null;
        ConstructionIntent intent=best.toIntent(original);
        java.util.List<BuildOperation> ops=buildingOperations(level,intent,best);
        return ops.isEmpty()?null:new ConstructionJob(intent,ops,0);
    }

    /** Cascade: bounded alignment → foundation → stairs/retaining → reject parcel (never relocate). */
    private static ResolvedBuildSite findBuildSite(ServerLevel level,ConstructionIntent original) {
        boolean parcelBound=original.hasParcel();
        java.util.List<int[]> offsets=parcelBound?ResolvedBuildSite.parcelAlignmentOffsets():ResolvedBuildSite.openSiteAlignmentOffsets();
        int maxSlope=switch(original.role()){case AIRFIELD -> 2;case FARM,KEEP,FACTORY -> 3;default -> 2;};
        ResolvedBuildSite best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int[] off:offsets){
            var candidateCenter=new dev.livingrealms.sim.world.SimPosition(original.center().x()+off[0],original.center().z()+off[1]);
            if(parcelBound && !ResolvedBuildSite.footprintInsideParcel(
                    candidateCenter,original.width(),original.depth(),original.rotationQuarterTurns(),
                    original.center(),original.parcelWidth(),original.parcelDepth())) continue;
            ConstructionIntent candidate=original.withCenter(candidateCenter);
            TerrainStats stats=terrainStats(level,candidate);
            if(stats==null)continue;
            int padSlope=stats.max()-stats.min();
            if(padSlope>6)continue;
            boolean flatOk=padSlope<=maxSlope;
            boolean adaptOk=padSlope<=4;
            if(!flatOk&&!adaptOk)continue;
            int foundationY=stats.max();
            EntranceProbe entrance=probeEntrance(level,candidate,foundationY);
            ResolvedBuildSite.Adaptation adaptation=ResolvedBuildSite.adaptationForGrade(
                    entrance.doorFloorY(),entrance.approachY(),padSlope);
            if(adaptation==ResolvedBuildSite.Adaptation.REJECTED)continue;
            if(!flatOk&&adaptation==ResolvedBuildSite.Adaptation.NONE) adaptation=ResolvedBuildSite.Adaptation.FOUNDATION_PIERS;
            StructureAccessValidator.AccessSample access=new StructureAccessValidator.AccessSample(
                    accessKindFor(entrance.doorFloorY(),entrance.approachY()),
                    entrance.doorFloorY(),entrance.approachY(),true,false,false);
            if(!StructureAccessValidator.accepts(access))continue;
            SettlementParcelPlanner.ParcelFrontage frontage=null;
            if(parcelBound){
                frontage=new SettlementParcelPlanner.ParcelFrontage(
                        original.parcelId(),original.parcelId(),
                        new dev.livingrealms.sim.world.SimPosition(entrance.streetX()+0.5,entrance.streetZ()+0.5),
                        original.rotationQuarterTurns(),0);
            }
            ResolvedBuildSite.EntranceSpec entranceSpec=new ResolvedBuildSite.EntranceSpec(
                    entrance.doorLocalX(),entrance.doorLocalZ(),entrance.doorFloorY(),entrance.approachY());
            double score=padSlope*120.0+Math.hypot(off[0],off[1])+(adaptation==ResolvedBuildSite.Adaptation.NONE?0:40);
            if(score<bestScore){
                bestScore=score;
                best=new ResolvedBuildSite(original.center(),candidateCenter,original.width(),original.depth(),
                        original.rotationQuarterTurns(),frontage,entranceSpec,foundationY,access,
                        original.parcelId(),original.parcelWidth(),original.parcelDepth(),adaptation);
            }
        }
        return best;
    }

    private static StructureAccessValidator.AccessKind accessKindFor(int doorFloorY,int approachY){
        int delta=Math.abs(doorFloorY-approachY);
        if(delta<=1)return StructureAccessValidator.AccessKind.FLAT;
        if(delta<=3)return StructureAccessValidator.AccessKind.STAIRS;
        if(delta<=8)return StructureAccessValidator.AccessKind.SWITCHBACK;
        return StructureAccessValidator.AccessKind.CLIFF;
    }

    private record EntranceProbe(int doorLocalX,int doorLocalZ,int doorFloorY,int approachY,int streetX,int streetZ){}

    private static EntranceProbe probeEntrance(ServerLevel level,ConstructionIntent intent,int baseY){
        int turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        int doorLocalX=0,doorLocalZ=-(intent.depth()/2);
        int ox=doorLocalX,oz=doorLocalZ;
        for(int i=0;i<turns;i++){int t=ox;ox=-oz;oz=t;}
        int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z());
        int approachX=cx+ox,approachZ=cz+oz;
        int stepX=0,stepZ=-1;for(int i=0;i<turns;i++){int t=stepX;stepX=-stepZ;stepZ=t;}
        int streetX=approachX+stepX,streetZ=approachZ+stepZ;
        return new EntranceProbe(doorLocalX,doorLocalZ,baseY,naturalSurfaceY(level,streetX,streetZ),streetX,streetZ);
    }

    private static TerrainStats terrainStats(ServerLevel level,ConstructionIntent intent){
        boolean allowWater=intent.role()==StructureRole.DOCK||intent.role()==StructureRole.FISHERY;
        int turns=Math.floorMod(intent.rotationQuarterTurns(),4);int w=(turns&1)==0?intent.width():intent.depth(),d=(turns&1)==0?intent.depth():intent.width();
        int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z()),hx=w/2,hz=d/2;
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,samples=0,steepNeighbors=0;Integer prevY=null;
        for(int z=-hz;z<=hz;z+=Math.max(2,d/6))for(int x=-hx;x<=hx;x+=Math.max(2,w/6)){
            int wx=cx+x,wz=cz+z;BlockPos probe=new BlockPos(wx,level.getSeaLevel(),wz);if(!level.hasChunkAt(probe))return null;
            int y=naturalSurfaceY(level,wx,wz);if(y<=level.getMinBuildHeight()+1||y>=level.getMaxBuildHeight()-18)return null;
            BlockState ground=level.getBlockState(new BlockPos(wx,y,wz));
            boolean flooded=!ground.getFluidState().isEmpty()||!level.getFluidState(new BlockPos(wx,y+1,wz)).isEmpty();
            if(flooded&&!allowWater)return null;
            if(!allowWater&&y<=level.getSeaLevel()-1)return null;
            BlockState below=level.getBlockState(new BlockPos(wx,y-1,wz));
            if(below.isAir()||(!allowWater&&!below.getFluidState().isEmpty()))return null;
            if(prevY!=null&&Math.abs(y-prevY)>4)steepNeighbors++;
            prevY=y;min=Math.min(min,y);max=Math.max(max,y);samples++;
        }
        if(min==Integer.MAX_VALUE)return null;
        if(samples>0&&steepNeighbors>Math.max(1,samples/4))return null;
        if(max-min>6)return null;
        return new TerrainStats(min,max);
    }

    private static java.util.List<BuildOperation> buildingOperations(ServerLevel level,ConstructionIntent intent,ResolvedBuildSite site){
        int baseY=site.foundationY();
        var blueprint=StructureBlueprintFactory.create(intent);int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z()),turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        java.util.List<BuildOperation> out=new java.util.ArrayList<>(blueprint.operationCount()+intent.width()*intent.depth());
        Integer doorLocalX=null,doorLocalZ=null;
        int pierDepth=site.adaptation()==ResolvedBuildSite.Adaptation.FOUNDATION_PIERS||site.adaptation()==ResolvedBuildSite.Adaptation.STAIRS_RETAINING?6:4;
        for(BlockPlacement p:blueprint.placements()){
            int rx=p.dx(),rz=p.dz();for(int i=0;i<turns;i++){int t=rx;rx=-rz;rz=t;}int wx=cx+rx,wz=cz+rz;
            out.add(new BuildOperation(wx,baseY+p.dy(),wz,p.slot(),p.phase()));
            if(p.dy()==0&&p.slot()==PaletteSlot.FOUNDATION){
                int surface=naturalSurfaceY(level,wx,wz);
                for(int y=baseY-1;y>surface&&y>=baseY-pierDepth;y--) out.add(new BuildOperation(wx,y,wz,PaletteSlot.FOUNDATION,p.phase()));
            }
            if(p.slot()==PaletteSlot.DOOR&&p.dy()==1){doorLocalX=p.dx();doorLocalZ=p.dz();}
        }
        if(doorLocalX!=null){
            int ox=doorLocalX,oz=doorLocalZ;
            for(int i=0;i<turns;i++){int t=ox;ox=-oz;oz=t;}
            int approachX=cx+ox,approachZ=cz+oz;
            int stepX=0,stepZ=-1;for(int i=0;i<turns;i++){int t=stepX;stepX=-stepZ;stepZ=t;}
            int streetX=approachX+stepX,streetZ=approachZ+stepZ;
            int approachY=site.entrance()!=null?site.entrance().approachY():naturalSurfaceY(level,streetX,streetZ);
            for(var fix:EntranceAccessPlanner.plan(doorLocalX,doorLocalZ,baseY,approachY)){
                int fx=fix.dx(),fz=fix.dz();for(int i=0;i<turns;i++){int t=fx;fx=-fz;fz=t;}
                out.add(new BuildOperation(cx+fx,fix.dy(),cz+fz,fix.slot(),dev.livingrealms.sim.construction.ConstructionPhase.DETAIL));
            }
        }
        return out;
    }

    private static java.util.List<BuildOperation> terrainFollowingOperations(ServerLevel level,ConstructionIntent intent){
        var blueprint=StructureBlueprintFactory.create(intent);int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z()),turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        java.util.List<BuildOperation> out=new java.util.ArrayList<>(blueprint.operationCount());
        java.util.Map<Long,Integer> heights=new java.util.HashMap<>();
        for(BlockPlacement p:blueprint.placements()){int rx=p.dx(),rz=p.dz();for(int i=0;i<turns;i++){int t=rx;rx=-rz;rz=t;}int wx=cx+rx,wz=cz+rz;long key=(((long)wx)<<32)^(wz&0xffffffffL);Integer surface=heights.get(key);if(surface==null){BlockPos probe=new BlockPos(wx,level.getSeaLevel(),wz);if(!level.hasChunkAt(probe))continue;surface=naturalSurfaceY(level,wx,wz);BlockState ground=level.getBlockState(new BlockPos(wx,surface,wz));if(!ground.getFluidState().isEmpty())continue;heights.put(key,surface);}out.add(new BuildOperation(wx,surface+p.dy(),wz,p.slot(),p.phase()));}
        return out;
    }


    /**
     * Builds a straight, walkable street at ground level.
     * Omitted centerline cells (unloaded/steep/unusable) are counted so receipts cannot treat them as irrelevant.
     */
    private static RoadPlan roadOperations(ServerLevel level,ConstructionIntent intent){
        if(intent.hasPath())return polylineRoadOperations(level,intent);
        int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z()),turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        int hx=intent.width()/2,hz=intent.depth()/2;
        boolean rural=intent.width()<=3; // countryside dirt paths: floor only, no curb/fence
        java.util.List<BuildOperation> out=new java.util.ArrayList<>(intent.width()*intent.depth()*3);
        int omittedRequired=0;
        Integer previous=null;
        for(int lz=-hz;lz<=hz;lz++){
            int crx=0,crz=lz;for(int i=0;i<turns;i++){int t=crx;crx=-crz;crz=t;}
            int centerX=cx+crx,centerZ=cz+crz;
            boolean rowUsable=true;
            if(!level.hasChunkAt(new BlockPos(centerX,level.getSeaLevel(),centerZ))){rowUsable=false;}
            int raw=rowUsable?naturalSurfaceY(level,centerX,centerZ):-1;
            if(rowUsable&&raw<=level.getMinBuildHeight()+1)rowUsable=false;
            boolean flooded=false;
            if(rowUsable){
                BlockState ground=level.getBlockState(new BlockPos(centerX,raw,centerZ));
                flooded=!ground.getFluidState().isEmpty()||!level.getFluidState(new BlockPos(centerX,raw+1,centerZ)).isEmpty();
            }
            int target;
            if(flooded){
                // Bridge deck: keep at sea level / previous deck so paths never trench underwater.
                int deck=Math.max(level.getSeaLevel(),previous==null?raw:previous);
                target=deck;
            }else{
                target=previous==null?raw:Math.max(previous-1,Math.min(previous+1,raw));
                // Bridge over cliffs/gaps instead of omitting the row.
                if(rowUsable&&Math.abs(raw-target)>4){
                    target=Math.max(raw,previous==null?raw:previous);
                }
            }
            if(!rowUsable){
                omittedRequired+=Math.max(1,intent.width());
                continue;
            }
            previous=target;
            for(int lx=-hx;lx<=hx;lx++){
                int rx=lx,rz=lz;for(int i=0;i<turns;i++){int t=rx;rx=-rz;rz=t;}int wx=cx+rx,wz=cz+rz;
                if(!level.hasChunkAt(new BlockPos(wx,level.getSeaLevel(),wz))){omittedRequired++;continue;}
                int surface=naturalSurfaceY(level,wx,wz);
                if(surface<=level.getMinBuildHeight()+1){omittedRequired++;continue;}
                for(int y=target+1;y<=Math.min(surface+8,target+10);y++){
                    BlockPos clearPos=new BlockPos(wx,y,wz);
                    BlockState st=level.getBlockState(clearPos);if(st.isAir())continue;
                    if(st.canBeReplaced()||st.is(BlockTags.LEAVES)||st.is(Blocks.SNOW)||st.is(Blocks.MOSS_CARPET)
                            ||WorldMutationGuard.isNaturalTreeLog(level,clearPos))
                        out.add(new BuildOperation(wx,y,wz,PaletteSlot.AIR,dev.livingrealms.sim.construction.ConstructionPhase.CLEAR));
                    else break;
                }
                out.add(new BuildOperation(wx,target+1,wz,PaletteSlot.AIR,dev.livingrealms.sim.construction.ConstructionPhase.CLEAR));
                // Rural: entire carriageway is PATH. Urban edges keep FOUNDATION sidewalks.
                PaletteSlot slot=(!rural && Math.abs(lx)==hx)?PaletteSlot.FOUNDATION:PaletteSlot.PATH;
                out.add(new BuildOperation(wx,target,wz,slot,dev.livingrealms.sim.construction.ConstructionPhase.FOUNDATION));
                if(!rural && Math.abs(lx)==hx&&Math.floorMod(lz+wx,11)==0)
                    out.add(new BuildOperation(wx,target+1,wz,PaletteSlot.LIGHT,dev.livingrealms.sim.construction.ConstructionPhase.DETAIL));
                // Bridge pillars / retaining when deck is above natural ground or water.
                if(surface<target){
                    for(int y=Math.max(surface+1,target-16);y<target;y++)
                        out.add(new BuildOperation(wx,y,wz,PaletteSlot.FOUNDATION,dev.livingrealms.sim.construction.ConstructionPhase.FOUNDATION));
                }else{
                    for(int y=target-1;y>surface&&y>=target-4;y--)
                        out.add(new BuildOperation(wx,y,wz,PaletteSlot.FOUNDATION,dev.livingrealms.sim.construction.ConstructionPhase.FOUNDATION));
                }
            }
        }
        return new RoadPlan(out,omittedRequired);
    }

    /** Materialize a true curved/diagonal street centerline emitted by SettlementStreetGraph. */
    private static RoadPlan polylineRoadOperations(ServerLevel level,ConstructionIntent intent){
        boolean rural=intent.width()<=3;
        int half=Math.max(0,intent.width()/2);
        java.util.List<BuildOperation> out=new java.util.ArrayList<>();
        java.util.Set<Long> visitedRows=new java.util.HashSet<>();
        int omittedRequired=0;
        Integer previousTarget=null;

        for(int segment=1;segment<intent.path().size();segment++){
            var a=intent.path().get(segment-1);
            var b=intent.path().get(segment);
            double vx=b.x()-a.x(),vz=b.z()-a.z(),length=Math.hypot(vx,vz);
            if(length<1e-6)continue;
            double nx=-vz/length,nz=vx/length;
            int steps=Math.max(1,(int)Math.ceil(length));
            for(int step=0;step<=steps;step++){
                double t=step/(double)steps;
                int centerX=(int)Math.round(a.x()+vx*t),centerZ=(int)Math.round(a.z()+vz*t);
                long rowKey=((long)centerX<<32)^(centerZ&0xffffffffL);
                if(!visitedRows.add(rowKey))continue;
                BlockPos centerProbe=new BlockPos(centerX,level.getSeaLevel(),centerZ);
                if(!level.hasChunkAt(centerProbe)){
                    omittedRequired+=Math.max(1,intent.width());
                    continue;
                }
                int raw=naturalSurfaceY(level,centerX,centerZ);
                if(raw<=level.getMinBuildHeight()+1){
                    omittedRequired+=Math.max(1,intent.width());
                    continue;
                }
                BlockState centerGround=level.getBlockState(new BlockPos(centerX,raw,centerZ));
                boolean flooded=!centerGround.getFluidState().isEmpty()
                        ||!level.getFluidState(new BlockPos(centerX,raw+1,centerZ)).isEmpty();
                int target;
                if(flooded){
                    target=Math.max(level.getSeaLevel(),previousTarget==null?raw:previousTarget);
                }else{
                    target=previousTarget==null?raw:Math.max(previousTarget-1,Math.min(previousTarget+1,raw));
                    if(previousTarget!=null&&Math.abs(raw-target)>4)target=Math.max(raw,previousTarget);
                }
                previousTarget=target;

                for(int side=-half;side<=half;side++){
                    int wx=(int)Math.round(centerX+nx*side),wz=(int)Math.round(centerZ+nz*side);
                    BlockPos probe=new BlockPos(wx,level.getSeaLevel(),wz);
                    if(!level.hasChunkAt(probe)){omittedRequired++;continue;}
                    int surface=naturalSurfaceY(level,wx,wz);
                    if(surface<=level.getMinBuildHeight()+1){omittedRequired++;continue;}
                    for(int y=target+1;y<=Math.min(surface+8,target+10);y++){
                        BlockPos clearPos=new BlockPos(wx,y,wz);
                        BlockState st=level.getBlockState(clearPos);
                        if(st.isAir())continue;
                        if(st.canBeReplaced()||st.is(BlockTags.LEAVES)||st.is(Blocks.SNOW)||st.is(Blocks.MOSS_CARPET)
                                ||WorldMutationGuard.isNaturalTreeLog(level,clearPos)){
                            out.add(new BuildOperation(wx,y,wz,PaletteSlot.AIR,
                                    dev.livingrealms.sim.construction.ConstructionPhase.CLEAR));
                        }else break;
                    }
                    out.add(new BuildOperation(wx,target+1,wz,PaletteSlot.AIR,
                            dev.livingrealms.sim.construction.ConstructionPhase.CLEAR));
                    PaletteSlot slot=(!rural&&Math.abs(side)==half)?PaletteSlot.FOUNDATION:PaletteSlot.PATH;
                    out.add(new BuildOperation(wx,target,wz,slot,
                            dev.livingrealms.sim.construction.ConstructionPhase.FOUNDATION));
                    if(surface<target){
                        for(int y=Math.max(surface+1,target-16);y<target;y++)
                            out.add(new BuildOperation(wx,y,wz,PaletteSlot.FOUNDATION,
                                    dev.livingrealms.sim.construction.ConstructionPhase.FOUNDATION));
                    }
                }
            }
        }
        return new RoadPlan(out,omittedRequired);
    }

    private record RoadPlan(java.util.List<BuildOperation> operations,int omittedRequired){}

    /** Top natural terrain, explicitly ignoring trees/leaves/replaceable vegetation. */
    private static int naturalSurfaceY(ServerLevel level,int x,int z){
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        int floor=level.getMinBuildHeight()+1;
        while(y>floor){BlockState st=level.getBlockState(new BlockPos(x,y,z));if(st.is(BlockTags.LOGS)||st.is(BlockTags.LEAVES)||st.canBeReplaced()){y--;continue;}break;}
        return y;
    }

    private record TerrainStats(int min,int max){}

    private static boolean nearPlayerForPresentation(ServerLevel level,Settlement settlement) {
        double x=settlement.position().x(),z=settlement.position().z();
        return level.players().stream().anyMatch(player->{
            double dx=player.getX()-x,dz=player.getZ()-z;
            return dx*dx+dz*dz<=PRESENTATION_RADIUS_SQR;
        });
    }


}
