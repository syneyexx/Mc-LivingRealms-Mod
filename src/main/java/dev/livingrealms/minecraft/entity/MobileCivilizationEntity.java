package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.compat.CompatibleContentRuntime;
import dev.livingrealms.sim.civilization.MigrationReason;
import dev.livingrealms.sim.civilization.MigrationStatus;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Comparator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Bounded physical representative of a canonical traveling MigrationGroup or PirateBand.
 * One entity can represent many strategic people. Despawn never changes canonical state; a real
 * physical death reports exactly its represented share once.
 */
public final class MobileCivilizationEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> KIND=SynchedEntityData.defineId(MobileCivilizationEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> CANONICAL_ID=SynchedEntityData.defineId(MobileCivilizationEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FACTION_ID=SynchedEntityData.defineId(MobileCivilizationEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SLOT=SynchedEntityData.defineId(MobileCivilizationEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REPRESENTED=SynchedEntityData.defineId(MobileCivilizationEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> REASON=SynchedEntityData.defineId(MobileCivilizationEntity.class,EntityDataSerializers.STRING);
    private boolean dematerializing;
    private boolean lossReported;

    public MobileCivilizationEntity(EntityType<? extends MobileCivilizationEntity> type,Level level){super(type,level);setPersistenceRequired();}
    public static AttributeSupplier.Builder createAttributes(){return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH,22).add(Attributes.MOVEMENT_SPEED,.27).add(Attributes.FOLLOW_RANGE,36).add(Attributes.ATTACK_DAMAGE,3).add(Attributes.ARMOR,1);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new MeleeAttackGoal(this,1.08,true));goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.68));goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,10));goalSelector.addGoal(8,new RandomLookAroundGoal(this));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(KIND,MobileCivilizationKind.MIGRATION.name());b.define(CANONICAL_ID,0L);b.define(FACTION_ID,0L);b.define(SLOT,-1);b.define(REPRESENTED,1);b.define(REASON,MigrationReason.OPPORTUNITY.name());}

    public void initializeMigration(long groupId,long factionId,int slot,int represented,MigrationReason reason){if(groupId<=0||factionId<=0||slot<0||represented<=0||reason==null)throw new IllegalArgumentException("migration projection");setProjection(MobileCivilizationKind.MIGRATION,groupId,factionId,slot,represented,reason.name());setCustomName(Component.literal(migrationLabel(reason)+" • x"+represented));applyKind();}
    public void initializePirate(long bandId,long originFactionId,int slot,int represented){if(bandId<=0||originFactionId<=0||slot<0||represented<=0)throw new IllegalArgumentException("pirate projection");setProjection(MobileCivilizationKind.PIRATE,bandId,originFactionId,slot,represented,MigrationReason.OPPORTUNITY.name());setCustomName(Component.literal("Pirate Raiders • x"+represented));applyKind();}
    private void setProjection(MobileCivilizationKind kind,long id,long faction,int slot,int represented,String reason){entityData.set(KIND,kind.name());entityData.set(CANONICAL_ID,id);entityData.set(FACTION_ID,faction);entityData.set(SLOT,slot);entityData.set(REPRESENTED,represented);entityData.set(REASON,reason);setCustomNameVisible(false);}

    public MobileCivilizationKind kind(){try{return MobileCivilizationKind.valueOf(entityData.get(KIND));}catch(IllegalArgumentException e){return MobileCivilizationKind.MIGRATION;}}
    public long canonicalId(){return entityData.get(CANONICAL_ID);} public long factionId(){return entityData.get(FACTION_ID);} public int projectionSlot(){return entityData.get(SLOT);} public int representedPeople(){return entityData.get(REPRESENTED);}
    public MigrationReason migrationReason(){try{return MigrationReason.valueOf(entityData.get(REASON));}catch(IllegalArgumentException e){return MigrationReason.OPPORTUNITY;}}
    public String projectionKey(){return kind().name().toLowerCase()+":"+canonicalId()+":"+projectionSlot();}
    public boolean isDematerializing(){return dematerializing;} public boolean lossReported(){return lossReported;} public void markLossReported(){lossReported=true;} public void dematerialize(){dematerializing=true;discard();}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide()||tickCount%10!=0||!(level() instanceof ServerLevel sl)||canonicalId()<=0)return;
        var state=SimulationRuntime.data(sl.getServer()).state();
        if(kind()==MobileCivilizationKind.MIGRATION){
            var group=state.findMigrationGroup(canonicalId()).orElse(null);
            if(group==null||group.status()!=MigrationStatus.TRAVELING||group.people()<=0){dematerialize();return;}
            if(getTarget()!=null)setTarget(null);
            var source=state.findSettlement(group.sourceSettlementId()).orElse(null);var target=state.findSettlement(group.targetSettlementId()).orElse(null);
            if(source==null||target==null){dematerialize();return;}
            SimPosition desired=source.position().lerp(target.position(),group.progress());navigateToward(desired,.84,22.0);
        }else{
            var band=state.findPirateBand(canonicalId()).orElse(null);
            if(band==null||!band.active()){dematerialize();return;}
            TradeCaravanEntity caravan=TradeCaravanIndex.loaded().stream().filter(TradeCaravanEntity::isAlive).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if(caravan!=null&&distanceToSqr(caravan)<=36.0D*36.0D)setTarget(caravan);else if(getTarget() instanceof TradeCaravanEntity)setTarget(null);
            if(getTarget()==null)navigateToward(band.position(),.92,18.0);
        }
    }
    private void navigateToward(SimPosition desired,double speed,double threshold){double dx=desired.x()-getX(),dz=desired.z()-getZ();if(dx*dx+dz*dz>threshold*threshold)getNavigation().moveTo(desired.x(),getY(),desired.z(),speed);}
    private void applyKind(){
        if(kind()==MobileCivilizationKind.PIRATE){
            setBase(Attributes.MAX_HEALTH,26);setBase(Attributes.ATTACK_DAMAGE,5);setBase(Attributes.ARMOR,2);setBase(Attributes.MOVEMENT_SPEED,.29);
            CompatibleContentRuntime.equipPirate(this,factionId(),projectionSlot());
        }else{
            setBase(Attributes.MAX_HEALTH,20);setBase(Attributes.ATTACK_DAMAGE,2);setBase(Attributes.ARMOR,0);setBase(Attributes.MOVEMENT_SPEED,.26);
            equipRefugeeKit();
        }
        setHealth(getMaxHealth());
    }

    /** Worn travel clothing + carried goods — distinct from ordinary citizen presentation. */
    private void equipRefugeeKit(){
        MigrationReason reason=migrationReason();
        int worn=switch(reason){
            case WAR -> 0x5A4636;
            case FAMINE -> 0x6E5A3C;
            case DISEASE -> 0x4A5540;
            case PERSECUTION -> 0x3F3F4A;
            default -> 0x6B5B45;
        };
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,dyedLeather(net.minecraft.world.item.Items.LEATHER_HELMET,worn));
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,dyedLeather(net.minecraft.world.item.Items.LEATHER_CHESTPLATE,worn^0x101010));
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,dyedLeather(net.minecraft.world.item.Items.LEATHER_LEGGINGS,worn^0x080808));
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,dyedLeather(net.minecraft.world.item.Items.LEATHER_BOOTS,0x2E2418));
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);
        // Carried goods / family kit — bundle when available, otherwise a chest as travel pack silhouette.
        net.minecraft.world.item.Item pack=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bundle"));
        if(pack==null||pack==net.minecraft.world.item.Items.AIR)pack=net.minecraft.world.item.Items.CHEST;
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new net.minecraft.world.item.ItemStack(pack));
    }

    private static net.minecraft.world.item.ItemStack dyedLeather(net.minecraft.world.item.Item item,int rgb){
        net.minecraft.world.item.ItemStack stack=new net.minecraft.world.item.ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR,new net.minecraft.world.item.component.DyedItemColor(rgb&0xFFFFFF,true));
        return stack;
    }
    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,double value){AttributeInstance instance=getAttribute(attribute);if(instance!=null)instance.setBaseValue(value);}
    private static String migrationLabel(MigrationReason reason){return switch(reason){case WAR,FAMINE,DISEASE,PERSECUTION->"Refugees";default->"Traveling Families";};}

    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putString("LRMobileKind",kind().name());t.putLong("LRCanonicalId",canonicalId());t.putLong("LRFaction",factionId());t.putInt("LRSlot",projectionSlot());t.putInt("LRRepresented",representedPeople());t.putString("LRMigrationReason",migrationReason().name());t.putBoolean("LRDematerializing",dematerializing);t.putBoolean("LRLossReported",lossReported);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);entityData.set(KIND,t.getString("LRMobileKind"));entityData.set(CANONICAL_ID,t.getLong("LRCanonicalId"));entityData.set(FACTION_ID,t.getLong("LRFaction"));entityData.set(SLOT,t.getInt("LRSlot"));entityData.set(REPRESENTED,Math.max(1,t.getInt("LRRepresented")));entityData.set(REASON,t.getString("LRMigrationReason"));dematerializing=t.getBoolean("LRDematerializing");lossReported=t.getBoolean("LRLossReported");applyKind();}
}
