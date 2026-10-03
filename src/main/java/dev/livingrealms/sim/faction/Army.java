package dev.livingrealms.sim.faction;

import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.util.Mathx;

public final class Army {
    private final long id; private final long factionId; private SimPosition position; private int infantry; private int cavalry; private int artillery; private int armor; private int aircraft; private double morale=.7; private double supply=.8;
    public Army(long id,long factionId,SimPosition pos,int infantry){this.id=id;this.factionId=factionId;this.position=pos;this.infantry=Math.max(0,infantry);}    
    public long id(){return id;} public long factionId(){return factionId;} public SimPosition position(){return position;} public int infantry(){return infantry;} public int cavalry(){return cavalry;} public int artillery(){return artillery;} public int armor(){return armor;} public int aircraft(){return aircraft;} public int totalPersonnel(){return infantry+cavalry*2+artillery*5+armor*4+aircraft*2;} public double morale(){return morale;} public double supply(){return supply;}
    public double combatPower(){return (infantry+cavalry*2.5+artillery*8+armor*15+aircraft*22)*(.4+.6*morale)*(.35+.65*supply);}
    public boolean destroyed(){return totalPersonnel()<=0 || combatPower()<0.5;}
    public void moveToward(SimPosition target,double distance){double d=position.distanceTo(target); if(d<=distance||d==0) position=target; else position=position.lerp(target,distance/d); supply=Mathx.clamp(supply-distance*.0004,0,1);}
    public void applyLossFraction(double f){f=Mathx.clamp(f,0,1);infantry=(int)Math.round(infantry*(1-f));cavalry=(int)Math.round(cavalry*(1-f));artillery=(int)Math.round(artillery*(1-f));armor=(int)Math.round(armor*(1-f));aircraft=(int)Math.round(aircraft*(1-f));morale=Mathx.clamp(morale-f*.8,0,1);}
    public void recordRepresentativeLoss(int representedPersonnel){
        if(representedPersonnel<=0)throw new IllegalArgumentException("representedPersonnel");
        int before=Math.max(1,totalPersonnel());
        applyLossFraction(Math.min(1.0,representedPersonnel/(double)before));
    }
    public void restoreState(int cavalry,int artillery,int armor,int aircraft,double morale,double supply){this.cavalry=Math.max(0,cavalry);this.artillery=Math.max(0,artillery);this.armor=Math.max(0,armor);this.aircraft=Math.max(0,aircraft);this.morale=Mathx.clamp(morale,0,1);this.supply=Mathx.clamp(supply,0,1);}
    public void resupply(double amount){supply=Mathx.clamp(supply+amount,0,1);} public void adjustMorale(double amount){if(!Double.isFinite(amount))throw new IllegalArgumentException("morale");morale=Mathx.clamp(morale+amount,0,1);}
    public int desertPersonnel(int representedPersonnel){if(representedPersonnel<=0||totalPersonnel()<=0)return 0;int before=totalPersonnel();double fraction=Math.min(.95,representedPersonnel/(double)before);infantry=(int)Math.round(infantry*(1-fraction));cavalry=(int)Math.round(cavalry*(1-fraction));artillery=(int)Math.round(artillery*(1-fraction));armor=(int)Math.round(armor*(1-fraction));aircraft=(int)Math.round(aircraft*(1-fraction));int lost=Math.max(0,before-totalPersonnel());morale=Mathx.clamp(morale-.04-.2*fraction,0,1);return lost;}
    public void addAircraft(int n){aircraft=Math.max(0,aircraft+n);} public void addArmor(int n){armor=Math.max(0,armor+n);} public void addArtillery(int n){artillery=Math.max(0,artillery+n);} public void addCavalry(int n){cavalry=Math.max(0,cavalry+n);}
}
