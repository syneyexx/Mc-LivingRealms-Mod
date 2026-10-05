package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.civilian.CitizenIdentity;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.config.RuntimeProjectionPolicy;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.CitizenJourney;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.projection.CitizenJourneyMaterializationPlanner;
import dev.livingrealms.sim.world.projection.CitizenJourneyProjection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Loaded-chunk physical projection of active {@link CitizenJourney} records.
 * Dematerializes when players leave without aborting the canonical journey;
 * only real entity death maps back via {@code recordPhysicalJourneyDeath}.
 */
public final class CitizenJourneyMaterializer {
    private CitizenJourneyMaterializer() {}

    public static void tick(ServerLevel level, LivingRealmsSavedData data) {
        var state = data.state();
        List<SimPosition> players = level.players().stream()
                .map(p -> new SimPosition(p.getX(), p.getZ()))
                .toList();
        CitizenJourneyMaterializationPlanner planner = new CitizenJourneyMaterializationPlanner(
                RuntimeProjectionPolicy.journeyRadiusBlocks(state.config()),
                RuntimeProjectionPolicy.journeyBudget(state.config()));
        List<CitizenJourneyProjection> desired = planner.plan(state, players);

        List<CitizenJourneyMaterializationPlanner.Snapshot> actual = FactionCitizenIndex.loadedJourneys().stream()
                .map(e -> new CitizenJourneyMaterializationPlanner.Snapshot(e.getUUID().toString(), e.journeyId()))
                .toList();
        Set<Long> activeIds = state.citizenJourneys().stream()
                .filter(CitizenJourney::active)
                .map(CitizenJourney::id)
                .collect(Collectors.toCollection(HashSet::new));
        CitizenJourneyMaterializationPlanner.Plan delta = planner.reconcile(desired, actual, activeIds);

        Map<String, FactionCitizenEntity> byKey = FactionCitizenIndex.loadedJourneys().stream()
                .collect(Collectors.toMap(e -> e.getUUID().toString(), e -> e, (a, b) -> a));
        for (CitizenJourneyMaterializationPlanner.Despawn removal : delta.despawns()) {
            // OUTSIDE_PHYSICAL_RADIUS / JOURNEY_FINISHED / DUPLICATE — never mint journey death.
            FactionCitizenEntity entity = byKey.get(removal.entityKey());
            if (entity != null && !entity.isRemoved()) entity.dematerialize();
        }

        Map<Long, CitizenJourneyProjection> wantedById = desired.stream()
                .collect(Collectors.toMap(CitizenJourneyProjection::journeyId, p -> p, (a, b) -> a));
        for (CitizenJourneyMaterializationPlanner.Spawn spawn : delta.spawns()) {
            if (FactionCitizenIndex.forJourney(spawn.journeyId()) != null) continue;
            CitizenJourney journey = state.findCitizenJourney(spawn.journeyId()).orElse(null);
            if (journey == null || !journey.active()) continue;
            CitizenJourneyProjection projection = wantedById.get(spawn.journeyId());
            SimPosition pos = projection != null ? projection.position() : spawn.position();
            int x = (int) Math.floor(pos.x()), z = (int) Math.floor(pos.z());
            BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.hasChunkAt(probe)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            FactionCitizenEntity entity = ModEntities.CITIZEN.get().create(level);
            if (entity == null) continue;
            CitizenRole role = roleFor(journey.purpose());
            String name;
            int skin;
            if (journey.citizenId() > 0) {
                SocialCitizen social = state.findSocialCitizen(journey.citizenId()).orElse(null);
                if (social != null && social.alive()) {
                    name = social.name();
                    skin = social.skinVariant();
                } else {
                    CitizenIdentity identity = CitizenIdentity.forProjection(
                            journey.factionId(), journey.originSettlementId(),
                            Math.floorMod((int) journey.id(), 10_000), role);
                    name = identity.name();
                    skin = identity.skinVariant();
                }
            } else {
                CitizenIdentity identity = CitizenIdentity.forProjection(
                        journey.factionId(), journey.originSettlementId(),
                        Math.floorMod((int) journey.id(), 10_000), role);
                name = identity.name();
                skin = identity.skinVariant();
            }
            entity.initializeJourney(journey.id(), journey.factionId(), journey.originSettlementId(),
                    journey.citizenId(), role, name + " • " + journey.purposeLabel(), skin);
            entity.moveTo(x + .5, y, z + .5, level.random.nextFloat() * 360F, 0);
            if (level.noCollision(entity)) level.addFreshEntity(entity);
        }
    }

    static CitizenRole roleFor(CitizenJourney.Purpose purpose) {
        return switch (purpose) {
            case COURIER, TAX_COLLECTOR, DIPLOMAT -> CitizenRole.OFFICIAL;
            case PEDDLER -> CitizenRole.TRADER;
            case PILGRIM -> CitizenRole.PRIEST;
            case PATROL -> CitizenRole.GUARD;
            case SHEPHERD, SEASONAL_WORKER -> CitizenRole.FARMER;
            case HUNTER -> CitizenRole.HUNTER;
            case EXPLORER -> CitizenRole.SCHOLAR;
        };
    }
}
