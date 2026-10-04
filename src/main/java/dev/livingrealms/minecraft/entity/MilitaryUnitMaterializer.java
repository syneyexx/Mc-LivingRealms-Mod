package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.military.MilitaryMaterializationPlanner;
import dev.livingrealms.sim.military.MilitaryProjection;
import dev.livingrealms.sim.military.MilitaryUnitClass;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Projects bounded military units near players. Positions use column / line / guard formations
 * rather than pure random scatter so armies read as organized forces.
 */
public final class MilitaryUnitMaterializer {
    private MilitaryUnitMaterializer() {}

    public static void tick(MinecraftServer server, LivingRealmsSavedData data) {
        ServerLevel level = server.overworld();
        List<SimPosition> players = level.players().stream().map(p -> new SimPosition(p.getX(), p.getZ())).toList();
        List<MilitaryProjection> desired = MilitaryMaterializationPlanner.plan(
                data.state().factions(), players,
                data.state().config().physicalRadiusBlocks(),
                data.state().config().maxPhysicalMilitaryEntities());
        Set<String> wanted = new HashSet<>();
        for (MilitaryProjection p : desired) wanted.add(p.projectionKey());
        Set<String> seen = new HashSet<>();
        for (MilitaryUnitEntity e : MilitaryUnitIndex.loaded()) {
            // Escort projections use a negative army-id namespace owned by CaravanEscortMaterializer.
            if (e.isEscort()) continue;
            String key = e.armyId() + ":" + e.projectionSlot();
            if (!wanted.contains(key) || !seen.add(key)) e.dematerialize();
        }
        for (MilitaryProjection p : desired) {
            if (MilitaryUnitIndex.forSlot(p.armyId(), p.slot()) != null) continue;
            Formation formation = formationFor(data, p);
            int count = (int) desired.stream().filter(x -> x.armyId() == p.armyId()).count();
            SimPosition pos = formationPosition(data.state().seed(), p, formation, count);
            int x = (int) Math.floor(pos.x()), z = (int) Math.floor(pos.z());
            BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.hasChunkAt(probe)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            MilitaryUnitEntity e = ModEntities.MILITARY_UNIT.get().create(level);
            if (e == null) continue;
            e.initializeProjection(p.armyId(), p.factionId(), p.slot(), p.unitClass(), p.representedPersonnel());
            float yaw = formationYaw(formation, p.slot());
            e.moveTo(x + .5, y, z + .5, yaw, 0);
            if (level.noCollision(e)) level.addFreshEntity(e);
        }
    }

    enum Formation { COLUMN, LINE, GUARD }

    private static Formation formationFor(LivingRealmsSavedData data, MilitaryProjection p) {
        boolean sieging = data.state().sieges().stream()
                .anyMatch(s -> s.active() && (s.attackerFactionId() == p.factionId() || s.defenderFactionId() == p.factionId())
                        && data.state().findSettlement(s.settlementId()).map(set -> set.position().distanceTo(p.armyPosition()) < 160).orElse(false));
        if (sieging) return Formation.LINE;
        Army army = data.state().findFaction(p.factionId()).flatMap(f -> f.armies().stream().filter(a -> a.id() == p.armyId()).findFirst()).orElse(null);
        if (army != null && army.supply() < .35) return Formation.GUARD;
        boolean garrison = data.state().findFaction(p.factionId()).map(f -> f.settlements().stream()
                .anyMatch(s -> s.position().distanceTo(p.armyPosition()) < 80)).orElse(false);
        if (garrison && army != null && army.morale() > .55) return Formation.GUARD;
        return Formation.COLUMN;
    }

    private static SimPosition formationPosition(long seed, MilitaryProjection p, Formation formation, int armyCount) {
        int n = Math.max(1, armyCount);
        int slot = p.slot();
        double baseX = p.armyPosition().x();
        double baseZ = p.armyPosition().z();
        DeterministicRng r = new DeterministicRng(seed ^ p.armyId() * 0x9E3779B97F4A7C15L);
        double facing = r.between(0, Math.PI * 2);
        double fx = Math.cos(facing), fz = Math.sin(facing);
        double rx = -fz, rz = fx;
        return switch (formation) {
            case COLUMN -> {
                double along = (slot - (n - 1) / 2.0) * 2.4;
                double side = ((slot % 2) * 2 - 1) * (0.9 + classSpacing(p.unitClass()) * 0.2);
                yield new SimPosition(baseX + fx * along + rx * side, baseZ + fz * along + rz * side);
            }
            case LINE -> {
                int rows = Math.max(1, (int) Math.ceil(n / 8.0));
                int cols = Math.max(1, (int) Math.ceil(n / (double) rows));
                int row = slot / cols;
                int col = slot % cols;
                double along = (col - (cols - 1) / 2.0) * 2.6;
                double depth = row * -2.8;
                yield new SimPosition(baseX + rx * along + fx * depth, baseZ + rz * along + fz * depth);
            }
            case GUARD -> {
                double ang = (Math.PI * 2 * slot) / n + r.between(0, 0.05);
                double rad = 6 + classSpacing(p.unitClass()) + (slot % 3);
                yield new SimPosition(baseX + Math.cos(ang) * rad, baseZ + Math.sin(ang) * rad);
            }
        };
    }

    private static float formationYaw(Formation formation, int slot) {
        return switch (formation) {
            case COLUMN -> (slot * 7f) % 360f;
            case LINE -> 90f + (slot % 3);
            case GUARD -> (slot * 45f) % 360f;
        };
    }

    private static double classSpacing(MilitaryUnitClass unitClass) {
        return switch (unitClass) {
            case CAVALRY -> 1.4;
            case ARTILLERY, ARMOR -> 2.0;
            default -> 1.0;
        };
    }
}
