package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.civilization.HiddenCache;
import dev.livingrealms.sim.civilization.RuinSite;
import dev.livingrealms.sim.civilization.PirateHideout;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
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
 * Only Living Realms-authored historical blocks may later be automatically removed or mutated.
 */
public final class HistoricalSiteMaterializer {
    private static final int MAX_SITES_PER_TICK=8;
    private static final int MAX_SCAN_PER_CATEGORY=32;
    private static final Map<Long,Long> CACHE_AT_POS=new HashMap<>();
    private static final Map<Long,Long> RUIN_AT_POS=new HashMap<>();
    private static final Map<Long,Long> HIDEOUT_AT_POS=new HashMap<>();
    private static int cacheCursor,hideoutCursor,ruinCursor,legendCursor;

    private HistoricalSiteMaterializer(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        AuthoredBlockLedger ledger=data.authoredBlocks();
        int budget=MAX_SITES_PER_TICK;
        int used=processCaches(level,data,ledger,Math.min(2,budget));budget-=used;
        used=processHideouts(level,data,ledger,Math.min(2,budget));budget-=used;
        used=processRuins(level,data,ledger,Math.min(2,budget));budget-=used;
        processLegends(level,data,ledger,Math.min(2,budget));
    }

    private static int processCaches(ServerLevel level,LivingRealmsSavedData data,AuthoredBlockLedger ledger,int quota){
        var items=data.state().hiddenCaches();if(quota<=0||items.isEmpty())return 0;
        cacheCursor=Math.floorMod(cacheCursor,items.size());int scanned=0,used=0;
        for(int n=0;n<items.size()&&scanned<MAX_SCAN_PER_CATEGORY&&used<quota;n++){
            HiddenCache cache=items.get(Math.floorMod(cacheCursor+n,items.size()));scanned++;
            if(!loadedAt(level,cache.position().x(),cache.position().z()))continue;
            BlockPos pos=findCacheSite(level,ledger,cache);if(pos==null)continue;
            if(cache.recovered()){
                CACHE_AT_POS.remove(pos.asLong());
                if(ledger.ownerType(pos.getX(),pos.getY(),pos.getZ())==AuthoredOwnerType.HIDDEN_CACHE
                        &&level.getBlockState(pos).is(Blocks.BARREL)){
                    level.removeBlock(pos,false);ledger.forget(pos.getX(),pos.getY(),pos.getZ());
                }
                used++;continue;
            }
            if(isAuthoredCache(level,ledger,pos)||placeCache(level,ledger,pos)){
                CACHE_AT_POS.put(pos.asLong(),cache.id());used++;
            }
        }
        cacheCursor=Math.floorMod(cacheCursor+Math.max(1,scanned),items.size());return used;
    }

    private static int processHideouts(ServerLevel level,LivingRealmsSavedData data,AuthoredBlockLedger ledger,int quota){
        var items=data.state().pirateHideouts();if(quota<=0||items.isEmpty())return 0;
        hideoutCursor=Math.floorMod(hideoutCursor,items.size());int scanned=0,used=0;
        for(int n=0;n<items.size()&&scanned<MAX_SCAN_PER_CATEGORY&&used<quota;n++){
            PirateHideout hideout=items.get(Math.floorMod(hideoutCursor+n,items.size()));scanned++;
            if(!hideout.active()||!loadedAt(level,hideout.position().x(),hideout.position().z()))continue;
            if(materializeHideout(level,ledger,hideout))used++;
        }
        hideoutCursor=Math.floorMod(hideoutCursor+Math.max(1,scanned),items.size());return used;
    }

    private static int processRuins(ServerLevel level,LivingRealmsSavedData data,AuthoredBlockLedger ledger,int quota){
        var items=data.state().ruinSites();if(quota<=0||items.isEmpty())return 0;
        ruinCursor=Math.floorMod(ruinCursor,items.size());int scanned=0,used=0;
        for(int n=0;n<items.size()&&scanned<MAX_SCAN_PER_CATEGORY&&used<quota;n++){
            RuinSite ruin=items.get(Math.floorMod(ruinCursor+n,items.size()));scanned++;
            if(!ruin.active()||!loadedAt(level,ruin.position().x(),ruin.position().z()))continue;
            if(materializeRuin(level,ledger,ruin))used++;
        }
        ruinCursor=Math.floorMod(ruinCursor+Math.max(1,scanned),items.size());return used;
    }

