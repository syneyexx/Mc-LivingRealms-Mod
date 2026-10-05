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


/** World history event-log persistence. */
public final class HistoryCodec {
    private HistoryCodec() {}

public static void writeHistory(DataOutputStream out,SimulationState state)throws IOException{List<WorldEvent> history=state.history().all();out.writeInt(history.size());for(WorldEvent ev:history){out.writeLong(ev.day());CodecIO.writeString(out,ev.type());CodecIO.writeString(out,ev.message());}}

public static void readHistory(DataInputStream in,SimulationState state)throws IOException{int hc=CodecIO.checkedCount(in.readInt(),20000,"history");for(int i=0;i<hc;i++)state.history().add(new WorldEvent(in.readLong(),CodecIO.readString(in),CodecIO.readString(in)));}

}
