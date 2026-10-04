package dev.livingrealms.minecraft;

import dev.livingrealms.minecraft.compat.WaystoneSettlementRuntime;
import dev.livingrealms.minecraft.construction.AuthoredBlockLedgerNbt;
import dev.livingrealms.minecraft.construction.SettlementGeographyNbt;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
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

/** Persistent canonical state stored in the Overworld data directory. */
public final class LivingRealmsSavedData extends SavedData {
    public static final String FILE_ID = "livingrealms_world";
    private static final String KEY_SCHEMA = "Schema";
    private static final String KEY_PAYLOAD = "Payload";
    private static final String KEY_PAYLOAD_INTEGRITY = "PayloadCrc32Plus1";
    private static final String KEY_CONTENT_REVISION = "ContentRevision";
    /**
     * Revision 8 keeps revision-7 Waystone provenance and adds a bounded authored-block ledger for
     * construction safety without changing SimulationStateCodec schema 16 bytes.
     */
    private static final int CONTENT_REVISION = 8;

    private final SimulationState state;
    /** settlementId -> packed BlockPos of Living Realms-authored Waystone only. */
    private final Map<Long, Long> waystonesBySettlement = new LinkedHashMap<>();
    /** Chunk-local Living Realms-authored block provenance (not part of binary schema payload). */
    private final AuthoredBlockLedger authoredBlocks = new AuthoredBlockLedger();

    private LivingRealmsSavedData(SimulationState state) {
        this.state = state;
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
        if (expectedIntegrity != 0L && expectedIntegrity != SimulationStateCodec.integrityToken(payload)) {
            throw new IllegalStateException("Living Realms save payload checksum mismatch");
        }
        int contentRevision = tag.getInt(KEY_CONTENT_REVISION);
        if (contentRevision > CONTENT_REVISION) throw new IllegalStateException("Unsupported Living Realms content revision " + contentRevision);
        LivingRealmsSavedData loaded = new LivingRealmsSavedData(SimulationStateCodec.decode(payload, SpeciesDataRegistry.current()));
        loaded.waystonesBySettlement.putAll(WaystoneSettlementRuntime.readProvenance(tag));
        AuthoredBlockLedgerNbt.read(tag, loaded.authoredBlocks);
        SettlementGeographyNbt.read(tag, loaded.state());
        // Revision 3 rebuilt unsafe early-RC structures. Revision 4 expands the canonical world
        // to the twelve-kingdom target while preserving already-migrated physical construction.
        int densityChanges = contentRevision < 6 ? SettlementDensitySeeder.ensureStarterDensity(loaded.state()) : 0;
        int wizardChanges = contentRevision < 5 ? WizardTreesSeeder.ensure(loaded.state()) : 0;
        int constructionResets=0;
        // Revision 6 replaces the old curved/free-form settlement layouts with connected street
        // blocks, accessible floors, denser housing and capital castles. Completion keys must be
        // rebuilt once so old saves do not incorrectly treat the new geometry as already present.
        if(contentRevision < 6){
            for(var faction:loaded.state().factions())for(var settlement:faction.settlements())constructionResets+=settlement.resetConstructionCompletion();
        }
        // Revision 8 introduces authored-block provenance. Do not reset construction completion:
        // existing completed keys stay; future overwrite protection uses the ledger + natural terrain.
        // Legacy payloads, pre-checksum RC saves and older RC4 content are rewritten in the
        // current validated form on the next normal Minecraft save. The content revision makes each
        // world-content migration one-shot so later conquest/destruction is never resurrected.
        if (outerSchema != SimulationStateCodec.SCHEMA_VERSION || expectedIntegrity == 0L || contentRevision < CONTENT_REVISION || densityChanges > 0 || wizardChanges > 0 || constructionResets > 0) loaded.setDirty();
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
