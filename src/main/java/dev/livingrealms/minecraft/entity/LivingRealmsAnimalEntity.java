package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.SpeciesDataRegistry;
import dev.livingrealms.sim.animal.AnimalBrain;
import dev.livingrealms.sim.animal.AnimalDecision;
import dev.livingrealms.sim.animal.AnimalIntent;
import dev.livingrealms.sim.animal.AnimalStimulus;
import dev.livingrealms.sim.ecology.ActivityCycle;
import dev.livingrealms.sim.ecology.Diet;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import dev.livingrealms.sim.ecology.SpeciesMobility;
import dev.livingrealms.sim.ecology.SpeciesMobilityResolver;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Generic terrestrial physical projection. Species biology remains data-driven; this class translates
 * AnimalBrain intents into vanilla navigation/targeting. Aquatic and flying adapters are separate roadmap slices.
 */
public final class LivingRealmsAnimalEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> SPECIES_ID = SynchedEntityData.defineId(LivingRealmsAnimalEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> POPULATION_GROUP_ID = SynchedEntityData.defineId(LivingRealmsAnimalEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> PROJECTION_SLOT = SynchedEntityData.defineId(LivingRealmsAnimalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> CURRENT_INTENT = SynchedEntityData.defineId(LivingRealmsAnimalEntity.class, EntityDataSerializers.STRING);

    private double localHunger;
    private double localThirst;
    private double reproductionDrive;
    private boolean dematerializing;
    private boolean physicalDeathReported;

    public LivingRealmsAnimalEntity(EntityType<? extends LivingRealmsAnimalEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.40D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.85D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPECIES_ID, "");
        builder.define(POPULATION_GROUP_ID, 0L);
        builder.define(PROJECTION_SLOT, -1);
        builder.define(CURRENT_INTENT, AnimalIntent.ROAM.name());
    }

    public void initializeProjection(long groupId, String speciesId, int slot, double initialHunger) {
        if (groupId <= 0 || speciesId == null || speciesId.isBlank() || slot < 0) throw new IllegalArgumentException("projection identity");
        this.entityData.set(POPULATION_GROUP_ID, groupId);
        this.entityData.set(SPECIES_ID, speciesId);
        this.entityData.set(PROJECTION_SLOT, slot);
        this.localHunger = clamp01(initialHunger);
        applySpeciesProfile();
        configureMobility();
    }

    public String speciesId() { return this.entityData.get(SPECIES_ID); }
    public long populationGroupId() { return this.entityData.get(POPULATION_GROUP_ID); }
    public int projectionSlot() { return this.entityData.get(PROJECTION_SLOT); }
    public String currentIntentName() { return this.entityData.get(CURRENT_INTENT); }
    public boolean isDematerializing() { return dematerializing; }
    public boolean physicalDeathReported() { return physicalDeathReported; }
    public void markPhysicalDeathReported() { physicalDeathReported = true; }

    public void dematerialize() {
        this.dematerializing = true;
        this.discard();
    }

    public SpeciesDefinition species() {
        return SpeciesDataRegistry.current().get(speciesId());
    }

    public SpeciesMobility mobility() {
        SpeciesDefinition species=species();
        return species==null?SpeciesMobility.TERRESTRIAL:SpeciesMobilityResolver.resolve(species);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.tickCount % 20 != 0) return;
        SpeciesDefinition species = species();
        if (species == null || populationGroupId() <= 0 || projectionSlot() < 0) return;
        if(mobility()==SpeciesMobility.AQUATIC) this.setAirSupply(this.getMaxAirSupply());
        if(mobility()==SpeciesMobility.FLYING) this.setNoGravity(true);

        localHunger = clamp01(localHunger + Math.max(0.002, species.dailyFoodKg() / Math.max(1.0, species.adultMassKg()) * 0.01));
        localThirst = clamp01(localThirst + (species.dailyWaterLitres() > 0 ? 0.003 : 0.0));
        reproductionDrive = clamp01(reproductionDrive + 0.0015);

        LivingRealmsAnimalEntity prey = nearestAnimal(species, true);
        LivingRealmsAnimalEntity predator = nearestAnimal(species, false);
        Player human = this.level().getNearestPlayer(this, 14.0D);
        boolean active = activeNow(species.activityCycle(), this.level().getDayTime() % 24000L);
        AnimalStimulus stimulus = new AnimalStimulus(
                localHunger,
                localThirst,
                predator == null ? 0.0 : 0.9,
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()),
                reproductionDrive,
                prey != null,
                predator != null,
                human != null,
                this.isInWaterOrBubble(),
                true,
                false,
                false,
                active
        );
        AnimalDecision decision = AnimalBrain.decide(species, stimulus);
        this.entityData.set(CURRENT_INTENT, decision.intent().name());
        enact(decision.intent(), prey, predator, human);
    }

    private void enact(AnimalIntent intent, LivingRealmsAnimalEntity prey, LivingRealmsAnimalEntity predator, Player human) {
        switch (intent) {
            case HUNT -> {
                if (prey != null) this.setTarget(prey);
            }
            case DEFEND -> {
                if (human != null && species() != null && species().attacksHumans()) this.setTarget(human);
                else if (predator != null) this.setTarget(predator);
            }
            case FLEE -> {
                this.setTarget(null);
                if (predator != null) fleeFrom(predator.position());
                else if (human != null) fleeFrom(human.position());
            }
            case GRAZE, FORAGE -> {
                this.setTarget(null);
                localHunger = clamp01(localHunger - 0.08);
            }
            case DRINK -> localThirst = clamp01(localThirst - 0.12);
            case REST -> this.getNavigation().stop();
            case MATE -> reproductionDrive = clamp01(reproductionDrive - 0.2);
            default -> { }
        }
    }

    private void fleeFrom(Vec3 threat) {
        Vec3 away = this.position().subtract(threat);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
        Vec3 destination = this.position().add(away.normalize().scale(12.0D));
        this.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.35D);
    }

    private LivingRealmsAnimalEntity nearestAnimal(SpeciesDefinition self, boolean preySearch) {
        List<LivingRealmsAnimalEntity> candidates = this.level().getEntitiesOfClass(
                LivingRealmsAnimalEntity.class,
                this.getBoundingBox().inflate(24.0D),
                other -> other != this && other.isAlive()
        );
        return candidates.stream()
                .filter(other -> {
                    SpeciesDefinition otherSpecies = other.species();
                    if (otherSpecies == null) return false;
                    return preySearch ? self.canPreyOn(other.speciesId()) : otherSpecies.canPreyOn(self.id());
                })
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void configureMobility() {
        SpeciesMobility mobility=mobility();
        switch(mobility){
            case AQUATIC -> { this.navigation=new WaterBoundPathNavigation(this,this.level()); this.moveControl=new SmoothSwimmingMoveControl(this,85,10,.9F,.18F,false); this.setNoGravity(false); }
            case AMPHIBIOUS -> { this.navigation=new AmphibiousPathNavigation(this,this.level()); this.moveControl=new SmoothSwimmingMoveControl(this,85,10,.85F,.28F,true); this.setNoGravity(false); }
            case FLYING -> { this.navigation=new FlyingPathNavigation(this,this.level()); this.moveControl=new FlyingMoveControl(this,20,true); this.setNoGravity(true); }
            case TERRESTRIAL -> { this.navigation=new GroundPathNavigation(this,this.level()); this.moveControl=new MoveControl(this); this.setNoGravity(false); }
        }
    }

    private void applySpeciesProfile() {
        SpeciesDefinition species = species();
        if (species == null) return;
        double massScale = Math.max(0.15D, Math.min(5.0D, Math.sqrt(species.adultMassKg() / 40.0D)));
        setBase(Attributes.MAX_HEALTH, Math.max(3.0D, Math.min(120.0D, 8.0D + massScale * 12.0D + species.defense() * 20.0D)));
        setBase(Attributes.ATTACK_DAMAGE, Math.max(1.0D, Math.min(24.0D, 1.0D + massScale * 2.0D + species.aggression() * 7.0D)));
        double movementSpeed = Math.max(0.16D, Math.min(0.42D, 0.20D + species.movementKmPerDay() / 500.0D));
        setBase(Attributes.MOVEMENT_SPEED, movementSpeed);
        setBase(Attributes.FLYING_SPEED, Math.max(0.22D, Math.min(0.72D, movementSpeed * 1.55D)));
        setBase(Attributes.ARMOR, Math.max(0.0D, Math.min(14.0D, species.defense() * 10.0D)));
        setBase(Attributes.KNOCKBACK_RESISTANCE, Math.max(0.0D, Math.min(0.85D, (massScale - 1.0D) * 0.15D)));
        setBase(Attributes.FOLLOW_RANGE, 28.0D);
        this.setHealth(this.getMaxHealth());
        this.setCustomName(Component.literal(species.commonName()));
        this.setCustomNameVisible(false);
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        var instance = this.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private static boolean activeNow(ActivityCycle cycle, long dayTime) {
        boolean daylight = dayTime < 12000L;
        boolean twilight = dayTime < 1800L || (dayTime >= 10500L && dayTime <= 13500L) || dayTime >= 22200L;
        return switch (cycle) {
            case DIURNAL -> daylight;
            case NOCTURNAL -> !daylight;
            case CREPUSCULAR -> twilight;
            case CATHEMERAL -> true;
        };
    }

    private static double clamp01(double value) { return Math.max(0.0D, Math.min(1.0D, value)); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("LivingRealmsSpecies", speciesId());
        tag.putLong("LivingRealmsPopulationGroup", populationGroupId());
        tag.putInt("LivingRealmsProjectionSlot", projectionSlot());
        tag.putDouble("LivingRealmsHunger", localHunger);
        tag.putDouble("LivingRealmsThirst", localThirst);
        tag.putDouble("LivingRealmsReproduction", reproductionDrive);
        tag.putBoolean("LivingRealmsDematerializing", dematerializing);
        tag.putBoolean("LivingRealmsDeathReported", physicalDeathReported);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(SPECIES_ID, tag.getString("LivingRealmsSpecies"));
        this.entityData.set(POPULATION_GROUP_ID, tag.getLong("LivingRealmsPopulationGroup"));
        this.entityData.set(PROJECTION_SLOT, tag.getInt("LivingRealmsProjectionSlot"));
        this.localHunger = clamp01(tag.getDouble("LivingRealmsHunger"));
        this.localThirst = clamp01(tag.getDouble("LivingRealmsThirst"));
        this.reproductionDrive = clamp01(tag.getDouble("LivingRealmsReproduction"));
        this.dematerializing = tag.getBoolean("LivingRealmsDematerializing");
        this.physicalDeathReported = tag.getBoolean("LivingRealmsDeathReported");
        applySpeciesProfile();
        configureMobility();
    }
}
