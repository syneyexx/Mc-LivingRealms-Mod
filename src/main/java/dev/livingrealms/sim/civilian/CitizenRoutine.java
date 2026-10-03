package dev.livingrealms.sim.civilian;
import dev.livingrealms.sim.world.SimPosition;
public record CitizenRoutine(CitizenActivity activity,SimPosition target,double speed,double arrivalRadius) {
    public CitizenRoutine {if(activity==null||target==null||!Double.isFinite(speed)||speed<=0||!Double.isFinite(arrivalRadius)||arrivalRadius<0)throw new IllegalArgumentException("citizen routine");}
}
