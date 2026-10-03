package dev.livingrealms.minecraft.entity;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.materialization.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reconciles aggregate wildlife cohorts with loaded terrestrial, aquatic, amphibious and flying projections. */
public final class WildlifeMaterializer {
    private WildlifeMaterializer() {}

    public static void tick(MinecraftServer server, LivingRealmsSavedData data) {
        ServerLevel level = server.overworld();
        List<SimPosition> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (player.serverLevel() == level) players.add(new SimPosition(player.getX(), player.getZ()));
        var state = data.state();
        var groups = state.regions().stream().flatMap(region -> region.populations().stream()).toList();
        MaterializationPlanner planner = new MaterializationPlanner(RuntimeProjectionPolicy.wildlife(state.config()));
        List<MaterializationRequest> requests = planner.plan(groups, state.species(), players);
        ProjectionPlan delta = MaterializationReconciler.reconcile(requests, WildlifeProjectionIndex.snapshots());
        for (ProjectionDespawn removal : delta.despawns()) {LivingRealmsAnimalEntity entity = WildlifeProjectionIndex.byEntityKey(removal.entityKey());if (entity != null && !entity.isRemoved()) entity.dematerialize();}
        for (ProjectionSpawn spawn : delta.spawns()) {
            PopulationGroup group = state.findPopulationGroup(spawn.populationGroupId()).orElse(null);SpeciesDefinition species=state.species().get(spawn.speciesId());if (group == null||species==null) continue;
            SimPosition horizontal = SpawnPlacementPlanner.position(state.seed(), group, spawn.slot());int x=(int)Math.floor(horizontal.x()),z=(int)Math.floor(horizontal.z());BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;
            SpeciesMobility mobility=SpeciesMobilityResolver.resolve(species);BlockPos spawnPos=resolveSpawn(level,x,z,mobility,spawn.slot());if(spawnPos==null)continue;
            LivingRealmsAnimalEntity entity = ModEntities.WILDLIFE.get().create(level);if (entity == null) continue;entity.initializeProjection(group.id(), group.speciesId(), spawn.slot(), group.hunger());entity.moveTo(spawnPos.getX()+.5,spawnPos.getY()+.2,spawnPos.getZ()+.5,level.random.nextFloat()*360,0);
            if ((mobility==SpeciesMobility.FLYING||level.noCollision(entity)) && level.addFreshEntity(entity)) continue;
            LivingRealms.LOGGER.debug("Could not materialize {} group {} slot {} as {}", group.speciesId(), group.id(), spawn.slot(),mobility);
        }
    }

    private static BlockPos resolveSpawn(ServerLevel level,int x,int z,SpeciesMobility mobility,int slot){
        if(mobility==SpeciesMobility.AQUATIC){BlockPos water=findWater(level,x,z);return water;}
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        if(mobility==SpeciesMobility.FLYING)y+=18+Math.floorMod(slot*7,20);
        if(mobility==SpeciesMobility.AMPHIBIOUS){BlockPos water=findWater(level,x,z);if(water!=null&&Math.floorMod(slot,2)==0)return water;}
        return new BlockPos(x,y,z);
    }
    private static BlockPos findWater(ServerLevel level,int originX,int originZ){
        for(int radius=0;radius<=24;radius+=4)for(int dz=-radius;dz<=radius;dz+=Math.max(1,radius==0?1:radius))for(int dx=-radius;dx<=radius;dx+=Math.max(1,radius==0?1:radius)){
            int x=originX+dx,z=originZ+dz;BlockPos column=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(column))continue;int surface=Math.min(level.getMaxBuildHeight()-2,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));for(int y=surface;y>=Math.max(level.getMinBuildHeight()+2,surface-32);y--){BlockPos p=new BlockPos(x,y,z);if(level.getFluidState(p).is(FluidTags.WATER))return p;}
        }return null;
    }
}