    private static int processLegends(ServerLevel level,LivingRealmsSavedData data,AuthoredBlockLedger ledger,int quota){
        var items=data.state().legends();if(quota<=0||items.isEmpty())return 0;
        legendCursor=Math.floorMod(legendCursor,items.size());int scanned=0,used=0;
        for(int n=0;n<items.size()&&scanned<MAX_SCAN_PER_CATEGORY&&used<quota;n++){
            var legend=items.get(Math.floorMod(legendCursor+n,items.size()));scanned++;
            if(legend.settlementId()<=0)continue;
            var settlement=data.state().findSettlement(legend.settlementId()).orElse(null);
            if(settlement==null||!loadedAt(level,settlement.position().x(),settlement.position().z()))continue;
            if(materializeLegendTrace(level,ledger,legend,settlement.position().x(),settlement.position().z()))used++;
        }
        legendCursor=Math.floorMod(legendCursor+Math.max(1,scanned),items.size());return used;
    }

    private static boolean loadedAt(ServerLevel level,double x,double z){
        return level.hasChunkAt(new BlockPos((int)Math.floor(x),level.getSeaLevel(),(int)Math.floor(z)));
    }

    public static OptionalLong cacheIdAt(BlockPos pos){Long id=pos==null?null:CACHE_AT_POS.get(pos.asLong());return id==null?OptionalLong.empty():OptionalLong.of(id);}
    public static OptionalLong ruinIdAt(BlockPos pos){Long id=pos==null?null:RUIN_AT_POS.get(pos.asLong());return id==null?OptionalLong.empty():OptionalLong.of(id);}
    public static OptionalLong pirateHideoutIdAt(BlockPos pos){Long id=pos==null?null:HIDEOUT_AT_POS.get(pos.asLong());return id==null?OptionalLong.empty():OptionalLong.of(id);}
    public static void forget(BlockPos pos){if(pos!=null){CACHE_AT_POS.remove(pos.asLong());RUIN_AT_POS.remove(pos.asLong());HIDEOUT_AT_POS.remove(pos.asLong());}}
    public static void clear(){
        CACHE_AT_POS.clear();RUIN_AT_POS.clear();HIDEOUT_AT_POS.clear();
        cacheCursor=hideoutCursor=ruinCursor=legendCursor=0;
    }


    private static BlockPos findCacheSite(ServerLevel level,AuthoredBlockLedger ledger,HiddenCache cache){
        int baseX=(int)Math.floor(cache.position().x()),baseZ=(int)Math.floor(cache.position().z());
        int[][] offsets={{0,0},{3,1},{-3,-1},{1,-4},{-1,4},{5,-3},{-5,3},{6,4},{-6,-4}};
        int rotation=Math.floorMod(Long.hashCode(cache.id()),offsets.length);
        for(int i=0;i<offsets.length;i++){
            int[] off=offsets[(i+rotation)%offsets.length];int x=baseX+off[0],z=baseZ+off[1];
            BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
            if(surface<=level.getMinBuildHeight()+2||surface>=level.getMaxBuildHeight()-3)continue;
            BlockPos pos=new BlockPos(x,surface-1,z);BlockState current=level.getBlockState(pos),cover=level.getBlockState(pos.above());
            if(isAuthoredCache(level,ledger,pos))return pos;
            // Player barrels at the expected site must never be adopted as LR caches.
            if(current.is(Blocks.BARREL))continue;
            if(!naturalExcavatable(current)||current.hasBlockEntity()||cover.hasBlockEntity())continue;
            if(!naturalCover(cover))continue;
            return pos;
        }
        return null;
    }

    private static boolean isAuthoredCache(ServerLevel level,AuthoredBlockLedger ledger,BlockPos pos){
        return level.getBlockState(pos).is(Blocks.BARREL)
                && ledger.ownerType(pos.getX(),pos.getY(),pos.getZ())==AuthoredOwnerType.HIDDEN_CACHE;
    }

    private static boolean placeCache(ServerLevel level,AuthoredBlockLedger ledger,BlockPos pos){
        BlockState current=level.getBlockState(pos);if(!naturalExcavatable(current)||current.hasBlockEntity())return false;
        return WorldMutationGuard.trySetAuthored(level,pos,Blocks.BARREL.defaultBlockState(),ledger,AuthoredOwnerType.HIDDEN_CACHE,true,false);
    }

