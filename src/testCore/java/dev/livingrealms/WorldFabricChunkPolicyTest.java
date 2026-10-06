package dev.livingrealms;

import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.ConstructionIntentChunkSelector;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.transport.RouteProjectionPlanner;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimPosition;
import java.nio.file.Files;
import java.nio.file.Path;

/** Architecture gate: core block fabric is loaded-chunk driven, not observer-radius driven. */
public final class WorldFabricChunkPolicyTest {
    private WorldFabricChunkPolicyTest() {}

    public static void main(String[] args) throws Exception {
        intentWindowSelection();
        routeWindowProjectionNeedsNoObserver();
        sourceArchitecturePins();
        System.out.println("PASS world-fabric chunk policy: bounded chunk handoff + observer-independent settlement/road fabric");
    }

    private static void intentWindowSelection() {
        ConstructionIntent house = new ConstructionIntent("house:0", 1, 2, StructureRole.HOUSE,
                new SimPosition(15, 15), 9, 7, 0, 100);
        check(ConstructionIntentChunkSelector.intersects(house, 0, 0), "house must touch origin chunk");
        check(ConstructionIntentChunkSelector.intersects(house, 1, 0), "access margin must cover adjacent east chunk");
        check(!ConstructionIntentChunkSelector.intersects(house, 4, 4), "distant chunk must not match");

        ConstructionIntent road = new ConstructionIntent("road:0", 1, 2, StructureRole.ROAD,
                new SimPosition(32, 32), 5, 80, 1, 100);
        check(ConstructionIntentChunkSelector.intersects(road, 0, 2), "rotated long road west extent");
        check(ConstructionIntentChunkSelector.intersects(road, 4, 2), "rotated long road east extent");
    }

    private static void routeWindowProjectionNeedsNoObserver() {
        TransportRoute route = new TransportRoute(9, 1, 2, 3, TransportMode.ROAD,
                1000, .8, .8, 300);
        var points = RouteProjectionPlanner.planInBounds(route,
                new SimPosition(0, 8), new SimPosition(1000, 8),
                496, 0, 511, 15, 128);
        check(!points.isEmpty(), "loaded chunk on route must receive physical route points without observers");
        check(points.stream().allMatch(p -> p.x() >= 496 && p.x() <= 511 && p.z() >= 0 && p.z() <= 15),
                "route window emitted points outside chunk");
    }

    private static void sourceArchitecturePins() throws Exception {
        String settlement = Files.readString(Path.of(
                "src/main/java/dev/livingrealms/minecraft/construction/SettlementConstructionMaterializer.java"));
        check(!settlement.contains("ACTIVATION_RADIUS=640"), "legacy 640 construction activation radius returned");
        check(!settlement.contains("if(!nearPlayer(level,settlement))"), "core discovery still requires nearPlayer");
        check(settlement.contains("CivilizationFabricChunkQueue.pollSettlement"), "settlement materializer lacks chunk handoff");
        check(settlement.contains("PRESENTATION_RADIUS=640.0D"), "entity/presentation LOD separation disappeared");
        check(settlement.contains("nearPlayerForPresentation"), "presentation proximity must remain explicit and separate");

        String transport = Files.readString(Path.of(
                "src/main/java/dev/livingrealms/minecraft/construction/TransportNetworkMaterializer.java"));
        check(!transport.contains("ACTIVATION_RADIUS"), "transport fabric still has observer activation radius");
        check(!transport.contains("level.players().isEmpty()"), "transport construction still depends on players");
        check(transport.contains("CivilizationFabricChunkQueue.pollTransport"), "transport lacks chunk handoff");
        check(transport.contains("RouteProjectionPlanner.planInBounds"), "transport lacks chunk-local route projection");

        String urban = Files.readString(Path.of(
                "src/main/java/dev/livingrealms/minecraft/construction/UrbanCoreMaterializer.java"));
        check(!urban.contains("nearPlayer("), "urban core still proximity-gated");
        check(!urban.contains("ACTIVATION"), "urban core still has activation radius");
        check(urban.contains("level.hasChunkAt"), "urban core must refuse force-loading");

        String events = Files.readString(Path.of(
                "src/main/java/dev/livingrealms/minecraft/LivingRealmsEvents.java"));
        check(events.contains("onChunkLoad(ChunkEvent.Load event)"), "NeoForge chunk-load handoff missing");
        check(events.contains("CivilizationFabricChunkQueue.onChunkAvailable"), "chunk event does not queue fabric");

        String queue = Files.readString(Path.of(
                "src/main/java/dev/livingrealms/minecraft/construction/CivilizationFabricChunkQueue.java"));
        check(queue.contains("MAX_PENDING_PER_DOMAIN = 8192"), "chunk handoff must stay bounded");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
