package dev.livingrealms.sim.faction;

import dev.livingrealms.sim.util.Mathx;

/**
 * Civilization-development qualification beyond raw population tier thresholds.
 * A huge impoverished settlement should not behave like a healthy metropolis.
 */
public final class SettlementDevelopment {
    private SettlementDevelopment() {}

    /** 0..1 development score from housing, food, infrastructure, market, admin, education, trade, wealth, services. */
    public static double score(
            Settlement settlement,
            double education,
            double tradeConnectivity,
            double administration,
            double publicServices
    ) {
        if (settlement == null) return 0;
        double housing = settlement.population() <= 0 ? 1.0 : Mathx.clamp((double) settlement.housing() / settlement.population(), 0, 1.25);
        housing = Math.min(1.0, housing);
        double food = settlement.foodSecurity();
        double infra = Mathx.clamp(settlement.infrastructure() / Math.max(20.0, settlement.population() * 0.02), 0, 1);
        double market = Mathx.clamp(settlement.prosperity() * 0.6 + settlement.employment() * 0.4, 0, 1);
        double admin = Mathx.clamp(administration, 0, 1);
        double edu = Mathx.clamp(education, 0, 1);
        double trade = Mathx.clamp(tradeConnectivity, 0, 1);
        double wealth = Mathx.clamp(settlement.prosperity(), 0, 1);
        double services = Mathx.clamp(publicServices, 0, 1);
        return Mathx.clamp(
                housing * 0.14 + food * 0.16 + infra * 0.12 + market * 0.12
                        + admin * 0.10 + edu * 0.08 + trade * 0.10 + wealth * 0.10 + services * 0.08,
                0, 1);
    }

    /**
     * Effective tier for behavior/presentation: population tier may not advance fully if development is weak.
     */
    public static Settlement.Tier effectiveTier(Settlement settlement, double developmentScore) {
        Settlement.Tier popTier = settlement.tier();
        double d = Mathx.clamp(developmentScore, 0, 1);
        if (d >= 0.72) return popTier;
        if (d >= 0.55) return demote(popTier, 1);
        if (d >= 0.38) return demote(popTier, 2);
        return demote(popTier, 3);
    }

    private static Settlement.Tier demote(Settlement.Tier tier, int steps) {
        int ord = Math.max(0, tier.ordinal() - Math.max(0, steps));
        return Settlement.Tier.values()[ord];
    }
}
