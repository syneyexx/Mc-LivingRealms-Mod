package dev.livingrealms.minecraft;

import dev.livingrealms.minecraft.compat.WaystoneSettlementRuntime;
import dev.livingrealms.minecraft.construction.AuthoredBlockLedgerNbt;
import dev.livingrealms.minecraft.construction.SettlementGeographyNbt;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.SettlementConstructionPolicy;
import dev.livingrealms.sim.persistence.ContentMigrationPolicy;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.ManualDayAdvanceScheduler;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.WizardTreesSeeder;
import dev.livingrealms.sim.worldgen.StarterWorldgenCompletion;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
    private static final String KEY_CIVILIZATION_WORLDGEN_VERSION = "CivilizationWorldgenVersion";
    private static final String KEY_ONBOARDED_PLAYERS = "OnboardedPlayers";
    /**
     * Revision 17: fresh saves adopt deterministic starter-worldgen receipts. Legacy saves remain
     * explicitly worldgen-disabled even after they are re-saved at the new content revision.
     */
    private static final int CONTENT_REVISION = 17;
    public static final int CURRENT_CIVILIZATION_WORLDGEN_VERSION = 1;

    private final SimulationState state;
    /** 0 = legacy runtime-authored starter layout; current value = true day-zero chunk worldgen. */
    private final int civilizationWorldgenVersion;
    /** settlementId -> packed BlockPos of Living Realms-authored Waystone only. */
    private final Map<Long, Long> waystonesBySettlement = new LinkedHashMap<>();
    /** Chunk-local Living Realms-authored block provenance (not part of binary schema payload). */
    private final AuthoredBlockLedger authoredBlocks = new AuthoredBlockLedger();
    /** Players who already received first-contact onboarding (outer NBT; not schema payload). */
    private final Set<UUID> onboardedPlayers = new HashSet<>();
    /** Transient setday/advance backlog; not persisted across reload. */
    private final ManualDayAdvanceScheduler dayAdvanceScheduler = new ManualDayAdvanceScheduler();

    private LivingRealmsSavedData(SimulationState state, int civilizationWorldgenVersion) {
        this.state = state;
        this.civilizationWorldgenVersion = Math.max(0, civilizationWorldgenVersion);
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
        LivingRealmsSavedData data = new LivingRealmsSavedData(state, CURRENT_CIVILIZATION_WORLDGEN_VERSION);
        StarterWorldgenCompletion.adoptPlannedBaseline(state);
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
        int worldgenVersion = tag.contains(KEY_CIVILIZATION_WORLDGEN_VERSION, Tag.TAG_INT)
                ? tag.getInt(KEY_CIVILIZATION_WORLDGEN_VERSION) : 0;
        if (worldgenVersion > CURRENT_CIVILIZATION_WORLDGEN_VERSION) {
            throw new IllegalStateException("Unsupported Living Realms civilization worldgen version " + worldgenVersion);
        }
        LivingRealmsSavedData loaded = new LivingRealmsSavedData(
                SimulationStateCodec.decode(payload, SpeciesDataRegistry.current()), worldgenVersion);
        loaded.waystonesBySettlement.putAll(WaystoneSettlementRuntime.readProvenance(tag));
        AuthoredBlockLedgerNbt.read(tag, loaded.authoredBlocks);
        SettlementGeographyNbt.read(tag, loaded.state());
        readOnboardedPlayers(tag, loaded.onboardedPlayers);
        Set<Long> legacyStreetFabricSettlementIds = new HashSet<>();
        if (ContentMigrationPolicy.shouldFreezeLegacyStreetFabric(contentRevision)) {
            for (var faction : loaded.state().factions()) for (var settlement : faction.settlements()) {
                if (SettlementConstructionPolicy.hasLegacyRoadReceipt(settlement)) {
                    legacyStreetFabricSettlementIds.add(settlement.id());
                }
            }
        }
        // Morphology reset MUST run before densifier so new hamlets keep empty completion, while
        // already-present settlements lose obsolete geometry keys exactly once (revision < 10).
        int constructionResets = 0;
        if (ContentMigrationPolicy.shouldResetMorphology(contentRevision)) {
            for (var faction : loaded.state().factions()) for (var settlement : faction.settlements()) {
                constructionResets += settlement.resetConstructionCompletion();
            }
        }
        int legacyFabricFreezes = 0;
        if (!legacyStreetFabricSettlementIds.isEmpty()) {
            for (var faction : loaded.state().factions()) for (var settlement : faction.settlements()) {
                if (legacyStreetFabricSettlementIds.contains(settlement.id())
                        && SettlementConstructionPolicy.markLegacyStreetFabric(settlement)) {
                    legacyFabricFreezes++;
                }
            }
        }
        int densityChanges = ContentMigrationPolicy.shouldEnsureDensity(contentRevision, CONTENT_REVISION)
                ? SettlementDensitySeeder.ensureStarterDensity(loaded.state()) : 0;
        int wizardChanges = ContentMigrationPolicy.shouldEnsureWizardTrees(contentRevision)
                ? WizardTreesSeeder.ensure(loaded.state()) : 0;
        densityChanges += 0; // spacing is planned at creation; anchored settlements are never relocated
        // Revision 8 introduced authored-block provenance. Revision 9 adds typed ownership.
        // Revision 10 morphology rebuild is gated above. Revision 11–13 are presentation/spacing.
        // Revision 14 is densifier Spec completeness + seeder completion-key ban.
        // Revision 16 freezes already-materialized legacy road layouts instead of rebuilding them.
        if (outerSchema != SimulationStateCodec.SCHEMA_VERSION
                || ContentMigrationPolicy.isLegacyIntegrityPath(expectedIntegrity)
                || contentRevision < CONTENT_REVISION
                || densityChanges > 0 || wizardChanges > 0 || constructionResets > 0
                || legacyFabricFreezes > 0) {
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

    public int civilizationWorldgenVersion() {
        return civilizationWorldgenVersion;
    }

    public boolean starterWorldgenEnabled() {
        return civilizationWorldgenVersion == CURRENT_CIVILIZATION_WORLDGEN_VERSION;
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

    public boolean hasOnboarded(UUID playerId) {
        return playerId != null && onboardedPlayers.contains(playerId);
    }

    public void markOnboarded(UUID playerId) {
        if (playerId == null) return;
        if (onboardedPlayers.add(playerId)) setDirty();
    }

    public void clearOnboardedPlayers() {
        if (!onboardedPlayers.isEmpty()) {
            onboardedPlayers.clear();
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        byte[] payload = SimulationStateCodec.encode(state);
        tag.putInt(KEY_SCHEMA, SimulationStateCodec.SCHEMA_VERSION);
        tag.putByteArray(KEY_PAYLOAD, payload);
        tag.putLong(KEY_PAYLOAD_INTEGRITY, SimulationStateCodec.integrityToken(payload));
        tag.putInt(KEY_CONTENT_REVISION, CONTENT_REVISION);
        tag.putInt(KEY_CIVILIZATION_WORLDGEN_VERSION, civilizationWorldgenVersion);
        WaystoneSettlementRuntime.writeProvenance(tag, waystonesBySettlement);
        AuthoredBlockLedgerNbt.write(tag, authoredBlocks);
        SettlementGeographyNbt.write(tag, state);
        writeOnboardedPlayers(tag, onboardedPlayers);
        return tag;
    }

    private static void writeOnboardedPlayers(CompoundTag tag, Set<UUID> players) {
        ListTag list = new ListTag();
        for (UUID id : players) list.add(StringTag.valueOf(id.toString()));
        tag.put(KEY_ONBOARDED_PLAYERS, list);
    }

    private static void readOnboardedPlayers(CompoundTag tag, Set<UUID> into) {
        into.clear();
        if (!tag.contains(KEY_ONBOARDED_PLAYERS, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(KEY_ONBOARDED_PLAYERS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                into.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries from hand-edited saves
            }
        }
    }
}
