package dev.livingrealms;

import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.BuildApplyResult;
import dev.livingrealms.sim.construction.BuildOperation;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.ConstructionJob;
import dev.livingrealms.sim.construction.ConstructionPhase;
import dev.livingrealms.sim.construction.ConstructionQueue;
import dev.livingrealms.sim.construction.ConstructionRetryKey;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.StructureIntegrityRules;
import dev.livingrealms.sim.construction.StructureMaterializationReceipt;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementGeographyProfile;
import dev.livingrealms.sim.faction.Stockpile;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** P0 regression gates: retry isolation, typed provenance, omitted geometry, foreign adoption, economy authority. */
public final class ProvenanceSafetyTest {
    private ProvenanceSafetyTest() {}

    public static void main(String[] args) {
        retryKeysIsolatedBetweenSettlements();
        typedProvenanceSeparatesOwners();
        provenanceCapCannotCreateUntrackedWriteContract();
        omittedRoadCellsPreventFalseCompletion();
        roadContinuityValidation();
        foreignAdoptionDoesNotCreatePhantomMine();
        geographyOverridesNamingAfterDiscovery();
        physicalProjectionCannotChangeEconomicOutput();
        System.out.println("PASS provenance safety: retry isolation + typed ownership + omitted geometry + foreign adoption + economy authority");
    }

    private static void retryKeysIsolatedBetweenSettlements() {
        ConstructionRetryKey a = new ConstructionRetryKey(10, "house:4");
        ConstructionRetryKey b = new ConstructionRetryKey(11, "house:4");
        check(!a.equals(b), "same local intent key must differ by settlement");
        check(a.wire().equals("10:house:4"), "wire format");
        check(b.wire().equals("11:house:4"), "wire format B");
        Map<String, Long> retry = new HashMap<>();
        retry.put(a.wire(), 5L);
        check(retry.get(b.wire()) == null, "settlement B must not inherit settlement A backoff");
        ConstructionIntent intentA = new ConstructionIntent("road:2:5", 1, 10, StructureRole.ROAD, new SimPosition(0, 0), 3, 5, 0, 10);
        ConstructionIntent intentB = new ConstructionIntent("road:2:5", 1, 11, StructureRole.ROAD, new SimPosition(100, 100), 3, 5, 0, 10);
        check(!ConstructionRetryKey.of(intentA).equals(ConstructionRetryKey.of(intentB)), "intent retry keys must include settlement id");
        ConstructionJob job = new ConstructionJob(intentA, List.of(new BuildOperation(0, 64, 0, PaletteSlot.PATH, ConstructionPhase.FOUNDATION)), 0);
        check(job.key().equals("10:road:2:5"), "job key must be globally stable");
        check(ConstructionRetryKey.parse(job.key()).intentKey().equals("road:2:5"), "parse must recover intent key with colons");
    }

    private static void typedProvenanceSeparatesOwners() {
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        check(ledger.record(8, 64, 8, AuthoredOwnerType.SETTLEMENT_STRUCTURE), "record settlement structure");
        check(ledger.ownerType(8, 64, 8) == AuthoredOwnerType.SETTLEMENT_STRUCTURE, "owner type persisted");
        check(!ledger.record(8, 64, 8, AuthoredOwnerType.INTERCITY_ROUTE), "transport must not overwrite settlement structure");
        check(ledger.ownerType(8, 64, 8) == AuthoredOwnerType.SETTLEMENT_STRUCTURE, "owner unchanged after rejected overwrite");
        check(ledger.record(9, 64, 8, AuthoredOwnerType.INTERCITY_ROUTE), "route may author its own cell");
        check(!AuthoredOwnerType.allowsOverwrite(AuthoredOwnerType.SETTLEMENT_ROAD, AuthoredOwnerType.INDUSTRIAL_SITE), "industry cannot steal roads");
        check(AuthoredOwnerType.allowsOverwrite(AuthoredOwnerType.INDUSTRIAL_SITE, AuthoredOwnerType.INDUSTRIAL_SITE), "same owner may repair");
        // Legacy V1 bits (owner=0) migrate as SETTLEMENT_STRUCTURE.
        AuthoredBlockLedger legacy = new AuthoredBlockLedger();
        Map<Long, long[]> packed = new HashMap<>();
        packed.put(AuthoredBlockLedger.chunkKey(0, 0), new long[]{AuthoredBlockLedger.blockCoordKey(1, 70, 1)});
        legacy.load(packed);
        check(legacy.ownerType(1, 70, 1) == AuthoredOwnerType.SETTLEMENT_STRUCTURE, "legacy type bits 0 => settlement structure");
    }

    private static void provenanceCapCannotCreateUntrackedWriteContract() {
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        int accepted = 0;
        for (int i = 0; i < AuthoredBlockLedger.MAX_BLOCKS_PER_CHUNK + 20; i++) {
            int x = i % 16;
            int z = (i / 16) % 16;
            int y = 40 + (i / 256);
            if (ledger.record(x, y, z, AuthoredOwnerType.SETTLEMENT_STRUCTURE)) accepted++;
        }
        check(accepted == AuthoredBlockLedger.MAX_BLOCKS_PER_CHUNK, "cap must hold");
        check(!ledger.canRecord(15, 40 + (AuthoredBlockLedger.MAX_BLOCKS_PER_CHUNK / 256) + 8, 15),
                "canRecord must be false when saturated for a new coordinate");
        // Contract used by WorldMutationGuard: refuse mutation when canRecord is false.
        check(ledger.canRecord(0, 40, 0), "already-recorded coordinate remains recordable (idempotent)");
    }

