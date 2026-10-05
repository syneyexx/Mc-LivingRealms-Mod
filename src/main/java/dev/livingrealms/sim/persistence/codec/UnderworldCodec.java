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


/** Underworld contract and stolen-goods persistence (schema 20). */
public final class UnderworldCodec {
    private UnderworldCodec() {}

public static void writeV20UnderworldContracts(DataOutputStream out,SimulationState state)throws IOException{
        List<UnderworldContract> contracts=new ArrayList<>(state.underworldContracts());
        contracts.sort(Comparator.comparingLong(UnderworldContract::id));
        out.writeInt(contracts.size());
        for(UnderworldContract c:contracts){
            out.writeLong(c.id());
            out.writeInt(c.type().ordinal());
            out.writeInt(c.status().ordinal());
            out.writeLong(c.jurisdictionFactionId());
            CodecIO.writeString(out,c.targetVictimKey());
            out.writeDouble(c.minValue());
            out.writeDouble(c.reward());
            out.writeLong(c.createdDay());
            out.writeLong(c.expiresDay());
            CodecIO.writeString(out,c.acceptorActorKey());
            out.writeLong(c.acceptedDay());
            out.writeLong(c.closedDay());
            out.writeLong(c.matchingCrimeId());
        }
        List<StolenGoodsEntry> goods=new ArrayList<>(state.stolenGoodsLedger().entries());
        goods.sort(Comparator.comparingLong(StolenGoodsEntry::id));
        out.writeInt(goods.size());
        for(StolenGoodsEntry e:goods){
            out.writeLong(e.id());
            CodecIO.writeString(out,e.actorKey());
            CodecIO.writeString(out,e.goodKey());
            out.writeDouble(e.value());
            out.writeInt(e.quantity());
            out.writeLong(e.sourceCrimeId());
            out.writeLong(e.acquiredDay());
            out.writeBoolean(e.sold());
            out.writeLong(e.soldDay());
            out.writeDouble(e.salePrice());
        }
    }

public static void readV20UnderworldContracts(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_UNDERWORLD_CONTRACTS,"underworld contracts");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            UnderworldContractType type=UnderworldContractType.values()[CodecIO.enumOrdinal(in.readInt(),UnderworldContractType.values().length,"underworld contract type")];
            UnderworldContract.Status status=UnderworldContract.Status.values()[CodecIO.enumOrdinal(in.readInt(),UnderworldContract.Status.values().length,"underworld contract status")];
            long jurisdiction=in.readLong();
            String target=CodecIO.readString(in);
            double minValue=in.readDouble();
            double reward=in.readDouble();
            long created=in.readLong();
            long expires=in.readLong();
            String acceptor=CodecIO.readString(in);
            long acceptedDay=in.readLong();
            long closedDay=in.readLong();
            long matchingCrime=in.readLong();
            UnderworldContract contract=new UnderworldContract(id,type,jurisdiction,target,minValue,reward,created,expires);
            contract.restore(status,acceptor,acceptedDay,closedDay,matchingCrime);
            state.addUnderworldContract(contract);
        }
        n=CodecIO.checkedCount(in.readInt(),StolenGoodsLedger.DEFAULT_MAX_ENTRIES,"stolen goods");
        for(int i=0;i<n;i++){
            long id=in.readLong();
            String actor=CodecIO.readString(in);
            String goodKey=CodecIO.readString(in);
            double value=in.readDouble();
            int quantity=in.readInt();
            long sourceCrime=in.readLong();
            long acquired=in.readLong();
            boolean sold=in.readBoolean();
            long soldDay=in.readLong();
            double salePrice=in.readDouble();
            StolenGoodsEntry entry=new StolenGoodsEntry(id,actor,goodKey,value,quantity,sourceCrime,acquired);
            entry.restore(sold,soldDay,salePrice);
            state.stolenGoodsLedger().restore(entry);
        }
    }

}
