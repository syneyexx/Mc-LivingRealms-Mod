package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import java.util.*;

/** Canonical displacement/migration group. It may later settle through the normal Settlement system. */
public final class MigrationGroup {
    private final long id,originFactionId,sourceSettlementId,createdDay;
    private long targetSettlementId,campSettlementId;
    private int people;
    private final MigrationReason reason;
    private final LinkedHashSet<Long> householdIds=new LinkedHashSet<>();
    private MigrationStatus status=MigrationStatus.TRAVELING;
    private double progress,food=.75,health=.85;
    public MigrationGroup(long id,long originFactionId,long sourceSettlementId,long targetSettlementId,long createdDay,int people,MigrationReason reason){
        if(id<=0||originFactionId<=0||sourceSettlementId<=0||targetSettlementId<0||createdDay<0||people<=0||reason==null)throw new IllegalArgumentException("migration group");
        this.id=id;this.originFactionId=originFactionId;this.sourceSettlementId=sourceSettlementId;this.targetSettlementId=targetSettlementId;this.createdDay=createdDay;this.people=people;this.reason=reason;
    }
    public long id(){return id;} public long originFactionId(){return originFactionId;} public long sourceSettlementId(){return sourceSettlementId;} public long targetSettlementId(){return targetSettlementId;} public long campSettlementId(){return campSettlementId;} public long createdDay(){return createdDay;} public int people(){return people;} public MigrationReason reason(){return reason;} public MigrationStatus status(){return status;} public double progress(){return progress;} public double food(){return food;} public double health(){return health;} public Set<Long> householdIds(){return Collections.unmodifiableSet(householdIds);} public boolean active(){return status==MigrationStatus.TRAVELING||status==MigrationStatus.CAMPED;}
    public boolean addHousehold(long householdId){if(householdId<=0||householdIds.size()>=8)return false;return householdIds.add(householdId);}
    public void advance(double amount){if(status!=MigrationStatus.TRAVELING)return;progress=Mathx.clamp(progress+Math.max(0,amount),0,1);} public void consume(double amount){food=Mathx.clamp(food-Math.max(0,amount),0,1);if(food<.25)health=Mathx.clamp(health-(.25-food)*.04,0,1);} public void resupply(double amount){food=Mathx.clamp(food+Math.max(0,amount),0,1);} public void adjustHealth(double delta){health=Mathx.clamp(health+delta,0,1);}
    public void losePeople(int count){people=Math.max(0,people-Math.max(0,count));if(people==0)status=MigrationStatus.LOST;} public void setTargetSettlementId(long value){if(value<0)throw new IllegalArgumentException("target");targetSettlementId=value;} public void campAt(long settlementId){if(settlementId<=0)throw new IllegalArgumentException("camp");campSettlementId=settlementId;status=MigrationStatus.CAMPED;progress=1;} public void settle(){status=MigrationStatus.SETTLED;progress=1;} public void turnBack(){status=MigrationStatus.TURNED_BACK;} public void restore(long target,long camp,int people,MigrationStatus status,double progress,double food,double health,Collection<Long> households){if(target<0||camp<0||people<0||status==null)throw new IllegalArgumentException("migration restore");this.targetSettlementId=target;this.campSettlementId=camp;this.people=people;this.status=status;this.progress=Mathx.clamp(progress,0,1);this.food=Mathx.clamp(food,0,1);this.health=Mathx.clamp(health,0,1);householdIds.clear();if(households!=null)for(long h:households)if(h>0&&householdIds.size()<8)householdIds.add(h);}
}
