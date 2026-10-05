package dev.livingrealms.api;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import java.util.Map;
import java.util.Optional;
@FunctionalInterface
public interface SpeciesProvider {
    Map<String, SpeciesDefinition> all();
    default Optional<SpeciesDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(all().get(id));
    }
}
