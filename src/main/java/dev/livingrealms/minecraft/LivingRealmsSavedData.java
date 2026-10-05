package dev.livingrealms.minecraft;

import dev.livingrealms.minecraft.compat.WaystoneSettlementRuntime;
import dev.livingrealms.minecraft.construction.AuthoredBlockLedgerNbt;
import dev.livingrealms.minecraft.construction.SettlementGeographyNbt;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.persistence.ContentMigrationPolicy;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.ManualDayAdvanceScheduler;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.WizardTreesSeeder;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent canonical state stored in the Overworld data directory.
 */
public final class LivingRealmsSavedData extends SavedData {
    public static final String FILE_ID = "livingrealms_world";
    private static final String KEY_SCHEMA = "Schema";
    private static final String KEY_PAYLOAD = "Payload";
    private static final String KEY_PAYLOAD_INTEGRITY = "PayloadCrc32Plus1";
    private static final String KEY_CONTENT_REVISION = "ContentRevision";
    /**
     * Revision 14: migration order fix (morphology reset before densifier), no seeder completion
     * keys, all 10 Specs per realm, goods-chain ContentRevision alignment with schema 18.
     */
    private static final int CONTENT_REVISION = 14;

    private final SimulationState state;
    /** settlementId -> packed BlockPos of Living Realms-authored Waystone only. */
    private final Map<Long, Long> waystonesBySettlement = new LinkedHashMap<>();
    /** Chunk-local Living Realms-authored block provenance (not part of binary schema payload). */
    private final AuthoredBlockLedger authoredBlocks = new AuthoredBlockLedger();
    /** Transient setday/advance backlog; not persisted across reload. */
    private final ManualDayAdvanceScheduler dayAdvanceScheduler = new ManualDayAdvanceScheduler();

    private LivingRealmsSavedData(SimulationState state) {
        this.state = state;
    }

    public ManualDayAdvanceScheduler dayAdvanceScheduler() {
        return dayAdvanceScheduler;
    }

    public static LivingRealmsSavedData create(long worldSeed) {
        return create(worldSeed, SpeciesDataRegistry.current());
    }

    public static LivingRealmsSavedData create(long worldSeed, Map<String, SpeciesDefinition> speciesCatalog) {
        SimulationState state = new SimulationState(worldSeed, speciesCatalog);
        DemoSeeder.seed(state);
        LivingRealmsSavedData data = new LivingRealmsSavedData(state);
        data.setDirty();
        return data;
    }

    public static LivingRealmsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int outerSchema = tag.getInt(KEY_SCHEMA);
        if (outerSchema < SimulationStateCodec.MIN_SUPPORTED_SCHEMA || outerSchema > SimulationStateCodec.SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported Living Realms save schema " + outerSchema);
        }
        byte[] payload = tag.getByteArray(KEY_PAYLOAD);
        if (payload.length == 0) {
            throw new IllegalStateException("Living Realms save payload is empty");
        }
        int innerSchema = SimulationStateCodec.inspectSchema(payload);
        if (innerSchema != outerSchema) {
            throw new IllegalStateException("Living Realms save schema mismatch: outer=" + outerSchema + ", payload=" + innerSchema);
        }
        long expectedIntegrity = tag.getLong(KEY_PAYLOAD_INTEGRITY);
        long actualIntegrity = SimulationStateCodec.integrityToken(payload);
        if (ContentMigrationPolicy.shouldRejectCorruptPayload(expectedIntegrity, actualIntegrity)) {
            throw new IllegalStateException("Living Realms save payload checksum mismatch");
        }
        int contentRevision = tag.getInt(KEY_CONTENT_REVISION);
        if (contentRevision > CONTENT_REVISION) throw new IllegalStateException("Unsupported Living Realms content revision " + contentRevision);
        LivingRealmsSavedData loaded = new LivingRealmsSavedData(SimulationStateCodec.decode(payload, SpeciesDataRegistry.current()));
        loaded.waystonesBySettlement.putAll(WaystoneSettlementRuntime.readProvenance(tag));
        AuthoredBlockLedgerNbt.read(tag, loaded.authoredBlocks);
        SettlementGeographyNbt.read(tag, loaded.state());
        // Morphology reset MUST run before densifier so new hamlets keep empty completion, while
        // already-present settlements lose obsolete geometry keys exactly once (revision < 10).
        int constructionResets = 0;
        if (ContentMigrationPolicy.shouldResetMorphology(contentRevision)) {
            for (var faction : loaded.state().factions()) for (var settlement : faction.settlements()) {
                constructionResets += settlement.resetConstructionCompletion();
            }
        }
        int densityChanges = ContentMigrationPolicy.shouldEnsureDensity(contentRevision, CONTENT_REVISION)
                ? SettlementDensitySeeder.ensureStarterDensity(loaded.state()) : 0;
        int wizardChanges = ContentMigrationPolicy.shouldEnsureWizardTrees(contentRevision)
                ? WizardTreesSeeder.ensure(loaded.state()) : 0;
        densityChanges += SettlementDensitySeeder.enforceSpacing(loaded.state());
        // Revision 8 introduced authored-block provenance. Revision 9 adds typed ownership.
        // Revision 10 morphology rebuild is gated above. Revision 11–13 are presentation/spacing.
        // Revision 14 is densifier Spec completeness + seeder completion-key ban.
        if (outerSchema != SimulationStateCodec.SCHEMA_VERSION
                || ContentMigrationPolicy.isLegacyIntegrityPath(expectedIntegrity)
                || contentRevision < CONTENT_REVISION
                || densityChanges > 0 || wizardChanges > 0 || constructionResets > 0) {
            loaded.setDirty();
        }
        return loaded;
    }

    public SimulationState state() {
        return state;
    }

    public AuthoredBlockLedger authoredBlocks() {
        return authoredBlocks;
    }

    public Long waystoneForSettlement(long settlementId){return waystonesBySettlement.get(settlementId);}
    public Set<Long> livingRealmsWaystonePositions(){return Set.copyOf(waystonesBySettlement.values());}
    public Map<Long,Long> waystonesBySettlement(){return Collections.unmodifiableMap(waystonesBySettlement);}
    public void recordWaystone(long settlementId,long packedPos){
        if(settlementId<=0)return;
        Long prev=waystonesBySettlement.put(settlementId,packedPos);
        if(prev==null||prev.longValue()!=packedPos)setDirty();
    }
    public void clearWaystone(long settlementId){
        if(waystonesBySettlement.remove(settlementId)!=null)setDirty();
    }
    public void clearWaystoneAt(long packedPos){
        boolean changed=waystonesBySettlement.entrySet().removeIf(e->e.getValue()==packedPos);
        if(changed)setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        byte[] payload = SimulationStateCodec.encode(state);
        tag.putInt(KEY_SCHEMA, SimulationStateCodec.SCHEMA_VERSION);
        tag.putByteArray(KEY_PAYLOAD, payload);
        tag.putLong(KEY_PAYLOAD_INTEGRITY, SimulationStateCodec.integrityToken(payload));
        tag.putInt(KEY_CONTENT_REVISION, CONTENT_REVISION);
        WaystoneSettlementRuntime.writeProvenance(tag, waystonesBySettlement);
        AuthoredBlockLedgerNbt.write(tag, authoredBlocks);
        SettlementGeographyNbt.write(tag, state);
        return tag;
    }
}
