package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.compat.CompatibleContentRuntime;
import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.MilitaryUnitClass;
import java.util.Comparator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Tactical representative of an aggregate Army, or a temporary caravan escort projection.
 * Escort projections use a negative army-id namespace ({@code -shipmentId}) so they never collide
 * with real armies and are owned by {@link CaravanEscortMaterializer}.
 */
public final class MilitaryUnitEntity extends PathfinderMob {
    private static final EntityDataAccessor<Long> ARMY_ID = SynchedEntityData.defineId(MilitaryUnitEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FACTION_ID = SynchedEntityData.defineId(MilitaryUnitEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(MilitaryUnitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REPRESENTED = SynchedEntityData.defineId(MilitaryUnitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> UNIT_CLASS = SynchedEntityData.defineId(MilitaryUnitEntity.class, EntityDataSerializers.STRING);
    private boolean dematerializing, lossReported;

    public MilitaryUnitEntity(EntityType<? extends MilitaryUnitEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 28)
                .add(Attributes.MOVEMENT_SPEED, .27)
                .add(Attributes.FOLLOW_RANGE, 38)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ARMOR, 3)
                .add(Attributes.KNOCKBACK_RESISTANCE, .15);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.12, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .65));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ARMY_ID, 0L);
        b.define(FACTION_ID, 0L);
        b.define(SLOT, -1);
        b.define(REPRESENTED, 1);
        b.define(UNIT_CLASS, MilitaryUnitClass.INFANTRY.name());
    }

    public void initializeProjection(long army, long faction, int slot, MilitaryUnitClass unitClass, int represented) {
        if (army <= 0 || faction <= 0 || slot < 0 || unitClass == null || represented <= 0) {
            throw new IllegalArgumentException("military projection");
        }
        entityData.set(ARMY_ID, army);
        entityData.set(FACTION_ID, faction);
        entityData.set(SLOT, slot);
        entityData.set(REPRESENTED, represented);
        entityData.set(UNIT_CLASS, unitClass.name());
        applyClass();
    }

    /**
     * Escort identity: army id stores {@code -shipmentId}. Real armies always use positive ids.
     */
    public void initializeEscortProjection(long shipmentId, long faction, int slot, MilitaryUnitClass unitClass) {
        if (shipmentId <= 0 || faction <= 0 || slot < 0 || unitClass == null) {
            throw new IllegalArgumentException("escort projection");
        }
        entityData.set(ARMY_ID, -shipmentId);
        entityData.set(FACTION_ID, faction);
        entityData.set(SLOT, slot);
        entityData.set(REPRESENTED, 1);
        entityData.set(UNIT_CLASS, unitClass.name());
        applyClass();
        setCustomName(Component.literal("escort"));
        setCustomNameVisible(false);
    }

    public long armyId() { return entityData.get(ARMY_ID); }
    public long factionId() { return entityData.get(FACTION_ID); }
    public int projectionSlot() { return entityData.get(SLOT); }
    public int representedPersonnel() { return entityData.get(REPRESENTED); }
    public boolean isEscort() { return armyId() < 0; }
    public long escortShipmentId() { return isEscort() ? -armyId() : 0L; }

    public MilitaryUnitClass unitClass() {
        try {
            return MilitaryUnitClass.valueOf(entityData.get(UNIT_CLASS));
        } catch (IllegalArgumentException e) {
            return MilitaryUnitClass.INFANTRY;
        }
    }

    public boolean isDematerializing() { return dematerializing; }
    public boolean lossReported() { return lossReported; }
    public void markLossReported() { lossReported = true; }
    public void dematerialize() { dematerializing = true; discard(); }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel sl && !sl.noCollision(this)) {
            int x = getBlockX(), z = getBlockZ(), y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            moveTo(x + .5, y, z + .5, getYRot(), getXRot());
            getNavigation().stop();
        }
        super.tick();
        if (level().isClientSide() || tickCount % 10 != 0 || !(level() instanceof ServerLevel sl)) return;
        var state = SimulationRuntime.data(sl.getServer()).state();
        if (isEscort()) {
            tickEscort(state);
            return;
        }
        var army = state.findArmy(armyId()).orElse(null);
        if (army == null) {
            dematerialize();
            return;
        }
        MilitaryUnitEntity enemy = MilitaryUnitIndex.loaded().stream()
                .filter(e -> e != this && e.isAlive() && !e.isEscort() && hostile(state, e.factionId()))
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (enemy != null && distanceToSqr(enemy) < 38 * 38) setTarget(enemy);
        else if (getTarget() instanceof MilitaryUnitEntity) setTarget(null);
        double dx = army.position().x() - getX(), dz = army.position().z() - getZ();
        if (dx * dx + dz * dz > 80 * 80) getNavigation().moveTo(army.position().x(), getY(), army.position().z(), .9);
    }

    private void tickEscort(dev.livingrealms.sim.world.SimulationState state) {
        TradeShipment shipment = state.findShipment(escortShipmentId()).orElse(null);
        if (shipment == null || shipment.arrived() || shipment.lossState() == TradeShipment.LossState.TOTAL) {
            dematerialize();
            return;
        }
        var pos = shipment.position();
        double dx = pos.x() - getX(), dz = pos.z() - getZ();
        double distSq = dx * dx + dz * dz;
        if (distSq > 10 * 10) getNavigation().moveTo(pos.x(), getY(), pos.z(), 1.05);
        else if (distSq > 4 * 4) getNavigation().moveTo(pos.x(), getY(), pos.z(), .85);
    }

    private boolean hostile(dev.livingrealms.sim.world.SimulationState state, long other) {
        return state.findFaction(factionId()).map(f -> {
            var r = f.relations().get(other);
            return r != null && r.status() == RelationStatus.WAR;
        }).orElse(false);
    }

    private void applyClass() {
        double hp, attack, armor, speed, knock;
        switch (unitClass()) {
            case INFANTRY -> { hp = 24; attack = 5; armor = 3; speed = .28; knock = .1; }
            case CAVALRY -> { hp = 34; attack = 7; armor = 4; speed = .34; knock = .25; }
            case ARTILLERY -> { hp = 42; attack = 9; armor = 5; speed = .18; knock = .45; }
            case ARMOR -> { hp = 80; attack = 12; armor = 12; speed = .22; knock = .8; }
            default -> throw new IllegalStateException();
        }
        setBase(Attributes.MAX_HEALTH, hp);
        setBase(Attributes.ATTACK_DAMAGE, attack);
        setBase(Attributes.ARMOR, armor);
        setBase(Attributes.MOVEMENT_SPEED, speed);
        setBase(Attributes.KNOCKBACK_RESISTANCE, knock);
        setHealth(getMaxHealth());
        if (!isEscort()) {
            setCustomName(Component.literal(unitClass().name().toLowerCase()));
            setCustomNameVisible(false);
        }
        if (!level().isClientSide() && factionId() > 0 && projectionSlot() >= 0) {
            CompatibleContentRuntime.equipMilitary(this, factionId(), unitClass(), projectionSlot());
        }
    }

    private void setBase(net.minecraft.core.Holder<Attribute> a, double v) {
        AttributeInstance i = getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putLong("LRArmy", armyId());
        t.putLong("LRFaction", factionId());
        t.putInt("LRSlot", projectionSlot());
        t.putInt("LRRepresented", representedPersonnel());
        t.putString("LRClass", unitClass().name());
        t.putBoolean("LRDematerializing", dematerializing);
        t.putBoolean("LRLossReported", lossReported);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(ARMY_ID, t.getLong("LRArmy"));
        entityData.set(FACTION_ID, t.getLong("LRFaction"));
        entityData.set(SLOT, t.getInt("LRSlot"));
        entityData.set(REPRESENTED, t.getInt("LRRepresented"));
        entityData.set(UNIT_CLASS, t.getString("LRClass"));
        dematerializing = t.getBoolean("LRDematerializing");
        lossReported = t.getBoolean("LRLossReported");
        applyClass();
    }
}
