package dev.livingrealms.sim.persistence.codec;

import dev.livingrealms.sim.aviation.*;
import dev.livingrealms.sim.config.SimulationConfig;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.civilian.AppearanceProfile;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.construction.ConstructionPhase;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.diplomacy.*;
import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.*;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.logistics.*;
import dev.livingrealms.sim.military.*;
import dev.livingrealms.sim.naval.*;
import dev.livingrealms.sim.player.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.transport.*;
import dev.livingrealms.sim.underworld.StolenGoodsEntry;
import dev.livingrealms.sim.underworld.StolenGoodsLedger;
import dev.livingrealms.sim.underworld.UnderworldContract;
import dev.livingrealms.sim.underworld.UnderworldContractType;
import dev.livingrealms.sim.underworld.UnderworldProfile;
import dev.livingrealms.sim.world.*;
import java.io.*;
import java.util.*;


/** Settlement economy, construction-origin and provenance/site persistence. */
public final class SettlementCodec {
    private SettlementCodec() {}

public static void writeV16SettlementEconomy(DataOutputStream out,SimulationState state)throws IOException{
        List<Settlement> settlements=new ArrayList<>();
        for(Faction f:state.factions())settlements.addAll(f.settlements());
        settlements.sort(Comparator.comparingLong(Settlement::id));
        out.writeInt(settlements.size());
        for(Settlement s:settlements){
            out.writeLong(s.id());
            out.writeDouble(s.barnCapacity());
            out.writeDouble(s.granaryCapacity());
            for(ResourceType rt:ResourceType.values())out.writeDouble(s.stockpile().get(rt));
        }
    }

public static void readV16SettlementEconomy(DataInputStream in,SimulationState state,int version)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),100000,"settlement economy");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            double barn=in.readDouble(),granary=in.readDouble();
            EnumMap<ResourceType,Double> stores=new EnumMap<>(ResourceType.class);
            for(int ri=0;ri<CodecIO.resourceCount(version);ri++)stores.put(ResourceType.values()[ri],in.readDouble());
            Settlement settlement=state.findSettlement(id).orElseThrow(()->new IOException("settlement economy references missing settlement "+id));
            try{settlement.restoreEconomy(barn,granary,stores);}catch(IllegalArgumentException bad){throw new IOException("invalid settlement economy for "+id,bad);}
        }
    }

public static void writeV18GoodsAndOrigins(DataOutputStream out,SimulationState state)throws IOException{
        // Construction origins for every completed key (MATERIALIZED default for older saves).
        List<Settlement> settlements=new ArrayList<>();
        for(Faction f:state.factions())settlements.addAll(f.settlements());
        settlements.sort(Comparator.comparingLong(Settlement::id));
        out.writeInt(settlements.size());
        for(Settlement s:settlements){
            out.writeLong(s.id());
            var completed=new ArrayList<>(s.completedConstruction());
            Collections.sort(completed);
            out.writeInt(completed.size());
            for(String key:completed){
                CodecIO.writeString(out,key);
                out.writeInt(s.constructionOrigin(key).ordinal());
            }
        }
    }

public static void readV18GoodsAndOrigins(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),100000,"construction origins");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            Settlement settlement=state.findSettlement(id).orElseThrow(()->new IOException("construction origins missing settlement "+id));
            int kc=CodecIO.checkedCount(in.readInt(),1000000,"origin keys");
            for(int k=0;k<kc;k++){
                String key=CodecIO.readString(in);
                ConstructionOrigin origin=ConstructionOrigin.values()[CodecIO.enumOrdinal(in.readInt(),ConstructionOrigin.values().length,"construction origin")];
                if(settlement.isConstructionCompleted(key))settlement.restoreConstructionOrigin(key,origin);
            }
        }
    }

