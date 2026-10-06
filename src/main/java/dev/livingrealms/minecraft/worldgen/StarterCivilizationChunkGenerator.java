package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.StructureBlueprint;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.transport.RouteProjectionPlanner;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.SettlementInitialWorldgenPlan;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Generates only the deterministic starter-fabric slice intersecting one Minecraft chunk. */
public final class StarterCivilizationChunkGenerator {
    private StarterCivilizationChunkGenerator() {}

    public static int generate(
            WorldgenFabricBlockWriter writer,
            StarterCivilizationFabricIndex.ChunkSlice slice,
            int chunkX,
            int chunkZ) {
        int writes = 0;
        for (StarterCivilizationFabricIndex.SettlementFabric fabric : slice.settlementFabric()) {
            writes += generateSettlementIntent(writer, fabric.settlement(), fabric.intent());
        }
        for (StarterCivilizationFabricIndex.RouteFabric route : slice.routes()) {
            writes += generateRegionalRoute(writer, route.route(), chunkX, chunkZ);
        }
        return writes;
    }

    private static int generateSettlementIntent(
            WorldgenFabricBlockWriter writer,
            SettlementInitialWorldgenPlan settlement,
            ConstructionIntent intent) {
        if (intent.role() == StructureRole.ROAD && intent.hasPath()) {
            return generatePath(writer, settlement.factionId(), intent.path(), intent.width());
        }

        StructureBlueprint blueprint = StructureBlueprintFactory.create(intent, settlement.architecture());
        int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
        int cx = (int) Math.round(intent.center().x());
        int cz = (int) Math.round(intent.center().z());
        boolean terrainFollowing = intent.role() == StructureRole.WALL
                || intent.role() == StructureRole.GATE
                || intent.role() == StructureRole.FARM
                || intent.role() == StructureRole.PASTURE
                || intent.role() == StructureRole.IRRIGATION;
        int baseY = terrainFollowing ? 0 : writer.siteBaseY(cx, cz, blueprint.width(), blueprint.depth(), turns);

        Set<String> doors = new HashSet<>();
        Set<String> beds = new HashSet<>();
        for (BlockPlacement p : blueprint.placements()) {
            if (p.slot() == PaletteSlot.DOOR) doors.add(key(p.dx(), p.dy(), p.dz()));
            if (p.slot() == PaletteSlot.BED) beds.add(key(p.dx(), p.dy(), p.dz()));
        }

        int writes = 0;
        for (BlockPlacement placement : blueprint.placements()) {
            int[] rotated = rotate(placement.dx(), placement.dz(), turns);
            int x = cx + rotated[0], z = cz + rotated[1];
            if (!writer.insideCurrentChunk(x, z)) continue;

            int columnBase = terrainFollowing ? writer.terrainY(x, z) : baseY;
            int y = columnBase + placement.dy();
            if (!terrainFollowing && placement.slot() == PaletteSlot.FOUNDATION && placement.dy() == 0
                    && writer.terrainY(x, z) < baseY - 1) {
                if (writer.fillFoundation(settlement.factionId(), x, baseY - 1, z)) writes++;
            }

            boolean doorUpper = placement.slot() == PaletteSlot.DOOR
                    && doors.contains(key(placement.dx(), placement.dy() - 1, placement.dz()));
            BedPart bedPart = null;
            if (placement.slot() == PaletteSlot.BED) {
                bedPart = beds.contains(key(placement.dx(), placement.dy(), placement.dz() - 1))
                        ? BedPart.HEAD : BedPart.FOOT;
            }
            if (writer.write(settlement.factionId(), placement.slot(), new BlockPos(x, y, z),
                    turns, doorUpper, bedPart)) {
                writes++;
            }
        }
        return writes;
    }

    private static int generatePath(
            WorldgenFabricBlockWriter writer,
            long factionId,
            List<SimPosition> path,
            int width) {
        if (path.size() < 2) return 0;
        int writes = 0;
        Set<Long> visited = new HashSet<>();
        for (int s = 0; s < path.size() - 1; s++) {
            SimPosition a = path.get(s), b = path.get(s + 1);
            double dx = b.x() - a.x(), dz = b.z() - a.z();
            double distance = Math.hypot(dx, dz);
            int steps = Math.max(1, (int) Math.ceil(distance));
            double nx = distance < 1.0e-9 ? 1 : -dz / distance;
            double nz = distance < 1.0e-9 ? 0 : dx / distance;
            int half = Math.max(0, width / 2);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double centerX = a.x() + dx * t, centerZ = a.z() + dz * t;
                for (int side = -half; side <= half; side++) {
                    int x = (int) Math.round(centerX + nx * side);
                    int z = (int) Math.round(centerZ + nz * side);
                    long packed = (((long) x) << 32) ^ (z & 0xffffffffL);
                    if (!visited.add(packed) || !writer.insideCurrentChunk(x, z)) continue;
                    if (writeRoadDeck(writer, factionId, x, z, false, false)) writes++;
                }
            }
        }
        return writes;
    }

    private static int generateRegionalRoute(
            WorldgenFabricBlockWriter writer,
            StarterRegionalRoutePlanner.RoutePlan route,
            int chunkX,
            int chunkZ) {
        int minX = chunkX << 4, minZ = chunkZ << 4;
        int halo = route.rural() ? 1 : 3;
        var points = RouteProjectionPlanner.planInBounds(
                route.asTransportRoute(), route.from(), route.to(),
                minX - halo, minZ - halo, minX + 15 + halo, minZ + 15 + halo, 4096);
        int writes = 0;
        Set<Long> visited = new HashSet<>();
        for (RouteProjectionPlanner.RoutePoint point : points) {
            int px = point.x(), pz = point.z();
            int nx = point.dz() == 0 ? 0 : Integer.signum(point.dz());
            int nz = point.dx() == 0 ? 0 : -Integer.signum(point.dx());
            if (nx == 0 && nz == 0) nx = 1;
            int half = route.rural() ? 0 : 2;
            for (int side = -half; side <= half; side++) {
                int x = px + nx * side, z = pz + nz * side;
                long packed = (((long) x) << 32) ^ (z & 0xffffffffL);
                if (!visited.add(packed) || !writer.insideCurrentChunk(x, z)) continue;
                if (writeRoadDeck(writer, route.factionId(), x, z, route.rural(), true)) writes++;
            }
        }
        return writes;
    }

    private static boolean writeRoadDeck(
            WorldgenFabricBlockWriter writer,
            long factionId,
            int x,
            int z,
            boolean rural,
            boolean regional) {
        int surface = writer.terrainY(x, z);
        int floor = writer.oceanFloorY(x, z);
        boolean water = surface - floor >= 2;
        int y = surface;
        BlockPos pos = new BlockPos(x, y, z);
        if (water) {
            var deck = rural ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            boolean wrote = writer.writeState(pos, deck, false);
            if (regional && Math.floorMod(x * 31 + z * 17, 11) == 0) {
                int bottom = Math.max(floor + 1, y - 12);
                for (int py = bottom; py < y; py++) {
                    writer.writeState(new BlockPos(x, py, z),
                            rural ? Blocks.OAK_LOG.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(), false);
                }
            }
            return wrote;
        }
        return writer.write(factionId, PaletteSlot.PATH, pos, 0, false, null);
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
