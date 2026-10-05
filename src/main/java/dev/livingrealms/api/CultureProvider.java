package dev.livingrealms.api;
import dev.livingrealms.sim.content.CultureDefinition;
import java.util.Map;
import java.util.Optional;
public interface CultureProvider {
    Map<String, CultureDefinition> all();
    Optional<CultureDefinition> find(String id);
    default CultureDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("unknown culture: " + id));
    }
}
