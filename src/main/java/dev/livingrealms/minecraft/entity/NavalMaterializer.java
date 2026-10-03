package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;

/** Materializes only nearby fleet slots and keeps strategic state authoritative. */
public final class NavalMaterializer {
    private NavalMaterializer(){}
    public static void tick(MinecraftServer server,LivingRealmsSavedData data){
        ServerLevel level=server.overworld();List<SimPosition> players=level.players().stream().map(p->new SimPosition(p.getX(),p.getZ())).toList();var config=data.state().config();List<FleetProjection> desired=NavalMaterializationPlanner.plan(data.state().fleets(),players,RuntimeProjectionPolicy.navalRadiusBlocks(config),config.maxPhysicalNavalEntities());
        Set<String>wanted=new HashSet<>();for(FleetProjection p:desired)wanted.add(p.projectionKey());Set<String>seen=new HashSet<>();for(LivingRealmsShipEntity e:ShipProjectionIndex.loaded()){String k=e.fleetId()+":"+e.projectionSlot();if(!wanted.contains(k)||!seen.add(k))e.dematerialize();}
        for(FleetProjection p:desired){if(ShipProjectionIndex.forSlot(p.fleetId(),p.slot())!=null)continue;SimPosition q=position(data.state().seed(),p);BlockPos spawn=findWaterSurface(level,(int)Math.floor(q.x()),(int)Math.floor(q.z()));if(spawn==null)continue;LivingRealmsShipEntity e=ModEntities.SHIP.get().create(level);if(e==null)continue;e.initializeProjection(p.fleetId(),p.factionId(),p.slot(),p.shipClass(),p.representedShips());e.moveTo(spawn.getX()+.5,spawn.getY()+.6,spawn.getZ()+.5,level.random.nextFloat()*360,0);level.addFreshEntity(e);}
    }
    private static SimPosition position(long seed,FleetProjection p){DeterministicRng r=new DeterministicRng(seed^p.fleetId()*0xA24BAED4963EE407L^p.slot()*0x9FB21C651E98DF25L);double radius=r.between(6,36),angle=r.between(0,Math.PI*2);return new SimPosition(p.position().x()+Math.cos(angle)*radius,p.position().z()+Math.sin(angle)*radius);}
    private static BlockPos findWaterSurface(ServerLevel level,int originX,int originZ){for(int radius=0;radius<=18;radius+=3)for(int dx=-radius;dx<=radius;dx+=Math.max(1,radius==0?1:3))for(int dz=-radius;dz<=radius;dz+=Math.max(1,radius==0?1:3)){int x=originX+dx,z=originZ+dz;BlockPos column=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(column))continue;int surface=Math.min(level.getMaxBuildHeight()-2,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));for(int y=surface;y>=Math.max(level.getMinBuildHeight()+2,surface-8);y--){BlockPos p=new BlockPos(x,y,z);if(level.getFluidState(p).is(FluidTags.WATER))return p;}}return null;}
}
