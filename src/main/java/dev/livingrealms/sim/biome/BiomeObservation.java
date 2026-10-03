package dev.livingrealms.sim.biome;

import java.util.Set;

/** Loader-agnostic biome facts supplied by the Minecraft adapter. */
public record BiomeObservation(String sourceId, Set<String> tags, double temperature, double humidity) {
    public BiomeObservation {
        if(sourceId==null||sourceId.isBlank())throw new IllegalArgumentException("sourceId");
        tags=Set.copyOf(tags);
        if(!Double.isFinite(temperature)||!Double.isFinite(humidity)||humidity<0||humidity>1.5)throw new IllegalArgumentException("climate");
    }
    public boolean has(String tag){return tags.contains(tag);}
}
