package dev.livingrealms.sim.law;

public final class BountyContract {
    public enum Status { OPEN, ASSIGNED, CLAIMED, CANCELLED }
    private final long id;private final String actorKey;private final long issuerFactionId;private final long createdDay;private double reward;private Status status=Status.OPEN;private String hunterKey="";
    public BountyContract(long id,String actorKey,long issuerFactionId,long createdDay,double reward){if(id<=0||actorKey==null||actorKey.isBlank()||issuerFactionId<=0||createdDay<0||reward<=0||!Double.isFinite(reward))throw new IllegalArgumentException("bounty contract");this.id=id;this.actorKey=actorKey;this.issuerFactionId=issuerFactionId;this.createdDay=createdDay;this.reward=reward;}
    public long id(){return id;}public String actorKey(){return actorKey;}public long issuerFactionId(){return issuerFactionId;}public long createdDay(){return createdDay;}public double reward(){return reward;}public Status status(){return status;}public String hunterKey(){return hunterKey;}
    public boolean assign(String hunter){if(status!=Status.OPEN||hunter==null||hunter.isBlank())return false;hunterKey=hunter;status=Status.ASSIGNED;return true;}
    public boolean unassign(String hunter){if(status!=Status.ASSIGNED||hunter==null||!hunterKey.equals(hunter))return false;hunterKey="";status=Status.OPEN;return true;}
    public void claim(){if(status==Status.OPEN||status==Status.ASSIGNED)status=Status.CLAIMED;}public void cancel(){if(status!=Status.CLAIMED)status=Status.CANCELLED;}public void updateReward(double v){if(v>reward)reward=v;}public void restore(Status status,String hunter){this.status=status;this.hunterKey=hunter==null?"":hunter;}
}
