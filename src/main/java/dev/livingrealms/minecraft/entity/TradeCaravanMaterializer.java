package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.logistics.projection.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Materializes a bounded set of nearby strategic shipments without force-loading chunks. */
public final class TradeCaravanMaterializer {
    private TradeCaravanMaterializer(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        var state=data.state();
        List<SimPosition> players=level.players().stream().map(p->new SimPosition(p.getX(),p.getZ())).toList();
        CaravanMaterializationPlanner planner=new CaravanMaterializationPlanner(RuntimeProjectionPolicy.caravans(state.config()));
        List<CaravanProjectionRequest> desired=planner.plan(state.shipments(),players);

        Collection<TradeCaravanEntity> loaded=TradeCaravanIndex.loaded();
        List<CaravanProjectionSnapshot> actual=loaded.stream()
                .map(e->new CaravanProjectionSnapshot(e.getUUID().toString(),e.shipmentId()))
                .toList();
        Set<Long> activeShipmentIds=new HashSet<>();
        for(TradeShipment shipment:state.shipments())if(!shipment.arrived())activeShipmentIds.add(shipment.id());
        CaravanProjectionPlan delta=planner.reconcile(desired,actual,activeShipmentIds);

        Map<String,TradeCaravanEntity> byKey=new HashMap<>();
        for(TradeCaravanEntity entity:loaded)byKey.put(entity.getUUID().toString(),entity);
        for(CaravanProjectionDespawn removal:delta.despawns()){
            TradeCaravanEntity entity=byKey.get(removal.entityKey());
            if(entity!=null&&!entity.isRemoved())entity.dematerialize();
        }
        for(CaravanProjectionSpawn spawn:delta.spawns()){
            TradeShipment shipment=state.findShipment(spawn.shipmentId()).orElse(null);
            if(shipment==null||shipment.arrived())continue;
            int x=(int)Math.floor(spawn.position().x()),z=(int)Math.floor(spawn.position().z());
            BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);
            if(!level.hasChunkAt(probe))continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            TradeCaravanEntity entity=ModEntities.CARAVAN.get().create(level);
            if(entity==null)continue;
            entity.initializeProjection(shipment);
            entity.moveTo(x+.5,y,z+.5,0,0);
            if(level.noCollision(entity))level.addFreshEntity(entity);
        }
    }
}
