package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.transport.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Opportunistically projects canonical road/rail corridors into already-loaded chunks. */
public final class TransportNetworkMaterializer {
    private static final double ACTIVATION_RADIUS=192.0D;
    private TransportNetworkMaterializer(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        if(level.players().isEmpty())return;int remaining=Math.max(16,data.state().config().constructionBlockOpsPerTick()/2);
        List<SimPosition> observers=level.players().stream().map(p->new SimPosition(p.getX(),p.getZ())).toList();
        TerrainCorridorPlanner.TerrainSample terrain=sampler(level,data.authoredBlocks());
        AuthoredBlockLedger ledger=data.authoredBlocks();
        for(TransportRoute route:data.state().routes()){
            if(remaining<=0)break;if(!route.operational()||(route.mode()!=TransportMode.ROAD&&route.mode()!=TransportMode.RAIL))continue;
            var from=data.state().findSettlement(route.fromSettlementId()).orElse(null);
            var to=data.state().findSettlement(route.toSettlementId()).orElse(null);if(from==null||to==null)continue;
            var points=RouteProjectionPlanner.plan(route,from.position(),to.position(),observers,ACTIVATION_RADIUS,remaining,terrain);
            for(var point:points){if(remaining<=0)break;remaining-=applyPoint(level,ledger,point,remaining);}
        }
    }

