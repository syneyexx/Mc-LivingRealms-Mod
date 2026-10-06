package dev.livingrealms.minecraft.construction;

import dev.livingrealms.sim.world.RoadsideSite;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/** Shared immutable roadside-site footprint/material template for worldgen and runtime projection. */
public final class RoadsideSiteTemplate {
    public record Placement(int materialIndex, int dx, int dz, BlockState state) {}

    private RoadsideSiteTemplate() {}

    public static List<Placement> placements(RoadsideSite site) {
        int[][] offsets = offsetsFor(site.type());
        int target = Math.max(1, Math.min(offsets.length, footprintBudget(site)));
        List<Placement> out = new ArrayList<>(target);
        for (int i = 0; i < target; i++) {
            int offsetIndex = Math.floorMod(i + Long.hashCode(site.id()), offsets.length);
            out.add(new Placement(i, offsets[offsetIndex][0], offsets[offsetIndex][1], blockFor(site, i)));
        }
        return List.copyOf(out);
    }

    public static int footprintBudget(RoadsideSite site) {
        return switch (site.lifecycle()) {
            case RUINED -> 2;
            case ABANDONED -> 3;
            case REPAIRED, ACTIVE -> switch (site.type()) {
                case WAYSTATION, TRAVELER_CAMP, SHEPHERD_CAMP, HUNTER_CAMP -> 6;
                case SHRINE, LOGGING_SITE, TOLL_POST, BATTLEFIELD_MEMORIAL -> 5;
                case MILESTONE, ABANDONED_CART, GALLOWS -> 3;
            };
        };
    }

    public static int[][] offsetsFor(RoadsideSite.Type type) {
        return switch (type) {
            case MILESTONE, ABANDONED_CART, GALLOWS -> new int[][]{{0, 0}, {1, 0}, {0, 1}};
            case SHRINE, TOLL_POST -> new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            default -> new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 1}, {-2, -1}, {1, -2}};
        };
    }

    public static BlockState blockFor(RoadsideSite site, int index) {
        boolean ruined = site.lifecycle() == RoadsideSite.Lifecycle.RUINED
                || site.lifecycle() == RoadsideSite.Lifecycle.ABANDONED;
        return switch (site.type()) {
            case WAYSTATION -> index == 0
                    ? (ruined ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.CAMPFIRE.defaultBlockState())
                    : (index == 1 ? Blocks.OAK_FENCE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            case SHRINE -> index == 0
                    ? Blocks.STONE_BRICKS.defaultBlockState()
                    : (index == 1 ? Blocks.TORCH.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            case MILESTONE -> Blocks.COBBLESTONE_WALL.defaultBlockState();
            case TRAVELER_CAMP -> index == 0
                    ? Blocks.CAMPFIRE.defaultBlockState()
                    : (index % 2 == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
            case HUNTER_CAMP -> index == 0
                    ? Blocks.CAMPFIRE.defaultBlockState()
                    : Blocks.OAK_LOG.defaultBlockState();
            case SHEPHERD_CAMP -> index == 0
                    ? Blocks.HAY_BLOCK.defaultBlockState()
                    : Blocks.OAK_FENCE.defaultBlockState();
            case LOGGING_SITE -> index == 0
                    ? Blocks.STRIPPED_OAK_LOG.defaultBlockState()
                    : Blocks.OAK_LOG.defaultBlockState();
            case TOLL_POST -> index == 0
                    ? Blocks.OAK_FENCE.defaultBlockState()
                    : (index == 1 ? Blocks.WHITE_BANNER.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            case ABANDONED_CART -> index == 0
                    ? Blocks.OAK_FENCE.defaultBlockState()
                    : Blocks.OAK_PLANKS.defaultBlockState();
            case BATTLEFIELD_MEMORIAL -> index == 0
                    ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : Blocks.STONE_BRICK_WALL.defaultBlockState();
            case GALLOWS -> Blocks.OAK_FENCE.defaultBlockState();
        };
    }

    /**
     * Worldgen-safe representation: avoid targets with block entities/ticking setup during chunk
     * decoration. Runtime-created later-history sites may still use the richer active blocks.
     */
    public static BlockState worldgenState(BlockState state) {
        if (state.is(Blocks.CAMPFIRE)) return Blocks.TORCH.defaultBlockState();
        if (state.is(Blocks.WHITE_BANNER)) return Blocks.WHITE_WOOL.defaultBlockState();
        return state;
    }

    public static boolean isRoadsideMaterial(BlockState state) {
        return state.is(Blocks.CAMPFIRE) || state.is(Blocks.OAK_FENCE) || state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.TORCH)
                || state.is(Blocks.COBBLESTONE_WALL) || state.is(Blocks.STONE_BRICK_WALL)
                || state.is(Blocks.WHITE_WOOL) || state.is(Blocks.OAK_LOG) || state.is(Blocks.STRIPPED_OAK_LOG)
                || state.is(Blocks.HAY_BLOCK) || state.is(Blocks.WHITE_BANNER)
                || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.COBBLESTONE);
    }
}
