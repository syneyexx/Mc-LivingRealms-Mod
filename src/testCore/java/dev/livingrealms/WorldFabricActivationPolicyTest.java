package dev.livingrealms;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Source-contract regression for world fabric vs entity/detail LOD.
 *
 * <p>These assertions intentionally read the Minecraft adapter sources rather than loading
 * NeoForge classes into the headless core suite. They lock the architectural boundary: persistent
 * block fabric is chunk-driven, while presentation/entity scope may remain player-proximity based.</p>
 */
public final class WorldFabricActivationPolicyTest {
    private WorldFabricActivationPolicyTest() {}

    public static void main(String[] args) throws Exception {
        String settlement = read("src/main/java/dev/livingrealms/minecraft/construction/SettlementConstructionMaterializer.java");
        String transport = read("src/main/java/dev/livingrealms/minecraft/construction/TransportNetworkMaterializer.java");
        String catalog = read("src/main/java/dev/livingrealms/minecraft/runtime/LivingRealmsRuntimeTaskCatalog.java");
        String events = read("src/main/java/dev/livingrealms/minecraft/LivingRealmsEvents.java");
        String demo = read("src/main/java/dev/livingrealms/sim/world/DemoSeeder.java");

        String settlementDiscovery = between(settlement,
                "private static void discoverLoadedWork", "private static void refreshPresentationScope");
        check(!settlementDiscovery.contains("players()"),
                "settlement block discovery must not require players");
        check(!settlementDiscovery.contains("nearPlayer("),
                "settlement block discovery must not use nearPlayer");
        check(settlementDiscovery.contains("CivilizationFabricChunkQueue.pollSettlement")
                        && settlementDiscovery.contains("level.hasChunkAt(center)"),
                "settlement block discovery must consume chunk hints and refuse force loading");

        String presentation = between(settlement,
                "private static void refreshPresentationScope", "private static void creditHousingFromCompletedHouse");
        check(presentation.contains("nearPlayerForPresentation(level,settlement)"),
                "presentation/entity scope should remain proximity bounded");

        check(!transport.contains("ACTIVATION_RADIUS"),
                "regional road fabric must not use the old observer activation radius");
        check(!transport.contains("level.players()"),
                "regional road block fabric must not be player-list driven");
        check(transport.contains("CivilizationFabricChunkQueue.pollTransport")
                        && transport.contains("RouteProjectionPlanner.planInBounds")
                        && transport.contains("level.hasChunkAt"),
                "regional roads must consume loaded-chunk hints without force loading");

        check(!catalog.contains("construction.urban_core")
                        && !catalog.contains("UrbanCoreMaterializer.tick"),
                "independent urban road writer must not bypass SettlementStreetGraph authority");

        check(events.contains("onChunkLoad(ChunkEvent.Load event)")
                        && events.contains("CivilizationFabricChunkQueue.onChunkAvailable"),
                "NeoForge chunk-load handoff to shared civilization fabric queue is missing");
        check(demo.contains("StarterRegionalRouteBootstrap.ensure(s, starterLayout)")
                        || demo.contains("StarterRegionalRouteBootstrap.ensure(s)"),
                "fresh-world starter road topology must exist before chunk fabric projection");

        System.out.println("PASS world fabric activation: chunks create persistent fabric; players only activate detail LOD");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String between(String source, String start, String end) {
        int a = source.indexOf(start);
        int b = source.indexOf(end, a + Math.max(1, start.length()));
        if (a < 0 || b < 0 || b <= a) throw new AssertionError("source markers missing: " + start + " -> " + end);
        return source.substring(a, b);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
