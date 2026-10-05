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


/** Ecology region and population persistence. */
public final class EcologyCodec {
    private EcologyCodec() {}

public static void writeRegions(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.regions().size());
        for(EcosystemRegion r:state.regions()){
            out.writeLong(r.id());CodecIO.writeString(out,r.biome().id());out.writeDouble(r.areaKm2());CodecIO.writePosition(out,r.center());out.writeDouble(r.plantBiomass());out.writeInt(r.populations().size());
            for(PopulationGroup p:r.populations()){
                out.writeLong(p.id());CodecIO.writeString(out,p.speciesId());CodecIO.writeString(out,p.biomeId());out.writeDouble(p.position().x());out.writeDouble(p.position().z());out.writeDouble(p.population());
                out.writeDouble(p.health());out.writeDouble(p.hunger());out.writeDouble(p.reproductiveFraction());
                out.writeDouble(p.thirst());out.writeDouble(p.diseasePressure());out.writeDouble(p.injuryPressure());out.writeDouble(p.averageAgeDays());out.writeDouble(p.migrationPressure());
            }
        }
    }

public static void readRegions(DataInputStream in,SimulationState state,int version)throws IOException{
        int regionCount=CodecIO.checkedCount(in.readInt(),100000,"regions");for(int i=0;i<regionCount;i++){
            long id=in.readLong();String biomeId=CodecIO.readString(in);var biome=state.biomes().get(biomeId);if(biome==null)throw new IOException("Unknown biome "+biomeId);double area=in.readDouble();SimPosition center=version>=8?CodecIO.readPosition(in):new SimPosition(0,0);EcosystemRegion r=new EcosystemRegion(id,biome,area,center);r.restorePlantBiomass(in.readDouble());int pc=CodecIO.checkedCount(in.readInt(),1000000,"populations");
            double sx=0,sz=0,weight=0;for(int j=0;j<pc;j++){long pid=in.readLong();String sid=CodecIO.readString(in);if(!state.species().containsKey(sid))throw new IOException("Save contains live species missing from active catalog: "+sid);String pb=CodecIO.readString(in);PopulationGroup p=new PopulationGroup(pid,sid,pb,new SimPosition(in.readDouble(),in.readDouble()),in.readDouble());p.setHealth(in.readDouble());p.setHunger(in.readDouble());p.setReproductiveFraction(in.readDouble());if(version>=4){p.setThirst(in.readDouble());p.setDiseasePressure(in.readDouble());p.setInjuryPressure(in.readDouble());p.setAverageAgeDays(in.readDouble());p.setMigrationPressure(in.readDouble());}r.add(p);double w=Math.max(.5,p.population());sx+=p.position().x()*w;sz+=p.position().z()*w;weight+=w;}if(version<8&&weight>0)r.restoreCenter(new SimPosition(sx/weight,sz/weight));state.addRegion(r);
        }
    }

}
