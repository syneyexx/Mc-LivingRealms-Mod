package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.sim.faction.RelationStatus;
import dev.livingrealms.sim.naval.ShipClass;
import java.util.Comparator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Physical surface-ship projection of a canonical strategic Fleet. */
public final class LivingRealmsShipEntity extends PathfinderMob {
    private static final EntityDataAccessor<Long> FLEET_ID=SynchedEntityData.defineId(LivingRealmsShipEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FACTION_ID=SynchedEntityData.defineId(LivingRealmsShipEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SLOT=SynchedEntityData.defineId(LivingRealmsShipEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REPRESENTED=SynchedEntityData.defineId(LivingRealmsShipEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SHIP_CLASS=SynchedEntityData.defineId(LivingRealmsShipEntity.class,EntityDataSerializers.STRING);
    private boolean dematerializing;
    private boolean lossReported;

    public LivingRealmsShipEntity(EntityType<? extends LivingRealmsShipEntity> type,Level level){super(type,level);setPersistenceRequired();setNoGravity(true);}
    public static AttributeSupplier.Builder createAttributes(){return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH,70).add(Attributes.FOLLOW_RANGE,128).add(Attributes.ATTACK_DAMAGE,12).add(Attributes.ARMOR,8).add(Attributes.MOVEMENT_SPEED,.25);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(FLEET_ID,0L);b.define(FACTION_ID,0L);b.define(SLOT,-1);b.define(REPRESENTED,1);b.define(SHIP_CLASS,ShipClass.PATROL_BOAT.name());}

    public void initializeProjection(long fleet,long faction,int slot,ShipClass cls,int represented){if(fleet<=0||faction<=0||slot<0||cls==null||represented<1)throw new IllegalArgumentException("ship projection");entityData.set(FLEET_ID,fleet);entityData.set(FACTION_ID,faction);entityData.set(SLOT,slot);entityData.set(SHIP_CLASS,cls.name());entityData.set(REPRESENTED,represented);applyClass();}
    public long fleetId(){return entityData.get(FLEET_ID);} public long factionId(){return entityData.get(FACTION_ID);} public int projectionSlot(){return entityData.get(SLOT);} public int representedShips(){return Math.max(1,entityData.get(REPRESENTED));}
    public ShipClass shipClass(){try{return ShipClass.valueOf(entityData.get(SHIP_CLASS));}catch(IllegalArgumentException e){return ShipClass.PATROL_BOAT;}}
    public boolean isDematerializing(){return dematerializing;} public boolean lossReported(){return lossReported;} public void markLossReported(){lossReported=true;} public void dematerialize(){dematerializing=true;discard();}

    @Override public void tick(){
        super.tick();setNoGravity(true);fallDistance=0;if(level().isClientSide()||!(level() instanceof ServerLevel sl))return;
        var state=SimulationRuntime.data(sl.getServer()).state();var fleet=state.findFleet(fleetId()).orElse(null);if(fleet==null||fleet.destroyed()){dematerialize();return;}
        double targetY=Math.max(sl.getSeaLevel()+1.0,getY());Vec3 destination=new Vec3(fleet.position().x(),targetY,fleet.position().z());Vec3 delta=destination.subtract(position());
        if(delta.horizontalDistanceSqr()>9){double speed=.20+shipClass().speed()/90.0;Vec3 horizontal=new Vec3(delta.x,0,delta.z).normalize().scale(speed);setDeltaMovement(horizontal.x,(targetY-getY())*.08,horizontal.z);setYRot((float)(Math.atan2(-delta.x,delta.z)*180/Math.PI));}else setDeltaMovement(getDeltaMovement().scale(.8));
        if(tickCount%30==0){LivingRealmsShipEntity enemy=ShipProjectionIndex.loaded().stream().filter(e->e!=this&&e.isAlive()&&hostile(state,e.factionId())&&distanceToSqr(e)<110*110).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);if(enemy!=null&&distanceToSqr(enemy)<60*60)enemy.hurt(damageSources().mobAttack(this),(float)(4+shipClass().attack()*.12));}
    }

    private boolean hostile(dev.livingrealms.sim.world.SimulationState state,long other){return state.findFaction(factionId()).map(f->{var r=f.relations().get(other);return r!=null&&r.status()==RelationStatus.WAR;}).orElse(false);}
    private void applyClass(){double hp=45+shipClass().defense()*1.4+shipClass().attack()*.35;setBase(Attributes.MAX_HEALTH,hp);setBase(Attributes.ARMOR,4+shipClass().defense()*.08);setBase(Attributes.ATTACK_DAMAGE,5+shipClass().attack()*.1);setHealth(getMaxHealth());setCustomName(Component.literal(shipClass().name().toLowerCase().replace('_',' ')));setCustomNameVisible(false);}
    private void setBase(net.minecraft.core.Holder<Attribute> attribute,double value){AttributeInstance instance=getAttribute(attribute);if(instance!=null)instance.setBaseValue(value);}

    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putLong("LRFleet",fleetId());t.putLong("LRFaction",factionId());t.putInt("LRSlot",projectionSlot());t.putInt("LRRepresented",representedShips());t.putString("LRShipClass",shipClass().name());t.putBoolean("LRDematerializing",dematerializing);t.putBoolean("LRLossReported",lossReported);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);entityData.set(FLEET_ID,t.getLong("LRFleet"));entityData.set(FACTION_ID,t.getLong("LRFaction"));entityData.set(SLOT,t.getInt("LRSlot"));entityData.set(REPRESENTED,Math.max(1,t.getInt("LRRepresented")));entityData.set(SHIP_CLASS,t.getString("LRShipClass"));dematerializing=t.getBoolean("LRDematerializing");lossReported=t.getBoolean("LRLossReported");setNoGravity(true);applyClass();}
}
