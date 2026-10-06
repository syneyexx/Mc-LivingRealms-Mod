package dev.livingrealms.minecraft.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.livingrealms.LivingRealms;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Persistent chunk-local metadata for true-worldgen Living Realms fabric. */
public final class ModWorldgenAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LivingRealms.MOD_ID);

    public static final Supplier<AttachmentType<ChunkProvenance>> STARTER_FABRIC_PROVENANCE =
            ATTACHMENTS.register("starter_fabric_provenance",
                    () -> AttachmentType.builder(ChunkProvenance::empty)
                            .serialize(ChunkProvenance.CODEC)
                            .build());

    private ModWorldgenAttachments() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static final class ChunkProvenance {
        private static final int OWNER_SHIFT = 20;
        private static final long OWNER_MASK = 0xFL << OWNER_SHIFT;
        private static final long COORD_MASK = ~OWNER_MASK;

        public static final Codec<ChunkProvenance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("version").forGetter(ChunkProvenance::version),
                Codec.LONG.listOf().fieldOf("entries").forGetter(ChunkProvenance::entries)
        ).apply(instance, ChunkProvenance::new));

        private final int version;
        private final List<Long> entries;
        private final Map<Long, AuthoredOwnerType> byCoordinate;

        public ChunkProvenance(int version, List<Long> entries) {
            this.version = Math.max(0, version);
            this.entries = List.copyOf(entries == null ? List.of() : entries);
            Map<Long, AuthoredOwnerType> indexed = new HashMap<>(Math.max(16, this.entries.size() * 2));
            for (long packed : this.entries) {
                indexed.put(packed & COORD_MASK,
                        AuthoredOwnerType.byId((int) ((packed & OWNER_MASK) >>> OWNER_SHIFT)));
            }
            this.byCoordinate = Map.copyOf(indexed);
        }

        public static ChunkProvenance empty() {
            return new ChunkProvenance(0, List.of());
        }

        public int version() { return version; }
        public List<Long> entries() { return entries; }
        public boolean isEmpty() { return entries.isEmpty(); }

        public AuthoredOwnerType ownerAt(int x, int y, int z) {
            return byCoordinate.get(coordKey(x, y, z));
        }

        public ChunkProvenance merge(int generationVersion, Collection<Long> additional) {
            if (additional == null || additional.isEmpty()) {
                return generationVersion == version ? this : new ChunkProvenance(generationVersion, entries);
            }
            Map<Long, Long> merged = new java.util.LinkedHashMap<>();
            for (long packed : entries) merged.put(packed & COORD_MASK, packed);
            for (long packed : additional) merged.put(packed & COORD_MASK, packed);
            return new ChunkProvenance(generationVersion, new ArrayList<>(merged.values()));
        }

        public static long pack(int x, int y, int z, AuthoredOwnerType owner) {
            if (owner == null) throw new IllegalArgumentException("owner");
            return (coordKey(x, y, z) & COORD_MASK) | ((long) owner.id() << OWNER_SHIFT);
        }

        private static long coordKey(int x, int y, int z) {
            int lx = x & 15;
            int lz = z & 15;
            int yy = Math.max(0, Math.min(4095, y + 2048));
            return ((long) yy << 8) | ((long) lx << 4) | lz;
        }
    }
}
