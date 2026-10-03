package dev.livingrealms.sim.materialization;

import dev.livingrealms.sim.world.SimulationLod;

/** Desired projection for one aggregate population group. */
public record MaterializationRequest(
        long populationGroupId,
        String speciesId,
        SimulationLod lod,
        int desiredPhysicalCount,
        double nearestPlayerDistance
) {}
