package dev.livingrealms.sim.content;

import dev.livingrealms.sim.construction.CultureArchitecture;
import java.util.List;
import java.util.Objects;

/**
 * Authored culture pack: naming, dialect, architecture family, and soft economic/district tendencies.
 * Wired into settlement identity, architecture selection, and dialogue.
 */
public record CultureDefinition(
        String id,
        String displayName,
        CultureArchitecture architectureFamily,
        String namingStyle,
        String dialectStyle,
        List<String> materialPalette,
        double economicTendency,
        double martialTendency,
        double artisticTendency,
        List<String> districtTendencies,
        CultureArchitecture relatedFamily
) {
    public CultureDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(architectureFamily, "architectureFamily");
        Objects.requireNonNull(namingStyle, "namingStyle");
        Objects.requireNonNull(dialectStyle, "dialectStyle");
        Objects.requireNonNull(materialPalette, "materialPalette");
        Objects.requireNonNull(districtTendencies, "districtTendencies");
        Objects.requireNonNull(relatedFamily, "relatedFamily");
        if (id.isBlank() || displayName.isBlank()) throw new IllegalArgumentException("culture identity");
        if (!Double.isFinite(economicTendency) || !Double.isFinite(martialTendency) || !Double.isFinite(artisticTendency)) {
            throw new IllegalArgumentException("culture tendencies");
        }
        materialPalette = List.copyOf(materialPalette);
        districtTendencies = List.copyOf(districtTendencies);
    }
}
