package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounded physical Create-flavoured projection of canonical industrial sites.
 * The contraption is visual/world feedback only; canonical production remains server-side in IndustrialSite.
 */
public final class IndustrialSiteMaterializer {
    private IndustrialSiteMaterializer(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        if(level.players().isEmpty())return;
        int remaining=Math.max(8,data.state().config().constructionBlockOpsPerTick()/4);
        Map<String,IndustrialSite> canonical=new HashMap<>();
        for(IndustrialSite site:data.state().industrialSites())canonical.put(key(site.factionId(),site.settlementId(),site.kind()),site);
        for(var faction:data.state().factions()){
            if(remaining<=0)break;
            for(IndustrySitePlanner.Site planned:IndustrySitePlanner.plan(faction)){
                if(remaining<=0)break;IndustrialSite site=canonical.get(key(planned.factionId(),planned.settlementId(),planned.kind()));if(site==null||!nearPlayer(level,planned.center(),Math.max(96.0D,Math.min(512.0D,data.state().config().physicalRadiusBlocks()))))continue;
                remaining-=materialize(level,site,planned,remaining);
            }
        }
    }

    private static int materialize(ServerLevel level,IndustrialSite site,IndustrySitePlanner.Site planned,int budget){
        BlockPos center=new BlockPos((int)Math.round(planned.center().x()),level.getSeaLevel(),(int)Math.round(planned.center().z()));if(!level.hasChunkAt(center))return 0;
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX(),center.getZ())-1;if(y<=level.getMinBuildHeight()+1||y>=level.getMaxBuildHeight()-5)return 0;
        int used=0;BlockPos base=new BlockPos(center.getX(),y,center.getZ());
        Block casing=CreateBlockLookup.orElse(site.kind()==IndustryKind.FUEL_REFINERY?"copper_casing":"andesite_casing",Blocks.IRON_BLOCK);
        for(int dx=-1;dx<=1&&used<budget;dx++)for(int dz=-1;dz<=1&&used<budget;dz++)used+=place(level,base.offset(dx,0,dz),casing.defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,base.above(),machine(site.kind()).defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,base.offset(-2,1,0),CreateBlockLookup.orElse("large_cogwheel",Blocks.COPPER_BLOCK).defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,base.offset(-1,1,0),CreateBlockLookup.orElse("shaft",Blocks.IRON_BARS).defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,base.offset(-2,1,1),CreateBlockLookup.orElse("gearbox",Blocks.IRON_BLOCK).defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,base.offset(1,1,0),CreateBlockLookup.orElse("cogwheel",Blocks.COPPER_BLOCK).defaultBlockState());
        if(used>=budget)return used;
        BlockState marker=Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,site.operational()&&site.status()==IndustrialSiteStatus.ACTIVE);
        used+=place(level,base.offset(0,1,2),marker);
        if(site.level()>=2&&used<budget&&needsBasin(site.kind()))used+=place(level,base.offset(1,1,1),CreateBlockLookup.orElse("basin",Blocks.CAULDRON).defaultBlockState());
        if(site.level()>=3&&used<budget)used+=place(level,base.offset(2,1,0),secondaryMachine(site.kind()).defaultBlockState());
        return used;
    }

    private static Block machine(IndustryKind kind){return switch(kind){
        case SAWMILL,TEXTILE_MILL->CreateBlockLookup.orElse("mechanical_saw",Blocks.STONECUTTER);
        case STONEWORKS->CreateBlockLookup.orElse("millstone",Blocks.STONECUTTER);
        case COKEWORKS->CreateBlockLookup.orElse("encased_fan",Blocks.BLAST_FURNACE);
        case METALWORKS,TOOLWORKS,MUNITIONS->CreateBlockLookup.orElse("mechanical_press",Blocks.SMITHING_TABLE);
        case FUEL_REFINERY->CreateBlockLookup.orElse("fluid_tank",Blocks.COPPER_BLOCK);
        case MACHINERY_WORKS->CreateBlockLookup.orElse("mechanical_mixer",Blocks.CRAFTING_TABLE);
    };}
    private static boolean needsBasin(IndustryKind kind){return kind==IndustryKind.METALWORKS||kind==IndustryKind.TOOLWORKS||kind==IndustryKind.MACHINERY_WORKS||kind==IndustryKind.MUNITIONS||kind==IndustryKind.FUEL_REFINERY;}
    private static Block secondaryMachine(IndustryKind kind){return switch(kind){
        case FUEL_REFINERY->CreateBlockLookup.orElse("mechanical_pump",Blocks.COPPER_BLOCK);
        case MACHINERY_WORKS,MUNITIONS,METALWORKS->CreateBlockLookup.orElse("depot",Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);
        default->CreateBlockLookup.orElse("depot",Blocks.STONE_SLAB);
    };}

    private static int place(ServerLevel level,BlockPos pos,BlockState target){
        if(!level.hasChunkAt(pos)||pos.getY()<=level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()-1)return 0;BlockState current=level.getBlockState(pos);if(current.equals(target))return 0;if(current.hasBlockEntity())return 0;if(!safe(current))return 0;return level.setBlock(pos,target,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS)?1:0;
    }
    private static boolean safe(BlockState state){
        if(state.isAir()||state.canBeReplaced())return true;Block block=state.getBlock();return state.is(BlockTags.LEAVES)||state.is(BlockTags.LOGS)||block==Blocks.GRASS_BLOCK||block==Blocks.DIRT||block==Blocks.COARSE_DIRT||block==Blocks.PODZOL||block==Blocks.MYCELIUM||block==Blocks.SAND||block==Blocks.RED_SAND||block==Blocks.GRAVEL||block==Blocks.STONE||block==Blocks.DEEPSLATE||block==Blocks.DIRT_PATH||block==Blocks.STONE_BRICKS||block==Blocks.COBBLED_DEEPSLATE||block==Blocks.IRON_BLOCK||block==Blocks.COPPER_BLOCK||block==Blocks.GRAY_CONCRETE||block==Blocks.OAK_PLANKS||block==Blocks.SPRUCE_PLANKS;
    }
    private static boolean nearPlayer(ServerLevel level,SimPosition pos,double radius){double radiusSqr=radius*radius;return level.players().stream().anyMatch(p->{double dx=p.getX()-pos.x(),dz=p.getZ()-pos.z();return dx*dx+dz*dz<=radiusSqr;});}
    private static String key(long faction,long settlement,IndustryKind kind){return faction+":"+settlement+":"+kind;}
}
