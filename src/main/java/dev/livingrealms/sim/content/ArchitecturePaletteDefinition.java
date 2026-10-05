package dev.livingrealms.sim.content;

import dev.livingrealms.sim.construction.CultureArchitecture;
import java.util.Map;
import java.util.Objects;

/** Wave 15.1 architecture material palette hints keyed by {@link CultureArchitecture}. */
public record ArchitecturePaletteDefinition(
        String id,
        CultureArchitecture architectureFamily,
        Map<String, String> slots,
        boolean genericFallback
) {
    public ArchitecturePaletteDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(architectureFamily, "architectureFamily");
        Objects.requireNonNull(slots, "slots");
        if (id.isBlank()) throw new IllegalArgumentException("palette id");
        if (slots.isEmpty()) throw new IllegalArgumentException("palette slots empty");
        slots = Map.copyOf(slots);
    }

    public String slot(String name) {
        return slots.get(name);
    }
}
