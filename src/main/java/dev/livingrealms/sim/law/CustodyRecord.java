package dev.livingrealms.sim.law;

/** Persistent incarceration record so arrest survives logout/reload. */
public final class CustodyRecord {
    private final long id;
    private final String actorKey;
    private final long factionId;
    private final long startDay;
    private final long releaseDay;
    private final double bountyAtArrest;
    private final String reason;
    private boolean active = true;

    public CustodyRecord(long id, String actorKey, long factionId, long startDay, long releaseDay, double bountyAtArrest, String reason) {
        if (id <= 0 || actorKey == null || actorKey.isBlank() || factionId <= 0 || startDay < 0 || releaseDay < startDay) throw new IllegalArgumentException("custody");
        if (!Double.isFinite(bountyAtArrest) || bountyAtArrest < 0) throw new IllegalArgumentException("bountyAtArrest");
        this.id = id;
        this.actorKey = actorKey;
        this.factionId = factionId;
        this.startDay = startDay;
        this.releaseDay = releaseDay;
        this.bountyAtArrest = bountyAtArrest;
        this.reason = reason == null ? "" : reason;
    }

    public long id(){return id;}
    public String actorKey(){return actorKey;}
    public long factionId(){return factionId;}
    public long startDay(){return startDay;}
    public long releaseDay(){return releaseDay;}
    public double bountyAtArrest(){return bountyAtArrest;}
    public String reason(){return reason;}
    public boolean active(){return active;}
    public long daysRemaining(long currentDay){return active ? Math.max(0, releaseDay-currentDay) : 0;}
    public void release(){active=false;}
    public void restoreActive(boolean value){active=value;}
}
