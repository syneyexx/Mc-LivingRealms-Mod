package dev.livingrealms.sim.construction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Role-specific topology/integrity checks that percent-complete alone cannot express.
 *
 * <p>These operate on planned/realized operation coordinates (core-testable) so Minecraft adapters
 * can reject incomplete roads/walls/buildings even when a ratio threshold would pass.
 */
public final class StructureIntegrityRules {
    private StructureIntegrityRules() {}

    public static boolean accepts(StructureRole role, List<BuildOperation> operations, StructureMaterializationReceipt receipt) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(receipt, "receipt");
        if (!receipt.physicallyAcceptable()) return false;
        return switch (role) {
            case ROAD -> roadConnected(operations, receipt);
            case WALL, GATE -> wallConnected(operations, receipt);
            case HOUSE, KEEP, MARKET, WAREHOUSE, WORKSHOP, BARRACKS, TEMPLE, SCHOOL, CLINIC, COURTHOUSE,
                    PRISON, TAVERN, BAKERY, BREWERY, MILL, FACTORY, DOCK, AIRFIELD -> buildingIntegrity(operations, receipt);
            default -> true;
        };
    }

    /** Connected walkable centerline: PATH ops must form one 4-connected component without large gaps. */
    public static boolean roadConnected(List<BuildOperation> operations, StructureMaterializationReceipt receipt) {
        Set<Long> pathCells = new HashSet<>();
        for (BuildOperation op : operations) {
            if (op.slot() != PaletteSlot.PATH && op.slot() != PaletteSlot.FOUNDATION) continue;
            pathCells.add(pack(op.x(), op.z()));
        }
        if (pathCells.isEmpty()) return receipt.omittedRequired() == 0 && receipt.expectedRequired() == 0;
        if (receipt.omittedRequired() > 0) {
            // Omitted expected cells break continuity by definition for roads.
            int totalCenter = pathCells.size() + receipt.omittedRequired();
            if (receipt.omittedRequired() > Math.max(1, totalCenter / 10)) return false;
        }
        return connectedComponentCount(pathCells) == 1;
    }

    public static boolean wallConnected(List<BuildOperation> operations, StructureMaterializationReceipt receipt) {
        Set<Long> wallCells = new HashSet<>();
        for (BuildOperation op : operations) {
            if (op.slot() != PaletteSlot.WALL && op.slot() != PaletteSlot.FOUNDATION) continue;
            wallCells.add(pack(op.x(), op.z()));
        }
        if (wallCells.isEmpty()) return receipt.expectedRequired() == 0;
        if (receipt.omittedRequired() > Math.max(1, wallCells.size() / 8)) return false;
        return connectedComponentCount(wallCells) <= 2;
    }

    public static boolean buildingIntegrity(List<BuildOperation> operations, StructureMaterializationReceipt receipt) {
        boolean hasFloor = false, hasDoor = false, hasWall = false;
        for (BuildOperation op : operations) {
            if (op.slot() == PaletteSlot.FLOOR || op.slot() == PaletteSlot.FOUNDATION) hasFloor = true;
            if (op.slot() == PaletteSlot.DOOR) hasDoor = true;
            if (op.slot() == PaletteSlot.WALL) hasWall = true;
        }
        if (StructureGeometryRules.requiresAllDoors(receipt.role()) && receipt.expectedDoors() > 0 && !hasDoor) return false;
        if (receipt.expectedRequired() > 0 && !(hasFloor && hasWall)) return false;
        return receipt.omittedRequired() == 0 || receipt.completionRatio() >= 0.98D;
    }

    private static int connectedComponentCount(Set<Long> cells) {
        Set<Long> seen = new HashSet<>();
        int components = 0;
        for (long cell : cells) {
            if (!seen.add(cell)) continue;
            components++;
            dfs(cell, cells, seen);
        }
        return components;
    }

    private static void dfs(long cell, Set<Long> cells, Set<Long> seen) {
        int x = (int) (cell >> 32);
        int z = (int) cell;
        long[] neighbors = {
                pack(x + 1, z), pack(x - 1, z), pack(x, z + 1), pack(x, z - 1)
        };
        for (long n : neighbors) {
            if (cells.contains(n) && seen.add(n)) dfs(n, cells, seen);
        }
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
