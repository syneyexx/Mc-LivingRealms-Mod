package dev.livingrealms.sim.civilization;

/** Emergent requests derived from canonical settlement pressure; never standalone scripted quests. Append-only for ordinal stability. */
public enum AssistanceTaskType {
    FOOD_RELIEF,
    MEDICAL_AID,
    SECURITY_SUPPORT,
    REFUGEE_SUPPORT,
    WATER_SUPPLY,
    HOUSING_SUPPLIES,
    TRADE_ESCORT,
    /** Emergent from damaged industry / degraded routes — stone/tools for repairs. */
    INFRASTRUCTURE_REPAIR,
    BANDIT_BOUNTY,
    BRIDGE_REPAIR,
    MILITARY_SUPPLY,
    RECONSTRUCTION_AID,
    MISSING_CARAVAN
}
