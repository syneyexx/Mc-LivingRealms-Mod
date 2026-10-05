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


/** Final-product persistence block (citizen social extensions, influence, debts, projects, plans, shipment logistics). */
public final class ConstructionCodec {
    private ConstructionCodec() {}

public static void writeV17FinalProduct(DataOutputStream out,SimulationState state)throws IOException{
        var citizens=new ArrayList<>(state.socialCitizens());citizens.sort(Comparator.comparingLong(SocialCitizen::id));
        out.writeInt(citizens.size());
        for(SocialCitizen c:citizens){
            out.writeLong(c.id());out.writeLong(c.appearancePacked());CodecIO.writeString(out,c.cultureKey());CodecIO.writeString(out,c.faithKey());
            out.writeInt(c.socialClass().ordinal());out.writeDouble(c.education());CodecIO.writeString(out,c.workplaceKey());
            out.writeInt(c.wealthClass().ordinal());out.writeDouble(c.personalInfluence());CodecIO.writeString(out,c.professionHistoryKey());
            out.writeInt(c.employmentStatus().ordinal());
        }
        var standings=new ArrayList<>(state.playerStandings().values());standings.sort(Comparator.comparing(PlayerStanding::actorKey));
        out.writeInt(standings.size());
        for(PlayerStanding ps:standings){
            CodecIO.writeString(out,ps.actorKey());
            var factions=new ArrayList<>(ps.influences().entrySet());factions.sort(Map.Entry.comparingByKey());
            out.writeInt(factions.size());
            for(var fe:factions){
                out.writeLong(fe.getKey());
                var inst=new ArrayList<>(fe.getValue().entrySet());inst.sort(Comparator.comparing(e->e.getKey().ordinal()));
                out.writeInt(inst.size());for(var ie:inst){out.writeInt(ie.getKey().ordinal());out.writeDouble(ie.getValue());}
            }
            out.writeInt(ps.careerTrack().ordinal());out.writeInt(ps.careerRankIndex());out.writeDouble(ps.careerService());
        }
        var debts=new ArrayList<>(state.debts());debts.sort(Comparator.comparingLong(SovereignDebt::id));
        out.writeInt(debts.size());
        for(SovereignDebt d:debts){out.writeLong(d.id());out.writeLong(d.debtorFactionId());CodecIO.writeString(out,d.creditorKey());out.writeDouble(d.principal());out.writeDouble(d.interestRate());out.writeDouble(d.remaining());out.writeLong(d.createdDay());out.writeLong(d.dueDay());out.writeDouble(d.risk());out.writeBoolean(d.defaulted());out.writeBoolean(d.active());}
        var projects=new ArrayList<>(state.grandProjects());projects.sort(Comparator.comparingLong(GrandProject::id));
        out.writeInt(projects.size());
        for(GrandProject p:projects){out.writeLong(p.id());out.writeLong(p.sponsorFactionId());out.writeLong(p.settlementId());out.writeInt(p.type().ordinal());out.writeLong(p.createdDay());out.writeDouble(p.progress());out.writeInt(p.phase().ordinal());CodecIO.writeString(out,p.pauseReason());out.writeBoolean(p.complete());out.writeBoolean(p.active());out.writeDouble(p.spentTreasury());out.writeDouble(p.spentFood());out.writeDouble(p.spentTimber());out.writeDouble(p.spentStone());out.writeDouble(p.spentIron());out.writeInt(p.laborApplied());out.writeBoolean(p.effectInfrastructure());out.writeBoolean(p.effectProsperity());out.writeBoolean(p.effectLegitimacy());out.writeBoolean(p.effectLegend());}
        var plans=new ArrayList<>(state.campaignPlans());plans.sort(Comparator.comparingLong(CampaignPlan::id));
        out.writeInt(plans.size());
        for(CampaignPlan p:plans){out.writeLong(p.id());out.writeLong(p.factionId());out.writeLong(p.warId());out.writeInt(p.type().ordinal());out.writeLong(p.targetSettlementId());out.writeInt(p.priority());out.writeLong(p.createdDay());out.writeBoolean(p.active());}
        var shipments=new ArrayList<>(state.shipments());shipments.sort(Comparator.comparingLong(TradeShipment::id));
        out.writeInt(shipments.size());
        for(TradeShipment s:shipments){out.writeLong(s.id());out.writeLong(s.originSettlementId());out.writeLong(s.destinationSettlementId());out.writeLong(s.routeId());out.writeInt(s.transportModeOrdinal());out.writeLong(s.departureDay());out.writeLong(s.expectedArrivalDay());out.writeDouble(s.risk());out.writeDouble(s.escortStrength());out.writeInt(s.lossState().ordinal());out.writeInt(s.delayDays());}
    }

public static void readV17FinalProduct(DataInputStream in,SimulationState state)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),100000,"citizen appearance/social");
        for(int i=0;i<n;i++){
            long id=in.readLong();long packed=in.readLong();String culture=CodecIO.readString(in),faith=CodecIO.readString(in);
            SocialClass socialClass=SocialClass.values()[CodecIO.enumOrdinal(in.readInt(),SocialClass.values().length,"social class")];
            double education=in.readDouble();String workplace=CodecIO.readString(in);
            SocialClass wealth=SocialClass.values()[CodecIO.enumOrdinal(in.readInt(),SocialClass.values().length,"wealth class")];
            double influence=in.readDouble();String historyKey=CodecIO.readString(in);
            EmploymentStatus employment=EmploymentStatus.values()[CodecIO.enumOrdinal(in.readInt(),EmploymentStatus.values().length,"employment")];
            SocialCitizen c=state.findSocialCitizen(id).orElseThrow(()->new IOException("unknown citizen in schema17 social: "+id));
            try{c.restoreSocialExtensions(culture,faith,socialClass,education,workplace,wealth,influence,packed,historyKey,employment);}catch(IllegalArgumentException bad){throw new IOException("invalid citizen social extensions "+id,bad);}
        }
        n=CodecIO.checkedCount(in.readInt(),1000000,"player influence/career");
        for(int i=0;i<n;i++){
            String actorKey=CodecIO.readString(in);PlayerStanding ps=state.playerStanding(actorKey);
            int fc=CodecIO.checkedCount(in.readInt(),100000,"influence factions");
            for(int j=0;j<fc;j++){long factionId=in.readLong();int ic=CodecIO.checkedCount(in.readInt(),InfluenceInstitution.values().length,"influence institutions");for(int k=0;k<ic;k++){InfluenceInstitution inst=InfluenceInstitution.values()[CodecIO.enumOrdinal(in.readInt(),InfluenceInstitution.values().length,"influence institution")];ps.restoreInfluence(factionId,inst,in.readDouble());}}
            CareerTrack track=CareerTrack.values()[CodecIO.enumOrdinal(in.readInt(),CareerTrack.values().length,"career track")];int rankIdx=CodecIO.checkedCount(in.readInt(),CareerRank.ranks(track).length-1,"career rank");double service=in.readDouble();
            try{ps.restoreCareer(track,rankIdx,service);}catch(IllegalArgumentException bad){throw new IOException("invalid career for "+actorKey,bad);}
        }
        n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_DEBTS,"sovereign debts");
        for(int i=0;i<n;i++){long id=in.readLong(),debtor=in.readLong();String creditor=CodecIO.readString(in);double principal=in.readDouble(),rate=in.readDouble(),remaining=in.readDouble();long created=in.readLong(),due=in.readLong();double risk=in.readDouble();boolean defaulted=in.readBoolean(),active=in.readBoolean();SovereignDebt debt=new SovereignDebt(id,debtor,creditor,principal,rate,created,due,risk);debt.restore(remaining,risk,defaulted,active);state.addDebt(debt);}
        n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_GRAND_PROJECTS,"grand projects");
        for(int i=0;i<n;i++){long id=in.readLong(),sponsor=in.readLong(),settlement=in.readLong();GrandProjectType type=GrandProjectType.values()[CodecIO.enumOrdinal(in.readInt(),GrandProjectType.values().length,"grand project type")];long created=in.readLong();double progress=in.readDouble();ConstructionPhase phase=ConstructionPhase.values()[CodecIO.enumOrdinal(in.readInt(),ConstructionPhase.values().length,"project phase")];String pause=CodecIO.readString(in);boolean complete=in.readBoolean(),active=in.readBoolean();double spentTreasury=in.readDouble(),spentFood=in.readDouble(),spentTimber=in.readDouble(),spentStone=in.readDouble(),spentIron=in.readDouble();int labor=CodecIO.checkedCount(in.readInt(),100000000,"project labor");boolean ei=in.readBoolean(),ep=in.readBoolean(),el=in.readBoolean(),elegend=in.readBoolean();GrandProject project=new GrandProject(id,sponsor,settlement,type,created);project.restore(progress,phase,pause,complete,active,spentTreasury,spentFood,spentTimber,spentStone,spentIron,labor,ei,ep,el,elegend);state.addGrandProject(project);}
        n=CodecIO.checkedCount(in.readInt(),SimulationState.MAX_CAMPAIGN_PLANS,"campaign plans");
        for(int i=0;i<n;i++){long id=in.readLong(),faction=in.readLong(),war=in.readLong();CampaignPlanType type=CampaignPlanType.values()[CodecIO.enumOrdinal(in.readInt(),CampaignPlanType.values().length,"campaign plan type")];long target=in.readLong();int priority=CodecIO.checkedCount(in.readInt(),1000000,"campaign priority");long created=in.readLong();boolean active=in.readBoolean();CampaignPlan plan=new CampaignPlan(id,faction,war,type,target,priority,created);plan.restore(priority,active);state.addCampaignPlan(plan);}
        n=CodecIO.checkedCount(in.readInt(),1000000,"shipment logistics");
        for(int i=0;i<n;i++){long id=in.readLong();long origin=in.readLong(),dest=in.readLong(),route=in.readLong();int mode=in.readInt();long dep=in.readLong(),eta=in.readLong();double risk=in.readDouble(),escort=in.readDouble();TradeShipment.LossState loss=TradeShipment.LossState.values()[CodecIO.enumOrdinal(in.readInt(),TradeShipment.LossState.values().length,"shipment loss")];int delay=CodecIO.checkedCount(in.readInt(),100000,"shipment delay");TradeShipment shipment=state.findShipment(id).orElseThrow(()->new IOException("shipment logistics missing shipment "+id));try{shipment.restoreLogistics(origin,dest,route,mode,dep,eta,risk,escort,loss,delay);}catch(IllegalArgumentException bad){throw new IOException("invalid shipment logistics "+id,bad);}
        }
    }

}
