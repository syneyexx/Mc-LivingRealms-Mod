package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.law.BountyHunterPlanner;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded physical projection for open NPC bounty pursuits. Never force-loads chunks. */
public final class BountyHunterMaterializer {
    private BountyHunterMaterializer(){}
    public static void tick(MinecraftServer server,LivingRealmsSavedData data){
        ServerLevel level=server.overworld();
        List<BountyHunterPlanner.OnlineTarget> targets=level.players().stream().map(p->new BountyHunterPlanner.OnlineTarget("player:"+p.getUUID(),new SimPosition(p.getX(),p.getZ()),p.isAlive())).toList();
        List<BountyHunterPlanner.Existing> existing=BountyHunterIndex.loaded().stream().map(e->new BountyHunterPlanner.Existing(e.contractId(),e.hunterKey())).toList();
        int budget=Math.max(1,Math.min(8,data.state().config().maxPhysicalMilitaryEntities()/12));
        var plan=BountyHunterPlanner.reconcile(data.state().bounties(),targets,existing,budget);
        for(long contractId:plan.despawnContractIds()){var loaded=BountyHunterIndex.forContract(contractId);int from=plan.keepContractIds().contains(contractId)?1:0;for(int i=from;i<loaded.size();i++)loaded.get(i).dematerialize();}
        for(var desired:plan.spawns()){
            if(!BountyHunterIndex.forContract(desired.contractId()).isEmpty())continue;
            SpawnPoint point=findSpawn(level,desired.targetPosition(),desired.contractId());if(point==null)continue;
            BountyHunterEntity entity=ModEntities.BOUNTY_HUNTER.get().create(level);if(entity==null)continue;
            entity.initializeProjection(desired.contractId(),desired.issuerFactionId(),desired.targetActorKey(),desired.hunterKey());entity.moveTo(point.x+.5,point.y,point.z+.5,level.random.nextFloat()*360F,0);
            if(level.noCollision(entity))level.addFreshEntity(entity);
        }
    }
    private static SpawnPoint findSpawn(ServerLevel level,SimPosition target,long contractId){
        long mix=contractId*0x9E3779B97F4A7C15L;double angle=((mix>>>11)&0xFFFF)/65535.0*Math.PI*2;double distance=28+((mix>>>29)&15);
        int x=(int)Math.floor(target.x()+Math.cos(angle)*distance),z=(int)Math.floor(target.z()+Math.sin(angle)*distance);BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))return null;int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);return new SpawnPoint(x,y,z);
    }
    private record SpawnPoint(int x,int y,int z){}
}
