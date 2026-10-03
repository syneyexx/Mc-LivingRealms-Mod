package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.civilization.HiddenCache;
import dev.livingrealms.sim.civilization.RuinSite;
import dev.livingrealms.sim.civilization.PirateHideout;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bounded physical projection for persistent historical sites.
 * Canonical RuinSite/HiddenCache records remain authoritative; this class never invents loot/state.
 */
public final class HistoricalSiteMaterializer {
    private static final double ACTIVATION_RADIUS=384.0D;
    private static final double ACTIVATION_RADIUS_SQR=ACTIVATION_RADIUS*ACTIVATION_RADIUS;
    private static final int MAX_SITES_PER_TICK=8;
    private static final Map<Long,Long> CACHE_AT_POS=new HashMap<>();
    private static final Map<Long,Long> RUIN_AT_POS=new HashMap<>();
    private static final Map<Long,Long> HIDEOUT_AT_POS=new HashMap<>();

    private HistoricalSiteMaterializer(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        if(level.players().isEmpty())return;
        int budget=MAX_SITES_PER_TICK;
        for(HiddenCache cache:data.state().hiddenCaches()){
            if(budget<=0)break;
            if(!nearPlayer(level,cache.position().x(),cache.position().z()))continue;
            BlockPos pos=findCacheSite(level,cache);
            if(pos==null)continue;
            if(cache.recovered()){
                CACHE_AT_POS.remove(pos.asLong());
                if(level.getBlockState(pos).is(Blocks.BARREL))level.removeBlock(pos,false);
                continue;
            }
            if(level.getBlockState(pos).is(Blocks.BARREL)||placeCache(level,pos)){
                CACHE_AT_POS.put(pos.asLong(),cache.id());budget--;
            }
        }
        for(PirateHideout hideout:data.state().pirateHideouts()){
            if(budget<=0)break;if(!hideout.active()||!nearPlayer(level,hideout.position().x(),hideout.position().z()))continue;if(materializeHideout(level,hideout))budget--;
        }
        for(RuinSite ruin:data.state().ruinSites()){
            if(budget<=0)break;
            if(!ruin.active()||!nearPlayer(level,ruin.position().x(),ruin.position().z()))continue;
            if(materializeRuin(level,ruin)){budget--;}
        }
    }

    public static OptionalLong cacheIdAt(BlockPos pos){Long id=pos==null?null:CACHE_AT_POS.get(pos.asLong());return id==null?OptionalLong.empty():OptionalLong.of(id);}
    public static OptionalLong ruinIdAt(BlockPos pos){Long id=pos==null?null:RUIN_AT_POS.get(pos.asLong());return id==null?OptionalLong.empty():OptionalLong.of(id);}
    public static OptionalLong pirateHideoutIdAt(BlockPos pos){Long id=pos==null?null:HIDEOUT_AT_POS.get(pos.asLong());return id==null?OptionalLong.empty():OptionalLong.of(id);}
    public static void forget(BlockPos pos){if(pos!=null){CACHE_AT_POS.remove(pos.asLong());RUIN_AT_POS.remove(pos.asLong());HIDEOUT_AT_POS.remove(pos.asLong());}}
    public static void clear(){CACHE_AT_POS.clear();RUIN_AT_POS.clear();HIDEOUT_AT_POS.clear();}

    private static boolean nearPlayer(ServerLevel level,double x,double z){return level.players().stream().anyMatch(p->{double dx=p.getX()-x,dz=p.getZ()-z;return dx*dx+dz*dz<=ACTIVATION_RADIUS_SQR;});}

    private static BlockPos findCacheSite(ServerLevel level,HiddenCache cache){
        int baseX=(int)Math.floor(cache.position().x()),baseZ=(int)Math.floor(cache.position().z());
        int[][] offsets={{0,0},{3,1},{-3,-1},{1,-4},{-1,4},{5,-3},{-5,3},{6,4},{-6,-4}};
        int rotation=Math.floorMod(Long.hashCode(cache.id()),offsets.length);
        for(int i=0;i<offsets.length;i++){
            int[] off=offsets[(i+rotation)%offsets.length];int x=baseX+off[0],z=baseZ+off[1];
            BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
            if(surface<=level.getMinBuildHeight()+2||surface>=level.getMaxBuildHeight()-3)continue;
            BlockPos pos=new BlockPos(x,surface-1,z);BlockState current=level.getBlockState(pos),cover=level.getBlockState(pos.above());
            if(current.is(Blocks.BARREL))return pos;
            if(!naturalExcavatable(current)||current.hasBlockEntity()||cover.hasBlockEntity())continue;
            if(!naturalCover(cover))continue;
            return pos;
        }
        return null;
    }

