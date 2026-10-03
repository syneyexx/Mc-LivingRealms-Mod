package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.civilization.projection.*;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reconciles bounded physical representatives of traveling migration groups and pirate bands. */
public final class MobileCivilizationMaterializer {
    private MobileCivilizationMaterializer(){}
    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        var state=data.state();List<SimPosition> players=level.players().stream().map(p->new SimPosition(p.getX(),p.getZ())).toList();
        List<MigrationProjection> migrations=MobileCivilizationProjectionPlanner.migrations(state,players,RuntimeProjectionPolicy.migrationRadiusBlocks(state.config()),RuntimeProjectionPolicy.migrationBudget(state.config()));
        List<PirateProjection> pirates=MobileCivilizationProjectionPlanner.pirates(state,players,RuntimeProjectionPolicy.pirateRadiusBlocks(state.config()),RuntimeProjectionPolicy.pirateBudget(state.config()));
        Set<String> wanted=new HashSet<>();for(MigrationProjection p:migrations)wanted.add(p.projectionKey());for(PirateProjection p:pirates)wanted.add(p.projectionKey());
        Set<String> seen=new HashSet<>();for(MobileCivilizationEntity entity:MobileCivilizationIndex.loaded()){String key=entity.projectionKey();if(!wanted.contains(key)||!seen.add(key))entity.dematerialize();}
        for(MigrationProjection p:migrations){if(MobileCivilizationIndex.forSlot(MobileCivilizationKind.MIGRATION,p.groupId(),p.slot())!=null)continue;spawnMigration(level,p);}
        for(PirateProjection p:pirates){if(MobileCivilizationIndex.forSlot(MobileCivilizationKind.PIRATE,p.bandId(),p.slot())!=null)continue;spawnPirate(level,p);}
    }
    private static void spawnMigration(ServerLevel level,MigrationProjection p){MobileCivilizationEntity entity=spawnBase(level,p.position());if(entity==null)return;entity.initializeMigration(p.groupId(),p.factionId(),p.slot(),p.representedPeople(),p.reason());finishSpawn(level,entity,p.position());}
    private static void spawnPirate(ServerLevel level,PirateProjection p){MobileCivilizationEntity entity=spawnBase(level,p.position());if(entity==null)return;entity.initializePirate(p.bandId(),p.originFactionId(),p.slot(),p.representedPirates());finishSpawn(level,entity,p.position());}
    private static MobileCivilizationEntity spawnBase(ServerLevel level,SimPosition p){int x=(int)Math.floor(p.x()),z=(int)Math.floor(p.z());BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))return null;return ModEntities.MOBILE_CIVILIZATION.get().create(level);}
    private static void finishSpawn(ServerLevel level,MobileCivilizationEntity entity,SimPosition p){int x=(int)Math.floor(p.x()),z=(int)Math.floor(p.z());int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);entity.moveTo(x+.5,y,z+.5,level.random.nextFloat()*360F,0);if(level.noCollision(entity))level.addFreshEntity(entity);}
}
