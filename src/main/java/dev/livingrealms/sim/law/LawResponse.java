package dev.livingrealms.sim.law;

/** Deterministic law-enforcement response derived from a jurisdiction-local wanted record. */
public record LawResponse(
        EnforcementAction action,
        WantedLevel wantedLevel,
        double outstandingBounty,
        double requestedFine,
        int sentenceDays,
        String reason
) {
    public LawResponse {
        if (action == null || wantedLevel == null) throw new IllegalArgumentException("law response");
        if (!Double.isFinite(outstandingBounty) || outstandingBounty < 0) throw new IllegalArgumentException("outstandingBounty");
        if (!Double.isFinite(requestedFine) || requestedFine < 0) throw new IllegalArgumentException("requestedFine");
        if (sentenceDays < 0) throw new IllegalArgumentException("sentenceDays");
        reason = reason == null ? "" : reason;
    }
}
