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


/** Strategic warfare, siege, naval and player-standing persistence blocks. */
public final class WarfareCodec {
    private WarfareCodec() {}

public static void writeV4Strategic(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.treaties().size());for(Treaty t:state.treaties()){out.writeLong(t.id());out.writeLong(t.factionA());out.writeLong(t.factionB());out.writeInt(t.type().ordinal());out.writeLong(t.startDay());out.writeLong(t.endDay());out.writeBoolean(t.active());}
        out.writeInt(state.wars().size());for(WarState w:state.wars()){out.writeLong(w.id());out.writeLong(w.attackerFactionId());out.writeLong(w.defenderFactionId());out.writeInt(w.goal().ordinal());out.writeLong(w.targetSettlementId());out.writeLong(w.startDay());out.writeDouble(w.attackerScore());out.writeDouble(w.attackerExhaustion());out.writeDouble(w.defenderExhaustion());out.writeBoolean(w.active());}
        out.writeInt(state.routes().size());for(TransportRoute r:state.routes()){out.writeLong(r.id());out.writeLong(r.ownerFactionId());out.writeLong(r.fromSettlementId());out.writeLong(r.toSettlementId());out.writeInt(r.mode().ordinal());out.writeDouble(r.distanceBlocks());out.writeDouble(r.quality());out.writeDouble(r.security());out.writeDouble(r.capacityPerDay());out.writeBoolean(r.operational());}
        out.writeInt(state.objectives().size());for(MilitaryObjective o:state.objectives()){out.writeLong(o.id());out.writeLong(o.armyId());out.writeLong(o.ownerFactionId());out.writeInt(o.type().ordinal());out.writeLong(o.targetFactionId());out.writeLong(o.targetSettlementId());out.writeDouble(o.targetPosition().x());out.writeDouble(o.targetPosition().z());out.writeLong(o.createdDay());out.writeInt(o.priority());out.writeBoolean(o.complete());}
        out.writeInt(state.sieges().size());for(SiegeState s:state.sieges()){out.writeLong(s.id());out.writeLong(s.attackerFactionId());out.writeLong(s.defenderFactionId());out.writeLong(s.settlementId());out.writeLong(s.startDay());out.writeDouble(s.progress());out.writeDouble(s.blockade());out.writeBoolean(s.active());}
        out.writeInt(state.airWings().size());for(AirWing w:state.airWings()){out.writeLong(w.id());out.writeLong(w.factionId());out.writeInt(w.model().ordinal());out.writeInt(w.aircraft());CodecIO.writePosition(out,w.position());CodecIO.writePosition(out,w.basePosition());CodecIO.writePosition(out,w.targetPosition());out.writeDouble(w.fuel());out.writeDouble(w.readiness());out.writeDouble(w.experience());out.writeInt(w.mission().ordinal());}
        out.writeInt(state.bounties().size());for(BountyContract b:state.bounties()){out.writeLong(b.id());CodecIO.writeString(out,b.actorKey());out.writeLong(b.issuerFactionId());out.writeLong(b.createdDay());out.writeDouble(b.reward());out.writeInt(b.status().ordinal());CodecIO.writeString(out,b.hunterKey());}
        LawCodec.writeCrimeLedger(out,state.crimeLedger());
    }

