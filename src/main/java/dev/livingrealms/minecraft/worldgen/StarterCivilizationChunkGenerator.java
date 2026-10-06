package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.StructureBlueprint;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.transport.RouteProjectionPlanner;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.SettlementInitialWorldgenPlan;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Generates only the deterministic starter-fabric slice intersecting one Minecraft chunk. */
public final class StarterCivilizationChunkGenerator {
    private record PreparedIntent(
            StructureBlueprint blueprint,
            int turns,
            boolean terrainFollowing,
            int baseY
    ) {}

    private StarterCivilizationChunkGenerator() {}

    public static int generate(
            WorldgenFabricBlockWriter writer,
            StarterCivilizationFabricIndex.ChunkSlice slice,
            int chunkX,
            int chunkZ) {
        // Compute every fixed structure base before the first LR write. This keeps cross-chunk
        // pieces and neighboring intents anchored to the same pre-existing terrain rather than to
        // geometry emitted earlier in this feature invocation.
        Map<StarterCivilizationFabricIndex.SettlementFabric, PreparedIntent> prepared = new HashMap<>();
        for (StarterCivilizationFabricIndex.SettlementFabric fabric : slice.settlementFabric()) {
            ConstructionIntent intent = fabric.intent();
            if (intent.role() == StructureRole.ROAD && intent.hasPath()) continue;
            StructureBlueprint blueprint = fabric.blueprint();
            int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
            boolean terrainFollowing = terrainFollowing(intent.role());
            int cx = (int) Math.round(intent.center().x());
            int cz = (int) Math.round(intent.center().z());
            int baseY = terrainFollowing ? 0
                    : writer.siteBaseY(cx, cz, blueprint.width(), blueprint.depth(), turns);
            prepared.put(fabric, new PreparedIntent(blueprint, turns, terrainFollowing, baseY));
        }

        int writes = 0;
        for (StarterCivilizationFabricIndex.SettlementFabric fabric : slice.settlementFabric()) {
            writes += generateSettlementIntent(
                    writer, fabric.settlement(), fabric.intent(), prepared.get(fabric));
        }
        for (StarterCivilizationFabricIndex.UrbanCoreFabric urbanCore : slice.urbanCores()) {
            writes += generateUrbanCore(writer, urbanCore, chunkX, chunkZ);
        }
        for (StarterCivilizationFabricIndex.RouteFabric route : slice.routes()) {
            writes += generateRegionalRoute(writer, route.route(), chunkX, chunkZ);
        }
        return writes;
    }

