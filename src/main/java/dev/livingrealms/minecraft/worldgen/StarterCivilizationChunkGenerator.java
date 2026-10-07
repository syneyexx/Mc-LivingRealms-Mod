package dev.livingrealms.minecraft.worldgen;

import dev.livingrealms.minecraft.construction.RoadsideSiteTemplate;
import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.StructureBlueprint;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.transport.RouteProjectionPlanner;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.worldgen.BridgeCrossingPlanner;
import dev.livingrealms.sim.worldgen.RoadSurfaceMask;
import dev.livingrealms.sim.worldgen.SettlementInitialWorldgenPlan;
import dev.livingrealms.sim.worldgen.StarterCivilizationFabricIndex;
import dev.livingrealms.sim.worldgen.StarterRegionalRoutePlanner;
import dev.livingrealms.sim.worldgen.WorldgenObjectAcceptance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
        return generate(
                writer, slice, StarterRegionalRouteGeometryIndex.ChunkSlice.EMPTY, chunkX, chunkZ);
    }

    public static int generate(
            WorldgenFabricBlockWriter writer,
            StarterCivilizationFabricIndex.ChunkSlice slice,
            StarterRegionalRouteGeometryIndex.ChunkSlice routeSlice,
            int chunkX,
            int chunkZ) {
        // Prefer the globally accepted object decision computed once during settlement resolution.
        // Falling back to a local generator sample only happens for legacy/null-acceptance fabric
        // (terrain-following roles skip fixed-base preparation entirely).
        Map<StarterCivilizationFabricIndex.SettlementFabric, PreparedIntent> prepared = new HashMap<>();
        for (StarterCivilizationFabricIndex.SettlementFabric fabric : slice.settlementFabric()) {
            ConstructionIntent intent = fabric.intent();
            if (intent.role() == StructureRole.ROAD && intent.hasPath()) continue;
            StructureBlueprint blueprint = fabric.blueprint();
            int turns = Math.floorMod(intent.rotationQuarterTurns(), 4);
            boolean terrainFollowing = terrainFollowing(intent.role());
            WorldgenObjectAcceptance.Decision acceptance = fabric.acceptance();
            if (!terrainFollowing && acceptance != null && !acceptance.accepted()) {
                // Globally rejected objects emit nothing in any intersecting chunk.
                continue;
            }
            int baseY;
            if (terrainFollowing) {
                baseY = 0;
            } else if (acceptance != null) {
                baseY = acceptance.baseY();
            } else {
                int cx = (int) Math.round(intent.center().x());
                int cz = (int) Math.round(intent.center().z());
                baseY = writer.siteBaseY(cx, cz, blueprint.width(), blueprint.depth(), turns);
            }
            prepared.put(fabric, new PreparedIntent(blueprint, turns, terrainFollowing, baseY));
        }

        int writes = 0;
        for (StarterCivilizationFabricIndex.SettlementFabric fabric : slice.settlementFabric()) {
            writes += generateSettlementIntent(
                    writer, fabric.settlement().factionId(), fabric.intent(), prepared.get(fabric));
        }
        for (StarterCivilizationFabricIndex.UrbanCoreFabric urbanCore : slice.urbanCores()) {
            writes += generateUrbanCore(writer, urbanCore, chunkX, chunkZ);
        }
        for (StarterRegionalRouteGeometryIndex.RouteSlice route : routeSlice.routes()) {
            writes += generateRegionalRoute(writer, route, chunkX, chunkZ);
        }
        // Starter roadside anchors are part of day-zero fabric. Generate them after roads so cells
        // crossing water can require and reuse the bridge deck authored earlier in this slice.
        for (StarterCivilizationFabricIndex.RoadsideFabric roadside : slice.roadsideSites()) {
            writes += generateRoadsideSite(writer, roadside.site());
        }
        return writes;
    }

    private static int generateSettlementIntent(
            WorldgenFabricBlockWriter writer,
            long factionId,
            ConstructionIntent intent,
            PreparedIntent prepared) {
        if (intent.role() == StructureRole.ROAD && intent.hasPath()) {
            return generatePath(writer, factionId, intent.path(), intent.width());
        }

        if (prepared == null) return 0; // globally rejected fixed structure
        PreparedIntent fixed = prepared;
        StructureBlueprint blueprint = fixed.blueprint();
        int turns = fixed.turns();
        int cx = (int) Math.round(intent.center().x());
        int cz = (int) Math.round(intent.center().z());
        boolean terrainFollowing = fixed.terrainFollowing();
        int baseY = fixed.baseY();

        // Global acceptance already decided terrain viability. Keep the local generator check as a
        // defense-in-depth assert that must agree with the cached decision.
        if (!terrainCompatibleFixedSite(writer, fixed, cx, cz)) return 0;
        if (!canAuthorIntentSlice(writer, intent, fixed, cx, cz)) return 0;

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
                if (writer.fillFoundation(factionId, x, baseY - 1, z, ownerType)) writes++;
            }

            boolean doorUpper = placement.slot() == PaletteSlot.DOOR
                    && doors.contains(key(placement.dx(), placement.dy() - 1, placement.dz()));
            BedPart bedPart = null;
            if (placement.slot() == PaletteSlot.BED) {
                bedPart = beds.contains(key(placement.dx(), placement.dy(), placement.dz() - 1))
                        ? BedPart.HEAD : BedPart.FOOT;
            }
            if (writer.write(factionId, placement.slot(), new BlockPos(x, y, z),
                    turns, doorUpper, bedPart, ownerType)) {
                writes++;
            }
        }
        return writes;
    }



    /**
     * Global terrain viability for fixed structures. Uses generator-only ground samples, so every
     * intersecting chunk reaches the same decision without reading/generated-neighbor dependency.
     */
    private static boolean terrainCompatibleFixedSite(
            WorldgenFabricBlockWriter writer,
            PreparedIntent prepared,
            int cx,
            int cz) {
        if (prepared.terrainFollowing()) return true;
        boolean sawFoundation = false;
        for (BlockPlacement placement : prepared.blueprint().placements()) {
            if (placement.slot() != PaletteSlot.FOUNDATION || placement.dy() != 0) continue;
            sawFoundation = true;
            int[] rotated = rotate(placement.dx(), placement.dz(), prepared.turns());
            int x = cx + rotated[0], z = cz + rotated[1];
            int ground = writer.generatorGroundY(x, z);
            int delta = prepared.baseY() - ground;
            if (delta > 64 || delta < -10) return false;
        }
        return !sawFoundation || prepared.baseY() > writer.level().getMinBuildHeight() + 1;
    }

    /**
     * Fail the whole current-chunk slice before the first write when any required cell is protected.
     * This covers registered structure pieces, block entities, foreign LR owner classes and unknown
     * non-natural solids without mutating or loading neighboring chunks.
     */
    private static boolean canAuthorIntentSlice(
            WorldgenFabricBlockWriter writer,
            ConstructionIntent intent,
            PreparedIntent prepared,
            int cx,
            int cz) {
        // Global generator-only terrain compatibility above is the deterministic all-chunk
        // acceptance decision. The writer still protects foreign blocks one cell at a time, but a
        // ravine/carver or one protected decoration in this particular chunk must not make only
        // this slice of a multi-chunk building disappear.
        return true;
    }

    private static int generatePath(
            WorldgenFabricBlockWriter writer,
            long factionId,
            List<SimPosition> path,
            int width) {
        if (path.size() < 2) return 0;
        int writes = 0;
        Set<Long> visited = new HashSet<>();
        // Supercover centerline + integer dilation: continuous road surface without diagonal holes.
        for (RoadSurfaceMask.Cell cell : RoadSurfaceMask.rasterize(path, Math.max(1, width))) {
            if (!visited.add(cell.packed()) || !writer.insideCurrentChunk(cell.x(), cell.z())) continue;
            if (writeRoadDeck(
                    writer, factionId, cell.x(), cell.z(),
                    false, false, Integer.MIN_VALUE,
                    AuthoredOwnerType.SETTLEMENT_ROAD)) {
                writes++;
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
                BlockPos groundPos = new BlockPos(x, ground, z);
                if (!writer.canReplaceForWorldgen(
                        groundPos, false, AuthoredOwnerType.SETTLEMENT_ROAD)) {
                    continue;
                }
                if (!writer.clearNaturalVegetationAbove(
                        x, ground, z, 8, AuthoredOwnerType.SETTLEMENT_ROAD)) {
                    continue;
                }
                if (writer.write(
                        settlement.factionId(), PaletteSlot.PATH,
                        groundPos, 0, false, null,
                        AuthoredOwnerType.SETTLEMENT_ROAD)) {
                    writes++;
                }
            }
        }
        return writes;
    }


    private static int generateRoadsideSite(
            WorldgenFabricBlockWriter writer,
            dev.livingrealms.sim.worldgen.StarterRoadsideSitePlanner.SitePlan plan) {
        var site = plan.asRoadsideSite();
        int cx = (int) Math.floor(site.position().x());
        int cz = (int) Math.floor(site.position().z());
        int writes = 0;
        for (RoadsideSiteTemplate.Placement placement : RoadsideSiteTemplate.placements(site)) {
            int x = cx + placement.dx();
            int z = cz + placement.dz();
            if (!writer.insideCurrentChunk(x, z)) continue;

            int ground = writer.terrainY(x, z);
            int waterSurface = writer.waterSurfaceY(x, z);
            boolean waterColumn = waterSurface > ground;
            int supportY = waterColumn ? waterSurface : ground;
            BlockPos support = new BlockPos(x, supportY, z);
            if (!writer.canSupportRoadside(support, waterColumn)) continue;

            BlockState target = placement.state();
            if (writer.writeState(
                    support.above(), target, false, AuthoredOwnerType.ROADSIDE_SITE)) {
                writes++;
            }
        }
        return writes;
    }

    private static int generateRegionalRoute(
            WorldgenFabricBlockWriter writer,
            StarterRegionalRouteGeometryIndex.RouteSlice routeSlice,
            int chunkX,
            int chunkZ) {
        StarterRegionalRoutePlanner.RoutePlan route = routeSlice.route();
        List<StarterRegionalRouteGeometryIndex.PlannedPoint> points = routeSlice.points();
        if (points.isEmpty()) return 0;

        List<BridgeCrossingPlanner.Sample> samples = new ArrayList<>(points.size());
        List<RoadSurfaceMask.Cell> centers = new ArrayList<>(points.size());
        for (StarterRegionalRouteGeometryIndex.PlannedPoint point : points) {
            int ground = writer.generatorGroundY(point.x(), point.z());
            samples.add(new BridgeCrossingPlanner.Sample(
                    point.x(), point.z(), point.deckY(), point.water(), ground));
            centers.add(new RoadSurfaceMask.Cell(point.x(), point.z()));
        }
        List<BridgeCrossingPlanner.Crossing> crossings = BridgeCrossingPlanner.plan(samples);

        int width = route.rural() ? 3 : 5;
        int writes = 0;
        Set<Long> visited = new HashSet<>();
        List<RoadSurfaceMask.Cell> surface = RoadSurfaceMask.rasterizeCenters(centers, width);

        for (RoadSurfaceMask.Cell cell : surface) {
            if (!visited.add(cell.packed()) || !writer.insideCurrentChunk(cell.x(), cell.z())) {
                continue;
            }
            StarterRegionalRouteGeometryIndex.PlannedPoint nearest =
                    nearestPlannedPoint(points, cell.x(), cell.z());
            if (nearest == null) continue;

            int sampleIndex = indexOfPoint(points, nearest);
            BridgeCrossingPlanner.Crossing crossing =
                    BridgeCrossingPlanner.findCovering(crossings, sampleIndex);
            if (crossing != null && !crossing.accepted()) {
                // Rejected span: do not emit sparse pillars. Leave the column natural.
                continue;
            }

            int deckY = crossing != null ? crossing.deckY() : nearest.deckY();
            boolean engineeredDeck = crossing != null
                    || nearest.water()
                    || deckY > writer.terrainY(cell.x(), cell.z()) + 3;
            writes += writeRoadDeck(
                    writer, route.factionId(), cell.x(), cell.z(),
                    route.rural(), true, deckY,
                    AuthoredOwnerType.INTERCITY_ROUTE,
                    engineeredDeck ? crossing : null) ? 1 : 0;
        }
        return writes;
    }

    private static StarterRegionalRouteGeometryIndex.PlannedPoint nearestPlannedPoint(
            List<StarterRegionalRouteGeometryIndex.PlannedPoint> points, int x, int z) {
        StarterRegionalRouteGeometryIndex.PlannedPoint best = null;
        long bestDist = Long.MAX_VALUE;
        for (StarterRegionalRouteGeometryIndex.PlannedPoint point : points) {
            long dx = point.x() - x;
            long dz = point.z() - z;
            long d2 = dx * dx + dz * dz;
            if (d2 < bestDist) {
                bestDist = d2;
                best = point;
            }
        }
        return best;
    }

    private static int indexOfPoint(
            List<StarterRegionalRouteGeometryIndex.PlannedPoint> points,
            StarterRegionalRouteGeometryIndex.PlannedPoint target) {
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            if (point.x() == target.x() && point.z() == target.z()) return i;
        }
        return -1;
    }

    private static boolean writeRoadDeck(
            WorldgenFabricBlockWriter writer,
            long factionId,
            int x,
            int z,
            boolean rural,
            boolean regional,
            int plannedDeckY,
            AuthoredOwnerType ownerType) {
        return writeRoadDeck(writer, factionId, x, z, rural, regional, plannedDeckY, ownerType, null);
    }

    private static boolean writeRoadDeck(
            WorldgenFabricBlockWriter writer,
            long factionId,
            int x,
            int z,
            boolean rural,
            boolean regional,
            int plannedDeckY,
            AuthoredOwnerType ownerType,
            BridgeCrossingPlanner.Crossing crossing) {
        int ground = writer.terrainY(x, z);
        int floor = writer.oceanFloorY(x, z);
        int waterSurface = writer.waterSurfaceY(x, z);
        boolean water = waterSurface > ground;
        int naturalSurface = water ? waterSurface : ground;
        int y = regional ? plannedDeckY : naturalSurface;
        if (water) y = Math.max(waterSurface, y);
        BlockPos pos = new BlockPos(x, y, z);

        if (water || (crossing != null && crossing.accepted())) {
            if (!writer.canReplaceForWorldgen(pos, false, ownerType)) return false;
            if (!writer.clearNaturalVegetationAbove(x, y, z, 8, ownerType)) return false;
            var deck = rural ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            boolean wrote = writer.writeState(pos, deck, false, ownerType);
            if (regional && wrote) {
                writeBridgeSupports(writer, x, z, y, floor, rural, ownerType, crossing);
            }
            return wrote;
        }

        if (!writer.prepareDryRoadColumn(
                factionId, x, z, ground, y, ownerType)) return false;
        if (!writer.canReplaceForWorldgen(pos, false, ownerType)) return false;
        if (y > ground + 3) {
            BlockState deck = rural
                    ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState();
            return writer.writeState(pos, deck, false, ownerType);
        }
        return writer.write(factionId, PaletteSlot.PATH, pos, 0, false, null, ownerType);
    }

    /**
     * Continuous bridge deck supports: abutments are solid, mid-span piers follow the planned
     * spacing. Never emits the old sparse 1-in-11 pillar skeleton without a deck object.
     */
    private static void writeBridgeSupports(
            WorldgenFabricBlockWriter writer,
            int x,
            int z,
            int deckY,
            int floorY,
            boolean rural,
            AuthoredOwnerType ownerType,
            BridgeCrossingPlanner.Crossing crossing) {
        int spacing = crossing == null ? 6 : crossing.supportSpacing();
        boolean abutment = crossing != null
                && ((x == crossing.abutmentFromX() && z == crossing.abutmentFromZ())
                || (x == crossing.abutmentToX() && z == crossing.abutmentToZ()));
        boolean pier = abutment || Math.floorMod(x * 31 + z * 17, spacing) == 0;
        if (!pier) return;

        int bottom = Math.max(floorY + 1, deckY - 48);
        BlockState pierState = rural
                ? Blocks.OAK_LOG.defaultBlockState()
                : Blocks.STONE_BRICKS.defaultBlockState();
        for (int py = bottom; py < deckY; py++) {
            writer.writeState(new BlockPos(x, py, z), pierState, false, ownerType);
        }
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
