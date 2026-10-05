package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.world.SimulationState;
import java.util.Objects;

/**
 * Canonical dirty-queue for player-registered structures. Minecraft block events mark structures
 * dirty; this runtime drains a bounded queue and applies capacity/validity updates without
 * force-loading chunks (physical scan happens only when the caller provides fresh metrics).
 */
public final class PlayerStructureRevalidation {
    public static final int MAX_QUEUE = 256;
    public static final int MAX_PER_TICK = 4;

    private PlayerStructureRevalidation() {}

    /** Marks structures whose bounding box contains the block for later revalidation. */
    public static int markDirtyAt(SimulationState state, int x, int y, int z) {
        Objects.requireNonNull(state, "state");
        int marked = 0;
        for (RegisteredPlayerStructure structure : state.registeredPlayerStructures()) {
            if (!structure.valid()) continue;
            if (x < structure.minX() || x > structure.maxX()) continue;
            if (y < structure.minY() - 1 || y > structure.maxY() + 1) continue;
            if (z < structure.minZ() || z > structure.maxZ()) continue;
            if (state.enqueuePlayerStructureRevalidation(structure.id())) marked++;
        }
        return marked;
    }

    /**
     * Applies a completed physical re-survey. Capacity may rise or fall; invalid structures lose
     * housing credit. Unloaded/deferred surveys must not call this with a failing open-shell result.
     */
    public static PlayerStructureRegistration.Result applySurvey(SimulationState state, long structureId,
                                                                 PlayerStructureValidator.SurveyMetrics metrics,
                                                                 long fingerprint,
                                                                 boolean deferredUnloaded) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(metrics, "metrics");
        RegisteredPlayerStructure structure = state.findRegisteredPlayerStructure(structureId).orElse(null);
        if (structure == null) return PlayerStructureRegistration.Result.fail("structure_missing");
        if (deferredUnloaded) {
            // Leave previous validity unchanged — cannot inspect while unloaded.
            return PlayerStructureRegistration.Result.ok(structureId, structure.capacity());
        }
        var validation = PlayerStructureValidator.validate(structure.role(), metrics);
        if (!validation.ok()) {
            return PlayerStructureRegistration.invalidate(state, structureId, validation.reason());
        }
        return PlayerStructureRegistration.updateCapacity(state, structureId, validation.capacity(), fingerprint);
    }

    /** Drain up to {@code limit} dirty structure IDs for physical re-scan by the Minecraft adapter. */
    public static long[] pollDirty(SimulationState state, int limit) {
        Objects.requireNonNull(state, "state");
        return state.pollPlayerStructureRevalidation(Math.max(0, Math.min(MAX_PER_TICK, limit)));
    }
}
