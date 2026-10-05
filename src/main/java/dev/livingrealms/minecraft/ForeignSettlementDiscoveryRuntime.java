package dev.livingrealms.minecraft;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.ForeignAdoptionClassifier;
import dev.livingrealms.sim.world.OutlyingSite;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Adopts loaded vanilla/modded villager settlements into canonical Living Realms state.
 * Respects the 2000-block settlement spacing rule via {@link ForeignAdoptionClassifier}.
 */
public final class ForeignSettlementDiscoveryRuntime {
    private static final double SEARCH_RADIUS = 150.0D;
    private ForeignSettlementDiscoveryRuntime() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        for (var player : level.players()) {
            List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                    player.getBoundingBox().inflate(SEARCH_RADIUS), Villager::isAlive);
            if (villagers.size() < 2) continue;
            List<List<Villager>> clusters = cluster(villagers);
            for (List<Villager> c : clusters) if (c.size() >= 2) adopt(level, data, c);
        }
    }

    private static List<List<Villager>> cluster(List<Villager> villagers) {
        List<List<Villager>> out = new ArrayList<>();
        for (Villager villager : villagers) {
            List<Villager> found = null;
            for (List<Villager> c : out)
                if (c.stream().anyMatch(v -> v.distanceToSqr(villager) <= 96.0D * 96.0D)) { found = c; break; }
            if (found == null) { found = new ArrayList<>(); out.add(found); }
            found.add(villager);
        }
        return out;
    }

    private static void adopt(ServerLevel level, LivingRealmsSavedData data, List<Villager> cluster) {
        double x = cluster.stream().mapToDouble(Villager::getX).average().orElse(0);
        double z = cluster.stream().mapToDouble(Villager::getZ).average().orElse(0);
        SimPosition pos = new SimPosition(x, z);
        int pop = Math.max(110, cluster.size() * 55);
        int housing = (int) Math.ceil(pop * 1.18D);
        String biome = level.getBiome(net.minecraft.core.BlockPos.containing(x, level.getSeaLevel(), z))
                .unwrapKey().map(key -> key.location().getPath()).orElse("frontier");
        String name = generatedName(biome, (int) Math.floor(x / 16.0), (int) Math.floor(z / 16.0));

        var result = ForeignAdoptionClassifier.classifyAndAdopt(data.state(), pos, name, pop, housing,
                OutlyingSite.Type.FOREIGN_HAMLET);
        if (result.outcome() == ForeignAdoptionClassifier.Outcome.NEW_SETTLEMENT && result.settlement() != null) {
            Faction owner = data.state().findSettlementOwner(result.settlement().id()).orElse(null);
            if (owner != null) {
                ForeignSettlementBootstrap.preserveExistingInfrastructure(data.state(), owner, result.settlement());
                result.settlement().markConstructionCompleted("foreign:protectorate");
                result.settlement().adjustUnrest(-.02);
            }
            data.setDirty();
        } else if (result.outcome() == ForeignAdoptionClassifier.Outcome.OUTLYING_SITE) {
            data.setDirty();
        }
        // BOUND_EXISTING / IDEMPOTENT_SITE: no new settlement, footprint stays where it is.
    }

    private static String generatedName(String biome, int chunkX, int chunkZ) {
        String[] a = {"Alder","Briar","Cedar","Dawn","Elm","Fox","Green","High","Iron","Juniper","Kings","Lark","Moss","Oak","Pine","River","Stone","Thorn","Willow","Wolf"};
        String[] b = {"brook","cross","dale","field","ford","gate","haven","hollow","mere","stead","ton","vale","watch","wick","wood"};
        int h = 31 * chunkX + chunkZ * 131 + biome.toLowerCase(Locale.ROOT).hashCode();
        return a[Math.floorMod(h, a.length)] + b[Math.floorMod(h >>> 8, b.length)];
    }
}
