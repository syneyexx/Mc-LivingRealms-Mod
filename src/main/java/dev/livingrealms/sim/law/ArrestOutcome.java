package dev.livingrealms.sim.law;

public record ArrestOutcome(
        boolean arrested,
        long custodyId,
        int sentenceDays,
        double bountyCleared,
        String reason
) {
    public ArrestOutcome {
        if (custodyId < 0 || sentenceDays < 0 || !Double.isFinite(bountyCleared) || bountyCleared < 0) throw new IllegalArgumentException("arrest outcome");
        reason = reason == null ? "" : reason;
    }
}
