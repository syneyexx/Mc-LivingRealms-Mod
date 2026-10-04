package dev.livingrealms.sim.government;

import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.ArrayList;

/** Borrowing, interest service, and default consequences for faction treasuries. */
public final class SovereignDebtEngine {
    private static final double LOW_TREASURY=120;
    private static final double MONTHLY_SERVICE_FRACTION=1.0/30.0;

    public void simulateDay(SimulationState state,DeterministicRng rng){
        maybeBorrow(state,rng);
        serviceAndDefault(state);
    }

    private static void maybeBorrow(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(Faction faction:state.factions()){
            if(state.debts().stream().filter(d->d.active()&&d.debtorFactionId()==faction.id()).count()>=4)continue;
            boolean atWar=state.wars().stream().anyMatch(w->w.active()&&w.involves(faction.id()));
            boolean projectNeed=state.grandProjects().stream().anyMatch(p->p.active()&&!p.complete()&&p.sponsorFactionId()==faction.id()&&p.pauseReason().contains("treasury"));
            if(faction.treasury()>LOW_TREASURY&&!atWar&&!projectNeed)continue;
            if(faction.treasury()>LOW_TREASURY*2.5)continue;
            if(day%17!=Math.floorMod(faction.id(),17))continue;
            double principal=80+rng.nextDouble()*220+(atWar?120:0)+(projectNeed?90:0);
            principal=Math.min(principal,Math.max(40,faction.population()*.04));
            double rate=.04+rng.nextDouble()*.08+(atWar?.03:0)+Math.max(0,.12-faction.government().legitimacy())*.2;
            double risk=Math.min(1,.18+(1-faction.government().stability())*.45+(atWar?.15:0));
            String creditor=rng.chance(.55)?"merchant:house_"+Math.floorMod(faction.id()*31L+day,97):"faction:"+peerCreditor(state,faction,rng);
            if(creditor.endsWith(":0"))creditor="merchant:house_"+Math.floorMod(faction.id()+day,61);
            long due=day+180+rng.nextInt(240);
            if(state.debts().size()>=SimulationState.MAX_DEBTS)continue;
            SovereignDebt debt=new SovereignDebt(state.nextId(),faction.id(),creditor,principal,rate,day,due,risk);
            state.addDebt(debt);faction.addTreasury(principal);
            state.history().add(new WorldEvent(day,"sovereign_borrow","faction="+faction.id()+", principal="+String.format(java.util.Locale.ROOT,"%.1f",principal)+", creditor="+creditor));
        }
    }

    private static long peerCreditor(SimulationState state,Faction debtor,DeterministicRng rng){
        for(Faction other:state.factions()){
            if(other.id()==debtor.id()||other.treasury()<200)continue;
            var rel=debtor.relations().get(other.id());
            if(rel!=null&&rel.status()!=RelationStatus.WAR&&rel.opinion()>-20&&rng.chance(.4))return other.id();
        }
        return 0;
    }

    private static void serviceAndDefault(SimulationState state){
        long day=state.clock().day();
        for(SovereignDebt debt:new ArrayList<>(state.debts())){
            if(!debt.active()||debt.defaulted())continue;
            Faction faction=state.findFaction(debt.debtorFactionId()).orElse(null);
            if(faction==null){debt.markDefaulted();continue;}
            debt.accrueInterest(MONTHLY_SERVICE_FRACTION/12.0);
            double dueToday=Math.max(0.5,debt.remaining()*debt.interestRate()/365.0+debt.principal()/(Math.max(30,debt.dueDay()-debt.createdDay())));
            if(faction.treasury()>=dueToday){
                double paid=debt.service(Math.min(faction.treasury(),dueToday));faction.addTreasury(-paid);
            }else if(day>=debt.dueDay()||(faction.treasury()<dueToday*.25&&debt.risk()>.55)){
                defaultDebt(state,faction,debt);
            }else{
                double partial=Math.min(faction.treasury(),dueToday*.35);
                if(partial>0){debt.service(partial);faction.addTreasury(-partial);}
                debt.restore(debt.remaining(),Math.min(1,debt.risk()+.01),false,true);
            }
        }
    }

    private static void defaultDebt(SimulationState state,Faction faction,SovereignDebt debt){
        debt.markDefaulted();
        faction.government().adjustLegitimacy(-.08);faction.government().adjustStability(-.05);
        for(Settlement s:faction.settlements())s.adjustUnrest(.04);
        if(debt.creditorKey().startsWith("faction:")){
            try{
                long other=Long.parseLong(debt.creditorKey().substring("faction:".length()));
                Faction peer=state.findFaction(other).orElse(null);
                if(peer!=null){faction.relationWith(other).adjust(-18);peer.relationWith(faction.id()).adjust(-12);}
            }catch(NumberFormatException ignored){}
        }else{
            for(var e:faction.relations().entrySet())if(e.getValue().tradeAgreement())e.getValue().adjust(-6);
        }
        state.history().add(new WorldEvent(state.clock().day(),"sovereign_default","faction="+faction.id()+", debt="+debt.id()+", remaining="+String.format(java.util.Locale.ROOT,"%.1f",debt.remaining())));
    }
}
