package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.compat.create.CreateIndustryAdapter;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounded physical Create-flavoured projection of canonical industrial sites.
 * The contraption is visual/world feedback only; canonical production remains server-side in IndustrialSite.
 * Create access is isolated behind {@link CreateIndustryAdapter}.
 */
public final class IndustrialSiteMaterializer {
    private static final int MAX_SCANNED_PER_TICK=32;
    private static int cursor;
    private IndustrialSiteMaterializer(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        int remaining=Math.max(8,data.state().config().constructionBlockOpsPerTick()/4);
        Map<String,IndustrialSite> canonical=new HashMap<>();
        for(IndustrialSite site:data.state().industrialSites())canonical.put(key(site.factionId(),site.settlementId(),site.kind()),site);
        java.util.List<IndustrySitePlanner.Site> plannedSites=new java.util.ArrayList<>();
        for(var faction:data.state().factions())plannedSites.addAll(IndustrySitePlanner.plan(faction));
        if(plannedSites.isEmpty())return;
        cursor=Math.floorMod(cursor,plannedSites.size());
        AuthoredBlockLedger ledger=data.authoredBlocks();
        int scanned=0;
        for(int n=0;n<plannedSites.size()&&scanned<MAX_SCANNED_PER_TICK&&remaining>0;n++){
            IndustrySitePlanner.Site planned=plannedSites.get(Math.floorMod(cursor+n,plannedSites.size()));
            scanned++;
            IndustrialSite site=canonical.get(key(planned.factionId(),planned.settlementId(),planned.kind()));
            if(site==null)continue;
            // materialize() refuses unloaded chunks. Player proximity is presentation-only.
            remaining-=materialize(level,ledger,site,planned,remaining);
        }
        cursor=Math.floorMod(cursor+Math.max(1,scanned),plannedSites.size());
    }

    public static void clear(){cursor=0;}

