package dev.livingrealms.minecraft.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Cheap regional-tier silhouette: no AI, pathfinding, or collision cost.
 * Represents armies, caravans, herds, settlement bustle, sails, and migration columns
 * between physical and regional LOD radii.
 */
public final class RegionalImpostorEntity extends Entity {
    public enum Kind { MILITARY_BANNER, HERD, CARAVAN_DUST, SETTLEMENT_BUSTLE, SAIL, MIGRATION }

    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(RegionalImpostorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> CANONICAL_ID = SynchedEntityData.defineId(RegionalImpostorEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(RegionalImpostorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(RegionalImpostorEntity.class, EntityDataSerializers.INT);
    private boolean dematerializing;

    public RegionalImpostorEntity(EntityType<? extends RegionalImpostorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public void initialize(Kind kind, long canonicalId, int slot, int rgb) {
        if (kind == null || canonicalId == 0 || slot < 0) throw new IllegalArgumentException("impostor");
        entityData.set(KIND, kind.name());
        entityData.set(CANONICAL_ID, canonicalId);
        entityData.set(SLOT, slot);
        entityData.set(COLOR, rgb);
        setCustomName(Component.literal(kind.name().toLowerCase().replace('_', ' ')));
        setCustomNameVisible(false);
    }

    public Kind kind() {
        try {
            return Kind.valueOf(entityData.get(KIND));
        } catch (IllegalArgumentException e) {
            return Kind.SETTLEMENT_BUSTLE;
        }
    }

    public long canonicalId() { return entityData.get(CANONICAL_ID); }
    public int projectionSlot() { return entityData.get(SLOT); }
    public int colorRgb() { return entityData.get(COLOR); }
    public String projectionKey() { return kind().name() + ":" + canonicalId() + ":" + projectionSlot(); }
    public boolean isDematerializing() { return dematerializing; }
    public void dematerialize() { dematerializing = true; discard(); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, Kind.SETTLEMENT_BUSTLE.name());
        builder.define(CANONICAL_ID, 0L);
        builder.define(SLOT, 0);
        builder.define(COLOR, 0xAAAAAA);
    }

    @Override
    public void tick() {
        super.tick();
        // Idle silhouette — no AI. Occasional client-facing nameplate when a player is very close.
        if (!level().isClientSide() && tickCount % 80 == 0) {
            setCustomNameVisible(level().getNearestPlayer(this, 48) != null);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getString("LRKind"));
        entityData.set(CANONICAL_ID, tag.getLong("LRId"));
        entityData.set(SLOT, tag.getInt("LRSlot"));
        entityData.set(COLOR, tag.getInt("LRColor"));
        dematerializing = tag.getBoolean("LRDematerializing");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("LRKind", kind().name());
        tag.putLong("LRId", canonicalId());
        tag.putInt("LRSlot", projectionSlot());
        tag.putInt("LRColor", colorRgb());
        tag.putBoolean("LRDematerializing", dematerializing);
    }
}
