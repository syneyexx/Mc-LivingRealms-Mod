package dev.livingrealms.sim.world;

import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.underworld.UnderworldActions;
import java.util.Optional;

/**
 * Player/crime/bounty façade implementation extracted from {@link SimulationState}.
 * SimulationState keeps the stable public API while this service owns cross-law orchestration.
 */
final class PlayerLawService {
    private PlayerLawService() {}

    static CrimeResult reportCrime(SimulationState state,String actorKey,long jurisdictionFactionId,
                                   CrimeType type,double value,SimPosition position,boolean witnessed,
                                   int witnessCount,String victimKey,String evidence){
        CrimeIncident incident=new CrimeIncident(state.nextId(),state.clock().day(),actorKey,
                jurisdictionFactionId,type,value,position,witnessed,witnessCount,victimKey,evidence);
        CrimeResult result=state.crimeEngine().report(state,incident);
        state.reputationEngine().onCrime(state,incident,result);
        if(result.registered())UnderworldActions.observeCrime(state,incident);
        return result;
    }

    static double captureCriminal(SimulationState state,String actorKey,long factionId){
        return state.crimeEngine().capture(state,actorKey,factionId);
    }

    static double payFine(SimulationState state,String actorKey,long factionId,double amount){
        return state.crimeEngine().payFine(state,actorKey,factionId,amount);
    }

    static LawResponse lawResponse(SimulationState state,String actorKey,long factionId){
        return state.lawEnforcementEngine().response(state,actorKey,factionId);
    }

    static ArrestOutcome arrestCriminal(SimulationState state,String actorKey,long factionId,String reason){
        return state.lawEnforcementEngine().arrest(state,actorKey,factionId,reason);
    }

    static BountyClaim claimBounty(SimulationState state,long contractId,String hunterKey){
        return state.bountyOfficeEngine().claim(state,contractId,hunterKey);
    }

    static BountyAssignment acceptBounty(SimulationState state,long contractId,String hunterKey){
        return state.bountyOfficeEngine().accept(state,contractId,hunterKey);
    }

    static BountyAssignment abandonBounty(SimulationState state,long contractId,String hunterKey){
        return state.bountyOfficeEngine().abandon(state,contractId,hunterKey);
    }

    static BountyClaim captureBountyAlive(SimulationState state,long contractId,String hunterKey,String reason){
        BountyContract contract=state.bounties().stream()
                .filter(b->b.id()==contractId).findFirst().orElse(null);
        if(contract==null)return new BountyClaim(false,contractId,"",hunterKey,0,0,0,"unknown_contract");
        LawResponse response=lawResponse(state,contract.actorKey(),contract.issuerFactionId());
        if(response.action()!=EnforcementAction.ARREST&&response.action()!=EnforcementAction.LETHAL_FORCE){
            return new BountyClaim(false,contract.id(),contract.actorKey(),hunterKey,
                    contract.issuerFactionId(),contract.reward(),0,"no_capture_warrant");
        }
        double bountyBefore=state.crimeLedger().findProfile(contract.actorKey())
                .flatMap(p->p.find(contract.issuerFactionId()))
                .map(JurisdictionWanted::bounty).orElse(0.0);
        BountyClaim claim=state.bountyOfficeEngine().claim(state,contractId,hunterKey);
        if(!claim.claimed())return claim;
        int sentence=Math.max(1,response.sentenceDays());
        CustodyRecord record=new CustodyRecord(state.nextId(),contract.actorKey(),
                contract.issuerFactionId(),state.clock().day(),state.clock().day()+sentence,
                bountyBefore,reason);
        state.addCustody(record);
        state.history().add(new WorldEvent(state.clock().day(),"bounty_target_captured_alive",
                "contract="+contractId+", actor="+contract.actorKey()+", hunter="+hunterKey
                        +", days="+sentence));
        return claim;
    }

    static Optional<CustodyRecord> activeCustody(SimulationState state,String actorKey,long factionId){
        return state.custody().stream().filter(CustodyRecord::active)
                .filter(c->c.actorKey().equals(actorKey)&&c.factionId()==factionId).findFirst();
    }

    static FactionJoinResult joinFaction(SimulationState state,String actorKey,long factionId){
        return state.reputationEngine().join(state,actorKey,factionId);
    }

    static boolean leaveFaction(SimulationState state,String actorKey){
        return state.reputationEngine().leave(state,actorKey);
    }

    static void grantFactionService(SimulationState state,String actorKey,long factionId,double points){
        state.reputationEngine().grantService(state,actorKey,factionId,points);
    }

    static boolean pardonCriminal(SimulationState state,String actorKey,long factionId){
        WantedProfile profile=state.crimeLedger().findProfile(actorKey).orElse(null);
        if(profile==null)return false;
        JurisdictionWanted wanted=profile.find(factionId).orElse(null);
        if(wanted==null)return false;
        wanted.pardon();
        state.history().add(new WorldEvent(state.clock().day(),"criminal_pardoned",
                "actor="+actorKey+", faction="+factionId));
        for(BountyContract bounty:state.bounties()){
            if(bounty.actorKey().equals(actorKey)
                    &&bounty.issuerFactionId()==factionId
                    &&bounty.status()!=BountyContract.Status.CLAIMED)bounty.cancel();
        }
        return true;
    }
}
