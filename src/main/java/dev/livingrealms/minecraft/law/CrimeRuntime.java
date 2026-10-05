package dev.livingrealms.minecraft.law;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import dev.livingrealms.minecraft.entity.FactionCitizenIndex;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.player.PlayerActorIdentity;
import dev.livingrealms.sim.property.*;
import dev.livingrealms.sim.territory.*;
import dev.livingrealms.sim.world.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;

/** Minecraft-side evidence/witness bridge into the authoritative crime model. */
public final class CrimeRuntime {
    private static final double WITNESS_RADIUS_SQR=30.0D*30.0D;
    private CrimeRuntime(){}

    public static CrimeResult reportInTerritory(ServerPlayer actor,CrimeType type,double value,Entity victim,String evidence){
        ServerLevel level=actor.serverLevel();var data=SimulationRuntime.data(level.getServer());var state=data.state();SimPosition pos=new SimPosition(actor.getX(),actor.getZ());Jurisdiction j=TerritoryEngine.resolve(state.factions(),pos,state.config().borderDisputeThreshold());if(j.primaryFactionId()<=0)return new CrimeResult(false,0,0,WantedLevel.NONE,"wilderness");return report(actor,j.primaryFactionId(),type,value,victim,evidence);
    }

    public static CrimeResult report(ServerPlayer actor,long issuerFactionId,CrimeType type,double value,Entity victim,String evidence){
        ServerLevel level=actor.serverLevel();var data=SimulationRuntime.data(level.getServer());var state=data.state();if(state.findFaction(issuerFactionId).isEmpty())return new CrimeResult(false,0,0,WantedLevel.NONE,"unknown_jurisdiction");int witnesses=countWitnesses(level,actor,issuerFactionId,victim);SimPosition pos=new SimPosition(actor.getX(),actor.getZ());String victimKey=victim==null?"":victim.getType().toString()+":"+victim.getUUID();CrimeResult result=state.reportCrime(actorKey(actor),issuerFactionId,type,value,pos,witnesses>0,witnesses,victimKey,evidence);if(result.registered()){data.setDirty();String realm=state.findFaction(issuerFactionId).map(f->f.name()).orElse("Unknown realm");actor.sendSystemMessage(Component.literal("Crime witnessed in "+realm+": "+type.name().toLowerCase().replace('_',' ')+" • bounty +"+Math.round(result.bountyAdded())+" • "+result.wantedLevel().name().toLowerCase().replace('_',' ')));}return result;
    }


    public static CrimeResult reportPropertyTheft(ServerPlayer actor,PropertyClaim claim,double value,String evidence){
        if(actor==null||claim==null)return new CrimeResult(false,0,0,WantedLevel.NONE,"invalid_property_theft");
        ServerLevel level=actor.serverLevel();var data=SimulationRuntime.data(level.getServer());var state=data.state();
        int witnesses=countWitnesses(level,actor,claim.factionId(),null);
        CrimeResult result=PropertyCrimeEngine.reportTheft(state,actorKey(actor),claim,value,witnesses,evidence);
        if(result.registered()){
            data.setDirty();
            String realm=state.findFaction(claim.factionId()).map(f->f.name()).orElse("Unknown realm");
            actor.sendSystemMessage(Component.literal("Theft witnessed in "+realm+": bounty +"+Math.round(result.bountyAdded())+" • "+result.wantedLevel().name().toLowerCase().replace('_',' ')));
        }
        return result;
    }

    public static int countWitnesses(ServerLevel level,ServerPlayer actor,long factionId,Entity victim){int count=0;for(FactionCitizenEntity citizen:FactionCitizenIndex.loaded()){if(citizen==victim||citizen.factionId()!=factionId||!citizen.isAlive()||citizen.distanceToSqr(actor)>WITNESS_RADIUS_SQR)continue;if(citizen.hasLineOfSight(actor))count++;}for(ServerPlayer player:level.players()){if(player==actor||player==victim||!player.isAlive()||player.distanceToSqr(actor)>WITNESS_RADIUS_SQR)continue;if(player.hasLineOfSight(actor))count++;}return count;}
    /** Canonical player identity — delegates to {@link PlayerActorIdentity}. Never use display names. */
    public static String actorKey(ServerPlayer player){
        return PlayerActorIdentity.of(player.getUUID());
    }
}
