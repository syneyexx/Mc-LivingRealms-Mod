package dev.livingrealms.sim.ecology;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Resolves presentation silhouette; prefers explicit visualFamily, then id/tag heuristics, then morphology. */
public final class SpeciesVisualFamilyResolver {
    private SpeciesVisualFamilyResolver() {}

    public static SpeciesVisualFamily resolve(SpeciesDefinition sp) {
        Objects.requireNonNull(sp, "species");
        if (sp.visualFamily() != null) return sp.visualFamily();
        return infer(sp.id(), sp.habitatTags(), sp.morphology(), sp.locomotion());
    }

    public static SpeciesVisualFamily infer(String id, Set<String> tags, MorphologyFamily morphology,
                                            LocomotionMode locomotion) {
        String v = id == null ? "" : id.toLowerCase(Locale.ROOT);
        Set<String> t = tags == null ? Set.of() : tags;
        if (v.contains("mouse") || v.contains("rat") || v.contains("squirrel") || v.contains("beaver")
                || morphology == MorphologyFamily.RODENT) return SpeciesVisualFamily.SMALL_RODENT;
        if (v.contains("rabbit") || v.contains("hare") || morphology == MorphologyFamily.LAGOMORPH)
            return SpeciesVisualFamily.LAGOMORPH;
        if (v.contains("wolf") || v.contains("fox") || v.contains("dog") || v.contains("coyote")
                || v.contains("jackal") || morphology == MorphologyFamily.CANID) return SpeciesVisualFamily.CANID;
        if (v.contains("lion") || v.contains("tiger") || v.contains("leopard") || v.contains("lynx")
                || v.contains("cheetah") || v.contains("cougar") || v.contains("panther")
                || (v.contains("cat") && !v.contains("cattle")) || morphology == MorphologyFamily.FELID)
            return SpeciesVisualFamily.FELID;
        if (v.contains("bear") || morphology == MorphologyFamily.URSID) return SpeciesVisualFamily.URSID;
        if (v.contains("deer") || v.contains("elk") || v.contains("moose") || v.contains("caribou")
                || v.contains("antelope") || v.contains("gazelle")) return SpeciesVisualFamily.CERVID;
        if (v.contains("bison") || v.contains("buffalo") || v.contains("cattle") || v.contains("cow")
                || v.contains("ox") || v.contains("goat") || v.contains("sheep") || v.contains("yak")
                || v.contains("wildebeest")) return SpeciesVisualFamily.BOVID;
        if (v.contains("horse") || v.contains("zebra") || v.contains("donkey") || v.contains("mule"))
            return SpeciesVisualFamily.EQUID;
        if (v.contains("camel") || v.contains("llama") || v.contains("alpaca")) return SpeciesVisualFamily.CAMELID;
        if (v.contains("elephant") || morphology == MorphologyFamily.PROBOSCIDEAN) return SpeciesVisualFamily.ELEPHANT;
        if (v.contains("rhino")) return SpeciesVisualFamily.RHINO;
        if (v.contains("ape") || v.contains("monkey") || v.contains("chimp") || v.contains("gorilla")
                || v.contains("baboon") || v.contains("lemur")) return SpeciesVisualFamily.PRIMATE;
        if (v.contains("otter") || v.contains("weasel") || v.contains("badger") || v.contains("marten")
                || v.contains("mink") || v.contains("ferret")) return SpeciesVisualFamily.MUSTELID;
        if (v.contains("boar") || v.contains("pig") || morphology == MorphologyFamily.SUID) return SpeciesVisualFamily.SUID;
        if (v.contains("hippo") || morphology == MorphologyFamily.HIPPOPOTAMID) return SpeciesVisualFamily.HIPPO;
        if (v.contains("crocodile") || v.contains("alligator") || v.contains("caiman") || v.contains("gharial")
                || morphology == MorphologyFamily.CROCODILIAN) return SpeciesVisualFamily.CROCODILIAN;
        if (v.contains("snake") || v.contains("serpent") || v.contains("python") || v.contains("viper"))
            return SpeciesVisualFamily.SNAKE;
        if (v.contains("turtle") || v.contains("tortoise") || v.contains("terrapin")) return SpeciesVisualFamily.TURTLE;
        if (v.contains("shark") || morphology == MorphologyFamily.SHARK) return SpeciesVisualFamily.SHARK;
        if (v.contains("ray") || v.contains("skate") || v.contains("manta")) return SpeciesVisualFamily.RAY;
        if (v.contains("whale") || v.contains("dolphin") || v.contains("orca") || v.contains("porpoise")
                || morphology == MorphologyFamily.CETACEAN) return SpeciesVisualFamily.CETACEAN;
        if (v.contains("seal") || v.contains("walrus") || v.contains("sea_lion")
                || morphology == MorphologyFamily.PINNIPED) return SpeciesVisualFamily.PINNIPED;
        if (v.contains("eagle") || v.contains("hawk") || v.contains("falcon") || v.contains("owl")
                || v.contains("vulture") || morphology == MorphologyFamily.RAPTOR_BIRD) return SpeciesVisualFamily.RAPTOR;
        if (v.contains("duck") || v.contains("goose") || v.contains("swan") || v.contains("pelican")
                || v.contains("heron") || v.contains("crane") || v.contains("cormorant"))
            return SpeciesVisualFamily.WATERFOWL;
        if (v.contains("chicken") || v.contains("turkey") || v.contains("pheasant") || v.contains("grouse")
                || v.contains("quail") || v.contains("ostrich") || v.contains("emu") || v.contains("kiwi"))
            return SpeciesVisualFamily.GROUND_BIRD;
        if (morphology == MorphologyFamily.BIRD || locomotion == LocomotionMode.FLYING || t.contains("flying"))
            return SpeciesVisualFamily.SONGBIRD;
        if (morphology == MorphologyFamily.FISH || (t.contains("aquatic") && !t.contains("amphibious")))
            return SpeciesVisualFamily.BONY_FISH;
        if (morphology == MorphologyFamily.AMPHIBIAN) return SpeciesVisualFamily.AMPHIBIAN;
        if (morphology == MorphologyFamily.OTHER_REPTILE) return SpeciesVisualFamily.OTHER_REPTILE;
        if (morphology == MorphologyFamily.INVERTEBRATE) return SpeciesVisualFamily.INVERTEBRATE;
        if (morphology == MorphologyFamily.UNGULATE) return SpeciesVisualFamily.CERVID;
        return SpeciesVisualFamily.GENERIC_QUADRUPED;
    }

    /** Coarse body plan used by the reusable Minecraft model parts. */
    public static ModelPlan modelPlan(SpeciesVisualFamily family) {
        return switch (family) {
            case BONY_FISH, SHARK, RAY -> ModelPlan.FISH;
            case CETACEAN, PINNIPED -> ModelPlan.MARINE_MAMMAL;
            case RAPTOR, WATERFOWL, SONGBIRD, GROUND_BIRD -> ModelPlan.BIRD;
            case CROCODILIAN, SNAKE, TURTLE, OTHER_REPTILE -> ModelPlan.REPTILE;
            case ELEPHANT, RHINO, HIPPO, URSID -> ModelPlan.BULKY_QUADRUPED;
            case FELID, CANID, MUSTELID, SMALL_RODENT, LAGOMORPH -> ModelPlan.LIGHT_QUADRUPED;
            case CERVID, BOVID, EQUID, CAMELID, SUID -> ModelPlan.UNGULATE;
            default -> ModelPlan.QUADRUPED;
        };
    }

    public enum ModelPlan {
        QUADRUPED, LIGHT_QUADRUPED, UNGULATE, BULKY_QUADRUPED, FISH, MARINE_MAMMAL, BIRD, REPTILE
    }
}
