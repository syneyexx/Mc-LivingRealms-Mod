package dev.livingrealms.sim.content;

import java.util.List;
import java.util.Objects;

/**
 * Authored surface-realm seed content. Density/spacing policy remains in
 * {@link dev.livingrealms.sim.world.SettlementDensitySeeder} — never encoded here.
 */
public record RealmDefinition(
        String id,
        String displayName,
        String rulerSeedName,
        String capitalName,
        double x,
        double z,
        int capitalPopulation,
        int capitalHousing,
        double technology,
        double treasury,
        int armyInfantry,
        List<SatelliteDefinition> satellites,
        List<String> namingHints,
        String cultureId
) {
    public RealmDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(rulerSeedName, "rulerSeedName");
        Objects.requireNonNull(capitalName, "capitalName");
        Objects.requireNonNull(satellites, "satellites");
        Objects.requireNonNull(namingHints, "namingHints");
        Objects.requireNonNull(cultureId, "cultureId");
        if (id.isBlank() || displayName.isBlank() || rulerSeedName.isBlank() || capitalName.isBlank()) {
            throw new IllegalArgumentException("realm identity fields blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(technology) || !Double.isFinite(treasury)) {
            throw new IllegalArgumentException("realm numeric fields");
        }
        if (capitalPopulation < 0 || capitalHousing < 0 || armyInfantry < 0) {
            throw new IllegalArgumentException("realm counts");
        }
        satellites = List.copyOf(satellites);
        namingHints = List.copyOf(namingHints);
    }

    public record SatelliteDefinition(String name, double dx, double dz, int population, int housing) {
        public SatelliteDefinition {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) throw new IllegalArgumentException("satellite name");
            if (!Double.isFinite(dx) || !Double.isFinite(dz)) throw new IllegalArgumentException("satellite offset");
            if (population < 0 || housing < 0) throw new IllegalArgumentException("satellite counts");
        }
    }
}
