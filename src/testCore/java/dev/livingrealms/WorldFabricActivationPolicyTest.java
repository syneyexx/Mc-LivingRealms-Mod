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
        String urban = read("src/main/java/dev/livingrealms/minecraft/construction/UrbanCoreMaterializer.java");
        String events = read("src/main/java/dev/livingrealms/minecraft/LivingRealmsEvents.java");
        String demo = read("src/main/java/dev/livingrealms/sim/world/DemoSeeder.java");

        String settlementDiscovery = between(settlement,
                "private static void discoverLoadedWork", "private static void refreshPresentationScope");
        check(!settlementDiscovery.contains("players()"),
                "settlement block discovery must not require players");
        check(!settlementDiscovery.contains("nearPlayer("),
                "settlement block discovery must not use nearPlayer");
        check(settlementDiscovery.contains("level.hasChunkAt(core)")
                        && settlementDiscovery.contains("level.hasChunkAt(center)"),
                "settlement block discovery must be loaded-chunk bounded");

        String presentation = between(settlement,
                "private static void refreshPresentationScope", "private static void creditHousingFromCompletedHouse");
        check(presentation.contains("nearPlayerForPresentation(level,settlement)"),
                "presentation/entity scope should remain proximity bounded");

        check(!transport.contains("ACTIVATION_RADIUS"),
                "regional road fabric must not use the old observer activation radius");
        check(!transport.contains("level.players()"),
                "regional road block fabric must not be player-list driven");
        check(transport.contains("onChunkLoaded(ChunkPos chunkPos)")
                        && transport.contains("PENDING_CHUNKS")
                        && transport.contains("level.hasChunkAt(probe)"),
                "regional roads must be queued from loaded chunks without force loading");

        check(!urban.contains("nearPlayer(") && !urban.contains("level.players()"),
                "urban core block fabric must not be player-proximity driven");
        check(urban.contains("level.hasChunkAt(core)") && urban.contains("level.hasChunkAt(probe)"),
                "urban core work must only touch already-loaded chunks");

        check(events.contains("ChunkEvent.Load")
                        && events.contains("TransportNetworkMaterializer.onChunkLoaded(event.getChunk().getPos())"),
                "NeoForge chunk-load wiring for road fabric is missing");
        check(demo.contains("new TransportNetworkEngine().ensureRoutes(s)"),
                "fresh-world road topology must exist before chunk fabric projection");

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
