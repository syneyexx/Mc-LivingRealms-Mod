package dev.livingrealms.minecraft.gametest;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.construction.PlayerStructureSurvey;
import dev.livingrealms.sim.construction.PlayerStructureRegistration;
import dev.livingrealms.sim.construction.PlayerStructureValidator;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.player.PlayerActorIdentity;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Physical block GameTests for {@link PlayerStructureSurvey#scanInterior}.
 * Builds houses with {@link GameTestHelper#setBlock} inside {@code gametests/empty} (8×8×8).
 */
@GameTestHolder(LivingRealms.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingRealmsStructureSurveyGameTests {
    private LivingRealmsStructureSurveyGameTests() {}

    /** Closed 7×7 house with floor, roof, door, and bed → survey PASS. */
    @GameTest(template = "gametests/empty", timeoutTicks = 80)
    public static void closedHouseSurveyPasses(GameTestHelper helper) {
        BlockPos door = buildClosedHouse(helper, true, true, true);
        var scan = PlayerStructureSurvey.scanInterior(helper.getLevel(), helper.absolutePos(door));
        helper.assertTrue(scan.ok(), "closed house must survey: " + scan.message());
        helper.assertTrue(scan.metrics() != null && scan.metrics().enclosed, "metrics enclosed");
        helper.assertTrue(scan.metrics().beds >= 1, "bed counted");
        helper.assertTrue(scan.metrics().roofCoverage >= 0.5, "roof coverage");
        helper.assertTrue(scan.metrics().floorCoverage >= 0.5, "floor coverage");
        helper.succeed();
    }

    /** Same shell without a roof → survey FAIL. */
    @GameTest(template = "gametests/empty", timeoutTicks = 80)
    public static void missingRoofSurveyFails(GameTestHelper helper) {
        BlockPos door = buildClosedHouse(helper, false, true, true);
        var scan = PlayerStructureSurvey.scanInterior(helper.getLevel(), helper.absolutePos(door));
        helper.assertFalse(scan.ok(), "missing roof must fail survey");
        helper.assertTrue(
                scan.reason() == PlayerStructureSurvey.FailReason.OPEN_TO_EXTERIOR
                        || scan.reason() == PlayerStructureSurvey.FailReason.VALIDATION,
                "expected open/validation fail, got " + scan.reason());
        helper.succeed();
    }

    /** Closed shell with one open wall → survey FAIL. */
    @GameTest(template = "gametests/empty", timeoutTicks = 80)
    public static void openWallSurveyFails(GameTestHelper helper) {
        BlockPos door = buildClosedHouse(helper, true, true, true);
        // Punch a hole in the east wall so the interior floods into exterior air.
        for (int z = 1; z <= 5; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(6, y, z), Blocks.AIR);
            }
        }
        var scan = PlayerStructureSurvey.scanInterior(helper.getLevel(), helper.absolutePos(door));
        helper.assertFalse(scan.ok(), "open wall must fail survey");
        helper.assertTrue(scan.reason() == PlayerStructureSurvey.FailReason.OPEN_TO_EXTERIOR
                        || scan.reason() == PlayerStructureSurvey.FailReason.TOO_LARGE,
                "expected open/too-large, got " + scan.reason());
        helper.succeed();
    }

    /** Compact two-story house with stairs → PASS when scan supports multi-level interiors. */
    @GameTest(template = "gametests/empty", timeoutTicks = 100)
    public static void twoStoryWithStairsSurveyPasses(GameTestHelper helper) {
        BlockPos door = buildTwoStoryHouse(helper);
        var scan = PlayerStructureSurvey.scanInterior(helper.getLevel(), helper.absolutePos(door));
        helper.assertTrue(scan.ok(), "two-story house with stairs must survey: " + scan.message());
        helper.assertTrue(scan.metrics() != null && scan.metrics().height >= 2, "multi-level height");
        helper.succeed();
    }

    /** Duplicate registration of the same footprint is rejected by the registration API. */
    @GameTest(template = "gametests/empty", timeoutTicks = 100)
    public static void duplicateRegistrationRejected(GameTestHelper helper) {
        BlockPos doorRel = buildClosedHouse(helper, true, true, true);
        BlockPos absDoor = helper.absolutePos(doorRel);
        var scan = PlayerStructureSurvey.scanInterior(helper.getLevel(), absDoor);
        helper.assertTrue(scan.ok(), "precondition survey: " + scan.message());
        var m = scan.metrics();

        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String actor = PlayerActorIdentity.of(uuid);
        SimulationState state = new SimulationState(0x53555256L);
        var founded = PlayerSettlementFounder.found(state, actor, "Surveyor", "Surveyburg",
                new SimPosition(absDoor.getX(), absDoor.getZ()));
        helper.assertTrue(founded.success(), founded.reason());

        var metrics = new PlayerStructureValidator.SurveyMetrics(
                m.width, m.depth, m.height, m.interiorCells, m.inspected,
                m.roofCoverage, m.floorCoverage, m.beds, m.enclosed, m.validEntrance,
                false, false);
        var first = PlayerStructureRegistration.register(
                state, actor, founded.settlementId(), RegisteredPlayerStructure.Role.HOUSE,
                m.minX, m.minY, m.minZ, m.maxX, m.maxY, m.maxZ,
                absDoor.getX(), absDoor.getY(), absDoor.getZ(), metrics, 0xD001L);
        helper.assertTrue(first.success(), "first register: " + first.reason());

        var dup = PlayerStructureRegistration.register(
                state, actor, founded.settlementId(), RegisteredPlayerStructure.Role.HOUSE,
                m.minX, m.minY, m.minZ, m.maxX, m.maxY, m.maxZ,
                absDoor.getX(), absDoor.getY(), absDoor.getZ(), metrics, 0xD002L);
        helper.assertFalse(dup.success(), "duplicate registration must be rejected");
        helper.assertTrue(dup.reason() != null && dup.reason().toLowerCase().contains("already"),
                "duplicate reason mentions already: " + dup.reason());
        helper.succeed();
    }

    /**
     * Builds a closed 7×7×~5 house in the 8×8×8 empty template.
     * Origin corner (0,0,0) … far corner (6,y,6). Door on the south wall at (3,1,6).
     */
    private static BlockPos buildClosedHouse(GameTestHelper helper, boolean roof, boolean floor, boolean bed) {
        clearVolume(helper);
        int min = 0, max = 6;
        // Floor
        if (floor) {
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.OAK_PLANKS);
                }
            }
        }
        // Walls y=1..3
        for (int y = 1; y <= 3; y++) {
            for (int x = min; x <= max; x++) {
                helper.setBlock(new BlockPos(x, y, min), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(x, y, max), Blocks.OAK_PLANKS);
            }
            for (int z = min + 1; z <= max - 1; z++) {
                helper.setBlock(new BlockPos(min, y, z), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(max, y, z), Blocks.OAK_PLANKS);
            }
        }
        // Clear interior air
        for (int y = 1; y <= 3; y++) {
            for (int x = min + 1; x <= max - 1; x++) {
                for (int z = min + 1; z <= max - 1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        // Roof
        if (roof) {
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    helper.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
                }
            }
        }
        BlockPos door = placeDoor(helper, new BlockPos(3, 1, max), Direction.SOUTH);
        if (bed) {
            placeBed(helper, new BlockPos(1, 1, 1), Direction.EAST);
        }
        return door;
    }

    /** Two stories with an oak stair connecting levels; fits in 8 height. */
    private static BlockPos buildTwoStoryHouse(GameTestHelper helper) {
        clearVolume(helper);
        int min = 0, max = 6;
        // Ground floor
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.OAK_PLANKS);
            }
        }
        // Lower walls y=1..2
        for (int y = 1; y <= 2; y++) {
            for (int x = min; x <= max; x++) {
                helper.setBlock(new BlockPos(x, y, min), Blocks.STONE_BRICKS);
                helper.setBlock(new BlockPos(x, y, max), Blocks.STONE_BRICKS);
            }
            for (int z = min + 1; z <= max - 1; z++) {
                helper.setBlock(new BlockPos(min, y, z), Blocks.STONE_BRICKS);
                helper.setBlock(new BlockPos(max, y, z), Blocks.STONE_BRICKS);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int x = min + 1; x <= max - 1; x++) {
                for (int z = min + 1; z <= max - 1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        // Mid floor at y=3 with stair gap at (2,3,2)
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.OAK_PLANKS);
            }
        }
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
        // Stairs: lower half facing north into the gap
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
        // Upper walls y=4..5
        for (int y = 4; y <= 5; y++) {
            for (int x = min; x <= max; x++) {
                helper.setBlock(new BlockPos(x, y, min), Blocks.STONE_BRICKS);
                helper.setBlock(new BlockPos(x, y, max), Blocks.STONE_BRICKS);
            }
            for (int z = min + 1; z <= max - 1; z++) {
                helper.setBlock(new BlockPos(min, y, z), Blocks.STONE_BRICKS);
                helper.setBlock(new BlockPos(max, y, z), Blocks.STONE_BRICKS);
            }
        }
        for (int y = 4; y <= 5; y++) {
            for (int x = min + 1; x <= max - 1; x++) {
                for (int z = min + 1; z <= max - 1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        // Roof
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                helper.setBlock(new BlockPos(x, 6, z), Blocks.OAK_PLANKS);
            }
        }
        BlockPos door = placeDoor(helper, new BlockPos(3, 1, max), Direction.SOUTH);
        placeBed(helper, new BlockPos(4, 1, 1), Direction.WEST);
        return door;
    }

    private static void clearVolume(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                for (int z = 0; z < 8; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static BlockPos placeDoor(GameTestHelper helper, BlockPos lower, Direction facing) {
        var base = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.POWERED, false);
        helper.setBlock(lower, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(lower.above(), base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        return lower;
    }

    private static void placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        helper.setBlock(foot, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(foot.relative(facing), Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.HEAD));
    }
}
