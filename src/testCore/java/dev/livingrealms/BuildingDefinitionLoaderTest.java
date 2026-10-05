package dev.livingrealms;

import dev.livingrealms.sim.content.BuildingDefinition;
import dev.livingrealms.sim.content.BuildingDefinitionLoader;
import dev.livingrealms.sim.construction.CultureArchitecture;
import dev.livingrealms.sim.construction.SettlementDistrict;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Settlement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Wave 25: focused coverage for data-driven building JSON loader. */
public final class BuildingDefinitionLoaderTest {
    private BuildingDefinitionLoaderTest() {}

    public static void main(String[] args) {
        BuildingDefinitionLoader.clearCache();
        List<BuildingDefinition> all = BuildingDefinitionLoader.loadAll();
        check(!all.isEmpty(), "buildings must load from data/livingrealms/buildings");
        Set<String> ids = new HashSet<>();
        boolean hasGeneric = false;
        for (BuildingDefinition b : all) {
            check(ids.add(b.id()), "duplicate building id " + b.id());
            check(b.displayName() != null && !b.displayName().isBlank(), b.id() + " displayName");
            check(b.cultureId() != null && !b.cultureId().isBlank(), b.id() + " cultureId");
            check(b.architectureFamily() != null, b.id() + " architectureFamily");
            check(b.role() != null, b.id() + " role");
            check(b.tier() != null, b.id() + " tier");
            check(b.district() != null, b.id() + " district");
            check(b.footprintWidth() > 0 && b.footprintDepth() > 0, b.id() + " footprint");
            if (b.genericFallback()) hasGeneric = true;
        }
        check(hasGeneric, "at least one genericFallback archetype required");

        BuildingDefinition decoded = BuildingDefinitionLoader.decode("""
                {"id":"loader_probe_house","displayName":"Probe House","cultureId":"generic",
                 "architectureFamily":"MEDIEVAL_FACHWERK","role":"HOUSE","tier":"HAMLET",
                 "district":"RESIDENTIAL","wealthMin":0.1,"wealthMax":0.9,
                 "footprintWidth":11,"footprintDepth":9,"genericFallback":false}
                """);
        check(decoded.role() == StructureRole.HOUSE, "decoded role");
        check(decoded.tier() == Settlement.Tier.HAMLET, "decoded tier");
        check(decoded.district() == SettlementDistrict.RESIDENTIAL, "decoded district");
        check(decoded.architectureFamily() == CultureArchitecture.MEDIEVAL_FACHWERK, "decoded family");
        check(decoded.footprintWidth() == 11 && decoded.footprintDepth() == 9, "decoded footprint");
        check(!decoded.genericFallback(), "decoded genericFallback false");

        BuildingDefinitionLoader.clearCache();
        List<BuildingDefinition> again = BuildingDefinitionLoader.loadAll();
        check(again.size() == all.size(), "cache clear reloads same catalog size");

        System.out.println("PASS building definition loader: " + all.size() + " archetypes + decode round-trip fields");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
