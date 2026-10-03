package dev.livingrealms.sim.ecology;

/** Compatibility bridge from the richer data-driven locomotion field to runtime navigation families. */
public final class SpeciesMobilityResolver {
    private SpeciesMobilityResolver() {}
    public static SpeciesMobility resolve(SpeciesDefinition species){
        return switch(species.locomotion()){
            case TERRESTRIAL -> SpeciesMobility.TERRESTRIAL;
            case AMPHIBIOUS -> SpeciesMobility.AMPHIBIOUS;
            case AQUATIC -> SpeciesMobility.AQUATIC;
            case FLYING -> SpeciesMobility.FLYING;
        };
    }
}
