package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.sim.law.*;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Physical projection of an open NPC bounty pursuit. The contract remains canonical state. */
public final class BountyHunterEntity extends PathfinderMob {
    private static final EntityDataAccessor<Long> CONTRACT_ID=SynchedEntityData.defineId(BountyHunterEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> ISSUER_FACTION=SynchedEntityData.defineId(BountyHunterEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<String> TARGET_ACTOR=SynchedEntityData.defineId(BountyHunterEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> HUNTER_KEY=SynchedEntityData.defineId(BountyHunterEntity.class,EntityDataSerializers.STRING);
    private boolean dematerializing;

    public BountyHunterEntity(EntityType<? extends BountyHunterEntity> type,Level level){super(type,level);setPersistenceRequired();}
    public static AttributeSupplier.Builder createAttributes(){return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH,32).add(Attributes.MOVEMENT_SPEED,.31).add(Attributes.FOLLOW_RANGE,48).add(Attributes.ATTACK_DAMAGE,7).add(Attributes.ARMOR,5);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new MeleeAttackGoal(this,1.15,true));goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.75));goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,14));goalSelector.addGoal(8,new RandomLookAroundGoal(this));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(CONTRACT_ID,0L);b.define(ISSUER_FACTION,0L);b.define(TARGET_ACTOR,"");b.define(HUNTER_KEY,"");}

    public void initializeProjection(long contractId,long issuerFactionId,String targetActorKey,String hunterKey){
        if(contractId<=0||issuerFactionId<=0||targetActorKey==null||targetActorKey.isBlank()||hunterKey==null||hunterKey.isBlank())throw new IllegalArgumentException("bounty hunter projection");
        entityData.set(CONTRACT_ID,contractId);entityData.set(ISSUER_FACTION,issuerFactionId);entityData.set(TARGET_ACTOR,targetActorKey);entityData.set(HUNTER_KEY,hunterKey);
        setCustomName(Component.literal("Bounty Hunter"));setCustomNameVisible(false);
    }
    public long contractId(){return entityData.get(CONTRACT_ID);}public long issuerFactionId(){return entityData.get(ISSUER_FACTION);}public String targetActorKey(){return entityData.get(TARGET_ACTOR);}public String hunterKey(){return entityData.get(HUNTER_KEY);}public boolean isDematerializing(){return dematerializing;}
    public void dematerialize(){dematerializing=true;discard();}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide()||tickCount%10!=0||!(level() instanceof ServerLevel sl)||contractId()<=0)return;
        var data=SimulationRuntime.data(sl.getServer());var state=data.state();
        BountyContract contract=state.bounties().stream().filter(b->b.id()==contractId()).findFirst().orElse(null);
        if(contract==null||contract.status()==BountyContract.Status.CLAIMED||contract.status()==BountyContract.Status.CANCELLED||
                (contract.status()==BountyContract.Status.ASSIGNED&&!Objects.equals(contract.hunterKey(),hunterKey()))){dematerialize();return;}
        ServerPlayer target=sl.getServer().getPlayerList().getPlayers().stream().filter(Player::isAlive).filter(p->("player:"+p.getUUID()).equals(targetActorKey())).findFirst().orElse(null);
        if(target==null||target.level()!=level()){dematerialize();return;}
        LawResponse response=state.lawResponse(targetActorKey(),issuerFactionId());
        if(response.action()==EnforcementAction.NONE||response.action()==EnforcementAction.QUESTION||response.action()==EnforcementAction.DEMAND_FINE){dematerialize();return;}
        if(response.action()==EnforcementAction.ARREST){
            setTarget(null);getNavigation().moveTo(target,1.18);
            if(distanceToSqr(target)<=4.0D){
                BountyClaim claim=state.captureBountyAlive(contractId(),hunterKey(),"captured by bounty hunter");
                if(claim.claimed()){data.setDirty();target.sendSystemMessage(Component.literal("A bounty hunter captured you for faction "+issuerFactionId()+". Reward paid: "+Math.round(claim.paidReward())));dematerialize();}
            }
        }else if(response.action()==EnforcementAction.LETHAL_FORCE){setTarget(target);}
    }

    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putLong("LivingRealmsContract",contractId());tag.putLong("LivingRealmsIssuerFaction",issuerFactionId());tag.putString("LivingRealmsTargetActor",targetActorKey());tag.putString("LivingRealmsHunterKey",hunterKey());tag.putBoolean("LivingRealmsDematerializing",dematerializing);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(CONTRACT_ID,tag.getLong("LivingRealmsContract"));entityData.set(ISSUER_FACTION,tag.getLong("LivingRealmsIssuerFaction"));entityData.set(TARGET_ACTOR,tag.getString("LivingRealmsTargetActor"));entityData.set(HUNTER_KEY,tag.getString("LivingRealmsHunterKey"));dematerializing=tag.getBoolean("LivingRealmsDematerializing");}
}
