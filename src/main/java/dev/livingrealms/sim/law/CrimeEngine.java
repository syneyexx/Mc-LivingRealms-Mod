package dev.livingrealms.sim.law;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.player.PlayerAgencyConsequences;
import dev.livingrealms.sim.world.*;
import java.util.Objects;

/** Authoritative crime/bounty rules. Unwitnessed crimes intentionally do not create a bounty. */
public final class CrimeEngine {
    public CrimeResult report(SimulationState state,CrimeIncident incident){
        Objects.requireNonNull(state);Objects.requireNonNull(incident);
        if(state.findFaction(incident.jurisdictionFactionId()).isEmpty()) return new CrimeResult(false,0,0,WantedLevel.NONE,"unknown_jurisdiction");
        if(!incident.witnessed()||incident.witnessCount()==0) {
            // Unwitnessed theft still drains local property — heat/bounty require witnesses.
            PlayerAgencyConsequences.onCrime(state,incident);
        state.history().add(new WorldEvent(incident.day(),"crime_unreported",incident.type()+" actor="+incident.actorKey()
                    +", faction="+incident.jurisdictionFactionId()+", settlement_loss=1"));
            dev.livingrealms.api.LivingRealmsApi.publish(new dev.livingrealms.api.event.CrimeCommitted(
                    incident.day(),incident.jurisdictionFactionId(),incident.actorKey(),incident.type().name(),false,0));
            return new CrimeResult(false,0,0,state.crimeLedger().profile(incident.actorKey()).find(incident.jurisdictionFactionId()).map(JurisdictionWanted::wantedLevel).orElse(WantedLevel.NONE),"no_witness");
        }
        Faction jurisdiction=state.findFaction(incident.jurisdictionFactionId()).orElseThrow();
        double valueComponent=Math.min(500,incident.stolenOrDamageValue()*.65);
        double witnessMultiplier=1.0+Math.min(8,incident.witnessCount()-1)*.04;
        double enforcement=.70+jurisdiction.government().lawEnforcement()*.60;
        double bounty=(incident.type().baseBounty()+valueComponent)*witnessMultiplier*enforcement;
        double notoriety=incident.type().notoriety()*(.8+.2*Math.min(3,incident.witnessCount()));
        double heat=Math.min(100,incident.type().notoriety()*.85+incident.witnessCount()*2.5);
        WantedProfile profile=state.crimeLedger().profile(incident.actorKey());
        JurisdictionWanted wanted=profile.in(incident.jurisdictionFactionId());
        wanted.addCrime(bounty,notoriety,heat,incident.day(),incident.type().violent());
        profile.addInfamy(notoriety*(incident.type().violent()?0.65:0.30));
        state.crimeLedger().record(incident);
        state.history().add(new WorldEvent(incident.day(),"crime_reported","actor="+incident.actorKey()+", crime="+incident.type()+", faction="+incident.jurisdictionFactionId()+", bounty+="+Math.round(bounty)));
        // Property loss, victim/witness memory, public order — underworld observe stays in SimulationState.
        PlayerAgencyConsequences.onCrime(state,incident);
        dev.livingrealms.api.LivingRealmsApi.publish(new dev.livingrealms.api.event.CrimeCommitted(
                incident.day(),incident.jurisdictionFactionId(),incident.actorKey(),incident.type().name(),true,bounty));
        return new CrimeResult(true,bounty,notoriety,wanted.wantedLevel(),"witnessed");
    }

    public void simulateDay(SimulationState state,double heatDecay,double infamyDecay){
        for(WantedProfile profile:state.crimeLedger().profiles().values()){
            profile.decayInfamy(infamyDecay);
            for(JurisdictionWanted wanted:profile.jurisdictions().values()) wanted.decay(heatDecay,infamyDecay*.35);
        }
    }

    public double capture(SimulationState state,String actorKey,long factionId){
        WantedProfile profile=state.crimeLedger().findProfile(actorKey).orElse(null);if(profile==null)return 0;
        JurisdictionWanted wanted=profile.find(factionId).orElse(null);if(wanted==null)return 0;
        double bounty=wanted.captureAndClear();
        state.history().add(new WorldEvent(state.clock().day(),"criminal_captured","actor="+actorKey+", faction="+factionId+", bounty="+Math.round(bounty)));
        return bounty;
    }

    public double payFine(SimulationState state,String actorKey,long factionId,double offered){
        if(offered<0||!Double.isFinite(offered))throw new IllegalArgumentException("offered");
        WantedProfile profile=state.crimeLedger().findProfile(actorKey).orElse(null);if(profile==null)return 0;
        JurisdictionWanted wanted=profile.find(factionId).orElse(null);if(wanted==null)return 0;
        double paid=wanted.pay(offered);
        if(paid>0) state.findFaction(factionId).ifPresent(f->f.addTreasury(paid));
        return paid;
    }
}
