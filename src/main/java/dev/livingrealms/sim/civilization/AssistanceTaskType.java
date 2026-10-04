package dev.livingrealms.sim.civilization;

/** Emergent requests derived from canonical settlement pressure; never standalone scripted quests. */
public enum AssistanceTaskType {
    FOOD_RELIEF,
    MEDICAL_AID,
    SECURITY_SUPPORT,
    REFUGEE_SUPPORT,
    WATER_SUPPLY,
    HOUSING_SUPPLIES,
    TRADE_ESCORT,
    /** Emergent from damaged industry / degraded routes — stone/tools for repairs. */
    INFRASTRUCTURE_REPAIR
}
