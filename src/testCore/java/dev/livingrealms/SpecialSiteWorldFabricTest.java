package dev.livingrealms;

import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.ForeignAdoptionClassifier;
import dev.livingrealms.sim.world.OutlyingSite;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SpecialSiteWorldFabricTest {
    private SpecialSiteWorldFabricTest() {}

    public static void main(String[] args) throws Exception {
        outlyingSitesRemainNonSettlements();
        sourcePins();
        System.out.println("PASS special-site world fabric: outlying + roadside + historical + industry are chunk-driven");
    }

    private static void outlyingSitesRemainNonSettlements() {
        SimulationState state = new SimulationState(0x51E51E51L);
        Faction faction = new Faction(state.nextId(), "Site Realm", "Warden");
        Settlement host = new Settlement(state.nextId(), "Host", new SimPosition(0, 0), 900, 1000,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.TOWN);
        faction.addSettlement(host);
        state.addFaction(faction);

        var result = ForeignAdoptionClassifier.classifyAndAdopt(
                state, new SimPosition(550, 0), "Foreign Cluster", 700, 760, OutlyingSite.Type.FOREIGN_HAMLET);
        check(result.outcome() == ForeignAdoptionClassifier.Outcome.OUTLYING_SITE,
                "spacing-blocked foreign site must become outlying site");
        check(result.site() != null && state.findOutlyingSite(result.site().id()).isPresent(),
                "outlying site must be canonical");
        check(state.findSettlement(result.site().id()).isEmpty(),
                "outlying site must never become a canonical settlement id");
    }

    private static void sourcePins() throws Exception {
        String roadside = read("src/main/java/dev/livingrealms/minecraft/construction/RoadsideSiteMaterializer.java");
        check(!roadside.contains("level.players().isEmpty()"), "roadside blocks still require players");
        check(!roadside.contains("RoadsideSiteMaterializationPlanner planner"),
                "roadside block authority still uses player LOD planner");
        check(roadside.contains("level.hasChunkAt"), "roadside fabric must refuse force loading");
        check(roadside.contains("cursor"), "roadside fabric needs round-robin fairness");

        String historical = read("src/main/java/dev/livingrealms/minecraft/construction/HistoricalSiteMaterializer.java");
        check(!historical.contains("ACTIVATION_RADIUS"), "historical fabric still has activation radius");
        check(!historical.contains("nearPlayer("), "historical blocks still depend on player proximity");
        check(historical.contains("loadedAt("), "historical fabric must require loaded chunks");
        check(historical.contains("cacheCursor") && historical.contains("hideoutCursor")
                        && historical.contains("ruinCursor") && historical.contains("legendCursor"),
                "historical categories need independent fairness cursors");

        String industrial = read("src/main/java/dev/livingrealms/minecraft/construction/IndustrialSiteMaterializer.java");
        check(!industrial.contains("if(level.players().isEmpty())"), "industrial blocks still require players");
        check(industrial.contains("presentationNear=nearPlayer"),
                "industrial particle LOD separation missing");
        check(industrial.contains("level.hasChunkAt"), "industrial fabric must refuse force loading");

        String outlying = read("src/main/java/dev/livingrealms/minecraft/construction/OutlyingSiteMaterializer.java");
        check(!outlying.contains("players()"), "outlying blocks must not use player proximity");
        check(outlying.contains("AuthoredOwnerType.OUTLYING_SITE"),
                "outlying fabric needs typed provenance");
        check(outlying.contains("level.hasChunkAt"), "outlying fabric must refuse force loading");

        String catalog = read("src/main/java/dev/livingrealms/minecraft/runtime/LivingRealmsRuntimeTaskCatalog.java");
        check(catalog.contains("construction.outlying_site")
                        && catalog.contains("OutlyingSiteMaterializer.tick"),
                "outlying fabric is not scheduler-wired");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
