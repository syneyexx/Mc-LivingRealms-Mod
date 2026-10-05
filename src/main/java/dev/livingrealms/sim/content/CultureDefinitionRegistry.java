package dev.livingrealms.sim.content;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Process-wide culture definition registry backed by {@link CultureDefinitionLoader}. */
public final class CultureDefinitionRegistry {
    private CultureDefinitionRegistry() {}

    public static Map<String, CultureDefinition> all() {
        return CultureDefinitionLoader.loadAll();
    }

    public static Optional<CultureDefinition> find(String id) {
        return CultureDefinitionLoader.find(id);
    }

    public static CultureDefinition getOrGeneric(String id) {
        Optional<CultureDefinition> found = find(id);
        if (found.isPresent()) return found.get();
        // Soft fallback: first medieval surface culture, else any.
        for (CultureDefinition c : all().values()) {
            if (c.architectureFamily().name().contains("MEDIEVAL")) return c;
        }
        Collection<CultureDefinition> values = all().values();
        if (values.isEmpty()) throw new IllegalStateException("empty culture registry");
        return values.iterator().next();
    }

    public static CultureDefinition forRealmCultureId(String cultureId) {
        Objects.requireNonNull(cultureId, "cultureId");
        return getOrGeneric(cultureId);
    }
}
