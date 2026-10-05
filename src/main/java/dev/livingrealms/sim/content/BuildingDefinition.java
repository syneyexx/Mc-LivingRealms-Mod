package dev.livingrealms.sim.content;

import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.construction.SettlementDistrict;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Settlement;
import java.util.Locale;
import java.util.Objects;

/**
 * Building archetype content. Queried by {@link BuildingTemplateRegistry} with culture→family→generic fallback.
 * Does not import external schematic templates — LR-authored metadata only.
 */
public record BuildingDefinition(
        String id,
        String displayName,
        String cultureId,
        CultureArchitecture architectureFamily,
        StructureRole role,
        Settlement.Tier tier,
        SettlementDistrict district,
        double wealthMin,
        double wealthMax,
        int footprintWidth,
        int footprintDepth,
        boolean genericFallback
) {
    public BuildingDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(cultureId, "cultureId");
        Objects.requireNonNull(architectureFamily, "architectureFamily");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(district, "district");
        if (id.isBlank() || displayName.isBlank() || cultureId.isBlank()) {
            throw new IllegalArgumentException("building identity");
        }
        if (!Double.isFinite(wealthMin) || !Double.isFinite(wealthMax) || wealthMin > wealthMax) {
            throw new IllegalArgumentException("building wealth range");
        }
        if (footprintWidth < 3 || footprintDepth < 3) throw new IllegalArgumentException("building footprint");
    }

    public boolean matchesWealth(double wealth) {
        return wealth >= wealthMin && wealth <= wealthMax;
    }

    public boolean footprintFits(int maxWidth, int maxDepth) {
        return footprintWidth <= maxWidth && footprintDepth <= maxDepth;
    }

    public String wireKey() {
        return id.toLowerCase(Locale.ROOT);
    }
}
