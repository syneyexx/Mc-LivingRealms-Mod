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


/** Faction, settlement roster, army and diplomatic-relation persistence. */
public final class FactionCodec {
    private FactionCodec() {}

public static void writeFactions(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.factions().size());
        for(Faction f:state.factions()){
            out.writeLong(f.id());CodecIO.writeString(out,f.name());CodecIO.writeString(out,f.rulerName());out.writeDouble(f.treasury());out.writeDouble(f.technology());writeGovernment(out,f.government());
            for(ResourceType rt:ResourceType.values())out.writeDouble(f.stockpile().get(rt));
            out.writeInt(f.settlements().size());
            for(Settlement st:f.settlements()){
                out.writeLong(st.id());CodecIO.writeString(out,st.name());out.writeDouble(st.position().x());out.writeDouble(st.position().z());out.writeInt(st.population());out.writeInt(st.housing());out.writeDouble(st.infrastructure());
                out.writeDouble(st.prosperity());out.writeDouble(st.unrest());out.writeDouble(st.foodSecurity());out.writeDouble(st.publicOrder());out.writeDouble(st.employment());
                var completed=new ArrayList<>(st.completedConstruction());Collections.sort(completed);out.writeInt(completed.size());for(String key:completed)CodecIO.writeString(out,key);out.writeInt(st.developmentPriority().ordinal());
            }
            out.writeInt(f.armies().size());for(Army a:f.armies()){out.writeLong(a.id());out.writeDouble(a.position().x());out.writeDouble(a.position().z());out.writeInt(a.infantry());out.writeInt(a.cavalry());out.writeInt(a.artillery());out.writeInt(a.armor());out.writeInt(a.aircraft());out.writeDouble(a.morale());out.writeDouble(a.supply());}
            var relations=new ArrayList<>(f.relations().entrySet());relations.sort(Map.Entry.comparingByKey());out.writeInt(relations.size());for(var e:relations){out.writeLong(e.getKey());DiplomaticRelation rel=e.getValue();out.writeDouble(rel.opinion());out.writeInt(rel.status().ordinal());out.writeBoolean(rel.tradeAgreement());}
        }
    }

public static void readFactions(DataInputStream in,SimulationState state,int version)throws IOException{
        int factionCount=CodecIO.checkedCount(in.readInt(),10000,"factions");for(int i=0;i<factionCount;i++){
            long id=in.readLong();Faction f=new Faction(id,CodecIO.readString(in),CodecIO.readString(in));f.restoreTreasury(in.readDouble());f.restoreTechnology(in.readDouble());if(version>=4)f.restoreGovernment(readGovernment(in));
            for(int ri=0;ri<CodecIO.resourceCount(version);ri++)f.stockpile().add(ResourceType.values()[ri],in.readDouble());
            int sc=CodecIO.checkedCount(in.readInt(),100000,"settlements");for(int j=0;j<sc;j++){Settlement st=new Settlement(in.readLong(),CodecIO.readString(in),new SimPosition(in.readDouble(),in.readDouble()),in.readInt(),in.readInt());st.improveInfrastructure(in.readDouble());if(version>=4)st.restoreSociety(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());if(version>=2){int cc=CodecIO.checkedCount(in.readInt(),1000000,"completed construction");for(int k=0;k<cc;k++)st.markConstructionCompleted(CodecIO.readString(in));}if(version>=10)st.setDevelopmentPriority(DevelopmentPriority.values()[CodecIO.enumOrdinal(in.readInt(),DevelopmentPriority.values().length,"development priority")]);f.addSettlement(st);}
            int ac=CodecIO.checkedCount(in.readInt(),100000,"armies");for(int j=0;j<ac;j++){Army a=new Army(in.readLong(),id,new SimPosition(in.readDouble(),in.readDouble()),in.readInt());a.restoreState(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readDouble(),in.readDouble());f.addArmy(a);}
            int rc=CodecIO.checkedCount(in.readInt(),100000,"relations");for(int j=0;j<rc;j++){long other=in.readLong();double opinion=in.readDouble();int ord=CodecIO.enumOrdinal(in.readInt(),RelationStatus.values().length,"relation status");boolean trade=in.readBoolean();f.relationWith(other).restore(opinion,RelationStatus.values()[ord],trade);}state.addFaction(f);
        }
    }

public static void writeGovernment(DataOutputStream out,GovernmentState g)throws IOException{
        out.writeInt(g.type().ordinal());out.writeInt(g.successionLaw().ordinal());RulerProfile r=g.ruler();out.writeLong(r.id());CodecIO.writeString(out,r.name());out.writeInt(r.ageYears());out.writeDouble(r.health());out.writeDouble(r.diplomacy());out.writeDouble(r.stewardship());out.writeDouble(r.martial());out.writeDouble(r.legitimacy());out.writeDouble(g.stability());out.writeDouble(g.legitimacy());out.writeDouble(g.corruption());out.writeDouble(g.taxRate());out.writeDouble(g.lawEnforcement());out.writeLong(g.yearsInPower());
    }

public static GovernmentState readGovernment(DataInputStream in)throws IOException{
        GovernmentType type=GovernmentType.values()[CodecIO.enumOrdinal(in.readInt(),GovernmentType.values().length,"government type")];SuccessionLaw succession=SuccessionLaw.values()[CodecIO.enumOrdinal(in.readInt(),SuccessionLaw.values().length,"succession law")];
        RulerProfile ruler=new RulerProfile(in.readLong(),CodecIO.readString(in),in.readInt(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());GovernmentState g=new GovernmentState(type,succession,ruler);g.restore(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readLong());return g;
    }

}
