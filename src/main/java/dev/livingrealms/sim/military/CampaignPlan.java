package dev.livingrealms.sim.military;

import java.util.Objects;

/** Persistent campaign-level plan for one faction in one war. */
public final class CampaignPlan {
    private final long id,factionId,warId,targetSettlementId,createdDay;
    private final CampaignPlanType type;
    private int priority;
    private boolean active=true;

    public CampaignPlan(long id,long factionId,long warId,CampaignPlanType type,long targetSettlementId,int priority,long createdDay){
        if(id<=0||factionId<=0||warId<=0||type==null||createdDay<0)throw new IllegalArgumentException("campaign plan");
        if(priority<0)throw new IllegalArgumentException("priority");
        this.id=id;this.factionId=factionId;this.warId=warId;this.type=type;this.targetSettlementId=Math.max(0,targetSettlementId);this.priority=priority;this.createdDay=createdDay;
    }

    public long id(){return id;} public long factionId(){return factionId;} public long warId(){return warId;}
    public CampaignPlanType type(){return type;} public long targetSettlementId(){return targetSettlementId;}
    public int priority(){return priority;} public long createdDay(){return createdDay;} public boolean active(){return active;}
    public void setPriority(int value){if(value<0)throw new IllegalArgumentException("priority");priority=value;}
    public void markComplete(){active=false;}
    public void restore(int priority,boolean active){if(priority<0)throw new IllegalArgumentException("priority");this.priority=priority;this.active=active;}
    @Override public String toString(){return type+":"+targetSettlementId;}
    public static CampaignPlan require(CampaignPlan plan){return Objects.requireNonNull(plan);}
}
