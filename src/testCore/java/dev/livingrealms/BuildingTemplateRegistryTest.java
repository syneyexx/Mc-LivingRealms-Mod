package dev.livingrealms;

import dev.livingrealms.sim.content.BuildingDefinition;
import dev.livingrealms.sim.content.BuildingDefinitionLoader;
import dev.livingrealms.sim.content.BuildingTemplateRegistry;
import dev.livingrealms.sim.content.CultureDefinition;
import dev.livingrealms.sim.content.CultureDefinitionLoader;
import dev.livingrealms.sim.content.CultureDefinitionRegistry;
import dev.livingrealms.sim.construction.SettlementDistrict;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Settlement;
import java.util.List;
import java.util.Map;

/** Wave 8/31: building loader + template fallback chain; culture loader smoke. */
public final class BuildingTemplateRegistryTest {
    private BuildingTemplateRegistryTest() {}

    public static void main(String[] args) {
        buildingLoaderSmoke();
        fallbackChain();
        cultureLoaderSmoke();
        System.out.println("PASS building template registry: loader + culture→family→generic fallback + culture smoke");
    }

    private static void buildingLoaderSmoke() {
        BuildingDefinitionLoader.clearCache();
        List<BuildingDefinition> all = BuildingDefinitionLoader.loadAll();
        check(!all.isEmpty(), "buildings must load");
        check(all.stream().anyMatch(BuildingDefinition::genericFallback), "generic fallback archetype required");
        BuildingDefinition house = BuildingDefinitionLoader.decode("""
                {"id":"tmp_house","displayName":"Tmp","cultureId":"generic","architectureFamily":"MEDIEVAL_FACHWERK",
                 "role":"HOUSE","tier":"HAMLET","district":"RESIDENTIAL","wealthMin":0,"wealthMax":1,
                 "footprintWidth":9,"footprintDepth":9,"genericFallback":true}
                """);
        check(house.role() == StructureRole.HOUSE, "decoded house role");
    }

    private static void fallbackChain() {
        BuildingDefinitionLoader.clearCache();
        CultureDefinitionLoader.clearCache();

        // Exact culture match for aster_riverborn house.
        BuildingDefinition exact = BuildingTemplateRegistry.require(new BuildingTemplateRegistry.Query(
                "aster_riverborn", StructureRole.HOUSE, Settlement.Tier.HAMLET,
                SettlementDistrict.RESIDENTIAL, 0.2, 20, 20));
        check("aster_riverborn".equals(exact.cultureId()), "exact culture house: " + exact.id());

        // Unknown culture with related family via culture pack → family or generic.
        BuildingDefinition related = BuildingTemplateRegistry.require(new BuildingTemplateRegistry.Query(
                "aster_riverborn", StructureRole.MARKET, Settlement.Tier.VILLAGE,
                SettlementDistrict.MARKET, 0.5, 30, 30));
        check(related.genericFallback() || related.architectureFamily().name().contains("MEDIEVAL")
                        || "generic".equals(related.cultureId()),
                "market should fall to family/generic, got " + related.id());

        // Completely unknown culture id → generic LR fallback.
        BuildingDefinition generic = BuildingTemplateRegistry.require(new BuildingTemplateRegistry.Query(
                "no_such_culture_zzz", StructureRole.HOUSE, Settlement.Tier.HAMLET,
                SettlementDistrict.RESIDENTIAL, 0.1, 20, 20));
        check(generic.genericFallback() || "generic".equals(generic.cultureId()),
                "unknown culture must hit generic fallback: " + generic.id());

        // Tiny footprint that only generic cottage may fit after soft match.
        BuildingDefinition tight = BuildingTemplateRegistry.find(new BuildingTemplateRegistry.Query(
                "no_such_culture_zzz", StructureRole.HOUSE, Settlement.Tier.CAMP,
                SettlementDistrict.RESIDENTIAL, 0.1, 9, 9)).orElse(null);
        check(tight != null, "tight footprint still finds a house template");
    }

    private static void cultureLoaderSmoke() {
        CultureDefinitionLoader.clearCache();
        Map<String, CultureDefinition> cultures = CultureDefinitionRegistry.all();
        check(cultures.size() >= 13, "≥13 cultures (12 surface + wizard_trees), got " + cultures.size());
        check(cultures.containsKey("wizard_trees"), "wizard_trees culture");
        check(cultures.containsKey("aster_riverborn"), "aster_riverborn culture");
        CultureDefinition aster = CultureDefinitionRegistry.require("aster_riverborn");
        check(aster.architectureFamily() != null, "architecture family");
        check(!aster.districtTendencies().isEmpty(), "district tendencies");
        check(!aster.materialPalette().isEmpty(), "material palette hints");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
