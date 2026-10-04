package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.sim.logistics.TradeShipment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.phys.Vec3;

/** Loaded projection of a persistent strategic TradeShipment. */
public final class TradeCaravanEntity extends PathfinderMob {
    private static final EntityDataAccessor<Long> SHIPMENT_ID=SynchedEntityData.defineId(TradeCaravanEntity.class,EntityDataSerializers.LONG);
    private boolean dematerializing;
    private boolean lossReported;

    public TradeCaravanEntity(EntityType<? extends TradeCaravanEntity> type,Level level){super(type,level);setPersistenceRequired();}

    public static AttributeSupplier.Builder createAttributes(){return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH,30).add(Attributes.MOVEMENT_SPEED,.26).add(Attributes.FOLLOW_RANGE,24).add(Attributes.ARMOR,2);}

    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(7,new RandomLookAroundGoal(this));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(SHIPMENT_ID,0L);}

    public void initializeProjection(TradeShipment shipment){
        this.entityData.set(SHIPMENT_ID,shipment.id());
        String escort=shipment.escortStrength()>.55?" • escorted":shipment.escortStrength()>.3?" • light escort":"";
        String loss=shipment.lossState()==TradeShipment.LossState.PARTIAL?" • damaged cargo":"";
        this.setCustomName(Component.literal("Trade Caravan • "+shipment.resource().name().toLowerCase()+" x"+(int)Math.round(shipment.amount())+escort+loss));
        this.setCustomNameVisible(false);
    }
    public long shipmentId(){return entityData.get(SHIPMENT_ID);} public boolean isDematerializing(){return dematerializing;} public boolean lossReported(){return lossReported;} public void markLossReported(){lossReported=true;}
    public void dematerialize(){dematerializing=true;discard();}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide()||tickCount%20!=0||!(level() instanceof ServerLevel serverLevel)||shipmentId()<=0)return;
        TradeShipment shipment=SimulationRuntime.data(serverLevel.getServer()).state().findShipment(shipmentId()).orElse(null);
        if(shipment==null){dematerialize();return;}
        Vec3 delta=new Vec3(shipment.destination().x()-getX(),0,shipment.destination().z()-getZ());
        if(delta.lengthSqr()>9){Vec3 step=delta.normalize().scale(Math.min(20,Math.sqrt(delta.lengthSqr())));getNavigation().moveTo(getX()+step.x,getY(),getZ()+step.z,1.0);}
    }

    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putLong("LivingRealmsShipment",shipmentId());tag.putBoolean("LivingRealmsDematerializing",dematerializing);tag.putBoolean("LivingRealmsLossReported",lossReported);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(SHIPMENT_ID,tag.getLong("LivingRealmsShipment"));dematerializing=tag.getBoolean("LivingRealmsDematerializing");lossReported=tag.getBoolean("LivingRealmsLossReported");}
}
