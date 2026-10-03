package dev.livingrealms.sim.ecology;

/** Maps detailed data-driven body plans onto the current renderer families. */
public final class SpeciesMorphologyResolver {
    private SpeciesMorphologyResolver() {}
    public static SpeciesMorphology resolve(SpeciesDefinition species){
        return switch(species.morphology()){
            case LAGOMORPH, RODENT -> SpeciesMorphology.SMALL_QUADRUPED;
            case UNGULATE, SUID -> SpeciesMorphology.UNGULATE;
            case CANID, FELID -> SpeciesMorphology.PREDATOR_QUADRUPED;
            case URSID -> SpeciesMorphology.BEAR;
            case PROBOSCIDEAN, HIPPOPOTAMID, GENERIC_QUADRUPED -> species.adultMassKg()>900?SpeciesMorphology.LARGE_MAMMAL:SpeciesMorphology.UNGULATE;
            case CROCODILIAN, OTHER_REPTILE, AMPHIBIAN -> SpeciesMorphology.CROCODILIAN;
            case FISH, SHARK, INVERTEBRATE -> SpeciesMorphology.FISH;
            case CETACEAN -> SpeciesMorphology.CETACEAN;
            case PINNIPED -> SpeciesMorphology.PINNIPED;
            case RAPTOR_BIRD, BIRD -> SpeciesMorphology.BIRD;
        };
    }
}
