package dev.livingrealms.sim.law;

import dev.livingrealms.sim.world.SimulationState;

/** Complete strategic escalation/custody rules used by physical law-enforcement adapters. */
public final class LawEnforcementEngine {
    public LawResponse response(SimulationState state, String actorKey, long factionId) {
        WantedProfile profile=state.crimeLedger().findProfile(actorKey).orElse(null);
        JurisdictionWanted wanted=profile==null?null:profile.find(factionId).orElse(null);
        if(wanted==null) return new LawResponse(EnforcementAction.NONE,WantedLevel.NONE,0,0,0,"clear");
        WantedLevel level=wanted.wantedLevel();
        double bounty=wanted.bounty();
        return switch(level){
            case NONE -> new LawResponse(EnforcementAction.NONE,level,bounty,0,0,"clear");
            case PERSON_OF_INTEREST -> new LawResponse(EnforcementAction.QUESTION,level,bounty,0,0,"identify_and_question");
            case WANTED -> {
                if(wanted.heat()<45 && wanted.violentCrimes()==0) {
                    double fine=Math.max(1,Math.min(bounty,bounty*.75+5));
                    yield new LawResponse(EnforcementAction.DEMAND_FINE,level,bounty,fine,0,"nonviolent_fine");
                }
                int days=Math.max(1,(int)Math.ceil(bounty/50.0));
                yield new LawResponse(EnforcementAction.ARREST,level,bounty,0,days,"active_warrant");
            }
            case DANGEROUS -> {
                int days=Math.max(3,(int)Math.ceil(bounty/40.0)+wanted.violentCrimes()*2);
                if(wanted.heat()>=90 && wanted.violentCrimes()>=2) yield new LawResponse(EnforcementAction.LETHAL_FORCE,level,bounty,0,days,"armed_dangerous_fugitive");
                yield new LawResponse(EnforcementAction.ARREST,level,bounty,0,days,"dangerous_warrant");
            }
            case MOST_WANTED -> {
                int days=Math.max(10,(int)Math.ceil(bounty/30.0)+wanted.violentCrimes()*3);
                if(wanted.heat()>=70 || wanted.violentCrimes()>0) yield new LawResponse(EnforcementAction.LETHAL_FORCE,level,bounty,0,days,"most_wanted");
                yield new LawResponse(EnforcementAction.ARREST,level,bounty,0,days,"most_wanted_nonviolent");
            }
        };
    }

    public ArrestOutcome arrest(SimulationState state,String actorKey,long factionId,String reason){
        LawResponse response=response(state,actorKey,factionId);
        if(response.action()!=EnforcementAction.ARREST && response.action()!=EnforcementAction.LETHAL_FORCE) return new ArrestOutcome(false,0,0,0,"no_arrest_warrant");
        double cleared=state.captureCriminal(actorKey,factionId);
        int sentence=Math.max(1,response.sentenceDays());
        CustodyRecord record=new CustodyRecord(state.nextId(),actorKey,factionId,state.clock().day(),state.clock().day()+sentence,cleared,reason);
        state.addCustody(record);
        for(BountyContract contract:state.bounties()) if(contract.actorKey().equals(actorKey)&&contract.issuerFactionId()==factionId&&contract.status()!=BountyContract.Status.CLAIMED) contract.cancel();
        state.history().add(new dev.livingrealms.sim.world.WorldEvent(state.clock().day(),"criminal_jailed","actor="+actorKey+", faction="+factionId+", days="+sentence));
        return new ArrestOutcome(true,record.id(),sentence,cleared,"arrested");
    }

    public void releaseExpired(SimulationState state){
        for(CustodyRecord record:state.custody()) if(record.active()&&record.releaseDay()<=state.clock().day()){
            record.release();
            state.history().add(new dev.livingrealms.sim.world.WorldEvent(state.clock().day(),"criminal_released","actor="+record.actorKey()+", faction="+record.factionId()));
        }
    }
}
