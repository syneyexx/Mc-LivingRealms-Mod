package dev.livingrealms;

import dev.livingrealms.sim.data.SpeciesJsonCodec;
import dev.livingrealms.sim.ecology.LocomotionMode;
import dev.livingrealms.sim.ecology.MorphologyFamily;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import dev.livingrealms.sim.ecology.SpeciesVisualFamily;
import dev.livingrealms.sim.ecology.SpeciesVisualFamilyResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Wave 14: every bundled species resolves morphology + visual family + locomotion. */
public final class WildlifeVisualFamilyTest {
    private WildlifeVisualFamilyTest() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of("src/main/resources/data/livingrealms/livingrealms/species");
        Map<String, SpeciesDefinition> all = new LinkedHashMap<>();
        try (var paths = Files.list(dir)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                SpeciesDefinition sp = SpeciesJsonCodec.decode(Files.readString(path));
                all.put(sp.id(), sp);
            }
        }
        check(all.size() >= 90, "expected bundled pack, got " + all.size());

        for (SpeciesDefinition sp : all.values()) {
            check(sp.morphology() != null, sp.id() + " missing morphology");
            check(sp.locomotion() != null, sp.id() + " missing locomotion");
            SpeciesVisualFamily family = SpeciesVisualFamilyResolver.resolve(sp);
            check(family != null, sp.id() + " missing visual family");
            var plan = SpeciesVisualFamilyResolver.modelPlan(family);
            check(plan != null, sp.id() + " missing model plan");
            // Texture path convention must be well-formed (actual PNG presence is a resource-pack concern).
            String safe = sp.id().replace(':', '_').replace('/', '_').toLowerCase(Locale.ROOT);
            check(!safe.isBlank() && !safe.contains(".."), sp.id() + " unsafe texture key");
        }

        // Silhouette families must differ for iconic contrasts.
        SpeciesVisualFamily lion = SpeciesVisualFamilyResolver.resolve(all.values().stream()
                .filter(s -> s.id().contains("lion")).findFirst().orElseThrow());
        SpeciesVisualFamily deer = SpeciesVisualFamilyResolver.resolve(all.values().stream()
                .filter(s -> s.id().contains("deer") || s.morphology() == MorphologyFamily.UNGULATE)
                .findFirst().orElseThrow());
        check(lion != deer, "lion and ungulate must differ visually");
        check(SpeciesVisualFamilyResolver.modelPlan(lion)
                        != SpeciesVisualFamilyResolver.modelPlan(
                                SpeciesVisualFamilyResolver.resolve(all.getOrDefault("orca",
                                        all.values().stream().filter(s -> s.locomotion() == LocomotionMode.AQUATIC)
                                                .findFirst().orElseThrow()))),
                "predator vs aquatic model plans differ");

        // Determinism
        for (SpeciesDefinition sp : all.values()) {
            check(SpeciesVisualFamilyResolver.resolve(sp) == SpeciesVisualFamilyResolver.resolve(sp),
                    "non-deterministic " + sp.id());
        }

        System.out.println("PASS WildlifeVisualFamilyTest: " + all.size() + " species resolved");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