public static void writeV19ProvenanceAndSites(DataOutputStream out,SimulationState state)throws IOException{
        List<Settlement> settlements=new ArrayList<>();
        for(Faction f:state.factions())settlements.addAll(f.settlements());
        settlements.sort(Comparator.comparingLong(Settlement::id));
        out.writeInt(settlements.size());
        for(Settlement s:settlements){
            out.writeLong(s.id());
            out.writeInt(s.origin().ordinal());
            out.writeBoolean(s.physicallyAnchored());
            out.writeInt(s.developmentMode().ordinal());
        }
        List<OutlyingSite> sites=new ArrayList<>(state.outlyingSites());
        sites.sort(Comparator.comparingLong(OutlyingSite::id));
        out.writeInt(sites.size());
        for(OutlyingSite site:sites){
            out.writeLong(site.id());out.writeLong(site.settlementId());out.writeLong(site.factionId());
            out.writeInt(site.type().ordinal());CodecIO.writePosition(out,site.position());CodecIO.writeString(out,site.name());
            out.writeInt(site.representedPopulation());out.writeDouble(site.housingCredit());
            out.writeBoolean(site.foreign());out.writeBoolean(site.active());
        }
        List<CitizenJourney> journeys=new ArrayList<>(state.citizenJourneys());
        journeys.sort(Comparator.comparingLong(CitizenJourney::id));
        out.writeInt(journeys.size());
        for(CitizenJourney j:journeys){
            out.writeLong(j.id());out.writeLong(j.citizenId());out.writeLong(j.factionId());
            out.writeLong(j.originSettlementId());out.writeLong(j.targetSettlementId());out.writeLong(j.routeId());
            out.writeInt(j.purpose().ordinal());out.writeLong(j.createdDay());out.writeDouble(j.progress());
            out.writeInt(j.status().ordinal());CodecIO.writeString(out,j.payload());
        }
        List<RoadsideSite> roadside=new ArrayList<>(state.roadsideSites());
        roadside.sort(Comparator.comparingLong(RoadsideSite::id));
        out.writeInt(roadside.size());
        for(RoadsideSite site:roadside){
            out.writeLong(site.id());out.writeInt(site.type().ordinal());CodecIO.writePosition(out,site.position());
            CodecIO.writeString(out,site.name());out.writeLong(site.relatedSettlementId());out.writeLong(site.relatedRouteId());
            out.writeLong(site.createdDay());out.writeInt(site.lifecycle().ordinal());out.writeBoolean(site.active());
        }
        List<UnderworldProfile> underworld=new ArrayList<>(state.underworldProfiles().values());
        underworld.sort(Comparator.comparing(UnderworldProfile::actorKey));
        out.writeInt(underworld.size());
        for(UnderworldProfile p:underworld){
            CodecIO.writeString(out,p.actorKey());
            out.writeInt(p.contractsCompleted());
            out.writeDouble(p.streetCred());
            out.writeDouble(p.briberySkill());
            out.writeLong(p.lastContractDay());
            out.writeLong(p.lastBribeDay());
            out.writeBoolean(p.blackMarketAccess());
        }
        List<RegisteredPlayerStructure> playerStructures=new ArrayList<>(state.registeredPlayerStructures());
        playerStructures.sort(Comparator.comparingLong(RegisteredPlayerStructure::id));
        out.writeInt(playerStructures.size());
        for(RegisteredPlayerStructure s:playerStructures){
            out.writeLong(s.id());out.writeLong(s.settlementId());CodecIO.writeString(out,s.ownerActorKey());
            out.writeInt(s.role().ordinal());
            out.writeInt(s.minX());out.writeInt(s.minY());out.writeInt(s.minZ());
            out.writeInt(s.maxX());out.writeInt(s.maxY());out.writeInt(s.maxZ());
            out.writeInt(s.doorX());out.writeInt(s.doorY());out.writeInt(s.doorZ());
            out.writeInt(s.capacity());out.writeLong(s.registrationDay());out.writeLong(s.fingerprint());
            out.writeBoolean(s.valid());out.writeLong(s.lastValidatedDay());
        }

    }