    private static int generateSettlementIntent(
            WorldgenFabricBlockWriter writer,
            SettlementInitialWorldgenPlan settlement,
            ConstructionIntent intent,
            PreparedIntent prepared) {
        if (intent.role() == StructureRole.ROAD && intent.hasPath()) {
            return generatePath(writer, settlement.factionId(), intent.path(), intent.width());
        }

        PreparedIntent fixed = Objects.requireNonNull(prepared, "prepared intent");
        StructureBlueprint blueprint = fixed.blueprint();
        int turns = fixed.turns();
        int cx = (int) Math.round(intent.center().x());
        int cz = (int) Math.round(intent.center().z());
        boolean terrainFollowing = fixed.terrainFollowing();
        int baseY = fixed.baseY();

        Set<String> doors = new HashSet<>();
        Set<String> beds = new HashSet<>();
        for (BlockPlacement p : blueprint.placements()) {
            if (p.slot() == PaletteSlot.DOOR) doors.add(key(p.dx(), p.dy(), p.dz()));
            if (p.slot() == PaletteSlot.BED) beds.add(key(p.dx(), p.dy(), p.dz()));
        }

        AuthoredOwnerType ownerType = AuthoredOwnerType.forStructureRole(intent.role());
        int writes = 0;
        for (BlockPlacement placement : blueprint.placements()) {
            int[] rotated = rotate(placement.dx(), placement.dz(), turns);
            int x = cx + rotated[0], z = cz + rotated[1];
            if (!writer.insideCurrentChunk(x, z)) continue;

            int columnBase = terrainFollowing ? writer.terrainY(x, z) : baseY;
            int y = columnBase + placement.dy();
            if (!terrainFollowing && placement.slot() == PaletteSlot.FOUNDATION && placement.dy() == 0
                    && writer.terrainY(x, z) < baseY - 1) {
                if (writer.fillFoundation(settlement.factionId(), x, baseY - 1, z, ownerType)) writes++;
            }

            boolean doorUpper = placement.slot() == PaletteSlot.DOOR
                    && doors.contains(key(placement.dx(), placement.dy() - 1, placement.dz()));
            BedPart bedPart = null;
            if (placement.slot() == PaletteSlot.BED) {
                bedPart = beds.contains(key(placement.dx(), placement.dy(), placement.dz() - 1))
                        ? BedPart.HEAD : BedPart.FOOT;
            }
            if (writer.write(settlement.factionId(), placement.slot(), new BlockPos(x, y, z),
                    turns, doorUpper, bedPart, ownerType)) {
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
                    if (writeRoadDeck(writer, factionId, x, z, false, false,
                            AuthoredOwnerType.SETTLEMENT_ROAD)) writes++;
                }
            }
        }
        return writes;
    }

    private static int generateUrbanCore(
            WorldgenFabricBlockWriter writer,
            StarterCivilizationFabricIndex.UrbanCoreFabric urbanCore,
            int chunkX,
            int chunkZ) {
        SettlementInitialWorldgenPlan settlement = urbanCore.settlement();
        int cx = (int) Math.round(settlement.center().x());
        int cz = (int) Math.round(settlement.center().z());
        int radius = urbanCore.radius();
        int minX = chunkX << 4, minZ = chunkZ << 4;
        int maxX = minX + 15, maxZ = minZ + 15;
        int writes = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean onAxis = (Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= radius)
                        || (Math.abs(z - cz) <= 1 && Math.abs(x - cx) <= radius);
                if (!onAxis) continue;
                int ground = writer.terrainY(x, z);
                if (writer.waterSurfaceY(x, z) > ground) continue;
                if (!writer.clearNaturalVegetationAbove(
                        x, ground, z, 8, AuthoredOwnerType.SETTLEMENT_ROAD)) {
                    continue;
                }
                if (writer.write(
                        settlement.factionId(), PaletteSlot.PATH,
                        new BlockPos(x, ground, z), 0, false, null,
                        AuthoredOwnerType.SETTLEMENT_ROAD)) {
                    writes++;
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
                if (writeRoadDeck(writer, route.factionId(), x, z, route.rural(), true,
                        AuthoredOwnerType.INTERCITY_ROUTE)) writes++;
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
            boolean regional,
            AuthoredOwnerType ownerType) {
        int ground = writer.terrainY(x, z);
        int floor = writer.oceanFloorY(x, z);
        int waterSurface = writer.waterSurfaceY(x, z);
        boolean water = waterSurface > ground;
        int y = water ? waterSurface : ground;
        if (!writer.clearNaturalVegetationAbove(x, y, z, 8, ownerType)) return false;
        BlockPos pos = new BlockPos(x, y, z);
        if (water) {
            var deck = rural ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            boolean wrote = writer.writeState(pos, deck, false, ownerType);
            if (regional && Math.floorMod(x * 31 + z * 17, 11) == 0) {
                int bottom = Math.max(floor + 1, y - 12);
                for (int py = bottom; py < y; py++) {
                    writer.writeState(new BlockPos(x, py, z),
                            rural ? Blocks.OAK_LOG.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(),
                            false, ownerType);
                }
            }
            return wrote;
        }
        return writer.write(factionId, PaletteSlot.PATH, pos, 0, false, null, ownerType);
    }

    private static boolean terrainFollowing(StructureRole role) {
        return role == StructureRole.WALL
                || role == StructureRole.GATE
                || role == StructureRole.FARM
                || role == StructureRole.PASTURE
                || role == StructureRole.IRRIGATION;
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
