package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.transport.RouteProjectionPlanner;
import dev.livingrealms.sim.transport.TransportNetworkEngine;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Loaded projection of a persistent strategic TradeShipment. Canonical shipment position is authority. */
public final class TradeCaravanEntity extends PathfinderMob {
    private static final EntityDataAccessor<Long> SHIPMENT_ID = SynchedEntityData.defineId(TradeCaravanEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> CARGO_UNITS = SynchedEntityData.defineId(TradeCaravanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WAGON_MODE = SynchedEntityData.defineId(TradeCaravanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double SOFT_RESYNC_BLOCKS = 64;
    private static final double HARD_RESYNC_BLOCKS = 140;
    private boolean dematerializing;
    private boolean lossReported;

    public TradeCaravanEntity(EntityType<? extends TradeCaravanEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, .26)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.ARMOR, 2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHIPMENT_ID, 0L);
        builder.define(CARGO_UNITS, 0);
        builder.define(WAGON_MODE, false);
    }

    public void initializeProjection(TradeShipment shipment) {
        this.entityData.set(SHIPMENT_ID, shipment.id());
        refreshPresentation(shipment);
    }

    private void refreshPresentation(TradeShipment shipment) {
        int units = (int) Math.round(Math.max(1, shipment.amount()));
        this.entityData.set(CARGO_UNITS, units);
        boolean wagon = units >= 40 || shipment.value() >= 180 || shipment.escortStrength() > .55;
        this.entityData.set(WAGON_MODE, wagon);
        String form = wagon ? "Wagon" : "Pack train";
        String escort = shipment.escortStrength() > .55 ? " • escorted" : shipment.escortStrength() > .3 ? " • light escort" : "";
        String loss = shipment.lossState() == TradeShipment.LossState.PARTIAL ? " • damaged cargo" : "";
        this.setCustomName(Component.literal(form + " • " + shipment.resource().name().toLowerCase() + " x" + units + escort + loss));
        this.setCustomNameVisible(false);
    }

    public long shipmentId() { return entityData.get(SHIPMENT_ID); }
    public int cargoUnits() { return entityData.get(CARGO_UNITS); }
    public boolean wagonMode() { return entityData.get(WAGON_MODE); }
    public boolean isDematerializing() { return dematerializing; }
    public boolean lossReported() { return lossReported; }
    public void markLossReported() { lossReported = true; }
    public void dematerialize() { dematerializing = true; discard(); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || tickCount % 20 != 0 || !(level() instanceof ServerLevel serverLevel) || shipmentId() <= 0) return;
        SimulationState state = SimulationRuntime.data(serverLevel.getServer()).state();
        TradeShipment shipment = state.findShipment(shipmentId()).orElse(null);
        if (shipment == null || shipment.arrived() || shipment.lossState() == TradeShipment.LossState.TOTAL) {
            dematerialize();
            return;
        }
        refreshPresentation(shipment);
        SimPosition canonical = shipment.position();
        double dx = canonical.x() - getX(), dz = canonical.z() - getZ();
        double drift = Math.hypot(dx, dz);
        // Soft-resync toward canonical without jittery every-tick teleport.
        if (drift > HARD_RESYNC_BLOCKS) {
            moveTo(canonical.x(), getY(), canonical.z(), getYRot(), getXRot());
            getNavigation().stop();
        } else if (drift > SOFT_RESYNC_BLOCKS) {
            getNavigation().moveTo(canonical.x(), getY(), canonical.z(), 1.15);
        } else {
            SimPosition steer = roadSteerPoint(state, shipment, canonical);
            Vec3 delta = new Vec3(steer.x() - getX(), 0, steer.z() - getZ());
            if (delta.lengthSqr() > 9) {
                Vec3 step = delta.normalize().scale(Math.min(20, Math.sqrt(delta.lengthSqr())));
                getNavigation().moveTo(getX() + step.x, getY(), getZ() + step.z, 1.0);
            }
        }
        // Travel presence: dust + occasional cart sound; nameplate when a player is close.
        boolean nearPlayer = serverLevel.players().stream().anyMatch(p -> distanceToSqr(p) < 28 * 28);
        setCustomNameVisible(nearPlayer);
        if (nearPlayer && tickCount % 40 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + .2, getZ(), 2, .35, .05, .35, .01);
            if (random.nextFloat() < .35f) {
                serverLevel.playSound(null, blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, .35f, .9f + random.nextFloat() * .2f);
            }
        }
        if (shipment.lossState() == TradeShipment.LossState.PARTIAL && tickCount % 60 == 0) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY() + .6, getZ(), 3, .2, .2, .2, .01);
        }
    }

    /**
     * Prefer the next corridor waypoint along the shipment's transport route when available;
     * otherwise steer toward destination with a soft pull through the canonical position.
     */
    private static SimPosition roadSteerPoint(SimulationState state, TradeShipment shipment, SimPosition canonical) {
        TransportRoute route = null;
        if (shipment.routeId() > 0) {
            route = state.routes().stream().filter(r -> r.id() == shipment.routeId()).findFirst().orElse(null);
        }
        if (route == null) {
            route = TransportNetworkEngine.bestRoute(state, shipment.sellerFactionId(),
                    shipment.originSettlementId(), shipment.destinationSettlementId()).orElse(null);
        }
        if (route != null && route.operational()) {
            List<RouteProjectionPlanner.RoutePoint> points = RouteProjectionPlanner.plan(
                    route, shipment.origin(), shipment.destination(), List.of(canonical), 96, 24);
            RouteProjectionPlanner.RoutePoint best = null;
            double bestAhead = Double.POSITIVE_INFINITY;
            for (RouteProjectionPlanner.RoutePoint p : points) {
                double along = Math.hypot(p.x() - canonical.x(), p.z() - canonical.z());
                double toDest = Math.hypot(p.x() - shipment.destination().x(), p.z() - shipment.destination().z());
                double fromCanonToDest = canonical.distanceTo(shipment.destination());
                if (toDest < fromCanonToDest - 4 && along < bestAhead) {
                    bestAhead = along;
                    best = p;
                }
            }
            if (best != null) return new SimPosition(best.x(), best.z());
        }
        // Blend destination vector with canonical authority so pathing cannot race ahead of truth.
        return new SimPosition(
                canonical.x() * .35 + shipment.destination().x() * .65,
                canonical.z() * .35 + shipment.destination().z() * .65);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("LivingRealmsShipment", shipmentId());
        tag.putInt("LivingRealmsCargo", cargoUnits());
        tag.putBoolean("LivingRealmsWagon", wagonMode());
        tag.putBoolean("LivingRealmsDematerializing", dematerializing);
        tag.putBoolean("LivingRealmsLossReported", lossReported);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SHIPMENT_ID, tag.getLong("LivingRealmsShipment"));
        entityData.set(CARGO_UNITS, tag.getInt("LivingRealmsCargo"));
        entityData.set(WAGON_MODE, tag.getBoolean("LivingRealmsWagon"));
        dematerializing = tag.getBoolean("LivingRealmsDematerializing");
        lossReported = tag.getBoolean("LivingRealmsLossReported");
    }
}