public static void readV19ProvenanceAndSites(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),100000,"settlement provenance");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            SettlementOrigin origin=SettlementOrigin.values()[CodecIO.enumOrdinal(in.readInt(),SettlementOrigin.values().length,"settlement origin")];
            boolean anchored=in.readBoolean();
            DevelopmentMode mode=DevelopmentMode.values()[CodecIO.enumOrdinal(in.readInt(),DevelopmentMode.values().length,"development mode")];
            Settlement settlement=state.findSettlement(id).orElseThrow(()->new IOException("provenance missing settlement "+id));
            settlement.restoreProvenance(origin,anchored,mode);
        }
        n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_OUTLYING_SITES,"outlying sites");
        for(int i=0;i<n;i++){
            long id=in.readLong(),settlementId=in.readLong(),factionId=in.readLong();
            OutlyingSite.Type type=OutlyingSite.Type.values()[CodecIO.enumOrdinal(in.readInt(),OutlyingSite.Type.values().length,"outlying type")];
            SimPosition pos=CodecIO.readPosition(in);String name=CodecIO.readString(in);
            int pop=in.readInt();double housing=in.readDouble();boolean foreign=in.readBoolean();boolean active=in.readBoolean();
            OutlyingSite site=new OutlyingSite(id,settlementId,factionId,type,pos,name,pop,housing,foreign);
            site.restoreActive(active);
            state.addOutlyingSite(site);
        }
        n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_CITIZEN_JOURNEYS,"citizen journeys");
        for(int i=0;i<n;i++){
            long id=in.readLong(),citizenId=in.readLong(),factionId=in.readLong();
            long originId=in.readLong(),targetId=in.readLong(),routeId=in.readLong();
            CitizenJourney.Purpose purpose=CitizenJourney.Purpose.values()[CodecIO.enumOrdinal(in.readInt(),CitizenJourney.Purpose.values().length,"journey purpose")];
            long created=in.readLong();double progress=in.readDouble();
            CitizenJourney.Status status=CitizenJourney.Status.values()[CodecIO.enumOrdinal(in.readInt(),CitizenJourney.Status.values().length,"journey status")];
            String payload=CodecIO.readString(in);
            CitizenJourney journey=new CitizenJourney(id,citizenId,factionId,originId,targetId,routeId,purpose,created);
            journey.restore(progress,status,payload,routeId);
            state.addCitizenJourney(journey);
        }
        n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_ROADSIDE_SITES,"roadside sites");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            RoadsideSite.Type type=RoadsideSite.Type.values()[CodecIO.enumOrdinal(in.readInt(),RoadsideSite.Type.values().length,"roadside type")];
            SimPosition pos=CodecIO.readPosition(in);String name=CodecIO.readString(in);
            long relatedSettlement=in.readLong(),relatedRoute=in.readLong(),created=in.readLong();
            RoadsideSite.Lifecycle lifecycle=RoadsideSite.Lifecycle.values()[CodecIO.enumOrdinal(in.readInt(),RoadsideSite.Lifecycle.values().length,"roadside lifecycle")];
            boolean active=in.readBoolean();
            RoadsideSite site=new RoadsideSite(id,type,pos,name,relatedSettlement,relatedRoute,created);
            site.restore(lifecycle,active);
            state.addRoadsideSite(site);
        }
        n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_UNDERWORLD_PROFILES,"underworld profiles");
        for(int i=0;i<n;i++){
            String actor=CodecIO.readString(in);
            UnderworldProfile profile=new UnderworldProfile(actor);
            profile.restore(in.readInt(),in.readDouble(),in.readDouble(),in.readLong(),in.readLong(),in.readBoolean());
            state.restoreUnderworldProfile(profile);
        }
        n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_REGISTERED_PLAYER_STRUCTURES,"registered player structures");
        for(int i=0;i<n;i++){
            long id=in.readLong(),settlementId=in.readLong();String owner=CodecIO.readString(in);
            RegisteredPlayerStructure.Role role=RegisteredPlayerStructure.Role.values()[CodecIO.enumOrdinal(in.readInt(),RegisteredPlayerStructure.Role.values().length,"player structure role")];
            int minX=in.readInt(),minY=in.readInt(),minZ=in.readInt(),maxX=in.readInt(),maxY=in.readInt(),maxZ=in.readInt();
            int doorX=in.readInt(),doorY=in.readInt(),doorZ=in.readInt();
            int capacity=in.readInt();long regDay=in.readLong();long fingerprint=in.readLong();
            boolean valid=in.readBoolean();long lastVal=in.readLong();
            RegisteredPlayerStructure s=new RegisteredPlayerStructure(id,settlementId,owner,role,minX,minY,minZ,maxX,maxY,maxZ,doorX,doorY,doorZ,capacity,regDay,fingerprint);
            s.restore(valid,capacity,fingerprint,lastVal,role);
            state.addRegisteredPlayerStructure(s);
        }

    }

}
