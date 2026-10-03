package dev.livingrealms.sim.civilization;

/** Persistent cross-faction political marriage; family relationships remain on the citizens themselves. */
public final class DiplomaticMarriage {
    private final long id,day,citizenA,citizenB,factionA,factionB;
    private boolean active=true;
    public DiplomaticMarriage(long id,long day,long citizenA,long citizenB,long factionA,long factionB){if(id<=0||day<0||citizenA<=0||citizenB<=0||factionA<=0||factionB<=0||factionA==factionB)throw new IllegalArgumentException("diplomatic marriage");this.id=id;this.day=day;this.citizenA=citizenA;this.citizenB=citizenB;this.factionA=factionA;this.factionB=factionB;}
    public long id(){return id;} public long day(){return day;} public long citizenA(){return citizenA;} public long citizenB(){return citizenB;} public long factionA(){return factionA;} public long factionB(){return factionB;} public boolean active(){return active;} public void end(){active=false;} public void restoreActive(boolean value){active=value;}
}
