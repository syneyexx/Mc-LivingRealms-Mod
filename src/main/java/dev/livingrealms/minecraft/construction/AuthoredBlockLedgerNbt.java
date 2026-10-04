package dev.livingrealms.minecraft.construction;

import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

/** Minecraft NBT bridge for {@link AuthoredBlockLedger}. Keeps schema-16 payload bytes untouched. */
public final class AuthoredBlockLedgerNbt {
    private AuthoredBlockLedgerNbt() {}

    public static void write(CompoundTag tag, AuthoredBlockLedger ledger) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Set<Long>> e : ledger.snapshot().entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putLong("Chunk", e.getKey());
            long[] packed = e.getValue().stream().mapToLong(Long::longValue).toArray();
            row.put("Blocks", new LongArrayTag(packed));
            list.add(row);
            if (list.size() >= AuthoredBlockLedger.MAX_CHUNKS) break;
        }
        tag.put(AuthoredBlockLedger.NBT_KEY, list);
    }

    public static void read(CompoundTag tag, AuthoredBlockLedger ledger) {
        ledger.clear();
        if (!tag.contains(AuthoredBlockLedger.NBT_KEY, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(AuthoredBlockLedger.NBT_KEY, Tag.TAG_COMPOUND);
        LinkedHashMap<Long, long[]> packed = new LinkedHashMap<>();
        for (int i = 0; i < list.size() && packed.size() < AuthoredBlockLedger.MAX_CHUNKS; i++) {
            CompoundTag row = list.getCompound(i);
            long chunk = row.getLong("Chunk");
            long[] blocks = row.getLongArray("Blocks");
            if (blocks.length == 0) continue;
            if (blocks.length > AuthoredBlockLedger.MAX_BLOCKS_PER_CHUNK) {
                long[] trimmed = new long[AuthoredBlockLedger.MAX_BLOCKS_PER_CHUNK];
                System.arraycopy(blocks, 0, trimmed, 0, trimmed.length);
                blocks = trimmed;
            }
            packed.put(chunk, blocks);
        }
        ledger.load(packed);
    }
}
