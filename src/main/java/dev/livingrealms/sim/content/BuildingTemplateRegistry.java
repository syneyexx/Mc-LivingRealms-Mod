package dev.livingrealms.sim.content;

import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.construction.SettlementDistrict;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Wave 31 — building template query with fallback:
 * exact culture → related architecture family → generic Living Realms fallback.
 * Does not import external templates.
 */
public final class BuildingTemplateRegistry {
    private BuildingTemplateRegistry() {}

    public record Query(
            String cultureId,
            StructureRole role,
            Settlement.Tier tier,
            SettlementDistrict district,
            double wealth,
            int maxFootprintWidth,
            int maxFootprintDepth
    ) {
        public Query {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(district, "district");
            if (!Double.isFinite(wealth)) throw new IllegalArgumentException("wealth");
            if (maxFootprintWidth < 1 || maxFootprintDepth < 1) throw new IllegalArgumentException("footprint");
        }
    }

    public static Optional<BuildingDefinition> find(Query query) {
        Objects.requireNonNull(query, "query");
        List<BuildingDefinition> all = BuildingDefinitionLoader.loadAll();
        CultureDefinition culture = query.cultureId() == null || query.cultureId().isBlank()
                ? null
                : CultureDefinitionRegistry.find(query.cultureId()).orElse(null);

        Optional<BuildingDefinition> exact = pick(all, query, MatchLevel.EXACT_CULTURE, culture);
        if (exact.isPresent()) return exact;

        Optional<BuildingDefinition> family = pick(all, query, MatchLevel.RELATED_FAMILY, culture);
        if (family.isPresent()) return family;

        return pick(all, query, MatchLevel.GENERIC, culture);
    }

    public static BuildingDefinition require(Query query) {
        return find(query).orElseThrow(() -> new IllegalStateException(
                "No building template for role=" + query.role() + " culture=" + query.cultureId()));
    }

    private enum MatchLevel { EXACT_CULTURE, RELATED_FAMILY, GENERIC }

    private static Optional<BuildingDefinition> pick(
            List<BuildingDefinition> all,
            Query query,
            MatchLevel level,
            CultureDefinition culture
    ) {
        List<BuildingDefinition> candidates = new ArrayList<>();
        for (BuildingDefinition b : all) {
            if (b.role() != query.role()) continue;
            if (!b.matchesWealth(query.wealth())) continue;
            if (!b.footprintFits(query.maxFootprintWidth, query.maxFootprintDepth)) continue;
            if (!matchesLevel(b, query, level, culture)) continue;
            // Prefer same or lower tier (can grow into); allow one tier above as soft match.
            int tierDelta = b.tier().ordinal() - query.tier().ordinal();
            if (tierDelta > 1) continue;
            candidates.add(b);
        }
        if (candidates.isEmpty()) {
            // Soften district + wealth on generic only.
            if (level != MatchLevel.GENERIC) return Optional.empty();
            for (BuildingDefinition b : all) {
                if (!b.genericFallback()) continue;
                if (b.role() != query.role()) continue;
                if (!b.footprintFits(query.maxFootprintWidth, query.maxFootprintDepth)) continue;
                candidates.add(b);
            }
        }
        return candidates.stream()
                .min(Comparator
                        .comparingInt((BuildingDefinition b) -> districtPenalty(b, query.district()))
                        .thenComparingInt(b -> Math.abs(b.tier().ordinal() - query.tier().ordinal()))
                        .thenComparing(BuildingDefinition::id));
    }

    private static boolean matchesLevel(
            BuildingDefinition b,
            Query query,
            MatchLevel level,
            CultureDefinition culture
    ) {
        return switch (level) {
            case EXACT_CULTURE -> query.cultureId() != null
                    && !query.cultureId().isBlank()
                    && query.cultureId().equals(b.cultureId());
            case RELATED_FAMILY -> {
                if (b.genericFallback()) {
                    yield false;
                }
                CultureArchitecture family = culture == null
                        ? b.architectureFamily()
                        : culture.relatedFamily();
                CultureArchitecture exact = culture == null
                        ? null
                        : culture.architectureFamily();
                yield b.architectureFamily() == family
                        || (exact != null && b.architectureFamily() == exact);
            }
            case GENERIC -> b.genericFallback() || "generic".equals(b.cultureId());
        };
    }

    private static int districtPenalty(BuildingDefinition b, SettlementDistrict wanted) {
        return b.district() == wanted ? 0 : 1;
    }
}