    private static int materialize(ServerLevel level,AuthoredBlockLedger ledger,IndustrialSite site,IndustrySitePlanner.Site planned,int budget){
        BlockPos center=new BlockPos((int)Math.round(planned.center().x()),level.getSeaLevel(),(int)Math.round(planned.center().z()));if(!level.hasChunkAt(center))return 0;
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX(),center.getZ())-1;if(y<=level.getMinBuildHeight()+1||y>=level.getMaxBuildHeight()-5)return 0;
        int used=0;BlockPos base=new BlockPos(center.getX(),y,center.getZ());
        Block casing=CreateIndustryAdapter.INSTANCE.resolveBlock(site.kind()==IndustryKind.FUEL_REFINERY?"copper_casing":"andesite_casing",Blocks.IRON_BLOCK);
        for(int dx=-1;dx<=1&&used<budget;dx++)for(int dz=-1;dz<=1&&used<budget;dz++)used+=place(level,ledger,base.offset(dx,0,dz),casing.defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,ledger,base.above(),machine(site.kind()).defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,ledger,base.offset(-2,1,0),CreateIndustryAdapter.INSTANCE.resolveBlock("large_cogwheel",Blocks.COPPER_BLOCK).defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,ledger,base.offset(-1,1,0),CreateIndustryAdapter.INSTANCE.resolveBlock("shaft",Blocks.IRON_BARS).defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,ledger,base.offset(-2,1,1),CreateIndustryAdapter.INSTANCE.resolveBlock("gearbox",Blocks.IRON_BLOCK).defaultBlockState());
        if(used>=budget)return used;
        used+=place(level,ledger,base.offset(1,1,0),CreateIndustryAdapter.INSTANCE.resolveBlock("cogwheel",Blocks.COPPER_BLOCK).defaultBlockState());
        if(used>=budget)return used;
        // Status beacon: active=lit lamp, idle=unlit lamp, starved=red wool, damaged=orange wool, offline/broken=black wool.
        BlockState marker=statusMarker(site);
        used+=place(level,ledger,base.offset(0,1,2),marker);
        boolean presentationNear=nearPlayer(level,planned.center(),
                Math.max(96.0D,Math.min(512.0D,512.0D)));
        if(presentationNear&&site.status()==IndustrialSiteStatus.ACTIVE&&site.operational()&&used<budget){
            // Particle cue is presentation LOD only; block existence is chunk-driven above.
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,base.getX()+.5,base.getY()+3.2,base.getZ()+.5,1,0.15,0.2,0.15,0.01);
        }else if(presentationNear&&(site.status()==IndustrialSiteStatus.STARVED||site.status()==IndustrialSiteStatus.DAMAGED)&&used<budget){
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,base.getX()+.5,base.getY()+2.4,base.getZ()+.5,2,0.2,0.15,0.2,0.0);
        }
        if(site.level()>=2&&used<budget&&needsBasin(site.kind()))used+=place(level,ledger,base.offset(1,1,1),CreateIndustryAdapter.INSTANCE.resolveBlock("basin",Blocks.CAULDRON).defaultBlockState());
        if(site.level()>=3&&used<budget)used+=place(level,ledger,base.offset(2,1,0),secondaryMachine(site.kind()).defaultBlockState());
        return used;
    }

    private static BlockState statusMarker(IndustrialSite site){
        return switch(site.status()){
            case ACTIVE -> Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,site.operational());
            case REPAIRING -> Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,false);
            case STARVED -> Blocks.RED_WOOL.defaultBlockState();
            case DAMAGED -> Blocks.ORANGE_WOOL.defaultBlockState();
            case OFFLINE -> Blocks.BLACK_WOOL.defaultBlockState();
        };
    }

    private static Block machine(IndustryKind kind){return switch(kind){
        case SAWMILL,TEXTILE_MILL->CreateIndustryAdapter.INSTANCE.resolveBlock("mechanical_saw",Blocks.STONECUTTER);
        case STONEWORKS->CreateIndustryAdapter.INSTANCE.resolveBlock("millstone",Blocks.STONECUTTER);
        case COKEWORKS->CreateIndustryAdapter.INSTANCE.resolveBlock("encased_fan",Blocks.BLAST_FURNACE);
        case METALWORKS,TOOLWORKS,MUNITIONS->CreateIndustryAdapter.INSTANCE.resolveBlock("mechanical_press",Blocks.SMITHING_TABLE);
        case FUEL_REFINERY->CreateIndustryAdapter.INSTANCE.resolveBlock("fluid_tank",Blocks.COPPER_BLOCK);
        case MACHINERY_WORKS->CreateIndustryAdapter.INSTANCE.resolveBlock("mechanical_mixer",Blocks.CRAFTING_TABLE);
    };}
    private static boolean needsBasin(IndustryKind kind){return kind==IndustryKind.METALWORKS||kind==IndustryKind.TOOLWORKS||kind==IndustryKind.MACHINERY_WORKS||kind==IndustryKind.MUNITIONS||kind==IndustryKind.FUEL_REFINERY;}
    private static Block secondaryMachine(IndustryKind kind){return switch(kind){
        case FUEL_REFINERY->CreateIndustryAdapter.INSTANCE.resolveBlock("mechanical_pump",Blocks.COPPER_BLOCK);
        case MACHINERY_WORKS,MUNITIONS,METALWORKS->CreateIndustryAdapter.INSTANCE.resolveBlock("depot",Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);
        default->CreateIndustryAdapter.INSTANCE.resolveBlock("depot",Blocks.STONE_SLAB);
    };}

    private static int place(ServerLevel level,AuthoredBlockLedger ledger,BlockPos pos,BlockState target){
        if(!level.hasChunkAt(pos)||pos.getY()<=level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()-1)return 0;
        BlockState current=level.getBlockState(pos);
        if(current.equals(target))return 0; // satisfy geometry without claiming unknown blocks
        // Only natural terrain / leaves / LR industrial blocks — never iron/copper/planks/stone bricks of unknown origin.
        return WorldMutationGuard.trySetAuthored(level,pos,target,ledger,AuthoredOwnerType.INDUSTRIAL_SITE,true,false)?1:0;
    }
    private static boolean nearPlayer(ServerLevel level,SimPosition pos,double radius){double radiusSqr=radius*radius;return level.players().stream().anyMatch(p->{double dx=p.getX()-pos.x(),dz=p.getZ()-pos.z();return dx*dx+dz*dz<=radiusSqr;});}
    private static String key(long faction,long settlement,IndustryKind kind){return faction+":"+settlement+":"+kind;}
}
