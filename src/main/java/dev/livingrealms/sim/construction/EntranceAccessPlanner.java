package dev.livingrealms.sim.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure entrance/foundation access planner. Ensures public doorways are reachable from the street
 * grade without a one-block jump. Offsets are in <em>blueprint local</em> space (front = -Z);
 * Minecraft adapters rotate them with the building.
 */
public final class EntranceAccessPlanner {
    public record AccessFix(int dx, int dy, int dz, PaletteSlot slot) {
        public AccessFix {
            Objects.requireNonNull(slot, "slot");
        }
    }

    private EntranceAccessPlanner() {}

    /**
     * @param doorLocalX door local X (usually 0)
     * @param doorLocalZ door local Z (front wall, typically {@code -depth/2})
     * @param doorFloorY world Y of the walkable floor / lower door
     * @param approachY world Y of sidewalk/street immediately outside the door
     */
    public static List<AccessFix> plan(int doorLocalX, int doorLocalZ, int doorFloorY, int approachY) {
        int delta = doorFloorY - approachY;
        if (delta == 0) return List.of();
        ArrayList<AccessFix> out = new ArrayList<>();
        // Outside step direction in local space is further forward (-Z).
        if (delta > 0 && delta <= 3) {
            for (int i = 1; i <= delta; i++) {
                out.add(new AccessFix(doorLocalX, approachY + i, doorLocalZ - i, PaletteSlot.FOUNDATION));
            }
            out.add(new AccessFix(doorLocalX, doorFloorY, doorLocalZ - 1, PaletteSlot.PATH));
        } else if (delta < 0 && delta >= -3) {
            int depth = -delta;
            for (int i = 1; i <= depth; i++) {
                out.add(new AccessFix(doorLocalX, doorFloorY + (depth - i), doorLocalZ - i, PaletteSlot.FOUNDATION));
            }
        }
        return List.copyOf(out);
    }

    public static boolean isAccessible(int doorFloorY, int approachY) {
        return Math.abs(doorFloorY - approachY) <= 1;
    }
}
