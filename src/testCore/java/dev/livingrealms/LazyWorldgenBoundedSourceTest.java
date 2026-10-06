package dev.livingrealms;

import java.nio.file.Files;
import java.nio.file.Path;

/** Architecture gate for fresh-world startup and chunk-local worldgen bounds. */
public final class LazyWorldgenBoundedSourceTest {
    private LazyWorldgenBoundedSourceTest() {}

    public static void main(String[] args) throws Exception {
        String routes = Files.readString(Path.of(
                "src/main/java/dev/livingrealms/minecraft/worldgen/LazyStarterRegionalRouteGeometryIndex.java"));
        String settlements = Files.readString(Path.of(
                "src/main/java/dev/livingrealms/minecraft/worldgen/LazyStarterCivilizationFabricIndex.java"));
        String savedData = Files.readString(Path.of(
                "src/main/java/dev/livingrealms/minecraft/LivingRealmsSavedData.java"));

        check(routes.contains("TerrainCorridorPlanner.planLocal"),
                "lazy regional worldgen must use the bounded local corridor planner");
        check(!routes.contains("StarterRegionalRouteGeometryIndex.planOne"),
                "lazy regional worldgen must never solve a full intercity route from one chunk");
        check(routes.contains("TILE_BLOCKS"),
                "lazy regional worldgen requires deterministic fixed planning tiles");

        check(settlements.contains("ConstructionIntentChunkSelector.intersects"),
                "settlement worldgen must select intents before blueprint creation");
        check(settlements.contains("blueprintCache.computeIfAbsent"),
                "multi-chunk settlement slices must reuse lazily-created blueprints");

        String create = between(savedData,
                "public static LivingRealmsSavedData create(long worldSeed, Map<String, SpeciesDefinition> speciesCatalog)",
                "public static LivingRealmsSavedData load(");
        check(!create.contains("StarterWorldgenCompletion.adoptPlannedBaseline(state)"),
                "fresh-save startup must not derive every surface physical plan for receipts");
        check(create.contains("StarterWorldgenCompletion.adoptWizardTreesBaseline(state)"),
                "small Wizard Trees starter layer should retain eager receipt adoption");

        System.out.println("PASS lazy worldgen bound: no global surface planning on startup/chunk route path");
    }

    private static String between(String source, String start, String end) {
        int a = source.indexOf(start);
        int b = source.indexOf(end, a + Math.max(1, start.length()));
        if (a < 0 || b < 0 || b <= a) throw new AssertionError("source markers missing");
        return source.substring(a, b);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