    private static boolean materializeRuin(ServerLevel level,AuthoredBlockLedger ledger,RuinSite ruin){
        int cx=(int)Math.floor(ruin.position().x()),cz=(int)Math.floor(ruin.position().z());
        int[][] offsets={{0,0},{1,0},{-1,0},{0,1},{0,-1},{2,1},{-2,-1},{1,-2},{-1,2},{3,0},{0,3}};
        int target=Math.max(3,Math.min(offsets.length,(int)Math.round(3+ruin.preservation()*8)));
        boolean touched=false;int placed=0;
        boolean wartime=ruin.cause()!=null&&ruin.cause().startsWith("war:");
        boolean abandoned=ruin.cause()!=null&&ruin.cause().startsWith("abandoned_raid:");
        for(int i=0;i<offsets.length&&placed<target;i++){
            int idx=Math.floorMod(i+Long.hashCode(ruin.id()),offsets.length);int x=cx+offsets[idx][0],z=cz+offsets[idx][1];
            BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;BlockPos ground=new BlockPos(x,surface,z),pos=ground.above();
            if(surface<=level.getMinBuildHeight()+1||surface>=level.getMaxBuildHeight()-5)continue;
            BlockState groundState=level.getBlockState(ground),current=level.getBlockState(pos);
            if(!naturalGround(groundState)||groundState.hasBlockEntity()||current.hasBlockEntity())continue;
            BlockState desired=ruinBlock(ruin.id(),i,wartime,abandoned);
            if(ledger.ownerType(pos.getX(),pos.getY(),pos.getZ())==AuthoredOwnerType.HISTORICAL_RUIN
                    && (current.equals(desired)||isRuinMaterial(current))){
                RUIN_AT_POS.put(pos.asLong(),ruin.id());placed++;continue;
            }
            // Player cobble/mossy bricks at the site satisfy nothing for ownership.
            if(!current.isAir()&&!current.canBeReplaced())continue;
            if(WorldMutationGuard.trySetAuthored(level,pos,desired,ledger,AuthoredOwnerType.HISTORICAL_RUIN,false,false)){
                RUIN_AT_POS.put(pos.asLong(),ruin.id());placed++;touched=true;
            }
        }
        return touched||placed>0;
    }

    private static boolean materializeHideout(ServerLevel level,AuthoredBlockLedger ledger,PirateHideout hideout){
        int cx=(int)Math.floor(hideout.position().x()),cz=(int)Math.floor(hideout.position().z());
        int[][] offsets={{0,0},{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,-2},{2,-2},{-2,2}};
        boolean touched=false;int placed=0;
        for(int i=0;i<offsets.length;i++){
            int x=cx+offsets[i][0],z=cz+offsets[i][1];
            BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
            if(surface<=level.getMinBuildHeight()+1||surface>=level.getMaxBuildHeight()-4)continue;
            BlockPos ground=new BlockPos(x,surface,z),pos=ground.above();
            BlockState g=level.getBlockState(ground),cur=level.getBlockState(pos);
            if(!naturalGround(g)||g.hasBlockEntity()||cur.hasBlockEntity())continue;
            BlockState desired=(i==0?Blocks.CAMPFIRE.defaultBlockState():(i==1?Blocks.BARREL.defaultBlockState():Blocks.OAK_FENCE.defaultBlockState()));
            if(ledger.ownerType(pos.getX(),pos.getY(),pos.getZ())==AuthoredOwnerType.PIRATE_HIDEOUT
                    && (cur.equals(desired)||cur.is(Blocks.OAK_FENCE)||cur.is(Blocks.CAMPFIRE)||cur.is(Blocks.BARREL))){
                HIDEOUT_AT_POS.put(pos.asLong(),hideout.id());placed++;continue;
            }
            if(!cur.isAir()&&!cur.canBeReplaced())continue;
            if(WorldMutationGuard.trySetAuthored(level,pos,desired,ledger,AuthoredOwnerType.PIRATE_HIDEOUT,false,false)){
                HIDEOUT_AT_POS.put(pos.asLong(),hideout.id());placed++;touched=true;
            }
        }
        return touched||placed>0;
    }