    private static TerrainCorridorPlanner.TerrainSample sampler(ServerLevel level,AuthoredBlockLedger ledger){
        return new TerrainCorridorPlanner.TerrainSample(){
            @Override public int height(int x,int z){
                BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);
                if(!level.hasChunkAt(probe))return Integer.MIN_VALUE;
                return naturalGroundY(level,x,z);
            }
            @Override public boolean water(int x,int z){
                BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);
                if(!level.hasChunkAt(probe))return false;
                int y=naturalGroundY(level,x,z);
                return !level.getBlockState(new BlockPos(x,y,z)).getFluidState().isEmpty();
            }
            @Override public boolean blocked(int x,int z){
                BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);
                if(!level.hasChunkAt(probe))return false;
                int y=naturalGroundY(level,x,z);
                BlockPos ground=new BlockPos(x,y,z);
                BlockState st=level.getBlockState(ground);
                if(st.hasBlockEntity())return true;
                AuthoredOwnerType owner=ledger.ownerType(x,y,z);
                // Reroute around settlement structures / industry; own intercity route is fine.
                if(owner!=null && owner!=AuthoredOwnerType.INTERCITY_ROUTE && owner!=AuthoredOwnerType.SETTLEMENT_ROAD) return true;
                // Unknown structural shells are obstacles — never cut through player builds.
                if(owner==null && !WorldMutationGuard.isNaturalTerrain(st) && !st.isAir() && st.getFluidState().isEmpty()
                        && !st.is(BlockTags.LEAVES) && !st.canBeReplaced() && !st.is(Blocks.DIRT_PATH) && !st.is(Blocks.RAIL)) {
                    return true;
                }
                return false;
            }
        };
    }

    private static int applyPoint(ServerLevel level,AuthoredBlockLedger ledger,RouteProjectionPlanner.RoutePoint point,int budget){
        int used=0;if(point.mode()==TransportMode.RAIL)return applyRail(level,ledger,point,budget);
        int nx=point.dz()==0?0:Integer.signum(point.dz()),nz=point.dx()==0?0:-Integer.signum(point.dx());if(nx==0&&nz==0)nx=1;
        int[] best=bestCorridorCenter(level,ledger,point,nx,nz);if(best==null)return 0;
        for(int side=-2;side<=2&&used<budget;side++){int x=best[0]+nx*side,z=best[1]+nz*side;used+=Math.abs(side)==2?placeSidewalk(level,ledger,x,z):placeRoad(level,ledger,x,z);}
        if(used<budget)used+=maybeBridge(level,ledger,best[0],best[1],point,budget-used);
        return used;
    }

    private static int maybeBridge(ServerLevel level,AuthoredBlockLedger ledger,int x,int z,RouteProjectionPlanner.RoutePoint point,int budget){
        int surface=naturalGroundY(level,x,z);BlockState current=level.getBlockState(new BlockPos(x,surface,z));
        if(current.getFluidState().isEmpty())return 0;
        int used=0;int deckY=Math.max(level.getSeaLevel(),surface);
        for(int i=-1;i<=1&&used<budget;i++){
            int bx=x+point.dx()*i,bz=z+point.dz()*i;
            BlockPos deck=new BlockPos(bx,deckY,bz);
            if(!level.hasChunkAt(deck))continue;
            if(WorldMutationGuard.trySetAuthored(level,deck,Blocks.STONE_BRICKS.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false))used++;
            for(int y=deckY-1;y>=level.getMinBuildHeight()+1&&y>=deckY-8&&used<budget;y--){
                BlockPos pillar=new BlockPos(bx,y,bz);BlockState st=level.getBlockState(pillar);
                if(st.isSolidRender(level,pillar)||st.hasBlockEntity())break;
                if(WorldMutationGuard.trySetAuthored(level,pillar,Blocks.STONE_BRICKS.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false))used++;
            }
        }
        return used;
    }

    private static int[] bestCorridorCenter(ServerLevel level,AuthoredBlockLedger ledger,RouteProjectionPlanner.RoutePoint point,int nx,int nz){
        int bestX=0,bestZ=0;double bestScore=Double.POSITIVE_INFINITY;boolean found=false;
        for(int shift:new int[]{0,4,-4,8,-8,12,-12,18,-18,24,-24,32,-32,48,-48}){
            int x=point.x()+nx*shift,z=point.z()+nz*shift;int y=naturalGroundY(level,x,z);if(y<=level.getMinBuildHeight()+1)continue;
            int fx=x+point.dx()*3,fz=z+point.dz()*3,bx=x-point.dx()*3,bz=z-point.dz()*3;
            int yf=naturalGroundY(level,fx,fz),yb=naturalGroundY(level,bx,bz);if(yf<=level.getMinBuildHeight()+1||yb<=level.getMinBuildHeight()+1)continue;
            int grade=Math.max(Math.abs(y-yf),Math.abs(y-yb));if(grade>3)continue;
            BlockState center=level.getBlockState(new BlockPos(x,y,z));
            if(center.hasBlockEntity())continue;
            AuthoredOwnerType owner=ledger.ownerType(x,y,z);
            if(owner!=null && owner!=AuthoredOwnerType.INTERCITY_ROUTE && owner!=AuthoredOwnerType.SETTLEMENT_ROAD)continue;
            if(owner==null && !WorldMutationGuard.isNaturalTerrain(center) && center.getFluidState().isEmpty()
                    && !center.is(BlockTags.LEAVES) && !center.canBeReplaced() && !center.is(Blocks.DIRT_PATH))continue;
            double vegetation=center.is(BlockTags.LEAVES)?8:(WorldMutationGuard.isNaturalTreeLog(level,new BlockPos(x,y,z))?24:0);
            double water=center.getFluidState().isEmpty()?0:8;
            double score=grade*20.0+Math.abs(shift)*1.5+vegetation+water;if(score<bestScore){bestScore=score;bestX=x;bestZ=z;found=true;}
        }
        return found?new int[]{bestX,bestZ}:null;
    }

    private static int placeSidewalk(ServerLevel level,AuthoredBlockLedger ledger,int x,int z){
        int surface=naturalGroundY(level,x,z);BlockPos ground=new BlockPos(x,surface,z);if(!level.hasChunkAt(ground))return 0;
        BlockState current=level.getBlockState(ground);if(current.hasBlockEntity())return 0;
        if(!current.getFluidState().isEmpty()){
            BlockPos deck=new BlockPos(x,Math.max(level.getSeaLevel(),surface),z);
            return WorldMutationGuard.trySetAuthored(level,deck,Blocks.STONE_BRICKS.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false)?1:0;
        }
        if(!clearNaturalVegetation(level,ledger,ground.above(),6))return 0;
        return WorldMutationGuard.trySetAuthored(level,ground,Blocks.STONE_BRICKS.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false)?1:0;
    }

    private static int placeRoad(ServerLevel level,AuthoredBlockLedger ledger,int x,int z){
        int surface=naturalGroundY(level,x,z);BlockPos ground=new BlockPos(x,surface,z);if(!level.hasChunkAt(ground))return 0;
        BlockState current=level.getBlockState(ground);if(current.hasBlockEntity())return 0;
        if(!current.getFluidState().isEmpty()){
            int y=Math.max(level.getSeaLevel(),surface);BlockPos deck=new BlockPos(x,y,z);
            return WorldMutationGuard.trySetAuthored(level,deck,Blocks.STONE_BRICKS.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false)?1:0;
        }
        if(!clearNaturalVegetation(level,ledger,ground.above(),6))return 0;
        return WorldMutationGuard.trySetAuthored(level,ground,Blocks.DIRT_PATH.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false)?1:0;
    }

    private static int applyRail(ServerLevel level,AuthoredBlockLedger ledger,RouteProjectionPlanner.RoutePoint point,int budget){
        if(budget<=0)return 0;int x=point.x(),z=point.z();int surface=naturalGroundY(level,x,z);BlockPos surfacePos=new BlockPos(x,surface,z);if(!level.hasChunkAt(surfacePos))return 0;
        BlockState surfaceState=level.getBlockState(surfacePos);if(surfaceState.hasBlockEntity())return 0;
        boolean water=!surfaceState.getFluidState().isEmpty();int baseY=water?Math.max(level.getSeaLevel(),surface):surface;BlockPos base=new BlockPos(x,baseY,z);int used=0;
        BlockState support=water?Blocks.STONE_BRICKS.defaultBlockState():Blocks.GRAVEL.defaultBlockState();
        if(!level.getBlockState(base).equals(support)){
            if(WorldMutationGuard.trySetAuthored(level,base,support,ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false))used++;
            else return 0;
        }
        if(used>=budget)return used;BlockPos railPos=base.above();BlockState there=level.getBlockState(railPos);
        if(there.hasBlockEntity())return used;
        // Never overwrite unknown player rails — only air/replaceable or LR-authored rail.
        AuthoredOwnerType railOwner=ledger.ownerType(railPos.getX(),railPos.getY(),railPos.getZ());
        if(!there.isAir()&&!there.canBeReplaced()&&!(there.is(Blocks.RAIL)&&railOwner==AuthoredOwnerType.INTERCITY_ROUTE))return used;
        RailShape shape=Math.abs(point.dx())>=Math.abs(point.dz())?RailShape.EAST_WEST:RailShape.NORTH_SOUTH;
        BlockState rail=Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,shape);
        if(!there.equals(rail)&&WorldMutationGuard.trySetAuthored(level,railPos,rail,ledger,AuthoredOwnerType.INTERCITY_ROUTE,false,false))used++;
        if(water&&used<budget&&Math.floorMod(x*31+z,11)==0){
            for(int y=baseY-1;y>=level.getMinBuildHeight()+1&&y>=baseY-10&&used<budget;y--){
                BlockPos pillar=new BlockPos(x,y,z);BlockState st=level.getBlockState(pillar);
                if(st.isSolidRender(level,pillar)||st.hasBlockEntity())break;
                if(WorldMutationGuard.trySetAuthored(level,pillar,Blocks.STONE_BRICKS.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false))used++;
            }
        }
        return used;
    }

    private static boolean clearNaturalVegetation(ServerLevel level,AuthoredBlockLedger ledger,BlockPos start,int height){
        for(int i=0;i<height;i++){
            BlockPos p=start.above(i);BlockState st=level.getBlockState(p);if(st.isAir())continue;
            if(st.canBeReplaced()||st.is(BlockTags.LEAVES)){
                if(!WorldMutationGuard.trySetAuthored(level,p,Blocks.AIR.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,false,false))return false;
                continue;
            }
            if(WorldMutationGuard.isNaturalTreeLog(level,p)){
                // Prefer reroute: refuse corridor clear through trees rather than felling unknown trunks.
                return false;
            }
            return false;
        }
        return true;
    }

    private static int naturalGroundY(ServerLevel level,int x,int z){
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;int floor=level.getMinBuildHeight()+1;
        while(y>floor){
            BlockState st=level.getBlockState(new BlockPos(x,y,z));
            if(st.is(BlockTags.LEAVES)||st.canBeReplaced()||WorldMutationGuard.isNaturalTreeLog(level,new BlockPos(x,y,z))){y--;continue;}
            break;
        }
        return y;
    }
}
