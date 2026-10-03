package dev.livingrealms.sim.law;

import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Immutable report presented to the law system after witness/detection logic. */
public record CrimeIncident(
        long id,
        long day,
        String actorKey,
        long jurisdictionFactionId,
        CrimeType type,
        double stolenOrDamageValue,
        SimPosition position,
        boolean witnessed,
        int witnessCount,
        String victimKey,
        String evidence
) {
    public CrimeIncident {
        if(id<=0)throw new IllegalArgumentException("id");
        if(day<0)throw new IllegalArgumentException("day");
        if(actorKey==null||actorKey.isBlank())throw new IllegalArgumentException("actorKey");
        if(jurisdictionFactionId<=0)throw new IllegalArgumentException("jurisdictionFactionId");
        type=Objects.requireNonNull(type); position=Objects.requireNonNull(position);
        if(!Double.isFinite(stolenOrDamageValue)||stolenOrDamageValue<0)throw new IllegalArgumentException("value");
        if(witnessCount<0)throw new IllegalArgumentException("witnessCount");
        victimKey=Objects.requireNonNullElse(victimKey,""); evidence=Objects.requireNonNullElse(evidence,"");
    }
}
