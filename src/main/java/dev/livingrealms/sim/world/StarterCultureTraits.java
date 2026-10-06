package dev.livingrealms.sim.world;

import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.content.CultureDefinition;
import dev.livingrealms.sim.content.CultureDefinitionRegistry;
import dev.livingrealms.sim.content.RealmDefinition;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable day-zero culture traits shared by canonical bootstrap and Minecraft world generation.
 *
 * <p>Keeping this derivation pure prevents still-ungenerated starter chunks from changing palette
 * after a realm's mutable civilization traits evolve later in the simulation.</p>
 */
public record StarterCultureTraits(
        double mercantile,
        double martial,
        double agrarian,
        double artistic,
        double tolerance,
        double openness
) {
    public static Optional<StarterCultureTraits> resolve(RealmDefinition realm) {
        Objects.requireNonNull(realm, "realm");
        if (realm.cultureId() == null || realm.cultureId().isBlank()) return Optional.empty();
        CultureDefinition culture = CultureDefinitionRegistry.find(realm.cultureId()).orElse(null);
        if (culture == null) return Optional.empty();
        double economic = culture.economicTendency();
        return Optional.of(new StarterCultureTraits(
                economic,
                culture.martialTendency(),
                Math.max(0.2, 0.75 - economic * 0.35),
                culture.artisticTendency(),
                0.55,
                Math.min(1.0, 0.35 + economic * 0.4)));
    }

    public void applyTo(FactionCivilizationState civilization) {
        Objects.requireNonNull(civilization, "civilization");
        civilization.setCultureTraits(
                mercantile, martial, agrarian, artistic, tolerance, openness);
    }
}
