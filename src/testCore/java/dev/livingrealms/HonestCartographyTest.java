package dev.livingrealms;

import dev.livingrealms.sim.cartography.TerrainKnowledge;
import dev.livingrealms.sim.cartography.TerrainMapSample;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Honest cartography: no fake sine-relief as terrain; ACTUAL / REGIONAL_ESTIMATE / UNKNOWN.
 */
public final class HonestCartographyTest {
    private HonestCartographyTest() {}

    public static void main(String[] args) throws Exception {
        knowledgeStatesResolveCorrectly();
        unknownIsParchmentNotHills();
        regionalLooksDistinct();
        mapSourcesHaveNoSineReliefFallback();
        System.out.println("PASS honest cartography: ACTUAL/REGIONAL_ESTIMATE/UNKNOWN, no sine-relief terrain");
    }

    private static void knowledgeStatesResolveCorrectly() {
        TerrainMapSample.ActualSample actual = new TerrainMapSample.ActualSample(0xFF2E6E45, 72, false);
        var loaded = TerrainMapSample.resolve(actual, "minecraft:plains", true);
        check(loaded.knowledge() == TerrainKnowledge.ACTUAL, "loaded sample must be ACTUAL");
        check(loaded.knowledge().allowsElevationShading(), "ACTUAL may shade elevation");

        var regional = TerrainMapSample.resolve(null, "minecraft:desert", true);
        check(regional.knowledge() == TerrainKnowledge.REGIONAL_ESTIMATE, "ecology hint must be REGIONAL_ESTIMATE");
        check(regional.knowledge().isEstimated(), "estimate flag");

        var unknown = TerrainMapSample.resolve(null, null, false);
        check(unknown.knowledge() == TerrainKnowledge.UNKNOWN, "no evidence must be UNKNOWN");
        check(!unknown.knowledge().allowsElevationShading(), "UNKNOWN must not invent elevation");
    }

    private static void unknownIsParchmentNotHills() {
        int a = TerrainMapSample.unknownFill(0, 0);
        int b = TerrainMapSample.unknownFill(16, 16);
        check(a == TerrainMapSample.UNKNOWN_PARCHMENT || a == TerrainMapSample.UNKNOWN_GRID,
                "unknown fill must be parchment/grid");
        check(b == TerrainMapSample.UNKNOWN_PARCHMENT || b == TerrainMapSample.UNKNOWN_GRID,
                "unknown hatch must stay parchment");
        var resolved = TerrainMapSample.resolve(null, "", false);
        check(resolved.color() == TerrainMapSample.UNKNOWN_PARCHMENT, "unknown resolve color is parchment");
    }

    private static void regionalLooksDistinct() {
        int desert = TerrainMapSample.regionalTint("minecraft:desert");
        int forest = TerrainMapSample.regionalTint("minecraft:forest");
        int ocean = TerrainMapSample.regionalTint("minecraft:ocean");
        check(desert != forest && forest != ocean, "regional biomes must tint differently");
        // Muted relative to a saturated actual-style green.
        check(desert != 0xFF9B7C3E, "regional desert tint must be muted vs saturated actual palette");
    }

    private static void mapSourcesHaveNoSineReliefFallback() throws Exception {
        Path root = Path.of("src/main/java/dev/livingrealms/minecraft/client/ui");
        String worldMap = Files.readString(root.resolve("RealmWorldMapScreen.java"));
        String cache = Files.readString(root.resolve("ClientTerrainMapCache.java"));
        check(!worldMap.contains("reliefNoise"), "RealmWorldMapScreen must not keep reliefNoise");
        check(!worldMap.contains("proceduralTerrain"), "RealmWorldMapScreen must not keep proceduralTerrain");
        check(!worldMap.contains("Math.sin(x*") && !worldMap.contains("Math.sin(wx"),
                "RealmWorldMapScreen must not use sine as terrain");
        check(!cache.contains("Math.sin(wx") && !cache.contains("Math.sin("),
                "ClientTerrainMapCache must not use sine as terrain fallback");
        check(worldMap.contains("TerrainKnowledge") || worldMap.contains("TerrainMapSample"),
                "RealmWorldMapScreen must use honest terrain knowledge");
        check(cache.contains("TerrainKnowledge") || cache.contains("TerrainMapSample"),
                "ClientTerrainMapCache must use honest terrain knowledge");
    }

    private static void check(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }
}
