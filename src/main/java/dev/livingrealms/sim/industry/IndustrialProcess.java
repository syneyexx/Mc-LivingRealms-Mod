package dev.livingrealms.sim.industry;

import dev.livingrealms.sim.faction.ResourceType;
import java.util.Map;
import java.util.Objects;

/** One abstract production recipe; amounts are per cycle. */
public record IndustrialProcess(
        IndustryKind kind,
        double minimumTechnology,
        double stressCost,
        Map<ResourceType,Double> inputs,
        Map<ResourceType,Double> outputs
) {
    public IndustrialProcess {
        kind=Objects.requireNonNull(kind,"kind");
        if(minimumTechnology<0||stressCost<=0) throw new IllegalArgumentException("technology/stress");
        inputs=Map.copyOf(Objects.requireNonNull(inputs,"inputs"));
        outputs=Map.copyOf(Objects.requireNonNull(outputs,"outputs"));
        if(outputs.isEmpty()) throw new IllegalArgumentException("outputs");
        inputs.forEach((r,v)->{if(r==null||v==null||v<0||!Double.isFinite(v))throw new IllegalArgumentException("input");});
        outputs.forEach((r,v)->{if(r==null||v==null||v<=0||!Double.isFinite(v))throw new IllegalArgumentException("output");});
    }
}
