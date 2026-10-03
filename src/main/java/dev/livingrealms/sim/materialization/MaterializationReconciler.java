package dev.livingrealms.sim.materialization;

import java.util.*;

/**
 * Converts desired cohort materialization counts into deterministic slot-level spawn/despawn actions.
 * Slot identity prevents duplicate physical animals after chunk reloads or repeated reconciliation.
 */
public final class MaterializationReconciler {
    private MaterializationReconciler() {}

    public static ProjectionPlan reconcile(Collection<MaterializationRequest> requests,
                                           Collection<PhysicalProjection> actual) {
        Objects.requireNonNull(requests, "requests");
        Objects.requireNonNull(actual, "actual");

        Map<Long, MaterializationRequest> desiredByGroup = new TreeMap<>();
        for (MaterializationRequest request : requests) {
            MaterializationRequest previous = desiredByGroup.put(request.populationGroupId(), request);
            if (previous != null) throw new IllegalArgumentException("Duplicate materialization request for group " + request.populationGroupId());
        }

        Map<Long, List<PhysicalProjection>> actualByGroup = new TreeMap<>();
        for (PhysicalProjection projection : actual) {
            actualByGroup.computeIfAbsent(projection.populationGroupId(), ignored -> new ArrayList<>()).add(projection);
        }
        actualByGroup.values().forEach(list -> list.sort(Comparator.comparing(PhysicalProjection::entityKey)));

        List<ProjectionSpawn> spawns = new ArrayList<>();
        List<ProjectionDespawn> despawns = new ArrayList<>();
        Set<Long> allGroups = new TreeSet<>();
        allGroups.addAll(desiredByGroup.keySet());
        allGroups.addAll(actualByGroup.keySet());

        for (long groupId : allGroups) {
            MaterializationRequest request = desiredByGroup.get(groupId);
            List<PhysicalProjection> loaded = actualByGroup.getOrDefault(groupId, List.of());

            if (request == null) {
                for (PhysicalProjection projection : loaded) {
                    despawns.add(remove(projection, ProjectionRemovalReason.ORPHANED_GROUP));
                }
                continue;
            }

            int desiredCount = Math.max(0, request.desiredPhysicalCount());
            Map<Integer, PhysicalProjection> keptBySlot = new TreeMap<>();
            for (PhysicalProjection projection : loaded) {
                if (!projection.speciesId().equals(request.speciesId())) {
                    despawns.add(remove(projection, ProjectionRemovalReason.SPECIES_MISMATCH));
                    continue;
                }
                if (projection.slot() >= desiredCount) {
                    ProjectionRemovalReason reason = desiredCount == 0
                            ? ProjectionRemovalReason.OUTSIDE_PHYSICAL_LOD
                            : ProjectionRemovalReason.OVER_BUDGET;
                    despawns.add(remove(projection, reason));
                    continue;
                }
                PhysicalProjection previous = keptBySlot.putIfAbsent(projection.slot(), projection);
                if (previous != null) despawns.add(remove(projection, ProjectionRemovalReason.DUPLICATE_SLOT));
            }

            for (int slot = 0; slot < desiredCount; slot++) {
                if (!keptBySlot.containsKey(slot)) {
                    spawns.add(new ProjectionSpawn(groupId, request.speciesId(), slot));
                }
            }
        }

        spawns.sort(Comparator.comparingLong(ProjectionSpawn::populationGroupId).thenComparingInt(ProjectionSpawn::slot));
        despawns.sort(Comparator.comparingLong(ProjectionDespawn::populationGroupId)
                .thenComparingInt(ProjectionDespawn::slot)
                .thenComparing(ProjectionDespawn::entityKey));
        return new ProjectionPlan(spawns, despawns);
    }

    private static ProjectionDespawn remove(PhysicalProjection projection, ProjectionRemovalReason reason) {
        return new ProjectionDespawn(projection.entityKey(), projection.populationGroupId(), projection.slot(), reason);
    }
}
