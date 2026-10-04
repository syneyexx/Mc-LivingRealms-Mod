package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;

/**
 * Production contract presentation layer over AssistanceTask.
 * Rewards and player-facing copy are derived from canonical pressure — not a second quest authority.
 */
public final class ProductionContract {
    private final AssistanceTask task;

    public ProductionContract(AssistanceTask task){this.task=java.util.Objects.requireNonNull(task);}
    public AssistanceTask task(){return task;}
    public long id(){return task.id();}
    public AssistanceTaskType type(){return task.type();}
    public String cause(){return task.causeKey();}
    public double progress(){return task.progress();}
    public boolean active(){return task.active();}
    public long expiresDay(){return task.expiresDay();}

    public double rewardTreasury(){
        return Mathx.clamp(8+task.initialPressure()*28+switch(task.type()){
            case TRADE_ESCORT,MISSING_CARAVAN,MILITARY_SUPPLY -> 18;
            case BANDIT_BOUNTY,SECURITY_SUPPORT -> 14;
            case RECONSTRUCTION_AID,BRIDGE_REPAIR,INFRASTRUCTURE_REPAIR -> 16;
            case FOOD_RELIEF,REFUGEE_SUPPORT,HOUSING_SUPPLIES -> 12;
            default -> 10;
        },5,80);
    }

    public double rewardReputation(){return Mathx.clamp(4+task.initialPressure()*10,2,18);}
    public double rewardInfluence(){return Mathx.clamp(2+task.initialPressure()*8,1,14);}

    public String title(){
        return switch(task.type()){
            case FOOD_RELIEF -> "Food Relief";
            case MEDICAL_AID -> "Medical Aid";
            case SECURITY_SUPPORT -> "Security Support";
            case REFUGEE_SUPPORT -> "Refugee Aid";
            case WATER_SUPPLY -> "Water Supply";
            case HOUSING_SUPPLIES -> "Housing Supplies";
            case TRADE_ESCORT -> "Trade Escort";
            case INFRASTRUCTURE_REPAIR -> "Infrastructure Repair";
            case BANDIT_BOUNTY -> "Bandit Bounty";
            case BRIDGE_REPAIR -> "Bridge Repair";
            case MILITARY_SUPPLY -> "Military Supply";
            case RECONSTRUCTION_AID -> "Reconstruction Aid";
            case MISSING_CARAVAN -> "Missing Caravan";
        };
    }

    public String why(){
        return "Cause: "+task.causeKey()+". Remaining pressure "+String.format(java.util.Locale.ROOT,"%.0f%%",task.remainingPressure()*100)+".";
    }

    public static ProductionContract of(AssistanceTask task){return new ProductionContract(task);}
}
