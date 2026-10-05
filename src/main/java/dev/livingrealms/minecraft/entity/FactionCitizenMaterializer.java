package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.compat.CompatibleContentRuntime;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.runtime.ProjectionBudget;
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
    public static void tick(MinecraftServer server,LivingRealmsSavedData data){ServerLevel level=server.overworld();var state=data.state();List<SimPosition> players=level.players().stream().map(p->new SimPosition(p.getX(),p.getZ())).toList();        ProjectionBudget budget = ProjectionBudget.forPlayers(state.config(), Math.max(1, players.size()));
        var desired = CitizenMaterializationPlanner.plan(state, state.factions(), players,
                RuntimeProjectionPolicy.citizenRadiusBlocks(state.config()), budget.lane(ProjectionBudget.Lane.CITIZENS));Map<String,CitizenProjection> wanted=new HashMap<>();for(CitizenProjection p:desired)wanted.put(p.projectionKey(),p);
        Set<Long> travelingHouseholds=new HashSet<>();state.migrationGroups().stream().filter(g->g.status()==MigrationStatus.TRAVELING).forEach(g->travelingHouseholds.addAll(g.householdIds()));
        Set<String> seen=new HashSet<>();for(FactionCitizenEntity entity:FactionCitizenIndex.loaded()){if(entity.isJourneyProjection())continue;String key=entity.settlementId()+":"+entity.projectionSlot();SocialCitizen social=entity.citizenId()>0?state.findSocialCitizen(entity.citizenId()).orElse(null):null;boolean traveling=social!=null&&social.householdId()>0&&travelingHouseholds.contains(social.householdId());if(traveling||!wanted.containsKey(key)||!seen.add(key))entity.dematerialize();else if(social!=null&&isCourtIdentity(state,social)&&!entity.isSpeaking())applyCourtPresentation(state,entity,social);}
        for(CitizenProjection projection:desired){if(FactionCitizenIndex.forSlot(projection.settlementId(),projection.slot())!=null)continue;int before=state.socialCitizens().size();SocialCitizen social=state.ensureSocialCitizen(projection.factionId(),projection.settlementId(),projection.slot(),projection.role());if(state.socialCitizens().size()!=before)data.setDirty();if(social.householdId()>0&&travelingHouseholds.contains(social.householdId()))continue;boolean court=isCourtIdentity(state,social);SimPosition pos=state.activeCustody("citizen:"+social.id(),social.factionId()).isPresent()?custodyPosition(data,projection):position(state,state.seed(),projection,social,court);int x=(int)Math.floor(pos.x()),z=(int)Math.floor(pos.z());BlockPos probe=new BlockPos(x,level.getSeaLevel(),z);if(!level.hasChunkAt(probe))continue;int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);FactionCitizenEntity entity=ModEntities.CITIZEN.get().create(level);if(entity==null)continue;entity.initializeProjection(social);if(court)applyCourtPresentation(state,entity,social);entity.moveTo(x+.5,y,z+.5,level.random.nextFloat()*360F,0);if(level.noCollision(entity))level.addFreshEntity(entity);}
    }
    private static boolean isCourtIdentity(dev.livingrealms.sim.world.SimulationState state,SocialCitizen social){
        var dynasty=state.dynasties().get(social.factionId());
        if(dynasty==null)return false;
        return social.id()==dynasty.rulerCitizenId()||social.id()==dynasty.heirCitizenId()||social.id()==dynasty.regentCitizenId();
    }
    private static void applyCourtPresentation(dev.livingrealms.sim.world.SimulationState state,FactionCitizenEntity entity,SocialCitizen social){
        var dynasty=state.dynasties().get(social.factionId());
        if(dynasty==null)return;
        var faction=state.findFaction(social.factionId()).orElse(null);
        String title=courtTitle(faction,dynasty,social);
        // Court uses dedicated high appearance indices (44–47) within the 48-skin pool — not gold armor.
        if(social.id()==dynasty.rulerCitizenId())entity.setSkinVariant(47);
        else if(social.id()==dynasty.heirCitizenId())entity.setSkinVariant(46);
        else if(social.id()==dynasty.regentCitizenId())entity.setSkinVariant(45);
        else entity.setSkinVariant(44);
        CompatibleContentRuntime.equipCitizen(entity,social.factionId(),CitizenRole.OFFICIAL,Math.floorMod((int)social.id(),32));
        entity.setCustomName(net.minecraft.network.chat.Component.literal(title+" "+social.name()));
        entity.setCustomNameVisible(true);
    }
    static String courtTitle(dev.livingrealms.sim.faction.Faction faction,dev.livingrealms.sim.civilization.DynastyState dynasty,SocialCitizen social){
        if(social.id()==dynasty.regentCitizenId()&&dynasty.regency())return "Regent";
        if(social.id()==dynasty.heirCitizenId())return "Heir";
        if(social.id()!=dynasty.rulerCitizenId())return "Court";
        if(faction==null)return "Ruler";
        return switch(faction.government().type()){
            case FEUDAL_MONARCHY,ABSOLUTE_MONARCHY,CONSTITUTIONAL_MONARCHY -> "Monarch";
            case REPUBLIC,MERCHANT_REPUBLIC -> "Consul";
            case MILITARY_JUNTA -> "Marshal";
            case THEOCRACY -> "Hierarch";
            case TRIBAL_COUNCIL -> "Chieftain";
        };
    }
    private static SimPosition custodyPosition(LivingRealmsSavedData data,CitizenProjection projection){var state=data.state();var settlement=state.findSettlement(projection.settlementId()).orElse(null);var faction=state.findFaction(projection.factionId()).orElse(null);if(settlement==null||faction==null)return projection.settlementCenter();return SettlementPlanner.plan(faction,settlement).stream().filter(i->i.role()==StructureRole.PRISON&&settlement.isConstructionCompleted(i.key())).findFirst().map(ConstructionIntent::center).orElse(settlement.position());}
    private static SimPosition position(dev.livingrealms.sim.world.SimulationState state,long seed,CitizenProjection p,SocialCitizen social,boolean court){
        if(court){
            var settlement=state.findSettlement(p.settlementId()).orElse(null);
            var faction=state.findFaction(p.factionId()).orElse(null);
            if(settlement!=null&&faction!=null){
                var keep=SettlementPlanner.plan(faction,settlement).stream()
                        .filter(i->i.role()==StructureRole.KEEP)
                        .findFirst().map(ConstructionIntent::center).orElse(settlement.position());
                // Slight deterministic offset so ruler/heir do not stack on the same block.
                double ox=(social.id()%7)-3,oz=((social.id()/7)%7)-3;
                return new SimPosition(keep.x()+ox,keep.z()+oz);
            }
        }
        DeterministicRng r=new DeterministicRng(seed^p.settlementId()*0x9E3779B97F4A7C15L^p.slot()*0xD1B54A32D192ED03L);double radius=r.between(4,55),angle=r.between(0,Math.PI*2);return new SimPosition(p.settlementCenter().x()+Math.cos(angle)*radius,p.settlementCenter().z()+Math.sin(angle)*radius);
    }
}
