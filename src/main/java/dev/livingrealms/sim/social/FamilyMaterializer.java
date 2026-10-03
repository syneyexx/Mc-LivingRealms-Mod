package dev.livingrealms.sim.social;

import dev.livingrealms.sim.civilian.CitizenIdentity;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimulationState;
import java.util.*;

/** Converts a bounded dependent record into the same persistent person when that person must become a named citizen. */
public final class FamilyMaterializer {
    private FamilyMaterializer() {}

    public static SocialCitizen materialize(SimulationState state,HouseholdState household,DependentChild dependent,CitizenRole role){
        Objects.requireNonNull(state,"state");Objects.requireNonNull(household,"household");Objects.requireNonNull(dependent,"dependent");Objects.requireNonNull(role,"role");
        SocialCitizen existing=state.findSocialCitizen(dependent.id()).orElse(null);if(existing!=null)return existing;
        long id=dependent.id();int slot=projectionSlot(state,household.settlementId(),id);
        CitizenIdentity identity=CitizenIdentity.forAgent(state.seed(),id,household.factionId(),household.settlementId(),slot,role);
        DeterministicRng rng=new DeterministicRng(state.seed()^id*0x9E3779B97F4A7C15L^dependent.birthDay());
        CitizenPersonality personality=new CitizenPersonality(rng.between(.05,.95),rng.between(.05,.95),rng.between(.05,.95),rng.between(.05,.95),rng.between(.05,.95),rng.between(.02,.75));
        SocialCitizen child=new SocialCitizen(id,household.factionId(),household.settlementId(),slot,identity.name(),identity.skinVariant(),role,dependent.birthDay(),personality);
        child.setHouseholdId(household.id());household.removeChild(id);household.addMember(id);state.addSocialCitizen(child);
        linkParentsAndSiblings(state,household,dependent,child);
        return child;
    }

    private static void linkParentsAndSiblings(SimulationState state,HouseholdState household,DependentChild dependent,SocialCitizen child){
        String childKey="citizen:"+child.id();
        for(long parentId:dependent.biologicalParents())state.findSocialCitizen(parentId).ifPresent(parent->{parent.relationship(childKey).setFamilyBond(FamilyBond.CHILD);child.relationship("citizen:"+parent.id()).setFamilyBond(FamilyBond.PARENT);});
        for(long parentId:dependent.adoptiveParents())state.findSocialCitizen(parentId).ifPresent(parent->{parent.relationship(childKey).setFamilyBond(FamilyBond.ADOPTED_CHILD);child.relationship("citizen:"+parent.id()).setFamilyBond(FamilyBond.ADOPTIVE_PARENT);});
        Set<Long> parents=new HashSet<>(dependent.biologicalParents());parents.addAll(dependent.adoptiveParents());
        for(long siblingId:household.memberIds())if(siblingId!=child.id())state.findSocialCitizen(siblingId).filter(SocialCitizen::alive).ifPresent(sibling->{
            boolean shared=parents.stream().anyMatch(parentId->{CitizenRelationship relation=sibling.relationships().get("citizen:"+parentId);return relation!=null&&(relation.familyBond()==FamilyBond.PARENT||relation.familyBond()==FamilyBond.ADOPTIVE_PARENT);});
            if(shared){child.relationship("citizen:"+sibling.id()).setFamilyBond(FamilyBond.SIBLING);sibling.relationship(childKey).setFamilyBond(FamilyBond.SIBLING);}
        });
    }

    private static int projectionSlot(SimulationState state,long settlementId,long id){
        int slot=60_000+Math.floorMod(Long.hashCode(id),39_000);Set<Integer> used=new HashSet<>();for(SocialCitizen c:state.socialCitizens())if(c.settlementId()==settlementId)used.add(c.projectionSlot());while(used.contains(slot)&&slot<99_999)slot++;if(used.contains(slot)){slot=59_999;while(slot>1&&used.contains(slot))slot--;}return slot;
    }
}