public static void readV4Strategic(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),100000,"treaties");for(int i=0;i<n;i++){Treaty t=new Treaty(in.readLong(),in.readLong(),in.readLong(),TreatyType.values()[CodecIO.enumOrdinal(in.readInt(),TreatyType.values().length,"treaty")],in.readLong(),in.readLong());t.restoreActive(in.readBoolean());state.addTreaty(t);}
        n=CodecIO.checkedCount(in.readInt(),100000,"wars");for(int i=0;i<n;i++){WarState w=new WarState(in.readLong(),in.readLong(),in.readLong(),WarGoalType.values()[CodecIO.enumOrdinal(in.readInt(),WarGoalType.values().length,"war goal")],in.readLong(),in.readLong());w.restore(in.readDouble(),in.readDouble(),in.readDouble(),in.readBoolean());state.addWar(w);}
        n=CodecIO.checkedCount(in.readInt(),1000000,"routes");for(int i=0;i<n;i++){long id=in.readLong(),owner=in.readLong(),from=in.readLong(),to=in.readLong();TransportMode mode=TransportMode.values()[CodecIO.enumOrdinal(in.readInt(),TransportMode.values().length,"transport mode")];double distance=in.readDouble();TransportRoute r=new TransportRoute(id,owner,from,to,mode,distance,in.readDouble(),in.readDouble(),in.readDouble());r.setOperational(in.readBoolean());state.addRoute(r);}
        n=CodecIO.checkedCount(in.readInt(),1000000,"objectives");for(int i=0;i<n;i++){MilitaryObjective o=new MilitaryObjective(in.readLong(),in.readLong(),in.readLong(),MilitaryObjectiveType.values()[CodecIO.enumOrdinal(in.readInt(),MilitaryObjectiveType.values().length,"objective")],in.readLong(),in.readLong(),new SimPosition(in.readDouble(),in.readDouble()),in.readLong(),in.readInt());o.restoreComplete(in.readBoolean());state.addObjective(o);}
        n=CodecIO.checkedCount(in.readInt(),100000,"sieges");for(int i=0;i<n;i++){SiegeState s=new SiegeState(in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());s.restore(in.readDouble(),in.readDouble(),in.readBoolean());state.addSiege(s);}
        n=CodecIO.checkedCount(in.readInt(),100000,"air wings");for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong();AircraftModel model=AircraftModel.values()[CodecIO.enumOrdinal(in.readInt(),AircraftModel.values().length,"aircraft model")];int aircraft=in.readInt();SimPosition pos=CodecIO.readPosition(in),base=CodecIO.readPosition(in),target=CodecIO.readPosition(in);double fuel=in.readDouble(),readiness=in.readDouble(),experience=in.readDouble();AirMission mission=AirMission.values()[CodecIO.enumOrdinal(in.readInt(),AirMission.values().length,"air mission")];AirWing w=new AirWing(id,faction,model,Math.max(1,aircraft),base);w.restore(aircraft,pos,base,target,fuel,readiness,experience,mission);state.addAirWing(w);}
        n=CodecIO.checkedCount(in.readInt(),1000000,"bounties");for(int i=0;i<n;i++){BountyContract b=new BountyContract(in.readLong(),CodecIO.readString(in),in.readLong(),in.readLong(),in.readDouble());BountyContract.Status status=BountyContract.Status.values()[CodecIO.enumOrdinal(in.readInt(),BountyContract.Status.values().length,"bounty status")];b.restore(status,CodecIO.readString(in));state.addBounty(b);}
        LawCodec.readCrimeLedger(in,state.crimeLedger());
    }

public static void writeV6NavalAndPlayers(DataOutputStream out,SimulationState state)throws IOException{
        out.writeInt(state.ports().size());
        for(PortState p:state.ports()){out.writeLong(p.id());out.writeLong(p.factionId());out.writeLong(p.settlementId());CodecIO.writePosition(out,p.position());out.writeInt(p.level());out.writeDouble(p.condition());out.writeDouble(p.security());out.writeBoolean(p.operational());}
        out.writeInt(state.fleets().size());
        for(Fleet f:state.fleets()){out.writeLong(f.id());out.writeLong(f.factionId());out.writeLong(f.homePortId());for(ShipClass c:ShipClass.values())out.writeInt(f.count(c));CodecIO.writePosition(out,f.position());CodecIO.writePosition(out,f.targetPosition());out.writeInt(f.mission().ordinal());out.writeDouble(f.fuel());out.writeDouble(f.readiness());out.writeDouble(f.experience());out.writeDouble(f.supply());out.writeInt(f.embarkedPersonnel());}
        var standings=new ArrayList<>(state.playerStandings().values());standings.sort(Comparator.comparing(PlayerStanding::actorKey));out.writeInt(standings.size());
        for(PlayerStanding ps:standings){CodecIO.writeString(out,ps.actorKey());var reps=new ArrayList<>(ps.reputations().entrySet());reps.sort(Map.Entry.comparingByKey());out.writeInt(reps.size());for(var e:reps){out.writeLong(e.getKey());out.writeDouble(e.getValue());}out.writeLong(ps.memberFactionId());out.writeInt(ps.rank().ordinal());out.writeLong(ps.joinedDay());out.writeDouble(ps.servicePoints());out.writeInt(ps.expulsions());}
    }

