package dev.livingrealms.minecraft.gametest;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.construction.WorldMutationGuard;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
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
}
