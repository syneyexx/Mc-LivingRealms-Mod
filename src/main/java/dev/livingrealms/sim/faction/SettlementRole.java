package dev.livingrealms.sim.faction;

import java.util.Objects;

/**
 * Stable physical/civilization role of a settlement.
 *
 * <p>Unlike {@link Settlement.Tier}, which is population-derived and may change during simulation,
 * this role records the settlement's place in the regional network. Placement, spacing and
 * transport topology must not silently change because population crossed a tier threshold.</p>
 */
public enum SettlementRole {
    CAPITAL,
    CITY,
    TOWN,
    VILLAGE,
    HAMLET,
    SPECIAL;

    public boolean ordinarySurfaceSettlement() {
        return this != SPECIAL;
    }

    public static SettlementRole fromTier(Settlement.Tier tier) {
        Objects.requireNonNull(tier, "tier");
        return switch (tier) {
            case METROPOLIS, CITY -> CITY;
            case TOWN -> TOWN;
            case VILLAGE -> VILLAGE;
            case HAMLET, CAMP -> HAMLET;
        };
    }
}