public static void readV6NavalAndPlayers(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),100000,"ports");
        for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),settlement=in.readLong();SimPosition pos=CodecIO.readPosition(in);int level=in.readInt();double condition=in.readDouble(),security=in.readDouble();boolean operational=in.readBoolean();PortState port=new PortState(id,faction,settlement,pos,level);port.restore(level,condition,security,operational);state.addPort(port);}
        n=CodecIO.checkedCount(in.readInt(),100000,"fleets");
        for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),home=in.readLong();EnumMap<ShipClass,Integer> ships=new EnumMap<>(ShipClass.class);for(ShipClass c:ShipClass.values())ships.put(c,CodecIO.checkedCount(in.readInt(),100000,"ship count"));SimPosition pos=CodecIO.readPosition(in),target=CodecIO.readPosition(in);NavalMission mission=NavalMission.values()[CodecIO.enumOrdinal(in.readInt(),NavalMission.values().length,"naval mission")];double fuel=in.readDouble(),readiness=in.readDouble(),experience=in.readDouble(),supply=in.readDouble();int embarked=CodecIO.checkedCount(in.readInt(),10000000,"embarked personnel");ShipClass seed=ShipClass.PATROL_BOAT;int count=Math.max(1,ships.values().stream().mapToInt(Integer::intValue).sum());for(ShipClass c:ShipClass.values())if(ships.getOrDefault(c,0)>0){seed=c;break;}Fleet fleet=new Fleet(id,faction,home,pos,seed,count);fleet.restore(ships,pos,home,target,mission,fuel,readiness,experience,supply,embarked);state.addFleet(fleet);}
        n=CodecIO.checkedCount(in.readInt(),1000000,"player standings");
        for(int i=0;i<n;i++){PlayerStanding ps=new PlayerStanding(CodecIO.readString(in));int rc=CodecIO.checkedCount(in.readInt(),100000,"player reputations");for(int j=0;j<rc;j++)ps.restoreReputation(in.readLong(),in.readDouble());long member=in.readLong();FactionRank rank=FactionRank.values()[CodecIO.enumOrdinal(in.readInt(),FactionRank.values().length,"faction rank")];long joined=in.readLong();double service=in.readDouble();int expulsions=CodecIO.checkedCount(in.readInt(),100000,"expulsions");ps.restoreMembership(member,rank,joined,service,expulsions);state.restorePlayerStanding(ps);}
    }

public static void writeV15SiegeEquipment(DataOutputStream out,SimulationState state)throws IOException{
        var sieges=new ArrayList<>(state.sieges());sieges.sort(Comparator.comparingLong(SiegeState::id));out.writeInt(sieges.size());for(SiegeState s:sieges){out.writeLong(s.id());out.writeInt(s.rams());out.writeInt(s.ladders());out.writeInt(s.artilleryPieces());out.writeDouble(s.breach());out.writeDouble(s.defenderCountermeasures());}
    }

public static void readV15SiegeEquipment(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),100000,"siege equipment");for(int i=0;i<n;i++){long id=in.readLong();int rams=CodecIO.checkedCount(in.readInt(),10000,"siege rams"),ladders=CodecIO.checkedCount(in.readInt(),100000,"siege ladders"),artillery=CodecIO.checkedCount(in.readInt(),10000,"siege artillery");double breach=in.readDouble(),counter=in.readDouble();SiegeState siege=state.sieges().stream().filter(s->s.id()==id).findFirst().orElseThrow(()->new IOException("siege equipment references missing siege "+id));siege.restoreEquipment(rams,ladders,artillery,breach,counter);}
    }

}
