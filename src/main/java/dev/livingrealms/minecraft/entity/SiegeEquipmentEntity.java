package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.sim.military.SiegeEquipmentKind;
import dev.livingrealms.sim.military.SiegeState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Loaded projection of SiegeState ram/ladder/artillery — never a decorative prop without siege authority. */
public final class SiegeEquipmentEntity extends PathfinderMob {
    private static final EntityDataAccessor<Long> SIEGE_ID=SynchedEntityData.defineId(SiegeEquipmentEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FACTION_ID=SynchedEntityData.defineId(SiegeEquipmentEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SLOT=SynchedEntityData.defineId(SiegeEquipmentEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> KIND=SynchedEntityData.defineId(SiegeEquipmentEntity.class,EntityDataSerializers.STRING);
    private boolean dematerializing;

    public SiegeEquipmentEntity(EntityType<? extends SiegeEquipmentEntity> type,Level level){super(type,level);setPersistenceRequired();setNoAi(true);}

    public static AttributeSupplier.Builder createAttributes(){
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,40)
                .add(Attributes.MOVEMENT_SPEED,0)
                .add(Attributes.FOLLOW_RANGE,16)
                .add(Attributes.KNOCKBACK_RESISTANCE,1)
                .add(Attributes.ARMOR,8);
    }

    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,10));
        goalSelector.addGoal(8,new RandomLookAroundGoal(this));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        super.defineSynchedData(b);
        b.define(SIEGE_ID,0L);b.define(FACTION_ID,0L);b.define(SLOT,-1);b.define(KIND,SiegeEquipmentKind.RAM.name());
    }

    public void initializeProjection(long siegeId,long factionId,int slot,SiegeEquipmentKind kind){
        if(siegeId<=0||factionId<=0||slot<0||kind==null)throw new IllegalArgumentException("siege equipment projection");
        entityData.set(SIEGE_ID,siegeId);entityData.set(FACTION_ID,factionId);entityData.set(SLOT,slot);entityData.set(KIND,kind.name());
        setCustomName(Component.literal(label(kind)));setCustomNameVisible(false);
    }

    public long siegeId(){return entityData.get(SIEGE_ID);} public long factionId(){return entityData.get(FACTION_ID);}
    public int projectionSlot(){return entityData.get(SLOT);}
    public SiegeEquipmentKind kind(){try{return SiegeEquipmentKind.valueOf(entityData.get(KIND));}catch(IllegalArgumentException e){return SiegeEquipmentKind.RAM;}}
    public boolean isDematerializing(){return dematerializing;}
    public void dematerialize(){dematerializing=true;discard();}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide()||tickCount%20!=0||!(level() instanceof ServerLevel sl)||siegeId()<=0)return;
        SiegeState siege=SimulationRuntime.data(sl.getServer()).state().sieges().stream()
                .filter(s->s.id()==siegeId()).findFirst().orElse(null);
        if(siege==null||!siege.active()||!equipmentStillProvisioned(siege))dematerialize();
    }

    private boolean equipmentStillProvisioned(SiegeState siege){
        return switch(kind()){
            case RAM -> siege.rams()>0;
            case LADDER -> siege.ladders()>0;
            case ARTILLERY -> siege.artilleryPieces()>0;
        };
    }

    private static String label(SiegeEquipmentKind kind){
        return switch(kind){
            case RAM -> "Battering Ram";
            case LADDER -> "Siege Ladder";
            case ARTILLERY -> "Siege Artillery";
        };
    }

    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        tag.putLong("LivingRealmsSiege",siegeId());
        tag.putLong("LivingRealmsFaction",factionId());
        tag.putInt("LivingRealmsSlot",projectionSlot());
        tag.putString("LivingRealmsKind",kind().name());
        tag.putBoolean("LivingRealmsDematerializing",dematerializing);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        entityData.set(SIEGE_ID,tag.getLong("LivingRealmsSiege"));
        entityData.set(FACTION_ID,tag.getLong("LivingRealmsFaction"));
        entityData.set(SLOT,tag.getInt("LivingRealmsSlot"));
        entityData.set(KIND,tag.getString("LivingRealmsKind"));
        dematerializing=tag.getBoolean("LivingRealmsDematerializing");
    }
}
