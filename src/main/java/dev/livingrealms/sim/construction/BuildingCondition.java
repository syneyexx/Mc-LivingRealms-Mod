package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.Mathx;

/**
 * Visible building condition derived from settlement prosperity, unrest and infrastructure.
 * Not a parallel building authority — presentation/repair demand helper over Settlement state.
 */
public enum BuildingCondition {
    NEW,
    MAINTAINED,
    WORN,
    DAMAGED,
    RUINED,
    ABANDONED;

    public static BuildingCondition of(Settlement settlement) {
        if (settlement == null) return WORN;
        double score = Mathx.clamp(
                settlement.prosperity() * .45
                        + settlement.infrastructure() / Math.max(40.0, settlement.population() * .05) * .25
                        + (1 - settlement.unrest()) * .2
                        + settlement.foodSecurity() * .1,
                0, 1);
        if (settlement.population() <= 0 || settlement.unrest() > .92) return ABANDONED;
        if (score < .18) return RUINED;
        if (score < .32 || settlement.unrest() > .7) return DAMAGED;
        if (score < .48) return WORN;
        if (score < .78) return MAINTAINED;
        return NEW;
    }

    public double repairDemand() {
        return switch (this) {
            case NEW -> 0;
            case MAINTAINED -> .05;
            case WORN -> .25;
            case DAMAGED -> .55;
            case RUINED -> .8;
            case ABANDONED -> .95;
        };
    }
}
