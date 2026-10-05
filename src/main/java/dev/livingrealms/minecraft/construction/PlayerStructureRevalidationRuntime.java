package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.sim.construction.PlayerStructureRevalidation;
import dev.livingrealms.sim.construction.PlayerStructureValidator;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.world.SimulationState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Minecraft adapter for {@link PlayerStructureRevalidation}: dirty on nearby block edits,
 * bounded periodic re-scan of loaded registered buildings, player feedback without spam.
 */
public final class PlayerStructureRevalidationRuntime {
    private static final Map<UUID, Long> LAST_FEEDBACK_TICK = new HashMap<>();
    private static final int FEEDBACK_COOLDOWN_TICKS = 200;
    private static int backstopCursor;

    private PlayerStructureRevalidationRuntime() {}

    public static void onBlockChanged(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        var data = SimulationRuntime.data(level.getServer());
        if (PlayerStructureRevalidation.markDirtyAt(data.state(), pos.getX(), pos.getY(), pos.getZ()) > 0) {
            data.setDirty();
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        var data = SimulationRuntime.data(server);
        SimulationState state = data.state();
        // Drain dirty queue.
        long[] dirty = PlayerStructureRevalidation.pollDirty(state, PlayerStructureRevalidation.MAX_PER_TICK);
        boolean changed = false;
        for (long id : dirty) {
            if (revalidateOne(overworld, state, id)) changed = true;
        }
        // Periodic backstop: round-robin a few valid structures whose chunks are loaded.
        if (server.getTickCount() % 40 == 0) {
            var list = state.registeredPlayerStructures();
            if (!list.isEmpty()) {
                int start = Math.floorMod(backstopCursor, list.size());
                int scanned = 0;
                for (int i = 0; i < list.size() && scanned < 2; i++) {
                    RegisteredPlayerStructure s = list.get(Math.floorMod(start + i, list.size()));
                    if (!s.valid()) continue;
                    BlockPos door = new BlockPos(s.doorX(), s.doorY(), s.doorZ());
                    if (!chunkLoaded(overworld, door)) continue;
                    state.enqueuePlayerStructureRevalidation(s.id());
                    scanned++;
                }
                backstopCursor = start + Math.max(1, scanned);
            }
        }
        if (changed) data.setDirty();
    }

    private static boolean revalidateOne(ServerLevel level, SimulationState state, long structureId) {
        RegisteredPlayerStructure structure = state.findRegisteredPlayerStructure(structureId).orElse(null);
        if (structure == null) return false;
        BlockPos door = new BlockPos(structure.doorX(), structure.doorY(), structure.doorZ());
        if (!chunkLoaded(level, door)) {
            // Leave validity unchanged; retry later.
            return false;
        }
        // Ensure the registered box is loaded enough to classify enclosure.
        if (!boxLoaded(level, structure)) {
            PlayerStructureRevalidation.applySurvey(state, structureId, dummyMetrics(), 0, true);
            return false;
        }
        var scan = PlayerStructureSurvey.scanInterior(level, door);
        if (!scan.ok()) {
            if (scan.reason() == PlayerStructureSurvey.FailReason.REACHES_UNLOADED_BOUNDARY
                    || scan.reason() == PlayerStructureSurvey.FailReason.ENTRANCE_UNLOADED) {
                return false;
            }
            boolean wasValid = structure.valid();
            int before = structure.capacity();
            var result = PlayerStructureRevalidation.applySurvey(state, structureId,
                    new PlayerStructureValidator.SurveyMetrics(0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false),
                    structure.fingerprint(), false);
            if (wasValid && result.success()) {
                notifyNearby(level, structure, "Living Realms: Registered "
                        + structure.role().name().toLowerCase().replace('_', ' ')
                        + " no longer meets housing requirements.");
            }
            return before != structure.capacity() || wasValid != structure.valid();
        }
        var m = scan.metrics();
        PlayerStructureValidator.SurveyMetrics metrics = new PlayerStructureValidator.SurveyMetrics(
                m.width, m.depth, m.height, m.interiorCells, m.inspected,
                m.roofCoverage, m.floorCoverage, m.beds, m.enclosed, m.validEntrance, false, false);
        long fingerprint = (((long) m.minX) << 48) ^ (((long) m.minZ) << 32)
                ^ (((long) m.interiorCells) << 16) ^ m.beds ^ structure.role().ordinal();
        int before = structure.capacity();
        boolean wasValid = structure.valid();
        PlayerStructureRevalidation.applySurvey(state, structureId, metrics, fingerprint, false);
        if (wasValid && (!structure.valid() || structure.capacity() < before)) {
            notifyNearby(level, structure, "Living Realms: Registered "
                    + structure.role().name().toLowerCase().replace('_', ' ')
                    + " no longer meets housing requirements.");
        }
        return before != structure.capacity() || wasValid != structure.valid();
    }

    private static void notifyNearby(ServerLevel level, RegisteredPlayerStructure structure, String message) {
        BlockPos door = new BlockPos(structure.doorX(), structure.doorY(), structure.doorZ());
        long tick = level.getServer().getTickCount();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5) > 48 * 48) continue;
            Long last = LAST_FEEDBACK_TICK.get(player.getUUID());
            if (last != null && tick - last < FEEDBACK_COOLDOWN_TICKS) continue;
            LAST_FEEDBACK_TICK.put(player.getUUID(), tick);
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private static boolean boxLoaded(ServerLevel level, RegisteredPlayerStructure s) {
        // Sample corners only — never force-load.
        return chunkLoaded(level, new BlockPos(s.minX(), s.minY(), s.minZ()))
                && chunkLoaded(level, new BlockPos(s.maxX(), s.minY(), s.minZ()))
                && chunkLoaded(level, new BlockPos(s.minX(), s.minY(), s.maxZ()))
                && chunkLoaded(level, new BlockPos(s.maxX(), s.minY(), s.maxZ()));
    }

    private static boolean chunkLoaded(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL);
    }

    private static PlayerStructureValidator.SurveyMetrics dummyMetrics() {
        return new PlayerStructureValidator.SurveyMetrics(0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false);
    }

    public static void clear() {
        LAST_FEEDBACK_TICK.clear();
        backstopCursor = 0;
    }
}
