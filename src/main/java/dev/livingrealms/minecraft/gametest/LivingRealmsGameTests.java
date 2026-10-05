package dev.livingrealms.minecraft.gametest;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.construction.WorldMutationGuard;
import dev.livingrealms.minecraft.presentation.SeasonalFarmPresentationRuntime;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentation;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentationPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real Minecraft GameTests for construction/provenance safety.
 * Uses data/livingrealms/structure/gametests/empty.nbt as the batch template.
 */
@GameTestHolder(LivingRealms.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingRealmsGameTests {
    private LivingRealmsGameTests() {}

    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void alreadyCorrectDoesNotClaimOwnership(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, Blocks.STONE_BRICKS);
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        boolean wrote = WorldMutationGuard.trySetAuthored(
                helper.getLevel(),
                helper.absolutePos(pos),
                Blocks.STONE_BRICKS.defaultBlockState(),
                ledger,
                AuthoredOwnerType.SETTLEMENT_STRUCTURE,
                true,
                false
        );
        helper.assertTrue(wrote, "identical geometry should satisfy without failing");
        helper.assertFalse(
                ledger.isAuthored(helper.absolutePos(pos).getX(), helper.absolutePos(pos).getY(), helper.absolutePos(pos).getZ()),
                "ALREADY_CORRECT must not adopt unknown player stone bricks as LR-authored"
        );
        helper.succeed();
    }

    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void unknownStructureIsProtected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.OAK_PLANKS);
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        boolean wrote = WorldMutationGuard.trySetAuthored(
                helper.getLevel(),
                helper.absolutePos(pos),
                Blocks.COBBLESTONE.defaultBlockState(),
                ledger,
                AuthoredOwnerType.SETTLEMENT_STRUCTURE,
                true,
                false
        );
        helper.assertFalse(wrote, "unknown player planks must not be overwritten");
        helper.assertBlockPresent(Blocks.OAK_PLANKS, pos);
        helper.succeed();
    }

    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void naturalTerrainMayBeAuthored(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, Blocks.GRASS_BLOCK);
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        BlockPos abs = helper.absolutePos(pos);
        boolean wrote = WorldMutationGuard.trySetAuthored(
                helper.getLevel(),
                abs,
                Blocks.DIRT_PATH.defaultBlockState(),
                ledger,
                AuthoredOwnerType.SETTLEMENT_ROAD,
                true,
                false
        );
        helper.assertTrue(wrote, "natural terrain may become an authored road");
        helper.assertTrue(ledger.ownerType(abs.getX(), abs.getY(), abs.getZ()) == AuthoredOwnerType.SETTLEMENT_ROAD,
                "authored road provenance must be recorded");
        helper.assertBlockPresent(Blocks.DIRT_PATH, pos);
        helper.succeed();
    }

    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void typedOwnersDoNotOverwriteEachOther(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos, Blocks.GRASS_BLOCK);
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        BlockPos abs = helper.absolutePos(pos);
        helper.assertTrue(WorldMutationGuard.trySetAuthored(
                helper.getLevel(), abs, Blocks.STONE_BRICKS.defaultBlockState(), ledger,
                AuthoredOwnerType.SETTLEMENT_STRUCTURE, true, false), "structure place");
        helper.assertFalse(WorldMutationGuard.trySetAuthored(
                helper.getLevel(), abs, Blocks.DIRT_PATH.defaultBlockState(), ledger,
                AuthoredOwnerType.INTERCITY_ROUTE, true, false), "route must not steal settlement structure");
        helper.assertBlockPresent(Blocks.STONE_BRICKS, pos);
        helper.succeed();
    }

    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void seasonalFarmTouchesOnlyAuthoredLand(GameTestHelper helper) {
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        BlockPos authoredFarm = new BlockPos(5, 2, 5);
        BlockPos playerFarm = new BlockPos(6, 2, 6);
        helper.setBlock(authoredFarm, Blocks.FARMLAND);
        helper.setBlock(authoredFarm.above(), Blocks.WHEAT.defaultBlockState().setValue(
                net.minecraft.world.level.block.CropBlock.AGE, 3));
        helper.setBlock(playerFarm, Blocks.FARMLAND);
        helper.setBlock(playerFarm.above(), Blocks.WHEAT.defaultBlockState().setValue(
                net.minecraft.world.level.block.CropBlock.AGE, 3));

        BlockPos authoredAbs = helper.absolutePos(authoredFarm);
        BlockPos playerAbs = helper.absolutePos(playerFarm);
        helper.assertTrue(ledger.record(
                authoredAbs.getX(), authoredAbs.getY(), authoredAbs.getZ(),
                AuthoredOwnerType.SETTLEMENT_STRUCTURE), "record authored farmland");

        var plan = SeasonalFarmPresentationPlan.fromLook(
                new SeasonalFarmPresentation.FarmLook(
                        SeasonalFarmPresentation.CropLook.LUSH, "test lush", 1.15));

        helper.assertTrue(
                SeasonalFarmPresentationRuntime.tryApplyAuthoredCell(
                        helper.getLevel(), ledger, authoredAbs, plan),
                "LR farmland may receive seasonal look");
        helper.assertFalse(
                SeasonalFarmPresentationRuntime.tryApplyAuthoredCell(
                        helper.getLevel(), ledger, playerAbs, plan),
                "player farmland without ledger must be skipped");

        helper.assertBlockPresent(Blocks.WHEAT, authoredFarm.above());
        helper.assertTrue(
                helper.getLevel().getBlockState(helper.absolutePos(authoredFarm.above()))
                        .getValue(net.minecraft.world.level.block.CropBlock.AGE) == 7,
                "lush look sets wheat age 7");
        helper.assertTrue(
                helper.getLevel().getBlockState(helper.absolutePos(playerFarm.above()))
                        .getValue(net.minecraft.world.level.block.CropBlock.AGE) == 3,
                "player crop age unchanged");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(authoredFarm)).inflate(2)).isEmpty(),
                "presentation must not drop harvest items");
        SeasonalFarmPresentationRuntime.clear();
        helper.succeed();
    }

    @GameTest(template = "gametests/empty", timeoutTicks = 40)
    public static void closedHouseSurveyPasses(GameTestHelper helper) {
        // 7x7 closed house: floor, walls, roof, south door, one bed.
        BlockPos origin = new BlockPos(1, 1, 1);
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            helper.setBlock(origin.offset(x, 0, z), Blocks.OAK_PLANKS);
            helper.setBlock(origin.offset(x, 4, z), Blocks.OAK_PLANKS);
        }
        for (int y = 1; y <= 3; y++) for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            boolean wall = x == 0 || x == 6 || z == 0 || z == 6;
            helper.setBlock(origin.offset(x, y, z), wall ? Blocks.OAK_PLANKS : Blocks.AIR);
        }
        BlockPos door = origin.offset(3, 1, 0);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH));
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH));
        helper.setBlock(origin.offset(2, 1, 3), Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT)
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(origin.offset(3, 1, 3), Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD)
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST));
        var scan = dev.livingrealms.minecraft.construction.PlayerStructureSurvey.scanInterior(
                helper.getLevel(), helper.absolutePos(door));
        helper.assertTrue(scan.ok(), "closed house must survey: " + scan.message());
        helper.succeed();
    }

    @GameTest(template = "gametests/empty", timeoutTicks = 40)
    public static void missingRoofSurveyFails(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            helper.setBlock(origin.offset(x, 0, z), Blocks.OAK_PLANKS);
        }
        for (int y = 1; y <= 3; y++) for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            boolean wall = x == 0 || x == 6 || z == 0 || z == 6;
            helper.setBlock(origin.offset(x, y, z), wall ? Blocks.OAK_PLANKS : Blocks.AIR);
        }
        BlockPos door = origin.offset(3, 1, 0);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH));
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH));
        var scan = dev.livingrealms.minecraft.construction.PlayerStructureSurvey.scanInterior(
                helper.getLevel(), helper.absolutePos(door));
        helper.assertFalse(scan.ok(), "missing roof must fail survey");
        helper.succeed();
    }

    @GameTest(template = "gametests/empty", timeoutTicks = 40)
    public static void openWallSurveyFails(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            helper.setBlock(origin.offset(x, 0, z), Blocks.OAK_PLANKS);
            helper.setBlock(origin.offset(x, 4, z), Blocks.OAK_PLANKS);
        }
        for (int y = 1; y <= 3; y++) for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            boolean wall = x == 0 || x == 6 || z == 0; // missing z==6 wall
            helper.setBlock(origin.offset(x, y, z), wall ? Blocks.OAK_PLANKS : Blocks.AIR);
        }
        BlockPos door = origin.offset(3, 1, 0);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH));
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH));
        var scan = dev.livingrealms.minecraft.construction.PlayerStructureSurvey.scanInterior(
                helper.getLevel(), helper.absolutePos(door));
        helper.assertFalse(scan.ok(), "open wall must fail enclosure");
        helper.succeed();
    }

}
