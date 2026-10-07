package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.StructureBlueprint;
import java.util.Objects;

/**
 * Single global accept/reject decision for one fixed worldgen structure object.
 *
 * <p>Multi-chunk structures must not be preflighted independently per intersecting chunk. Every
 * chunk emits only already-approved pieces of the same immutable decision (base Y + accepted
 * flag). Terrain samples come from a generator-only height source so the decision is chunk-order
 * independent and never force-loads neighbors.</p>
 */
public final class WorldgenObjectAcceptance {
    public static final int MAX_FOUNDATION_RISE = 64;
    public static final int MAX_FOUNDATION_SINK = 10;

    @FunctionalInterface
    public interface HeightSampler {
        /** Natural ground / ocean-floor height at column (x,z). Must not load chunks. */
        int groundY(int x, int z);
    }

    public record Decision(
            String objectKey,
            int centerX,
            int centerZ,
            int baseY,
            boolean accepted,
            String rejectReason
    ) {
        public Decision {
            if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("objectKey");
            rejectReason = rejectReason == null ? "" : rejectReason;
        }
    }

    private WorldgenObjectAcceptance() {}

    public static Decision decide(
            ConstructionIntent intent,
            StructureBlueprint blueprint,
            HeightSampler surface,
            HeightSampler ground,
            int minBuildHeight) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(ground, "ground");

        String key = intent.key();
        int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
        int cx = (int) Math.round(intent.center().x());
        int cz = (int) Math.round(intent.center().z());
        int baseY = siteBaseY(cx, cz, blueprint.width(), blueprint.depth(), turns, surface);

        if (baseY <= minBuildHeight + 1) {
            return new Decision(key, cx, cz, baseY, false, "base below build limit");
        }

        boolean sawFoundation = false;
        for (BlockPlacement placement : blueprint.placements()) {
            if (placement.slot() != PaletteSlot.FOUNDATION || placement.dy() != 0) continue;
            sawFoundation = true;
            int[] rotated = rotate(placement.dx(), placement.dz(), turns);
            int x = cx + rotated[0];
            int z = cz + rotated[1];
            int columnGround = ground.groundY(x, z);
            int delta = baseY - columnGround;
            if (delta > MAX_FOUNDATION_RISE || delta < -MAX_FOUNDATION_SINK) {
                return new Decision(
                        key, cx, cz, baseY, false,
                        "foundation delta " + delta + " at " + x + "," + z);
            }
        }
        if (!sawFoundation) {
            // Non-foundation blueprints still need a stable base; accept when center is buildable.
            int centerGround = ground.groundY(cx, cz);
            int delta = baseY - centerGround;
            if (delta > MAX_FOUNDATION_RISE || delta < -MAX_FOUNDATION_SINK) {
                return new Decision(key, cx, cz, baseY, false, "center delta " + delta);
            }
        }
        return new Decision(key, cx, cz, baseY, true, "");
    }

    public static int siteBaseY(
            int centerX, int centerZ, int width, int depth, int turns, HeightSampler surface) {
        Objects.requireNonNull(surface, "surface");
        int w = (turns & 1) == 0 ? width : depth;
        int d = (turns & 1) == 0 ? depth : width;
        int hx = Math.max(1, w / 2);
        int hz = Math.max(1, d / 2);
        int y = surface.groundY(centerX, centerZ);
        y = Math.max(y, surface.groundY(centerX - hx, centerZ - hz));
        y = Math.max(y, surface.groundY(centerX + hx, centerZ - hz));
        y = Math.max(y, surface.groundY(centerX - hx, centerZ + hz));
        y = Math.max(y, surface.groundY(centerX + hx, centerZ + hz));
        y = Math.max(y, surface.groundY(centerX - hx, centerZ));
        y = Math.max(y, surface.groundY(centerX + hx, centerZ));
        y = Math.max(y, surface.groundY(centerX, centerZ - hz));
        y = Math.max(y, surface.groundY(centerX, centerZ + hz));
        return y;
    }

    private static int[] rotate(int x, int z, int turns) {
        return switch (turns) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            default -> new int[]{z, -x};
        };
    }
}
