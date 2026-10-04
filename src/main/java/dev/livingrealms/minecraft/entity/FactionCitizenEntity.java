package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.compat.CompatibleContentRuntime;
import dev.livingrealms.sim.civilian.CitizenIdentity;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilian.CitizenRoutine;
import dev.livingrealms.sim.civilian.CitizenRoutinePlanner;
import dev.livingrealms.sim.law.*;
import dev.livingrealms.sim.ecology.Diet;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.social.SocialCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import java.util.Comparator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Physical representative of aggregate settlement population. */
public final class FactionCitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<Long> FACTION_ID=SynchedEntityData.defineId(FactionCitizenEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> SETTLEMENT_ID=SynchedEntityData.defineId(FactionCitizenEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> CITIZEN_ID=SynchedEntityData.defineId(FactionCitizenEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SLOT=SynchedEntityData.defineId(FactionCitizenEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> ROLE=SynchedEntityData.defineId(FactionCitizenEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PERSON_NAME=SynchedEntityData.defineId(FactionCitizenEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SKIN_VARIANT=SynchedEntityData.defineId(FactionCitizenEntity.class,EntityDataSerializers.INT);
    private boolean dematerializing;
    private boolean deathReported;
    private long speechUntilTick;

    public FactionCitizenEntity(EntityType<? extends FactionCitizenEntity> type,Level level){super(type,level);setPersistenceRequired();}
    public static AttributeSupplier.Builder createAttributes(){return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.27).add(Attributes.FOLLOW_RANGE,28).add(Attributes.ATTACK_DAMAGE,3).add(Attributes.ARMOR,1);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new MeleeAttackGoal(this,1.1,true));goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.85));goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,10));goalSelector.addGoal(8,new RandomLookAroundGoal(this));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(FACTION_ID,0L);b.define(SETTLEMENT_ID,0L);b.define(CITIZEN_ID,0L);b.define(SLOT,-1);b.define(ROLE,CitizenRole.FARMER.name());b.define(PERSON_NAME,"");b.define(SKIN_VARIANT,0);}

    public void initializeProjection(SocialCitizen citizen){if(citizen==null||!citizen.alive())throw new IllegalArgumentException("social citizen");entityData.set(FACTION_ID,citizen.factionId());entityData.set(SETTLEMENT_ID,citizen.settlementId());entityData.set(CITIZEN_ID,citizen.id());entityData.set(SLOT,citizen.projectionSlot());entityData.set(ROLE,citizen.role().name());entityData.set(PERSON_NAME,citizen.name());entityData.set(SKIN_VARIANT,citizen.skinVariant());applyRole();}
    public long factionId(){return entityData.get(FACTION_ID);}public long settlementId(){return entityData.get(SETTLEMENT_ID);}public long citizenId(){return entityData.get(CITIZEN_ID);}public int projectionSlot(){return entityData.get(SLOT);}public String personName(){return entityData.get(PERSON_NAME);}public int skinVariant(){return entityData.get(SKIN_VARIANT);}public void setSkinVariant(int variant){entityData.set(SKIN_VARIANT,Math.floorMod(variant,12));}public CitizenRole role(){try{return CitizenRole.valueOf(entityData.get(ROLE));}catch(IllegalArgumentException ex){return CitizenRole.FARMER;}}
    public boolean isDematerializing(){return dematerializing;}public boolean deathReported(){return deathReported;}public void markDeathReported(){deathReported=true;}public boolean isSpeaking(){return tickCount<speechUntilTick;}public void speak(String text,int durationTicks){if(text==null||text.isBlank()||durationTicks<=0)return;String line=text.replace('\n',' ').trim();if(line.length()>140)line=line.substring(0,139)+"…";setCustomName(Component.literal(personName().isBlank()?line:personName()+": "+line));setCustomNameVisible(true);speechUntilTick=(long)tickCount+durationTicks;}public void dematerialize(){dematerializing=true;discard();}

    @Override public void tick(){
        super.tick();
        if(!level().isClientSide()&&speechUntilTick>0&&tickCount>=speechUntilTick){speechUntilTick=0;setCustomName(Component.literal(personName().isBlank()?role().name().toLowerCase().replace('_',' '):personName()));setCustomNameVisible(false);}
        if(level().isClientSide()||tickCount%20!=0||!(level() instanceof ServerLevel sl)||factionId()<=0)return;
        var data=SimulationRuntime.data(sl.getServer());
        var state=data.state();
        if(citizenId()<=0&&settlementId()>0&&projectionSlot()>=0){SocialCitizen social=state.ensureSocialCitizen(factionId(),settlementId(),projectionSlot(),role());bindSocialCitizen(social);data.setDirty();}
        SocialCitizen bound=citizenId()>0?state.findSocialCitizen(citizenId()).orElse(null):null;
        boolean inCustody=bound!=null&&state.activeCustody("citizen:"+bound.id(),bound.factionId()).isPresent();
        if(inCustody){setTarget(null);getNavigation().stop();if(!isNoAi())setNoAi(true);return;}
        if(isNoAi())setNoAi(false);
        if(role()!=CitizenRole.GUARD){
            if(getTarget() instanceof Player)setTarget(null);
            if(role()==CitizenRole.HUNTER&&tickCount%20==0)huntWildlife(sl);
            if(role()==CitizenRole.LUMBERJACK&&tickCount%40==0)workLumber(sl,data);
            if(role()==CitizenRole.FARMER&&tickCount%60==0)workFarm(sl,data);
            if(role()==CitizenRole.MINER&&tickCount%80==0)workMine(sl,data);
            if(role()==CitizenRole.FISHER&&tickCount%100==0)workFish(sl,data);
            if(tickCount%40==0)followRoutine(state);
            return;
        }
        Player suspect=sl.getEntitiesOfClass(Player.class,getBoundingBox().inflate(22),Player::isAlive).stream()
                .filter(p->state.lawResponse("player:"+p.getUUID(),factionId()).action()!=EnforcementAction.NONE)
                .max(Comparator.<Player>comparingInt(p->state.lawResponse("player:"+p.getUUID(),factionId()).action().ordinal())
                        .thenComparingDouble(p->-distanceToSqr(p))).orElse(null);
        if(suspect==null){setTarget(null);if(tickCount%40==0)followRoutine(state);return;}
        LawResponse response=state.lawResponse("player:"+suspect.getUUID(),factionId());
        switch(response.action()){
            case NONE -> setTarget(null);
            case QUESTION -> {
                setTarget(null);getNavigation().moveTo(suspect,.85);
                if(tickCount%100==0) suspect.sendSystemMessage(Component.literal("Guard: Halt. You are wanted for questioning in this realm."));
            }
            case DEMAND_FINE -> {
                setTarget(null);getNavigation().moveTo(suspect,.95);
                if(tickCount%100==0) suspect.sendSystemMessage(Component.literal("Guard: Outstanding fine "+Math.round(response.requestedFine())+". Check /livingrealms wanted."));
            }
            case ARREST -> {
                setTarget(null);getNavigation().moveTo(suspect,1.05);
                if(distanceToSqr(suspect)<=4.0D){
                    ArrestOutcome outcome=state.arrestCriminal("player:"+suspect.getUUID(),factionId(),"guard arrest");
                    if(outcome.arrested()){data.setDirty();suspect.sendSystemMessage(Component.literal("You were arrested for "+outcome.sentenceDays()+" simulated day(s)."));getNavigation().stop();}
                }
            }
            case LETHAL_FORCE -> setTarget(suspect);
        }
    }

    private void huntWildlife(ServerLevel level){
        LivingRealmsAnimalEntity prey=level.getEntitiesOfClass(LivingRealmsAnimalEntity.class,getBoundingBox().inflate(30.0D),animal->{
            var species=animal.species();
            if(species==null||!animal.isAlive())return false;
            return species.diet()!=Diet.CARNIVORE&&species.adultMassKg()<=650.0D;
        }).stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(prey!=null)setTarget(prey);
        else if(getTarget() instanceof LivingRealmsAnimalEntity)setTarget(null);
    }

    /**
     * Physical work is visualization only. Canonical economy remains authoritative; loaded chunks
     * must never create extra stockpile production. Workers also must not destroy player/unknown blocks.
     */
    private void workLumber(ServerLevel level,LivingRealmsSavedData data){
        BlockPos site=findAuthoredWorksite(level,data,blockPosition(),8,AuthoredOwnerType.INFRASTRUCTURE,AuthoredOwnerType.SETTLEMENT_STRUCTURE);
        if(site==null){followRoutine(data.state());return;}
        moveOrSwingAt(site,.95);
    }

    private void workFarm(ServerLevel level,LivingRealmsSavedData data){
        BlockPos site=findAuthoredWorksite(level,data,blockPosition(),8,AuthoredOwnerType.SETTLEMENT_STRUCTURE,AuthoredOwnerType.INFRASTRUCTURE);
        if(site==null){followRoutine(data.state());return;}
        moveOrSwingAt(site,.9);
    }

    private void workMine(ServerLevel level,LivingRealmsSavedData data){
        BlockPos site=findAuthoredWorksite(level,data,blockPosition(),8,AuthoredOwnerType.INFRASTRUCTURE);
        if(site==null){followRoutine(data.state());return;}
        moveOrSwingAt(site,.86);
    }

    private void workFish(ServerLevel level,LivingRealmsSavedData data){
        if(!nearWater(level,blockPosition(),7)){followRoutine(data.state());return;}
        // Visual fishing only — no canonical stockpile mutation from loaded-chunk projection.
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private void moveOrSwingAt(BlockPos site,double speed){
        double dx=getX()-(site.getX()+.5),dy=getY()-(site.getY()+.5),dz=getZ()-(site.getZ()+.5);
        if(dx*dx+dy*dy+dz*dz>6.25D){getNavigation().moveTo(site.getX()+.5,site.getY(),site.getZ()+.5,speed);return;}
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private static BlockPos findAuthoredWorksite(ServerLevel level,LivingRealmsSavedData data,BlockPos center,int radius,AuthoredOwnerType... owners){
        if(data==null||owners==null||owners.length==0)return null;
        BlockPos best=null;double bestDistance=Double.POSITIVE_INFINITY;
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-radius,-3,-radius),center.offset(radius,4,radius))){
            AuthoredOwnerType owner=data.authoredBlocks().ownerType(pos.getX(),pos.getY(),pos.getZ());
            if(owner==null)continue;
            boolean match=false;for(AuthoredOwnerType allowed:owners)if(owner==allowed){match=true;break;}
            if(!match)continue;
            double d=pos.distSqr(center);if(d<bestDistance){bestDistance=d;best=pos.immutable();}
        }
        return best;
    }

    private static boolean nearWater(ServerLevel level,BlockPos center,int radius){
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-radius,-2,-radius),center.offset(radius,2,radius)))if(level.getFluidState(pos).is(FluidTags.WATER))return true;
        return false;
    }

    private void followRoutine(dev.livingrealms.sim.world.SimulationState state){
        var faction=state.findFaction(factionId()).orElse(null);
        var settlement=state.findSettlement(settlementId()).orElse(null);
        if(faction==null||settlement==null)return;
        CitizenRoutine routine=CitizenRoutinePlanner.plan(state,faction,settlement,role(),projectionSlot(),(int)Math.floorMod(level().getDayTime(),24000L));
        double dx=getX()-routine.target().x(),dz=getZ()-routine.target().z(),r=routine.arrivalRadius();
        if(dx*dx+dz*dz<=r*r){getNavigation().stop();return;}
        getNavigation().moveTo(routine.target().x(),getY(),routine.target().z(),routine.speed());
    }

    private void bindSocialCitizen(SocialCitizen citizen){entityData.set(CITIZEN_ID,citizen.id());entityData.set(PERSON_NAME,citizen.name());entityData.set(SKIN_VARIANT,citizen.skinVariant());entityData.set(ROLE,citizen.role().name());setCustomName(Component.literal(citizen.name()));}

    private void applyRole(){CitizenRole role=role();double hp=role==CitizenRole.GUARD?28:20,attack=role==CitizenRole.GUARD?5:2,armor=role==CitizenRole.GUARD?4:1;setBase(Attributes.MAX_HEALTH,hp);setBase(Attributes.ATTACK_DAMAGE,attack);setBase(Attributes.ARMOR,armor);setHealth(getMaxHealth());setCustomName(Component.literal(personName().isBlank()?role.name().toLowerCase().replace('_',' '):personName()));setCustomNameVisible(false);if(!level().isClientSide()&&factionId()>0&&projectionSlot()>=0)CompatibleContentRuntime.equipCitizen(this,factionId(),role,projectionSlot());}
    private void setBase(net.minecraft.core.Holder<Attribute> attribute,double value){AttributeInstance i=getAttribute(attribute);if(i!=null)i.setBaseValue(value);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putLong("LivingRealmsFaction",factionId());tag.putLong("LivingRealmsSettlement",settlementId());tag.putLong("LivingRealmsCitizen",citizenId());tag.putInt("LivingRealmsSlot",projectionSlot());tag.putString("LivingRealmsRole",role().name());tag.putString("LivingRealmsName",personName());tag.putInt("LivingRealmsSkin",skinVariant());tag.putBoolean("LivingRealmsDematerializing",dematerializing);tag.putBoolean("LivingRealmsDeathReported",deathReported);tag.putLong("LivingRealmsSpeechRemaining",Math.max(0L,speechUntilTick-tickCount));}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(FACTION_ID,tag.getLong("LivingRealmsFaction"));entityData.set(SETTLEMENT_ID,tag.getLong("LivingRealmsSettlement"));entityData.set(CITIZEN_ID,tag.getLong("LivingRealmsCitizen"));entityData.set(SLOT,tag.getInt("LivingRealmsSlot"));entityData.set(ROLE,tag.getString("LivingRealmsRole"));String restoredName=tag.getString("LivingRealmsName");if(restoredName.isBlank()){CitizenIdentity identity=CitizenIdentity.forProjection(factionId(),settlementId(),Math.max(0,projectionSlot()),role());restoredName=identity.name();entityData.set(SKIN_VARIANT,identity.skinVariant());}else entityData.set(SKIN_VARIANT,Math.floorMod(tag.getInt("LivingRealmsSkin"),12));entityData.set(PERSON_NAME,restoredName);dematerializing=tag.getBoolean("LivingRealmsDematerializing");deathReported=tag.getBoolean("LivingRealmsDeathReported");speechUntilTick=(long)tickCount+Math.max(0L,tag.getLong("LivingRealmsSpeechRemaining"));applyRole();}
}
