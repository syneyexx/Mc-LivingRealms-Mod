package dev.livingrealms.sim.world.projection;

import dev.livingrealms.sim.world.CitizenJourney;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Pure LOD request: one physical traveler for one active {@link CitizenJourney}. */
public record CitizenJourneyProjection(long journeyId, long factionId, long originSettlementId,
                                       long citizenId, CitizenJourney.Purpose purpose,
                                       SimPosition position, double nearestPlayerDistance) {
    public CitizenJourneyProjection {
        if (journeyId <= 0 || factionId <= 0 || originSettlementId <= 0) throw new IllegalArgumentException("ids");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(nearestPlayerDistance) || nearestPlayerDistance < 0) {
            throw new IllegalArgumentException("nearestPlayerDistance");
        }
        citizenId = Math.max(0, citizenId);
    }

    public String projectionKey() {
        return "journey:" + journeyId;
    }
}
