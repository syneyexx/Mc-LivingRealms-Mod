package dev.livingrealms.minecraft;

import dev.livingrealms.sim.compat.ModCompatibilityPolicy;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Discovers already-generated residential/civic structures in loaded chunks and folds them into
 * canonical Living Realms state. Foreign structure blocks remain authoritative; Living Realms marks
 * their initial infrastructure as complete and only builds later expansion around them.
 */
public final class ForeignStructureDiscoveryRuntime {
    private static final int CHUNK_RADIUS = 4;
    private static final double EXISTING_RADIUS = 220.0D;
    private static final Set<Long> SCANNED_CHUNKS = new HashSet<>();

    private ForeignStructureDiscoveryRuntime() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        for (var player : level.players()) {
            ChunkPos center = new ChunkPos(player.blockPosition());
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                if (!level.hasChunk(chunk.x, chunk.z) || !SCANNED_CHUNKS.add(chunk.toLong())) continue;
                scanChunk(level, data, chunk);
            }
        }
    }

    public static void clear() { SCANNED_CHUNKS.clear(); }

    private static void scanChunk(ServerLevel level, LivingRealmsSavedData data, ChunkPos chunk) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (StructureStart start : level.structureManager().startsForStructure(chunk, structure -> settlementLike(registry.getKey(structure)))) {
            if (start == null || !start.isValid()) continue;
            ResourceLocation id = registry.getKey(start.getStructure());
            if (!settlementLike(id)) continue;
            var box = start.getBoundingBox();
            SimPosition pos = new SimPosition((box.minX() + box.maxX()) * 0.5D, (box.minZ() + box.maxZ()) * 0.5D);
            if (hasSettlementNear(data, pos)) continue;
            adopt(data, id, pos);
        }
    }

    private static boolean settlementLike(ResourceLocation id) {
        if (id == null) return false;
        String namespace = id.getNamespace();
        String path = id.getPath().toLowerCase(Locale.ROOT);
        boolean targetNamespace = namespace.equals("minecraft") || ModCompatibilityPolicy.find(namespace)
                .map(entry -> entry.usableByLivingWorld() && (entry.category() == ModCompatibilityPolicy.Category.WORLDGEN
                        || entry.category() == ModCompatibilityPolicy.Category.BUILDING
                        || entry.category() == ModCompatibilityPolicy.Category.CONTENT))
                .orElse(false);
        if (!targetNamespace) return false;
        return path.contains("village") || path.contains("town") || path.contains("city") || path.contains("colony")
                || path.contains("settlement") || path.contains("castle") || path.contains("fort") || path.contains("keep")
                || path.contains("tower") || path.contains("medieval") || path.contains("manor") || path.contains("outpost")
                || path.contains("hamlet") || path.contains("house") || path.contains("building");
    }

    private static boolean hasSettlementNear(LivingRealmsSavedData data, SimPosition pos) {
        for (Faction faction : data.state().factions()) for (Settlement settlement : faction.settlements())
            if (settlement.position().distanceTo(pos) <= EXISTING_RADIUS) return true;
        return false;
    }

    private static void adopt(LivingRealmsSavedData data, ResourceLocation structureId, SimPosition pos) {
        Faction owner = data.state().factions().stream().min(Comparator.comparingDouble(f -> f.settlements().stream()
                .mapToDouble(s -> s.position().distanceTo(pos)).min().orElse(Double.POSITIVE_INFINITY))).orElse(null);
        if (owner == null) return;
        int population = inferredPopulation(structureId.getPath());
        Settlement settlement = new Settlement(data.state().nextId(), generatedName(structureId, pos), pos, population,
                (int)Math.ceil(population * 1.18D));
        owner.addSettlement(settlement);
        ForeignSettlementBootstrap.preserveExistingInfrastructure(data.state(), owner, settlement);
        owner.stockpile().add(ResourceType.FOOD, Math.max(180, population * 0.7D));
        owner.stockpile().add(ResourceType.WOOD, Math.max(120, population * 0.35D));
        owner.stockpile().add(ResourceType.STONE, Math.max(100, population * 0.30D));
        data.state().history().add(new WorldEvent(data.state().clock().day(), "foreign_structure_adopted",
                settlement.name() + " adopted from " + structureId + " into " + owner.name() + "; original structure preserved"));
        data.setDirty();
    }

    private static int inferredPopulation(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.contains("city")) return 2_400;
        if (p.contains("town")) return 1_150;
        if (p.contains("village") || p.contains("colony") || p.contains("settlement")) return 420;
        if (p.contains("castle") || p.contains("fort") || p.contains("keep")) return 260;
        if (p.contains("tower") || p.contains("outpost")) return 95;
        return 135;
    }

    private static String generatedName(ResourceLocation structureId, SimPosition pos) {
        String raw = structureId.getPath().replace('/', '_');
        String[] words = raw.split("[_\\-]+");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (word.isBlank() || word.equals("structure")) continue;
            if (!title.isEmpty()) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        if (title.isEmpty()) title.append("Foreign Settlement");
        int mark = Math.floorMod((int)Math.round(pos.x() * 31 + pos.z() * 17), 997);
        return title + " " + mark;
    }
}
