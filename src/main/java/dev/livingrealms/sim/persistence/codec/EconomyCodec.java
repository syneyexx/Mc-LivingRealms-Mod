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


/** Trade shipment, industry and simulation-config persistence. */
public final class EconomyCodec {
    private EconomyCodec() {}

public static void writeShipments(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.shipments().size());for(TradeShipment s:state.shipments()){out.writeLong(s.id());out.writeLong(s.sellerFactionId());out.writeLong(s.buyerFactionId());out.writeInt(s.resource().ordinal());out.writeDouble(s.amount());out.writeDouble(s.value());out.writeDouble(s.origin().x());out.writeDouble(s.origin().z());out.writeDouble(s.destination().x());out.writeDouble(s.destination().z());out.writeDouble(s.progress());}
    }

public static void readShipments(DataInputStream in,SimulationState state,int version)throws IOException{int tc=CodecIO.checkedCount(in.readInt(),1000000,"trade shipments");for(int i=0;i<tc;i++){long shipmentId=in.readLong(),seller=in.readLong(),buyer=in.readLong();int resourceOrdinal=CodecIO.enumOrdinal(in.readInt(),CodecIO.resourceCount(version),"shipment resource");TradeShipment shipment=new TradeShipment(shipmentId,seller,buyer,ResourceType.values()[resourceOrdinal],in.readDouble(),in.readDouble(),new SimPosition(in.readDouble(),in.readDouble()),new SimPosition(in.readDouble(),in.readDouble()));try{shipment.restoreProgress(in.readDouble());}catch(IllegalArgumentException e){throw new IOException("bad shipment progress",e);}state.addShipment(shipment);}}

public static void writeV7Industry(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.industrialSites().size());
        for(IndustrialSite site:state.industrialSites()){out.writeLong(site.id());out.writeLong(site.factionId());out.writeLong(site.settlementId());out.writeInt(site.kind().ordinal());out.writeInt(site.level());out.writeDouble(site.condition());out.writeInt(site.status().ordinal());out.writeInt(site.starvedDays());out.writeInt(site.downtimeDays());out.writeInt(site.lastCycles());out.writeDouble(site.lastUtilization());}
    }

public static void readV7Industry(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),1000000,"industrial sites");
        for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong();IndustryKind kind=IndustryKind.values()[CodecIO.enumOrdinal(in.readInt(),IndustryKind.values().length,"industry kind")];int level=in.readInt();double condition=in.readDouble();IndustrialSiteStatus status=IndustrialSiteStatus.values()[CodecIO.enumOrdinal(in.readInt(),IndustrialSiteStatus.values().length,"industry status")];int starved=CodecIO.checkedCount(in.readInt(),10000000,"industry starved days"),downtime=CodecIO.checkedCount(in.readInt(),10000000,"industry downtime"),cycles=CodecIO.checkedCount(in.readInt(),10000000,"industry cycles");double utilization=in.readDouble();IndustrialSite site=new IndustrialSite(id,faction,settlement,kind,level);site.restore(condition,status,starved,downtime,cycles,utilization);state.addIndustrialSite(site);}
    }

public static void writeV9Config(DataOutputStream out,SimulationState state)throws IOException{
        SimulationConfig c=state.config();
        out.writeDouble(c.physicalRadiusBlocks());out.writeDouble(c.regionalRadiusBlocks());
        out.writeInt(c.maxPhysicalWildlife());out.writeInt(c.maxPhysicalCaravans());out.writeInt(c.maxPhysicalMilitaryEntities());out.writeInt(c.maxPhysicalNavalEntities());
        out.writeInt(c.constructionBlockOpsPerTick());out.writeInt(c.strategicDaysPerStep());
        out.writeDouble(c.crimeHeatDecayPerDay());out.writeDouble(c.reputationDecayPerDay());out.writeDouble(c.rebellionThreshold());out.writeDouble(c.borderDisputeThreshold());
    }

public static void readV9Config(DataInputStream in,SimulationState state)throws IOException{
        try{
            state.setConfig(new SimulationConfig(in.readDouble(),in.readDouble(),CodecIO.checkedCount(in.readInt(),100000,"wildlife budget"),CodecIO.checkedCount(in.readInt(),100000,"caravan budget"),CodecIO.checkedCount(in.readInt(),100000,"military budget"),CodecIO.checkedCount(in.readInt(),100000,"naval budget"),CodecIO.checkedCount(in.readInt(),1000000,"construction ops"),CodecIO.checkedCount(in.readInt(),365,"strategic days"),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble()));
        }catch(IllegalArgumentException ex){throw new IOException("invalid simulation config",ex);}
    }

}
