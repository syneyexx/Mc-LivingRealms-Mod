package dev.livingrealms.sim.military;

import dev.livingrealms.sim.util.Mathx;

public final class SiegeState {
    private final long id,attackerFactionId,defenderFactionId,settlementId,startDay;
    private double progress,blockade,breach,defenderCountermeasures=.35;
    private int rams,ladders,artilleryPieces;
    private boolean active=true;
    public SiegeState(long id,long attackerFactionId,long defenderFactionId,long settlementId,long startDay){if(id<=0||attackerFactionId<=0||defenderFactionId<=0||settlementId<=0||attackerFactionId==defenderFactionId)throw new IllegalArgumentException("siege identity");this.id=id;this.attackerFactionId=attackerFactionId;this.defenderFactionId=defenderFactionId;this.settlementId=settlementId;this.startDay=Math.max(0,startDay);}
    public long id(){return id;} public long attackerFactionId(){return attackerFactionId;} public long defenderFactionId(){return defenderFactionId;} public long settlementId(){return settlementId;} public long startDay(){return startDay;} public double progress(){return progress;} public double blockade(){return blockade;} public double breach(){return breach;} public double defenderCountermeasures(){return defenderCountermeasures;} public int rams(){return rams;} public int ladders(){return ladders;} public int artilleryPieces(){return artilleryPieces;} public boolean active(){return active;}
    public void addEquipment(int rams,int ladders,int artillery){this.rams=Math.max(0,this.rams+Math.max(0,rams));this.ladders=Math.max(0,this.ladders+Math.max(0,ladders));this.artilleryPieces=Math.max(0,this.artilleryPieces+Math.max(0,artillery));}
    public void damageEquipment(int ramLoss,int ladderLoss,int artilleryLoss){rams=Math.max(0,rams-Math.max(0,ramLoss));ladders=Math.max(0,ladders-Math.max(0,ladderLoss));artilleryPieces=Math.max(0,artilleryPieces-Math.max(0,artilleryLoss));}
    public void addBreach(double value){breach=Mathx.clamp(breach+value,0,1);}
    public void adjustCountermeasures(double value){defenderCountermeasures=Mathx.clamp(defenderCountermeasures+value,0,1);}
    public void advance(double amount,double blockade){progress=Mathx.clamp(progress+amount,0,1);this.blockade=Mathx.clamp(blockade,0,1);if(progress>=1)active=false;} public void end(){active=false;} public void restore(double progress,double blockade,boolean active){this.progress=Mathx.clamp(progress,0,1);this.blockade=Mathx.clamp(blockade,0,1);this.active=active&&this.progress<1;}
    public void restoreEquipment(int rams,int ladders,int artillery,double breach,double countermeasures){this.rams=Math.max(0,rams);this.ladders=Math.max(0,ladders);this.artilleryPieces=Math.max(0,artillery);this.breach=Mathx.clamp(breach,0,1);this.defenderCountermeasures=Mathx.clamp(countermeasures,0,1);}
}
