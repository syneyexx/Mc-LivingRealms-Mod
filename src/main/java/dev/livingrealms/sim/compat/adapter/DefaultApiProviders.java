package dev.livingrealms.sim.compat.adapter;

import dev.livingrealms.api.CultureProvider;
import dev.livingrealms.api.SpeciesProvider;
import dev.livingrealms.api.StructureProvider;
import dev.livingrealms.api.TradeGoodsProvider;
import dev.livingrealms.sim.content.BuildingTemplateRegistry;
import dev.livingrealms.sim.content.CultureDefinition;
import dev.livingrealms.sim.content.CultureDefinitionRegistry;
import dev.livingrealms.sim.ecology.SpeciesCatalog;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import dev.livingrealms.sim.faction.ResourceType;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public final class DefaultApiProviders {
    private DefaultApiProviders() {}
    public static SpeciesProvider species() { return SpeciesCatalog::starter; }
    public static SpeciesProvider species(Map<String, SpeciesDefinition> catalog) {
        Map<String, SpeciesDefinition> frozen = Map.copyOf(catalog);
        return () -> frozen;
    }
    public static CultureProvider cultures() {
        return new CultureProvider() {
            @Override public Map<String, CultureDefinition> all() { return CultureDefinitionRegistry.all(); }
            @Override public Optional<CultureDefinition> find(String id) { return CultureDefinitionRegistry.find(id); }
        };
    }
    public static StructureProvider structures() { return BuildingTemplateRegistry::find; }
    public static TradeGoodsProvider tradeGoods() {
        return () -> Arrays.stream(ResourceType.values()).map(Enum::name).toList();
    }
}
