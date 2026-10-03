package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import java.util.Objects;

/**
 * Persistent emergent request backed by a real settlement condition. Progress is derived from
 * canonical world state, so a task cannot outlive the shortage/outbreak/threat that created it.
 */
public final class AssistanceTask {
    private final long id,factionId,settlementId,createdDay,expiresDay;
    private final AssistanceTaskType type;
    private final String causeKey;
    private final double initialPressure;
    private double remainingPressure;
    private AssistanceTaskStatus status=AssistanceTaskStatus.OPEN;

    public AssistanceTask(long id,long factionId,long settlementId,long createdDay,long expiresDay,AssistanceTaskType type,String causeKey,double initialPressure){
        if(id<=0||factionId<=0||settlementId<=0||createdDay<0||expiresDay<createdDay||type==null||causeKey==null||causeKey.isBlank()||!Double.isFinite(initialPressure)||initialPressure<=0||initialPressure>1)throw new IllegalArgumentException("assistance task");
        this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.createdDay=createdDay;this.expiresDay=expiresDay;this.type=type;this.causeKey=causeKey;this.initialPressure=initialPressure;remainingPressure=initialPressure;
    }
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public long createdDay(){return createdDay;} public long expiresDay(){return expiresDay;} public AssistanceTaskType type(){return type;} public String causeKey(){return causeKey;} public double initialPressure(){return initialPressure;} public double remainingPressure(){return remainingPressure;} public double progress(){return Mathx.clamp(1-remainingPressure/Math.max(.000001,initialPressure),0,1);} public AssistanceTaskStatus status(){return status;} public boolean active(){return status==AssistanceTaskStatus.OPEN;}
    public void updatePressure(long day,double pressure){if(!Double.isFinite(pressure))throw new IllegalArgumentException("pressure");if(status!=AssistanceTaskStatus.OPEN)return;remainingPressure=Mathx.clamp(pressure,0,1);if(remainingPressure<=Math.min(.12,initialPressure*.28))status=AssistanceTaskStatus.RESOLVED;else if(day>expiresDay)status=AssistanceTaskStatus.EXPIRED;}
    public void cancel(){if(status==AssistanceTaskStatus.OPEN)status=AssistanceTaskStatus.CANCELLED;}
    public void restore(double remainingPressure,AssistanceTaskStatus status){if(!Double.isFinite(remainingPressure)||status==null)throw new IllegalArgumentException("assistance task restore");this.remainingPressure=Mathx.clamp(remainingPressure,0,1);this.status=status;}
    @Override public String toString(){return type+"@"+settlementId+"("+status+")";}
    @Override public boolean equals(Object o){return o instanceof AssistanceTask t&&t.id==id;} @Override public int hashCode(){return Objects.hash(id);}
}
