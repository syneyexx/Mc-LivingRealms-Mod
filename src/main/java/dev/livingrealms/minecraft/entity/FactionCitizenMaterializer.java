package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.civilization.MigrationStatus;
import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.levelgen.Heightmap;

/** Materializes bounded civilian representatives only in loaded chunks around players. */
public final class FactionCitizenMaterializer {
    private FactionCitizenMaterializer(){}
    public static void tick(MinecraftServer server,LivingRealmsSavedData data){ServerLevel level=server.overworld();var state=data.state();List<SimPosition> players=level.players().stream().map(p->new SimPosition(p.getX(),p.getZ())).toList();var desired=CitizenMaterializationPlanner.plan(state.factions(),players,RuntimeProjectionPolicy.citizenRadiusBlocks(state.config()),RuntimeProjectionPolicy.citizenBudget(state.config()));Map<String,CitizenProjection> wanted=new HashMap<>();for(CitizenProjection p:desired)wanted.put(p.projectionKey(),p);
        Set<Long> travelingHouseholds=new HashSet<>();state.migrationGroups().stream().filter(g->g.status()==MigrationStatus.TRAVELING).forEach(g->travelingHouseholds.addAll(g.householdIds()));
        Set<String> seen=new HashSet<>();for(FactionCitizenEntity entity:FactionCitizenIndex.loaded()){String key=entity.settlementId()+":"+entity.projectionSlot();SocialCitizen social=entity.citizenId()>0?state.findSocialCitizen(entity.citizenId()).orElse(null):null;boolean traveling=social!=null&&social.householdId()>0&&travelingHouseholds.contains(social.householdId());if(traveling||!wanted.containsKey(key)||!seen.add(key))entity.dematerialize();}
        for(CitizenProjection projection:desired){if(FactionCitizenIndex.forSlot(projection.settlementId(),projection.slot())!=null)continue;int before=state.socialCitizens().size();SocialCitizen social=state.ensureSocialCitizen(projection.factionId(),projection.settlementId(),projection.slot(),projection.role());if(state.socialCitizens().size()!=before)data.setDirty();if(social.householdId()>0&&travelingHouseholds.contains(social.householdId()))continue;SimPosition pos=state.activeCustody("citizen:"+social.id(),social.factionId()).isPresent()?custodyPosition(data,projection):position(state.seed(),projection);int x=(int)Math.floor(pos.x()),z=(int)Math.floor(pos.z());BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);FactionCitizenEntity entity=ModEntities.CITIZEN.get().create(level);if(entity==null)continue;entity.initializeProjection(social);entity.moveTo(x+.5,y,z+.5,level.random.nextFloat()*360F,0);if(level.noCollision(entity))level.addFreshEntity(entity);}
    }
    private static SimPosition custodyPosition(LivingRealmsSavedData data,CitizenProjection projection){var state=data.state();var settlement=state.findSettlement(projection.settlementId()).orElse(null);var faction=state.findFaction(projection.factionId()).orElse(null);if(settlement==null||faction==null)return projection.settlementCenter();return SettlementPlanner.plan(faction,settlement).stream().filter(i->i.role()==StructureRole.PRISON&&settlement.isConstructionCompleted(i.key())).findFirst().map(ConstructionIntent::center).orElse(settlement.position());}
    private static SimPosition position(long seed,CitizenProjection p){DeterministicRng r=new DeterministicRng(seed^p.settlementId()*0x9E3779B97F4A7C15L^p.slot()*0xD1B54A32D192ED03L);double radius=r.between(4,55),angle=r.between(0,Math.PI*2);return new SimPosition(p.settlementCenter().x()+Math.cos(angle)*radius,p.settlementCenter().z()+Math.sin(angle)*radius);}
}
