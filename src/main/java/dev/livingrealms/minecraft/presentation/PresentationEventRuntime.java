package dev.livingrealms.minecraft.presentation;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.presentation.CivicChoreographyPlanner;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentation;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minecraft-side consumer of non-authoritative civic choreography. Does not mutate canonical state.
 */
public final class PresentationEventRuntime {
    public static final int MAX_NEARBY_EVENTS = 24;

    private PresentationEventRuntime() {}

    public record NearbyCue(long settlementId, String settlementName, String kind, String cue,
                            double intensity, String farmLook) {}

    public static List<NearbyCue> collectNearby(SimulationState state, double playerX, double playerZ,
                                                double radiusBlocks) {
        Objects.requireNonNull(state, "state");
        double r2 = radiusBlocks * radiusBlocks;
        List<NearbyCue> out = new ArrayList<>();
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                double dx = settlement.position().x() - playerX;
                double dz = settlement.position().z() - playerZ;
                if (dx * dx + dz * dz > r2) continue;
                var farm = SeasonalFarmPresentation.forSettlement(state, settlement);
                for (var event : CivicChoreographyPlanner.planSettlement(state, faction, settlement)) {
                    out.add(new NearbyCue(settlement.id(), settlement.name(), event.kind().name(),
                            event.cue(), event.intensity(), farm.look().name()));
                    if (out.size() >= MAX_NEARBY_EVENTS) return List.copyOf(out);
                }
            }
        }
        return List.copyOf(out);
    }
}
