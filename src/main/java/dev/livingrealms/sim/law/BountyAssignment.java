package dev.livingrealms.sim.law;

/** Result of accepting or abandoning a bounty contract. */
public record BountyAssignment(boolean success,long contractId,String hunterKey,String reason) {
    public BountyAssignment {
        if(contractId<0) throw new IllegalArgumentException("contractId");
        hunterKey=hunterKey==null?"":hunterKey;
        reason=reason==null?"":reason;
    }
}
