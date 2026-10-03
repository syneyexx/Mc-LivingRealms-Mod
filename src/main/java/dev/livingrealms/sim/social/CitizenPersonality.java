package dev.livingrealms.sim.social;

import dev.livingrealms.sim.util.Mathx;

/** Continuous personality traits. 0.0 and 1.0 are strong opposites/extremes, not labels. */
public record CitizenPersonality(double aggression,double tradeAffinity,double caution,double greed,double loyalty,double treachery) {
    public CitizenPersonality {
        aggression=bounded(aggression,"aggression");tradeAffinity=bounded(tradeAffinity,"tradeAffinity");
        caution=bounded(caution,"caution");greed=bounded(greed,"greed");loyalty=bounded(loyalty,"loyalty");treachery=bounded(treachery,"treachery");
    }
    private static double bounded(double v,String name){if(!Double.isFinite(v))throw new IllegalArgumentException(name);return Mathx.clamp(v,0,1);}
}
