package dev.livingrealms.sim.law;

import dev.livingrealms.sim.util.Mathx;

public final class JurisdictionWanted {
    private final long factionId;
    private double bounty;
    private double notoriety;
    private double heat;
    private long lastCrimeDay=-1;
    private int witnessedCrimes;
    private int violentCrimes;
    private int captures;

    public JurisdictionWanted(long factionId){if(factionId<=0)throw new IllegalArgumentException("factionId");this.factionId=factionId;}
    public long factionId(){return factionId;} public double bounty(){return bounty;} public double notoriety(){return notoriety;} public double heat(){return heat;} public long lastCrimeDay(){return lastCrimeDay;} public int witnessedCrimes(){return witnessedCrimes;} public int violentCrimes(){return violentCrimes;} public int captures(){return captures;}
    public WantedLevel wantedLevel(){
        if(bounty<=.001&&notoriety<5)return WantedLevel.NONE;
        if(bounty<25&&notoriety<20)return WantedLevel.PERSON_OF_INTEREST;
        if(bounty<125&&notoriety<60)return WantedLevel.WANTED;
        if(bounty<500&&notoriety<140)return WantedLevel.DANGEROUS;
        return WantedLevel.MOST_WANTED;
    }
    public void addCrime(double bountyDelta,double notorietyDelta,double heatDelta,long day,boolean violent){bounty=Math.max(0,bounty+bountyDelta);notoriety=Math.max(0,notoriety+notorietyDelta);heat=Mathx.clamp(heat+heatDelta,0,100);lastCrimeDay=day;witnessedCrimes++;if(violent)violentCrimes++;}
    public void decay(double heatPerDay,double notorietyPerDay){heat=Math.max(0,heat-Math.max(0,heatPerDay));notoriety=Math.max(0,notoriety-Math.max(0,notorietyPerDay));}
    public double pay(double amount){if(amount<0||!Double.isFinite(amount))throw new IllegalArgumentException("amount");double paid=Math.min(amount,bounty);bounty-=paid;return paid;}
    public double captureAndClear(){double previous=bounty;bounty=0;heat=0;notoriety*=.35;captures++;return previous;}
    public void pardon(){bounty=0;heat=0;notoriety=0;}
    public void restore(double bounty,double notoriety,double heat,long lastCrimeDay,int witnessedCrimes,int violentCrimes,int captures){this.bounty=Math.max(0,bounty);this.notoriety=Math.max(0,notoriety);this.heat=Mathx.clamp(heat,0,100);this.lastCrimeDay=lastCrimeDay;this.witnessedCrimes=Math.max(0,witnessedCrimes);this.violentCrimes=Math.max(0,violentCrimes);this.captures=Math.max(0,captures);}
}
