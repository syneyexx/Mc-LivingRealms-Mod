package dev.livingrealms.sim.law;

/** Result of a valid bounty capture. Payment is limited by the issuing realm's treasury. */
public record BountyClaim(
        boolean claimed,
        long contractId,
        String actorKey,
        String hunterKey,
        long issuerFactionId,
        double postedReward,
        double paidReward,
        String reason
) {
    public BountyClaim {
        if (contractId < 0 || issuerFactionId < 0) throw new IllegalArgumentException("ids");
        if (!Double.isFinite(postedReward) || postedReward < 0 || !Double.isFinite(paidReward) || paidReward < 0) throw new IllegalArgumentException("reward");
        actorKey = actorKey == null ? "" : actorKey;
        hunterKey = hunterKey == null ? "" : hunterKey;
        reason = reason == null ? "" : reason;
    }
}
