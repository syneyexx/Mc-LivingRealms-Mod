package dev.livingrealms.minecraft.entity;

import dev.livingrealms.LivingRealms;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity registrations for loaded physical projections of canonical simulation objects. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, LivingRealms.MOD_ID);

    public static final Supplier<EntityType<LivingRealmsAnimalEntity>> WILDLIFE = ENTITY_TYPES.register(
            "wildlife", () -> EntityType.Builder.of(LivingRealmsAnimalEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.2F).clientTrackingRange(10).updateInterval(3)
                    .build(LivingRealms.MOD_ID + ":wildlife")
    );

    public static final Supplier<EntityType<TradeCaravanEntity>> CARAVAN = ENTITY_TYPES.register(
            "trade_caravan", () -> EntityType.Builder.of(TradeCaravanEntity::new, MobCategory.CREATURE)
                    .sized(1.25F, 1.65F).clientTrackingRange(12).updateInterval(3)
                    .build(LivingRealms.MOD_ID + ":trade_caravan")
    );


    public static final Supplier<EntityType<FactionCitizenEntity>> CITIZEN = ENTITY_TYPES.register(
            "faction_citizen", () -> EntityType.Builder.of(FactionCitizenEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(3)
                    .build(LivingRealms.MOD_ID + ":faction_citizen")
    );


    public static final Supplier<EntityType<MobileCivilizationEntity>> MOBILE_CIVILIZATION = ENTITY_TYPES.register(
            "mobile_civilization", () -> EntityType.Builder.of(MobileCivilizationEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(12).updateInterval(2)
                    .build(LivingRealms.MOD_ID + ":mobile_civilization")
    );


    public static final Supplier<EntityType<MilitaryUnitEntity>> MILITARY_UNIT = ENTITY_TYPES.register(
            "military_unit", () -> EntityType.Builder.of(MilitaryUnitEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 1.7F).clientTrackingRange(12).updateInterval(2)
                    .build(LivingRealms.MOD_ID + ":military_unit")
    );


    public static final Supplier<EntityType<LivingRealmsAircraftEntity>> AIRCRAFT = ENTITY_TYPES.register(
            "aircraft", () -> EntityType.Builder.of(LivingRealmsAircraftEntity::new, MobCategory.CREATURE)
                    .sized(2.4F, 0.9F).clientTrackingRange(16).updateInterval(1)
                    .build(LivingRealms.MOD_ID + ":aircraft")
    );


    public static final Supplier<EntityType<LivingRealmsShipEntity>> SHIP = ENTITY_TYPES.register(
            "ship", () -> EntityType.Builder.of(LivingRealmsShipEntity::new, MobCategory.CREATURE)
                    .sized(3.2F, 1.8F).clientTrackingRange(18).updateInterval(2)
                    .build(LivingRealms.MOD_ID + ":ship")
    );


    public static final Supplier<EntityType<BountyHunterEntity>> BOUNTY_HUNTER = ENTITY_TYPES.register(
            "bounty_hunter", () -> EntityType.Builder.of(BountyHunterEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(12).updateInterval(2)
                    .build(LivingRealms.MOD_ID + ":bounty_hunter")
    );

    public static final Supplier<EntityType<SiegeEquipmentEntity>> SIEGE_EQUIPMENT = ENTITY_TYPES.register(
            "siege_equipment", () -> EntityType.Builder.of(SiegeEquipmentEntity::new, MobCategory.MISC)
                    .sized(1.8F, 1.6F).clientTrackingRange(14).updateInterval(5)
                    .build(LivingRealms.MOD_ID + ":siege_equipment")
    );

    private ModEntities() {}

    public static void register(IEventBus modBus) {ENTITY_TYPES.register(modBus);modBus.addListener(ModEntities::createAttributes);}
    private static void createAttributes(EntityAttributeCreationEvent event) {event.put(WILDLIFE.get(), LivingRealmsAnimalEntity.createAttributes().build());event.put(CARAVAN.get(),TradeCaravanEntity.createAttributes().build());event.put(CITIZEN.get(),FactionCitizenEntity.createAttributes().build());event.put(MILITARY_UNIT.get(),MilitaryUnitEntity.createAttributes().build());event.put(MOBILE_CIVILIZATION.get(),MobileCivilizationEntity.createAttributes().build());event.put(AIRCRAFT.get(),LivingRealmsAircraftEntity.createAttributes().build());event.put(SHIP.get(),LivingRealmsShipEntity.createAttributes().build());event.put(BOUNTY_HUNTER.get(),BountyHunterEntity.createAttributes().build());event.put(SIEGE_EQUIPMENT.get(),SiegeEquipmentEntity.createAttributes().build());}
}
