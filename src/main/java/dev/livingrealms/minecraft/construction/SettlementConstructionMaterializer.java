package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.BuildApplyResult;
import dev.livingrealms.sim.construction.BuildOperation;
import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.ConstructionJob;
import dev.livingrealms.sim.construction.ConstructionQueue;
import dev.livingrealms.sim.construction.EntranceAccessPlanner;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.PhysicalDevelopmentReconciler;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureGeometryRules;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.construction.WizardTreesPlanner;
import dev.livingrealms.sim.compat.ModCompatibilityPolicy;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Projects strategic settlement blueprints into loaded Overworld chunks under a hard per-tick
 * operation budget. Canonical completion remains in the simulation save; the queue is disposable.
 */
public final class SettlementConstructionMaterializer {
    private static final double ACTIVATION_RADIUS=640.0D;
    private static final double ACTIVATION_RADIUS_SQR=ACTIVATION_RADIUS*ACTIVATION_RADIUS;
    private static final int MAX_QUEUED_JOBS=48;
    private static final int MAX_SETTLEMENTS_PER_DISCOVERY=12;
    private static final ConstructionQueue QUEUE=new ConstructionQueue();
    private static final Map<String,Settlement> JOB_OWNERS=new HashMap<>();
    /** intentKey -> earliest game day when rediscovery is allowed after a failed physical realization. */
    private static final Map<String,Long> RETRY_AFTER_DAY=new HashMap<>();
    private static int catchupTicks;
    private static long catchupSimulatedDays;
    private static int catchupIntentsPerSettlement=1;
    private static int settlementScanCursor;

