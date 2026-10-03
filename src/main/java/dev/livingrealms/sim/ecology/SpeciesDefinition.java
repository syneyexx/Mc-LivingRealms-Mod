package dev.livingrealms.sim.ecology;

import dev.livingrealms.sim.biome.ClimateBand;
import java.util.Locale;
import java.util.Set;

/** Data model shared by abstract simulation and physical Minecraft entities. */
public record SpeciesDefinition(
        String id,
        String commonName,
        Diet diet,
        ActivityCycle activityCycle,
        SocialPattern socialPattern,
        double adultMassKg,
        double lifespanDays,
        double maturityDays,
        double gestationDays,
        double offspringPerBirth,
        double birthsPerYear,
        double dailyFoodKg,
        double dailyWaterLitres,
        double movementKmPerDay,
        double aggression,
        double fearfulness,
        double huntSkill,
        double defense,
        double minGroup,
        double maxGroup,
        Set<ClimateBand> climates,
        Set<String> habitatTags,
        Set<String> preySpecies,
        Set<String> predatorSpecies,
        boolean attacksHumans,
        MorphologyFamily morphology,
        LocomotionMode locomotion,
        double swimSpeedFactor,
        double flightSpeedFactor
) {
    public SpeciesDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (commonName == null || commonName.isBlank()) throw new IllegalArgumentException("commonName");
        if (diet == null || activityCycle == null || socialPattern == null || morphology == null || locomotion == null) throw new IllegalArgumentException("species enums");
        climates = Set.copyOf(climates);
        habitatTags = Set.copyOf(habitatTags);
        preySpecies = Set.copyOf(preySpecies);
        predatorSpecies = Set.copyOf(predatorSpecies);
        if (adultMassKg <= 0 || lifespanDays <= 0 || dailyFoodKg < 0) throw new IllegalArgumentException("invalid biological values");
        if (!Double.isFinite(swimSpeedFactor) || swimSpeedFactor < 0 || !Double.isFinite(flightSpeedFactor) || flightSpeedFactor < 0) throw new IllegalArgumentException("invalid locomotion factors");
        if (locomotion == LocomotionMode.FLYING && flightSpeedFactor <= 0) throw new IllegalArgumentException("flying species requires flight speed");
        if ((locomotion == LocomotionMode.AQUATIC || locomotion == LocomotionMode.AMPHIBIOUS) && swimSpeedFactor <= 0) throw new IllegalArgumentException("swimming species requires swim speed");
    }

    /** Backward-compatible constructor for the original species schema. */
    public SpeciesDefinition(
            String id,String commonName,Diet diet,ActivityCycle activityCycle,SocialPattern socialPattern,
            double adultMassKg,double lifespanDays,double maturityDays,double gestationDays,double offspringPerBirth,
            double birthsPerYear,double dailyFoodKg,double dailyWaterLitres,double movementKmPerDay,double aggression,
            double fearfulness,double huntSkill,double defense,double minGroup,double maxGroup,Set<ClimateBand> climates,
            Set<String> habitatTags,Set<String> preySpecies,Set<String> predatorSpecies,boolean attacksHumans) {
        this(id,commonName,diet,activityCycle,socialPattern,adultMassKg,lifespanDays,maturityDays,gestationDays,offspringPerBirth,
                birthsPerYear,dailyFoodKg,dailyWaterLitres,movementKmPerDay,aggression,fearfulness,huntSkill,defense,minGroup,maxGroup,
                climates,habitatTags,preySpecies,predatorSpecies,attacksHumans,
                inferMorphology(id,habitatTags),inferLocomotion(habitatTags),inferSwimFactor(habitatTags),inferFlightFactor(habitatTags));
    }

    public boolean canPreyOn(String speciesId) { return preySpecies.contains(speciesId); }

    public static LocomotionMode inferLocomotion(Set<String> tags){
        if(tags.contains("flying"))return LocomotionMode.FLYING;
        if(tags.contains("aquatic")&&tags.contains("amphibious"))return LocomotionMode.AMPHIBIOUS;
        if(tags.contains("aquatic"))return LocomotionMode.AQUATIC;
        if(tags.contains("amphibious"))return LocomotionMode.AMPHIBIOUS;
        return LocomotionMode.TERRESTRIAL;
    }
    public static double inferSwimFactor(Set<String> tags){LocomotionMode mode=inferLocomotion(tags);return switch(mode){case AQUATIC->1.0;case AMPHIBIOUS->.72;default->.20;};}
    public static double inferFlightFactor(Set<String> tags){return inferLocomotion(tags)==LocomotionMode.FLYING?1.0:0.0;}
    public static MorphologyFamily inferMorphology(String id,Set<String> tags){
        String v=id.toLowerCase(Locale.ROOT);
        if(v.contains("wolf")||v.contains("fox")||v.contains("dog")||v.contains("coyote")||v.contains("jackal"))return MorphologyFamily.CANID;
        if(v.contains("lion")||v.contains("tiger")||v.contains("leopard")||v.contains("cat")||v.contains("lynx")||v.contains("cheetah"))return MorphologyFamily.FELID;
        if(v.contains("bear"))return MorphologyFamily.URSID;
        if(v.contains("elephant"))return MorphologyFamily.PROBOSCIDEAN;
        if(v.contains("boar")||v.contains("pig"))return MorphologyFamily.SUID;
        if(v.contains("hippo"))return MorphologyFamily.HIPPOPOTAMID;
        if(v.contains("rabbit")||v.contains("hare"))return MorphologyFamily.LAGOMORPH;
        if(v.contains("crocodile")||v.contains("alligator")||v.contains("caiman")||v.contains("gharial"))return MorphologyFamily.CROCODILIAN;
        if(v.contains("shark"))return MorphologyFamily.SHARK;
        if(v.contains("orca")||v.contains("whale")||v.contains("dolphin")||v.contains("porpoise"))return MorphologyFamily.CETACEAN;
        if(v.contains("seal")||v.contains("walrus")||v.contains("sea_lion"))return MorphologyFamily.PINNIPED;
        if(v.contains("eagle")||v.contains("hawk")||v.contains("falcon")||v.contains("owl"))return MorphologyFamily.RAPTOR_BIRD;
        if(tags.contains("flying"))return MorphologyFamily.BIRD;
        if(tags.contains("aquatic")&& !tags.contains("amphibious"))return MorphologyFamily.FISH;
        if(v.contains("deer")||v.contains("bison")||v.contains("zebra")||v.contains("wildebeest")||v.contains("moose")||v.contains("antelope")||v.contains("horse")||v.contains("goat")||v.contains("sheep"))return MorphologyFamily.UNGULATE;
        return MorphologyFamily.GENERIC_QUADRUPED;
    }
}
