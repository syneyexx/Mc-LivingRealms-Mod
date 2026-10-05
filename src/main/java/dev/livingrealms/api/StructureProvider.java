package dev.livingrealms.api;
import dev.livingrealms.sim.content.BuildingDefinition;
import dev.livingrealms.sim.content.BuildingTemplateRegistry;
import java.util.Optional;
@FunctionalInterface
public interface StructureProvider {
    Optional<BuildingDefinition> find(BuildingTemplateRegistry.Query query);
    default BuildingDefinition require(BuildingTemplateRegistry.Query query) {
        return find(query).orElseThrow(() -> new IllegalArgumentException("no building template for " + query.role()));
    }
}
