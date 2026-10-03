package dev.livingrealms.sim.construction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable ordered block plan for one structure. */
public record StructureBlueprint(String id, int width, int depth, int height, List<BlockPlacement> placements) {
    public StructureBlueprint {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (width <= 0 || depth <= 0 || height <= 0) throw new IllegalArgumentException("dimensions");
        Objects.requireNonNull(placements, "placements");
        var copy = new ArrayList<>(placements);
        copy.sort(Comparator.comparing(BlockPlacement::phase)
                .thenComparingInt(BlockPlacement::dy)
                .thenComparingInt(BlockPlacement::dz)
                .thenComparingInt(BlockPlacement::dx));
        placements = List.copyOf(copy);
    }

    public int operationCount() { return placements.size(); }
}
