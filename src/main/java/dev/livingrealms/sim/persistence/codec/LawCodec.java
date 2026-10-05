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


/** Custody, bounty and crime-ledger persistence. */
public final class LawCodec {
    private LawCodec() {}

public static void writeV5Law(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.custody().size());
        for(CustodyRecord c:state.custody()){out.writeLong(c.id());CodecIO.writeString(out,c.actorKey());out.writeLong(c.factionId());out.writeLong(c.startDay());out.writeLong(c.releaseDay());out.writeDouble(c.bountyAtArrest());CodecIO.writeString(out,c.reason());out.writeBoolean(c.active());}
    }

public static void readV5Law(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),1000000,"custody");
        for(int i=0;i<n;i++){CustodyRecord c=new CustodyRecord(in.readLong(),CodecIO.readString(in),in.readLong(),in.readLong(),in.readLong(),in.readDouble(),CodecIO.readString(in));c.restoreActive(in.readBoolean());state.addCustody(c);}
    }

public static void writeCrimeLedger(DataOutputStream out,CrimeLedger ledger)throws IOException{
        var profiles=new ArrayList<>(ledger.profiles().values());profiles.sort(Comparator.comparing(WantedProfile::actorKey));out.writeInt(profiles.size());
        for(WantedProfile p:profiles){CodecIO.writeString(out,p.actorKey());out.writeDouble(p.globalInfamy());var jurisdictions=new ArrayList<>(p.jurisdictions().values());jurisdictions.sort(Comparator.comparingLong(JurisdictionWanted::factionId));out.writeInt(jurisdictions.size());for(JurisdictionWanted w:jurisdictions){out.writeLong(w.factionId());out.writeDouble(w.bounty());out.writeDouble(w.notoriety());out.writeDouble(w.heat());out.writeLong(w.lastCrimeDay());out.writeInt(w.witnessedCrimes());out.writeInt(w.violentCrimes());out.writeInt(w.captures());}}
        List<CrimeIncident> incidents=ledger.incidents();out.writeInt(incidents.size());for(CrimeIncident c:incidents){out.writeLong(c.id());out.writeLong(c.day());CodecIO.writeString(out,c.actorKey());out.writeLong(c.jurisdictionFactionId());out.writeInt(c.type().ordinal());out.writeDouble(c.stolenOrDamageValue());CodecIO.writePosition(out,c.position());out.writeBoolean(c.witnessed());out.writeInt(c.witnessCount());CodecIO.writeString(out,c.victimKey());CodecIO.writeString(out,c.evidence());}
    }

public static void readCrimeLedger(DataInputStream in,CrimeLedger ledger)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),1000000,"wanted profiles");for(int i=0;i<n;i++){String actor=CodecIO.readString(in);WantedProfile p=ledger.profile(actor);p.restoreInfamy(in.readDouble());int jc=CodecIO.checkedCount(in.readInt(),100000,"wanted jurisdictions");for(int j=0;j<jc;j++){long faction=in.readLong();JurisdictionWanted w=p.in(faction);w.restore(in.readDouble(),in.readDouble(),in.readDouble(),in.readLong(),in.readInt(),in.readInt(),in.readInt());}}
        n=CodecIO.checkedCount(in.readInt(),20000,"crime incidents");for(int i=0;i<n;i++){CrimeIncident c=new CrimeIncident(in.readLong(),in.readLong(),CodecIO.readString(in),in.readLong(),CrimeType.values()[CodecIO.enumOrdinal(in.readInt(),CrimeType.values().length,"crime type")],in.readDouble(),CodecIO.readPosition(in),in.readBoolean(),in.readInt(),CodecIO.readString(in),CodecIO.readString(in));ledger.restoreIncident(c);}
    }

}
