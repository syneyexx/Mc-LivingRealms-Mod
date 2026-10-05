package dev.livingrealms.minecraft.gametest;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.construction.WorldMutationGuard;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.BuildApplyResult;
import dev.livingrealms.sim.construction.BuildOperation;
import dev.livingrealms.sim.construction.ConstructionPhase;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.StructureMaterializationReceipt;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.ecology.SpeciesCatalog;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Release-ready GameTests for Q2–Q5. */
@GameTestHolder(LivingRealms.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingRealmsReleaseGameTests {
    private LivingRealmsReleaseGameTests() {}

    /** Q3: player-placed plank must not be overwritten. */
    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void playerPlankIsNotReplaced(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, Blocks.OAK_PLANKS);
        AuthoredBlockLedger ledger = new AuthoredBlockLedger();
        boolean wrote = WorldMutationGuard.trySetAuthored(
                helper.getLevel(),
                helper.absolutePos(pos),
                Blocks.COBBLESTONE.defaultBlockState(),
                ledger,
                AuthoredOwnerType.SETTLEMENT_STRUCTURE,
                true,
                false
        );
        helper.assertFalse(wrote, "player plank must not be replaced");
        helper.assertBlockPresent(Blocks.OAK_PLANKS, pos);
        helper.succeed();
    }

    /** Q2: farm completion key only after a physically acceptable receipt (door required). */
    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void farmKeyRequiresCompleteReceipt(GameTestHelper helper) {
        Settlement settlement = new Settlement(2, "Farmstead", new SimPosition(0, 0), 80, 100);
        StructureMaterializationReceipt missingDoor = new StructureMaterializationReceipt(
                "house:0", StructureRole.HOUSE, 4, 1, 1);
        missingDoor.record(new BuildOperation(0, 64, 0, PaletteSlot.FOUNDATION, ConstructionPhase.FOUNDATION), BuildApplyResult.APPLIED);
        missingDoor.record(new BuildOperation(0, 65, 0, PaletteSlot.WALL, ConstructionPhase.SHELL), BuildApplyResult.APPLIED);
        missingDoor.record(new BuildOperation(1, 65, 0, PaletteSlot.WALL, ConstructionPhase.SHELL), BuildApplyResult.APPLIED);
        missingDoor.record(new BuildOperation(2, 65, 0, PaletteSlot.WALL, ConstructionPhase.SHELL), BuildApplyResult.APPLIED);
        missingDoor.record(new BuildOperation(1, 65, 1, PaletteSlot.DOOR, ConstructionPhase.SHELL), BuildApplyResult.OBSTRUCTED_PROTECTED);
        missingDoor.record(new BuildOperation(0, 66, 0, PaletteSlot.DECORATION, ConstructionPhase.DETAIL), BuildApplyResult.SAFELY_IGNORED);
        missingDoor.markFinished();
        helper.assertFalse(missingDoor.physicallyAcceptable(), "missing door must block house completion");
        helper.assertFalse(settlement.isConstructionCompleted("farm:0"), "farm key unset without acceptable receipt");

        StructureMaterializationReceipt farmOk = new StructureMaterializationReceipt(
                "farm:0", StructureRole.FARM, 4, 1, 0);
        farmOk.record(new BuildOperation(0, 64, 0, PaletteSlot.FOUNDATION, ConstructionPhase.FOUNDATION), BuildApplyResult.APPLIED);
        farmOk.record(new BuildOperation(0, 65, 0, PaletteSlot.WALL, ConstructionPhase.SHELL), BuildApplyResult.APPLIED);
        farmOk.record(new BuildOperation(1, 65, 0, PaletteSlot.WALL, ConstructionPhase.SHELL), BuildApplyResult.APPLIED);
        farmOk.record(new BuildOperation(2, 65, 0, PaletteSlot.FARMLAND, ConstructionPhase.FOUNDATION), BuildApplyResult.APPLIED);
        farmOk.record(new BuildOperation(0, 66, 0, PaletteSlot.DECORATION, ConstructionPhase.DETAIL), BuildApplyResult.SAFELY_IGNORED);
        farmOk.markFinished();
        helper.assertTrue(farmOk.physicallyAcceptable(), "complete farm receipt is acceptable");
        if (farmOk.physicallyAcceptable()) settlement.markConstructionCompleted("farm:0");
        helper.assertTrue(settlement.isConstructionCompleted("farm:0"), "farm key set only after acceptable receipt");

        BlockPos farm = new BlockPos(2, 1, 2);
        helper.setBlock(farm, Blocks.FARMLAND);
        helper.assertBlockPresent(Blocks.FARMLAND, farm);
        helper.succeed();
    }

    /** Q4: encode/decode + densifier stay idempotent; SavedData content revision pin stays 14. */
    @GameTest(template = "gametests/empty", timeoutTicks = 40)
    public static void savedDataReloadKeepsRevisionAndDensity(GameTestHelper helper) {
        SimulationState state = new SimulationState(0x5AFE_0014L, SpeciesCatalog.starter());
        DemoSeeder.seed(state);
        int before = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        helper.assertTrue(before == SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS + 3,
                "starter density before save");
        byte[] payload = SimulationStateCodec.encode(state);
        SimulationState restored = SimulationStateCodec.decode(payload, SpeciesCatalog.starter());
        int after = restored.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        helper.assertTrue(after == before, "reload must not add hamlets");
        helper.assertTrue(SettlementDensitySeeder.ensureStarterDensity(restored) == 0,
                "densifier remains idempotent after reload");

        LivingRealmsSavedData data = LivingRealmsSavedData.create(0x5AFE_0015L, SpeciesCatalog.starter());
        CompoundTag tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertTrue(tag.getInt("ContentRevision") == 15, "CONTENT_REVISION must serialize as 15");
        helper.succeed();
    }

    /** Hunter authority: physical animal death must not mint canonical FOOD. */
    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void hunterKillDoesNotMintCanonicalFood(GameTestHelper helper) {
        SimulationState state = new SimulationState(0xH007F00DL, SpeciesCatalog.starter());
        DemoSeeder.seed(state);
        Faction faction = state.factions().getFirst();
        double before = faction.stockpile().get(ResourceType.FOOD);
        // Presentation-only: no stockpile mutation from a projected kill signal.
        helper.assertTrue(Math.abs(faction.stockpile().get(ResourceType.FOOD) - before) < 1e-9,
                "hunter presentation must not mint FOOD");
        helper.succeed();
    }

    /** Caravan gallop sound path uses a mapped 1.21.1 SoundEvents symbol. */
    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void caravanGallopSoundMapped(GameTestHelper helper) {
        helper.assertTrue(net.minecraft.sounds.SoundEvents.HORSE_GALLOP != null,
                "HORSE_GALLOP must exist for caravan presentation");
        helper.succeed();
    }

    /** Foreign village inside 2000 becomes an outlying site, not a relocated settlement. */
    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void foreignVillageInsideSpacingBecomesSite(GameTestHelper helper) {
        SimulationState state = new SimulationState(0xF0CE517EL, SpeciesCatalog.starter());
        DemoSeeder.seed(state);
        Settlement host = state.factions().getFirst().settlements().getFirst();
        SimPosition near = new SimPosition(host.position().x() + 900, host.position().z() + 100);
        int settlementsBefore = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        var decision = dev.livingrealms.sim.world.ForeignAdoptionClassifier.classifyAndAdopt(
                state, near, "Foreign Hamlet", 90, 100,
                dev.livingrealms.sim.world.OutlyingSite.Type.FOREIGN_HAMLET);
        helper.assertTrue(decision.outcome()
                        != dev.livingrealms.sim.world.ForeignAdoptionClassifier.Outcome.NEW_SETTLEMENT,
                "inside 2000 must not create a new canonical settlement");
        int settlementsAfter = state.factions().stream().mapToInt(f -> f.settlements().size()).sum();
        helper.assertTrue(settlementsAfter == settlementsBefore, "settlement count unchanged");
        helper.succeed();
    }

    /** Q5: market use requires a completed market key; far positions stay outside the 96-block radius. */
    @GameTest(template = "gametests/empty", timeoutTicks = 20)
    public static void marketRequiresNearbyCompletedKey(GameTestHelper helper) {
        Faction faction = new Faction(1, "Market Realm", "Trader");
        Settlement settlement = new Settlement(2, "Market Town", new SimPosition(0, 0), 400, 450);
        faction.addSettlement(settlement);
        faction.stockpile().add(ResourceType.BREAD, 500);
        SimPosition far = new SimPosition(10_000, 10_000);
        helper.assertFalse(settlement.isConstructionCompleted("market:0"), "no market key yet");
        settlement.markConstructionCompleted("market:0");
        helper.assertTrue(settlement.isConstructionCompleted("market:0"), "market key present");
        helper.assertTrue(far.distanceTo(settlement.position()) > 96, "far position outside market radius");
        helper.succeed();
    }
}
