package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.compat.CompatibleContentRuntime;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.PaletteSlot;
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
    /** Drop of this many blocks (or more) triggers a bridge deck instead of staircasing into a pit. */
    private static final int BRIDGE_GAP_THRESHOLD=3;
    private TransportNetworkMaterializer(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        if(level.players().isEmpty())return;int remaining=Math.max(16,data.state().config().constructionBlockOpsPerTick()/2);
        List<SimPosition> observers=level.players().stream().map(p->new SimPosition(p.getX(),p.getZ())).toList();
        TerrainCorridorPlanner.TerrainSample terrain=sampler(level,data.authoredBlocks());
        AuthoredBlockLedger ledger=data.authoredBlocks();
        for(TransportRoute route:data.state().routes()){
            if(remaining<=0)break;
            // ROAD/RAIL keep full carriageways; CARAVAN becomes a narrow countryside dirt path.
            if(!route.operational()||(route.mode()!=TransportMode.ROAD&&route.mode()!=TransportMode.RAIL&&route.mode()!=TransportMode.CARAVAN))continue;
            var from=data.state().findSettlement(route.fromSettlementId()).orElse(null);
            var to=data.state().findSettlement(route.toSettlementId()).orElse(null);if(from==null||to==null)continue;
            var points=RouteProjectionPlanner.plan(route,from.position(),to.position(),observers,ACTIVATION_RADIUS,remaining,terrain);
            boolean rural=route.mode()==TransportMode.CARAVAN
                    || from.tier().ordinal()<=dev.livingrealms.sim.faction.Settlement.Tier.VILLAGE.ordinal()
                    || to.tier().ordinal()<=dev.livingrealms.sim.faction.Settlement.Tier.VILLAGE.ordinal();
            for(var point:points){if(remaining<=0)break;remaining-=applyPoint(level,ledger,point,remaining,rural);}
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
                return !level.getBlockState(new BlockPos(x,y,z)).getFluidState().isEmpty()
                        ||!level.getFluidState(new BlockPos(x,y+1,z)).isEmpty();
            }
            @Override public boolean blocked(int x,int z){
                BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);
                if(!level.hasChunkAt(probe))return false;
                int y=naturalGroundY(level,x,z);
                BlockPos ground=new BlockPos(x,y,z);
                BlockState st=level.getBlockState(ground);
                if(st.hasBlockEntity())return true;
                AuthoredOwnerType owner=ledger.ownerType(x,y,z);
                if(owner!=null && owner!=AuthoredOwnerType.INTERCITY_ROUTE && owner!=AuthoredOwnerType.SETTLEMENT_ROAD) return true;
                if(owner==null && !WorldMutationGuard.isNaturalTerrain(st) && !st.isAir() && st.getFluidState().isEmpty()
                        && !st.is(BlockTags.LEAVES) && !st.canBeReplaced() && !st.is(Blocks.DIRT_PATH) && !st.is(Blocks.RAIL)
                        && !isRoadSurface(st)) {
                    return true;
                }
                return false;
            }
        };
    }

    private static int applyPoint(ServerLevel level,AuthoredBlockLedger ledger,RouteProjectionPlanner.RoutePoint point,int budget,boolean rural){
        int used=0;if(point.mode()==TransportMode.RAIL)return applyRail(level,ledger,point,budget);
        int nx=point.dz()==0?0:Integer.signum(point.dz()),nz=point.dx()==0?0:-Integer.signum(point.dx());if(nx==0&&nz==0)nx=1;
        int[] best=bestCorridorCenter(level,ledger,point,nx,nz);if(best==null)return 0;
        int deckY=corridorDeckY(level,best[0],best[1],point);
        // Rural / caravan: single dirt path, no sidewalk fences. Urban: full carriageway + curb.
        int half=rural?0:2;
        for(int side=-half;side<=half&&used<budget;side++){
            int x=best[0]+nx*side,z=best[1]+nz*side;
            if(!rural&&Math.abs(side)==2)used+=placeSidewalk(level,ledger,x,z,deckY);
            else used+=placeRoad(level,ledger,x,z,deckY,side==0,rural);
        }
        if(used<budget)used+=maybeBridge(level,ledger,best[0],best[1],point,deckY,budget-used,rural);
        if(!rural&&used<budget&&Math.floorMod(best[0]*17+best[1],29)==0){
            used+=placeLamp(level,ledger,best[0]+nx*2,best[1]+nz*2,deckY);
        }
        return used;
    }

    private static int corridorDeckY(ServerLevel level,int x,int z,RouteProjectionPlanner.RoutePoint point){
        int surface=naturalGroundY(level,x,z);
        int ahead=naturalGroundY(level,x+point.dx()*3,z+point.dz()*3);
        int behind=naturalGroundY(level,x-point.dx()*3,z-point.dz()*3);
        boolean water=!level.getBlockState(new BlockPos(x,surface,z)).getFluidState().isEmpty()
                ||!level.getFluidState(new BlockPos(x,surface+1,z)).isEmpty();
        int gap=Math.max(Math.abs(surface-ahead),Math.abs(surface-behind));
        if(water)return Math.max(level.getSeaLevel(),Math.max(surface,Math.max(ahead,behind)));
        if(gap>=BRIDGE_GAP_THRESHOLD)return Math.max(surface,Math.max(ahead,behind));
        return surface;
    }

    private static int maybeBridge(ServerLevel level,AuthoredBlockLedger ledger,int x,int z,RouteProjectionPlanner.RoutePoint point,int deckY,int budget,boolean rural){
        int surface=naturalGroundY(level,x,z);
        boolean water=!level.getBlockState(new BlockPos(x,surface,z)).getFluidState().isEmpty()
                ||!level.getFluidState(new BlockPos(x,surface+1,z)).isEmpty()
                ||surface<level.getSeaLevel();
        int ahead=naturalGroundY(level,x+point.dx()*3,z+point.dz()*3);
        int behind=naturalGroundY(level,x-point.dx()*3,z-point.dz()*3);
        int gap=Math.max(Math.abs(surface-ahead),Math.abs(surface-behind));
        if(!water&&gap<BRIDGE_GAP_THRESHOLD&&deckY<=surface)return 0;
        int used=0;
        BlockState deck=rural?Blocks.SPRUCE_PLANKS.defaultBlockState():bridgeDeckState();
        BlockState pillar=rural?Blocks.OAK_LOG.defaultBlockState():Blocks.STONE_BRICKS.defaultBlockState();
        int half=rural?1:2;
        for(int i=-half;i<=half&&used<budget;i++){
            int bx=x+point.dx()*i,bz=z+point.dz()*i;
            BlockPos deckPos=new BlockPos(bx,deckY,bz);
            if(!level.hasChunkAt(deckPos))continue;
            clearNaturalVegetation(level,ledger,deckPos.above(),8,true);
            if(WorldMutationGuard.trySetAuthored(level,deckPos,deck,ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false))used++;
            if(Math.abs(i)==half){
                BlockPos rail=deckPos.above();
                BlockState railing=rural?Blocks.OAK_FENCE.defaultBlockState():Blocks.STONE_BRICK_WALL.defaultBlockState();
                if(WorldMutationGuard.trySetAuthored(level,rail,railing,ledger,AuthoredOwnerType.INTERCITY_ROUTE,false,false))used++;
            }
            int ground=naturalGroundY(level,bx,bz);
            for(int y=deckY-1;y>=level.getMinBuildHeight()+1&&y>=Math.min(ground,deckY-16)&&used<budget;y--){
                BlockPos pillarPos=new BlockPos(bx,y,bz);BlockState st=level.getBlockState(pillarPos);
                if(st.isSolidRender(level,pillarPos)||st.hasBlockEntity())break;
                if(WorldMutationGuard.trySetAuthored(level,pillarPos,pillar,ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false))used++;
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
            int grade=Math.max(Math.abs(y-yf),Math.abs(y-yb));
            // Prefer bridging gaps rather than rejecting the corridor entirely.
            if(grade>8)continue;
            BlockState center=level.getBlockState(new BlockPos(x,y,z));
            if(center.hasBlockEntity())continue;
            AuthoredOwnerType owner=ledger.ownerType(x,y,z);
            if(owner!=null && owner!=AuthoredOwnerType.INTERCITY_ROUTE && owner!=AuthoredOwnerType.SETTLEMENT_ROAD)continue;
            if(owner==null && !WorldMutationGuard.isNaturalTerrain(center) && center.getFluidState().isEmpty()
                    && !center.is(BlockTags.LEAVES) && !center.canBeReplaced() && !center.is(Blocks.DIRT_PATH) && !isRoadSurface(center))continue;
            double vegetation=center.is(BlockTags.LEAVES)?4:(WorldMutationGuard.isNaturalTreeLog(level,new BlockPos(x,y,z))?12:0);
            double water=center.getFluidState().isEmpty()?0:6;
            double score=Math.min(grade,BRIDGE_GAP_THRESHOLD)*20.0+Math.max(0,grade-BRIDGE_GAP_THRESHOLD)*4.0+Math.abs(shift)*1.5+vegetation+water;
            if(score<bestScore){bestScore=score;bestX=x;bestZ=z;found=true;}
        }
        return found?new int[]{bestX,bestZ}:null;
    }

    private static int placeSidewalk(ServerLevel level,AuthoredBlockLedger ledger,int x,int z,int deckY){
        BlockPos ground=new BlockPos(x,deckY,z);if(!level.hasChunkAt(ground))return 0;
        if(!clearNaturalVegetation(level,ledger,ground.above(),8,true))return 0;
        BlockState curb=CompatibleContentRuntime.decorativeBlock(1L,PaletteSlot.FOUNDATION).orElse(Blocks.STONE_BRICKS.defaultBlockState());
        return WorldMutationGuard.trySetAuthored(level,ground,curb,ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false)?1:0;
    }

    private static int placeRoad(ServerLevel level,AuthoredBlockLedger ledger,int x,int z,int deckY,boolean center,boolean rural){
        BlockPos ground=new BlockPos(x,deckY,z);if(!level.hasChunkAt(ground))return 0;
        if(!clearNaturalVegetation(level,ledger,ground.above(),8,true))return 0;
        // Countryside: plain dirt path. Urban: packed mud / Macaw paving carriageway.
        BlockState surface=rural
                ?Blocks.DIRT_PATH.defaultBlockState()
                :CompatibleContentRuntime.decorativeBlock(center?2L:3L,PaletteSlot.PATH)
                    .orElse(center?Blocks.PACKED_MUD.defaultBlockState():Blocks.DIRT_PATH.defaultBlockState());
        int used=WorldMutationGuard.trySetAuthored(level,ground,surface,ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false)?1:0;
        // Keep grass/dirt from regenerating into the carriageway for a few blocks of clearance.
        for(int dy=1;dy<=2;dy++){
            BlockPos clear=ground.above(dy);
            BlockState st=level.getBlockState(clear);
            if(st.isAir())continue;
            if(st.canBeReplaced()||st.is(BlockTags.LEAVES)||st.is(Blocks.SNOW)||st.is(Blocks.MOSS_CARPET))
                if(WorldMutationGuard.trySetAuthored(level,clear,Blocks.AIR.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,false,false))used++;
        }
        return used;
    }

    private static int placeLamp(ServerLevel level,AuthoredBlockLedger ledger,int x,int z,int deckY){
        BlockPos base=new BlockPos(x,deckY+1,z);if(!level.hasChunkAt(base))return 0;
        int used=0;
        BlockState post=CompatibleContentRuntime.decorativeBlock(5L,PaletteSlot.FENCE).orElse(Blocks.STONE_BRICK_WALL.defaultBlockState());
        BlockState light=CompatibleContentRuntime.decorativeBlock(5L,PaletteSlot.LIGHT).orElse(Blocks.LANTERN.defaultBlockState());
        if(WorldMutationGuard.trySetAuthored(level,base,post,ledger,AuthoredOwnerType.INTERCITY_ROUTE,false,false))used++;
        if(WorldMutationGuard.trySetAuthored(level,base.above(),light,ledger,AuthoredOwnerType.INTERCITY_ROUTE,false,false))used++;
        return used;
    }

    private static int applyRail(ServerLevel level,AuthoredBlockLedger ledger,RouteProjectionPlanner.RoutePoint point,int budget){
        if(budget<=0)return 0;int x=point.x(),z=point.z();int surface=naturalGroundY(level,x,z);BlockPos surfacePos=new BlockPos(x,surface,z);if(!level.hasChunkAt(surfacePos))return 0;
        BlockState surfaceState=level.getBlockState(surfacePos);if(surfaceState.hasBlockEntity())return 0;
        boolean water=!surfaceState.getFluidState().isEmpty()||!level.getFluidState(surfacePos.above()).isEmpty();
        int baseY=water?Math.max(level.getSeaLevel(),surface):surface;BlockPos base=new BlockPos(x,baseY,z);int used=0;
        BlockState support=water?Blocks.STONE_BRICKS.defaultBlockState():Blocks.GRAVEL.defaultBlockState();
        if(!level.getBlockState(base).equals(support)){
            if(WorldMutationGuard.trySetAuthored(level,base,support,ledger,AuthoredOwnerType.INTERCITY_ROUTE,true,false))used++;
            else return 0;
        }
        if(used>=budget)return used;BlockPos railPos=base.above();BlockState there=level.getBlockState(railPos);
        if(there.hasBlockEntity())return used;
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

    private static boolean clearNaturalVegetation(ServerLevel level,AuthoredBlockLedger ledger,BlockPos start,int height,boolean fellTrees){
        for(int i=0;i<height;i++){
            BlockPos p=start.above(i);BlockState st=level.getBlockState(p);if(st.isAir())continue;
            if(st.canBeReplaced()||st.is(BlockTags.LEAVES)||st.is(Blocks.SNOW)||st.is(Blocks.MOSS_CARPET)||st.is(Blocks.VINE)){
                if(!WorldMutationGuard.trySetAuthored(level,p,Blocks.AIR.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,false,false))return false;
                continue;
            }
            if(WorldMutationGuard.isNaturalTreeLog(level,p)){
                if(!fellTrees)return false;
                if(!WorldMutationGuard.trySetAuthored(level,p,Blocks.AIR.defaultBlockState(),ledger,AuthoredOwnerType.INTERCITY_ROUTE,false,true))return false;
                continue;
            }
            // Existing road surfaces are fine to overwrite; other solids block the corridor.
            if(isRoadSurface(st))continue;
            return false;
        }
        return true;
    }

    private static boolean isRoadSurface(BlockState st){
        return st.is(Blocks.DIRT_PATH)||st.is(Blocks.PACKED_MUD)||st.is(Blocks.GRAVEL)||st.is(Blocks.COBBLESTONE)
                ||st.is(Blocks.STONE_BRICKS)||st.is(Blocks.SMOOTH_STONE)||st.is(Blocks.ANDESITE);
    }

    private static BlockState bridgeDeckState(){
        return CompatibleContentRuntime.decorativeBlock(7L,PaletteSlot.FOUNDATION).orElse(Blocks.STONE_BRICKS.defaultBlockState());
    }

    private static int naturalGroundY(ServerLevel level,int x,int z){
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;int floor=level.getMinBuildHeight()+1;
        while(y>floor){
            BlockState st=level.getBlockState(new BlockPos(x,y,z));
            if(st.is(BlockTags.LEAVES)||st.canBeReplaced()||WorldMutationGuard.isNaturalTreeLog(level,new BlockPos(x,y,z))||st.is(Blocks.SNOW)){y--;continue;}
            break;
        }
        return y;
    }
}