    private static boolean materializeLegendTrace(ServerLevel level,AuthoredBlockLedger ledger,
                                                 dev.livingrealms.sim.civilization.LegendRecord legend,double sx,double sz){
        String key=legend.subjectKey()==null?"":legend.subjectKey();
        TraceKind kind;
        if(key.startsWith("grave:"))kind=TraceKind.GRAVE;
        else if(key.startsWith("battle_marker:")||key.startsWith("raid_scar:"))kind=TraceKind.BATTLE_MARKER;
        else if(legend.monumented()||key.startsWith("capture_memorial:")||key.contains("monument"))kind=TraceKind.MEMORIAL;
        else return false;
        int salt=Long.hashCode(legend.id());
        int cx=(int)Math.floor(sx)+(kind==TraceKind.GRAVE?6:kind==TraceKind.BATTLE_MARKER?-5:3);
        int cz=(int)Math.floor(sz)+(kind==TraceKind.GRAVE?-4:kind==TraceKind.BATTLE_MARKER?7:-2);
        cx+=Math.floorMod(salt,5)-2;cz+=Math.floorMod(salt/7,5)-2;
        BlockPos probe=new BlockPos(cx,level.getSeaLevel(),cz);if(!level.hasChunkAt(probe))return false;
        int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,cx,cz)-1;
        if(surface<=level.getMinBuildHeight()+1||surface>=level.getMaxBuildHeight()-6)return false;
        BlockPos ground=new BlockPos(cx,surface,cz),pos=ground.above();
        BlockState groundState=level.getBlockState(ground),current=level.getBlockState(pos);
        if(!naturalGround(groundState)||groundState.hasBlockEntity()||current.hasBlockEntity())return false;
        BlockState desired=switch(kind){
            case GRAVE->Blocks.STONE_BRICK_WALL.defaultBlockState();
            case BATTLE_MARKER->Blocks.COBBLESTONE_WALL.defaultBlockState();
            case MEMORIAL->Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        };
        if(ledger.ownerType(pos.getX(),pos.getY(),pos.getZ())==AuthoredOwnerType.HISTORICAL_RUIN
                &&(current.equals(desired)||isRuinMaterial(current)||current.is(Blocks.STONE_BRICK_WALL)||current.is(Blocks.COBBLESTONE_WALL)||current.is(Blocks.CHISELED_STONE_BRICKS))){
            RUIN_AT_POS.put(pos.asLong(),legend.id());
            return true;
        }
        if(!current.isAir()&&!current.canBeReplaced())return false;
        if(!WorldMutationGuard.trySetAuthored(level,pos,desired,ledger,AuthoredOwnerType.HISTORICAL_RUIN,false,false))return false;
        RUIN_AT_POS.put(pos.asLong(),legend.id());
        if(kind==TraceKind.MEMORIAL||kind==TraceKind.BATTLE_MARKER){
            BlockPos top=pos.above();
            if(level.getBlockState(top).isAir()||level.getBlockState(top).canBeReplaced()){
                BlockState accent=kind==TraceKind.MEMORIAL?Blocks.TORCH.defaultBlockState():Blocks.WHITE_BANNER.defaultBlockState();
                WorldMutationGuard.trySetAuthored(level,top,accent,ledger,AuthoredOwnerType.HISTORICAL_RUIN,false,false);
            }
        }
        return true;
    }

    private enum TraceKind{GRAVE,BATTLE_MARKER,MEMORIAL}

    private static BlockState ruinBlock(long id,int index,boolean wartime,boolean abandoned){
        int pick=Math.floorMod(Long.hashCode(id*31L+index*17L),4);
        if(wartime)return switch(pick){case 0->Blocks.CRACKED_STONE_BRICKS.defaultBlockState();case 1->Blocks.COBBLESTONE.defaultBlockState();case 2->Blocks.BLACKSTONE.defaultBlockState();default->Blocks.MOSSY_COBBLESTONE.defaultBlockState();};
        if(abandoned)return switch(pick){case 0->Blocks.OAK_FENCE.defaultBlockState();case 1->Blocks.COBBLESTONE.defaultBlockState();case 2->Blocks.MOSSY_COBBLESTONE.defaultBlockState();default->Blocks.CAMPFIRE.defaultBlockState();};
        return switch(pick){case 0->Blocks.MOSSY_COBBLESTONE.defaultBlockState();case 1->Blocks.CRACKED_STONE_BRICKS.defaultBlockState();case 2->Blocks.COBBLESTONE.defaultBlockState();default->Blocks.MOSSY_STONE_BRICKS.defaultBlockState();};
    }
    private static boolean isRuinMaterial(BlockState s){return s.is(Blocks.MOSSY_COBBLESTONE)||s.is(Blocks.CRACKED_STONE_BRICKS)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.MOSSY_STONE_BRICKS)||s.is(Blocks.BLACKSTONE)||s.is(Blocks.OAK_FENCE)||s.is(Blocks.CAMPFIRE)||s.is(Blocks.STONE_BRICK_WALL)||s.is(Blocks.COBBLESTONE_WALL)||s.is(Blocks.CHISELED_STONE_BRICKS);}
    private static boolean naturalCover(BlockState s){return naturalGround(s)||s.is(BlockTags.LEAVES)||s.is(Blocks.SNOW);}
    private static boolean naturalExcavatable(BlockState s){return naturalGround(s)||s.is(BlockTags.BASE_STONE_OVERWORLD);}
    private static boolean naturalGround(BlockState s){return WorldMutationGuard.isNaturalTerrain(s)||s.is(Blocks.TUFF)||s.is(BlockTags.BASE_STONE_OVERWORLD);}
}
