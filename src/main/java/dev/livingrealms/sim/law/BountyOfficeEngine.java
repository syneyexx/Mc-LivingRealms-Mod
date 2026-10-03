package dev.livingrealms.sim.law;

import dev.livingrealms.sim.world.*;
import java.util.*;

/** Turns sufficiently large local bounties into persistent contracts. */
public final class BountyOfficeEngine {
    public void simulateDay(SimulationState state){
        for(WantedProfile profile:state.crimeLedger().profiles().values())for(JurisdictionWanted wanted:profile.jurisdictions().values()){
            if(wanted.bounty()<25)continue;
            BountyContract existing=state.bounties().stream().filter(b->(b.status()==BountyContract.Status.OPEN||b.status()==BountyContract.Status.ASSIGNED)&&b.actorKey().equals(profile.actorKey())&&b.issuerFactionId()==wanted.factionId()).findFirst().orElse(null);
            if(existing==null){BountyContract c=new BountyContract(state.nextId(),profile.actorKey(),wanted.factionId(),state.clock().day(),wanted.bounty());state.addBounty(c);state.history().add(new WorldEvent(state.clock().day(),"bounty_posted","actor="+profile.actorKey()+", faction="+wanted.factionId()+", reward="+Math.round(wanted.bounty())));}else existing.updateReward(wanted.bounty());
        }
        for(BountyContract contract:state.bounties())if(contract.status()!=BountyContract.Status.CLAIMED&&contract.status()!=BountyContract.Status.CANCELLED){WantedProfile p=state.crimeLedger().findProfile(contract.actorKey()).orElse(null);double remaining=p==null?0:p.find(contract.issuerFactionId()).map(JurisdictionWanted::bounty).orElse(0.0);if(remaining<=.001)contract.cancel();}
    }

    public BountyAssignment accept(SimulationState state,long contractId,String hunterKey){
        if(hunterKey==null||hunterKey.isBlank())return new BountyAssignment(false,contractId,hunterKey,"invalid_hunter");
        BountyContract contract=state.bounties().stream().filter(b->b.id()==contractId).findFirst().orElse(null);
        if(contract==null)return new BountyAssignment(false,contractId,hunterKey,"unknown_contract");
        if(contract.actorKey().equals(hunterKey))return new BountyAssignment(false,contractId,hunterKey,"cannot_hunt_self");
        if(contract.status()!=BountyContract.Status.OPEN)return new BountyAssignment(false,contractId,hunterKey,"contract_not_open");
        boolean alreadyAssigned=state.bounties().stream().anyMatch(b->b.status()==BountyContract.Status.ASSIGNED&&b.hunterKey().equals(hunterKey));
        if(alreadyAssigned)return new BountyAssignment(false,contractId,hunterKey,"hunter_already_has_contract");
        double outstanding=state.crimeLedger().findProfile(contract.actorKey()).flatMap(p->p.find(contract.issuerFactionId())).map(JurisdictionWanted::bounty).orElse(0.0);
        if(outstanding<=.001){contract.cancel();return new BountyAssignment(false,contractId,hunterKey,"target_no_longer_wanted");}
        if(!contract.assign(hunterKey))return new BountyAssignment(false,contractId,hunterKey,"assignment_failed");
        state.history().add(new WorldEvent(state.clock().day(),"bounty_accepted","contract="+contract.id()+", hunter="+hunterKey));
        return new BountyAssignment(true,contract.id(),hunterKey,"accepted");
    }

    public BountyAssignment abandon(SimulationState state,long contractId,String hunterKey){
        BountyContract contract=state.bounties().stream().filter(b->b.id()==contractId).findFirst().orElse(null);
        if(contract==null)return new BountyAssignment(false,contractId,hunterKey,"unknown_contract");
        if(!contract.unassign(hunterKey))return new BountyAssignment(false,contractId,hunterKey,"not_assigned_to_hunter");
        state.history().add(new WorldEvent(state.clock().day(),"bounty_abandoned","contract="+contract.id()+", hunter="+hunterKey));
        return new BountyAssignment(true,contract.id(),hunterKey,"abandoned");
    }

    public BountyClaim claim(SimulationState state,long contractId,String hunterKey){
        if(hunterKey==null||hunterKey.isBlank()) return new BountyClaim(false,contractId,"",hunterKey,0,0,0,"invalid_hunter");
        BountyContract contract=state.bounties().stream().filter(b->b.id()==contractId).findFirst().orElse(null);
        if(contract==null) return new BountyClaim(false,contractId,"",hunterKey,0,0,0,"unknown_contract");
        if(contract.status()==BountyContract.Status.CLAIMED||contract.status()==BountyContract.Status.CANCELLED) return new BountyClaim(false,contract.id(),contract.actorKey(),hunterKey,contract.issuerFactionId(),contract.reward(),0,"closed_contract");
        if(contract.status()==BountyContract.Status.ASSIGNED&&!contract.hunterKey().equals(hunterKey)) return new BountyClaim(false,contract.id(),contract.actorKey(),hunterKey,contract.issuerFactionId(),contract.reward(),0,"assigned_to_other_hunter");
        double outstanding=state.crimeLedger().findProfile(contract.actorKey()).flatMap(p->p.find(contract.issuerFactionId())).map(JurisdictionWanted::bounty).orElse(0.0);
        if(outstanding<=.001){contract.cancel();return new BountyClaim(false,contract.id(),contract.actorKey(),hunterKey,contract.issuerFactionId(),contract.reward(),0,"target_no_longer_wanted");}
        if(contract.status()==BountyContract.Status.OPEN) contract.assign(hunterKey);
        double cleared=state.captureCriminal(contract.actorKey(),contract.issuerFactionId());
        if(cleared<=0){contract.cancel();return new BountyClaim(false,contract.id(),contract.actorKey(),hunterKey,contract.issuerFactionId(),contract.reward(),0,"capture_failed");}
        double paid=state.findFaction(contract.issuerFactionId()).map(f->{double v=Math.min(f.treasury(),contract.reward());f.addTreasury(-v);return v;}).orElse(0.0);
        contract.claim();
        state.history().add(new WorldEvent(state.clock().day(),"bounty_claimed","contract="+contract.id()+", actor="+contract.actorKey()+", hunter="+hunterKey+", paid="+Math.round(paid)));
        return new BountyClaim(true,contract.id(),contract.actorKey(),hunterKey,contract.issuerFactionId(),contract.reward(),paid,paid+1e-9<contract.reward()?"treasury_shortfall":"paid");
    }

}
