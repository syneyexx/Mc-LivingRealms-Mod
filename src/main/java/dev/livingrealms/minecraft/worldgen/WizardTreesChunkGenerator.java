package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.PaletteSlot;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Writes only the current chunk's immutable day-zero Wizard Trees underground slice. */
public final class WizardTreesChunkGenerator {
    private WizardTreesChunkGenerator() {}

    public static int generate(
            WorldgenFabricBlockWriter writer,
            WizardTreesWorldgenIndex.ChunkSlice slice) {
        int writes = 0;
        for (WizardTreesWorldgenIndex.WizardFabric fabric : slice.fabric()) {
            if (!canAuthorSlice(writer, fabric)) continue;
            writes += writeFabric(writer, fabric);
        }
        return writes;
    }

    private static boolean canAuthorSlice(
            WorldgenFabricBlockWriter writer,
            WizardTreesWorldgenIndex.WizardFabric fabric) {
        int turns = Math.floorMod(fabric.intent().rotationQuarterTurns(), 4);
        int cx = (int) Math.round(fabric.intent().center().x());
        int cz = (int) Math.round(fabric.intent().center().z());
        Set<Long> virtuallyCleared = new HashSet<>();

        for (BlockPlacement placement : fabric.blueprint().placements()) {
            int[] rotated = rotate(placement.dx(), placement.dz(), turns);
            int x = cx + rotated[0], z = cz + rotated[1];
            if (!writer.insideCurrentChunk(x, z)) continue;

            BlockPos pos = new BlockPos(x, fabric.baseY() + placement.dy(), z);
            boolean clearing = placement.slot() == PaletteSlot.AIR;
            if (!virtuallyCleared.contains(pos.asLong())
                    && !writer.canReplaceForWorldgen(
                    pos, clearing, AuthoredOwnerType.WIZARD_TREES)) {
                return false;
            }
            if (clearing) virtuallyCleared.add(pos.asLong());
        }
        return true;
    }

    private static int writeFabric(
            WorldgenFabricBlockWriter writer,
            WizardTreesWorldgenIndex.WizardFabric fabric) {
        int turns = Math.floorMod(fabric.intent().rotationQuarterTurns(), 4);
        int cx = (int) Math.round(fabric.intent().center().x());
        int cz = (int) Math.round(fabric.intent().center().z());

        Set<String> doors = new HashSet<>();
        for (BlockPlacement placement : fabric.blueprint().placements()) {
            if (placement.slot() == PaletteSlot.DOOR) {
                doors.add(key(placement.dx(), placement.dy(), placement.dz()));
            }
        }

        int writes = 0;
        for (BlockPlacement placement : fabric.blueprint().placements()) {
            int[] rotated = rotate(placement.dx(), placement.dz(), turns);
            int x = cx + rotated[0], z = cz + rotated[1];
            if (!writer.insideCurrentChunk(x, z)) continue;

            boolean doorUpper = placement.slot() == PaletteSlot.DOOR
                    && doors.contains(key(
                    placement.dx(), placement.dy() - 1, placement.dz()));
            if (writer.write(
                    fabric.factionId(),
                    placement.slot(),
                    new BlockPos(x, fabric.baseY() + placement.dy(), z),
                    turns,
                    doorUpper,
                    null,
                    AuthoredOwnerType.WIZARD_TREES)) {
                writes++;
            }
        }
        return writes;
    }

    private static int[] rotate(int x, int z, int turns) {
        return switch (turns) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            default -> new int[]{z, -x};
        };
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
