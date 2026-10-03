package dev.livingrealms;

import dev.livingrealms.sim.biome.BiomeObservation;
import dev.livingrealms.sim.biome.BiomeSignalNormalizer;
import dev.livingrealms.sim.config.SimulationConfig;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.TickRateLimiter;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.nio.ByteBuffer;
import java.util.List;

/** Regression checks for production-only failure modes that are easy to miss in feature tests. */
public final class ProductionHardeningTest {
    private ProductionHardeningTest() {}

    public static void main(String[] args) {
        rateLimiterSurvivesIntegratedServerRestart();
        configurationRejectsNonFiniteAndRunawayValues();
        biomeBoundaryNormalizesModdedClimateValues();
        positionsRejectNonFiniteAndOutOfWorldValues();
        canonicalAdditionsAdvanceIdWatermark();
        currentSchemaRepairsStaleIdWatermark();
        encoderRejectsInvalidCanonicalReferences();
        System.out.println("PASS production hardening: tick-reset limiter + modded-biome climate normalization + bounded config/positions + canonical ID/save validation");
    }

    private static void biomeBoundaryNormalizesModdedClimateValues() {
        BiomeObservation nan = BiomeSignalNormalizer.observation("modded:nan_climate", List.of("modded:forest"), Double.NaN, Double.NaN);
        check(Double.isFinite(nan.temperature()) && Double.isFinite(nan.humidity()), "non-finite modded climate escaped normalizer");
        check(nan.temperature() == .8D && nan.humidity() == .4D, "non-finite modded climate did not use neutral fallback");

        BiomeObservation high = BiomeSignalNormalizer.observation("modded:wet", List.of(), 1.25D, 99D);
        check(high.humidity() == 1.5D && high.has("humid") && high.has("warm"), "runaway modded humidity was not clamped before classification");

        BiomeObservation low = BiomeSignalNormalizer.observation("modded:dry", List.of(), -.25D, -9D);
        check(low.humidity() == 0D && low.has("dry") && low.has("frozen"), "negative modded humidity was not clamped before classification");
    }

    private static void rateLimiterSurvivesIntegratedServerRestart() {
        TickRateLimiter<String> limiter = new TickRateLimiter<>(4);
        check(limiter.allow("player", 100), "first action must pass");
        check(!limiter.allow("player", 103), "action inside window must be rejected");
        check(limiter.allow("player", 104), "action at window boundary must pass");
        check(limiter.allow("player", 2), "backwards tick after logical-server restart must start a new window");
        check(!limiter.allow("player", 3), "new server window must still rate-limit");
        limiter.remove("player");
        check(limiter.allow("player", 3), "logout removal must clear per-player state");
        limiter.clear();
        check(limiter.trackedKeys() == 0, "server-stop clear must release limiter state");
    }

    private static void configurationRejectsNonFiniteAndRunawayValues() {
        rejectsConfig(() -> new SimulationConfig(Double.NaN, 2048, 1, 1, 1, 1, 1, 1, 1, .1, .8, 12), "NaN physical radius");
        rejectsConfig(() -> new SimulationConfig(320, Double.POSITIVE_INFINITY, 1, 1, 1, 1, 1, 1, 1, .1, .8, 12), "infinite regional radius");
        rejectsConfig(() -> new SimulationConfig(320, 2048, SimulationConfig.MAX_PHYSICAL_ENTITY_BUDGET + 1, 1, 1, 1, 1, 1, 1, .1, .8, 12), "runaway entity budget");
        rejectsConfig(() -> new SimulationConfig(320, 2048, 1, 1, 1, 1, 1, SimulationConfig.MAX_STRATEGIC_DAYS_PER_STEP + 1, 1, .1, .8, 12), "runaway strategic step");
        rejectsConfig(() -> new SimulationConfig(320, 2048, 1, 1, 1, 1, 1, 1, Double.NaN, .1, .8, 12), "NaN decay");
        rejectsConfig(() -> new SimulationConfig(320, 2048, 1, 1, 1, 1, 1, 1, 1, .1, Double.NaN, 12), "NaN rebellion threshold");
    }

    private static void positionsRejectNonFiniteAndOutOfWorldValues() {
        rejectsPosition(() -> new SimPosition(Double.NaN, 0), "NaN position");
        rejectsPosition(() -> new SimPosition(Double.POSITIVE_INFINITY, 0), "infinite position");
        rejectsPosition(() -> new SimPosition(SimPosition.MAX_ABS_COORDINATE + 1, 0), "outside Minecraft coordinate envelope");
        new SimPosition(SimPosition.MAX_ABS_COORDINATE, -SimPosition.MAX_ABS_COORDINATE);
    }

    private static void canonicalAdditionsAdvanceIdWatermark() {
        SimulationState state = new SimulationState(77L);
        Faction faction = new Faction(10_000L, "Imported Realm", "Ruler");
        faction.addSettlement(new Settlement(10_500L, "Imported City", new SimPosition(0, 0), 100, 120));
        state.addFaction(faction);
        check(state.peekNextId() == 10_501L, "addFaction did not observe imported child IDs");
        check(state.nextId() == 10_501L, "next canonical allocation did not follow imported watermark");
    }

    private static void currentSchemaRepairsStaleIdWatermark() {
        SimulationState state = new SimulationState(0x51A7E5L);
        DemoSeeder.seed(state);
        byte[] encoded = SimulationStateCodec.encode(state);
        ByteBuffer.wrap(encoded).putLong(24, 1L); // Simulate an old/stale header watermark.
        SimulationState restored = SimulationStateCodec.decode(encoded, state.species());
        long maxKnown = restored.factions().stream().flatMap(f -> f.settlements().stream()).mapToLong(s -> s.id()).max().orElse(0L);
        check(restored.peekNextId() > maxKnown, "stale nextId watermark was not repaired");
        long allocated = restored.nextId();
        check(allocated > maxKnown, "next allocated canonical id can collide after repair");
    }

    private static void encoderRejectsInvalidCanonicalReferences() {
        SimulationState invalid = new SimulationState(91L);
        Faction faction = new Faction(100L, "Broken Realm", "Ruler");
        faction.addSettlement(new Settlement(101L, "Broken City", new SimPosition(0, 0), 100, 120));
        faction.relationWith(999L).adjust(10.0D); // Missing counterparty: semantically invalid canonical state.
        invalid.addFaction(faction);
        boolean rejected = false;
        try { SimulationStateCodec.encode(invalid); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "encoder wrote a semantically invalid current-schema state");
    }

    private static void rejectsConfig(Runnable factory, String label) {
        boolean rejected = false;
        try { factory.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid config accepted: " + label);
    }

    private static void rejectsPosition(Runnable factory, String label) {
        boolean rejected = false;
        try { factory.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid position accepted: " + label);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