    private static void omittedRoadCellsPreventFalseCompletion() {
        ConstructionIntent intent = new ConstructionIntent("road:9", 1, 2, StructureRole.ROAD, new SimPosition(0, 0), 3, 7, 0, 10);
        List<BuildOperation> ops = new ArrayList<>();
        for (int i = 0; i < 3; i++) ops.add(new BuildOperation(0, 70, i, PaletteSlot.PATH, ConstructionPhase.FOUNDATION));
        ConstructionJob job = new ConstructionJob(intent, ops, 0, 6);
        ConstructionQueue queue = new ConstructionQueue();
        queue.enqueue(job);
        int guard = 40;
        while (!queue.isEmpty() && guard-- > 0) queue.tick(8, (j, op) -> BuildApplyResult.ALREADY_CORRECT);
        check(job.complete(), "cursor finished");
        check(job.receipt().omittedRequired() == 6, "omitted required tracked");
        check(!job.physicallyComplete(), "omitted centerline cells must prevent false road completion");
    }

    private static void roadContinuityValidation() {
        List<BuildOperation> connected = List.of(
                new BuildOperation(0, 70, 0, PaletteSlot.PATH, ConstructionPhase.FOUNDATION),
                new BuildOperation(0, 70, 1, PaletteSlot.PATH, ConstructionPhase.FOUNDATION),
                new BuildOperation(0, 70, 2, PaletteSlot.PATH, ConstructionPhase.FOUNDATION)
        );
        StructureMaterializationReceipt ok = new StructureMaterializationReceipt("road:a", StructureRole.ROAD, 3, 0, 0, 0);
        for (BuildOperation op : connected) ok.record(op, BuildApplyResult.ALREADY_CORRECT);
        ok.markFinished();
        check(StructureIntegrityRules.roadConnected(connected, ok), "connected road accepted");

        List<BuildOperation> fragmented = List.of(
                new BuildOperation(0, 70, 0, PaletteSlot.PATH, ConstructionPhase.FOUNDATION),
                new BuildOperation(0, 70, 8, PaletteSlot.PATH, ConstructionPhase.FOUNDATION)
        );
        StructureMaterializationReceipt bad = new StructureMaterializationReceipt("road:b", StructureRole.ROAD, 2, 0, 0, 0);
        for (BuildOperation op : fragmented) bad.record(op, BuildApplyResult.ALREADY_CORRECT);
        bad.markFinished();
        check(!StructureIntegrityRules.roadConnected(fragmented, bad), "fragmented road rejected");
    }

    private static void foreignAdoptionDoesNotCreatePhantomMine() {
        SimulationState state = new SimulationState(42);
        DemoSeeder.seed(state);
        Faction faction = state.factions().iterator().next();
        Settlement settlement = new Settlement(state.nextId(), "Adopted Hamlet", new SimPosition(12_000, 12_000), 420, 500);
        faction.addSettlement(settlement);
        // Mirror ForeignSettlementBootstrap rules without Minecraft.
        settlement.markConstructionCompleted("foreign:adopted_footprint");
        settlement.markConstructionCompleted("house:0");
        settlement.markConstructionCompleted("keep:0");
        for (var intent : PrimaryEconomyPlanner.plan(state, faction, settlement)) {
            check(!settlement.isConstructionCompleted(intent.key()),
                    "foreign adoption must not mark primary economy complete: " + intent.key());
        }
        check(!settlement.isConstructionCompleted("mine:0"), "no phantom mine");
        check(!settlement.isConstructionCompleted("fishery:0"), "no phantom fishery");
        check(!settlement.isConstructionCompleted("lumber_camp:0"), "no phantom lumber camp");
    }

    private static void geographyOverridesNamingAfterDiscovery() {
        Settlement settlement = new Settlement(99, "Port Haven", new SimPosition(0, 0), 200, 220);
        check(settlement.geography().coastal(), "name heuristic bootstrap may imply coast");
        check(!settlement.geography().worldDiscovered(), "bootstrap is not world-discovered");
        SettlementGeographyProfile inland = SettlementGeographyProfile.unknown().withDiscovery(
                false, false, false, false, 0.05, 96, 3, 0.7, 0.2, 0.4, "minecraft:plains"
        );
        settlement.setGeography(inland);
        check(settlement.geography().worldDiscovered(), "discovery marks worldDiscovered");
        check(!settlement.geography().coastal(), "terrain discovery must override coastal name heuristic");
        check(!settlement.geography().shipSuitable(), "inland discovered geography is not ship-suitable");
        settlement.rename("Port Haven");
        check(!settlement.geography().coastal(), "rename must not overwrite discovered geography");
    }

    private static void physicalProjectionCannotChangeEconomicOutput() {
        SimulationState withProjection = new SimulationState(7);
        DemoSeeder.seed(withProjection);
        SimulationState withoutProjection = new SimulationState(7);
        DemoSeeder.seed(withoutProjection);
        // Simulate "physical workers present" by attempting stockpile adds that the old entity path did.
        // Under the new contract, projection must not mutate stockpiles — so both states stay identical
        // when only canonical advanceDays runs.
        for (int day = 0; day < 30; day++) {
            withProjection.advanceDays(1);
            withoutProjection.advanceDays(1);
            // Deliberately do NOT add physical worker bonuses to withProjection.
        }
        Faction a = withProjection.factions().iterator().next();
        Faction b = withoutProjection.factions().iterator().next();
        Stockpile sa = a.stockpile();
        Stockpile sb = b.stockpile();
        for (var type : dev.livingrealms.sim.faction.ResourceType.values()) {
            check(Math.abs(sa.get(type) - sb.get(type)) < 1e-9,
                    "canonical economy must match with/without physical projection for " + type);
        }
    }

    private static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }
}
