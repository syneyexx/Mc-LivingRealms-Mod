package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Keeps CITY+ cores from looking like untouched forest inside the walls.
 * Paves only the two cardinal arterials through the keep — never a countryside-scale lattice grid.
 */
public final class UrbanCoreMaterializer {
    private static final double ACTIVATION = 220.0;
    private static int cursor;

    private UrbanCoreMaterializer() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        if (level.players().isEmpty()) return;
        int budget = 36;
        AuthoredBlockLedger ledger = data.authoredBlocks();
        java.util.List<Settlement> cities = new java.util.ArrayList<>();
        for (Faction faction : data.state().factions()) {
            for (Settlement settlement : faction.settlements()) {
                if (settlement.tier().ordinal() < Settlement.Tier.CITY.ordinal()) continue;
                if (!nearPlayer(level, settlement)) continue;
                cities.add(settlement);
            }
        }
        if (cities.isEmpty()) return;
        cursor = Math.floorMod(cursor, cities.size());
        Settlement settlement = cities.get(cursor++);
        int radius = settlement.tier() == Settlement.Tier.METROPOLIS ? 96 : 72;
        int cx = (int) Math.round(settlement.position().x());
        int cz = (int) Math.round(settlement.position().z());
        // Only the cross through the keep (NS + EW), 3 blocks wide — no every-5 lattice over nature.
        int phase = Math.floorMod((int) (level.getGameTime() / 20L), Math.max(1, radius * 2 + 1));
        int along = -radius + phase;
        for (int side = -1; side <= 1 && budget > 0; side++) {
            budget -= clearAndPave(level, ledger, cx + side, cz + along, true);
            budget -= clearAndPave(level, ledger, cx + along, cz + side, true);
        }
        if (budget < 36) data.setDirty();
    }

    private static int clearAndPave(ServerLevel level, AuthoredBlockLedger ledger, int x, int z, boolean arterial) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int floor = level.getMinBuildHeight() + 1;
        while (y > floor) {
            BlockState st = level.getBlockState(new BlockPos(x, y, z));
            if (st.is(BlockTags.LEAVES) || st.canBeReplaced() || WorldMutationGuard.isNaturalTreeLog(level, new BlockPos(x, y, z))) {
                y--;
                continue;
            }
            break;
        }
        BlockPos ground = new BlockPos(x, y, z);
        BlockState groundState = level.getBlockState(ground);
        if (!groundState.getFluidState().isEmpty() || !level.getFluidState(ground.above()).isEmpty()) return 0;
        int used = 0;
        for (int i = 1; i <= 8; i++) {
            BlockPos p = ground.above(i);
            BlockState st = level.getBlockState(p);
            if (st.isAir()) continue;
            if (st.canBeReplaced() || st.is(BlockTags.LEAVES) || WorldMutationGuard.isNaturalTreeLog(level, p) || st.is(Blocks.SNOW)) {
                if (WorldMutationGuard.trySetAuthored(level, p, Blocks.AIR.defaultBlockState(), ledger, AuthoredOwnerType.SETTLEMENT_ROAD, false, true))
                    used++;
            } else break;
        }
        AuthoredOwnerType existing = ledger.ownerType(x, y, z);
        if (existing != null && existing != AuthoredOwnerType.SETTLEMENT_ROAD && existing != AuthoredOwnerType.INTERCITY_ROUTE)
            return used;
        if (existing == null && !WorldMutationGuard.isNaturalTerrain(groundState) && !groundState.is(Blocks.DIRT_PATH)
                && !groundState.is(Blocks.GRASS_BLOCK) && !groundState.is(Blocks.DIRT) && !groundState.is(Blocks.COARSE_DIRT)
                && !groundState.is(Blocks.PODZOL) && groundState.getFluidState().isEmpty()) {
            return used;
        }
        BlockState path = arterial
                ? FactionBlockPalette.state(1L, PaletteSlot.PATH)
                : Blocks.DIRT_PATH.defaultBlockState();
        if (WorldMutationGuard.trySetAuthored(level, ground, path, ledger, AuthoredOwnerType.SETTLEMENT_ROAD, true, false))
            used++;
        return used;
    }

    private static boolean nearPlayer(ServerLevel level, Settlement settlement) {
        double x = settlement.position().x(), z = settlement.position().z();
        return level.players().stream().anyMatch(p -> {
            double dx = p.getX() - x, dz = p.getZ() - z;
            return dx * dx + dz * dz <= ACTIVATION * ACTIVATION;
        });
    }
}