    private SettlementConstructionMaterializer() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        discoverLoadedWork(level,data);
        int operationBudget=Math.max(320,data.state().config().constructionBlockOpsPerTick());
        if(catchupTicks>0){operationBudget=Math.max(operationBudget,960);catchupTicks--;}
        AuthoredBlockLedger ledger=data.authoredBlocks();
        var result=QUEUE.tick(operationBudget,(job,operation)->apply(level,job,operation,ledger));
        boolean dirty=result.applied()>0;
        long day=data.state().clock().day();
        for(String completed:result.completedJobKeys()) {
            Settlement owner=JOB_OWNERS.remove(completed);
            int split=completed.indexOf(':');
            String constructionKey=split<0?completed:completed.substring(split+1);
            RETRY_AFTER_DAY.remove(constructionKey);
            if(owner!=null && owner.markConstructionCompleted(constructionKey)) dirty=true;
        }
        for(String rejected:result.rejectedJobKeys()) {
            Settlement owner=JOB_OWNERS.remove(rejected);
            int split=rejected.indexOf(':');
            String constructionKey=split<0?rejected:rejected.substring(split+1);
            // Back off so an impossible/obstructed building cannot replan every tick.
            long backoff=owner!=null && owner.isConstructionCompleted(constructionKey)?day+1:day+3;
            RETRY_AFTER_DAY.put(constructionKey,backoff);
            dirty=true;
        }
        if(dirty) data.setDirty();
    }

    public static void requestCatchup(long simulatedDays){
        if(simulatedDays<=0)return;
        int requested=(int)Math.min(400L,40L+Math.min(120L,simulatedDays)*3L);
        catchupTicks=Math.max(catchupTicks,requested);
        catchupSimulatedDays=Math.max(catchupSimulatedDays,simulatedDays);
        // Per-settlement refinement uses PhysicalDevelopmentReconciler inside discoverLoadedWork.
        catchupIntentsPerSettlement=Math.max(2,Math.min(12,1+(int)Math.min(8L,simulatedDays/12L)));
    }

    public static void clear() {
        QUEUE.clear(); JOB_OWNERS.clear(); RETRY_AFTER_DAY.clear();
        catchupTicks=0; catchupSimulatedDays=0; catchupIntentsPerSettlement=1; settlementScanCursor=0;
    }

    private static void discoverLoadedWork(ServerLevel level, LivingRealmsSavedData data) {
        if(QUEUE.size()>=MAX_QUEUED_JOBS || level.players().isEmpty()) return;
        boolean catchingUp=catchupTicks>0;
        long day=data.state().clock().day();
        java.util.List<Faction> factions=new java.util.ArrayList<>(data.state().factions());
        java.util.List<Settlement> near=new java.util.ArrayList<>();
        java.util.Map<Long,Faction> owners=new HashMap<>();
        for(Faction faction:factions) for(Settlement settlement:faction.settlements()) {
            if(!nearPlayer(level,settlement)) continue;
            near.add(settlement);
            owners.put(settlement.id(),faction);
        }
        if(near.isEmpty()) return;
        // Fair rotation: do not let one early settlement monopolize discovery forever.
        settlementScanCursor=Math.floorMod(settlementScanCursor,near.size());
        Set<Long> queuedSettlements=new HashSet<>();
        for(ConstructionJob job:QUEUE.jobs()) queuedSettlements.add(job.intent().settlementId());
        int scanned=0;
        for(int n=0;n<near.size()&&scanned<MAX_SETTLEMENTS_PER_DISCOVERY&&QUEUE.size()<MAX_QUEUED_JOBS;n++){
            Settlement settlement=near.get(Math.floorMod(settlementScanCursor+n,near.size()));
            scanned++;
            // One active job per settlement keeps budgets fair across the realm.
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
            pending.sort(java.util.Comparator.comparingInt(ConstructionIntent::priority).reversed().thenComparing(ConstructionIntent::key));
            int enqueued=0;
            for(ConstructionIntent intent:pending) {
                if(QUEUE.size()>=MAX_QUEUED_JOBS) break;
                Long retryAfter=RETRY_AFTER_DAY.get(intent.key());
                if(retryAfter!=null && day<retryAfter) continue;
                BlockPos center=new BlockPos((int)Math.round(intent.center().x()),level.getSeaLevel(),(int)Math.round(intent.center().z()));
                // Unloaded high-priority intents must not starve later loaded work.
                if(!level.hasChunkAt(center)) continue;
                ConstructionJob job=createTerrainAwareJob(level,intent);
                if(job==null) {
                    RETRY_AFTER_DAY.put(intent.key(),day+2);
                    continue;
                }
                if(QUEUE.enqueue(job)){
                    JOB_OWNERS.put(job.key(),settlement);
                    queuedSettlements.add(settlement.id());
                    enqueued++;
                }
                if(enqueued>=allow) break;
            }
        }
        settlementScanCursor=Math.floorMod(settlementScanCursor+Math.max(1,scanned),Math.max(1,near.size()));
    }


    /**
     * Resolves a blueprint against real terrain instead of assuming the entire footprint shares
     * the height of its centre block. Roads and walls step with terrain; buildings search nearby
     * for the flattest dry pad and receive short foundation piers where the ground drops away.
     */
    private static ConstructionJob createTerrainAwareJob(ServerLevel level,ConstructionIntent original) {
        if(isWizardRole(original.role()))return createWizardUndergroundJob(level,original);
        if(original.role()==StructureRole.ROAD) {
            java.util.List<BuildOperation> ops=roadOperations(level,original);
            return ops.isEmpty()?null:new ConstructionJob(original,ops,0);
        }
        if(original.role()==StructureRole.WALL) {
            java.util.List<BuildOperation> ops=terrainFollowingOperations(level,original);
            return ops.isEmpty()?null:new ConstructionJob(original,ops,0);
        }
        BuildSite site=findBuildSite(level,original);
        if(site==null)return null;
        ConstructionIntent intent=site.intent();
        java.util.List<BuildOperation> ops=buildingOperations(level,intent,site.baseY());
        return ops.isEmpty()?null:new ConstructionJob(intent,ops,0);
    }

    private static boolean isWizardRole(StructureRole role){return role==StructureRole.WIZARD_HALL||role==StructureRole.WIZARD_GROVE||role==StructureRole.WIZARD_HOME||role==StructureRole.WIZARD_TUNNEL;}

    private static ConstructionJob createWizardUndergroundJob(ServerLevel level,ConstructionIntent original){
        int[][] offsets={{0,0},{16,0},{-16,0},{0,16},{0,-16},{28,28},{28,-28},{-28,28},{-28,-28}};BuildSite best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int[] off:offsets){int x=(int)Math.round(original.center().x()+off[0]),z=(int)Math.round(original.center().z()+off[1]);BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;int surface=naturalSurfaceY(level,x,z);int base=Math.max(level.getMinBuildHeight()+8,surface-18);if(base+10>=surface)continue;double score=-surface*4.0+Math.hypot(off[0],off[1]);if(score<bestScore){ConstructionIntent shifted=off[0]==0&&off[1]==0?original:new ConstructionIntent(original.key(),original.factionId(),original.settlementId(),original.role(),new dev.livingrealms.sim.world.SimPosition(original.center().x()+off[0],original.center().z()+off[1]),original.width(),original.depth(),original.rotationQuarterTurns(),original.priority());best=new BuildSite(shifted,base);bestScore=score;}}
        if(best==null)return null;java.util.List<BuildOperation> ops=buildingOperations(level,best.intent(),best.baseY());return ops.isEmpty()?null:new ConstructionJob(best.intent(),ops,0);
    }

    private static BuildSite findBuildSite(ServerLevel level,ConstructionIntent original) {
        int[][] offsets={{0,0},{8,0},{-8,0},{0,8},{0,-8},{12,12},{12,-12},{-12,12},{-12,-12},{20,0},{-20,0},{0,20},{0,-20}};
        BuildSite best=null;double bestScore=Double.POSITIVE_INFINITY;
        int maxSlope=switch(original.role()){case AIRFIELD -> 2;case FARM,KEEP,FACTORY -> 3;default -> 2;};
        for(int[] off:offsets){
            ConstructionIntent candidate=off[0]==0&&off[1]==0?original:new ConstructionIntent(original.key(),original.factionId(),original.settlementId(),original.role(),new dev.livingrealms.sim.world.SimPosition(original.center().x()+off[0],original.center().z()+off[1]),original.width(),original.depth(),original.rotationQuarterTurns(),original.priority());
            TerrainStats stats=terrainStats(level,candidate);if(stats==null||stats.max()-stats.min()>maxSlope)continue;
            double score=(stats.max()-stats.min())*120.0+Math.hypot(off[0],off[1]);
            if(score<bestScore){bestScore=score;best=new BuildSite(candidate,stats.max());}
        }
        return best;
    }

    private static TerrainStats terrainStats(ServerLevel level,ConstructionIntent intent){
        int turns=Math.floorMod(intent.rotationQuarterTurns(),4);int w=(turns&1)==0?intent.width():intent.depth(),d=(turns&1)==0?intent.depth():intent.width();
        int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z()),hx=w/2,hz=d/2;
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int z=-hz;z<=hz;z+=Math.max(2,d/6))for(int x=-hx;x<=hx;x+=Math.max(2,w/6)){
            int wx=cx+x,wz=cz+z;BlockPos probe=new BlockPos(wx,level.getSeaLevel(),wz);if(!level.hasChunkAt(probe))return null;
            int y=naturalSurfaceY(level,wx,wz);if(y<=level.getMinBuildHeight()+1||y>=level.getMaxBuildHeight()-18)return null;
            BlockState ground=level.getBlockState(new BlockPos(wx,y,wz));if(!ground.getFluidState().isEmpty())return null;
            min=Math.min(min,y);max=Math.max(max,y);
        }
        if(min==Integer.MAX_VALUE)return null;return new TerrainStats(min,max);
    }

    private static java.util.List<BuildOperation> buildingOperations(ServerLevel level,ConstructionIntent intent,int baseY){
        var blueprint=StructureBlueprintFactory.create(intent);int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z()),turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        java.util.List<BuildOperation> out=new java.util.ArrayList<>(blueprint.operationCount()+intent.width()*intent.depth());
        Integer doorLocalX=null,doorLocalZ=null;
        for(BlockPlacement p:blueprint.placements()){
            int rx=p.dx(),rz=p.dz();for(int i=0;i<turns;i++){int t=rx;rx=-rz;rz=t;}int wx=cx+rx,wz=cz+rz;
            out.add(new BuildOperation(wx,baseY+p.dy(),wz,p.slot(),p.phase()));
            if(p.dy()==0&&p.slot()==PaletteSlot.FOUNDATION){int surface=naturalSurfaceY(level,wx,wz);for(int y=baseY-1;y>surface&&y>=baseY-4;y--)out.add(new BuildOperation(wx,y,wz,PaletteSlot.FOUNDATION,p.phase()));}
            if(p.slot()==PaletteSlot.DOOR&&p.dy()==1){doorLocalX=p.dx();doorLocalZ=p.dz();}
        }
        if(doorLocalX!=null){
            int ox=doorLocalX,oz=doorLocalZ;
            for(int i=0;i<turns;i++){int t=ox;ox=-oz;oz=t;}
            int approachX=cx+ox,approachZ=cz+oz;
            // Sample sidewalk/street one block further outside the doorway.
            int stepX=0,stepZ=-1;for(int i=0;i<turns;i++){int t=stepX;stepX=-stepZ;stepZ=t;}
            int streetX=approachX+stepX,streetZ=approachZ+stepZ;
            int approachY=naturalSurfaceY(level,streetX,streetZ);
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


    /** Builds a straight, walkable street at ground level. Tree trunks are never treated as terrain. */
    private static java.util.List<BuildOperation> roadOperations(ServerLevel level,ConstructionIntent intent){
        int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z()),turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        int hx=intent.width()/2,hz=intent.depth()/2;
        java.util.List<BuildOperation> out=new java.util.ArrayList<>(intent.width()*intent.depth()*3);
        Integer previous=null;
        for(int lz=-hz;lz<=hz;lz++){
            int crx=0,crz=lz;for(int i=0;i<turns;i++){int t=crx;crx=-crz;crz=t;}
            int centerX=cx+crx,centerZ=cz+crz;if(!level.hasChunkAt(new BlockPos(centerX,level.getSeaLevel(),centerZ)))continue;
            int raw=naturalSurfaceY(level,centerX,centerZ);if(raw<=level.getMinBuildHeight()+1)continue;
            int target=previous==null?raw:Math.max(previous-1,Math.min(previous+1,raw));
            // Do not staircase over cliffs/mountains. A badly mismatched row is left untouched.
            if(Math.abs(raw-target)>4){previous=null;continue;}previous=target;
            for(int lx=-hx;lx<=hx;lx++){
                int rx=lx,rz=lz;for(int i=0;i<turns;i++){int t=rx;rx=-rz;rz=t;}int wx=cx+rx,wz=cz+rz;
                if(!level.hasChunkAt(new BlockPos(wx,level.getSeaLevel(),wz)))continue;
                int surface=naturalSurfaceY(level,wx,wz);if(surface<=level.getMinBuildHeight()+1||Math.abs(surface-target)>4)continue;
                // Clear only natural vegetation/tree material above the intended street surface.
                for(int y=target+1;y<=Math.min(surface+6,target+8);y++){
                    BlockState st=level.getBlockState(new BlockPos(wx,y,wz));if(st.isAir())continue;
                    if(st.is(BlockTags.LEAVES)||st.is(BlockTags.LOGS)||st.canBeReplaced())out.add(new BuildOperation(wx,y,wz,PaletteSlot.AIR,dev.livingrealms.sim.construction.ConstructionPhase.CLEAR));
                    else break;
                }
                PaletteSlot slot=Math.abs(lx)==hx?PaletteSlot.FOUNDATION:PaletteSlot.PATH;
                out.add(new BuildOperation(wx,target,wz,slot,dev.livingrealms.sim.construction.ConstructionPhase.FOUNDATION));
                // Short retaining support keeps sidewalks from floating over small dips.
                for(int y=target-1;y>surface&&y>=target-4;y--)out.add(new BuildOperation(wx,y,wz,PaletteSlot.FOUNDATION,dev.livingrealms.sim.construction.ConstructionPhase.FOUNDATION));
            }
        }
        return out;
    }

    /** Top natural terrain, explicitly ignoring trees/leaves/replaceable vegetation. */
    private static int naturalSurfaceY(ServerLevel level,int x,int z){
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        int floor=level.getMinBuildHeight()+1;
        while(y>floor){BlockState st=level.getBlockState(new BlockPos(x,y,z));if(st.is(BlockTags.LOGS)||st.is(BlockTags.LEAVES)||st.canBeReplaced()){y--;continue;}break;}
        return y;
    }

    private record TerrainStats(int min,int max){}
    private record BuildSite(ConstructionIntent intent,int baseY){}

    private static boolean nearPlayer(ServerLevel level,Settlement settlement) {
        double x=settlement.position().x(),z=settlement.position().z();
        return level.players().stream().anyMatch(player->{double dx=player.getX()-x,dz=player.getZ()-z;return dx*dx+dz*dz<=ACTIVATION_RADIUS_SQR;});
    }

    private static BuildApplyResult apply(ServerLevel level,ConstructionJob job,BuildOperation operation,AuthoredBlockLedger ledger) {
        BlockPos pos=new BlockPos(operation.x(),operation.y(),operation.z());
        if(pos.getY()<=level.getMinBuildHeight() || pos.getY()>=level.getMaxBuildHeight()-1) {
            return StructureGeometryRules.isRequiredGeometry(operation.slot(),operation.phase())
                    ? BuildApplyResult.TERMINALLY_IMPOSSIBLE : BuildApplyResult.SAFELY_IGNORED;
        }
        if(!level.hasChunkAt(pos)) return BuildApplyResult.DEFERRED_UNLOADED;

        BlockState current=level.getBlockState(pos);
        // Hard stop: unknown block entities / machines / containers are never overwritten.
        if(current.hasBlockEntity() && !ledger.isAuthored(pos.getX(),pos.getY(),pos.getZ())) {
            return StructureGeometryRules.isRequiredGeometry(operation.slot(),operation.phase())
                    ? BuildApplyResult.OBSTRUCTED_PROTECTED : BuildApplyResult.SAFELY_IGNORED;
        }

        if(operation.slot()==PaletteSlot.DOOR){
            return applyDoor(level,job,pos,current,ledger);
        }

        BlockState target=FactionBlockPalette.state(job.intent().factionId(),operation.slot());
        if(current.equals(target)) {
            ledger.record(pos.getX(),pos.getY(),pos.getZ());
            return BuildApplyResult.ALREADY_CORRECT;
        }

        if(operation.slot()==PaletteSlot.AIR) {
            if(current.isAir()) return BuildApplyResult.ALREADY_CORRECT;
            if(!safeToClear(current,ledger,pos)&&!(isWizardRole(job.intent().role())&&safeWizardExcavate(current))) {
                return StructureGeometryRules.isRequiredGeometry(operation.slot(),operation.phase())
                        ? BuildApplyResult.OBSTRUCTED_PROTECTED : BuildApplyResult.SAFELY_IGNORED;
            }
        } else if(!safeToReplace(current,target,operation.slot(),ledger,pos,job)) {
            return StructureGeometryRules.isRequiredGeometry(operation.slot(),operation.phase())
                    ? BuildApplyResult.OBSTRUCTED_PROTECTED : BuildApplyResult.SAFELY_IGNORED;
        }

        int flags=Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
        if(!level.setBlock(pos,target,flags)) return BuildApplyResult.FAILED;
        ledger.record(pos.getX(),pos.getY(),pos.getZ());
        return BuildApplyResult.APPLIED;
    }

    private static BuildApplyResult applyDoor(ServerLevel level,ConstructionJob job,BlockPos pos,BlockState current,AuthoredBlockLedger ledger){
        BlockState doorBase=FactionBlockPalette.state(job.intent().factionId(),PaletteSlot.DOOR);
        if(!(doorBase.getBlock() instanceof DoorBlock)){
            if(current.isAir())return BuildApplyResult.ALREADY_CORRECT;
            if(!safeToClear(current,ledger,pos))return BuildApplyResult.OBSTRUCTED_PROTECTED;
            if(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS))return BuildApplyResult.FAILED;
            ledger.record(pos.getX(),pos.getY(),pos.getZ());
            return BuildApplyResult.APPLIED;
        }
        Direction facing=doorFacing(job.intent().rotationQuarterTurns());
        BlockState below=level.getBlockState(pos.below());
        boolean upper=below.getBlock() instanceof DoorBlock && below.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                && below.getValue(DoorBlock.FACING)==facing;
        BlockState target=doorBase
                .setValue(DoorBlock.FACING,facing)
                .setValue(DoorBlock.HALF,upper?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN,false)
                .setValue(DoorBlock.POWERED,false);
        if(current.equals(target)){
            ledger.record(pos.getX(),pos.getY(),pos.getZ());
            return BuildApplyResult.ALREADY_CORRECT;
        }
        boolean authoredDoor=current.getBlock() instanceof DoorBlock && ledger.isAuthored(pos.getX(),pos.getY(),pos.getZ());
        if(!current.isAir()&&!current.canBeReplaced()&&!safeToClear(current,ledger,pos)&&!authoredDoor)return BuildApplyResult.OBSTRUCTED_PROTECTED;
        boolean ok=level.setBlock(pos,target,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
        if(!ok)return BuildApplyResult.FAILED;
        ledger.record(pos.getX(),pos.getY(),pos.getZ());
        if(!upper){
            BlockPos up=pos.above();
            BlockState upCur=level.getBlockState(up);
            if(upCur.isAir()||upCur.canBeReplaced()||safeToClear(upCur,ledger,up)||(upCur.getBlock() instanceof DoorBlock && ledger.isAuthored(up.getX(),up.getY(),up.getZ()))){
                BlockState upperState=doorBase.setValue(DoorBlock.FACING,facing).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER).setValue(DoorBlock.OPEN,false).setValue(DoorBlock.POWERED,false);
                if(level.setBlock(up,upperState,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS))ledger.record(up.getX(),up.getY(),up.getZ());
            }
        }
        return BuildApplyResult.APPLIED;
    }

    /** Player approaches the front (-Z local) looking toward +Z before rotation → SOUTH at rot 0. */
    private static Direction doorFacing(int quarterTurns){
        Direction[] order={Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST};
        return order[Math.floorMod(quarterTurns,4)];
    }

    private static boolean safeToClear(BlockState state,AuthoredBlockLedger ledger,BlockPos pos) {
        if(state.canBeReplaced()) return true;
        if(ledger.isAuthored(pos.getX(),pos.getY(),pos.getZ())) return true;
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.is(Blocks.SNOW) || state.is(Blocks.VINE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING);
    }

    /** True for materials Living Realms itself places — used only with provenance or active job footprint rebuild. */
    private static boolean isLivingRealmsPaletteMaterial(BlockState state) {
        Block block=state.getBlock();
        return block==Blocks.STONE_BRICKS || block==Blocks.COBBLED_DEEPSLATE || block==Blocks.SANDSTONE
                || block==Blocks.TUFF_BRICKS || block==Blocks.OAK_PLANKS || block==Blocks.SPRUCE_PLANKS
                || block==Blocks.BIRCH_PLANKS || block==Blocks.DARK_OAK_PLANKS || block==Blocks.SMOOTH_SANDSTONE
                || block==Blocks.BRICKS || block==Blocks.OAK_LOG || block==Blocks.SPRUCE_LOG
                || block==Blocks.STRIPPED_BIRCH_LOG || block==Blocks.DARK_OAK_LOG || block==Blocks.DEEPSLATE_TILES
                || block==Blocks.CUT_SANDSTONE || block==Blocks.DARK_PRISMARINE || block==Blocks.GLASS
                || block==Blocks.OAK_FENCE || block==Blocks.SPRUCE_FENCE || block==Blocks.BIRCH_FENCE
                || block==Blocks.DARK_OAK_FENCE || block==Blocks.DIRT_PATH || block==Blocks.FARMLAND
                || block==Blocks.WHEAT || block==Blocks.GLOWSTONE || block==Blocks.IRON_BLOCK
                || block==Blocks.COPPER_BLOCK || block==Blocks.GRAY_CONCRETE || block==Blocks.BARREL || block==Blocks.REDSTONE_TORCH
                || block==Blocks.OAK_DOOR || block==Blocks.SPRUCE_DOOR || block==Blocks.BIRCH_DOOR || block==Blocks.DARK_OAK_DOOR
                || block==CreateBlockLookup.orElse("copper_casing",Blocks.IRON_BLOCK)
                || block==CreateBlockLookup.orElse("andesite_casing",Blocks.COPPER_BLOCK);
    }

    private static boolean safeWizardExcavate(BlockState state){Block b=state.getBlock();return state.is(BlockTags.BASE_STONE_OVERWORLD)||b==Blocks.DIRT||b==Blocks.COARSE_DIRT||b==Blocks.ROOTED_DIRT||b==Blocks.GRAVEL||b==Blocks.CLAY||b==Blocks.MUD||b==Blocks.SAND||b==Blocks.RED_SAND;}

    private static boolean oldForeignConstructionMaterial(BlockState state){
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if(id==null||id.getNamespace().equals("minecraft")||id.getNamespace().equals("create"))return false;
        return ModCompatibilityPolicy.find(id.getNamespace()).map(e->e.usableByLivingWorld()&&(e.category()==ModCompatibilityPolicy.Category.BUILDING||e.category()==ModCompatibilityPolicy.Category.CONTENT)).orElse(false);
    }

    private static boolean insideActiveJobFootprint(ConstructionJob job,BlockPos pos){
        ConstructionIntent intent=job.intent();
        int turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        int w=(turns&1)==0?intent.width():intent.depth();
        int d=(turns&1)==0?intent.depth():intent.width();
        int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z());
        int margin=2;
        return Math.abs(pos.getX()-cx)<=w/2+margin && Math.abs(pos.getZ()-cz)<=d/2+margin;
    }

    private static boolean naturalTerrain(BlockState state){
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE) || state.is(Blocks.TERRACOTTA) || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean safeToReplace(BlockState state,BlockState target,PaletteSlot slot,AuthoredBlockLedger ledger,BlockPos pos,ConstructionJob job) {
        if(state.isAir() || state.canBeReplaced()) return true;
        if(state.equals(target)) return true;
        if(ledger.isAuthored(pos.getX(),pos.getY(),pos.getZ())) return true;
        if(state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) return true;
        // Rebuild of Living Realms/legacy LR materials is allowed only inside the active job footprint.
        if(insideActiveJobFootprint(job,pos) && (isLivingRealmsPaletteMaterial(state) || oldForeignConstructionMaterial(state))) return true;
        if(slot==PaletteSlot.FOUNDATION || slot==PaletteSlot.PATH || slot==PaletteSlot.FARMLAND || slot==PaletteSlot.RUNWAY) {
            return naturalTerrain(state);
        }
        // Player/foreign structures made of the same vanilla blocks are never treated as free real estate.
        return false;
    }
}
