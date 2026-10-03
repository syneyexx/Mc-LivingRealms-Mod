package dev.livingrealms.sim.diplomacy;

public final class Treaty {
    private final long id;
    private final long factionA;
    private final long factionB;
    private final TreatyType type;
    private final long startDay;
    private final long endDay;
    private boolean active=true;
    public Treaty(long id,long factionA,long factionB,TreatyType type,long startDay,long endDay){if(id<=0||factionA<=0||factionB<=0||factionA==factionB)throw new IllegalArgumentException("treaty identity");if(type==null)throw new IllegalArgumentException("type");if(startDay<0||endDay<startDay)throw new IllegalArgumentException("days");this.id=id;this.factionA=factionA;this.factionB=factionB;this.type=type;this.startDay=startDay;this.endDay=endDay;}
    public long id(){return id;} public long factionA(){return factionA;} public long factionB(){return factionB;} public TreatyType type(){return type;} public long startDay(){return startDay;} public long endDay(){return endDay;} public boolean active(){return active;}
    public boolean involves(long factionId){return factionA==factionId||factionB==factionId;} public boolean between(long a,long b){return (factionA==a&&factionB==b)||(factionA==b&&factionB==a);} public void terminate(){active=false;} public void restoreActive(boolean v){active=v;}
}