    private static boolean placeCache(ServerLevel level,BlockPos pos){
        BlockState current=level.getBlockState(pos);if(!naturalExcavatable(current)||current.hasBlockEntity())return false;
        return level.setBlock(pos,Blocks.BARREL.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
    }

    private static boolean materializeRuin(ServerLevel level,RuinSite ruin){
        int cx=(int)Math.floor(ruin.position().x()),cz=(int)Math.floor(ruin.position().z());
        int[][] offsets={{0,0},{1,0},{-1,0},{0,1},{0,-1},{2,1},{-2,-1},{1,-2},{-1,2},{3,0},{0,3}};
        int target=Math.max(3,Math.min(offsets.length,(int)Math.round(3+ruin.preservation()*8)));
        boolean touched=false;int placed=0;
        for(int i=0;i<offsets.length&&placed<target;i++){
            int idx=Math.floorMod(i+Long.hashCode(ruin.id()),offsets.length);int x=cx+offsets[idx][0],z=cz+offsets[idx][1];
            BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;BlockPos ground=new BlockPos(x,surface,z),pos=ground.above();
            if(surface<=level.getMinBuildHeight()+1||surface>=level.getMaxBuildHeight()-5)continue;
            BlockState groundState=level.getBlockState(ground),current=level.getBlockState(pos);
            if(!naturalGround(groundState)||groundState.hasBlockEntity()||current.hasBlockEntity())continue;
            BlockState desired=ruinBlock(ruin.id(),i);
            if(current.equals(desired)||isRuinMaterial(current)){RUIN_AT_POS.put(pos.asLong(),ruin.id());placed++;continue;}
            if(!current.isAir()&&!current.canBeReplaced())continue;
            if(level.setBlock(pos,desired,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS)){RUIN_AT_POS.put(pos.asLong(),ruin.id());placed++;touched=true;}
        }
        return touched||placed>0;
    }

    private static boolean materializeHideout(ServerLevel level,PirateHideout hideout){
        int cx=(int)Math.floor(hideout.position().x()),cz=(int)Math.floor(hideout.position().z());int[][] offsets={{0,0},{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,-2},{2,-2},{-2,2}};boolean touched=false;int placed=0;
        for(int i=0;i<offsets.length;i++){int x=cx+offsets[i][0],z=cz+offsets[i][1];BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;if(surface<=level.getMinBuildHeight()+1||surface>=level.getMaxBuildHeight()-4)continue;BlockPos ground=new BlockPos(x,surface,z),pos=ground.above();BlockState g=level.getBlockState(ground),cur=level.getBlockState(pos);if(!naturalGround(g)||g.hasBlockEntity()||cur.hasBlockEntity())continue;BlockState desired=(i==0?Blocks.CAMPFIRE.defaultBlockState():(i==1?Blocks.BARREL.defaultBlockState():Blocks.OAK_FENCE.defaultBlockState()));if(cur.equals(desired)||cur.is(Blocks.OAK_FENCE)||cur.is(Blocks.CAMPFIRE)||cur.is(Blocks.BARREL)){HIDEOUT_AT_POS.put(pos.asLong(),hideout.id());placed++;continue;}if(!cur.isAir()&&!cur.canBeReplaced())continue;if(level.setBlock(pos,desired,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS)){HIDEOUT_AT_POS.put(pos.asLong(),hideout.id());placed++;touched=true;}}
        return touched||placed>0;
    }

    private static BlockState ruinBlock(long id,int index){int pick=Math.floorMod(Long.hashCode(id*31L+index*17L),4);return switch(pick){case 0->Blocks.MOSSY_COBBLESTONE.defaultBlockState();case 1->Blocks.CRACKED_STONE_BRICKS.defaultBlockState();case 2->Blocks.COBBLESTONE.defaultBlockState();default->Blocks.MOSSY_STONE_BRICKS.defaultBlockState();};}
    private static boolean isRuinMaterial(BlockState s){return s.is(Blocks.MOSSY_COBBLESTONE)||s.is(Blocks.CRACKED_STONE_BRICKS)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.MOSSY_STONE_BRICKS);}
    private static boolean naturalCover(BlockState s){return naturalGround(s)||s.is(BlockTags.LEAVES)||s.is(Blocks.SNOW);}
    private static boolean naturalExcavatable(BlockState s){return naturalGround(s)||s.is(BlockTags.BASE_STONE_OVERWORLD);}
    private static boolean naturalGround(BlockState s){return s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT)||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.PODZOL)||s.is(Blocks.MYCELIUM)||s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)||s.is(Blocks.GRAVEL)||s.is(Blocks.STONE)||s.is(Blocks.DEEPSLATE)||s.is(Blocks.TUFF)||s.is(BlockTags.BASE_STONE_OVERWORLD);}
}
