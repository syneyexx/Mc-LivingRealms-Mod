package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.military.SiegeEquipmentProjection;
import dev.livingrealms.sim.military.SiegeMaterializationPlanner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.livingrealms.sim.world.SimPosition;

/** Materializes siege rams/ladders/artillery near players from active SiegeState. */
public final class SiegeEquipmentMaterializer {
    private SiegeEquipmentMaterializer(){}

    public static void tick(MinecraftServer server,LivingRealmsSavedData data){
        ServerLevel level=server.overworld();
        List<SimPosition> players=level.players().stream().map(p->new SimPosition(p.getX(),p.getZ())).toList();
        int budget=Math.min(48,Math.max(8,data.state().config().maxPhysicalMilitaryEntities()/2));
        List<SiegeEquipmentProjection> desired=SiegeMaterializationPlanner.plan(
                data.state(),players,data.state().config().physicalRadiusBlocks(),budget);
        Set<String> wanted=new HashSet<>();
        for(SiegeEquipmentProjection p:desired)wanted.add(p.projectionKey());
        Set<String> seen=new HashSet<>();
        for(SiegeEquipmentEntity e:SiegeEquipmentIndex.loaded()){
            String key=e.siegeId()+":"+e.projectionSlot()+":"+e.kind().name();
            if(!wanted.contains(key)||!seen.add(key))e.dematerialize();
        }
        for(SiegeEquipmentProjection p:desired){
            if(SiegeEquipmentIndex.forSlot(p.siegeId(),p.slot(),p.kind().name())!=null)continue;
            int x=(int)Math.floor(p.position().x()),z=(int)Math.floor(p.position().z());
            BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);
            if(!level.hasChunkAt(probe))continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            SiegeEquipmentEntity e=ModEntities.SIEGE_EQUIPMENT.get().create(level);
            if(e==null)continue;
            e.initializeProjection(p.siegeId(),p.attackerFactionId(),p.slot(),p.kind());
            e.moveTo(x+.5,y,z+.5,level.random.nextFloat()*360,0);
            if(level.noCollision(e))level.addFreshEntity(e);
        }
    }
}
