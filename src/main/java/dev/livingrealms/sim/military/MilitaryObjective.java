package dev.livingrealms.sim.military;

import dev.livingrealms.sim.world.SimPosition;

public final class MilitaryObjective {
    private final long id;
    private final long armyId;
    private final long ownerFactionId;
    private final MilitaryObjectiveType type;
    private final long targetFactionId;
    private final long targetSettlementId;
    private final SimPosition targetPosition;
    private final long createdDay;
    private int priority;
    private boolean complete;
    public MilitaryObjective(long id,long armyId,long ownerFactionId,MilitaryObjectiveType type,long targetFactionId,long targetSettlementId,SimPosition targetPosition,long createdDay,int priority){if(id<=0||armyId<=0||ownerFactionId<=0)throw new IllegalArgumentException("objective identity");if(type==null||targetPosition==null)throw new IllegalArgumentException("objective fields");this.id=id;this.armyId=armyId;this.ownerFactionId=ownerFactionId;this.type=type;this.targetFactionId=targetFactionId;this.targetSettlementId=targetSettlementId;this.targetPosition=targetPosition;this.createdDay=Math.max(0,createdDay);this.priority=Math.max(0,priority);}
    public long id(){return id;} public long armyId(){return armyId;} public long ownerFactionId(){return ownerFactionId;} public MilitaryObjectiveType type(){return type;} public long targetFactionId(){return targetFactionId;} public long targetSettlementId(){return targetSettlementId;} public SimPosition targetPosition(){return targetPosition;} public long createdDay(){return createdDay;} public int priority(){return priority;} public boolean complete(){return complete;}
    public void setPriority(int v){priority=Math.max(0,v);} public void markComplete(){complete=true;} public void restoreComplete(boolean v){complete=v;}
}
