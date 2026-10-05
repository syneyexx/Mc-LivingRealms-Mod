package dev.livingrealms.sim.faction;

import java.util.Objects;

/**
 * Wave 46 — authoritative guard for development-mode participation in ordinary growth.
 *
 * <p>{@link DevelopmentMode#PLAYER_LED} must not secretly receive automatic ordinary houses
 * or abstract auto-housing / district-fill housing unless an explicit policy allows it.
 * Roads and public civic infrastructure remain allowed.
 */
public final class DevelopmentModeGuard {
    private DevelopmentModeGuard() {}

    /** PLAYER_LED blocks ordinary auto housing generation (planner houses + abstract addHousing). */
    public static boolean allowsAutoHousing(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return settlement.developmentMode() != DevelopmentMode.PLAYER_LED;
    }

    /**
     * Ordinary house intents from {@link dev.livingrealms.sim.construction.SettlementPlanner}.
     * HYBRID only when there is a genuine housing shortage; AUTO always (subject to caps).
     */
    public static boolean allowsOrdinaryHouseEmission(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return switch (settlement.developmentMode()) {
            case PLAYER_LED -> false;
            case AUTO -> true;
            case HYBRID -> settlement.housingShortage() > 0;
        };
    }

    /**
     * Abstract FactionEngine housing increments (wood/stone paid capacity).
     * Distinct from physical house intents — still blocked for PLAYER_LED.
     */
    public static boolean allowsAbstractHousingGrowth(Settlement settlement) {
        return allowsAutoHousing(settlement);
    }

    /**
     * Automatic residential district fill that would spawn ordinary houses.
     * Civic/road infrastructure is not gated here.
     */
    public static boolean allowsAutomaticResidentialDistrictFill(Settlement settlement) {
        return allowsOrdinaryHouseEmission(settlement);
    }

    /** Roads / wells / markets / temples remain available in all modes. */
    public static boolean allowsPublicInfrastructure(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return true;
    }

    public static String describe(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return settlement.developmentMode().name()
                + " autoHousing=" + allowsAutoHousing(settlement)
                + " houseEmission=" + allowsOrdinaryHouseEmission(settlement)
                + " abstractGrowth=" + allowsAbstractHousingGrowth(settlement);
    }
}
